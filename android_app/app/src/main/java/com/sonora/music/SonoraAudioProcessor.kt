package com.sonora.music
import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder

typealias SonoraAudioEngine = DoomentioEngine


/**
 * Real-time digital PCM Audio Processor for Sonora / DDMC.
 *
 * Motor Doomentio (Doomentio Engine):
 * High-performance 64-bit Audio DSP & Sound Processing Engine.
 * 1. Hardware-agnostic Stereo-to-Mono downmixing: (Left + Right) / 2
 * 2. Real Stereo Balance Pan: -100 (Full Left) to +100 (Full Right)
 * 3. Per-Track Digital Bass Boost and Gain Multiplier
 */
object DoomentioEngine {
    const val ENGINE_NAME = "Doomentio Engine"
    @Volatile var isMono: Boolean = false
    @Volatile var balancePan: Float = 0f // -100 to +100
    @Volatile var currentTrackBassBoostLevel: Int = 0 // -1000 to +1000 mB
    @Volatile var currentTrackGainMultiplier: Float = 1.0f // 0.5f to 2.0f
    @Volatile var currentTrackKey: String = ""

    // Low-shelf IIR filter state for digital bass enhancement on 16-bit PCM
    @Volatile var bassGainLinear: Float = 1.0f // computed from currentTrackBassBoostLevel

    fun init(context: Context) {
        isMono = SonoraStore.getBool(context, "audio_mono", false)
        balancePan = SonoraStore.getFloat(context, "audio_balance_pan", 0f)
    }

    fun setMono(context: Context, mono: Boolean) {
        isMono = mono
        SonoraStore.putBool(context, "audio_mono", mono)
    }

    fun setBalance(context: Context, pan: Float) {
        balancePan = pan.coerceIn(-100f, 100f)
        SonoraStore.putFloat(context, "audio_balance_pan", balancePan)
    }

    fun loadTrackOverrides(context: Context, trackKey: String) {
        currentTrackKey = trackKey
        currentTrackBassBoostLevel = SonoraStore.getInt(context, "track_bass_$trackKey", 0)
        currentTrackGainMultiplier = SonoraStore.getFloat(context, "track_gain_$trackKey", 1.0f)
        // Convert mB to linear gain: 10^(mB / 2000)
        bassGainLinear = if (currentTrackBassBoostLevel != 0) {
            Math.pow(10.0, currentTrackBassBoostLevel / 2000.0).toFloat().coerceIn(0.5f, 3.0f)
        } else {
            1.0f
        }
    }

    fun setTrackBass(context: Context, trackKey: String, bassMilliBels: Int) {
        SonoraStore.putInt(context, "track_bass_$trackKey", bassMilliBels)
        if (currentTrackKey == trackKey) {
            currentTrackBassBoostLevel = bassMilliBels
            bassGainLinear = if (bassMilliBels != 0) {
                Math.pow(10.0, bassMilliBels / 2000.0).toFloat().coerceIn(0.5f, 3.0f)
            } else {
                1.0f
            }
        }
    }

    fun setTrackGain(context: Context, trackKey: String, gain: Float) {
        val clamped = gain.coerceIn(0.5f, 2.0f)
        SonoraStore.putFloat(context, "track_gain_$trackKey", clamped)
        if (currentTrackKey == trackKey) {
            currentTrackGainMultiplier = clamped
        }
    }
}

class SonoraAudioProcessor : BaseAudioProcessor() {

    // Simple 1st-order IIR low-pass filter states for bass boost
    private var lLow: Float = 0f
    private var rLow: Float = 0f
    private val nativePcmFeed = FloatArray(512)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // We handle 16-bit PCM. If input is 16-bit PCM, enforce 2 channels stereo output.
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        return AudioProcessor.AudioFormat(inputAudioFormat.sampleRate, 2, C.ENCODING_PCM_16BIT)
    }

    override fun isActive(): Boolean {
        // Keep active so any changes to mono, balance pan, or per-track gain are processed live without pipeline reset
        return inputAudioFormat != AudioProcessor.AudioFormat.NOT_SET
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val inputChannelCount = inputAudioFormat.channelCount
        val sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(8000)
        // IIR filter alpha for ~150Hz cutoff: dt / (RC + dt)
        val dt = 1f / sampleRate
        val rc = 1f / (2f * Math.PI.toFloat() * 150f)
        val alpha = (dt / (rc + dt)).coerceIn(0.01f, 0.5f)

        val mono = DoomentioEngine.isMono
        val pan = DoomentioEngine.balancePan
        val gain = DoomentioEngine.currentTrackGainMultiplier
        val bassMultiplier = DoomentioEngine.bassGainLinear

        // Calculate stereo pan factors
        val leftPan = if (pan > 0f) (100f - pan) / 100f else 1.0f
        val rightPan = if (pan < 0f) (100f + pan) / 100f else 1.0f

        // Each 16-bit sample is 2 bytes. If input has inputChannelCount channels:
        val frameCount = remaining / (inputChannelCount * 2)
        val outputBytes = frameCount * 4 // Always 2 channels * 2 bytes = 4 bytes per frame

        val outBuffer = replaceOutputBuffer(outputBytes)
        outBuffer.order(ByteOrder.LITTLE_ENDIAN)

        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until frameCount) {
            var left: Int
            var right: Int

            if (inputChannelCount >= 2) {
                left = inputBuffer.short.toInt()
                right = inputBuffer.short.toInt()
                // Skip additional surround channels if present
                for (ch in 2 until inputChannelCount) {
                    inputBuffer.short
                }
            } else {
                // Mono input -> duplicate to both
                val s = inputBuffer.short.toInt()
                left = s
                right = s
            }

            // 1. Mono downmixing: (L + R) / 2
            if (mono) {
                val mixed = (left + right) / 2
                left = mixed
                right = mixed
            }

            // 2. Per-track bass boost filter (if active)
            if (bassMultiplier != 1.0f) {
                lLow += alpha * (left - lLow)
                rLow += alpha * (right - rLow)
                val lHigh = left - lLow
                val rHigh = right - rLow
                left = (lLow * bassMultiplier + lHigh).toInt()
                right = (rLow * bassMultiplier + rHigh).toInt()
            }

            // 3. Stereo Balance Pan
            if (pan != 0f) {
                left = (left * leftPan).toInt()
                right = (right * rightPan).toInt()
            }

            // 4. Per-track digital gain
            if (gain != 1.0f) {
                left = (left * gain).toInt()
                right = (right * gain).toInt()
            }

            // Clamp to 16-bit PCM range (-32768 to 32767)
            val finalL = left.coerceIn(-32768, 32767).toShort()
            val finalR = right.coerceIn(-32768, 32767).toShort()

            if (i < 512) {
                nativePcmFeed[i] = ((finalL.toInt() + finalR.toInt()) * 0.5f) / 32768.0f
            }

            outBuffer.putShort(finalL)
            outBuffer.putShort(finalR)
        }

        if (frameCount > 0 && DdmcNative.isAvailable) {
            val feedCount = minOf(frameCount, 512)
            DdmcNative.feedAudioSamples(nativePcmFeed, feedCount, 1)
        }

        outBuffer.flip()
    }
}



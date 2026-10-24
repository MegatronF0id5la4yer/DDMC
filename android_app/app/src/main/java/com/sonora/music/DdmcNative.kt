package com.sonora.music

import android.util.Log

object DdmcNative {
    private const val TAG = "DdmcNative"

    var isAvailable: Boolean = false
        private set

    init {
        try {
            System.loadLibrary("ddmc_native")
            isAvailable = true
            Log.i(TAG, "libddmc_native.so loaded successfully!")
        } catch (t: Throwable) {
            isAvailable = false
            Log.w(TAG, "Native C++ library not available, using pure Kotlin DSP fallback: ${t.message}")
        }
    }

    external fun nativeInit(perfMode: Int): Boolean
    external fun nativeSetPerfMode(perfMode: Int)
    external fun nativeFeedAudioSamples(samples: FloatArray, count: Int, channels: Int)
    external fun nativeFeedFftBytes(rawFft: ByteArray, count: Int)
    external fun nativeProcessFft(
        dtSec: Float,
        outMagnitudes: FloatArray,
        outPeakCaps: FloatArray,
        outWaveform: FloatArray
    ): Boolean
    external fun nativeGetVocalEnergy(): Float
    external fun nativeGetBassEnergy(): Float
    external fun nativeIsBeat(): Boolean
    external fun nativeUpdateCompanion(
        dtSec: Float,
        energy: Float,
        isBeat: Boolean,
        outData: FloatArray
    )
    external fun nativeTriggerJump()
    external fun nativeTriggerDance()
    external fun nativeSetCompanionCharacter(charId: Int)

    fun initialize(perfMode: Int = 1): Boolean {
        if (!isAvailable) return false
        return try {
            nativeInit(perfMode)
        } catch (t: Throwable) {
            Log.e(TAG, "Error initializing native engine", t)
            false
        }
    }

    fun processSpectrum(
        dtSec: Float,
        outMags: FloatArray,
        outCaps: FloatArray,
        outWave: FloatArray
    ): Boolean {
        if (!isAvailable) return false
        return try {
            nativeProcessFft(dtSec, outMags, outCaps, outWave)
        } catch (t: Throwable) {
            false
        }
    }

    fun feedFft(bytes: ByteArray?) {
        if (!isAvailable || bytes == null) return
        try {
            nativeFeedFftBytes(bytes, bytes.size)
        } catch (_: Throwable) {}
    }

    fun feedAudioSamples(samples: FloatArray, count: Int, channels: Int = 1) {
        if (!isAvailable || samples.isEmpty()) return
        try {
            nativeFeedAudioSamples(samples, count, channels)
        } catch (_: Throwable) {}
    }

    fun isAudioBeat(): Boolean {
        if (!isAvailable) return false
        return try {
            nativeIsBeat()
        } catch (_: Throwable) {
            false
        }
    }

    fun getVocalLevel(): Float {
        if (!isAvailable) return 0f
        return try {
            nativeGetVocalEnergy()
        } catch (_: Throwable) {
            0f
        }
    }

    fun getBassLevel(): Float {
        if (!isAvailable) return 0f
        return try {
            nativeGetBassEnergy()
        } catch (_: Throwable) {
            0f
        }
    }
}

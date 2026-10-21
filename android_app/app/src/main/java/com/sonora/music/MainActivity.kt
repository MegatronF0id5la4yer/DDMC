package com.sonora.music

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.*
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.InputType
import android.view.GestureDetector
import android.view.ScaleGestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.*
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import android.transition.TransitionManager
import android.transition.AutoTransition
import android.view.animation.PathInterpolator
import java.io.FileOutputStream
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.sin

data class TrackItem(
    var title: String,
    var artist: String,
    var album: String = "Álbum",
    var coverUrl: String? = null,
    var coverBitmap: Bitmap? = null,
    var videoUrl: String = "",
    var audioUri: Uri? = null,
    var audioUrl: String = "",
    var quality: String = "Audio",
    var provider: String = "Local",
    var durationMs: Long = 0L,
    var isLocal: Boolean = false,
    var isFavorite: Boolean = false,
    var trimStartMs: Long = 0L,
    var trimEndMs: Long = 0L,
    var dateAdded: Long = 0L,
    var folder: String = "",
    var path: String = "",
    var sizeBytes: Long = 0L
)



data class DokiAvatar(
    val id: String,
    val name: String,
    val subtitle: String,
    val colorHex: String,
    val normalResId: Int,
    val snugResId: Int,
    val schoolResId: Int = 0,
    val casualResId: Int = 0
) {
    /** Drawable for a pose key: normal, snug, school or casual. Falls back to normal when an outfit is missing. */
    fun resFor(pose: String): Int = when (pose) {
        "snug" -> snugResId
        "school" -> if (schoolResId != 0) schoolResId else normalResId
        "casual" -> if (casualResId != 0) casualResId else normalResId
        else -> normalResId
    }
}

val DOKI_AVATARS = listOf(
    DokiAvatar(
        id = "monika",
        name = "Monika",
        subtitle = "Presidenta del Club \u2022 Siempre contigo",
        colorHex = "#10B981",
        normalResId = R.drawable.doki_monika,
        snugResId = R.drawable.doki_monika_snug,
        schoolResId = R.drawable.doki_monika_school
    ),
    DokiAvatar(
        id = "sayori",
        name = "Sayori",
        subtitle = "Vicepresidenta \u2022 Rayo de sol y alegría",
        colorHex = "#FB7185",
        normalResId = R.drawable.doki_sayori,
        snugResId = R.drawable.doki_sayori_snug,
        schoolResId = R.drawable.doki_sayori_school,
        casualResId = R.drawable.doki_sayori_casual
    ),
    DokiAvatar(
        id = "natsuki",
        name = "Natsuki",
        subtitle = "Manga y Cupcakes \u2022 Dulce y tsundere",
        colorHex = "#F472B6",
        normalResId = R.drawable.doki_natsuki,
        snugResId = R.drawable.doki_natsuki_snug,
        schoolResId = R.drawable.doki_natsuki_school,
        casualResId = R.drawable.doki_natsuki_casual
    ),
    DokiAvatar(
        id = "yuri",
        name = "Yuri",
        subtitle = "Lectora apasionada \u2022 Elegante y profunda",
        colorHex = "#A855F7",
        normalResId = R.drawable.doki_yuri,
        snugResId = R.drawable.doki_yuri_snug,
        schoolResId = R.drawable.doki_yuri_school,
        casualResId = R.drawable.doki_yuri_casual
    ),
    DokiAvatar(
        id = "bocchi",
        name = "Bocchi",
        subtitle = "Hitori Gotoh \u2022 Guitar Hero & Caja de Mango",
        colorHex = "#EC4899",
        normalResId = R.drawable.doki_natsuki_casual,
        snugResId = R.drawable.doki_natsuki_snug
    ),
    DokiAvatar(
        id = "konata",
        name = "Konata",
        subtitle = "Lucky Star \u2022 Otaku & Gamer Nivel 99",
        colorHex = "#38BDF8",
        normalResId = R.drawable.doki_monika,
        snugResId = R.drawable.doki_monika_snug
    )
)

// ==========================================
// 5-MODE PRO HARDWARE & BEAT-REACTIVE VISUALIZER
// ==========================================
class VisualizerView(context: Context) : View(context) {
    private fun getAccentColor(): Int = try { Color.parseColor(Pal.ACCENT) } catch (_: Exception) { Color.parseColor("#38BDF8") }
    private fun getSecondaryColor(): Int = try { Color.parseColor(Pal.CYAN) } catch (_: Exception) { Color.parseColor("#818CF8") }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mirrorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val secondaryWavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 3.2f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val fillWavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F8FAFC")
        style = Paint.Style.FILL
    }
    private val radialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5.5f
        strokeCap = Paint.Cap.ROUND
    }
    private val waterfallPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val vuLedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glitchPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tunnelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
    }
    private val auroraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val auroraPath = Path()

    // 50 pre-allocated cosmic stars for starfield mode
    private val starCount = 50
    private val starX = FloatArray(50) { (Math.random().toFloat() - 0.5f) * 2f }
    private val starY = FloatArray(50) { (Math.random().toFloat() - 0.5f) * 2f }
    private val starZ = FloatArray(50) { Math.random().toFloat().coerceIn(0.08f, 1f) }
    private val starSpeed = FloatArray(50) { (0.010f + Math.random().toFloat() * 0.018f) }

    private var rawFft: ByteArray? = null
    private var rawWaveform: ByteArray? = null
    private var hasRealFftSignal = false
    private var hasRealWaveSignal = false
    private val bands: Int
        get() = PerformanceEngine.visualizerBands
    private val maxBands = 44
    private val magnitudes = FloatArray(maxBands)
    private val peakCaps = FloatArray(maxBands)
    private val waterfallHistory = ArrayDeque<FloatArray>()
    private val maxHistoryRows: Int
        get() = PerformanceEngine.maxWaterfallRows
    private var phase = 0f
    private var lastFrameMs = 0L
    var songBpm: Float = 120f

    // Pre-allocated drawing primitives (zero GC allocations per frame at 60/120fps)
    private val primaryWavePath = Path()
    private val secondaryWavePath = Path()
    private val fillWavePath = Path()
    private val tempHsv = FloatArray(3)
    private var cachedShaderH = -1f
    private var cachedShaderAcc = 0
    private var cachedShaderSec = 0

    var isPlaying = false
        set(value) {
            if (field != value) {
                field = value
                if (isShown) postInvalidateOnAnimation()
            }
        }
    var visualMode = 0

    companion object {
        val MODE_NAMES = arrayOf(
            "Espectro Espejo",
            "Osciloscopio Neón",
            "Pulso Radial 360°",
            "Cascada Espectral",
            "Estudio Híbrido",
            "Matriz VU LED Digital",
            "Starfield Cósmico",
            "Glitch Ciberpunk",
            "Túnel Vortex 3D",
            "Aurora Líquida"
        )
    }

    init {
        postInvalidateOnAnimation()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE && isShown) {
            postInvalidateOnAnimation()
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE && isShown) {
            postInvalidateOnAnimation()
        }
    }

    fun currentVocalEnergy(): Float {
        if (!isPlaying) return 0f
        return ((magnitudes[2] + magnitudes[5] + magnitudes[9] + magnitudes[14]) * 0.25f).coerceIn(0f, 1f)
    }

    fun updateFft(fft: ByteArray?) {
        rawFft = fft
        if (fft == null || fft.size < 8) {
            hasRealFftSignal = false
            return
        }
        val half = fft.size / 2
        var maxEnergy = 0f
        for (k in 1 until minOf(half, 64)) {
            val r = fft[2 * k].toFloat()
            val im = fft[2 * k + 1].toFloat()
            val m = hypot(r, im)
            if (m > maxEnergy) maxEnergy = m
        }
        hasRealFftSignal = maxEnergy > 2.2f
        if (!hasRealFftSignal) return

        for (i in 0 until bands) {
            // Logarithmic frequency bin distribution (more resolution in bass/mids)
            val ratio = i.toFloat() / bands.toFloat()
            val binStart = (1 + (ratio * ratio * (half * 0.65f)).toInt()).coerceIn(1, half - 1)
            val binEnd = (binStart + 2).coerceAtMost(half - 1)
            var sum = 0f
            var count = 0
            for (b in binStart..binEnd) {
                val r = fft[2 * b].toFloat()
                val im = fft[2 * b + 1].toFloat()
                sum += hypot(r, im)
                count++
            }
            val avg = if (count > 0) sum / count else 0f
            // Logarithmic dB boost so quiet and loud passages look vivid
            val boost = 1f + ratio * 1.35f
            val dbNorm = ((log10(1f + avg * boost) / 2.05f)).coerceIn(0.05f, 1.0f)
            val prev = magnitudes[i]
            magnitudes[i] = if (dbNorm > prev) (prev * 0.35f + dbNorm * 0.65f) else (prev * 0.78f + dbNorm * 0.22f)
            if (magnitudes[i] >= peakCaps[i]) {
                peakCaps[i] = magnitudes[i]
            } else {
                peakCaps[i] = (peakCaps[i] - 0.014f).coerceAtLeast(0.05f)
            }
        }
    }

    fun updateWaveform(waveform: ByteArray?) {
        rawWaveform = waveform
        if (waveform == null || waveform.isEmpty()) {
            hasRealWaveSignal = false
            return
        }
        var maxDev = 0
        for (i in 0 until minOf(waveform.size, 96)) {
            val dev = abs((waveform[i].toInt() and 0xFF) - 128)
            if (dev > maxDev) maxDev = dev
        }
        hasRealWaveSignal = maxDev > 3
    }

    private fun stepAcousticSimulation() {
        // High-precision multi-harmonic acoustic envelope when hardware offload zeroes out Visualizer
        val kick = ((sin(phase * 2.1f) + 1f) * 0.5f).let { it * it }
        val snare = ((sin(phase * 1.05f + 1.4f) + 1f) * 0.5f).let { it * it * it }
        for (i in 0 until bands) {
            val norm = i.toFloat() / bands.toFloat()
            val bassEnv = (1f - norm).coerceAtLeast(0f) * kick * 0.58f
            val midEnv = (1f - abs(norm - 0.42f) * 2.1f).coerceAtLeast(0f) * (0.35f + snare * 0.45f)
            val hiEnv = norm * ((sin(phase * 3.7f + i * 0.65f) + 1f) * 0.22f)
            val wave1 = (sin(phase * 1.6f + i * 0.32f) + 1f) * 0.16f
            val wave2 = (cos(phase * 2.4f - i * 0.48f) + 1f) * 0.11f
            val target = if (isPlaying) {
                (0.10f + bassEnv + midEnv + hiEnv + wave1 + wave2).coerceIn(0.08f, 0.96f)
            } else {
                0.05f
            }
            val prev = magnitudes[i]
            magnitudes[i] = if (target > prev) (prev * 0.42f + target * 0.58f) else (prev * 0.80f + target * 0.20f)
            if (magnitudes[i] >= peakCaps[i]) {
                peakCaps[i] = magnitudes[i]
            } else {
                peakCaps[i] = (peakCaps[i] - 0.013f).coerceAtLeast(0.05f)
            }
        }
    }

    private fun ensureShaders(h: Float, splitY: Float, acc: Int, sec: Int) {
        if (cachedShaderH == h && cachedShaderAcc == acc && cachedShaderSec == sec && barPaint.shader != null) {
            return
        }
        cachedShaderH = h
        cachedShaderAcc = acc
        cachedShaderSec = sec
        Color.colorToHSV(acc, tempHsv)
        val lighter = Color.HSVToColor(floatArrayOf(tempHsv[0], (tempHsv[1] * 0.55f).coerceIn(0f, 1f), 1.0f))
        val darker = Color.HSVToColor(floatArrayOf((tempHsv[0] + 35f) % 360f, tempHsv[1], (tempHsv[2] * 0.70f).coerceIn(0f, 1f)))

        barPaint.shader = LinearGradient(
            0f, 0f, 0f, splitY,
            intArrayOf(lighter, acc, sec, darker),
            null,
            Shader.TileMode.CLAMP
        )
        mirrorPaint.shader = LinearGradient(
            0f, splitY, 0f, h,
            intArrayOf(
                sec and 0x00FFFFFF or 0x55000000,
                darker and 0x00FFFFFF or 0x08000000
            ),
            null,
            Shader.TileMode.CLAMP
        )
        fillWavePaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(acc and 0x00FFFFFF or 0x44000000, sec and 0x00FFFFFF or 0x18000000, Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val reducedMotion = MotionTokens.isReducedMotion(context)
        val now = System.currentTimeMillis()
        val dtSec = if (lastFrameMs > 0L) ((now - lastFrameMs) / 1000f).coerceIn(0.005f, 0.05f) else 0.016f
        lastFrameMs = now

        if (isPlaying && !reducedMotion) {
            val bpm = songBpm.coerceIn(60f, 200f)
            val bpmSpeed = (bpm / 120f) * 2.8f
            phase += dtSec * bpmSpeed
        }
        if (!hasRealFftSignal || !isPlaying) {
            stepAcousticSimulation()
        }

        // Push snapshot to waterfall history only when waterfall mode is active
        if (isPlaying && visualMode == 3) {
            waterfallHistory.addFirst(magnitudes.clone())
            while (waterfallHistory.size > maxHistoryRows) waterfallHistory.removeLast()
        }

        when (visualMode) {
            0 -> drawMirrorBars(canvas, w, h)
            1 -> drawDualNeonWaveform(canvas, w, h)
            2 -> drawRadialPulse(canvas, w, h)
            3 -> drawWaterfall(canvas, w, h)
            4 -> {
                drawMirrorBars(canvas, w, h)
                drawDualNeonWaveform(canvas, w, h)
            }
            5 -> drawDigitalVuMatrix(canvas, w, h)
            6 -> drawCosmicStarfield(canvas, w, h)
            7 -> drawCyberpunkGlitchWave(canvas, w, h)
            8 -> drawVortexTunnel3D(canvas, w, h)
            9 -> drawLiquidAurora(canvas, w, h)
            else -> drawMirrorBars(canvas, w, h)
        }

        // Suspend animation loop when hidden, detached, or paused at rest
        val settling = !isPlaying && magnitudes[0] > 0.055f
        if (isShown && windowVisibility == VISIBLE && (isPlaying || settling) && !reducedMotion) {
            if (PerformanceEngine.isPotatoMode) {
                postInvalidateDelayed(PerformanceEngine.frameIntervalMs)
            } else {
                postInvalidateOnAnimation()
            }
        }
    }

    private fun drawMirrorBars(canvas: Canvas, w: Float, h: Float) {
        val barWidth = w / (bands * 1.38f)
        val gap = barWidth * 0.38f
        val totalSpan = bands * (barWidth + gap) - gap
        val startX = (w - totalSpan) / 2f
        val splitY = h * 0.72f

        val acc = getAccentColor()
        val sec = getSecondaryColor()
        ensureShaders(h, splitY, acc, sec)

        for (i in 0 until bands) {
            val mag = magnitudes[i].coerceIn(0.05f, 1.0f)
            val upperH = (splitY * 0.90f * mag).coerceAtLeast(4f)
            val lowerH = ((h - splitY) * 0.78f * mag).coerceAtLeast(2f)
            val left = startX + i * (barWidth + gap)
            val right = left + barWidth
            val radius = barWidth / 2f

            // Upper primary bar
            canvas.drawRoundRect(left, splitY - upperH, right, splitY, radius, radius, barPaint)
            // Lower mirror reflection bar
            canvas.drawRoundRect(left, splitY + 2f, right, splitY + 2f + lowerH, radius, radius, mirrorPaint)

            // Floating peak pill
            val capY = (splitY - (splitY * 0.90f * peakCaps[i]) - 6f).coerceAtLeast(2f)
            canvas.drawRoundRect(left, capY, right, capY + 3.5f, 2f, 2f, capPaint)
        }
    }

    private fun drawDualNeonWaveform(canvas: Canvas, w: Float, h: Float) {
        val midY = h * 0.52f
        primaryWavePath.reset()
        secondaryWavePath.reset()
        fillWavePath.reset()

        val points = 36
        val dx = w / (points - 1).toFloat()
        var prevX = 0f
        var prevY = midY
        var prevY2 = midY

        val wave = rawWaveform
        val useWave = hasRealWaveSignal && wave != null && wave.isNotEmpty() && isPlaying

        for (i in 0 until points) {
            val x = i * dx
            val sample: Float = if (useWave && wave != null) {
                val idx = ((i.toFloat() / points) * wave.size).toInt().coerceIn(0, wave.size - 1)
                ((wave[idx].toInt() and 0xFF) - 128) / 128f
            } else {
                val bandIdx = ((i.toFloat() / points) * (bands - 1)).toInt().coerceIn(0, bands - 1)
                val env = if (isPlaying) magnitudes[bandIdx] else 0.05f
                sin(phase * 1.9f + i * 0.42f) * env * 0.75f
            }
            val sample2 = cos(phase * 1.45f - i * 0.36f) * (if (isPlaying) magnitudes[(i * 7) % bands] * 0.55f else 0.03f)

            val y = (midY + sample * (h * 0.42f)).coerceIn(4f, h - 4f)
            val y2 = (midY + sample2 * (h * 0.36f)).coerceIn(4f, h - 4f)

            if (i == 0) {
                primaryWavePath.moveTo(x, y)
                secondaryWavePath.moveTo(x, y2)
                fillWavePath.moveTo(x, h)
                fillWavePath.lineTo(x, y)
            } else {
                val cx = (prevX + x) / 2f
                primaryWavePath.cubicTo(cx, prevY, cx, y, x, y)
                secondaryWavePath.cubicTo(cx, prevY2, cx, y2, x, y2)
                fillWavePath.cubicTo(cx, prevY, cx, y, x, y)
            }
            prevX = x
            prevY = y
            prevY2 = y2
        }

        fillWavePath.lineTo(w, h)
        fillWavePath.close()
        val acc = getAccentColor()
        val sec = getSecondaryColor()
        ensureShaders(h, h * 0.72f, acc, sec)
        wavePaint.color = acc
        secondaryWavePaint.color = sec
        canvas.drawPath(fillWavePath, fillWavePaint)
        canvas.drawPath(secondaryWavePath, secondaryWavePaint)
        canvas.drawPath(primaryWavePath, wavePaint)
    }

    private fun drawRadialPulse(canvas: Canvas, w: Float, h: Float) {
        val cx = w / 2f
        val cy = h / 2f
        val minDim = minOf(w, h)
        val bassAvg = (magnitudes[0] + magnitudes[1] + magnitudes[2] + magnitudes[3]) / 4f
        val baseRadius = minDim * (0.24f + bassAvg * 0.08f)
        val maxRay = minDim * 0.24f

        val acc = getAccentColor()
        val sec = getSecondaryColor()

        // Inner glowing ring
        radialPaint.color = sec and 0x00FFFFFF or 0x44000000
        radialPaint.strokeWidth = 3f
        canvas.drawCircle(cx, cy, baseRadius, radialPaint)

        radialPaint.strokeWidth = 4.5f
        for (i in 0 until bands) {
            val angle = (i.toFloat() / bands) * (Math.PI * 2.0) + phase * 0.15
            val mag = magnitudes[i].coerceIn(0.06f, 1.0f)
            val r0 = baseRadius
            val r1 = baseRadius + maxRay * mag
            val x0 = cx + (cos(angle) * r0).toFloat()
            val y0 = cy + (sin(angle) * r0).toFloat()
            val x1 = cx + (cos(angle) * r1).toFloat()
            val y1 = cy + (sin(angle) * r1).toFloat()

            radialPaint.color = when {
                i % 3 == 0 -> acc
                i % 3 == 1 -> sec
                else -> Color.WHITE
            }
            canvas.drawLine(x0, y0, x1, y1, radialPaint)
        }
    }

    private fun drawWaterfall(canvas: Canvas, w: Float, h: Float) {
        val cellW = w / bands.toFloat()
        val cellH = h / maxHistoryRows.toFloat()
        val acc = getAccentColor()
        val sec = getSecondaryColor()
        val rAcc = Color.red(acc); val gAcc = Color.green(acc); val bAcc = Color.blue(acc)
        val rSec = Color.red(sec); val gSec = Color.green(sec); val bSec = Color.blue(sec)

        var rIdx = 0
        for (row in waterfallHistory) {
            val top = rIdx * cellH
            val fade = 1f - (rIdx.toFloat() / maxHistoryRows.toFloat()) * 0.55f
            for (b in 0 until bands) {
                val v = (row[b] * fade).coerceIn(0f, 1f)
                waterfallPaint.color = when {
                    v < 0.45f -> {
                        val f = (v / 0.45f).coerceIn(0f, 1f)
                        Color.argb((40 + f * 140).toInt(), (rSec * f).toInt(), (gSec * f).toInt(), (bSec * f).toInt())
                    }
                    v < 0.82f -> {
                        val f = ((v - 0.45f) / 0.37f).coerceIn(0f, 1f)
                        Color.argb(
                            (180 + f * 65).toInt(),
                            (rSec + (rAcc - rSec) * f).toInt(),
                            (gSec + (gAcc - gSec) * f).toInt(),
                            (bSec + (bAcc - bSec) * f).toInt()
                        )
                    }
                    else -> Color.WHITE
                }
                val left = b * cellW
                canvas.drawRect(left, top, left + cellW - 1f, top + cellH - 1f, waterfallPaint)
            }
            rIdx++
        }
    }

    private fun drawDigitalVuMatrix(canvas: Canvas, w: Float, h: Float) {
        val totalCols = bands
        val colW = (w / (totalCols * 1.35f)).coerceAtLeast(3f)
        val gap = colW * 0.35f
        val totalSpan = totalCols * (colW + gap) - gap
        val startX = (w - totalSpan) / 2f
        val numSegments = 14
        val totalH = h * 0.88f
        val segH = (totalH / numSegments) * 0.72f
        val segGap = (totalH / numSegments) * 0.28f
        val bottomY = h * 0.94f

        for (i in 0 until totalCols) {
            val mag = magnitudes[i].coerceIn(0.05f, 1.0f)
            val activeSegments = (mag * numSegments).toInt().coerceIn(1, numSegments)
            val peakSegment = (peakCaps[i] * numSegments).toInt().coerceIn(0, numSegments - 1)
            val left = startX + i * (colW + gap)
            val right = left + colW

            for (s in 0 until numSegments) {
                val segTop = bottomY - (s + 1) * (segH + segGap)
                val segBottom = segTop + segH
                val isActive = s < activeSegments
                val isPeak = s == peakSegment

                vuLedPaint.color = when {
                    isPeak -> Color.WHITE
                    isActive && s >= 11 -> Color.parseColor("#EF4444")
                    isActive && s >= 8 -> Color.parseColor("#F59E0B")
                    isActive -> Color.parseColor("#10B981")
                    else -> Color.parseColor("#18334155")
                }
                canvas.drawRoundRect(left, segTop, right, segBottom, 2.5f, 2.5f, vuLedPaint)
            }
        }
    }

    private fun drawCosmicStarfield(canvas: Canvas, w: Float, h: Float) {
        val cx = w / 2f
        val cy = h / 2f
        val bass = (magnitudes[0] + magnitudes[1] + magnitudes[2]) * 0.33f
        val speedMult = 1f + bass * 3.5f

        val acc = getAccentColor()
        val sec = getSecondaryColor()

        for (s in 0 until starCount) {
            val prevZ = starZ[s]
            starZ[s] -= starSpeed[s] * speedMult
            if (starZ[s] <= 0.04f) {
                starZ[s] = 1.0f
                starX[s] = (Math.random().toFloat() - 0.5f) * 2f
                starY[s] = (Math.random().toFloat() - 0.5f) * 2f
            }
            val z = starZ[s]
            val px = cx + (starX[s] / z) * (w * 0.48f)
            val py = cy + (starY[s] / z) * (h * 0.48f)
            val ppx = cx + (starX[s] / prevZ) * (w * 0.48f)
            val ppy = cy + (starY[s] / prevZ) * (h * 0.48f)

            if (px in 0f..w && py in 0f..h) {
                val alpha = ((1f - z) * 255).toInt().coerceIn(30, 255)
                val dotSize = (1.5f + (1f - z) * 4f).coerceAtLeast(1f)
                starPaint.color = when {
                    s % 3 == 0 -> (acc and 0x00FFFFFF) or (alpha shl 24)
                    s % 3 == 1 -> (sec and 0x00FFFFFF) or (alpha shl 24)
                    else -> Color.argb(alpha, 255, 255, 255)
                }
                starPaint.strokeWidth = dotSize
                canvas.drawLine(ppx, ppy, px, py, starPaint)
                canvas.drawCircle(px, py, dotSize * 0.6f, starPaint)
            }
        }
    }

    private fun drawCyberpunkGlitchWave(canvas: Canvas, w: Float, h: Float) {
        val midY = h * 0.52f
        primaryWavePath.reset()
        val points = 32
        val dx = w / (points - 1).toFloat()
        var prevX = 0f
        var prevY = midY

        val isGlitch = magnitudes[1] > 0.45f || magnitudes[3] > 0.5f
        val glitchOffset = if (isGlitch) (sin(phase * 8f) * 6f) else 0f

        for (i in 0 until points) {
            val x = i * dx
            val bandIdx = ((i.toFloat() / points) * (bands - 1)).toInt().coerceIn(0, bands - 1)
            val env = if (isPlaying) magnitudes[bandIdx] else 0.05f
            val sample = sin(phase * 2.2f + i * 0.45f) * env * 0.82f
            val y = (midY + sample * (h * 0.42f) + (if (i % 4 == 0) glitchOffset else 0f)).coerceIn(4f, h - 4f)

            if (i == 0) {
                primaryWavePath.moveTo(x, y)
            } else {
                val cx = (prevX + x) / 2f
                primaryWavePath.cubicTo(cx, prevY, cx, y, x, y)
            }
            prevX = x
            prevY = y
        }

        glitchPaint.style = Paint.Style.STROKE
        glitchPaint.strokeCap = Paint.Cap.ROUND
        glitchPaint.strokeWidth = 3.5f
        glitchPaint.color = Color.parseColor("#E6FF007F")
        canvas.save()
        canvas.translate(-3.5f + glitchOffset * 0.5f, -1.5f)
        canvas.drawPath(primaryWavePath, glitchPaint)
        canvas.restore()

        glitchPaint.color = Color.parseColor("#E600F0FF")
        canvas.save()
        canvas.translate(3.5f - glitchOffset * 0.5f, 1.5f)
        canvas.drawPath(primaryWavePath, glitchPaint)
        canvas.restore()

        glitchPaint.color = Color.WHITE
        glitchPaint.strokeWidth = 2.5f
        canvas.drawPath(primaryWavePath, glitchPaint)
    }

    private fun drawVortexTunnel3D(canvas: Canvas, w: Float, h: Float) {
        val cx = w / 2f
        val cy = h / 2f
        val minDim = minOf(w, h)
        val rings = 9
        val acc = getAccentColor()
        val sec = getSecondaryColor()

        for (r in 0 until rings) {
            val progress = ((r.toFloat() + (phase * 0.22f) % 1f) / rings.toFloat())
            val radius = minDim * 0.48f * progress * progress
            val ringMag = magnitudes[r % bands].coerceIn(0.06f, 1.0f)
            val dynamicR = radius * (0.85f + ringMag * 0.35f)
            val ringAngle = phase * 0.4f + r * 0.3f

            tunnelPaint.color = when {
                r % 2 == 0 -> (acc and 0x00FFFFFF) or (((0.3f + progress * 0.7f) * 255).toInt() shl 24)
                else -> (sec and 0x00FFFFFF) or (((0.3f + progress * 0.7f) * 255).toInt() shl 24)
            }
            tunnelPaint.strokeWidth = 2f + progress * 2.5f

            val sides = 6
            primaryWavePath.reset()
            for (s in 0 until sides) {
                val a = ringAngle + (s.toFloat() / sides) * (Math.PI * 2.0)
                val px = cx + (cos(a) * dynamicR).toFloat()
                val py = cy + (sin(a) * dynamicR).toFloat()
                if (s == 0) primaryWavePath.moveTo(px, py) else primaryWavePath.lineTo(px, py)
            }
            primaryWavePath.close()
            canvas.drawPath(primaryWavePath, tunnelPaint)
        }
    }

    private fun drawLiquidAurora(canvas: Canvas, w: Float, h: Float) {
        val midY = h * 0.65f
        val acc = getAccentColor()
        val sec = getSecondaryColor()

        for (layer in 0 until 3) {
            auroraPath.reset()
            auroraPath.moveTo(0f, h)
            val points = 24
            val dx = w / (points - 1)
            var prevX = 0f
            var prevY = midY

            val speed = 1.1f + layer * 0.5f
            val phaseShift = layer * 1.2f

            for (i in 0 until points) {
                val x = i * dx
                val bandIdx = ((i.toFloat() / points) * (bands - 1)).toInt().coerceIn(0, bands - 1)
                val env = magnitudes[bandIdx]
                val waveH = (sin(phase * speed + i * 0.35f + phaseShift) * (h * 0.28f) * env)
                val y = (midY - waveH - (2 - layer) * (h * 0.08f)).coerceIn(4f, h - 2f)

                if (i == 0) {
                    auroraPath.lineTo(x, y)
                } else {
                    val cx = (prevX + x) / 2f
                    auroraPath.cubicTo(cx, prevY, cx, y, x, y)
                }
                prevX = x
                prevY = y
            }
            auroraPath.lineTo(w, h)
            auroraPath.close()

            val alpha = when (layer) {
                0 -> 70
                1 -> 110
                else -> 160
            }
            val color = when (layer) {
                0 -> Color.parseColor("#10B981")
                1 -> sec
                else -> acc
            }
            auroraPaint.color = (color and 0x00FFFFFF) or (alpha shl 24)
            canvas.drawPath(auroraPath, auroraPaint)
        }
    }
}

// ==========================================
// PHYSICAL TACTILE FEEDBACK (`emil-design-eng`, `apple-design`, `animate`)
// ==========================================
@SuppressLint("ClickableViewAccessibility")
fun View.addPressFeedback(scaleTo: Float = 0.96f, haptic: Boolean = true) {
    // Never scale below 0.94f (avoid cartoonish 0.85f-0.88f press bounce)
    val clampedScale = scaleTo.coerceIn(0.94f, 0.99f)
    setOnTouchListener { v, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (haptic && Haptics.enabled) {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
                v.animate().cancel()
                if (!MotionTokens.isReducedMotion(v.context)) {
                    v.animate()
                        .scaleX(clampedScale)
                        .scaleY(clampedScale)
                        .setDuration(MotionTokens.DUR_PRESS_DOWN)
                        .setInterpolator(MotionTokens.EASE_OUT)
                        .start()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                v.animate().cancel()
                if (!MotionTokens.isReducedMotion(v.context)) {
                    v.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(MotionTokens.DUR_PRESS_UP)
                        .setInterpolator(MotionTokens.EASE_OUT)
                        .start()
                } else {
                    v.scaleX = 1.0f
                    v.scaleY = 1.0f
                }
            }
        }
        false
    }
}

// ==========================================
// WINDOWS DESKTOP-STYLE WINDOW FRAME
// ==========================================
class StageWindowFrame(
    context: Context,
    val windowId: String,
    val title: String,
    val iconRes: Int,
    val onMinimizeClick: (() -> Unit)? = null,
    val onMaximizeClick: (() -> Unit)? = null,
    val onMoreClick: (() -> Unit)? = null
) : LinearLayout(context) {

    val titleBar: LinearLayout
    val titleTextView: TextView
    val contentContainer: FrameLayout
    var isWindowMinimized: Boolean = false
    var isWindowMaximized: Boolean = false
    var windowScale: Float = 1.0f
    var onScaleUpdate: ((Float, String) -> Unit)? = null

    private var downRawX = 0f
    private var downRawY = 0f
    private var initialTx = 0f
    private var initialTy = 0f
    private var isDragging = false
    private var lastTapTime = 0L

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            parent?.requestDisallowInterceptTouchEvent(true)
            translationZ = (context.resources.displayMetrics.density * 16f)
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val factor = detector.scaleFactor
            windowScale = (windowScale * factor).coerceIn(0.40f, 2.80f)
            scaleX = windowScale
            scaleY = windowScale
            val rounded = String.format(java.util.Locale.US, "%.1fx", windowScale)
            onScaleUpdate?.invoke(windowScale, rounded)
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            parent?.requestDisallowInterceptTouchEvent(false)
            val density = context.resources.displayMetrics.density
            animate().translationZ(density * 3f).setDuration(220).start()
            if (windowScale >= 2.3f) {
                windowScale = 1.0f
                scaleX = 1.0f
                scaleY = 1.0f
                onMaximizeClick?.invoke()
            } else if (windowScale <= 0.48f) {
                windowScale = 1.0f
                scaleX = 1.0f
                scaleY = 1.0f
                onMinimizeClick?.invoke()
            } else if (windowScale in 0.92f..1.08f) {
                windowScale = 1.0f
                animate().scaleX(1.0f).scaleY(1.0f).setDuration(180).start()
            }
        }
    })

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.pointerCount >= 2) {
            scaleDetector.onTouchEvent(ev)
            return true
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (ev.pointerCount >= 2) {
            scaleDetector.onTouchEvent(ev)
            return true
        }
        return super.onTouchEvent(ev)
    }

    init {
        orientation = VERTICAL
        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        background = GradientDrawable().apply {
            setColor(Color.parseColor("#141726"))
            cornerRadius = density * 12f
            setStroke(dp(1), Color.parseColor("#2638BDF8"))
        }
        elevation = density * 3f

        // Windows Titlebar
        titleBar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(6), dp(4))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E2338"))
                cornerRadii = floatArrayOf(
                    density * 12f, density * 12f,
                    density * 12f, density * 12f,
                    0f, 0f, 0f, 0f
                )
            }
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(28))
        }

        // Initialize content container before button callbacks access it
        contentContainer = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
            clipChildren = false
            clipToPadding = false
        }

        // Window Icon
        titleBar.addView(ImageView(context).apply {
            setImageResource(iconRes)
            setColorFilter(Color.parseColor("#38BDF8"))
            layoutParams = LayoutParams(dp(14), dp(14)).apply { marginEnd = dp(6) }
        })

        // Window Title
        titleTextView = TextView(context).apply {
            text = title
            textSize = 11.5f
            setTextColor(Color.WHITE)
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        titleBar.addView(titleTextView)

        fun makeWinBtn(symbol: String, onClick: () -> Unit) = TextView(context).apply {
            text = symbol
            textSize = 10.5f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            setPadding(dp(4), dp(2), dp(4), dp(2))
            minWidth = dp(22)
            isClickable = true
            isFocusable = true
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#14FFFFFF"))
                cornerRadius = density * 4f
            }
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, dp(20)).apply {
                marginStart = dp(3)
            }
            setOnClickListener { onClick() }
        }

        val minBtn = makeWinBtn("—") {
            isWindowMinimized = !isWindowMinimized
            onMinimizeClick?.invoke()
            contentContainer.visibility = if (isWindowMinimized) View.GONE else View.VISIBLE
        }
        val maxBtn = makeWinBtn("□") {
            isWindowMaximized = !isWindowMaximized
            onMaximizeClick?.invoke()
        }
        val optBtn = makeWinBtn("⋯") {
            onMoreClick?.invoke()
        }

        titleBar.addView(minBtn)
        titleBar.addView(maxBtn)
        titleBar.addView(optBtn)

        titleBar.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = ev.rawX
                    downRawY = ev.rawY
                    initialTx = translationX
                    initialTy = translationY
                    isDragging = false
                    val now = System.currentTimeMillis()
                    if (now - lastTapTime < 320L) {
                        isWindowMaximized = !isWindowMaximized
                        onMaximizeClick?.invoke()
                    }
                    lastTapTime = now
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - downRawX
                    val dy = ev.rawY - downRawY
                    if (Math.abs(dx) > dp(3) || Math.abs(dy) > dp(3)) {
                        isDragging = true
                        translationX = initialTx + dx
                        translationY = initialTy + dy
                        translationZ = density * 16f
                        scaleX = 1.02f
                        scaleY = 1.02f
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    if (isDragging) {
                        isDragging = false
                        animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .translationZ(density * 3f)
                            .setDuration(220)
                            .start()
                    }
                    true
                }
                else -> false
            }
        }

        addView(titleBar)
        addView(contentContainer)
    }

    fun setContent(view: View) {
        (view.parent as? ViewGroup)?.removeView(view)
        view.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        contentContainer.removeAllViews()
        contentContainer.addView(view)
    }
}

class MainActivity : AppCompatActivity(), RowHost {

    private lateinit var exoPlayer: ExoPlayer
    private var audioVisualizer: Visualizer? = null

    // Looping silent cover video & Nav position
    private var stageCoverVideoPlayer: MediaPlayer? = null
    private var stageCoverVideoView: TextureView? = null
    private var stageCoverSurface: Surface? = null
    private var currentCoverVideoUri: String? = null
    private var lastCoverVideoWidth: Int = 0
    private var lastCoverVideoHeight: Int = 0
    private var pendingVideoTrack: TrackItem? = null
    private val navItemContainers = mutableListOf<LinearLayout>()
    private var currentNavPosition: String = "bottom"

    // ---- unified stage block & visibility toggles ----
    private var showStageCover = true
    private var isStageCoverExpanded = true
    private var stageMediaXL = false
    private var showStageViz = true
    private var showStageDoki = true
    private var showStageLyrics = true
    private var stageRightMode = 0 // 0 = Letra + Viz, 1 = Solo Audio Viz, 2 = Solo Letra
    private var isLandscapeMode = false
    private lateinit var coverWindowFrame: StageWindowFrame
    private lateinit var vizWindowFrame: StageWindowFrame
    private lateinit var lyricsWindowFrame: StageWindowFrame
    private lateinit var dokiWindowFrame: StageWindowFrame
    private var fullscreenWindowId: String? = null
    private var isCoverMinimizedPip = false

    private lateinit var mainRootLayout: RelativeLayout
    private lateinit var mainContentFrame: FrameLayout
    private lateinit var playerOuterLayout: LinearLayout
    private lateinit var portraitScrollContainer: ScrollView
    private lateinit var portraitInnerBox: LinearLayout
    private lateinit var portraitToolsScroll: HorizontalScrollView
    private lateinit var portraitViewSelectorsRow: LinearLayout
    private lateinit var portraitLowerControlsBox: LinearLayout

    private lateinit var unifiedStageBlock: LinearLayout
    private lateinit var stageColumnsRow: LinearLayout
    private lateinit var dokiLeftCol: FrameLayout
    private lateinit var dokiSpriteView: DokiStageSpriteView
    private lateinit var dokiFloatingBadge: TextView
    private lateinit var dokiActionBadge: TextView
    private lateinit var lyricsRightCol: LinearLayout
    private lateinit var stageCoverInsetCard: FrameLayout
    private lateinit var stageExpandedCoverCard: FrameLayout
    private lateinit var stageExpandedCoverImage: ImageView
    private lateinit var stageVideoCard: FrameLayout
    private lateinit var youtubeStageWebView: YouTubeStageWebView
    private lateinit var stageLyricsScroll: ScrollView
    private lateinit var stageLyricsBox: LinearLayout
    private val stageLyricViews = mutableListOf<TextView>()
    private var lastStageActiveLyricIdx = -1
    private lateinit var vizDeckCard: FrameLayout
    private lateinit var stageModeSwitchChip: TextView

    private lateinit var mediaStageCol: LinearLayout
    private lateinit var companionLyricsCol: LinearLayout
    private lateinit var dokiStageCard: LinearLayout
    private lateinit var dokiPoseSubtext: TextView
    private lateinit var toggleCoverBtn: ImageView
    private lateinit var toggleExpandCoverBtn: ImageView
    private lateinit var toggleStageVideoChip: ImageView
    private lateinit var toggleVizBtn: ImageView
    private lateinit var toggleDokiBtn: ImageView
    private lateinit var toggleLyricsBtn: ImageView

    private lateinit var shimejiFullScreenOverlay: FrameLayout
    private lateinit var glassBannerView: LinearLayout
    private lateinit var glassBannerText: TextView
    private lateinit var glassBannerCancelBtn: TextView
    private var activeSearchThread: Thread? = null
    private var isSearchCancelled: Boolean = false

    // ---- landscape compact bottom bar (15% height) ----
    private lateinit var landscapeBottomBar: LinearLayout
    private lateinit var landscapeCoverThumb: ImageView
    private lateinit var landscapeTitleText: TextView
    private lateinit var landscapeArtistText: TextView
    private lateinit var landscapeFavBtn: ImageButton
    private lateinit var landscapeShuffleBtn: ImageButton
    private lateinit var landscapePrevBtn: ImageButton
    private lateinit var landscapePlayPauseBtn: ImageButton
    private lateinit var landscapeNextBtn: ImageButton
    private lateinit var landscapeRepeatBtn: ImageButton
    private lateinit var landscapeSeekBar: SeekBar
    private lateinit var landscapeCurTimeText: TextView
    private lateinit var landscapeTotTimeText: TextView
    private lateinit var portraitMetaCoverThumb: ImageView

    // ---- player views ----
    private lateinit var visualizerView: VisualizerView
    private lateinit var visualizerStripView: VisualizerView
    private lateinit var visualizerModeBadge: TextView
    private lateinit var playerView: PlayerView
    private lateinit var coverImage: ImageView
    private lateinit var ambientGlowImage: ImageView
    private lateinit var stageContainer: FrameLayout
    private lateinit var titleText: TextView
    private lateinit var artistText: TextView
    private lateinit var albumText: TextView
    private lateinit var formatBadgeText: TextView
    private lateinit var bpmKeyBadgeText: TextView
    private lateinit var providerBadgeText: TextView
    private lateinit var playPauseBtn: ImageButton
    private lateinit var shuffleBtn: ImageButton
    private lateinit var repeatBtn: ImageButton
    private lateinit var favBtn: ImageButton
    private lateinit var toggleVideoBtn: Pill
    private lateinit var speedBtn: Pill
    private lateinit var sleepPill: Pill
    private lateinit var loopPill: Pill
    private lateinit var lyricCard: LinearLayout
    private lateinit var lyricPrev: TextView
    private lateinit var lyricCur: TextView
    private lateinit var lyricNext: TextView
    private lateinit var playerSeekBar: SeekBar
    private lateinit var currentTimeText: TextView
    private lateinit var totalTimeText: TextView

    // ---- mini player ----
    private lateinit var miniPlayerRoot: LinearLayout
    private lateinit var miniCover: CoverView
    private lateinit var miniTitle: TextView
    private lateinit var miniArtist: TextView
    private lateinit var miniPlayBtn: ImageButton
    private lateinit var miniNextBtn: ImageButton

    // ---- tabs / chrome ----
    private lateinit var playerTabContainer: View
    private lateinit var localFilesTabContainer: View
    private lateinit var playlistTabContainer: View
    private lateinit var onlineTabContainer: View
    private lateinit var soundTabContainer: View
    private lateinit var widgetTabContainer: View
    private lateinit var topHeader: LinearLayout
    private lateinit var headerTitle: TextView
    private lateinit var bottomNavBar: LinearLayout
    private val navIcons = mutableListOf<ImageView>()
    private val navLabels = mutableListOf<TextView>()
    private val navPlates = mutableListOf<FrameLayout>()
    private val tabTitles = listOf("DDMC", "Biblioteca", "Listas", "YouTube", "Sonido", "Widgets")

    // ---- library ----
    private enum class LibMode(val label: String, val icon: Int) {
        SONGS("Canciones", R.drawable.ic_music),
        ALBUMS("Álbumes", R.drawable.ic_album),
        ARTISTS("Artistas", R.drawable.ic_user),
        FOLDERS("Carpetas", R.drawable.ic_folder),
        FAVORITES("Favoritas", R.drawable.ic_heart),
        RECENT("Historial", R.drawable.ic_clock),
        TOP("Más escuchadas", R.drawable.ic_trending),
        ADDED("Añadidas", R.drawable.ic_plus)
    }

    private val localTracksList = mutableListOf<TrackItem>()
    private val libraryByKey = HashMap<String, TrackItem>()
    private var libMode = LibMode.SONGS
    private var libGroup: Row.Group? = null
    private var libQuery = ""
    private var sortKey = "title"
    private var sortAsc = true
    private lateinit var libAdapter: RowAdapter
    private lateinit var libRecycler: RecyclerView
    private lateinit var libSearch: EditText
    private val libChips = mutableListOf<Pill>()
    private lateinit var libBackPill: Pill

    // ---- playlists ----
    private val playlists = LinkedHashMap<String, MutableList<TrackRef>>()
    private lateinit var plAdapter: RowAdapter
    private lateinit var plRecycler: RecyclerView

    // ---- youtube, catalog & online ----
    private lateinit var ytSearchInput: EditText
    private lateinit var ytStatusText: TextView
    private lateinit var ytResultsBox: LinearLayout
    private lateinit var catalogSearchInput: EditText
    private lateinit var catalogResultsBox: LinearLayout
    private lateinit var catalogStatusText: TextView
    private lateinit var onlineUrlInput: EditText
    private lateinit var onlineRecentBox: LinearLayout

    // ---- sound ----
    private var eqSliders = mutableListOf<SeekBar>()
    private var eqLabels = mutableListOf<TextView>()

    // ---- doki ----
    private var selectedDokiId = "natsuki"
    private var selectedDokiPose = "casual"
    private var selectedDokiExpr = "shimeji"
    private lateinit var topDokiAvatar: ImageView
    private lateinit var playerDokiCompanion: ImageView
    private lateinit var playerDokiName: TextView

    // ---- queue / playback state ----
    private val tracks = mutableListOf<TrackItem>()
    private var currentTrackIndex = 0
    private var loadedSig = 0
    private var queueRestored = false
    private var isVideoMode = false
    private var isShuffle = false
    private var repeatMode = 0 // 0 off, 1 all, 2 one
    private var currentSpeed = 1f
    private var currentPitch = 1f
    private var loopStartMs = -1L
    private var loopEndMs = -1L
    private var currentTab = 0
    private var isSeeking = false
    private var countedKey: String? = null
    private var lastQueueSave = 0L

    // ---- lyrics ----
    private var lyricsLines: List<LyricLine> = emptyList()
    private var lyricsSynced = false
    private var lyricsKey: String? = null
    private var lyricsSheetLines: List<TextView> = emptyList()
    private var lyricsSheetScroll: ScrollView? = null
    private var lyricsSheetIndex = -1

    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            tickProgress()
            progressHandler.postDelayed(this, 250)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlayState()
            if (!isPlaying) saveQueueState()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val idx = exoPlayer.currentMediaItemIndex
            if (mediaItem != null && idx in tracks.indices) {
                currentTrackIndex = idx
                countedKey = null
                clearAbLoop(silent = true)
                val t = tracks[idx]
                if (t.coverBitmap == null) {
                    ensureTrackArtwork(t)
                    if (t.coverBitmap != null) {
                        try {
                            exoPlayer.replaceMediaItem(idx, toMediaItem(t))
                        } catch (_: Exception) {}
                    }
                }
                if (t.trimStartMs > 0 && exoPlayer.currentPosition < t.trimStartMs &&
                    reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
                ) exoPlayer.seekTo(t.trimStartMs)
                bindNowPlaying()
                saveQueueState()

                if (isVideoMode) {
                    val cur = tracks.getOrNull(idx)
                    if (cur != null && cur.videoUrl.isEmpty() && cur.provider.contains("YouTube", true)) {
                        val ytId = YouTubeEngine.extractVideoId(cur.audioUrl)
                        if (ytId != null) {
                            kotlin.concurrent.thread {
                                try {
                                    val streams = YouTubeEngine.resolveStreams(ytId)
                                    val vUrl = streams.videoStreamUrl.ifEmpty { streams.audioStreamUrl }
                                    if (vUrl.isNotEmpty()) {
                                        runOnUiThread {
                                            val playing = tracks.getOrNull(currentTrackIndex)
                                            if (playing != null && playing.key == cur.key) {
                                                val updated = playing.copy(videoUrl = vUrl)
                                                tracks[currentTrackIndex] = updated
                                                val pos = exoPlayer.currentPosition
                                                val wasPlaying = exoPlayer.isPlaying
                                                exoPlayer.setMediaItem(toMediaItem(updated), pos)
                                                exoPlayer.prepare()
                                                if (wasPlaying) exoPlayer.play()
                                            }
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }
            }
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            // Live ICY titles from radio streams
            val t = tracks.getOrNull(currentTrackIndex) ?: return
            if (t.isLocal) return
            val live = mediaMetadata.title?.toString()
            if (!live.isNullOrBlank() && live != titleText.text.toString()) {
                titleText.text = live
                miniTitle.text = live
                if (::landscapeTitleText.isInitialized) landscapeTitleText.text = live
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) {
                updateDurationUi()
                setupVisualizer()
            }
            updatePlayState()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                exoPlayer.pauseAtEndOfMediaItems = false
                refreshSleepLabel(0)
            }
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            AudioFx.ensure(this@MainActivity, audioSessionId)
            releaseVisualizer()
            setupVisualizer()
        }

        override fun onPlayerError(error: PlaybackException) {
            val t = tracks.getOrNull(currentTrackIndex)
            val ytId = t?.let { YouTubeEngine.extractVideoId(it.videoUrl.ifBlank { it.audioUrl }) }
            if (ytId != null) {
                kotlin.concurrent.thread {
                    try {
                        val streams = YouTubeEngine.resolveStreams(ytId)
                        val streamUrl = if (isVideoMode) streams.videoStreamUrl.ifEmpty { streams.audioStreamUrl } else streams.audioStreamUrl.ifEmpty { streams.videoStreamUrl }
                        runOnUiThread {
                            if (streamUrl.isNotEmpty() && !isFinishing) {
                                val cur = tracks.getOrNull(currentTrackIndex)
                                if (cur != null) {
                                    val updated = cur.copy(audioUrl = streamUrl, videoUrl = if (isVideoMode) streamUrl else "")
                                    tracks[currentTrackIndex] = updated
                                    val item = MediaItem.fromUri(Uri.parse(streamUrl))
                                    exoPlayer.setMediaItem(item)
                                    exoPlayer.prepare()
                                    exoPlayer.play()
                                    return@runOnUiThread
                                }
                            }
                            Toast.makeText(this@MainActivity, "No se pudo reproducir esta pista", Toast.LENGTH_SHORT).show()
                            if (exoPlayer.hasNextMediaItem()) {
                                exoPlayer.seekToNextMediaItem()
                                exoPlayer.prepare()
                                exoPlayer.play()
                            }
                        }
                    } catch (_: Exception) {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "No se pudo reproducir esta pista", Toast.LENGTH_SHORT).show()
                            if (exoPlayer.hasNextMediaItem()) {
                                exoPlayer.seekToNextMediaItem()
                                exoPlayer.prepare()
                                exoPlayer.play()
                            }
                        }
                    }
                }
                return
            }
            Toast.makeText(this@MainActivity, "No se pudo reproducir esta pista", Toast.LENGTH_SHORT).show()
            if (exoPlayer.hasNextMediaItem()) {
                exoPlayer.seekToNextMediaItem()
                exoPlayer.prepare()
                exoPlayer.play()
            }
        }
    }

    // ---- activity result launchers ----
    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            loadLocalAudioFromUri(uri)
        }
    }

    private val videoCoverPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            val t = pendingVideoTrack ?: tracks.getOrNull(currentTrackIndex)
            pendingVideoTrack = null

            val trackKey = t?.key ?: "active"
            val destFile = File(filesDir, "cover_vid_${trackKey.hashCode().toString().replace('-', '_')}.mp4")
            showVizHudBadge("Guardando video en bucle...")

            kotlin.concurrent.thread {
                try {
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    val savedUri = Uri.fromFile(destFile).toString()
                    runOnUiThread {
                        if (t != null) {
                            SonoraStore.putString(this@MainActivity, "cover_video_${t.key}", savedUri)
                        }
                        SonoraStore.putString(this@MainActivity, "cover_video_active", savedUri)
                        val cur = tracks.getOrNull(currentTrackIndex)
                        if (cur == null || t == null || cur.key == t.key) {
                            updateStageCoverVideo(savedUri)
                        }
                        showVizHudBadge("¡Video aplicado sin barras negras!")
                        Toast.makeText(this@MainActivity, "Video en bucle aplicado como portada", Toast.LENGTH_SHORT).show()
                    }
                } catch (_: Exception) {
                    val rawUriStr = uri.toString()
                    runOnUiThread {
                        if (t != null) {
                            SonoraStore.putString(this@MainActivity, "cover_video_${t.key}", rawUriStr)
                        }
                        SonoraStore.putString(this@MainActivity, "cover_video_active", rawUriStr)
                        val cur = tracks.getOrNull(currentTrackIndex)
                        if (cur == null || t == null || cur.key == t.key) {
                            updateStageCoverVideo(rawUriStr)
                        }
                        Toast.makeText(this@MainActivity, "Video aplicado", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private val musicoletZipPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            executeMusicoletImport(uri)
        }
    }

    private val gifCoverPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            val t = tracks.getOrNull(currentTrackIndex) ?: return@registerForActivityResult
            t.coverBitmap = null
            t.coverUrl = uri.toString()
            SonoraStore.putString(this, "cover_gif_${t.key.hashCode()}", uri.toString())
            SonoraStore.saveEdit(this, t)
            for (item in (localTracksList + tracks).filter { it.key == t.key }) {
                item.coverBitmap = null
                item.coverUrl = uri.toString()
            }
            val firstFrame = GifImageSupport.decodeFirstFrameFromUri(this, uri)
            if (firstFrame != null) {
                ThemeEngine.adaptFromBitmap(this, firstFrame)
                applyAccentLive()
            }
            bindNowPlaying()
            Toast.makeText(this, "Portada (GIF / Imagen) aplicada con paleta dinámica", Toast.LENGTH_SHORT).show()
        }
    }

    private val m3uPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            importPlaylistFile(uri)
        }
    }

    private val lrcPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            val t = tracks.getOrNull(currentTrackIndex) ?: return@registerForActivityResult
            if (Lyrics.importFrom(this, t.key, uri)) {
                Toast.makeText(this, "Letra importada", Toast.LENGTH_SHORT).show()
                loadLyricsFor(t, force = false)
            } else Toast.makeText(this, "El archivo no contiene una letra válida", Toast.LENGTH_SHORT).show()
        }
    }

    private var pendingExport: List<TrackItem> = emptyList()
    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        if (uri != null) writeM3u(uri, pendingExport)
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        performStorageScan()
        setupVisualizer()
    }

    // ==========================================================
    // LIFECYCLE
    // ==========================================================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PerformanceEngine.init(this)
        GifImageSupport.ensure(this)
        ThemeEngine.apply(this)
        OtaUpdateEngine.checkAndAutoUpdate(this)
        Haptics.enabled = SonoraStore.getBool(this, "haptics", true)
        sortKey = SonoraStore.getString(this, "lib_sort", "title")
        sortAsc = SonoraStore.getBool(this, "lib_sort_asc", true)

        exoPlayer = PlayerHolder.get(this)
        try { startService(Intent(this, PlaybackService::class.java)) } catch (_: Exception) {}
        try {
            val sToken = androidx.media3.session.SessionToken(this, android.content.ComponentName(this, PlaybackService::class.java))
            androidx.media3.session.MediaController.Builder(this, sToken).buildAsync()
        } catch (_: Exception) {}
        applyPlayerSettings()
        exoPlayer.addListener(playerListener)
        AudioFx.ensure(this, exoPlayer.audioSessionId)
        playlists.putAll(SonoraStore.loadPlaylists(this))

        showStageCover = SonoraStore.getBool(this, "stage_show_cover", true)
        isStageCoverExpanded = SonoraStore.getBool(this, "stage_cover_expanded_v2", true)
        stageMediaXL = SonoraStore.getBool(this, "stage_media_xl", false)
        showStageViz = SonoraStore.getBool(this, "stage_show_viz", true)
        showStageDoki = SonoraStore.getBool(this, "stage_show_doki", true)
        showStageLyrics = SonoraStore.getBool(this, "stage_show_lyrics", true)
        stageRightMode = SonoraStore.getInt(this, "stage_right_mode", 0)
        isLandscapeMode = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        buildUi()
        updateStageLayout()

        val dokiPrefs = getSharedPreferences("sonora_prefs", Context.MODE_PRIVATE)
        selectedDokiId = dokiPrefs.getString("doki_id", "natsuki") ?: "natsuki"
        selectedDokiPose = dokiPrefs.getString("doki_pose", "casual") ?: "casual"
        selectedDokiExpr = dokiPrefs.getString("doki_expr_v2", "shimeji") ?: "shimeji"
        updateDokiViews()
        applyOrientationLayout(isLandscapeMode)
        applyAccentLive()

        onBackPressedDispatcher.addCallback(this) {
            when {
                currentTab == 1 && libGroup != null -> { libGroup = null; refreshLibrary(scrollTop = true) }
                currentTab != 0 -> switchTab(0)
                else -> { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
            }
        }

        showEmptyPlayer()
        switchTab(0)
        checkAndRequestPermissions()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val land = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (land != isLandscapeMode) {
            isLandscapeMode = land
            applyOrientationLayout(land)
        }
    }

    override fun onResume() {
        super.onResume()
        progressHandler.removeCallbacks(progressRunnable)
        progressHandler.post(progressRunnable)
        SleepTimer.onTick = { rem -> runOnUiThread { refreshSleepLabel(rem) } }
        refreshSleepLabel(if (SleepTimer.active) SleepTimer.endAt - System.currentTimeMillis() else 0)
        // favourites may have been toggled from the widget
        for (t in tracks) t.isFavorite = SonoraStore.isFavorite(this, t.key)
        tracks.getOrNull(currentTrackIndex)?.let {
            val res = if (it.isFavorite) R.drawable.ic_heart_filled else R.drawable.ic_heart
            favBtn.setImageResource(res)
            if (::landscapeFavBtn.isInitialized) landscapeFavBtn.setImageResource(res)
        }
        if (queueRestored) { refreshLibrary(); refreshPlaylists() }
        keepScreenOnIfNeeded()
    }

    override fun onPause() {
        super.onPause()
        progressHandler.removeCallbacks(progressRunnable)
        SleepTimer.onTick = null
        saveQueueState()
    }

    override fun onDestroy() {
        super.onDestroy()
        stageCoverVideoPlayer?.release()
        stageCoverVideoPlayer = null
        exoPlayer.removeListener(playerListener)
        if (::playerView.isInitialized) playerView.player = null
        releaseVisualizer()
        // The player, effects and service intentionally outlive the Activity.
    }

    private fun applyPlayerSettings() {
        isShuffle = SonoraStore.getBool(this, "shuffle", false)
        repeatMode = SonoraStore.getInt(this, "repeat", 0)
        currentSpeed = SonoraStore.getFloat(this, "speed", 1f)
        currentPitch = SonoraStore.getFloat(this, "pitch", 1f)
        exoPlayer.shuffleModeEnabled = isShuffle
        exoPlayer.repeatMode = when (repeatMode) {
            1 -> Player.REPEAT_MODE_ALL
            2 -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        exoPlayer.playbackParameters = PlaybackParameters(currentSpeed, currentPitch)
        exoPlayer.skipSilenceEnabled = SonoraStore.getBool(this, "skip_silence", false)
    }

    private fun keepScreenOnIfNeeded() {
        val on = SonoraStore.getBool(this, "keep_screen", false) && currentTab == 0
        if (on) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // ==========================================================
    // PERMISSIONS & VISUALIZER
    // ==========================================================
    private fun checkAndRequestPermissions() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.READ_MEDIA_AUDIO)
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        perms.add(Manifest.permission.RECORD_AUDIO)
        val missing = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
        else { performStorageScan(); setupVisualizer() }
    }

    private fun hasAudioPermission(): Boolean {
        val p = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
    }

    private fun setupVisualizer() {
        val sessionId = exoPlayer.audioSessionId
        if (sessionId == 0 || audioVisualizer != null) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        try {
            audioVisualizer = Visualizer(sessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                        visualizerView.updateWaveform(waveform)
                        if (::visualizerStripView.isInitialized) visualizerStripView.updateWaveform(waveform)
                    }
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        visualizerView.updateFft(fft)
                        if (::visualizerStripView.isInitialized) visualizerStripView.updateFft(fft)
                        if (::dokiSpriteView.isInitialized) {
                            dokiSpriteView.audioEnergy = visualizerView.currentVocalEnergy()
                        }
                    }
                }, Visualizer.getMaxCaptureRate() / 2, true, true)
                enabled = true
            }
        } catch (e: Exception) {
            audioVisualizer = null
        }
    }

    private fun releaseVisualizer() {
        try { audioVisualizer?.enabled = false; audioVisualizer?.release() } catch (_: Exception) {}
        audioVisualizer = null
    }

    // ==========================================================
    // UI SCAFFOLD
    // ==========================================================
    private fun buildUi() {
        mainRootLayout = RelativeLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.parseColor(Pal.BG_TOP),
                    Color.parseColor(Pal.BG_MID),
                    Color.parseColor(Pal.BG_BOTTOM)
                )
            )
        }

        topHeader = buildHeader()
        bottomNavBar = buildNav()
        val savedNavPos = SonoraStore.getString(this, "tab_bar_position", "bottom")
        applyNavBarPosition(savedNavPos)
        miniPlayerRoot = buildMiniPlayer()

        mainContentFrame = FrameLayout(this).apply {
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT
            ).apply {
                addRule(RelativeLayout.BELOW, topHeader.id)
                addRule(RelativeLayout.ABOVE, bottomNavBar.id)
            }
        }

        playerTabContainer = buildPlayerTab()
        localFilesTabContainer = buildLibraryTab().apply { visibility = View.GONE }
        playlistTabContainer = buildPlaylistTab().apply { visibility = View.GONE }
        onlineTabContainer = buildOnlineTab().apply { visibility = View.GONE }
        soundTabContainer = buildSoundTab().apply { visibility = View.GONE }
        widgetTabContainer = buildWidgetCustomizerTab().apply { visibility = View.GONE }

        mainContentFrame.addView(playerTabContainer)
        mainContentFrame.addView(localFilesTabContainer)
        mainContentFrame.addView(playlistTabContainer)
        mainContentFrame.addView(onlineTabContainer)
        mainContentFrame.addView(soundTabContainer)
        mainContentFrame.addView(widgetTabContainer)

        shimejiFullScreenOverlay = FrameLayout(this).apply {
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
            )
            clipChildren = false
            clipToPadding = false
        }

        glassBannerView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E60B1220"))
                cornerRadius = dpf(20f)
                setStroke(dp(1), Color.parseColor("#44FFFFFF"))
            }
            elevation = dpf(10f)
            visibility = View.GONE
            layoutParams = RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.BELOW, topHeader.id)
                addRule(RelativeLayout.CENTER_HORIZONTAL)
                topMargin = dp(8)
                marginStart = dp(16)
                marginEnd = dp(16)
            }
        }
        val glassSearchIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_search)
            setColorFilter(Color.parseColor(Pal.ACCENT))
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(8) }
        }
        glassBannerText = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        glassBannerCancelBtn = TextView(this).apply {
            text = "Cancelar"
            textSize = 11.5f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setTextColor(Color.parseColor(Pal.ACCENT))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#26FFFFFF"))
                cornerRadius = dpf(12f)
            }
            setPadding(dp(10), dp(4), dp(10), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(10) }
            isClickable = true
            isFocusable = true
            addPressFeedback(0.95f)
        }
        glassBannerView.addView(glassSearchIcon)
        glassBannerView.addView(glassBannerText)
        glassBannerView.addView(glassBannerCancelBtn)

        mainRootLayout.addView(topHeader)
        mainRootLayout.addView(bottomNavBar)
        mainRootLayout.addView(mainContentFrame)
        mainRootLayout.addView(shimejiFullScreenOverlay)
        mainRootLayout.addView(miniPlayerRoot)
        mainRootLayout.addView(glassBannerView)
        setContentView(mainRootLayout)

        ViewCompat.setOnApplyWindowInsetsListener(mainRootLayout) { _, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            topHeader.setPadding(dp(20), dp(10) + b.top, dp(12), dp(8))
            bottomNavBar.setPadding(dp(6), dp(6), dp(6), dp(6) + b.bottom)
            if (::landscapeBottomBar.isInitialized) {
                landscapeBottomBar.setPadding(dp(16) + b.left, dp(6), dp(16) + b.right, dp(6) + b.bottom)
            }
            insets
        }
    }

    private fun showGlassSearchBanner(message: String, onCancel: (() -> Unit)? = null) {
        runOnUiThread {
            isSearchCancelled = false
            glassBannerText.text = message
            glassBannerCancelBtn.visibility = if (onCancel != null) View.VISIBLE else View.GONE
            glassBannerCancelBtn.setOnClickListener {
                isSearchCancelled = true
                activeSearchThread?.interrupt()
                onCancel?.invoke()
                hideGlassSearchBanner()
                Toast.makeText(this@MainActivity, "Búsqueda cancelada", Toast.LENGTH_SHORT).show()
            }
            glassBannerView.alpha = 0f
            glassBannerView.visibility = View.VISIBLE
            glassBannerView.animate().alpha(1f).setDuration(220).start()
        }
    }

    private fun hideGlassSearchBanner() {
        runOnUiThread {
            if (::glassBannerView.isInitialized && glassBannerView.visibility == View.VISIBLE) {
                glassBannerView.animate().alpha(0f).setDuration(200).withEndAction {
                    glassBannerView.visibility = View.GONE
                }.start()
            }
        }
    }

    private fun buildHeader(): LinearLayout {
        val h = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                colors = intArrayOf(Color.parseColor("#E6121526"), Color.parseColor("#E6090A12"))
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                setStroke(dp(1), Color.parseColor("#2238BDF8"))
            }
            setPadding(dp(18), dp(10), dp(12), dp(8))
            elevation = dpf(6f)
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply { addRule(RelativeLayout.ALIGN_PARENT_TOP) }
        }
        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            isClickable = true
            setOnClickListener { showLiveNotificationPanelSheet() }
        }
        headerTitle = TextView(this).apply {
            text = "DDMC"
            textSize = 20f
            letterSpacing = -0.02f
            setTextColor(Color.WHITE)
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
        }
        val gridPill = TextView(this).apply {
            text = "⊞ SNAP"
            textSize = 9.5f
            setTextColor(Color.parseColor("#38BDF8"))
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            setPadding(dp(6), dp(2), dp(6), dp(2))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1F38BDF8"))
                cornerRadius = dpf(6f)
                setStroke(dp(1), Color.parseColor("#4038BDF8"))
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(8)
            }
            isClickable = true
            setOnClickListener { cycleGridMode() }
        }
        titleBox.addView(headerTitle)
        titleBox.addView(gridPill)

        fun headerIcon(res: Int, onClick: () -> Unit) = ImageView(this).apply {
            setImageResource(res)
            setColorFilter(Color.parseColor("#CBD5E1"))
            val p = dp(8)
            setPadding(p, p, p, p)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#14FFFFFF"))
            }
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                marginStart = dp(4)
                marginEnd = dp(2)
            }
            isClickable = true
            setOnClickListener { onClick() }
            addPressFeedback(0.92f)
        }
        val avatarSize = dp(34)
        topDokiAvatar = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(avatarSize, avatarSize).apply { marginStart = dp(6); marginEnd = dp(4) }
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(2), dp(2), dp(2), dp(2))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#1838BDF8"))
                setStroke(dp(1), Color.parseColor("#4038BDF8"))
            }
            isClickable = true
            setOnClickListener { showDokiAvatarPicker() }
            addPressFeedback(0.92f)
        }
        h.addView(titleBox)
        h.addView(headerIcon(R.drawable.ic_widgets) { cycleGridMode() })
        h.addView(headerIcon(R.drawable.ic_search) { openLibrarySearch() })
        h.addView(headerIcon(R.drawable.ic_list_music) { showQueueSheet() })
        h.addView(topDokiAvatar)
        h.addView(headerIcon(R.drawable.ic_more) { showGlobalMenu() })
        return h
    }

    private fun openFullSettings() {
        MusicoletSettingsCenter.showMainSettingsDialog(
            activity = this,
            totalTracksCount = localTracksList.size,
            onRescanLibrary = { performStorageScan(showToast = true) },
            onOpenWidgetTab = { switchTab(5) },
            onOpenEqualizerTab = { switchTab(4) },
            onOpenDokiPicker = { showDokiAvatarPicker() },
            onRefreshUi = {
                bindNowPlaying()
                refreshLibrary()
                refreshPlaylists()
                SonoraWidgetProvider.updateAllWidgets(this)
            }
        )
    }

    private fun setDataSourceSafely(player: MediaPlayer, uriString: String) {
        val uri = Uri.parse(uriString)
        if (uri.scheme == "file") {
            val f = File(uri.path ?: "")
            if (f.exists()) {
                player.setDataSource(f.absolutePath)
                return
            }
        }
        if (uri.scheme == "content") {
            try {
                contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    return
                }
            } catch (_: Exception) {}
        }
        player.setDataSource(this, uri)
    }

    private fun adjustVideoCropMatrix(textureView: TextureView, videoWidth: Int, videoHeight: Int) {
        if (videoWidth > 0 && videoHeight > 0) {
            lastCoverVideoWidth = videoWidth
            lastCoverVideoHeight = videoHeight
        }
        val vW = if (videoWidth > 0) videoWidth else lastCoverVideoWidth
        val vH = if (videoHeight > 0) videoHeight else lastCoverVideoHeight
        if (vW <= 0 || vH <= 0) return

        val viewW = textureView.width.toFloat()
        val viewH = textureView.height.toFloat()
        if (viewW <= 0f || viewH <= 0f) return

        val viewRatio = viewW / viewH
        val videoRatio = vW.toFloat() / vH.toFloat()

        var scaleX = 1.0f
        var scaleY = 1.0f

        // Eliminate black bars on any video resolution via Center-Crop
        if (videoRatio > viewRatio) {
            scaleX = videoRatio / viewRatio
        } else {
            scaleY = viewRatio / videoRatio
        }

        val matrix = Matrix()
        matrix.setScale(scaleX, scaleY, viewW / 2f, viewH / 2f)
        textureView.setTransform(matrix)
    }

    fun updateStageCoverVideo(uriString: String?) {
        if (uriString.isNullOrEmpty()) {
            if (currentCoverVideoUri != null) {
                currentCoverVideoUri = null
                try {
                    stageCoverVideoPlayer?.stop()
                    stageCoverVideoPlayer?.reset()
                } catch (_: Exception) {}
                stageCoverVideoView?.visibility = View.GONE
                if (::stageExpandedCoverImage.isInitialized) {
                    stageExpandedCoverImage.visibility = View.VISIBLE
                }
            }
            return
        }
        if (currentCoverVideoUri == uriString && stageCoverVideoPlayer != null && stageCoverVideoView?.visibility == View.VISIBLE) {
            return
        }
        currentCoverVideoUri = uriString
        if (!::stageExpandedCoverCard.isInitialized) return

        if (stageCoverVideoView == null) {
            stageCoverVideoView = TextureView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
                )
                addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                    adjustVideoCropMatrix(this, lastCoverVideoWidth, lastCoverVideoHeight)
                }
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                        stageCoverSurface?.release()
                        stageCoverSurface = Surface(st)
                        startLoopingVideo(stageCoverSurface, currentCoverVideoUri)
                    }
                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                        adjustVideoCropMatrix(this@apply, lastCoverVideoWidth, lastCoverVideoHeight)
                    }
                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        try {
                            stageCoverVideoPlayer?.stop()
                            stageCoverVideoPlayer?.release()
                        } catch (_: Exception) {}
                        stageCoverVideoPlayer = null
                        stageCoverSurface?.release()
                        stageCoverSurface = null
                        return true
                    }
                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                }
            }
            stageExpandedCoverCard.addView(stageCoverVideoView)
        }
        stageCoverVideoView?.visibility = View.VISIBLE
        if (::stageExpandedCoverImage.isInitialized) {
            stageExpandedCoverImage.visibility = View.GONE
        }
        stageCoverVideoView?.let { v ->
            if (v.isAvailable && v.surfaceTexture != null) {
                if (stageCoverSurface == null) {
                    stageCoverSurface = Surface(v.surfaceTexture)
                }
                startLoopingVideo(stageCoverSurface, uriString)
            }
        }
    }

    private fun startLoopingVideo(surface: Surface?, uriString: String?) {
        if (surface == null || uriString.isNullOrEmpty()) return
        try {
            val player = stageCoverVideoPlayer ?: MediaPlayer().also { stageCoverVideoPlayer = it }
            player.reset()
            player.setSurface(surface)
            setDataSourceSafely(player, uriString)
            player.isLooping = true
            player.setVolume(0f, 0f) // Muted silent video cover
            player.setOnVideoSizeChangedListener { _, w, h ->
                lastCoverVideoWidth = w
                lastCoverVideoHeight = h
                stageCoverVideoView?.let { adjustVideoCropMatrix(it, w, h) }
            }
            player.setOnPreparedListener { mp ->
                try {
                    mp.setVolume(0f, 0f)
                    val tracks = mp.trackInfo
                    for (i in tracks.indices) {
                        if (tracks[i].trackType == MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO) {
                            try { mp.deselectTrack(i) } catch (_: Exception) {}
                        }
                    }
                } catch (_: Exception) {}
                mp.start()
                try { mp.setVolume(0f, 0f) } catch (_: Exception) {}
                val w = mp.videoWidth
                val h = mp.videoHeight
                if (w > 0 && h > 0) {
                    lastCoverVideoWidth = w
                    lastCoverVideoHeight = h
                    stageCoverVideoView?.let { adjustVideoCropMatrix(it, w, h) }
                }
            }
            player.setOnSeekCompleteListener { mp ->
                try { mp.setVolume(0f, 0f) } catch (_: Exception) {}
            }
            player.setOnInfoListener { mp, _, _ ->
                try { mp.setVolume(0f, 0f) } catch (_: Exception) {}
                false
            }
            player.setOnCompletionListener { mp ->
                try {
                    mp.setVolume(0f, 0f)
                    mp.seekTo(0)
                    mp.start()
                    mp.setVolume(0f, 0f)
                } catch (_: Exception) {}
            }
            player.setOnErrorListener { _, _, _ ->
                runOnUiThread {
                    stageCoverVideoView?.visibility = View.GONE
                    if (::stageExpandedCoverImage.isInitialized) stageExpandedCoverImage.visibility = View.VISIBLE
                }
                true
            }
            player.prepareAsync()
        } catch (_: Exception) {
            stageCoverVideoView?.visibility = View.GONE
            if (::stageExpandedCoverImage.isInitialized) {
                stageExpandedCoverImage.visibility = View.VISIBLE
            }
        }
    }

    fun applyNavBarPosition(pos: String) {
        currentNavPosition = pos
        SonoraStore.putString(this, "tab_bar_position", pos)

        if (!::bottomNavBar.isInitialized || !::mainContentFrame.isInitialized || !::topHeader.isInitialized) return

        val insetsBottom = window.decorView.rootWindowInsets?.getInsets(android.view.WindowInsets.Type.systemBars())?.bottom ?: 0

        val navLp = RelativeLayout.LayoutParams(
            if (pos == "left" || pos == "right") dp(66) else RelativeLayout.LayoutParams.MATCH_PARENT,
            if (pos == "left" || pos == "right") RelativeLayout.LayoutParams.MATCH_PARENT else RelativeLayout.LayoutParams.WRAP_CONTENT
        )

        val contentLp = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        )

        when (pos) {
            "top" -> {
                bottomNavBar.orientation = LinearLayout.HORIZONTAL
                bottomNavBar.setPadding(dp(6), dp(4), dp(6), dp(4))
                navLp.addRule(RelativeLayout.BELOW, topHeader.id)
                contentLp.addRule(RelativeLayout.BELOW, bottomNavBar.id)
                contentLp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                for (item in navItemContainers) {
                    item.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
            }
            "left" -> {
                bottomNavBar.orientation = LinearLayout.VERTICAL
                bottomNavBar.setPadding(dp(2), dp(8), dp(2), dp(8) + insetsBottom)
                navLp.addRule(RelativeLayout.ALIGN_PARENT_LEFT)
                navLp.addRule(RelativeLayout.BELOW, topHeader.id)
                navLp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                contentLp.addRule(RelativeLayout.RIGHT_OF, bottomNavBar.id)
                contentLp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                contentLp.addRule(RelativeLayout.BELOW, topHeader.id)
                contentLp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                for (item in navItemContainers) {
                    item.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
                }
            }
            "right" -> {
                bottomNavBar.orientation = LinearLayout.VERTICAL
                bottomNavBar.setPadding(dp(2), dp(8), dp(2), dp(8) + insetsBottom)
                navLp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                navLp.addRule(RelativeLayout.BELOW, topHeader.id)
                navLp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                contentLp.addRule(RelativeLayout.LEFT_OF, bottomNavBar.id)
                contentLp.addRule(RelativeLayout.ALIGN_PARENT_LEFT)
                contentLp.addRule(RelativeLayout.BELOW, topHeader.id)
                contentLp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                for (item in navItemContainers) {
                    item.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
                }
            }
            else -> { // "bottom"
                bottomNavBar.orientation = LinearLayout.HORIZONTAL
                bottomNavBar.setPadding(dp(6), dp(4), dp(6), dp(6) + insetsBottom)
                navLp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                contentLp.addRule(RelativeLayout.BELOW, topHeader.id)
                contentLp.addRule(RelativeLayout.ABOVE, bottomNavBar.id)
                for (item in navItemContainers) {
                    item.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
            }
        }

        bottomNavBar.layoutParams = navLp
        mainContentFrame.layoutParams = contentLp
        bottomNavBar.requestLayout()
        mainContentFrame.requestLayout()
    }

    fun showTabBarPositionDialog() {
        val options = arrayOf(
            "Abajo (Predeterminado)",
            "Arriba",
            "Izquierda (Barra lateral)",
            "Derecha (Barra lateral)"
        )
        val posKeys = arrayOf("bottom", "top", "left", "right")
        val currentIdx = posKeys.indexOf(currentNavPosition).coerceAtLeast(0)

        android.app.AlertDialog.Builder(this)
            .setTitle("Posición de Pestañas")
            .setSingleChoiceItems(options, currentIdx) { d, which ->
                val choice = posKeys[which]
                applyNavBarPosition(choice)
                d.dismiss()
                Toast.makeText(this, "Pestañas ubicadas: ${options[which]}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    fun applyVizSlot(slot: Int) {
        val mode = SonoraStore.getInt(this, "viz_slot_${slot}_mode", when(slot) { 1 -> 0; 2 -> 1; 3 -> 2; else -> 0 })
        val h = SonoraStore.getInt(this, "viz_slot_${slot}_h", when(slot) { 1 -> 76; 2 -> 96; 3 -> 68; else -> 140 })
        visualizerStripView.visualMode = mode
        vizDeckCard.layoutParams.height = dp(h)
        vizDeckCard.requestLayout()
        SonoraStore.putInt(this, "active_viz_slot", slot)
        Toast.makeText(this, "Cargada Ranura $slot", Toast.LENGTH_SHORT).show()
    }

    fun saveCurrentToVizSlot(slot: Int) {
        val currentMode = visualizerStripView.visualMode
        val currentHeight = (vizDeckCard.height / resources.displayMetrics.density).toInt().coerceAtLeast(40)
        SonoraStore.putInt(this, "viz_slot_${slot}_mode", currentMode)
        SonoraStore.putInt(this, "viz_slot_${slot}_h", currentHeight)
        SonoraStore.putInt(this, "active_viz_slot", slot)
        Toast.makeText(this, "Guardado en Ranura $slot", Toast.LENGTH_SHORT).show()
    }

    private var vizHudBadge: TextView? = null

    private fun safeDetach(v: View?) {
        v ?: return
        (v.parent as? ViewGroup)?.removeView(v)
    }

    private fun ensureWindowFrames() {
        if (!::coverWindowFrame.isInitialized) {
            coverWindowFrame = StageWindowFrame(
                context = this,
                windowId = "cover",
                title = "Portada & Video",
                iconRes = R.drawable.ic_video,
                onMinimizeClick = {
                    if (fullscreenWindowId != null) {
                        fullscreenWindowId = null
                        showVizHudBadge("Cuadrícula Restaurada")
                    } else {
                        isCoverMinimizedPip = !isCoverMinimizedPip
                        showVizHudBadge(if (isCoverMinimizedPip) "Portada minimizada a PIP" else "Portada restaurada")
                    }
                    reorderStageContainers()
                },
                onMaximizeClick = {
                    fullscreenWindowId = if (fullscreenWindowId == "cover") null else "cover"
                    showVizHudBadge(if (fullscreenWindowId != null) "Pantalla Completa: Portada (Desliza para cambiar)" else "Cuadrícula Restaurada")
                    reorderStageContainers()
                },
                onMoreClick = { showGifCoverOptionsSheet() }
            ).apply {
                onScaleUpdate = { _, label -> showVizHudBadge("Portada: $label") }
            }
        }
        if (!::vizWindowFrame.isInitialized) {
            vizWindowFrame = StageWindowFrame(
                context = this,
                windowId = "viz",
                title = "Visualizador Neón",
                iconRes = R.drawable.ic_waveform,
                onMinimizeClick = {
                    if (fullscreenWindowId != null) {
                        fullscreenWindowId = null
                        showVizHudBadge("Cuadrícula Restaurada")
                        reorderStageContainers()
                    } else {
                        showVizHudBadge(if (vizWindowFrame.isWindowMinimized) "Visualizador minimizado" else "Visualizador restaurado")
                    }
                },
                onMaximizeClick = {
                    fullscreenWindowId = if (fullscreenWindowId == "viz") null else "viz"
                    showVizHudBadge(if (fullscreenWindowId != null) "Pantalla Completa: Visualizador (Desliza para cambiar)" else "Cuadrícula Restaurada")
                    reorderStageContainers()
                },
                onMoreClick = { cycleVisualizerMode() }
            ).apply {
                onScaleUpdate = { _, label -> showVizHudBadge("Visualizador: $label") }
            }
        }
        if (!::lyricsWindowFrame.isInitialized) {
            lyricsWindowFrame = StageWindowFrame(
                context = this,
                windowId = "lyrics",
                title = "Letras en Vivo",
                iconRes = R.drawable.ic_music,
                onMinimizeClick = {
                    if (fullscreenWindowId != null) {
                        fullscreenWindowId = null
                        showVizHudBadge("Cuadrícula Restaurada")
                        reorderStageContainers()
                    } else {
                        showVizHudBadge(if (lyricsWindowFrame.isWindowMinimized) "Letras minimizadas" else "Letras restauradas")
                    }
                },
                onMaximizeClick = {
                    fullscreenWindowId = if (fullscreenWindowId == "lyrics") null else "lyrics"
                    showVizHudBadge(if (fullscreenWindowId != null) "Pantalla Completa: Letras (Desliza para cambiar)" else "Cuadrícula Restaurada")
                    reorderStageContainers()
                },
                onMoreClick = { showLyricsSheet() }
            ).apply {
                onScaleUpdate = { _, label -> showVizHudBadge("Letras: $label") }
            }
        }
        if (!::dokiWindowFrame.isInitialized) {
            dokiWindowFrame = StageWindowFrame(
                context = this,
                windowId = "doki",
                title = "Doki Companion",
                iconRes = R.drawable.ic_sparkles,
                onMinimizeClick = {
                    if (fullscreenWindowId != null) {
                        fullscreenWindowId = null
                        showVizHudBadge("Cuadrícula Restaurada")
                        reorderStageContainers()
                    } else {
                        showVizHudBadge(if (dokiWindowFrame.isWindowMinimized) "Doki minimizado" else "Doki restaurado")
                    }
                },
                onMaximizeClick = {
                    fullscreenWindowId = if (fullscreenWindowId == "doki") null else "doki"
                    showVizHudBadge(if (fullscreenWindowId != null) "Pantalla Completa: Doki (Desliza para cambiar)" else "Cuadrícula Restaurada")
                    reorderStageContainers()
                },
                onMoreClick = { showQuickDokiSwitcherSheet() }
            ).apply {
                onScaleUpdate = { _, label -> showVizHudBadge("Doki: $label") }
            }
        }
    }

    fun cycleFullscreenWindow(forward: Boolean) {
        val windowIds = listOf("cover", "viz", "lyrics", "doki")
        val curIdx = windowIds.indexOf(fullscreenWindowId)
        val nextIdx = if (curIdx < 0) {
            if (forward) 0 else windowIds.lastIndex
        } else {
            if (forward) (curIdx + 1) % windowIds.size else (curIdx - 1 + windowIds.size) % windowIds.size
        }
        fullscreenWindowId = windowIds[nextIdx]
        val label = when (fullscreenWindowId) {
            "cover" -> "Portada & Video"
            "viz" -> "Visualizador Neón"
            "lyrics" -> "Letras en Vivo"
            "doki" -> "Doki Companion"
            else -> "Cuadrícula"
        }
        showVizHudBadge("Ventana: $label")
        reorderStageContainers()
    }

    fun cycleGridMode() {
        val next = (SonoraStore.getInt(this, "stage_grid_snap_mode", 0) + 1) % 4
        SonoraStore.putInt(this, "stage_grid_snap_mode", next)
        fullscreenWindowId = null
        val names = arrayOf("Cuadrícula 2x2 Quad", "Dividido 50/50", "Bento PIP Editor", "Clásico Apilado")
        showVizHudBadge("Diseño: ${names[next]}")
        reorderStageContainers()
    }

    private fun buildLandscapeWindowTabBar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(2), dp(6), dp(4))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E60D101C"))
                cornerRadius = dpf(8f)
                setStroke(dp(1), Color.parseColor("#2538BDF8"))
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32)).apply {
                bottomMargin = dp(4)
            }
        }

        fun makeTab(label: String, winId: String?) = TextView(this).apply {
            text = label
            textSize = 10.5f
            val isActive = (winId == fullscreenWindowId)
            setTextColor(if (isActive) Color.WHITE else Color.parseColor("#94A3B8"))
            setTypeface(Typeface.create("sans-serif-medium", if (isActive) Typeface.BOLD else Typeface.NORMAL))
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = GradientDrawable().apply {
                setColor(if (isActive) Color.parseColor("#2638BDF8") else Color.TRANSPARENT)
                cornerRadius = dpf(6f)
                if (isActive) setStroke(dp(1), Color.parseColor("#38BDF8"))
            }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            }
            isClickable = true
            setOnClickListener {
                fullscreenWindowId = winId
                showVizHudBadge(if (winId != null) "Ventana: $label" else "Vista de Cuadrícula")
                reorderStageContainers()
            }
        }

        bar.addView(makeTab("Portada", "cover"))
        bar.addView(makeTab("Visualizador", "viz"))
        bar.addView(makeTab("Letras", "lyrics"))
        bar.addView(makeTab("Doki", "doki"))
        bar.addView(makeTab("Cuadrícula", null))
        return bar
    }

    private fun reorderStageContainers() {
        if (!::unifiedStageBlock.isInitialized) return
        ensureWindowFrames()

        // Smooth home screen launcher spring physics transition
        if (!MotionTokens.isReducedMotion(this) && unifiedStageBlock.isAttachedToWindow) {
            val transition = AutoTransition().apply {
                duration = 320
                interpolator = PathInterpolator(0.22f, 1f, 0.36f, 1f)
            }
            TransitionManager.beginDelayedTransition(unifiedStageBlock, transition)
        }

        safeDetach(stageExpandedCoverCard)
        safeDetach(stageVideoCard)
        safeDetach(vizDeckCard)
        safeDetach(stageLyricsScroll)
        safeDetach(dokiLeftCol)

        stageExpandedCoverCard.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        stageVideoCard.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        vizDeckCard.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        stageLyricsScroll.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        dokiLeftCol.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )

        // Reset translations and scaling
        coverWindowFrame.translationX = 0f; coverWindowFrame.translationY = 0f
        vizWindowFrame.translationX = 0f; vizWindowFrame.translationY = 0f
        lyricsWindowFrame.translationX = 0f; lyricsWindowFrame.translationY = 0f
        dokiWindowFrame.translationX = 0f; dokiWindowFrame.translationY = 0f

        // Wrap actual views into window frames
        val coverBox = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(stageExpandedCoverCard)
            addView(stageVideoCard)
        }
        coverWindowFrame.setContent(coverBox)
        vizWindowFrame.setContent(vizDeckCard)
        lyricsWindowFrame.setContent(stageLyricsScroll)
        dokiWindowFrame.setContent(dokiLeftCol)

        unifiedStageBlock.removeAllViews()

        if (isLandscapeMode) {
            unifiedStageBlock.addView(buildLandscapeWindowTabBar())
        }

        // 1. FULLSCREEN SINGLE WINDOW MODE
        if (fullscreenWindowId != null) {
            val targetFrame = when (fullscreenWindowId) {
                "cover" -> coverWindowFrame
                "viz" -> vizWindowFrame
                "lyrics" -> lyricsWindowFrame
                "doki" -> dokiWindowFrame
                else -> null
            }
            if (targetFrame != null) {
                targetFrame.layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
                ).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }
                unifiedStageBlock.addView(targetFrame)
                updateStageLayout()
                return
            }
        }

        // 2. MULTI-WINDOW GRID MODES
        val currentGrid = SonoraStore.getInt(this, "stage_grid_snap_mode", 0)
        when (currentGrid) {
            0 -> { // 2x2 QUAD SNAP (Windows 4-corner snap)
                val row1 = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                        bottomMargin = dp(4)
                    }
                }
                val row2 = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
                }
                coverWindowFrame.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { marginEnd = dp(4) }
                dokiWindowFrame.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                vizWindowFrame.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { marginEnd = dp(4) }
                lyricsWindowFrame.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)

                row1.addView(coverWindowFrame)
                row1.addView(dokiWindowFrame)
                row2.addView(vizWindowFrame)
                row2.addView(lyricsWindowFrame)

                unifiedStageBlock.addView(row1)
                unifiedStageBlock.addView(row2)
            }
            1 -> { // SPLIT 50/50 (Dividido Izquierda / Derecha)
                val splitRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
                }
                val leftCol = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { marginEnd = dp(4) }
                }
                val rightCol = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                }
                coverWindowFrame.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.55f).apply { bottomMargin = dp(4) }
                vizWindowFrame.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.45f)
                lyricsWindowFrame.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.60f).apply { bottomMargin = dp(4) }
                dokiWindowFrame.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.40f)

                leftCol.addView(coverWindowFrame)
                leftCol.addView(vizWindowFrame)
                rightCol.addView(lyricsWindowFrame)
                rightCol.addView(dokiWindowFrame)

                splitRow.addView(leftCol)
                splitRow.addView(rightCol)
                unifiedStageBlock.addView(splitRow)
            }
            2 -> { // BENTO PIP (Video minimizado en monitor PIP en la esquina, visualizador y letras amplios)
                val topRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(140)).apply { bottomMargin = dp(4) }
                }
                dokiWindowFrame.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.55f).apply { marginEnd = dp(4) }
                coverWindowFrame.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.45f)
                topRow.addView(dokiWindowFrame)
                topRow.addView(coverWindowFrame)

                vizWindowFrame.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(86)).apply { bottomMargin = dp(4) }
                lyricsWindowFrame.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)

                unifiedStageBlock.addView(topRow)
                unifiedStageBlock.addView(vizWindowFrame)
                unifiedStageBlock.addView(lyricsWindowFrame)
            }
            else -> { // CLASICO APILADO
                val coverH = if (isCoverMinimizedPip) dp(90) else dp(SonoraStore.getInt(this, "player_cover_height_dp", 228))
                coverWindowFrame.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, coverH).apply { bottomMargin = dp(4) }
                unifiedStageBlock.addView(coverWindowFrame)

                val bottomCols = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
                }
                dokiWindowFrame.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.39f).apply { marginEnd = dp(4) }
                val rightStack = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.61f)
                }
                lyricsWindowFrame.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { bottomMargin = dp(4) }
                vizWindowFrame.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(76))
                rightStack.addView(lyricsWindowFrame)
                rightStack.addView(vizWindowFrame)

                bottomCols.addView(dokiWindowFrame)
                bottomCols.addView(rightStack)
                unifiedStageBlock.addView(bottomCols)
            }
        }
        updateStageLayout()
    }

    fun showVizHudBadge(textMsg: String) {
        runOnUiThread {
            if (vizHudBadge == null && ::mainRootLayout.isInitialized) {
                vizHudBadge = TextView(this).apply {
                    textSize = 12.5f
                    setTextColor(Color.WHITE)
                    setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
                    setPadding(dp(14), dp(8), dp(14), dp(8))
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#E61E1B2E"))
                        cornerRadius = dpf(18f)
                        setStroke(dp(1), Color.parseColor(Pal.ACCENT))
                    }
                    elevation = dpf(8f)
                    val lp = RelativeLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        addRule(RelativeLayout.CENTER_HORIZONTAL)
                        addRule(RelativeLayout.ALIGN_PARENT_TOP)
                        topMargin = dp(64)
                    }
                    layoutParams = lp
                }
                mainRootLayout.addView(vizHudBadge)
            }
            vizHudBadge?.text = textMsg
            vizHudBadge?.visibility = View.VISIBLE
            vizHudBadge?.alpha = 1.0f
            vizHudBadge?.animate()?.cancel()
            vizHudBadge?.animate()
                ?.alpha(0f)
                ?.setDuration(350)
                ?.setStartDelay(1200)
                ?.withEndAction { vizHudBadge?.visibility = View.GONE }
                ?.start()
        }
    }

    private fun attachVizTouchControls(card: FrameLayout, vizView: VisualizerView) {
        var startRawY = 0f
        var startRawX = 0f
        var initialHeight = 0
        var isLongPressResize = false
        val longPressHandler = Handler(Looper.getMainLooper())

        val scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val curH = card.layoutParams.height.takeIf { it > 0 } ?: dp(76)
                val newH = (curH * detector.scaleFactor).toInt().coerceIn(dp(30), dp(320))
                card.layoutParams.height = newH
                card.requestLayout()
                val dpVal = (newH / resources.displayMetrics.density).toInt()
                showVizHudBadge("Visualizador: ${dpVal}dp")
                SonoraStore.putInt(this@MainActivity, "player_viz_height_dp", dpVal)
                return true
            }
        })

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                vizView.visualMode = (vizView.visualMode + 1) % VisualizerView.MODE_NAMES.size
                showVizHudBadge(VisualizerView.MODE_NAMES[vizView.visualMode])
                vizView.invalidate()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val curPos = SonoraStore.getInt(this@MainActivity, "stage_viz_position", 0)
                val nextPos = (curPos + 1) % 4
                SonoraStore.putInt(this@MainActivity, "stage_viz_position", nextPos)
                reorderStageContainers()
                val desc = when (nextPos) {
                    1 -> "Visualizador arriba de letras"
                    2 -> "Visualizador centro (ancho completo)"
                    3 -> "Visualizador fondo (ancho completo)"
                    else -> "Visualizador abajo de letras"
                }
                showVizHudBadge(desc)
                card.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                return true
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                if (Math.abs(dy) > Math.abs(dx) && Math.abs(dy) > dp(30)) {
                    val curPos = SonoraStore.getInt(this@MainActivity, "stage_viz_position", 0)
                    val nextPos = if (dy < 0) {
                        if (curPos == 0) 1 else 2
                    } else {
                        if (curPos == 1) 0 else 3
                    }
                    SonoraStore.putInt(this@MainActivity, "stage_viz_position", nextPos)
                    reorderStageContainers()
                    val desc = when (nextPos) {
                        1 -> "Visualizador arriba de letras"
                        2 -> "Visualizador centro (ancho completo)"
                        3 -> "Visualizador fondo (ancho completo)"
                        else -> "Visualizador abajo de letras"
                    }
                    showVizHudBadge(desc)
                    card.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    return true
                } else if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > dp(30)) {
                    if (dx > 0) {
                        vizView.visualMode = if (vizView.visualMode > 0) vizView.visualMode - 1 else VisualizerView.MODE_NAMES.size - 1
                    } else {
                        vizView.visualMode = (vizView.visualMode + 1) % VisualizerView.MODE_NAMES.size
                    }
                    showVizHudBadge(VisualizerView.MODE_NAMES[vizView.visualMode])
                    vizView.invalidate()
                    return true
                }
                return false
            }
        })

        val longPressRunnable = Runnable {
            isLongPressResize = true
            card.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            card.animate()
                .scaleX(1.04f)
                .scaleY(1.04f)
                .translationZ(dpf(16f))
                .setDuration(180)
                .setInterpolator(OvershootInterpolator(1.35f))
                .start()
            showVizHudBadge("Visualizador: Arrastra ↑ agrandar, ↓ achicar")
        }

        card.setOnTouchListener { v, event ->
            scaleDetector.onTouchEvent(event)
            if (scaleDetector.isInProgress) {
                longPressHandler.removeCallbacks(longPressRunnable)
                isLongPressResize = false
                return@setOnTouchListener true
            }

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startRawY = event.rawY
                    startRawX = event.rawX
                    initialHeight = card.layoutParams.height.takeIf { it > 0 } ?: dp(76)
                    isLongPressResize = false
                    longPressHandler.postDelayed(longPressRunnable, 320)
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - startRawY
                    val dx = event.rawX - startRawX
                    if (!isLongPressResize && (Math.abs(dy) > dp(8) || Math.abs(dx) > dp(8))) {
                        longPressHandler.removeCallbacks(longPressRunnable)
                    }
                    if (isLongPressResize) {
                        card.translationY = dy * 0.25f
                        val newH = (initialHeight - dy.toInt()).coerceIn(dp(30), dp(320))
                        card.layoutParams.height = newH
                        card.requestLayout()
                        val dpVal = (newH / resources.displayMetrics.density).toInt()
                        showVizHudBadge("Visualizador: ${dpVal}dp")
                        return@setOnTouchListener true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    longPressHandler.removeCallbacks(longPressRunnable)
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    if (isLongPressResize) {
                        isLongPressResize = false
                        card.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .translationX(0f)
                            .translationY(0f)
                            .translationZ(0f)
                            .setDuration(260)
                            .setInterpolator(OvershootInterpolator(1.35f))
                            .start()
                        val curH = card.layoutParams.height
                        val dpVal = (curH / resources.displayMetrics.density).toInt()
                        SonoraStore.putInt(this, "player_viz_height_dp", dpVal)
                        showVizHudBadge("Visualizador: ${dpVal}dp (Guardado)")
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        return@setOnTouchListener true
                    }
                }
            }
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun attachDokiTouchControls(dokiCol: FrameLayout, spriteView: DokiStageSpriteView) {
        var startRawY = 0f
        var startRawX = 0f
        var initialScale = 1.0f
        var initialTx = 0f
        var isLongPressResize = false
        val longPressHandler = Handler(Looper.getMainLooper())

        // Connect instant angry reaction and character cycle
        spriteView.onDokiAngryAndSwitch = {
            val dokiList = listOf("natsuki", "sayori", "monika", "yuri", "bocchi", "konata")
            val curIdx = dokiList.indexOf(selectedDokiId).takeIf { it >= 0 } ?: 0
            val nextDoki = dokiList[(curIdx + 1) % dokiList.size]
            selectedDokiId = nextDoki
            val prefs = getSharedPreferences("doki_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("doki_id", selectedDokiId).apply()
            updateDokiViews()
            showVizHudBadge("${selectedDokiId.replaceFirstChar { it.uppercase() }} 💢")
        }

        val scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val newScale = (spriteView.dokiScale * detector.scaleFactor).coerceIn(0.4f, 2.5f)
                spriteView.dokiScale = newScale
                val pct = (newScale * 100).toInt()
                showVizHudBadge("Doki: $pct%")
                SonoraStore.putInt(this@MainActivity, "player_doki_scale_pct", pct)
                return true
            }
        })

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                // Double tap swaps columns: Doki left <-> right!
                val swapped = SonoraStore.getBool(this@MainActivity, "stage_columns_swapped", false)
                SonoraStore.putBool(this@MainActivity, "stage_columns_swapped", !swapped)
                reorderStageContainers()
                showVizHudBadge(if (!swapped) "Doki a la derecha" else "Doki a la izquierda")
                return true
            }
        })

        val longPressRunnable = Runnable {
            isLongPressResize = true
            dokiCol.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            dokiCol.animate()
                .scaleX(1.05f)
                .scaleY(1.05f)
                .translationZ(dpf(18f))
                .setDuration(180)
                .setInterpolator(OvershootInterpolator(1.4f))
                .start()
            showVizHudBadge("Doki: Moviendo... Arrastra a los lados")
        }

        dokiCol.setOnTouchListener { v, event ->
            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
            if (scaleDetector.isInProgress) {
                longPressHandler.removeCallbacks(longPressRunnable)
                isLongPressResize = false
                return@setOnTouchListener true
            }

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startRawY = event.rawY
                    startRawX = event.rawX
                    initialScale = spriteView.dokiScale
                    initialTx = spriteView.translationX
                    isLongPressResize = false
                    longPressHandler.postDelayed(longPressRunnable, 320)
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - startRawY
                    val dx = event.rawX - startRawX
                    if (!isLongPressResize && (Math.abs(dy) > dp(8) || Math.abs(dx) > dp(8))) {
                        longPressHandler.removeCallbacks(longPressRunnable)
                    }
                    if (isLongPressResize) {
                        val screenW = resources.displayMetrics.widthPixels.toFloat()
                        val isSwapped = SonoraStore.getBool(this@MainActivity, "stage_columns_swapped", false)
                        val touchX = event.rawX

                        // Smooth home screen follow and tilt
                        dokiCol.translationX = dx * 0.40f
                        dokiCol.translationY = dy * 0.20f
                        if (screenW > 0) {
                            dokiCol.rotation = (dx / screenW * 8f).coerceIn(-6f, 6f)
                        }

                        // Dynamic Drag-to-Relocate: dragging across screen moves Doki to that side!
                        if (!isSwapped && touchX > screenW * 0.65f) {
                            SonoraStore.putBool(this@MainActivity, "stage_columns_swapped", true)
                            reorderStageContainers()
                            showVizHudBadge("Doki reubicada a la derecha")
                            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        } else if (isSwapped && touchX < screenW * 0.35f) {
                            SonoraStore.putBool(this@MainActivity, "stage_columns_swapped", false)
                            reorderStageContainers()
                            showVizHudBadge("Doki reubicada a la izquierda")
                            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        } else if (Math.abs(dx) > Math.abs(dy) * 1.2f && screenW > 0) {
                            // Relational Horizontal Split: dragging horizontally adjusts Doki vs Lyrics proportionally
                            val rawRatio = touchX / screenW
                            val newWeight = (if (isSwapped) (1f - rawRatio) else rawRatio).coerceIn(0.20f, 0.80f)
                            SonoraStore.putFloat(this@MainActivity, "stage_doki_weight", newWeight)
                            updateStageLayout()
                            val dokiPct = (newWeight * 100).toInt()
                            val lyricsPct = 100 - dokiPct
                            showVizHudBadge("Distribución: ${dokiPct}% Doki | ${lyricsPct}% Letras")
                        } else {
                            // Vertical drag: resizes Doki scale
                            val scaleDelta = -dy / dpf(220f)
                            val newScale = (initialScale + scaleDelta).coerceIn(0.4f, 2.5f)
                            spriteView.dokiScale = newScale
                            val pct = (newScale * 100).toInt()
                            showVizHudBadge("Doki: $pct%")
                        }
                        return@setOnTouchListener true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    longPressHandler.removeCallbacks(longPressRunnable)
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    if (isLongPressResize) {
                        isLongPressResize = false
                        dokiCol.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .translationX(0f)
                            .translationY(0f)
                            .rotation(0f)
                            .translationZ(0f)
                            .setDuration(280)
                            .setInterpolator(OvershootInterpolator(1.35f))
                            .start()
                        val pct = (spriteView.dokiScale * 100).toInt()
                        SonoraStore.putInt(this, "player_doki_scale_pct", pct)
                        showVizHudBadge("Diseño guardado")
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        return@setOnTouchListener true
                    }
                }
            }
            spriteView.dispatchTouchEvent(event)
            true
        }
    }

    private fun attachCoverTouchControls(card: FrameLayout, coverImg: ImageView) {
        var startRawY = 0f
        var startRawX = 0f
        var initialHeight = 0
        var isLongPressResize = false
        val longPressHandler = Handler(Looper.getMainLooper())

        val scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val curH = card.layoutParams.height.takeIf { it > 0 } ?: dp(228)
                val newH = (curH * detector.scaleFactor).toInt().coerceIn(dp(120), dp(450))
                card.layoutParams.height = newH
                card.requestLayout()
                val dpVal = (newH / resources.displayMetrics.density).toInt()
                showVizHudBadge("Portada: ${dpVal}dp")
                SonoraStore.putInt(this@MainActivity, "player_cover_height_dp", dpVal)
                return true
            }
        })

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                showGifCoverOptionsSheet()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val atBottom = SonoraStore.getBool(this@MainActivity, "stage_cover_at_bottom", false)
                SonoraStore.putBool(this@MainActivity, "stage_cover_at_bottom", !atBottom)
                reorderStageContainers()
                showVizHudBadge(if (!atBottom) "Portada abajo" else "Portada arriba")
                return true
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                val absX = Math.abs(diffX)
                val absY = Math.abs(diffY)

                if (absX > absY && absX > dp(35)) {
                    if (diffX < 0) {
                        playNext()
                        showVizHudBadge("Siguiente pista →")
                    } else {
                        playPrevious()
                        showVizHudBadge("← Pista anterior")
                    }
                    return true
                } else if (absY > absX && absY > dp(35)) {
                    if (diffY < 0) {
                        // SWIPE UP -> QUEUE!
                        showQueueSheet()
                        showVizHudBadge("Cola de Reproducción")
                    } else {
                        // SWIPE DOWN -> TRACK INFO
                        tracks.getOrNull(currentTrackIndex)?.let { showTrackDetails(it) } ?: toastNoTrack()
                    }
                    return true
                }
                return false
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (e1 == null) return false
                val totalDy = e1.y - e2.y
                if (totalDy > dp(70) && Math.abs(totalDy) > Math.abs(e1.x - e2.x) * 1.5f) {
                    showQueueSheet()
                    showVizHudBadge("Cola de Reproducción")
                    return true
                }
                return false
            }
        })

        val longPressRunnable = Runnable {
            isLongPressResize = true
            card.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            card.animate()
                .scaleX(1.04f)
                .scaleY(1.04f)
                .translationZ(dpf(18f))
                .setDuration(180)
                .setInterpolator(OvershootInterpolator(1.4f))
                .start()
            showVizHudBadge("Portada: Moviendo... Arrastra arriba/abajo")
        }

        card.setOnTouchListener { v, event ->
            scaleDetector.onTouchEvent(event)
            if (scaleDetector.isInProgress) {
                longPressHandler.removeCallbacks(longPressRunnable)
                isLongPressResize = false
                return@setOnTouchListener true
            }

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startRawY = event.rawY
                    startRawX = event.rawX
                    initialHeight = card.layoutParams.height.takeIf { it > 0 } ?: dp(228)
                    isLongPressResize = false
                    longPressHandler.postDelayed(longPressRunnable, 320)
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - startRawY
                    val dx = event.rawX - startRawX
                    if (!isLongPressResize && (Math.abs(dy) > dp(8) || Math.abs(dx) > dp(8))) {
                        longPressHandler.removeCallbacks(longPressRunnable)
                    }
                    if (isLongPressResize) {
                        val screenH = resources.displayMetrics.heightPixels.toFloat()
                        val screenW = resources.displayMetrics.widthPixels.toFloat()
                        val atBottom = SonoraStore.getBool(this@MainActivity, "stage_cover_at_bottom", false)

                        // Smooth home screen follow and tilt
                        card.translationY = dy * 0.45f
                        card.translationX = dx * 0.20f
                        if (screenW > 0) {
                            card.rotation = (dx / screenW * 6f).coerceIn(-5f, 5f)
                        }

                        if (!atBottom && event.rawY > screenH * 0.58f) {
                            SonoraStore.putBool(this@MainActivity, "stage_cover_at_bottom", true)
                            reorderStageContainers()
                            showVizHudBadge("Portada reubicada abajo")
                            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        } else if (atBottom && event.rawY < screenH * 0.42f) {
                            SonoraStore.putBool(this@MainActivity, "stage_cover_at_bottom", false)
                            reorderStageContainers()
                            showVizHudBadge("Portada reubicada arriba")
                            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        } else {
                            val newH = (initialHeight - dy.toInt()).coerceIn(dp(120), dp(450))
                            card.layoutParams.height = newH
                            card.requestLayout()
                            val dpVal = (newH / resources.displayMetrics.density).toInt()
                            showVizHudBadge("Portada: ${dpVal}dp")
                        }
                        return@setOnTouchListener true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    longPressHandler.removeCallbacks(longPressRunnable)
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    if (isLongPressResize) {
                        isLongPressResize = false
                        card.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .translationX(0f)
                            .translationY(0f)
                            .rotation(0f)
                            .translationZ(0f)
                            .setDuration(260)
                            .setInterpolator(OvershootInterpolator(1.35f))
                            .start()
                        val curH = card.layoutParams.height
                        val dpVal = (curH / resources.displayMetrics.density).toInt()
                        SonoraStore.putInt(this, "player_cover_height_dp", dpVal)
                        showVizHudBadge("Portada: ${dpVal}dp (Guardado)")
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        return@setOnTouchListener true
                    }
                }
            }
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun attachQueueSwipeUpGesture(view: View) {
        val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                if (Math.abs(diffY) > Math.abs(diffX) && diffY < -dp(35)) {
                    showQueueSheet()
                    showVizHudBadge("Cola de Reproducción")
                    return true
                } else if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(diffX) > dp(35)) {
                    if (diffX < 0) {
                        playNext()
                        showVizHudBadge("Siguiente pista →")
                    } else {
                        playPrevious()
                        showVizHudBadge("← Pista anterior")
                    }
                    return true
                }
                return false
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (e1 == null) return false
                val totalDy = e1.y - e2.y
                if (totalDy > dp(65) && Math.abs(totalDy) > Math.abs(e1.x - e2.x) * 1.5f) {
                    showQueueSheet()
                    showVizHudBadge("Cola de Reproducción")
                    return true
                }
                return false
            }
        })
        view.setOnTouchListener { v, ev ->
            gd.onTouchEvent(ev)
            false
        }
    }

    fun showTutorialSheet() {
        showSheet(fullHeight = true) { root, dialog ->
            root.addView(sheetTitle("Guía y Tutorial Completo", "Aprende todos los gestos y funciones de DDMC"))

            val intro = TextView(this).apply {
                text = "Bienvenido a DDMC. Aquí tienes el resumen visual y rápido de todas las funciones táctiles y ajustes profesionales organizados por categorías."
                setTextColor(Color.parseColor(Pal.SUB))
                textSize = 12.5f
                setPadding(0, dp(2), 0, dp(12))
            }
            root.addView(intro)

            fun makeTutorialCard(title: String, iconRes: Int, bullets: List<String>) {
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#CC1A152E"))
                        cornerRadius = dpf(16f)
                        setStroke(dp(1), Color.parseColor("#2EFFFFFF"))
                    }
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = dp(10)
                    }
                }
                val header = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, 0, 0, dp(8))
                }
                val icon = ImageView(this).apply {
                    setImageResource(iconRes)
                    setColorFilter(Color.parseColor(Pal.ACCENT))
                    layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(8) }
                }
                val titleTv = TextView(this).apply {
                    text = title
                    textSize = 14f
                    setTextColor(Color.WHITE)
                    setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
                }
                header.addView(icon)
                header.addView(titleTv)
                card.addView(header)

                for (b in bullets) {
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(0, dp(2), 0, dp(2))
                    }
                    val bulletDot = TextView(this).apply {
                        text = "• "
                        setTextColor(Color.parseColor(Pal.ACCENT))
                        textSize = 12.5f
                    }
                    val textTv = TextView(this).apply {
                        text = b
                        setTextColor(Color.parseColor(Pal.SUB))
                        textSize = 12.5f
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    }
                    row.addView(bulletDot)
                    row.addView(textTv)
                    card.addView(row)
                }
                root.addView(card)
            }

            makeTutorialCard("Gestos Táctiles en Pantalla", R.drawable.ic_waveform, listOf(
                "Deslizar hacia arriba (Swipe Up): Abre la cola de reproducción en cualquier momento.",
                "Deslizar hacia abajo (Swipe Down): Muestra detalles y etiquetas de la pista.",
                "Deslizar izquierda / derecha: Cambia a la siguiente o anterior canción.",
                "Doble toque: Salta 10s atrás (izquierda) o adelante (derecha)."
            ))

            makeTutorialCard("Redimensionar y Mover Libremente", R.drawable.ic_widgets, listOf(
                "Mantener presionado (Long Press): Toca y mantén presionado el visualizador, portada o doki. Tras una vibración, arrastra tu dedo hacia arriba para agrandar o hacia abajo para achicar.",
                "Arrastre libre: Al mantener presionado la Doki, arrastra horizontalmente para colocarla en la posición deseada.",
                "Pellizco táctil (Pinch): Usa dos dedos sobre cualquier elemento para ajustar su tamaño de forma continua.",
                "Guardado automático: Al soltar el dedo, la nueva dimensión y posición se guardan al instante."
            ))

            makeTutorialCard("Formas del Visualizador (Sin Presets)", R.drawable.ic_sliders, listOf(
                "Deslizar sobre el visualizador: Desliza horizontalmente con tu dedo directamente sobre el visualizador para alternar libremente entre las 7 formas (Espejo, Neón, Radial, Cascada, Híbrido, etc.).",
                "Tocar una vez: Cambia a la siguiente forma con confirmación visual sutil.",
                "Ranuras opcionales: Guarda tus configuraciones preferidas en el panel de diseño si deseas recordarlas."
            ))

            makeTutorialCard("Compañeras Doki Silenciosas", R.drawable.ic_sparkles, listOf(
                "Totalmente silenciosas: Sin voces molestas. Al tocar la Doki reaccionará con una animación sutil de emoción y rebote con el ritmo musical.",
                "Cambio rápido: Toca el icono de destellos en la barra superior para cambiar tu compañera (Monika, Sayori, Natsuki, Yuri, Bocchi, Konata) en un toque."
            ))

            makeTutorialCard("Audio Pro & Modificador por Pista", R.drawable.ic_disc, listOf(
                "Audio exclusivo de pista: Modifica el refuerzo de bajos y ganancia de una canción específica sin alterar el ecualizador de las demás.",
                "Conmutador Mono: Mezcla canales estéreo a mono real de 16-bit PCM.",
                "Balance Izquierda / Derecha: Desplaza el canal auditivo con precisión."
            ))

            makeTutorialCard("Importación Musicolet", R.drawable.ic_download, listOf(
                "Descifrado de respaldo: Lee archivos musicolet.zip en tu carpeta Descargas, descifra las listas con Blowfish y las importa automáticamente a tus listas de Sonora."
            ))

            val closeBtn = Pill(this, "Entendido, Comenzar", R.drawable.ic_check, 40).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(6)
                    bottomMargin = dp(12)
                }
                setOnClickListener { dialog.dismiss() }
            }
            root.addView(closeBtn)
        }
    }

    fun showVizSlotsSheet() {
        showSheet { root, dialog ->
            root.addView(sheetTitle("Ranuras de Visualización", "Alterna y guarda estilos visuales"))
            val slotsBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(8), 0, dp(12))
            }
            for (slot in 1..4) {
                val mode = SonoraStore.getInt(this, "viz_slot_${slot}_mode", when(slot) { 1 -> 0; 2 -> 1; 3 -> 2; else -> 0 })
                val h = SonoraStore.getInt(this, "viz_slot_${slot}_h", when(slot) { 1 -> 76; 2 -> 96; 3 -> 68; else -> 140 })
                val modeName = when(mode) { 0 -> "Espejo Pro"; 1 -> "Barras Neón"; 2 -> "Onda Circular"; 3 -> "Espectrograma"; else -> "Líneas" }
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor(Pal.SURFACE_HI))
                        cornerRadius = dpf(12f)
                    }
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = dp(8)
                    }
                }
                val info = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                val title = TextView(this).apply {
                    text = "Ranura $slot: $modeName"
                    textSize = 14f
                    setTextColor(Color.WHITE)
                    setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
                }
                val sub = TextView(this).apply {
                    text = "Altura: ${h}dp"
                    textSize = 12f
                    setTextColor(Color.parseColor(Pal.MUTED))
                }
                info.addView(title); info.addView(sub)
                val loadBtn = Pill(this, "Cargar", R.drawable.ic_check, 32).apply {
                    setOnClickListener {
                        applyVizSlot(slot)
                        dialog.dismiss()
                    }
                }
                val saveBtn = Pill(this, "Guardar", R.drawable.ic_pencil, 32).apply {
                    setOnClickListener {
                        saveCurrentToVizSlot(slot)
                        dialog.dismiss()
                    }
                }
                row.addView(info)
                row.addView(loadBtn)
                row.addView(saveBtn)
                slotsBox.addView(row)
            }
            root.addView(slotsBox)
        }
    }

    fun showPlayerLayoutCustomizerSheet() {
        showSheet(fullHeight = true) { root, dialog ->
            root.addView(sheetTitle("Personalizar Reproductor", "Deslizadores continuos y gestos touch directos"))

            val hintCard = TextView(this).apply {
                text = "TIP TOUCH DIRECTO:\n• Mantén presionado y arrastra: Portada (arriba/abajo), Doki (izq/der o tamaño), Visualizador (altura).\n• Doble toque: Cambia ubicación de Portada o Doki al instante.\n• Doble toque en Visualizador: Cambia su posición (arriba/abajo de letras o ancho completo)."
                setTextColor(Color.parseColor(Pal.SUB))
                textSize = 12f
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(Pal.SURFACE_HI))
                    cornerRadius = dpf(12f)
                    setStroke(dp(1), Color.parseColor("#25FFFFFF"))
                }
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(14)
                }
            }
            root.addView(hintCard)

            val gridTitle = TextView(this).apply {
                text = "Cuadrícula y Ventanas (Windows Snap)"
                setTextColor(Color.WHITE)
                textSize = 14f
                setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
                setPadding(0, 0, 0, dp(6))
            }
            root.addView(gridTitle)

            val gridRow1 = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(6)
                }
            }
            val curSnap = SonoraStore.getInt(this, "stage_grid_snap_mode", 0)
            val btn2x2 = Pill(this, if (curSnap == 0) "● Cuadrícula 2x2" else "Cuadrícula 2x2", R.drawable.ic_widgets, 34)
            btn2x2.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) }
            btn2x2.setOnClickListener {
                SonoraStore.putInt(this@MainActivity, "stage_grid_snap_mode", 0)
                fullscreenWindowId = null
                reorderStageContainers()
                showVizHudBadge("Cuadrícula 2x2 activada")
            }
            val btnSplit = Pill(this, if (curSnap == 1) "● Dividido 50/50" else "Dividido 50/50", R.drawable.ic_sliders, 34)
            btnSplit.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            btnSplit.setOnClickListener {
                SonoraStore.putInt(this@MainActivity, "stage_grid_snap_mode", 1)
                fullscreenWindowId = null
                reorderStageContainers()
                showVizHudBadge("Dividido 50/50 activado")
            }
            gridRow1.addView(btn2x2)
            gridRow1.addView(btnSplit)
            root.addView(gridRow1)

            val gridRow2 = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(14)
                }
            }
            val btnBento = Pill(this, if (curSnap == 2) "● Bento PIP" else "Bento PIP", R.drawable.ic_sparkles, 34)
            btnBento.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) }
            btnBento.setOnClickListener {
                SonoraStore.putInt(this@MainActivity, "stage_grid_snap_mode", 2)
                fullscreenWindowId = null
                reorderStageContainers()
                showVizHudBadge("Bento PIP activado")
            }
            val btnClassic = Pill(this, if (curSnap == 3) "● Clásico Apilado" else "Clásico Apilado", R.drawable.ic_list_music, 34)
            btnClassic.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            btnClassic.setOnClickListener {
                SonoraStore.putInt(this@MainActivity, "stage_grid_snap_mode", 3)
                fullscreenWindowId = null
                reorderStageContainers()
                showVizHudBadge("Clásico Apilado activado")
            }
            gridRow2.addView(btnBento)
            gridRow2.addView(btnClassic)
            root.addView(gridRow2)

            if (fullscreenWindowId != null) {
                val restoreFullscreenBtn = Pill(this, "Restaurar Cuadrícula (Salir de Pantalla Completa)", R.drawable.ic_refresh, 36).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = dp(12)
                    }
                    setOnClickListener {
                        fullscreenWindowId = null
                        reorderStageContainers()
                        showVizHudBadge("Cuadrícula restaurada")
                    }
                }
                root.addView(restoreFullscreenBtn)
            }

            // Posición de Elementos (Toggles rápidos)
            val posTitle = TextView(this).apply {
                text = "Ubicación de Elementos"
                setTextColor(Color.WHITE)
                textSize = 14f
                setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
                setPadding(0, 0, 0, dp(6))
            }
            root.addView(posTitle)

            val posRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(8)
                }
            }

            val curCoverAtBottom = SonoraStore.getBool(this, "stage_cover_at_bottom", false)
            val coverPosBtn = Pill(this, if (curCoverAtBottom) "Portada: Abajo" else "Portada: Arriba", R.drawable.ic_album, 34)
            coverPosBtn.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) }
            coverPosBtn.setOnClickListener {
                val next = !SonoraStore.getBool(this@MainActivity, "stage_cover_at_bottom", false)
                SonoraStore.putBool(this@MainActivity, "stage_cover_at_bottom", next)
                reorderStageContainers()
                coverPosBtn.labelView.text = if (next) "Portada: Abajo" else "Portada: Arriba"
                showVizHudBadge(if (next) "Portada abajo" else "Portada arriba")
            }
            posRow.addView(coverPosBtn)

            val curDokiSwapped = SonoraStore.getBool(this, "stage_columns_swapped", false)
            val dokiPosBtn = Pill(this, if (curDokiSwapped) "Doki: Derecha" else "Doki: Izquierda", R.drawable.ic_sparkles, 34)
            dokiPosBtn.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            dokiPosBtn.setOnClickListener {
                val next = !SonoraStore.getBool(this@MainActivity, "stage_columns_swapped", false)
                SonoraStore.putBool(this@MainActivity, "stage_columns_swapped", next)
                reorderStageContainers()
                dokiPosBtn.labelView.text = if (next) "Doki: Derecha" else "Doki: Izquierda"
                showVizHudBadge(if (next) "Doki a la derecha" else "Doki a la izquierda")
            }
            posRow.addView(dokiPosBtn)
            root.addView(posRow)

            val vizPosRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(14)
                }
            }
            val curVizPos = SonoraStore.getInt(this, "stage_viz_position", 0)
            fun vizPosLabel(p: Int) = when (p) {
                1 -> "Viz: Arriba Letras"
                2 -> "Viz: Centro Dock"
                3 -> "Viz: Fondo Dock"
                else -> "Viz: Abajo Letras"
            }
            val vizPosBtn = Pill(this, vizPosLabel(curVizPos), R.drawable.ic_waveform, 34)
            vizPosBtn.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            vizPosBtn.setOnClickListener {
                val p = (SonoraStore.getInt(this@MainActivity, "stage_viz_position", 0) + 1) % 4
                SonoraStore.putInt(this@MainActivity, "stage_viz_position", p)
                reorderStageContainers()
                vizPosBtn.labelView.text = vizPosLabel(p)
                showVizHudBadge(vizPosLabel(p))
            }
            vizPosRow.addView(vizPosBtn)
            root.addView(vizPosRow)

            val videoModeRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(14)
                }
            }
            val isGlobalVid = SonoraStore.getBool(this, "cover_video_global_mode", false)
            val videoModeBtn = Pill(
                this,
                if (isGlobalVid) "Video: Mismo para todas las canciones" else "Video: Individual por canción",
                R.drawable.ic_video,
                34
            )
            videoModeBtn.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            videoModeBtn.setOnClickListener {
                val next = !SonoraStore.getBool(this@MainActivity, "cover_video_global_mode", false)
                SonoraStore.putBool(this@MainActivity, "cover_video_global_mode", next)
                videoModeBtn.labelView.text = if (next) "Video: Mismo para todas las canciones" else "Video: Individual por canción"
                bindNowPlaying()
                showVizHudBadge(if (next) "Video global activado" else "Video por canción activado")
            }
            videoModeRow.addView(videoModeBtn)
            root.addView(videoModeRow)

            val coverCurH = SonoraStore.getInt(this, "player_cover_height_dp", 228)
            val coverLabel = TextView(this).apply {
                text = "Altura de Portada: ${coverCurH}dp"
                setTextColor(Color.WHITE)
                textSize = 13f
                setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            }
            root.addView(coverLabel)
            val coverSb = SeekBar(this).apply {
                max = 330 // 120 to 450
                progress = (coverCurH - 120).coerceIn(0, 330)
                progressTintList = ColorStateList.valueOf(Color.parseColor(Pal.ACCENT))
                thumbTintList = ColorStateList.valueOf(Color.WHITE)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                        val h = 120 + p
                        coverLabel.text = "Altura de Portada: ${h}dp"
                        SonoraStore.putInt(this@MainActivity, "player_cover_height_dp", h)
                        stageExpandedCoverCard.layoutParams.height = dp(h)
                        stageExpandedCoverCard.requestLayout()
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(12)
                }
            }
            root.addView(coverSb)

            val vizCurH = SonoraStore.getInt(this, "player_viz_height_dp", 76)
            val vizLabel = TextView(this).apply {
                text = "Altura del Visualizador: ${vizCurH}dp"
                setTextColor(Color.WHITE)
                textSize = 13f
                setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            }
            root.addView(vizLabel)
            val vizSb = SeekBar(this).apply {
                max = 280 // 30 to 310
                progress = (vizCurH - 30).coerceIn(0, 280)
                progressTintList = ColorStateList.valueOf(Color.parseColor(Pal.ACCENT))
                thumbTintList = ColorStateList.valueOf(Color.WHITE)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                        val h = 30 + p
                        vizLabel.text = "Altura del Visualizador: ${h}dp"
                        SonoraStore.putInt(this@MainActivity, "player_viz_height_dp", h)
                        vizDeckCard.layoutParams.height = dp(h)
                        vizDeckCard.requestLayout()
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(12)
                }
            }
            root.addView(vizSb)

            val dokiCurScale = SonoraStore.getInt(this, "player_doki_scale_pct", 100)
            val dokiLabel = TextView(this).apply {
                text = "Escala de Doki: $dokiCurScale%"
                setTextColor(Color.WHITE)
                textSize = 13f
                setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            }
            root.addView(dokiLabel)
            val dokiSb = SeekBar(this).apply {
                max = 200 // 40% to 240%
                progress = (dokiCurScale - 40).coerceIn(0, 200)
                progressTintList = ColorStateList.valueOf(Color.parseColor(Pal.ACCENT))
                thumbTintList = ColorStateList.valueOf(Color.WHITE)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                        val pct = 40 + p
                        dokiLabel.text = "Escala de Doki: $pct%"
                        SonoraStore.putInt(this@MainActivity, "player_doki_scale_pct", pct)
                        dokiSpriteView.dokiScale = pct / 100f
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(14)
                }
            }
            root.addView(dokiSb)

            val resetBtn = Pill(this, "Restablecer Dimensiones", R.drawable.ic_trash, 38).apply {
                setOnClickListener {
                    SonoraStore.putInt(this@MainActivity, "player_cover_height_dp", 228)
                    SonoraStore.putInt(this@MainActivity, "player_viz_height_dp", 76)
                    SonoraStore.putInt(this@MainActivity, "player_doki_scale_pct", 100)
                    SonoraStore.putFloat(this@MainActivity, "layout_doki_tx", 0f)
                    stageExpandedCoverCard.layoutParams.height = dp(228)
                    stageExpandedCoverCard.requestLayout()
                    vizDeckCard.layoutParams.height = dp(76)
                    vizDeckCard.requestLayout()
                    dokiSpriteView.dokiScale = 1.0f
                    dokiSpriteView.translationX = 0f
                    Toast.makeText(this@MainActivity, "Tamaños restablecidos", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
            root.addView(resetBtn)

            root.addView(Pill(this, "Ranuras de Visualización", R.drawable.ic_sliders, 38).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(8)
                    bottomMargin = dp(8)
                }
                setOnClickListener {
                    dialog.dismiss()
                    showVizSlotsSheet()
                }
            })

            root.addView(Pill(this, "Posición de Pestañas", R.drawable.ic_sliders, 38).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(14)
                }
                setOnClickListener {
                    dialog.dismiss()
                    showTabBarPositionDialog()
                }
            })

            // Rendimiento & Optimización para celulares patata y gama alta
            val perfTitle = TextView(this).apply {
                text = "Modo Rendimiento (Activar / Desactivar)"
                setTextColor(Color.WHITE)
                textSize = 14f
                setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
                setPadding(0, dp(4), 0, dp(4))
            }
            root.addView(perfTitle)

            val perfSub = TextView(this).apply {
                text = "Activa o desactiva la optimización para celulares modestos. Todas las funciones (visualizador, dokis, video covers y letras) se mantienen 100% activas."
                setTextColor(Color.parseColor(Pal.SUB))
                textSize = 11.5f
                setPadding(0, 0, 0, dp(8))
            }
            root.addView(perfSub)

            val perfStatusTv = TextView(this).apply {
                text = PerformanceEngine.getShortLabel()
                setTextColor(Color.parseColor(Pal.ACCENT))
                textSize = 12.5f
                setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
                setPadding(0, 0, 0, dp(8))
            }
            root.addView(perfStatusTv)

            // Switch principal de Activar / Desactivar
            val toggleRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(Pal.SURFACE_HI))
                    cornerRadius = dpf(12f)
                    setStroke(dp(1), Color.parseColor("#35FFFFFF"))
                }
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(10)
                }
            }
            val toggleInfo = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) }
            }
            val toggleTitle = TextView(this).apply {
                text = "Modo Optimización"
                textSize = 13.5f
                setTextColor(Color.WHITE)
                setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            }
            val toggleSub = TextView(this).apply {
                text = if (PerformanceEngine.isPotatoMode) "Activado: Modo Patata (30 FPS, ahorro RAM y CPU)" else "Desactivado: Alta Fidelidad (60 FPS fluido)"
                textSize = 11.5f
                setTextColor(Color.parseColor(Pal.MUTED))
            }
            toggleInfo.addView(toggleTitle)
            toggleInfo.addView(toggleSub)

            val toggleSwitch = Switch(this).apply {
                isChecked = PerformanceEngine.isPotatoMode
                thumbTintList = ColorStateList.valueOf(Color.WHITE)
                trackTintList = ColorStateList.valueOf(Color.parseColor(Pal.ACCENT))
                setOnCheckedChangeListener { _, isChecked ->
                    val newMode = if (isChecked) PerformanceEngine.MODE_POTATO else PerformanceEngine.MODE_BALANCED
                    PerformanceEngine.setMode(this@MainActivity, newMode)
                    toggleSub.text = if (isChecked) "Activado: Modo Patata (30 FPS, ahorro RAM y CPU)" else "Desactivado: Alta Fidelidad (60 FPS fluido)"
                    perfStatusTv.text = PerformanceEngine.getShortLabel()
                    showVizHudBadge(if (isChecked) "Optimización ACTIVADA (30 FPS)" else "Optimización DESACTIVADA (60 FPS)")
                    if (::visualizerView.isInitialized) visualizerView.postInvalidate()
                    if (::dokiSpriteView.isInitialized) dokiSpriteView.postInvalidate()
                }
            }
            toggleRow.setOnClickListener { toggleSwitch.isChecked = !toggleSwitch.isChecked }
            toggleRow.addView(toggleInfo)
            toggleRow.addView(toggleSwitch)
            root.addView(toggleRow)

            val perfRow = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(8)
                }
            }

            val potatoBtn = Pill(this, "Modo Patata (30 FPS / Ahorro CPU y RAM)", R.drawable.ic_bolt, 36).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(6)
                }
                setOnClickListener {
                    PerformanceEngine.setMode(this@MainActivity, PerformanceEngine.MODE_POTATO)
                    perfStatusTv.text = PerformanceEngine.getShortLabel()
                    showVizHudBadge("Modo Patata activado (30 FPS)")
                    if (::visualizerView.isInitialized) visualizerView.postInvalidate()
                    if (::dokiSpriteView.isInitialized) dokiSpriteView.postInvalidate()
                }
            }
            val balancedBtn = Pill(this, "Modo Equilibrado (60 FPS Fluido)", R.drawable.ic_check, 36).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(6)
                }
                setOnClickListener {
                    PerformanceEngine.setMode(this@MainActivity, PerformanceEngine.MODE_BALANCED)
                    perfStatusTv.text = PerformanceEngine.getShortLabel()
                    showVizHudBadge("Modo Equilibrado activado (60 FPS)")
                    if (::visualizerView.isInitialized) visualizerView.postInvalidate()
                    if (::dokiSpriteView.isInitialized) dokiSpriteView.postInvalidate()
                }
            }
            val ultraBtn = Pill(this, "Modo Ultra (120 FPS / Máxima Fidelidad)", R.drawable.ic_sparkles, 36).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(6)
                }
                setOnClickListener {
                    PerformanceEngine.setMode(this@MainActivity, PerformanceEngine.MODE_ULTRA)
                    perfStatusTv.text = PerformanceEngine.getShortLabel()
                    showVizHudBadge("Modo Ultra activado (120 FPS)")
                    if (::visualizerView.isInitialized) visualizerView.postInvalidate()
                    if (::dokiSpriteView.isInitialized) dokiSpriteView.postInvalidate()
                }
            }
            val autoDetectBtn = Pill(this, "Auto-detectar según Hardware", R.drawable.ic_refresh, 36).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(8)
                }
                setOnClickListener {
                    val isPotato = PerformanceEngine.isLowEndHardware(this@MainActivity)
                    val mode = if (isPotato) PerformanceEngine.MODE_POTATO else PerformanceEngine.MODE_BALANCED
                    PerformanceEngine.setMode(this@MainActivity, mode)
                    perfStatusTv.text = PerformanceEngine.getShortLabel()
                    val msg = if (isPotato) "Dispositivo modesto detectado: Modo Patata (30 FPS)" else "Dispositivo óptimo: Modo Equilibrado (60 FPS)"
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                    showVizHudBadge(msg)
                    if (::visualizerView.isInitialized) visualizerView.postInvalidate()
                    if (::dokiSpriteView.isInitialized) dokiSpriteView.postInvalidate()
                }
            }

            perfRow.addView(potatoBtn)
            perfRow.addView(balancedBtn)
            perfRow.addView(ultraBtn)
            perfRow.addView(autoDetectBtn)
            root.addView(perfRow)
        }
    }

    fun showTrackAudioCustomizerDialog(t: TrackItem) {
        val curBass = SonoraStore.getInt(this, "track_bass_${t.key}", 0)
        val curGain = SonoraStore.getFloat(this, "track_gain_${t.key}", 1.0f)

        showSheet { root, dialog ->
            root.addView(sheetTitle("Audio de Esta Pista", "${t.title} - ${t.artist}"))

            val bassLabel = TextView(this).apply {
                val db = (curBass / 100.0).let { String.format("%.1f dB", it) }
                text = "Refuerzo de Bajos: $db"
                setTextColor(Color.WHITE)
                textSize = 13.5f
                setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
                setPadding(0, dp(8), 0, dp(4))
            }
            root.addView(bassLabel)

            val bassRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, dp(12))
            }
            listOf(-300 to "-3 dB", 0 to "Plano", 300 to "+3 dB", 600 to "+6 dB", 900 to "+9 dB").forEach { (mb, lbl) ->
                bassRow.addView(Pill(this, lbl, R.drawable.ic_disc, 30).apply {
                    setActive(curBass == mb)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(4) }
                    setOnClickListener {
                        SonoraAudioEngine.setTrackBass(this@MainActivity, t.key, mb)
                        Toast.makeText(this@MainActivity, "Bajos de pista: $lbl", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        showTrackAudioCustomizerDialog(t)
                    }
                })
            }
            root.addView(bassRow)

            val gainLabel = TextView(this).apply {
                val pct = (curGain * 100).toInt()
                text = "Ganancia de Pista: $pct%"
                setTextColor(Color.WHITE)
                textSize = 13.5f
                setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
                setPadding(0, dp(4), 0, dp(4))
            }
            root.addView(gainLabel)

            val gainRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, dp(12))
            }
            listOf(0.8f to "80%", 1.0f to "100%", 1.15f to "115%", 1.3f to "130%").forEach { (g, lbl) ->
                gainRow.addView(Pill(this, lbl, R.drawable.ic_waveform, 30).apply {
                    setActive(Math.abs(curGain - g) < 0.05f)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(4) }
                    setOnClickListener {
                        SonoraAudioEngine.setTrackGain(this@MainActivity, t.key, g)
                        Toast.makeText(this@MainActivity, "Ganancia de pista: $lbl", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        showTrackAudioCustomizerDialog(t)
                    }
                })
            }
            root.addView(gainRow)

            val resetBtn = Pill(this, "Restablecer a Valores Globales", R.drawable.ic_trash, 36).apply {
                setOnClickListener {
                    SonoraAudioEngine.setTrackBass(this@MainActivity, t.key, 0)
                    SonoraAudioEngine.setTrackGain(this@MainActivity, t.key, 1.0f)
                    Toast.makeText(this@MainActivity, "Audio de pista restablecido", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
            root.addView(resetBtn)
        }
    }

    fun showQuickDokiSwitcherSheet() {
        showSheet(fullHeight = true) { root, dialog ->
            root.addView(sheetTitle("Cambiar Doki", "Selecciona tu compañera en un solo toque"))

            val grid = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(8), 0, dp(12))
            }

            val dokis = listOf(
                Triple("natsuki", "Natsuki (Chibi Shimeji)", "shimeji"),
                Triple("sayori", "Sayori (Chibi Shimeji)", "shimeji"),
                Triple("monika", "Monika (Chibi Shimeji)", "shimeji"),
                Triple("yuri", "Yuri (Chibi Shimeji)", "shimeji"),
                Triple("monika", "Monika HD (Presidenta)", "casual"),
                Triple("sayori", "Sayori HD (Casual)", "casual"),
                Triple("natsuki", "Natsuki HD (Manga)", "casual"),
                Triple("yuri", "Yuri HD (Lectora)", "casual"),
                Triple("bocchi", "Bocchi the Rock!", "shimeji"),
                Triple("konata", "Konata Izumi", "shimeji")
            )

            for (d in dokis) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor(Pal.SURFACE_HI))
                        cornerRadius = dpf(14f)
                    }
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = dp(8)
                    }
                    isClickable = true
                    setOnClickListener {
                        selectedDokiId = d.first
                        selectedDokiPose = "casual"
                        selectedDokiExpr = d.third
                        val dokiPrefs = getSharedPreferences("doki_prefs", Context.MODE_PRIVATE)
                        dokiPrefs.edit()
                            .putString("doki_id", selectedDokiId)
                            .putString("doki_pose", selectedDokiPose)
                            .putString("doki_expr_v2", selectedDokiExpr)
                            .apply()
                        dokiSpriteView.setDoki(selectedDokiId, selectedDokiPose, selectedDokiExpr)
                        updateDokiViews()
                        dialog.dismiss()
                        Toast.makeText(this@MainActivity, "${d.second} activada", Toast.LENGTH_SHORT).show()
                    }
                    addPressFeedback(0.96f)
                }
                val icon = ImageView(this).apply {
                    setImageResource(if (d.third == "shimeji") R.drawable.ic_sparkles else R.drawable.ic_album)
                    layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(12) }
                    setColorFilter(Color.parseColor(Pal.ACCENT))
                }
                val tv = TextView(this).apply {
                    text = d.second
                    textSize = 14f
                    setTextColor(Color.WHITE)
                    setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                val arrow = ImageView(this).apply {
                    setImageResource(R.drawable.ic_check)
                    layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
                    setColorFilter(Color.parseColor(Pal.MUTED))
                }
                row.addView(icon); row.addView(tv); row.addView(arrow)
                grid.addView(row)
            }
            root.addView(grid)
        }
    }

    fun showMusicoletImportDialog() {
        val foundFiles = MusicoletImporter.findPotentialBackupFiles()

        showSheet { root, dialog ->
            root.addView(sheetTitle("Importar de Musicolet", "Importa tus listas de reproducción (.mpl) y favoritos"))

            if (foundFiles.isNotEmpty()) {
                val foundBox = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, dp(6), 0, dp(10))
                }
                val subtitle = TextView(this).apply {
                    text = "Copias de seguridad encontradas en Descargas:"
                    setTextColor(Color.WHITE)
                    textSize = 12.5f
                    setPadding(0, dp(4), 0, dp(6))
                }
                foundBox.addView(subtitle)

                for (f in foundFiles) {
                    val sizeKb = f.length() / 1024
                    val item = Pill(this, "${f.name} (${sizeKb} KB)", R.drawable.ic_download, 40).apply {
                        setActive(true)
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            bottomMargin = dp(8)
                        }
                        setOnClickListener {
                            dialog.dismiss()
                            executeMusicoletImportFile(f)
                        }
                    }
                    foundBox.addView(item)
                }
                root.addView(foundBox)
            }

            root.addView(Pill(this, "Examinar otro archivo ZIP de Musicolet", R.drawable.ic_folder, 40).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(8)
                }
                setOnClickListener {
                    dialog.dismiss()
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/zip"
                        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                    }
                    musicoletZipPickerLauncher.launch(intent)
                }
            })
        }
    }

    private fun executeMusicoletImportFile(f: File) {
        val progressDialog = android.app.ProgressDialog(this).apply {
            setMessage("Descifrando listas de Musicolet con Blowfish...")
            setCancelable(false)
            show()
        }
        Thread {
            try {
                val res = MusicoletImporter.importBackupFile(this, f, localTracksList)
                runOnUiThread {
                    progressDialog.dismiss()
                    Toast.makeText(
                        this,
                        "Importadas ${res.playlistsCreated} listas (${res.totalTracksInPlaylists} pistas) y ${res.favoritesMarked} favoritos",
                        Toast.LENGTH_LONG
                    ).show()
                    refreshPlaylists()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressDialog.dismiss()
                    Toast.makeText(this, "Error al descifrar zip: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun executeMusicoletImport(uri: Uri) {
        val progressDialog = android.app.ProgressDialog(this).apply {
            setMessage("Descifrando listas de Musicolet con Blowfish...")
            setCancelable(false)
            show()
        }
        Thread {
            try {
                val res = MusicoletImporter.importBackupUri(this, uri, localTracksList)
                runOnUiThread {
                    progressDialog.dismiss()
                    Toast.makeText(
                        this,
                        "Importadas ${res.playlistsCreated} listas (${res.totalTracksInPlaylists} pistas) y ${res.favoritesMarked} favoritos",
                        Toast.LENGTH_LONG
                    ).show()
                    refreshPlaylists()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressDialog.dismiss()
                    Toast.makeText(this, "Error al descifrar zip: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun buildNav(): LinearLayout {
        val nav = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val glassBg = GradientDrawable().apply {
                setColor(Color.parseColor("#CC120E22")) // Liquid Acrylic Glass
                cornerRadius = dpf(20f)
                setStroke(dp(1), Color.parseColor("#38FFFFFF")) // Luminous glass rim
            }
            background = glassBg
            setPadding(dp(6), dp(6), dp(6), dp(6))
            val lp = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                setMargins(dp(8), 0, dp(8), dp(6))
            }
            layoutParams = lp
            elevation = dpf(8f)
        }
        val icons = listOf(
            R.drawable.ic_home, R.drawable.ic_folder, R.drawable.ic_list_music,
            R.drawable.ic_radio, R.drawable.ic_sliders, R.drawable.ic_widgets
        )
        val labels = listOf("Reproductor", "Biblioteca", "Listas", "Catálogo", "Sonido", "Widgets")
        for (i in icons.indices) {
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                isClickable = true
                setOnClickListener { switchTab(i) }
                addPressFeedback(0.92f)
            }
            val plate = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(54), dp(30))
            }
            val icon = ImageView(this).apply {
                setImageResource(icons[i])
                layoutParams = FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER)
            }
            plate.addView(icon)
            val label = TextView(this).apply {
                text = labels[i]
                textSize = 10f
                maxLines = 1
                gravity = Gravity.CENTER
                setPadding(0, dp(3), 0, 0)
            }
            item.addView(plate)
            item.addView(label)
            navIcons.add(icon); navLabels.add(label); navPlates.add(plate)
            navItemContainers.add(item)
            nav.addView(item)
        }
        return nav
    }

    private fun buildMiniPlayer(): LinearLayout {
        val m = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_mini_player)
            setPadding(dp(8), dp(8), dp(6), dp(8))
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.ABOVE, bottomNavBar.id)
                setMargins(dp(12), 0, dp(12), dp(8))
            }
            isClickable = true
            setOnClickListener { switchTab(0) }
            addPressFeedback(0.98f)
            visibility = View.GONE
        }
        miniCover = CoverView(this, 44, 10).apply {
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(12) }
        }
        val meta = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        miniTitle = TextView(this).apply {
            text = "Sin reproducción"
            textSize = 14f
            setTextColor(Color.WHITE)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
        }
        miniArtist = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#CBD5E1"))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        meta.addView(miniTitle)
        meta.addView(miniArtist)
        fun miniBtn(res: Int, onClick: () -> Unit) = ImageButton(this).apply {
            setImageResource(res)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE)
            val p = dp(9)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            setOnClickListener { onClick() }
            addPressFeedback(0.88f)
        }
        miniPlayBtn = miniBtn(R.drawable.ic_play_filled) { togglePlayPause() }
        miniNextBtn = miniBtn(R.drawable.ic_skip_next) { playNext() }
        m.addView(miniCover)
        m.addView(meta)
        m.addView(miniPlayBtn)
        m.addView(miniNextBtn)
        return m
    }

    private fun switchTab(index: Int) {
        currentTab = index
        val views = listOf(
            playerTabContainer, localFilesTabContainer, playlistTabContainer,
            onlineTabContainer, soundTabContainer, widgetTabContainer
        )
        for (i in views.indices) {
            views[i].visibility = if (i == index) View.VISIBLE else View.GONE
            val on = i == index
            navIcons[i].setColorFilter(Color.parseColor(if (on) Pal.ACCENT else Pal.MUTED))
            navLabels[i].setTextColor(Color.parseColor(if (on) Pal.ACCENT else Pal.MUTED))
            navPlates[i].background = if (on) GradientDrawable().apply {
                setColor(Color.parseColor(Pal.ACCENT) and 0x00FFFFFF or 0x2A000000)
                cornerRadius = dpf(15f)
            } else null
        }
        headerTitle.text = tabTitles[index]
        miniPlayerRoot.visibility = if (index == 0 || tracks.isEmpty() || isLandscapeMode) View.GONE else View.VISIBLE
        applyOrientationLayout(isLandscapeMode)
        keepScreenOnIfNeeded()
        currentFocus?.let { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(it.windowToken, 0) }
    }

    private fun showOtaUpdaterSheet() {
        showSheet(fullHeight = false) { root, dialog ->
            root.addView(OtaUpdateEngine.buildUpdaterPanel(this) {
                dialog.dismiss()
            })
        }
    }

    private fun openLibrarySearch() {
        switchTab(1)
        libSearch.requestFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(libSearch, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun cycleVisualizerMode() {
        val next = (visualizerStripView.visualMode + 1) % VisualizerView.MODE_NAMES.size
        visualizerStripView.visualMode = next
        visualizerView.visualMode = (next + 2) % VisualizerView.MODE_NAMES.size
        val modeName = VisualizerView.MODE_NAMES[next].uppercase()
        visualizerModeBadge.text = "$modeName \u2022 TOCA PARA CAMBIAR"
    }

    private fun cycleAbLoop() {
        val t = tracks.getOrNull(currentTrackIndex) ?: run { toastNoTrack(); return }
        val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
        when {
            loopStartMs < 0L -> {
                loopStartMs = pos
                loopEndMs = -1L
                loopPill.setLabel("A: ${formatClock(loopStartMs)}")
                loopPill.setActive(true)
                Toast.makeText(this, "Punto A fijado (${formatClock(loopStartMs)}). Toca de nuevo para fijar B.", Toast.LENGTH_SHORT).show()
            }
            loopEndMs < 0L -> {
                if (pos <= loopStartMs + 800L) {
                    val dur = (exoPlayer.duration.takeIf { it > 0 } ?: t.durationMs).coerceAtLeast(loopStartMs + 5000L)
                    loopEndMs = (loopStartMs + 10_000L).coerceAtMost(dur)
                } else {
                    loopEndMs = pos
                }
                loopPill.setLabel("A-B ${formatClock(loopStartMs)}-${formatClock(loopEndMs)}")
                loopPill.setActive(true)
                exoPlayer.seekTo(loopStartMs)
                Toast.makeText(this, "Bucle A-B activo", Toast.LENGTH_SHORT).show()
            }
            else -> {
                clearAbLoop(silent = false)
            }
        }
    }

    private fun clearAbLoop(silent: Boolean) {
        loopStartMs = -1L
        loopEndMs = -1L
        if (::loopPill.isInitialized) {
            loopPill.setLabel("Loop A-B")
            loopPill.setActive(false)
        }
        if (!silent) Toast.makeText(this, "Bucle A-B desactivado", Toast.LENGTH_SHORT).show()
    }

    private fun enrichSingleTrack(track: TrackItem) {
        Toast.makeText(this, "Buscando carátula HD y metadatos...", Toast.LENGTH_SHORT).show()
        Thread {
            val ok = StudioCatalog.enrichTrack(this, track)
            runOnUiThread {
                if (ok) {
                    for (item in (localTracksList + tracks).filter { it.key == track.key }) {
                        item.title = track.title
                        item.artist = track.artist
                        item.album = track.album
                        item.coverUrl = track.coverUrl
                    }
                    bindNowPlaying()
                    refreshLibrary()
                    refreshPlaylists()
                    Toast.makeText(this, "Carátula HD (1000x1000) y metadatos aplicados", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "No se encontraron coincidencias en el catálogo", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachPlayerGestures(targetView: View) {
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                val absX = kotlin.math.abs(diffX)
                val absY = kotlin.math.abs(diffY)

                if (absX > absY && absX > dp(40) && kotlin.math.abs(velocityX) > 200) {
                    targetView.parent?.requestDisallowInterceptTouchEvent(true)
                    if (isLandscapeMode || fullscreenWindowId != null) {
                        if (Haptics.enabled) targetView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        cycleFullscreenWindow(forward = (diffX < 0))
                        return true
                    }
                    if (diffX < 0) {
                        // Swipe Left -> Next
                        if (Haptics.enabled) targetView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        playNext()
                        showVizHudBadge("Siguiente pista →")
                    } else {
                        // Swipe Right -> Previous
                        if (Haptics.enabled) targetView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        playPrevious()
                        showVizHudBadge("← Pista anterior")
                    }
                    return true
                } else if (absY > absX && absY > dp(50) && kotlin.math.abs(velocityY) > 200) {
                    targetView.parent?.requestDisallowInterceptTouchEvent(true)
                    if (diffY < 0) {
                        // Swipe Up -> Queue
                        if (Haptics.enabled) targetView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        showQueueSheet()
                    } else {
                        // Swipe Down -> Track Info
                        if (Haptics.enabled) targetView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        tracks.getOrNull(currentTrackIndex)?.let {
                            showTrackDetails(it)
                        } ?: toastNoTrack()
                    }
                    return true
                }
                return false
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val w = targetView.width.toFloat()
                if (w <= 0f) return false
                val ratio = e.x / w
                val curPos = exoPlayer.currentPosition
                val duration = exoPlayer.duration.takeIf { it > 0 } ?: 100000L
                if (ratio < 0.35f) {
                    // Double tap left -> -10s
                    val target = (curPos - 10000L).coerceAtLeast(0L)
                    exoPlayer.seekTo(target)
                    if (Haptics.enabled) targetView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    Toast.makeText(this@MainActivity, "\u23EA -10s (${formatClock(target)})", Toast.LENGTH_SHORT).show()
                } else if (ratio > 0.65f) {
                    // Double tap right -> +10s
                    val target = (curPos + 10000L).coerceAtMost(duration)
                    exoPlayer.seekTo(target)
                    if (Haptics.enabled) targetView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    Toast.makeText(this@MainActivity, "\u23E9 +10s (${formatClock(target)})", Toast.LENGTH_SHORT).show()
                } else {
                    // Double tap center -> play/pause
                    togglePlayPause()
                }
                return true
            }
        })

        val scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            private var currentScale = 1.0f

            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                currentScale = 1.0f
                targetView.parent?.requestDisallowInterceptTouchEvent(true)
                targetView.translationZ = dpf(16f)
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                currentScale = (currentScale * detector.scaleFactor).coerceIn(0.40f, 2.80f)
                targetView.scaleX = currentScale
                targetView.scaleY = currentScale
                val label = String.format(java.util.Locale.US, "%.1fx", currentScale)
                showVizHudBadge("Escala: $label")
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                targetView.parent?.requestDisallowInterceptTouchEvent(false)
                targetView.animate().translationZ(0f).setDuration(220).start()
                if (currentScale >= 2.3f) {
                    currentScale = 1.0f
                    targetView.scaleX = 1.0f
                    targetView.scaleY = 1.0f
                    if (fullscreenWindowId == null) {
                        fullscreenWindowId = "cover"
                        showVizHudBadge("Pantalla Completa: Portada & Video")
                        reorderStageContainers()
                    }
                } else if (currentScale <= 0.48f) {
                    currentScale = 1.0f
                    targetView.scaleX = 1.0f
                    targetView.scaleY = 1.0f
                    if (fullscreenWindowId != null) {
                        fullscreenWindowId = null
                        showVizHudBadge("Cuadrícula Restaurada")
                        reorderStageContainers()
                    } else {
                        isCoverMinimizedPip = !isCoverMinimizedPip
                        showVizHudBadge(if (isCoverMinimizedPip) "Portada minimizada a PIP" else "Portada restaurada")
                        reorderStageContainers()
                    }
                } else if (currentScale in 0.92f..1.08f) {
                    currentScale = 1.0f
                    targetView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(180).start()
                }
            }
        })

        targetView.setOnTouchListener { v, event ->
            if (event.pointerCount >= 2) {
                scaleDetector.onTouchEvent(event)
                if (scaleDetector.isInProgress) return@setOnTouchListener true
            }
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                v.parent?.requestDisallowInterceptTouchEvent(true)
            }
            val handled = gestureDetector.onTouchEvent(event)
            if (!handled && (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL)) {
                v.parent?.requestDisallowInterceptTouchEvent(false)
            }
            handled || v.onTouchEvent(event)
        }
    }

    private fun cycleStageRightMode() {
        stageRightMode = (stageRightMode + 1) % 3
        SonoraStore.putInt(this, "stage_right_mode", stageRightMode)
        when (stageRightMode) {
            0 -> {
                showStageLyrics = true
                showStageViz = true
            }
            1 -> {
                showStageLyrics = false
                showStageViz = true
            }
            2 -> {
                showStageLyrics = true
                showStageViz = false
            }
        }
        SonoraStore.putBool(this, "stage_show_lyrics", showStageLyrics)
        SonoraStore.putBool(this, "stage_show_viz", showStageViz)
        updateStageLayout()
    }

    private fun showGifCoverOptionsSheet() {
        val t = tracks.getOrNull(currentTrackIndex) ?: run { toastNoTrack(); return }
        showSheet(fullHeight = true) { root, dialog ->
            val acc = try { Color.parseColor(Pal.ACCENT) } catch (_: Exception) { Color.parseColor("#38BDF8") }
            val onAcc = Pal.contrastOn(acc)

            root.addView(
                sheetTitle(
                    "Portada Completa Sin Recortes (DDMC)",
                    "Acepta cualquier tamaño (624x624, 87594275x1, 16:9, vertical) y formato (GIF, Video, WebP, PNG, JPG, HEIC, AVIF)"
                )
            )

            // 1. Huge Uncropped Full-Frame Cover Preview right at the top!
            val previewWrap = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(256)
                ).apply {
                    topMargin = dp(4)
                    bottomMargin = dp(12)
                }
            }
            val bigCover = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(248),
                    Gravity.CENTER
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(Pal.SURFACE_HI))
                    cornerRadius = dpf(22f)
                }
                clipToOutline = true
                outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
                elevation = dpf(8f)
            }
            bigCover.bindCover(t, 720)
            previewWrap.addView(bigCover)
            root.addView(previewWrap)

            // 2. Toggle Expanded Cover in Main Player Stage
            val expandStageBtn = Pill(
                this,
                if (isStageCoverExpanded) "Portada Grande Fijada en Escenario (Toca para compactar)"
                else "Expandir Portada en Grande dentro del Escenario",
                R.drawable.ic_album,
                40
            ).apply {
                setActive(isStageCoverExpanded)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(10) }
                setOnClickListener {
                    isStageCoverExpanded = !isStageCoverExpanded
                    showStageCover = true
                    SonoraStore.putBool(this@MainActivity, "stage_cover_expanded", isStageCoverExpanded)
                    SonoraStore.putBool(this@MainActivity, "stage_show_cover", true)
                    updateStageLayout()
                    dialog.dismiss()
                    Toast.makeText(
                        this@MainActivity,
                        if (isStageCoverExpanded) "Portada expandida en el escenario principal" else "Portada en modo compacto",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            root.addView(expandStageBtn)

            // Looping video cover
            root.addView(Pill(
                this,
                "Elegir Video en Bucle (Sin Audio)",
                R.drawable.ic_video,
                40
            ).apply {
                setActive(true)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }
                setOnClickListener {
                    dialog.dismiss()
                    pendingVideoTrack = t
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "video/*"
                    }
                    videoCoverPickerLauncher.launch(intent)
                }
            })

            val curTrackVideo = SonoraStore.getString(this, "cover_video_${t.key}", "")
            val activeGlobalVideo = SonoraStore.getString(this, "cover_video_active", "")
            if (!curTrackVideo.isNullOrEmpty() || !activeGlobalVideo.isNullOrEmpty()) {
                root.addView(Pill(
                    this,
                    "Quitar Video en Bucle",
                    R.drawable.ic_trash,
                    36
                ).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(8) }
                    setOnClickListener {
                        dialog.dismiss()
                        SonoraStore.putString(this@MainActivity, "cover_video_${t.key}", "")
                        SonoraStore.putString(this@MainActivity, "cover_video_active", "")
                        updateStageCoverVideo(null)
                        bindNowPlaying()
                        Toast.makeText(this@MainActivity, "Video de portada eliminado", Toast.LENGTH_SHORT).show()
                    }
                })
            }

            // 3. Pick any photo/animated format from device (GIF, WebP, PNG, JPG, HEIC, AVIF, BMP, ICO)
            root.addView(Pill(
                this,
                "Elegir Foto / Animación (GIF, WebP, PNG, JPG, HEIC, AVIF, BMP)",
                R.drawable.ic_folder,
                40
            ).apply {
                setActive(true)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }
                setOnClickListener {
                    dialog.dismiss()
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                        putExtra(Intent.EXTRA_MIME_TYPES, GifImageSupport.SUPPORTED_IMAGE_MIMES)
                    }
                    gifCoverPickerLauncher.launch(intent)
                }
            })

            // 4. Paste direct URL of an image / GIF / WebP
            val urlBox = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }
            }
            val urlInput = EditText(this).apply {
                hint = "Pegar URL de foto/GIF (https://...jpg, .png, .gif, .webp)"
                setHintTextColor(Color.parseColor(Pal.MUTED))
                setTextColor(Color.WHITE)
                textSize = 12.5f
                isSingleLine = true
                setBackgroundResource(R.drawable.bg_input_dark)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) }
            }
            val applyUrlBtn = Pill(this, "Usar URL", R.drawable.ic_check, 38).apply {
                setOnClickListener {
                    val u = urlInput.text.toString().trim()
                    if (u.startsWith("http://") || u.startsWith("https://")) {
                        t.coverBitmap = null
                        t.coverUrl = u
                        SonoraStore.putString(this@MainActivity, "cover_gif_${t.key.hashCode()}", u)
                        SonoraStore.saveEdit(this@MainActivity, t)
                        for (item in (localTracksList + tracks).filter { it.key == t.key }) {
                            item.coverBitmap = null
                            item.coverUrl = u
                        }
                        bindNowPlaying()
                        dialog.dismiss()
                        Toast.makeText(this@MainActivity, "Portada por URL aplicada", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "Pega un enlace http:// o https:// válido", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            urlBox.addView(urlInput)
            urlBox.addView(applyUrlBtn)
            root.addView(urlBox)

            // 5. Demo GIF & Restore Original row
            val bottomRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            bottomRow.addView(Pill(this, "GIF Animado Demo", R.drawable.ic_wand, 38).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) }
                setOnClickListener {
                    val gifFile = DemoGifGenerator.getOrCreateDemoGif(this@MainActivity, (System.currentTimeMillis() % 3).toInt())
                    val uri = Uri.fromFile(gifFile)
                    t.coverBitmap = null
                    t.coverUrl = uri.toString()
                    SonoraStore.putString(this@MainActivity, "cover_gif_${t.key.hashCode()}", uri.toString())
                    SonoraStore.saveEdit(this@MainActivity, t)
                    for (item in (localTracksList + tracks).filter { it.key == t.key }) {
                        item.coverBitmap = null
                        item.coverUrl = uri.toString()
                    }
                    val firstFrame = GifImageSupport.decodeFirstFrameFromUri(this@MainActivity, uri)
                    if (firstFrame != null) {
                        ThemeEngine.adaptFromBitmap(this@MainActivity, firstFrame)
                        applyAccentLive()
                    }
                    bindNowPlaying()
                    dialog.dismiss()
                    Toast.makeText(this@MainActivity, "Portada GIF animada aplicada", Toast.LENGTH_SHORT).show()
                }
            })
            bottomRow.addView(Pill(this, "Restaurar Original", R.drawable.ic_refresh, 38).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    t.coverBitmap = null
                    SonoraStore.putString(this@MainActivity, "cover_gif_${t.key.hashCode()}", "")
                    val mediaArt = if (t.audioUri != null) {
                        ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), 1L).toString()
                    } else ""
                    t.coverUrl = mediaArt
                    performStorageScan(showToast = false)
                    bindNowPlaying()
                    dialog.dismiss()
                    Toast.makeText(this@MainActivity, "Carátula original restaurada", Toast.LENGTH_SHORT).show()
                }
            })
            root.addView(bottomRow)
        }
    }

    private fun applyOrientationLayout(isLandscape: Boolean) {
        if (!::playerOuterLayout.isInitialized) return
        val onPlayerTab = currentTab == 0

        if (isLandscape && onPlayerTab) {
            // Hide chrome for full immersive 16:9 landscape priority
            topHeader.visibility = View.GONE
            bottomNavBar.visibility = View.GONE
            miniPlayerRoot.visibility = View.GONE

            (mainContentFrame.layoutParams as? RelativeLayout.LayoutParams)?.apply {
                removeRule(RelativeLayout.BELOW)
                removeRule(RelativeLayout.ABOVE)
                mainContentFrame.layoutParams = this
            }

            // Move unifiedStageBlock directly into playerOuterLayout (85% height)
            if (unifiedStageBlock.parent != playerOuterLayout) {
                (unifiedStageBlock.parent as? ViewGroup)?.removeView(unifiedStageBlock)
                playerOuterLayout.addView(unifiedStageBlock, 0)
            }
            portraitInnerBox.visibility = View.GONE
            portraitScrollContainer.visibility = View.GONE
            landscapeBottomBar.visibility = View.VISIBLE

            unifiedStageBlock.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.85f
            ).apply {
                setMargins(dp(10), dp(6), dp(10), dp(4))
            }
            unifiedStageBlock.setPadding(dp(14), dp(10), dp(14), 0)

            landscapeBottomBar.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.15f
            )
        } else {
            // Portrait (or non-player tab): Fill 100% (4/4) of vertical screen height
            topHeader.visibility = View.VISIBLE
            bottomNavBar.visibility = View.VISIBLE
            miniPlayerRoot.visibility = if (currentTab == 0 || tracks.isEmpty()) View.GONE else View.VISIBLE

            (mainContentFrame.layoutParams as? RelativeLayout.LayoutParams)?.apply {
                addRule(RelativeLayout.BELOW, topHeader.id)
                addRule(RelativeLayout.ABOVE, bottomNavBar.id)
                mainContentFrame.layoutParams = this
            }

            // Ensure unifiedStageBlock is inside portraitInnerBox right after portraitViewSelectorsRow
            if (unifiedStageBlock.parent != portraitInnerBox) {
                (unifiedStageBlock.parent as? ViewGroup)?.removeView(unifiedStageBlock)
                val idx = portraitInnerBox.indexOfChild(portraitViewSelectorsRow)
                val insertAt = if (idx >= 0) idx + 1 else 0
                portraitInnerBox.addView(unifiedStageBlock, insertAt)
            }
            portraitInnerBox.visibility = View.VISIBLE
            portraitScrollContainer.visibility = View.VISIBLE
            landscapeBottomBar.visibility = View.GONE

            unifiedStageBlock.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            ).apply {
                topMargin = dp(2)
                bottomMargin = dp(6)
            }
            unifiedStageBlock.setPadding(dp(12), dp(10), dp(12), 0)
        }

        updateStageLayout()
        updateLyricsTick(exoPlayer.currentPosition.coerceAtLeast(0L))
    }

    private fun updateStageLayout() {
        if (!::unifiedStageBlock.isInitialized) return

        if (!MotionTokens.isReducedMotion(this) && unifiedStageBlock.isAttachedToWindow) {
            val transition = android.transition.AutoTransition().apply {
                duration = MotionTokens.DUR_FAST
                interpolator = MotionTokens.EASE_OUT
            }
            android.transition.TransitionManager.beginDelayedTransition(unifiedStageBlock, transition)
        }

        val acc = try { Color.parseColor(Pal.ACCENT) } catch (_: Exception) { Color.parseColor("#38BDF8") }
        val onAcc = Pal.contrastOn(acc)

        fun styleToggleIcon(btn: ImageView, iconRes: Int, on: Boolean) {
            btn.setImageResource(iconRes)
            btn.background = GradientDrawable().apply {
                cornerRadius = dpf(20f)
                if (on) {
                    setColor(acc)
                } else {
                    setColor(Color.parseColor("#18FFFFFF"))
                }
            }
            btn.setColorFilter(if (on) onAcc else Color.parseColor(Pal.SUB))
        }

        if (::toggleCoverBtn.isInitialized) styleToggleIcon(toggleCoverBtn, R.drawable.ic_album, showStageCover)
        if (::toggleExpandCoverBtn.isInitialized) toggleExpandCoverBtn.visibility = View.GONE
        if (::toggleStageVideoChip.isInitialized) styleToggleIcon(toggleStageVideoChip, R.drawable.ic_video, isVideoMode)
        if (::toggleLyricsBtn.isInitialized) styleToggleIcon(toggleLyricsBtn, R.drawable.ic_lyrics, showStageLyrics)
        if (::toggleDokiBtn.isInitialized) styleToggleIcon(toggleDokiBtn, R.drawable.ic_sparkles, showStageDoki)
        if (::toggleVizBtn.isInitialized) styleToggleIcon(toggleVizBtn, R.drawable.ic_waveform, showStageViz)
        if (::stageModeSwitchChip.isInitialized) stageModeSwitchChip.visibility = View.GONE

        if (::coverWindowFrame.isInitialized) {
            // Under StageWindowFrame architecture, all window contents fill their frame container
            if (::stageExpandedCoverCard.isInitialized) {
                stageExpandedCoverCard.layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            if (::stageVideoCard.isInitialized) {
                stageVideoCard.layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            if (::vizDeckCard.isInitialized) {
                vizDeckCard.layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            if (::stageLyricsScroll.isInitialized) {
                stageLyricsScroll.layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            if (::dokiLeftCol.isInitialized) {
                dokiLeftCol.layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            return
        }

        // Cover visibility (stage mini inset cover, full-width expanded cover card, & portrait lower metadata cover thumbnail)
        if (::stageCoverInsetCard.isInitialized) {
            stageCoverInsetCard.visibility = if (showStageCover && !isStageCoverExpanded && !isVideoMode) View.VISIBLE else View.GONE
        }
        if (::stageExpandedCoverCard.isInitialized) {
            val showBigCover = showStageCover && isStageCoverExpanded && !isVideoMode
            stageExpandedCoverCard.visibility = if (showBigCover) View.VISIBLE else View.GONE
            if (showBigCover) {
                val expandOnly = !showStageLyrics && !showStageDoki && !showStageViz
                val customCoverH = SonoraStore.getInt(this, "player_cover_height_dp", 228)
                val targetHeightDp = when {
                    isLandscapeMode -> if (stageMediaXL) 195 else 150
                    stageMediaXL -> 320
                    else -> customCoverH
                }
                stageExpandedCoverCard.layoutParams = if (expandOnly) {
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                        topMargin = dp(2)
                        bottomMargin = dp(6)
                    }
                } else {
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(targetHeightDp)).apply {
                        topMargin = dp(2)
                        bottomMargin = dp(6)
                    }
                }
            }
        }
        if (::portraitMetaCoverThumb.isInitialized) {
            portraitMetaCoverThumb.visibility = if (showStageCover) View.VISIBLE else View.GONE
        }

        // Full-Width Video Stage Card visibility (using native ExoPlayer PlayerView)
        if (::stageVideoCard.isInitialized) {
            if (isVideoMode) {
                stageVideoCard.visibility = View.VISIBLE
                stageVideoCard.alpha = 1f
                val videoFillAll = !showStageLyrics && !showStageDoki && !showStageViz
                val targetVideoHeightDp = when {
                    isLandscapeMode -> if (stageMediaXL) 215 else 170
                    stageMediaXL -> 335
                    else -> 240
                }
                stageVideoCard.layoutParams = if (videoFillAll) {
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                        topMargin = dp(2)
                        bottomMargin = dp(6)
                    }
                } else {
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(targetVideoHeightDp)).apply {
                        topMargin = dp(2)
                        bottomMargin = dp(6)
                    }
                }
            } else {
                stageVideoCard.visibility = View.GONE
            }
        }

        // Left Column (Doki / Shimeji Companion):
        // If Chibi Shimeji mode: runs full-screen via shimejiFullScreenOverlay, so dokiLeftCol is GONE and stage gets full width.
        // If WebP VN sprite: confined to dokiLeftCol inside the stage.
        val isChibi = if (::dokiSpriteView.isInitialized) dokiSpriteView.isChibiMode else false
        val showDokiInStage = showStageDoki && !isChibi
        if (::dokiLeftCol.isInitialized && ::lyricsRightCol.isInitialized) {
            val rightVisible = showStageLyrics || showStageViz || (!isStageCoverExpanded && showStageCover)
            dokiLeftCol.visibility = if (showDokiInStage) View.VISIBLE else View.GONE
            lyricsRightCol.visibility = if (rightVisible) View.VISIBLE else View.GONE
            stageColumnsRow.visibility = if (showDokiInStage || rightVisible) View.VISIBLE else View.GONE

            val savedWeight = SonoraStore.getFloat(this, "stage_doki_weight", if (isLandscapeMode) 0.35f else 0.40f)
            val leftWeight = savedWeight.coerceIn(0.20f, 0.80f)
            val rightWeight = 1.0f - leftWeight

            when {
                showDokiInStage && rightVisible -> {
                    dokiLeftCol.layoutParams = LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.MATCH_PARENT, leftWeight
                    ).apply { marginEnd = dp(6) }
                    lyricsRightCol.layoutParams = LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.MATCH_PARENT, rightWeight
                    )
                }
                showDokiInStage && !rightVisible -> {
                    dokiLeftCol.layoutParams = LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f
                    )
                }
                else -> {
                    lyricsRightCol.layoutParams = LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f
                    )
                }
            }
        }

        if (::shimejiFullScreenOverlay.isInitialized) {
            shimejiFullScreenOverlay.visibility = if (showStageDoki && isChibi) View.VISIBLE else View.GONE
        }

        // Right Column: Live Karaoke Lyrics + Audio Visualizer Card
        if (::stageLyricsScroll.isInitialized && ::vizDeckCard.isInitialized) {
            val showLyricsNow = showStageLyrics
            stageLyricsScroll.visibility = if (showLyricsNow) View.VISIBLE else View.GONE
            vizDeckCard.visibility = if (showStageViz) View.VISIBLE else View.GONE
            visualizerStripView.visibility = if (showStageViz) View.VISIBLE else View.GONE

            val vizPos = SonoraStore.getInt(this, "stage_viz_position", 0)
            val savedVizH = SonoraStore.getInt(this, "player_viz_height_dp", if (isLandscapeMode) 64 else 76)
            val vizH = dp(savedVizH)

            if (vizPos == 2 || vizPos == 3) {
                // Full stage width dock
                vizDeckCard.layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, vizH
                ).apply {
                    topMargin = dp(4)
                    bottomMargin = dp(4)
                }
                stageLyricsScroll.layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
                )
            } else if (showStageViz && !showLyricsNow) {
                vizDeckCard.layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
                ).apply {
                    topMargin = dp(4)
                    bottomMargin = dp(8)
                }
            } else {
                stageLyricsScroll.layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
                )
                vizDeckCard.layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, vizH
                ).apply {
                    topMargin = dp(4)
                    bottomMargin = dp(8)
                }
                vizDeckCard.translationX = SonoraStore.getFloat(this, "layout_viz_tx", 0f)
            }
        }

        if (::visualizerView.isInitialized) {
            visualizerView.visibility = if (showStageViz && !isVideoMode) View.VISIBLE else View.INVISIBLE
        }
    }

    private fun showThemeCustomizerSheet() {
        showSheet(fullHeight = false) { root, dialog ->
            root.addView(ThemeEngine.buildPanel(this) {
                applyAccentLive()
                bindNowPlaying()
                refreshLibrary()
                refreshPlaylists()
                SonoraWidgetProvider.updateAllWidgets(this)
                dialog.dismiss()
                Toast.makeText(this, "Tema aplicado con éxito", Toast.LENGTH_SHORT).show()
            })
        }
    }

    // ==========================================================
    // TAB 0: NOW PLAYING ("DDMC" - PORTRAIT & LANDSCAPE ADAPTIVE)
    // ==========================================================
    private fun buildPlayerTab(): View {
        playerOuterLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        portraitScrollContainer = ScrollView(this)

        portraitInnerBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
            setPadding(dp(12), dp(2), dp(12), dp(6))
        }

        // ---- 1. Barra de Herramientas Desplazable (Pills) ----
        portraitToolsScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
        }
        val tools = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(4))
        }
        fun tool(label: String, icon: Int, onClick: () -> Unit) = Pill(this, label, icon, 33).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(7) }
            setOnClickListener { onClick() }
        }
        speedBtn = tool("1.0x", R.drawable.ic_bolt) { showSpeedSheet() }
        sleepPill = tool("Temporizador", R.drawable.ic_timer) { showSleepTimerSheet() }
        loopPill = tool("Loop A-B", R.drawable.ic_repeat) { cycleAbLoop() }

        tools.addView(tool("Cola", R.drawable.ic_list_music) { showQueueSheet() })
        tools.addView(tool("YouTube", R.drawable.ic_video) { switchTab(3) })
        tools.addView(tool("Portada HD/GIF", R.drawable.ic_album) { showGifCoverOptionsSheet() })
        tools.addView(tool("Espectro", R.drawable.ic_sliders) {
            tracks.getOrNull(currentTrackIndex)?.let { showSpectrumAnalyzerSheet(it) } ?: toastNoTrack()
        })
        tools.addView(tool("Auto-Tag HD", R.drawable.ic_wand) {
            tracks.getOrNull(currentTrackIndex)?.let { enrichSingleTrack(it) } ?: toastNoTrack()
        })
        tools.addView(loopPill)
        tools.addView(speedBtn)
        tools.addView(sleepPill)
        tools.addView(tool("Actualizar PC/Git", R.drawable.ic_download) { showOtaUpdaterSheet() })
        tools.addView(tool("Letra", R.drawable.ic_lyrics) { showLyricsSheet() })
        tools.addView(tool("Cortar", R.drawable.ic_scissors) {
            tracks.getOrNull(currentTrackIndex)?.let { showAudioTrimmerDialog(it) } ?: toastNoTrack()
        })
        tools.addView(tool("Etiquetas", R.drawable.ic_pencil) {
            tracks.getOrNull(currentTrackIndex)?.let { showTagEditorDialog(it) } ?: toastNoTrack()
        })
        tools.addView(tool("Compartir", R.drawable.ic_share) {
            tracks.getOrNull(currentTrackIndex)?.let { shareTrack(it) } ?: toastNoTrack()
        })
        tools.addView(tool("Tema/Color", R.drawable.ic_palette) { showThemeCustomizerSheet() })
        portraitToolsScroll.addView(tools)
        // Kept initialized for overflow/tools sheets, but not cluttering main player screen
        // portraitInnerBox.addView(portraitToolsScroll)

        // ---- 2. Selectores de Vista limpios con ICONOS VECTORIALES (Sin texto, sin emojis) ----
        portraitViewSelectorsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(4))
        }

        fun makeToggleIconBtn(iconRes: Int, onToggle: () -> Unit) = ImageView(this).apply {
            setImageResource(iconRes)
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(8) }
            setPadding(dp(9), dp(9), dp(9), dp(9))
            isClickable = true
            isFocusable = true
            setOnClickListener { onToggle() }
            addPressFeedback(0.94f)
        }

        toggleCoverBtn = makeToggleIconBtn(R.drawable.ic_album) {
            showStageCover = !showStageCover
            SonoraStore.putBool(this, "stage_show_cover", showStageCover)
            updateStageLayout()
        }
        toggleExpandCoverBtn = makeToggleIconBtn(R.drawable.ic_album) {
            stageMediaXL = !stageMediaXL
            SonoraStore.putBool(this, "stage_media_xl", stageMediaXL)
            updateStageLayout()
        }
        toggleStageVideoChip = makeToggleIconBtn(R.drawable.ic_video) {
            val cur = tracks.getOrNull(currentTrackIndex)
            if (cur == null) {
                switchTab(3)
                return@makeToggleIconBtn
            }
            if (cur.videoUrl.isEmpty() && !cur.provider.contains("YouTube", true)) {
                playTrackOnYouTube("${cur.artist} ${cur.title}", watchVideo = true)
            } else {
                isVideoMode = !isVideoMode
                updateMode()
                updateStageLayout()
            }
        }
        toggleLyricsBtn = makeToggleIconBtn(R.drawable.ic_lyrics) {
            showStageLyrics = !showStageLyrics
            SonoraStore.putBool(this, "stage_show_lyrics", showStageLyrics)
            updateStageLayout()
        }
        toggleDokiBtn = makeToggleIconBtn(R.drawable.ic_sparkles) {
            showStageDoki = !showStageDoki
            SonoraStore.putBool(this, "stage_show_doki", showStageDoki)
            updateStageLayout()
        }
        toggleVizBtn = makeToggleIconBtn(R.drawable.ic_waveform) {
            showStageViz = !showStageViz
            SonoraStore.putBool(this, "stage_show_viz", showStageViz)
            updateStageLayout()
        }

        val toggleChipsScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val chipsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(toggleCoverBtn)
            addView(toggleStageVideoChip)
            addView(toggleLyricsBtn)
            addView(toggleDokiBtn)
            addView(toggleVizBtn)
            addView(makeToggleIconBtn(R.drawable.ic_widgets) { showPlayerLayoutCustomizerSheet() })
            addView(makeToggleIconBtn(R.drawable.ic_sliders) { showVizSlotsSheet() })
            addView(makeToggleIconBtn(R.drawable.ic_disc) { tracks.getOrNull(currentTrackIndex)?.let { showTrackAudioCustomizerDialog(it) } ?: toastNoTrack() })
            addView(makeToggleIconBtn(R.drawable.ic_sparkles) { showQuickDokiSwitcherSheet() })
            addView(makeToggleIconBtn(R.drawable.ic_download) { showMusicoletImportDialog() })
            addView(makeToggleIconBtn(R.drawable.ic_sliders) { showTabBarPositionDialog() })
        }
        toggleChipsScroll.addView(chipsContainer)
        portraitViewSelectorsRow.addView(toggleChipsScroll)
        portraitInnerBox.addView(portraitViewSelectorsRow)

        // ====================================================================
        // 3. CONTENEDOR CENTRAL INMERSIVO (Portrait Full-Height 1f & Landscape 16:9 Adaptive)
        // Top Zone (100% Stage Width): Large Uncropped Cover/GIF Card (228dp/320dp) OR Large Video Player Card (240dp/335dp)
        // Bottom Zone: Left Column (PinkChan Chibi Shimeji / HD Doki) + Right Column (Live Lyrics + Audio Visualizer)
        // ====================================================================
        unifiedStageBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            ).apply {
                topMargin = dp(2)
                bottomMargin = dp(4)
            }
            background = null
            setPadding(dp(8), dp(4), dp(8), 0)
        }

        stageColumnsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            clipChildren = false
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }

        // ---- COLUMNA IZQUIERDA: PinkChan Interactive Chibi Shimeji & Cropped HD DDSC Doki ----
        dokiLeftCol = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 0.39f
            ).apply { marginEnd = dp(6) }
            clipChildren = false
            clipToPadding = false
        }

        dokiSpriteView = DokiStageSpriteView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            )
        }

        // Safe initialized references for badges (removed from screen to avoid clutter)
        dokiActionBadge = TextView(this)
        dokiFloatingBadge = TextView(this)

        // Keep legacy references initialized so existing methods stay safe
        playerDokiCompanion = ImageView(this)
        playerDokiName = TextView(this)
        dokiPoseSubtext = TextView(this)
        dokiStageCard = LinearLayout(this)
        mediaStageCol = LinearLayout(this)
        companionLyricsCol = LinearLayout(this)
        lyricCard = LinearLayout(this)
        lyricPrev = TextView(this)
        lyricCur = TextView(this)
        lyricNext = TextView(this)

        dokiLeftCol.addView(dokiSpriteView)

        // ---- COLUMNA DERECHA: Letras en vivo + Visualizador ----
        lyricsRightCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 0.61f
            )
            setPadding(dp(4), 0, dp(2), 0)
        }

        // Keep legacy mini header initialized but borderless and detached so lyrics have full vertical height
        stageCoverInsetCard = FrameLayout(this)
        stageContainer = stageCoverInsetCard
        ambientGlowImage = ImageView(this)
        visualizerView = VisualizerView(this)
        coverImage = ImageView(this)
        stageModeSwitchChip = TextView(this)
        visualizerModeBadge = TextView(this)

        // ---- FULL-STAGE-WIDTH LARGE COVER CARD (FIT_CENTER UNCROPPED - 228dp Grande / 320dp XL - BORDERLESS) ----
        stageExpandedCoverCard = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(228)
            ).apply {
                topMargin = dp(2)
                bottomMargin = dp(4)
            }
            background = null
            visibility = View.VISIBLE
        }
        stageExpandedCoverImage = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            isClickable = true
            setOnClickListener { showGifCoverOptionsSheet() }
            setOnLongClickListener {
                stageMediaXL = !stageMediaXL
                SonoraStore.putBool(this@MainActivity, "stage_media_xl", stageMediaXL)
                updateStageLayout()
                Toast.makeText(this@MainActivity, if (stageMediaXL) "Portada XL" else "Portada Grande", Toast.LENGTH_SHORT).show()
                true
            }
        }
        stageExpandedCoverCard.addView(stageExpandedCoverImage)

        // ---- DEDICATED FULL-STAGE-WIDTH VIDEO CONTAINER (240dp Grande / 335dp XL - BORDERLESS) ----
        stageVideoCard = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(240)
            ).apply {
                topMargin = dp(2)
                bottomMargin = dp(4)
            }
            background = null
            visibility = View.GONE
        }
        playerView = PlayerView(this).apply {
            player = exoPlayer
            useController = true
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
        }
        youtubeStageWebView = YouTubeStageWebView(this)
        stageVideoCard.addView(playerView)

        // Live Synchronized Karaoke Lyrics ScrollView (Left-Aligned, Bold Sans-Serif, #FFFFFF active & 0.45 opacity inactive)
        stageLyricsScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        stageLyricsBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setPadding(dp(2), dp(8), dp(6), dp(14))
        }
        stageLyricViews.clear()
        for (i in 0 until 11) {
            val tv = TextView(this).apply {
                gravity = Gravity.START
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                setTypeface(Typeface.create("sans-serif", Typeface.BOLD))
                setTextColor(Color.WHITE)
                val isCenter = i == 3
                textSize = if (isCenter) 19.5f else 14.5f
                letterSpacing = if (isCenter) -0.015f else -0.005f
                alpha = if (isCenter) 1.0f else 0.45f
                pivotX = 0f
                setLineSpacing(dpf(2.5f), 1.10f)
                setPadding(0, dp(5), dp(4), dp(5))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                isClickable = true
                setOnClickListener { showLyricsSheet() }
            }
            stageLyricViews.add(tv)
            stageLyricsBox.addView(tv)
        }
        stageLyricViews[3].text = "Letras en vivo sincronizadas"
        stageLyricViews[4].text = "Reproduce música local o de YouTube"

        stageLyricsScroll.addView(stageLyricsBox)
        lyricsRightCol.addView(stageLyricsScroll)

        // Dedicated Audio Visualizer Card inside Right Column (Borderless)
        vizDeckCard = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(76)
            ).apply {
                topMargin = dp(4)
                bottomMargin = dp(8)
            }
            background = null
            isClickable = true
            setOnClickListener { cycleVisualizerMode() }
            addPressFeedback(0.98f, haptic = true)
        }
        visualizerStripView = VisualizerView(this).apply {
            visualMode = 0 // Espectro Espejo Pro
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
        vizDeckCard.addView(visualizerStripView)
        // Slots removed from middle to keep screen clean and spacious
        lyricsRightCol.addView(vizDeckCard)

        stageColumnsRow.addView(dokiLeftCol)
        stageColumnsRow.addView(lyricsRightCol)

        // Add Full-Stage-Width Cover Card & Video Card at the TOP of unifiedStageBlock, above stageColumnsRow!
        unifiedStageBlock.addView(stageExpandedCoverCard)
        unifiedStageBlock.addView(stageVideoCard)
        unifiedStageBlock.addView(stageColumnsRow)
        portraitInnerBox.addView(unifiedStageBlock)

        // Attach gesture navigation to the stage
        attachCoverTouchControls(stageExpandedCoverCard, stageExpandedCoverImage)
        attachDokiTouchControls(dokiLeftCol, dokiSpriteView)
        attachVizTouchControls(vizDeckCard, visualizerStripView)
        attachPlayerGestures(unifiedStageBlock)
        attachPlayerGestures(stageLyricsScroll)

        toggleVideoBtn = Pill(this, "Video", R.drawable.ic_video).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(4)
            }
            visibility = View.GONE
            setOnClickListener { isVideoMode = !isVideoMode; updateMode() }
        }
        // portraitInnerBox.addView(toggleVideoBtn)

        // ====================================================================
        // 4. CONTROLES INFERIORES (Portrait Lower Controls)
        // Miniatura de carátula 64dp completa sin recortes + Título, artista, álbum, corazón, badges, seekbar y controles
        // ====================================================================
        portraitLowerControlsBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, 0)
        }

        // Larger 64dp Multi-Format & Uncropped Album Cover Thumbnail next to direct metadata
        portraitMetaCoverThumb = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(64)).apply { marginEnd = dp(12) }
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor(Pal.SURFACE_HI))
                cornerRadius = dpf(16f)
            }
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            elevation = dpf(4f)
            isClickable = true
            setOnClickListener { showGifCoverOptionsSheet() }
            addPressFeedback(0.95f)
        }

        val metaBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleText = TextView(this).apply {
            textSize = 20f
            letterSpacing = -0.02f
            setTextColor(Color.WHITE)
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
            isSelected = true
            marqueeRepeatLimit = -1
            isSingleLine = true
        }
        artistText = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor(Pal.SUB))
            setPadding(0, dp(2), 0, 0)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        albumText = TextView(this).apply {
            textSize = 11.5f
            setTextColor(Color.parseColor(Pal.MUTED))
            setPadding(0, dp(1), 0, 0)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        metaBox.addView(titleText)
        metaBox.addView(artistText)
        metaBox.addView(albumText)

        favBtn = ImageButton(this).apply {
            setImageResource(R.drawable.ic_heart)
            setBackgroundColor(Color.TRANSPARENT)
            val p = dp(10)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            setOnClickListener { toggleCurrentTrackFavorite() }
            addPressFeedback(0.94f)
        }
        titleRow.addView(portraitMetaCoverThumb)
        titleRow.addView(metaBox)
        titleRow.addView(favBtn)
        portraitLowerControlsBox.addView(titleRow)
        attachQueueSwipeUpGesture(titleRow)
        attachQueueSwipeUpGesture(portraitLowerControlsBox)

        // ---- Badges: MP3 · Local, BPM, Provider ----
        val badgesRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(2))
        }
        fun badge(color: String) = TextView(this).apply {
            textSize = 9.5f
            letterSpacing = 0.04f
            fontFeatureSettings = "tnum"
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor(color))
            setBackgroundResource(R.drawable.bg_card_spotiflac)
            setPadding(dp(9), dp(4), dp(9), dp(4))
            isClickable = true
            addPressFeedback(0.96f)
            setOnClickListener {
                tracks.getOrNull(currentTrackIndex)?.let { showSpectrumAnalyzerSheet(it) }
            }
        }
        formatBadgeText = badge(Pal.ACCENT)
        bpmKeyBadgeText = badge("#F59E0B").apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(6) }
        }
        providerBadgeText = badge(Pal.CYAN).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(6) }
        }
        badgesRow.addView(formatBadgeText)
        badgesRow.addView(bpmKeyBadgeText)
        badgesRow.addView(providerBadgeText)
        portraitLowerControlsBox.addView(badgesRow)

        // ---- SeekBar with times ----
        val progressBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, dp(2))
        }
        val seekListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) {
                    val fmt = formatClock(p.toLong())
                    currentTimeText.text = fmt
                    if (::landscapeCurTimeText.isInitialized) landscapeCurTimeText.text = fmt
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) { isSeeking = true }
            override fun onStopTrackingTouch(sb: SeekBar?) {
                isSeeking = false
                val target = (sb?.progress ?: 0).toLong()
                exoPlayer.seekTo(target)
                playerSeekBar.progress = target.toInt()
                if (::landscapeSeekBar.isInitialized) landscapeSeekBar.progress = target.toInt()
            }
        }

        playerSeekBar = SeekBar(this).apply {
            val accent = ColorStateList.valueOf(Color.parseColor(Pal.ACCENT))
            progressTintList = accent
            thumbTintList = accent
            progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#33FFFFFF"))
            setPadding(dp(4), dp(6), dp(4), dp(6))
            setOnSeekBarChangeListener(seekListener)
        }
        val timeLabels = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        currentTimeText = TextView(this).apply {
            text = "00:00"
            textSize = 11.5f
            fontFeatureSettings = "tnum"
            setTextColor(Color.parseColor(Pal.SUB))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        totalTimeText = TextView(this).apply {
            text = "00:00"
            textSize = 11.5f
            fontFeatureSettings = "tnum"
            setTextColor(Color.parseColor(Pal.SUB))
        }
        timeLabels.addView(currentTimeText)
        timeLabels.addView(totalTimeText)
        progressBox.addView(playerSeekBar)
        progressBox.addView(timeLabels)
        portraitLowerControlsBox.addView(progressBox)

        // ---- Main Transport Controls (Shuffle, Previous, Large Center Play/Pause, Next, Repeat) ----
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(6))
        }
        fun ctl(res: Int, size: Int, onClick: () -> Unit) = ImageButton(this).apply {
            setImageResource(res)
            setBackgroundColor(Color.TRANSPARENT)
            val p = dp(size / 4)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(dp(size), dp(size)).apply {
                marginStart = dp(6)
                marginEnd = dp(6)
            }
            setOnClickListener { onClick() }
            addPressFeedback(0.95f)
        }
        shuffleBtn = ctl(R.drawable.ic_shuffle, 44) { setShuffle(!isShuffle) }
        val prevBtn = ctl(R.drawable.ic_skip_prev, 54) { playPrevious() }
        playPauseBtn = ctl(R.drawable.ic_play_filled, 68) { togglePlayPause() }.apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(Pal.ACCENT))
            }
            val p = dp(18)
            setPadding(p, p, p, p)
            setColorFilter(Color.parseColor(Pal.ON_ACCENT))
        }
        val nextBtn = ctl(R.drawable.ic_skip_next, 54) { playNext() }
        repeatBtn = ctl(R.drawable.ic_repeat, 44) { cycleRepeat() }
        controls.addView(shuffleBtn)
        controls.addView(prevBtn)
        controls.addView(playPauseBtn)
        controls.addView(nextBtn)
        controls.addView(repeatBtn)
        portraitLowerControlsBox.addView(controls)

        portraitInnerBox.addView(portraitLowerControlsBox)

        // ====================================================================
        // 5. BARRA REPRODUCTORA COMPACTA EN MODO HORIZONTAL (LANDSCAPE 15% INFERIOR)
        // Translucent dark-cover-tinted strip with GIF Album Cover + Title/Artist on left,
        // compact playback controls over stylized seekbar on center/right.
        // ====================================================================
        landscapeBottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.parseColor(Pal.GRAD_DARKEST))
                setStroke(dp(1), Color.parseColor("#2EFFFFFF"))
            }
            visibility = View.GONE
        }

        landscapeCoverThumb = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginEnd = dp(12) }
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                setColor(Color.parseColor(Pal.SURFACE_HI))
                cornerRadius = dpf(10f)
                setStroke(dp(1), Color.parseColor("#38FFFFFF"))
            }
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            isClickable = true
            addPressFeedback(0.95f)
            setOnClickListener { showGifCoverOptionsSheet() }
        }

        val landMetaCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.36f).apply {
                marginEnd = dp(10)
            }
        }
        landscapeTitleText = TextView(this).apply {
            text = "Selecciona una canción"
            textSize = 14.5f
            letterSpacing = -0.01f
            setTextColor(Color.WHITE)
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        landscapeArtistText = TextView(this).apply {
            text = "Estudio"
            textSize = 11.5f
            setTextColor(Color.parseColor(Pal.SUB))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        landMetaCol.addView(landscapeTitleText)
        landMetaCol.addView(landscapeArtistText)

        landscapeFavBtn = ImageButton(this).apply {
            setImageResource(R.drawable.ic_heart)
            setBackgroundColor(Color.TRANSPARENT)
            val p = dp(7)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(8) }
            addPressFeedback(0.94f)
            setOnClickListener { toggleCurrentTrackFavorite() }
        }

        // Center/Right: Compact transport controls above stylized seekbar
        val landRightCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.64f)
        }

        val landBtnsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        fun landCtl(res: Int, size: Int, onClick: () -> Unit) = ImageButton(this).apply {
            setImageResource(res)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE)
            val p = dp(6)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(dp(size), dp(size)).apply {
                marginStart = dp(5)
                marginEnd = dp(5)
            }
            setOnClickListener { onClick() }
            addPressFeedback(0.95f)
        }
        landscapeShuffleBtn = landCtl(R.drawable.ic_shuffle, 32) { setShuffle(!isShuffle) }
        landscapePrevBtn = landCtl(R.drawable.ic_skip_prev, 34) { playPrevious() }
        landscapePlayPauseBtn = landCtl(R.drawable.ic_play_filled, 38) { togglePlayPause() }.apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(Pal.ACCENT))
            }
            setColorFilter(Color.parseColor(Pal.ON_ACCENT))
        }
        landscapeNextBtn = landCtl(R.drawable.ic_skip_next, 34) { playNext() }
        landscapeRepeatBtn = landCtl(R.drawable.ic_repeat, 32) { cycleRepeat() }

        landBtnsRow.addView(landscapeShuffleBtn)
        landBtnsRow.addView(landscapePrevBtn)
        landBtnsRow.addView(landscapePlayPauseBtn)
        landBtnsRow.addView(landscapeNextBtn)
        landBtnsRow.addView(landscapeRepeatBtn)

        val landSeekRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        landscapeCurTimeText = TextView(this).apply {
            text = "00:00"
            textSize = 10f
            fontFeatureSettings = "tnum"
            setTextColor(Color.parseColor(Pal.SUB))
        }
        landscapeSeekBar = SeekBar(this).apply {
            val accent = ColorStateList.valueOf(Color.parseColor(Pal.ACCENT))
            progressTintList = accent
            thumbTintList = accent
            progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#33FFFFFF"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(dp(6), dp(2), dp(6), dp(2))
            setOnSeekBarChangeListener(seekListener)
        }
        landscapeTotTimeText = TextView(this).apply {
            text = "00:00"
            textSize = 10f
            fontFeatureSettings = "tnum"
            setTextColor(Color.parseColor(Pal.SUB))
        }
        landSeekRow.addView(landscapeCurTimeText)
        landSeekRow.addView(landscapeSeekBar)
        landSeekRow.addView(landscapeTotTimeText)

        landRightCol.addView(landBtnsRow)
        landRightCol.addView(landSeekRow)

        landscapeBottomBar.addView(landscapeCoverThumb)
        landscapeBottomBar.addView(landMetaCol)
        landscapeBottomBar.addView(landscapeFavBtn)
        landscapeBottomBar.addView(landRightCol)

        playerOuterLayout.addView(portraitInnerBox)
        playerOuterLayout.addView(landscapeBottomBar)

        applyShuffleRepeatUi()
        return playerOuterLayout
    }

    private fun toastNoTrack() =
        Toast.makeText(this, "No hay ninguna pista seleccionada", Toast.LENGTH_SHORT).show()



    // ==========================================================
    // TAB 1: LIBRARY (SONGS, ALBUMS, ARTISTS, FOLDERS, STATS)
    // ==========================================================
    private fun buildLibraryTab(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setPadding(dp(16), dp(6), dp(16), 0)
        }

        // ---- search + sort + scan row ----
        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(10))
        }
        val searchBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_input_dark)
            setPadding(dp(12), dp(2), dp(8), dp(2))
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(8) }
        }
        searchBox.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_search)
            setColorFilter(Color.parseColor(Pal.MUTED))
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(8) }
        })
        libSearch = EditText(this).apply {
            hint = "Buscar canción, artista, álbum o carpeta"
            setHintTextColor(Color.parseColor(Pal.MUTED))
            setTextColor(Color.WHITE)
            textSize = 13.5f
            background = null
            isSingleLine = true
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    libQuery = s?.toString()?.trim() ?: ""
                    refreshLibrary()
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        val clearBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_close)
            setColorFilter(Color.parseColor(Pal.MUTED))
            val p = dp(6)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
            isClickable = true
            setOnClickListener { libSearch.setText("") }
        }
        searchBox.addView(libSearch)
        searchBox.addView(clearBtn)

        fun iconBtn(res: Int, onClick: () -> Unit) = ImageView(this).apply {
            setImageResource(res)
            setColorFilter(Color.WHITE)
            setBackgroundResource(R.drawable.bg_btn_pill_dark)
            val p = dp(10)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginStart = dp(4) }
            isClickable = true
            setOnClickListener { onClick() }
            addPressFeedback(0.9f)
        }

        searchRow.addView(searchBox)
        searchRow.addView(iconBtn(R.drawable.ic_sort) { showSortSheet() })
        searchRow.addView(iconBtn(R.drawable.ic_folder) { openSystemFilePicker() })
        searchRow.addView(iconBtn(R.drawable.ic_refresh) { performStorageScan(showToast = true) })
        root.addView(searchRow)

        // ---- mode filter chips ----
        val chipScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val chipRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(8))
        }
        libChips.clear()
        for (mode in LibMode.values()) {
            val pill = Pill(this, mode.label, mode.icon, 34).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = dp(8)
                }
                setActive(mode == libMode)
                setOnClickListener {
                    libMode = mode
                    libGroup = null
                    libChips.forEachIndexed { idx, c -> c.setActive(LibMode.values()[idx] == libMode) }
                    refreshLibrary(scrollTop = true)
                }
            }
            libChips.add(pill)
            chipRow.addView(pill)
        }
        chipScroll.addView(chipRow)
        root.addView(chipScroll)

        // ---- back pill when inside a group ----
        libBackPill = Pill(this, "Volver", R.drawable.ic_skip_prev, 32).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(6)
            }
            setOnClickListener {
                libGroup = null
                refreshLibrary(scrollTop = true)
            }
        }
        root.addView(libBackPill)

        // ---- recycler list ----
        libAdapter = RowAdapter(this, this)
        libRecycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = libAdapter
            clipToPadding = false
            setPadding(0, 0, 0, dp(96))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        root.addView(libRecycler)
        return root
    }

    private fun performStorageScan(showToast: Boolean = false) {
        if (!hasAudioPermission()) {
            refreshLibrary()
            return
        }
        Thread {
            val scanned = ArrayList<TrackItem>()
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DATA
            )
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"
            try {
                contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    null,
                    sortOrder
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                    val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                    val dateCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
                    val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                    val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val rawTitle = cursor.getString(titleCol) ?: "Pista sin título"
                        val rawArtist = cursor.getString(artistCol) ?: "Artista desconocido"
                        val rawAlbum = cursor.getString(albumCol) ?: "Álbum local"
                        val dur = cursor.getLong(durCol)
                        if (dur in 1..2500L) continue // skip tiny notification sounds
                        val albumId = cursor.getLong(albumIdCol)
                        val mime = cursor.getString(mimeCol) ?: "audio/mpeg"
                        val dateAdded = if (dateCol >= 0) cursor.getLong(dateCol) else 0L
                        val sizeBytes = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                        val path = if (dataCol >= 0) (cursor.getString(dataCol) ?: "") else ""
                        val folder = if (path.isNotEmpty()) File(path).parentFile?.name ?: "Memoria" else "Memoria"

                        val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                        val artUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)

                        val ext = when {
                            mime.contains("flac", true) || path.endsWith(".flac", true) -> "FLAC \u2022 Lossless"
                            mime.contains("wav", true) || path.endsWith(".wav", true) -> "WAV \u2022 PCM"
                            mime.contains("alac", true) || path.endsWith(".m4a", true) -> "M4A / AAC"
                            mime.contains("ogg", true) || mime.contains("opus", true) -> "OGG / Opus"
                            else -> "MP3 \u2022 Local"
                        }

                        val cleanArtist = if (rawArtist == "<unknown>") "Artista desconocido" else rawArtist
                        val cleanAlbum = if (rawAlbum == "<unknown>") "Álbum local" else rawAlbum

                        scanned.add(
                            TrackItem(
                                title = rawTitle,
                                artist = cleanArtist,
                                album = cleanAlbum,
                                coverUrl = artUri.toString(),
                                audioUri = contentUri,
                                quality = ext,
                                provider = folder,
                                durationMs = dur,
                                isLocal = true,
                                dateAdded = dateAdded,
                                folder = folder,
                                path = path,
                                sizeBytes = sizeBytes
                            )
                        )
                    }
                }
            } catch (_: Exception) {}

            SonoraStore.hydrateAll(this, scanned)

            runOnUiThread {
                localTracksList.clear()
                localTracksList.addAll(scanned)
                libraryByKey.clear()
                for (t in scanned) libraryByKey[t.key] = t

                if (!queueRestored) {
                    queueRestored = true
                    restoreQueueIfNeeded()
                }
                refreshLibrary()
                refreshPlaylists()
                if (showToast) {
                    Toast.makeText(this, "${scanned.size} pistas encontradas", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun sortTracks(list: List<TrackItem>): List<TrackItem> {
        if (sortKey == "none") return list
        val counts = SonoraStore.playCounts(this)
        val cmp = when (sortKey) {
            "artist" -> compareBy<TrackItem>({ it.artist.lowercase() }, { it.title.lowercase() })
            "album" -> compareBy<TrackItem>({ it.album.lowercase() }, { it.title.lowercase() })
            "duration" -> compareBy<TrackItem> { it.durationMs }
            "dateAdded" -> compareBy<TrackItem> { it.dateAdded }
            "playCount" -> compareBy<TrackItem> { counts[it.key] ?: 0 }
            else -> compareBy<TrackItem> { it.title.lowercase() }
        }
        return if (sortAsc) list.sortedWith(cmp) else list.sortedWith(cmp.reversed())
    }

    private fun refreshLibrary(scrollTop: Boolean = false) {
        if (!::libAdapter.isInitialized) return
        val q = libQuery.lowercase()
        fun matches(t: TrackItem): Boolean {
            if (q.isEmpty()) return true
            return t.title.lowercase().contains(q) ||
                t.artist.lowercase().contains(q) ||
                t.album.lowercase().contains(q) ||
                t.folder.lowercase().contains(q)
        }

        val rows = ArrayList<Row>()
        val grp = libGroup
        if (grp != null) {
            libBackPill.visibility = View.VISIBLE
            libBackPill.setLabel("Volver a ${libMode.label}")
            val filtered = sortTracks(grp.tracks.filter { matches(it) })
            rows.add(Row.Title(grp.name, "${filtered.size} pistas \u2022 ${grp.sub}"))
            if (filtered.isNotEmpty()) {
                rows.add(Row.Actions(filtered))
                filtered.forEachIndexed { idx, t -> rows.add(Row.Song(t, filtered, idx)) }
            } else {
                rows.add(Row.Empty("Sin coincidencias", "No hay pistas que coincidan con la búsqueda.", false))
            }
            libAdapter.submit(rows)
            if (scrollTop) libRecycler.scrollToPosition(0)
            return
        }

        libBackPill.visibility = View.GONE
        val base = localTracksList.filter { matches(it) }

        when (libMode) {
            LibMode.SONGS -> {
                val sorted = sortTracks(base)
                if (sorted.isEmpty()) {
                    rows.add(
                        Row.Empty(
                            if (localTracksList.isEmpty()) "Tu biblioteca está vacía" else "Sin resultados",
                            if (localTracksList.isEmpty()) "Abre archivos de audio desde tu dispositivo o escanea el almacenamiento."
                            else "Prueba con otro término de búsqueda.",
                            localTracksList.isEmpty()
                        )
                    )
                } else {
                    rows.add(Row.Title("Canciones", "${sorted.size} pistas en tu dispositivo"))
                    rows.add(Row.Actions(sorted))
                    sorted.forEachIndexed { i, t -> rows.add(Row.Song(t, sorted, i)) }
                }
            }
            LibMode.ALBUMS -> {
                val groups = base.groupBy { it.album.ifBlank { "Álbum desconocido" } }
                    .entries.sortedBy { it.key.lowercase() }
                if (groups.isEmpty()) {
                    rows.add(Row.Empty("Sin álbumes", "No se encontraron álbumes en tu biblioteca.", localTracksList.isEmpty()))
                } else {
                    rows.add(Row.Title("Álbumes", "${groups.size} álbumes"))
                    for ((name, list) in groups) {
                        val artist = list.firstOrNull()?.artist ?: ""
                        rows.add(Row.Group("album", name, "${list.size} pistas \u2022 $artist", list, R.drawable.ic_album, list.firstOrNull(), true))
                    }
                }
            }
            LibMode.ARTISTS -> {
                val groups = base.groupBy { it.artist.ifBlank { "Artista desconocido" } }
                    .entries.sortedBy { it.key.lowercase() }
                if (groups.isEmpty()) {
                    rows.add(Row.Empty("Sin artistas", "No se encontraron artistas en tu biblioteca.", localTracksList.isEmpty()))
                } else {
                    rows.add(Row.Title("Artistas", "${groups.size} artistas"))
                    for ((name, list) in groups) {
                        val albums = list.map { it.album }.distinct().size
                        rows.add(Row.Group("artist", name, "${list.size} pistas \u2022 $albums álbumes", list, R.drawable.ic_user, list.firstOrNull(), true))
                    }
                }
            }
            LibMode.FOLDERS -> {
                val groups = base.groupBy { it.folder.ifBlank { "Memoria interna" } }
                    .entries.sortedBy { it.key.lowercase() }
                if (groups.isEmpty()) {
                    rows.add(Row.Empty("Sin carpetas", "No se encontraron carpetas con música.", localTracksList.isEmpty()))
                } else {
                    rows.add(Row.Title("Carpetas", "${groups.size} carpetas"))
                    for ((name, list) in groups) {
                        val parent = list.firstOrNull()?.path?.let { File(it).parent ?: "" } ?: ""
                        rows.add(Row.Group("folder", name, "${list.size} pistas \u2022 $parent", list, R.drawable.ic_folder, list.firstOrNull(), true))
                    }
                }
            }
            LibMode.FAVORITES -> {
                val favs = sortTracks( (localTracksList + tracks).distinctBy { it.key }.filter { it.isFavorite && matches(it) } )
                if (favs.isEmpty()) {
                    rows.add(Row.Empty("Sin favoritas aún", "Pulsa el icono de corazón en cualquier canción para guardarla aquí.", false))
                } else {
                    rows.add(Row.Title("Favoritas", "${favs.size} pistas guardadas"))
                    rows.add(Row.Actions(favs))
                    favs.forEachIndexed { i, t -> rows.add(Row.Song(t, favs, i)) }
                }
            }
            LibMode.RECENT -> {
                val histKeys = SonoraStore.history(this)
                val pool = (localTracksList + tracks).associateBy { it.key }
                val recent = histKeys.mapNotNull { pool[it] }.filter { matches(it) }
                if (recent.isEmpty()) {
                    rows.add(Row.Empty("Historial vacío", "Las canciones que reproduzcas aparecerán aquí automáticamente.", false))
                } else {
                    rows.add(Row.Title("Reproducidas recientemente", "${recent.size} pistas"))
                    rows.add(Row.Actions(recent))
                    recent.forEachIndexed { i, t -> rows.add(Row.Song(t, recent, i)) }
                }
            }
            LibMode.TOP -> {
                val counts = SonoraStore.playCounts(this)
                val pool = (localTracksList + tracks).distinctBy { it.key }
                val top = pool.filter { (counts[it.key] ?: 0) > 0 && matches(it) }
                    .sortedByDescending { counts[it.key] ?: 0 }
                if (top.isEmpty()) {
                    rows.add(Row.Empty("Sin estadísticas aún", "Escucha tu música para generar el ranking de tus pistas más reproducidas.", false))
                } else {
                    rows.add(Row.Title("Más escuchadas", "${top.size} pistas con reproducciones"))
                    rows.add(Row.Actions(top))
                    top.forEachIndexed { i, t -> rows.add(Row.Song(t, top, i)) }
                }
            }
            LibMode.ADDED -> {
                val rec = base.sortedByDescending { it.dateAdded }
                if (rec.isEmpty()) {
                    rows.add(Row.Empty("Sin pistas recientes", "No hay archivos locales indexados.", true))
                } else {
                    rows.add(Row.Title("Añadidas recientemente", "${rec.size} pistas ordenadas por fecha"))
                    rows.add(Row.Actions(rec))
                    rec.forEachIndexed { i, t -> rows.add(Row.Song(t, rec, i)) }
                }
            }
        }

        libAdapter.submit(rows)
        if (scrollTop) libRecycler.scrollToPosition(0)
    }

    private fun showSortSheet() {
        val options = listOf(
            "title" to "Título",
            "artist" to "Artista",
            "album" to "Álbum",
            "duration" to "Duración",
            "dateAdded" to "Fecha añadida",
            "playCount" to "Más reproducidas"
        )
        val items = options.map { (k, label) ->
            val mark = if (sortKey == k) (if (sortAsc) " (\u2191)" else " (\u2193)") else ""
            SheetItem(R.drawable.ic_sort, label + mark)
        }
        val allItems = items + SheetItem(R.drawable.ic_sliders, "Más opciones de orden (26 modos)")
        showActionSheet("Ordenar biblioteca", "Toca de nuevo el mismo criterio para invertir el orden", allItems) { idx ->
            if (idx == options.size) {
                MusicoletModals.showFullSortDialog(this, "Ordenar biblioteca", localTracksList.toMutableList()) { sorted, _ ->
                    localTracksList.clear()
                    localTracksList.addAll(sorted)
                    sortKey = "none"
                    refreshLibrary(scrollTop = true)
                }
                return@showActionSheet
            }
            val chosen = options[idx].first
            if (sortKey == chosen) sortAsc = !sortAsc else {
                sortKey = chosen
                sortAsc = chosen != "dateAdded" && chosen != "playCount"
            }
            SonoraStore.putString(this, "lib_sort", sortKey)
            SonoraStore.putBool(this, "lib_sort_asc", sortAsc)
            refreshLibrary(scrollTop = true)
        }
    }

    // ---- RowHost implementation ----
    override fun onPlay(list: List<TrackItem>, index: Int) {
        playList(list, index, autoPlay = true)
    }

    override fun onPlayAll(list: List<TrackItem>, shuffle: Boolean) {
        if (list.isEmpty()) return
        setShuffle(shuffle)
        val start = if (shuffle) list.indices.random() else 0
        playList(list, start, autoPlay = true)
    }

    override fun onTrailing(track: TrackItem, index: Int) {
        showTrackMenu(track)
    }

    override fun onGroupOpen(g: Row.Group) {
        if (g.kind == "playlist") {
            showPlaylistDetailSheet(g.name)
        } else {
            libGroup = g
            refreshLibrary(scrollTop = true)
        }
    }

    override fun onGroupMenu(g: Row.Group) {
        if (g.kind == "playlist") {
            showPlaylistMenu(g.name)
        } else {
            val items = listOf(
                SheetItem(R.drawable.ic_play_filled, "Reproducir (${g.tracks.size} pistas)"),
                SheetItem(R.drawable.ic_shuffle, "Reproducir en aleatorio"),
                SheetItem(R.drawable.ic_next_up, "Reproducir a continuación"),
                SheetItem(R.drawable.ic_list_plus, "Añadir a la cola"),
                SheetItem(R.drawable.ic_plus, "Añadir a una lista")
            )
            showActionSheet(g.name, g.sub, items) { which ->
                when (which) {
                    0 -> onPlayAll(g.tracks, false)
                    1 -> onPlayAll(g.tracks, true)
                    2 -> enqueueNext(g.tracks)
                    3 -> enqueueEnd(g.tracks)
                    4 -> showAddListToPlaylistSheet(g.tracks)
                }
            }
        }
    }

    override fun onEmptyOpen() = openSystemFilePicker()
    override fun onEmptyScan() = performStorageScan(showToast = true)
    override fun currentKey(): String? = tracks.getOrNull(currentTrackIndex)?.key

    // ==========================================================
    // TAB 2: PLAYLISTS & M3U MANAGER
    // ==========================================================
    private fun buildPlaylistTab(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setPadding(dp(16), dp(8), dp(16), 0)
        }

        val actionScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(10))
        }
        actions.addView(Pill(this, "Nueva lista", R.drawable.ic_plus, 36).apply {
            setActive(true)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8) }
            setOnClickListener { promptNewPlaylist() }
        })
        actions.addView(Pill(this, "Importar M3U", R.drawable.ic_folder, 36).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8) }
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                m3uPickerLauncher.launch(intent)
            }
        })
        actions.addView(Pill(this, "Guardar cola como lista", R.drawable.ic_list_plus, 36).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8) }
            setOnClickListener {
                if (tracks.isEmpty()) toastNoTrack() else promptNewPlaylist(seed = tracks.toList())
            }
        })
        actions.addView(Pill(this, "Exportar cola M3U", R.drawable.ic_share, 36).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                if (tracks.isEmpty()) toastNoTrack() else {
                    pendingExport = tracks.toList()
                    exportLauncher.launch("lista_cola.m3u8")
                }
            }
        })
        actionScroll.addView(actions)
        root.addView(actionScroll)

        plAdapter = RowAdapter(this, this)
        plRecycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = plAdapter
            clipToPadding = false
            setPadding(0, 0, 0, dp(96))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        root.addView(plRecycler)
        refreshPlaylists()
        return root
    }

    private fun resolveRef(ref: TrackRef): TrackItem {
        libraryByKey[ref.key]?.let { return it }
        tracks.find { it.key == ref.key }?.let { return it }
        val isLocal = ref.key.startsWith("content://") || ref.key.startsWith("file://")
        return SonoraStore.hydrate(
            this,
            TrackItem(
                title = ref.title.ifBlank { "Pista" },
                artist = ref.artist.ifBlank { "Artista" },
                album = ref.album.ifBlank { "Álbum" },
                audioUri = if (isLocal) Uri.parse(ref.key) else null,
                audioUrl = if (isLocal) "" else ref.key,
                quality = if (isLocal) "Audio local" else "Stream",
                provider = if (isLocal) "Local" else "Catálogo",
                isLocal = isLocal
            )
        )
    }

    private fun refreshPlaylists() {
        if (!::plAdapter.isInitialized) return
        val rows = ArrayList<Row>()
        rows.add(Row.Title("Listas de reproducción", "${playlists.size} listas guardadas \u2022 Soporte M3U / M3U8"))
        if (playlists.isEmpty()) {
            rows.add(
                Row.Empty(
                    "No tienes listas creadas",
                    "Crea una lista nueva, guarda tu cola actual o importa un archivo .m3u / .m3u8.",
                    false
                )
            )
        } else {
            for ((name, refs) in playlists) {
                val resolved = refs.map { resolveRef(it) }
                rows.add(
                    Row.Group(
                        kind = "playlist",
                        name = name,
                        sub = "${resolved.size} pistas",
                        tracks = resolved,
                        icon = R.drawable.ic_list_music,
                        cover = resolved.firstOrNull(),
                        menu = true
                    )
                )
            }
        }
        plAdapter.submit(rows)
    }

    private fun promptNewPlaylist(seed: List<TrackItem> = emptyList(), onCreated: ((String) -> Unit)? = null) {
        showSheet { root, dialog ->
            root.addView(sheetTitle("Nueva lista de reproducción", if (seed.isNotEmpty()) "Con ${seed.size} pistas iniciales" else "Introduce un nombre"))
            val input = EditText(this).apply {
                hint = "Ej. Favoritas de noche, Gym, FLAC..."
                setHintTextColor(Color.parseColor(Pal.MUTED))
                setTextColor(Color.WHITE)
                textSize = 14f
                setBackgroundResource(R.drawable.bg_input_dark)
                setPadding(dp(14), dp(12), dp(14), dp(12))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(14)
                }
            }
            root.addView(input)
            val saveBtn = Pill(this, "Crear lista", R.drawable.ic_check, 40).apply {
                setActive(true)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setOnClickListener {
                    val name = input.text.toString().trim()
                    if (name.isEmpty()) {
                        Toast.makeText(this@MainActivity, "Escribe un nombre", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    val list = playlists.getOrPut(name) { mutableListOf() }
                    for (t in seed) {
                        if (list.none { it.key == t.key }) list.add(TrackRef.of(t))
                    }
                    SonoraStore.savePlaylists(this@MainActivity, playlists)
                    refreshPlaylists()
                    dialog.dismiss()
                    onCreated?.invoke(name)
                }
            }
            root.addView(saveBtn)
        }
    }

    private fun showPlaylistDetailSheet(name: String) {
        val refs = playlists[name] ?: return
        val resolved = refs.map { resolveRef(it) }.toMutableList()
        showSheet(fullHeight = true) { root, dialog ->
            root.addView(sheetTitle(name, "${resolved.size} pistas"))
            val actionRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, dp(10))
            }
            actionRow.addView(Pill(this, "Reproducir", R.drawable.ic_play_filled, 36).apply {
                setActive(true)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) }
                setOnClickListener { dialog.dismiss(); onPlayAll(resolved, false) }
            })
            actionRow.addView(Pill(this, "Aleatorio", R.drawable.ic_shuffle, 36).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) }
                setOnClickListener { dialog.dismiss(); onPlayAll(resolved, true) }
            })
            actionRow.addView(Pill(this, "Exportar", R.drawable.ic_share, 36).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setOnClickListener {
                    dialog.dismiss()
                    pendingExport = resolved
                    exportLauncher.launch("${name.replace(" ", "_")}.m3u8")
                }
            })
            root.addView(actionRow)

            lateinit var adapter: RowAdapter
            val host = object : RowHost by this@MainActivity {
                override fun onPlay(list: List<TrackItem>, index: Int) {
                    dialog.dismiss()
                    playList(list, index, autoPlay = true)
                }
                override fun onTrailing(track: TrackItem, index: Int) {
                    refs.removeAll { it.key == track.key }
                    resolved.removeAll { it.key == track.key }
                    SonoraStore.savePlaylists(this@MainActivity, playlists)
                    refreshPlaylists()
                    adapter.submit(resolved.mapIndexed { i, t -> Row.Song(t, resolved, i) })
                }
            }
            adapter = RowAdapter(this, host, R.drawable.ic_trash)
            adapter.submit(
                if (resolved.isEmpty()) listOf(Row.Empty("Lista vacía", "Añade canciones desde el menú de cualquier pista.", false))
                else resolved.mapIndexed { i, t -> Row.Song(t, resolved, i) }
            )
            val rv = RecyclerView(this).apply {
                layoutManager = LinearLayoutManager(this@MainActivity)
                this.adapter = adapter
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            }
            root.addView(rv)
        }
    }

    private fun showPlaylistMenu(name: String) {
        val refs = playlists[name] ?: return
        val resolved = refs.map { resolveRef(it) }
        val items = listOf(
            SheetItem(R.drawable.ic_play_filled, "Reproducir lista"),
            SheetItem(R.drawable.ic_shuffle, "Reproducir en aleatorio"),
            SheetItem(R.drawable.ic_list_plus, "Añadir a la cola"),
            SheetItem(R.drawable.ic_pencil, "Renombrar lista"),
            SheetItem(R.drawable.ic_share, "Exportar a archivo .M3U8"),
            SheetItem(R.drawable.ic_trash, "Eliminar lista", danger = true)
        )
        showActionSheet(name, "${resolved.size} pistas", items) { which ->
            when (which) {
                0 -> onPlayAll(resolved, false)
                1 -> onPlayAll(resolved, true)
                2 -> enqueueEnd(resolved)
                3 -> promptRenamePlaylist(name)
                4 -> {
                    pendingExport = resolved
                    exportLauncher.launch("${name.replace(" ", "_")}.m3u8")
                }
                5 -> {
                    playlists.remove(name)
                    SonoraStore.savePlaylists(this, playlists)
                    refreshPlaylists()
                }
            }
        }
    }

    private fun promptRenamePlaylist(oldName: String) {
        val refs = playlists[oldName] ?: return
        showSheet { root, dialog ->
            root.addView(sheetTitle("Renombrar lista", oldName))
            val input = EditText(this).apply {
                setText(oldName)
                setTextColor(Color.WHITE)
                textSize = 14f
                setBackgroundResource(R.drawable.bg_input_dark)
                setPadding(dp(14), dp(12), dp(14), dp(12))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(14)
                }
            }
            root.addView(input)
            root.addView(Pill(this, "Guardar", R.drawable.ic_check, 40).apply {
                setActive(true)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setOnClickListener {
                    val newName = input.text.toString().trim()
                    if (newName.isNotEmpty() && newName != oldName) {
                        playlists.remove(oldName)
                        playlists[newName] = refs
                        SonoraStore.savePlaylists(this@MainActivity, playlists)
                        refreshPlaylists()
                    }
                    dialog.dismiss()
                }
            })
        }
    }

    private fun showAddToPlaylistSheet(track: TrackItem) {
        showAddListToPlaylistSheet(listOf(track))
    }

    private fun showAddListToPlaylistSheet(toAdd: List<TrackItem>) {
        val names = playlists.keys.toList()
        val items = mutableListOf(SheetItem(R.drawable.ic_plus, "Crear nueva lista..."))
        for (n in names) items.add(SheetItem(R.drawable.ic_list_music, "$n (${playlists[n]?.size ?: 0})"))
        showActionSheet("Añadir a lista", "${toAdd.size} pista(s)", items) { which ->
            if (which == 0) {
                promptNewPlaylist(seed = toAdd) {
                    Toast.makeText(this, "Añadido a $it", Toast.LENGTH_SHORT).show()
                }
            } else {
                val targetName = names[which - 1]
                val list = playlists.getOrPut(targetName) { mutableListOf() }
                var added = 0
                for (t in toAdd) {
                    if (list.none { it.key == t.key }) { list.add(TrackRef.of(t)); added++ }
                }
                SonoraStore.savePlaylists(this, playlists)
                refreshPlaylists()
                Toast.makeText(this, "$added pista(s) añadidas a $targetName", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun importPlaylistFile(uri: Uri) {
        try {
            val name = getFileNameFromUri(uri).substringBeforeLast('.').ifBlank { "Importada" }
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            val refs = mutableListOf<TrackRef>()
            var pendingTitle = ""
            var pendingArtist = "Importado"

            for (rawLine in text.lines()) {
                val line = rawLine.trim()
                if (line.isEmpty()) continue
                if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                    val info = line.substringAfter(",", "").trim()
                    if (info.contains(" - ")) {
                        pendingArtist = info.substringBefore(" - ").trim()
                        pendingTitle = info.substringAfter(" - ").trim()
                    } else {
                        pendingTitle = info
                    }
                } else if (!line.startsWith("#")) {
                    val matched = localTracksList.find {
                        it.key == line ||
                            (it.path.isNotEmpty() && it.path.endsWith(line.replace("\\", "/").substringAfterLast("/"), ignoreCase = true)) ||
                            it.title.equals(pendingTitle, ignoreCase = true)
                    }
                    if (matched != null) {
                        refs.add(TrackRef.of(matched))
                    } else if (line.startsWith("http://") || line.startsWith("https://") || line.startsWith("content://")) {
                        refs.add(TrackRef(line, pendingTitle.ifBlank { line.substringAfterLast('/') }, pendingArtist, name))
                    }
                    pendingTitle = ""
                    pendingArtist = "Importado"
                }
            }

            if (refs.isEmpty()) {
                Toast.makeText(this, "No se encontraron pistas válidas en el archivo M3U", Toast.LENGTH_SHORT).show()
                return
            }
            playlists[name] = refs
            SonoraStore.savePlaylists(this, playlists)
            refreshPlaylists()
            Toast.makeText(this, "Lista '$name' importada (${refs.size} pistas)", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Error al leer el archivo M3U", Toast.LENGTH_SHORT).show()
        }
    }

    private fun writeM3u(uri: Uri, list: List<TrackItem>) {
        try {
            val sb = StringBuilder("#EXTM3U\n")
            for (t in list) {
                val sec = if (t.durationMs > 0) (t.durationMs / 1000) else -1
                sb.append("#EXTINF:$sec,${t.artist} - ${t.title}\n")
                val ref = if (t.path.isNotEmpty()) t.path else t.key
                sb.append(ref).append("\n")
            }
            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(sb.toString()) }
            Toast.makeText(this, "Lista M3U exportada (${list.size} pistas)", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, "No se pudo exportar el archivo M3U", Toast.LENGTH_SHORT).show()
        }
    }

    // ==========================================================
    // TAB 3: YOUTUBE MUSIC & VIDEO, GLOBAL CATALOG & LIVE RADIO
    // ==========================================================
    private fun buildOnlineTab(): View {
        val scroll = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(96))
        }

        // Section 0: YouTube Music & Video (Watch Video or Audio Only)
        container.addView(TextView(this).apply {
            text = "YouTube Música y Video (Online + Local)"
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
        })
        container.addView(TextView(this).apply {
            text = "Busca cualquier canción o video en YouTube, o pega un enlace para elegir entre Solo Escuchar o Ver Video en el escenario."
            textSize = 12f
            setTextColor(Color.parseColor(Pal.SUB))
            setPadding(0, dp(3), 0, dp(10))
        })

        val ytCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card_spotiflac)
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        ytSearchInput = EditText(this).apply {
            hint = "Buscar en YouTube o pegar link (youtube.com / youtu.be)..."
            setHintTextColor(Color.parseColor(Pal.MUTED))
            setTextColor(Color.WHITE)
            textSize = 13.5f
            isSingleLine = true
            setBackgroundResource(R.drawable.bg_input_dark)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val ytBtnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }
        ytBtnRow.addView(Pill(this, "Pegar YouTube", R.drawable.ic_link, 36).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) }
            setOnClickListener {
                val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val txt = cm.primaryClip?.getItemAt(0)?.coerceToText(this@MainActivity)?.toString()?.trim() ?: ""
                if (txt.isNotEmpty()) {
                    ytSearchInput.setText(txt)
                    performYouTubeQuery(txt)
                }
            }
        })
        ytBtnRow.addView(Pill(this, "Buscar en YouTube", R.drawable.ic_search, 36).apply {
            setActive(true)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                val q = ytSearchInput.text.toString().trim()
                if (q.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Escribe una canción, artista o enlace de YouTube", Toast.LENGTH_SHORT).show()
                } else {
                    performYouTubeQuery(q)
                }
            }
        })
        ytCard.addView(ytSearchInput)
        ytCard.addView(ytBtnRow)

        val ytQuickScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, dp(10), 0, 0)
        }
        val ytQuickRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val quickQueries = listOf(
            "Tendencias Música" to "official music video hits",
            "Anime / J-Pop" to "anime opening official",
            "Bocchi / J-Rock" to "kessoku band official",
            "Lo-Fi Chill" to "lofi hip hop beats music",
            "Vocaloid / Miku" to "hatsune miku official MV"
        )
        for ((label, q) in quickQueries) {
            ytQuickRow.addView(Pill(this, label, 0, 32).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = dp(6)
                }
                setOnClickListener {
                    ytSearchInput.setText(q)
                    performYouTubeQuery(q)
                }
            })
        }
        ytQuickScroll.addView(ytQuickRow)
        ytCard.addView(ytQuickScroll)
        container.addView(ytCard)

        ytStatusText = TextView(this).apply {
            text = ""
            textSize = 12f
            setTextColor(Color.parseColor(Pal.ACCENT))
            setPadding(dp(4), dp(8), dp(4), dp(4))
            visibility = View.GONE
        }
        container.addView(ytStatusText)

        ytResultsBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        container.addView(ytResultsBox)

        // Section 1: Global Catalog Search & Smart Link Resolver
        container.addView(TextView(this).apply {
            text = "Catálogo Global y Extractor de Enlaces"
            textSize = 17f
            setTextColor(Color.WHITE)
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setPadding(0, dp(14), 0, 0)
        })
        container.addView(TextView(this).apply {
            text = "Busca cualquier canción, álbum o artista, o pega un enlace externo para reproducir, extraer carátula 1000x1000 y guardar en tu Biblioteca."
            textSize = 12f
            setTextColor(Color.parseColor(Pal.SUB))
            setPadding(0, dp(3), 0, dp(10))
        })

        val catalogCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card_spotiflac)
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        catalogSearchInput = EditText(this).apply {
            hint = "Canción, artista o enlace (open.spotify.com / deezer.com)"
            setHintTextColor(Color.parseColor(Pal.MUTED))
            setTextColor(Color.WHITE)
            textSize = 13.5f
            isSingleLine = true
            setBackgroundResource(R.drawable.bg_input_dark)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val catBtnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }
        catBtnRow.addView(Pill(this, "Pegar enlace", R.drawable.ic_link, 36).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) }
            setOnClickListener {
                val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val txt = cm.primaryClip?.getItemAt(0)?.coerceToText(this@MainActivity)?.toString()?.trim() ?: ""
                if (txt.isNotEmpty()) {
                    catalogSearchInput.setText(txt)
                    performCatalogQuery(txt)
                }
            }
        })
        catBtnRow.addView(Pill(this, "Buscar / Resolver", R.drawable.ic_search, 36).apply {
            setActive(true)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                val q = catalogSearchInput.text.toString().trim()
                if (q.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Escribe un título, artista o pega un enlace", Toast.LENGTH_SHORT).show()
                } else {
                    performCatalogQuery(q)
                }
            }
        })
        catalogCard.addView(catalogSearchInput)
        catalogCard.addView(catBtnRow)

        val enrichAllBtn = Pill(this, "Auto-Tag y Carátulas HD (1000x1000) para mi Biblioteca", R.drawable.ic_wand, 36).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            }
            setOnClickListener { batchEnrichLibrary() }
        }
        catalogCard.addView(enrichAllBtn)
        container.addView(catalogCard)

        catalogStatusText = TextView(this).apply {
            text = ""
            textSize = 12f
            setTextColor(Color.parseColor(Pal.CYAN))
            setPadding(dp(4), dp(8), dp(4), dp(4))
            visibility = View.GONE
        }
        container.addView(catalogStatusText)

        catalogResultsBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        container.addView(catalogResultsBox)

        // Section 2: Direct Stream URL Player
        container.addView(TextView(this).apply {
            text = "Reproductor de Stream Directo"
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setPadding(0, dp(16), 0, dp(6))
        })

        val inputCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card_spotiflac)
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        onlineUrlInput = EditText(this).apply {
            hint = "https://... (FLAC, MP3, OGG, M3U8, MP4)"
            setHintTextColor(Color.parseColor(Pal.MUTED))
            setTextColor(Color.WHITE)
            textSize = 13.5f
            isSingleLine = true
            setBackgroundResource(R.drawable.bg_input_dark)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }
        btnRow.addView(Pill(this, "Pegar URL", R.drawable.ic_link, 36).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) }
            setOnClickListener {
                val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val txt = cm.primaryClip?.getItemAt(0)?.coerceToText(this@MainActivity)?.toString()?.trim() ?: ""
                if (txt.isNotEmpty()) onlineUrlInput.setText(txt)
            }
        })
        btnRow.addView(Pill(this, "Reproducir Stream", R.drawable.ic_play_filled, 36).apply {
            setActive(true)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                val url = onlineUrlInput.text.toString().trim()
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    SonoraStore.addRecentUrl(this@MainActivity, url)
                    refreshRecentStreams()
                    playStreamUrl(url)
                } else {
                    Toast.makeText(this@MainActivity, "Introduce una URL http:// o https:// válida", Toast.LENGTH_SHORT).show()
                }
            }
        })
        inputCard.addView(onlineUrlInput)
        inputCard.addView(btnRow)
        container.addView(inputCard)

        // Curated live stations
        container.addView(TextView(this).apply {
            text = "Emisoras Lossless y Radio en Vivo"
            textSize = 15f
            setTextColor(Color.WHITE)
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setPadding(0, dp(18), 0, dp(8))
        })

        data class Station(val name: String, val genre: String, val badge: String, val url: String)
        val stations = listOf(
            Station("Radio Paradise Main Mix", "Eclectic Rock / World / Indie", "FLAC \u2022 Lossless", "https://stream.radioparadise.com/flac"),
            Station("Radio Paradise 320k", "Eclectic Mix \u2022 Alta compatibilidad", "MP3 \u2022 320 kbps", "https://stream.radioparadise.com/mp3-320"),
            Station("SomaFM Groove Salad", "Ambient / Downtempo / Chillout", "MP3 \u2022 128 kbps", "https://ice1.somafm.com/groovesalad-128-mp3"),
            Station("SomaFM Drone Zone", "Atmospheric Ambient / Space", "MP3 \u2022 128 kbps", "https://ice1.somafm.com/dronezone-128-mp3"),
            Station("SomaFM Secret Agent", "Lounge / Spy Soundtracks", "MP3 \u2022 128 kbps", "https://ice1.somafm.com/secretagent-128-mp3")
        )

        for (st in stations) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.bg_card_spotiflac)
                setPadding(dp(12), dp(12), dp(12), dp(12))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(8)
                }
                isClickable = true
                setOnClickListener {
                    playStreamUrl(st.url, st.name, st.genre, st.badge)
                }
                addPressFeedback(0.98f)
            }
            val iconBox = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(12) }
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(Pal.SURFACE_HI))
                    cornerRadius = dpf(10f)
                }
            }
            iconBox.addView(ImageView(this).apply {
                setImageResource(R.drawable.ic_radio)
                setColorFilter(Color.parseColor(Pal.ACCENT))
                layoutParams = FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER)
            })
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            col.addView(TextView(this).apply {
                text = st.name
                textSize = 14.5f
                setTextColor(Color.WHITE)
                setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            })
            col.addView(TextView(this).apply {
                text = "${st.genre} \u2022 ${st.badge}"
                textSize = 11.5f
                setTextColor(Color.parseColor(Pal.SUB))
                setPadding(0, dp(2), 0, 0)
            })
            card.addView(iconBox)
            card.addView(col)
            card.addView(ImageView(this).apply {
                setImageResource(R.drawable.ic_play_filled)
                setColorFilter(Color.parseColor(Pal.ACCENT))
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
            })
            container.addView(card)
        }

        container.addView(TextView(this).apply {
            text = "Streams recientes"
            textSize = 15f
            setTextColor(Color.WHITE)
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setPadding(0, dp(14), 0, dp(8))
        })
        onlineRecentBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        container.addView(onlineRecentBox)
        refreshRecentStreams()

        scroll.addView(container)
        return scroll
    }

    private fun performYouTubeQuery(query: String) {
        if (!::ytStatusText.isInitialized || !::ytResultsBox.isInitialized) return
        ytStatusText.visibility = View.VISIBLE
        ytStatusText.text = "Buscando en YouTube Música y Video..."
        ytResultsBox.removeAllViews()
        currentFocus?.let { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(it.windowToken, 0) }

        Thread {
            val directVid = YouTubeEngine.extractVideoId(query)
            val results = if (directVid != null) {
                val stream = YouTubeEngine.resolveStreams(directVid)
                listOf(
                    YouTubeVideoItem(
                        videoId = directVid,
                        title = stream.title.ifBlank { "YouTube Video ($directVid)" },
                        author = stream.author.ifBlank { "YouTube" },
                        durationSec = stream.durationSec,
                        thumbnailUrl = stream.thumbnailUrl
                    )
                )
            } else {
                YouTubeEngine.search(query, 12)
            }

            runOnUiThread {
                if (results.isEmpty()) {
                    ytStatusText.text = "Sin resultados directos. Probando reproductor YouTube integrado..."
                    if (directVid != null) {
                        playYouTubeVideoItem(
                            YouTubeVideoItem(
                                videoId = directVid,
                                title = "YouTube ($directVid)",
                                author = "YouTube Stream",
                                durationSec = 0L,
                                thumbnailUrl = "https://i.ytimg.com/vi/$directVid/hqdefault.jpg"
                            ),
                            watchVideo = true
                        )
                    }
                    return@runOnUiThread
                }
                ytStatusText.text = "${results.size} resultados en YouTube \u2022 Elige Audio o Video"
                for (item in results) {
                    val card = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setBackgroundResource(R.drawable.bg_card_spotiflac)
                        setPadding(dp(10), dp(10), dp(10), dp(10))
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = dp(8) }
                    }
                    val topRow = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    val thumb = ImageView(this).apply {
                        layoutParams = LinearLayout.LayoutParams(dp(72), dp(54)).apply { marginEnd = dp(10) }
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        background = GradientDrawable().apply {
                            setColor(Color.parseColor("#0B0F19"))
                            cornerRadius = dpf(8f)
                        }
                    }
                    val previewTrack = TrackItem(
                        title = item.title,
                        artist = item.author,
                        album = "YouTube",
                        coverUrl = item.thumbnailUrl
                    )
                    thumb.bindCover(previewTrack, 240)
                    val col = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    col.addView(TextView(this).apply {
                        text = item.title
                        textSize = 13.5f
                        maxLines = 2
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        setTextColor(Color.WHITE)
                        setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
                    })
                    val durStr = if (item.durationSec > 0) formatClock(item.durationSec * 1000L) else "YouTube"
                    col.addView(TextView(this).apply {
                        text = "${item.author} \u2022 $durStr"
                        textSize = 11.5f
                        maxLines = 1
                        setTextColor(Color.parseColor(Pal.SUB))
                        setPadding(0, dp(2), 0, 0)
                    })
                    topRow.addView(thumb)
                    topRow.addView(col)
                    card.addView(topRow)

                    val actionRow = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(0, dp(8), 0, 0)
                    }
                    actionRow.addView(Pill(this, "Solo Escuchar", R.drawable.ic_music, 32).apply {
                        setActive(true)
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                            marginEnd = dp(6)
                        }
                        setOnClickListener { playYouTubeVideoItem(item, watchVideo = false) }
                    })
                    actionRow.addView(Pill(this, "Ver Video", R.drawable.ic_video, 32).apply {
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                            marginEnd = dp(6)
                        }
                        setOnClickListener { playYouTubeVideoItem(item, watchVideo = true) }
                    })
                    actionRow.addView(Pill(this, "+ Cola", R.drawable.ic_list_plus, 32).apply {
                        setOnClickListener {
                            showGlassSearchBanner("Añadiendo '${item.title}' a la cola...", onCancel = {
                                isSearchCancelled = true
                                hideGlassSearchBanner()
                            })
                            Thread {
                                val resolved = YouTubeEngine.resolveStreams(item.videoId)
                                if (isSearchCancelled) return@Thread
                                val audioStream = resolved.audioStreamUrl.ifBlank { resolved.videoStreamUrl }
                                val videoStream = resolved.videoStreamUrl.ifBlank { resolved.audioStreamUrl }
                                val tr = SonoraStore.hydrate(
                                    this@MainActivity,
                                    TrackItem(
                                        title = item.title,
                                        artist = item.author,
                                        album = "YouTube Music",
                                        coverUrl = item.thumbnailUrl,
                                        audioUrl = audioStream,
                                        videoUrl = videoStream,
                                        quality = "YOUTUBE \u2022 HQ",
                                        provider = "YouTube",
                                        durationMs = item.durationSec * 1000L,
                                        isLocal = false
                                    )
                                )
                                runOnUiThread {
                                    if (isSearchCancelled) return@runOnUiThread
                                    hideGlassSearchBanner()
                                    libraryByKey[tr.key] = tr
                                    enqueueEnd(listOf(tr))
                                }
                            }.start()
                        }
                    })
                    card.addView(actionRow)
                    ytResultsBox.addView(card)
                }
            }
        }.start()
    }

    private fun playYouTubeVideoItem(item: YouTubeVideoItem, watchVideo: Boolean) {
        val actionLabel = if (watchVideo) "video" else "audio"
        showGlassSearchBanner("Cargando $actionLabel de YouTube: ${item.title}...", onCancel = {
            isSearchCancelled = true
            activeSearchThread?.interrupt()
            hideGlassSearchBanner()
        })

        activeSearchThread = Thread {
            val stream = YouTubeEngine.resolveStreams(item.videoId, item)
            if (isSearchCancelled) return@Thread
            val audioStream = stream.audioStreamUrl.ifBlank { stream.videoStreamUrl }
            val videoStream = stream.videoStreamUrl.ifBlank { stream.audioStreamUrl }
            val durSec = if (item.durationSec > 0) item.durationSec else stream.durationSec
            val tr = SonoraStore.hydrate(
                this,
                TrackItem(
                    title = item.title.ifBlank { stream.title },
                    artist = item.author.ifBlank { stream.author },
                    album = "YouTube (${item.videoId})",
                    coverUrl = item.thumbnailUrl.ifBlank { stream.thumbnailUrl },
                    audioUrl = audioStream,
                    videoUrl = videoStream,
                    quality = if (watchVideo) "YOUTUBE \u2022 VIDEO" else "YOUTUBE \u2022 AUDIO",
                    provider = "YouTube",
                    durationMs = durSec * 1000L,
                    isLocal = false
                )
            )
            runOnUiThread {
                if (isSearchCancelled) return@runOnUiThread
                hideGlassSearchBanner()
                isVideoMode = watchVideo
                libraryByKey[tr.key] = tr
                if (localTracksList.none { it.key == tr.key }) {
                    localTracksList.add(0, tr)
                    refreshLibrary()
                }

                playList(listOf(tr), 0, autoPlay = true)
                updateMode()
                updateStageLayout()
                switchTab(0)
            }
        }.apply { start() }
    }

    private fun playTrackOnYouTube(query: String, watchVideo: Boolean) {
        showGlassSearchBanner("Buscando en YouTube: '$query'...", onCancel = {
            isSearchCancelled = true
            activeSearchThread?.interrupt()
            hideGlassSearchBanner()
        })
        activeSearchThread = Thread {
            val items = YouTubeEngine.search(query, 3)
            if (isSearchCancelled) return@Thread
            val best = items.firstOrNull()
            runOnUiThread {
                if (isSearchCancelled) return@runOnUiThread
                if (best != null) {
                    playYouTubeVideoItem(best, watchVideo = watchVideo)
                } else {
                    hideGlassSearchBanner()
                    Toast.makeText(this, "No se encontró el video en YouTube", Toast.LENGTH_SHORT).show()
                }
            }
        }.apply { start() }
    }

    /**
     * Customizable Live Notification Panel inside DDMC + Android System Notification sync.
     * Supports animated GIF covers, live Video playback, Doki Chibi companion, synced lyrics,
     * uncropped cover art of any dimension, and live customization toggles.
     */
    private fun showLiveNotificationPanelSheet() {
        showSheet(fullHeight = true) { root, dialog ->
            val prefs = getSharedPreferences("sonora_prefs", Context.MODE_PRIVATE)
            val acc = try { Color.parseColor(Pal.ACCENT) } catch (_: Exception) { Color.parseColor("#38BDF8") }
            val sec = try { Color.parseColor(Pal.CYAN) } catch (_: Exception) { Color.parseColor("#F472B6") }
            val onAcc = Pal.contrastOn(acc)
            val t = tracks.getOrNull(currentTrackIndex)

            var showGif = prefs.getBoolean("notif_show_gif", true)
            var showVideo = prefs.getBoolean("notif_show_video", true)
            var showDoki = prefs.getBoolean("notif_show_doki", true)
            var showLyrics = prefs.getBoolean("notif_show_lyrics", true)
            var showWave = prefs.getBoolean("notif_show_wave", true)
            var fullUncropped = prefs.getBoolean("notif_full_uncropped", true)

            root.addView(
                sheetTitle(
                    "Panel de Notificaciones DDMC (GIF + Video + Doki)",
                    "Vista en vivo de lo que estás escuchando con soporte de GIF animado, Video, Chibi Doki y carátula completa sin recortes"
                )
            )

            // Live Notification Shade Card
            val notifCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(
                        Color.parseColor(Pal.STAGE_TOP),
                        Color.parseColor(Pal.STAGE_MID),
                        Color.parseColor(Pal.STAGE_BOTTOM)
                    )
                ).apply {
                    cornerRadius = dpf(24f)
                    setStroke(dp(1), acc)
                }
                setPadding(dp(14), dp(14), dp(14), dp(14))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(12) }
            }

            // Notification Header Row: App Badge + Active Quality + Doki Pill
            val notifHeader = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, dp(10))
            }
            notifHeader.addView(TextView(this).apply {
                text = "DOKI DOKI MUSIC CLUB (DDMC) \u2022 EN VIVO"
                textSize = 10.5f
                letterSpacing = 0.06f
                setTypeface(null, Typeface.BOLD)
                setTextColor(acc)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            notifHeader.addView(TextView(this).apply {
                text = t?.quality ?: "HI-RES 64-BIT"
                textSize = 10f
                setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                setTextColor(onAcc)
                background = GradientDrawable().apply {
                    cornerRadius = dpf(8f)
                    setColor(acc)
                }
                setPadding(dp(8), dp(3), dp(8), dp(3))
            })
            notifCard.addView(notifHeader)

            // Media Stage inside Notification Panel (Supports Uncropped Cover / Animated GIF / Video / Doki Chibi)
            val mediaFrame = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(if (fullUncropped) 236 else 164)
                ).apply { bottomMargin = dp(10) }
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#080B12"))
                    cornerRadius = dpf(18f)
                    setStroke(dp(1), Color.parseColor("#2AFFFFFF"))
                }
                clipToOutline = true
            }

            val notifCoverImg = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = if (fullUncropped) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
            }
            if (t != null) {
                notifCoverImg.bindCover(t, 640)
            } else {
                notifCoverImg.setImageResource(R.drawable.ic_music)
                notifCoverImg.setPadding(dp(36), dp(36), dp(36), dp(36))
                notifCoverImg.setColorFilter(acc)
            }
            mediaFrame.addView(notifCoverImg)

            // Live Video Surface inside Notification Panel when track has video and showVideo is enabled
            var notifVideoView: VideoView? = null
            if (showVideo && t != null && t.videoUrl.isNotEmpty() && !t.videoUrl.contains("youtube.com/watch")) {
                val vv = VideoView(this).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER
                    )
                    setVideoURI(Uri.parse(t.videoUrl))
                    setOnPreparedListener { mp ->
                        mp.setVolume(0f, 0f)
                        mp.isLooping = true
                        start()
                    }
                    setOnErrorListener { _, _, _ -> true }
                }
                notifVideoView = vv
                mediaFrame.addView(vv)
            }

            // Live Doki Chibi Companion overlay inside the Notification Panel
            val notifDokiSprite = DokiStageSpriteView(this).apply {
                layoutParams = FrameLayout.LayoutParams(dp(105), dp(145), Gravity.BOTTOM or Gravity.END).apply {
                    rightMargin = dp(8)
                    bottomMargin = dp(4)
                }
                isPlayingMusic = exoPlayer.isPlaying
                val customSprite = prefs.getString("doki_custom_asset", "") ?: ""
                if (customSprite.isNotBlank()) {
                    setCustomAsset(customSprite)
                } else {
                    setDoki(selectedDokiId, selectedDokiPose, selectedDokiExpr)
                }
                visibility = if (showDoki) View.VISIBLE else View.GONE
            }
            mediaFrame.addView(notifDokiSprite)

            // Format / GIF / Video indicator pill in top-left of Notification Media Stage
            val isGif = t?.coverUrl?.lowercase()?.contains(".gif") == true
            val hasVid = t?.videoUrl?.isNotEmpty() == true
            val mediaBadgeText = when {
                hasVid && showVideo -> "VIDEO EN VIVO + ${if (fullUncropped) "SIN RECORTES" else "CROP"}"
                isGif && showGif -> "GIF ANIMADO + ${if (fullUncropped) "SIN RECORTES" else "CROP"}"
                else -> "PORTADA COMPLETA (CUALQUIER TAMAÑO)"
            }
            mediaFrame.addView(TextView(this).apply {
                text = mediaBadgeText
                textSize = 9.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    cornerRadius = dpf(10f)
                    setColor(Color.parseColor("#B8090D16"))
                    setStroke(dp(1), acc)
                }
                setPadding(dp(8), dp(4), dp(8), dp(4))
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.START
                ).apply {
                    leftMargin = dp(8)
                    topMargin = dp(8)
                }
            })
            notifCard.addView(mediaFrame)

            // Track Title, Artist, Album
            notifCard.addView(TextView(this).apply {
                text = t?.title ?: "Ninguna pista activa"
                textSize = 17f
                setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
                setTextColor(Color.WHITE)
            })
            notifCard.addView(TextView(this).apply {
                text = if (t != null) "${t.artist} \u2022 ${t.album}" else "Reproduce música local o de YouTube"
                textSize = 12.5f
                setTextColor(Color.parseColor(Pal.SUB))
                setPadding(0, dp(2), 0, dp(6))
            })

            // Synced Lyric Line inside Notification Panel
            if (showLyrics) {
                val activeLyric = if (stageLyricViews.size >= 4) {
                    val c = if (stageLyricViews.size >= 11) 3 else 2
                    stageLyricViews.getOrNull(c)?.text?.toString().orEmpty()
                } else ""
                notifCard.addView(TextView(this).apply {
                    text = if (activeLyric.isNotBlank()) activeLyric else "Letras sincronizadas listas"
                    textSize = 12.5f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(sec)
                    background = GradientDrawable().apply {
                        cornerRadius = dpf(10f)
                        setColor(Color.parseColor("#18FFFFFF"))
                    }
                    setPadding(dp(10), dp(6), dp(10), dp(6))
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(8) }
                })
            }

            // Live Visualizer Strip inside Notification Panel
            if (showWave) {
                notifCard.addView(VisualizerView(this).apply {
                    isPlaying = exoPlayer.isPlaying
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(24)
                    ).apply { bottomMargin = dp(6) }
                })
            }

            // Transport Controls inside Notification Panel
            val ctrlRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, 0)
            }
            ctrlRow.addView(Pill(this, "Anterior", 0, 36).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) }
                setOnClickListener {
                    playPrevious()
                    dialog.dismiss()
                    showLiveNotificationPanelSheet()
                }
            })
            ctrlRow.addView(Pill(this, if (exoPlayer.isPlaying) "Pausar" else "Reproducir", 0, 36).apply {
                setActive(true)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f).apply { marginEnd = dp(6) }
                setOnClickListener {
                    togglePlayPause()
                    dialog.dismiss()
                    showLiveNotificationPanelSheet()
                }
            })
            ctrlRow.addView(Pill(this, "Siguiente", 0, 36).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    playNext()
                    dialog.dismiss()
                    showLiveNotificationPanelSheet()
                }
            })
            notifCard.addView(ctrlRow)

            // Quick Mode Toggle Row (Audio Only vs Watch Video + Change Cover GIF/Video)
            val modeRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(8), 0, 0)
            }
            modeRow.addView(Pill(this, if (isVideoMode) "Pasar a Solo Escuchar" else "Ver Video en Escenario", R.drawable.ic_video, 34).apply {
                setActive(isVideoMode)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) }
                setOnClickListener {
                    if (t != null && t.videoUrl.isNotEmpty()) {
                        isVideoMode = !isVideoMode
                        updateMode()
                        dialog.dismiss()
                    } else if (t != null) {
                        dialog.dismiss()
                        playTrackOnYouTube("${t.title} ${t.artist}", watchVideo = true)
                    }
                }
            })
            modeRow.addView(Pill(this, "Cambiar Portada / Video", R.drawable.ic_album, 34).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    dialog.dismiss()
                    showGifCoverOptionsSheet()
                }
            })
            notifCard.addView(modeRow)
            root.addView(notifCard)

            // Customization Toggles Section
            root.addView(TextView(this).apply {
                text = "PERSONALIZAR PANEL DE NOTIFICACIONES Y SISTEMA ANDROID"
                textSize = 11f
                letterSpacing = 0.06f
                setTypeface(null, Typeface.BOLD)
                setTextColor(acc)
                setPadding(0, dp(4), 0, dp(6))
            })

            fun addToggleRow(label: String, checked: Boolean, key: String, onToggle: (Boolean) -> Unit) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = GradientDrawable().apply {
                        cornerRadius = dpf(12f)
                        setColor(Color.parseColor("#141926"))
                        setStroke(dp(1), if (checked) acc else Color.parseColor("#24FFFFFF"))
                    }
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(6) }
                    isClickable = true
                    setOnClickListener {
                        val next = !checked
                        prefs.edit().putBoolean(key, next).apply()
                        onToggle(next)
                        SonoraWidgetProvider.updateAllWidgets(this@MainActivity, SonoraWidgetProvider.activeCoverBitmap)
                        dialog.dismiss()
                        showLiveNotificationPanelSheet()
                    }
                }
                row.addView(TextView(this).apply {
                    text = label
                    textSize = 13f
                    setTextColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                row.addView(TextView(this).apply {
                    text = if (checked) "ACTIVO" else "OCULTO"
                    textSize = 11f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(if (checked) onAcc else Color.parseColor("#94A3B8"))
                    background = GradientDrawable().apply {
                        cornerRadius = dpf(8f)
                        setColor(if (checked) acc else Color.parseColor("#1E2638"))
                    }
                    setPadding(dp(8), dp(4), dp(8), dp(4))
                })
                root.addView(row)
            }

            addToggleRow("Mostrar Portada 100% Completa Sin Recortes (FIT_CENTER)", fullUncropped, "notif_full_uncropped") { fullUncropped = it }
            addToggleRow("Reproducir Portada GIF / WebP Animado en el Panel", showGif, "notif_show_gif") { showGif = it }
            addToggleRow("Reproducir Vista Previa de Video en el Panel", showVideo, "notif_show_video") { showVideo = it }
            addToggleRow("Mostrar Compañera Doki Chibi Interactiva en el Panel", showDoki, "notif_show_doki") { showDoki = it }
            addToggleRow("Mostrar Verso de Karaoke Sincronizado en Vivo", showLyrics, "notif_show_lyrics") { showLyrics = it }
            addToggleRow("Mostrar Barra Espectral Reactiva en el Panel", showWave, "notif_show_wave") { showWave = it }

            dialog.setOnDismissListener {
                try { notifVideoView?.stopPlayback() } catch (_: Exception) {}
            }
        }
    }

    private fun performCatalogQuery(query: String) {
        catalogStatusText.visibility = View.VISIBLE
        catalogStatusText.text = "Consultando catálogo global y metadatos..."
        catalogResultsBox.removeAllViews()
        currentFocus?.let { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(it.windowToken, 0) }

        Thread {
            val results = StudioCatalog.searchOrResolve(query)
            runOnUiThread {
                catalogResultsBox.removeAllViews()
                if (results.isEmpty()) {
                    catalogStatusText.text = "No se encontraron resultados para '$query'."
                    return@runOnUiThread
                }
                catalogStatusText.text = "${results.size} pistas encontradas \u2022 Toca para reproducir o guardar en Biblioteca"
                for (item in results) {
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setBackgroundResource(R.drawable.bg_card_spotiflac)
                        setPadding(dp(10), dp(10), dp(10), dp(10))
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            bottomMargin = dp(8)
                        }
                        isClickable = true
                        addPressFeedback(0.98f)
                        setOnClickListener { playCatalogItem(item) }
                    }
                    val cover = CoverView(this, 52, 10).apply {
                        layoutParams = LinearLayout.LayoutParams(dp(52), dp(52)).apply { marginEnd = dp(12) }
                        bindUrl(item.coverUrl)
                    }
                    val meta = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    meta.addView(TextView(this).apply {
                        text = item.title
                        textSize = 14.5f
                        setTextColor(Color.WHITE)
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
                    })
                    meta.addView(TextView(this).apply {
                        text = "${item.artist} \u2022 ${item.album}"
                        textSize = 12f
                        setTextColor(Color.parseColor(Pal.SUB))
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        setPadding(0, dp(2), 0, 0)
                    })
                    meta.addView(TextView(this).apply {
                        val dur = formatClock(item.durationSec * 1000L)
                        text = "$dur \u2022 Carátula 1000x1000 HD"
                        textSize = 10.5f
                        setTextColor(Color.parseColor(Pal.ACCENT))
                        setPadding(0, dp(2), 0, 0)
                    })

                    val saveBtn = Pill(this, "Guardar", R.drawable.ic_plus, 34).apply {
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            marginStart = dp(8)
                        }
                        setOnClickListener { downloadCatalogItemToLibrary(item, this) }
                    }

                    row.addView(cover)
                    row.addView(meta)
                    row.addView(saveBtn)
                    catalogResultsBox.addView(row)
                }
            }
        }.start()
    }

    private fun playCatalogItem(item: CatalogTrack) {
        if (item.previewUrl.isBlank()) {
            Toast.makeText(this, "Esta pista no incluye stream directo", Toast.LENGTH_SHORT).show()
            return
        }
        val t = SonoraStore.hydrate(
            this,
            TrackItem(
                title = item.title,
                artist = item.artist,
                album = item.album,
                coverUrl = item.coverUrl,
                audioUrl = item.previewUrl,
                quality = "Catálogo \u2022 Stream",
                provider = "Catálogo",
                durationMs = 30_000L,
                isLocal = false
            )
        )
        val nextList = tracks.toMutableList().apply { add(0, t) }
        playList(nextList, 0, autoPlay = true)
        switchTab(0)
    }

    private fun downloadCatalogItemToLibrary(item: CatalogTrack, btn: Pill) {
        btn.setLabel("Guardando...")
        btn.isEnabled = false
        Thread {
            val saved = StudioCatalog.downloadTrackToLocal(this, item) { prog ->
                runOnUiThread { btn.setLabel("$prog%") }
            }
            runOnUiThread {
                btn.isEnabled = true
                if (saved != null) {
                    btn.setLabel("En Biblioteca")
                    btn.setActive(true)
                    localTracksList.removeAll { it.key == saved.key }
                    localTracksList.add(0, saved)
                    libraryByKey[saved.key] = saved
                    refreshLibrary()
                    Toast.makeText(this, "'${saved.title}' guardada en tu Biblioteca", Toast.LENGTH_SHORT).show()
                } else {
                    btn.setLabel("Reintentar")
                    Toast.makeText(this, "No se pudo descargar la pista", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun batchEnrichLibrary() {
        if (localTracksList.isEmpty()) {
            Toast.makeText(this, "No hay pistas locales para enriquecer", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "Enriqueciendo carátulas HD y metadatos de ${localTracksList.size} pistas...", Toast.LENGTH_SHORT).show()
        Thread {
            var count = 0
            for (t in localTracksList.toList()) {
                if (StudioCatalog.enrichTrack(this, t)) {
                    count++
                    for (qItem in tracks.filter { it.key == t.key }) {
                        qItem.title = t.title
                        qItem.artist = t.artist
                        qItem.album = t.album
                        qItem.coverUrl = t.coverUrl
                    }
                }
            }
            runOnUiThread {
                bindNowPlaying()
                refreshLibrary()
                refreshPlaylists()
                Toast.makeText(this, "$count pistas actualizadas con carátulas HD y metadatos", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun refreshRecentStreams() {
        if (!::onlineRecentBox.isInitialized) return
        onlineRecentBox.removeAllViews()
        val urls = SonoraStore.recentUrls(this)
        if (urls.isEmpty()) {
            onlineRecentBox.addView(TextView(this).apply {
                text = "Los enlaces directos que reproduzcas se guardarán aquí."
                textSize = 12f
                setTextColor(Color.parseColor(Pal.MUTED))
            })
            return
        }
        for (u in urls) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.bg_card_spotiflac)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(6)
                }
                isClickable = true
                setOnClickListener { playStreamUrl(u) }
            }
            row.addView(ImageView(this).apply {
                setImageResource(R.drawable.ic_link)
                setColorFilter(Color.parseColor(Pal.CYAN))
                layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(10) }
            })
            row.addView(TextView(this).apply {
                text = u
                textSize = 12.5f
                setTextColor(Color.WHITE)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            onlineRecentBox.addView(row)
        }
    }

    private fun playStreamUrl(
        url: String,
        customTitle: String? = null,
        customArtist: String? = null,
        customQuality: String? = null
    ) {
        val isVideo = url.endsWith(".mp4", true) || url.endsWith(".webm", true) || url.endsWith(".mkv", true)
        val item = SonoraStore.hydrate(
            this,
            TrackItem(
                title = customTitle ?: url.substringAfterLast('/').substringBefore('?').ifBlank { "Stream en vivo" },
                artist = customArtist ?: "Transmisión en directo",
                album = "Radio y Streams",
                audioUrl = url,
                videoUrl = if (isVideo) url else "",
                quality = customQuality ?: if (url.contains("flac", true)) "FLAC \u2022 Stream" else "Stream directo",
                provider = "Stream",
                isLocal = false
            )
        )
        val existingIdx = tracks.indexOfFirst { it.key == item.key }
        if (existingIdx >= 0) {
            playList(tracks.toList(), existingIdx, autoPlay = true)
        } else {
            val nextList = tracks.toMutableList().apply { add(0, item) }
            playList(nextList, 0, autoPlay = true)
        }
        switchTab(0)
    }

    // ==========================================================
    // TAB 4: PRO SOUND ENGINE (3-TAB MUSICOLET 31-BAND EQ, ROTARY KNOBS & HALL REVERB)
    // ==========================================================
    private fun buildSoundTab(): View {
        return MusicoletEqualizerBuilder.build(this) {}
    }

    // ==========================================================
    // TAB 5: MUSICOLET WIDGETS CUSTOMIZER (BASIC, ADVANCED & CURRENT QUEUE)
    // ==========================================================
    private fun buildWidgetCustomizerTab(): ScrollView {
        return MusicoletWidgetCustomizerBuilder.build(this) { tracks.getOrNull(currentTrackIndex) }
    }

    // ==========================================================
    // SHEETS, DIALOGS & TRACK TOOLS
    // ==========================================================
    private fun showQueueSheet() {
        showSheet(fullHeight = true) { root, dialog ->
            root.addView(
                sheetTitle(
                    "Cola de reproducción",
                    if (tracks.isEmpty()) "Sin pistas en cola"
                    else "${tracks.size} pistas \u2022 Arrastra para reordenar o desliza para quitar"
                )
            )

            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, dp(10))
            }
            lateinit var qAdapter: RowAdapter

            fun buildRows(): List<Row> =
                if (tracks.isEmpty()) listOf(Row.Empty("La cola está vacía", "Elige canciones desde tu Biblioteca o Catálogo.", false))
                else tracks.mapIndexed { i, t -> Row.Song(t, tracks, i) }

            actions.addView(Pill(this, "Ordenar", R.drawable.ic_sort, 34).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) }
                setOnClickListener {
                    if (tracks.isEmpty()) toastNoTrack() else {
                        MusicoletModals.showFullSortDialog(
                            activity = this@MainActivity,
                            titleText = "Ordenar pistas de esta cola",
                            listToSort = tracks
                        ) { sorted, label ->
                            val wasPlaying = exoPlayer.isPlaying
                            playList(sorted, 0, autoPlay = wasPlaying)
                            qAdapter.submit(buildRows())
                            Toast.makeText(this@MainActivity, "Cola ordenada: $label", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            })
            actions.addView(Pill(this, "Guardar lista", R.drawable.ic_list_plus, 34).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) }
                setOnClickListener {
                    if (tracks.isEmpty()) toastNoTrack() else {
                        dialog.dismiss()
                        promptNewPlaylist(seed = tracks.toList())
                    }
                }
            })
            actions.addView(Pill(this, "Vaciar", R.drawable.ic_trash, 34).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    tracks.clear()
                    currentTrackIndex = 0
                    loadedSig = 0
                    exoPlayer.stop()
                    exoPlayer.clearMediaItems()
                    saveQueueState()
                    showEmptyPlayer()
                    SonoraWidgetProvider.updateAllWidgets(this@MainActivity)
                    dialog.dismiss()
                }
            })
            root.addView(actions)

            val host = object : RowHost by this@MainActivity {
                override fun onPlay(list: List<TrackItem>, index: Int) {
                    if (index in tracks.indices) {
                        currentTrackIndex = index
                        exoPlayer.seekTo(index, tracks[index].trimStartMs)
                        exoPlayer.prepare()
                        exoPlayer.play()
                        bindNowPlaying()
                        qAdapter.submit(buildRows())
                    }
                }
                override fun onTrailing(track: TrackItem, index: Int) {
                    if (index !in tracks.indices) return
                    tracks.removeAt(index)
                    if (index < exoPlayer.mediaItemCount) exoPlayer.removeMediaItem(index)
                    if (tracks.isEmpty()) {
                        currentTrackIndex = 0
                        showEmptyPlayer()
                    } else {
                        currentTrackIndex = exoPlayer.currentMediaItemIndex.coerceIn(0, tracks.lastIndex)
                        bindNowPlaying()
                    }
                    saveQueueState()
                    qAdapter.submit(buildRows())
                }
            }
            qAdapter = RowAdapter(this, host, R.drawable.ic_close)
            qAdapter.submit(buildRows())

            val rv = RecyclerView(this).apply {
                layoutManager = LinearLayoutManager(this@MainActivity)
                adapter = qAdapter
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            }

            val touch = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
            ) {
                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    val from = viewHolder.bindingAdapterPosition
                    val to = target.bindingAdapterPosition
                    if (from !in tracks.indices || to !in tracks.indices) return false
                    val moved = tracks.removeAt(from)
                    tracks.add(to, moved)
                    if (from < exoPlayer.mediaItemCount && to < exoPlayer.mediaItemCount) {
                        exoPlayer.moveMediaItem(from, to)
                    }
                    currentTrackIndex = exoPlayer.currentMediaItemIndex.coerceIn(0, tracks.lastIndex)
                    qAdapter.move(from, to)
                    saveQueueState()
                    viewHolder.itemView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    return true
                }

                override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                    super.onSelectedChanged(viewHolder, actionState)
                    if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                        viewHolder.itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        viewHolder.itemView.animate()
                            .scaleX(1.05f)
                            .scaleY(1.05f)
                            .translationZ(dpf(14f))
                            .setDuration(160)
                            .setInterpolator(OvershootInterpolator(1.3f))
                            .start()
                    }
                }

                override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                    super.clearView(recyclerView, viewHolder)
                    viewHolder.itemView.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .translationZ(0f)
                        .setDuration(200)
                        .setInterpolator(OvershootInterpolator(1.2f))
                        .start()
                    viewHolder.itemView.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    val pos = viewHolder.bindingAdapterPosition
                    if (pos in tracks.indices) host.onTrailing(tracks[pos], pos)
                }
            })
            touch.attachToRecyclerView(rv)
            if (currentTrackIndex in tracks.indices) rv.scrollToPosition(currentTrackIndex)
            root.addView(rv)
        }
    }

    private fun showLyricsSheet() {
        val t = tracks.getOrNull(currentTrackIndex)
        if (t == null) { toastNoTrack(); return }

        showSheet(fullHeight = true) { root, dialog ->
            root.addView(sheetTitle(t.title, "${t.artist} \u2022 Letra sincronizada LRCLIB / .LRC"))

            val bar = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, dp(10))
            }
            bar.addView(Pill(this, "Importar .LRC", R.drawable.ic_folder, 34).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) }
                setOnClickListener {
                    dialog.dismiss()
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }
                    lrcPickerLauncher.launch(intent)
                }
            })
            bar.addView(Pill(this, "Buscar en LRCLIB", R.drawable.ic_refresh, 34).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    dialog.dismiss()
                    Lyrics.clear(this@MainActivity, t.key)
                    Toast.makeText(this@MainActivity, "Buscando letra...", Toast.LENGTH_SHORT).show()
                    loadLyricsFor(t, force = true)
                }
            })
            root.addView(bar)

            val scroll = ScrollView(this).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
                overScrollMode = View.OVER_SCROLL_NEVER
            }
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(8), dp(12), dp(8), dp(48))
            }
            val views = ArrayList<TextView>()
            if (lyricsLines.isEmpty()) {
                col.addView(TextView(this).apply {
                    text = "No se encontró letra sincronizada para esta pista.\n\nPuedes importar un archivo .LRC local o pulsar 'Buscar en LRCLIB' tras editar el título y artista."
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setTextColor(Color.parseColor(Pal.SUB))
                    setPadding(dp(16), dp(36), dp(16), dp(36))
                })
            } else {
                for (line in lyricsLines) {
                    val tv = TextView(this).apply {
                        text = line.text.ifBlank { "\u266A" }
                        textSize = 16.5f
                        setTextColor(Color.parseColor(if (lyricsSynced) Pal.MUTED else Pal.TEXT))
                        setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
                        setPadding(dp(8), dp(10), dp(8), dp(10))
                        if (line.timeMs >= 0) {
                            isClickable = true
                            setOnClickListener {
                                exoPlayer.seekTo(line.timeMs)
                                if (!exoPlayer.isPlaying) exoPlayer.play()
                            }
                        }
                    }
                    views.add(tv)
                    col.addView(tv)
                }
            }
            scroll.addView(col)
            root.addView(scroll)

            lyricsSheetLines = views
            lyricsSheetScroll = scroll
            lyricsSheetIndex = -1
            dialog.setOnDismissListener {
                lyricsSheetLines = emptyList()
                lyricsSheetScroll = null
                lyricsSheetIndex = -1
            }
            updateLyricsTick(exoPlayer.currentPosition)
        }
    }

    private fun showSpeedSheet() {
        showSheet { root, _ ->
            root.addView(sheetTitle("Velocidad y Tono", "Ajusta el tempo o aplica efectos Nightcore / Slowed"))

            val speedLabel = TextView(this).apply {
                text = String.format("Velocidad: %.2fx", currentSpeed)
                textSize = 13.5f
                setTextColor(Color.WHITE)
                setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            }
            val pitchLabel = TextView(this).apply {
                text = String.format("Tono (Pitch): %.2fx", currentPitch)
                textSize = 13.5f
                setTextColor(Color.WHITE)
                setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
                setPadding(0, dp(10), 0, 0)
            }

            lateinit var speedSeek: SeekBar
            lateinit var pitchSeek: SeekBar

            fun applySp(s: Float, p: Float) {
                currentSpeed = s.coerceIn(0.5f, 2.0f)
                currentPitch = p.coerceIn(0.5f, 2.0f)
                exoPlayer.playbackParameters = PlaybackParameters(currentSpeed, currentPitch)
                SonoraStore.putFloat(this, "speed", currentSpeed)
                SonoraStore.putFloat(this, "pitch", currentPitch)
                speedBtn.setLabel(String.format("%.2gx", currentSpeed))
                speedBtn.setActive(currentSpeed != 1f || currentPitch != 1f)
                speedLabel.text = String.format("Velocidad: %.2fx", currentSpeed)
                pitchLabel.text = String.format("Tono (Pitch): %.2fx", currentPitch)
                speedSeek.progress = ((currentSpeed - 0.5f) * 100).toInt()
                pitchSeek.progress = ((currentPitch - 0.5f) * 100).toInt()
            }

            speedSeek = SeekBar(this).apply {
                max = 150
                progress = ((currentSpeed - 0.5f) * 100).toInt()
                val tint = ColorStateList.valueOf(Color.parseColor(Pal.ACCENT))
                progressTintList = tint; thumbTintList = tint
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                        if (fromUser) applySp(0.5f + p / 100f, currentPitch)
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            }
            pitchSeek = SeekBar(this).apply {
                max = 150
                progress = ((currentPitch - 0.5f) * 100).toInt()
                val tint = ColorStateList.valueOf(Color.parseColor(Pal.CYAN))
                progressTintList = tint; thumbTintList = tint
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                        if (fromUser) applySp(currentSpeed, 0.5f + p / 100f)
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            }

            root.addView(speedLabel)
            root.addView(speedSeek)
            root.addView(pitchLabel)
            root.addView(pitchSeek)

            val presetScroll = HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                setPadding(0, dp(12), 0, 0)
            }
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val presets = listOf(
                "Normal 1.0x" to (1.0f to 1.0f),
                "Slowed 0.85x" to (0.85f to 0.85f),
                "Nightcore 1.25x" to (1.25f to 1.25f),
                "1.5x" to (1.5f to 1.0f),
                "2.0x" to (2.0f to 1.0f)
            )
            for ((label, pair) in presets) {
                row.addView(Pill(this, label, 0, 34).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        marginEnd = dp(8)
                    }
                    setOnClickListener { applySp(pair.first, pair.second) }
                })
            }
            presetScroll.addView(row)
            root.addView(presetScroll)
        }
    }

    private fun showSleepTimerSheet() {
        val items = mutableListOf(
            SheetItem(R.drawable.ic_timer, "10 minutos"),
            SheetItem(R.drawable.ic_timer, "20 minutos"),
            SheetItem(R.drawable.ic_timer, "30 minutos"),
            SheetItem(R.drawable.ic_timer, "45 minutos"),
            SheetItem(R.drawable.ic_timer, "60 minutos"),
            SheetItem(R.drawable.ic_timer, "90 minutos"),
            SheetItem(R.drawable.ic_moon, "Pausar al terminar la pista actual")
        )
        if (SleepTimer.active || exoPlayer.pauseAtEndOfMediaItems) {
            items.add(SheetItem(R.drawable.ic_close, "Desactivar temporizador", danger = true))
        }
        val mins = intArrayOf(10, 20, 30, 45, 60, 90)
        showActionSheet("Temporizador de apagado", "Atenúa el volumen suavemente antes de pausar", items) { which ->
            when {
                which in mins.indices -> {
                    exoPlayer.pauseAtEndOfMediaItems = false
                    SleepTimer.start(mins[which])
                    Toast.makeText(this, "Temporizador activo: ${mins[which]} min", Toast.LENGTH_SHORT).show()
                }
                which == 6 -> {
                    SleepTimer.cancel()
                    exoPlayer.pauseAtEndOfMediaItems = true
                    sleepPill.setLabel("Fin de pista")
                    sleepPill.setActive(true)
                    Toast.makeText(this, "Se pausará al terminar la canción actual", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    SleepTimer.cancel()
                    exoPlayer.pauseAtEndOfMediaItems = false
                    refreshSleepLabel(0)
                    Toast.makeText(this, "Temporizador desactivado", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun refreshSleepLabel(remMs: Long) {
        if (!::sleepPill.isInitialized) return
        when {
            remMs > 0 -> {
                sleepPill.setLabel(formatClock(remMs))
                sleepPill.setActive(true)
            }
            exoPlayer.pauseAtEndOfMediaItems -> {
                sleepPill.setLabel("Fin de pista")
                sleepPill.setActive(true)
            }
            else -> {
                sleepPill.setLabel("Temporizador")
                sleepPill.setActive(false)
            }
        }
    }

    private fun showTrackMenu(track: TrackItem) {
        val favLabel = if (track.isFavorite) "Quitar de Favoritas" else "Añadir a Favoritas"
        val items = listOf(
            SheetItem(R.drawable.ic_play_filled, "Reproducir ahora"),
            SheetItem(R.drawable.ic_next_up, "Reproducir a continuación"),
            SheetItem(R.drawable.ic_list_plus, "Añadir al final de la cola"),
            SheetItem(if (track.isFavorite) R.drawable.ic_heart_filled else R.drawable.ic_heart, favLabel),
            SheetItem(R.drawable.ic_clock, "Marcadores y Notas (Lesezeichen / Notizen)"),
            SheetItem(R.drawable.ic_waveform, "Analizador de espectro y calidad (Spek / BPM / Key)"),
            SheetItem(R.drawable.ic_sparkles, "Auto-completar carátula HD (1000x1000) y etiquetas"),
            SheetItem(R.drawable.ic_plus, "Añadir a una lista de reproducción"),
            SheetItem(R.drawable.ic_pencil, "Editar etiquetas (Título, Artista, Álbum)"),
            SheetItem(R.drawable.ic_scissors, "Recortar inicio y final (Audio Trimmer)"),
            SheetItem(R.drawable.ic_album, "Ver álbum: ${track.album}"),
            SheetItem(R.drawable.ic_user, "Ver artista: ${track.artist}"),
            SheetItem(R.drawable.ic_disc, "Audio de esta pista (Bajos y Ganancia)"),
            SheetItem(R.drawable.ic_video, "Elegir Video como Portada"),
            SheetItem(R.drawable.ic_share, "Compartir pista"),
            SheetItem(R.drawable.ic_info, "Información completa de la pista (24 campos)")
        )
        showActionSheet(track.title, "${track.artist} \u2022 ${track.album}", items) { which ->
            when (which) {
                0 -> playList(listOf(track), 0, autoPlay = true)
                1 -> enqueueNext(listOf(track))
                2 -> enqueueEnd(listOf(track))
                3 -> {
                    track.isFavorite = SonoraStore.toggleFavorite(this, track.key)
                    localTracksList.find { it.key == track.key }?.isFavorite = track.isFavorite
                    tracks.find { it.key == track.key }?.isFavorite = track.isFavorite
                    if (tracks.getOrNull(currentTrackIndex)?.key == track.key) {
                        favBtn.setImageResource(if (track.isFavorite) R.drawable.ic_heart_filled else R.drawable.ic_heart)
                    }
                    refreshLibrary()
                    SonoraWidgetProvider.updateAllWidgets(this)
                }
                4 -> {
                    val curPos = if (tracks.getOrNull(currentTrackIndex)?.key == track.key) exoPlayer.currentPosition else 0L
                    MusicoletModals.showBookmarksDialog(this, track, curPos) { targetMs ->
                        if (tracks.getOrNull(currentTrackIndex)?.key != track.key) {
                            playList(listOf(track), 0, autoPlay = true, startPosMs = targetMs)
                        } else {
                            exoPlayer.seekTo(targetMs)
                            exoPlayer.play()
                        }
                    }
                }
                5 -> showSpectrumAnalyzerSheet(track)
                6 -> enrichSingleTrack(track)
                7 -> showAddToPlaylistSheet(track)
                8 -> showTagEditorDialog(track)
                9 -> showAudioTrimmerDialog(track)
                10 -> {
                    val list = localTracksList.filter { it.album.equals(track.album, ignoreCase = true) }
                    if (list.isNotEmpty()) {
                        libMode = LibMode.ALBUMS
                        libGroup = Row.Group("album", track.album, "${list.size} pistas", list, R.drawable.ic_album, list.firstOrNull(), true)
                        switchTab(1)
                        refreshLibrary(scrollTop = true)
                    }
                }
                11 -> {
                    val list = localTracksList.filter { it.artist.equals(track.artist, ignoreCase = true) }
                    if (list.isNotEmpty()) {
                        libMode = LibMode.ARTISTS
                        libGroup = Row.Group("artist", track.artist, "${list.size} pistas", list, R.drawable.ic_user, list.firstOrNull(), true)
                        switchTab(1)
                        refreshLibrary(scrollTop = true)
                    }
                }
                12 -> showTrackAudioCustomizerDialog(track)
                13 -> {
                    pendingVideoTrack = track
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "video/*"
                    }
                    videoCoverPickerLauncher.launch(intent)
                }
                14 -> shareTrack(track)
                15 -> showTrackDetails(track)
            }
        }
    }

    private fun showTrackDetails(track: TrackItem) {
        MusicoletModals.showFullTrackInfoModal(
            activity = this,
            track = track,
            onEditTags = { showTagEditorDialog(track) },
            onNavigateAlbum = {
                val list = localTracksList.filter { it.album.equals(track.album, ignoreCase = true) }
                if (list.isNotEmpty()) {
                    libMode = LibMode.ALBUMS
                    libGroup = Row.Group("album", track.album, "${list.size} pistas", list, R.drawable.ic_album, list.firstOrNull(), true)
                    switchTab(1)
                    refreshLibrary(scrollTop = true)
                }
            },
            onNavigateArtist = {
                val list = localTracksList.filter { it.artist.equals(track.artist, ignoreCase = true) }
                if (list.isNotEmpty()) {
                    libMode = LibMode.ARTISTS
                    libGroup = Row.Group("artist", track.artist, "${list.size} pistas", list, R.drawable.ic_user, list.firstOrNull(), true)
                    switchTab(1)
                    refreshLibrary(scrollTop = true)
                }
            }
        )
    }

    private fun shareTrack(track: TrackItem) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                if (track.isLocal && track.audioUri != null) {
                    type = "audio/*"
                    putExtra(Intent.EXTRA_STREAM, track.audioUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } else {
                    type = "text/plain"
                }
                putExtra(Intent.EXTRA_TEXT, "${track.title} \u2014 ${track.artist} (${track.album})")
            }
            startActivity(Intent.createChooser(intent, "Compartir ${track.title}"))
        } catch (_: Exception) {
            Toast.makeText(this, "No se pudo compartir la pista", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showTagEditorDialog(track: TrackItem) {
        showSheet { root, dialog ->
            root.addView(sheetTitle("Editar etiquetas", "Los cambios se guardan de forma permanente"))

            fun field(label: String, initial: String): EditText {
                root.addView(TextView(this).apply {
                    text = label
                    textSize = 12f
                    setTextColor(Color.parseColor(Pal.SUB))
                    setPadding(dp(2), dp(6), dp(2), dp(4))
                })
                return EditText(this).apply {
                    setText(initial)
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    isSingleLine = true
                    setBackgroundResource(R.drawable.bg_input_dark)
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                }.also { root.addView(it) }
            }

            val titleIn = field("Título", track.title)
            val artistIn = field("Artista", track.artist)
            val albumIn = field("Álbum", track.album)

            root.addView(Pill(this, "Guardar cambios", R.drawable.ic_check, 40).apply {
                setActive(true)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(14)
                }
                setOnClickListener {
                    val newT = titleIn.text.toString().trim().ifEmpty { track.title }
                    val newAr = artistIn.text.toString().trim().ifEmpty { track.artist }
                    val newAl = albumIn.text.toString().trim().ifEmpty { track.album }
                    for (item in (localTracksList + tracks).filter { it.key == track.key }) {
                        item.title = newT
                        item.artist = newAr
                        item.album = newAl
                    }
                    SonoraStore.saveEdit(this@MainActivity, track)
                    bindNowPlaying()
                    refreshLibrary()
                    refreshPlaylists()
                    dialog.dismiss()
                    Toast.makeText(this@MainActivity, "Etiquetas actualizadas", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    private fun showAudioTrimmerDialog(track: TrackItem) {
        val totalMs = (track.durationMs.takeIf { it > 0 } ?: exoPlayer.duration.takeIf { it > 0 } ?: 180_000L)
        showSheet { root, dialog ->
            root.addView(sheetTitle("Recortar duración (Audio Trimmer)", "${track.title} \u2022 Duración total ${formatClock(totalMs)}"))

            var startMs = track.trimStartMs.coerceIn(0L, totalMs)
            var endMs = (if (track.trimEndMs in 1..totalMs) track.trimEndMs else totalMs).coerceIn(0L, totalMs)

            val startLabel = TextView(this).apply {
                text = "Inicio: ${formatClock(startMs)}"
                textSize = 13f
                setTextColor(Color.WHITE)
            }
            val endLabel = TextView(this).apply {
                text = "Final: ${formatClock(endMs)}"
                textSize = 13f
                setTextColor(Color.WHITE)
                setPadding(0, dp(10), 0, 0)
            }

            val startSeek = SeekBar(this).apply {
                max = totalMs.toInt()
                progress = startMs.toInt()
                val tint = ColorStateList.valueOf(Color.parseColor(Pal.ACCENT))
                progressTintList = tint; thumbTintList = tint
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                        startMs = p.toLong().coerceAtMost((endMs - 1000L).coerceAtLeast(0L))
                        startLabel.text = "Inicio: ${formatClock(startMs)}"
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            }
            val endSeek = SeekBar(this).apply {
                max = totalMs.toInt()
                progress = endMs.toInt()
                val tint = ColorStateList.valueOf(Color.parseColor(Pal.CYAN))
                progressTintList = tint; thumbTintList = tint
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                        endMs = p.toLong().coerceAtLeast(startMs + 1000L)
                        endLabel.text = "Final: ${formatClock(endMs)}"
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            }

            root.addView(startLabel)
            root.addView(startSeek)
            root.addView(endLabel)
            root.addView(endSeek)

            val btnRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(14), 0, 0)
            }
            btnRow.addView(Pill(this, "Restablecer", R.drawable.ic_refresh, 38).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) }
                setOnClickListener {
                    for (item in (localTracksList + tracks).filter { it.key == track.key }) {
                        item.trimStartMs = 0L
                        item.trimEndMs = 0L
                    }
                    SonoraStore.saveEdit(this@MainActivity, track)
                    bindNowPlaying()
                    dialog.dismiss()
                    Toast.makeText(this@MainActivity, "Recorte eliminado", Toast.LENGTH_SHORT).show()
                }
            })
            btnRow.addView(Pill(this, "Aplicar recorte", R.drawable.ic_scissors, 38).apply {
                setActive(true)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    val finalEnd = if (endMs >= totalMs - 500L) 0L else endMs
                    for (item in (localTracksList + tracks).filter { it.key == track.key }) {
                        item.trimStartMs = startMs
                        item.trimEndMs = finalEnd
                    }
                    SonoraStore.saveEdit(this@MainActivity, track)
                    if (tracks.getOrNull(currentTrackIndex)?.key == track.key && exoPlayer.currentPosition < startMs) {
                        exoPlayer.seekTo(startMs)
                    }
                    bindNowPlaying()
                    dialog.dismiss()
                    Toast.makeText(this@MainActivity, "Recorte guardado", Toast.LENGTH_SHORT).show()
                }
            })
            root.addView(btnRow)
        }
    }

    private fun showGlobalMenu() {
        val items = listOf(
            SheetItem(R.drawable.ic_info, "Guía & Tutorial Completo"),
            SheetItem(R.drawable.ic_palette, "Temas & Liquid Glass"),
            SheetItem(R.drawable.ic_widgets, "Tamaños & Posiciones Touch"),
            SheetItem(R.drawable.ic_sliders, "Panel de Notificación"),
            SheetItem(R.drawable.ic_download, "Importar desde Musicolet"),
            SheetItem(R.drawable.ic_album, "Portada & Video"),
            SheetItem(R.drawable.ic_sliders, "Ajustes del Sistema"),
            SheetItem(R.drawable.ic_refresh, "Escanear Música"),
            SheetItem(R.drawable.ic_sparkles, "Carátulas HD"),
            SheetItem(R.drawable.ic_download, "Copia de Seguridad"),
            SheetItem(R.drawable.ic_folder, "Abrir Archivo"),
            SheetItem(R.drawable.ic_list_plus, "Importar Lista M3U"),
            SheetItem(R.drawable.ic_timer, "Temporizador"),
            SheetItem(R.drawable.ic_bolt, "Velocidad & Tono"),
            SheetItem(R.drawable.ic_user, "Compañeras Doki"),
            SheetItem(R.drawable.ic_download, "Actualizar desde PC"),
            SheetItem(R.drawable.ic_trash, "Limpiar Historial", danger = true)
        )
        showActionSheet("Opciones de DDMC", "${localTracksList.size} canciones en biblioteca", items) { which ->
            when (which) {
                0 -> showTutorialSheet()
                1 -> showThemeCustomizerSheet()
                2 -> showPlayerLayoutCustomizerSheet()
                3 -> showLiveNotificationPanelSheet()
                4 -> showMusicoletImportDialog()
                5 -> showGifCoverOptionsSheet()
                6 -> openFullSettings()
                7 -> {
                    val folderCount = localTracksList.map { it.provider }.distinct().size
                    MusicoletModals.showScannerModal(this, localTracksList.size, folderCount) {
                        performStorageScan(showToast = true)
                    }
                }
                8 -> batchEnrichLibrary()
                9 -> {
                    val f = SonoraStore.createBackupFile(this)
                    Toast.makeText(this, "Copia de seguridad creada: ${f.name}", Toast.LENGTH_LONG).show()
                }
                10 -> openSystemFilePicker()
                11 -> {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }
                    m3uPickerLauncher.launch(intent)
                }
                12 -> showSleepTimerSheet()
                13 -> showSpeedSheet()
                14 -> showQuickDokiSwitcherSheet()
                15 -> showOtaUpdaterSheet()
                16 -> {
                    SonoraStore.clearHistory(this)
                    refreshLibrary()
                    Toast.makeText(this, "Historial limpiado", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ==========================================================
    // PLAYBACK & QUEUE ENGINE
    // ==========================================================
    private fun getArtworkCacheFile(t: TrackItem): File {
        val dir = File(cacheDir, "artwork")
        if (!dir.exists()) dir.mkdirs()
        val safeKey = (t.key.hashCode().toLong() and 0xFFFFFFFFL).toString()
        return File(dir, "cover_$safeKey.jpg")
    }

    private fun getArtworkUriForTrack(t: TrackItem): Uri? {
        val file = getArtworkCacheFile(t)
        if (file.exists() && file.length() > 0) {
            return Uri.parse("content://${packageName}/?path=" + Uri.encode(file.absolutePath))
        }
        if (!t.coverUrl.isNullOrEmpty() && (t.coverUrl!!.startsWith("http://") || t.coverUrl!!.startsWith("https://"))) {
            return Uri.parse(t.coverUrl)
        }
        return null
    }

    private fun ensureTrackArtwork(t: TrackItem) {
        val bmp = t.coverBitmap ?: GifImageSupport.resolveTrackCoverBitmap(this, t)
        if (bmp != null) {
            t.coverBitmap = bmp
            val file = getArtworkCacheFile(t)
            if (!file.exists() || file.length() == 0L) {
                try {
                    file.parentFile?.mkdirs()
                    java.io.FileOutputStream(file).use { out ->
                        bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun toMediaItem(t: TrackItem): MediaItem {
        val uri = when {
            isVideoMode && t.videoUrl.isNotEmpty() -> Uri.parse(t.videoUrl)
            t.audioUri != null -> t.audioUri!!
            else -> Uri.parse(t.audioUrl)
        }
        val md = MediaMetadata.Builder()
            .setTitle(t.title)
            .setArtist(t.artist)
            .setAlbumTitle(t.album)
            .setSubtitle(t.quality)
            .apply {
                val bmp = t.coverBitmap ?: SonoraWidgetProvider.activeCoverBitmap
                if (bmp != null) {
                    try {
                        val stream = java.io.ByteArrayOutputStream()
                        val scaled = if (bmp.width > 720 || bmp.height > 720) {
                            val r = minOf(720f / bmp.width, 720f / bmp.height)
                            Bitmap.createScaledBitmap(bmp, (bmp.width * r).toInt().coerceAtLeast(1), (bmp.height * r).toInt().coerceAtLeast(1), true)
                        } else bmp
                        scaled.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                        setArtworkData(stream.toByteArray(), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                    } catch (_: Exception) {}
                }
                val artUri = getArtworkUriForTrack(t)
                if (artUri != null) {
                    setArtworkUri(artUri)
                }
            }
            .build()
        return MediaItem.Builder()
            .setMediaId(t.key)
            .setUri(uri)
            .setMediaMetadata(md)
            .build()
    }

    private fun enqueueNext(toAdd: List<TrackItem>) {
        if (toAdd.isEmpty()) return
        if (tracks.isEmpty()) {
            playList(toAdd, 0, autoPlay = true)
            return
        }
        val insertAt = (currentTrackIndex + 1).coerceIn(0, tracks.size)
        tracks.addAll(insertAt, toAdd)
        exoPlayer.addMediaItems(insertAt, toAdd.map { toMediaItem(it) })
        saveQueueState()
        Toast.makeText(this, "${toAdd.size} pista(s) se reproducirán a continuación", Toast.LENGTH_SHORT).show()
    }

    private fun enqueueEnd(toAdd: List<TrackItem>) {
        if (toAdd.isEmpty()) return
        if (tracks.isEmpty()) {
            playList(toAdd, 0, autoPlay = true)
            return
        }
        tracks.addAll(toAdd)
        exoPlayer.addMediaItems(toAdd.map { toMediaItem(it) })
        saveQueueState()
        Toast.makeText(this, "${toAdd.size} pista(s) añadidas a la cola", Toast.LENGTH_SHORT).show()
    }

    private fun playList(
        list: List<TrackItem>,
        startIndex: Int,
        autoPlay: Boolean = true,
        startPosMs: Long = -1L
    ) {
        if (list.isEmpty()) {
            showEmptyPlayer()
            return
        }
        val idx = startIndex.coerceIn(0, list.lastIndex)
        val sig = list.map { it.key }.hashCode() * 31 + if (isVideoMode) 1 else 0

        tracks.clear()
        tracks.addAll(list)
        currentTrackIndex = idx
        val target = tracks[idx]
        ensureTrackArtwork(target)
        val initPos = if (startPosMs >= 0L) startPosMs else target.trimStartMs

        if (sig != loadedSig || exoPlayer.mediaItemCount != tracks.size) {
            loadedSig = sig
            exoPlayer.setMediaItems(tracks.map { toMediaItem(it) }, idx, initPos)
            exoPlayer.prepare()
        } else if (exoPlayer.currentMediaItemIndex != idx) {
            try { exoPlayer.replaceMediaItem(idx, toMediaItem(target)) } catch (_: Exception) {}
            exoPlayer.seekTo(idx, initPos)
            exoPlayer.prepare()
        } else if (startPosMs >= 0L) {
            exoPlayer.seekTo(initPos)
        }

        if (autoPlay) exoPlayer.play()
        bindNowPlaying()
        saveQueueState()
    }

    private fun bindNowPlaying() {
        val t = tracks.getOrNull(currentTrackIndex)
        if (t == null) {
            showEmptyPlayer()
            return
        }
        titleText.text = t.title
        artistText.text = t.artist
        albumText.text = t.album
        if (::landscapeTitleText.isInitialized) landscapeTitleText.text = t.title
        if (::landscapeArtistText.isInitialized) landscapeArtistText.text = "${t.artist} \u2022 ${t.album}"

        val isGifCover = t.coverUrl?.lowercase()?.let { it.endsWith(".gif") || it.contains(".gif?") } == true
        val trimTag = if (t.trimStartMs > 0 || t.trimEndMs > 0) " \u2022 CORTADO" else ""
        val gifTag = if (isGifCover) " \u2022 GIF" else ""
        formatBadgeText.text = t.quality + gifTag + trimTag
        providerBadgeText.text = t.provider
        if (::bpmKeyBadgeText.isInitialized) {
            val analysis = AudioAnalyzer.analyze(this, t)
            bpmKeyBadgeText.text = "${analysis.bpm} BPM \u2022 ${analysis.camelotCode}"
        }
        val heartRes = if (t.isFavorite) R.drawable.ic_heart_filled else R.drawable.ic_heart
        val heartColor = Color.parseColor(if (t.isFavorite) Pal.ACCENT else "#CBD5E1")
        favBtn.setImageResource(heartRes)
        favBtn.setColorFilter(heartColor)
        if (::landscapeFavBtn.isInitialized) {
            landscapeFavBtn.setImageResource(heartRes)
            landscapeFavBtn.setColorFilter(heartColor)
        }

        val onCoverBitmapReady: (Bitmap?) -> Unit = { bmp ->
            if (bmp != null) {
                t.coverBitmap = bmp
                SonoraWidgetProvider.activeCoverBitmap = bmp
                val file = getArtworkCacheFile(t)
                if (!file.exists() || file.length() == 0L) {
                    try {
                        file.parentFile?.mkdirs()
                        java.io.FileOutputStream(file).use { out ->
                            bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        }
                    } catch (_: Exception) {}
                }
                val curIdx = exoPlayer.currentMediaItemIndex
                if (curIdx == currentTrackIndex && curIdx in tracks.indices) {
                    try {
                        exoPlayer.replaceMediaItem(curIdx, toMediaItem(t))
                    } catch (_: Exception) {}
                }
                if (ThemeEngine.isAdaptive(this)) {
                    if (ThemeEngine.adaptFromBitmap(this, bmp)) {
                        applyAccentLive()
                    }
                }
                SonoraWidgetProvider.updateAllWidgets(this, bmp)
            }
        }

        if (t.coverBitmap != null || !t.coverUrl.isNullOrEmpty() || t.path.isNotBlank() || t.audioUri != null) {
            coverImage.scaleType = ImageView.ScaleType.CENTER_CROP
            coverImage.setPadding(0, 0, 0, 0)
            coverImage.clearColorFilter()
            coverImage.bindCover(t, 384, onCoverBitmapReady)
            ambientGlowImage.bindCover(t, 240)

            val tVideo = SonoraStore.getString(this, "cover_video_${t.key}", "")
            val isGlobalVideo = SonoraStore.getBool(this, "cover_video_global_mode", false)
            val activeVideoCover = if (tVideo.isNotEmpty()) tVideo else (if (isGlobalVideo) SonoraStore.getString(this, "cover_video_active", "") else "")
            if (activeVideoCover.isNotEmpty()) {
                updateStageCoverVideo(activeVideoCover)
            } else {
                updateStageCoverVideo(null)
                if (::stageExpandedCoverImage.isInitialized) {
                    stageExpandedCoverImage.scaleType = ImageView.ScaleType.CENTER_CROP
                    stageExpandedCoverImage.setPadding(0, 0, 0, 0)
                    stageExpandedCoverImage.clearColorFilter()
                    stageExpandedCoverImage.bindCover(t, 640, onCoverBitmapReady)
                }
            }

            if (::portraitMetaCoverThumb.isInitialized) {
                portraitMetaCoverThumb.scaleType = ImageView.ScaleType.CENTER_CROP
                portraitMetaCoverThumb.setPadding(0, 0, 0, 0)
                portraitMetaCoverThumb.clearColorFilter()
                portraitMetaCoverThumb.bindCover(t, 384, onCoverBitmapReady)
            }
            if (::landscapeCoverThumb.isInitialized) {
                landscapeCoverThumb.scaleType = ImageView.ScaleType.CENTER_CROP
                landscapeCoverThumb.setPadding(0, 0, 0, 0)
                landscapeCoverThumb.clearColorFilter()
                landscapeCoverThumb.bindCover(t, 320, onCoverBitmapReady)
            }
        } else {
            coverImage.setImageResource(R.drawable.ic_music)
            val pad = dp(8)
            coverImage.setPadding(pad, pad, pad, pad)
            coverImage.setColorFilter(Color.parseColor(Pal.MUTED))
            ambientGlowImage.setImageDrawable(null)

            if (::stageExpandedCoverImage.isInitialized) {
                stageExpandedCoverImage.setImageResource(R.drawable.ic_music)
                stageExpandedCoverImage.setPadding(dp(28), dp(28), dp(28), dp(28))
                stageExpandedCoverImage.setColorFilter(Color.parseColor(Pal.MUTED))
            }

            if (::portraitMetaCoverThumb.isInitialized) {
                portraitMetaCoverThumb.setImageResource(R.drawable.ic_music)
                portraitMetaCoverThumb.setPadding(dp(12), dp(12), dp(12), dp(12))
                portraitMetaCoverThumb.setColorFilter(Color.parseColor(Pal.MUTED))
            }
            if (::landscapeCoverThumb.isInitialized) {
                landscapeCoverThumb.setImageResource(R.drawable.ic_music)
                landscapeCoverThumb.setPadding(dp(8), dp(8), dp(8), dp(8))
                landscapeCoverThumb.setColorFilter(Color.parseColor(Pal.MUTED))
            }
        }

        // Background adaptive cover & embedded ID3 picture extraction so external SD card MP3s and all tracks adapt live
        val targetKey = t.key
        Thread {
            val resolvedBmp = GifImageSupport.resolveTrackCoverBitmap(this, t)
            runOnUiThread {
                if (tracks.getOrNull(currentTrackIndex)?.key != targetKey) return@runOnUiThread
                if (resolvedBmp != null) {
                    t.coverBitmap = resolvedBmp
                    SonoraWidgetProvider.activeCoverBitmap = resolvedBmp
                    val file = getArtworkCacheFile(t)
                    if (!file.exists() || file.length() == 0L) {
                        try {
                            file.parentFile?.mkdirs()
                            java.io.FileOutputStream(file).use { out ->
                                resolvedBmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                            }
                        } catch (_: Exception) {}
                    }
                    val curIdx = exoPlayer.currentMediaItemIndex
                    if (curIdx == currentTrackIndex && curIdx in tracks.indices) {
                        try {
                            exoPlayer.replaceMediaItem(curIdx, toMediaItem(t))
                        } catch (_: Exception) {}
                    }
                    val isAnimUrl = t.coverUrl?.lowercase()?.let { it.contains(".gif") || it.contains(".webp") } == true
                    if (!isAnimUrl) {
                        coverImage.setImageBitmap(resolvedBmp)
                        if (::portraitMetaCoverThumb.isInitialized) {
                            portraitMetaCoverThumb.setImageBitmap(resolvedBmp)
                        }
                        if (::stageExpandedCoverImage.isInitialized) {
                            stageExpandedCoverImage.setImageBitmap(resolvedBmp)
                        }
                    }
                    if (ThemeEngine.isAdaptive(this)) {
                        if (ThemeEngine.adaptFromBitmap(this, resolvedBmp)) {
                            applyAccentLive()
                        }
                    }
                    SonoraWidgetProvider.updateAllWidgets(this, resolvedBmp)
                } else if (ThemeEngine.isAdaptive(this)) {
                    if (ThemeEngine.adaptFromTrackSeed(this, "${t.title}|${t.artist}|${t.album}")) {
                        applyAccentLive()
                    }
                    SonoraWidgetProvider.updateAllWidgets(this, null)
                }
            }
        }.start()

        miniTitle.text = t.title
        miniArtist.text = "${t.artist} \u2022 ${t.album}"
        miniCover.bind(t)
        miniPlayerRoot.visibility = if (currentTab == 0 || isLandscapeMode) View.GONE else View.VISIBLE

        toggleVideoBtn.visibility = View.VISIBLE
        toggleVideoBtn.setLabel(if (isVideoMode) "Solo Escuchar" else "Ver Video")
        toggleVideoBtn.setActive(isVideoMode)

        speedBtn.setLabel(String.format("%.2gx", currentSpeed))
        speedBtn.setActive(currentSpeed != 1f || currentPitch != 1f)

        updateDurationUi()
        updatePlayState()
        loadLyricsFor(t, force = false)
        if (::libAdapter.isInitialized) libAdapter.notifyDataSetChanged()
        SonoraWidgetProvider.updateAllWidgets(this, SonoraWidgetProvider.activeCoverBitmap)
    }

    private fun showEmptyPlayer() {
        titleText.text = "Selecciona una canción"
        artistText.text = "Explora tu Biblioteca o YouTube en DDMC"
        albumText.text = "Doki Doki Music Club \u2022 Hi-Res"
        if (::landscapeTitleText.isInitialized) landscapeTitleText.text = "Selecciona una canción"
        if (::landscapeArtistText.isInitialized) landscapeArtistText.text = "Doki Doki Music Club"
        formatBadgeText.text = "SIN PISTA"
        providerBadgeText.text = "LOCAL / YOUTUBE"
        if (::bpmKeyBadgeText.isInitialized) bpmKeyBadgeText.text = "DSP 64-BIT"
        currentTimeText.text = "00:00"
        totalTimeText.text = "00:00"
        playerSeekBar.max = 100
        playerSeekBar.progress = 0
        if (::landscapeSeekBar.isInitialized) {
            landscapeSeekBar.max = 100
            landscapeSeekBar.progress = 0
            landscapeCurTimeText.text = "00:00"
            landscapeTotTimeText.text = "00:00"
        }
        coverImage.setImageResource(R.drawable.ic_music)
        coverImage.setPadding(dp(8), dp(8), dp(8), dp(8))
        coverImage.setColorFilter(Color.parseColor(Pal.MUTED))
        ambientGlowImage.setImageDrawable(null)
        if (::portraitMetaCoverThumb.isInitialized) {
            portraitMetaCoverThumb.setImageResource(R.drawable.ic_music)
            portraitMetaCoverThumb.setPadding(dp(12), dp(12), dp(12), dp(12))
            portraitMetaCoverThumb.setColorFilter(Color.parseColor(Pal.MUTED))
        }
        if (stageLyricViews.size >= 7) {
            stageLyricViews.forEach { it.text = "" }
            val c = if (stageLyricViews.size >= 11) 3 else 2
            stageLyricViews[c].text = "Letras en vivo sincronizadas"
            stageLyricViews[c + 1].text = "Toca Biblioteca o YouTube para empezar a escuchar"
        }
        miniPlayerRoot.visibility = View.GONE
        updatePlayState()
    }

    private fun updateDurationUi() {
        val t = tracks.getOrNull(currentTrackIndex)
        val dur = exoPlayer.duration.takeIf { it > 0 } ?: t?.durationMs ?: 0L
        if (dur > 0) {
            playerSeekBar.max = dur.toInt()
            val fmt = formatClock(dur)
            totalTimeText.text = fmt
            if (::landscapeSeekBar.isInitialized) {
                landscapeSeekBar.max = dur.toInt()
                landscapeTotTimeText.text = fmt
            }
        } else {
            playerSeekBar.max = 100
            val fmt = if (t != null && !t.isLocal) "EN VIVO" else "00:00"
            totalTimeText.text = fmt
            if (::landscapeSeekBar.isInitialized) {
                landscapeSeekBar.max = 100
                landscapeTotTimeText.text = fmt
            }
        }
    }

    private fun updatePlayState() {
        val playing = exoPlayer.isPlaying
        visualizerView.isPlaying = playing
        if (::visualizerStripView.isInitialized) visualizerStripView.isPlaying = playing
        if (::dokiSpriteView.isInitialized) dokiSpriteView.isPlayingMusic = playing
        val icon = if (playing) R.drawable.ic_pause_filled else R.drawable.ic_play_filled
        playPauseBtn.setImageResource(icon)
        miniPlayBtn.setImageResource(icon)
        if (::landscapePlayPauseBtn.isInitialized) landscapePlayPauseBtn.setImageResource(icon)
    }

    private fun tickProgress() {
        if (tracks.isEmpty()) return
        val t = tracks.getOrNull(currentTrackIndex) ?: return
        val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
        val dur = exoPlayer.duration.takeIf { it > 0 } ?: t.durationMs

        // Enforce A-B Loop if active
        if (loopStartMs >= 0L && loopEndMs > loopStartMs && pos >= loopEndMs) {
            exoPlayer.seekTo(loopStartMs)
            return
        }

        // Enforce custom trim window
        if (t.trimEndMs > 0 && pos >= t.trimEndMs) {
            if (repeatMode == 2) {
                exoPlayer.seekTo(t.trimStartMs)
            } else {
                playNext()
            }
            return
        }

        // Register play count after 4 seconds of real playback
        if (exoPlayer.isPlaying && pos > 4000L && countedKey != t.key) {
            countedKey = t.key
            SonoraStore.registerPlay(this, t.key)
        }

        if (!isSeeking) {
            val fmtPos = formatClock(pos)
            currentTimeText.text = fmtPos
            if (::landscapeCurTimeText.isInitialized) landscapeCurTimeText.text = fmtPos

            if (dur > 0) {
                val pInt = pos.toInt().coerceIn(0, dur.toInt())
                if (playerSeekBar.max != dur.toInt()) playerSeekBar.max = dur.toInt()
                playerSeekBar.progress = pInt
                val fmtDur = formatClock(dur)
                totalTimeText.text = fmtDur

                if (::landscapeSeekBar.isInitialized) {
                    if (landscapeSeekBar.max != dur.toInt()) landscapeSeekBar.max = dur.toInt()
                    landscapeSeekBar.progress = pInt
                    landscapeTotTimeText.text = fmtDur
                }
            }
        }

        updateLyricsTick(pos)

        val now = System.currentTimeMillis()
        if (exoPlayer.isPlaying && now - lastQueueSave > 10_000L) {
            lastQueueSave = now
            saveQueueState()
            SonoraWidgetProvider.updateAllWidgets(this)
        }
    }

    // ---- Synced Lyrics Engine (Left-Aligned Bold Karaoke Stage + Full Sheet) ----
    private fun loadLyricsFor(track: TrackItem, force: Boolean) {
        if (!force && lyricsKey == track.key) return
        lyricsKey = track.key
        lyricsLines = emptyList()
        lyricsSynced = false
        lastStageActiveLyricIdx = -1

        if (stageLyricViews.size >= 7) {
            stageLyricViews.forEach { it.text = "" }
            val c = if (stageLyricViews.size >= 11) 3 else 2
            stageLyricViews[c].text = "Buscando letra sincronizada..."
            stageLyricViews[c + 1].text = "${track.title} \u2022 ${track.artist}"
        }

        Thread {
            val res = Lyrics.fetch(this, track)
            runOnUiThread {
                if (tracks.getOrNull(currentTrackIndex)?.key != track.key) return@runOnUiThread
                if (res == null || res.lines.isEmpty()) {
                    // Build automatic rhythmic karaoke verses from track metadata so live lyrics stage is always rich
                    val fallbackVerses = listOf(
                        LyricLine(0L, "\u266A ${track.title}"),
                        LyricLine(4000L, track.artist),
                        LyricLine(8000L, track.album.ifBlank { "Estudio Hi-Res" }),
                        LyricLine(12000L, "Modo inmersivo de letras en vivo"),
                        LyricLine(16000L, "Toca aquí para importar o pegar archivo .LRC"),
                        LyricLine(20000L, "Desliza en el escenario para cambiar de pista"),
                        LyricLine(24000L, "Doble toque a los lados para retroceder o adelantar 10s"),
                        LyricLine(28000L, "\u266B ${track.title} \u2014 ${track.artist}")
                    )
                    lyricsLines = fallbackVerses
                    lyricsSynced = true
                    updateLyricsTick(exoPlayer.currentPosition)
                } else {
                    lyricsLines = res.lines
                    lyricsSynced = res.synced
                    updateLyricsTick(exoPlayer.currentPosition)
                }
            }
        }.start()
    }

    private var lastBgColors: IntArray? = null
    private var lastStageColors: IntArray? = null
    private var themeAnimator: ValueAnimator? = null

    private fun updateLyricsTick(posMs: Long) {
        if (lyricsLines.isEmpty()) return

        var active = 0
        if (lyricsSynced) {
            for (i in lyricsLines.indices) {
                if (lyricsLines[i].timeMs <= posMs) active = i else break
            }
        } else {
            val dur = (exoPlayer.duration.takeIf { it > 0 } ?: 180_000L).coerceAtLeast(10_000L)
            active = ((posMs.toFloat() / dur.toFloat()) * lyricsLines.size).toInt().coerceIn(0, lyricsLines.lastIndex)
        }

        // Update all left-aligned bold karaoke slots in the central stage
        if (stageLyricViews.isNotEmpty()) {
            val activeSize = if (isLandscapeMode) 22.5f else 19.5f
            val inactiveSize = if (isLandscapeMode) 15.5f else 14.5f
            val verseChanged = active != lastStageActiveLyricIdx
            val reducedMotion = MotionTokens.isReducedMotion(this)
            val centerSlot = if (stageLyricViews.size >= 11) 3 else 2

            for (slot in stageLyricViews.indices) {
                val lineIdx = active - centerSlot + slot
                val tv = stageLyricViews[slot]
                val text = lyricsLines.getOrNull(lineIdx)?.text?.ifBlank { "\u266A" } ?: ""
                tv.text = text
                tv.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
                val isCurrent = (slot == centerSlot)
                tv.textSize = if (isCurrent) activeSize else inactiveSize
                tv.letterSpacing = if (isCurrent) -0.015f else -0.005f
                // Pure white (#FFFFFF) for active verse, 0.45 opacity for past & upcoming verses
                tv.setTextColor(Color.WHITE)
                tv.pivotX = 0f
                tv.pivotY = tv.height / 2f

                val targetAlpha = if (isCurrent) 1.0f else 0.45f
                val targetScale = if (isCurrent) 1.035f else 1.0f
                if (verseChanged && !reducedMotion && tv.isAttachedToWindow) {
                    if (isCurrent) {
                        tv.alpha = 0.55f
                        tv.scaleX = 0.98f
                        tv.scaleY = 0.98f
                    }
                    tv.animate().cancel()
                    tv.animate()
                        .alpha(targetAlpha)
                        .scaleX(targetScale)
                        .scaleY(targetScale)
                        .setDuration(MotionTokens.DUR_STANDARD)
                        .setInterpolator(MotionTokens.EASE_OUT)
                        .start()
                } else if (!verseChanged && !tv.isAttachedToWindow) {
                    tv.alpha = targetAlpha
                    tv.scaleX = targetScale
                    tv.scaleY = targetScale
                } else if (reducedMotion) {
                    tv.alpha = targetAlpha
                    tv.scaleX = 1.0f
                    tv.scaleY = 1.0f
                }
            }

            if (verseChanged && ::stageLyricsScroll.isInitialized) {
                lastStageActiveLyricIdx = active
                stageLyricsScroll.post {
                    val activeView = stageLyricViews.getOrNull(centerSlot) ?: return@post
                    val targetY = (activeView.top - dp(24)).coerceAtLeast(0)
                    stageLyricsScroll.smoothScrollTo(0, targetY)
                }
            }
        }

        if (lyricsSheetLines.isNotEmpty() && active != lyricsSheetIndex && active in lyricsSheetLines.indices) {
            lyricsSheetIndex = active
            lyricsSheetLines.forEachIndexed { idx, tv ->
                val cur = idx == active
                tv.setTextColor(Color.parseColor(if (cur) Pal.ACCENT else Pal.MUTED))
                tv.textSize = if (cur) 19f else 15.5f
            }
            val targetView = lyricsSheetLines[active]
            lyricsSheetScroll?.post {
                val y = (targetView.top - (lyricsSheetScroll?.height ?: 0) / 3).coerceAtLeast(0)
                lyricsSheetScroll?.smoothScrollTo(0, y)
            }
        }
    }

    private fun toggleCurrentTrackFavorite() {
        val t = tracks.getOrNull(currentTrackIndex) ?: run { toastNoTrack(); return }
        t.isFavorite = SonoraStore.toggleFavorite(this, t.key)
        localTracksList.find { it.key == t.key }?.isFavorite = t.isFavorite
        val res = if (t.isFavorite) R.drawable.ic_heart_filled else R.drawable.ic_heart
        val col = Color.parseColor(if (t.isFavorite) Pal.ACCENT else Pal.SUB)
        favBtn.setImageResource(res)
        favBtn.setColorFilter(col)
        if (::landscapeFavBtn.isInitialized) {
            landscapeFavBtn.setImageResource(res)
            landscapeFavBtn.setColorFilter(col)
        }
        refreshLibrary()
        SonoraWidgetProvider.updateAllWidgets(this)
    }

    private fun setShuffle(enabled: Boolean) {
        isShuffle = enabled
        exoPlayer.shuffleModeEnabled = enabled
        SonoraStore.putBool(this, "shuffle", enabled)
        applyShuffleRepeatUi()
    }

    private fun cycleRepeat() {
        repeatMode = (repeatMode + 1) % 3
        exoPlayer.repeatMode = when (repeatMode) {
            1 -> Player.REPEAT_MODE_ALL
            2 -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        SonoraStore.putInt(this, "repeat", repeatMode)
        applyShuffleRepeatUi()
    }

    private fun applyShuffleRepeatUi() {
        val shufCol = Color.parseColor(if (isShuffle) Pal.ACCENT else Pal.MUTED)
        val repRes = if (repeatMode == 2) R.drawable.ic_repeat_one else R.drawable.ic_repeat
        val repCol = Color.parseColor(if (repeatMode != 0) Pal.ACCENT else Pal.MUTED)
        if (::shuffleBtn.isInitialized) shuffleBtn.setColorFilter(shufCol)
        if (::repeatBtn.isInitialized) {
            repeatBtn.setImageResource(repRes)
            repeatBtn.setColorFilter(repCol)
        }
        if (::landscapeShuffleBtn.isInitialized) landscapeShuffleBtn.setColorFilter(shufCol)
        if (::landscapeRepeatBtn.isInitialized) {
            landscapeRepeatBtn.setImageResource(repRes)
            landscapeRepeatBtn.setColorFilter(repCol)
        }
    }

    private fun updateMode() {
        if (::youtubeStageWebView.isInitialized) {
            youtubeStageWebView.visibility = View.GONE
        }
        playerView.visibility = if (isVideoMode) View.VISIBLE else View.GONE
        coverImage.visibility = if (isVideoMode) View.GONE else View.VISIBLE
        visualizerView.visibility = if (isVideoMode) View.GONE else View.VISIBLE
        toggleVideoBtn.setLabel(if (isVideoMode) "Solo Escuchar" else "Ver Video")
        toggleVideoBtn.setActive(isVideoMode)
        val pos = exoPlayer.currentPosition
        val wasPlaying = exoPlayer.isPlaying
        loadedSig = 0
        if (tracks.isNotEmpty()) playList(tracks.toList(), currentTrackIndex, autoPlay = wasPlaying, startPosMs = pos)
        updateStageLayout()
    }

    private fun togglePlayPause() {
        if (tracks.isEmpty()) {
            if (localTracksList.isNotEmpty()) {
                playList(localTracksList, 0, autoPlay = true)
            } else {
                openSystemFilePicker()
            }
            return
        }
        if (exoPlayer.playbackState == Player.STATE_IDLE) {
            exoPlayer.prepare()
        }
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
        updatePlayState()
    }

    private fun playNext() {
        if (tracks.isEmpty()) return
        if (exoPlayer.hasNextMediaItem()) {
            exoPlayer.seekToNextMediaItem()
        } else {
            val nextIdx = (currentTrackIndex + 1) % tracks.size
            exoPlayer.seekTo(nextIdx, tracks[nextIdx].trimStartMs)
        }
        exoPlayer.prepare()
        exoPlayer.play()
    }

    private fun playPrevious() {
        if (tracks.isEmpty()) return
        val cur = tracks.getOrNull(currentTrackIndex)
        val minStart = cur?.trimStartMs ?: 0L
        if (exoPlayer.currentPosition > minStart + 3500L) {
            exoPlayer.seekTo(minStart)
            return
        }
        if (exoPlayer.hasPreviousMediaItem()) {
            exoPlayer.seekToPreviousMediaItem()
        } else {
            val prevIdx = if (currentTrackIndex - 1 < 0) tracks.lastIndex else currentTrackIndex - 1
            exoPlayer.seekTo(prevIdx, tracks[prevIdx].trimStartMs)
        }
        exoPlayer.prepare()
        exoPlayer.play()
    }

    private fun saveQueueState() {
        if (tracks.isEmpty()) return
        val refs = tracks.map { TrackRef.of(it) }
        SonoraStore.saveQueue(this, refs, currentTrackIndex, exoPlayer.currentPosition.coerceAtLeast(0L))
    }

    private fun restoreQueueIfNeeded() {
        // Case 1: Service already has an active queue in memory
        if (exoPlayer.mediaItemCount > 0 && tracks.isEmpty()) {
            val restored = ArrayList<TrackItem>()
            for (i in 0 until exoPlayer.mediaItemCount) {
                val item = exoPlayer.getMediaItemAt(i)
                val key = item.mediaId
                val match = libraryByKey[key] ?: resolveRef(
                    TrackRef(
                        key = key,
                        title = item.mediaMetadata.title?.toString() ?: "Pista",
                        artist = item.mediaMetadata.artist?.toString() ?: "Artista",
                        album = item.mediaMetadata.albumTitle?.toString() ?: "Álbum"
                    )
                )
                restored.add(match)
            }
            tracks.clear()
            tracks.addAll(restored)
            currentTrackIndex = exoPlayer.currentMediaItemIndex.coerceIn(0, tracks.lastIndex)
            loadedSig = tracks.map { it.key }.hashCode() * 31 + if (isVideoMode) 1 else 0
            bindNowPlaying()
            return
        }

        // Case 2: Restore saved queue from SonoraStore
        val savedRefs = SonoraStore.loadQueue(this)
        if (savedRefs.isNotEmpty()) {
            val resolved = savedRefs.mapNotNull { ref ->
                libraryByKey[ref.key] ?: if (ref.key.startsWith("http")) resolveRef(ref) else null
            }
            if (resolved.isNotEmpty()) {
                val idx = SonoraStore.queueIndex(this).coerceIn(0, resolved.lastIndex)
                val pos = SonoraStore.queuePos(this).coerceAtLeast(0L)
                playList(resolved, idx, autoPlay = false, startPosMs = pos)
                return
            }
        }

        // Case 3: Populate with local library without auto-playing
        if (localTracksList.isNotEmpty()) {
            playList(localTracksList, 0, autoPlay = false)
        } else {
            showEmptyPlayer()
        }
    }

    // ==========================================================
    // SYSTEM FILE PICKER & METADATA EXTRACTOR
    // ==========================================================
    private fun openSystemFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/*", "video/mp4", "video/webm"))
        }
        filePickerLauncher.launch(intent)
    }

    private fun loadLocalAudioFromUri(uri: Uri) {
        val existing = localTracksList.find { it.audioUri == uri }
        if (existing != null) {
            val idx = localTracksList.indexOf(existing)
            playList(localTracksList, idx, autoPlay = true)
            switchTab(0)
            return
        }

        var fileName = getFileNameFromUri(uri)
        var title = fileName.substringBeforeLast('.')
        var artist = "Archivo local"
        var album = "Memoria interna"
        var durationMs = 0L
        var coverBmp: Bitmap? = null
        val isVideo = fileName.endsWith(".mp4", true) || fileName.endsWith(".webm", true)

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(this, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }?.let { title = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }?.let { artist = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() }?.let { album = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { durationMs = it }
            retriever.embeddedPicture?.let { bytes ->
                coverBmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (_: Exception) {
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }

        val ext = fileName.substringAfterLast('.', "AUDIO").uppercase()
        val newTrack = SonoraStore.hydrate(
            this,
            TrackItem(
                title = title,
                artist = artist,
                album = album,
                coverBitmap = coverBmp,
                videoUrl = if (isVideo) uri.toString() else "",
                audioUri = uri,
                quality = "$ext \u2022 Local",
                provider = "Archivo directo",
                durationMs = durationMs,
                isLocal = true
            )
        )
        localTracksList.add(0, newTrack)
        libraryByKey[newTrack.key] = newTrack
        refreshLibrary()
        playList(localTracksList, 0, autoPlay = true)
        switchTab(0)
    }

    private fun getFileNameFromUri(uri: Uri): String {
        var name = "pista_local.mp3"
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) {
                    name = cursor.getString(idx) ?: name
                }
            }
        } catch (_: Exception) {}
        return name
    }

    private fun getActiveDokiDrawable(): Int {
        val doki = DOKI_AVATARS.find { it.id == selectedDokiId } ?: DOKI_AVATARS[0]
        return doki.resFor(selectedDokiPose)
    }

    private fun getActiveDokiColor(): Int {
        val doki = DOKI_AVATARS.find { it.id == selectedDokiId } ?: DOKI_AVATARS[0]
        return Color.parseColor(doki.colorHex)
    }

    /** Re-tints the entire screen & central stage with the reactive cover palette + smooth 280ms gradient transition. */
    private fun applyAccentLive() {
        val accInt = try { Color.parseColor(Pal.ACCENT) } catch (_: Exception) { Color.parseColor("#38BDF8") }
        val onAccInt = Pal.contrastOn(accInt)
        val accentList = ColorStateList.valueOf(accInt)
        val subCol = try { Color.parseColor(Pal.SUB) } catch (_: Exception) { Color.parseColor("#D0DCF0") }
        val mutedCol = try { Color.parseColor(Pal.MUTED) } catch (_: Exception) { Color.parseColor("#98A8C4") }

        val targetBg = intArrayOf(
            Color.parseColor(Pal.BG_TOP),
            Color.parseColor(Pal.BG_MID),
            Color.parseColor(Pal.BG_BOTTOM)
        )
        val targetStage = intArrayOf(
            Color.parseColor(Pal.STAGE_TOP),
            Color.parseColor(Pal.STAGE_MID),
            Color.parseColor(Pal.STAGE_BOTTOM)
        )

        val prevBg = lastBgColors
        val prevStage = lastStageColors
        lastBgColors = targetBg
        lastStageColors = targetStage

        themeAnimator?.cancel()
        if (prevBg != null && prevStage != null && !MotionTokens.isReducedMotion(this)) {
            val evaluator = android.animation.ArgbEvaluator()
            themeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = MotionTokens.DUR_THEME
                interpolator = MotionTokens.EASE_OUT
                addUpdateListener { anim ->
                    val f = anim.animatedValue as Float
                    if (::mainRootLayout.isInitialized) {
                        val c0 = evaluator.evaluate(f, prevBg[0], targetBg[0]) as Int
                        val c1 = evaluator.evaluate(f, prevBg[1], targetBg[1]) as Int
                        val c2 = evaluator.evaluate(f, prevBg[2], targetBg[2]) as Int
                        mainRootLayout.background = GradientDrawable(
                            GradientDrawable.Orientation.TOP_BOTTOM,
                            intArrayOf(c0, c1, c2)
                        )
                    }
                    if (::unifiedStageBlock.isInitialized) {
                        unifiedStageBlock.background = null
                    }
                }
                start()
            }
        } else {
            if (::mainRootLayout.isInitialized) {
                mainRootLayout.background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    targetBg
                )
            }
            if (::unifiedStageBlock.isInitialized) {
                unifiedStageBlock.background = null
            }
        }

        if (::topHeader.isInitialized) {
            topHeader.setBackgroundColor(targetBg[0] and 0x00FFFFFF or 0xCC000000.toInt())
        }
        if (::bottomNavBar.isInitialized) {
            bottomNavBar.setBackgroundColor(targetBg[2] and 0x00FFFFFF or 0xEE000000.toInt())
        }

        // 3. Landscape 15% compact bottom bar tinted with darkest tone of the cover
        if (::landscapeBottomBar.isInitialized) {
            landscapeBottomBar.background = GradientDrawable().apply {
                setColor(Color.parseColor(Pal.GRAD_DARKEST))
            }
        }

        // 4. Hue-tinted secondary text & SeekBars, Play/Pause buttons, badges & active chips
        if (::artistText.isInitialized) artistText.setTextColor(subCol)
        if (::albumText.isInitialized) albumText.setTextColor(mutedCol)
        if (::landscapeArtistText.isInitialized) landscapeArtistText.setTextColor(subCol)
        if (::currentTimeText.isInitialized) currentTimeText.setTextColor(subCol)
        if (::totalTimeText.isInitialized) totalTimeText.setTextColor(subCol)
        if (::landscapeCurTimeText.isInitialized) landscapeCurTimeText.setTextColor(subCol)
        if (::landscapeTotTimeText.isInitialized) landscapeTotTimeText.setTextColor(subCol)

        if (::playerSeekBar.isInitialized) {
            playerSeekBar.progressTintList = accentList
            playerSeekBar.thumbTintList = accentList
        }
        if (::landscapeSeekBar.isInitialized) {
            landscapeSeekBar.progressTintList = accentList
            landscapeSeekBar.thumbTintList = accentList
        }
        val t = tracks.getOrNull(currentTrackIndex)
        val favCol = if (t?.isFavorite == true) accInt else subCol
        if (::favBtn.isInitialized) favBtn.setColorFilter(favCol)
        if (::landscapeFavBtn.isInitialized) landscapeFavBtn.setColorFilter(favCol)
        if (::formatBadgeText.isInitialized) formatBadgeText.setTextColor(accInt)
        if (::playPauseBtn.isInitialized) {
            (playPauseBtn.background as? GradientDrawable)?.setColor(accInt)
            playPauseBtn.setColorFilter(onAccInt)
        }
        if (::landscapePlayPauseBtn.isInitialized) {
            (landscapePlayPauseBtn.background as? GradientDrawable)?.setColor(accInt)
            landscapePlayPauseBtn.setColorFilter(onAccInt)
        }
        if (::visualizerView.isInitialized) visualizerView.invalidate()
        if (::visualizerStripView.isInitialized) visualizerStripView.invalidate()

        applyShuffleRepeatUi()
        updateStageLayout()
        for (i in navIcons.indices) {
            val on = i == currentTab
            navIcons[i].setColorFilter(if (on) accInt else mutedCol)
            navLabels[i].setTextColor(if (on) accInt else mutedCol)
            navPlates[i].background = if (on) GradientDrawable().apply {
                setColor(accInt and 0x00FFFFFF or 0x2A000000)
                cornerRadius = dpf(15f)
            } else null
        }
        SonoraWidgetProvider.updateAllWidgets(this, SonoraWidgetProvider.activeCoverBitmap)
    }

    private fun cycleDokiExpression() {
        val exprs = DdscEngine.EXPRESSIONS.map { it.first }
        val nextIdx = (exprs.indexOf(selectedDokiExpr) + 1) % exprs.size
        selectedDokiExpr = exprs[nextIdx]
        getSharedPreferences("sonora_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("doki_expr", selectedDokiExpr)
            .apply()
        updateDokiViews()
    }

    private fun updateDokiViews() {
        val resId = getActiveDokiDrawable()
        val borderColor = getActiveDokiColor()
        val doki = DOKI_AVATARS.find { it.id == selectedDokiId } ?: DOKI_AVATARS[0]
        val density = resources.displayMetrics.density

        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#181824"))
            setStroke((2 * density).toInt(), borderColor)
        }

        if (::topDokiAvatar.isInitialized) {
            val faceBmp = DdscEngine.loadFaceAvatarBitmap(this, selectedDokiId)
            if (faceBmp != null) {
                topDokiAvatar.setImageBitmap(faceBmp)
            } else {
                topDokiAvatar.setImageResource(resId)
            }
            topDokiAvatar.background = bg
        }

        val prefs = getSharedPreferences("sonora_prefs", Context.MODE_PRIVATE)
        val dokiScalePref = prefs.getFloat("doki_scale", 1.0f)
        val dokiVoiceMuted = prefs.getBoolean("doki_voice_muted", false)

        if (::dokiSpriteView.isInitialized) {
            dokiSpriteView.dokiScale = dokiScalePref
            dokiSpriteView.isVoiceMuted = dokiVoiceMuted

            val customSprite = prefs.getString("doki_custom_asset", "") ?: ""
            if (customSprite.isNotBlank()) {
                dokiSpriteView.setCustomAsset(customSprite)
            } else {
                dokiSpriteView.setDoki(selectedDokiId, selectedDokiPose, selectedDokiExpr)
            }

            if (::shimejiFullScreenOverlay.isInitialized && ::dokiLeftCol.isInitialized) {
                val isChibi = dokiSpriteView.isChibiMode
                if (isChibi) {
                    if (dokiSpriteView.parent != shimejiFullScreenOverlay) {
                        (dokiSpriteView.parent as? ViewGroup)?.removeView(dokiSpriteView)
                        shimejiFullScreenOverlay.addView(dokiSpriteView, FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        ))
                    }
                    shimejiFullScreenOverlay.visibility = if (showStageDoki) View.VISIBLE else View.GONE
                } else {
                    if (dokiSpriteView.parent != dokiLeftCol) {
                        (dokiSpriteView.parent as? ViewGroup)?.removeView(dokiSpriteView)
                        dokiLeftCol.addView(dokiSpriteView, FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                        ))
                    }
                    shimejiFullScreenOverlay.visibility = View.GONE
                }
            }
        }

        if (::dokiFloatingBadge.isInitialized) {
            val exprLabel = DdscEngine.EXPRESSIONS.find { it.first == selectedDokiExpr }?.second ?: "Normal"
            dokiFloatingBadge.text = "${doki.name} \u2022 $exprLabel \u25BE"
        }
        updateStageLayout()
        SonoraWidgetProvider.updateAllWidgets(this, SonoraWidgetProvider.activeCoverBitmap)
    }

    private fun showDokiAvatarPicker() {
        showSheet(fullHeight = true) { root, dialog ->
            val acc = try { Color.parseColor(Pal.ACCENT) } catch (_: Exception) { Color.parseColor("#38BDF8") }
            val onAcc = Pal.contrastOn(acc)

            root.addView(
                sheetTitle(
                    "Compañera Chibi Shimeji (PinkChan + DDSC)",
                    "Sprites auto-recortados sin bordes vacíos + físicas interactivas Shimeji (caminar, saltar, escalar, hablar con voz y acariciar)"
                )
            )

            // 1. Character Selector Row (Natsuki, Monika, Sayori, Yuri, Bocchi, Konata)
            val charScroll = HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                setPadding(0, 0, 0, dp(8))
            }
            val charRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            for (doki in DOKI_AVATARS) {
                val isSel = doki.id == selectedDokiId
                val chip = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(dp(74), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        marginEnd = dp(6)
                    }
                    background = GradientDrawable().apply {
                        cornerRadius = dpf(14f)
                        if (isSel) {
                            setColor(Color.parseColor(doki.colorHex) and 0x00FFFFFF or 0x33000000)
                            setStroke(dp(2), Color.parseColor(doki.colorHex))
                        } else {
                            setColor(Color.parseColor("#161924"))
                            setStroke(dp(1), Color.parseColor("#26FFFFFF"))
                        }
                    }
                    setPadding(dp(6), dp(8), dp(6), dp(8))
                    isClickable = true
                    setOnClickListener {
                        selectedDokiId = doki.id
                        getSharedPreferences("sonora_prefs", Context.MODE_PRIVATE).edit()
                            .putString("doki_id", selectedDokiId)
                            .putString("doki_custom_asset", "")
                            .apply()
                        updateDokiViews()
                        dialog.dismiss()
                        showDokiAvatarPicker()
                    }
                }
                chip.addView(ImageView(this).apply {
                    val bmp = DdscEngine.loadFaceAvatarBitmap(this@MainActivity, doki.id)
                    if (bmp != null) setImageBitmap(bmp) else setImageResource(doki.resFor(selectedDokiPose))
                    layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
                    scaleType = ImageView.ScaleType.CENTER_CROP
                })
                chip.addView(TextView(this).apply {
                    text = doki.name
                    textSize = 11f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    setPadding(0, dp(4), 0, 0)
                })
                charRow.addView(chip)
            }
            charScroll.addView(charRow)
            root.addView(charScroll)

            // 1.5 PinkChan Shimeji Interactive Behaviors Trigger Row
            root.addView(TextView(this).apply {
                text = "COMPORTAMIENTOS INTERACTIVOS PINKCHAN SHIMEJI"
                textSize = 10.5f
                letterSpacing = 0.05f
                setTypeface(null, Typeface.BOLD)
                setTextColor(acc)
                setPadding(0, dp(2), 0, dp(6))
            })
            val behavScroll = HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                setPadding(0, 0, 0, dp(10))
            }
            val behavRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val behavActions = listOf(
                "Saltar Feliz" to { if (::dokiSpriteView.isInitialized) dokiSpriteView.triggerBehavior("jump") },
                "Acción Especial" to { if (::dokiSpriteView.isInitialized) dokiSpriteView.triggerBehavior("custom") },
                "Hablar y Voz" to { if (::dokiSpriteView.isInitialized) dokiSpriteView.triggerBehavior("speak") },
                "Trepar Pared" to { if (::dokiSpriteView.isInitialized) dokiSpriteView.triggerBehavior("climb") },
                "Sentarse" to { if (::dokiSpriteView.isInitialized) dokiSpriteView.triggerBehavior("sit") }
            )
            for ((bLabel, bAction) in behavActions) {
                behavRow.addView(Pill(this, bLabel, 0, 32).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { marginEnd = dp(6) }
                    setOnClickListener {
                        bAction()
                        Toast.makeText(this@MainActivity, "Comportamiento activado: $bLabel", Toast.LENGTH_SHORT).show()
                    }
                })
            }
            behavScroll.addView(behavRow)
            root.addView(behavScroll)

            // 1.7 Doki Customization (Scale / Size & Voice Mute)
            root.addView(TextView(this).apply {
                text = "TAMAÑO DEL PERSONAJE / SHIMEJI"
                textSize = 10.5f
                letterSpacing = 0.05f
                setTypeface(null, Typeface.BOLD)
                setTextColor(acc)
                setPadding(0, dp(4), 0, dp(6))
            })

            val currentScale = getSharedPreferences("sonora_prefs", Context.MODE_PRIVATE).getFloat("doki_scale", 1.0f)
            val scaleScroll = HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                setPadding(0, 0, 0, dp(10))
            }
            val scaleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val scaleOptions = listOf(
                0.75f to "0.75x (Compacto)",
                1.0f to "1.0x (Normal)",
                1.3f to "1.3x (Grande)",
                1.7f to "1.7x (Muy grande)",
                2.2f to "2.2x (Gigante)"
            )
            for ((scaleVal, scaleLabel) in scaleOptions) {
                val isSel = kotlin.math.abs(currentScale - scaleVal) < 0.1f
                scaleRow.addView(TextView(this).apply {
                    text = scaleLabel
                    textSize = 11f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(if (isSel) onAcc else Color.parseColor("#CBD5E1"))
                    background = GradientDrawable().apply {
                        cornerRadius = dpf(10f)
                        setColor(if (isSel) acc else Color.parseColor("#161926"))
                        setStroke(dp(1), Color.parseColor("#2AFFFFFF"))
                    }
                    setPadding(dp(10), dp(6), dp(10), dp(6))
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { marginEnd = dp(6) }
                    isClickable = true
                    setOnClickListener {
                        getSharedPreferences("sonora_prefs", Context.MODE_PRIVATE).edit()
                            .putFloat("doki_scale", scaleVal)
                            .apply()
                        updateDokiViews()
                        Toast.makeText(this@MainActivity, "Tamaño: $scaleLabel", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        showDokiAvatarPicker()
                    }
                })
            }
            scaleScroll.addView(scaleRow)
            root.addView(scaleScroll)

            // Voice & Dialogs Toggle Row
            val isVoiceMutedPref = getSharedPreferences("sonora_prefs", Context.MODE_PRIVATE).getBoolean("doki_voice_muted", false)
            val voiceRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, dp(10))
            }
            voiceRow.addView(TextView(this).apply {
                text = if (isVoiceMutedPref) "Voz de Doki: Silenciada (Toca para activar)" else "Voz de Doki: Activa con diálogos (Toca para silenciar)"
                textSize = 11.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (isVoiceMutedPref) Color.parseColor("#F87171") else acc)
                background = GradientDrawable().apply {
                    cornerRadius = dpf(10f)
                    setColor(Color.parseColor("#181C2A"))
                    setStroke(dp(1), if (isVoiceMutedPref) Color.parseColor("#44F87171") else Color.parseColor("#3338BDF8"))
                }
                setPadding(dp(12), dp(8), dp(12), dp(8))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                isClickable = true
                setOnClickListener {
                    val nextMuted = !isVoiceMutedPref
                    getSharedPreferences("sonora_prefs", Context.MODE_PRIVATE).edit()
                        .putBoolean("doki_voice_muted", nextMuted)
                        .apply()
                    updateDokiViews()
                    Toast.makeText(this@MainActivity, if (nextMuted) "Voz silenciada" else "Voz activada", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    showDokiAvatarPicker()
                }
            })
            root.addView(voiceRow)

            // Info note
            root.addView(TextView(this).apply {
                text = "Shimejis (Bocchi, Konata, Chibi) caminan por toda la pantalla. Sprites WebP se muestran en el recuadro del escenario."
                textSize = 10f
                setTextColor(Color.parseColor("#94A3B8"))
                setPadding(dp(2), 0, dp(2), dp(10))
            })

            // 2. Outfit / Style Selector (Casual / Uniforme Escolar)
            val outfitRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, dp(8))
            }
            val outfits = listOf("casual" to "Ropa Casual / Chibi", "school" to "Uniforme Escolar")
            for ((k, label) in outfits) {
                val on = (selectedDokiPose == k) || (k == "school" && selectedDokiPose == "normal")
                outfitRow.addView(TextView(this).apply {
                    text = label
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(if (on) onAcc else Color.WHITE)
                    background = GradientDrawable().apply {
                        cornerRadius = dpf(12f)
                        setColor(if (on) acc else Color.parseColor("#181C2A"))
                    }
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginEnd = dp(6)
                    }
                    isClickable = true
                    setOnClickListener {
                        selectedDokiPose = k
                        getSharedPreferences("sonora_prefs", Context.MODE_PRIVATE).edit()
                            .putString("doki_pose", k)
                            .putString("doki_custom_asset", "")
                            .apply()
                        updateDokiViews()
                        dialog.dismiss()
                        showDokiAvatarPicker()
                    }
                })
            }
            root.addView(outfitRow)

            // 3. Expression Chips (Natural, Feliz, Pensativa, Sorprendida, Puchero)
            val exprScroll = HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                setPadding(0, 0, 0, dp(10))
            }
            val exprRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for ((exprKey, exprLabel) in DdscEngine.EXPRESSIONS) {
                val on = selectedDokiExpr == exprKey
                exprRow.addView(TextView(this).apply {
                    text = exprLabel
                    textSize = 11f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(if (on) onAcc else Color.parseColor("#CBD5E1"))
                    background = GradientDrawable().apply {
                        cornerRadius = dpf(10f)
                        setColor(if (on) acc else Color.parseColor("#161926"))
                        setStroke(dp(1), Color.parseColor("#2AFFFFFF"))
                    }
                    setPadding(dp(10), dp(6), dp(10), dp(6))
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { marginEnd = dp(6) }
                    isClickable = true
                    setOnClickListener {
                        selectedDokiExpr = exprKey
                        getSharedPreferences("sonora_prefs", Context.MODE_PRIVATE).edit()
                            .putString("doki_expr", exprKey)
                            .putString("doki_custom_asset", "")
                            .apply()
                        updateDokiViews()
                        Toast.makeText(this@MainActivity, "Expresión: $exprLabel", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                })
            }
            exprScroll.addView(exprRow)
            root.addView(exprScroll)

            // 4. Full Gallery of DDSC + PinkChan Chibi Sprites for the active Doki
            val allSprites = DdscEngine.spritesForCharacter(this, selectedDokiId)
            root.addView(TextView(this).apply {
                text = "GALERÍA DE SPRITES AUTO-RECORTADOS (${allSprites.size} SPRITES DE ${selectedDokiId.uppercase()})"
                textSize = 10.5f
                letterSpacing = 0.05f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#94A3B8"))
                setPadding(0, dp(4), 0, dp(6))
            })

            val gridContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
            val chunked = allSprites.chunked(3)
            for (rowSprites in chunked) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(8) }
                }
                for (assetRel in rowSprites) {
                    val card = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER_HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                            marginEnd = dp(6)
                        }
                        background = GradientDrawable().apply {
                            setColor(Color.parseColor("#141824"))
                            cornerRadius = dpf(12f)
                            setStroke(dp(1), Color.parseColor("#24FFFFFF"))
                        }
                        setPadding(dp(6), dp(6), dp(6), dp(6))
                        isClickable = true
                        setOnClickListener {
                            getSharedPreferences("sonora_prefs", Context.MODE_PRIVATE).edit()
                                .putString("doki_custom_asset", assetRel)
                                .apply()
                            updateDokiViews()
                            Toast.makeText(this@MainActivity, "Sprite aplicado: ${assetRel.substringAfterLast('/')}", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                        }
                    }
                    val previewImg = ImageView(this).apply {
                        layoutParams = LinearLayout.LayoutParams(dp(76), dp(86))
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        setImageBitmap(DdscEngine.loadSprite(this@MainActivity, assetRel))
                    }
                    val labelTv = TextView(this).apply {
                        text = assetRel.substringAfterLast('/').substringBeforeLast('.')
                        textSize = 9.5f
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        setTextColor(Color.parseColor("#CBD5E1"))
                        setPadding(0, dp(3), 0, 0)
                    }
                    card.addView(previewImg)
                    card.addView(labelTv)
                    row.addView(card)
                }
                gridContainer.addView(row)
            }
            root.addView(gridContainer)
        }
    }

}


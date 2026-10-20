package com.sonora.music

import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Typeface
import android.graphics.drawable.Animatable
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.media.MediaPlayer
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import coil.Coil
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.dispose
import coil.load
import kotlin.math.abs

/**
 * Shared motion tokens following `emil-design-eng`, `apple-design`, `animate`, and `review-animations`:
 * - Custom cubic-bezier curves instead of weak built-in linear/default curves
 * - Fast, asymmetric press/release timings (100ms down, 160ms up, under 300ms UI rule)
 * - Apple UIScrollView rubber-band formula for boundary overscroll
 * - System Reduced Motion accessibility check (`ANIMATOR_DURATION_SCALE`)
 */
object MotionTokens {
    /** `--ease-out`: `cubic-bezier(0.23, 1, 0.32, 1)` for responsive enter/exit & press interactions. */
    val EASE_OUT = PathInterpolator(0.23f, 1f, 0.32f, 1f)

    /** `--ease-in-out`: `cubic-bezier(0.77, 0, 0.175, 1)` for on-screen morphing & layout transitions. */
    val EASE_IN_OUT = PathInterpolator(0.77f, 0f, 0.175f, 1f)

    /** `--ease-drawer`: `cubic-bezier(0.32, 0.72, 0, 1)` (iOS / Vaul sheet curve) for bottom sheets. */
    val EASE_DRAWER = PathInterpolator(0.32f, 0.72f, 0f, 1f)

    const val DUR_PRESS_DOWN = 100L
    const val DUR_PRESS_UP = 160L
    const val DUR_FAST = 180L
    const val DUR_STANDARD = 220L
    const val DUR_THEME = 280L
    const val DUR_SHEET = 260L

    /** Checks if the user has enabled Reduced Motion / disabled animations at the OS level. */
    fun isReducedMotion(context: Context? = null): Boolean {
        if (!ValueAnimator.areAnimatorsEnabled()) return true
        if (context != null) {
            return try {
                Settings.Global.getFloat(
                    context.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f
                ) == 0f
            } catch (_: Exception) {
                false
            }
        }
        return false
    }

    /** Apple's UIScrollView rubber-band damping formula (`coeff = 0.55f`) for dragging past limits. */
    fun rubberband(offset: Float, dimension: Float, coeff: Float = 0.55f): Float {
        if (dimension <= 0f) return 0f
        val sign = if (offset < 0f) -1f else 1f
        val x = abs(offset)
        val damped = (1.0f - (1.0f / ((x * coeff / dimension) + 1.0f))) * dimension
        return damped * sign
    }
}

/**
 * Runtime palette. Surface, gradient, hue-tinted secondary text, and accent values are dynamically
 * rewritten by [ThemeEngine] from the active track's album cover (or first GIF frame).
 */
object Pal {
    var BG = "#0E131F"
    var BG_TOP = "#1E2942"
    var BG_MID = "#131B2E"
    var BG_BOTTOM = "#090D16"

    var STAGE_TOP = "#2B3B5E"
    var STAGE_MID = "#1B2640"
    var STAGE_BOTTOM = "#0F1526"
    var GRAD_DARKEST = "#0A0E1A"

    var SURFACE = "#151C2C"
    var SURFACE_HI = "#1F293D"
    const val TEXT = "#FFFFFF"
    var SUB = "#D0DCF0"
    var MUTED = "#98A8C4"
    var ACCENT = "#38BDF8"
    var CYAN = "#818CF8"
    var ON_ACCENT = "#080C14"
    const val DANGER = "#F87171"

    /** Returns high-contrast foreground color (#080C14 or #FFFFFF) for any background color. */
    fun contrastOn(bgColor: Int): Int {
        val r = Color.red(bgColor) / 255.0
        val g = Color.green(bgColor) / 255.0
        val b = Color.blue(bgColor) / 255.0
        val lum = 0.2126 * r + 0.7152 * g + 0.0722 * b
        return if (lum > 0.50) Color.parseColor("#080C14") else Color.WHITE
    }
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

fun formatClock(ms: Long): String {
    val total = (ms.coerceAtLeast(0)) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}

/** Ensures Coil is configured with native animated GIF decoding (ImageDecoderDecoder / GifDecoder) and software bitmaps for pixel palette extraction. */
object GifImageSupport {
    private var initialized = false

    fun ensure(context: Context) {
        if (initialized) return
        initialized = true
        val loader = ImageLoader.Builder(context.applicationContext)
            .allowHardware(false)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .crossfade(false)
            .build()
        Coil.setImageLoader(loader)
    }

    fun ensureSoftwareBitmap(bmp: Bitmap?): Bitmap? {
        if (bmp == null) return null
        return try {
            if (Build.VERSION.SDK_INT >= 26 && bmp.config == Bitmap.Config.HARDWARE) {
                bmp.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                bmp
            }
        } catch (_: Exception) {
            bmp
        }
    }

    /** Extracts the first frame (or current frame) of any Drawable (BitmapDrawable, AnimatedImageDrawable, GifDrawable) as a software Bitmap. */
    fun extractFirstFrame(drawable: Drawable?): Bitmap? {
        if (drawable == null) return null
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return ensureSoftwareBitmap(drawable.bitmap)
        }
        return try {
            val w = drawable.intrinsicWidth.coerceIn(32, 160)
            val h = drawable.intrinsicHeight.coerceIn(32, 160)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val oldBounds = android.graphics.Rect(drawable.bounds)
            drawable.setBounds(0, 0, w, h)
            drawable.draw(canvas)
            if (!oldBounds.isEmpty) drawable.bounds = oldBounds
            bmp
        } catch (_: Exception) {
            null
        }
    }

    val SUPPORTED_IMAGE_MIMES = arrayOf(
        "image/*",
        "image/gif",
        "image/webp",
        "image/png",
        "image/jpeg",
        "image/jpg",
        "image/heic",
        "image/heif",
        "image/avif",
        "image/bmp",
        "image/x-ms-bmp",
        "image/x-icon",
        "image/vnd.microsoft.icon",
        "image/tiff",
        "image/svg+xml",
        "image/x-adobe-dng",
        "video/mp4",
        "video/webm",
        "video/x-matroska"
    )

    /**
     * Normalizes any bitmap of ANY dimension or aspect ratio (e.g. 624x624, 87594275x1, 1x9999999, 16000x12000)
     * into a safe hardware-renderable software Bitmap while showing 100% of the image without any cropping.
     */
    fun normalizeAnySizeBitmap(raw: Bitmap?, maxSidePx: Int = 1024, preserveFullImage: Boolean = true): Bitmap? {
        val src = ensureSoftwareBitmap(raw) ?: return null
        val w = src.width.coerceAtLeast(1)
        val h = src.height.coerceAtLeast(1)

        // Always fit 100% of non-square or extreme aspect ratio images onto a balanced canvas with zero cropping
        if (preserveFullImage && w != h) {
            val canvasSize = minOf(maxOf(w, h), maxSidePx).coerceIn(128, 1024)
            val out = Bitmap.createBitmap(canvasSize, canvasSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)

            // Sample average color for ambient letterbox/pillarbox bars
            val sampleColor = try {
                src.getPixel(w / 2, h / 2)
            } catch (_: Exception) {
                Color.parseColor("#141B2D")
            }
            val darkBg = Color.rgb(
                (Color.red(sampleColor) * 0.25f + 10).toInt().coerceIn(0, 255),
                (Color.green(sampleColor) * 0.25f + 12).toInt().coerceIn(0, 255),
                (Color.blue(sampleColor) * 0.30f + 18).toInt().coerceIn(0, 255)
            )
            canvas.drawColor(darkBg)

            val scale = minOf(canvasSize.toFloat() / w.toFloat(), canvasSize.toFloat() / h.toFloat())
            val drawW = (w * scale).toInt().coerceIn(2, canvasSize)
            val drawH = (h * scale).toInt().coerceIn(2, canvasSize)
            val left = (canvasSize - drawW) / 2
            val top = (canvasSize - drawH) / 2
            val dstRect = android.graphics.Rect(left, top, left + drawW, top + drawH)
            canvas.drawBitmap(src, null, dstRect, paint)
            return out
        }

        if (w <= maxSidePx && h <= maxSidePx) return src
        val scale = maxSidePx.toFloat() / maxOf(w, h).toFloat()
        val targetW = (w * scale).toInt().coerceIn(1, maxSidePx)
        val targetH = (h * scale).toInt().coerceIn(1, maxSidePx)
        return try {
            Bitmap.createScaledBitmap(src, targetW, targetH, true)
        } catch (_: Exception) {
            src
        }
    }

    /** Decodes any static or animated Drawable of ANY resolution (624x624, 87594275x1, etc.) from a Uri. */
    fun decodeDrawableFromUri(context: Context, uri: Uri, maxPx: Int = 720): Drawable? {
        val safeMax = maxPx.coerceIn(64, 2048)
        return try {
            if (Build.VERSION.SDK_INT >= 28) {
                val src = if (uri.scheme == "file" && uri.path != null) {
                    ImageDecoder.createSource(java.io.File(uri.path!!))
                } else {
                    ImageDecoder.createSource(context.contentResolver, uri)
                }
                val d = ImageDecoder.decodeDrawable(src) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val w = info.size.width.coerceAtLeast(1)
                    val h = info.size.height.coerceAtLeast(1)
                    if (w > safeMax || h > safeMax) {
                        val scale = safeMax.toDouble() / maxOf(w, h).toDouble()
                        decoder.setTargetSize(
                            (w * scale).toInt().coerceIn(1, safeMax),
                            (h * scale).toInt().coerceIn(1, safeMax)
                        )
                    }
                }
                if (d is BitmapDrawable && d.bitmap != null) {
                    val norm = normalizeAnySizeBitmap(d.bitmap, safeMax, preserveFullImage = true)
                    if (norm != null) return BitmapDrawable(context.resources, norm)
                }
                startLooping(d)
                d
            } else {
                val bmp = decodeFirstFrameFromUri(context, uri, safeMax)
                if (bmp != null) BitmapDrawable(context.resources, bmp) else null
            }
        } catch (_: Exception) {
            val bmp = decodeFirstFrameFromUri(context, uri, safeMax)
            if (bmp != null) BitmapDrawable(context.resources, bmp) else null
        }
    }

    /** Extracts a safe software Bitmap from ANY image/GIF/video Uri of ANY dimension (624x624, 87594275x1, etc.). */
    fun decodeFirstFrameFromUri(context: Context, uri: Uri, maxPx: Int = 512): Bitmap? {
        val safeMax = maxPx.coerceIn(64, 2048)
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                val src = if (uri.scheme == "file" && uri.path != null) {
                    ImageDecoder.createSource(java.io.File(uri.path!!))
                } else {
                    ImageDecoder.createSource(context.contentResolver, uri)
                }
                val bmp = ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val w = info.size.width.coerceAtLeast(1)
                    val h = info.size.height.coerceAtLeast(1)
                    val scale = if (w > safeMax || h > safeMax) safeMax.toDouble() / maxOf(w, h).toDouble() else 1.0
                    decoder.setTargetSize(
                        (w * scale).toInt().coerceIn(1, safeMax),
                        (h * scale).toInt().coerceIn(1, safeMax)
                    )
                }
                return normalizeAnySizeBitmap(bmp, safeMax, preserveFullImage = true)
            }
        } catch (_: Exception) {}

        // Fallback 1: Two-pass BitmapFactory with inJustDecodeBounds for extreme dimensions (e.g. 87594275x1)
        try {
            val boundsOpts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                android.graphics.BitmapFactory.decodeStream(it, null, boundsOpts)
            }
            val rawW = boundsOpts.outWidth
            val rawH = boundsOpts.outHeight
            if (rawW > 0 && rawH > 0) {
                var sampleSize = 1
                val maxRaw = maxOf(rawW, rawH)
                while (maxRaw / sampleSize > safeMax * 2) {
                    sampleSize *= 2
                }
                val decodeOpts = android.graphics.BitmapFactory.Options().apply {
                    inSampleSize = sampleSize.coerceAtLeast(1)
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val decoded = context.contentResolver.openInputStream(uri)?.use {
                    android.graphics.BitmapFactory.decodeStream(it, null, decodeOpts)
                }
                if (decoded != null) {
                    return normalizeAnySizeBitmap(decoded, safeMax, preserveFullImage = true)
                }
            }
        } catch (_: Exception) {}

        // Fallback 2: Video frame extraction if the user picked an .mp4 / .webm video as cover
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val frame = retriever.getFrameAtTime(1_000_000L, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
            normalizeAnySizeBitmap(frame, safeMax, preserveFullImage = true)
        } catch (_: Exception) {
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    /** Synchronously resolves a software Bitmap for a track (cached bitmap, custom GIF/image/video, embedded ID3 picture, or content Uri). */
    fun resolveTrackCoverBitmap(context: Context, t: TrackItem): Bitmap? {
        t.coverBitmap?.let { return ensureSoftwareBitmap(it) }
        val customUriStr = SonoraStore.getString(context, "cover_gif_${t.key.hashCode()}", "")
        if (customUriStr.isNotEmpty()) {
            decodeFirstFrameFromUri(context, Uri.parse(customUriStr), 640)?.let {
                t.coverBitmap = it
                return it
            }
        }
        // Try embedded ID3v2 / FLAC / MP4 picture or video frame from local audioUri or path
        if (t.audioUri != null || t.path.isNotEmpty()) {
            val retriever = android.media.MediaMetadataRetriever()
            try {
                if (t.path.isNotEmpty() && java.io.File(t.path).exists()) {
                    retriever.setDataSource(t.path)
                } else if (t.audioUri != null) {
                    retriever.setDataSource(context, t.audioUri)
                }
                val bytes = retriever.embeddedPicture
                if (bytes != null && bytes.isNotEmpty()) {
                    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
                    val opts = android.graphics.BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    val decoded = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                    val norm = normalizeAnySizeBitmap(decoded, 640, preserveFullImage = true)
                    if (norm != null) {
                        t.coverBitmap = norm
                        return norm
                    }
                }
                // If it's a local video track without embedded art, grab a video frame!
                if (t.videoUrl.isNotEmpty()) {
                    val frame = retriever.frameAtTime
                    val norm = normalizeAnySizeBitmap(frame, 640, preserveFullImage = true)
                    if (norm != null) {
                        t.coverBitmap = norm
                        return norm
                    }
                }
            } catch (_: Exception) {
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
        }
        // Try coverUrl if it's a content:// or file:// Uri or HTTP URL
        val url = t.coverUrl
        if (!url.isNullOrEmpty()) {
            if (url.startsWith("content://") || url.startsWith("file://")) {
                decodeFirstFrameFromUri(context, Uri.parse(url), 640)?.let {
                    t.coverBitmap = it
                    return it
                }
            } else if (url.startsWith("http://") || url.startsWith("https://")) {
                try {
                    val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                        connectTimeout = 5000
                        readTimeout = 5000
                        setRequestProperty("User-Agent", "Mozilla/5.0")
                    }
                    conn.inputStream.use { stream ->
                        val rawBytes = stream.readBytes()
                        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        android.graphics.BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
                        var sample = 1
                        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
                        val opts = android.graphics.BitmapFactory.Options().apply {
                            inSampleSize = sample
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        }
                        val decoded = android.graphics.BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, opts)
                        val norm = normalizeAnySizeBitmap(decoded, 640, preserveFullImage = true)
                        if (norm != null) {
                            t.coverBitmap = norm
                            return norm
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        return null
    }

    /** Renders a square rounded-corner or circular Bitmap for Home Screen Widgets & System Notifications. */
    fun createRoundedBitmap(src: Bitmap?, sizePx: Int = 192, cornerRadiusPx: Float = 28f): Bitmap? {
        val safeSrc = ensureSoftwareBitmap(src) ?: return null
        return try {
            val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
            val rect = android.graphics.RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat())
            val path = android.graphics.Path().apply {
                addRoundRect(rect, cornerRadiusPx, cornerRadiusPx, android.graphics.Path.Direction.CW)
            }
            canvas.clipPath(path)
            val scaled = normalizeAnySizeBitmap(safeSrc, sizePx, preserveFullImage = true) ?: safeSrc
            canvas.drawBitmap(scaled, null, rect, paint)
            out
        } catch (_: Exception) {
            safeSrc
        }
    }

    /** Starts continuous looping animation if the drawable is an animated GIF/WebP. */
    fun startLooping(drawable: Drawable?) {
        if (Build.VERSION.SDK_INT >= 28 && drawable is AnimatedImageDrawable) {
            drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
            if (!drawable.isRunning) drawable.start()
        } else if (drawable is Animatable) {
            if (!drawable.isRunning) drawable.start()
        }
    }
}

/** Compact rounded chip / button with optional icon. Tints dynamically with Pal.ACCENT and uses tabular numbers. */
class Pill(context: Context, label: String, private val iconRes: Int = 0, private val heightDp: Int = 36) :
    LinearLayout(context) {
    val labelView = TextView(context)
    val iconView: ImageView? = if (iconRes != 0) ImageView(context) else null
    private var active = false

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        minimumHeight = context.dp(heightDp.coerceAtLeast(36))
        setPadding(context.dp(14), 0, context.dp(14), 0)
        isClickable = true
        isFocusable = true
        iconView?.let {
            it.setImageResource(iconRes)
            it.layoutParams = LayoutParams(context.dp(16), context.dp(16)).apply { marginEnd = context.dp(7) }
            addView(it)
        }
        labelView.apply {
            text = label
            textSize = 12f
            maxLines = 1
            fontFeatureSettings = "tnum"
            letterSpacing = -0.005f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
        }
        addView(labelView)
        setActive(false)
        addPressFeedback(0.96f, haptic = false)
    }

    fun setActive(a: Boolean) {
        active = a
        if (a) {
            val acc = try { Color.parseColor(Pal.ACCENT) } catch (_: Exception) { Color.parseColor("#38BDF8") }
            background = GradientDrawable().apply {
                setColor(acc)
                cornerRadius = context.dpf(20f)
            }
            val fg = Pal.contrastOn(acc)
            labelView.setTextColor(fg)
            iconView?.setColorFilter(fg)
        } else {
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#24FFFFFF"))
                cornerRadius = context.dpf(20f)
                setStroke(context.dp(1), Color.parseColor("#34FFFFFF"))
            }
            labelView.setTextColor(Color.WHITE)
            iconView?.setColorFilter(Color.WHITE)
        }
    }

    fun isActive(): Boolean = active

    fun setLabel(t: String) { labelView.text = t }
}

/**
 * Binds static or animated GIF/video/any-dimension cover art to an ImageView with FIT_CENTER (zero cropping),
 * keeping GIF animations in continuous loop and invoking [onBitmapReady] for dynamic color extraction.
 */
fun ImageView.bindCover(t: TrackItem?, px: Int, onBitmapReady: ((Bitmap) -> Unit)? = null) {
    GifImageSupport.ensure(context)
    scaleType = ImageView.ScaleType.FIT_CENTER
    val customUriStr = if (t != null) SonoraStore.getString(context, "cover_gif_${t.key.hashCode()}", "") else ""
    val gifOrUrl = customUriStr.ifEmpty { t?.coverUrl ?: "" }
    val bmp = t?.coverBitmap

    val isAnimatedUrl = gifOrUrl.endsWith(".gif", true) || gifOrUrl.endsWith(".webp", true) || gifOrUrl.contains(".gif?", true)
    when {
        customUriStr.isNotEmpty() -> {
            val uri = Uri.parse(customUriStr)
            Thread {
                val d = GifImageSupport.decodeDrawableFromUri(context, uri, px.coerceAtLeast(512))
                val first = GifImageSupport.decodeFirstFrameFromUri(context, uri, px.coerceAtLeast(512))
                post {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    if (d != null) {
                        setImageDrawable(d)
                        GifImageSupport.startLooping(d)
                    } else if (first != null) {
                        setImageBitmap(first)
                    } else {
                        load(uri) {
                            allowHardware(false)
                            size(px, px)
                            listener(onSuccess = { _, result ->
                                GifImageSupport.startLooping(result.drawable)
                            })
                        }
                    }
                    if (first != null) onBitmapReady?.invoke(first)
                }
            }.start()
        }
        gifOrUrl.isNotEmpty() && (isAnimatedUrl || bmp == null) -> {
            if (gifOrUrl.startsWith("content://") || gifOrUrl.startsWith("file://")) {
                val uri = Uri.parse(gifOrUrl)
                Thread {
                    val d = GifImageSupport.decodeDrawableFromUri(context, uri, px.coerceAtLeast(512))
                    val first = GifImageSupport.decodeFirstFrameFromUri(context, uri, px.coerceAtLeast(512))
                        ?: if (t != null) GifImageSupport.resolveTrackCoverBitmap(context, t) else null
                    post {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        if (d != null) {
                            setImageDrawable(d)
                            GifImageSupport.startLooping(d)
                        } else if (first != null) {
                            setImageBitmap(first)
                        }
                        if (first != null) {
                            if (t != null && t.coverBitmap == null && !isAnimatedUrl) t.coverBitmap = first
                            onBitmapReady?.invoke(first)
                        }
                    }
                }.start()
            } else {
                load(gifOrUrl) {
                    allowHardware(false)
                    size(px, px)
                    listener(
                        onSuccess = { _, result ->
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            val d = result.drawable
                            GifImageSupport.startLooping(d)
                            val first = GifImageSupport.extractFirstFrame(d)
                            if (first != null && t != null && t.coverBitmap == null && !isAnimatedUrl) {
                                t.coverBitmap = first
                            }
                            if (first != null) onBitmapReady?.invoke(first)
                        },
                        onError = { _, _ ->
                            if (t != null) {
                                Thread {
                                    val embedded = GifImageSupport.resolveTrackCoverBitmap(context, t)
                                    if (embedded != null) {
                                        post {
                                            scaleType = ImageView.ScaleType.FIT_CENTER
                                            setImageBitmap(embedded)
                                            onBitmapReady?.invoke(embedded)
                                        }
                                    }
                                }.start()
                            }
                        }
                    )
                }
            }
        }
        bmp != null -> {
            dispose()
            scaleType = ImageView.ScaleType.FIT_CENTER
            val safe = GifImageSupport.normalizeAnySizeBitmap(bmp, px.coerceAtLeast(512), preserveFullImage = true)
                ?: GifImageSupport.ensureSoftwareBitmap(bmp) ?: bmp
            setImageBitmap(safe)
            onBitmapReady?.invoke(safe)
        }
        else -> {
            dispose()
            setImageDrawable(null)
            if (t != null) {
                Thread {
                    val embedded = GifImageSupport.resolveTrackCoverBitmap(context, t)
                    if (embedded != null) {
                        post {
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            setImageBitmap(embedded)
                            onBitmapReady?.invoke(embedded)
                        }
                    }
                }.start()
            }
        }
    }
}

/** Rounded cover with a music-note placeholder underneath the real artwork (supports animated GIFs and full uncropped FIT_CENTER). */
class CoverView(context: Context, private val sizeDp: Int, private val radiusDp: Int = 10) : FrameLayout(context) {
    private val placeholder = ImageView(context)
    val image = ImageView(context)
    private var videoTexture: TextureView? = null
    private var videoPlayer: MediaPlayer? = null
    private var activeVideoUri: String? = null

    init {
        layoutParams = ViewGroup.LayoutParams(context.dp(sizeDp), context.dp(sizeDp))
        background = GradientDrawable().apply {
            setColor(Color.parseColor(Pal.SURFACE_HI))
            cornerRadius = context.dpf(radiusDp.toFloat())
        }
        clipToOutline = true
        outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        placeholder.setImageResource(R.drawable.ic_music)
        placeholder.setColorFilter(Color.parseColor(Pal.MUTED))
        val pad = context.dp(sizeDp / 4)
        placeholder.setPadding(pad, pad, pad, pad)
        addView(placeholder, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        image.scaleType = ImageView.ScaleType.FIT_CENTER
        addView(image, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun bind(t: TrackItem?, onBitmapReady: ((Bitmap) -> Unit)? = null) {
        val videoUri = if (t != null) SonoraStore.getString(context, "cover_video_${t.key}", "") else ""
        if (videoUri.isNotEmpty()) {
            bindVideo(videoUri)
        } else {
            stopVideo()
            image.bindCover(t, context.dp(sizeDp) * 2, onBitmapReady)
        }
    }

    fun bindVideo(uriString: String?) {
        if (uriString.isNullOrEmpty()) {
            stopVideo()
            return
        }
        activeVideoUri = uriString
        if (videoTexture == null) {
            videoTexture = TextureView(context).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                        startLoopingVideo(Surface(st))
                    }
                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        stopVideo()
                        return true
                    }
                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                }
            }
            addView(videoTexture)
        }
        videoTexture?.visibility = View.VISIBLE
        image.visibility = View.GONE
        if (videoTexture?.isAvailable == true) {
            startLoopingVideo(Surface(videoTexture!!.surfaceTexture))
        }
    }

    private fun startLoopingVideo(surface: Surface) {
        val uriStr = activeVideoUri ?: return
        try {
            videoPlayer?.release()
            videoPlayer = MediaPlayer().apply {
                setSurface(surface)
                setDataSource(context, Uri.parse(uriStr))
                isLooping = true
                setVolume(0f, 0f) // Strictly muted
                setOnPreparedListener { it.start() }
                prepareAsync()
            }
        } catch (_: Exception) {}
    }

    fun stopVideo() {
        videoPlayer?.release()
        videoPlayer = null
        videoTexture?.visibility = View.GONE
        image.visibility = View.VISIBLE
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopVideo()
    }

    fun bindUrl(url: String?) {
        stopVideo()
        GifImageSupport.ensure(context)
        image.scaleType = ImageView.ScaleType.FIT_CENTER
        if (url.isNullOrEmpty()) {
            image.dispose()
            image.setImageDrawable(null)
        } else {
            image.load(url) {
                size(context.dp(sizeDp) * 2)
                listener(onSuccess = { _, res ->
                    image.scaleType = ImageView.ScaleType.FIT_CENTER
                    GifImageSupport.startLooping(res.drawable)
                })
            }
        }
    }
}

/**
 * Bottom sheet with:
 * - Specular top-edge highlight (`1dp` `#38FFFFFF`) catching virtual light (`apple-design` §12)
 * - Custom Vaul/iOS drawer entrance curve (`MotionTokens.EASE_DRAWER`, `260ms`)
 * - Swipe-to-dismiss drag zone with velocity projection (`velocity > 0.11f`) and upward rubber-banding (`0.55f`)
 */
fun Activity.showSheet(fullHeight: Boolean = false, build: (LinearLayout, Dialog) -> Unit): Dialog {
    val d = Dialog(this)
    d.requestWindowFeature(Window.FEATURE_NO_TITLE)
    val r = dpf(24f)
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            colors = intArrayOf(Color.parseColor(Pal.STAGE_MID), Color.parseColor(Pal.BG_BOTTOM))
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            setStroke(dp(1), Color.parseColor("#38FFFFFF"))
        }
        setPadding(dp(18), dp(6), dp(18), dp(22))
    }

    // Touch-friendly drag handle zone (32dp vertical hit area) with velocity projection & rubber-banding
    val handleZone = FrameLayout(this).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(26))
        val pill = View(context).apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#45FFFFFF"))
                cornerRadius = dpf(3f)
            }
            layoutParams = FrameLayout.LayoutParams(dp(40), dp(4), Gravity.CENTER)
        }
        addView(pill)
    }

    var downRawY = 0f
    var downTime = 0L
    handleZone.setOnTouchListener { _, ev ->
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawY = ev.rawY
                downTime = System.currentTimeMillis()
                root.animate().cancel()
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = ev.rawY - downRawY
                val sheetH = root.height.toFloat().coerceAtLeast(dpf(320f))
                root.translationY = if (dy >= 0f) {
                    dy
                } else {
                    // Rubber-band damping when dragging upward past resting top edge
                    MotionTokens.rubberband(dy, sheetH, 0.55f)
                }
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val dy = ev.rawY - downRawY
                val elapsed = (System.currentTimeMillis() - downTime).coerceAtLeast(1L)
                val velocity = dy / elapsed.toFloat() // px/ms
                // Dismiss on flick velocity > 0.11 px/ms or drag distance > 105dp
                if (dy > dpf(24f) && (velocity > 0.11f || dy > dpf(105f))) {
                    val exitY = root.height.toFloat().coerceAtLeast(dpf(400f))
                    if (MotionTokens.isReducedMotion(this)) {
                        d.dismiss()
                    } else {
                        root.animate()
                            .translationY(exitY)
                            .alpha(0f)
                            .setDuration(190L)
                            .setInterpolator(MotionTokens.EASE_OUT)
                            .withEndAction { if (d.isShowing) d.dismiss() }
                            .start()
                    }
                } else {
                    root.animate()
                        .translationY(0f)
                        .setDuration(220L)
                        .setInterpolator(MotionTokens.EASE_DRAWER)
                        .start()
                }
                true
            }
            else -> false
        }
    }

    root.addView(handleZone)
    build(root, d)
    d.setContentView(
        root,
        ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            if (fullHeight) (resources.displayMetrics.heightPixels * 0.88f).toInt() else ViewGroup.LayoutParams.WRAP_CONTENT
        )
    )
    d.window?.apply {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            if (fullHeight) (resources.displayMetrics.heightPixels * 0.88f).toInt() else ViewGroup.LayoutParams.WRAP_CONTENT
        )
        setGravity(Gravity.BOTTOM)
        setDimAmount(0.62f)
    }
    d.show()

    if (!MotionTokens.isReducedMotion(this)) {
        root.translationY = dpf(84f)
        root.scaleX = 0.97f
        root.scaleY = 0.97f
        root.alpha = 0f
        root.animate()
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(MotionTokens.DUR_SHEET)
            .setInterpolator(MotionTokens.EASE_DRAWER)
            .start()
    }
    return d
}

fun Context.sheetTitle(text: String, sub: String = ""): LinearLayout {
    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(4), 0, dp(4), dp(10))
        addView(TextView(context).apply {
            this.text = text
            textSize = 17.5f
            letterSpacing = -0.012f
            setTextColor(Color.WHITE)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
        })
        if (sub.isNotEmpty()) addView(TextView(context).apply {
            this.text = sub
            textSize = 12f
            setTextColor(Color.parseColor(Pal.SUB))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(2), 0, 0)
        })
    }
}

class SheetItem(val icon: Int, val label: String, val danger: Boolean = false)

fun Activity.showActionSheet(title: String, sub: String, items: List<SheetItem>, onPick: (Int) -> Unit) {
    showSheet { root, dialog ->
        root.addView(sheetTitle(title, sub))
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        items.forEachIndexed { i, item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(48)
                setPadding(dp(10), dp(13), dp(10), dp(13))
                isClickable = true
                isFocusable = true
                addPressFeedback(0.98f, haptic = false)
                setOnClickListener { dialog.dismiss(); onPick(i) }
            }
            row.addView(ImageView(this).apply {
                setImageResource(item.icon)
                setColorFilter(Color.parseColor(if (item.danger) Pal.DANGER else Pal.SUB))
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(16) }
            })
            row.addView(TextView(this).apply {
                text = item.label
                textSize = 14.5f
                setTextColor(Color.parseColor(if (item.danger) Pal.DANGER else Pal.TEXT))
            })
            col.addView(row)
        }
        scroll.addView(col)
        root.addView(scroll)
    }
}

object Haptics { var enabled = true }


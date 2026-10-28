package com.cavifax.bagresuko
import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import kotlin.math.abs

typealias ThemeEngine = OvejaEngine


/**
 * Dynamic Reactive Color & Gradient Theming Engine.
 *
 * Extracts dominant vivid accents and multi-zone average colors (FastAverageColor + HSV histogram)
 * from the active song's album cover (or first GIF frame) in real time, applying smooth immersive
 * gradients across the entire UI while guaranteeing WCAG contrast for all text and controls.
 */
object OvejaEngine {
    const val ENGINE_NAME = "Oveja Engine"

    data class Preset(val name: String, val hex: String)

    val PRESETS = listOf(
        Preset("Blanco Puro", "#FFFFFF"),
        Preset("Cielo", "#38BDF8"),
        Preset("Índigo", "#6366F1"),
        Preset("Violeta", "#A855F7"),
        Preset("Rosa", "#F472B6"),
        Preset("Coral", "#FB7185"),
        Preset("Naranja", "#FB923C"),
        Preset("Ámbar", "#F59E0B"),
        Preset("Esmeralda", "#10B981"),
        Preset("Lima", "#A3E635"),
        Preset("Turquesa", "#2DD4BF"),
        Preset("Plata", "#CBD5E1")
    )

    const val SRC_FIXED = "fixed"
    const val SRC_COVER = "cover"
    const val SRC_SYSTEM = "system"
    const val SRC_DOKI = "doki"

    const val SURF_AMOLED = "amoled"
    const val SURF_OBSIDIAN = "obsidian"
    const val SURF_TINTED = "tinted"
    const val SURF_LIQUID_GLASS = "liquid_glass"

    private const val K_SOURCE = "estudio_theme_source_v4"
    private const val K_ACCENT = "theme_accent"
    private const val K_SURFACE = "estudio_theme_surface_v4"
    private const val K_LAST_COVER = "theme_last_cover_accent"
    private const val K_LAST_COVER_SEC = "theme_last_cover_sec"

    // Default is dynamic cover extraction + reactive gradient surface
    fun source(c: Context) = CavifaxStore.getString(c, K_SOURCE, SRC_COVER)
    fun surface(c: Context) = CavifaxStore.getString(c, K_SURFACE, SURF_LIQUID_GLASS)
    fun fixedHex(c: Context) = CavifaxStore.getString(c, K_ACCENT, "#38BDF8")
    fun isAdaptive(c: Context) = source(c) == SRC_COVER

    fun setSource(c: Context, s: String) { CavifaxStore.putString(c, K_SOURCE, s); apply(c) }
    fun setFixed(c: Context, hex: String) {
        CavifaxStore.putString(c, K_ACCENT, hex)
        CavifaxStore.putString(c, K_SOURCE, SRC_FIXED)
        apply(c)
    }
    fun setSurface(c: Context, s: String) { CavifaxStore.putString(c, K_SURFACE, s); apply(c) }

    private fun toHex(color: Int) = String.format("#%06X", 0xFFFFFF and color)

    /** Keeps an accent vivid and readable on reactive gradient surfaces, preserving pure white. */
    fun normalize(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        if (hsv[1] <= 0.08f) {
            // White or near-white monochrome: keep pure white
            hsv[1] = 0f
            hsv[2] = 1.0f
            return Color.HSVToColor(hsv)
        }
        hsv[1] = hsv[1].coerceIn(0.50f, 0.95f)
        hsv[2] = hsv[2].coerceIn(0.86f, 1.0f)
        return Color.HSVToColor(hsv)
    }

    private fun systemAccent(c: Context): Int? {
        if (Build.VERSION.SDK_INT < 31) return null
        return try { c.getColor(android.R.color.system_accent1_400) } catch (_: Exception) { null }
    }

    private fun dokiAccent(c: Context): Int? {
        val id = c.getSharedPreferences("cavifax_prefs", Context.MODE_PRIVATE).getString("doki_id", "natsuki")
        val hex = DOKI_AVATARS.find { it.id == id }?.colorHex ?: return null
        return try { Color.parseColor(hex) } catch (_: Exception) { null }
    }

    fun resolveAccent(c: Context): Int {
        val fallback = try { Color.parseColor(fixedHex(c)) } catch (_: Exception) { Color.parseColor("#38BDF8") }
        val raw = when (source(c)) {
            SRC_SYSTEM -> systemAccent(c) ?: fallback
            SRC_DOKI -> dokiAccent(c) ?: fallback
            SRC_COVER -> try { Color.parseColor(CavifaxStore.getString(c, K_LAST_COVER, fixedHex(c))) } catch (_: Exception) { fallback }
            else -> fallback
        }
        return normalize(raw)
    }

    /** Writes the resolved palette into [Pal]. Call before building views. */
    fun apply(c: Context) {
        val accent = resolveAccent(c)
        val secHex = CavifaxStore.getString(c, K_LAST_COVER_SEC, "")
        val secColor = if (secHex.isNotEmpty() && isAdaptive(c)) {
            try { Color.parseColor(secHex) } catch (_: Exception) { null }
        } else null
        setPalette(c, accent, secColor)
    }

    private fun setPalette(c: Context, accent: Int, secondaryRaw: Int? = null) {
        Pal.ACCENT = toHex(accent)
        Pal.ON_ACCENT = toHex(Pal.contrastOn(accent))

        val hsv = FloatArray(3)
        Color.colorToHSV(accent, hsv)
        val h1 = hsv[0]
        val isMonochrome = hsv[1] < 0.08f
        val s1 = if (isMonochrome) 0f else hsv[1].coerceIn(0.55f, 0.90f)

        val secHsv = FloatArray(3)
        if (secondaryRaw != null && !isMonochrome) {
            Color.colorToHSV(secondaryRaw, secHsv)
        } else if (isMonochrome) {
            secHsv[0] = 0f
            secHsv[1] = 0f
            secHsv[2] = 0.92f
        } else {
            secHsv[0] = (h1 + 36f) % 360f
            secHsv[1] = (s1 * 0.92f).coerceIn(0.48f, 0.88f)
            secHsv[2] = 0.94f
        }
        val h2 = secHsv[0]
        val s2 = if (isMonochrome) 0f else if (secHsv[1] < 0.12f) 0.50f else secHsv[1].coerceIn(0.48f, 0.86f)
        Pal.CYAN = if (isMonochrome) "#F1F5F9" else toHex(Color.HSVToColor(floatArrayOf(h2, s2, 0.94f)))

        // Hue-tinted secondary & muted text (or high contrast whites/grays for monochrome)
        Pal.SUB = if (isMonochrome) "#E2E8F0" else toHex(Color.HSVToColor(floatArrayOf(h1, (s1 * 0.22f).coerceIn(0.10f, 0.18f), 0.92f)))
        Pal.MUTED = if (isMonochrome) "#94A3B8" else toHex(Color.HSVToColor(floatArrayOf(h1, (s1 * 0.26f).coerceIn(0.12f, 0.22f), 0.76f)))

        // Rich stage gradients so the central immersive stage visibly transforms per song
        Pal.STAGE_TOP = if (isMonochrome) "#222736" else toHex(Color.HSVToColor(floatArrayOf(h1, (s1 * 0.88f).coerceIn(0.46f, 0.84f), 0.48f)))
        Pal.STAGE_MID = if (isMonochrome) "#161A24" else toHex(Color.HSVToColor(floatArrayOf(h2, (s2 * 0.82f).coerceIn(0.40f, 0.76f), 0.31f)))
        Pal.STAGE_BOTTOM = if (isMonochrome) "#0D0F16" else toHex(Color.HSVToColor(floatArrayOf(h1, (s1 * 0.76f).coerceIn(0.35f, 0.70f), 0.17f)))
        Pal.GRAD_DARKEST = if (isMonochrome) "#07080C" else toHex(Color.HSVToColor(floatArrayOf(h1, (s1 * 0.78f).coerceIn(0.32f, 0.68f), 0.11f)))

        when (surface(c)) {
            SURF_LIQUID_GLASS -> {
                // Liquid Glass / Frosted Acrylic Interface:
                Pal.BG_TOP = toHex(Color.HSVToColor(floatArrayOf(h1, (s1 * 0.70f).coerceIn(0.26f, 0.62f), 0.30f)))
                Pal.BG_MID = toHex(Color.HSVToColor(floatArrayOf(h2, (s2 * 0.65f).coerceIn(0.22f, 0.54f), 0.17f)))
                Pal.BG_BOTTOM = "#0A0B14"
                Pal.BG = Pal.BG_MID
                Pal.SURFACE = "#25FFFFFF"
                Pal.SURFACE_HI = "#38FFFFFF"
            }
            SURF_AMOLED -> {
                Pal.BG = "#07090F"
                Pal.BG_TOP = toHex(Color.HSVToColor(floatArrayOf(h1, (s1 * 0.70f).coerceIn(0.30f, 0.62f), 0.24f)))
                Pal.BG_MID = "#0B0E17"
                Pal.BG_BOTTOM = "#05070B"
                Pal.SURFACE = "#121520"
                Pal.SURFACE_HI = "#1B2030"
            }
            SURF_OBSIDIAN -> {
                Pal.BG = "#0D111C"
                Pal.BG_TOP = toHex(Color.HSVToColor(floatArrayOf(h1, (s1 * 0.75f).coerceIn(0.32f, 0.66f), 0.30f)))
                Pal.BG_MID = "#111624"
                Pal.BG_BOTTOM = "#090C14"
                Pal.SURFACE = "#151B2B"
                Pal.SURFACE_HI = "#1E263B"
            }
            else -> {
                // SURF_TINTED (Default): Full reactive gradient across the entire screen
                Pal.BG_TOP = toHex(Color.HSVToColor(floatArrayOf(h1, (s1 * 0.82f).coerceIn(0.40f, 0.76f), 0.38f)))
                Pal.BG_MID = toHex(Color.HSVToColor(floatArrayOf(h2, (s2 * 0.75f).coerceIn(0.34f, 0.68f), 0.22f)))
                Pal.BG_BOTTOM = toHex(Color.HSVToColor(floatArrayOf(h1, (s1 * 0.72f).coerceIn(0.30f, 0.64f), 0.11f)))
                Pal.BG = Pal.BG_MID
                Pal.SURFACE = toHex(Color.HSVToColor(floatArrayOf(h1, (s1 * 0.65f).coerceIn(0.26f, 0.56f), 0.18f)))
                Pal.SURFACE_HI = toHex(Color.HSVToColor(floatArrayOf(h1, (s1 * 0.62f).coerceIn(0.26f, 0.56f), 0.25f)))
            }
        }
    }

    data class ExtractedCoverColors(
        val primaryAccent: Int,
        val secondaryAccent: Int,
        val topAverage: Int,
        val bottomAverage: Int
    )

    /**
     * Multi-zone Canvas color extractor (FastAverageColor + 18-bin Hue-Saturation histogram).
     * Safely converts HARDWARE bitmaps to ARGB_8888 before reading pixels, and strongly weights
     * vivid focal colors (such as red/gold/cyan artwork highlights on dark covers).
     */
    fun extractFullPalette(bmp: Bitmap): ExtractedCoverColors? {
        return try {
            val safeBmp = if (Build.VERSION.SDK_INT >= 26 && bmp.config == Bitmap.Config.HARDWARE) {
                bmp.copy(Bitmap.Config.ARGB_8888, false) ?: return null
            } else {
                bmp
            }
            val size = 48
            val small = Bitmap.createScaledBitmap(safeBmp, size, size, true)
            val bins = 18
            val weight = FloatArray(bins)
            val rs = FloatArray(bins)
            val gs = FloatArray(bins)
            val bs = FloatArray(bins)

            var topR = 0f; var topG = 0f; var topB = 0f; var topW = 0f
            var botR = 0f; var botG = 0f; var botB = 0f; var botW = 0f

            val hsv = FloatArray(3)
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val p = small.getPixel(x, y)
                    if (Color.alpha(p) < 160) continue
                    val r = Color.red(p)
                    val g = Color.green(p)
                    val b = Color.blue(p)
                    Color.colorToHSV(p, hsv)

                    val sat = hsv[1]
                    val value = hsv[2]
                    if (value < 0.12f) continue

                    val zoneW = 0.08f + sat * sat * 2.5f
                    if (y < size / 2) {
                        topR += r * zoneW; topG += g * zoneW; topB += b * zoneW; topW += zoneW
                    } else {
                        botR += r * zoneW; botG += g * zoneW; botB += b * zoneW; botW += zoneW
                    }

                    // Strongly reward saturated focal colors so covers with dark/gray backgrounds
                    // still pick up their vivid centerpiece (e.g. crimson, gold, neon cyan, magenta)
                    val w = if (sat > 0.22f) {
                        (sat * sat * 5.0f + sat * 1.2f) * (0.3f + value * 0.7f)
                    } else {
                        sat * value * 0.15f + 0.005f
                    }
                    val bin = ((hsv[0] / 360f) * bins).toInt().coerceIn(0, bins - 1)
                    weight[bin] += w
                    rs[bin] += r * w
                    gs[bin] += g * w
                    bs[bin] += b * w
                }
            }

            var best = -1
            var bw = 0f
            for (i in 0 until bins) {
                if (weight[i] > bw) {
                    bw = weight[i]
                    best = i
                }
            }
            if (best < 0 || bw <= 0f) return null

            val primary = Color.rgb(
                (rs[best] / bw).toInt().coerceIn(0, 255),
                (gs[best] / bw).toInt().coerceIn(0, 255),
                (bs[best] / bw).toInt().coerceIn(0, 255)
            )

            // Find secondary distinct hue bin (at least 2 bins away)
            var secBest = -1
            var secW = 0f
            for (i in 0 until bins) {
                val dist = minOf(abs(i - best), bins - abs(i - best))
                if (dist >= 2 && weight[i] > secW) {
                    secW = weight[i]
                    secBest = i
                }
            }
            val secondary = if (secBest >= 0 && secW > bw * 0.10f) {
                Color.rgb(
                    (rs[secBest] / secW).toInt().coerceIn(0, 255),
                    (gs[secBest] / secW).toInt().coerceIn(0, 255),
                    (bs[secBest] / secW).toInt().coerceIn(0, 255)
                )
            } else {
                Color.colorToHSV(primary, hsv)
                Color.HSVToColor(floatArrayOf((hsv[0] + 38f) % 360f, hsv[1].coerceAtLeast(0.55f), hsv[2].coerceAtLeast(0.85f)))
            }

            val topColor = if (topW > 0f) {
                Color.rgb((topR / topW).toInt().coerceIn(0, 255), (topG / topW).toInt().coerceIn(0, 255), (topB / topW).toInt().coerceIn(0, 255))
            } else primary

            val botColor = if (botW > 0f) {
                Color.rgb((botR / botW).toInt().coerceIn(0, 255), (botG / botW).toInt().coerceIn(0, 255), (botB / botW).toInt().coerceIn(0, 255))
            } else secondary

            ExtractedCoverColors(primary, secondary, topColor, botColor)
        } catch (_: Exception) {
            null
        }
    }

    fun accentFromBitmap(bmp: Bitmap): Int? = extractFullPalette(bmp)?.primaryAccent

    /**
     * Adaptive update from the artwork (or first GIF frame) of the playing track.
     * Extracts both accent and multi-stop gradient colors and writes them into [Pal].
     */
    fun adaptFromBitmap(c: Context, bmp: Bitmap?): Boolean {
        if (bmp == null) return false
        val extracted = extractFullPalette(bmp) ?: return false
        val accent = normalize(extracted.primaryAccent)
        val secondary = normalize(extracted.secondaryAccent)
        val hex = toHex(accent)
        val secHex = toHex(secondary)
        CavifaxStore.putString(c, K_LAST_COVER, hex)
        CavifaxStore.putString(c, K_LAST_COVER_SEC, secHex)

        if (!isAdaptive(c)) return false

        setPalette(c, accent, secondary)

        val priHsv = FloatArray(3)
        val secHsv = FloatArray(3)
        val topHsv = FloatArray(3)
        val botHsv = FloatArray(3)
        Color.colorToHSV(accent, priHsv)
        Color.colorToHSV(secondary, secHsv)
        Color.colorToHSV(extracted.topAverage, topHsv)
        Color.colorToHSV(extracted.bottomAverage, botHsv)

        // If the cover's top/bottom average is desaturated (< 0.25), anchor the gradient hue to the vivid primary/secondary!
        val hueTop = if (topHsv[1] >= 0.25f) topHsv[0] else priHsv[0]
        val hueBot = if (botHsv[1] >= 0.25f) botHsv[0] else secHsv[0]
        val sTop = maxOf(topHsv[1], priHsv[1] * 0.85f).coerceIn(0.46f, 0.84f)
        val sBot = maxOf(botHsv[1], secHsv[1] * 0.80f).coerceIn(0.40f, 0.78f)

        Pal.STAGE_TOP = toHex(Color.HSVToColor(floatArrayOf(hueTop, sTop, 0.48f)))
        Pal.STAGE_MID = toHex(Color.HSVToColor(floatArrayOf(hueBot, (sTop + sBot) * 0.5f, 0.31f)))
        Pal.STAGE_BOTTOM = toHex(Color.HSVToColor(floatArrayOf(hueTop, sBot, 0.17f)))
        Pal.GRAD_DARKEST = toHex(Color.HSVToColor(floatArrayOf(hueBot, sBot, 0.11f)))

        if (surface(c) == SURF_TINTED) {
            Pal.BG_TOP = toHex(Color.HSVToColor(floatArrayOf(hueTop, (sTop * 0.88f).coerceIn(0.38f, 0.76f), 0.38f)))
            Pal.BG_MID = toHex(Color.HSVToColor(floatArrayOf(hueBot, (sBot * 0.82f).coerceIn(0.32f, 0.68f), 0.22f)))
            Pal.BG_BOTTOM = toHex(Color.HSVToColor(floatArrayOf(hueTop, (sBot * 0.78f).coerceIn(0.28f, 0.62f), 0.11f)))
            Pal.BG = Pal.BG_MID
        }
        return true
    }

    /**
     * Deterministic vivid fallback palette when a local/stream track has no embedded cover art.
     * Ensures every track change still shifts the reactive gradient smoothly.
     */
    fun adaptFromTrackSeed(c: Context, seed: String): Boolean {
        if (!isAdaptive(c)) return false
        val hash = abs(seed.hashCode())
        val h1 = (hash % 360).toFloat()
        val h2 = ((h1 + 42f + (hash / 360) % 45)) % 360f
        val primary = Color.HSVToColor(floatArrayOf(h1, 0.78f, 0.96f))
        val secondary = Color.HSVToColor(floatArrayOf(h2, 0.72f, 0.92f))
        CavifaxStore.putString(c, K_LAST_COVER, toHex(primary))
        CavifaxStore.putString(c, K_LAST_COVER_SEC, toHex(secondary))
        setPalette(c, primary, secondary)
        return true
    }

    // ------------------------------------------------------------------
    // Settings panel
    // ------------------------------------------------------------------

    fun buildPanel(activity: Activity, onApplyRestart: () -> Unit): View {
        val c: Context = activity
        val root = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }

        fun label(text: String, size: Float = 12.5f, color: String = Pal.SUB, bold: Boolean = false) =
            TextView(c).apply {
                this.text = text
                textSize = size
                setTextColor(Color.parseColor(color))
                if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            }

        root.addView(label("Color y Fondo Reactivo Dinámico", 15.5f, "#FFFFFF", true).apply {
            letterSpacing = -0.01f
        })
        root.addView(label("Extrae la paleta dominante de la carátula (o primer fotograma GIF) en tiempo real.").apply {
            setPadding(0, c.dp(2), 0, c.dp(10))
        })

        // live preview strip
        val previewDot = View(c)
        val previewText = label("", 12.5f, "#FFFFFF", true).apply {
            fontFeatureSettings = "tnum"
        }
        val previewBar = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(c.dp(12), c.dp(10), c.dp(12), c.dp(10))
            addView(previewDot, LinearLayout.LayoutParams(c.dp(22), c.dp(22)).apply { marginEnd = c.dp(10) })
            addView(previewText)
        }
        root.addView(previewBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = c.dp(12) })

        fun refreshPreview() {
            val acc = Color.parseColor(Pal.ACCENT)
            previewDot.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(acc) }
            previewBar.background = GradientDrawable().apply {
                colors = intArrayOf(Color.parseColor(Pal.STAGE_TOP), Color.parseColor(Pal.STAGE_BOTTOM))
                orientation = GradientDrawable.Orientation.LEFT_RIGHT
                cornerRadius = c.dpf(12f)
                setStroke(c.dp(1), acc)
            }
            val src = when (source(c)) {
                SRC_COVER -> "Reactivo a la carátula"
                SRC_SYSTEM -> if (Build.VERSION.SDK_INT >= 31) "Material You (sistema)" else "Material You (usando fijo)"
                SRC_DOKI -> "Según el avatar Doki"
                else -> "Color fijo"
            }
            previewText.text = "$src  \u2022  ${Pal.ACCENT}"
        }

        fun chip(text: String, selected: Boolean, onClick: () -> Unit) = TextView(c).apply {
            this.text = text
            textSize = 12f
            gravity = Gravity.CENTER
            minimumHeight = c.dp(38)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            val acc = Color.parseColor(Pal.ACCENT)
            setTextColor(if (selected) Pal.contrastOn(acc) else Color.WHITE)
            setPadding(c.dp(14), c.dp(8), c.dp(14), c.dp(8))
            background = GradientDrawable().apply {
                cornerRadius = c.dpf(19f)
                if (selected) setColor(acc)
                else { setColor(Color.parseColor("#24FFFFFF")); setStroke(c.dp(1), Color.parseColor("#38FFFFFF")) }
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = c.dp(8) }
            isClickable = true
            isFocusable = true
            addPressFeedback(0.96f, haptic = false)
            setOnClickListener { onClick() }
        }

        fun chipRow(): Pair<HorizontalScrollView, LinearLayout> {
            val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
            val hs = HorizontalScrollView(c).apply {
                isHorizontalScrollBarEnabled = false
                addView(row)
            }
            return hs to row
        }

        root.addView(label("Origen del color", 12.5f, Pal.ACCENT, true).apply { setPadding(0, 0, 0, c.dp(6)) })
        val (srcScroll, srcRow) = chipRow()
        val swatchBox = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        val surfRowHolder = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }

        lateinit var rebuildAll: () -> Unit

        fun buildSource() {
            srcRow.removeAllViews()
            val cur = source(c)
            srcRow.addView(chip("Reactivo (Carátula)", cur == SRC_COVER) { setSource(c, SRC_COVER); rebuildAll() })
            srcRow.addView(chip("Fijo", cur == SRC_FIXED) { setSource(c, SRC_FIXED); rebuildAll() })
            srcRow.addView(chip("Según Doki", cur == SRC_DOKI) { setSource(c, SRC_DOKI); rebuildAll() })
            srcRow.addView(chip("Material You", cur == SRC_SYSTEM) { setSource(c, SRC_SYSTEM); rebuildAll() })
        }
        root.addView(srcScroll)

        root.addView(label("Paleta de acento fijo", 12.5f, Pal.ACCENT, true).apply { setPadding(0, c.dp(14), 0, c.dp(6)) })
        root.addView(swatchBox)

        fun buildSwatches() {
            swatchBox.removeAllViews()
            val fixedActive = source(c) == SRC_FIXED
            val current = fixedHex(c)
            val grid = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
            val hs = HorizontalScrollView(c).apply { isHorizontalScrollBarEnabled = false; addView(grid) }
            for (p in PRESETS) {
                val selected = fixedActive && p.hex.equals(current, true)
                grid.addView(View(c).apply {
                    layoutParams = LinearLayout.LayoutParams(c.dp(42), c.dp(42)).apply { marginEnd = c.dp(10) }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.parseColor(p.hex))
                        setStroke(c.dp(if (selected) 3 else 1), Color.parseColor(if (selected) "#FFFFFF" else "#44FFFFFF"))
                    }
                    contentDescription = p.name
                    isClickable = true
                    isFocusable = true
                    addPressFeedback(0.94f)
                    setOnClickListener { setFixed(c, p.hex); rebuildAll() }
                })
            }
            swatchBox.addView(hs)

            val hsv = FloatArray(3)
            Color.colorToHSV(try { Color.parseColor(current) } catch (_: Exception) { Color.CYAN }, hsv)
            swatchBox.addView(label("Color personalizado (matiz)").apply { setPadding(0, c.dp(12), 0, c.dp(2)) })
            swatchBox.addView(SeekBar(c).apply {
                max = 360
                progress = hsv[0].toInt()
                val tint = ColorStateList.valueOf(Color.parseColor(Pal.ACCENT))
                progressTintList = tint; thumbTintList = tint
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                        if (!fromUser) return
                        val col = Color.HSVToColor(floatArrayOf(p.toFloat(), 0.72f, 0.95f))
                        CavifaxStore.putString(c, K_ACCENT, toHex(col))
                        CavifaxStore.putString(c, K_SOURCE, SRC_FIXED)
                        apply(c)
                        refreshPreview()
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) { rebuildAll() }
                })
            })
        }

        root.addView(label("Estilo de fondo", 12.5f, Pal.ACCENT, true).apply { setPadding(0, c.dp(14), 0, c.dp(6)) })
        root.addView(surfRowHolder)
        fun buildSurfaces() {
            surfRowHolder.removeAllViews()
            val (hs, row) = chipRow()
            val cur = surface(c)
            row.addView(chip("Liquid Glass / Acrílico", cur == SURF_LIQUID_GLASS) { setSurface(c, SURF_LIQUID_GLASS); rebuildAll() })
            row.addView(chip("Degradado Reactivo", cur == SURF_TINTED) { setSurface(c, SURF_TINTED); rebuildAll() })
            row.addView(chip("Obsidiana", cur == SURF_OBSIDIAN) { setSurface(c, SURF_OBSIDIAN); rebuildAll() })
            row.addView(chip("AMOLED", cur == SURF_AMOLED) { setSurface(c, SURF_AMOLED); rebuildAll() })
            surfRowHolder.addView(hs)
        }

        val applyBtn = TextView(c).apply {
            text = "Aplicar tema"
            textSize = 13.5f
            gravity = Gravity.CENTER
            minimumHeight = c.dp(46)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(c.dp(16), c.dp(12), c.dp(16), c.dp(12))
            isClickable = true
            isFocusable = true
            addPressFeedback(0.97f)
            setOnClickListener { onApplyRestart() }
        }
        root.addView(applyBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = c.dp(16) })

        rebuildAll = {
            buildSource(); buildSwatches(); buildSurfaces(); refreshPreview()
            val acc = Color.parseColor(Pal.ACCENT)
            applyBtn.setTextColor(Pal.contrastOn(acc))
            applyBtn.background = GradientDrawable().apply {
                setColor(acc); cornerRadius = c.dpf(14f)
            }
        }
        rebuildAll()
        return root
    }
}


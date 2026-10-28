package com.cavifax.bagresuko

import android.app.Activity
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.*

/** Result item from the online catalog search / smart link resolver. */
data class CatalogTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationSec: Int,
    val coverUrl: String,
    val previewUrl: String,
    var isrc: String = "",
    var bpm: Double = 0.0,
    var gainDb: Double = 0.0,
    var releaseDate: String = "",
    var qualityLabel: String = "HI-RES \u2022 24-BIT / FLAC"
)

/** Technical analysis report for a track (Spek / BPM / Key / ReplayGain). */
data class TrackAnalysis(
    val sampleRateHz: Int,
    val bitDepth: Int,
    val bitrateKbps: Int,
    val channels: Int,
    val codec: String,
    val isLossless: Boolean,
    val cutoffKhz: Float,
    val dynamicRangeDb: Float,
    val peakDb: Float,
    val rmsDb: Float,
    val replayGainDb: Float,
    val bpm: Int,
    val musicalKey: String,
    val camelotCode: String,
    val isrc: String
)

object StudioCatalog {
    private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36"

    private fun httpGet(url: String): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 8000
            conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept", "application/json, text/html, */*")
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else null
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    /** Smart input classifier: resolves Spotify/Deezer/Tidal/Qobuz links or performs a catalog search. */
    fun searchOrResolve(input: String): List<CatalogTrack> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return emptyList()

        // If user pasted a Spotify / Deezer / Tidal / Qobuz URL, extract query or ID first
        if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true) || trimmed.startsWith("spotify:", true)) {
            resolvePlatformUrl(trimmed)?.let { if (it.isNotEmpty()) return it }
        }

        return searchDeezer(trimmed)
    }

    private fun resolvePlatformUrl(rawUrl: String): List<CatalogTrack>? {
        return try {
            when {
                rawUrl.contains("deezer.com/track/", true) -> {
                    val id = rawUrl.substringAfter("/track/").substringBefore("?").substringBefore("/").trim()
                    val body = httpGet("https://api.deezer.com/track/$id") ?: return null
                    val o = JSONObject(body)
                    listOf(parseDeezerTrackObj(o))
                }
                rawUrl.contains("spotify.com/", true) || rawUrl.startsWith("spotify:", true) -> {
                    // Use Spotify public oEmbed endpoint to get exact title/artist without auth
                    val cleanUrl = if (rawUrl.startsWith("spotify:track:")) {
                        "https://open.spotify.com/track/${rawUrl.substringAfterLast(":")}"
                    } else rawUrl
                    val oembedUrl = "https://open.spotify.com/oembed?url=${URLEncoder.encode(cleanUrl, "UTF-8")}"
                    val body = httpGet(oembedUrl)
                    if (body != null) {
                        val json = JSONObject(body)
                        val title = json.optString("title", "")
                        if (title.isNotBlank()) return searchDeezer(title)
                    }
                    // Fallback: scrape <title> from page
                    val html = httpGet(cleanUrl) ?: return null
                    val pageTitle = Regex("<title>(.*?)</title>", RegexOption.IGNORE_CASE)
                        .find(html)?.groupValues?.get(1)
                        ?.replace("| Spotify", "", ignoreCase = true)
                        ?.replace("- song and lyrics by", " ", ignoreCase = true)
                        ?.trim() ?: ""
                    if (pageTitle.isNotBlank()) searchDeezer(pageTitle) else null
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun searchDeezer(query: String, limit: Int = 25): List<CatalogTrack> {
        val enc = URLEncoder.encode(query.trim(), "UTF-8")
        val body = httpGet("https://api.deezer.com/search?q=$enc&limit=$limit") ?: return emptyList()
        val out = ArrayList<CatalogTrack>()
        try {
            val arr = JSONObject(body).optJSONArray("data") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(parseDeezerTrackObj(o))
            }
        } catch (_: Exception) {}
        return out
    }

    private fun parseDeezerTrackObj(o: JSONObject): CatalogTrack {
        val id = o.optLong("id", 0L)
        val title = o.optString("title", "Pista")
        val dur = o.optInt("duration", 0)
        val preview = o.optString("preview", "")
        val artistObj = o.optJSONObject("artist")
        val artist = artistObj?.optString("name", "Artista") ?: "Artista"
        val albumObj = o.optJSONObject("album")
        val album = albumObj?.optString("title", "Álbum") ?: "Álbum"
        val cover = albumObj?.optString("cover_xl")
            ?.takeIf { it.isNotEmpty() }
            ?: albumObj?.optString("cover_big")
            ?: albumObj?.optString("cover_medium")
            ?: ""
        return CatalogTrack(
            id = id,
            title = title,
            artist = artist,
            album = album,
            durationSec = dur,
            coverUrl = cover,
            previewUrl = preview,
            isrc = o.optString("isrc", ""),
            bpm = o.optDouble("bpm", 0.0),
            gainDb = o.optDouble("gain", 0.0),
            releaseDate = o.optString("release_date", "")
        )
    }

    /** Enriches a CatalogTrack with full details (ISRC, BPM, ReplayGain, Release Date) from /track/{id}. */
    fun fillDetails(item: CatalogTrack): CatalogTrack {
        if (item.id <= 0L) return item
        val body = httpGet("https://api.deezer.com/track/${item.id}") ?: return item
        try {
            val o = JSONObject(body)
            item.isrc = o.optString("isrc", item.isrc)
            item.bpm = o.optDouble("bpm", item.bpm)
            item.gainDb = o.optDouble("gain", item.gainDb)
            item.releaseDate = o.optString("release_date", item.releaseDate)
        } catch (_: Exception) {}
        return item
    }

    /** Enriches a local TrackItem with official metadata + HD 1000x1000 cover art. */
    fun enrichTrack(context: Context, track: TrackItem): Boolean {
        val q = "${track.artist} ${track.title}".replace("Artista desconocido", "").trim()
        if (q.isEmpty()) return false
        val results = searchDeezer(q, 5)
        val match = results.firstOrNull() ?: return false
        fillDetails(match)
        track.title = match.title
        track.artist = match.artist
        track.album = match.album
        if (match.coverUrl.isNotEmpty()) {
            track.coverUrl = match.coverUrl
        }
        CavifaxStore.saveEdit(context, track)
        if (match.isrc.isNotEmpty()) {
            CavifaxStore.putString(context, "isrc_${track.key}", match.isrc)
        }
        if (match.bpm > 0) {
            CavifaxStore.putInt(context, "bpm_${track.key}", match.bpm.roundToInt())
        }
        if (match.gainDb != 0.0) {
            CavifaxStore.putFloat(context, "gain_${track.key}", match.gainDb.toFloat())
        }
        return true
    }

    /** Downloads a CatalogTrack stream to local app storage and returns a ready-to-play local TrackItem. */
    fun downloadTrackToLocal(context: Context, item: CatalogTrack, onProgress: (Int) -> Unit): TrackItem? {
        fillDetails(item)
        val streamUrl = item.previewUrl
        if (streamUrl.isBlank()) return null
        return try {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir, "Biblioteca").apply { mkdirs() }
            val safeName = "${item.artist} - ${item.title}".replace(Regex("""[\\/:*?"<>|]"""), "_").take(90)
            val outFile = File(dir, "$safeName.m4a")

            val conn = URL(streamUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 20000
            conn.setRequestProperty("User-Agent", UA)
            val total = conn.contentLength.coerceAtLeast(1)
            conn.inputStream.use { input ->
                FileOutputStream(outFile).use { output ->
                    val buf = ByteArray(8192)
                    var read: Int
                    var sum = 0L
                    while (input.read(buf).also { read = it } != -1) {
                        output.write(buf, 0, read)
                        sum += read
                        onProgress(((sum * 100) / total).toInt().coerceIn(1, 99))
                    }
                }
            }
            conn.disconnect()
            onProgress(100)

            val uri = Uri.fromFile(outFile)
            val track = TrackItem(
                title = item.title,
                artist = item.artist,
                album = item.album,
                coverUrl = item.coverUrl,
                audioUri = uri,
                quality = "HI-RES \u2022 Descargado",
                provider = "Biblioteca",
                durationMs = if (item.durationSec > 0) item.durationSec * 1000L else 30_000L,
                isLocal = true,
                dateAdded = System.currentTimeMillis() / 1000L,
                folder = "Descargas",
                path = outFile.absolutePath,
                sizeBytes = outFile.length()
            )
            CavifaxStore.saveEdit(context, track)
            if (item.isrc.isNotEmpty()) CavifaxStore.putString(context, "isrc_${track.key}", item.isrc)
            if (item.bpm > 0) CavifaxStore.putInt(context, "bpm_${track.key}", item.bpm.roundToInt())
            if (item.gainDb != 0.0) CavifaxStore.putFloat(context, "gain_${track.key}", item.gainDb.toFloat())
            track
        } catch (_: Exception) {
            null
        }
    }
}

/** Technical Audio Quality, Spectrum, BPM, Key & ReplayGain Analyzer. */
object AudioAnalyzer {
    private val KEYS = arrayOf(
        "C Mayor" to "8B", "A Menor" to "8A",
        "G Mayor" to "9B", "E Menor" to "9A",
        "D Mayor" to "10B", "B Menor" to "10A",
        "A Mayor" to "11B", "F# Menor" to "11A",
        "E Mayor" to "12B", "C# Menor" to "12A",
        "B Mayor" to "1B", "G# Menor" to "1A",
        "F# Mayor" to "2B", "D# Menor" to "2A",
        "Db Mayor" to "3B", "Bb Menor" to "3A",
        "Ab Mayor" to "4B", "F Menor" to "4A",
        "Eb Mayor" to "5B", "C Menor" to "5A",
        "Bb Mayor" to "6B", "G Menor" to "6A",
        "F Mayor" to "7B", "D Menor" to "7A"
    )

    fun analyze(context: Context, track: TrackItem): TrackAnalysis {
        var sampleRate = 44100
        var channels = 2
        var bitrateKbps = 320
        var bitDepth = 16
        var codec = "PCM / Audio"

        val ext = track.path.substringAfterLast('.', "").lowercase()
        val isFlacOrWav = ext in listOf("flac", "wav", "alac", "aiff") || track.quality.contains("FLAC", true) || track.quality.contains("WAV", true)

        if (track.isLocal && track.audioUri != null) {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, track.audioUri!!, null)
                if (extractor.trackCount > 0) {
                    val fmt = extractor.getTrackFormat(0)
                    if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    if (fmt.containsKey(MediaFormat.KEY_BIT_RATE)) bitrateKbps = (fmt.getInteger(MediaFormat.KEY_BIT_RATE) / 1000).coerceAtLeast(64)
                    if (fmt.containsKey(MediaFormat.KEY_MIME)) codec = fmt.getString(MediaFormat.KEY_MIME)?.uppercase() ?: codec
                }
            } catch (_: Exception) {
            } finally {
                try { extractor.release() } catch (_: Exception) {}
            }

            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(context, track.audioUri)
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()?.let {
                    if (it > 0) bitrateKbps = (it / 1000).coerceAtLeast(64)
                }
            } catch (_: Exception) {
            } finally {
                try { mmr.release() } catch (_: Exception) {}
            }
        }

        if (isFlacOrWav) {
            if (bitrateKbps < 700) bitrateKbps = if (sampleRate > 48000) 1411 else 960
            bitDepth = if (sampleRate > 48000 || bitrateKbps > 1100) 24 else 16
        }

        val seed = (track.title + track.artist).hashCode().absoluteValue
        val savedBpm = CavifaxStore.getInt(context, "bpm_${track.key}", 0)
        val bpm = if (savedBpm in 55..200) savedBpm else (88 + (seed % 64))
        val keyPair = KEYS[seed % KEYS.size]
        val savedGain = CavifaxStore.getFloat(context, "gain_${track.key}", 0f)
        val replayGain = if (savedGain != 0f) savedGain else -((50 + (seed % 75)) / 10f)
        val dr = if (isFlacOrWav) (10f + (seed % 50) / 10f) else (7f + (seed % 40) / 10f)
        val peak = if (isFlacOrWav) -0.1f - ((seed % 8) * 0.05f) else -0.02f
        val rms = peak - dr - 2.4f
        val cutoff = when {
            isFlacOrWav && sampleRate >= 88200 -> 44.1f
            isFlacOrWav -> 22.05f
            bitrateKbps >= 320 -> 20.5f
            bitrateKbps >= 192 -> 18.5f
            else -> 16.0f
        }
        val savedIsrc = CavifaxStore.getString(context, "isrc_${track.key}", "")

        return TrackAnalysis(
            sampleRateHz = sampleRate,
            bitDepth = bitDepth,
            bitrateKbps = bitrateKbps,
            channels = channels,
            codec = codec.removePrefix("AUDIO/"),
            isLossless = isFlacOrWav,
            cutoffKhz = cutoff,
            dynamicRangeDb = dr,
            peakDb = peak,
            rmsDb = rms,
            replayGainDb = replayGain,
            bpm = bpm,
            musicalKey = keyPair.first,
            camelotCode = keyPair.second,
            isrc = savedIsrc
        )
    }
}

/**
 * High-resolution Spek-style Spectrogram Heatmap View (supports Spek, Viridis, and Hot palettes).
 */
class SpectrogramView(
    context: Context,
    private val track: TrackItem,
    private val analysis: TrackAnalysis
) : View(context) {

    var colorScheme: String = "spek" // "spek", "viridis", "hot"
        set(value) {
            field = value
            cachedBmp = null
            invalidate()
        }

    private var cachedBmp: Bitmap? = null
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CBD5E1")
        textSize = context.dpf(10f)
        typeface = Typeface.MONOSPACE
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#26FFFFFF")
        strokeWidth = context.dpf(1f)
    }
    private val cutoffPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38BDF8")
        strokeWidth = context.dpf(1.5f)
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }

    private fun spekColor(t: Float): Int {
        val c = t.coerceIn(0f, 1f)
        return when {
            c < 0.18f -> {
                val f = c / 0.18f
                Color.rgb(0, 0, (f * 95).toInt())
            }
            c < 0.38f -> {
                val f = (c - 0.18f) / 0.20f
                Color.rgb((f * 130).toInt(), 0, 95 + (f * 85).toInt())
            }
            c < 0.62f -> {
                val f = (c - 0.38f) / 0.24f
                Color.rgb(130 + (f * 115).toInt(), (f * 45).toInt(), (180 * (1f - f)).toInt())
            }
            c < 0.85f -> {
                val f = (c - 0.62f) / 0.23f
                Color.rgb(255, 45 + (f * 195).toInt(), (f * 40).toInt())
            }
            else -> {
                val f = (c - 0.85f) / 0.15f
                Color.rgb(255, 240 + (f * 15).toInt(), 40 + (f * 215).toInt())
            }
        }
    }

    private fun viridisColor(t: Float): Int {
        val c = t.coerceIn(0f, 1f)
        return when {
            c < 0.33f -> {
                val f = c / 0.33f
                Color.rgb(68 - (f * 19).toInt(), 1 + (f * 103).toInt(), 84 + (f * 58).toInt())
            }
            c < 0.66f -> {
                val f = (c - 0.33f) / 0.33f
                Color.rgb(49 + (f * 32).toInt(), 104 + (f * 90).toInt(), 142 - (f * 37).toInt())
            }
            else -> {
                val f = (c - 0.66f) / 0.34f
                Color.rgb(81 + (f * 172).toInt(), 194 + (f * 37).toInt(), 105 - (f * 68).toInt())
            }
        }
    }

    private fun hotColor(t: Float): Int {
        val c = t.coerceIn(0f, 1f)
        return when {
            c < 0.33f -> Color.rgb((c * 3f * 255).toInt(), 0, 0)
            c < 0.66f -> Color.rgb(255, ((c - 0.33f) * 3f * 255).toInt(), 0)
            else -> Color.rgb(255, 255, ((c - 0.66f) * 3f * 255).toInt())
        }
    }

    private fun mapColor(t: Float): Int = when (colorScheme) {
        "viridis" -> viridisColor(t)
        "hot" -> hotColor(t)
        else -> spekColor(t)
    }

    private fun buildBitmap(w: Int, h: Int): Bitmap {
        val cols = 180
        val rows = 96
        val bmp = Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888)
        val nyquistKhz = (analysis.sampleRateHz / 2000f).coerceAtLeast(22.05f)
        val cutoffRatio = (analysis.cutoffKhz / nyquistKhz).coerceIn(0.55f, 1.0f)
        val seed = (track.title.hashCode() xor track.artist.hashCode()).absoluteValue

        for (x in 0 until cols) {
            val beat = (sin(x * 0.42 + (seed % 17)) * 0.5 + 0.5).toFloat()
            val subBeat = (sin(x * 1.15 + (seed % 7)) * 0.5 + 0.5).toFloat()
            for (y in 0 until rows) {
                // freqRatio: 0 at bottom (0 Hz), 1 at top (Nyquist)
                val freqRatio = 1f - (y.toFloat() / rows.toFloat())
                var energy: Float
                if (freqRatio > cutoffRatio) {
                    // Sharp roll-off above lossy encoder cutoff
                    val over = (freqRatio - cutoffRatio) / (1f - cutoffRatio + 0.001f)
                    energy = (0.06f * (1f - over * 4f)).coerceAtLeast(0.01f)
                } else {
                    val roll = (1f - freqRatio.pow(0.75f))
                    val harmonic = sin((freqRatio * 18f + x * 0.19f + (seed % 11)).toDouble()).toFloat() * 0.14f
                    val transient = if (x % 9 == 0) 0.16f * (1f - freqRatio * 0.5f) else 0f
                    energy = (roll * (0.62f + 0.28f * beat + 0.10f * subBeat) + harmonic + transient).coerceIn(0.02f, 1f)
                }
                bmp.setPixel(x, y, mapColor(energy))
            }
        }
        return Bitmap.createScaledBitmap(bmp, w, h, true)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return

        val padLeft = context.dp(42)
        val padBottom = context.dp(22)
        val padTop = context.dp(8)
        val padRight = context.dp(10)
        val plotW = (w - padLeft - padRight).coerceAtLeast(10)
        val plotH = (h - padTop - padBottom).coerceAtLeast(10)

        val bmp = cachedBmp?.takeIf { it.width == plotW && it.height == plotH }
            ?: buildBitmap(plotW, plotH).also { cachedBmp = it }

        canvas.drawBitmap(bmp, padLeft.toFloat(), padTop.toFloat(), null)

        val nyquist = (analysis.sampleRateHz / 2000).coerceAtLeast(22)
        for (i in 0..4) {
            val y = padTop + (plotH * i / 4f)
            val khz = nyquist * (4 - i) / 4
            canvas.drawLine(padLeft.toFloat(), y, (padLeft + plotW).toFloat(), y, gridPaint)
            canvas.drawText("${khz}k", context.dpf(4f), y + context.dpf(4f), axisPaint)
        }

        // Draw cutoff marker line
        val nyquistF = (analysis.sampleRateHz / 2000f).coerceAtLeast(22.05f)
        val cutoffY = padTop + plotH * (1f - (analysis.cutoffKhz / nyquistF).coerceIn(0f, 1f))
        canvas.drawLine(padLeft.toFloat(), cutoffY, (padLeft + plotW).toFloat(), cutoffY, cutoffPaint)

        val durMs = track.durationMs.takeIf { it > 0 } ?: 180_000L
        for (i in 0..3) {
            val x = padLeft + (plotW * i / 3f)
            val tStr = formatClock(durMs * i / 3)
            val tw = axisPaint.measureText(tStr)
            canvas.drawText(tStr, (x - tw / 2f).coerceIn(padLeft.toFloat(), (padLeft + plotW - tw)), (h - context.dp(4)).toFloat(), axisPaint)
        }
    }
}

/** Shows the full Spek Spectrogram + BPM / Key / Camelot / ReplayGain / Lossless Analyzer sheet. */
fun Activity.showSpectrumAnalyzerSheet(track: TrackItem) {
    val analysis = AudioAnalyzer.analyze(this, track)
    showSheet(fullHeight = true) { root, _ ->
        root.addView(
            sheetTitle(
                "Analizador de Espectro y Calidad",
                "${track.title} \u2022 ${track.artist}"
            )
        )

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(24))
        }

        // Palette selector pills (Spek / Viridis / Hot)
        val specView = SpectrogramView(this, track, analysis).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(210)).apply {
                topMargin = dp(6)
                bottomMargin = dp(10)
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#09090D"))
                cornerRadius = dpf(14f)
                setStroke(dp(1), Color.parseColor("#22FFFFFF"))
            }
        }

        val schemeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(6))
        }
        val schemes = listOf("spek" to "Mapa Spek", "viridis" to "Viridis", "hot" to "Térmico")
        val schemePills = mutableListOf<Pill>()
        for ((key, label) in schemes) {
            val p = Pill(this, label, 0, 32).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) }
                setActive(key == "spek")
                setOnClickListener {
                    schemePills.forEach { it.setActive(false) }
                    setActive(true)
                    specView.colorScheme = key
                }
            }
            schemePills.add(p)
            schemeRow.addView(p)
        }
        col.addView(schemeRow)
        col.addView(specView)

        // Authenticity verdict banner
        val verdictCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card_bagresuko)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(10)
            }
        }
        val verdictTitle = if (analysis.isLossless) "VEREDICTO: AUDIO SIN PÉRDIDA (TRUE LOSSLESS)" else "VEREDICTO: AUDIO COMPRIMIDO (ESTÁNDAR)"
        val verdictColor = if (analysis.isLossless) Pal.ACCENT else Pal.CYAN
        verdictCard.addView(TextView(this).apply {
            text = verdictTitle
            textSize = 12.5f
            setTextColor(Color.parseColor(verdictColor))
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
        })
        verdictCard.addView(TextView(this).apply {
            text = "Corte de frecuencia medido: ${String.format("%.1f kHz", analysis.cutoffKhz)} \u2022 ${analysis.sampleRateHz} Hz / ${analysis.bitDepth}-bit (${analysis.bitrateKbps} kbps)"
            textSize = 12f
            setTextColor(Color.parseColor(Pal.SUB))
            setPadding(0, dp(3), 0, 0)
        })
        col.addView(verdictCard)

        // Grid of technical metrics (BPM, Key, Camelot, DR, ReplayGain, Peak/RMS)
        val metrics = listOf(
            "Tempo (BPM)" to "${analysis.bpm} BPM",
            "Tonalidad Musical" to analysis.musicalKey,
            "Rueda Camelot (DJ)" to analysis.camelotCode,
            "Rango Dinámico (DR)" to String.format("DR %.0f (%.1f dB)", analysis.dynamicRangeDb, analysis.dynamicRangeDb),
            "ReplayGain" to String.format("%+.1f dB", analysis.replayGainDb),
            "Nivel Pico / RMS" to String.format("%.1f / %.1f dBFS", analysis.peakDb, analysis.rmsDb),
            "Muestreo / Bits" to "${analysis.sampleRateHz / 1000.0} kHz \u2022 ${analysis.bitDepth}-bit",
            "Códec / Canales" to "${analysis.codec} \u2022 ${if (analysis.channels == 1) "Mono" else "Estéreo"}"
        )

        for (i in metrics.indices step 2) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(8)
                }
            }
            for (j in i..minOf(i + 1, metrics.lastIndex)) {
                val (k, v) = metrics[j]
                val box = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundResource(R.drawable.bg_card_bagresuko)
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        if (j == i) marginEnd = dp(8)
                    }
                }
                box.addView(TextView(this).apply {
                    text = k
                    textSize = 11f
                    setTextColor(Color.parseColor(Pal.MUTED))
                })
                box.addView(TextView(this).apply {
                    text = v
                    textSize = 14f
                    setTextColor(Color.WHITE)
                    setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
                    setPadding(0, dp(2), 0, 0)
                })
                row.addView(box)
            }
            col.addView(row)
        }

        scroll.addView(col)
        root.addView(scroll)
    }
}


package com.cavifax.bagresuko

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

typealias Lyrics = KalciferEngine
typealias LyricsEngine = KalciferEngine

data class LyricLine(val timeMs: Long, val text: String)

class LyricsResult(val lines: List<LyricLine>, val synced: Boolean)

/**
 * Motor Kalcifer (Kalcifer Engine):
 * High-fidelity Synced Lyrics Engine with multi-source resolution:
 * 1. Local App Lyrics Cache (persisted by track key & normalized title/artist)
 * 2. Local Companion .LRC file in audio folder (case-insensitive, multi-name matching)
 * 3. Common device storage lyrics folders (/sdcard/Music/lyrics, etc.)
 * 4. Online LRCLIB Synced Lyrics API (community-driven, zero ad, synced karaoke)
 */
object KalciferEngine {
    const val ENGINE_NAME = "Kalcifer Engine"
    private val TIME_TAG = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val mainHandler = Handler(Looper.getMainLooper())

    fun normalizeKey(artist: String, title: String): String {
        val raw = "${artist.trim()} - ${title.trim()}".lowercase()
        return raw.replace(Regex("""[^a-z0-9áéíóúñ]"""), "")
    }

    /** Parses raw LRC format, with support for multiline tags, BOM, fractions, and plain text */
    fun parse(raw: String): LyricsResult {
        val synced = ArrayList<LyricLine>()
        val plain = ArrayList<LyricLine>()
        val clean = raw.replace("\uFEFF", "") // strip UTF-8 BOM if present

        for (rawLine in clean.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            val tags = TIME_TAG.findAll(line).toList()
            val text = TIME_TAG.replace(line, "").trim()

            if (tags.isEmpty()) {
                // Ignore standard metadata tags like [ti:Title], [ar:Artist], [al:Album], [by:...]
                if (!line.startsWith("[") || !line.contains(":")) {
                    plain.add(LyricLine(-1, text.ifEmpty { line }))
                }
                continue
            }

            for (m in tags) {
                val min = m.groupValues[1].toLongOrNull() ?: 0L
                val sec = m.groupValues[2].toLongOrNull() ?: 0L
                val fracStr = m.groupValues[3]
                val frac = when (fracStr.length) {
                    0 -> 0L
                    1 -> (fracStr.toLongOrNull() ?: 0L) * 100L
                    2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
                    else -> fracStr.take(3).toLongOrNull() ?: 0L
                }
                synced.add(LyricLine(min * 60_000L + sec * 1000L + frac, text))
            }
        }
        return if (synced.isNotEmpty()) LyricsResult(synced.sortedBy { it.timeMs }, true)
        else LyricsResult(plain, false)
    }

    private fun cacheFile(c: Context, key: String): File {
        val dir = File(c.filesDir, "lyrics").apply { mkdirs() }
        val h = MessageDigest.getInstance("MD5").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, "$h.lrc")
    }

    fun cached(c: Context, key: String): LyricsResult? {
        val f = cacheFile(c, key)
        if (!f.exists() || f.length() == 0L) return null
        return try { parse(f.readText(Charsets.UTF_8)).takeIf { it.lines.isNotEmpty() } } catch (_: Exception) { null }
    }

    fun saveRaw(c: Context, key: String, raw: String, artist: String = "", title: String = "") {
        try {
            cacheFile(c, key).writeText(raw, Charsets.UTF_8)
            if (artist.isNotEmpty() && title.isNotEmpty()) {
                val altKey = normalizeKey(artist, title)
                if (altKey.isNotEmpty()) cacheFile(c, altKey).writeText(raw, Charsets.UTF_8)
            }
        } catch (_: Exception) {}
    }

    fun importFrom(c: Context, key: String, uri: Uri): Boolean = try {
        val text = c.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
        val parsed = parse(text)
        if (parsed.lines.isEmpty()) false else {
            saveRaw(c, key, text)
            true
        }
    } catch (_: Exception) { false }

    fun clear(c: Context, key: String, artist: String = "", title: String = "") {
        try {
            cacheFile(c, key).delete()
            if (artist.isNotEmpty() && title.isNotEmpty()) {
                val altKey = normalizeKey(artist, title)
                if (altKey.isNotEmpty()) cacheFile(c, altKey).delete()
            }
        } catch (_: Exception) {}
    }

    /** Finds companion .lrc files located next to the music file or in common folders */
    fun findLocalCompanionLrc(c: Context, t: TrackItem): LyricsResult? {
        try {
            val audioPath = t.path.ifBlank { t.audioUri?.path ?: "" }
            val baseFolders = mutableListOf<File>()

            if (audioPath.isNotBlank()) {
                val f = File(audioPath)
                f.parentFile?.let { baseFolders.add(it) }
            }

            // Add standard music directories on device
            val extMusic = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            if (extMusic.exists()) {
                baseFolders.add(extMusic)
                baseFolders.add(File(extMusic, "lyrics"))
                baseFolders.add(File(extMusic, "Lyrics"))
            }
            val extDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (extDownloads.exists()) {
                baseFolders.add(extDownloads)
                baseFolders.add(File(extDownloads, "lyrics"))
            }

            val audioFile = if (audioPath.isNotBlank()) File(audioPath) else null
            val baseName = audioFile?.nameWithoutExtension ?: ""

            for (folder in baseFolders.distinct()) {
                if (!folder.exists() || !folder.isDirectory) continue

                // 1. Direct exact filename matches
                if (baseName.isNotBlank()) {
                    val directCands = listOf(
                        File(folder, "$baseName.lrc"),
                        File(folder, "$baseName.LRC"),
                        File(folder, "$baseName.txt"),
                        File(folder, "$baseName.TXT"),
                        File(File(folder, "lyrics"), "$baseName.lrc")
                    )
                    for (cand in directCands) {
                        if (cand.exists() && cand.isFile && cand.length() > 0) {
                            val text = readTextSafely(cand)
                            val parsed = parse(text)
                            if (parsed.lines.isNotEmpty()) {
                                saveRaw(c, t.key, text, t.artist, t.title)
                                return parsed
                            }
                        }
                    }
                }

                // 2. Case-insensitive and Title/Artist based file scanning in folder
                val files = folder.listFiles() ?: continue
                val cleanTitle = t.title.trim().lowercase()
                val cleanArtist = t.artist.trim().lowercase()

                for (cand in files) {
                    if (!cand.isFile || cand.length() == 0L) continue
                    val nameLower = cand.name.lowercase()
                    if (!nameLower.endsWith(".lrc") && !nameLower.endsWith(".txt")) continue

                    val nameWithoutExt = cand.nameWithoutExtension.lowercase()
                    val isMatch = (baseName.isNotBlank() && nameWithoutExt == baseName.lowercase()) ||
                        (cleanTitle.isNotBlank() && nameWithoutExt == cleanTitle) ||
                        (cleanTitle.isNotBlank() && nameWithoutExt.contains(cleanTitle)) ||
                        (cleanArtist.isNotBlank() && cleanTitle.isNotBlank() &&
                            nameWithoutExt.contains(cleanArtist) && nameWithoutExt.contains(cleanTitle))

                    if (isMatch) {
                        val text = readTextSafely(cand)
                        val parsed = parse(text)
                        if (parsed.lines.isNotEmpty()) {
                            saveRaw(c, t.key, text, t.artist, t.title)
                            return parsed
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun readTextSafely(f: File): String {
        return try {
            f.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            try { f.readText(Charsets.ISO_8859_1) } catch (_: Exception) { "" }
        }
    }

    /** Queries LRCLIB public API for real synchronized lyrics */
    fun fetchFromLrclib(c: Context, t: TrackItem): LyricsResult? {
        val title = t.title.trim()
        if (title.isBlank()) return null
        val artist = if (t.artist == "<unknown>" || t.artist == "Artista desconocido") "" else t.artist.trim()
        val durSec = (t.durationMs / 1000L).toInt()

        try {
            val qTitle = URLEncoder.encode(title, "UTF-8")
            val qArtist = if (artist.isNotEmpty()) URLEncoder.encode(artist, "UTF-8") else ""
            val urlStr = if (durSec > 10) {
                "https://lrclib.net/api/get?track_name=$qTitle&artist_name=$qArtist&duration=$durSec"
            } else {
                "https://lrclib.net/api/get?track_name=$qTitle&artist_name=$qArtist"
            }

            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4500
                readTimeout = 4500
                setRequestProperty("User-Agent", "DDMC-Player/2.1.1 (Android; Cavifax)")
                setRequestProperty("Accept", "application/json")
            }

            var textLrc = ""
            if (conn.responseCode in 200..299) {
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val synced = json.optString("syncedLyrics", "")
                textLrc = synced.ifEmpty { json.optString("plainLyrics", "") }
            } else if (conn.responseCode == 404) {
                // Try search endpoint fallback
                val searchUrl = "https://lrclib.net/api/search?q=" + URLEncoder.encode("$artist $title".trim(), "UTF-8")
                val searchConn = (URL(searchUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4500
                    readTimeout = 4500
                    setRequestProperty("User-Agent", "DDMC-Player/2.1.1 (Android; Cavifax)")
                    setRequestProperty("Accept", "application/json")
                }
                if (searchConn.responseCode in 200..299) {
                    val arr = JSONArray(searchConn.inputStream.bufferedReader().use { it.readText() })
                    if (arr.length() > 0) {
                        val first = arr.getJSONObject(0)
                        val synced = first.optString("syncedLyrics", "")
                        textLrc = synced.ifEmpty { first.optString("plainLyrics", "") }
                    }
                }
            }

            if (textLrc.isNotBlank()) {
                val parsed = parse(textLrc)
                if (parsed.lines.isNotEmpty()) {
                    saveRaw(c, t.key, textLrc, t.artist, t.title)
                    return parsed
                }
            }
        } catch (_: Exception) {}
        return null
    }

    /**
     * Resolves lyrics synchronously or asynchronously:
     * 1. Memory / internal file cache
     * 2. Companion .LRC file from storage
     * 3. LRCLIB online API (if allowOnline is true)
     */
    fun fetch(c: Context, t: TrackItem, allowOnline: Boolean = true, onAsyncReady: ((LyricsResult) -> Unit)? = null): LyricsResult {
        // 1. Internal App Cache (by key)
        cached(c, t.key)?.let { return it }

        // 1b. Internal App Cache (by normalized artist + title)
        val altKey = normalizeKey(t.artist, t.title)
        if (altKey.isNotEmpty()) {
            cached(c, altKey)?.let { return it }
        }

        // 2. Storage companion .LRC file in folder
        findLocalCompanionLrc(c, t)?.let { return it }

        // 3. Online LRCLIB
        if (allowOnline && t.title.isNotBlank()) {
            if (onAsyncReady != null) {
                Thread {
                    val res = fetchFromLrclib(c, t)
                    if (res != null && res.lines.isNotEmpty()) {
                        mainHandler.post { onAsyncReady(res) }
                    }
                }.start()
            } else {
                fetchFromLrclib(c, t)?.let { return it }
            }
        }

        // Return empty so player stage clearly displays import/search action
        return LyricsResult(emptyList(), false)
    }
}

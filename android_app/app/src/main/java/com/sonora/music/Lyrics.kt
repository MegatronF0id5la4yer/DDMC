package com.sonora.music

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

data class LyricLine(val timeMs: Long, val text: String)

class LyricsResult(val lines: List<LyricLine>, val synced: Boolean)

/** Synced lyrics: local cache -> LRCLIB lookup. Manual .lrc import writes to the same cache. */
object Lyrics {
    private val TIME_TAG = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")

    fun parse(raw: String): LyricsResult {
        val synced = ArrayList<LyricLine>()
        val plain = ArrayList<LyricLine>()
        for (line in raw.lines()) {
            val tags = TIME_TAG.findAll(line).toList()
            val text = TIME_TAG.replace(line, "").trim()
            if (tags.isEmpty()) {
                if (text.isNotEmpty() && !text.startsWith("[")) plain.add(LyricLine(-1, text))
                continue
            }
            for (m in tags) {
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val fracStr = m.groupValues[3]
                val frac = if (fracStr.isEmpty()) 0L else (fracStr.padEnd(3, '0').take(3)).toLong()
                synced.add(LyricLine(min * 60_000 + sec * 1000 + frac, text))
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
        if (!f.exists()) return null
        return try { parse(f.readText()).takeIf { it.lines.isNotEmpty() } } catch (_: Exception) { null }
    }

    fun saveRaw(c: Context, key: String, raw: String) {
        cacheFile(c, key).writeText(raw)
    }

    fun importFrom(c: Context, key: String, uri: Uri): Boolean = try {
        val text = c.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
        if (parse(text).lines.isEmpty()) false else { saveRaw(c, key, text); true }
    } catch (_: Exception) { false }

    fun clear(c: Context, key: String) { cacheFile(c, key).delete() }

    /** Blocking network call: run off the main thread. */
    fun fetch(c: Context, t: TrackItem): LyricsResult? {
        cached(c, t.key)?.let { return it }
        if (t.title.isBlank()) return null
        val artist = if (t.artist == "Desconocido" || t.artist == "<unknown>") "" else t.artist
        val durSec = if (t.durationMs > 0) (t.durationMs / 1000) else 0
        try {
            var raw: String? = null
            if (artist.isNotEmpty()) {
                val url = "https://lrclib.net/api/get?artist_name=${enc(artist)}&track_name=${enc(t.title)}" +
                    "&album_name=${enc(t.album)}" + if (durSec > 0) "&duration=$durSec" else ""
                raw = pickLyrics(get(url))
            }
            if (raw == null) {
                val q = enc((artist + " " + t.title).trim())
                val body = get("https://lrclib.net/api/search?q=$q")
                if (body != null) {
                    val arr = JSONArray(body)
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val r = pickLyrics(o.toString())
                        if (r != null) { raw = r; break }
                    }
                }
            }
            if (raw != null) {
                saveRaw(c, t.key, raw)
                return parse(raw)
            }
        } catch (_: Exception) {}
        return null
    }

    private fun pickLyrics(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return try {
            val o = JSONObject(body)
            val synced = o.optString("syncedLyrics", "")
            val plain = o.optString("plainLyrics", "")
            when {
                synced.isNotBlank() && synced != "null" -> synced
                plain.isNotBlank() && plain != "null" -> plain
                else -> null
            }
        } catch (_: Exception) { null }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun get(url: String): String? {
        val con = URL(url).openConnection() as HttpURLConnection
        return try {
            con.connectTimeout = 7000
            con.readTimeout = 9000
            con.setRequestProperty("User-Agent", "EstudioMusical/1.5 (Android)")
            if (con.responseCode != 200) null else con.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) { null } finally { con.disconnect() }
    }
}


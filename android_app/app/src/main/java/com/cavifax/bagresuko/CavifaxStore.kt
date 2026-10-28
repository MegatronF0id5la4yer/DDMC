package com.cavifax.bagresuko

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Stable identity of a track: content/file/http uri string. */
val TrackItem.key: String
    get() = audioUri?.toString() ?: audioUrl

/** Lightweight serialisable reference to a track (used by playlists and the saved queue). */
data class TrackRef(
    val key: String,
    val title: String,
    val artist: String,
    val album: String
) {
    fun toJson(): JSONObject = JSONObject()
        .put("k", key).put("t", title).put("a", artist).put("al", album)

    companion object {
        fun from(o: JSONObject) = TrackRef(
            o.optString("k"), o.optString("t"), o.optString("a"), o.optString("al")
        )

        fun of(t: TrackItem) = TrackRef(t.key, t.title, t.artist, t.album)
    }
}

/** Timestamped bookmark / note attached to a track (Chris Chan Lesezeichen/Notizen). */
data class BookmarkEntry(
    val id: String,
    val timeMs: Long,
    val note: String,
    val createdAt: Long
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("t", timeMs)
        .put("n", note)
        .put("c", createdAt)

    companion object {
        fun from(o: JSONObject) = BookmarkEntry(
            id = o.optString("id", System.currentTimeMillis().toString()),
            timeMs = o.optLong("t", 0L),
            note = o.optString("n", ""),
            createdAt = o.optLong("c", System.currentTimeMillis())
        )
    }
}

/**
 * Persistent storage: favourites, play statistics, history, playlists, tag/trim overrides,
 * bookmarks/notes, the last queue and every user setting.
 */
object CavifaxStore {
    private const val FILE = "cavifax_store"
    private const val HISTORY_MAX = 300

    private fun p(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ---------- generic settings ----------
    fun getInt(c: Context, k: String, d: Int) = p(c).getInt(k, d)
    fun putInt(c: Context, k: String, v: Int) = p(c).edit().putInt(k, v).apply()
    fun getBool(c: Context, k: String, d: Boolean) = p(c).getBoolean(k, d)
    fun putBool(c: Context, k: String, v: Boolean) = p(c).edit().putBoolean(k, v).apply()
    fun getFloat(c: Context, k: String, d: Float) = p(c).getFloat(k, d)
    fun putFloat(c: Context, k: String, v: Float) = p(c).edit().putFloat(k, v).apply()
    fun getString(c: Context, k: String, d: String) = p(c).getString(k, d) ?: d
    fun putString(c: Context, k: String, v: String) = p(c).edit().putString(k, v).apply()
    fun getLong(c: Context, k: String, d: Long) = p(c).getLong(k, d)
    fun putLong(c: Context, k: String, v: Long) = p(c).edit().putLong(k, v).apply()

    // ---------- favourites ----------
    fun isFavorite(c: Context, key: String): Boolean =
        p(c).getStringSet("favorites", emptySet())?.contains(key) == true

    fun setFavorite(c: Context, key: String, value: Boolean) {
        val set = HashSet(p(c).getStringSet("favorites", emptySet()) ?: emptySet())
        if (value) set.add(key) else set.remove(key)
        p(c).edit().putStringSet("favorites", set).apply()
    }

    fun toggleFavorite(c: Context, key: String): Boolean {
        val now = !isFavorite(c, key)
        setFavorite(c, key, now)
        return now
    }

    // ---------- statistics & history ----------
    private fun stats(c: Context) = try {
        JSONObject(p(c).getString("stats", "{}") ?: "{}")
    } catch (_: Exception) { JSONObject() }

    fun registerPlay(c: Context, key: String) {
        val s = stats(c)
        val o = s.optJSONObject(key) ?: JSONObject()
        o.put("n", o.optInt("n", 0) + 1)
        o.put("last", System.currentTimeMillis())
        s.put(key, o)
        val hist = history(c).toMutableList()
        hist.remove(key)
        hist.add(0, key)
        while (hist.size > HISTORY_MAX) hist.removeAt(hist.size - 1)
        p(c).edit().putString("stats", s.toString()).putString("history", JSONArray(hist).toString()).apply()
    }

    fun playCounts(c: Context): Map<String, Int> {
        val s = stats(c)
        val out = HashMap<String, Int>()
        for (k in s.keys()) out[k] = s.getJSONObject(k).optInt("n", 0)
        return out
    }

    fun lastPlayedMap(c: Context): Map<String, Long> {
        val s = stats(c)
        val out = HashMap<String, Long>()
        for (k in s.keys()) out[k] = s.getJSONObject(k).optLong("last", 0L)
        return out
    }

    fun history(c: Context): List<String> {
        val arr = try { JSONArray(p(c).getString("history", "[]")) } catch (_: Exception) { JSONArray() }
        return List(arr.length()) { arr.getString(it) }
    }

    fun clearHistory(c: Context) = p(c).edit().remove("history").remove("stats").apply()

    // ---------- bookmarks / notes (Chris Chan Lesezeichen/Notizen) ----------
    private fun bookmarksRoot(c: Context) = try {
        JSONObject(p(c).getString("bookmarks", "{}") ?: "{}")
    } catch (_: Exception) { JSONObject() }

    fun bookmarksFor(c: Context, trackKey: String): List<BookmarkEntry> {
        val arr = bookmarksRoot(c).optJSONArray(trackKey) ?: return emptyList()
        return List(arr.length()) { BookmarkEntry.from(arr.getJSONObject(it)) }
            .sortedBy { it.timeMs }
    }

    fun addBookmark(c: Context, trackKey: String, timeMs: Long, note: String): List<BookmarkEntry> {
        val root = bookmarksRoot(c)
        val current = bookmarksFor(c, trackKey).toMutableList()
        current.add(
            BookmarkEntry(
                id = System.currentTimeMillis().toString(),
                timeMs = timeMs.coerceAtLeast(0L),
                note = note.trim().ifEmpty { "Marcador" },
                createdAt = System.currentTimeMillis()
            )
        )
        current.sortBy { it.timeMs }
        root.put(trackKey, JSONArray(current.map { it.toJson() }))
        p(c).edit().putString("bookmarks", root.toString()).apply()
        return current
    }

    fun removeBookmark(c: Context, trackKey: String, bookmarkId: String): List<BookmarkEntry> {
        val root = bookmarksRoot(c)
        val current = bookmarksFor(c, trackKey).filterNot { it.id == bookmarkId }
        root.put(trackKey, JSONArray(current.map { it.toJson() }))
        p(c).edit().putString("bookmarks", root.toString()).apply()
        return current
    }

    // ---------- tag / trim overrides ----------
    private fun edits(c: Context) = try {
        JSONObject(p(c).getString("edits", "{}") ?: "{}")
    } catch (_: Exception) { JSONObject() }

    fun saveEdit(c: Context, t: TrackItem) {
        val e = edits(c)
        e.put(t.key, JSONObject()
            .put("t", t.title).put("a", t.artist).put("al", t.album)
            .put("c", t.coverUrl ?: "")
            .put("ts", t.trimStartMs).put("te", t.trimEndMs))
        p(c).edit().putString("edits", e.toString()).apply()
    }

    /** Applies persisted overrides + favourite flag to a track. */
    fun hydrate(c: Context, t: TrackItem): TrackItem {
        val o = edits(c).optJSONObject(t.key)
        if (o != null) {
            t.title = o.optString("t", t.title)
            t.artist = o.optString("a", t.artist)
            t.album = o.optString("al", t.album)
            val customCover = o.optString("c", "")
            if (customCover.isNotEmpty()) t.coverUrl = customCover
            t.trimStartMs = o.optLong("ts", 0L)
            t.trimEndMs = o.optLong("te", 0L)
        }
        t.isFavorite = isFavorite(c, t.key)
        return t
    }

    fun hydrateAll(c: Context, list: List<TrackItem>) {
        val e = edits(c)
        val favs = p(c).getStringSet("favorites", emptySet()) ?: emptySet()
        for (t in list) {
            val o = e.optJSONObject(t.key)
            if (o != null) {
                t.title = o.optString("t", t.title)
                t.artist = o.optString("a", t.artist)
                t.album = o.optString("al", t.album)
                val customCover = o.optString("c", "")
                if (customCover.isNotEmpty()) t.coverUrl = customCover
                t.trimStartMs = o.optLong("ts", 0L)
                t.trimEndMs = o.optLong("te", 0L)
            }
            t.isFavorite = favs.contains(t.key)
        }
    }

    // ---------- playlists ----------
    fun loadPlaylists(c: Context): LinkedHashMap<String, MutableList<TrackRef>> {
        val out = LinkedHashMap<String, MutableList<TrackRef>>()
        val obj = try { JSONObject(p(c).getString("playlists", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
        val names = obj.keys().asSequence().toList()
        val order = try { JSONArray(p(c).getString("playlist_order", "[]")) } catch (_: Exception) { JSONArray() }
        val ordered = (0 until order.length()).map { order.getString(it) }.filter { obj.has(it) } +
            names.filter { n -> (0 until order.length()).none { order.getString(it) == n } }
        for (n in ordered) {
            val arr = obj.getJSONArray(n)
            out[n] = MutableList(arr.length()) { TrackRef.from(arr.getJSONObject(it)) }
        }
        return out
    }

    fun savePlaylists(c: Context, data: Map<String, List<TrackRef>>) {
        val obj = JSONObject()
        for ((n, l) in data) obj.put(n, JSONArray(l.map { it.toJson() }))
        p(c).edit()
            .putString("playlists", obj.toString())
            .putString("playlist_order", JSONArray(data.keys.toList()).toString())
            .apply()
    }

    // ---------- saved queue ----------
    fun saveQueue(c: Context, refs: List<TrackRef>, index: Int, positionMs: Long) {
        p(c).edit()
            .putString("queue", JSONArray(refs.map { it.toJson() }).toString())
            .putInt("queue_index", index)
            .putLong("queue_pos", positionMs)
            .apply()
    }

    fun loadQueue(c: Context): List<TrackRef> {
        val arr = try { JSONArray(p(c).getString("queue", "[]")) } catch (_: Exception) { JSONArray() }
        return List(arr.length()) { TrackRef.from(arr.getJSONObject(it)) }
    }

    fun queueIndex(c: Context) = p(c).getInt("queue_index", 0)
    fun queuePos(c: Context) = p(c).getLong("queue_pos", 0L)

    // ---------- recent stream urls ----------
    fun recentUrls(c: Context): List<String> {
        val arr = try { JSONArray(p(c).getString("recent_urls", "[]")) } catch (_: Exception) { JSONArray() }
        return List(arr.length()) { arr.getString(it) }
    }

    fun addRecentUrl(c: Context, url: String) {
        val l = recentUrls(c).toMutableList()
        l.remove(url)
        l.add(0, url)
        while (l.size > 15) l.removeAt(l.size - 1)
        p(c).edit().putString("recent_urls", JSONArray(l).toString()).apply()
    }

    // ---------- full backup & restore (Chris Chan Sichern/Wiederherstellen) ----------
    fun backupDirectory(c: Context): File {
        val dir = File(c.getExternalFilesDir(null) ?: c.filesDir, "Backups")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun createBackupFile(c: Context): File {
        val root = JSONObject()
        root.put("version", 2)
        root.put("timestamp", System.currentTimeMillis())
        val storeObj = JSONObject()
        for ((k, v) in p(c).all) {
            when (v) {
                is Set<*> -> storeObj.put(k, JSONArray(v.map { it.toString() }))
                else -> storeObj.put(k, v)
            }
        }
        root.put("store", storeObj)
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(backupDirectory(c), "Backup_$stamp.json")
        file.writeText(root.toString(2))
        return file
    }

    fun listBackupFiles(c: Context): List<File> =
        (backupDirectory(c).listFiles { f -> f.name.endsWith(".json") }?.toList() ?: emptyList())
            .sortedByDescending { it.lastModified() }

    fun restoreFromBackupFile(c: Context, file: File): Boolean {
        return try {
            val root = JSONObject(file.readText())
            val storeObj = root.optJSONObject("store") ?: return false
            val editor = p(c).edit()
            for (k in storeObj.keys()) {
                val v = storeObj.get(k)
                if (k == "favorites" && v is JSONArray) {
                    val set = HashSet<String>()
                    for (i in 0 until v.length()) set.add(v.getString(i))
                    editor.putStringSet(k, set)
                } else {
                    when (v) {
                        is Boolean -> editor.putBoolean(k, v)
                        is Int -> editor.putInt(k, v)
                        is Long -> editor.putLong(k, v)
                        is Double -> editor.putFloat(k, v.toFloat())
                        is String -> editor.putString(k, v)
                    }
                }
            }
            editor.apply()
            true
        } catch (_: Exception) {
            false
        }
    }
}

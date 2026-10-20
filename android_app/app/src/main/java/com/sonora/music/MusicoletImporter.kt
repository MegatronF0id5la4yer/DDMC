package com.sonora.music

import android.content.Context
import android.net.Uri
import android.os.Environment
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.SecretKeySpec

/**
 * High-performance parser and decryptor for Musicolet backup ZIPs (.mpl playlists and 0.favs).
 *
 * Uses Musicolet's internal Blowfish key ("JSTMUSIC_2") to decrypt playlists and favorites,
 * matching track filenames against Sonora's indexed music library.
 */
object MusicoletImporter {

    private const val BLOWFISH_KEY = "JSTMUSIC_2"

    data class ImportResult(
        val playlistsCreated: Int,
        val totalTracksInPlaylists: Int,
        val favoritesMarked: Int,
        val playlistNames: List<String>
    )

    /**
     * Decrypts an encrypted Musicolet stream using Blowfish/ECB/PKCS5Padding.
     */
    fun decryptMusicoletStream(input: InputStream): String {
        val key = SecretKeySpec(BLOWFISH_KEY.toByteArray(StandardCharsets.UTF_8), "Blowfish")
        val cipher = Cipher.getInstance("Blowfish")
        cipher.init(Cipher.DECRYPT_MODE, key)

        val cis = CipherInputStream(input, cipher)
        val baos = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        var read: Int
        while (cis.read(buf).also { read = it } != -1) {
            baos.write(buf, 0, read)
        }
        return String(baos.toByteArray(), StandardCharsets.UTF_8)
    }

    /**
     * Finds potential Musicolet backup files on device storage.
     */
    fun findPotentialBackupFiles(): List<File> {
        val found = mutableListOf<File>()
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (downloadDir != null && downloadDir.exists()) {
            val defaultZip = File(downloadDir, "musicolet.zip")
            if (defaultZip.exists() && defaultZip.isFile) found.add(defaultZip)

            downloadDir.listFiles { f ->
                f.isFile && f.extension.equals("zip", ignoreCase = true) &&
                        (f.name.contains("musicolet", ignoreCase = true) || f.name.matches(Regex("\\d{4}-\\d{2}-\\d{2}.*\\.zip")))
            }?.forEach { if (!found.contains(it)) found.add(it) }
        }

        val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val musicoletBackups = File(docsDir, "Musicolet/Backups")
        if (musicoletBackups.exists() && musicoletBackups.isDirectory) {
            musicoletBackups.listFiles { f -> f.isFile && f.extension.equals("zip", ignoreCase = true) }
                ?.forEach { if (!found.contains(it)) found.add(it) }
        }

        return found
    }

    /**
     * Extracts song filenames from decoded Musicolet SAF URIs:
     * e.g. "content://com.android.externalstorage.documents/tree/9C33-6BBD%3AMusic/document/9C33-6BBD%3AMusic%2FIron%20Man.opus"
     * -> "Iron Man.opus"
     */
    fun extractFilenameFromUri(rawUri: String): String {
        return try {
            val decoded = URLDecoder.decode(rawUri, "UTF-8")
            val lastSlash = decoded.lastIndexOf('/')
            if (lastSlash != -1 && lastSlash < decoded.length - 1) {
                decoded.substring(lastSlash + 1).trim()
            } else {
                decoded.trim()
            }
        } catch (_: Exception) {
            rawUri.substringAfterLast('/').trim()
        }
    }

    /**
     * Imports a Musicolet backup from an InputStream (either direct file or content URI).
     */
    fun importBackup(
        context: Context,
        inputStream: InputStream,
        libraryTracks: List<TrackItem>
    ): ImportResult {
        var playlistsCreated = 0
        var totalTracksInPlaylists = 0
        var favoritesMarked = 0
        val createdNames = mutableListOf<String>()

        // Index existing library tracks by filename (lowercased) and by title (lowercased)
        val tracksByFilename = mutableMapOf<String, TrackItem>()
        val tracksByTitle = mutableMapOf<String, TrackItem>()

        for (t in libraryTracks) {
            val fn = extractFilenameFromUri(t.path).lowercase()
            tracksByFilename[fn] = t
            val cleanTitle = t.title.trim().lowercase()
            if (cleanTitle.isNotEmpty()) {
                tracksByTitle[cleanTitle] = t
            }
        }

        fun matchTrack(rawUri: String): TrackItem? {
            val fn = extractFilenameFromUri(rawUri).lowercase()
            tracksByFilename[fn]?.let { return it }

            // Try matching title without extension
            val titleNoExt = fn.substringBeforeLast('.').trim()
            tracksByTitle[titleNoExt]?.let { return it }

            // Partial contains match
            for ((key, track) in tracksByFilename) {
                if (key.contains(titleNoExt) || titleNoExt.contains(key)) {
                    return track
                }
            }
            return null
        }

        val zis = ZipInputStream(inputStream)
        var entry: ZipEntry? = zis.nextEntry

        while (entry != null) {
            val name = entry.name
            if (!entry.isDirectory) {
                if (name.equals("0.favs", ignoreCase = true) || name.endsWith("/0.favs", ignoreCase = true)) {
                    try {
                        val jsonStr = decryptMusicoletStream(zis)
                        val json = JSONObject(jsonStr)
                        val arr = json.optJSONArray("S_P")
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                val uri = arr.getString(i)
                                val matched = matchTrack(uri)
                                if (matched != null) {
                                    SonoraStore.putBool(context, "fav_${matched.key}", true)
                                    favoritesMarked++
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else if (name.endsWith(".mpl", ignoreCase = true)) {
                    val playlistName = name.substringAfterLast('/').substringBeforeLast(".mpl").trim()
                    if (playlistName.isNotEmpty()) {
                        try {
                            val jsonStr = decryptMusicoletStream(zis)
                            val json = JSONObject(jsonStr)
                            val arr = json.optJSONArray("S_P")
                            if (arr != null && arr.length() > 0) {
                                val matchedTracks = mutableListOf<TrackItem>()
                                for (i in 0 until arr.length()) {
                                    val uri = arr.getString(i)
                                    val matched = matchTrack(uri)
                                    if (matched != null && !matchedTracks.any { it.key == matched.key }) {
                                        matchedTracks.add(matched)
                                    }
                                }
                                if (matchedTracks.isNotEmpty()) {
                                    // Save as Sonora playlist
                                    val safeName = playlistName.replace('/', '_').replace('\\', '_')
                                    val existingPlaylists = SonoraStore.loadPlaylists(context)
                                    val currentList = existingPlaylists[safeName] ?: mutableListOf()
                                    for (t in matchedTracks) {
                                        if (!currentList.any { it.key == t.key }) {
                                            currentList.add(TrackRef.of(t))
                                        }
                                    }
                                    existingPlaylists[safeName] = currentList
                                    SonoraStore.savePlaylists(context, existingPlaylists)

                                    playlistsCreated++
                                    totalTracksInPlaylists += matchedTracks.size
                                    createdNames.add(safeName)
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }
        zis.close()

        return ImportResult(
            playlistsCreated = playlistsCreated,
            totalTracksInPlaylists = totalTracksInPlaylists,
            favoritesMarked = favoritesMarked,
            playlistNames = createdNames
        )
    }

    /**
     * Imports from a File object.
     */
    fun importBackupFile(
        context: Context,
        file: File,
        libraryTracks: List<TrackItem>
    ): ImportResult {
        return FileInputStream(file).use { fis ->
            importBackup(context, fis, libraryTracks)
        }
    }

    /**
     * Imports from a content Uri (picked by user via SAF).
     */
    fun importBackupUri(
        context: Context,
        uri: Uri,
        libraryTracks: List<TrackItem>
    ): ImportResult {
        val cr = context.contentResolver
        val inputStream = cr.openInputStream(uri) ?: throw IllegalArgumentException("No se pudo abrir el archivo")
        return inputStream.use { fis ->
            importBackup(context, fis, libraryTracks)
        }
    }
}

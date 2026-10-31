package com.cavifax.bagresuko

import android.content.Context
import android.os.Build
import android.webkit.CookieManager
import java.io.File
import org.json.JSONObject

/**
 * CookieBackupEngine:
 * Automatically flushes, persists and restores WebView cookies across updates,
 * process terminations and app restarts.
 */
object CookieBackupEngine {
    private const val PREF_NAME = "cavifax_cookie_backup"
    private const val KEY_COOKIES = "saved_cookies_map"
    private val DOMAINS = listOf(
        "https://www.youtube.com",
        "https://youtube.com",
        "https://music.youtube.com",
        "https://accounts.google.com",
        "https://google.com"
    )

    fun flush() {
        try {
            CookieManager.getInstance().flush()
        } catch (_: Exception) {}
    }

    /** Saves current CookieManager cookies to SharedPreferences and external backup file */
    fun backup(context: Context) {
        try {
            flush()
            val cm = CookieManager.getInstance()
            val root = JSONObject()
            for (domain in DOMAINS) {
                val c = cm.getCookie(domain)
                if (!c.isNullOrBlank()) {
                    root.put(domain, c)
                }
            }
            if (root.length() > 0) {
                val jsonStr = root.toString()
                context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_COOKIES, jsonStr)
                    .apply()

                // Also write to app external files dir so it survives cache clears
                try {
                    val extDir = context.getExternalFilesDir(null)
                    if (extDir != null) {
                        File(extDir, "cookies_backup.json").writeText(jsonStr)
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    /** Restores cookies into CookieManager if currently empty or on app startup */
    fun restore(context: Context) {
        try {
            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)

            // Try reading from SharedPreferences first, then external backup file
            var jsonStr = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_COOKIES, null)
            if (jsonStr.isNullOrBlank()) {
                val extDir = context.getExternalFilesDir(null)
                if (extDir != null) {
                    val backupFile = File(extDir, "cookies_backup.json")
                    if (backupFile.exists() && backupFile.length() > 0) {
                        jsonStr = backupFile.readText()
                    }
                }
            }
            if (!jsonStr.isNullOrBlank()) {
                val root = JSONObject(jsonStr)
                for (domain in root.keys()) {
                    val cookieStr = root.optString(domain, "")
                    if (cookieStr.isNotEmpty()) {
                        for (part in cookieStr.split(";")) {
                            val trimmed = part.trim()
                            if (trimmed.isNotEmpty()) {
                                cm.setCookie(domain, trimmed)
                            }
                        }
                    }
                }
                flush()
            }
        } catch (_: Exception) {}
    }

    /** Returns cookies for YouTube requests */
    fun getYouTubeCookies(): String {
        return try {
            CookieManager.getInstance().getCookie("https://www.youtube.com") ?: ""
        } catch (_: Exception) { "" }
    }
}

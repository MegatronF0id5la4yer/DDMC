package com.cavifax.bagresuko
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

typealias YouTubeEngine = SkisoEngine


data class YouTubeVideoItem(
    val videoId: String,
    val title: String,
    val author: String,
    val durationSec: Long,
    val thumbnailUrl: String,
    val viewsText: String = "YouTube"
)

data class YouTubeResolvedStreams(
    val videoId: String,
    val title: String,
    val author: String,
    val durationSec: Long,
    val thumbnailUrl: String,
    val audioStreamUrl: String,
    val videoStreamUrl: String,
    val hlsUrl: String = ""
)

/**
 * YouTube & YouTube Music Search, Stream Extractor, and Hybrid Video/Audio Stage Engine.
 * Supports:
 * - Direct YouTube search (`search`) returning real YouTube videos & music tracks with HD thumbnails.
 * - URL parsing (`youtube.com/watch?v=...`, `youtu.be/...`, `music.youtube.com/watch?v=...`, `youtube.com/shorts/...`).
 * - Direct Audio (`m4a`/`opus`) + Video (`mp4`/`m3u8`) stream extraction via Innertube / Piped / Invidious APIs.
 * - Embedded WebView IFrame/Mobile fallback player so every YouTube video plays in both "Ver Video" and "Solo Escuchar" modes.
 */
object SkisoEngine {
    const val ENGINE_NAME = "Skiso Engine"

    private val PIPED_INSTANCES = listOf(
        "https://api.piped.private.coffee",
        "https://pipedapi.kavin.rocks",
        "https://pipedapi.leptons.xyz",
        "https://pipedapi.nosebs.ru"
    )

    private val INVIDIOUS_INSTANCES = listOf(
        "https://inv.nadeko.net",
        "https://invidious.nerdvpn.de",
        "https://invidious.privacyredirect.com"
    )

    fun isYouTubeUrl(input: String): Boolean {
        val s = input.trim().lowercase()
        return s.contains("youtube.com/") || s.contains("youtu.be/") || s.contains("music.youtube.com/")
    }

    fun extractVideoId(input: String): String? {
        val clean = input.trim()
        if (clean.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) return clean
        return try {
            val uri = Uri.parse(clean)
            val host = uri.host?.lowercase() ?: ""
            when {
                host.contains("youtu.be") -> uri.pathSegments.firstOrNull()?.take(11)
                host.contains("youtube.com") -> {
                    uri.getQueryParameter("v")?.take(11)
                        ?: if (uri.pathSegments.firstOrNull() in listOf("shorts", "embed", "live", "v")) {
                            uri.pathSegments.getOrNull(1)?.take(11)
                        } else null
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun highResThumbnail(videoId: String): String =
        "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

    /**
     * Searches YouTube directly for songs, music videos, or any query.
     */
    fun search(query: String, maxResults: Int = 18): List<YouTubeVideoItem> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        // If user pasted a direct YouTube URL, resolve its metadata first
        val directId = extractVideoId(trimmed)
        if (directId != null) {
            val single = fetchSingleVideoMeta(directId)
            if (single != null) return listOf(single)
        }

        // 1. Primary: Direct YouTube results page `ytInitialData` JSON extraction (zero third-party dependency, ultra fast)
        val directResults = searchViaYouTubeWebJson(trimmed, maxResults)
        if (directResults.isNotEmpty()) return directResults

        // 2. Fallback: Piped API instances
        for (base in PIPED_INSTANCES) {
            try {
                val enc = URLEncoder.encode(trimmed, "UTF-8")
                val body = httpGet("$base/search?q=$enc&filter=all", timeoutMs = 5500) ?: continue
                val root = JSONObject(body)
                val items = root.optJSONArray("items") ?: continue
                val out = ArrayList<YouTubeVideoItem>()
                for (i in 0 until items.length()) {
                    val obj = items.getJSONObject(i)
                    val urlStr = obj.optString("url", "")
                    val vid = extractVideoId("https://youtube.com$urlStr") ?: continue
                    val title = obj.optString("title", "")
                    if (title.isBlank()) continue
                    val author = obj.optString("uploaderName", "YouTube")
                    val dur = obj.optLong("duration", 0L).coerceAtLeast(0L)
                    val thumb = obj.optString("thumbnail", "").ifBlank { highResThumbnail(vid) }
                    out.add(YouTubeVideoItem(vid, title, author, dur, thumb, "YouTube"))
                    if (out.size >= maxResults) break
                }
                if (out.isNotEmpty()) return out
            } catch (_: Exception) {}
        }

        // 3. Fallback: Invidious API instances
        for (base in INVIDIOUS_INSTANCES) {
            try {
                val enc = URLEncoder.encode(trimmed, "UTF-8")
                val body = httpGet("$base/api/v1/search?q=$enc&type=video", timeoutMs = 5500) ?: continue
                val arr = JSONArray(body)
                val out = ArrayList<YouTubeVideoItem>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val vid = obj.optString("videoId", "").takeIf { it.length == 11 } ?: continue
                    val title = obj.optString("title", "")
                    if (title.isBlank()) continue
                    val author = obj.optString("author", "YouTube")
                    val dur = obj.optLong("lengthSeconds", 0L).coerceAtLeast(0L)
                    out.add(YouTubeVideoItem(vid, title, author, dur, highResThumbnail(vid), "YouTube"))
                    if (out.size >= maxResults) break
                }
                if (out.isNotEmpty()) return out
            } catch (_: Exception) {}
        }

        return emptyList()
    }

    private fun searchViaYouTubeWebJson(query: String, maxResults: Int): List<YouTubeVideoItem> {
        return try {
            val enc = URLEncoder.encode(query, "UTF-8")
            val html = httpGet(
                "https://www.youtube.com/results?search_query=$enc&hl=es",
                timeoutMs = 7000,
                userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
            ) ?: return emptyList()

            val marker = "var ytInitialData = "
            val startIdx = html.indexOf(marker)
            val jsonPart = if (startIdx >= 0) {
                val from = startIdx + marker.length
                val endIdx = html.indexOf(";</script>", from)
                if (endIdx > from) html.substring(from, endIdx) else html
            } else html

            val out = ArrayList<YouTubeVideoItem>()
            val seen = HashSet<String>()
            val blocks = jsonPart.split("\"videoRenderer\":{")
            for (i in 1 until blocks.size) {
                val chunk = blocks[i].take(4500)
                val vid = Regex("\"videoId\":\"([a-zA-Z0-9_-]{11})\"").find(chunk)?.groupValues?.get(1) ?: continue
                if (!seen.add(vid)) continue

                val title = Regex("\"title\":\\{\"runs\":\\[\\{\"text\":\"((?:[^\"\\\\]|\\\\.)*)\"")
                    .find(chunk)?.groupValues?.get(1)?.let { unescapeJson(it) }
                    ?: Regex("\"title\":\\{\"simpleText\":\"((?:[^\"\\\\]|\\\\.)*)\"")
                        .find(chunk)?.groupValues?.get(1)?.let { unescapeJson(it) }
                    ?: continue

                val author = Regex("\"ownerText\":\\{\"runs\":\\[\\{\"text\":\"((?:[^\"\\\\]|\\\\.)*)\"")
                    .find(chunk)?.groupValues?.get(1)?.let { unescapeJson(it) }
                    ?: Regex("\"longBylineText\":\\{\"runs\":\\[\\{\"text\":\"((?:[^\"\\\\]|\\\\.)*)\"")
                        .find(chunk)?.groupValues?.get(1)?.let { unescapeJson(it) }
                    ?: "YouTube"

                val lengthStr = Regex("\"lengthText\":\\{[^}]*\"simpleText\":\"([0-9:]+)\"")
                    .find(chunk)?.groupValues?.get(1) ?: ""
                val durSec = parseColonDuration(lengthStr)

                val views = Regex("\"shortViewCountText\":\\{[^}]*\"simpleText\":\"((?:[^\"\\\\]|\\\\.)*)\"")
                    .find(chunk)?.groupValues?.get(1)?.let { unescapeJson(it) }
                    ?: "YouTube"

                out.add(
                    YouTubeVideoItem(
                        videoId = vid,
                        title = title,
                        author = author,
                        durationSec = durSec,
                        thumbnailUrl = highResThumbnail(vid),
                        viewsText = views
                    )
                )
                if (out.size >= maxResults) break
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun fetchSingleVideoMeta(videoId: String): YouTubeVideoItem? {
        return try {
            val oembedUrl = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json"
            val jsonStr = httpGet(oembedUrl, timeoutMs = 5000) ?: return YouTubeVideoItem(
                videoId = videoId,
                title = "Video de YouTube ($videoId)",
                author = "YouTube",
                durationSec = 0L,
                thumbnailUrl = highResThumbnail(videoId)
            )
            val obj = JSONObject(jsonStr)
            YouTubeVideoItem(
                videoId = videoId,
                title = obj.optString("title", "YouTube ($videoId)"),
                author = obj.optString("author_name", "YouTube"),
                durationSec = 0L,
                thumbnailUrl = highResThumbnail(videoId)
            )
        } catch (_: Exception) {
            YouTubeVideoItem(
                videoId = videoId,
                title = "Video de YouTube ($videoId)",
                author = "YouTube",
                durationSec = 0L,
                thumbnailUrl = highResThumbnail(videoId)
            )
        }
    }

    /**
     * Resolves playable direct audio and video stream URLs for ExoPlayer, with fallback to embedded YouTube player.
     */
    fun resolveStreams(videoId: String, fallbackItem: YouTubeVideoItem? = null): YouTubeResolvedStreams {
        val baseTitle = fallbackItem?.title ?: "YouTube ($videoId)"
        val baseAuthor = fallbackItem?.author ?: "YouTube"
        val baseDur = fallbackItem?.durationSec ?: 0L
        val thumb = fallbackItem?.thumbnailUrl ?: highResThumbnail(videoId)

        // 1. Try YouTube Innertube Android VR / ANDROID_TESTSUITE player API for direct adaptive + progressive streams
        resolveViaInnertube(videoId)?.let { return it }

        // 2. Try Piped stream endpoints
        for (base in PIPED_INSTANCES) {
            try {
                val body = httpGet("$base/streams/$videoId", timeoutMs = 5500) ?: continue
                val root = JSONObject(body)
                val title = root.optString("title", baseTitle).ifBlank { baseTitle }
                val uploader = root.optString("uploader", baseAuthor).ifBlank { baseAuthor }
                val dur = root.optLong("duration", baseDur)
                val hls = root.optString("hls", "")

                var bestAudio = ""
                var bestBitrate = 0
                val audioArr = root.optJSONArray("audioStreams")
                if (audioArr != null) {
                    for (i in 0 until audioArr.length()) {
                        val a = audioArr.getJSONObject(i)
                        val url = a.optString("url", "")
                        val br = a.optInt("bitrate", 0)
                        val mime = a.optString("mimeType", "")
                        if (url.isNotEmpty() && (br > bestBitrate || mime.contains("mp4", true))) {
                            bestAudio = url
                            bestBitrate = br
                        }
                    }
                }

                var bestVideo = hls
                val videoArr = root.optJSONArray("videoStreams")
                if (videoArr != null) {
                    for (i in 0 until videoArr.length()) {
                        val v = videoArr.getJSONObject(i)
                        val videoOnly = v.optBoolean("videoOnly", true)
                        val url = v.optString("url", "")
                        if (!videoOnly && url.isNotEmpty()) {
                            bestVideo = url
                            break
                        }
                    }
                }

                if (bestAudio.isNotEmpty() || bestVideo.isNotEmpty() || hls.isNotEmpty()) {
                    val audioFinal = bestAudio.ifEmpty { bestVideo.ifEmpty { hls } }
                    val videoFinal = bestVideo.ifEmpty { hls.ifEmpty { bestAudio } }
                    return YouTubeResolvedStreams(
                        videoId = videoId,
                        title = title,
                        author = uploader,
                        durationSec = dur,
                        thumbnailUrl = thumb,
                        audioStreamUrl = audioFinal,
                        videoStreamUrl = videoFinal,
                        hlsUrl = hls
                    )
                }
            } catch (_: Exception) {}
        }

        // 3. Try Invidious API endpoints
        for (base in INVIDIOUS_INSTANCES) {
            try {
                val body = httpGet("$base/api/v1/videos/$videoId", timeoutMs = 5500) ?: continue
                val root = JSONObject(body)
                val title = root.optString("title", baseTitle).ifBlank { baseTitle }
                val author = root.optString("author", baseAuthor).ifBlank { baseAuthor }
                val dur = root.optLong("lengthSeconds", baseDur)

                var bestAudio = ""
                val adaptive = root.optJSONArray("adaptiveFormats")
                if (adaptive != null) {
                    for (i in 0 until adaptive.length()) {
                        val f = adaptive.getJSONObject(i)
                        val type = f.optString("type", "")
                        val u = f.optString("url", "")
                        if (type.startsWith("audio/") && u.isNotEmpty()) {
                            bestAudio = u
                            if (type.contains("mp4", true)) break
                        }
                    }
                }

                var bestVideo = ""
                val fmtStreams = root.optJSONArray("formatStreams")
                if (fmtStreams != null) {
                    for (i in 0 until fmtStreams.length()) {
                        val f = fmtStreams.getJSONObject(i)
                        val u = f.optString("url", "")
                        if (u.isNotEmpty()) {
                            bestVideo = u
                            break
                        }
                    }
                }

                if (bestAudio.isNotEmpty() || bestVideo.isNotEmpty()) {
                    return YouTubeResolvedStreams(
                        videoId = videoId,
                        title = title,
                        author = author,
                        durationSec = dur,
                        thumbnailUrl = thumb,
                        audioStreamUrl = bestAudio.ifEmpty { bestVideo },
                        videoStreamUrl = bestVideo.ifEmpty { bestAudio }
                    )
                }
            } catch (_: Exception) {}
        }

        // 4. Try Cobalt tools stream API for direct mp4/mp3 stream
        try {
            val conn = (URL("https://api.cobalt.tools/api/json").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 5000
                readTimeout = 5000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }
            val payload = """{"url":"https://www.youtube.com/watch?v=$videoId","downloadMode":"auto"}"""
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode in 200..299) {
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(resp)
                val direct = root.optString("url", "")
                if (direct.isNotEmpty()) {
                    return YouTubeResolvedStreams(
                        videoId = videoId,
                        title = baseTitle,
                        author = baseAuthor,
                        durationSec = baseDur,
                        thumbnailUrl = thumb,
                        audioStreamUrl = direct,
                        videoStreamUrl = direct
                    )
                }
            }
        } catch (_: Exception) {}

        // 5. Fallback watch URL
        val watchUrl = "https://www.youtube.com/watch?v=$videoId"
        return YouTubeResolvedStreams(
            videoId = videoId,
            title = baseTitle,
            author = baseAuthor,
            durationSec = baseDur,
            thumbnailUrl = thumb,
            audioStreamUrl = watchUrl,
            videoStreamUrl = watchUrl
        )
    }

    private fun resolveViaInnertube(videoId: String): YouTubeResolvedStreams? {
        val clients = listOf(
            Triple("ANDROID_VR", "1.60.19", "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"),
            Triple("IOS", "19.29.1", "com.google.ios.youtube/19.29.1 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X;)")
        )
        for ((clientName, clientVer, ua) in clients) {
            try {
                val conn = (URL("https://www.youtube.com/youtubei/v1/player?prettyPrint=false").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 5500
                    readTimeout = 5500
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("User-Agent", ua)
                    val cookies = CookieBackupEngine.getYouTubeCookies()
                    if (cookies.isNotBlank()) {
                        setRequestProperty("Cookie", cookies)
                    }
                }
                val payload = """
                    {
                      "videoId": "$videoId",
                      "context": {
                        "client": {
                          "clientName": "$clientName",
                          "clientVersion": "$clientVer",
                          "hl": "es",
                          "gl": "MX"
                        }
                      },
                      "contentCheckOk": true,
                      "racyCheckOk": true
                    }
                """.trimIndent()
                conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                if (conn.responseCode !in 200..299) continue
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(resp)
                val playability = root.optJSONObject("playabilityStatus")?.optString("status", "") ?: ""
                if (playability != "OK") continue

                val details = root.optJSONObject("videoDetails")
                val title = details?.optString("title", "YouTube ($videoId)") ?: "YouTube ($videoId)"
                val author = details?.optString("author", "YouTube") ?: "YouTube"
                val dur = details?.optString("lengthSeconds", "0")?.toLongOrNull() ?: 0L

                val streamingData = root.optJSONObject("streamingData") ?: continue
                val hlsManifest = streamingData.optString("hlsManifestUrl", "")

                var bestAudio = ""
                var bestBitrate = 0
                val adaptive = streamingData.optJSONArray("adaptiveFormats")
                var bestAdaptiveVideo = ""
                if (adaptive != null) {
                    for (i in 0 until adaptive.length()) {
                        val f = adaptive.getJSONObject(i)
                        val mime = f.optString("mimeType", "")
                        val url = f.optString("url", "")
                        val br = f.optInt("bitrate", 0)
                        if (url.isNotEmpty() && mime.startsWith("audio/")) {
                            val score = br + if (mime.contains("mp4", true)) 50_000 else 0
                            if (score > bestBitrate) {
                                bestBitrate = score
                                bestAudio = url
                            }
                        } else if (url.isNotEmpty() && mime.startsWith("video/")) {
                            if (bestAdaptiveVideo.isEmpty() || mime.contains("mp4", true)) {
                                bestAdaptiveVideo = url
                            }
                        }
                    }
                }

                var bestProgVideo = ""
                val formats = streamingData.optJSONArray("formats")
                if (formats != null) {
                    for (i in 0 until formats.length()) {
                        val f = formats.getJSONObject(i)
                        val url = f.optString("url", "")
                        if (url.isNotEmpty()) {
                            bestProgVideo = url
                        }
                    }
                }

                val finalAudio = bestAudio.ifEmpty { bestProgVideo.ifEmpty { hlsManifest } }
                val finalVideo = bestProgVideo.ifEmpty { bestAdaptiveVideo.ifEmpty { hlsManifest.ifEmpty { bestAudio } } }
                if (finalAudio.isNotEmpty() || finalVideo.isNotEmpty()) {
                    return YouTubeResolvedStreams(
                        videoId = videoId,
                        title = title,
                        author = author,
                        durationSec = dur,
                        thumbnailUrl = highResThumbnail(videoId),
                        audioStreamUrl = finalAudio,
                        videoStreamUrl = finalVideo,
                        hlsUrl = hlsManifest
                    )
                }
            } catch (_: Exception) {}
        }
        return null
    }

    private fun parseColonDuration(s: String): Long {
        if (s.isBlank()) return 0L
        val parts = s.trim().split(":").mapNotNull { it.toLongOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 3600L + parts[1] * 60L + parts[2]
            2 -> parts[0] * 60L + parts[1]
            1 -> parts[0]
            else -> 0L
        }
    }

    private fun unescapeJson(s: String): String {
        return try {
            JSONArray("[\"$s\"]").getString(0)
        } catch (_: Exception) {
            s.replace("\\\"", "\"").replace("\\u0026", "&")
        }
    }

    private fun httpGet(
        urlStr: String,
        timeoutMs: Int = 6000,
        userAgent: String = "EstudioMusical/1.5 (Android)"
    ): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept-Language", "es-MX,es;q=0.9,en;q=0.8")
            }
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else null
        } catch (_: Exception) {
            null
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }
}

/**
 * Embedded YouTube WebView Player for the stage when playing a YouTube video/song via WebView fallback
 * or when the user chooses to view the official YouTube video stream directly inside the stage.
 * Continues playing audio seamlessly when hidden in "Solo Escuchar" mode!
 */
@SuppressLint("SetJavaScriptEnabled")
class YouTubeStageWebView(context: Context) : FrameLayout(context) {

    val webView: WebView = WebView(context)
    var currentVideoId: String = ""
        private set
    var isPlayingState: Boolean = false
        private set

    init {
        setBackgroundColor(Color.BLACK)
        webView.apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                loadsImagesAutomatically = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
            }
            CookieManager.getInstance().setAcceptCookie(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            }
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val u = request?.url?.toString() ?: return false
                    if (u.contains("youtube.com") || u.contains("youtu.be") || u.contains("googlevideo.com")) {
                        return false
                    }
                    return true
                }
            }
        }
        addView(webView)
    }

    fun loadVideo(videoId: String, autoPlay: Boolean = true) {
        if (videoId.isBlank()) return
        currentVideoId = videoId
        isPlayingState = autoPlay
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <style>
              * { margin: 0; padding: 0; background: #000; overflow: hidden; }
              html, body, iframe { width: 100%; height: 100%; border: 0; }
            </style>
            </head>
            <body>
              <iframe id="ytplayer"
                src="https://www.youtube.com/embed/$videoId?autoplay=${if (autoPlay) 1 else 0}&playsinline=1&controls=1&rel=0&modestbranding=1&enablejsapi=1"
                allow="autoplay; encrypted-media; picture-in-picture"
                allowfullscreen>
              </iframe>
            </body>
            </html>
        """.trimIndent()
        webView.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null)
    }

    fun stopPlayback() {
        currentVideoId = ""
        isPlayingState = false
        webView.loadUrl("about:blank")
    }
}



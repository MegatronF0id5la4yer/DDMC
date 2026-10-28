package com.cavifax.bagresuko
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

typealias OtaUpdateEngine = JerryEngine


/**
 * Motor Jerry (Jerry Engine):
 * High-speed local and remote OTA updater engine for DDMC.
 */
object JerryEngine {
    const val ENGINE_NAME = "Jerry Engine"

    private const val PREF_PC_HOST = "ota_pc_host"
    private const val PREF_GIT_REPO = "ota_git_repo"
    private const val PREF_DIRECT_APK_URL = "ota_direct_apk_url"
    const val PREF_AUTO_UPDATE_ENABLED = "ota_auto_update_enabled"
    private const val PREF_LAST_SEEN_COMMIT = "ota_last_seen_commit"

    // Default PC LAN IP detected on this machine (192.168.1.150:8765) + USB ADB tunnel fallback (127.0.0.1:8765)
    const val DEFAULT_PC_HOST = "192.168.1.150:8765"
    const val DEFAULT_GIT_REPO = "MegatronF0id5la4yer/DDMC"

    data class UpdateInfo(
        val sourceName: String,
        val version: String,
        val commitHash: String,
        val branch: String,
        val updatedAt: String,
        val apkSizeMb: String,
        val changelog: String,
        val apkDownloadUrl: String,
        val canTriggerPcBuild: Boolean = false,
        val pcBaseUrl: String = ""
    )

    fun getPcHost(c: Context): String =
        CavifaxStore.getString(c, PREF_PC_HOST, DEFAULT_PC_HOST).ifBlank { DEFAULT_PC_HOST }

    fun setPcHost(c: Context, host: String) =
        CavifaxStore.putString(c, PREF_PC_HOST, host.trim())

    fun getGitRepo(c: Context): String {
        val raw = CavifaxStore.getString(c, PREF_GIT_REPO, DEFAULT_GIT_REPO).ifBlank { DEFAULT_GIT_REPO }
        if (raw.contains("cavifax", ignoreCase = true) || raw.contains("estudio-doki-music", ignoreCase = true) || raw.contains("doki-doki-music-club-ddmc", ignoreCase = true)) {
            CavifaxStore.putString(c, PREF_GIT_REPO, DEFAULT_GIT_REPO)
            return DEFAULT_GIT_REPO
        }
        return raw
    }

    fun setGitRepo(c: Context, repo: String) =
        CavifaxStore.putString(c, PREF_GIT_REPO, repo.trim())

    fun getDirectApkUrl(c: Context): String =
        CavifaxStore.getString(c, PREF_DIRECT_APK_URL, "")

    fun setDirectApkUrl(c: Context, url: String) =
        CavifaxStore.putString(c, PREF_DIRECT_APK_URL, url.trim())

    /**
     * Checks this PC's update server. Automatically tries USB ADB reverse (127.0.0.1:8765)
     * and the configured LAN IP (e.g. 192.168.1.150:8765).
     */
    fun checkPcServer(c: Context): Result<UpdateInfo> {
        val rawHost = getPcHost(c)
            .removePrefix("http://")
            .removePrefix("https://")
            .trimEnd('/')
        val candidates = linkedSetOf(
            "http://127.0.0.1:8765",
            "http://$rawHost"
        )
        var lastErr: Exception? = null
        for (base in candidates) {
            try {
                val url = URL("$base/update.json")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 2500
                    readTimeout = 4000
                    requestMethod = "GET"
                }
                if (conn.responseCode in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val apkPath = json.optString("apkPath", "/Estudio-Musical-HiRes.apk")
                    val fullApkUrl = if (apkPath.startsWith("http")) apkPath else "$base$apkPath"
                    return Result.success(
                        UpdateInfo(
                            sourceName = "Servidor PC Local ($base)",
                            version = json.optString("version", "1.4.0-Estudio"),
                            commitHash = json.optString("commit", "HEAD"),
                            branch = json.optString("branch", "main"),
                            updatedAt = json.optString("updatedAt", "Reciente"),
                            apkSizeMb = json.optString("sizeMb", "29.5 MB"),
                            changelog = json.optString(
                                "changelog",
                                "Build directo desde esta PC (UI Estudio + Dokis DDSC + Landscape 16:9 + GIF Covers)."
                            ),
                            apkDownloadUrl = fullApkUrl,
                            canTriggerPcBuild = true,
                            pcBaseUrl = base
                        )
                    )
                }
            } catch (e: Exception) {
                lastErr = e
            }
        }
        return Result.failure(
            lastErr ?: Exception("No se pudo conectar con la PC en $rawHost ni por túnel USB 127.0.0.1:8765")
        )
    }

    /**
     * Checks GitHub Releases & latest Git commits for the configured repository.
     */
    fun checkGitServer(c: Context): Result<UpdateInfo> {
        val customApk = getDirectApkUrl(c)
        val repo = getGitRepo(c)
            .removePrefix("https://github.com/")
            .removeSuffix(".git")
            .trim('/')

        try {
            // 1. Check latest commit on GitHub
            var commitSha = "HEAD"
            var commitMsg = "Último commit en repositorio Git"
            var commitDate = "Reciente"
            var branchName = "main"

            try {
                val commitConn = (URL("https://api.github.com/repos/$repo/commits?per_page=1").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4500
                    readTimeout = 5000
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    setRequestProperty("User-Agent", "Estudio-Android-OTA")
                }
                if (commitConn.responseCode in 200..299) {
                    val arr = JSONArray(commitConn.inputStream.bufferedReader().use { it.readText() })
                    if (arr.length() > 0) {
                        val cObj = arr.getJSONObject(0)
                        commitSha = cObj.optString("sha", "").take(7).ifBlank { "HEAD" }
                        val commitInner = cObj.optJSONObject("commit")
                        commitMsg = commitInner?.optString("message", commitMsg)?.lines()?.firstOrNull() ?: commitMsg
                        commitDate = commitInner?.optJSONObject("committer")?.optString("date", "Reciente") ?: "Reciente"
                    }
                }
            } catch (_: Exception) {}

            // 2. Check latest GitHub Release for an .apk asset
            var releaseTag = "git-$commitSha"
            var apkUrl = customApk
            var sizeMb = "APK Git"
            var releaseNotes = commitMsg

            try {
                val relConn = (URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4500
                    readTimeout = 5000
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    setRequestProperty("User-Agent", "Estudio-Android-OTA")
                }
                if (relConn.responseCode in 200..299) {
                    val relJson = JSONObject(relConn.inputStream.bufferedReader().use { it.readText() })
                    releaseTag = relJson.optString("tag_name", releaseTag)
                    val body = relJson.optString("body", "")
                    if (body.isNotBlank()) releaseNotes = body.take(240)
                    val assets = relJson.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val a = assets.getJSONObject(i)
                            val name = a.optString("name", "")
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                apkUrl = a.optString("browser_download_url", apkUrl)
                                val bytes = a.optLong("size", 0L)
                                if (bytes > 0L) {
                                    sizeMb = String.format("%.1f MB", bytes / (1024f * 1024f))
                                }
                                break
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            // If repo has no release APK yet, fall back to PC LAN server APK URL if reachable
            if (apkUrl.isBlank()) {
                val pcFallback = checkPcServer(c).getOrNull()
                if (pcFallback != null) {
                    apkUrl = pcFallback.apkDownloadUrl
                    sizeMb = pcFallback.apkSizeMb
                    releaseNotes = "$commitMsg\n(APK servido vía PC sincronizada con Git)"
                }
            }

            return Result.success(
                UpdateInfo(
                    sourceName = "GitHub ($repo)",
                    version = releaseTag,
                    commitHash = commitSha,
                    branch = branchName,
                    updatedAt = commitDate,
                    apkSizeMb = sizeMb,
                    changelog = releaseNotes,
                    apkDownloadUrl = apkUrl,
                    canTriggerPcBuild = false
                )
            )
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    /**
     * Triggers `git pull` + Gradle APK build on the connected PC server and returns the updated metadata.
     */
    fun triggerPcGitSyncAndBuild(baseUrl: String): Result<UpdateInfo> {
        return try {
            val conn = (URL("$baseUrl/rebuild").openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 120_000 // allow up to 2 minutes for git pull + gradle assembleDebug
                requestMethod = "POST"
            }
            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val apkPath = json.optString("apkPath", "/Estudio-Musical-HiRes.apk")
                val fullApkUrl = if (apkPath.startsWith("http")) apkPath else "$baseUrl$apkPath"
                Result.success(
                    UpdateInfo(
                        sourceName = "PC + Git Sync ($baseUrl)",
                        version = json.optString("version", "1.4.0-Estudio"),
                        commitHash = json.optString("commit", "HEAD"),
                        branch = json.optString("branch", "main"),
                        updatedAt = json.optString("updatedAt", "Ahora mismo"),
                        apkSizeMb = json.optString("sizeMb", "29.5 MB"),
                        changelog = json.optString("changelog", "Git pull y compilación completados en PC."),
                        apkDownloadUrl = fullApkUrl,
                        canTriggerPcBuild = true,
                        pcBaseUrl = baseUrl
                    )
                )
            } else {
                Result.failure(Exception("HTTP ${conn.responseCode} al compilar en PC"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Downloads the APK from `apkUrl` into cache and invokes the Android system Package Installer.
     */
    fun downloadAndInstallApk(
        activity: Activity,
        apkUrl: String,
        onProgress: (percent: Int, statusText: String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (apkUrl.isBlank()) {
            onError("No hay URL de APK disponible. Inicia el servidor en tu PC o configura una URL directa.")
            return
        }
        Thread {
            try {
                activity.runOnUiThread { onProgress(2, "Conectando a $apkUrl...") }
                val conn = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Estudio-Android-OTA")
                }
                val code = conn.responseCode
                if (code !in 200..299) {
                    activity.runOnUiThread { onError("Error HTTP $code al descargar el APK") }
                    return@Thread
                }
                val totalBytes = conn.contentLengthLong.takeIf { it > 0 } ?: (30L * 1024L * 1024L)
                val outFile = File(activity.cacheDir, "Estudio-Update.apk")
                if (outFile.exists()) outFile.delete()

                conn.inputStream.use { input ->
                    FileOutputStream(outFile).use { output ->
                        val buf = ByteArray(32 * 1024)
                        var downloaded = 0L
                        var lastUiUpdate = 0L
                        while (true) {
                            val r = input.read(buf)
                            if (r == -1) break
                            output.write(buf, 0, r)
                            downloaded += r
                            val now = System.currentTimeMillis()
                            if (now - lastUiUpdate > 120L) {
                                lastUiUpdate = now
                                val pct = ((downloaded * 100L) / totalBytes).toInt().coerceIn(1, 99)
                                val mbDone = String.format("%.1f", downloaded / (1024f * 1024f))
                                val mbTot = String.format("%.1f", totalBytes / (1024f * 1024f))
                                activity.runOnUiThread {
                                    onProgress(pct, "Descargando actualización: $mbDone / $mbTot MB ($pct%)")
                                }
                            }
                        }
                        output.flush()
                    }
                }

                activity.runOnUiThread {
                    onProgress(100, "APK descargado (${String.format("%.1f MB", outFile.length() / (1024f * 1024f))}). Abriendo instalador...")
                    launchApkInstaller(activity, outFile, onError)
                }
            } catch (e: Exception) {
                activity.runOnUiThread {
                    onError("Fallo en la descarga: ${e.localizedMessage ?: e.javaClass.simpleName}")
                }
            }
        }.start()
    }

    private fun launchApkInstaller(activity: Activity, apkFile: File, onError: (String) -> Unit) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!activity.packageManager.canRequestPackageInstalls()) {
                    Toast.makeText(
                        activity,
                        "Habilita 'Instalar aplicaciones desconocidas' para Estudio y vuelve a pulsar Instalar",
                        Toast.LENGTH_LONG
                    ).show()
                    val permIntent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${activity.packageName}")
                    )
                    activity.startActivity(permIntent)
                    return
                }
            }

            val apkUri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(installIntent)
        } catch (e: Exception) {
            onError("No se pudo abrir el instalador: ${e.localizedMessage}")
        }
    }

    /**
     * Checks for new versions in the background on app launch and automatically downloads & triggers installation.
     */
    fun checkAndAutoUpdate(activity: Activity) {
        val autoUpdate = CavifaxStore.getBool(activity, PREF_AUTO_UPDATE_ENABLED, false)
        if (!autoUpdate) return

        Thread {
            try {
                Thread.sleep(3500L) // Allow initial UI inflation to finish
                val res = checkGitServer(activity).getOrNull() ?: return@Thread
                val lastSeen = CavifaxStore.getString(activity, PREF_LAST_SEEN_COMMIT, "")
                val isNewCommit = res.commitHash != "HEAD" && res.commitHash != lastSeen
                val isNewRelease = res.version.isNotBlank() && res.version != lastSeen

                if (res.apkDownloadUrl.isNotBlank() && (isNewCommit || isNewRelease)) {
                    activity.runOnUiThread {
                        Toast.makeText(
                            activity,
                            "Nueva versión de DDMC detectada (${res.version}). Descargando e instalando automáticamente...",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    downloadAndInstallApk(
                        activity = activity,
                        apkUrl = res.apkDownloadUrl,
                        onProgress = { pct, _ ->
                            if (pct == 100) {
                                val tag = if (res.commitHash != "HEAD") res.commitHash else res.version
                                CavifaxStore.putString(activity, PREF_LAST_SEEN_COMMIT, tag)
                            }
                        },
                        onError = {}
                    )
                }
            } catch (_: Exception) {}
        }.start()
    }

    /**
     * Builds the interactive OTA Updater UI panel for PC (LAN / USB) and Git / GitHub updates.
     */
    fun buildUpdaterPanel(activity: Activity, onClose: () -> Unit): View {
        val d = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        fun dpf(v: Float) = v * d

        val scroll = ScrollView(activity).apply {
            isFillViewport = true
        }
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(16))
        }

        val acc = try { Color.parseColor(Pal.ACCENT) } catch (_: Exception) { Color.parseColor("#38BDF8") }
        val onAcc = Pal.contrastOn(acc)

        // Header
        root.addView(TextView(activity).apply {
            text = "Actualizaciones (Esta PC / Git)"
            textSize = 19f
            letterSpacing = -0.015f
            setTextColor(Color.WHITE)
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
        })
        root.addView(TextView(activity).apply {
            text = "Recibe compilaciones en directo desde esta PC (Wi-Fi LAN o USB ADB) o desde el repositorio Git / GitHub."
            textSize = 12f
            setTextColor(Color.parseColor(Pal.SUB))
            setPadding(0, dp(3), 0, dp(12))
        })

        // Live Status Card
        val statusCard = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor(Pal.SURFACE))
                cornerRadius = dpf(16f)
                setStroke(dp(1), acc and 0x00FFFFFF or 0x55000000)
            }
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }

        val statusTitle = TextView(activity).apply {
            text = "Buscando servidor de actualizaciones..."
            textSize = 14f
            fontFeatureSettings = "tnum"
            setTextColor(acc)
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
        }
        val statusMeta = TextView(activity).apply {
            text = "Comprobando conexión con esta PC (127.0.0.1:8765 / ${getPcHost(activity)}) y Git..."
            textSize = 12f
            fontFeatureSettings = "tnum"
            setTextColor(Color.parseColor(Pal.SUB))
            setPadding(0, dp(4), 0, dp(4))
        }
        val statusChangelog = TextView(activity).apply {
            text = ""
            textSize = 11.5f
            setTextColor(Color.parseColor(Pal.MUTED))
            visibility = View.GONE
        }
        val progressBar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(acc)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(8)
            ).apply { topMargin = dp(8) }
            visibility = View.GONE
        }

        statusCard.addView(statusTitle)
        statusCard.addView(statusMeta)
        statusCard.addView(statusChangelog)
        statusCard.addView(progressBar)
        root.addView(statusCard)

        var latestInfo: UpdateInfo? = null

        fun bindInfo(info: UpdateInfo) {
            latestInfo = info
            statusTitle.text = "Conectado: ${info.sourceName}"
            statusMeta.text = "Versión: ${info.version}  •  Commit: ${info.commitHash} (${info.branch})\nTamaño: ${info.apkSizeMb}  •  Fecha: ${info.updatedAt}"
            statusChangelog.text = info.changelog
            statusChangelog.visibility = View.VISIBLE
        }

        // Primary Action Buttons
        fun actionButton(label: String, filled: Boolean, onClick: () -> Unit) = TextView(activity).apply {
            text = label
            textSize = 13f
            gravity = Gravity.CENTER
            minimumHeight = dp(46)
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setTextColor(if (filled) onAcc else Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dpf(14f)
                if (filled) {
                    setColor(acc)
                } else {
                    setColor(Color.parseColor(Pal.SURFACE_HI))
                    setStroke(dp(1), Color.parseColor("#38FFFFFF"))
                }
            }
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
            isClickable = true
            isFocusable = true
            addPressFeedback(0.97f)
            setOnClickListener { onClick() }
        }

        // 1. Download & Install from PC immediately
        root.addView(actionButton("Recibir e Instalar APK desde esta PC ahora", filled = true) {
            statusTitle.text = "Consultando servidor en esta PC..."
            progressBar.visibility = View.VISIBLE
            progressBar.progress = 5
            Thread {
                val res = checkPcServer(activity)
                activity.runOnUiThread {
                    res.onSuccess { info ->
                        bindInfo(info)
                        downloadAndInstallApk(
                            activity = activity,
                            apkUrl = info.apkDownloadUrl,
                            onProgress = { pct, msg ->
                                progressBar.visibility = View.VISIBLE
                                progressBar.progress = pct
                                statusTitle.text = msg
                            },
                            onError = { err ->
                                progressBar.visibility = View.GONE
                                statusTitle.text = err
                            }
                        )
                    }.onFailure { err ->
                        progressBar.visibility = View.GONE
                        statusTitle.text = "PC no detectada"
                        statusMeta.text = "${err.localizedMessage}\nAsegúrate de que pc_update_server.py esté activo en tu PC o verifica la IP."
                    }
                }
            }.start()
        })

        // 2. Trigger Git Pull + Gradle Compile on PC & Install
        root.addView(actionButton("Git Pull + Compilar en PC y Actualizar", filled = false) {
            statusTitle.text = "Sincronizando Git y compilando APK en tu PC..."
            statusMeta.text = "Ejecutando git pull + gradlew assembleDebug en la PC..."
            progressBar.visibility = View.VISIBLE
            progressBar.progress = 15
            Thread {
                val pcCheck = checkPcServer(activity).getOrNull()
                val baseUrl = pcCheck?.pcBaseUrl ?: "http://${getPcHost(activity)}"
                val buildRes = triggerPcGitSyncAndBuild(baseUrl)
                activity.runOnUiThread {
                    buildRes.onSuccess { info ->
                        bindInfo(info)
                        downloadAndInstallApk(
                            activity = activity,
                            apkUrl = info.apkDownloadUrl,
                            onProgress = { pct, msg ->
                                progressBar.visibility = View.VISIBLE
                                progressBar.progress = pct
                                statusTitle.text = msg
                            },
                            onError = { err ->
                                progressBar.visibility = View.GONE
                                statusTitle.text = err
                            }
                        )
                    }.onFailure { err ->
                        progressBar.visibility = View.GONE
                        statusTitle.text = "Error al compilar en PC"
                        statusMeta.text = err.localizedMessage ?: "Error desconocido"
                    }
                }
            }.start()
        })

        // 3. Check & Update from Git / GitHub Repository
        root.addView(actionButton("Buscar e Instalar desde Git / GitHub Releases", filled = false) {
            statusTitle.text = "Consultando repositorio Git (${getGitRepo(activity)})..."
            progressBar.visibility = View.VISIBLE
            progressBar.progress = 10
            Thread {
                val gitRes = checkGitServer(activity)
                activity.runOnUiThread {
                    gitRes.onSuccess { info ->
                        bindInfo(info)
                        if (info.apkDownloadUrl.isNotBlank()) {
                            downloadAndInstallApk(
                                activity = activity,
                                apkUrl = info.apkDownloadUrl,
                                onProgress = { pct, msg ->
                                    progressBar.visibility = View.VISIBLE
                                    progressBar.progress = pct
                                    statusTitle.text = msg
                                },
                                onError = { err ->
                                    progressBar.visibility = View.GONE
                                    statusTitle.text = err
                                }
                            )
                        } else {
                            progressBar.visibility = View.GONE
                            statusTitle.text = "Git verificado (${info.commitHash})"
                        }
                    }.onFailure { err ->
                        progressBar.visibility = View.GONE
                        statusTitle.text = "Error al consultar GitHub"
                        statusMeta.text = err.localizedMessage ?: "Verifica el nombre del repositorio"
                    }
                }
            }.start()
        })

        // Configuration Section (PC IP:Port & Git Repo)
        root.addView(TextView(activity).apply {
            text = "CONFIGURACIÓN DE SERVIDOR PC Y GIT"
            textSize = 10.5f
            letterSpacing = 0.06f
            setTextColor(Color.parseColor(Pal.MUTED))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(10), 0, dp(6))
        })

        fun inputField(label: String, initial: String, hintStr: String, onSave: (String) -> Unit): LinearLayout {
            return LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }

                addView(TextView(activity).apply {
                    text = label
                    textSize = 11.5f
                    setTextColor(Color.parseColor(Pal.SUB))
                    setPadding(0, 0, 0, dp(3))
                })

                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val edit = EditText(activity).apply {
                    setText(initial)
                    hint = hintStr
                    fontFeatureSettings = "tnum"
                    setHintTextColor(Color.parseColor(Pal.MUTED))
                    setTextColor(Color.WHITE)
                    textSize = 13f
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                    setBackgroundResource(R.drawable.bg_input_dark)
                    setPadding(dp(12), dp(9), dp(12), dp(9))
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginEnd = dp(8)
                    }
                }
                val saveBtn = TextView(activity).apply {
                    text = "Guardar"
                    textSize = 11.5f
                    gravity = Gravity.CENTER
                    minimumHeight = dp(40)
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(onAcc)
                    background = GradientDrawable().apply {
                        setColor(acc)
                        cornerRadius = dpf(10f)
                    }
                    setPadding(dp(14), dp(9), dp(14), dp(9))
                    isClickable = true
                    isFocusable = true
                    addPressFeedback(0.96f)
                    setOnClickListener {
                        onSave(edit.text.toString())
                        Toast.makeText(activity, "Guardado", Toast.LENGTH_SHORT).show()
                    }
                }
                row.addView(edit)
                row.addView(saveBtn)
                addView(row)
            }
        }

        root.addView(
            inputField(
                "IP y Puerto de esta PC (LAN o 127.0.0.1:8765 por USB ADB):",
                getPcHost(activity),
                "192.168.1.150:8765"
            ) { setPcHost(activity, it) }
        )

        root.addView(
            inputField(
                "Repositorio Git / GitHub (usuario/repo):",
                getGitRepo(activity),
                DEFAULT_GIT_REPO
            ) { setGitRepo(activity, it) }
        )

        root.addView(
            inputField(
                "URL directa de APK (opcional, rama Git raw / servidor propio):",
                getDirectApkUrl(activity),
                "https://.../Estudio-Musical-HiRes.apk"
            ) { setDirectApkUrl(activity, it) }
        )

        // Auto-probe PC and Git status when opening the sheet
        Thread {
            val pc = checkPcServer(activity).getOrNull()
            if (pc != null) {
                activity.runOnUiThread { bindInfo(pc) }
            } else {
                val git = checkGitServer(activity).getOrNull()
                activity.runOnUiThread {
                    if (git != null) {
                        bindInfo(git)
                    } else {
                        statusTitle.text = "Listo para actualizar"
                        statusMeta.text = "Ejecuta pc_update_server.py en tu PC o pulsa cualquiera de los botones superiores."
                    }
                }
            }
        }.start()

        scroll.addView(root)
        return scroll
    }
}



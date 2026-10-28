package com.cavifax.bagresuko

import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.*
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.media3.common.Player
import kotlin.math.sin

object CavifaxWidgetPrefs {
    private const val PREFS_NAME = "cavifax_chrischan_widget_prefs"

    private fun sp(c: Context) = c.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Widget Design mode: "LIGHT", "DARK", "SYSTEM", "ALBUM", "AMOLED", "DOKI_CLUB"
    fun getDesignMode(context: Context): String =
        sp(context).getString("designMode", "ALBUM") ?: "ALBUM"

    fun setDesignMode(context: Context, mode: String) =
        sp(context).edit().putString("designMode", mode).apply()

    fun getTransparency(context: Context): Int =
        sp(context).getInt("transparency", 15)

    fun setTransparency(context: Context, value: Int) {
        val v = value.coerceIn(0, 100)
        sp(context).edit()
            .putInt("transparency", v)
            .putInt("opacity", (100 - v).coerceIn(0, 100))
            .apply()
    }

    fun getOpacity(context: Context): Int =
        sp(context).getInt("opacity", 85)

    fun setOpacity(context: Context, value: Int) {
        val v = value.coerceIn(0, 100)
        sp(context).edit()
            .putInt("opacity", v)
            .putInt("transparency", (100 - v).coerceIn(0, 100))
            .apply()
    }

    fun getBgColor(context: Context): String =
        sp(context).getString("bgColor", "#121216") ?: "#121216"

    fun setBgColor(context: Context, color: String) =
        sp(context).edit().putString("bgColor", color).apply()

    fun getAccentColor(context: Context): String =
        sp(context).getString("accentColor", Pal.ACCENT) ?: Pal.ACCENT

    fun setAccentColor(context: Context, color: String) =
        sp(context).edit().putString("accentColor", color).apply()

    fun getCornerRadius(context: Context): Int =
        sp(context).getInt("cornerRadius", 22)

    fun setCornerRadius(context: Context, radius: Int) =
        sp(context).edit().putInt("cornerRadius", radius).apply()

    // Tab 1: BÁSICO
    fun getShowCover(context: Context): Boolean =
        sp(context).getBoolean("showCover", true)

    fun setShowCover(context: Context, show: Boolean) =
        sp(context).edit().putBoolean("showCover", show).apply()

    fun getShowDokiCompanion(context: Context): Boolean =
        sp(context).getBoolean("showDokiCompanion", true)

    fun setShowDokiCompanion(context: Context, show: Boolean) =
        sp(context).edit().putBoolean("showDokiCompanion", show).apply()

    fun getShowAddToPlaylist(context: Context): Boolean =
        sp(context).getBoolean("showAddToPlaylist", true)

    fun setShowAddToPlaylist(context: Context, show: Boolean) =
        sp(context).edit().putBoolean("showAddToPlaylist", show).apply()

    fun getShowFavorite(context: Context): Boolean =
        sp(context).getBoolean("showFavorite", true)

    fun setShowFavorite(context: Context, show: Boolean) =
        sp(context).edit().putBoolean("showFavorite", show).apply()

    fun getShowQualityBadge(context: Context): Boolean =
        sp(context).getBoolean("showQualityBadge", true)

    fun setShowQualityBadge(context: Context, show: Boolean) =
        sp(context).edit().putBoolean("showQualityBadge", show).apply()

    fun getShowAlbumTitle(context: Context): Boolean =
        sp(context).getBoolean("showAlbumTitle", true)

    fun setShowAlbumTitle(context: Context, show: Boolean) =
        sp(context).edit().putBoolean("showAlbumTitle", show).apply()

    // Tab 2: AVANZADO
    fun getShowProgress(context: Context): Boolean =
        sp(context).getBoolean("showProgress", true)

    fun setShowProgress(context: Context, show: Boolean) =
        sp(context).edit().putBoolean("showProgress", show).apply()

    fun getShowFastSeek(context: Context): Boolean =
        sp(context).getBoolean("showFastSeek", true)

    fun setShowFastSeek(context: Context, show: Boolean) =
        sp(context).edit().putBoolean("showFastSeek", show).apply()

    fun getShowShuffleRepeat(context: Context): Boolean =
        sp(context).getBoolean("showShuffleRepeat", true)

    fun setShowShuffleRepeat(context: Context, show: Boolean) =
        sp(context).edit().putBoolean("showShuffleRepeat", show).apply()

    fun getRoundCover(context: Context): Boolean =
        sp(context).getBoolean("roundCover", true)

    fun setRoundCover(context: Context, show: Boolean) =
        sp(context).edit().putBoolean("roundCover", show).apply()

    // Tab 3: COLA ACTUAL
    fun getShowQueue(context: Context): Boolean =
        sp(context).getBoolean("showQueue", false)

    fun setShowQueue(context: Context, show: Boolean) =
        sp(context).edit().putBoolean("showQueue", show).apply()

    fun getShowQueueNumbers(context: Context): Boolean =
        sp(context).getBoolean("showQueueNumbers", true)

    fun setShowQueueNumbers(context: Context, show: Boolean) =
        sp(context).edit().putBoolean("showQueueNumbers", show).apply()

    fun getShowQueueDuration(context: Context): Boolean =
        sp(context).getBoolean("showQueueDuration", true)

    fun setShowQueueDuration(context: Context, show: Boolean) =
        sp(context).edit().putBoolean("showQueueDuration", show).apply()

    // Notification Panel Preferences
    fun getNotifShowVideoOrGif(context: Context): Boolean =
        sp(context).getBoolean("notif_show_media", true)

    fun setNotifShowVideoOrGif(context: Context, show: Boolean) =
        sp(context).edit().putBoolean("notif_show_media", show).apply()

    fun getNotifShowLyrics(context: Context): Boolean =
        sp(context).getBoolean("notif_show_lyrics", true)

    fun setNotifShowLyrics(context: Context, show: Boolean) =
        sp(context).edit().putBoolean("notif_show_lyrics", show).apply()

    fun getNotifShowVisualizer(context: Context): Boolean =
        sp(context).getBoolean("notif_show_viz", true)

    fun setNotifShowVisualizer(context: Context, show: Boolean) =
        sp(context).edit().putBoolean("notif_show_viz", show).apply()

    fun getNotifMediaSizeMode(context: Context): Int =
        sp(context).getInt("notif_media_size", 1)

    fun setNotifMediaSizeMode(context: Context, mode: Int) =
        sp(context).edit().putInt("notif_media_size", mode.coerceIn(0, 2)).apply()

    fun getSystemCustomNotifEnabled(context: Context): Boolean =
        sp(context).getBoolean("sys_custom_notif", true)

    fun setSystemCustomNotifEnabled(context: Context, enabled: Boolean) =
        sp(context).edit().putBoolean("sys_custom_notif", enabled).apply()
}

class CavifaxWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_PLAY_PAUSE = "com.cavifax.bagresuko.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.cavifax.bagresuko.ACTION_NEXT"
        const val ACTION_PREV = "com.cavifax.bagresuko.ACTION_PREV"
        const val ACTION_FAVORITE = "com.cavifax.bagresuko.ACTION_FAVORITE"
        const val ACTION_REW = "com.cavifax.bagresuko.ACTION_REW"
        const val ACTION_FF = "com.cavifax.bagresuko.ACTION_FF"
        const val ACTION_REPEAT = "com.cavifax.bagresuko.ACTION_REPEAT"
        const val ACTION_DOKI_TAP = "com.cavifax.bagresuko.ACTION_DOKI_TAP"
        const val ACTION_QUEUE_1 = "com.cavifax.bagresuko.ACTION_QUEUE_1"
        const val ACTION_QUEUE_2 = "com.cavifax.bagresuko.ACTION_QUEUE_2"
        const val ACTION_QUEUE_3 = "com.cavifax.bagresuko.ACTION_QUEUE_3"
        const val ACTION_UPDATE_PREFS = "com.cavifax.bagresuko.ACTION_UPDATE_PREFS"

        private const val DDMC_NOTIF_CHANNEL_ID = "ddmc_live_player_channel"
        private const val DDMC_NOTIF_ID = 7742

        @Volatile
        var activeCoverBitmap: Bitmap? = null

        @Volatile
        private var widgetDokiFrameStep: Int = 2

        fun updateAllWidgets(context: Context, coverOverride: Bitmap? = null) {
            if (coverOverride != null) {
                activeCoverBitmap = coverOverride
            }
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, CavifaxWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            for (appWidgetId in appWidgetIds) {
                updateWidget(context, appWidgetManager, appWidgetId)
            }
            updateSystemCustomNotification(context)
        }

        private fun pending(context: Context, code: Int, action: String): PendingIntent {
            val i = Intent(context, CavifaxWidgetProvider::class.java).apply { this.action = action }
            return PendingIntent.getBroadcast(
                context, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun resolveDesignColors(context: Context): Triple<Int, Int, Int> {
            val mode = CavifaxWidgetPrefs.getDesignMode(context)
            val accent = try { Color.parseColor(Pal.ACCENT) } catch (_: Exception) {
                try { Color.parseColor(CavifaxWidgetPrefs.getAccentColor(context)) } catch (_: Exception) { Color.parseColor("#38BDF8") }
            }
            val isSystemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            return when (mode) {
                "LIGHT" -> Triple(Color.parseColor("#F8FAFC"), Color.parseColor("#0F172A"), Color.parseColor("#475569"))
                "DARK" -> Triple(Color.parseColor("#111420"), Color.WHITE, Color.parseColor("#CBD5E1"))
                "AMOLED" -> Triple(Color.parseColor("#05060A"), Color.WHITE, Color.parseColor("#94A3B8"))
                "DOKI_CLUB" -> Triple(Color.parseColor("#221129"), Color.WHITE, Color.parseColor("#F9A8D4"))
                "SYSTEM" -> if (isSystemDark) {
                    Triple(Color.parseColor("#111420"), Color.WHITE, Color.parseColor("#CBD5E1"))
                } else {
                    Triple(Color.parseColor("#F8FAFC"), Color.parseColor("#0F172A"), Color.parseColor("#475569"))
                }
                else -> { // ALBUM (Adaptive glassmorphic dark tint)
                    val r = (Color.red(accent) * 0.22f + 14).toInt().coerceIn(0, 255)
                    val g = (Color.green(accent) * 0.22f + 16).toInt().coerceIn(0, 255)
                    val b = (Color.blue(accent) * 0.28f + 24).toInt().coerceIn(0, 255)
                    Triple(Color.rgb(r, g, b), Color.WHITE, Color.parseColor("#D8E4F8"))
                }
            }
        }

        /**
         * Renders a high-resolution rounded glassmorphic backdrop with customizable cornerRadius,
         * subtle album-art ambient glow, neon accent border, and subtle audio spectrum bars.
         */
        private fun renderWidgetBackdropBitmap(
            context: Context,
            baseBg: Int,
            accentColor: Int,
            opacityPercent: Int,
            cornerRadiusDp: Int,
            isPlaying: Boolean
        ): Bitmap {
            val w = 680
            val h = 280
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val density = context.resources.displayMetrics.density
            val radiusPx = (cornerRadiusDp * density).coerceIn(8f, 96f)
            val alphaInt = ((opacityPercent / 100f) * 255).toInt().coerceIn(75, 255)

            val rect = RectF(2f, 2f, w - 2f, h - 2f)
            val clipPath = Path().apply {
                addRoundRect(rect, radiusPx, radiusPx, Path.Direction.CW)
            }
            canvas.save()
            canvas.clipPath(clipPath)

            // 1. Rich angled gradient fill
            val r0 = Color.red(baseBg)
            val g0 = Color.green(baseBg)
            val b0 = Color.blue(baseBg)
            val r1 = (r0 * 0.72f + Color.red(accentColor) * 0.28f).toInt().coerceIn(0, 255)
            val g1 = (g0 * 0.72f + Color.green(accentColor) * 0.28f).toInt().coerceIn(0, 255)
            val b1 = (b0 * 0.72f + Color.blue(accentColor) * 0.28f).toInt().coerceIn(0, 255)

            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, w.toFloat(), h.toFloat(),
                    Color.argb(alphaInt, r1, g1, b1),
                    Color.argb(alphaInt, (r0 * 0.85f).toInt(), (g0 * 0.85f).toInt(), (b0 * 0.88f).toInt()),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(rect, bgPaint)

            // 2. Ambient radial glow on top-right (behind the 3D Doki companion)
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    w * 0.86f, h * 0.34f, w * 0.38f,
                    Color.argb((alphaInt * 0.32f).toInt(), Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(rect, glowPaint)

            // 3. Subtle audio equalizer wave bars along the bottom when music is playing
            val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(if (isPlaying) 42 else 18, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
                style = Paint.Style.FILL
            }
            val numBars = 36
            val barW = (w - 48f) / numBars
            val phaseSeed = (System.currentTimeMillis() / 350L).toFloat()
            for (i in 0 until numBars) {
                val wave = if (isPlaying) {
                    (0.35f + 0.65f * kotlin.math.abs(sin(i * 0.45f + phaseSeed)))
                } else {
                    0.18f
                }
                val bh = (h * 0.16f) * wave
                val bx = 24f + i * barW
                canvas.drawRoundRect(
                    bx,
                    h - 4f - bh,
                    bx + barW * 0.62f,
                    h - 2f,
                    4f,
                    4f,
                    barPaint
                )
            }
            canvas.restore()

            // 4. Outer glassmorphic accent border
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 3f
                shader = LinearGradient(
                    0f, 0f, w.toFloat(), h.toFloat(),
                    Color.argb(150, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)),
                    Color.argb(55, 255, 255, 255),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(rect, radiusPx, radiusPx, borderPaint)
            return out
        }

        fun loadDokiCompanionBitmap(context: Context): Bitmap? {
            val dokiSp = context.getSharedPreferences("cavifax_prefs", Context.MODE_PRIVATE)
            val dokiId = dokiSp.getString("doki_id", "natsuki") ?: "natsuki"
            val dokiPose = dokiSp.getString("doki_pose", "casual") ?: "casual"
            val dokiExpr = dokiSp.getString("doki_expr", "3d_hd") ?: "3d_hd"
            val playing = PlayerHolder.peek()?.isPlaying == true
            val animName = if (playing) "dance" else "idle"
            val fIdx = if (playing) ((System.currentTimeMillis() / 180L + widgetDokiFrameStep) % 12L).toInt() else (widgetDokiFrameStep % 12)
            return (if (dokiExpr == "3d" || dokiId == "bocchi" || dokiId == "konata") {
                DdscEngine.load3dFrame(context, dokiId, animName, fIdx)
            } else {
                DdscEngine.load3dHdFrame(context, dokiId, dokiPose, animName, fIdx)
            }) ?: DdscEngine.loadFaceAvatarBitmap(context, dokiId, dokiPose)
        }

        private fun buildRemoteViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_cavifax)

            val opacity = CavifaxWidgetPrefs.getOpacity(context)
            val cornerRadius = CavifaxWidgetPrefs.getCornerRadius(context)
            val accentColorHex = Pal.ACCENT.ifBlank { CavifaxWidgetPrefs.getAccentColor(context) }
            val accentInt = try { Color.parseColor(accentColorHex) } catch (_: Exception) { Color.parseColor("#38BDF8") }
            val showCover = CavifaxWidgetPrefs.getShowCover(context)
            val showDoki = CavifaxWidgetPrefs.getShowDokiCompanion(context)
            val showAddToPlaylist = CavifaxWidgetPrefs.getShowAddToPlaylist(context)
            val showProgress = CavifaxWidgetPrefs.getShowProgress(context)
            val showQualityBadge = CavifaxWidgetPrefs.getShowQualityBadge(context)
            val showFavorite = CavifaxWidgetPrefs.getShowFavorite(context)
            val showAlbum = CavifaxWidgetPrefs.getShowAlbumTitle(context)
            val showFastSeek = CavifaxWidgetPrefs.getShowFastSeek(context)
            val showQueue = CavifaxWidgetPrefs.getShowQueue(context)
            val showQueueNumbers = CavifaxWidgetPrefs.getShowQueueNumbers(context)
            val roundCover = CavifaxWidgetPrefs.getRoundCover(context)

            val player = PlayerHolder.peek()
            val hasItem = player != null && player.mediaItemCount > 0
            val playing = player?.isPlaying == true

            try {
                val (baseBg, titleCol, subCol) = resolveDesignColors(context)
                val bgBmp = renderWidgetBackdropBitmap(context, baseBg, accentInt, opacity, cornerRadius, playing)
                views.setImageViewBitmap(R.id.widget_bg_canvas, bgBmp)
                views.setTextColor(R.id.widget_title, titleCol)
                views.setTextColor(R.id.widget_artist, subCol)
                views.setTextColor(R.id.widget_quality_badge, accentInt)
                views.setTextColor(R.id.widget_time_elapsed, subCol)
                views.setTextColor(R.id.widget_time_total, subCol)
                views.setInt(R.id.widget_prev_btn, "setColorFilter", titleCol)
                views.setInt(R.id.widget_next_btn, "setColorFilter", titleCol)
                views.setInt(R.id.widget_rew_btn, "setColorFilter", subCol)
                views.setInt(R.id.widget_ff_btn, "setColorFilter", subCol)
            } catch (_: Exception) {}

            val md = if (hasItem) player!!.mediaMetadata else null
            val title = if (hasItem) (md?.title?.toString() ?: "Doki Doki Music Club") else "Doki Doki Music Club"
            val artistRaw = if (hasItem) (md?.artist?.toString() ?: "Artista desconocido") else "Toca para reproducir tu música"
            val albumRaw = if (hasItem) (md?.albumTitle?.toString() ?: "") else ""
            val artist = if (showAlbum && albumRaw.isNotEmpty() && hasItem) "$artistRaw \u2022 $albumRaw" else artistRaw
            val quality = if (hasItem) (md?.subtitle?.toString() ?: "HI-RES AUDIO") else "DDMC CLUB"
            val mediaId = player?.currentMediaItem?.mediaId
            val fav = hasItem && !mediaId.isNullOrEmpty() && CavifaxStore.isFavorite(context, mediaId)

            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(R.id.widget_artist, artist)
            views.setTextViewText(R.id.widget_quality_badge, quality)

            val dokiSp = context.getSharedPreferences("cavifax_prefs", Context.MODE_PRIVATE)
            val dokiId = dokiSp.getString("doki_id", "natsuki") ?: "natsuki"
            val dokiPose = dokiSp.getString("doki_pose", "casual") ?: "casual"
            val dokiExpr = dokiSp.getString("doki_expr", "3d_hd") ?: "3d_hd"

            // Bind rounded album cover
            val coverBmp = activeCoverBitmap
            if (showCover && coverBmp != null && !coverBmp.isRecycled) {
                val cornerPx = if (roundCover) 28f else 6f
                val rendered = GifImageSupport.createRoundedBitmap(coverBmp, 180, cornerPx) ?: coverBmp
                views.setImageViewBitmap(R.id.widget_cover, rendered)
            } else {
                val dokiBmp = DdscEngine.loadFaceAvatarBitmap(context, dokiId, dokiPose)
                if (dokiBmp != null) {
                    val rendered = GifImageSupport.createRoundedBitmap(dokiBmp, 160, 28f) ?: dokiBmp
                    views.setImageViewBitmap(R.id.widget_cover, rendered)
                } else {
                    views.setImageViewResource(R.id.widget_cover, R.drawable.doki_natsuki)
                }
            }

            // Bind small Doki avatar badge AND full 3D Doki companion figure on the right!
            views.setViewVisibility(R.id.widget_doki_avatar, if (showDoki) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_doki_companion, if (showDoki) View.VISIBLE else View.GONE)
            if (showDoki) {
                val dokiAvatarBmp = DdscEngine.loadFaceAvatarBitmap(context, dokiId, dokiPose)
                if (dokiAvatarBmp != null) {
                    views.setImageViewBitmap(R.id.widget_doki_avatar, dokiAvatarBmp)
                }
                val animName = if (playing) "dance" else "idle"
                val fIdx = if (playing) ((System.currentTimeMillis() / 180L + widgetDokiFrameStep) % 12L).toInt() else (widgetDokiFrameStep % 12)
                val companion3dBmp = if (dokiExpr == "3d" || dokiId == "bocchi" || dokiId == "konata") {
                    DdscEngine.load3dFrame(context, dokiId, animName, fIdx)
                } else {
                    DdscEngine.load3dHdFrame(context, dokiId, dokiPose, animName, fIdx)
                } ?: dokiAvatarBmp
                if (companion3dBmp != null) {
                    views.setImageViewBitmap(R.id.widget_doki_companion, companion3dBmp)
                }
            }

            val dur = player?.duration?.coerceAtLeast(0L) ?: 0L
            val pos = player?.currentPosition?.coerceAtLeast(0L) ?: 0L
            val prog = if (dur > 0) ((pos * 1000L) / dur).toInt().coerceIn(0, 1000) else 0
            views.setProgressBar(R.id.widget_progress, 1000, prog, false)
            views.setTextViewText(R.id.widget_time_elapsed, formatClock(pos))
            views.setTextViewText(R.id.widget_time_total, formatClock(dur))

            views.setViewVisibility(R.id.widget_cover, if (showCover) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_playlist_btn, if (showAddToPlaylist) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_progress_section, if (showProgress) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_quality_badge, if (showQualityBadge && quality.isNotEmpty()) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_fav_btn, if (showFavorite) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_rew_btn, if (showFastSeek) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_ff_btn, if (showFastSeek) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_queue_container, if (showQueue) View.VISIBLE else View.GONE)

            if (showQueue) {
                val queueRefs = CavifaxStore.loadQueue(context)
                val curIdx = player?.currentMediaItemIndex ?: CavifaxStore.queueIndex(context)
                views.setTextViewText(
                    R.id.widget_queue_header,
                    "SIGUIENTE EN COLA (${if (queueRefs.isNotEmpty()) "${curIdx + 1}/${queueRefs.size}" else "0"})"
                )
                val rowIds = intArrayOf(R.id.widget_queue_item_1, R.id.widget_queue_item_2, R.id.widget_queue_item_3)
                for (offset in 0..2) {
                    val qIdx = curIdx + offset
                    val ref = queueRefs.getOrNull(qIdx)
                    if (ref != null) {
                        val prefix = if (showQueueNumbers) "${qIdx + 1}. " else "\u2022 "
                        val marker = if (offset == 0) "\u25B6 " else ""
                        views.setTextViewText(rowIds[offset], "$marker$prefix${ref.title} \u2014 ${ref.artist}")
                        views.setViewVisibility(rowIds[offset], View.VISIBLE)
                    } else {
                        views.setViewVisibility(rowIds[offset], View.GONE)
                    }
                }
            }

            views.setImageViewResource(
                R.id.widget_play_btn,
                if (playing) R.drawable.ic_pause_filled else R.drawable.ic_play_filled
            )
            views.setImageViewResource(
                R.id.widget_fav_btn,
                if (fav) R.drawable.ic_heart_filled else R.drawable.ic_heart
            )
            views.setInt(
                R.id.widget_fav_btn,
                "setColorFilter",
                if (fav) Color.parseColor("#F43F5E") else Color.parseColor("#CBD5E1")
            )

            val repeatMode = player?.repeatMode ?: Player.REPEAT_MODE_OFF
            views.setImageViewResource(
                R.id.widget_playlist_btn,
                if (repeatMode == Player.REPEAT_MODE_ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat
            )
            views.setInt(
                R.id.widget_playlist_btn,
                "setColorFilter",
                if (repeatMode != Player.REPEAT_MODE_OFF) accentInt else Color.parseColor("#CBD5E1")
            )

            val appPendingIntent = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, appPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_cover, appPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_meta_container, appPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_play_btn, pending(context, 1, ACTION_PLAY_PAUSE))
            views.setOnClickPendingIntent(R.id.widget_next_btn, pending(context, 2, ACTION_NEXT))
            views.setOnClickPendingIntent(R.id.widget_prev_btn, pending(context, 3, ACTION_PREV))
            views.setOnClickPendingIntent(R.id.widget_fav_btn, pending(context, 4, ACTION_FAVORITE))
            views.setOnClickPendingIntent(R.id.widget_rew_btn, pending(context, 5, ACTION_REW))
            views.setOnClickPendingIntent(R.id.widget_ff_btn, pending(context, 6, ACTION_FF))
            views.setOnClickPendingIntent(R.id.widget_playlist_btn, pending(context, 10, ACTION_REPEAT))
            views.setOnClickPendingIntent(R.id.widget_doki_companion, pending(context, 11, ACTION_DOKI_TAP))
            views.setOnClickPendingIntent(R.id.widget_doki_avatar, pending(context, 11, ACTION_DOKI_TAP))
            views.setOnClickPendingIntent(R.id.widget_queue_item_1, pending(context, 7, ACTION_QUEUE_1))
            views.setOnClickPendingIntent(R.id.widget_queue_item_2, pending(context, 8, ACTION_QUEUE_2))
            views.setOnClickPendingIntent(R.id.widget_queue_item_3, pending(context, 9, ACTION_QUEUE_3))

            return views
        }

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            try {
                val views = buildRemoteViews(context)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (_: Exception) {}
        }

        fun updateSystemCustomNotification(context: Context) {
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(DDMC_NOTIF_ID)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    nm.deleteNotificationChannel(DDMC_NOTIF_CHANNEL_ID)
                }
            } catch (_: Exception) {}
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val player = PlayerHolder.peek()
        val rewSec = CavifaxStore.getInt(context, "seek_back_sec", 10)
        val ffSec = CavifaxStore.getInt(context, "seek_fwd_sec", 10)
        when (intent.action) {
            ACTION_PLAY_PAUSE -> {
                if (player == null || player.mediaItemCount == 0) {
                    context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } else if (player.isPlaying) player.pause() else player.play()
            }
            ACTION_NEXT -> player?.let {
                if (it.mediaItemCount > 0) { if (it.hasNextMediaItem()) it.seekToNextMediaItem() else it.seekTo(0, 0L) }
            }
            ACTION_PREV -> player?.let {
                if (it.mediaItemCount > 0) {
                    if (it.currentPosition > 3000 || !it.hasPreviousMediaItem()) it.seekTo(it.currentMediaItemIndex, 0L)
                    else it.seekToPreviousMediaItem()
                }
            }
            ACTION_REW -> player?.let {
                if (it.mediaItemCount > 0) it.seekTo((it.currentPosition - rewSec * 1000L).coerceAtLeast(0L))
            }
            ACTION_FF -> player?.let {
                if (it.mediaItemCount > 0) it.seekTo(it.currentPosition + ffSec * 1000L)
            }
            ACTION_REPEAT -> player?.let {
                it.repeatMode = when (it.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
            }
            ACTION_DOKI_TAP -> {
                // Cycle 3D animation frame and every 3 taps switch to the next Doki character right from the widget!
                widgetDokiFrameStep = (widgetDokiFrameStep + 3) % 12
                if (widgetDokiFrameStep == 0) {
                    val order = listOf("natsuki", "monika", "sayori", "yuri", "bocchi", "konata")
                    val sp = context.getSharedPreferences("cavifax_prefs", Context.MODE_PRIVATE)
                    val cur = sp.getString("doki_id", "natsuki") ?: "natsuki"
                    val nextDoki = order[(order.indexOf(cur) + 1) % order.size]
                    sp.edit().putString("doki_id", nextDoki).apply()
                }
            }
            ACTION_QUEUE_1 -> player?.let { if (it.mediaItemCount > 0) it.play() }
            ACTION_QUEUE_2 -> player?.let {
                val target = it.currentMediaItemIndex + 1
                if (target < it.mediaItemCount) { it.seekTo(target, 0L); it.play() }
            }
            ACTION_QUEUE_3 -> player?.let {
                val target = it.currentMediaItemIndex + 2
                if (target < it.mediaItemCount) { it.seekTo(target, 0L); it.play() }
            }
            ACTION_FAVORITE -> {
                val id = player?.currentMediaItem?.mediaId
                if (!id.isNullOrEmpty()) CavifaxStore.toggleFavorite(context, id)
            }
        }
        updateAllWidgets(context)
    }
}

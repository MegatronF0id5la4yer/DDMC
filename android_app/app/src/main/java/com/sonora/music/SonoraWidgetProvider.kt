package com.sonora.music

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat

object SonoraWidgetPrefs {
    private const val PREFS_NAME = "sonora_musicolet_widget_prefs"

    private fun sp(c: Context) = c.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Widget Design mode: "LIGHT", "DARK", "SYSTEM", "ALBUM", "AMOLED", "DOKI_CLUB"
    fun getDesignMode(context: Context): String =
        sp(context).getString("designMode", "ALBUM") ?: "ALBUM"

    fun setDesignMode(context: Context, mode: String) =
        sp(context).edit().putString("designMode", mode).apply()

    fun getTransparency(context: Context): Int =
        sp(context).getInt("transparency", 25)

    fun setTransparency(context: Context, value: Int) {
        val v = value.coerceIn(0, 100)
        sp(context).edit()
            .putInt("transparency", v)
            .putInt("opacity", (100 - v).coerceIn(0, 100))
            .apply()
    }

    fun getOpacity(context: Context): Int =
        sp(context).getInt("opacity", 75)

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
        sp(context).getInt("cornerRadius", 18)

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
        sp(context).getBoolean("showFastSeek", false)

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

    // Notification Panel Preferences (GIF, Video, Lyrics, Doki, Custom Style)
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
        sp(context).getInt("notif_media_size", 1) // 0=Compact, 1=Large Uncropped, 2=Cinema Full-Width

    fun setNotifMediaSizeMode(context: Context, mode: Int) =
        sp(context).edit().putInt("notif_media_size", mode.coerceIn(0, 2)).apply()

    fun getSystemCustomNotifEnabled(context: Context): Boolean =
        sp(context).getBoolean("sys_custom_notif", true)

    fun setSystemCustomNotifEnabled(context: Context, enabled: Boolean) =
        sp(context).edit().putBoolean("sys_custom_notif", enabled).apply()
}

class SonoraWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_PLAY_PAUSE = "com.sonora.music.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.sonora.music.ACTION_NEXT"
        const val ACTION_PREV = "com.sonora.music.ACTION_PREV"
        const val ACTION_FAVORITE = "com.sonora.music.ACTION_FAVORITE"
        const val ACTION_REW = "com.sonora.music.ACTION_REW"
        const val ACTION_FF = "com.sonora.music.ACTION_FF"
        const val ACTION_QUEUE_1 = "com.sonora.music.ACTION_QUEUE_1"
        const val ACTION_QUEUE_2 = "com.sonora.music.ACTION_QUEUE_2"
        const val ACTION_QUEUE_3 = "com.sonora.music.ACTION_QUEUE_3"
        const val ACTION_UPDATE_PREFS = "com.sonora.music.ACTION_UPDATE_PREFS"

        private const val DDMC_NOTIF_CHANNEL_ID = "ddmc_live_player_channel"
        private const val DDMC_NOTIF_ID = 7742

        @Volatile
        var activeCoverBitmap: Bitmap? = null

        fun updateAllWidgets(context: Context, coverOverride: Bitmap? = null) {
            if (coverOverride != null) {
                activeCoverBitmap = coverOverride
            }
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, SonoraWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            for (appWidgetId in appWidgetIds) {
                updateWidget(context, appWidgetManager, appWidgetId)
            }
            updateSystemCustomNotification(context)
        }

        private fun pending(context: Context, code: Int, action: String): PendingIntent {
            val i = Intent(context, SonoraWidgetProvider::class.java).apply { this.action = action }
            return PendingIntent.getBroadcast(
                context, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun resolveDesignColors(context: Context): Triple<Int, Int, Int> {
            val mode = SonoraWidgetPrefs.getDesignMode(context)
            val accent = try { Color.parseColor(Pal.ACCENT) } catch (_: Exception) {
                try { Color.parseColor(SonoraWidgetPrefs.getAccentColor(context)) } catch (_: Exception) { Color.parseColor("#38BDF8") }
            }
            val isSystemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            return when (mode) {
                "LIGHT" -> Triple(Color.parseColor("#F8FAFC"), Color.parseColor("#0F172A"), Color.parseColor("#475569"))
                "DARK" -> Triple(Color.parseColor("#121521"), Color.WHITE, Color.parseColor("#CBD5E1"))
                "AMOLED" -> Triple(Color.BLACK, Color.WHITE, Color.parseColor("#94A3B8"))
                "DOKI_CLUB" -> Triple(Color.parseColor("#24122B"), Color.WHITE, Color.parseColor("#F9A8D4"))
                "SYSTEM" -> if (isSystemDark) {
                    Triple(Color.parseColor("#121521"), Color.WHITE, Color.parseColor("#CBD5E1"))
                } else {
                    Triple(Color.parseColor("#F8FAFC"), Color.parseColor("#0F172A"), Color.parseColor("#475569"))
                }
                else -> { // ALBUM (Adaptive to active cover)
                    val r = (Color.red(accent) * 0.25f + 14).toInt().coerceIn(0, 255)
                    val g = (Color.green(accent) * 0.25f + 16).toInt().coerceIn(0, 255)
                    val b = (Color.blue(accent) * 0.32f + 24).toInt().coerceIn(0, 255)
                    Triple(Color.rgb(r, g, b), Color.WHITE, Color.parseColor("#D8E4F8"))
                }
            }
        }

        private fun buildRemoteViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_sonora)

            val opacity = SonoraWidgetPrefs.getOpacity(context)
            val accentColorHex = Pal.ACCENT.ifBlank { SonoraWidgetPrefs.getAccentColor(context) }
            val showCover = SonoraWidgetPrefs.getShowCover(context)
            val showDoki = SonoraWidgetPrefs.getShowDokiCompanion(context)
            val showAddToPlaylist = SonoraWidgetPrefs.getShowAddToPlaylist(context)
            val showProgress = SonoraWidgetPrefs.getShowProgress(context)
            val showQualityBadge = SonoraWidgetPrefs.getShowQualityBadge(context)
            val showFavorite = SonoraWidgetPrefs.getShowFavorite(context)
            val showAlbum = SonoraWidgetPrefs.getShowAlbumTitle(context)
            val showFastSeek = SonoraWidgetPrefs.getShowFastSeek(context)
            val showQueue = SonoraWidgetPrefs.getShowQueue(context)
            val showQueueNumbers = SonoraWidgetPrefs.getShowQueueNumbers(context)
            val roundCover = SonoraWidgetPrefs.getRoundCover(context)

            try {
                val (baseBg, titleCol, subCol) = resolveDesignColors(context)
                val alphaInt = ((opacity / 100f) * 255).toInt().coerceIn(60, 255)
                val finalBgColor = Color.argb(alphaInt, Color.red(baseBg), Color.green(baseBg), Color.blue(baseBg))
                views.setInt(R.id.widget_root, "setBackgroundColor", finalBgColor)
                views.setTextColor(R.id.widget_title, titleCol)
                views.setTextColor(R.id.widget_artist, subCol)
                views.setTextColor(R.id.widget_quality_badge, Color.parseColor(accentColorHex))
            } catch (_: Exception) {}

            val player = PlayerHolder.peek()
            val hasItem = player != null && player.mediaItemCount > 0
            val md = if (hasItem) player!!.mediaMetadata else null
            val title = if (hasItem) (md?.title?.toString() ?: "Doki Doki Music Club") else "Doki Doki Music Club (DDMC)"
            val artistRaw = if (hasItem) (md?.artist?.toString() ?: "") else "Toca para abrir DDMC"
            val albumRaw = if (hasItem) (md?.albumTitle?.toString() ?: "") else ""
            val artist = if (showAlbum && albumRaw.isNotEmpty() && hasItem) "$artistRaw \u2022 $albumRaw" else artistRaw
            val quality = if (hasItem) (md?.subtitle?.toString() ?: "DDMC \u2022 HI-RES") else "DDMC \u2022 HI-RES"
            val playing = player?.isPlaying == true
            val mediaId = player?.currentMediaItem?.mediaId
            val fav = hasItem && !mediaId.isNullOrEmpty() && SonoraStore.isFavorite(context, mediaId)

            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(R.id.widget_artist, artist)
            views.setTextViewText(R.id.widget_quality_badge, quality)

            // Bind uncropped album cover / GIF / video frame
            val coverBmp = activeCoverBitmap
            if (showCover && coverBmp != null && !coverBmp.isRecycled) {
                val cornerPx = if (roundCover) 24f else 4f
                val rendered = GifImageSupport.createRoundedBitmap(coverBmp, 160, cornerPx) ?: coverBmp
                views.setImageViewBitmap(R.id.widget_cover, rendered)
            } else {
                val dokiId = context.getSharedPreferences("sonora_prefs", Context.MODE_PRIVATE)
                    .getString("doki_id", "monika") ?: "monika"
                val dokiBmp = DdscEngine.loadFaceAvatarBitmap(context, dokiId)
                if (dokiBmp != null) {
                    views.setImageViewBitmap(R.id.widget_cover, dokiBmp)
                } else {
                    views.setImageViewResource(R.id.widget_cover, R.drawable.doki_monika)
                }
            }

            // Bind active Doki companion badge
            views.setViewVisibility(R.id.widget_doki_avatar, if (showDoki) View.VISIBLE else View.GONE)
            if (showDoki) {
                val dokiId = context.getSharedPreferences("sonora_prefs", Context.MODE_PRIVATE)
                    .getString("doki_id", "monika") ?: "monika"
                val dokiBmp = DdscEngine.loadFaceAvatarBitmap(context, dokiId)
                if (dokiBmp != null) {
                    views.setImageViewBitmap(R.id.widget_doki_avatar, dokiBmp)
                } else {
                    views.setImageViewResource(R.id.widget_doki_avatar, R.drawable.doki_monika)
                }
            }

            val dur = player?.duration ?: 0L
            val pos = player?.currentPosition ?: 0L
            val prog = if (dur > 0) ((pos * 1000L) / dur).toInt().coerceIn(0, 1000) else 0
            views.setProgressBar(R.id.widget_progress, 1000, prog, false)

            views.setViewVisibility(R.id.widget_cover, if (showCover) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_playlist_btn, if (showAddToPlaylist) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_progress, if (showProgress) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_quality_badge, if (showQualityBadge && quality.isNotEmpty()) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_fav_btn, if (showFavorite) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_rew_btn, if (showFastSeek) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_ff_btn, if (showFastSeek) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_queue_container, if (showQueue) View.VISIBLE else View.GONE)

            if (showQueue) {
                val queueRefs = SonoraStore.loadQueue(context)
                val curIdx = player?.currentMediaItemIndex ?: SonoraStore.queueIndex(context)
                views.setTextViewText(
                    R.id.widget_queue_header,
                    "COLA DDMC (${if (queueRefs.isNotEmpty()) "${curIdx + 1}/${queueRefs.size}" else "0"})"
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
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )
            views.setImageViewResource(
                R.id.widget_fav_btn,
                if (fav) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off
            )

            val appPendingIntent = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, appPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_cover, appPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_meta_container, appPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_playlist_btn, appPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_play_btn, pending(context, 1, ACTION_PLAY_PAUSE))
            views.setOnClickPendingIntent(R.id.widget_next_btn, pending(context, 2, ACTION_NEXT))
            views.setOnClickPendingIntent(R.id.widget_prev_btn, pending(context, 3, ACTION_PREV))
            views.setOnClickPendingIntent(R.id.widget_fav_btn, pending(context, 4, ACTION_FAVORITE))
            views.setOnClickPendingIntent(R.id.widget_rew_btn, pending(context, 5, ACTION_REW))
            views.setOnClickPendingIntent(R.id.widget_ff_btn, pending(context, 6, ACTION_FF))
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
            if (!SonoraWidgetPrefs.getSystemCustomNotifEnabled(context)) return
            val player = PlayerHolder.peek() ?: return
            if (player.mediaItemCount == 0) return
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val ch = NotificationChannel(
                        DDMC_NOTIF_CHANNEL_ID,
                        "DDMC Panel de Reproducción",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "Panel de notificaciones personalizable de Doki Doki Music Club"
                        setShowBadge(false)
                    }
                    nm.createNotificationChannel(ch)
                }
                val views = buildRemoteViews(context)
                val openIntent = PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val md = player.mediaMetadata
                val notif = NotificationCompat.Builder(context, DDMC_NOTIF_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_media_play)
                    .setContentTitle(md.title ?: "Doki Doki Music Club")
                    .setContentText(md.artist ?: "DDMC")
                    .setCustomContentView(views)
                    .setCustomBigContentView(views)
                    .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                    .setContentIntent(openIntent)
                    .setOngoing(player.isPlaying)
                    .setSilent(true)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .build()
                nm.notify(DDMC_NOTIF_ID, notif)
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
        val rewSec = SonoraStore.getInt(context, "seek_back_sec", 10)
        val ffSec = SonoraStore.getInt(context, "seek_fwd_sec", 10)
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
                if (!id.isNullOrEmpty()) SonoraStore.toggleFavorite(context, id)
            }
        }
        updateAllWidgets(context)
    }
}

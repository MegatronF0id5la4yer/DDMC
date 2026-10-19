package com.sonora.music

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** Process-wide ExoPlayer so playback survives the Activity and is shared with the service/widget. */
object PlayerHolder {
    @Volatile
    private var player: ExoPlayer? = null

    fun get(context: Context): ExoPlayer = player ?: synchronized(this) {
        player ?: ExoPlayer.Builder(context.applicationContext)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
            .also { player = it }
    }

    fun peek(): ExoPlayer? = player
}

/** Foreground media service: lock-screen / notification controls, headset buttons and widget refresh. */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    companion object {
        const val CHANNEL_ID = "ddmc_playback"
        const val NOTIFICATION_ID = 1001
        const val ACTION_FAVORITE = "ACTION_FAVORITE"
        @Volatile var activeSession: MediaSession? = null

        fun updateFavoriteButton(isFav: Boolean) {
            val s = activeSession ?: return
            val favRes = if (isFav) R.drawable.ic_heart_filled else R.drawable.ic_heart
            val favCmd = SessionCommand(ACTION_FAVORITE, Bundle.EMPTY)
            val favBtn = CommandButton.Builder()
                .setDisplayName("Favorito")
                .setIconResId(favRes)
                .setSessionCommand(favCmd)
                .build()
            try {
                s.setCustomLayout(ImmutableList.of(favBtn))
            } catch (_: Exception) {}
        }
    }

    private val widgetListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = SonoraWidgetProvider.updateAllWidgets(this@PlaybackService)
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val key = mediaItem?.mediaId ?: ""
            val isFav = if (key.isNotEmpty()) SonoraStore.getBool(this@PlaybackService, "fav_$key", false) else false
            updateFavoriteButton(isFav)
            SonoraWidgetProvider.updateAllWidgets(this@PlaybackService)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val player = PlayerHolder.get(this)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val curKey = player.currentMediaItem?.mediaId ?: ""
        val initFav = if (curKey.isNotEmpty()) SonoraStore.getBool(this, "fav_$curKey", false) else false
        val favCmd = SessionCommand(ACTION_FAVORITE, Bundle.EMPTY)
        val favBtn = CommandButton.Builder()
            .setDisplayName("Favorito")
            .setIconResId(if (initFav) R.drawable.ic_heart_filled else R.drawable.ic_heart)
            .setSessionCommand(favCmd)
            .build()

        val callback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    .add(favCmd)
                    .build()
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(commands)
                    .setCustomLayout(ImmutableList.of(favBtn))
                    .build()
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                if (customCommand.customAction == ACTION_FAVORITE) {
                    val p = session.player
                    val cur = p.currentMediaItem
                    val key = cur?.mediaId ?: ""
                    if (key.isNotEmpty()) {
                        val isFav = SonoraStore.getBool(this@PlaybackService, "fav_$key", false)
                        val newFav = !isFav
                        SonoraStore.putBool(this@PlaybackService, "fav_$key", newFav)
                        updateFavoriteButton(newFav)
                        SonoraWidgetProvider.updateAllWidgets(this@PlaybackService)
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                return super.onCustomCommand(session, controller, customCommand, args)
            }
        }

        session = MediaSession.Builder(this, player)
            .setSessionActivity(open)
            .setCallback(callback)
            .build().also { activeSession = it }

        val notifProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(CHANNEL_ID)
            .setChannelName(R.string.notif_channel_name)
            .setNotificationId(NOTIFICATION_ID)
            .build().apply {
                setSmallIcon(R.drawable.ic_music)
            }
        setMediaNotificationProvider(notifProvider)

        player.addListener(widgetListener)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controles de reproducción multimedia interactivos de DDMC"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (SonoraStore.getBool(this, "adv_stop_on_task_removed", false)) {
            p?.stop()
            stopSelf()
            return
        }
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        PlayerHolder.peek()?.removeListener(widgetListener)
        activeSession = null
        session?.release()
        session = null
        super.onDestroy()
    }
}

/** Audio effects live at process level so the EQ keeps working after the Activity is closed. */
object AudioFx {
    var equalizer: Equalizer? = null
    var bass: BassBoost? = null
    var virtualizer: Virtualizer? = null
    var loudness: LoudnessEnhancer? = null
    private var session = -1

    fun setEnabled(enabled: Boolean) {
        try { equalizer?.enabled = enabled } catch (_: Exception) {}
        try { bass?.enabled = enabled } catch (_: Exception) {}
        try { virtualizer?.enabled = enabled } catch (_: Exception) {}
        try { loudness?.enabled = enabled } catch (_: Exception) {}
    }

    fun ensure(c: Context, sessionId: Int) {
        if (sessionId == 0) return
        if (sessionId == session) return
        release()
        session = sessionId
        try { equalizer = Equalizer(0, sessionId) } catch (_: Exception) {}
        try { bass = BassBoost(0, sessionId) } catch (_: Exception) {}
        try { virtualizer = Virtualizer(0, sessionId) } catch (_: Exception) {}
        try { loudness = LoudnessEnhancer(sessionId) } catch (_: Exception) {}
        apply(c)
    }

    fun apply(c: Context) {
        try {
            equalizer?.let { eq ->
                eq.enabled = SonoraStore.getBool(c, "eq_on", true)
                val saved = SonoraStore.getString(c, "eq_levels", "").split(",").mapNotNull { it.toIntOrNull() }
                for (i in 0 until eq.numberOfBands.toInt()) {
                    eq.setBandLevel(i.toShort(), (saved.getOrNull(i) ?: 0).toShort())
                }
            }
        } catch (_: Exception) {}
        try {
            bass?.let { it.setStrength(SonoraStore.getInt(c, "bass", 0).toShort()); it.enabled = true }
        } catch (_: Exception) {}
        try {
            virtualizer?.let { it.setStrength(SonoraStore.getInt(c, "virt", 0).toShort()); it.enabled = true }
        } catch (_: Exception) {}
        try {
            loudness?.let { it.setTargetGain(SonoraStore.getInt(c, "gain", 0)); it.enabled = true }
        } catch (_: Exception) {}
    }

    fun saveBands(c: Context) {
        val eq = equalizer ?: return
        try {
            val csv = (0 until eq.numberOfBands.toInt()).joinToString(",") { eq.getBandLevel(it.toShort()).toString() }
            SonoraStore.putString(c, "eq_levels", csv)
        } catch (_: Exception) {}
    }

    fun release() {
        try { equalizer?.release() } catch (_: Exception) {}
        try { bass?.release() } catch (_: Exception) {}
        try { virtualizer?.release() } catch (_: Exception) {}
        try { loudness?.release() } catch (_: Exception) {}
        equalizer = null; bass = null; virtualizer = null; loudness = null
        session = -1
    }
}


/** Process-level sleep timer: keeps counting even if the Activity is closed. Fades out before pausing. */
object SleepTimer {
    private val handler = Handler(Looper.getMainLooper())
    var endAt = 0L
        private set
    var onTick: ((Long) -> Unit)? = null
    val active: Boolean get() = endAt > 0

    private val tick = object : Runnable {
        override fun run() {
            val rem = endAt - System.currentTimeMillis()
            val p = PlayerHolder.peek()
            if (rem <= 0) {
                p?.pause()
                p?.volume = 1f
                endAt = 0
                onTick?.invoke(0)
            } else {
                if (rem < 8000) p?.volume = (rem / 8000f).coerceIn(0f, 1f)
                onTick?.invoke(rem)
                handler.postDelayed(this, 500)
            }
        }
    }

    fun start(minutes: Int) {
        cancel()
        endAt = System.currentTimeMillis() + minutes * 60_000L
        handler.post(tick)
    }

    fun cancel() {
        handler.removeCallbacks(tick)
        endAt = 0
        PlayerHolder.peek()?.volume = 1f
        onTick?.invoke(0)
    }
}


package com.sonora.music

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlin.math.hypot

/**
 * Ventana Emergente Flotante (Floating Overlay Window) para DDMC.
 *
 * Diseno exacto segun el boceto del usuario:
 * - Recuadro exterior compacto y elegante (~164dp x 196dp).
 * - En el centro: Cuadrado perfecto con la caratula del album (126dp x 126dp, esquinas 18dp, borde fino).
 * - En la base: Visualizador en forma de onda (Osciloscopio Neon) alineado exactamente al ancho de la caratula.
 * - Fondo 100% Transparente por defecto (o Cristal Liquido suave con pulsacion larga o boton sutil).
 * - Sin botones de texto antiesteticos: controles iconicos minimalistas con auto-fade.
 * - Toque simple en caratula: Play/Pausa con destello.
 * - Doble toque en caratula: Abre DDMC.
 * - Arrastre suave a cualquier parte de la pantalla.
 */
class FloatingPlayerService : Service() {

    companion object {
        private const val TAG = "FloatingPlayer"

        var isRunning: Boolean = false
            private set

        private var instance: FloatingPlayerService? = null

        var currentCover: Bitmap? = null
            private set
        var currentTrackTitle: String = "DDMC"
            private set
        var currentArtist: String = "Reproductor"
            private set
        var isCurrentlyPlaying: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, FloatingPlayerService::class.java)
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting FloatingPlayerService", e)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingPlayerService::class.java)
            try {
                context.stopService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping FloatingPlayerService", e)
            }
        }

        fun updateTrack(title: String, artist: String, cover: Bitmap?) {
            currentTrackTitle = title
            currentArtist = artist
            if (cover != null) currentCover = cover
            instance?.setTrackInfo(title, artist, cover)
        }

        fun updateCover(bitmap: Bitmap?) {
            if (bitmap != null) currentCover = bitmap
            instance?.setCoverBitmap(bitmap)
        }

        fun updatePlayState(isPlaying: Boolean) {
            isCurrentlyPlaying = isPlaying
            instance?.setPlayingState(isPlaying)
        }

        fun updateFft(fft: ByteArray?) {
            instance?.setFft(fft)
        }

        fun updateWaveform(waveform: ByteArray?) {
            instance?.setWaveform(waveform)
        }
    }

    private var windowManager: WindowManager? = null
    private var floatingRoot: FrameLayout? = null
    private var cardContainer: LinearLayout? = null
    private var miniTopBar: FrameLayout? = null
    private var toggleBgBtn: ImageView? = null
    private var closeBtn: TextView? = null
    private var coverCard: FrameLayout? = null
    private var coverImage: ImageView? = null
    private var visualizerView: VisualizerView? = null
    private var playIndicator: ImageView? = null
    private var transportRow: LinearLayout? = null
    private var progressBar: ProgressBar? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var uiScale: Float = 1f
    private val baseW get() = dp(164)
    private val baseH get() = dp(236)

    private val progressRunnable = object : Runnable {
        override fun run() {
            val pl = PlayerHolder.peek()
            if (pl != null && pl.duration > 0) {
                progressBar?.max = 1000
                progressBar?.progress = (pl.currentPosition * 1000L / pl.duration).toInt().coerceIn(0, 1000)
            }
            uiHandler.postDelayed(this, 500)
        }
    }

    private var isPlaying: Boolean = false
    private var isTransparentBg: Boolean = true

    private val uiHandler = Handler(Looper.getMainLooper())
    private val fadeControlsRunnable = Runnable {
        miniTopBar?.animate()?.alpha(0f)?.setDuration(300)?.start()
        transportRow?.animate()?.alpha(0f)?.setDuration(300)?.start()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun dpf(v: Float): Float = v * resources.displayMetrics.density

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        isRunning = true

        isTransparentBg = SonoraStore.getBool(this, "float_window_transparent", true)

        val player = PlayerHolder.peek()
        if (player != null) {
            isPlaying = player.isPlaying
            isCurrentlyPlaying = player.isPlaying
            player.addListener(playerListener)
        } else {
            isPlaying = isCurrentlyPlaying
        }

        setupFloatingWindow()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        refreshDisplay()
        return START_STICKY
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            setPlayingState(playing)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            refreshDisplay()
        }
    }

    private fun refreshDisplay() {
        val player = PlayerHolder.peek()
        if (player != null) {
            setPlayingState(player.isPlaying)
        }
        val bmp = SonoraWidgetProvider.activeCoverBitmap ?: currentCover
        applyCoverBitmap(bmp)
    }

    private fun setupFloatingWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Cannot setup overlay: Settings.canDrawOverlays is false")
            stopSelf()
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (windowManager == null) {
            Log.e(TAG, "WindowManager service not available")
            stopSelf()
            return
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val dm = resources.displayMetrics
        val screenW = dm.widthPixels
        val screenH = dm.heightPixels

        // Ventana compacta y bien proporcionada
        val windowWidth = dp(164)
        val windowHeight = dp(236)

        val defaultX = (screenW - windowWidth) / 2
        val defaultY = (screenH - windowHeight) / 3

        uiScale = SonoraStore.getFloat(this, "float_window_scale", 1f).coerceIn(0.6f, 1.8f)
        val initialX = SonoraStore.getInt(this, "float_window_x", defaultX).coerceIn(0, (screenW - windowWidth).coerceAtLeast(0))
        val initialY = SonoraStore.getInt(this, "float_window_y", defaultY).coerceIn(0, (screenH - windowHeight).coerceAtLeast(0))

        val params = WindowManager.LayoutParams(
            (windowWidth * uiScale).toInt(),
            (windowHeight * uiScale).toInt(),
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialX
            y = initialY
        }

        // 1. Contenedor Raiz Flotante
        floatingRoot = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
            setBackgroundColor(Color.TRANSPARENT)
        }

        // 2. Tarjeta / Recuadro interior
        cardContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(dp(6), dp(4), dp(6), dp(6))
            updateContainerBackground()
        }

        // 3. Mini barra superior sutil (transparencia y cerrar) con auto-ocultado
        miniTopBar = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(22)
            ).apply {
                bottomMargin = dp(2)
            }
            alpha = 0.85f
        }

        // Boton icono de transparencia (sutil circulo de cristal)
        toggleBgBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_palette)
            setColorFilter(Color.parseColor("#E0FFFFFF"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#80141624"))
                shape = GradientDrawable.OVAL
                setStroke(dp(1), Color.parseColor("#44FFFFFF"))
            }
            setPadding(dp(3), dp(3), dp(3), dp(3))
            layoutParams = FrameLayout.LayoutParams(dp(20), dp(20), Gravity.START or Gravity.CENTER_VERTICAL)
            setOnClickListener {
                isTransparentBg = !isTransparentBg
                SonoraStore.putBool(this@FloatingPlayerService, "float_window_transparent", isTransparentBg)
                updateContainerBackground()
                resetControlsFadeTimer()
            }
        }
        miniTopBar?.addView(toggleBgBtn)

        // Boton cerrar sutil
        closeBtn = TextView(this).apply {
            text = "\u2715"
            textSize = 10f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#991C1E2B"))
                shape = GradientDrawable.OVAL
                setStroke(dp(1), Color.parseColor("#44FFFFFF"))
            }
            layoutParams = FrameLayout.LayoutParams(dp(20), dp(20), Gravity.END or Gravity.CENTER_VERTICAL)
            setOnClickListener {
                stopSelf()
            }
        }
        miniTopBar?.addView(closeBtn)

        cardContainer?.addView(miniTopBar)

        // 4. Caratula en el Centro (Cuadrado exacto de 126dp con esquinas suaves y borde)
        val cardSize = dp(126)
        coverCard = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(cardSize, cardSize).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1826"))
                cornerRadius = dpf(18f)
                setStroke(dp(1), Color.parseColor("#4DFFFFFF"))
            }
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            elevation = dpf(6f)
        }

        coverImage = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        coverCard?.addView(coverImage)

        // Indicador de Play/Pausa central
        playIndicator = ImageView(this).apply {
            setImageResource(R.drawable.ic_play_filled)
            setColorFilter(Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#99000000"))
                shape = GradientDrawable.OVAL
            }
            setPadding(dp(9), dp(9), dp(9), dp(9))
            visibility = View.GONE
        }
        coverCard?.addView(playIndicator)

        cardContainer?.addView(coverCard)

        // 5. Visualizador Reactivo en la parte inferior (Osciloscopio Neon / Onda) alineado al ancho de la caratula
        visualizerView = VisualizerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                cardSize,
                dp(36)
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(4)
            }
            visualMode = SonoraStore.getInt(this@FloatingPlayerService, "float_visual_mode", 1) // 1 = Osciloscopio Neon
            this.isPlaying = this@FloatingPlayerService.isPlaying
            setPadding(dp(2), dp(1), dp(2), dp(1))

            setOnClickListener {
                val modes = intArrayOf(1, 0, 2, 4) // Onda, Espectro Barras, Pulso Radial, Estudio Hibrido
                val cur = modes.indexOf(visualMode)
                visualMode = modes[(cur + 1) % modes.size]
                SonoraStore.putInt(this@FloatingPlayerService, "float_visual_mode", visualMode)
                postInvalidateOnAnimation()
                wakeUpControlsTemporarily()
            }
        }
        cardContainer?.addView(visualizerView)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(cardSize, dp(3)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(4)
            }
            max = 1000
            progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#F472B6"))
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#33FFFFFF"))
        }
        cardContainer?.addView(progressBar)

        fun tbtn(res: Int, big: Boolean, action: () -> Unit) = ImageView(this).apply {
            setImageResource(res)
            setColorFilter(Color.WHITE)
            val s = if (big) dp(32) else dp(26)
            layoutParams = LinearLayout.LayoutParams(s, s).apply { marginStart = dp(8); marginEnd = dp(8) }
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#80141624"))
                shape = GradientDrawable.OVAL
            }
            setOnClickListener { action(); wakeUpControlsTemporarily() }
        }
        transportRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
            addView(tbtn(R.drawable.ic_skip_prev, false) {
                PlayerHolder.peek()?.let { pl ->
                    if (pl.currentPosition > 3000 || !pl.hasPreviousMediaItem()) pl.seekTo(pl.currentMediaItemIndex, 0L)
                    else pl.seekToPreviousMediaItem()
                }
            })
            addView(tbtn(R.drawable.ic_play_filled, true) {
                PlayerHolder.peek()?.let { pl ->
                    if (pl.isPlaying) pl.pause() else pl.play()
                    setPlayingState(pl.isPlaying)
                }
            })
            addView(tbtn(R.drawable.ic_skip_next, false) {
                PlayerHolder.peek()?.let { pl ->
                    if (pl.hasNextMediaItem()) pl.seekToNextMediaItem() else pl.seekTo(0, 0L)
                }
            })
        }
        cardContainer?.addView(transportRow)

        floatingRoot?.addView(cardContainer)
        floatingRoot?.pivotX = 0f
        floatingRoot?.pivotY = 0f
        floatingRoot?.scaleX = uiScale
        floatingRoot?.scaleY = uiScale
        windowParams = params
        uiHandler.post(progressRunnable)

        applyCoverBitmap(currentCover ?: SonoraWidgetProvider.activeCoverBitmap)

        setupTouchListener(params)
        resetControlsFadeTimer()

        try {
            windowManager?.addView(floatingRoot, params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add floating window to WindowManager", e)
        }
    }

    private fun resetControlsFadeTimer() {
        uiHandler.removeCallbacks(fadeControlsRunnable)
        miniTopBar?.alpha = 0.85f
        transportRow?.alpha = 1f
        uiHandler.postDelayed(fadeControlsRunnable, 3500)
    }

    private fun wakeUpControlsTemporarily() {
        miniTopBar?.animate()?.alpha(0.85f)?.setDuration(180)?.start()
        transportRow?.animate()?.alpha(1f)?.setDuration(180)?.start()
        resetControlsFadeTimer()
    }

    private fun updateContainerBackground() {
        val container = cardContainer ?: return
        if (isTransparentBg) {
            container.background = null
            container.elevation = 0f
        } else {
            container.background = GradientDrawable().apply {
                setColor(Color.parseColor("#D90E101A"))
                cornerRadius = dpf(22f)
                setStroke(dp(1), Color.parseColor("#33FFFFFF"))
            }
            container.clipToOutline = true
            container.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            container.elevation = dpf(8f)
        }
    }

    private fun applyCoverBitmap(bitmap: Bitmap?) {
        val bmp = bitmap ?: getFallbackCoverBitmap()
        coverImage?.setImageBitmap(bmp)
    }

    private fun getFallbackCoverBitmap(): Bitmap {
        val size = dp(126)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val grad = LinearGradient(
            0f, 0f, size.toFloat(), size.toFloat(),
            intArrayOf(Color.parseColor("#1E1B2E"), Color.parseColor("#2D1236")),
            null,
            Shader.TileMode.CLAMP
        )
        paint.shader = grad
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        paint.shader = null

        val center = size / 2f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dpf(1.2f)
        paint.color = Color.parseColor("#24FFFFFF")
        canvas.drawCircle(center, center, center * 0.84f, paint)
        canvas.drawCircle(center, center, center * 0.66f, paint)
        canvas.drawCircle(center, center, center * 0.48f, paint)

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#E11D48")
        canvas.drawCircle(center, center, center * 0.28f, paint)

        paint.color = Color.parseColor("#0F172A")
        canvas.drawCircle(center, center, center * 0.08f, paint)

        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = dpf(8f)
        paint.isFakeBoldText = true
        canvas.drawText("DDMC", center, center + dpf(2.8f), paint)

        return bmp
    }

    private fun setupTouchListener(params: WindowManager.LayoutParams) {
        val root = floatingRoot ?: return

        val scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                uiScale = (uiScale * d.scaleFactor).coerceIn(0.6f, 1.8f)
                root.scaleX = uiScale
                root.scaleY = uiScale
                params.width = (baseW * uiScale).toInt()
                params.height = (baseH * uiScale).toInt()
                try { windowManager?.updateViewLayout(root, params) } catch (_: Exception) {}
                return true
            }

            override fun onScaleEnd(d: ScaleGestureDetector) {
                SonoraStore.putFloat(this@FloatingPlayerService, "float_window_scale", uiScale)
            }
        })

        root.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isDragging = false
            private var lastTapTime = 0L

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                scaleDetector.onTouchEvent(event)
                if (scaleDetector.isInProgress || event.pointerCount > 1) {
                    isDragging = true
                    return true
                }
                val dm = resources.displayMetrics
                val maxW = (dm.widthPixels - params.width).coerceAtLeast(0)
                val maxH = (dm.heightPixels - params.height).coerceAtLeast(0)

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        wakeUpControlsTemporarily()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (hypot(dx.toDouble(), dy.toDouble()) > dp(7)) {
                            isDragging = true
                            params.x = (initialX + dx).coerceIn(-dp(16), maxW + dp(16))
                            params.y = (initialY + dy).coerceIn(0, maxH)
                            try {
                                windowManager?.updateViewLayout(floatingRoot, params)
                            } catch (_: Exception) {}
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isDragging) {
                            val now = System.currentTimeMillis()
                            if (now - lastTapTime < 340L) {
                                val openAppIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                }
                                if (openAppIntent != null) startActivity(openAppIntent)
                            } else {
                                val player = PlayerHolder.peek()
                                if (player != null && player.mediaItemCount > 0) {
                                    if (player.isPlaying) player.pause() else player.play()
                                    setPlayingState(player.isPlaying)
                                } else {
                                    sendBroadcast(Intent(SonoraWidgetProvider.ACTION_PLAY_PAUSE).apply {
                                        setPackage(packageName)
                                    })
                                }
                                showBriefPlayIndicator()
                            }
                            lastTapTime = now
                        } else {
                            SonoraStore.putInt(this@FloatingPlayerService, "float_window_x", params.x)
                            SonoraStore.putInt(this@FloatingPlayerService, "float_window_y", params.y)
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    fun setCoverBitmap(bitmap: Bitmap?) {
        applyCoverBitmap(bitmap)
    }

    fun setTrackInfo(title: String, artist: String, bitmap: Bitmap?) {
        currentTrackTitle = title
        currentArtist = artist
        applyCoverBitmap(bitmap)
    }

    fun setPlayingState(playing: Boolean) {
        isPlaying = playing
        isCurrentlyPlaying = playing
        visualizerView?.isPlaying = playing
        visualizerView?.postInvalidateOnAnimation()
    }

    fun setFft(fft: ByteArray?) {
        visualizerView?.updateFft(fft)
    }

    fun setWaveform(waveform: ByteArray?) {
        visualizerView?.updateWaveform(waveform)
    }

    private fun showBriefPlayIndicator() {
        val player = PlayerHolder.peek()
        val playingNow = player?.isPlaying ?: isPlaying
        playIndicator?.apply {
            setImageResource(if (playingNow) R.drawable.ic_pause_filled else R.drawable.ic_play_filled)
            alpha = 1.0f
            visibility = View.VISIBLE
            animate().alpha(0f).setDuration(600L).withEndAction { visibility = View.GONE }.start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        instance = null
        uiHandler.removeCallbacksAndMessages(null)
        uiHandler.removeCallbacks(progressRunnable)
        PlayerHolder.peek()?.removeListener(playerListener)
        if (floatingRoot != null) {
            try {
                windowManager?.removeView(floatingRoot)
            } catch (_: Exception) {}
            floatingRoot = null
        }
    }
}

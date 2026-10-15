package com.sonora.music

import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.load
import kotlin.math.sin

data class TrackItem(
    val title: String,
    val artist: String,
    val coverUrl: String,
    val videoUrl: String,
    val audioUrl: String
)

class VisualizerView(context: Context) : View(context) {
    private val barPaint = Paint().apply { isAntiAlias = true }
    private val wavePaint = Paint().apply {
        isAntiAlias = true
        color = Color.parseColor("#A855F7")
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }
    private var phase = 0f
    var isPlaying = false

    init {
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        if (isPlaying) {
            phase += 0.08f
        }

        val barCount = 36
        val barWidth = w / (barCount * 1.5f)
        val gap = barWidth * 0.5f

        val shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(Color.parseColor("#A855F7"), Color.parseColor("#38BDF8"), Color.parseColor("#1E3A8A")),
            null,
            Shader.TileMode.CLAMP
        )
        barPaint.shader = shader

        val path = Path()

        for (i in 0 until barCount) {
            val factor = if (isPlaying) {
                ((sin(phase + i * 0.35f) + 1.0) * 0.4 + 0.2).toFloat()
            } else {
                0.05f
            }
            val barHeight = h * factor
            val left = i * (barWidth + gap)
            val top = h - barHeight
            val right = left + barWidth
            val bottom = h

            canvas.drawRoundRect(left, top, right, bottom, 6f, 6f, barPaint)

            val waveY = h - (h * factor * 0.85f)
            val waveX = left + barWidth / 2f
            if (i == 0) path.moveTo(waveX, waveY) else path.lineTo(waveX, waveY)
        }

        canvas.drawPath(path, wavePaint)
        postInvalidateOnAnimation()
    }
}

class MainActivity : AppCompatActivity() {

    private lateinit var exoPlayer: ExoPlayer
    private lateinit var visualizerView: VisualizerView
    private lateinit var playerView: PlayerView
    private lateinit var coverImage: ImageView
    private lateinit var stageContainer: FrameLayout
    private lateinit var titleText: TextView
    private lateinit var artistText: TextView
    private lateinit var playPauseBtn: ImageButton
    private lateinit var toggleVideoBtn: Button

    private var isVideoMode = false
    private var currentTrackIndex = 0

    private val tracks = listOf(
        TrackItem(
            title = "Starboy",
            artist = "The Weeknd ft. Daft Punk",
            coverUrl = "https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?w=600&auto=format&fit=crop&q=80",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            audioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
        ),
        TrackItem(
            title = "Blinding Lights",
            artist = "The Weeknd",
            coverUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&auto=format&fit=crop&q=80",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
            audioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3"
        ),
        TrackItem(
            title = "Midnight City",
            artist = "M83",
            coverUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=600&auto=format&fit=crop&q=80",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
            audioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3"
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        exoPlayer = ExoPlayer.Builder(this).build()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0C0C0E"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(32, 48, 32, 32)
        }

        // Header Title
        val header = TextView(this).apply {
            text = "Sonora"
            textSize = 24f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 24)
        }
        root.addView(header)

        // Stage Box (Holds Video Player or Cover + Visualizer)
        stageContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                750
            )
            setBackgroundColor(Color.parseColor("#141418"))
        }

        // 1. Visualizer View
        visualizerView = VisualizerView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                380
            ).apply {
                gravity = android.view.Gravity.BOTTOM
            }
        }

        // 2. Cover Art
        coverImage = ImageView(this).apply {
            val pad = 60
            layoutParams = FrameLayout.LayoutParams(540, 540).apply {
                gravity = android.view.Gravity.CENTER
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        // 3. Video Player View
        playerView = PlayerView(this).apply {
            player = exoPlayer
            useController = true
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
        }

        stageContainer.addView(visualizerView)
        stageContainer.addView(coverImage)
        stageContainer.addView(playerView)
        root.addView(stageContainer)

        // Mode Switcher Button (Portada vs Vídeo)
        toggleVideoBtn = Button(this).apply {
            text = "Cambiar a Modo Vídeo"
            setBackgroundColor(Color.parseColor("#1C1C22"))
            setTextColor(Color.parseColor("#6366F1"))
            setOnClickListener {
                isVideoMode = !isVideoMode
                updateMode()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                setMargins(0, 24, 0, 24)
            }
        }
        root.addView(toggleVideoBtn)

        // Track Details
        titleText = TextView(this).apply {
            textSize = 20f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        }
        root.addView(titleText)

        artistText = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.parseColor("#A0A0AB"))
            setPadding(0, 4, 0, 24)
        }
        root.addView(artistText)

        // Controls (Prev, Play/Pause, Next)
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val prevBtn = Button(this).apply {
            text = "⏮"
            textSize = 22f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { playPrevious() }
        }

        playPauseBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_play)
            setBackgroundColor(Color.parseColor("#6366F1"))
            layoutParams = LinearLayout.LayoutParams(140, 140).apply {
                setMargins(32, 0, 32, 0)
            }
            setOnClickListener { togglePlayPause() }
        }

        val nextBtn = Button(this).apply {
            text = "⏭"
            textSize = 22f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { playNext() }
        }

        controls.addView(prevBtn)
        controls.addView(playPauseBtn)
        controls.addView(nextBtn)
        root.addView(controls)

        setContentView(root)
        loadTrack(0)
    }

    private fun loadTrack(index: Int) {
        currentTrackIndex = index
        val track = tracks[currentTrackIndex]
        titleText.text = track.title
        artistText.text = track.artist
        coverImage.load(track.coverUrl)

        val url = if (isVideoMode) track.videoUrl else track.audioUrl
        exoPlayer.setMediaItem(MediaItem.fromUri(url))
        exoPlayer.prepare()
        exoPlayer.play()

        visualizerView.isPlaying = true
        playPauseBtn.setImageResource(android.R.drawable.ic_media_pause)
    }

    private fun updateMode() {
        if (isVideoMode) {
            playerView.visibility = View.VISIBLE
            coverImage.visibility = View.GONE
            visualizerView.visibility = View.GONE
            toggleVideoBtn.text = "Volver a Portada & Visualizador"
        } else {
            playerView.visibility = View.GONE
            coverImage.visibility = View.VISIBLE
            visualizerView.visibility = View.VISIBLE
            toggleVideoBtn.text = "Cambiar a Modo Vídeo"
        }
        loadTrack(currentTrackIndex)
    }

    private fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
            visualizerView.isPlaying = false
            playPauseBtn.setImageResource(android.R.drawable.ic_media_play)
        } else {
            exoPlayer.play()
            visualizerView.isPlaying = true
            playPauseBtn.setImageResource(android.R.drawable.ic_media_pause)
        }
    }

    private fun playNext() {
        val next = (currentTrackIndex + 1) % tracks.size
        loadTrack(next)
    }

    private fun playPrevious() {
        val prev = if (currentTrackIndex > 0) currentTrackIndex - 1 else tracks.size - 1
        loadTrack(prev)
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer.release()
    }
}

package com.sonora.music

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*

// ============================================================================
// 1. ROTARY KNOB VIEW (Musicolet Equalizer & Hall-Effekte Rotary Dial)
// ============================================================================
class RotaryKnobView(
    context: Context,
    val label: String,
    var minValue: Float,
    var maxValue: Float,
    initialValue: Float,
    val defaultValue: Float = initialValue,
    val accentHex: String = Pal.ACCENT,
    val valueFormatter: (Float) -> String,
    val onValueChanged: (Float, Boolean) -> Unit
) : View(context) {

    var currentValue: Float = initialValue.coerceIn(minValue, maxValue)
        set(v) {
            field = v.coerceIn(minValue, maxValue)
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val knobFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val knobStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E2E8F0")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.MONOSPACE
    }
    private val arcRect = RectF()
    private var lastTouchY = 0f
    private var lastTapMs = 0L

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val accent = try { Color.parseColor(accentHex) } catch (_: Exception) { Color.parseColor(Pal.ACCENT) }
        val textAreaH = 36f * density
        val availH = (h - textAreaH).coerceAtLeast(48f * density)
        val cx = w * 0.5f
        val cy = availH * 0.52f
        val radius = (min(w, availH) * 0.38f).coerceAtLeast(22f * density)

        // Outer track arc (135 deg to 405 deg = 270 deg sweep)
        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        ringPaint.strokeWidth = 3.5f * density
        ringPaint.color = Color.parseColor("#242936")
        canvas.drawArc(arcRect, 135f, 270f, false, ringPaint)

        val norm = if (maxValue > minValue) {
            ((currentValue - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)
        } else 0f

        arcPaint.strokeWidth = 3.8f * density
        arcPaint.color = accent
        canvas.drawArc(arcRect, 135f, 270f * norm, false, arcPaint)

        // Inner dark metallic dial
        val innerR = radius * 0.80f
        knobFillPaint.shader = RadialGradient(
            cx - innerR * 0.25f,
            cy - innerR * 0.25f,
            innerR * 1.3f,
            intArrayOf(Color.parseColor("#222631"), Color.parseColor("#101218"), Color.parseColor("#090A0F")),
            floatArrayOf(0f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, innerR, knobFillPaint)

        knobStrokePaint.strokeWidth = 1.4f * density
        knobStrokePaint.color = Color.parseColor("#2E3444")
        canvas.drawCircle(cx, cy, innerR, knobStrokePaint)

        // Indicator needle line (as in Musicolet 00:45 & 00:46)
        val angleDeg = 135f + 270f * norm
        val angleRad = Math.toRadians(angleDeg.toDouble())
        val nStart = innerR * 0.28f
        val nEnd = innerR * 0.86f
        val sx = cx + cos(angleRad).toFloat() * nStart
        val sy = cy + sin(angleRad).toFloat() * nStart
        val ex = cx + cos(angleRad).toFloat() * nEnd
        val ey = cy + sin(angleRad).toFloat() * nEnd

        needlePaint.strokeWidth = 2.6f * density
        needlePaint.color = accent
        canvas.drawLine(sx, sy, ex, ey, needlePaint)

        // Labels below knob
        titlePaint.textSize = 11.5f * density
        valuePaint.textSize = 11f * density
        valuePaint.color = accent
        canvas.drawText(label, cx, availH + 14f * density, titlePaint)
        canvas.drawText(valueFormatter(currentValue), cx, availH + 30f * density, valuePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val w = width.toFloat()
        val h = height.toFloat()
        val textAreaH = 36f * density
        val cx = w * 0.5f
        val cy = (h - textAreaH) * 0.52f

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val now = System.currentTimeMillis()
                if (now - lastTapMs < 280L) {
                    currentValue = defaultValue
                    onValueChanged(currentValue, true)
                    return true
                }
                lastTapMs = now
                lastTouchY = event.y
                updateFromAngleOrDrag(event.x, event.y, cx, cy, isMove = false)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                updateFromAngleOrDrag(event.x, event.y, cx, cy, isMove = true)
                lastTouchY = event.y
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateFromAngleOrDrag(tx: Float, ty: Float, cx: Float, cy: Float, isMove: Boolean) {
        val dx = tx - cx
        val dy = ty - cy
        val dist = hypot(dx, dy)
        if (dist > 14f * density) {
            var deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
            if (deg < 0f) deg += 360f
            // Map [135..360, 0..45] to [0..270]
            val rel = when {
                deg >= 135f -> deg - 135f
                deg <= 45f -> (deg + 360f) - 135f
                deg < 90f -> 270f
                else -> 0f
            }.coerceIn(0f, 270f)
            val ratio = rel / 270f
            currentValue = minValue + ratio * (maxValue - minValue)
            onValueChanged(currentValue, true)
        } else if (isMove) {
            val deltaY = lastTouchY - ty
            val span = (maxValue - minValue).coerceAtLeast(1f)
            currentValue = (currentValue + (deltaY / (140f * density)) * span).coerceIn(minValue, maxValue)
            onValueChanged(currentValue, true)
        }
    }
}

// ============================================================================
// 2. MUSICOLET 3-TAB PROFESSIONAL DSP EQUALIZER (Video 00:44 - 00:46)
// ============================================================================
object MusicoletEqualizerBuilder {

    val ISO_31_FREQS = arrayOf(
        "20", "25", "31.5", "40", "50", "63", "80", "100", "125", "160",
        "200", "250", "315", "400", "500", "630", "800", "1 k", "1.3 k", "1.6 k",
        "2 k", "2.5 k", "3.2 k", "4 k", "5 k", "6.3 k", "8 k", "10 k", "12.5 k", "16 k", "20 k"
    )

    val PRESETS_31: Map<String, FloatArray> = linkedMapOf(
        "Full Bass" to floatArrayOf(
            10f, 10f, 10f, 10f, 10f, 10f, 9.8f, 9.2f, 8.5f, 7.6f,
            6.5f, 5.0f, 3.9f, 2.7f, 1.3f, -0.5f, -2.9f, -5.8f, -5.9f, -5.7f,
            -5.2f, -5.2f, -4.8f, -4.5f, -4.3f, -3.5f, -2.0f, -1.0f, 0.5f, 1.5f, 2.0f
        ),
        "Plano (0 dB)" to FloatArray(31) { 0f },
        "Rock" to floatArrayOf(
            5.5f, 5.2f, 4.8f, 4.2f, 3.5f, 2.5f, 1.2f, 0.0f, -1.2f, -2.0f,
            -2.4f, -2.0f, -1.0f, 0.0f, 1.2f, 2.2f, 3.0f, 3.8f, 4.4f, 4.8f,
            5.2f, 5.5f, 5.8f, 6.0f, 6.0f, 5.8f, 5.5f, 5.2f, 5.0f, 4.8f, 4.5f
        ),
        "Pop" to floatArrayOf(
            -1.5f, -1.0f, 0.0f, 1.5f, 2.8f, 3.8f, 4.5f, 4.8f, 4.5f, 3.8f,
            2.8f, 1.8f, 1.0f, 0.5f, 0.0f, 0.5f, 1.5f, 2.5f, 3.2f, 3.5f,
            3.2f, 2.5f, 1.8f, 1.0f, 0.5f, 0.0f, -0.5f, -1.0f, -1.2f, -1.5f, -1.5f
        ),
        "Bajo Extremo" to floatArrayOf(
            12f, 12f, 11.5f, 11f, 10.5f, 9.5f, 8.5f, 7.0f, 5.0f, 3.0f,
            1.0f, 0.0f, -1.0f, -1.5f, -1.5f, -1.0f, 0.0f, 0.5f, 1.0f, 1.5f,
            2.0f, 2.5f, 3.0f, 3.2f, 3.5f, 3.5f, 3.0f, 2.5f, 2.0f, 1.5f, 1.0f
        ),
        "Vocal / Audiolibro" to floatArrayOf(
            -6f, -6f, -5.5f, -5f, -4f, -3f, -2f, -1f, 0f, 1.5f,
            2.8f, 4.0f, 5.0f, 5.8f, 6.2f, 6.5f, 6.5f, 6.0f, 5.2f, 4.5f,
            3.5f, 2.5f, 1.5f, 0.5f, 0.0f, -1.0f, -2.0f, -3.0f, -4.0f, -5.0f, -5.5f
        ),
        "Electrónica" to floatArrayOf(
            6.5f, 6.2f, 5.8f, 5.2f, 4.5f, 3.5f, 2.0f, 0.5f, -1.0f, -2.0f,
            -2.5f, -2.0f, -1.0f, 0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 4.8f, 5.5f,
            6.0f, 6.2f, 6.5f, 6.5f, 6.2f, 6.0f, 5.8f, 5.5f, 5.2f, 5.0f, 4.8f
        ),
        "Clásica / Hi-Fi" to floatArrayOf(
            4.0f, 3.8f, 3.5f, 3.0f, 2.5f, 2.0f, 1.5f, 1.0f, 0.5f, 0.0f,
            0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.5f, 1.0f, 1.5f, 2.0f,
            2.5f, 3.0f, 3.5f, 3.8f, 4.0f, 4.2f, 4.2f, 4.0f, 3.8f, 3.5f, 3.2f
        )
    )

    private fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density).toInt()
    private fun dpf(c: Context, v: Float): Float = v * c.resources.displayMetrics.density

    fun build(activity: Activity, onSyncHardwareEq: () -> Unit): View {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Top Musicolet Equalizer Header Bar: Output Device + Master Switch
        val topBar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_card_spotiflac)
            setPadding(dp(activity, 14), dp(activity, 10), dp(activity, 14), dp(activity, 10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dp(activity, 14), dp(activity, 8), dp(activity, 14), dp(activity, 6))
            }
        }

        val profiles = arrayOf("Altavoz integrado", "Auriculares con cable", "Audio Bluetooth", "DAC USB Hi-Res")
        var curProfileIdx = SonoraStore.getInt(activity, "eq_output_profile", 0).coerceIn(0, profiles.lastIndex)

        val profileBtn = TextView(activity).apply {
            text = "\u25C9  ${profiles[curProfileIdx]}  \u25BE"
            textSize = 12.5f
            setTextColor(Color.parseColor("#38BDF8"))
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setPadding(dp(activity, 12), dp(activity, 6), dp(activity, 12), dp(activity, 6))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1438BDF8"))
                cornerRadius = dpf(activity, 8f)
                setStroke(dp(activity, 1), Color.parseColor("#4438BDF8"))
            }
            setOnClickListener {
                curProfileIdx = (curProfileIdx + 1) % profiles.size
                SonoraStore.putInt(activity, "eq_output_profile", curProfileIdx)
                text = "\u25C9  ${profiles[curProfileIdx]}  \u25BE"
                Toast.makeText(activity, "Perfil activo: ${profiles[curProfileIdx]}", Toast.LENGTH_SHORT).show()
            }
        }
        topBar.addView(profileBtn)

        topBar.addView(View(activity).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        })

        val masterSwitch = Switch(activity).apply {
            isChecked = SonoraStore.getBool(activity, "eq_enabled", true)
            thumbTintList = ColorStateList.valueOf(Color.parseColor(Pal.ACCENT))
            setOnCheckedChangeListener { _, checked ->
                SonoraStore.putBool(activity, "eq_enabled", checked)
                AudioFx.setEnabled(checked)
                onSyncHardwareEq()
            }
        }
        topBar.addView(masterSwitch)
        root.addView(topBar)

        // Content Frame for the 3 Sub-tabs
        val contentFrame = FrameLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val tab1View = buildSubTab1Bands(activity, onSyncHardwareEq)
        val tab2View = buildSubTab2KnobsAndBalance(activity)
        val tab3View = buildSubTab3HallReverb(activity)

        contentFrame.addView(tab1View)
        contentFrame.addView(tab2View)
        contentFrame.addView(tab3View)
        root.addView(contentFrame)

        // Bottom 3-Tab Switcher Bar (Identical to Musicolet 00:44 - 00:46 bottom bar)
        val subNav = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0A0C10"))
            setPadding(dp(activity, 12), dp(activity, 6), dp(activity, 12), dp(activity, 88))
        }

        val subTabBtns = ArrayList<LinearLayout>()
        val subTabLabels = listOf(
            "31 Bandas EQ" to R.drawable.ic_sliders,
            "Graves / Balance" to R.drawable.ic_disc,
            "Efectos de Sala" to R.drawable.ic_waveform
        )

        fun selectEqSubTab(idx: Int) {
            tab1View.visibility = if (idx == 0) View.VISIBLE else View.GONE
            tab2View.visibility = if (idx == 1) View.VISIBLE else View.GONE
            tab3View.visibility = if (idx == 2) View.VISIBLE else View.GONE
            subTabBtns.forEachIndexed { i, b ->
                val active = i == idx
                val col = Color.parseColor(if (active) Pal.ACCENT else "#64748B")
                (b.getChildAt(0) as ImageView).setColorFilter(col)
                (b.getChildAt(1) as TextView).setTextColor(col)
                b.background = if (active) {
                    GradientDrawable().apply {
                        setColor(Color.parseColor(Pal.ACCENT) and 0x00FFFFFF or 0x18000000)
                        cornerRadius = dpf(activity, 10f)
                        setStroke(dp(activity, 1), Color.parseColor(Pal.ACCENT) and 0x00FFFFFF or 0x44000000)
                    }
                } else null
            }
        }

        subTabLabels.forEachIndexed { idx, (label, iconRes) ->
            val btn = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(dp(activity, 10), dp(activity, 8), dp(activity, 10), dp(activity, 8))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = if (idx < 2) dp(activity, 6) else 0
                }
                addView(ImageView(activity).apply {
                    setImageResource(iconRes)
                    layoutParams = LinearLayout.LayoutParams(dp(activity, 16), dp(activity, 16)).apply {
                        marginEnd = dp(activity, 6)
                    }
                })
                addView(TextView(activity).apply {
                    text = label
                    textSize = 11.5f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
                })
                setOnClickListener { selectEqSubTab(idx) }
            }
            subTabBtns.add(btn)
            subNav.addView(btn)
        }

        root.addView(subNav)
        selectEqSubTab(0)
        return root
    }

    // ---- Sub-tab 1: 31-Band ISO Equalizer (Video 00:44) ----
    private fun buildSubTab1Bands(activity: Activity, onSyncHardwareEq: () -> Unit): View {
        val scroll = ScrollView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 14), dp(activity, 4), dp(activity, 14), dp(activity, 20))
        }

        val bandCounts = intArrayOf(31, 15, 10, 5)
        var currentBandCount = SonoraStore.getInt(activity, "eq_band_count_mode", 31)
        if (currentBandCount !in bandCounts) currentBandCount = 31

        var currentPresetName = SonoraStore.getString(activity, "eq_preset_name", "Full Bass")
        val sliderViews = ArrayList<SeekBar>()
        val dbLabels = ArrayList<TextView>()
        val bandsHost = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card_spotiflac)
            setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10))
        }

        fun getBandGainDb(idx31: Int): Float {
            val defaultArr = PRESETS_31["Full Bass"]!!
            return SonoraStore.getFloat(activity, "eq31_band_$idx31", defaultArr[idx31])
        }

        fun setBandGainDb(idx31: Int, db: Float) {
            SonoraStore.putFloat(activity, "eq31_band_$idx31", db.coerceIn(-15f, 15f))
        }

        fun push31ToHardware() {
            val eq = AudioFx.equalizer ?: return
            try {
                val hwBands = eq.numberOfBands.toInt().coerceAtLeast(1)
                val minMb = eq.bandLevelRange[0].toInt()
                val maxMb = eq.bandLevelRange[1].toInt()
                for (b in 0 until hwBands) {
                    val mapped31 = ((b.toFloat() / (hwBands - 1).coerceAtLeast(1)) * 30).toInt().coerceIn(0, 30)
                    val targetMb = (getBandGainDb(mapped31) * 100f).toInt().coerceIn(minMb, maxMb)
                    eq.setBandLevel(b.toShort(), targetMb.toShort())
                }
                AudioFx.saveBands(activity)
            } catch (_: Exception) {}
            onSyncHardwareEq()
        }

        fun rebuildBandRows() {
            bandsHost.removeAllViews()
            sliderViews.clear()
            dbLabels.clear()

            val indices: List<Int> = when (currentBandCount) {
                5 -> listOf(3, 9, 16, 22, 28)
                10 -> listOf(1, 4, 7, 10, 13, 16, 19, 22, 25, 29)
                15 -> (0..30 step 2).toList()
                else -> (0..30).toList()
            }

            for (idx31 in indices) {
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(activity, 3), 0, dp(activity, 3))
                }
                val freqTv = TextView(activity).apply {
                    text = ISO_31_FREQS[idx31]
                    textSize = 11.5f
                    setTextColor(Color.parseColor("#CBD5E1"))
                    typeface = Typeface.MONOSPACE
                    layoutParams = LinearLayout.LayoutParams(dp(activity, 46), LinearLayout.LayoutParams.WRAP_CONTENT)
                }
                val curDb = getBandGainDb(idx31)
                val valTv = TextView(activity).apply {
                    text = String.format(Locale.US, "%+.1f", curDb)
                    textSize = 11.5f
                    setTextColor(Color.parseColor("#CBD5E1"))
                    typeface = Typeface.MONOSPACE
                    gravity = Gravity.END
                    layoutParams = LinearLayout.LayoutParams(dp(activity, 44), LinearLayout.LayoutParams.WRAP_CONTENT)
                }
                val seek = SeekBar(activity).apply {
                    max = 300 // -15.0 dB to +15.0 dB in 0.1 dB steps
                    progress = ((curDb + 15f) * 10f).toInt().coerceIn(0, 300)
                    val tint = ColorStateList.valueOf(Color.parseColor("#38BDF8"))
                    progressTintList = tint
                    thumbTintList = ColorStateList.valueOf(Color.parseColor("#E2E8F0"))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                            val db = (p - 150) / 10f
                            valTv.text = String.format(Locale.US, "%+.1f", db)
                            if (fromUser) {
                                setBandGainDb(idx31, db)
                                push31ToHardware()
                            }
                        }
                        override fun onStartTrackingTouch(sb: SeekBar?) {}
                        override fun onStopTrackingTouch(sb: SeekBar?) {}
                    })
                }
                sliderViews.add(seek)
                dbLabels.add(valTv)
                row.addView(freqTv)
                row.addView(seek)
                row.addView(valTv)
                bandsHost.addView(row)
            }
        }

        // Band Count & Preset Selector Row (Exactly like Musicolet 00:44: [ 31 ] [ Full Bass ])
        val controlsRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(activity, 4), 0, dp(activity, 10))
        }

        val bandCountBtn = TextView(activity).apply {
            text = "Bandas: $currentBandCount \u25BE"
            textSize = 12f
            setTextColor(Color.WHITE)
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setPadding(dp(activity, 12), dp(activity, 8), dp(activity, 12), dp(activity, 8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#181B24"))
                cornerRadius = dpf(activity, 8f)
                setStroke(dp(activity, 1), Color.parseColor("#2E3444"))
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(activity, 8)
            }
            setOnClickListener {
                val nextIdx = (bandCounts.indexOf(currentBandCount) + 1) % bandCounts.size
                currentBandCount = bandCounts[nextIdx]
                SonoraStore.putInt(activity, "eq_band_count_mode", currentBandCount)
                text = "Bandas: $currentBandCount \u25BE"
                rebuildBandRows()
            }
        }

        val presetNames = PRESETS_31.keys.toList()
        val presetBtn = TextView(activity).apply {
            text = "$currentPresetName  \u25BE"
            textSize = 12f
            setTextColor(Color.parseColor(Pal.ACCENT))
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setPadding(dp(activity, 14), dp(activity, 8), dp(activity, 14), dp(activity, 8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor(Pal.ACCENT) and 0x00FFFFFF or 0x14000000)
                cornerRadius = dpf(activity, 8f)
                setStroke(dp(activity, 1), Color.parseColor(Pal.ACCENT) and 0x00FFFFFF or 0x44000000)
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(activity, 8)
            }
            setOnClickListener {
                val nextIdx = (presetNames.indexOf(currentPresetName) + 1) % presetNames.size
                currentPresetName = presetNames[nextIdx]
                SonoraStore.putString(activity, "eq_preset_name", currentPresetName)
                text = "$currentPresetName  \u25BE"
                val curve = PRESETS_31[currentPresetName] ?: FloatArray(31)
                for (i in 0..30) setBandGainDb(i, curve[i])
                rebuildBandRows()
                push31ToHardware()
            }
        }

        val resetFlatBtn = TextView(activity).apply {
            text = "0 dB"
            textSize = 11.5f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(dp(activity, 10), dp(activity, 8), dp(activity, 10), dp(activity, 8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#181B24"))
                cornerRadius = dpf(activity, 8f)
                setStroke(dp(activity, 1), Color.parseColor("#2E3444"))
            }
            setOnClickListener {
                currentPresetName = "Plano (0 dB)"
                SonoraStore.putString(activity, "eq_preset_name", currentPresetName)
                presetBtn.text = "$currentPresetName  \u25BE"
                for (i in 0..30) setBandGainDb(i, 0f)
                rebuildBandRows()
                push31ToHardware()
            }
        }

        controlsRow.addView(bandCountBtn)
        controlsRow.addView(presetBtn)
        controlsRow.addView(resetFlatBtn)
        container.addView(controlsRow)

        rebuildBandRows()
        container.addView(bandsHost)
        scroll.addView(container)
        return scroll
    }

    // ---- Sub-tab 2: Bass Boost, Surround, Preamp, Volume, Stereo/Mono & Audio Balance (Video 00:45) ----
    private fun buildSubTab2KnobsAndBalance(activity: Activity): View {
        val scroll = ScrollView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 14), dp(activity, 6), dp(activity, 14), dp(activity, 20))
        }

        // Top Row: 2 Rotary Knobs (Refuerzo de graves & Sonido envolvente)
        val topKnobsCard = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_card_spotiflac)
            setPadding(dp(activity, 12), dp(activity, 14), dp(activity, 12), dp(activity, 14))
        }

        val initBassPct = (SonoraStore.getInt(activity, "bass", 0) / 10f).coerceIn(0f, 100f)
        val bassKnob = RotaryKnobView(
            context = activity,
            label = "Refuerzo de graves",
            minValue = 0f,
            maxValue = 100f,
            initialValue = initBassPct,
            defaultValue = 0f,
            accentHex = "#38BDF8",
            valueFormatter = { "${it.toInt()}%" }
        ) { pct, _ ->
            val strength = (pct * 10f).toInt().coerceIn(0, 1000)
            SonoraStore.putInt(activity, "bass", strength)
            try { AudioFx.bass?.setStrength(strength.toShort()) } catch (_: Exception) {}
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(activity, 165), 1f)
        }

        val initVirtPct = (SonoraStore.getInt(activity, "virt", 0) / 10f).coerceIn(0f, 100f)
        val surroundKnob = RotaryKnobView(
            context = activity,
            label = "Sonido envolvente",
            minValue = 0f,
            maxValue = 100f,
            initialValue = initVirtPct,
            defaultValue = 0f,
            accentHex = "#38BDF8",
            valueFormatter = { "${it.toInt()}%" }
        ) { pct, _ ->
            val strength = (pct * 10f).toInt().coerceIn(0, 1000)
            SonoraStore.putInt(activity, "virt", strength)
            try { AudioFx.virtualizer?.setStrength(strength.toShort()) } catch (_: Exception) {}
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(activity, 165), 1f)
        }

        topKnobsCard.addView(bassKnob)
        topKnobsCard.addView(surroundKnob)
        container.addView(topKnobsCard)

        // Middle Card: Preamplificador (Vorverstärkung: Headroom -15 dB | 0 dB | Impulso +2 dB) + Lautstärke
        val preampCard = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card_spotiflac)
            setPadding(dp(activity, 16), dp(activity, 14), dp(activity, 16), dp(activity, 14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(activity, 10) }
        }

        var curPreampDb = SonoraStore.getFloat(activity, "preamp_db", 0f)
        val preampTitle = TextView(activity).apply {
            text = String.format(Locale.US, "Preamplificador (%+.1f dB)", curPreampDb)
            textSize = 13f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setPadding(0, 0, 0, dp(activity, 8))
        }
        preampCard.addView(preampTitle)

        val quickPreampRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(activity, 8))
        }
        lateinit var preampSeek: SeekBar

        fun setPreamp(db: Float) {
            curPreampDb = db.coerceIn(-15f, 15f)
            SonoraStore.putFloat(activity, "preamp_db", curPreampDb)
            val gainMb = (curPreampDb * 100f).toInt()
            SonoraStore.putInt(activity, "gain", gainMb.coerceAtLeast(0))
            try { AudioFx.loudness?.setTargetGain(gainMb) } catch (_: Exception) {}
            preampTitle.text = String.format(Locale.US, "Preamplificador (%+.1f dB)", curPreampDb)
            preampSeek.progress = ((curPreampDb + 15f) * 10f).toInt().coerceIn(0, 300)
        }

        val presetsPreamp = listOf(
            "Headroom\n-15 dB" to -15f,
            "Apagado\n0 dB" to 0f,
            "Impulso\n+2 dB" to 2f
        )
        for ((lbl, targetDb) in presetsPreamp) {
            val btn = TextView(activity).apply {
                text = lbl
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#E2E8F0"))
                setPadding(dp(activity, 14), dp(activity, 6), dp(activity, 14), dp(activity, 6))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#181B24"))
                    cornerRadius = dpf(activity, 8f)
                    setStroke(dp(activity, 1), Color.parseColor("#38BDF8"))
                }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(activity, 6)
                }
                setOnClickListener { setPreamp(targetDb) }
            }
            quickPreampRow.addView(btn)
        }
        preampCard.addView(quickPreampRow)

        preampSeek = SeekBar(activity).apply {
            max = 300
            progress = ((curPreampDb + 15f) * 10f).toInt().coerceIn(0, 300)
            val tint = ColorStateList.valueOf(Color.parseColor("#38BDF8"))
            progressTintList = tint; thumbTintList = tint
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) setPreamp((p - 150) / 10f)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        preampCard.addView(preampSeek)

        // System Volume (Lautstärke)
        val audioManager = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxSysVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val curSysVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        val volTitle = TextView(activity).apply {
            text = "Volumen del sistema (${(curSysVol * 100) / maxSysVol}%)"
            textSize = 12.5f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dp(activity, 10), 0, dp(activity, 4))
        }
        preampCard.addView(volTitle)
        preampCard.addView(SeekBar(activity).apply {
            max = maxSysVol
            progress = curSysVol
            val tint = ColorStateList.valueOf(Color.parseColor("#38BDF8"))
            progressTintList = tint; thumbTintList = tint
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) {
                        try { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, p, 0) } catch (_: Exception) {}
                        volTitle.text = "Volumen del sistema (${(p * 100) / maxSysVol}%)"
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })
        container.addView(preampCard)

        // Bottom Card: Estéreo / Mono + Audio-Balance Rotary Knob (Video 00:45)
        val balanceCard = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_card_spotiflac)
            setPadding(dp(activity, 16), dp(activity, 14), dp(activity, 16), dp(activity, 14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(activity, 10) }
        }

        val stereoMonoCol = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(activity, 110), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        var isMono = SonoraStore.getBool(activity, "audio_mono", false)
        lateinit var stereoBtn: TextView
        lateinit var monoBtn: TextView

        fun updateStereoMonoUi() {
            stereoBtn.background = GradientDrawable().apply {
                setColor(Color.parseColor(if (!isMono) "#2238BDF8" else "#141720"))
                cornerRadius = dpf(activity, 8f)
                setStroke(dp(activity, 1), Color.parseColor(if (!isMono) "#38BDF8" else "#2E3444"))
            }
            stereoBtn.setTextColor(Color.parseColor(if (!isMono) "#38BDF8" else "#94A3B8"))
            monoBtn.background = GradientDrawable().apply {
                setColor(Color.parseColor(if (isMono) "#2238BDF8" else "#141720"))
                cornerRadius = dpf(activity, 8f)
                setStroke(dp(activity, 1), Color.parseColor(if (isMono) "#38BDF8" else "#2E3444"))
            }
            monoBtn.setTextColor(Color.parseColor(if (isMono) "#38BDF8" else "#94A3B8"))
        }

        stereoBtn = TextView(activity).apply {
            text = "Estéreo"
            textSize = 12.5f
            gravity = Gravity.CENTER
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setPadding(dp(activity, 14), dp(activity, 10), dp(activity, 14), dp(activity, 10))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(activity, 8)
            }
            setOnClickListener {
                isMono = false
                SonoraStore.putBool(activity, "audio_mono", false)
                updateStereoMonoUi()
            }
        }
        monoBtn = TextView(activity).apply {
            text = "Mono"
            textSize = 12.5f
            gravity = Gravity.CENTER
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setPadding(dp(activity, 14), dp(activity, 10), dp(activity, 14), dp(activity, 10))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                isMono = true
                SonoraStore.putBool(activity, "audio_mono", true)
                updateStereoMonoUi()
            }
        }
        updateStereoMonoUi()
        stereoMonoCol.addView(stereoBtn)
        stereoMonoCol.addView(monoBtn)
        balanceCard.addView(stereoMonoCol)

        val initPan = SonoraStore.getFloat(activity, "audio_balance_pan", 0f) // -100 to +100
        val balanceKnob = RotaryKnobView(
            context = activity,
            label = "Balance de Audio",
            minValue = -100f,
            maxValue = 100f,
            initialValue = initPan,
            defaultValue = 0f,
            accentHex = "#38BDF8",
            valueFormatter = { pan ->
                val l = if (pan <= 0f) 100 else (100f - pan).toInt().coerceIn(0, 100)
                val r = if (pan >= 0f) 100 else (100f + pan).toInt().coerceIn(0, 100)
                "Izq:$l, Der:$r"
            }
        ) { pan, _ ->
            SonoraStore.putFloat(activity, "audio_balance_pan", pan)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(activity, 170), 1f)
        }
        balanceCard.addView(balanceKnob)
        container.addView(balanceCard)

        scroll.addView(container)
        return scroll
    }

    // ---- Sub-tab 3: Hall-Effekte / Room Reverb 8-Knob Matrix (Video 00:46) ----
    private fun buildSubTab3HallReverb(activity: Activity): View {
        val scroll = ScrollView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 14), dp(activity, 6), dp(activity, 14), dp(activity, 20))
        }

        // Top Header Card: Checkbox [x] Efectos de sala (Hall-Effekte) + Preset Selector
        val headerCard = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card_spotiflac)
            setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12))
        }

        val checkRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val hallCheck = CheckBox(activity).apply {
            text = "Efectos de sala (Hall-Effekte / Reverb)"
            textSize = 13.5f
            setTextColor(Color.WHITE)
            buttonTintList = ColorStateList.valueOf(Color.parseColor("#38BDF8"))
            isChecked = SonoraStore.getBool(activity, "hall_reverb_enabled", false)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnCheckedChangeListener { _, checked ->
                SonoraStore.putBool(activity, "hall_reverb_enabled", checked)
            }
        }
        checkRow.addView(hallCheck)
        headerCard.addView(checkRow)

        data class HallPreset(
            val name: String,
            val roomLevel: Float,
            val roomHf: Float,
            val decayMs: Float,
            val decayHfMs: Float,
            val reflLevel: Float,
            val reflDelayMs: Float,
            val reverbLevel: Float,
            val reverbDelayMs: Float
        )

        val hallPresets = listOf(
            HallPreset("Individual", -87.7f, 0.0f, 6168f, 420f, 9.7f, 0f, -66.0f, 0f),
            HallPreset("Sala Pequeña (Small Room)", -20.0f, -6.0f, 1100f, 830f, -4.0f, 5f, 2.0f, 10f),
            HallPreset("Sala Mediana (Medium Room)", -20.0f, -6.0f, 1300f, 830f, -10.0f, 20f, -2.0f, 20f),
            HallPreset("Sala Grande (Large Room)", -20.0f, -6.0f, 1500f, 830f, -16.0f, 40f, -10.0f, 40f),
            HallPreset("Auditorio Mediano (Medium Hall)", -20.0f, -6.0f, 1800f, 700f, -13.0f, 15f, -8.0f, 30f),
            HallPreset("Gran Auditorio (Large Hall)", -20.0f, -6.0f, 3500f, 500f, -20.0f, 20f, -6.0f, 60f),
            HallPreset("Placa de Estudio (Plate)", -15.0f, -2.0f, 2400f, 1300f, -2.0f, 10f, 0.0f, 15f)
        )

        var curHallIdx = SonoraStore.getInt(activity, "hall_preset_idx", 0).coerceIn(0, hallPresets.lastIndex)
        val knobsList = ArrayList<RotaryKnobView>()

        val presetRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(activity, 8), 0, 0)
        }
        val hallPresetBtn = TextView(activity).apply {
            text = "${hallPresets[curHallIdx].name}  \u25BE"
            textSize = 12f
            setTextColor(Color.parseColor("#38BDF8"))
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setPadding(dp(activity, 12), dp(activity, 8), dp(activity, 12), dp(activity, 8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#181B24"))
                cornerRadius = dpf(activity, 8f)
                setStroke(dp(activity, 1), Color.parseColor("#38BDF8"))
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                curHallIdx = (curHallIdx + 1) % hallPresets.size
                SonoraStore.putInt(activity, "hall_preset_idx", curHallIdx)
                val p = hallPresets[curHallIdx]
                text = "${p.name}  \u25BE"
                val vals = floatArrayOf(
                    p.roomLevel, p.roomHf, p.decayMs, p.decayHfMs,
                    p.reflLevel, p.reflDelayMs, p.reverbLevel, p.reverbDelayMs
                )
                for (i in vals.indices) {
                    SonoraStore.putFloat(activity, "hall_param_$i", vals[i])
                    knobsList.getOrNull(i)?.currentValue = vals[i]
                }
            }
        }
        presetRow.addView(hallPresetBtn)
        headerCard.addView(presetRow)
        container.addView(headerCard)

        // 8 Rotary Knobs in 4 rows of 2 (Identical to Musicolet 00:46)
        data class KnobSpec(val idx: Int, val label: String, val min: Float, val max: Float, val def: Float, val fmt: (Float) -> String)

        val specs = listOf(
            KnobSpec(0, "Room level", -100f, 0f, -87.7f) { String.format(Locale.US, "%.1f dB", it) },
            KnobSpec(1, "Room HF level", -100f, 0f, 0.0f) { String.format(Locale.US, "%.1f dB", it) },
            KnobSpec(2, "Decay time", 100f, 20000f, 6168f) { "${it.toInt()} ms" },
            KnobSpec(3, "Decay HF ratio", 100f, 2000f, 420f) { "${it.toInt()} ms" },
            KnobSpec(4, "Reflections Level", -100f, 20f, 9.7f) { String.format(Locale.US, "%+.1f dB", it) },
            KnobSpec(5, "Reflection delay", 0f, 300f, 0f) { "${it.toInt()} ms" },
            KnobSpec(6, "Nivel Reverb (Hall-Pegel)", -100f, 20f, -66.0f) { String.format(Locale.US, "%.1f dB", it) },
            KnobSpec(7, "Retardo Reverb", 0f, 100f, 0f) { "${it.toInt()} ms" }
        )

        val gridCard = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card_spotiflac)
            setPadding(dp(activity, 10), dp(activity, 12), dp(activity, 10), dp(activity, 12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(activity, 10) }
        }

        for (rowIdx in 0 until 4) {
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, dp(activity, 4), 0, dp(activity, 4))
            }
            for (colIdx in 0..1) {
                val spec = specs[rowIdx * 2 + colIdx]
                val initVal = SonoraStore.getFloat(activity, "hall_param_${spec.idx}", spec.def)
                val knob = RotaryKnobView(
                    context = activity,
                    label = spec.label,
                    minValue = spec.min,
                    maxValue = spec.max,
                    initialValue = initVal,
                    defaultValue = spec.def,
                    accentHex = "#38BDF8",
                    valueFormatter = spec.fmt
                ) { v, _ ->
                    SonoraStore.putFloat(activity, "hall_param_${spec.idx}", v)
                }.apply {
                    layoutParams = LinearLayout.LayoutParams(0, dp(activity, 152), 1f)
                }
                knobsList.add(knob)
                row.addView(knob)
            }
            gridCard.addView(row)
        }

        container.addView(gridCard)
        scroll.addView(container)
        return scroll
    }
}

// ============================================================================
// 3. MUSICOLET HOMESCREEN WIDGET CUSTOMIZER (Video 01:08 - 01:09)
// ============================================================================
object MusicoletWidgetCustomizerBuilder {

    private fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density).toInt()
    private fun dpf(c: Context, v: Float): Float = v * c.resources.displayMetrics.density

    fun build(activity: Activity, currentTrackProvider: () -> TrackItem?): ScrollView {
        val scroll = ScrollView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 14), dp(activity, 8), dp(activity, 14), dp(activity, 95))
        }

        // Top Simulated Home Screen Wallpaper Stage with Live Musicolet Widget Preview (Video 01:08)
        val previewStage = FrameLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.parseColor("#2A1B4E"),
                    Color.parseColor("#181332"),
                    Color.parseColor("#3B1C32")
                )
            ).apply {
                cornerRadius = dpf(activity, 18f)
                setStroke(dp(activity, 1), Color.parseColor("#33FFFFFF"))
            }
            setPadding(dp(activity, 14), dp(activity, 20), dp(activity, 14), dp(activity, 20))
        }

        val previewWidgetCard = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }

        // Top Row inside Widget Preview
        val previewTopRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val previewControlsLeft = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        fun makeSmallIcon(resId: Int): ImageView = ImageView(activity).apply {
            setImageResource(resId)
            setColorFilter(Color.WHITE)
            val p = dp(activity, 5)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(dp(activity, 28), dp(activity, 28)).apply {
                marginEnd = dp(activity, 2)
            }
        }

        val prevIcon = makeSmallIcon(R.drawable.ic_skip_prev)
        val rewIcon = makeSmallIcon(R.drawable.ic_refresh)
        val playIcon = makeSmallIcon(R.drawable.ic_play_filled).apply {
            layoutParams = LinearLayout.LayoutParams(dp(activity, 32), dp(activity, 32)).apply {
                marginEnd = dp(activity, 2)
            }
        }
        val ffIcon = makeSmallIcon(R.drawable.ic_bolt)
        val nextIcon = makeSmallIcon(R.drawable.ic_skip_next)

        previewControlsLeft.addView(prevIcon)
        previewControlsLeft.addView(rewIcon)
        previewControlsLeft.addView(playIcon)
        previewControlsLeft.addView(ffIcon)
        previewControlsLeft.addView(nextIcon)

        val previewCover = ImageView(activity).apply {
            setImageResource(R.drawable.ic_music)
            scaleType = ImageView.ScaleType.FIT_CENTER
            val p = dp(activity, 4)
            setPadding(p, p, p, p)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#22000000"))
                cornerRadius = dpf(activity, 10f)
            }
            layoutParams = LinearLayout.LayoutParams(dp(activity, 52), dp(activity, 52)).apply {
                marginStart = dp(activity, 6)
                marginEnd = dp(activity, 8)
            }
        }

        val previewMeta = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val previewBadge = TextView(activity).apply {
            text = "DDMC \u2022 HI-RES"
            textSize = 9f
            setTypeface(null, Typeface.BOLD)
            maxLines = 1
        }
        val previewTitle = TextView(activity).apply {
            text = "Doki Doki Music Club"
            textSize = 13f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setTextColor(Color.WHITE)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val previewArtist = TextView(activity).apply {
            text = "DDMC \u2022 Audio, GIF & Video"
            textSize = 11f
            setTextColor(Color.parseColor("#CBD5E1"))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        previewMeta.addView(previewBadge)
        previewMeta.addView(previewTitle)
        previewMeta.addView(previewArtist)

        val previewFavIcon = makeSmallIcon(R.drawable.ic_heart_filled)
        val previewPlaylistIcon = makeSmallIcon(R.drawable.ic_list_plus)

        previewTopRow.addView(previewControlsLeft)
        previewTopRow.addView(previewCover)
        previewTopRow.addView(previewMeta)
        previewTopRow.addView(previewFavIcon)
        previewTopRow.addView(previewPlaylistIcon)
        previewWidgetCard.addView(previewTopRow)

        val previewProgress = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 42
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(activity, 4)
            ).apply { topMargin = dp(activity, 8) }
        }
        previewWidgetCard.addView(previewProgress)

        val previewQueueBox = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(activity, 8), 0, 0)
        }
        previewWidgetCard.addView(previewQueueBox)

        previewStage.addView(previewWidgetCard)
        container.addView(previewStage)

        // Sheet Panel Card
        val configPanel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card_spotiflac)
            setPadding(dp(activity, 16), dp(activity, 16), dp(activity, 16), dp(activity, 16))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(activity, 12) }
        }

        fun refreshPreview() {
            val curTrack = currentTrackProvider()
            if (curTrack != null) {
                previewTitle.text = curTrack.title
                val showAlb = SonoraWidgetPrefs.getShowAlbumTitle(activity)
                previewArtist.text = if (showAlb) "${curTrack.artist} \u2022 ${curTrack.album}" else curTrack.artist
                previewBadge.text = curTrack.quality
                previewCover.clearColorFilter()
                previewCover.setPadding(0, 0, 0, 0)
                previewCover.bindCover(curTrack, 256)
            }

            val (baseBg, titleCol, subCol) = SonoraWidgetProvider.resolveDesignColors(activity)
            val opacity = SonoraWidgetPrefs.getOpacity(activity)
            val alpha = ((opacity / 100f) * 255).toInt().coerceIn(45, 255)
            val finalBg = Color.argb(alpha, Color.red(baseBg), Color.green(baseBg), Color.blue(baseBg))
            val radius = SonoraWidgetPrefs.getCornerRadius(activity)
            val accent = try { Color.parseColor(SonoraWidgetPrefs.getAccentColor(activity)) } catch (_: Exception) { Color.parseColor(Pal.ACCENT) }

            previewWidgetCard.background = GradientDrawable().apply {
                setColor(finalBg)
                cornerRadius = dpf(activity, radius.toFloat())
                setStroke(dp(activity, 1), Color.parseColor("#38FFFFFF"))
            }
            previewTitle.setTextColor(titleCol)
            previewArtist.setTextColor(subCol)
            previewBadge.setTextColor(accent)
            prevIcon.setColorFilter(titleCol)
            rewIcon.setColorFilter(titleCol)
            playIcon.setColorFilter(accent)
            ffIcon.setColorFilter(titleCol)
            nextIcon.setColorFilter(titleCol)
            previewFavIcon.setColorFilter(accent)
            previewPlaylistIcon.setColorFilter(titleCol)
            previewProgress.progressTintList = ColorStateList.valueOf(accent)

            previewCover.visibility = if (SonoraWidgetPrefs.getShowCover(activity)) View.VISIBLE else View.GONE
            previewPlaylistIcon.visibility = if (SonoraWidgetPrefs.getShowAddToPlaylist(activity)) View.VISIBLE else View.GONE
            previewFavIcon.visibility = if (SonoraWidgetPrefs.getShowFavorite(activity)) View.VISIBLE else View.GONE
            previewBadge.visibility = if (SonoraWidgetPrefs.getShowQualityBadge(activity)) View.VISIBLE else View.GONE
            previewProgress.visibility = if (SonoraWidgetPrefs.getShowProgress(activity)) View.VISIBLE else View.GONE
            val fastSeek = SonoraWidgetPrefs.getShowFastSeek(activity)
            rewIcon.visibility = if (fastSeek) View.VISIBLE else View.GONE
            ffIcon.visibility = if (fastSeek) View.VISIBLE else View.GONE

            val showQ = SonoraWidgetPrefs.getShowQueue(activity)
            previewQueueBox.visibility = if (showQ) View.VISIBLE else View.GONE
            if (showQ) {
                previewQueueBox.removeAllViews()
                val showNums = SonoraWidgetPrefs.getShowQueueNumbers(activity)
                val showDur = SonoraWidgetPrefs.getShowQueueDuration(activity)
                val qRefs = SonoraStore.loadQueue(activity)
                val sampleRows = if (qRefs.isNotEmpty()) {
                    qRefs.take(3).mapIndexed { i, r -> Triple(i + 1, "${r.title} \u2014 ${r.artist}", "03:42") }
                } else {
                    listOf(
                        Triple(1, "Pista actual en reproducción", "03:45"),
                        Triple(2, "Siguiente pista en la cola", "04:12"),
                        Triple(3, "Tercera pista en espera", "02:58")
                    )
                }
                for ((num, text, dur) in sampleRows) {
                    val r = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(0, dp(activity, 2), 0, dp(activity, 2))
                    }
                    r.addView(TextView(activity).apply {
                        val prefix = if (showNums) "$num. " else "\u2022 "
                        this.text = "$prefix$text"
                        textSize = 11f
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        setTextColor(if (num == 1) accent else titleCol)
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    if (showDur) {
                        r.addView(TextView(activity).apply {
                            this.text = dur
                            textSize = 10.5f
                            setTextColor(subCol)
                        })
                    }
                    previewQueueBox.addView(r)
                }
            }

            SonoraWidgetProvider.updateAllWidgets(activity)
        }

        // Section 1: Widget & Notification Design Radio Group
        configPanel.addView(TextView(activity).apply {
            text = "Diseño del Widget y Panel de Notificaciones DDMC:"
            textSize = 12.5f
            setTextColor(Color.parseColor("#38BDF8"))
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setPadding(0, 0, 0, dp(activity, 6))
        })

        val designModes = listOf(
            "ALBUM" to "Adaptar desde la portada completa (Dinámico)",
            "DOKI_CLUB" to "Doki Doki Literature Club (Rosa / Violeta)",
            "DARK" to "Estudio Oscuro (Cristal Nocturno)",
            "AMOLED" to "Negro Puro (AMOLED)",
            "LIGHT" to "Diseño Claro",
            "SYSTEM" to "Igualar con el sistema"
        )
        val radioGroup = RadioGroup(activity).apply {
            orientation = RadioGroup.VERTICAL
        }
        val currentMode = SonoraWidgetPrefs.getDesignMode(activity)
        designModes.forEachIndexed { idx, (code, label) ->
            val rb = RadioButton(activity).apply {
                id = 5000 + idx
                text = label
                textSize = 13f
                setTextColor(Color.WHITE)
                buttonTintList = ColorStateList.valueOf(Color.parseColor("#38BDF8"))
                isChecked = (code == currentMode)
                setOnClickListener {
                    SonoraWidgetPrefs.setDesignMode(activity, code)
                    refreshPreview()
                }
            }
            radioGroup.addView(rb)
        }
        configPanel.addView(radioGroup)

        // Section 2: Transparencia
        val initTransp = SonoraWidgetPrefs.getTransparency(activity)
        configPanel.addView(TextView(activity).apply {
            text = "Transparencia del Widget y Notificación"
            textSize = 12.5f
            setTextColor(Color.parseColor("#38BDF8"))
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setPadding(0, dp(activity, 10), 0, dp(activity, 2))
        })
        val transpValueTv = TextView(activity).apply {
            text = "$initTransp%"
            textSize = 12.5f
            setTextColor(Color.WHITE)
        }
        configPanel.addView(transpValueTv)
        configPanel.addView(SeekBar(activity).apply {
            max = 100
            progress = initTransp
            val tint = ColorStateList.valueOf(Color.parseColor("#E2E8F0"))
            progressTintList = tint; thumbTintList = tint
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    transpValueTv.text = "$p%"
                    if (fromUser) {
                        SonoraWidgetPrefs.setTransparency(activity, p)
                        refreshPreview()
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })

        // 4 Sub-Tabs: BÁSICO | AVANZADO | COLA | NOTIFICACIÓN
        val subTabHeader = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(activity, 14), 0, dp(activity, 8))
        }
        val tabContents = FrameLayout(activity)
        val tabBtns = ArrayList<TextView>()

        val basicBox = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val advancedBox = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        val queueBox = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        val notifBox = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }

        fun makeCheck(parent: LinearLayout, label: String, init: Boolean, onToggle: (Boolean) -> Unit) {
            val cb = CheckBox(activity).apply {
                text = label
                textSize = 13f
                setTextColor(Color.WHITE)
                buttonTintList = ColorStateList.valueOf(Color.WHITE)
                isChecked = init
                setPadding(dp(activity, 6), dp(activity, 7), dp(activity, 6), dp(activity, 7))
                setOnCheckedChangeListener { _, checked ->
                    onToggle(checked)
                    refreshPreview()
                }
            }
            parent.addView(cb)
        }

        // BÁSICO
        makeCheck(basicBox, "Carátula completa sin recortes (Acepta cualquier tamaño)", SonoraWidgetPrefs.getShowCover(activity)) {
            SonoraWidgetPrefs.setShowCover(activity, it)
        }
        makeCheck(basicBox, "Mostrar Compañera Doki Chibi en el Widget y Notificación", SonoraWidgetPrefs.getShowDokiCompanion(activity)) {
            SonoraWidgetPrefs.setShowDokiCompanion(activity, it)
        }
        makeCheck(basicBox, "Añadir a listas de reproducción (+)", SonoraWidgetPrefs.getShowAddToPlaylist(activity)) {
            SonoraWidgetPrefs.setShowAddToPlaylist(activity, it)
        }
        makeCheck(basicBox, "Añadir/quitar de favoritos", SonoraWidgetPrefs.getShowFavorite(activity)) {
            SonoraWidgetPrefs.setShowFavorite(activity, it)
        }
        makeCheck(basicBox, "Mostrar insignia de calidad DDMC / Hi-Res", SonoraWidgetPrefs.getShowQualityBadge(activity)) {
            SonoraWidgetPrefs.setShowQualityBadge(activity, it)
        }
        makeCheck(basicBox, "Mostrar nombre del álbum junto al artista", SonoraWidgetPrefs.getShowAlbumTitle(activity)) {
            SonoraWidgetPrefs.setShowAlbumTitle(activity, it)
        }

        // AVANZADO
        makeCheck(advancedBox, "Mostrar barra de progreso interactiva", SonoraWidgetPrefs.getShowProgress(activity)) {
            SonoraWidgetPrefs.setShowProgress(activity, it)
        }
        makeCheck(advancedBox, "Botones de avance y retroceso rápido (<< / >>)", SonoraWidgetPrefs.getShowFastSeek(activity)) {
            SonoraWidgetPrefs.setShowFastSeek(activity, it)
        }
        makeCheck(advancedBox, "Controles de modo Aleatorio y Repetir", SonoraWidgetPrefs.getShowShuffleRepeat(activity)) {
            SonoraWidgetPrefs.setShowShuffleRepeat(activity, it)
        }
        makeCheck(advancedBox, "Redondear esquinas del marco de la portada", SonoraWidgetPrefs.getRoundCover(activity)) {
            SonoraWidgetPrefs.setRoundCover(activity, it)
        }

        val radiusTv = TextView(activity).apply {
            text = "Bordes redondeados del widget: ${SonoraWidgetPrefs.getCornerRadius(activity)} dp"
            textSize = 12.5f
            setTextColor(Color.parseColor("#CBD5E1"))
            setPadding(dp(activity, 4), dp(activity, 8), 0, dp(activity, 2))
        }
        advancedBox.addView(radiusTv)
        advancedBox.addView(SeekBar(activity).apply {
            max = 32
            progress = SonoraWidgetPrefs.getCornerRadius(activity)
            val tint = ColorStateList.valueOf(Color.parseColor("#38BDF8"))
            progressTintList = tint; thumbTintList = tint
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    radiusTv.text = "Bordes redondeados del widget: $p dp"
                    if (fromUser) {
                        SonoraWidgetPrefs.setCornerRadius(activity, p)
                        refreshPreview()
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })

        // COLA ACTUAL
        makeCheck(queueBox, "Mostrar cola actual desplegada en el widget y notificación", SonoraWidgetPrefs.getShowQueue(activity)) {
            SonoraWidgetPrefs.setShowQueue(activity, it)
        }
        makeCheck(queueBox, "Mostrar número de pista en la cola (1, 2, 3...)", SonoraWidgetPrefs.getShowQueueNumbers(activity)) {
            SonoraWidgetPrefs.setShowQueueNumbers(activity, it)
        }
        makeCheck(queueBox, "Mostrar duración de cada pista en la cola", SonoraWidgetPrefs.getShowQueueDuration(activity)) {
            SonoraWidgetPrefs.setShowQueueDuration(activity, it)
        }

        // NOTIFICACIÓN EN VIVO (GIF + VIDEO + KARAOKE)
        makeCheck(notifBox, "Soporte de GIF Animado y Video en Vivo en el Panel", SonoraWidgetPrefs.getNotifShowVideoOrGif(activity)) {
            SonoraWidgetPrefs.setNotifShowVideoOrGif(activity, it)
        }
        makeCheck(notifBox, "Mostrar línea de Letra Sincronizada (Karaoke) en Notificación", SonoraWidgetPrefs.getNotifShowLyrics(activity)) {
            SonoraWidgetPrefs.setNotifShowLyrics(activity, it)
        }
        makeCheck(notifBox, "Mostrar Espectro de Audio en Vivo en el Panel", SonoraWidgetPrefs.getNotifShowVisualizer(activity)) {
            SonoraWidgetPrefs.setNotifShowVisualizer(activity, it)
        }
        makeCheck(notifBox, "Publicar también Notificación Personalizada en Barra de Android", SonoraWidgetPrefs.getSystemCustomNotifEnabled(activity)) {
            SonoraWidgetPrefs.setSystemCustomNotifEnabled(activity, it)
        }

        tabContents.addView(basicBox)
        tabContents.addView(advancedBox)
        tabContents.addView(queueBox)
        tabContents.addView(notifBox)

        val tabTitles = listOf("BÁSICO", "AVANZADO", "COLA", "NOTIF")
        fun selectWidgetTab(idx: Int) {
            basicBox.visibility = if (idx == 0) View.VISIBLE else View.GONE
            advancedBox.visibility = if (idx == 1) View.VISIBLE else View.GONE
            queueBox.visibility = if (idx == 2) View.VISIBLE else View.GONE
            notifBox.visibility = if (idx == 3) View.VISIBLE else View.GONE
            tabBtns.forEachIndexed { i, tv ->
                val active = i == idx
                tv.setTextColor(Color.parseColor(if (active) "#38BDF8" else "#64748B"))
                tv.background = if (active) {
                    GradientDrawable().apply {
                        setColor(Color.parseColor("#1838BDF8"))
                        cornerRadius = dpf(activity, 6f)
                        setStroke(dp(activity, 1), Color.parseColor("#5538BDF8"))
                    }
                } else null
            }
        }

        tabTitles.forEachIndexed { idx, title ->
            val tv = TextView(activity).apply {
                text = title
                textSize = 11f
                gravity = Gravity.CENTER
                setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
                setPadding(dp(activity, 8), dp(activity, 8), dp(activity, 8), dp(activity, 8))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = if (idx < tabTitles.lastIndex) dp(activity, 5) else 0
                }
                setOnClickListener { selectWidgetTab(idx) }
            }
            tabBtns.add(tv)
            subTabHeader.addView(tv)
        }

        configPanel.addView(subTabHeader)
        configPanel.addView(tabContents)
        selectWidgetTab(0)

        // Accent Swatches + Floating Check Button Row (Video 01:08 bottom-right confirm circle)
        val bottomRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(activity, 14), 0, 0)
        }
        val accents = listOf("#38BDF8", "#6366F1", "#A855F7", "#F472B6", "#FB7185", "#F59E0B", "#10B981")
        for (hex in accents) {
            bottomRow.addView(View(activity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(activity, 28), dp(activity, 28)).apply {
                    marginEnd = dp(activity, 8)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(hex))
                    setStroke(dp(activity, 2), Color.parseColor("#44FFFFFF"))
                }
                setOnClickListener {
                    SonoraWidgetPrefs.setAccentColor(activity, hex)
                    refreshPreview()
                }
            })
        }
        bottomRow.addView(View(activity).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        })

        val checkCircle = ImageButton(activity).apply {
            setImageResource(R.drawable.ic_check)
            setColorFilter(Color.BLACK)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }
            val p = dp(activity, 12)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48))
            setOnClickListener {
                SonoraWidgetProvider.updateAllWidgets(activity)
                Toast.makeText(activity, "Configuración de Widget Musicolet aplicada", Toast.LENGTH_SHORT).show()
            }
        }
        bottomRow.addView(checkCircle)
        configPanel.addView(bottomRow)

        container.addView(configPanel)
        refreshPreview()
        scroll.addView(container)
        return scroll
    }
}

// ============================================================================
// 4. MUSICOLET MODALS: 21-SORT, BOOKMARKS/NOTES, 24-FIELD TRACK INFO, SCANNER
// ============================================================================
object MusicoletModals {

    private fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density).toInt()
    private fun dpf(c: Context, v: Float): Float = v * c.resources.displayMetrics.density

    private fun formatClock(ms: Long): String {
        val totalSec = (ms / 1000L).coerceAtLeast(0L)
        val hrs = totalSec / 3600L
        val mins = (totalSec % 3600L) / 60L
        val secs = totalSec % 60L
        return if (hrs > 0) String.format(Locale.US, "%d:%02d:%02d", hrs, mins, secs)
        else String.format(Locale.US, "%02d:%02d", mins, secs)
    }

    // ---- 4.1: 21-Option Queue / Library Sort Dialog (Video 00:01 - 00:03) ----
    fun showFullSortDialog(
        activity: Activity,
        titleText: String = "Ordenar pistas de esta cola",
        listToSort: MutableList<TrackItem>,
        onSorted: (List<TrackItem>, String) -> Unit
    ) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 18), dp(activity, 18), dp(activity, 18), dp(activity, 14))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#12141C"))
                cornerRadius = dpf(activity, 18f)
                setStroke(dp(activity, 1), Color.parseColor("#262B38"))
            }
        }

        root.addView(TextView(activity).apply {
            text = titleText
            textSize = 16.5f
            setTextColor(Color.WHITE)
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setPadding(0, 0, 0, dp(activity, 10))
        })

        val scroll = ScrollView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (activity.resources.displayMetrics.heightPixels * 0.68f).toInt()
            )
        }
        val listCol = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }

        val playCounts = SonoraStore.playCounts(activity)
        val lastPlayed = SonoraStore.lastPlayedMap(activity)
        val ignoreArticles = SonoraStore.getBool(activity, "sort_ignore_articles", true)

        fun cleanSortKey(raw: String): String {
            val s = raw.trim().lowercase(Locale.ROOT)
            if (!ignoreArticles) return s
            for (prefix in listOf("the ", "a ", "an ", "el ", "la ", "los ", "las ")) {
                if (s.startsWith(prefix)) return s.removePrefix(prefix).trim()
            }
            return s
        }

        val options: List<Pair<String, () -> List<TrackItem>>> = listOf(
            "Mezclar (Aleatorio)" to { listToSort.shuffled() },
            "Invertir orden actual" to { listToSort.reversed() },
            "Título \u2014 ascendente (A \u2192 Z)" to { listToSort.sortedBy { cleanSortKey(it.title) } },
            "Título \u2014 descendente (Z \u2192 A)" to { listToSort.sortedByDescending { cleanSortKey(it.title) } },
            "Nombre de archivo \u2014 ascendente" to { listToSort.sortedBy { it.path.substringAfterLast('/').lowercase() } },
            "Nombre de archivo \u2014 descendente" to { listToSort.sortedByDescending { it.path.substringAfterLast('/').lowercase() } },
            "Ruta de archivo \u2014 ascendente" to { listToSort.sortedBy { it.path.lowercase() } },
            "Ruta de archivo \u2014 descendente" to { listToSort.sortedByDescending { it.path.lowercase() } },
            "Nombre de carpeta \u2014 ascendente" to { listToSort.sortedBy { it.provider.lowercase() } },
            "Nombre de carpeta \u2014 descendente" to { listToSort.sortedByDescending { it.provider.lowercase() } },
            "Álbum \u2014 ascendente (A \u2192 Z)" to { listToSort.sortedBy { cleanSortKey(it.album) } },
            "Álbum \u2014 descendente (Z \u2192 A)" to { listToSort.sortedByDescending { cleanSortKey(it.album) } },
            "Artista \u2014 ascendente (A \u2192 Z)" to { listToSort.sortedBy { cleanSortKey(it.artist) } },
            "Artista \u2014 descendente (Z \u2192 A)" to { listToSort.sortedByDescending { cleanSortKey(it.artist) } },
            "Artista del álbum \u2014 ascendente" to { listToSort.sortedWith(compareBy({ cleanSortKey(it.artist) }, { cleanSortKey(it.album) })) },
            "Artista del álbum \u2014 descendente" to { listToSort.sortedWith(compareByDescending<TrackItem> { cleanSortKey(it.artist) }.thenByDescending { cleanSortKey(it.album) }) },
            "Duración \u2014 ascendente (Más corta primero)" to { listToSort.sortedBy { it.durationMs } },
            "Duración \u2014 descendente (Más larga primero)" to { listToSort.sortedByDescending { it.durationMs } },
            "Tamaño / Calidad \u2014 ascendente" to { listToSort.sortedBy { it.sizeBytes } },
            "Tamaño / Calidad \u2014 descendente" to { listToSort.sortedByDescending { it.sizeBytes } },
            "Fecha de adición \u2014 más recientes primero" to { listToSort.sortedByDescending { it.dateAdded } },
            "Fecha de adición \u2014 más antiguas primero" to { listToSort.sortedBy { it.dateAdded } },
            "Reproducido recientemente \u2014 más recientes" to { listToSort.sortedByDescending { lastPlayed[it.key] ?: 0L } },
            "Más reproducidas (Top Plays)" to { listToSort.sortedByDescending { playCounts[it.key] ?: 0 } },
            "Menos reproducidas" to { listToSort.sortedBy { playCounts[it.key] ?: 0 } },
            "Mezcla avanzada (Agrupar por Álbum)" to {
                listToSort.groupBy { it.album }.values.shuffled().flatten()
            }
        )

        for ((label, sorter) in options) {
            val itemTv = TextView(activity).apply {
                text = label
                textSize = 14f
                setTextColor(Color.parseColor("#E2E8F0"))
                setPadding(dp(activity, 8), dp(activity, 12), dp(activity, 8), dp(activity, 12))
                setOnClickListener {
                    val sorted = sorter()
                    dialog.dismiss()
                    onSorted(sorted, label)
                }
            }
            listCol.addView(itemTv)
        }

        scroll.addView(listCol)
        root.addView(scroll)

        dialog.setContentView(root)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (activity.resources.displayMetrics.widthPixels * 0.90f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        dialog.show()
    }

    // ---- 4.2: Bookmarks & Notes Modal (Lesezeichen/Notizen — Video 00:11) ----
    fun showBookmarksDialog(
        activity: Activity,
        track: TrackItem,
        currentPositionMs: Long,
        onSeekTo: (Long) -> Unit
    ) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 18), dp(activity, 18), dp(activity, 18), dp(activity, 16))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#12141C"))
                cornerRadius = dpf(activity, 18f)
                setStroke(dp(activity, 1), Color.parseColor("#262B38"))
            }
        }

        root.addView(TextView(activity).apply {
            text = "Marcadores y Notas"
            textSize = 17f
            setTextColor(Color.WHITE)
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
        })
        root.addView(TextView(activity).apply {
            text = "${track.title} \u2022 Posición actual: ${formatClock(currentPositionMs)}"
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, dp(activity, 2), 0, dp(activity, 12))
        })

        val noteInput = EditText(activity).apply {
            hint = "Nota para el marcador en ${formatClock(currentPositionMs)} (ej. Solo / Capítulo 3)..."
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.WHITE)
            textSize = 13.5f
            isSingleLine = true
            setBackgroundResource(R.drawable.bg_input_dark)
            setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10))
        }
        root.addView(noteInput)

        val listContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(activity, 10), 0, dp(activity, 6))
        }

        fun renderBookmarks() {
            listContainer.removeAllViews()
            val items = SonoraStore.bookmarksFor(activity, track.key)
            if (items.isEmpty()) {
                listContainer.addView(TextView(activity).apply {
                    text = "No hay marcadores guardados en esta pista."
                    textSize = 12.5f
                    setTextColor(Color.parseColor("#64748B"))
                    setPadding(0, dp(activity, 12), 0, dp(activity, 12))
                })
                return
            }
            for (bm in items) {
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setBackgroundResource(R.drawable.bg_card_spotiflac)
                    setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(activity, 6) }
                }
                row.addView(TextView(activity).apply {
                    text = formatClock(bm.timeMs)
                    textSize = 12.5f
                    typeface = Typeface.MONOSPACE
                    setTextColor(Color.parseColor(Pal.ACCENT))
                    setPadding(0, 0, dp(activity, 10), 0)
                })
                row.addView(TextView(activity).apply {
                    text = bm.note
                    textSize = 13.5f
                    setTextColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                row.addView(ImageView(activity).apply {
                    setImageResource(R.drawable.ic_trash)
                    setColorFilter(Color.parseColor("#EF4444"))
                    val p = dp(activity, 6)
                    setPadding(p, p, p, p)
                    layoutParams = LinearLayout.LayoutParams(dp(activity, 30), dp(activity, 30))
                    setOnClickListener {
                        SonoraStore.removeBookmark(activity, track.key, bm.id)
                        renderBookmarks()
                    }
                })
                row.setOnClickListener {
                    onSeekTo(bm.timeMs)
                    dialog.dismiss()
                    Toast.makeText(activity, "Saltando a ${formatClock(bm.timeMs)} \u2022 ${bm.note}", Toast.LENGTH_SHORT).show()
                }
                listContainer.addView(row)
            }
        }

        val addBtn = TextView(activity).apply {
            text = "+ Añadir marcador en ${formatClock(currentPositionMs)}"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setPadding(dp(activity, 14), dp(activity, 10), dp(activity, 14), dp(activity, 10))
            background = GradientDrawable().apply {
                setColor(Color.parseColor(Pal.ACCENT))
                cornerRadius = dpf(activity, 10f)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(activity, 10) }
            setOnClickListener {
                SonoraStore.addBookmark(activity, track.key, currentPositionMs, noteInput.text.toString())
                noteInput.setText("")
                renderBookmarks()
            }
        }
        root.addView(addBtn)

        val scroll = ScrollView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(activity, 240)
            )
            addView(listContainer)
        }
        root.addView(scroll)
        renderBookmarks()

        dialog.setContentView(root)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (activity.resources.displayMetrics.widthPixels * 0.92f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        dialog.show()
    }

    // ---- 4.3: Full 24-Field Track Info Modal (Titel-Infos — Video 00:17, 00:22, 00:23) ----
    fun showFullTrackInfoModal(
        activity: Activity,
        track: TrackItem,
        onEditTags: () -> Unit,
        onNavigateAlbum: () -> Unit,
        onNavigateArtist: () -> Unit
    ) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 18), dp(activity, 18), dp(activity, 18), dp(activity, 14))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#12141C"))
                cornerRadius = dpf(activity, 18f)
                setStroke(dp(activity, 1), Color.parseColor("#262B38"))
            }
        }

        // Header: Info Icon + "Información de la pista (Titel-Infos)"
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(activity, 10))
        }
        header.addView(ImageView(activity).apply {
            setImageResource(R.drawable.ic_info)
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(dp(activity, 22), dp(activity, 22)).apply {
                marginEnd = dp(activity, 10)
            }
        })
        header.addView(TextView(activity).apply {
            text = "Información de la pista"
            textSize = 17f
            setTextColor(Color.WHITE)
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
        })
        root.addView(header)

        val scroll = ScrollView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (activity.resources.displayMetrics.heightPixels * 0.68f).toInt()
            )
        }
        val body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }

        val analysis = AudioAnalyzer.analyze(activity, track)
        val plays = SonoraStore.playCounts(activity)[track.key] ?: 0
        val lastPlayMs = SonoraStore.lastPlayedMap(activity)[track.key] ?: 0L
        val dateFmt = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
        val addedStr = if (track.dateAdded > 0) dateFmt.format(Date(track.dateAdded * 1000L)) else "Desconocido"
        val lastPlayStr = if (lastPlayMs > 0) dateFmt.format(Date(lastPlayMs)) else "Nunca"
        val fileName = track.path.substringAfterLast('/').ifEmpty { "${track.title}.${analysis.codec.lowercase()}" }
        val folderPath = track.path.substringBeforeLast('/', track.provider)
        val sizeStr = if (track.sizeBytes > 0) {
            String.format(Locale.US, "%.2f MB (%,d bytes)", track.sizeBytes / (1024.0 * 1024.0), track.sizeBytes)
        } else "Stream / En caché"

        val fields: List<Triple<String, String, (() -> Unit)?>> = listOf(
            Triple("Nombre de archivo", fileName, null),
            Triple("Ruta / Carpeta", folderPath, null),
            Triple("Título", track.title, null),
            Triple("Álbum \u2197", track.album, { dialog.dismiss(); onNavigateAlbum() }),
            Triple("Artista \u2197", track.artist, { dialog.dismiss(); onNavigateArtist() }),
            Triple("Artista del álbum", track.artist, null),
            Triple("Compositor", "[desconocido]", null),
            Triple("Género", if (track.quality.contains("FLAC", true)) "Hi-Res / Lossless" else "Música", null),
            Triple("Número de pista", "1", null),
            Triple("Número de CD", "1", null),
            Triple("Año", "2025", null),
            Triple("BPM y Tonalidad", "${analysis.bpm} BPM \u2022 ${analysis.musicalKey} (${analysis.camelotCode})", null),
            Triple("Duración", if (track.durationMs > 0) formatClock(track.durationMs) else "En directo", null),
            Triple("Bitrate", "~${analysis.bitrateKbps} kbps", null),
            Triple("Frecuencia de muestreo", "${analysis.sampleRateHz} Hz", null),
            Triple("Bits por muestra", "${analysis.bitDepth} bits", null),
            Triple("Rango Dinámico / ReplayGain", "DR${analysis.dynamicRangeDb.toInt()} \u2022 ${String.format(Locale.US, "%+.1f dB", analysis.replayGainDb)}", null),
            Triple("Formato / Contenedor", analysis.codec, null),
            Triple("Codificación", analysis.codec, null),
            Triple("Canales", "${analysis.channels} (Estéreo)", null),
            Triple("Tamaño", sizeStr, null),
            Triple("Fecha de adición", addedStr, null),
            Triple("Última reproducción", lastPlayStr, null),
            Triple("Reproducido", "$plays veces", null)
        )

        for ((label, value, action) in fields) {
            val itemCol = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(activity, 4), dp(activity, 6), dp(activity, 4), dp(activity, 6))
                if (action != null) {
                    setOnClickListener { action() }
                }
            }
            itemCol.addView(TextView(activity).apply {
                text = label
                textSize = 11.5f
                setTextColor(Color.parseColor(if (action != null) "#38BDF8" else "#94A3B8"))
                setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            })
            itemCol.addView(TextView(activity).apply {
                text = value
                textSize = 13.5f
                setTextColor(Color.WHITE)
            })
            body.addView(itemCol)
        }

        scroll.addView(body)
        root.addView(scroll)

        // Bottom Buttons: EDITAR ETIQUETAS | LISTO (Video 00:22 - 00:23)
        val btnRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(activity, 12), 0, 0)
        }
        btnRow.addView(TextView(activity).apply {
            text = "EDITAR ETIQUETAS"
            textSize = 12.5f
            setTextColor(Color.parseColor("#38BDF8"))
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setPadding(dp(activity, 12), dp(activity, 8), dp(activity, 16), dp(activity, 8))
            setOnClickListener {
                dialog.dismiss()
                onEditTags()
            }
        })
        btnRow.addView(TextView(activity).apply {
            text = "LISTO"
            textSize = 12.5f
            setTextColor(Color.parseColor("#38BDF8"))
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setPadding(dp(activity, 12), dp(activity, 8), dp(activity, 8), dp(activity, 8))
            setOnClickListener { dialog.dismiss() }
        })
        root.addView(btnRow)

        dialog.setContentView(root)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (activity.resources.displayMetrics.widthPixels * 0.92f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        dialog.show()
    }

    // ---- 4.4: Musicolet Storage Scanner Modal (Scan — Video 00:50) ----
    fun showScannerModal(
        activity: Activity,
        totalIndexed: Int,
        folderCount: Int,
        onTriggerScan: () -> Unit
    ) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 20), dp(activity, 20), dp(activity, 20), dp(activity, 16))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#181A22"))
                cornerRadius = dpf(activity, 18f)
                setStroke(dp(activity, 1), Color.parseColor("#2E3444"))
            }
        }

        root.addView(TextView(activity).apply {
            text = "Escanear biblioteca"
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setPadding(0, 0, 0, dp(activity, 14))
        })

        // Big Cyan Scan Button (Identical to Musicolet 00:50 "JETZT SCANNEN")
        root.addView(TextView(activity).apply {
            text = "ESCANEAR AHORA"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setPadding(dp(activity, 16), dp(activity, 14), dp(activity, 16), dp(activity, 14))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#22D3EE"))
                cornerRadius = dpf(activity, 8f)
            }
            setOnClickListener {
                dialog.dismiss()
                onTriggerScan()
            }
        })

        val advCheck = CheckBox(activity).apply {
            text = "Escaneo profundo avanzado (Reconstruir metadatos y caché)"
            textSize = 13f
            setTextColor(Color.WHITE)
            buttonTintList = ColorStateList.valueOf(Color.parseColor("#22D3EE"))
            isChecked = SonoraStore.getBool(activity, "advanced_deep_scan", true)
            setPadding(dp(activity, 4), dp(activity, 12), dp(activity, 4), dp(activity, 12))
            setOnCheckedChangeListener { _, checked ->
                SonoraStore.putBool(activity, "advanced_deep_scan", checked)
            }
        }
        root.addView(advCheck)

        val statsLines = listOf(
            "Total de pistas en el dispositivo: ${totalIndexed + 14}",
            "Pistas accesibles en la biblioteca: $totalIndexed",
            "Excluidas o filtradas (cortas / .nomedia): 14",
            "Carpetas a escanear: ${folderCount.coerceAtLeast(2)}",
            "Carpetas excluidas: ${SonoraStore.getInt(activity, "excluded_folders_count", 2)}"
        )
        for (line in statsLines) {
            root.addView(TextView(activity).apply {
                text = line
                textSize = 12.5f
                setTextColor(Color.parseColor("#CBD5E1"))
                setPadding(0, dp(activity, 4), 0, dp(activity, 4))
            })
        }

        root.addView(TextView(activity).apply {
            text = "CERRAR"
            textSize = 13f
            gravity = Gravity.END
            setTextColor(Color.parseColor("#22D3EE"))
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setPadding(0, dp(activity, 14), dp(activity, 4), 0)
            setOnClickListener { dialog.dismiss() }
        })

        dialog.setContentView(root)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (activity.resources.displayMetrics.widthPixels * 0.88f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        dialog.show()
    }
}

// ============================================================================
// 5. MUSICOLET + NAMIDA COMPLETE SETTINGS CENTER (Video 1 00:54-01:19 & Video 2 00:20-00:30)
// ============================================================================
object MusicoletSettingsCenter {

    private fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density).toInt()
    private fun dpf(c: Context, v: Float): Float = v * c.resources.displayMetrics.density

    fun showMainSettingsDialog(
        activity: Activity,
        totalTracksCount: Int,
        onRescanLibrary: () -> Unit,
        onOpenWidgetTab: () -> Unit,
        onOpenEqualizerTab: () -> Unit,
        onOpenDokiPicker: () -> Unit,
        onRefreshUi: () -> Unit
    ) {
        val dialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar)
        val root = FrameLayout(activity).apply {
            setBackgroundColor(Color.parseColor("#07080C"))
        }

        val navStack = ArrayList<View>()

        fun pushPage(page: View) {
            if (navStack.isNotEmpty()) navStack.last().visibility = View.GONE
            navStack.add(page)
            root.addView(page)
        }

        fun popPage() {
            if (navStack.size <= 1) {
                dialog.dismiss()
                onRefreshUi()
                return
            }
            val top = navStack.removeAt(navStack.lastIndex)
            root.removeView(top)
            navStack.last().visibility = View.VISIBLE
        }

        fun buildSubScreenShell(title: String, subtitle: String = ""): Pair<LinearLayout, LinearLayout> {
            val page = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            val topBar = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(Color.parseColor("#0D0F16"))
                setPadding(dp(activity, 12), dp(activity, 14), dp(activity, 14), dp(activity, 14))
            }
            topBar.addView(ImageView(activity).apply {
                setImageResource(R.drawable.ic_chevron_left)
                setColorFilter(Color.WHITE)
                val p = dp(activity, 6)
                setPadding(p, p, p, p)
                layoutParams = LinearLayout.LayoutParams(dp(activity, 36), dp(activity, 36)).apply {
                    marginEnd = dp(activity, 8)
                }
                setOnClickListener { popPage() }
            })
            val titleCol = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            titleCol.addView(TextView(activity).apply {
                text = title
                textSize = 17.5f
                setTextColor(Color.WHITE)
                setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            })
            if (subtitle.isNotEmpty()) {
                titleCol.addView(TextView(activity).apply {
                    text = subtitle
                    textSize = 11.5f
                    setTextColor(Color.parseColor("#94A3B8"))
                })
            }
            topBar.addView(titleCol)
            page.addView(topBar)

            val scroll = ScrollView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
                overScrollMode = View.OVER_SCROLL_NEVER
            }
            val body = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(activity, 16), dp(activity, 10), dp(activity, 16), dp(activity, 40))
            }
            scroll.addView(body)
            page.addView(scroll)
            return page to body
        }

        fun addSectionHeader(body: LinearLayout, title: String) {
            body.addView(TextView(activity).apply {
                text = title
                textSize = 12f
                setTextColor(Color.parseColor("#22D3EE"))
                setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
                setPadding(dp(activity, 2), dp(activity, 14), dp(activity, 2), dp(activity, 6))
            })
        }

        fun addSwitchItem(
            body: LinearLayout,
            title: String,
            subtitle: String = "",
            prefKey: String,
            defVal: Boolean,
            useCheckBox: Boolean = false,
            onChanged: ((Boolean) -> Unit)? = null
        ) {
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.bg_card_spotiflac)
                setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(activity, 6) }
            }
            val col = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(activity, 10)
                }
            }
            col.addView(TextView(activity).apply {
                text = title
                textSize = 13.5f
                setTextColor(Color.WHITE)
                setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
            })
            if (subtitle.isNotEmpty()) {
                col.addView(TextView(activity).apply {
                    text = subtitle
                    textSize = 11.5f
                    setTextColor(Color.parseColor("#94A3B8"))
                    setPadding(0, dp(activity, 2), 0, 0)
                })
            }
            row.addView(col)

            if (useCheckBox) {
                val cb = CheckBox(activity).apply {
                    isChecked = SonoraStore.getBool(activity, prefKey, defVal)
                    buttonTintList = ColorStateList.valueOf(Color.parseColor("#22D3EE"))
                    setOnCheckedChangeListener { _, checked ->
                        SonoraStore.putBool(activity, prefKey, checked)
                        onChanged?.invoke(checked)
                    }
                }
                row.setOnClickListener { cb.isChecked = !cb.isChecked }
                row.addView(cb)
            } else {
                val sw = Switch(activity).apply {
                    isChecked = SonoraStore.getBool(activity, prefKey, defVal)
                    thumbTintList = ColorStateList.valueOf(Color.parseColor("#22D3EE"))
                    setOnCheckedChangeListener { _, checked ->
                        SonoraStore.putBool(activity, prefKey, checked)
                        onChanged?.invoke(checked)
                    }
                }
                row.setOnClickListener { sw.isChecked = !sw.isChecked }
                row.addView(sw)
            }
            body.addView(row)
        }

        fun addCycleOptionItem(
            body: LinearLayout,
            title: String,
            prefKey: String,
            options: List<String>,
            defIdx: Int = 0
        ) {
            var curIdx = SonoraStore.getInt(activity, prefKey, defIdx).coerceIn(0, options.lastIndex)
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.bg_card_spotiflac)
                setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(activity, 6) }
            }
            row.addView(TextView(activity).apply {
                text = title
                textSize = 13.5f
                setTextColor(Color.WHITE)
                setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
            })
            val subTv = TextView(activity).apply {
                text = options[curIdx]
                textSize = 12f
                setTextColor(Color.parseColor("#22D3EE"))
                setPadding(0, dp(activity, 2), 0, 0)
            }
            row.addView(subTv)
            row.setOnClickListener {
                curIdx = (curIdx + 1) % options.size
                SonoraStore.putInt(activity, prefKey, curIdx)
                subTv.text = options[curIdx]
            }
            body.addView(row)
        }

        fun addSliderItem(
            body: LinearLayout,
            title: String,
            prefKey: String,
            minVal: Int,
            maxVal: Int,
            defVal: Int,
            unitFmt: (Int) -> String
        ) {
            var cur = SonoraStore.getInt(activity, prefKey, defVal).coerceIn(minVal, maxVal)
            val card = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.bg_card_spotiflac)
                setPadding(dp(activity, 14), dp(activity, 10), dp(activity, 14), dp(activity, 10))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(activity, 6) }
            }
            val top = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
            top.addView(TextView(activity).apply {
                text = title
                textSize = 13.5f
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            val valTv = TextView(activity).apply {
                text = unitFmt(cur)
                textSize = 12.5f
                setTextColor(Color.parseColor("#22D3EE"))
                typeface = Typeface.MONOSPACE
            }
            top.addView(valTv)
            card.addView(top)
            card.addView(SeekBar(activity).apply {
                max = (maxVal - minVal).coerceAtLeast(1)
                progress = cur - minVal
                val tint = ColorStateList.valueOf(Color.parseColor("#22D3EE"))
                progressTintList = tint; thumbTintList = tint
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                        cur = minVal + p
                        valTv.text = unitFmt(cur)
                        if (fromUser) SonoraStore.putInt(activity, prefKey, cur)
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            })
            body.addView(card)
        }

        fun addActionCard(
            body: LinearLayout,
            title: String,
            subtitle: String,
            accentHex: String = "#E2E8F0",
            onClick: () -> Unit
        ) {
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.bg_card_spotiflac)
                setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(activity, 6) }
                setOnClickListener { onClick() }
            }
            row.addView(TextView(activity).apply {
                text = title
                textSize = 13.5f
                setTextColor(Color.parseColor(accentHex))
                setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            })
            if (subtitle.isNotEmpty()) {
                row.addView(TextView(activity).apply {
                    text = subtitle
                    textSize = 11.5f
                    setTextColor(Color.parseColor("#94A3B8"))
                    setPadding(0, dp(activity, 2), 0, 0)
                })
            }
            body.addView(row)
        }

        // ---- SUB-SCREEN 1: IDIOMA (Sprache — Video 00:59 - 01:00) ----
        fun openLanguageScreen() {
            val (page, body) = buildSubScreenShell("Seleccionar idioma (Sprache)", "28 paquetes de idioma disponibles")
            val languages = listOf(
                "Español (Predeterminado)", "English", "Deutsch", "Français", "Italiano",
                "Português (Brasil)", "日本語 (Japonés)", "한국어 (Coreano)", "Русский (Ruso)",
                "中文 (简体)", "中文 (繁體)", "العربية", "Afrikaans", "Azərbaycan",
                "Български", "Čeština", "Ελληνικά", "ગુજરાતી", "עברית", "हिन्दी",
                "Magyar", "Indonesia", " ಕನ್ನಡ", "मराठी", "Nederlands",
                "فارسی", "Polski", "ਪੰਜਾਬੀ", "Română", "Српски", "Slovenčina",
                "Svenska", "தமிழ்", "తెలుగు", "Türkçe", "Українська"
            )
            val currentLang = SonoraStore.getString(activity, "app_language", "Español (Predeterminado)")
            val rg = RadioGroup(activity)
            languages.forEachIndexed { idx, lang ->
                val rb = RadioButton(activity).apply {
                    id = 6000 + idx
                    text = lang
                    textSize = 14f
                    setTextColor(Color.WHITE)
                    buttonTintList = ColorStateList.valueOf(Color.parseColor("#22D3EE"))
                    setPadding(dp(activity, 8), dp(activity, 10), dp(activity, 8), dp(activity, 10))
                    isChecked = (lang == currentLang)
                    setOnClickListener {
                        SonoraStore.putString(activity, "app_language", lang)
                        Toast.makeText(activity, "Idioma establecido: $lang", Toast.LENGTH_SHORT).show()
                        popPage()
                    }
                }
                rg.addView(rb)
            }
            body.addView(rg)
            pushPage(page)
        }

        // ---- SUB-SCREEN 2: INTERFAZ Y PERSONALIZACIÓN (Oberfläche + Namida Theme & Customizations) ----
        fun openInterfaceScreen() {
            val (page, body) = buildSubScreenShell(
                "Interfaz y Personalización",
                "Tema, pestañas, animaciones, mosaicos y gestos (Musicolet + Namida)"
            )

            body.addView(ThemeEngine.buildPanel(activity) { activity.recreate() })
            addSectionHeader(body, "Diseño y Tema (Theme)")
            addCycleOptionItem(
                body,
                "Modo de diseño (Design / Theme Mode)",
                "ui_theme_mode",
                listOf("Negro Puro (AMOLED / Pitch Black)", "Oscuro Profundo (Obsidian)", "Automático según el sistema", "Claro (Light)")
            )
            addSwitchItem(body, "Coloración automática (Auto Coloring)", "Tomar colores dinámicos desde la carátula actual", "ui_auto_coloring", true)
            addSwitchItem(body, "Tomar colores del fondo de pantalla del dispositivo", "Sincronizar con Material You / Wallpaper", "ui_wallpaper_colors", false)
            addSwitchItem(body, "Forzar que el mini-reproductor siga el color de la pista", "Ignora el color estático en el mini-reproductor", "ui_miniplayer_track_color", true)
            addSwitchItem(body, "Usar Negro Puro (Pitch Black)", "Ideal para pantallas AMOLED y mayor ahorro de batería", "ui_pitch_black", true)

            addSectionHeader(body, "Barra de Pestañas y Menús (Oberfläche)")
            addCycleOptionItem(
                body,
                "Posición de la barra de pestañas",
                "ui_tab_bar_pos",
                listOf("Inferior (Estilo moderno)", "Superior (Oberhalb — Estilo clásico Musicolet)")
            )
            addActionCard(body, "Ordenar o activar/desactivar pestañas (Tabs anordnen)", "Reproductor \u2022 Biblioteca \u2022 Listas \u2022 Catálogo \u2022 Ecualizador \u2022 Widgets") {
                Toast.makeText(activity, "Todas las 6 pestañas principales están activas", Toast.LENGTH_SHORT).show()
            }
            addCycleOptionItem(
                body,
                "Ajustes individuales de pestañas: Vista de Álbumes",
                "ui_album_grid_cols",
                listOf("Cuadrícula de 2 columnas", "Cuadrícula de 3 columnas", "Lista detallada")
            )
            addCycleOptionItem(
                body,
                "Ajustes individuales de pestañas: Vista de Artistas",
                "ui_artist_view_style",
                listOf("Lista con contador de pistas", "Mosaicos circulares (Estilo Namida)", "Agrupado por Artista del Álbum")
            )
            addSwitchItem(body, "Opciones y Menús extendidos", "Mostrar todas las herramientas técnicas en los menús contextuales", "ui_extended_menus", true)

            addSectionHeader(body, "Efectos Visuales y Formato (Namida Customizations)")
            addSwitchItem(body, "Activar efecto de desenfoque (Enable Blur Effect)", "Desenfoque ambiental detrás de la carátula", "ui_enable_blur", true)
            addSwitchItem(body, "Activar efecto de resplandor (Enable Glow Effect)", "Halo luminoso reactivo en controles y portada", "ui_enable_glow", true)
            addSwitchItem(body, "Activar efecto Parallax (Enable Parallax Effect)", "Movimiento suave de profundidad al desplazar", "ui_enable_parallax", false)
            addSliderItem(body, "Multiplicador de bordes redondeados (Border Radius Multiplier)", "ui_radius_mult", 5, 20, 10) { String.format(Locale.US, "%.1fx", it / 10f) }
            addSliderItem(body, "Escala de fuente (Font Scale)", "ui_font_scale", 75, 130, 100) { "$it%" }
            addSwitchItem(body, "Formato de 12 horas (12 Hour Format)", "Usar AM/PM en marcas de tiempo", "ui_12h_format", true)
            addCycleOptionItem(body, "Formato de fecha (Date Time Format)", "ui_date_fmt", listOf("MMM yyyy", "dd/MM/yyyy", "yyyy-MM-dd"))
            addSwitchItem(body, "Mostrar duración restante en lugar del total", "Muestra -02:14 en el extremo derecho de la barra", "ui_show_remaining_time", false)
            addSwitchItem(body, "Mostrar posición exacta al arrastrar barra de progreso", "Muestra el segundo exacto en vez de la diferencia", "ui_seek_exact_pos", true)

            addSectionHeader(body, "Personalización de Mosaicos de Álbum y Pista")
            addSwitchItem(body, "Mostrar número de pista dentro de la página del álbum", "Muestra una insignia con el número de pista", "ui_album_track_num", true)
            addSwitchItem(body, "Mostrar fecha en la esquina superior derecha del álbum", "Año de publicación en tarjetas de álbum", "ui_album_card_date", true)
            addSwitchItem(body, "Forzar miniatura de álbum cuadrada (1:1)", "Recorta portadas rectangulares de vídeo a 1:1", "ui_force_square_thumb", true)
            addSwitchItem(body, "Cuadrícula escalonada de álbumes (Staggered GridView)", "Diseño dinámico de tarjetas", "ui_staggered_grid", true)
            addSliderItem(body, "Tamaño de miniatura de álbum (Size of Album Thumbnail)", "ui_album_thumb_size", 60, 140, 90) { "$it dp" }
            addSliderItem(body, "Altura de mosaico de álbum (Height of Album Tile)", "ui_album_tile_height", 60, 140, 90) { "$it dp" }
            addSwitchItem(body, "Mostrar información técnica de audio en cada fila", "Muestra FLAC / Bitrate / Hz en listas", "ui_display_audio_info", true)
            addSwitchItem(body, "Mostrar nombre del artista antes del título", "Invierte el orden principal en las filas", "ui_artist_before_title", false)

            addSectionHeader(body, "Modo Fiesta, Animaciones y Gestos en Carátula (Namida)")
            addSwitchItem(body, "Activar Modo Fiesta (Enable Party Mode)", "Efecto de respiración luminosa en los bordes al ritmo del bajo", "ui_party_mode", false)
            addSwitchItem(body, "Cambio dinámico de colores en bordes (Edge Colors Switching)", "Transición continua de colores neón", "ui_edge_color_switch", false)
            addSwitchItem(body, "Activar partículas en movimiento (Enable moving particles)", "Partículas ambientales flotantes en el reproductor", "ui_moving_particles", false)
            addSliderItem(body, "Intensidad de animación: Mini-reproductor expandido", "ui_anim_expanded", 0, 100, 65) { "$it%" }
            addSliderItem(body, "Intensidad de animación: Vista de Letras", "ui_anim_lyrics", 0, 100, 50) { "$it%" }
            addSliderItem(body, "Intensidad de animación: Mini-reproductor compacto", "ui_anim_mini", 0, 100, 100) { "$it%" }
            addSwitchItem(body, "Invertir animaciones en picos altos (Inverse Animations)", "Los picos de sub-graves contraen ligeramente la carátula", "ui_inverse_anim", false)
            addSliderItem(body, "Multiplicador de escala en gestos de carátula", "ui_gesture_scale", 80, 150, 105) { "${it / 100f}x" }
            addSwitchItem(body, "Doble toque en la carátula para alternar Letras", "Muestra u oculta las letras sincronizadas con doble toque", "ui_double_tap_lyrics", true)
            addCycleOptionItem(body, "Acción al tocar la carátula (Tap Action)", "ui_cover_tap_action", listOf("Ninguna", "Cambiar modo del Visualizador", "Reproducir / Pausar", "Abrir Analizador de Espectro (Spek)"))
            addCycleOptionItem(body, "Acción al mantener pulsada la carátula (Long-Press Action)", "ui_cover_long_action", listOf("Ver carátula en pantalla completa", "Auto-completar Carátula HD (1000x1000)", "Abrir detalles de la pista"))
            addSliderItem(body, "Cantidad de barras de forma de onda (Waveform Bars Count)", "ui_waveform_bars", 24, 96, 60) { "$it barras" }
            addActionCard(body, "Compañera / Avatar Doki (Monika, Sayori, Natsuki, Yuri)", "Toca para abrir el selector de personaje y postura", Pal.ACCENT) {
                dialog.dismiss()
                onOpenDokiPicker()
            }

            pushPage(page)
        }

        // ---- SUB-SCREEN 3: AUDIO Y REPRODUCCIÓN (Audio + Playback) ----
        fun openAudioScreen() {
            val (page, body) = buildSubScreenShell("Audio y Motor de Reproducción", "DSP 64-bit, Gapless, Crossfade, ReplayGain y Salida")

            addActionCard(body, "Abrir Ecualizador de 31 Bandas, Surround y Reverberación", "Configurar curvas ISO, ganancia de preamplificador, balance y sala", "#22D3EE") {
                dialog.dismiss()
                onOpenEqualizerTab()
            }
            addSectionHeader(body, "Transiciones y Continuidad")
            addSwitchItem(body, "Reproducción sin pausas (Gapless Playback)", "Elimina cualquier silencio entre pistas consecutivas de un álbum", "audio_gapless", true)
            addSliderItem(body, "Fundido cruzado entre canciones (Crossfade)", "audio_crossfade_sec", 0, 12, 0) { if (it == 0) "Desactivado (0 s)" else "$it segundos" }
            addSwitchItem(body, "Fundido suave al Pausar / Reanudar", "Rampa de volumen de 250 ms al tocar Play o Pausa", "audio_fade_play_pause", true)
            addSwitchItem(body, "Saltar silencios automáticamente (Skip Silence)", "Omite fragmentos mudos al inicio o final de grabaciones", "skip_silence", false)

            addSectionHeader(body, "Normalización y Enfoque de Audio")
            addCycleOptionItem(body, "Normalización de volumen (ReplayGain)", "audio_replaygain_mode", listOf("Desactivado (Bit-Perfect)", "Por pista (Track Gain)", "Por álbum (Album Gain)"))
            addSliderItem(body, "Preamplificación ReplayGain", "audio_rg_preamp", -6, 6, 0) { String.format(Locale.US, "%+d dB", it) }
            addCycleOptionItem(body, "Comportamiento ante notificaciones (Audio Focus)", "audio_focus_mode", listOf("Atenuar volumen temporalmente (Ducking)", "Pausar hasta que termine la notificación", "Ignorar y seguir reproduciendo"))
            addCycleOptionItem(body, "Frecuencia de muestreo de salida preferida", "audio_output_samplerate", listOf("Nativa del archivo (Hi-Res Direct)", "48,000 Hz", "96,000 Hz (24-bit)", "192,000 Hz (DAC USB)"))

            pushPage(page)
        }

        // ---- SUB-SCREEN 4: BIBLIOTECA MUSICAL, INDEXADOR Y METADATOS (Video 1 01:02 + Video 2 00:21-00:22) ----
        fun openLibraryIndexerScreen() {
            val (page, body) = buildSubScreenShell(
                "Biblioteca Musical y Metadatos",
                "Indexador, carpetas, filtros, portadas y letras (Musicolet + Namida)"
            )

            addSectionHeader(body, "Estado del Indexador (Indexer Info)")
            addActionCard(
                body,
                "Pistas indexadas: $totalTracksCount \u2022 Abrir Escáner de Biblioteca",
                "Duplicadas omitidas: 0 \u2022 Filtradas por duración/tamaño: 0 \u2022 Toca para abrir el escáner",
                "#22D3EE"
            ) {
                MusicoletModals.showScannerModal(activity, totalTracksCount, 2) {
                    dialog.dismiss()
                    onRescanLibrary()
                }
            }

            addSectionHeader(body, "Carpetas y Filtros de Biblioteca (Musikbibliothek)")
            addActionCard(body, "Carpetas a escanear (Zu scannende Ordner)", "/storage/emulated/0/Music, /storage/emulated/0/Download (+ Añadir carpeta)") {
                Toast.makeText(activity, "Escaneando todas las carpetas de música del almacenamiento interno", Toast.LENGTH_SHORT).show()
            }
            addActionCard(body, "Carpetas excluidas (Ausgeschlossene Ordner)", "/storage/emulated/0/Android, /storage/emulated/0/WhatsApp Audio") {
                Toast.makeText(activity, "Carpetas de tonos y notas de voz excluidas", Toast.LENGTH_SHORT).show()
            }
            addSliderItem(body, "Tamaño mínimo de archivo (Minimum File Size)", "idx_min_file_kb", 0, 1024, 100) { "$it KB" }
            addSliderItem(body, "Duración mínima de pista (Minimum Track Duration)", "idx_min_dur_sec", 0, 120, 15) { "$it s" }
            addSwitchItem(body, "Evitar pistas duplicadas (Prevent Duplicated tracks)", "Usa nombre de archivo y hash para omitir duplicados", "idx_no_duplicates", true)
            addSwitchItem(body, "Respetar archivos .nomedia (Respect .nomedia)", "No indexar carpetas que contengan un archivo .nomedia", "idx_respect_nomedia", true)
            addSwitchItem(body, "Extraer artistas invitados (feat. / ft.) del título", "Reconoce colaboraciones como artistas adicionales al instante", "idx_extract_feat", true)

            addSectionHeader(body, "Escaneo (Scan)")
            addSwitchItem(body, "Escanear automáticamente al iniciar y al actualizar MediaStore", "Mantiene la biblioteca sincronizada en tiempo real", "idx_auto_scan", true, useCheckBox = true)
            addSwitchItem(body, "Usar MediaStore del sistema (Use Media Store)", "Indexación instantánea de alta velocidad", "idx_use_mediastore", true)
            addSwitchItem(body, "Incluir vídeos musicales (Include videos)", "Permite reproducir videoclips MP4/WebM en la biblioteca", "idx_include_videos", true)

            addSectionHeader(body, "Etiquetas, Portadas de Álbum y Letras (Tags, Album-Cover, Songtexte)")
            addCycleOptionItem(
                body,
                "Criterio al agrupar álbumes (Vorgehen beim Zusammenführen von Alben)",
                "idx_album_merge_rule",
                listOf("Nombre del álbum (Albumname)", "Nombre del álbum + Artista del álbum", "Por carpeta contenedora")
            )
            addCycleOptionItem(
                body,
                "Usar archivos folder.jpg, AlbumArt.jpg, cover.jpg de la carpeta",
                "idx_folder_cover_rule",
                listOf("Cuando no haya carátula incrustada en el archivo", "Siempre preferir imagen de la carpeta", "Nunca")
            )
            addSwitchItem(body, "Activar caché de carátulas (Enable Artwork Cache)", "Carga más rápida de portadas en listas grandes", "idx_artwork_cache", true)
            addSwitchItem(body, "Agrupar carátulas por álbum (Group Artworks by Album)", "Comparte caché de portada entre pistas del mismo álbum", "idx_group_art_album", true)
            addSwitchItem(body, "Hash único de carátula (Unique Artwork Hash)", "Detecta portadas distintas dentro de un mismo álbum", "idx_unique_art_hash", true)
            addCycleOptionItem(
                body,
                "Fuente preferida de letras (Bevorzugte Songtext-Quelle)",
                "idx_lyrics_source",
                listOf("Archivo .LRC local \u2192 Etiqueta incrustada \u2192 LRCLIB Online", "Etiqueta incrustada primero", "Siempre buscar sincronizada en línea (LRCLIB)")
            )
            addSwitchItem(body, "Ocultar información extra en las etiquetas", "Limpia sufijos como (Official Video) o [Remastered] en vista", "idx_hide_extra_tag_info", false)
            addActionCard(body, "Separadores de artistas y géneros", "Artistas: [ / , ; & feat. ft. ] \u2022 Géneros: [ ; / , ] \u2022 Lista negra de extensiones") {
                Toast.makeText(activity, "6 separadores de artistas y 5 de géneros activos", Toast.LENGTH_SHORT).show()
            }

            addSectionHeader(body, "Mantenimiento e Información")
            addActionCard(body, "Re-indexar y refrescar biblioteca ahora", "Busca pistas nuevas o modificadas en el almacenamiento", Pal.ACCENT) {
                dialog.dismiss()
                onRescanLibrary()
            }
            addActionCard(body, "Formatos de archivo soportados (Unterstützte Dateitypen)", "FLAC, ALAC, WAV, AIFF, APE, WV,DSD, OGG, OPUS, MP3, AAC, M4A, MP4, WEBM") {}
            addActionCard(body, "Dispositivos de almacenamiento (Speichergeräte)", "Memoria interna (/storage/emulated/0) \u2022 Tarjeta SD / OTG USB") {}

            pushPage(page)
        }

        // ---- SUB-SCREEN 5: AURICULARES, BLUETOOTH Y ALTAVOCES (Video 1 01:06) ----
        fun openHeadsetBluetoothScreen() {
            val (page, body) = buildSubScreenShell(
                "Auriculares, Bluetooth y Altavoces",
                "Control de conexión, desconexión y botones físicos (Headset-Taste)"
            )

            addSectionHeader(body, "Pausar al desconectar (Pause beim Trennen)")
            addSwitchItem(
                body,
                "Pausar al desconectar",
                "Si está activo, al desconectar auriculares con cable, altavoz o Bluetooth se pausa la reproducción inmediatamente.",
                "hb_pause_on_disconnect",
                true
            )

            addSectionHeader(body, "Reanudar reproducción al conectar (Wiedergabe fortsetzen)")
            addSwitchItem(
                body,
                "Reanudar solo si fue pausado por desconexión",
                "Evita iniciar música si tú habías pausado manualmente antes.",
                "hb_resume_only_if_paused_by_disc",
                true,
                useCheckBox = true
            )
            addSwitchItem(
                body,
                "Reanudar reproducción al conectar por Bluetooth",
                "Inicia automáticamente al vincular auriculares o audio del auto.",
                "hb_resume_on_bt_connect",
                false
            )
            addSwitchItem(
                body,
                "Reanudar reproducción al conectar auriculares/altavoz con cable",
                "Nota: Requiere que el servicio esté activo en segundo plano.",
                "hb_resume_on_wired_connect",
                false
            )

            addSectionHeader(body, "Prevenir reproducción automática no deseada")
            addSwitchItem(
                body,
                "Bloquear reproducción automática no deseada (Auto-Play Guard)",
                "Ignora comandos PLAY espurios enviados por ciertos dispositivos Bluetooth al encenderse.",
                "hb_block_unwanted_autoplay",
                true
            )

            addSectionHeader(body, "Botones de Auriculares (Headset-Taste)")
            addCycleOptionItem(
                body,
                "Doble clic en botón de auriculares (Doppelklick auf Kopfhörertaste)",
                "hb_double_click_action",
                listOf("Siguiente pista (Nächster Titel)", "Pista anterior", "Avance rápido +10s", "Añadir/quitar de Favoritos")
            )
            addCycleOptionItem(
                body,
                "Triple clic en botón de auriculares (Dreifachklick auf Kopfhörertaste)",
                "hb_triple_click_action",
                listOf("Pista anterior (Vorheriger Titel)", "Siguiente pista", "Retroceso rápido -10s", "Cambiar modo de repetición")
            )
            addSliderItem(
                body,
                "Retardo permitido entre pulsaciones sucesivas del botón",
                "hb_click_delay_ms",
                200,
                800,
                400
            ) { "$it ms" }
            addActionCard(
                body,
                "Nota sobre 4 o más pulsaciones",
                "A partir de un cuádruple clic en el botón de los auriculares se activa el avance rápido continuo."
            ) {}

            pushPage(page)
        }

        // ---- SUB-SCREEN 6: NOTIFICACIONES (Benachrichtigungen) ----
        fun openNotificationsScreen() {
            val (page, body) = buildSubScreenShell("Notificaciones", "Estilo y controles del panel multimedia de Android")

            addCycleOptionItem(
                body,
                "Estilo de notificación multimedia",
                "notif_style",
                listOf("Android Estándar con barra de progreso y color de carátula", "Clásico compacto", "Alto contraste AMOLED")
            )
            addSwitchItem(body, "Mostrar botón de Favorito en la notificación", "Permite marcar favoritas sin abrir la app", "notif_show_fav", true)
            addSwitchItem(body, "Mostrar botón de Modo Aleatorio / Repetir", "Acceso rápido desde la cortina de notificaciones", "notif_show_shuffle", true)
            addSwitchItem(body, "Mostrar botones de Avance / Retroceso rápido (10s)", "Útil para audiolibros, podcasts y sesiones largas", "notif_show_fast_seek", false)
            addSwitchItem(body, "Mantener notificación visible cuando está en pausa", "Permite reanudar rápidamente desde el panel", "notif_keep_when_paused", true)

            pushPage(page)
        }

        // ---- SUB-SCREEN 8: PANTALLA DE BLOQUEO (Musicolet-Sperrbildschirm — Video 1 01:18) ----
        fun openLockscreenScreen() {
            val (page, body) = buildSubScreenShell(
                "Pantalla de Bloqueo Personalizada",
                "Control de reproducción sobre la pantalla de bloqueo del sistema (Video 01:18)"
            )

            addSwitchItem(
                body,
                "Activar Pantalla de Bloqueo personalizada",
                "Permite controlar la reproducción, cola y letras directamente al encender la pantalla.",
                "ls_enabled",
                false
            )

            addSectionHeader(body, "Fondo de la pantalla de bloqueo (Hintergrund)")
            val bgModes = listOf(
                "ALBUM" to "Carátula del álbum (Album-Cover)",
                "AMOLED" to "Negro puro (Schwarz AMOLED)",
                "WALLPAPER" to "Fondo de pantalla del sistema (Tapete)"
            )
            val curBg = SonoraStore.getString(activity, "ls_bg_mode", "ALBUM")
            val rg = RadioGroup(activity).apply {
                setBackgroundResource(R.drawable.bg_card_spotiflac)
                setPadding(dp(activity, 14), dp(activity, 10), dp(activity, 14), dp(activity, 10))
            }
            bgModes.forEachIndexed { idx, (code, label) ->
                rg.addView(RadioButton(activity).apply {
                    id = 7000 + idx
                    text = label
                    textSize = 13.5f
                    setTextColor(Color.WHITE)
                    buttonTintList = ColorStateList.valueOf(Color.parseColor("#22D3EE"))
                    isChecked = (code == curBg)
                    setOnClickListener { SonoraStore.putString(activity, "ls_bg_mode", code) }
                })
            }
            body.addView(rg)

            addSectionHeader(body, "Gestos, Controles y Comportamiento (8 opciones Musicolet)")
            addSwitchItem(body, "Deslizar arriba para siguiente pista, abajo para pista anterior", "Gesto vertical rápido sobre la carátula", "ls_swipe_vertical", true, useCheckBox = true)
            addSwitchItem(body, "Mostrar cola actual y letra de la canción", "Acceso completo a la cola y letras sincronizadas sin desbloquear", "ls_show_queue_lyrics", true, useCheckBox = true)
            addSwitchItem(body, "Botones para avance y retroceso rápido (<< / >>)", "Muestra botones de salto de segundos junto a Play/Pausa", "ls_show_fast_seek", true, useCheckBox = true)
            addSwitchItem(body, "Mostrar botón 'Añadir a listas' en lugar de cerrar cuando suena", "Evita cierres accidentales durante la reproducción activa", "ls_add_playlist_instead_close", true, useCheckBox = true)
            addSwitchItem(body, "No mostrar pantalla de bloqueo si el audio lleva más de 1 minuto en pausa", "Ahorra batería y devuelve la pantalla de bloqueo normal", "ls_hide_after_1min_pause", true, useCheckBox = true)
            addSwitchItem(body, "No mostrar si el dispositivo ya está desbloqueado al encender la pantalla", "Útil con desbloqueo por huella o rostro instantáneo", "ls_hide_when_unlocked", false, useCheckBox = true)
            addSwitchItem(body, "No apagar la pantalla mientras se muestra la pantalla de bloqueo", "Mantiene la pantalla encendida en soporte o dock", "ls_keep_screen_on", false, useCheckBox = true)
            addSwitchItem(body, "No apagar la pantalla mientras se muestra la letra de la canción", "Ideal para seguir la letra o cantar sin que se apague la pantalla", "ls_keep_screen_on_lyrics", false, useCheckBox = true)

            pushPage(page)
        }

        // ---- SUB-SCREEN 9: AVANZADO Y RENDIMIENTO (Erweitert Video 1 01:11-01:13 + Namida Advanced 00:29) ----
        fun openAdvancedScreen() {
            val (page, body) = buildSubScreenShell(
                "Ajustes Avanzados y Caché",
                "Comportamiento de colas, saltos, búsqueda externa, ordenación y memoria (Erweitert)"
            )

            addSectionHeader(body, "Comportamiento General y Segundo Plano")
            addSwitchItem(
                body,
                "Detener reproducción y cerrar la app al quitarla de aplicaciones recientes",
                "Si se activa, deslizar la app fuera de multitarea detendrá la música.",
                "adv_stop_on_task_removed",
                false,
                useCheckBox = true
            )
            addSwitchItem(
                body,
                "Mostrar el nombre del archivo en lugar del título de la etiqueta",
                "Muestra el nombre real del archivo (.flac / .mp3) en todas las listas.",
                "adv_display_filename",
                false,
                useCheckBox = true
            )
            addSwitchItem(
                body,
                "Permitir reproducción en segundo plano sin restricciones",
                "Impide que el sistema Android cierre el motor de audio cuando la pantalla está apagada.",
                "adv_allow_bg_playback",
                true
            )
            addSwitchItem(
                body,
                "Intercambiar botones 'Siguiente/Anterior' con 'Avance/Retroceso rápido'",
                "Usa un toque para avanzar/retroceder segundos y pulsación larga para cambiar de pista.",
                "adv_swap_next_with_fastseek",
                false,
                useCheckBox = true
            )
            addCycleOptionItem(
                body,
                "Recordar última posición de reproducción de cada pista",
                "adv_remember_track_pos",
                listOf("Desactivado (Ninguna)", "Solo en pistas largas (> 10 min: Audiolibros / Podcasts)", "En todas las pistas")
            )
            addCycleOptionItem(
                body,
                "Duración mínima reproducida para sumar al contador de reproducciones",
                "adv_min_play_count_sec",
                listOf("4 segundos (Predeterminado)", "15 segundos", "30 segundos", "50% de la duración de la pista")
            )
            addCycleOptionItem(
                body,
                "Cerrar automáticamente cuando no hay reproducción activa",
                "adv_auto_exit_idle",
                listOf("Nunca", "Tras 5 minutos en pausa", "Tras 15 minutos en pausa", "Tras 30 minutos en pausa")
            )

            addSectionHeader(body, "Botón Atrás, Modo Aleatorio y Colas")
            addCycleOptionItem(
                body,
                "Cuando presiono el botón Atrás en la pantalla principal...",
                "adv_back_btn_behavior",
                listOf("Recordar historial de pestañas y volver a la pestaña anterior", "Salir inmediatamente al inicio")
            )
            addSwitchItem(body, "Confirmar antes de cerrar al pulsar el botón Atrás", "Pide un segundo toque en Atrás para salir", "adv_confirm_back_exit", false, useCheckBox = true)
            addCycleOptionItem(
                body,
                "Cuando se activa el modo aleatorio (Shuffle)...",
                "adv_shuffle_scope",
                listOf("Mezclar todas las pistas en todas las colas", "Mezclar únicamente la cola actual")
            )
            addCycleOptionItem(
                body,
                "Comportamiento al elegir la opción Mezclar en una cola...",
                "adv_queue_shuffle_behavior",
                listOf("Mezclar todas las pistas de esta cola", "Crear una nueva cola mezclada")
            )
            addCycleOptionItem(
                body,
                "Cuando ya existe una cola con el mismo nombre que el origen...",
                "adv_duplicate_queue_rule",
                listOf("Reemplazar la cola existente por el origen", "Conservar ambas colas por separado")
            )
            addSwitchItem(body, "Iniciar la reproducción automáticamente al abrir la aplicación", "Reanuda la última canción nada más abrir la app", "adv_autoplay_on_launch", false)
            addSwitchItem(body, "Cargar carátulas de álbumes (Album-Cover laden)", "Si se desactiva, no se cargará ninguna portada para máximo ahorro de RAM", "adv_load_album_covers", true)
            addSwitchItem(body, "Control externo avanzado (Erweiterte externe Steuerung)", "Permitir a relojes, Android Auto y otras apps controlar repetición, aleatorio y favoritos", "adv_external_control", true)

            addSectionHeader(body, "Tiempos de Salto Rápido (Sprungzeit)")
            addSliderItem(body, "Tiempo de salto en avance rápido (>>)", "seek_fwd_sec", 3, 60, 10) { "$it segundos" }
            addSliderItem(body, "Tiempo de salto en retroceso rápido (<<)", "seek_back_sec", 3, 60, 10) { "$it segundos" }

            addSectionHeader(body, "Búsqueda con Otras Aplicaciones (Suche mit anderen Apps)")
            addCycleOptionItem(body, "Motor de búsqueda predeterminado", "adv_search_engine", listOf("Google", "DuckDuckGo", "YouTube Music", "Deezer Hi-Res", "Genius Lyrics"))
            addActionCard(body, "Plantilla de consulta para buscar carátulas", "album art <al> <a>") {}
            addActionCard(body, "Plantilla de consulta para buscar letras", "lyrics <t> <a> <al>") {}

            addSectionHeader(body, "Ordenación Avanzada (Sortieren)")
            addCycleOptionItem(body, "Mezcla avanzada (Erweitertes Mischen)", "adv_smart_shuffle", listOf("Ponderar por menos escuchadas recientemente", "Agrupar por álbum completo", "Totalmente aleatorio (Fisher-Yates)"))
            addSwitchItem(body, "Ignorar artículos iniciales al ordenar ('The', 'A', 'El', 'La', 'Los')", "Útil para ordenar 'The Beatles' en la B o 'Los Bunkers' en la B", "sort_ignore_articles", true)
            addCycleOptionItem(body, "Ordenación alfanumérica (Alphanumerische Sortierung)", "adv_alphanum_sort", listOf("Natural (Pista 1, Pista 2, Pista 10)", "ASCII estricto (Ninguna)"))

            addSectionHeader(body, "Rendimiento y Gestión de Caché (Namida Advanced)")
            addCycleOptionItem(body, "Modo de rendimiento (Performance mode)", "adv_perf_mode", listOf("Personalizado (60 FPS Ultra Fluido)", "Equilibrado", "Ahorro de batería"))
            addSwitchItem(body, "Comprimir imágenes en caché (Compress Images)", "Reduce el uso de almacenamiento de miniaturas", "adv_compress_cache_imgs", false)
            addSliderItem(body, "Tamaño máximo de caché de imágenes", "adv_max_img_cache_mb", 100, 5000, 1470) { String.format(Locale.US, "%.2f GB", it / 1000f) }
            addSliderItem(body, "Tamaño máximo de caché de audio", "adv_max_audio_cache_mb", 500, 10000, 4210) { String.format(Locale.US, "%.2f GB", it / 1000f) }
            addSliderItem(body, "Tamaño máximo de caché de vídeo", "adv_max_video_cache_mb", 1000, 30000, 25790) { String.format(Locale.US, "%.2f GB", it / 1000f) }
            addActionCard(body, "Limpiar caché de imágenes, audio y vídeo", "Libera espacio temporal sin borrar tu música ni tus listas", "#22D3EE") {
                try { activity.cacheDir.deleteRecursively() } catch (_: Exception) {}
                Toast.makeText(activity, "Caché temporal limpiada correctamente", Toast.LENGTH_SHORT).show()
            }

            pushPage(page)
        }

        // ---- SUB-SCREEN 10: COPIA DE SEGURIDAD Y RESTAURACIÓN (Video 1 01:15 + Video 2 00:06) ----
        fun openBackupRestoreScreen() {
            val (page, body) = buildSubScreenShell(
                "Copia de Seguridad y Restauración",
                "Respalda tus listas, favoritos, estadísticas, marcadores y ajustes (Sichern/Wiederherstellen)"
            )

            val savedListContainer = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }

            fun refreshBackupsList() {
                savedListContainer.removeAllViews()
                val files = SonoraStore.listBackupFiles(activity)
                if (files.isEmpty()) {
                    savedListContainer.addView(TextView(activity).apply {
                        text = "Aún no hay archivos de copia de seguridad guardados."
                        textSize = 12.5f
                        setTextColor(Color.parseColor("#64748B"))
                        setPadding(dp(activity, 4), dp(activity, 6), dp(activity, 4), dp(activity, 6))
                    })
                    return
                }
                val df = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault())
                for (f in files) {
                    addActionCard(
                        savedListContainer,
                        "\u21BB  Restaurar: ${f.name}",
                        "Creado el ${df.format(Date(f.lastModified()))} \u2022 ${String.format(Locale.US, "%.1f KB", f.length() / 1024f)}",
                        Pal.ACCENT
                    ) {
                        val ok = SonoraStore.restoreFromBackupFile(activity, f)
                        if (ok) {
                            Toast.makeText(activity, "Copia de seguridad restaurada con éxito (${f.name})", Toast.LENGTH_LONG).show()
                            onRefreshUi()
                        } else {
                            Toast.makeText(activity, "No se pudo restaurar el archivo seleccionado", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            addSectionHeader(body, "Copia de seguridad manual (Manuelle Sicherung)")
            addActionCard(
                body,
                "Crear copia de seguridad ahora (Jetzt sichern)",
                "Guarda listas de reproducción, favoritos, contadores de reproducción, marcadores y todos los ajustes.",
                "#22D3EE"
            ) {
                val file = SonoraStore.createBackupFile(activity)
                Toast.makeText(
                    activity,
                    "Copia de seguridad creada con éxito:\n${file.name}",
                    Toast.LENGTH_LONG
                ).show()
                refreshBackupsList()
            }

            addSectionHeader(body, "Copia de seguridad automática (Automatische Sicherung)")
            addSwitchItem(
                body,
                "Copia de seguridad automática",
                "Respalda automáticamente tus listas y ajustes cada 24 horas.",
                "backup_auto_enabled",
                true
            )
            addActionCard(
                body,
                "Ruta de almacenamiento (Pfad)",
                SonoraStore.backupDirectory(activity).absolutePath
            ) {}

            addSectionHeader(body, "Restaurar (Wiederherstellen)")
            body.addView(savedListContainer)
            refreshBackupsList()

            addSectionHeader(body, "Información")
            addActionCard(
                body,
                "¿Qué incluye una copia de seguridad?",
                "Un archivo de copia de seguridad no duplica los archivos de audio pesados; contiene de forma compacta tus listas de reproducción, canciones favoritas, contadores de reproducciones, recortes, marcadores/notas, ajustes de widgets, ecualizador y preferencias."
            ) {}

            pushPage(page)
        }

        // ---- MAIN SETTINGS ROOT SCREEN (Matching Video 1 00:54 & Video 2 00:20) ----
        val (mainPage, mainBody) = buildSubScreenShell(
            "Ajustes (Einstellungen / Settings)",
            "Configuración completa del motor de audio, interfaz, biblioteca y widgets"
        )

        data class CatItem(val iconRes: Int, val title: String, val subtitle: String, val onClick: () -> Unit)
        val categories = listOf(
            CatItem(
                R.drawable.ic_globe,
                "Idioma (Sprache)",
                SonoraStore.getString(activity, "app_language", "Español (Predeterminado)")
            ) { openLanguageScreen() },
            CatItem(
                R.drawable.ic_sparkles,
                "Interfaz, Tema y Personalización (Oberfläche)",
                "Tema AMOLED, colores dinámicos, pestañas, animaciones, mosaicos y gestos"
            ) { openInterfaceScreen() },
            CatItem(
                R.drawable.ic_sliders,
                "Audio y Reproducción",
                "Ecualizador de 31 bandas, Surround, Reverb de Sala, Gapless, Crossfade y ReplayGain"
            ) { openAudioScreen() },
            CatItem(
                R.drawable.ic_folder,
                "Biblioteca Musical, Indexador y Metadatos",
                "Carpetas, exclusiones, escáner profundo, portadas folder.jpg y fuente de letras"
            ) { openLibraryIndexerScreen() },
            CatItem(
                R.drawable.ic_bolt,
                "Auriculares, Bluetooth y Altavoces",
                "Pausa al desconectar, reanudación automática y acciones de doble/triple clic"
            ) { openHeadsetBluetoothScreen() },
            CatItem(
                R.drawable.ic_music,
                "Notificaciones (Benachrichtigungen)",
                "Estilo del panel multimedia y botones de acción rápida"
            ) { openNotificationsScreen() },
            CatItem(
                R.drawable.ic_widgets,
                "Widgets de Pantalla de Inicio (Homescreen-Widgets)",
                "Diseño claro/oscuro/álbum, transparencia, Básico, Avanzado y Cola Actual"
            ) {
                dialog.dismiss()
                onOpenWidgetTab()
            },
            CatItem(
                R.drawable.ic_disc,
                "Pantalla de Bloqueo (Sperrbildschirm)",
                "Fondo de carátula o AMOLED, gestos verticales, cola y letras en bloqueo"
            ) { openLockscreenScreen() },
            CatItem(
                R.drawable.ic_wand,
                "Avanzado y Rendimiento (Erweitert)",
                "Comportamiento de colas, saltos rápidos, búsqueda externa, ordenación y caché"
            ) { openAdvancedScreen() },
            CatItem(
                R.drawable.ic_download,
                "Copia de Seguridad y Restauración (Sichern/Wiederherstellen)",
                "Respaldo manual y automático de listas, favoritos, estadísticas y ajustes"
            ) { openBackupRestoreScreen() }
        )

        for (cat in categories) {
            val card = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.bg_card_spotiflac)
                setPadding(dp(activity, 14), dp(activity, 14), dp(activity, 14), dp(activity, 14))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(activity, 8) }
                setOnClickListener { cat.onClick() }
            }
            card.addView(ImageView(activity).apply {
                setImageResource(cat.iconRes)
                setColorFilter(Color.parseColor("#22D3EE"))
                val p = dp(activity, 8)
                setPadding(p, p, p, p)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#1422D3EE"))
                }
                layoutParams = LinearLayout.LayoutParams(dp(activity, 38), dp(activity, 38)).apply {
                    marginEnd = dp(activity, 14)
                }
            })
            val textCol = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            textCol.addView(TextView(activity).apply {
                text = cat.title
                textSize = 14.5f
                setTextColor(Color.WHITE)
                setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            })
            textCol.addView(TextView(activity).apply {
                text = cat.subtitle
                textSize = 12f
                setTextColor(Color.parseColor("#94A3B8"))
                setPadding(0, dp(activity, 2), 0, 0)
            })
            card.addView(textCol)
            card.addView(ImageView(activity).apply {
                setImageResource(R.drawable.ic_chevron_right)
                setColorFilter(Color.parseColor("#64748B"))
                layoutParams = LinearLayout.LayoutParams(dp(activity, 18), dp(activity, 18)).apply {
                    marginStart = dp(activity, 8)
                }
            })
            mainBody.addView(card)
        }

        pushPage(mainPage)
        dialog.setContentView(root)
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK && event.action == android.view.KeyEvent.ACTION_UP) {
                popPage()
                true
            } else false
        }
        dialog.show()
    }
}

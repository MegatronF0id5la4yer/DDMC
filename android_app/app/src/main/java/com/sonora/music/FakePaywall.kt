package com.sonora.music

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.CountDownTimer
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

object FakePaywall {

    private var actionCounter = 0
    private var isPaywallActive = false

    fun maybeIntercept(context: Context, actionName: String, onProceed: () -> Unit) {
        if (!BuildConfig.IS_TROLL_EDITION) {
            onProceed()
            return
        }

        actionCounter++
        showPaywallDialog(context, actionName, onProceed)
    }

    private fun showPaywallDialog(context: Context, actionName: String, onProceed: () -> Unit) {
        if (isPaywallActive) return
        isPaywallActive = true

        val dialog = Dialog(context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setCancelable(false)
        }

        val scroll = ScrollView(context).apply {
            isFillViewport = true
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(context.dp(20), context.dp(24), context.dp(20), context.dp(24))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#180A0A"))
                cornerRadius = context.dpf(24f)
                setStroke(context.dp(2), Color.parseColor("#EF4444"))
            }
            layoutParams = FrameLayout.LayoutParams(
                context.dp(340),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }

        val badge = TextView(context).apply {
            text = "ALTO AHI, PARASITO SOCIAL"
            textSize = 11f
            setTextColor(Color.WHITE)
            setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD))
            setPadding(context.dp(10), context.dp(4), context.dp(10), context.dp(4))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#DC2626"))
                cornerRadius = context.dpf(8f)
            }
        }
        root.addView(badge)

        val title = TextView(context).apply {
            text = "DDMC PLUS: MEMBRESIA OBLIGATORIA"
            textSize = 17f
            setTextColor(Color.WHITE)
            setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD))
            gravity = Gravity.CENTER
            setPadding(0, context.dp(10), 0, context.dp(4))
        }
        root.addView(title)

        val desc = TextView(context).apply {
            text = "Intentaste: '$actionName'.\nEsta funcion requiere una suscripcion activa al Patreon de Doomentio o financiar el capitulo 1 de Nostalgia y Pasion."
            textSize = 12.5f
            setTextColor(Color.parseColor("#FCA5A5"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, context.dp(14))
        }
        root.addView(desc)

        fun makePlan(name: String, price: String, benefit: String): LinearLayout {
            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(context.dp(12), context.dp(10), context.dp(12), context.dp(10))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#261111"))
                    cornerRadius = context.dpf(12f)
                    setStroke(context.dp(1), Color.parseColor("#7F1D1D"))
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = context.dp(8) }

                val top = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    val n = TextView(context).apply {
                        text = name
                        setTextColor(Color.WHITE)
                        textSize = 13f
                        setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    val p = TextView(context).apply {
                        text = price
                        setTextColor(Color.parseColor("#F87171"))
                        textSize = 13f
                        setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
                    }
                    addView(n)
                    addView(p)
                }
                val b = TextView(context).apply {
                    text = benefit
                    setTextColor(Color.parseColor("#9CA3AF"))
                    textSize = 11f
                    setPadding(0, context.dp(2), 0, 0)
                }
                addView(top)
                addView(b)
            }
        }

        root.addView(makePlan("Plan Ario Honorario", "$99.99 / sem", "Permite pausar la musica y mirar a la Doki sin culpa"))
        root.addView(makePlan("Plan Oveja Plus", "$49.99 / mes", "Incluye 1 litro de leche y salto de canciones"))
        root.addView(makePlan("Plan Jerry Engine Pro", "$15.00 / accion", "Desbloquea el ecualizador y quita este mensaje por 2 minutos"))

        val btnPay = TextView(context).apply {
            text = "PAGAR $99.99 CON NOSTRACOINS"
            textSize = 13f
            setTextColor(Color.WHITE)
            setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD))
            gravity = Gravity.CENTER
            setPadding(context.dp(16), context.dp(12), context.dp(16), context.dp(12))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#DC2626"))
                cornerRadius = context.dpf(14f)
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = context.dp(6); bottomMargin = context.dp(8) }
            isClickable = true
            setOnClickListener {
                Toast.makeText(
                    context,
                    "Error: Tarjeta de debito Coppel rechazada por saldo insuficiente. Vende un colchon viejo para continuar.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        root.addView(btnPay)

        val btnAd = TextView(context).apply {
            text = "Ver anuncio de 30s de Skiso para saltar"
            textSize = 12f
            setTextColor(Color.parseColor("#F87171"))
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            gravity = Gravity.CENTER
            setPadding(context.dp(14), context.dp(10), context.dp(14), context.dp(10))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#3B1212"))
                cornerRadius = context.dpf(12f)
                setStroke(context.dp(1), Color.parseColor("#EF4444"))
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = context.dp(8) }
            isClickable = true
        }

        btnAd.setOnClickListener {
            btnAd.isEnabled = false
            object : CountDownTimer(4000, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val sec = millisUntilFinished / 1000 + 1
                    btnAd.text = "Viendo anuncio de Colchones Perez... $sec s"
                }

                override fun onFinish() {
                    isPaywallActive = false
                    dialog.dismiss()
                    Toast.makeText(context, "Anuncio completado. Accion autorizada.", Toast.LENGTH_SHORT).show()
                    onProceed()
                }
            }.start()
        }
        root.addView(btnAd)

        val btnPobre = TextView(context).apply {
            text = "Continuar como usuario pobre (humillacion publica)"
            textSize = 10.5f
            setTextColor(Color.parseColor("#6B7280"))
            gravity = Gravity.CENTER
            setPadding(0, context.dp(6), 0, context.dp(4))
            isClickable = true
            setOnClickListener {
                isPaywallActive = false
                dialog.dismiss()
                Toast.makeText(context, "Has elegido la pobreza. Doomentio se decepciona de ti.", Toast.LENGTH_SHORT).show()
                onProceed()
            }
        }
        root.addView(btnPobre)

        scroll.addView(root)
        dialog.setContentView(scroll)
        dialog.show()
    }
}

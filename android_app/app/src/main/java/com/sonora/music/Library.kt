package com.sonora.music

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

sealed class Row {
    data class Title(val text: String, val sub: String = "") : Row()
    data class Actions(val tracks: List<TrackItem>) : Row()
    data class Song(val track: TrackItem, val list: List<TrackItem>, val index: Int) : Row()
    data class Group(
        val kind: String,
        val name: String,
        val sub: String,
        val tracks: List<TrackItem>,
        val icon: Int,
        val cover: TrackItem? = null,
        val menu: Boolean = false
    ) : Row()
    data class Empty(val title: String, val desc: String, val buttons: Boolean) : Row()
}

interface RowHost {
    fun onPlay(list: List<TrackItem>, index: Int)
    fun onPlayAll(list: List<TrackItem>, shuffle: Boolean)
    fun onTrailing(track: TrackItem, index: Int)
    fun onGroupOpen(g: Row.Group)
    fun onGroupMenu(g: Row.Group)
    fun onEmptyOpen()
    fun onEmptyScan()
    fun currentKey(): String?
}

/** One adapter for songs, albums/artists/folders, playlists, queue and empty states. */
class RowAdapter(
    private val ctx: Context,
    private val host: RowHost,
    private val trailingIcon: Int = R.drawable.ic_more
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var rows: List<Row> = emptyList()
        private set

    fun submit(r: List<Row>) {
        rows = r
        notifyDataSetChanged()
    }

    fun move(from: Int, to: Int) {
        val m = rows.toMutableList()
        m.add(to, m.removeAt(from))
        rows = m
        notifyItemMoved(from, to)
    }

    override fun getItemCount() = rows.size

    override fun getItemViewType(position: Int) = when (rows[position]) {
        is Row.Title -> 0
        is Row.Actions -> 1
        is Row.Song -> 2
        is Row.Group -> 3
        is Row.Empty -> 4
    }

    private class VH(v: View) : RecyclerView.ViewHolder(v)

    private fun tv(size: Float, color: String, bold: Boolean = false) = TextView(ctx).apply {
        textSize = size
        setTextColor(Color.parseColor(color))
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        if (bold) setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val lp = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        val root: View = when (viewType) {
            0 -> LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(ctx.dp(4), ctx.dp(10), ctx.dp(4), ctx.dp(8))
                addView(tv(20f, Pal.TEXT, true).apply { tag = "t" })
                addView(tv(12f, Pal.SUB).apply { tag = "s" })
            }
            1 -> LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, ctx.dp(4), 0, ctx.dp(10))
                val play = Pill(ctx, "Reproducir", R.drawable.ic_play_filled).apply { tag = "play"; setActive(true) }
                val shuf = Pill(ctx, "Aleatorio", R.drawable.ic_shuffle).apply { tag = "shuf" }
                addView(play, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = ctx.dp(8) })
                addView(shuf, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
            2 -> LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(ctx.dp(4), ctx.dp(7), ctx.dp(2), ctx.dp(7))
                isClickable = true
                isFocusable = true
                addPressFeedback(0.985f, haptic = false)
                addView(CoverView(ctx, 48, 10).apply { tag = "c" })
                addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = ctx.dp(12)
                        marginEnd = ctx.dp(8)
                    }
                    addView(tv(14.5f, Pal.TEXT, true).apply { tag = "t" })
                    addView(tv(12f, Pal.SUB).apply { tag = "s"; setPadding(0, ctx.dp(2), 0, 0) })
                })
                addView(tv(11.5f, Pal.MUTED).apply { tag = "d"; fontFeatureSettings = "tnum" })
                addView(ImageView(ctx).apply {
                    tag = "m"
                    setImageResource(trailingIcon)
                    setColorFilter(Color.parseColor("#9CA3AF"))
                    val p = ctx.dp(10)
                    setPadding(p, p, p, p)
                    layoutParams = LinearLayout.LayoutParams(ctx.dp(42), ctx.dp(42))
                    isClickable = true
                    addPressFeedback(0.85f, haptic = false)
                })
            }
            3 -> LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(ctx.dp(4), ctx.dp(7), ctx.dp(2), ctx.dp(7))
                isClickable = true
                isFocusable = true
                addPressFeedback(0.985f, haptic = false)
                addView(CoverView(ctx, 52, 12).apply { tag = "c" })
                addView(ImageView(ctx).apply {
                    tag = "i"
                    setColorFilter(Color.parseColor(Pal.CYAN))
                    val p = ctx.dp(14)
                    setPadding(p, p, p, p)
                    layoutParams = LinearLayout.LayoutParams(ctx.dp(52), ctx.dp(52)).apply { marginStart = -ctx.dp(52) }
                })
                addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = ctx.dp(14)
                        marginEnd = ctx.dp(8)
                    }
                    addView(tv(15f, Pal.TEXT, true).apply { tag = "t" })
                    addView(tv(12f, Pal.SUB).apply { tag = "s"; setPadding(0, ctx.dp(2), 0, 0) })
                })
                addView(ImageView(ctx).apply {
                    tag = "m"
                    setImageResource(R.drawable.ic_more)
                    setColorFilter(Color.parseColor("#9CA3AF"))
                    val p = ctx.dp(10)
                    setPadding(p, p, p, p)
                    layoutParams = LinearLayout.LayoutParams(ctx.dp(42), ctx.dp(42))
                    isClickable = true
                    addPressFeedback(0.85f, haptic = false)
                })
            }
            else -> LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundResource(R.drawable.bg_card_spotiflac)
                setPadding(ctx.dp(22), ctx.dp(30), ctx.dp(22), ctx.dp(26))
                addView(ImageView(ctx).apply {
                    setImageResource(R.drawable.ic_folder)
                    setColorFilter(Color.parseColor(Pal.CYAN))
                    layoutParams = LinearLayout.LayoutParams(ctx.dp(44), ctx.dp(44)).apply { bottomMargin = ctx.dp(12) }
                })
                addView(tv(16f, Pal.TEXT, true).apply { tag = "t"; gravity = Gravity.CENTER })
                addView(tv(12.5f, Pal.SUB).apply {
                    tag = "s"
                    maxLines = 5
                    gravity = Gravity.CENTER
                    setPadding(0, ctx.dp(6), 0, ctx.dp(16))
                })
                addView(LinearLayout(ctx).apply {
                    tag = "b"
                    gravity = Gravity.CENTER
                    addView(Pill(ctx, "Abrir archivo", R.drawable.ic_folder).apply { tag = "open"; setActive(true) },
                        LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = ctx.dp(8) })
                    addView(Pill(ctx, "Escanear", R.drawable.ic_refresh).apply { tag = "scan" })
                })
            }
        }
        root.layoutParams = lp
        if (viewType == 4) (lp as ViewGroup.MarginLayoutParams).topMargin = ctx.dp(12)
        return VH(root)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val v = holder.itemView
        when (val row = rows[position]) {
            is Row.Title -> {
                v.findViewWithTag<TextView>("t").text = row.text
                v.findViewWithTag<TextView>("s").apply {
                    text = row.sub
                    visibility = if (row.sub.isEmpty()) View.GONE else View.VISIBLE
                }
            }
            is Row.Actions -> {
                v.findViewWithTag<View>("play").setOnClickListener { host.onPlayAll(row.tracks, false) }
                v.findViewWithTag<View>("shuf").setOnClickListener { host.onPlayAll(row.tracks, true) }
            }
            is Row.Song -> {
                val t = row.track
                val cur = host.currentKey() == t.key
                v.findViewWithTag<CoverView>("c").bind(t)
                v.findViewWithTag<TextView>("t").apply {
                    text = t.title
                    setTextColor(Color.parseColor(if (cur) Pal.ACCENT else Pal.TEXT))
                }
                v.findViewWithTag<TextView>("s").text = listOf(t.artist, t.album).filter { it.isNotBlank() }.joinToString(" \u2022 ")
                v.findViewWithTag<TextView>("d").text = if (t.durationMs > 0) formatClock(t.durationMs) else ""
                v.findViewWithTag<View>("m").setOnClickListener { host.onTrailing(t, row.index) }
                v.setOnClickListener { host.onPlay(row.list, row.index) }
            }
            is Row.Group -> {
                val c = v.findViewWithTag<CoverView>("c")
                c.bind(row.cover)
                v.findViewWithTag<ImageView>("i").apply {
                    setImageResource(row.icon)
                    visibility = if (row.cover?.coverBitmap != null || !row.cover?.coverUrl.isNullOrEmpty()) View.GONE else View.VISIBLE
                }
                v.findViewWithTag<TextView>("t").text = row.name
                v.findViewWithTag<TextView>("s").text = row.sub
                v.findViewWithTag<View>("m").apply {
                    visibility = if (row.menu) View.VISIBLE else View.INVISIBLE
                    setOnClickListener { host.onGroupMenu(row) }
                }
                v.setOnClickListener { host.onGroupOpen(row) }
            }
            is Row.Empty -> {
                v.findViewWithTag<TextView>("t").text = row.title
                v.findViewWithTag<TextView>("s").text = row.desc
                v.findViewWithTag<View>("b").visibility = if (row.buttons) View.VISIBLE else View.GONE
                v.findViewWithTag<View>("open").setOnClickListener { host.onEmptyOpen() }
                v.findViewWithTag<View>("scan").setOnClickListener { host.onEmptyScan() }
            }
        }
    }
}


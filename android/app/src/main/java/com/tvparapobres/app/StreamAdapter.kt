package com.tvparapobres.app

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

/**
 * Tarjetas de canales/películas/series con:
 * - estrella de favoritos (☆/★, tint del acento del perfil)
 * - footer "Ver más" paginado para LIVE
 * - focus de TV con escala + borde de acento
 */
class StreamAdapter(
    @Volatile var accent: Int,
    private val tvMode: Boolean,
    private val onOpen: (Strm) -> Unit,
    private val onFav: (Strm) -> Unit,
    private val onMore: () -> Unit,
    private val onHistDel: (String) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_STREAM = 0
        const val TYPE_MORE = 1
        const val TYPE_HIST = 2
    }

    private val items = mutableListOf<Strm>()
    private var hasMore = false
    private val favSet = HashSet<String>()
    var histMode = false
    private var moreLabel = "VER MÁS ›"
    // Nombres ya limpiados: evita repetir regex en cada bind/scroll (fluidez + anti-ANR).
    private val cleanCache = object : LinkedHashMap<String, String>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>): Boolean = size > 600
    }

    private fun cleanName(raw: String): String {
        cleanCache[raw]?.let { return it }
        val c = NameCleaner.clean(raw)
        cleanCache[raw] = c
        return c
    }

    fun setFavs(list: List<Strm>) {
        favSet.clear()
        for (s in list) favSet.add("${s.type}:${s.id}")
        notifyDataSetChanged()
    }

    fun applyFav(s: Strm, fav: Boolean) {
        val k = "${s.type}:${s.id}"
        if (fav) favSet.add(k) else favSet.remove(k)
        val i = items.indexOfFirst { it.type == s.type && it.id == s.id }
        if (i >= 0) notifyItemChanged(i)
    }

    fun submit(list: List<Strm>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun append(more: List<Strm>) {
        val start = items.size
        items.addAll(more)
        notifyItemRangeInserted(if (hasMore) start else start, more.size)
    }

    fun setHasMore(b: Boolean) {
        if (hasMore == b) return
        hasMore = b
        if (b) notifyItemInserted(items.size) else notifyItemRemoved(items.size)
    }

    /** Etiqueta del footer como la web: "Ver más (N) ↓". */
    fun setMoreLabel(remaining: Int) {
        moreLabel = "Ver más ($remaining) ↓"
        if (hasMore && items.isNotEmpty()) notifyItemChanged(items.size)
    }

    private fun lighten(c: Int, k: Float): Int {
        fun m(v: Int) = (v + (255 - v) * k).toInt().coerceIn(0, 255)
        return android.graphics.Color.rgb(m(android.graphics.Color.red(c)), m(android.graphics.Color.green(c)), m(android.graphics.Color.blue(c)))
    }

    private fun accentStrong(): Int = lighten(accent, 0.4f)

    private fun fmtTime(ms: Long): String {
        if (ms <= 0) return ""
        val t = ms / 1000
        val h = t / 3600
        val m = (t % 3600) / 60
        val s = t % 60
        val mm = if (h > 0) m.toString().padStart(2, '0') else m.toString()
        val ss = s.toString().padStart(2, '0')
        return if (h > 0) "$h:$mm:$ss" else "$m:$ss"
    }

    fun shownCount(): Int = items.size

    fun favCount(): Int = favSet.size

    fun hasMore(): Boolean = hasMore

    override fun getItemCount(): Int = items.size + if (hasMore) 1 else 0

    override fun getItemViewType(position: Int): Int =
        if (position < items.size) {
            if (histMode) TYPE_HIST else TYPE_STREAM
        } else TYPE_MORE

    inner class StreamHolder(view: View) : RecyclerView.ViewHolder(view) {
        val logo: ImageView = view.findViewById(R.id.logo)
        val name: TextView = view.findViewById(R.id.name)
        val btnFav: TextView = view.findViewById(R.id.btnFav)
    }

    inner class HistHolder(view: View) : RecyclerView.ViewHolder(view) {
        val logo: ImageView = view.findViewById(R.id.hlogo)
        val name: TextView = view.findViewById(R.id.hname)
        val bar: android.widget.ProgressBar = view.findViewById(R.id.hbar)
        val meta: TextView = view.findViewById(R.id.hmeta)
        val del: TextView = view.findViewById(R.id.hdel)
    }

    inner class MoreHolder(val btn: MaterialButton) : RecyclerView.ViewHolder(btn)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (viewType == TYPE_MORE) {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_more, parent, false) as MaterialButton
            v.isFocusable = true
            v.isFocusableInTouchMode = true
            v.setOnClickListener { onMore() }
            return MoreHolder(v)
        }
        if (viewType == TYPE_HIST) {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false) as MaterialCardView
            if (tvMode) {
                v.isFocusable = true
                v.isFocusableInTouchMode = true
                v.setOnFocusChangeListener { view, has ->
                    view.scaleX = if (has) 1.04f else 1f
                    view.scaleY = if (has) 1.04f else 1f
                    val card = view as MaterialCardView
                    card.strokeColor = if (has) accent else 0x12FFFFFF.toInt()
                    card.strokeWidth = if (has) 3 else 1
                }
            }
            return HistHolder(v)
        }
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_stream, parent, false) as MaterialCardView
        if (tvMode) {
            v.isFocusable = true
            v.isFocusableInTouchMode = true
            v.setOnFocusChangeListener { view, has ->
                view.scaleX = if (has) 1.04f else 1f
                view.scaleY = if (has) 1.04f else 1f
                val card = view as MaterialCardView
                card.strokeColor = if (has) accent else 0x12FFFFFF.toInt()
                card.strokeWidth = if (has) 3 else 1
            }
        }
        return StreamHolder(v)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is MoreHolder -> {
                holder.btn.text = moreLabel
                holder.btn.setTextColor(accentStrong())
                holder.btn.strokeColor = ColorStateList.valueOf(accent)
            }
            is HistHolder -> {
                val s = items[position]
                holder.name.text = cleanName(s.name)
                if (s.icon.isNotBlank()) {
                    Glide.with(holder.logo)
                        .load(s.icon)
                        .placeholder(R.drawable.ic_launcher)
                        .fallback(R.drawable.ic_launcher)
                        .error(R.drawable.ic_launcher)
                        .into(holder.logo)
                } else {
                    holder.logo.setImageResource(R.drawable.ic_launcher)
                }
                if (s.dur > 0) {
                    holder.bar.visibility = View.VISIBLE
                    holder.bar.max = 100
                    holder.bar.progress = ((s.pos * 100 / s.dur).toInt()).coerceIn(0, 100)
                    holder.bar.progressTintList = ColorStateList.valueOf(accent)
                    holder.bar.backgroundTintList = ColorStateList.valueOf(0x14FFFFFF.toInt())
                } else {
                    holder.bar.visibility = View.GONE
                }
                if (s.type == "live") {
                    holder.meta.text = "● Canal"
                    holder.meta.setTextColor(0xFF66D9A8.toInt())
                    holder.meta.typeface = android.graphics.Typeface.MONOSPACE
                    holder.meta.textSize = 11f
                    holder.meta.letterSpacing = 0.06f
                } else {
                    holder.meta.text = "▶ Continuar" +
                        (if (s.pos > 0) " ${fmtTime(s.pos)}" else "") +
                        (if (s.dur > 0) " / ${fmtTime(s.dur)}" else "")
                    holder.meta.setTextColor(accentStrong())
                    holder.meta.typeface = android.graphics.Typeface.DEFAULT_BOLD
                    holder.meta.textSize = 11f
                    holder.meta.letterSpacing = 0f
                }
                holder.del.setOnClickListener { onHistDel(s.hkey) }
                holder.itemView.setOnClickListener { onOpen(s) }
            }
            is StreamHolder -> {
                val s = items[position]
                holder.name.text = cleanName(s.name)
                if (s.icon.isNotBlank()) {
                    Glide.with(holder.logo)
                        .load(s.icon)
                        .placeholder(R.drawable.ic_launcher)
                        .fallback(R.drawable.ic_launcher)
                        .error(R.drawable.ic_launcher)
                        .into(holder.logo)
                } else {
                    holder.logo.setImageResource(R.drawable.ic_launcher)
                }
                val fav = favSet.contains("${s.type}:${s.id}")
                holder.btnFav.text = if (fav) "★" else "☆"
                if (fav) {
                    holder.btnFav.setTextColor(accentStrong())
                    holder.btnFav.setShadowLayer(6f, 0f, 0f, accent)
                } else {
                    holder.btnFav.setTextColor(0xFFFFFFFF.toInt())
                    holder.btnFav.setShadowLayer(0f, 0f, 0f, 0)
                }
                holder.btnFav.setOnClickListener {
                    onFav(s)
                }
                holder.itemView.setOnClickListener { onOpen(s) }
            }
        }
    }
}
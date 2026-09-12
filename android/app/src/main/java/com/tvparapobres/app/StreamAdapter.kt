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
    private val onMore: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_STREAM = 0
        const val TYPE_MORE = 1
    }

    private val items = mutableListOf<Strm>()
    private var hasMore = false
    private val favSet = HashSet<String>()
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

    fun shownCount(): Int = items.size

    fun hasMore(): Boolean = hasMore

    override fun getItemCount(): Int = items.size + if (hasMore) 1 else 0

    override fun getItemViewType(position: Int): Int =
        if (position < items.size) TYPE_STREAM else TYPE_MORE

    inner class StreamHolder(view: View) : RecyclerView.ViewHolder(view) {
        val logo: ImageView = view.findViewById(R.id.logo)
        val name: TextView = view.findViewById(R.id.name)
        val btnFav: TextView = view.findViewById(R.id.btnFav)
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
                holder.btn.setTextColor(accent)
                holder.btn.strokeColor = ColorStateList.valueOf(accent)
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
                holder.btnFav.setTextColor(if (fav) accent else 0xFFFFFFFF.toInt())
                holder.btnFav.setOnClickListener {
                    onFav(s)
                }
                holder.itemView.setOnClickListener { onOpen(s) }
            }
        }
    }
}
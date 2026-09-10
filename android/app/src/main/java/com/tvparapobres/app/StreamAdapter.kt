package com.tvparapobres.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions

class StreamAdapter(
    private val onOpen: (Strm) -> Unit
) : RecyclerView.Adapter<StreamAdapter.Holder>() {

    private val items = mutableListOf<Strm>()

    inner class Holder(val view: View) : RecyclerView.ViewHolder(view) {
        val logo: ImageView = view.findViewById(R.id.logo)
        val name: TextView = view.findViewById(R.id.name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_stream, parent, false)
        return Holder(v)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val s = items[position]
        holder.name.text = s.name
        if (s.icon.isNotBlank()) {
            Glide.with(holder.logo)
                .load(s.icon)
                .placeholder(R.drawable.ic_launcher)
                .fallback(R.drawable.ic_launcher)
                .error(R.drawable.ic_launcher)
                .transition(DrawableTransitionOptions.withCrossFade(200))
                .into(holder.logo)
        } else {
            holder.logo.setImageResource(R.drawable.ic_launcher)
        }
        holder.view.setOnClickListener { onOpen(items[holder.bindingAdapterPosition]) }
    }

    override fun getItemCount() = items.size

    fun submit(list: List<Strm>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }
}
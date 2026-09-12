package com.tvparapobres.app

import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.recyclerview.widget.RecyclerView

/** 12 tarjetas skeleton con brillo deslizante, como .skel-card de la web. */
class SkelAdapter : RecyclerView.Adapter<SkelAdapter.H>() {

    inner class H(v: View) : RecyclerView.ViewHolder(v) {
        val hi: View = v.findViewById(R.id.skelHi)
        var anim: ObjectAnimator? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): H {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_skel, parent, false)
        return H(v)
    }

    override fun onBindViewHolder(h: H, position: Int) {
        h.anim?.cancel()
        h.hi.post {
            val w = h.itemView.width.toFloat()
            h.anim = ObjectAnimator.ofFloat(h.hi, "translationX", -180f, w + 180f).apply {
                duration = 1400
                interpolator = LinearInterpolator()
                repeatCount = ObjectAnimator.INFINITE
                start()
            }
        }
    }

    override fun onViewRecycled(h: H) {
        h.anim?.cancel()
        h.anim = null
    }

    override fun getItemCount(): Int = 12
}

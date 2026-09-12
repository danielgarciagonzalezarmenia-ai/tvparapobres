package com.tvparapobres.app

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton

/**
 * Teclado PIN navegable con control remoto.
 * Verifica automáticamente al llegar a 4 dígitos; ⌫ borra.
 * onResult se invoca una sola vez (true = PIN correcto).
 */
object PinDialog {

    @SuppressLint("ClickableViewAccessibility")
    fun show(
        context: Context,
        profileName: String,
        expectedPin: String,
        accent: Int,
        onResult: (Boolean) -> Unit
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_pin, null)
        val title = view.findViewById<TextView>(R.id.pinTitle)
        val dotsRow = view.findViewById<LinearLayout>(R.id.pinDots)

        title.text = "PERFIL \"${profileName.uppercase()}\" — INGRESA PIN"
        title.setTextColor(accent)

        val dots = ArrayList<ImageView>()
        for (i in 0 until 4) {
            val d = ImageView(context)
            val lp = LinearLayout.LayoutParams(16, 16)
            lp.setMargins(7, 0, 7, 0)
            d.layoutParams = lp
            dotsRow.addView(d)
            dots.add(d)
        }

        var entered = ""
        var delivered = false

        val dialog = AlertDialog.Builder(context, androidx.appcompat.R.style.Theme_AppCompat_Dialog)
            .setView(view)
            .setCancelable(true)
            .create()

        fun refresh() {
            dots.forEachIndexed { i, d -> d.setBackgroundDrawable(bgDot(accent, entered.length > i)) }
        }

        fun finish(pinOk: Boolean) {
            if (delivered) return
            delivered = true
            dialog.dismiss()
            onResult(pinOk)
        }

        fun press(d: String) {
            if (entered.length < 4) {
                entered += d
                refresh()
                if (entered.length == 4) finish(entered == expectedPin)
            }
        }

        fun back() {
            if (entered.isNotEmpty()) {
                entered = entered.dropLast(1)
                refresh()
            }
        }

        fun attach(id: Int, digit: String?) {
            view.findViewById<MaterialButton>(id).setOnClickListener {
                if (digit != null) press(digit) else back()
            }
        }
        attach(R.id.k1, "1"); attach(R.id.k2, "2"); attach(R.id.k3, "3")
        attach(R.id.k4, "4"); attach(R.id.k5, "5"); attach(R.id.k6, "6")
        attach(R.id.k7, "7"); attach(R.id.k8, "8"); attach(R.id.k9, "9")
        attach(R.id.k0, "0"); attach(R.id.kBack, null)
        view.findViewById<MaterialButton>(R.id.kCancel).setOnClickListener { finish(false) }

        dialog.setOnDismissListener { finish(false) }

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 320f, context.resources.displayMetrics).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }
        dialog.show()
    }

    private fun bgDot(accent: Int, on: Boolean): Drawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(if (on) accent else 0x22FFFFFF.toInt())
        setStroke(1, if (on) accent else 0x33FFFFFF.toInt())
    }
}
package com.tvparapobres.app

import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Pantalla de PIN espejo de la web: avatar, nombre, puntos y teclado. */
class PinActivity : AppCompatActivity() {

    private var pin = ""
    private var want = ""
    private var pid = ""
    private var accent = Color.parseColor("#ff4d2e")
    private lateinit var dots: List<View>
    private lateinit var dotsRow: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pin)

        pid = intent.getStringExtra(ProfilePickerActivity.EXTRA_PID).orEmpty()
        val p = Profiles.detail(pid)
        if (p == null) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        want = p.pin
        accent = Profiles.accentArgb(p.color)

        findViewById<AvatarView>(R.id.pinAvatar).set(p.photo, accent, p.avatar)
        findViewById<TextView>(R.id.pinName).text = p.name
        findViewById<TextView>(R.id.pinTitle).text = "PIN de ${p.name}"

        dotsRow = findViewById(R.id.dotsRow)
        dots = listOf(
            findViewById(R.id.dot0), findViewById(R.id.dot1),
            findViewById(R.id.dot2), findViewById(R.id.dot3)
        )
        paintDots()

        val pad = findViewById<GridLayout>(R.id.keypad)
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "del", "0", "ok")
        for (k in keys) {
            val b = TextView(this)
            val lp = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            )
            lp.width = 0
            lp.setMargins(dp(4), dp(4), dp(4), dp(4))
            b.layoutParams = lp
            b.gravity = Gravity.CENTER
            b.minHeight = dp(56)
            b.background = resources.getDrawable(R.drawable.bg_pinkey, theme)
            b.setTextColor(Color.WHITE)
            b.textSize = 18f
            b.setTypeface(null, android.graphics.Typeface.BOLD)
            b.isFocusable = true
            b.isFocusableInTouchMode = true
            when (k) {
                "del" -> {
                    b.text = "⌫"
                    b.setOnClickListener { if (pin.isNotEmpty()) { pin = ""; paintDots() } }
                }
                "ok" -> {
                    b.text = "✓"
                    b.setTextColor(0xFF66D9A8.toInt())
                    b.setOnClickListener { submit() }
                }
                else -> {
                    b.text = k
                    b.setOnClickListener { press(k) }
                }
            }
            pad.addView(b)
        }

        findViewById<TextView>(R.id.btnPinBack).setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun dotBg(on: Boolean): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        if (on) {
            setColor(accent)
        } else {
            setColor(Color.TRANSPARENT)
            setStroke(dp(2), withAlpha(accent, 0x73))
        }
    }

    private fun withAlpha(c: Int, a: Int): Int = (c and 0x00FFFFFF) or ((a and 0xFF) shl 24)

    private fun paintDots() {
        dots.forEachIndexed { i, d -> d.background = dotBg(i < pin.length) }
    }

    private fun press(d: String) {
        if (pin.length >= 4) return
        pin += d
        paintDots()
    }

    private fun submit() {
        if (pin == want) {
            setResult(Activity.RESULT_OK, Intent().putExtra(ProfilePickerActivity.EXTRA_PID, pid))
            finish()
            return
        }
        pin = ""
        paintDots()
        ObjectAnimator.ofFloat(dotsRow, "translationX", 0f, 18f, -18f, 12f, -12f, 0f).apply {
            duration = 450
            start()
        }
    }
}

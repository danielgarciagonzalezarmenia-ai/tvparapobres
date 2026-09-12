package com.tvparapobres.app

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class CreateProfileActivity : AppCompatActivity() {

    private lateinit var nameInput: EditText
    private lateinit var pinInput: EditText
    private lateinit var paletteRow: LinearLayout
    private lateinit var btnSave: TextView
    private var selectedColor = Profiles.PALETTE[0]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_profile)

        nameInput = findViewById(R.id.nameInput)
        pinInput = findViewById(R.id.pinInput)
        paletteRow = findViewById(R.id.paletteRow)
        btnSave = findViewById(R.id.btnSave)

        findViewById<TextView>(R.id.btnBack).setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        buildPalette()

        btnSave.setOnClickListener { save() }
        nameInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                btnSave.alpha = if (s?.trim().isNullOrEmpty()) 0.4f else 1f
            }
        })

        nameInput.post { nameInput.requestFocus() }
    }

    private fun buildPalette() {
        val gap = resources.getDimensionPixelSize(R.dimen.cat_gap)
        for ((i, hex) in Profiles.PALETTE.withIndex()) {
            val b = MaterialButton(this)
            val size = (54 * 0.7f).toInt()
            b.layoutParams = LinearLayout.LayoutParams(size, size).apply { setMargins(4, 4, gap, 4) }
            b.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Profiles.accentArgb(hex))
            }
            b.setOnClickListener {
                selectedColor = hex
                setSelection()
            }
            b.setOnFocusChangeListener { v, has ->
                v.scaleX = if (has) 1.15f else 1f
                v.scaleY = if (has) 1.15f else 1f
            }
            paletteRow.addView(b)
        }
        setSelection()
    }

    private fun setSelection() {
        for (i in 0 until paletteRow.childCount) {
            val swatch = paletteRow.getChildAt(i) as MaterialButton
            val isSel = Profiles.PALETTE[i] == selectedColor
            swatch.invalidate()
            val ring = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Profiles.accentArgb(Profiles.PALETTE[i]))
                if (isSel) {
                    setStroke((4 * resources.displayMetrics.density).toInt(), 0xFFFFFFFF.toInt())
                }
            }
            swatch.background = ring
            swatch.strokeWidth = 0
        }
    }

    private fun save() {
        val name = nameInput.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, "Escribe un nombre para el perfil", Toast.LENGTH_SHORT).show()
            nameInput.requestFocus()
            return
        }
        val pin = pinInput.text.toString().trim()
        val finalPin = if (pin.isEmpty()) "" else if (pin.length == 4 && pin.all { it.isDigit() }) pin else {
            Toast.makeText(this, "El PIN debe tener 4 dígitos", Toast.LENGTH_SHORT).show()
            return
        }
        val pid = Profiles.add(name, selectedColor, finalPin)
        setResult(Activity.RESULT_OK, Intent().putExtra(ProfilePickerActivity.EXTRA_PID, pid))
        finish()
    }
}
package com.tvparapobres.app

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Base64
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.ByteArrayOutputStream

/** Formulario de perfil espejo del web: foto, nombre, color, carita, PIN. Sirve para crear y editar. */
class CreateProfileActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_EDIT_ID = "edit_id"
    }

    private lateinit var formTitle: TextView
    private lateinit var photoPreview: AvatarView
    private lateinit var btnPhoto: TextView
    private lateinit var btnPhotoDel: TextView
    private lateinit var nameInput: EditText
    private lateinit var pinInput: EditText
    private lateinit var paletteRow: LinearLayout
    private lateinit var facesRow: LinearLayout
    private lateinit var btnSave: TextView

    private var editId: String? = null
    private var selectedColor = Profiles.PALETTE[0]
    private var selectedFace = 0
    private var photoData = ""

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val raw = contentResolver.openInputStream(uri)?.use { ins ->
                BitmapFactory.decodeStream(ins)
            } ?: return@registerForActivityResult
            val sc = minOf(1f, 384f / maxOf(raw.width, raw.height))
            val bmp = if (sc < 1f) {
                Bitmap.createScaledBitmap(raw, (raw.width * sc).toInt(), (raw.height * sc).toInt(), true)
            } else raw
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
            photoData = "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            refreshPhoto()
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo leer la imagen", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_profile)

        formTitle = findViewById(R.id.formTitle)
        photoPreview = findViewById(R.id.photoPreview)
        btnPhoto = findViewById(R.id.btnPhoto)
        btnPhotoDel = findViewById(R.id.btnPhotoDel)
        nameInput = findViewById(R.id.nameInput)
        pinInput = findViewById(R.id.pinInput)
        paletteRow = findViewById(R.id.paletteRow)
        facesRow = findViewById(R.id.facesRow)
        btnSave = findViewById(R.id.btnSave)

        editId = intent.getStringExtra(EXTRA_EDIT_ID)
        val editing = Profiles.detail(editId ?: "")
        if (editing != null) {
            formTitle.text = "EDITAR PERFIL"
            btnSave.text = "GUARDAR"
            nameInput.setText(editing.name)
            pinInput.setText(editing.pin)
            selectedColor = editing.color
            selectedFace = editing.avatar
            photoData = editing.photo
        }

        findViewById<TextView>(R.id.btnBack).setOnClickListener { cancel() }
        findViewById<TextView>(R.id.btnCancel).setOnClickListener { cancel() }
        btnPhoto.setOnClickListener { pickPhoto.launch("image/*") }
        btnPhotoDel.setOnClickListener {
            photoData = ""
            refreshPhoto()
        }
        btnSave.setOnClickListener { save() }

        buildPalette()
        buildFaces()
        refreshPhoto()
        refreshSaveBtn()
        nameInput.post { if (editing == null) nameInput.requestFocus() }
    }

    private fun cancel() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    private fun accentOf(hex: String): Int {
        return try {
            Color.parseColor(if (hex.startsWith("#") && hex.length == 7) hex else Profiles.DEFAULT_COLOR)
        } catch (_: Exception) {
            Color.parseColor(Profiles.DEFAULT_COLOR)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun refreshPhoto() {
        photoPreview.set(photoData, accentOf(selectedColor), selectedFace)
        btnPhoto.text = if (photoData.isEmpty()) "Subir foto" else "Cambiar foto"
        btnPhotoDel.visibility = if (photoData.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun refreshSaveBtn() {
        val c = accentOf(selectedColor)
        val light = lighten(c, 0.55f)
        btnSave.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR, intArrayOf(c, light)
        ).apply { cornerRadius = dp(12).toFloat() }
    }

    private fun lighten(c: Int, k: Float): Int {
        fun m(v: Int) = (v + (255 - v) * k).toInt().coerceIn(0, 255)
        return Color.rgb(m(Color.red(c)), m(Color.green(c)), m(Color.blue(c)))
    }

    private fun buildPalette() {
        paletteRow.removeAllViews()
        for (hex in Profiles.PALETTE) {
            val b = FrameLayout(this)
            val size = dp(38)
            b.layoutParams = LinearLayout.LayoutParams(size, size).apply {
                setMargins(dp(4), dp(4), dp(8), dp(4))
            }
            b.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(accentOf(hex))
                if (hex == selectedColor) setStroke(dp(2), Color.WHITE)
            }
            b.isFocusable = true
            b.isFocusableInTouchMode = true
            b.setOnClickListener {
                selectedColor = hex
                buildPalette()
                buildFaces()
                refreshPhoto()
                refreshSaveBtn()
            }
            paletteRow.addView(b)
        }
    }

    private fun buildFaces() {
        facesRow.removeAllViews()
        for (i in AvatarView.FACES.indices) {
            val box = FrameLayout(this)
            val size = dp(48)
            box.layoutParams = LinearLayout.LayoutParams(size, size).apply {
                setMargins(dp(4), dp(4), dp(9), dp(4))
            }
            val av = AvatarView(this)
            av.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
            av.set("", accentOf(selectedColor), i)
            box.addView(av)
            box.background = if (i == selectedFace) {
                resources.getDrawable(R.drawable.bg_avatar_sel, theme)
            } else null
            box.isFocusable = true
            box.isFocusableInTouchMode = true
            box.setOnClickListener {
                selectedFace = i
                buildFaces()
                refreshPhoto()
            }
            facesRow.addView(box)
        }
    }

    private fun save() {
        val name = nameInput.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, "Ponle un nombre a tu perfil.", Toast.LENGTH_SHORT).show()
            nameInput.requestFocus()
            return
        }
        val pin = pinInput.text.toString().trim().replace(Regex("[^0-9]"), "")
        if (pin.isNotEmpty() && pin.length > 4) {
            Toast.makeText(this, "El PIN debe tener de 1 a 4 números.", Toast.LENGTH_SHORT).show()
            return
        }
        val id = editId?.let { eid ->
            if (Profiles.detail(eid) != null) {
                Profiles.update(eid, name, selectedColor, pin, selectedFace, photoData)
                eid
            } else null
        } ?: Profiles.add(name, selectedColor, pin, selectedFace, photoData)
        setResult(Activity.RESULT_OK, Intent().putExtra(ProfilePickerActivity.EXTRA_PID, id))
        finish()
    }
}

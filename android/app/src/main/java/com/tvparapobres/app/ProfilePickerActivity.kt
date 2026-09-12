package com.tvparapobres.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

/** Selector "¿Quién está mirando?" espejo de la web. */
class ProfilePickerActivity : AppCompatActivity() {

    companion object {
        const val REQ_CREATE = 31
        const val REQ_PIN = 32
        const val EXTRA_PID = "pid"
    }

    private lateinit var cardRow: LinearLayout
    private var editingId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_picker)
        cardRow = findViewById(R.id.cardRow)

        if (Profiles.profiles().isEmpty()) {
            startActivityForResult(Intent(this, CreateProfileActivity::class.java), REQ_CREATE)
            return
        }
        buildCards()
    }

    private fun buildCards() {
        cardRow.removeAllViews()
        val profiles = Profiles.profiles()

        for (p in profiles) {
            val card = layoutInflater.inflate(R.layout.item_profile, cardRow, false) as MaterialCardView
            val acc = Profiles.accentArgb(p.color)
            card.findViewById<AvatarView>(R.id.profileAvatar).set(p.photo, acc, p.avatar)
            card.findViewById<TextView>(R.id.profileName).text = p.name
            val lock = card.findViewById<TextView>(R.id.profileLock)
            lock.visibility = if (p.pin.isNotEmpty()) View.VISIBLE else View.GONE

            val del = card.findViewById<TextView>(R.id.profileDelete)
            del.visibility = if (profiles.size > 1) View.VISIBLE else View.GONE
            del.setOnClickListener { confirmDelete(p.name, p.id) }

            val edit = card.findViewById<ImageView>(R.id.profileEdit)
            edit.setOnClickListener {
                editingId = p.id
                val i = Intent(this, CreateProfileActivity::class.java)
                i.putExtra(CreateProfileActivity.EXTRA_EDIT_ID, p.id)
                startActivityForResult(i, REQ_CREATE)
            }

            if (isTvDevice(this)) {
                card.setOnFocusChangeListener { v, has ->
                    v.scaleX = if (has) 1.06f else 1f
                    v.scaleY = if (has) 1.06f else 1f
                }
            }
            card.setOnClickListener { onProfile(p.id) }
            cardRow.addView(card)
        }

        val add = layoutInflater.inflate(R.layout.item_profile_add, cardRow, false) as MaterialCardView
        if (isTvDevice(this)) {
            add.setOnFocusChangeListener { v, has ->
                v.scaleX = if (has) 1.06f else 1f
                v.scaleY = if (has) 1.06f else 1f
            }
        }
        add.setOnClickListener {
            editingId = null
            startActivityForResult(Intent(this, CreateProfileActivity::class.java), REQ_CREATE)
        }
        cardRow.addView(add)

        cardRow.post { cardRow.getChildAt(0)?.requestFocus() }
    }

    private fun onProfile(id: String) {
        val p = Profiles.detail(id) ?: return
        if (p.pin.isNotEmpty()) {
            val i = Intent(this, PinActivity::class.java)
            i.putExtra(EXTRA_PID, id)
            startActivityForResult(i, REQ_PIN)
        } else {
            unlock(id)
        }
    }

    private fun unlock(id: String) {
        Profiles.setActive(id)
        Profiles.remember(id)
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_PID, id))
        finish()
    }

    private fun confirmDelete(name: String, id: String) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar perfil")
            .setMessage("¿Eliminar \"$name\"? Se borrará su historial y favoritos.")
            .setPositiveButton("Eliminar") { d, _ ->
                d.dismiss()
                Profiles.remove(id)
                if (Profiles.profiles().isEmpty()) {
                    startActivityForResult(Intent(this, CreateProfileActivity::class.java), REQ_CREATE)
                }
                buildCards()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PIN) {
            val pid = data?.getStringExtra(EXTRA_PID)
            if (resultCode == RESULT_OK && pid != null) {
                unlock(pid)
            }
            return
        }
        if (requestCode == REQ_CREATE) {
            val pid = data?.getStringExtra(EXTRA_PID)
            val wasEdit = editingId != null
            editingId = null
            if (resultCode == RESULT_OK && pid != null && !wasEdit) {
                unlock(pid)
            } else if (Profiles.profiles().isEmpty()) {
                finish()
            } else {
                buildCards()
            }
        }
    }
}

package com.tvparapobres.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/** Muestra el último crash con botón para copiarlo. Solo vistas del sistema. */
class CrashReportActivity : AppCompatActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_crash)
        val f = File(filesDir, "crash.txt")
        val txt = try {
            if (f.exists()) f.readText() else "Sin registro."
        } catch (_: Exception) {
            "Sin registro."
        }
        findViewById<TextView>(R.id.crashVersion).text =
            "Versión instalada: " + BuildConfig.VERSION_NAME
        findViewById<TextView>(R.id.crashTrace).text = txt
        findViewById<TextView>(R.id.btnCopy).setOnClickListener {
            try {
                val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("crash", txt))
                Toast.makeText(this, "Copiado. Pégalo en el chat.", Toast.LENGTH_LONG).show()
            } catch (_: Exception) {
                Toast.makeText(this, "No se pudo copiar", Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<TextView>(R.id.btnRetry).setOnClickListener {
            try {
                f.delete()
            } catch (_: Exception) {
            }
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
        findViewById<TextView>(R.id.btnClose).setOnClickListener { finish() }
    }
}

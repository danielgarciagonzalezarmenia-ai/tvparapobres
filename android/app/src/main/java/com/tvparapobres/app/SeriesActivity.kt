package com.tvparapobres.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Ficha de serie espejo del modal web: portada, meta, temporadas plegables y episodios. */
class SeriesActivity : AppCompatActivity() {

    private lateinit var sePoster: ImageView
    private lateinit var seName: TextView
    private lateinit var seMeta: TextView
    private lateinit var sePlot: TextView
    private lateinit var seCast: TextView
    private lateinit var seasonsBox: LinearLayout
    private lateinit var epEmpty: TextView
    private lateinit var epLoading: View

    private val seriesId by lazy { intent.getStringExtra("seriesId") ?: "" }
    private val seriesName by lazy { intent.getStringExtra("seriesName") ?: "" }
    private var openSeason: String? = null
    private var full: SeriesFull? = null
    private var accent: Int = Color.parseColor("#ff4d2e")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_series)

        Profiles.init(applicationContext)
        val pid = intent.getStringExtra("pid") ?: Profiles.sessionId()
        accent = Profiles.accentArgb(Profiles.detail(pid)?.color)
        HistoryManager.init(applicationContext, pid.ifBlank { "default" })

        sePoster = findViewById(R.id.sePoster)
        seName = findViewById(R.id.seName)
        seMeta = findViewById(R.id.seMeta)
        sePlot = findViewById(R.id.sePlot)
        seCast = findViewById(R.id.seCast)
        seasonsBox = findViewById(R.id.seasonsBox)
        epEmpty = findViewById(R.id.epEmpty)
        epLoading = findViewById(R.id.epLoading)
        findViewById<TextView>(R.id.seClose).setOnClickListener { finish() }

        seName.text = seriesName
        epLoading.visibility = View.VISIBLE
        lifecycleScope.launch {
            val f = try {
                withContext(Dispatchers.IO) { Xtream.seriesFull(seriesId) }
            } catch (e: Exception) {
                null
            }
            epLoading.visibility = View.GONE
            full = f
            if (f == null || f.seasons.isEmpty()) {
                epEmpty.visibility = View.VISIBLE
                return@launch
            }
            if (openSeason == null) openSeason = f.seasons[0].id
            bindHeader(f)
            buildSeasons()
        }
    }

    private fun bindHeader(f: SeriesFull) {
        if (f.cover.isNotBlank()) {
            Glide.with(sePoster).load(f.cover).into(sePoster)
        } else {
            sePoster.visibility = View.INVISIBLE
        }
        if (f.name.isNotBlank()) seName.text = NameCleaner.clean(f.name)
        val parts = mutableListOf<String>()
        if (f.year.isNotBlank()) parts.add(f.year)
        if (f.rating.isNotBlank()) parts.add("★ ${f.rating}/10")
        if (f.genre.isNotBlank()) parts.add(f.genre)
        if (parts.isEmpty()) {
            seMeta.visibility = View.GONE
        } else {
            val joined = parts.joinToString(" • ")
            val ss = SpannableString(joined)
            if (f.rating.isNotBlank()) {
                val i = joined.indexOf("★")
                if (i >= 0) {
                    val end = joined.indexOf("/10", i) + 3
                    ss.setSpan(
                        ForegroundColorSpan(lighten(accent)),
                        i, end.coerceAtMost(ss.length), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
            seMeta.text = ss
        }
        if (f.plot.isNotBlank()) sePlot.text = f.plot else sePlot.visibility = View.GONE
        if (f.cast.isNotBlank()) seCast.text = f.cast else seCast.visibility = View.GONE
    }

    private fun lighten(c: Int): Int {
        fun m(v: Int) = (v + (255 - v) * 0.4f).toInt().coerceIn(0, 255)
        return Color.rgb(m(Color.red(c)), m(Color.green(c)), m(Color.blue(c)))
    }

    private fun seasonLabel(f: SeriesFull, id: String): String {
        if (id == "0") return "Episodios"
        val custom = f.seasonNames[id].orEmpty()
        if (custom.isNotBlank()) return NameCleaner.clean(custom)
        return if (id == "1") "Temporada 1" else "Temporada $id"
    }

    private fun buildSeasons() {
        val f = full ?: return
        seasonsBox.removeAllViews()
        for (s in f.seasons) {
            val sec = layoutInflater.inflate(R.layout.item_season, seasonsBox, false)
            val head = sec.findViewById<LinearLayout>(R.id.seasonHead)
            val label = sec.findViewById<TextView>(R.id.seasonLabel)
            val toggle = sec.findViewById<TextView>(R.id.seasonToggle)
            val epsBox = sec.findViewById<LinearLayout>(R.id.seasonEps)
            val open = openSeason == s.id
            label.text = seasonLabel(f, s.id)
            toggle.text = if (open) "▾" else "▸"
            if (open) {
                epsBox.visibility = View.VISIBLE
                s.eps.forEachIndexed { i, ep ->
                    if (i > 0) {
                        val div = View(this)
                        div.layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 1
                        )
                        div.setBackgroundColor(0x0DFFFFFF.toInt())
                        epsBox.addView(div)
                    }
                    val row = layoutInflater.inflate(R.layout.item_episode, epsBox, false)
                    row.findViewById<TextView>(R.id.epNum).text =
                        ep.num.padStart(2, '0')
                    val t = NameCleaner.clean(ep.title)
                    row.findViewById<TextView>(R.id.epTitle).text =
                        t.ifBlank { "Capítulo ${ep.num}" }
                    val dur = row.findViewById<TextView>(R.id.epDur)
                    if (ep.dur.isNotBlank()) {
                        dur.text = ep.dur
                        dur.visibility = View.VISIBLE
                    }
                    row.findViewById<TextView>(R.id.epPlay).setTextColor(lighten(accent))
                    row.isFocusable = true
                    row.isFocusableInTouchMode = true
                    row.setOnClickListener { openEpisode(ep) }
                    epsBox.addView(row)
                }
            }
            head.setOnClickListener {
                openSeason = if (open) null else s.id
                buildSeasons()
            }
            seasonsBox.addView(sec)
        }
    }

    private fun openEpisode(ep: Episode) {
        val pid = intent.getStringExtra("pid") ?: Profiles.sessionId()
        val url = Accounts.seriesUrl(ep.id, ep.ext)
        val key = "ser:${ep.id}"
        HistoryManager.save(
            org.json.JSONObject().apply {
                put("key", key)
                put("id", ep.id)
                put("name", "$seriesName — Cap ${ep.num}")
                put("logo", "")
                put("type", "series")
                put("ext", ep.ext)
                put("url", url)
                put("position", 0)
                put("duration", 0)
                put("ts", System.currentTimeMillis())
            }
        )
        val i = Intent(this, PlayerActivity::class.java)
        i.putExtra("url", url)
        i.putExtra("title", "$seriesName — Cap ${ep.num}")
        i.putExtra("histKey", key)
        i.putExtra("isLive", false)
        i.putExtra("pid", pid.ifBlank { Profiles.sessionId() })
        startActivity(i)
    }
}

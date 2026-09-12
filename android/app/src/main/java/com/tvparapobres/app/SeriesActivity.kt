package com.tvparapobres.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SeriesActivity : AppCompatActivity() {

    private lateinit var epList: RecyclerView
    private lateinit var epLoading: View
    private val seriesId by lazy { intent.getStringExtra("seriesId") ?: "" }
    private val seriesName by lazy { intent.getStringExtra("seriesName") ?: "" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_series)

        Profiles.init(applicationContext)
        val pid = intent.getStringExtra("pid") ?: Profiles.sessionId()
        HistoryManager.init(applicationContext, pid.ifBlank { "default" })

        epList = findViewById(R.id.epList)
        epLoading = findViewById(R.id.epLoading)
        findViewById<TextView>(R.id.epTitle).text = seriesName

        epList.layoutManager = LinearLayoutManager(this)

        epLoading.visibility = View.VISIBLE
        lifecycleScope.launch {
            val eps = try {
                withContext(Dispatchers.IO) { Xtream.seriesInfo(seriesId) }
            } catch (e: Exception) {
                emptyList<Episode>()
            }
            epLoading.visibility = View.GONE

            val sorted = eps.sortedWith(compareBy<Episode> {
                it.num.toIntOrNull() ?: Int.MAX_VALUE
            }.thenBy { it.title })
            epList.adapter = EpisodeAdapter(sorted) { ep ->
                val url = Accounts.seriesUrl(ep.id, ep.ext)
                val key = "ser:${ep.id}"
                val entry = org.json.JSONObject().apply {
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
                HistoryManager.save(entry)
                val i = Intent(this@SeriesActivity, PlayerActivity::class.java)
                i.putExtra("url", url)
                i.putExtra("title", "$seriesName — Cap ${ep.num}")
                i.putExtra("histKey", key)
                i.putExtra("isLive", false)
                i.putExtra("pid", pid.ifBlank { Profiles.sessionId() })
                startActivity(i)
            }
        }
    }

    private inner class EpisodeAdapter(
        private val eps: List<Episode>,
        private val onOpen: (Episode) -> Unit
    ) : RecyclerView.Adapter<EpisodeAdapter.Holder>() {

        inner class Holder(val root: View) : RecyclerView.ViewHolder(root) {
            val tv: TextView = root.findViewById(R.id.epName)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val root = layoutInflater.inflate(R.layout.item_episode, parent, false)
            if (isTvDevice(this@SeriesActivity)) {
                root.isFocusable = true
                root.foreground = root.context.getDrawable(R.drawable.bg_card_focus)
                root.setOnFocusChangeListener { view, has ->
                    view.scaleX = if (has) 1.03f else 1f
                    view.scaleY = if (has) 1.03f else 1f
                }
            }
            return Holder(root)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val ep = eps[position]
            holder.tv.text = "Cap ${ep.num.orEmpty()} — ${NameCleaner.clean(ep.title.orEmpty())}"
            holder.root.setOnClickListener { onOpen(ep) }
        }

        override fun getItemCount() = eps.size
    }
}
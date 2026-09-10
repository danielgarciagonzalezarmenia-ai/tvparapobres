package com.tvparapobres.app

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var grid: RecyclerView
    private lateinit var catRow: LinearLayout
    private lateinit var loading: View
    private lateinit var tabs: TabLayout
    private lateinit var streamAdapter: StreamAdapter

    private var currentTab = 0
    private var cats = emptyList<Cat>()
    private val catButtons = mutableListOf<MaterialButton>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        grid = findViewById(R.id.grid)
        catRow = findViewById(R.id.catRow)
        loading = findViewById(R.id.loading)
        tabs = findViewById(R.id.tabLayout)

        grid.layoutManager = GridLayoutManager(this, 3)
        streamAdapter = StreamAdapter(::openStream)
        grid.adapter = streamAdapter

        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentTab = tab.position
                reload()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        reload()
    }

    private fun reload() {
        setBusy(true)
        catRow.removeAllViews()
        catButtons.clear()
        cats = emptyList()
        streamAdapter.submit(emptyList())

        lifecycleScope.launch {
            val res = try {
                withContext(Dispatchers.IO) {
                    when (currentTab) {
                        0 -> Xtream.liveCategories()
                        1 -> Xtream.vodCategories()
                        else -> Xtream.seriesCategories()
                    }
                }
            } catch (e: Exception) {
                emptyList<Cat>()
            }
            if (res.isEmpty()) {
                Toast.makeText(this@MainActivity, "Sin respuesta del proveedor", Toast.LENGTH_LONG).show()
                setBusy(false)
                return@launch
            }
            cats = res
            paintCats()
            selectCat(res[0])
        }
    }

    private fun paintCats() {
        catRow.removeAllViews()
        catButtons.clear()
        val gap = resources.getDimensionPixelSize(R.dimen.cat_gap)
        for (cat in cats) {
            val btn = MaterialButton(this)
            btn.text = cat.name
            btn.textSize = 12f
            btn.isAllCaps = false
            btn.insetTop = 0
            btn.insetBottom = 0
            btn.minHeight = 0
            btn.cornerRadius = 20
            btn.strokeWidth = resources.getDimensionPixelSize(R.dimen.cat_stroke)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, gap, 0)
            btn.layoutParams = lp
            btn.setOnClickListener { selectCat(cat) }
            catButtons.add(btn)
            catRow.addView(btn)
        }
        styleCats(null)
    }

    private fun styleCats(selected: Cat?) {
        selectedColor = 0xFFFF4D2E.toInt()
        unselectedColor = 0x00131318
        for ((i, cat) in cats.withIndex()) {
            val btn = catButtons[i]
            val isSel = cat == selected
            btn.setTextColor(Color.WHITE)
            btn.backgroundTintList = ColorStateList.valueOf(
                if (isSel) selectedColor else unselectedColor
            )
            btn.strokeColor = ColorStateList.valueOf(
                if (isSel) 0xFFFF4D2E.toInt() else 0x1AFFFFFF.toInt()
            )
        }
    }

    private var selectedColor = 0
    private var unselectedColor = 0

    private fun selectCat(cat: Cat) {
        styleCats(cat)
        setBusy(true)
        lifecycleScope.launch {
            val list = try {
                withContext(Dispatchers.IO) {
                    when (currentTab) {
                        0 -> Xtream.liveStreams(cat.id)
                        1 -> Xtream.vodStreams(cat.id)
                        else -> Xtream.series(cat.id)
                    }
                }
            } catch (e: Exception) {
                emptyList<Strm>()
            }
            streamAdapter.submit(list)
            setBusy(false)
        }
    }

    private fun setBusy(b: Boolean) {
        loading.visibility = if (b) View.VISIBLE else View.GONE
        grid.visibility = if (b) View.GONE else View.VISIBLE
    }

    private fun openStream(s: Strm) {
        if (s.type == "series") {
            val i = Intent(this, SeriesActivity::class.java)
            i.putExtra("seriesId", s.id)
            i.putExtra("seriesName", s.name)
            startActivity(i)
            return
        }
        val url = if (s.type == "live") Accounts.liveUrl(s.id) else Accounts.movieUrl(s.id, s.ext)
        val i = Intent(this, PlayerActivity::class.java)
        i.putExtra("url", url)
        i.putExtra("title", s.name)
        startActivity(i)
    }
}
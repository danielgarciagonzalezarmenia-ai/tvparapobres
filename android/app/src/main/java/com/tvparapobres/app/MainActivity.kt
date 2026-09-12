package com.tvparapobres.app

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
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
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PAGE = 240
        private const val REQ_PICKER = 11
        private const val REQ_CREATE = 12
        private const val TAB_FAV = 3
        private const val TAB_HIST = 4
    }

    private lateinit var grid: RecyclerView
    private lateinit var catRow: LinearLayout
    private lateinit var catScroller: View
    private lateinit var loading: ProgressBar
    private lateinit var tabs: TabLayout
    private lateinit var searchBox: EditText
    private lateinit var btnProfile: TextView
    private lateinit var streamAdapter: StreamAdapter

    private var currentTab = 0
    private var cats = emptyList<Cat>()
    private val catButtons = mutableListOf<MaterialButton>()
    private var allItems = emptyList<Strm>()
    private var visibleLive = 0
    private var loadingMore = false
    private var searching = false
    private var accent = Profiles.accentArgb()
    private var tvMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Profiles.init(applicationContext)
        tvMode = isTvDevice(this)

        grid = findViewById(R.id.grid)
        catRow = findViewById(R.id.catRow)
        catScroller = findViewById(R.id.catScroller)
        loading = findViewById(R.id.loading)
        tabs = findViewById(R.id.tabLayout)
        searchBox = findViewById(R.id.searchBox)
        btnProfile = findViewById(R.id.btnProfile)

        findViewById<View>(R.id.btnDonate).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://tvparapobres.tipsterpage.com/kttCMjB2")))
        }
        btnProfile.setOnClickListener {
            startActivityForResult(Intent(this, ProfilePickerActivity::class.java), REQ_PICKER)
        }

        val cols = when {
            tvMode -> 6
            resources.configuration.screenWidthDp >= 600 -> 4
            else -> 3
        }
        grid.setHasFixedSize(true)
        grid.itemAnimator = null
        grid.setItemViewCacheSize(60)
        val glm = GridLayoutManager(this, cols)
        glm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int =
                if (streamAdapter.getItemViewType(position) == StreamAdapter.TYPE_MORE) cols else 1
        }
        grid.layoutManager = glm
        streamAdapter = StreamAdapter(accent, tvMode, ::openItem, ::toggleFav, ::loadMore)
        grid.adapter = streamAdapter

        grid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (currentTab != 0 || searching) return
                val lm = rv.layoutManager as GridLayoutManager
                val total = rv.adapter?.itemCount ?: 0
                if (lm.findLastVisibleItemPosition() >= total - 8 && streamAdapter.hasMore() && !loadingMore) {
                    loadMore()
                }
            }
        })

        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentTab = tab.position
                searchBox.text.clear()
                reload()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterSearch(s?.toString().orEmpty())
            }
        })

        ensureProfile()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && tvMode && currentTab == 0 && streamAdapter.itemCount > 0) {
            grid.post { grid.requestFocus() }
        }
    }

    private fun ensureProfile() {
        if (Profiles.profiles().isEmpty()) {
            startActivityForResult(Intent(this, CreateProfileActivity::class.java), REQ_CREATE)
        } else if (Profiles.sessionId().isEmpty()) {
            startActivityForResult(Intent(this, ProfilePickerActivity::class.java), REQ_PICKER)
        } else {
            applyProfile(Profiles.sessionId())
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val pid = data?.getStringExtra(ProfilePickerActivity.EXTRA_PID)
        when (requestCode) {
            REQ_PICKER, REQ_CREATE -> {
                if (resultCode == RESULT_OK && pid != null) {
                    applyProfile(pid)
                } else if (Profiles.profiles().isEmpty()) {
                    startActivityForResult(Intent(this, CreateProfileActivity::class.java), REQ_CREATE)
                } else if (Profiles.sessionId().isEmpty()) {
                    finish()
                } else {
                    applyProfile(Profiles.sessionId())
                }
            }
        }
    }

    private fun applyProfile(pid: String) {
        Profiles.setActive(pid)
        Profiles.remember(pid)
        HistoryManager.init(this, pid)
        FavoritesManager.init(this, pid)
        accent = Profiles.accentArgb(Profiles.detail(pid)?.color)
        streamAdapter.accent = accent

        val p = Profiles.detail(pid)
        btnProfile.text = p?.name?.firstOrNull()?.uppercase() ?: "?"
        val avatarBg = android.graphics.drawable.GradientDrawable()
        avatarBg.shape = android.graphics.drawable.GradientDrawable.OVAL
        avatarBg.setColor(accent)
        btnProfile.background = avatarBg
        btnProfile.setTextColor(Color.WHITE)

        tabs.setSelectedTabIndicatorColor(accent)
        tabs.setTabTextColors(Color.parseColor("#a3a6ad"), accent)
        loading.indeterminateTintList = ColorStateList.valueOf(accent)

        streamAdapter.setFavs(favsAsStreams())
        reload()
    }

    private fun favsAsStreams(): List<Strm> =
        FavoritesManager.load().map { e ->
            Strm(
                id = e.optString("id", ""),
                name = e.optString("name", "Sin título"),
                icon = e.optString("logo", ""),
                ext = e.optString("ext", ""),
                type = e.optString("type", "live")
            )
        }

    private fun filterSearch(q: String) {
        if (q.isBlank()) {
            searching = false
            if (currentTab == 0) {
                visibleLive = 0
                loadingMore = false
                showLivePage(initial = true)
            } else {
                streamAdapter.submit(allItems)
                streamAdapter.setHasMore(false)
            }
            return
        }
        searching = true
        val lower = q.lowercase()
        val filtered = allItems.filter { it.name.lowercase().contains(lower) }
        streamAdapter.submit(filtered)
        streamAdapter.setHasMore(false)
        grid.scrollToPosition(0)
    }

    private fun reload() {
        searching = false
        visibleLive = 0
        loadingMore = false
        streamAdapter.setHasMore(false)
        when (currentTab) {
            0 -> loadLive()
            1, 2 -> loadCats()
            TAB_FAV -> loadFavs()
            TAB_HIST -> loadHistory()
        }
    }

    private fun setBusy(b: Boolean) {
        loading.visibility = if (b) View.VISIBLE else View.GONE
        grid.visibility = if (b) View.GONE else View.VISIBLE
    }

    private fun loadLive() {
        catScroller.visibility = View.GONE
        setBusy(true)
        lifecycleScope.launch {
            val list = try {
                withContext(Dispatchers.IO) { Xtream.liveAll() }
            } catch (e: Exception) {
                emptyList<Strm>()
            }
            if (list.isEmpty()) {
                Toast.makeText(this@MainActivity, "Sin respuesta del proveedor", Toast.LENGTH_LONG).show()
                setBusy(false)
                return@launch
            }
            allItems = Xtream.liveSorter(list)
            streamAdapter.setFavs(favsAsStreams())
            showLivePage(initial = true)
            setBusy(false)
        }
    }

    private fun showLivePage(initial: Boolean) {
        val all = allItems
        val target = minOf(visibleLive + PAGE, all.size)
        val chunk = all.subList(visibleLive, target)
        if (initial) {
            visibleLive = 0
            streamAdapter.submit(all.subList(0, minOf(PAGE, all.size)))
            visibleLive = all.subList(0, minOf(PAGE, all.size)).size
        } else {
            streamAdapter.append(chunk)
            visibleLive = target
        }
        streamAdapter.setHasMore(visibleLive < all.size)
    }

    private fun loadMore() {
        if (loadingMore) return
        if (visibleLive >= allItems.size) return
        loadingMore = true
        showLivePage(initial = false)
        loadingMore = false
    }

    private fun loadCats() {
        catScroller.visibility = View.VISIBLE
        setBusy(true)
        catRow.removeAllViews()
        catButtons.clear()
        cats = emptyList()
        lifecycleScope.launch {
            val res = try {
                withContext(Dispatchers.IO) {
                    if (currentTab == 1) Xtream.vodCategories() else Xtream.seriesCategories()
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

    private fun loadFavs() {
        catScroller.visibility = View.GONE
        setBusy(true)
        allItems = favsAsStreams()
        streamAdapter.setFavs(allItems)
        streamAdapter.submit(allItems)
        streamAdapter.setHasMore(false)
        setBusy(false)
        if (allItems.isEmpty()) Toast.makeText(this, "Sin favoritos todavía", Toast.LENGTH_SHORT).show()
    }

    private fun loadHistory() {
        catScroller.visibility = View.GONE
        setBusy(false)
        catRow.removeAllViews()
        val entries = HistoryManager.loadAll()
        if (entries.isEmpty()) {
            streamAdapter.submit(emptyList())
            streamAdapter.setHasMore(false)
            Toast.makeText(this, "Sin historial", Toast.LENGTH_SHORT).show()
            return
        }
        val items = entries.map { e ->
            Strm(
                id = e.optString("id", ""),
                name = e.optString("name", "Sin título"),
                icon = e.optString("logo", ""),
                ext = e.optString("ext", ""),
                type = e.optString("type", "vod")
            )
        }
        allItems = items
        streamAdapter.setFavs(favsAsStreams())
        streamAdapter.submit(items)
        streamAdapter.setHasMore(false)
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
            if (tvMode) {
                btn.isFocusable = true
                btn.isFocusableInTouchMode = true
            }
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
        val selBg = (accent and 0x00FFFFFF) or 0x24000000.toInt()
        val unselBg = 0xFF121316.toInt()
        for ((i, cat) in cats.withIndex()) {
            val btn = catButtons[i]
            val isSel = cat == selected
            btn.setTextColor(if (isSel) Color.WHITE else 0xFFA3A6AD.toInt())
            btn.backgroundTintList = ColorStateList.valueOf(if (isSel) selBg else unselBg)
            btn.strokeColor = ColorStateList.valueOf(if (isSel) accent else 0x12FFFFFF.toInt())
        }
    }

    private fun selectCat(cat: Cat) {
        styleCats(cat)
        setBusy(true)
        lifecycleScope.launch {
            val list = try {
                withContext(Dispatchers.IO) {
                    if (currentTab == 1) Xtream.vodStreams(cat.id) else Xtream.series(cat.id)
                }
            } catch (e: Exception) {
                emptyList<Strm>()
            }
            allItems = list
            streamAdapter.setFavs(favsAsStreams())
            streamAdapter.submit(list)
            streamAdapter.setHasMore(false)
            setBusy(false)
        }
    }

    private fun toggleFav(s: Strm) {
        val item = JSONObject().apply {
            put("id", s.id)
            put("name", s.name)
            put("logo", s.icon)
            put("type", s.type)
            put("ext", s.ext)
            put("ts", System.currentTimeMillis())
        }
        val (_, added) = FavoritesManager.toggle(item)
        streamAdapter.applyFav(s, added)
        Toast.makeText(this, if (added) "Guardado en favoritos" else "Quitado de favoritos", Toast.LENGTH_SHORT).show()
        if (currentTab == TAB_FAV) reload()
    }

    private fun openItem(s: Strm) {
        if (currentTab == TAB_HIST) {
            openFromHistory(s)
            return
        }
        if (currentTab == TAB_FAV && s.type == "series") {
            val i = Intent(this, SeriesActivity::class.java)
            i.putExtra("seriesId", s.id)
            i.putExtra("seriesName", s.name)
            i.putExtra("pid", Profiles.sessionId())
            startActivity(i)
            return
        }
        play(s, s.type)
    }

    private fun play(s: Strm, type: String) {
        val isLive = type == "live"
        val url = when (type) {
            "live" -> Accounts.liveUrl(s.id)
            "series" -> Accounts.seriesUrl(s.id, s.ext)
            else -> Accounts.movieUrl(s.id, s.ext)
        }
        val key = when (type) {
            "live" -> "live:${s.id}"
            "series" -> "ser:${s.id}"
            else -> "vod:${s.id}"
        }
        val entry = JSONObject().apply {
            put("key", key)
            put("id", s.id)
            put("name", s.name)
            put("logo", s.icon)
            put("type", type)
            put("ext", s.ext)
            put("url", url)
            put("position", 0)
            put("duration", 0)
            put("ts", System.currentTimeMillis())
        }
        HistoryManager.save(entry)

        val i = Intent(this, PlayerActivity::class.java)
        i.putExtra("url", url)
        i.putExtra("title", s.name)
        i.putExtra("histKey", key)
        i.putExtra("isLive", isLive)
        i.putExtra("pid", Profiles.sessionId())
        startActivity(i)
    }

    private fun openFromHistory(s: Strm) {
        val entries = HistoryManager.loadAll()
        val match = entries.find { it.optString("id") == s.id && it.optString("name") == s.name }
        val url = match?.optString("url").orEmpty()
        val pos = match?.optLong("position", 0) ?: 0
        val key = match?.optString("key").orEmpty()
        if (url.isBlank()) {
            Toast.makeText(this, "URL no disponible", Toast.LENGTH_SHORT).show()
            return
        }
        val i = Intent(this, PlayerActivity::class.java)
        i.putExtra("url", url)
        i.putExtra("title", s.name)
        i.putExtra("histKey", key)
        i.putExtra("isLive", key.startsWith("live:"))
        i.putExtra("startPosition", pos)
        i.putExtra("pid", Profiles.sessionId())
        startActivity(i)
    }
}
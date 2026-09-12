package com.tvparapobres.app

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.TypefaceSpan
import android.view.View
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
    }

    private lateinit var grid: RecyclerView
    private lateinit var skelGrid: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var catRow: LinearLayout
    private lateinit var catScroller: View
    private lateinit var tabs: TabLayout
    private lateinit var searchBox: EditText
    private lateinit var btnProfile: FrameLayout
    private lateinit var proAvatar: AvatarView
    private lateinit var btnFav: TextView
    private lateinit var btnHist: TextView
    private lateinit var gridTitle: TextView
    private lateinit var gridCount: TextView
    private lateinit var servLock: LinearLayout
    private lateinit var servTitle: TextView
    private lateinit var servSub: TextView
    private lateinit var servHint: TextView
    private lateinit var streamAdapter: StreamAdapter

    private var currentTab = 0
    private var showFavs = false
    private var showHist = false
    private var activeCat: Cat? = null
    private var cats = emptyList<Cat>()
    private val catButtons = mutableListOf<MaterialButton>()
    private var allItems = emptyList<Strm>()
    private var visibleLive = 0
    private var loadingMore = false
    private var searching = false
    private var accent: Int = Color.parseColor(Profiles.DEFAULT_COLOR)
    private var tvMode = false
    private var compactTabs = false
    private var restoring = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Si hubo un crash, mostrar el reporte en vez de reintentar a ciegas.
        try {
            val crashFile = java.io.File(filesDir, "crash.txt")
            if (crashFile.exists()) {
                startActivity(Intent(this, CrashReportActivity::class.java))
                finish()
                return
            }
        } catch (_: Exception) {
        }
        setContentView(R.layout.activity_main)

        Profiles.init(applicationContext)
        accent = Profiles.accentArgb()
        tvMode = isTvDevice(this)
        compactTabs = !tvMode && resources.configuration.screenWidthDp < 600

        grid = findViewById(R.id.grid)
        catRow = findViewById(R.id.catRow)
        catScroller = findViewById(R.id.catScroller)
        skelGrid = findViewById(R.id.skelGrid)
        emptyView = findViewById(R.id.emptyView)
        tabs = findViewById(R.id.tabLayout)
        searchBox = findViewById(R.id.searchBox)
        btnProfile = findViewById(R.id.btnProfile)
        proAvatar = findViewById(R.id.proAvatar)
        btnFav = findViewById(R.id.btnFav)
        btnHist = findViewById(R.id.btnHist)
        gridTitle = findViewById(R.id.gridTitle)
        gridCount = findViewById(R.id.gridCount)
        servLock = findViewById(R.id.servLock)
        servTitle = findViewById(R.id.servTitle)
        servSub = findViewById(R.id.servSub)
        servHint = findViewById(R.id.servHint)
        setupTabs()

        findViewById<View>(R.id.btnDonate).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://tvparapobres.tipsterpage.com/kttCMjB2")))
        }
        btnProfile.setOnClickListener {
            startActivityForResult(Intent(this, ProfilePickerActivity::class.java), REQ_PICKER)
        }
        btnFav.setOnClickListener {
            showFavs = !showFavs
            if (showFavs) showHist = false
            updateHeadButtons()
            reload()
            if (searchBox.text.isNotEmpty()) filterSearch(searchBox.text.toString())
        }
        btnHist.setOnClickListener {
            showHist = !showHist
            if (showHist) showFavs = false
            updateHeadButtons()
            reload()
            if (searchBox.text.isNotEmpty()) filterSearch(searchBox.text.toString())
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
        streamAdapter = StreamAdapter(accent, tvMode, ::openItem, ::toggleFav, ::loadMore, ::removeHist)
        grid.adapter = streamAdapter
        skelGrid.layoutManager = GridLayoutManager(this, cols)
        skelGrid.adapter = SkelAdapter()
        skelGrid.setHasFixedSize(true)

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
                if (restoring) return
                currentTab = tab.position
                showFavs = false
                showHist = false
                styleTabs()
                updateHeadButtons()
                reload()
                if (searchBox.text.isNotEmpty()) searchBox.text.clear()
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

        if (savedInstanceState != null) restoreState(savedInstanceState)
        ensureProfile()
        checkService()
        lockHandler.postDelayed(lockCheck, 15000)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", currentTab)
        outState.putBoolean("favs", showFavs)
        outState.putBoolean("hist", showHist)
        if (::searchBox.isInitialized) outState.putString("q", searchBox.text.toString())
    }

    private fun restoreState(saved: Bundle) {
        restoring = true
        currentTab = saved.getInt("tab", 0)
        showFavs = saved.getBoolean("favs", false)
        showHist = saved.getBoolean("hist", false)
        tabs.getTabAt(currentTab)?.select()
        styleTabs()
        updateHeadButtons()
        searchBox.setText(saved.getString("q", ""))
        restoring = false
    }

    override fun onDestroy() {
        lockHandler.removeCallbacks(lockCheck)
        super.onDestroy()
    }

    /* ----- Bloqueos de servicio/vencimiento, como la web ----- */
    private val lockHandler = Handler(Looper.getMainLooper())
    private val lockCheck = object : Runnable {
        override fun run() {
            checkService()
            lockHandler.postDelayed(this, 15000)
        }
    }

    private fun checkService() {
        lifecycleScope.launch {
            val info = try {
                withContext(Dispatchers.IO) { Xtream.userInfo() }
            } catch (_: Exception) {
                null
            }
            if (info == null) {
                showLock("down")
                return@launch
            }
            val exp = Xtream.expDate(info)
            if (exp > 0) {
                val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                val today = fmt.format(java.util.Date())
                val expStr = fmt.format(java.util.Date(exp * 1000))
                if (today >= expStr) {
                    showLock("expired")
                    return@launch
                }
            }
            hideLock()
        }
    }

    private fun showLock(mode: String) {
        if (mode == "expired") {
            servTitle.text = "Actualizando servidores"
            servSub.text = "Estamos actualizando los servidores para seguir disfrutando de contenido gratis. Gracias por tu paciencia."
            servHint.visibility = View.GONE
        } else {
            servTitle.text = "Sin servicio por el momento"
            servSub.text = "Nuestro equipo está realizando mantenimiento. El contenido volverá a estar disponible en unos minutos. ¡Gracias por tu paciencia!"
            servHint.text = "Reintentando automáticamente en unos segundos..."
            servHint.visibility = View.VISIBLE
        }
        servLock.visibility = View.VISIBLE
    }

    private fun hideLock() {
        servLock.visibility = View.GONE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && tvMode && currentTab == 0 && streamAdapter.itemCount > 0) {
            grid.post { grid.requestFocus() }
        }
    }

    /* ----- Acento dinámico (igual que --accent* de la web) ----- */
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun withAlpha(c: Int, a: Int): Int = (c and 0x00FFFFFF) or ((a and 0xFF) shl 24)
    private fun lighten(c: Int, k: Float): Int {
        fun m(v: Int) = (v + (255 - v) * k).toInt().coerceIn(0, 255)
        return Color.rgb(m(Color.red(c)), m(Color.green(c)), m(Color.blue(c)))
    }
    private fun accentStrong(): Int = lighten(accent, 0.4f)
    private fun accent2(): Int = lighten(accent, 0.55f)

    private fun tabActiveBg(): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(accent, accent2())).apply {
            cornerRadius = dp(9).toFloat()
        }

    private fun headActiveBg(): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(withAlpha(accent, 0x24))
        setStroke(dp(1), withAlpha(accent, 0x73))
        cornerRadius = dp(12).toFloat()
    }

    private fun countBg(): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(withAlpha(accent, 0x24))
        setStroke(dp(1), withAlpha(accent, 0x38))
        cornerRadius = dp(20).toFloat()
    }

    private fun dashBg(): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(accent, Color.TRANSPARENT)
    ).apply { cornerRadius = dp(2).toFloat() }

    private fun donateBg(): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(accent, accent2())).apply {
            cornerRadius = dp(12).toFloat()
        }

    /* ----- Tabs estilo web (pills con icono) ----- */
    private val TAB_TITLES = listOf("LIVE", "PELÍCULAS", "SERIES")
    private val TAB_ICONS = listOf(R.drawable.ic_tab_live, R.drawable.ic_tab_film, R.drawable.ic_tab_series)

    private fun setupTabs() {
        tabs.removeAllTabs()
        TAB_TITLES.forEachIndexed { i, t ->
            val tab = tabs.newTab()
            val v = layoutInflater.inflate(R.layout.tab_pill, tabs, false)
            val label = v.findViewById<TextView>(R.id.tabLabel)
            val icon = v.findViewById<ImageView>(R.id.tabIcon)
            label.text = t
            if (compactTabs) {
                // En vertical angosto no caben icono+texto: solo texto (como la web móvil).
                icon.visibility = View.GONE
                label.textSize = 11f
            } else {
                icon.setImageResource(TAB_ICONS[i])
            }
            tab.customView = v
            tabs.addTab(tab)
        }
        styleTabs()
    }

    private fun styleTabs() {
        val sel = tabs.selectedTabPosition
        for (i in 0 until tabs.tabCount) {
            val v = tabs.getTabAt(i)?.customView ?: continue
            val on = i == sel
            val label = v.findViewById<TextView>(R.id.tabLabel)
            val icon = v.findViewById<ImageView>(R.id.tabIcon)
            v.background = if (on) tabActiveBg() else null
            label.setTextColor(if (on) Color.WHITE else 0xFFA3A6AD.toInt())
            icon.setColorFilter(if (on) Color.WHITE else 0xFFA3A6AD.toInt())
        }
    }

    /* ----- Botones ★/reloj con contador + título/contador del grid (como la web) ----- */
    private fun updateHeadButtons() {
        val strong = accentStrong()
        val muted = 0xFFA3A6AD.toInt()
        val fc = streamAdapter.favCount()
        val fss = SpannableString("★ $fc")
        fss.setSpan(ForegroundColorSpan(if (showFavs) strong else Color.WHITE), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        fss.setSpan(ForegroundColorSpan(if (showFavs) strong else muted), 2, fss.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        fss.setSpan(TypefaceSpan("monospace"), 2, fss.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        fss.setSpan(AbsoluteSizeSpan(11, true), 2, fss.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        btnFav.text = fss
        btnFav.background = if (showFavs) headActiveBg() else ContextCompat.getDrawable(this, R.drawable.bg_headbtn)
        val hc = try { HistoryManager.loadAll().size } catch (_: Exception) { 0 }
        btnHist.text = "$hc"
        btnHist.setTextColor(if (showHist) strong else muted)
        btnHist.compoundDrawableTintList = ColorStateList.valueOf(if (showHist) strong else Color.WHITE)
        btnHist.background = if (showHist) headActiveBg() else ContextCompat.getDrawable(this, R.drawable.bg_headbtn)
        findViewById<View>(R.id.btnDonate).background = donateBg()
    }

    private fun currentTitle(): String = when {
        showHist -> "Historial"
        showFavs -> "Favoritos"
        currentTab == 0 -> "Canales"
        else -> cleanName(activeCat?.name ?: "...")
    }

    private fun updateHead(title: String, total: Int) {
        gridTitle.text = title
        gridCount.text = "$total"
        gridCount.setTextColor(accentStrong())
        gridCount.background = countBg()
        findViewById<View>(R.id.gridDash).background = dashBg()
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
        proAvatar.set(p?.photo, accent, p?.avatar ?: 0)

        tabs.setSelectedTabIndicatorColor(accent)
        tabs.setTabTextColors(Color.parseColor("#a3a6ad"), accent)

        streamAdapter.setFavs(favsAsStreams())
        styleTabs()
        updateHeadButtons()
        updateHead(currentTitle(), allItems.size)
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
        // Se busca sobre el nombre NORMALIZADO (limpio, sin tildes, sin simbolos ni
        // espacios): es lo mismo que ve el usuario en la tarjeta. Asi "espn"
        // encuentra "*ESP*N", "E S P N" o "3SPN".
        val nq = norm(q)
        if (nq.isEmpty()) {
            searching = false
            if (!showFavs && !showHist && currentTab == 0) {
                visibleLive = 0
                loadingMore = false
                showLivePage(initial = true)
            } else {
                streamAdapter.submit(allItems)
                streamAdapter.setHasMore(false)
                if (allItems.isEmpty()) showEmpty("Sin resultados.") else hideEmpty()
            }
            updateHead(currentTitle(), allItems.size)
            return
        }
        searching = true
        val filtered = allItems.filter { normCached(it.name).contains(nq) }
        streamAdapter.submit(filtered)
        streamAdapter.setHasMore(false)
        if (filtered.isEmpty()) showEmpty("Sin resultados.") else hideEmpty()
        updateHead(currentTitle(), filtered.size)
        grid.scrollToPosition(0)
    }

    private val R_MARKS = Regex("\\p{M}")
    private val R_NONALNUM = Regex("[^a-z0-9]")
    private val normCache = HashMap<String, String>(2048)

    private fun norm(s: String): String {
        val folded = java.text.Normalizer.normalize(cleanName(s), java.text.Normalizer.Form.NFD)
            .replace(R_MARKS, "")
        return folded.lowercase().replace(R_NONALNUM, "")
    }

    private fun cleanName(raw: String): String = NameCleaner.clean(raw)

    private fun normCached(raw: String): String =
        normCache.getOrPut(raw) { norm(raw) }

    private fun warmNorm(items: List<Strm>) {
        for (s in items) normCache.getOrPut(s.name) { norm(s.name) }
    }

    private fun reload() {
        searching = false
        visibleLive = 0
        loadingMore = false
        streamAdapter.histMode = showHist
        streamAdapter.setHasMore(false)
        when {
            showHist -> loadHistory()
            showFavs -> loadFavs()
            currentTab == 0 -> loadLive()
            else -> loadCats()
        }
    }

    private var busy = false

    private fun setBusy(b: Boolean) {
        busy = b
        skelGrid.visibility = if (b) View.VISIBLE else View.GONE
        emptyView.visibility = View.GONE
        grid.visibility = if (b) View.GONE else View.VISIBLE
    }

    private fun showEmpty(msg: String) {
        emptyView.text = msg
        emptyView.visibility = View.VISIBLE
        grid.visibility = View.GONE
    }

    private fun hideEmpty() {
        emptyView.visibility = View.GONE
        if (!busy) grid.visibility = View.VISIBLE
    }

    private fun animateGrid() {
        grid.layoutAnimation =
            AnimationUtils.loadLayoutAnimation(this, R.anim.grid_layout_animation)
        grid.scheduleLayoutAnimation()
        grid.postDelayed({ grid.layoutAnimation = null }, 900)
    }

    private fun loadLive() {
        catScroller.visibility = View.GONE
        setBusy(true)
        lifecycleScope.launch {
            // Red en IO y ordenamiento pesado en Default: nada bloquea el hilo principal (evita ANR).
            val list = try {
                val raw = withContext(Dispatchers.IO) { Xtream.liveAll() }
                withContext(Dispatchers.Default) {
                    val sorted = Xtream.liveSorter(raw)
                    warmNorm(sorted)
                    sorted
                }
            } catch (e: Exception) {
                emptyList<Strm>()
            }
            if (list.isEmpty()) {
                setBusy(false)
                showEmpty("Sin resultados.")
                return@launch
            }
            allItems = list
            streamAdapter.setFavs(favsAsStreams())
            updateHeadButtons()
            updateHead("Canales", allItems.size)
            if (searchBox.text.isNotEmpty()) filterSearch(searchBox.text.toString())
            else showLivePage(initial = true)
            setBusy(false)
        }
    }

    private fun showLivePage(initial: Boolean) {
        val all = allItems
        if (all.isEmpty()) {
            showEmpty("Sin resultados.")
            return
        }
        hideEmpty()
        val target = minOf(visibleLive + PAGE, all.size)
        val chunk = all.subList(visibleLive, target)
        if (initial) {
            visibleLive = 0
            streamAdapter.submit(all.subList(0, minOf(PAGE, all.size)))
            visibleLive = all.subList(0, minOf(PAGE, all.size)).size
            animateGrid()
        } else {
            streamAdapter.append(chunk)
            visibleLive = target
        }
        streamAdapter.setHasMore(visibleLive < all.size)
        if (visibleLive < all.size) streamAdapter.setMoreLabel(all.size - visibleLive)
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
                setBusy(false)
                showEmpty("Sin resultados.")
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
        warmNorm(allItems)
        streamAdapter.setFavs(allItems)
        streamAdapter.submit(allItems)
        streamAdapter.setHasMore(false)
        updateHead("Favoritos", allItems.size)
        setBusy(false)
        if (allItems.isEmpty()) {
            showEmpty("Aún no tienes favoritos. Toca ★ en un canal para guardarlo.")
        } else {
            hideEmpty()
            animateGrid()
        }
    }

    private fun loadHistory() {
        catScroller.visibility = View.GONE
        setBusy(false)
        catRow.removeAllViews()
        // Como la web: el historial no lista canales en vivo.
        val entries = HistoryManager.loadAll().filter { it.optString("type") != "live" }
        if (entries.isEmpty()) {
            streamAdapter.submit(emptyList())
            streamAdapter.setHasMore(false)
            updateHead("Historial", 0)
            showEmpty("Aún no hay nada en tu historial.")
            return
        }
        val items = entries.map { e ->
            Strm(
                id = e.optString("id", ""),
                name = e.optString("name", "Sin título"),
                icon = e.optString("logo", ""),
                ext = e.optString("ext", ""),
                type = e.optString("type", "vod"),
                pos = e.optLong("position", 0),
                dur = e.optLong("duration", 0),
                hkey = e.optString("key", "")
            )
        }
        allItems = items
        warmNorm(items)
        streamAdapter.setFavs(favsAsStreams())
        streamAdapter.submit(items)
        streamAdapter.setHasMore(false)
        hideEmpty()
        animateGrid()
        updateHead("Historial", items.size)
    }

    private fun removeHist(key: String) {
        if (key.isEmpty()) return
        HistoryManager.remove(key)
        updateHeadButtons()
        if (showHist) reload()
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
        activeCat = cat
        styleCats(cat)
        setBusy(true)
        lifecycleScope.launch {
            val list = try {
                withContext(Dispatchers.IO) {
                    (if (currentTab == 1) Xtream.vodStreams(cat.id) else Xtream.series(cat.id))
                        .also { warmNorm(it) }
                }
            } catch (e: Exception) {
                emptyList<Strm>()
            }
            allItems = list
            streamAdapter.setFavs(favsAsStreams())
            streamAdapter.submit(list)
            streamAdapter.setHasMore(false)
            updateHead(cleanName(cat.name), list.size)
            if (list.isEmpty()) {
                showEmpty("Sin resultados.")
            } else {
                hideEmpty()
                animateGrid()
            }
            if (searchBox.text.isNotEmpty()) filterSearch(searchBox.text.toString())
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
        updateHeadButtons()
        if (showFavs) reload()
    }

    private fun openItem(s: Strm) {
        if (showHist) {
            openFromHistory(s)
            return
        }
        if (showFavs && s.type == "series") {
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
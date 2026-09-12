package com.tvparapobres.app

import android.animation.ObjectAnimator
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.ui.PlayerControlView
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class PlayerActivity : AppCompatActivity(), Player.Listener {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var playerTopBar: LinearLayout
    private lateinit var streamTitle: TextView
    private lateinit var chipText: TextView
    private lateinit var pulseDot: View
    private lateinit var chposView: TextView
    private lateinit var epgBar: LinearLayout
    private lateinit var epgNowRow: LinearLayout
    private lateinit var epgNowTag: TextView
    private lateinit var epgNowTitle: TextView
    private lateinit var epgNowAt: TextView
    private lateinit var epgNextRow: LinearLayout
    private lateinit var epgNextTag: TextView
    private lateinit var epgNextTitle: TextView
    private lateinit var epgNextAt: TextView
    private lateinit var epgDiv: View
    private lateinit var connectOverlay: LinearLayout
    private lateinit var connectSub: TextView
    private lateinit var dot1: View
    private lateinit var dot2: View
    private lateinit var dot3: View
    private lateinit var errorOverlay: LinearLayout
    private lateinit var errorMsg: TextView
    private val uiHandler = Handler(Looper.getMainLooper())
    private var accent: Int = Color.parseColor("#ff4d2e")
    private var barsVisible = true
    private var epgHasContent = false
    private val barsHide = Runnable { showBars(false) }

    private var histKey: String? = null
    private var startPos: Long = 0
    private var isLive = false
    private var currentUrl: String? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var channelList = emptyList<Strm>()
    private var liveLoading = false
    private var idx = -1

    private var retries = 0
    private var retrying = false
    private var lastPosIn = -1L
    private var lastMoveAt = 0L

    private val retryRunnable = Runnable {
        retrying = false
        currentUrl?.let { setMedia(it) }
    }
    private val stallRunnable = object : Runnable {
        override fun run() {
            checkStall()
            uiHandler.postDelayed(this, 5000)
        }
    }

    private val dataSourceFactory by lazy {
        DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20000)
            .setReadTimeoutMs(60000)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        if (!isTvDevice(this)) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "tvpp:playback")
        wakeLock?.acquire(4 * 60 * 60 * 1000L)

        Profiles.init(applicationContext)
        val pid = intent.getStringExtra("pid") ?: Profiles.sessionId()
        accent = Profiles.accentArgb(Profiles.detail(pid)?.color)
        HistoryManager.init(applicationContext, pid.ifBlank { "default" })
        FavoritesManager.init(applicationContext, pid.ifBlank { "default" })

        playerView = findViewById(R.id.playerView)
        playerTopBar = findViewById(R.id.playerTopBar)
        streamTitle = findViewById(R.id.streamTitle)
        chipText = findViewById(R.id.chipText)
        pulseDot = findViewById(R.id.pulseDot)
        chposView = findViewById(R.id.chposView)
        epgBar = findViewById(R.id.epgBar)
        epgNowRow = findViewById(R.id.epgNowRow)
        epgNowTag = findViewById(R.id.epgNowTag)
        epgNowTitle = findViewById(R.id.epgNowTitle)
        epgNowAt = findViewById(R.id.epgNowAt)
        epgNextRow = findViewById(R.id.epgNextRow)
        epgNextTag = findViewById(R.id.epgNextTag)
        epgNextTitle = findViewById(R.id.epgNextTitle)
        epgNextAt = findViewById(R.id.epgNextAt)
        epgDiv = findViewById(R.id.epgDiv)
        connectOverlay = findViewById(R.id.connectOverlay)
        connectSub = findViewById(R.id.connectSub)
        dot1 = findViewById(R.id.dot1)
        dot2 = findViewById(R.id.dot2)
        dot3 = findViewById(R.id.dot3)
        errorOverlay = findViewById(R.id.errorOverlay)
        errorMsg = findViewById(R.id.errorMsg)

        val url = intent.getStringExtra("url")
        val title = intent.getStringExtra("title")
        histKey = intent.getStringExtra("histKey")
        isLive = intent.getBooleanExtra("isLive", false)
        startPos = intent.getLongExtra("startPosition", 0)

        if (url.isNullOrBlank()) {
            finish()
            return
        }

        streamTitle.text = title.orEmpty()
        currentUrl = url
        applyPlayerTheme()

        setupPlayer(url, startPos)
        showConnecting(null)

        if (isLive) {
            chipText.text = "EN VIVO"
            chipText.setTextColor(0xFFFF8A91.toInt())
            loadChannels()
            loadEpg(histKey?.removePrefix("live:").orEmpty())
        } else {
            chipText.text = "REPRODUCIENDO"
            chposView.text = ""
        }
    }

    private fun lighten(c: Int, k: Float): Int {
        fun m(v: Int) = (v + (255 - v) * k).toInt().coerceIn(0, 255)
        return Color.rgb(m(Color.red(c)), m(Color.green(c)), m(Color.blue(c)))
    }

    private fun withAlpha(c: Int, a: Int): Int = (c and 0x00FFFFFF) or ((a and 0xFF) shl 24)

    /** Tiñe el player con el acento del perfil, como el theme de la web. */
    private fun applyPlayerTheme() {
        val strong = lighten(accent, 0.4f)
        for (d in listOf(dot1, dot2, dot3)) d.backgroundTintList = ColorStateList.valueOf(accent)
        epgNowTag.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(accent)
            cornerRadius = 20f * resources.displayMetrics.density
        }
        epgNextTag.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(withAlpha(accent, 0x24))
            cornerRadius = 20f * resources.displayMetrics.density
        }
        epgNextTag.setTextColor(strong)
    }

    private val connAnims = mutableListOf<ObjectAnimator>()
    private var pulseAnim: ObjectAnimator? = null

    private fun startPulse() {
        pulseAnim?.cancel()
        pulseAnim = ObjectAnimator.ofFloat(pulseDot, "alpha", 1f, 0.25f).apply {
            duration = 1100
            interpolator = LinearInterpolator()
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            start()
        }
        connAnims.add(pulseAnim!!)
    }

    private fun startDots() {
        val dots = listOf(dot1 to 0L, dot2 to 140L, dot3 to 280L)
        for ((v, delay) in dots) {
            val a = ObjectAnimator.ofFloat(v, "alpha", 0.35f, 1f).apply {
                duration = 1100
                interpolator = LinearInterpolator()
                startDelay = delay
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
                start()
            }
            val s = ObjectAnimator.ofFloat(v, "scaleX", 0.7f, 1.15f).apply {
                duration = 1100
                interpolator = LinearInterpolator()
                startDelay = delay
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
                start()
            }
            val s2 = ObjectAnimator.ofFloat(v, "scaleY", 0.7f, 1.15f).apply {
                duration = 1100
                interpolator = LinearInterpolator()
                startDelay = delay
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
                start()
            }
            connAnims.add(a)
            connAnims.add(s)
            connAnims.add(s2)
        }
    }

    private fun stopOverlaysAnim() {
        connAnims.forEach { it.cancel() }
        connAnims.clear()
        pulseAnim = null
        pulseDot.alpha = 1f
        for (d in listOf(dot1, dot2, dot3)) {
            d.alpha = 1f
            d.scaleX = 1f
            d.scaleY = 1f
        }
    }

    private fun showConnecting(sub: String?) {
        errorOverlay.visibility = View.GONE
        if (sub != null) connectSub.text = sub
        if (connectOverlay.visibility != View.VISIBLE) {
            connectOverlay.visibility = View.VISIBLE
            startDots()
        }
    }

    private fun showError(msg: String) {
        stopOverlaysAnim()
        startPulse()
        connectOverlay.visibility = View.GONE
        errorMsg.text = msg
        errorOverlay.visibility = View.VISIBLE
    }

    private fun hideOverlays() {
        stopOverlaysAnim()
        connectOverlay.visibility = View.GONE
        errorOverlay.visibility = View.GONE
        startPulse()
    }

    private fun setupPlayer(url: String, position: Long) {
        val p = ExoPlayer.Builder(this).build()
        p.addListener(this)
        player = p
        playerView.player = p
        // Barras propias siguen al control nativo: pantalla completa real sin nada fijo.
        playerView.setControllerVisibilityListener(
            PlayerControlView.VisibilityListener { visibility ->
                showBars(visibility == View.VISIBLE)
            }
        )
        setMedia(url)
        if (position > 0) p.seekTo(position)
    }

    private fun showBars(v: Boolean) {
        barsVisible = v
        uiHandler.removeCallbacks(barsHide)
        playerTopBar.visibility = if (v) View.VISIBLE else View.GONE
        epgBar.visibility = if (v && epgHasContent) View.VISIBLE else View.GONE
    }

    private fun setMedia(url: String) {
        val p = player ?: return
        val mediaItem = MediaItem.fromUri(url)
        val source = if (url.endsWith(".m3u8")) {
            HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        } else {
            ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        }
        p.stop()
        p.clearMediaItems()
        p.setMediaSource(source)
        p.prepare()
        p.playWhenReady = true
    }

    private fun loadChannels() {
        if (liveLoading) return
        liveLoading = true
        lifecycleScope.launch {
            val list = try {
                withContext(Dispatchers.IO) { Xtream.liveAll() }
            } catch (e: Exception) {
                emptyList<Strm>()
            }
            // El ordenamiento es pesado: va en background para no congelar el reproductor (ANR).
            channelList = withContext(Dispatchers.Default) { Xtream.liveSorter(list) }
            val myId = histKey?.removePrefix("live:")
            idx = if (myId.isNullOrEmpty()) -1 else channelList.indexOfFirst { it.id == myId }
            if (channelList.isNotEmpty()) {
                val shown = if (idx >= 0) idx + 1 else 0
                chposView.text = if (shown > 0) "$shown / ${channelList.size}" else "1 / ${channelList.size}"
            }
            liveLoading = false
        }
    }

    private fun loadEpg(streamId: String) {
        if (streamId.isEmpty()) return
        lifecycleScope.launch {
            val lines = try {
                withContext(Dispatchers.IO) { Xtream.shortEpg(streamId) }
            } catch (e: Exception) {
                emptyList<Xtream.EpgLine>()
            }
            if (!isFinishing) renderEpg(lines)
        }
    }

    private fun renderEpg(lines: List<Xtream.EpgLine>) {
        val now = System.currentTimeMillis() / 1000
        val cur = lines.firstOrNull { it.start <= now && now < it.end }
        val next = lines.firstOrNull { it.start > now }
        epgHasContent = cur != null || next != null
        if (!epgHasContent) {
            epgBar.visibility = View.GONE
            return
        }
        epgBar.visibility = if (barsVisible) View.VISIBLE else View.GONE
        if (cur != null) {
            epgNowRow.visibility = View.VISIBLE
            epgNowTitle.text = NameCleaner.clean(cur.title).ifBlank { "Sin título" }
            epgNowAt.text = "${fmtHour(cur.start)} – ${fmtHour(cur.end)}"
        } else {
            epgNowRow.visibility = View.GONE
        }
        if (next != null) {
            epgNextRow.visibility = View.VISIBLE
            epgNextTitle.text = NameCleaner.clean(next.title).ifBlank { "Sin título" }
            epgNextAt.text = fmtHour(next.start)
        } else {
            epgNextRow.visibility = View.GONE
        }
        epgDiv.visibility = if (cur != null && next != null) View.VISIBLE else View.GONE
    }

    private fun fmtHour(epochSec: Long): String =
        try {
            java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date(epochSec * 1000))
        } catch (_: Exception) {
            ""
        }

    private fun switchChannel(delta: Int) {
        if (!isLive) return
        if (channelList.isEmpty()) {
            showConnecting("CARGANDO CANALES…")
            loadChannels()
            return
        }
        var i = idx + delta
        if (i < 0) i = channelList.size - 1
        if (i >= channelList.size) i = 0
        idx = i
        val s = channelList[idx]
        val url = Accounts.liveUrl(s.id)
        val key = "live:${s.id}"
        currentUrl = url
        histKey = key
        streamTitle.text = s.name
        chposView.text = "${idx + 1} / ${channelList.size}"
        HistoryManager.save(
            JSONObject().apply {
                put("key", key)
                put("id", s.id)
                put("name", s.name)
                put("logo", s.icon)
                put("type", "live")
                put("ext", s.ext)
                put("url", url)
                put("position", 0)
                put("duration", 0)
                put("ts", System.currentTimeMillis())
            }
        )
        retries = 0
        setMedia(url)
        renderEpg(emptyList())
        epgHasContent = false
        epgBar.visibility = View.GONE
        loadEpg(s.id)
        showConnecting(null)
        // Feedback del cambio aunque las barras estén ocultas.
        if (!playerView.isControllerVisible) {
            showBars(true)
            uiHandler.removeCallbacks(barsHide)
            uiHandler.postDelayed(barsHide, 4000)
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        retrying = false
        retries++
        if (retries > 60) {
            showError("Sin señal. Cambia de canal para reintentar.")
            return
        }
        val delay = if (retries <= 8) 3000L * retries else 30000L
        showError("Reintentando… ($retries)")
        uiHandler.removeCallbacks(retryRunnable)
        uiHandler.postDelayed(retryRunnable, delay.coerceAtMost(30000))
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_READY) {
            retries = 0
            hideOverlays()
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            lastMoveAt = System.currentTimeMillis()
            lastPosIn = player?.currentPosition ?: -1
        }
    }

    private fun checkStall() {
        val p = player ?: return
        val now = System.currentTimeMillis()
        if (!p.playWhenReady) return
        val pos = p.currentPosition
        val state = p.playbackState
        if (state == Player.STATE_BUFFERING || state == Player.STATE_IDLE) {
            if (p.isLoading && now - lastMoveAt > 20000 && isLive) {
                showError("Señal lenta, reintentando…")
                forceRetry()
            }
        } else if (p.isPlaying) {
            if (pos == lastPosIn) {
                if (now - lastMoveAt > 10000 && isLive) {
                    showError("Señal congelada, reintentando…")
                    forceRetry()
                }
            } else {
                lastMoveAt = now
                lastPosIn = pos
            }
        }
    }

    private fun forceRetry() {
        if (retrying) return
        retrying = true
        showError("Reintentando…")
        uiHandler.removeCallbacks(retryRunnable)
        uiHandler.postDelayed(retryRunnable, 1500)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            // Cambio de canal invisible (sin botones en pantalla): CH, Page y flechas.
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_DPAD_UP -> {
                switchChannel(1); true
            }
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_DPAD_DOWN -> {
                switchChannel(-1); true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onStart() {
        super.onStart()
        startPulse()
        uiHandler.postDelayed(stallRunnable, 5000)
    }

    override fun onStop() {
        uiHandler.removeCallbacks(stallRunnable)
        stopOverlaysAnim()
        super.onStop()
    }

    override fun onPause() {
        super.onPause()
        uiHandler.removeCallbacks(retryRunnable)
        uiHandler.removeCallbacks(barsHide)
        savePos()
        player?.playWhenReady = false
        playerView.onPause()
    }

    override fun onResume() {
        super.onResume()
        playerView.onResume()
    }

    override fun onDestroy() {
        uiHandler.removeCallbacks(retryRunnable)
        uiHandler.removeCallbacks(stallRunnable)
        uiHandler.removeCallbacks(barsHide)
        savePos()
        player?.release()
        player = null
        super.onDestroy()
        try { wakeLock?.release() } catch (_: Exception) {}
        wakeLock = null
    }

    private fun savePos() {
        val p = player ?: return
        val key = histKey ?: return
        val pos = p.currentPosition.coerceAtLeast(0)
        val dur = p.duration.coerceAtLeast(0)
        if (isLive) {
            if (key.startsWith("live:")) HistoryManager.touchTs(key)
            return
        }
        if (pos > 0 && dur > 0) {
            HistoryManager.updatePosition(key, pos, dur)
        }
    }
}
package com.tvparapobres.app

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
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
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class PlayerActivity : AppCompatActivity(), Player.Listener {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var streamTitle: TextView
    private lateinit var epgText: TextView
    private lateinit var chanInfo: TextView
    private lateinit var bottomBar: FrameLayout
    private lateinit var btnPrev: TextView
    private lateinit var btnNext: TextView
    private val uiHandler = Handler(Looper.getMainLooper())

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

    private val hideRunnable = Runnable { hideOverlays() }
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
        HistoryManager.init(applicationContext, pid.ifBlank { "default" })
        FavoritesManager.init(applicationContext, pid.ifBlank { "default" })

        playerView = findViewById(R.id.playerView)
        streamTitle = findViewById(R.id.streamTitle)
        epgText = findViewById(R.id.epgText)
        chanInfo = findViewById(R.id.chanInfo)
        bottomBar = findViewById(R.id.bottomBar)
        btnPrev = findViewById(R.id.btnPrev)
        btnNext = findViewById(R.id.btnNext)

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

        btnPrev.setOnClickListener { switchChannel(-1) }
        btnNext.setOnClickListener { switchChannel(1) }
        if (isTvDevice(this)) {
            btnPrev.setOnFocusChangeListener { v, has ->
                v.scaleX = if (has) 1.08f else 1f
                v.scaleY = if (has) 1.08f else 1f
            }
            btnNext.setOnFocusChangeListener { v, has ->
                v.scaleX = if (has) 1.08f else 1f
                v.scaleY = if (has) 1.08f else 1f
            }
        }

        setupPlayer(url, startPos)
        showOverlay()

        if (isLive) {
            loadChannels()
            loadEpg(histKey?.removePrefix("live:").orEmpty())
        }
    }

    private fun setupPlayer(url: String, position: Long) {
        val p = ExoPlayer.Builder(this).build()
        p.addListener(this)
        player = p
        playerView.player = p
        setMedia(url)
        if (position > 0) p.seekTo(position)
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
            channelList = Xtream.liveSorter(list)
            val myId = histKey?.removePrefix("live:")
            idx = if (myId.isNullOrEmpty()) -1 else channelList.indexOfFirst { it.id == myId }
            if (channelList.isNotEmpty()) {
                val shown = if (idx >= 0) idx + 1 else 0
                chanInfo.text = if (shown > 0) "$shown / ${channelList.size}" else "1 / ${channelList.size}"
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
        if (lines.isEmpty()) {
            epgText.visibility = View.GONE
            return
        }
        val now = System.currentTimeMillis() / 1000
        val cur = lines.firstOrNull { it.start <= now && now < it.end }
        val next = lines.firstOrNull { it.start > now }
        val sb = StringBuilder()
        if (cur != null) {
            sb.append("AHORA: ").append(cur.title)
        }
        if (next != null) {
            if (sb.isNotEmpty()) sb.append("\n")
            sb.append("DESPUÉS: ").append(next.title)
                .append("  ").append(fmtTime(next.start))
        }
        if (sb.isEmpty()) {
            epgText.text = "Sin EPG para este canal"
        } else {
            epgText.text = sb.toString()
        }
        epgText.visibility = View.VISIBLE
    }

    private fun fmtTime(epochSec: Long): String =
        try {
            java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date(epochSec * 1000))
        } catch (_: Exception) {
            ""
        }

    private fun switchChannel(delta: Int) {
        if (!isLive) {
            showOverlay()
            return
        }
        if (channelList.isEmpty()) {
            chanInfo.text = "Cargando canales…"
            showOverlay()
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
        chanInfo.text = "${idx + 1} / ${channelList.size}"
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
        loadEpg(s.id)
        showOverlay()
    }

    override fun onPlayerError(error: PlaybackException) {
        retrying = false
        retries++
        if (retries > 60) {
            chanInfo.text = "Sin señal. Usa los botones o cambia de canal para reintentar."
            showOverlay()
            return
        }
        val delay = if (retries <= 8) 3000L * retries else 30000L
        chanInfo.text = "Reintentando… ($retries)"
        showOverlay()
        uiHandler.removeCallbacks(retryRunnable)
        uiHandler.postDelayed(retryRunnable, delay.coerceAtMost(30000))
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_READY) {
            retries = 0
            if (chanInfo.text.startsWith("Reintentando") || chanInfo.text.startsWith("Sin señal")) {
                chanInfo.text = if (isLive && idx >= 0) "${idx + 1} / ${channelList.size}" else ""
            }
            showOverlay()
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
                chanInfo.text = "Señal lenta, reintentando…"
                showOverlay()
                forceRetry()
            }
        } else if (p.isPlaying) {
            if (pos == lastPosIn) {
                if (now - lastMoveAt > 10000 && isLive) {
                    chanInfo.text = "Señal congelada, reintentando…"
                    showOverlay()
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
        chanInfo.text = "Reintentando…"
        showOverlay()
        uiHandler.removeCallbacks(retryRunnable)
        uiHandler.postDelayed(retryRunnable, 1500)
    }

    private fun showOverlay() {
        bottomBar.visibility = View.VISIBLE
        streamTitle.visibility = View.VISIBLE
        if (!epgText.text.isNullOrBlank()) epgText.visibility = View.VISIBLE
        uiHandler.removeCallbacks(hideRunnable)
        uiHandler.postDelayed(hideRunnable, 6000)
    }

    private fun hideOverlays() {
        bottomBar.visibility = View.GONE
        streamTitle.animate().alpha(0f).setDuration(300).withEndAction { streamTitle.visibility = View.GONE }.start()
        epgText.visibility = View.GONE
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> {
                switchChannel(1); true
            }
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> {
                switchChannel(-1); true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onStart() {
        super.onStart()
        uiHandler.postDelayed(stallRunnable, 5000)
    }

    override fun onStop() {
        uiHandler.removeCallbacks(stallRunnable)
        super.onStop()
    }

    override fun onPause() {
        super.onPause()
        uiHandler.removeCallbacks(hideRunnable)
        uiHandler.removeCallbacks(retryRunnable)
        savePos()
        player?.playWhenReady = false
        playerView.onPause()
    }

    override fun onResume() {
        super.onResume()
        playerView.onResume()
    }

    override fun onDestroy() {
        uiHandler.removeCallbacks(hideRunnable)
        uiHandler.removeCallbacks(retryRunnable)
        uiHandler.removeCallbacks(stallRunnable)
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
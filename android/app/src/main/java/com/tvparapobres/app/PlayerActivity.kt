package com.tvparapobres.app

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource

class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var streamTitle: TextView
    private var histKey: String? = null
    private var startPos: Long = 0
    private var wakeLock: PowerManager.WakeLock? = null
    private val hideHandler = Handler(Looper.getMainLooper())

    private val hideTitle = Runnable {
        streamTitle.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction { streamTitle.visibility = View.GONE }
            .start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "tvpp:playback")
        wakeLock?.acquire(4 * 60 * 60 * 1000L)

        HistoryManager.init(applicationContext)

        val url = intent.getStringExtra("url")
        val title = intent.getStringExtra("title")
        histKey = intent.getStringExtra("histKey")
        startPos = intent.getLongExtra("startPosition", 0)
        playerView = findViewById(R.id.playerView)
        streamTitle = findViewById(R.id.streamTitle)
        streamTitle.text = title.orEmpty()
        streamTitle.alpha = 1f
        streamTitle.visibility = View.VISIBLE
        hideHandler.postDelayed(hideTitle, 2000)

        if (url.isNullOrBlank()) {
            finish()
            return
        }

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20000)
            .setReadTimeoutMs(60000)

        val mediaItem = MediaItem.fromUri(Uri.parse(url))
        val source = if (url.endsWith(".m3u8")) {
            HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        } else {
            ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        }

        player = ExoPlayer.Builder(this).build().also { p ->
            p.setMediaSource(source)
            p.prepare()
            p.playWhenReady = true
            if (startPos > 0) p.seekTo(startPos)
            playerView.player = p
        }
    }

    override fun onPause() {
        super.onPause()
        hideHandler.removeCallbacks(hideTitle)
        savePos()
        player?.playWhenReady = false
        playerView.onPause()
    }

    override fun onResume() {
        super.onResume()
        playerView.onResume()
    }

    override fun onDestroy() {
        hideHandler.removeCallbacks(hideTitle)
        savePos()
        super.onDestroy()
        player?.release()
        player = null
        try { wakeLock?.release() } catch (_: Exception) {}
        wakeLock = null
    }

    private fun savePos() {
        val p = player ?: return
        val key = histKey ?: return
        val pos = p.currentPosition.coerceAtLeast(0)
        val dur = p.duration.coerceAtLeast(0)
        if (pos > 0 && dur > 0) {
            HistoryManager.updatePosition(key, pos, dur)
        }
    }
}
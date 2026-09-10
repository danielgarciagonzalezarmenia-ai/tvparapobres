package com.tvparapobres.app

import android.net.Uri
import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        val url = intent.getStringExtra("url")
        val title = intent.getStringExtra("title")
        playerView = findViewById(R.id.playerView)
        findViewById<TextView>(R.id.streamTitle).text = title.orEmpty()

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
            playerView.player = p
        }
    }

    override fun onPause() {
        super.onPause()
        player?.playWhenReady = false
        playerView.onPause()
    }

    override fun onResume() {
        super.onResume()
        playerView.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}
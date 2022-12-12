package com.demons.player

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.demons.player.BasePlayerView.Companion.SCREEN_NORMAL

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        supportActionBar?.setDisplayUseLogoEnabled(false)
        setContentView(R.layout.activity_main)
        val playerView = findViewById<PlayerView>(R.id.video_player)
        playerView?.setUp(
            "http://vfx.mtime.cn/Video/2019/03/19/mp4/190319222227698228.mp4",
            "测试",
            SCREEN_NORMAL,
            IjkPlayerMedia::class.java
        )
        playerView?.startVideo()
    }

    override fun onBackPressed() {
        if (BasePlayerView.backPress()) {
            return
        }
        super.onBackPressed()
    }

    override fun onPause() {
        super.onPause()
        BasePlayerView.goOnPlayOnPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        BasePlayerView.releaseAllVideos()
    }
}
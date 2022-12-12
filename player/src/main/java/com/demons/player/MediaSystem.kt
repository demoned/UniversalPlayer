package com.demons.player

import android.graphics.SurfaceTexture
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Surface

/**
 * 实现系统的播放引擎
 */
class MediaSystem(basePlayerView: BasePlayerView?) : MediaInterface(basePlayerView),
    MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener,
    MediaPlayer.OnBufferingUpdateListener, MediaPlayer.OnSeekCompleteListener,
    MediaPlayer.OnErrorListener, MediaPlayer.OnInfoListener,
    MediaPlayer.OnVideoSizeChangedListener {
    var mediaPlayer: MediaPlayer? = null
    override fun prepare() {
        release()
        mMediaHandlerThread = HandlerThread("media_system")
        mMediaHandlerThread?.start()
        mMediaHandler = mMediaHandlerThread?.looper?.let { Handler(it) } //主线程还是非主线程，就在这里
        handler = Looper.myLooper()?.let { Handler(it) }
        mMediaHandler?.post {
            try {
                mediaPlayer = MediaPlayer()
                mediaPlayer?.setAudioStreamType(AudioManager.STREAM_MUSIC)
                mediaPlayer?.isLooping = basePlayerView?.dataSource?.looping == true
                mediaPlayer?.setOnPreparedListener(this@MediaSystem)
                mediaPlayer?.setOnCompletionListener(this@MediaSystem)
                mediaPlayer?.setOnBufferingUpdateListener(this@MediaSystem)
                mediaPlayer?.setScreenOnWhilePlaying(true)
                mediaPlayer?.setOnSeekCompleteListener(this@MediaSystem)
                mediaPlayer?.setOnErrorListener(this@MediaSystem)
                mediaPlayer?.setOnInfoListener(this@MediaSystem)
                mediaPlayer?.setOnVideoSizeChangedListener(this@MediaSystem)
                val method = MediaPlayer::class.java.getDeclaredMethod("setDataSource", String::class.java, MutableMap::class.java)
                method.invoke(
                    mediaPlayer,
                    basePlayerView?.dataSource?.currentUrl.toString(),
                    basePlayerView?.dataSource?.headerMap
                )
                mediaPlayer?.prepareAsync()
                mediaPlayer?.setSurface(Surface(surfaceTexture))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun start() {
        mMediaHandler?.post { mediaPlayer?.start() }
    }

    override fun pause() {
        mMediaHandler?.post { mediaPlayer?.pause() }
    }

    override val isPlaying: Boolean
        get() = mediaPlayer?.isPlaying == true

    override fun seekTo(time: Long) {
        mMediaHandler?.post {
            try {
                mediaPlayer?.seekTo(time.toInt())
            } catch (e: IllegalStateException) {
                e.printStackTrace()
            }
        }
    }

    override fun release() {
        if (mMediaHandler != null && mMediaHandlerThread != null && mediaPlayer != null) {
            val tmpMediaPlayer: MediaPlayer = mediaPlayer as MediaPlayer
            surfaceTexture = null
            mMediaHandler?.post {
                tmpMediaPlayer.setSurface(null)
                tmpMediaPlayer.release()
                mMediaHandlerThread?.quit()
            }
            mediaPlayer = null
        }
    }
    
    override val currentPosition: Long = mediaPlayer?.currentPosition?.toLong()?:0

    override val duration: Long = mediaPlayer?.duration?.toLong()?:0

    override fun setVolume(leftVolume: Float, rightVolume: Float) {
        if (mMediaHandler == null) return
        mMediaHandler?.post {
            if (mediaPlayer != null) mediaPlayer?.setVolume(
                leftVolume,
                rightVolume
            )
        }
    }

    override fun setSpeed(speed: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val playbackParams = mediaPlayer?.playbackParams
            if (playbackParams != null) {
                playbackParams.speed = speed
                mediaPlayer?.playbackParams = playbackParams
            }
        }
    }

    override fun onPrepared(mediaPlayer: MediaPlayer) {
        handler?.post { basePlayerView?.onPrepared() } //如果是mp3音频，走这里
    }

    override fun onCompletion(mediaPlayer: MediaPlayer) {
        handler?.post { basePlayerView?.onCompletion() }
    }

    override fun onBufferingUpdate(mediaPlayer: MediaPlayer, percent: Int) {
        handler?.post { basePlayerView?.setBufferProgress(percent) }
    }

    override fun onSeekComplete(mediaPlayer: MediaPlayer) {
        handler?.post { basePlayerView?.onSeekComplete() }
    }

    override fun onError(mediaPlayer: MediaPlayer, what: Int, extra: Int): Boolean {
        handler?.post { basePlayerView?.onError(what, extra) }
        return true
    }

    override fun onInfo(mediaPlayer: MediaPlayer, what: Int, extra: Int): Boolean {
        handler?.post { basePlayerView?.onInfo(what) }
        return false
    }

    override fun onVideoSizeChanged(mediaPlayer: MediaPlayer, width: Int, height: Int) {
        handler?.post { basePlayerView?.onVideoSizeChanged(width, height) }
    }

    override fun setSurface(surface: Surface?) {
        mediaPlayer?.setSurface(surface)
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        if (surfaceTexture == null) {
            surfaceTexture = surface
            prepare()
        } else {
            basePlayerView?.textureView?.setSurfaceTexture(surfaceTexture!!)
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        return false
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
}
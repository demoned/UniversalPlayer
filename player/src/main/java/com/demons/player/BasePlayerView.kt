package com.demons.player

import android.content.*
import android.content.pm.ActivityInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import android.media.MediaPlayer
import android.provider.Settings
import android.provider.Settings.SettingNotFoundException
import android.util.AttributeSet
import android.view.*
import android.view.View.OnTouchListener
import android.widget.*
import android.widget.SeekBar.OnSeekBarChangeListener
import java.lang.reflect.Constructor
import java.util.*
import kotlin.math.abs

abstract class BasePlayerView : FrameLayout, View.OnClickListener, OnSeekBarChangeListener,
    OnTouchListener {
    var code = -1
    var screenConfig = -1
    var dataSource: DataSource? = null
    var mediaInterface: MediaInterface? = null
    var seekToInAdvance: Long = 0
    var startButton: ImageView? = null
    private var progressBar: SeekBar? = null
    var fullscreenButton: ImageView? = null
    var textureViewContainer: ViewGroup? = null
    var topContainer: ViewGroup? = null
    var bottomContainer: ViewGroup? = null
    var textureView: TextureView? = null
    private var widthRatio = 0
    private var heightRatio = 0
    private var mediaInterfaceClass: Class<*>? = null
    private var videoRotation = 0
    private var seekToPosition = -1
    private var currentTimeTextView: TextView? = null
    private var totalTimeTextView: TextView? = null
    private var preloading = false
    protected var goBackFullscreenTime: Long = 0
    protected var gotoFullscreenTime: Long = 0
    private var timer: Timer? = null
    private var mScreenWidth = 0
    private var mScreenHeight = 0
    private var mAudioManager: AudioManager? = null
    private var mProgressTimerTask: ProgressTimerTask? = null
    private var mTouchingProgressBar = false
    private var mDownX = 0f
    private var mDownY = 0f
    protected var mChangeVolume = false
    protected var mChangePosition = false
    private var mChangeBrightness = false
    private var mGestureDownPosition: Long = 0
    private var mGestureDownVolume = 0
    private var mGestureDownBrightness = 0f
    protected var mSeekTimePosition: Long = 0
    protected var mContext: Context? = null
    protected var mCurrentPosition: Long = 0
    private var blockLayoutParams: ViewGroup.LayoutParams? = null
    private var blockIndex = 0
    private var blockWidth = 0
    private var blockHeight = 0

    constructor(context: Context?) : super(context!!) {
        init(context)
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(
        context!!, attrs
    ) {
        init(context)
    }

    abstract val layoutId: Int

    open fun init(context: Context?) {
        inflate(context, layoutId, this)
        this.mContext = context
        startButton = findViewById(R.id.start)
        fullscreenButton = findViewById(R.id.fullscreen)
        progressBar = findViewById(R.id.bottom_seek_progress)
        currentTimeTextView = findViewById(R.id.current)
        totalTimeTextView = findViewById(R.id.total)
        bottomContainer = findViewById(R.id.layout_bottom)
        textureViewContainer = findViewById(R.id.surface_container)
        topContainer = findViewById(R.id.layout_top)
        if (startButton == null) {
            startButton = ImageView(context)
        }
        if (fullscreenButton == null) {
            fullscreenButton = ImageView(context)
        }
        if (progressBar == null) {
            progressBar = SeekBar(context)
        }
        if (currentTimeTextView == null) {
            currentTimeTextView = TextView(context)
        }
        if (totalTimeTextView == null) {
            totalTimeTextView = TextView(context)
        }
        if (bottomContainer == null) {
            bottomContainer = LinearLayout(context)
        }
        if (textureViewContainer == null) {
            textureViewContainer = FrameLayout(context!!)
        }
        if (topContainer == null) {
            topContainer = RelativeLayout(context)
        }
        startButton!!.setOnClickListener(this)
        fullscreenButton!!.setOnClickListener(this)
        progressBar!!.setOnSeekBarChangeListener(this)
        bottomContainer!!.setOnClickListener(this)
        textureViewContainer!!.setOnClickListener(this)
        textureViewContainer!!.setOnTouchListener(this)
        mScreenWidth = getContext().resources.displayMetrics.widthPixels
        mScreenHeight = getContext().resources.displayMetrics.heightPixels
        code = STATE_IDLE
    }

    fun setUp(url: String?, title: String?) {
        setUp(url?.let { DataSource(it, title) }, SCREEN_NORMAL)
    }

    fun setUp(url: String?, title: String?, screen: Int) {
        setUp(url?.let { DataSource(it, title) }, screen)
    }

    fun setUp(dataSource: DataSource?, screen: Int) {
        setUp(dataSource, screen, MediaSystem::class.java)
    }

    fun setUp(url: String?, title: String?, screen: Int, mediaInterfaceClass: Class<*>?) {
        setUp(url?.let { DataSource(it, title) }, screen, mediaInterfaceClass)
    }

    open fun setUp(dataSource: DataSource?, screen: Int, mediaInterfaceClass: Class<*>?) {
        this.dataSource = dataSource
        this.screenConfig = screen
        onStateNormal()
        this.mediaInterfaceClass = mediaInterfaceClass
    }

    fun setMediaInterface(mediaInterfaceClass: Class<*>?) {
        reset()
        this.mediaInterfaceClass = mediaInterfaceClass
    }

    override fun onClick(v: View) {
        val i = v.id
        if (i == R.id.start) {
            clickStart()
        } else if (i == R.id.fullscreen) {
            clickFullscreen()
        }
    }

    private fun clickFullscreen() {
        if (code == STATE_AUTO_COMPLETE) return
        if (screenConfig == SCREEN_FULLSCREEN) {
            backPress()
        } else {
            gotoFullscreen()
        }
    }

    private fun clickStart() {
        if (dataSource == null || dataSource!!.urlsMap.isEmpty() || dataSource?.currentUrl == null) {
            Toast.makeText(context, resources.getString(R.string.no_url), Toast.LENGTH_SHORT)
                .show()
            return
        }
        when (code) {
            STATE_NORMAL -> {
                if (!dataSource?.currentUrl.toString()
                        .startsWith("file") && !dataSource?.currentUrl.toString().startsWith("/") &&
                    !Utils.isWifiConnected(context) && !WIFI_TIP_DIALOG_SHOWED
                ) { //这个可以放到std中
                    showWifiDialog()
                    return
                }
                startVideo()
            }
            STATE_PLAYING -> {
                mediaInterface!!.pause()
                onStatePause()
            }
            STATE_PAUSE -> {
                mediaInterface!!.start()
                onStatePlaying()
            }
            STATE_AUTO_COMPLETE -> {
                startVideo()
            }
        }
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        val id = v.id
        if (id == R.id.surface_container) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> touchActionDown(x, y)
                MotionEvent.ACTION_MOVE -> touchActionMove(x, y)
                MotionEvent.ACTION_UP -> touchActionUp()
            }
        }
        return false
    }

    private fun touchActionUp() {
        mTouchingProgressBar = false
        dismissProgressDialog()
        dismissVolumeDialog()
        dismissBrightnessDialog()
        if (mChangePosition) {
            mediaInterface!!.seekTo(mSeekTimePosition)
            val duration = duration
            val progress = (mSeekTimePosition * 100 / if (duration == 0L) 1 else duration).toInt()
            progressBar!!.progress = progress
        }
        if (mChangeVolume) {
            //change volume event
        }
        startProgressTimer()
    }

    private fun touchActionMove(x: Float, y: Float) {
        val deltaX = x - mDownX
        var deltaY = y - mDownY
        val absDeltaX = abs(deltaX)
        val absDeltaY = abs(deltaY)
        if (screenConfig == SCREEN_FULLSCREEN) {
            //拖动的是NavigationBar和状态栏
            if (mDownX > Utils.getScreenWidth(context) || mDownY < Utils.getStatusBarHeight(context)) {
                return
            }
            if (!mChangePosition && !mChangeVolume && !mChangeBrightness) {
                if (absDeltaX > THRESHOLD || absDeltaY > THRESHOLD) {
                    cancelProgressTimer()
                    if (absDeltaX >= THRESHOLD) {
                        // 全屏模式下的CURRENT_STATE_ERROR状态下,不响应进度拖动事件.
                        // 否则会因为mediaplayer的状态非法导致App Crash
                        if (code != STATE_ERROR) {
                            mChangePosition = true
                            mGestureDownPosition = currentPositionWhenPlaying
                        }
                    } else {
                        //如果y轴滑动距离超过设置的处理范围，那么进行滑动事件处理
                        if (mDownX < mScreenHeight * 0.5f) { //左侧改变亮度
                            mChangeBrightness = true
                            val lp = Utils.getWindow(context).attributes
                            if (lp.screenBrightness < 0) {
                                try {
                                    mGestureDownBrightness = Settings.System.getInt(
                                        context.contentResolver,
                                        Settings.System.SCREEN_BRIGHTNESS
                                    ).toFloat()
                                } catch (e: SettingNotFoundException) {
                                    e.printStackTrace()
                                }
                            } else {
                                mGestureDownBrightness = lp.screenBrightness * 255
                            }
                        } else { //右侧改变声音
                            mChangeVolume = true
                            mGestureDownVolume =
                                mAudioManager!!.getStreamVolume(AudioManager.STREAM_MUSIC)
                        }
                    }
                }
            }
        }
        if (mChangePosition) {
            val totalTimeDuration = duration
            if (PROGRESS_DRAG_RATE <= 0) {
                PROGRESS_DRAG_RATE = 1f
            }
            mSeekTimePosition =
                (mGestureDownPosition + deltaX * totalTimeDuration / (mScreenWidth * PROGRESS_DRAG_RATE)).toInt()
                    .toLong()
            if (mSeekTimePosition > totalTimeDuration) mSeekTimePosition = totalTimeDuration
            val seekTime = Utils.stringForTime(mSeekTimePosition)
            val totalTime = Utils.stringForTime(totalTimeDuration)
            showProgressDialog(deltaX, seekTime, mSeekTimePosition, totalTime, totalTimeDuration)
        }
        if (mChangeVolume) {
            deltaY = -deltaY
            val max = mAudioManager!!.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val deltaV = (max * deltaY * 3 / mScreenHeight).toInt()
            mAudioManager!!.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                mGestureDownVolume + deltaV,
                0
            )
            //dialog中显示百分比
            val volumePercent =
                (mGestureDownVolume * 100 / max + deltaY * 3 * 100 / mScreenHeight).toInt()
            showVolumeDialog(-deltaY, volumePercent)
        }
        if (mChangeBrightness) {
            deltaY = -deltaY
            val deltaV = (255 * deltaY * 3 / mScreenHeight).toInt()
            val params = Utils.getWindow(context).attributes
            if ((mGestureDownBrightness + deltaV) / 255 >= 1) { //这和声音有区别，必须自己过滤一下负值
                params.screenBrightness = 1f
            } else if ((mGestureDownBrightness + deltaV) / 255 <= 0) {
                params.screenBrightness = 0.01f
            } else {
                params.screenBrightness = (mGestureDownBrightness + deltaV) / 255
            }
            Utils.getWindow(context).attributes = params
            //dialog中显示百分比
            val brightnessPercent =
                (mGestureDownBrightness * 100 / 255 + deltaY * 3 * 100 / mScreenHeight).toInt()
            showBrightnessDialog(brightnessPercent)
            //                        mDownY = y;
        }
    }

    private fun touchActionDown(x: Float, y: Float) {
        mTouchingProgressBar = true
        mDownX = x
        mDownY = y
        mChangeVolume = false
        mChangePosition = false
        mChangeBrightness = false
    }

    open fun onStateNormal() {
        code = STATE_NORMAL
        cancelProgressTimer()
        if (mediaInterface != null) mediaInterface!!.release()
    }

    open fun onStatePreparing() {
        code = STATE_PREPARING
        resetProgressAndTime()
    }

    open fun onStatePreparingPlaying() {
        code = STATE_PREPARING_PLAYING
    }

    open fun onStatePreparingChangeUrl() {
        code = STATE_PREPARING_CHANGE_URL
        releaseAllVideos()
        startVideo()
        //        mediaInterface.prepare();
    }

    open fun changeUrl(dataSource: DataSource, seekToInAdvance: Long) {
        this.dataSource = dataSource
        this.seekToInAdvance = seekToInAdvance
        onStatePreparingChangeUrl()
    }

    fun onPrepared() {
        code = STATE_PREPARED
        if (!preloading) {
            mediaInterface!!.start() //这里原来是非县城
            preloading = false
        }
        if (dataSource?.currentUrl.toString().lowercase(Locale.getDefault()).contains("mp3") ||
            dataSource?.currentUrl.toString().lowercase(Locale.getDefault()).contains("wma") ||
            dataSource?.currentUrl.toString().lowercase(Locale.getDefault()).contains("aac") ||
            dataSource?.currentUrl.toString().lowercase(Locale.getDefault()).contains("m4a") ||
            dataSource?.currentUrl.toString().lowercase(Locale.getDefault()).contains("wav")
        ) {
            onStatePlaying()
        }
    }

    fun startPreloading() {
        preloading = true
        startVideo()
    }

    /**
     * 如果STATE_PREPARED就播放，如果没准备完成就走正常的播放函数startVideo();
     */
    fun startVideoAfterPreloading() {
        if (code == STATE_PREPARED) {
            mediaInterface!!.start()
        } else {
            preloading = false
            startVideo()
        }
    }

    open fun onStatePlaying() {
        if (code == STATE_PREPARED) { //如果是准备完成视频后第一次播放，先判断是否需要跳转进度。
            mAudioManager =
                applicationContext!!.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            mAudioManager!!.requestAudioFocus(
                onAudioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
            if (seekToInAdvance != 0L) {
                mediaInterface!!.seekTo(seekToInAdvance)
                seekToInAdvance = 0
            } else {
                val position = Utils.getSavedProgress(context, dataSource?.currentUrl)
                if (position != 0L) {
                    mediaInterface!!.seekTo(position) //这里为什么区分开呢，第一次的播放和resume播放是不一样的。 这里怎么区分是一个问题。然后
                }
            }
        }
        code = STATE_PLAYING
        startProgressTimer()
    }

    open fun onStatePause() {
        code = STATE_PAUSE
        startProgressTimer()
    }

    open fun onStateError() {
        code = STATE_ERROR
        cancelProgressTimer()
    }

    open fun onStateAutoComplete() {
        code = STATE_AUTO_COMPLETE
        cancelProgressTimer()
        progressBar!!.progress = 100
        currentTimeTextView!!.text = totalTimeTextView!!.text
    }

    fun onInfo(what: Int) {
        if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
            if (code == STATE_PREPARED || code == STATE_PREPARING_CHANGE_URL || code == STATE_PREPARING_PLAYING) {
                onStatePlaying() //开始渲染图像，真正进入playing状态
            }
        } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
            backUpBufferState = code
            setState(STATE_PREPARING_PLAYING)
        } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
            if (backUpBufferState != -1) {
                setState(backUpBufferState)
                backUpBufferState = -1
            }
        }
    }

    fun onError(what: Int, extra: Int) {
        if (what != 38 && extra != -38 && what != -38 && extra != 38 && extra != -19) {
            onStateError()
            mediaInterface!!.release()
        }
    }

    open fun onCompletion() {
        Runtime.getRuntime().gc()
        cancelProgressTimer()
        dismissBrightnessDialog()
        dismissProgressDialog()
        dismissVolumeDialog()
        onStateAutoComplete()
        mediaInterface!!.release()
        Utils.scanForActivity(context)!!.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Utils.saveProgress(context, dataSource?.currentUrl, 0)
        if (screenConfig == SCREEN_FULLSCREEN) {
            if (CONTAINER_LIST.size == 0) {
                clearFloatScreen() //直接进入全屏
            } else {
                gotoNormalCompletion()
            }
        }
    }

    private fun gotoNormalCompletion() {
        goBackFullscreenTime = System.currentTimeMillis() //退出全屏
        val vg = Utils.scanForActivity(mContext)!!.window.decorView as ViewGroup
        vg.removeView(this)
        textureViewContainer!!.removeView(textureView)
        CONTAINER_LIST.last.removeViewAt(blockIndex) //remove block
        CONTAINER_LIST.last.addView(this, blockIndex, blockLayoutParams)
        CONTAINER_LIST.pop()
        setScreenNormal()
        Utils.showStatusBar(mContext)
        Utils.setRequestedOrientation(mContext, NORMAL_ORIENTATION)
        Utils.showSystemUI(mContext)
    }

    /**
     * 多数表现为中断当前播放
     */
    open fun reset() {
        if (code == STATE_PLAYING || code == STATE_PAUSE) {
            val position = currentPositionWhenPlaying
            Utils.saveProgress(context, dataSource?.currentUrl, position)
        }
        cancelProgressTimer()
        dismissBrightnessDialog()
        dismissProgressDialog()
        dismissVolumeDialog()
        onStateNormal()
        textureViewContainer!!.removeAllViews()
        val mAudioManager =
            applicationContext!!.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        mAudioManager.abandonAudioFocus(onAudioFocusChangeListener)
        Utils.scanForActivity(context)!!.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (mediaInterface != null) mediaInterface!!.release()
    }

    /**
     * 里面的的onState...()其实就是setState...()，因为要可以被复写，所以参考Activity的onCreate(),onState..()的方式看着舒服一些，老铁们有何高见。
     *
     * @param state stateId
     */
    private fun setState(state: Int) {
        when (state) {
            STATE_NORMAL -> onStateNormal()
            STATE_PREPARING -> onStatePreparing()
            STATE_PREPARING_PLAYING -> onStatePreparingPlaying()
            STATE_PREPARING_CHANGE_URL -> onStatePreparingChangeUrl()
            STATE_PLAYING -> onStatePlaying()
            STATE_PAUSE -> onStatePause()
            STATE_ERROR -> onStateError()
            STATE_AUTO_COMPLETE -> onStateAutoComplete()
        }
    }

    fun setScreen(screen: Int) { //特殊的个别的进入全屏的按钮在这里设置  只有setup的时候能用上
        when (screen) {
            SCREEN_NORMAL -> setScreenNormal()
            SCREEN_FULLSCREEN -> setScreenFullscreen()
            SCREEN_TINY -> setScreenTiny()
        }
    }

    open fun startVideo() {
        setCurrent(this)
        try {
            val constructor: Constructor<Any> = mediaInterfaceClass?.getConstructor(
                BasePlayerView::class.java
            ) as Constructor<Any>
            mediaInterface = constructor.newInstance(this) as MediaInterface
        } catch (e: Exception) {
            e.printStackTrace()
        }
        addTextureView()
        Utils.scanForActivity(context)?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onStatePreparing()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (screenConfig == SCREEN_FULLSCREEN || screenConfig == SCREEN_TINY) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        if (widthRatio != 0 && heightRatio != 0) {
            val specWidth = MeasureSpec.getSize(widthMeasureSpec)
            val specHeight = (specWidth * heightRatio.toFloat() / widthRatio).toInt()
            setMeasuredDimension(specWidth, specHeight)
            val childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(specWidth, MeasureSpec.EXACTLY)
            val childHeightMeasureSpec =
                MeasureSpec.makeMeasureSpec(specHeight, MeasureSpec.EXACTLY)
            getChildAt(0).measure(childWidthMeasureSpec, childHeightMeasureSpec)
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    private fun addTextureView() {
        if (textureView != null) textureViewContainer!!.removeView(textureView)
        textureView = TextureView(context.applicationContext)
        textureView!!.surfaceTextureListener = mediaInterface
        val layoutParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            Gravity.CENTER
        )
        textureViewContainer!!.addView(textureView, layoutParams)
    }

    fun clearFloatScreen() {
        Utils.showStatusBar(context)
        Utils.setRequestedOrientation(context, NORMAL_ORIENTATION)
        Utils.showSystemUI(context)
        val vg = Utils.scanForActivity(context)!!.window.decorView as ViewGroup
        vg.removeView(this)
        if (mediaInterface != null) mediaInterface!!.release()
        currentPlayerView = null
    }

    fun onVideoSizeChanged(width: Int, height: Int) {
        if (textureView != null) {
            if (videoRotation != 0) {
                textureView!!.rotation = videoRotation.toFloat()
            }
            textureView!!.setVideoSize(width, height)
        }
    }

    private fun startProgressTimer() {
        cancelProgressTimer()
        timer = Timer()
        mProgressTimerTask = ProgressTimerTask()
        timer!!.schedule(mProgressTimerTask, 0, 300)
    }

    private fun cancelProgressTimer() {
        if (timer != null) {
            timer!!.cancel()
        }
        if (mProgressTimerTask != null) {
            mProgressTimerTask!!.cancel()
        }
    }

    open fun onProgress(progress: Int, position: Long, duration: Long) {
        mCurrentPosition = position
        if (!mTouchingProgressBar) {
            if (seekToPosition != -1) {
                seekToPosition = if (seekToPosition > progress) {
                    return
                } else {
                    -1 //这个关键帧有没有必要做
                }
            } else {
                progressBar!!.progress = progress
            }
        }
        if (position != 0L) currentTimeTextView!!.text = Utils.stringForTime(position)
        totalTimeTextView!!.text = Utils.stringForTime(duration)
    }

    open fun setBufferProgress(bufferProgress: Int) {
        progressBar!!.secondaryProgress = bufferProgress
    }

    open fun resetProgressAndTime() {
        mCurrentPosition = 0
        progressBar!!.progress = 0
        progressBar!!.secondaryProgress = 0
        currentTimeTextView!!.text = Utils.stringForTime(0)
        totalTimeTextView!!.text = Utils.stringForTime(0)
    }

    val currentPositionWhenPlaying: Long
        get() {
            var position: Long = 0
            if (code == STATE_PLAYING || code == STATE_PAUSE || code == STATE_PREPARING_PLAYING) {
                position = mediaInterface?.currentPosition!!
            }
            return position
        }
    val duration: Long
        get() {
            return mediaInterface?.duration!!
        }

    override fun onStartTrackingTouch(seekBar: SeekBar) {
        cancelProgressTimer()
        var vpdown = parent
        while (vpdown != null) {
            vpdown.requestDisallowInterceptTouchEvent(true)
            vpdown = vpdown.parent
        }
    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {
        startProgressTimer()
        var vpup = parent
        while (vpup != null) {
            vpup.requestDisallowInterceptTouchEvent(false)
            vpup = vpup.parent
        }
        if (code != STATE_PLAYING &&
            code != STATE_PAUSE
        ) return
        val time = seekBar.progress * duration / 100
        seekToPosition = seekBar.progress
        mediaInterface!!.seekTo(time)
    }

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        if (fromUser) {
            //设置这个progres对应的时间，给textview
            val duration = duration
            currentTimeTextView!!.text = Utils.stringForTime(progress * duration / 100)
        }
    }

    private fun clone(vg: ViewGroup) {
        try {
            val constructor = this@BasePlayerView.javaClass.getConstructor(
                Context::class.java
            ) as Constructor<BasePlayerView>
            val basePlayerView = constructor.newInstance(context)
            basePlayerView.id = id
            basePlayerView.minimumWidth = blockWidth
            basePlayerView.minimumHeight = blockHeight
            vg.addView(basePlayerView, blockIndex, blockLayoutParams)
            basePlayerView.setUp(dataSource!!.cloneMe(), SCREEN_NORMAL, mediaInterfaceClass)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 如果全屏或者返回全屏的视图有问题，复写这两个函数gotoScreenNormal(),根据自己布局的情况重新布局。
     */
    private fun gotoFullscreen() {
        gotoFullscreenTime = System.currentTimeMillis()
        var vg = parent as ViewGroup
        mContext = vg.context
        blockLayoutParams = layoutParams
        blockIndex = vg.indexOfChild(this)
        blockWidth = width
        blockHeight = height
        vg.removeView(this)
        clone(vg)
        CONTAINER_LIST.add(vg)
        vg = Utils.scanForActivity(mContext)!!.window.decorView as ViewGroup
        val fullLayout: ViewGroup.LayoutParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        vg.addView(this, fullLayout)
        setScreenFullscreen()
        Utils.hideStatusBar(mContext)
        Utils.setRequestedOrientation(mContext, FULLSCREEN_ORIENTATION)
        Utils.hideSystemUI(mContext) //华为手机和有虚拟键的手机全屏时可隐藏虚拟键 issue:1326
    }

    fun gotoNormalScreen() { //goback本质上是goto
        goBackFullscreenTime = System.currentTimeMillis() //退出全屏
        val vg = Utils.scanForActivity(mContext)!!.window.decorView as ViewGroup
        vg.removeView(this)
        CONTAINER_LIST.last.removeViewAt(blockIndex) //remove block
        CONTAINER_LIST.last.addView(this, blockIndex, blockLayoutParams)
        CONTAINER_LIST.pop()
        setScreenNormal()
        Utils.showStatusBar(mContext)
        Utils.setRequestedOrientation(mContext, NORMAL_ORIENTATION)
        Utils.showSystemUI(mContext)
    }

    open fun setScreenNormal() {
        screenConfig = SCREEN_NORMAL
    }

    open fun setScreenFullscreen() {
        screenConfig = SCREEN_FULLSCREEN
    }

    open fun setScreenTiny() {
        screenConfig = SCREEN_TINY
    }

    //    //重力感应的时候调用的函数，、、这里有重力感应的参数，暂时不能删除
    fun autoFullscreen(x: Float) { //TODO写道demo中
        if (currentPlayerView != null && (code == STATE_PLAYING || code == STATE_PAUSE)
            && screenConfig != SCREEN_FULLSCREEN && screenConfig != SCREEN_TINY
        ) {
            if (x > 0) {
                Utils.setRequestedOrientation(
                    context,
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                )
            } else {
                Utils.setRequestedOrientation(
                    context,
                    ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                )
            }
            gotoFullscreen()
        }
    }

    fun autoQuitFullscreen() {
        if (System.currentTimeMillis() - lastAutoFullscreenTime > 2000 && code == STATE_PLAYING && screenConfig == SCREEN_FULLSCREEN) {
            lastAutoFullscreenTime = System.currentTimeMillis()
            backPress()
        }
    }

    fun onSeekComplete() {}
    open fun showWifiDialog() {}
    open fun showProgressDialog(
        deltaX: Float,
        seekTime: String?, seekTimePosition: Long,
        totalTime: String?, totalTimeDuration: Long
    ) {
    }

    open fun dismissProgressDialog() {}
    open fun showVolumeDialog(deltaY: Float, volumePercent: Int) {}
    open fun dismissVolumeDialog() {}
    open fun showBrightnessDialog(brightnessPercent: Int) {}
    open fun dismissBrightnessDialog() {}

    //这个函数必要吗
    val applicationContext: Context?
        get() { //这个函数必要吗
            val context = context
            if (context != null) {
                val applicationContext = context.applicationContext
                if (applicationContext != null) {
                    return applicationContext
                }
            }
            return context
        }

    class AutoFullscreenListener : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) { //可以得到传感器实时测量出来的变化值
            val x = event.values[SensorManager.DATA_X]
            val y = event.values[SensorManager.DATA_Y]
            val z = event.values[SensorManager.DATA_Z]
            //过滤掉用力过猛会有一个反向的大数值
            if (x < -12 || x > 12) {
                if (System.currentTimeMillis() - lastAutoFullscreenTime > 2000) {
                    if (currentPlayerView != null) currentPlayerView!!.autoFullscreen(x)
                    lastAutoFullscreenTime = System.currentTimeMillis()
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    inner class ProgressTimerTask : TimerTask() {
        override fun run() {
            if (code == STATE_PLAYING || code == STATE_PAUSE || code == STATE_PREPARING_PLAYING) {
                post {
                    val position = currentPositionWhenPlaying
                    val duration = duration
                    val progress = (position * 100 / if (duration == 0L) 1 else duration).toInt()
                    onProgress(progress, position, duration)
                }
            }
        }
    }

    companion object {
        const val SCREEN_NORMAL = 0
        const val SCREEN_FULLSCREEN = 1
        const val SCREEN_TINY = 2
        const val STATE_IDLE = -1
        const val STATE_NORMAL = 0
        const val STATE_PREPARING = 1
        const val STATE_PREPARING_CHANGE_URL = 2
        const val STATE_PREPARING_PLAYING = 3
        const val STATE_PREPARED = 4
        const val STATE_PLAYING = 5
        const val STATE_PAUSE = 6
        const val STATE_AUTO_COMPLETE = 7
        const val STATE_ERROR = 8
        const val VIDEO_IMAGE_DISPLAY_TYPE_ADAPTER = 0
        const val VIDEO_IMAGE_DISPLAY_TYPE_FILL_PARENT = 1
        const val VIDEO_IMAGE_DISPLAY_TYPE_FILL_CROP = 2
        const val VIDEO_IMAGE_DISPLAY_TYPE_ORIGINAL = 3
        const val THRESHOLD = 80
        var currentPlayerView: BasePlayerView? = null
        var CONTAINER_LIST = LinkedList<ViewGroup>()
        var TOOL_BAR_EXIST = true
        var FULLSCREEN_ORIENTATION = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        var NORMAL_ORIENTATION = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        var SAVE_PROGRESS = true
        var WIFI_TIP_DIALOG_SHOWED = false
        var VIDEO_IMAGE_DISPLAY_TYPE = 0
        var lastAutoFullscreenTime: Long = 0
        private var ON_PLAY_PAUSE_TMP_STATE = 0 //这个考虑不放到库里，去自定义
        var backUpBufferState = -1
        var PROGRESS_DRAG_RATE = 1f //进度条滑动阻尼系数 越大播放进度条滑动越慢
        var onAudioFocusChangeListener = OnAudioFocusChangeListener { focusChange ->

            //是否新建个class，代码更规矩，并且变量的位置也很尴尬
            when (focusChange) {
                AudioManager.AUDIOFOCUS_GAIN -> {}
                AudioManager.AUDIOFOCUS_LOSS -> releaseAllVideos()
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> try {
                    val player = currentPlayerView
                    if (player != null && player.code == STATE_PLAYING) {
                        player.startButton!!.performClick()
                    }
                } catch (e: IllegalStateException) {
                    e.printStackTrace()
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {}
            }
        }

        /**
         * 增加准备状态逻辑
         */
        fun goOnPlayOnResume() {
            if (currentPlayerView != null) {
                if (currentPlayerView!!.code == STATE_PAUSE) {
                    if (ON_PLAY_PAUSE_TMP_STATE == STATE_PAUSE) {
                        currentPlayerView!!.onStatePause()
                        currentPlayerView!!.mediaInterface!!.pause()
                    } else {
                        currentPlayerView!!.onStatePlaying()
                        currentPlayerView!!.mediaInterface!!.start()
                    }
                    ON_PLAY_PAUSE_TMP_STATE = 0
                } else if (currentPlayerView!!.code == STATE_PREPARING) {
                    //准备状态暂停后的
                    currentPlayerView!!.startVideo()
                }
                if (currentPlayerView!!.screenConfig == SCREEN_FULLSCREEN) {
                    Utils.hideStatusBar(currentPlayerView!!.mContext)
                    Utils.hideSystemUI(currentPlayerView!!.mContext)
                }
            }
        }

        /**
         * 增加准备状态逻辑
         */
        fun goOnPlayOnPause() {
            if (currentPlayerView != null) {
                when (currentPlayerView!!.code) {
                    STATE_AUTO_COMPLETE, STATE_NORMAL, STATE_ERROR -> {
                        releaseAllVideos()
                    }
                    STATE_PREPARING -> {
                        //准备状态暂停的逻辑
                        setCurrent(currentPlayerView)
                        currentPlayerView!!.code = STATE_PREPARING
                    }
                    else -> {
                        ON_PLAY_PAUSE_TMP_STATE = currentPlayerView!!.code
                        currentPlayerView!!.onStatePause()
                        currentPlayerView!!.mediaInterface!!.pause()
                    }
                }
            }
        }

        fun startFullscreenDirectly(
            context: Context?,
            _class: Class<BasePlayerView?>,
            url: String?,
            title: String?
        ) {
            startFullscreenDirectly(context, _class, url?.let { DataSource(it, title) })
        }

        private fun startFullscreenDirectly(
            context: Context?,
            _class: Class<BasePlayerView?>,
            dataSource: DataSource?
        ) {
            Utils.hideStatusBar(context)
            Utils.setRequestedOrientation(context, FULLSCREEN_ORIENTATION)
            Utils.hideSystemUI(context)
            val vp = Utils.scanForActivity(context)!!.window.decorView as ViewGroup
            try {
                val constructor = _class.getConstructor(
                    Context::class.java
                )
                val basePlayerView = constructor.newInstance(context)
                val lp = LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                )
                vp.addView(basePlayerView, lp)
                basePlayerView?.setUp(dataSource, SCREEN_FULLSCREEN)
                basePlayerView?.startVideo()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun releaseAllVideos() {
            if (currentPlayerView != null) {
                currentPlayerView!!.reset()
                currentPlayerView = null
            }
            CONTAINER_LIST.clear()
        }

        fun backPress(): Boolean {
            if (CONTAINER_LIST.size != 0 && currentPlayerView != null) { //判断条件，因为当前所有goBack都是回到普通窗口
                currentPlayerView!!.gotoNormalScreen()
                return true
            } else if (CONTAINER_LIST.size == 0 && currentPlayerView != null && currentPlayerView!!.screenConfig != SCREEN_NORMAL) { //退出直接进入的全屏
                currentPlayerView!!.clearFloatScreen()
                return true
            }
            return false
        }

        fun setCurrent(basePlayerView: BasePlayerView?) {
            if (currentPlayerView != null) currentPlayerView!!.reset()
            currentPlayerView = basePlayerView
        }

        fun setTextureViewRotation(rotation: Int) {
            if (currentPlayerView != null && currentPlayerView!!.textureView != null) {
                currentPlayerView!!.textureView!!.rotation = rotation.toFloat()
            }
        }

        fun setVideoImageDisplayType(type: Int) {
            VIDEO_IMAGE_DISPLAY_TYPE = type
            if (currentPlayerView != null && currentPlayerView!!.textureView != null) {
                currentPlayerView!!.textureView!!.requestLayout()
            }
        }
    }
}
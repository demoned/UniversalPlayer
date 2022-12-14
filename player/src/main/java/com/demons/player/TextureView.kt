package com.demons.player

import android.content.Context
import android.util.AttributeSet
import android.view.TextureView
import android.view.View

class TextureView : TextureView {
    private var currentVideoWidth: Int
    private var currentVideoHeight: Int

    constructor(context: Context?) : super(context!!) {
        currentVideoWidth = 0
        currentVideoHeight = 0
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(
        context!!, attrs
    ) {
        currentVideoWidth = 0
        currentVideoHeight = 0
    }

    fun setVideoSize(currentVideoWidth: Int, currentVideoHeight: Int) {
        if (this.currentVideoWidth != currentVideoWidth || this.currentVideoHeight != currentVideoHeight) {
            this.currentVideoWidth = currentVideoWidth
            this.currentVideoHeight = currentVideoHeight
            requestLayout()
        }
    }

    override fun setRotation(rotation: Float) {
        if (rotation != getRotation()) {
            super.setRotation(rotation)
            requestLayout()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var widthSpec = widthMeasureSpec
        var heightSpec = heightMeasureSpec
        val viewRotation = rotation.toInt()
        val videoWidth = currentVideoWidth
        var videoHeight = currentVideoHeight
        var parentHeight = (parent as View).measuredHeight
        var parentWidth = (parent as View).measuredWidth
        if (parentWidth != 0 && parentHeight != 0 && videoWidth != 0 && videoHeight != 0) {
            if (BasePlayerView.VIDEO_IMAGE_DISPLAY_TYPE == BasePlayerView.VIDEO_IMAGE_DISPLAY_TYPE_FILL_PARENT) {
                if (viewRotation == 90 || viewRotation == 270) {
                    val tempSize = parentWidth
                    parentWidth = parentHeight
                    parentHeight = tempSize
                }
                /**强制充满 */
                videoHeight = videoWidth * parentHeight / parentWidth
            }
        }

        // 如果判断成立，则说明显示的TextureView和本身的位置是有90度的旋转的，所以需要交换宽高参数。
        if (viewRotation == 90 || viewRotation == 270) {
            val tempMeasureSpec = widthSpec
            widthSpec = heightSpec
            heightSpec = tempMeasureSpec
        }
        var width = getDefaultSize(videoWidth, widthSpec)
        var height = getDefaultSize(videoHeight, heightSpec)
        if (videoWidth > 0 && videoHeight > 0) {
            val widthSpecMode = MeasureSpec.getMode(widthSpec)
            val widthSpecSize = MeasureSpec.getSize(widthSpec)
            val heightSpecMode = MeasureSpec.getMode(heightSpec)
            val heightSpecSize = MeasureSpec.getSize(heightSpec)
            if (widthSpecMode == MeasureSpec.EXACTLY && heightSpecMode == MeasureSpec.EXACTLY) {
                // the size is fixed
                width = widthSpecSize
                height = heightSpecSize
                // for compatibility, we adjust size based on aspect ratio
                if (videoWidth * height < width * videoHeight) {
                    width = height * videoWidth / videoHeight
                } else if (videoWidth * height > width * videoHeight) {
                    height = width * videoHeight / videoWidth
                }
            } else if (widthSpecMode == MeasureSpec.EXACTLY) {
                // only the width is fixed, adjust the height to match aspect ratio if possible
                width = widthSpecSize
                height = width * videoHeight / videoWidth
                if (heightSpecMode == MeasureSpec.AT_MOST && height > heightSpecSize) {
                    // couldn't match aspect ratio within the constraints
                    height = heightSpecSize
                    width = height * videoWidth / videoHeight
                }
            } else if (heightSpecMode == MeasureSpec.EXACTLY) {
                // only the height is fixed, adjust the width to match aspect ratio if possible
                height = heightSpecSize
                width = height * videoWidth / videoHeight
                if (widthSpecMode == MeasureSpec.AT_MOST && width > widthSpecSize) {
                    // couldn't match aspect ratio within the constraints
                    width = widthSpecSize
                    height = width * videoHeight / videoWidth
                }
            } else {
                // neither the width nor the height are fixed, try to use actual video size
                width = videoWidth
                height = videoHeight
                if (heightSpecMode == MeasureSpec.AT_MOST && height > heightSpecSize) {
                    // too tall, decrease both width and height
                    height = heightSpecSize
                    width = height * videoWidth / videoHeight
                }
                if (widthSpecMode == MeasureSpec.AT_MOST && width > widthSpecSize) {
                    // too wide, decrease both width and height
                    width = widthSpecSize
                    height = width * videoHeight / videoWidth
                }
            }
        } else {
            // no size yet, just adopt the given spec sizes
        }
        if (parentWidth != 0 && parentHeight != 0 && videoWidth != 0 && videoHeight != 0) {
            if (BasePlayerView.VIDEO_IMAGE_DISPLAY_TYPE == BasePlayerView.VIDEO_IMAGE_DISPLAY_TYPE_ORIGINAL) {
                /**原图 */
                height = videoHeight
                width = videoWidth
            } else if (BasePlayerView.VIDEO_IMAGE_DISPLAY_TYPE == BasePlayerView.VIDEO_IMAGE_DISPLAY_TYPE_FILL_CROP) {
                if (viewRotation == 90 || viewRotation == 270) {
                    val tempSize = parentWidth
                    parentWidth = parentHeight
                    parentHeight = tempSize
                }
                /**充满剪切 */
                if (videoHeight.toDouble() / videoWidth > parentHeight.toDouble() / parentWidth) {
                    height = (parentWidth.toDouble() / width.toDouble() * height.toDouble()).toInt()
                    width = parentWidth
                } else if (videoHeight.toDouble() / videoWidth < parentHeight.toDouble() / parentWidth) {
                    width = (parentHeight.toDouble() / height.toDouble() * width.toDouble()).toInt()
                    height = parentHeight
                }
            }
        }
        setMeasuredDimension(width, height)
    }
}
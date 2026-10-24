package com.adfreepdf.reader.ui.viewer

import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.abs

/** A simple pinch-to-zoom + pan image view used to display PDF pages. */
class ZoomableImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : AppCompatImageView(context, attrs, defStyle) {

    private val matrix = Matrix()
    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val factor = detector.scaleFactor
                val newScale = (currentScale * factor).coerceIn(MIN, MAX)
                val applied = newScale / currentScale
                currentScale = newScale
                matrix.postScale(applied, applied, detector.focusX, detector.focusY)
                imageMatrix = matrix
                return true
            }
        }
    )

    private var currentScale = 1f
    private val last = PointF()
    private var dragging = false
    var onTap: (() -> Unit)? = null
    var onScaleChanged: ((Float) -> Unit)? = null

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                last.set(event.x, event.y); dragging = true
            }
            MotionEvent.ACTION_MOVE -> if (dragging && !scaleDetector.isInProgress) {
                val dx = event.x - last.x
                val dy = event.y - last.y
                matrix.postTranslate(dx, dy)
                imageMatrix = matrix
                last.set(event.x, event.y)
            }
            MotionEvent.ACTION_UP -> {
                if (dragging && abs(event.x - last.x) < 8 && abs(event.y - last.y) < 8) onTap?.invoke()
                dragging = false
            }
            MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return true
    }

    /** Resets the view so a new bitmap fits the screen. */
    fun resetForBitmap(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        matrix.reset()
        val vw = width.toFloat().takeIf { it > 0 } ?: w.toFloat()
        val vh = height.toFloat().takeIf { it > 0 } ?: h.toFloat()
        val scale = minOf(vw / w, vh / h)
        matrix.postScale(scale, scale)
        matrix.postTranslate((vw - w * scale) / 2f, (vh - h * scale) / 2f)
        currentScale = scale
        imageMatrix = matrix
        onScaleChanged?.invoke(scale)
    }

    override fun setImageBitmap(bm: android.graphics.Bitmap?) {
        super.setImageBitmap(bm)
        bm?.let { post { resetForBitmap(it.width, it.height) } }
    }

    fun zoomBy(f: Float) {
        val newScale = (currentScale * f).coerceIn(MIN, MAX)
        val applied = newScale / currentScale
        currentScale = newScale
        matrix.postScale(applied, applied, width / 2f, height / 2f)
        imageMatrix = matrix
        onScaleChanged?.invoke(currentScale)
    }

    companion object { const val MIN = 0.5f; const val MAX = 8f }
}

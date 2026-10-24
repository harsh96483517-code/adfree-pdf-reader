package com.adfreepdf.reader.ui.edit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

/** Freehand annotation layer drawn on top of a PDF page. */
class DrawingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    enum class Mode { HIGHLIGHT, PEN, TEXT, SIGNATURE, ERASER }

    sealed class Item
    class Stroke(val pts: MutableList<PointF>, val color: Int, val width: Float, val highlight: Boolean) : Item()
    class TextItem(val at: PointF, val text: String, val color: Int) : Item()
    class SigItem(val rect: RectF, val bmp: Bitmap) : Item()

    var mode: Mode = Mode.PEN
        set(v) { field = v }
    var color: Int = Color.RED
    var onTextRequest: ((PointF) -> Unit)? = null
    var onSignatureRequest: ((PointF) -> Unit)? = null

    private val items = mutableListOf<Item>()
    private var current: Stroke? = null

    init { isClickable = true }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    fun undo() { if (items.isNotEmpty()) { items.removeAt(items.size - 1); invalidate() } }
    fun clearAll() { items.clear(); invalidate() }
    fun isEmpty() = items.isEmpty()
    fun items(): List<Item> = items

    fun addText(at: PointF, text: String) { items.add(TextItem(at, text, color)); invalidate() }
    fun addSignature(rect: RectF, bmp: Bitmap) { items.add(SigItem(rect, bmp)); invalidate() }

    /** Replaces everything (used when switching pages). */
    fun restore(list: List<Item>) { items.clear(); items.addAll(list); invalidate() }
    fun detach() { items.clear(); invalidate() }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (mode) {
            Mode.TEXT -> {
                if (event.actionMasked == MotionEvent.ACTION_UP) onTextRequest?.invoke(PointF(event.x, event.y))
                return true
            }
            Mode.SIGNATURE -> {
                if (event.actionMasked == MotionEvent.ACTION_UP) onSignatureRequest?.invoke(PointF(event.x, event.y))
                return true
            }
            Mode.ERASER -> {
                if (event.actionMasked == MotionEvent.ACTION_UP) eraseAt(event.x, event.y)
                return true
            }
            else -> { /* pen / highlight */ }
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val s = Stroke(mutableListOf(PointF(event.x, event.y)), color,
                    if (mode == Mode.HIGHLIGHT) dp(22f) else dp(3f), mode == Mode.HIGHLIGHT)
                current = s; items.add(s); invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                current?.pts?.add(PointF(event.x, event.y))
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> current = null
        }
        return true
    }

    private fun eraseAt(x: Float, y: Float) {
        val idx = items.indexOfLast { hit(it, x, y) }
        if (idx >= 0) { items.removeAt(idx); invalidate() }
    }

    private fun hit(item: Item, x: Float, y: Float): Boolean = when (item) {
        is Stroke -> item.pts.any { hypot((it.x - x).toDouble(), (it.y - y).toDouble()) < dp(24f) }
        is TextItem -> hypot((item.at.x - x).toDouble(), (item.at.y - y).toDouble()) < dp(30f)
        is SigItem -> item.rect.contains(x, y)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        items.forEach { item ->
            when (item) {
                is Stroke -> {
                    paint.color = if (item.highlight) (item.color and 0x00FFFFFF) or (0x66000000) else item.color
                    paint.strokeWidth = item.width
                    paint.alpha = if (item.highlight) 110 else 255
                    if (item.pts.size == 1) {
                        val p = item.pts[0]
                        canvas.drawPoint(p.x, p.y, paint)
                    } else {
                        for (i in 0 until item.pts.size - 1) {
                            canvas.drawLine(item.pts[i].x, item.pts[i].y, item.pts[i + 1].x, item.pts[i + 1].y, paint)
                        }
                    }
                }
                is TextItem -> {
                    paint.color = item.color; paint.alpha = 255; paint.strokeWidth = dp(1f)
                    val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = item.color; textSize = dp(16f); style = Paint.Style.FILL
                    }
                    canvas.drawText(item.text, item.at.x, item.at.y, tp)
                }
                is SigItem -> canvas.drawBitmap(item.bmp, null, item.rect, null)
            }
        }
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
}

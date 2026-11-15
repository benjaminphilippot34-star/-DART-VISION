package com.dartvision.mobile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

/** Transparent touch overlay used to select the board's four outer corners. */
class CalibrationOverlay(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val points = mutableListOf<ImagePoint>()
    var onPoint: ((Double, Double) -> Unit)? = null
    var active: Boolean = false
        set(value) { field = value; invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!active) return
        paint.color = 0xCC00E5FF.toInt()
        paint.strokeWidth = 5f
        paint.style = Paint.Style.STROKE
        canvas.drawRect(8f, 8f, width - 8f, height - 8f, paint)
        paint.style = Paint.Style.FILL
        points.forEachIndexed { index, point ->
            canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), 13f, paint)
            paint.textSize = 30f
            canvas.drawText((index + 1).toString(), point.x.toFloat() + 18f, point.y.toFloat() - 18f, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!active) return false
        if (event.action == MotionEvent.ACTION_UP) {
            onPoint?.invoke(event.x.toDouble(), event.y.toDouble())
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun resetPoints() { points.clear(); invalidate() }
    fun addScreenPoint(x: Double, y: Double) { points.add(ImagePoint(x, y)); invalidate() }
}

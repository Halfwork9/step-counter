package com.example.stepcounter

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import java.util.Locale

class WeeklyChart(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var steps = IntArray(0)
    private var labels = arrayOf<String>()
    private var todayIdx = -1

    private val d = resources.displayMetrics.density
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF334155.toInt() }
    private val barToday = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF34D399.toInt() }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF94A3B8.toInt(); textSize = 12 * d; textAlign = Paint.Align.CENTER
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE2E8F0.toInt(); textSize = 11 * d; textAlign = Paint.Align.CENTER
    }

    fun setData(steps: IntArray, labels: Array<String>, todayIdx: Int) {
        this.steps = steps
        this.labels = labels
        this.todayIdx = todayIdx
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (steps.isEmpty()) return
        val max = maxOf(1, steps.max())
        val n = steps.size
        val slot = width / n.toFloat()
        val barW = slot * 0.5f
        val labelH = labelPaint.textSize * 2.0f
        val chartBottom = height - labelH
        val chartTop = valuePaint.textSize * 1.8f

        for (i in 0 until n) {
            val cx = slot * i + slot / 2
            val h = if (steps[i] == 0) 4 * d
                    else (steps[i].toFloat() / max) * (chartBottom - chartTop - 10 * d) + 10 * d
            val top = chartBottom - h
            val left = cx - barW / 2
            canvas.drawRoundRect(
                RectF(left, top, left + barW, chartBottom),
                barW / 2, barW / 2,
                if (i == todayIdx) barToday else barPaint
            )
            canvas.drawText(labels.getOrElse(i) { "" }, cx, height - labelPaint.textSize * 0.4f, labelPaint)
            if (steps[i] > 0) canvas.drawText(short(steps[i]), cx, top - 8 * d, valuePaint)
        }
    }

    private fun short(v: Int): String =
        if (v >= 1000) String.format(Locale.US, "%.1fk", v / 1000f) else v.toString()
}
package com.max.assistant

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View

class UltronHudView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val cyan = Color.parseColor("#00E5FF")
    private val cyanBright = Color.parseColor("#5CF5FF")

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = cyan
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val boltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.BLACK
    }

    private var rot1 = 0f
    private var rot2 = 0f
    private var rot3 = 0f

    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var animating = true

    private val ticker = object : Runnable {
        override fun run() {
            if (!animating) return
            rot1 = (rot1 + 0.15f) % 360f
            rot2 = (rot2 - 0.25f) % 360f
            rot3 = (rot3 + 0.35f) % 360f
            invalidate()
            handler.postDelayed(this, 16)
        }
    }

    init {
        handler.post(ticker)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animating = false
        handler.removeCallbacksAndMessages(null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val maxR = minOf(width, height) / 2f

        // 1. OUTER GLOW (solid cyan with alpha)
        fillPaint.color = Color.parseColor("#4000E5FF")
        canvas.drawCircle(cx, cy, maxR * 0.98f, fillPaint)

        // 2. ROTATING RINGS
        strokePaint.color = cyan

        strokePaint.alpha = 170
        strokePaint.strokeWidth = maxR * 0.012f
        drawTicks(canvas, cx, cy, maxR * 0.94f, 40, rot1)

        strokePaint.alpha = 220
        strokePaint.strokeWidth = maxR * 0.014f
        drawDashedRing(canvas, cx, cy, maxR * 0.84f, 6f, 22, rot2)

        strokePaint.alpha = 255
        strokePaint.strokeWidth = maxR * 0.018f
        drawArcs(canvas, cx, cy, maxR * 0.72f, rot3)

        strokePaint.alpha = 230
        strokePaint.strokeWidth = maxR * 0.012f
        drawDashedRing(canvas, cx, cy, maxR * 0.60f, 4f, 30, -rot2)

        strokePaint.alpha = 255
        strokePaint.strokeWidth = maxR * 0.018f
        canvas.drawCircle(cx, cy, maxR * 0.46f, strokePaint)

        // 3. CENTER SOLID BRIGHT ORB (koi gradient nahi — pure color)
        val orbR = maxR * 0.40f
        fillPaint.color = Color.parseColor("#6000E5FF") // soft glow behind
        canvas.drawCircle(cx, cy, orbR * 1.4f, fillPaint)

        fillPaint.color = cyanBright   // SOLID BRIGHT CYAN
        canvas.drawCircle(cx, cy, orbR, fillPaint)

        // 4. BLACK LIGHTNING BOLT (solid, no gradient)
        drawBolt(canvas, cx, cy, orbR * 0.85f)
    }

    private fun drawTicks(canvas: Canvas, cx: Float, cy: Float, r: Float, count: Int, rot: Float) {
        for (i in 0 until count) {
            val a = Math.toRadians((i * (360f / count) + rot).toDouble())
            val x1 = cx + (r * 0.95f * Math.cos(a)).toFloat()
            val y1 = cy + (r * 0.95f * Math.sin(a)).toFloat()
            val x2 = cx + (r * Math.cos(a)).toFloat()
            val y2 = cy + (r * Math.sin(a)).toFloat()
            canvas.drawLine(x1, y1, x2, y2, strokePaint)
        }
    }

    private fun drawDashedRing(canvas: Canvas, cx: Float, cy: Float, r: Float,
                               dashLen: Float, segs: Int, rot: Float) {
        val total = (2 * Math.PI * r).toFloat()
        val dashDeg = dashLen / total * 360f
        val step = 360f / segs
        for (i in 0 until segs) {
            val start = i * step + rot
            canvas.drawArc(cx - r, cy - r, cx + r, cy + r, start, dashDeg, false, strokePaint)
        }
    }

    private fun drawArcs(canvas: Canvas, cx: Float, cy: Float, r: Float, rot: Float) {
        val arcLen = 40f
        for (i in 0 until 5) {
            val start = i * (arcLen + 32f) + rot
            canvas.drawArc(cx - r, cy - r, cx + r, cy + r, start, arcLen, false, strokePaint)
        }
    }

    private fun drawBolt(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val p = Path()
        p.moveTo(cx + s * 0.15f, cy - s * 1.0f)
        p.lineTo(cx - s * 0.55f, cy + s * 0.10f)
        p.lineTo(cx - s * 0.10f, cy + s * 0.10f)
        p.lineTo(cx - s * 0.20f, cy + s * 1.0f)
        p.lineTo(cx + s * 0.55f, cy - s * 0.10f)
        p.lineTo(cx + s * 0.10f, cy - s * 0.10f)
        p.close()
        canvas.drawPath(p, boltPaint)
    }
}

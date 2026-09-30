package com.max.assistant

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
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
    private val cyanDim = Color.parseColor("#0088AA")

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val boltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#0A0E14")
    }

    private var rot1 = 0f
    private var rot2 = 0f
    private var rot3 = 0f
    private var pulse = 1f

    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var animating = true

    private val ticker = object : Runnable {
        override fun run() {
            if (!animating) return
            rot1 = (rot1 + 0.35f) % 360f
            rot2 = (rot2 - 0.55f) % 360f
            rot3 = (rot3 + 0.75f) % 360f
            invalidate()
            handler.postDelayed(this, 16)
        }
    }

    private val pulseTicker = object : Runnable {
        private var grow = true
        override fun run() {
            if (!animating) return
            if (grow) {
                pulse += 0.006f
                if (pulse >= 1.06f) grow = false
            } else {
                pulse -= 0.006f
                if (pulse <= 0.96f) grow = true
            }
            invalidate()
            handler.postDelayed(this, 30)
        }
    }

    init {
        handler.post(ticker)
        handler.post(pulseTicker)
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

        // Outer soft glow
        glowPaint.shader = RadialGradient(cx, cy, maxR,
            intArrayOf(
                Color.parseColor("#00000000"),
                Color.parseColor("#2800E5FF"),
                Color.parseColor("#00000000")
            ), floatArrayOf(0.55f, 0.85f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, maxR, glowPaint)

        // Ring 1 - outermost tiny dashed (like tick marks)
        ringPaint.color = cyan
        ringPaint.alpha = 180
        ringPaint.strokeWidth = maxR * 0.012f
        drawTicks(canvas, cx, cy, maxR * 0.94f, 40, rot1)

        // Ring 2 - dashed segments
        ringPaint.alpha = 220
        ringPaint.strokeWidth = maxR * 0.014f
        drawDashedRing(canvas, cx, cy, maxR * 0.84f, 6f, 22, rot2)

        // Ring 3 - long arcs
        ringPaint.alpha = 255
        ringPaint.strokeWidth = maxR * 0.018f
        drawArcs(canvas, cx, cy, maxR * 0.72f, rot3)

        // Ring 4 - small dashes
        ringPaint.color = cyan
        ringPaint.alpha = 230
        ringPaint.strokeWidth = maxR * 0.012f
        drawDashedRing(canvas, cx, cy, maxR * 0.60f, 4f, 30, -rot2)

        // Ring 5 - inner solid cyan ring
        ringPaint.alpha = 255
        ringPaint.strokeWidth = maxR * 0.016f
        canvas.drawCircle(cx, cy, maxR * 0.46f, ringPaint)

        // Center glow halo
        val centerR = maxR * 0.40f * pulse
        glowPaint.shader = RadialGradient(cx, cy, centerR * 1.7f,
            intArrayOf(
                Color.parseColor("#6600E5FF"),
                Color.parseColor("#2200E5FF"),
                Color.parseColor("#00000000")
            ), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, centerR * 1.7f, glowPaint)

        // Center solid orb
        fillPaint.shader = RadialGradient(cx, cy, centerR,
            intArrayOf(
                Color.parseColor("#B0FFFFFF"),
                Color.parseColor("#FF00E5FF"),
                Color.parseColor("#CC0088AA")
            ), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, centerR, fillPaint)

        // Lightning bolt
        drawBolt(canvas, cx, cy, centerR * 0.85f)
    }

    private fun drawTicks(canvas: Canvas, cx: Float, cy: Float, r: Float, count: Int, rot: Float) {
        for (i in 0 until count) {
            val a = Math.toRadians((i * (360f / count) + rot).toDouble())
            val x1 = cx + (r * 0.96f * Math.cos(a)).toFloat()
            val y1 = cy + (r * 0.96f * Math.sin(a)).toFloat()
            val x2 = cx + (r * Math.cos(a)).toFloat()
            val y2 = cy + (r * Math.sin(a)).toFloat()
            canvas.drawLine(x1, y1, x2, y2, ringPaint)
        }
    }

    private fun drawDashedRing(canvas: Canvas, cx: Float, cy: Float, r: Float,
                               dashLen: Float, segs: Int, rot: Float) {
        val total = (2 * Math.PI * r).toFloat()
        val dashDeg = dashLen / total * 360f
        val step = 360f / segs
        for (i in 0 until segs) {
            val start = i * step + rot
            canvas.drawArc(cx - r, cy - r, cx + r, cy + r, start, dashDeg, false, ringPaint)
        }
    }

    private fun drawArcs(canvas: Canvas, cx: Float, cy: Float, r: Float, rot: Float) {
        // 5 arcs with gaps, offset so they look like broken lines
        val arcLen = 40f
        for (i in 0 until 5) {
            val start = i * (arcLen + 32f) + rot
            canvas.drawArc(cx - r, cy - r, cx + r, cy + r, start, arcLen, false, ringPaint)
        }
    }

    private fun drawBolt(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val p = Path()
        // Classic lightning bolt
        p.moveTo(cx + s * 0.15f, cy - s * 0.95f)   // top
        p.lineTo(cx - s * 0.42f, cy + s * 0.10f)   // mid-left outer
        p.lineTo(cx - s * 0.08f, cy + s * 0.10f)   // mid-left inner
        p.lineTo(cx - s * 0.15f, cy + s * 0.95f)   // bottom
        p.lineTo(cx + s * 0.42f, cy - s * 0.12f)   // mid-right outer
        p.lineTo(cx + s * 0.08f, cy - s * 0.12f)   // mid-right inner
        p.close()
        canvas.drawPath(p, boltPaint)
    }
}

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
        color = Color.parseColor("#000000")
    }

    private var rot1 = 0f
    private var rot2 = 0f
    private var rot3 = 0f
    private var glowPulse = 1f

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

    private val pulseTicker = object : Runnable {
        private var grow = true
        override fun run() {
            if (!animating) return
            if (grow) {
                glowPulse += 0.008f
                if (glowPulse >= 1.15f) grow = false
            } else {
                glowPulse -= 0.008f
                if (glowPulse <= 0.9f) grow = true
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

        // ===== 1. Big outer cyan haze (bottom layer) =====
        glowPaint.shader = RadialGradient(cx, cy, maxR * 1.0f,
            intArrayOf(
                Color.parseColor("#4000E5FF"),
                Color.parseColor("#1800E5FF"),
                Color.parseColor("#00000000")
            ), floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, maxR, glowPaint)

        // ===== 2. Rotating rings =====
        ringPaint.color = cyan

        ringPaint.alpha = 170
        ringPaint.strokeWidth = maxR * 0.012f
        drawTicks(canvas, cx, cy, maxR * 0.94f, 40, rot1)

        ringPaint.alpha = 220
        ringPaint.strokeWidth = maxR * 0.014f
        drawDashedRing(canvas, cx, cy, maxR * 0.84f, 6f, 22, rot2)

        ringPaint.alpha = 255
        ringPaint.strokeWidth = maxR * 0.018f
        drawArcs(canvas, cx, cy, maxR * 0.72f, rot3)

        ringPaint.alpha = 230
        ringPaint.strokeWidth = maxR * 0.012f
        drawDashedRing(canvas, cx, cy, maxR * 0.60f, 4f, 30, -rot2)

        ringPaint.alpha = 255
        ringPaint.strokeWidth = maxR * 0.018f
        canvas.drawCircle(cx, cy, maxR * 0.46f, ringPaint)

        // ===== 3. Center bright cyan orb (glowing) =====
        val centerR = maxR * 0.42f

        // Big glow behind the orb
        glowPaint.shader = RadialGradient(cx, cy, centerR * 2.0f * glowPulse,
            intArrayOf(
                Color.parseColor("#9900E5FF"),
                Color.parseColor("#4400E5FF"),
                Color.parseColor("#00000000")
            ), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, centerR * 2.0f * glowPulse, glowPaint)

        // Solid glowing orb
        fillPaint.shader = RadialGradient(cx, cy, centerR,
            intArrayOf(
                Color.parseColor("#CCFFFFFF"),
                Color.parseColor("#FF4DF2FF"),
                Color.parseColor("#FF00C8E5"),
                Color.parseColor("#FF0088AA")
            ), floatArrayOf(0f, 0.35f, 0.75f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, centerR, fillPaint)

        // Thin cyan ring on orb edge
        ringPaint.alpha = 255
        ringPaint.strokeWidth = maxR * 0.008f
        canvas.drawCircle(cx, cy, centerR, ringPaint)

        // ===== 4. Black lightning bolt (always on top, always visible) =====
        drawBolt(canvas, cx, cy, centerR * 0.9f)
    }

    private fun drawTicks(canvas: Canvas, cx: Float, cy: Float, r: Float, count: Int, rot: Float) {
        for (i in 0 until count) {
            val a = Math.toRadians((i * (360f / count) + rot).toDouble())
            val x1 = cx + (r * 0.95f * Math.cos(a)).toFloat()
            val y1 = cy + (r * 0.95f * Math.sin(a)).toFloat()
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
        val arcLen = 40f
        for (i in 0 until 5) {
            val start = i * (arcLen + 32f) + rot
            canvas.drawArc(cx - r, cy - r, cx + r, cy + r, start, arcLen, false, ringPaint)
        }
    }

    private fun drawBolt(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        // Original simple bolt shape — big and clear
        val p = Path()
        p.moveTo(cx + s * 0.10f, cy - s * 0.95f)   // top right
        p.lineTo(cx - s * 0.55f, cy + s * 0.10f)   // left middle outer
        p.lineTo(cx - s * 0.10f, cy + s * 0.10f)   // left middle inner
        p.lineTo(cx - s * 0.20f, cy + s * 0.95f)   // bottom
        p.lineTo(cx + s * 0.55f, cy - s * 0.10f)   // right middle outer
        p.lineTo(cx + s * 0.10f, cy - s * 0.10f)   // right middle inner
        p.close()
        canvas.drawPath(p, boltPaint)
    }
}

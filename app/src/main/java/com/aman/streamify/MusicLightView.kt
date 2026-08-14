package com.aman.streamify

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin

class MusicLightView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(5f)
    }

    private var effect = "Sound Wave"
    private var phase = 0f
    private var active = false

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1400L
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            phase = it.animatedFraction
            if (active) invalidate()
        }
    }

    fun setEffect(name: String) {
        effect = name
        invalidate()
    }

    fun setActive(value: Boolean) {
        active = value
        visibility = if (value) VISIBLE else GONE
        if (value) {
            if (!animator.isStarted) animator.start()
        } else {
            animator.cancel()
        }
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!active) return

        val pad = dp(7f)
        val rect = RectF(pad, pad, width - pad, height - pad)
        val pulse = 0.6f + (sin(phase * Math.PI * 2).toFloat() + 1f) * 0.2f
        paint.alpha = (255 * pulse).toInt()

        paint.shader = when (effect) {
            "Blue" -> LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(Color.CYAN, Color.rgb(35, 95, 255), Color.MAGENTA),
                null, Shader.TileMode.CLAMP
            )
            "Green" -> LinearGradient(
                0f, height.toFloat(), width.toFloat(), 0f,
                intArrayOf(Color.rgb(124, 255, 0), Color.CYAN, Color.rgb(124, 255, 0)),
                null, Shader.TileMode.CLAMP
            )
            "Shimmer" -> {
                val x = width * phase
                LinearGradient(
                    x - width * .35f, 0f, x + width * .35f, height.toFloat(),
                    intArrayOf(Color.MAGENTA, Color.CYAN, Color.YELLOW, Color.MAGENTA),
                    null, Shader.TileMode.MIRROR
                )
            }
            else -> {
                val c1 = Color.HSVToColor(floatArrayOf((phase * 360f) % 360f, .85f, 1f))
                val c2 = Color.HSVToColor(floatArrayOf(((phase * 360f) + 120f) % 360f, .85f, 1f))
                LinearGradient(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    c1, c2, Shader.TileMode.MIRROR
                )
            }
        }

        paint.strokeWidth = dp(4f) + dp(2f) * pulse
        canvas.drawRoundRect(rect, dp(24f), dp(24f), paint)
        paint.shader = null
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}

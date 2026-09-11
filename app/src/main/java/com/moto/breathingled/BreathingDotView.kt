package com.moto.breathingled

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

/**
 * 自定义绘制组件：OLED 全纯黑底板 (#000000 不发光) + 左上角纯实心呼吸小圆点 + 防烧屏微位移
 */
class BreathingDotView @JvmOverloads constructor(
    context: Context,
    private val prefs: LedPreferences? = null,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val actualPrefs: LedPreferences by lazy {
        prefs ?: LedPreferences(context)
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor(actualPrefs.dotColor)
    }

    private var currentAlpha = 0f
    private var animator: ValueAnimator? = null
    private var pixelShiftX = 0f
    private var pixelShiftY = 0f
    private val shiftHandler = Handler(Looper.getMainLooper())

    // 防烧屏微移定时器（每60秒在2像素内微移，防止 OLED 固态发光像素老化）
    private val shiftRunnable = object : Runnable {
        override fun run() {
            if (actualPrefs.enablePixelShift) {
                pixelShiftX = ((System.currentTimeMillis() % 5) - 2).toFloat()
                pixelShiftY = (((System.currentTimeMillis() / 7) % 5) - 2).toFloat()
                invalidate()
            }
            shiftHandler.postDelayed(this, 60_000)
        }
    }

    init {
        // 背景设为纯黑(#000000)，Motorola Edge 的 pOLED 屏幕纯黑像素完全断电不耗电
        setBackgroundColor(Color.BLACK)
    }

    fun startBreathing() {
        animator?.cancel()
        paint.color = Color.parseColor(actualPrefs.dotColor)
        animator = ValueAnimator.ofFloat(0.15f, 1.0f).apply {
            duration = (actualPrefs.breathingDuration * 1000).toLong()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                currentAlpha = it.animatedValue as Float
                invalidate()
            }
            start()
        }
        shiftHandler.post(shiftRunnable)
    }

    fun stopBreathing() {
        animator?.cancel()
        shiftHandler.removeCallbacks(shiftRunnable)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val density = resources.displayMetrics.density
        val baseRadius = actualPrefs.dotRadiusDp * density
        val centerX = (actualPrefs.offsetXDp * density) + pixelShiftX
        val centerY = (actualPrefs.offsetYDp * density) + pixelShiftY

        // 绘制纯实心呼吸小圆点（边缘清晰利落，无杂光）
        paint.alpha = (currentAlpha * 255).toInt().coerceIn(0, 255)
        canvas.drawCircle(centerX, centerY, baseRadius, paint)
    }
}
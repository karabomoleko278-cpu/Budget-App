package com.iie.vaultquest.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.iie.vaultquest.R
import com.iie.vaultquest.domain.BudgetHealth
import com.iie.vaultquest.domain.BudgetLevel

/**
 * Horizontal, segmented budget bar (Budgetly's visual goal indicator).
 *
 * The track spans 0 → maximum goal. A fill grows with spending and is colour-coded
 * (green on-track / amber caution-or-under / red over), and a tick marks where the
 * Minimum threshold sits. Distinct from a circular gauge — a linear "fuel bar".
 *
 * onDraw is fully guarded so a render edge case can't crash the host (stability).
 */
class LinearBudgetBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val tag = "LinearBudgetBar"
    private val barHeight = dp(18f)
    private val corner = dp(9f)

    private var health: BudgetHealth? = null
    private var animatedFill = 0f
    private var animator: ValueAnimator? = null

    private val trackRect = RectF()
    private val fillRect = RectF()

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.divider)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.vault_text_primary)
    }

    fun setHealth(newHealth: BudgetHealth) {
        health = newHealth
        animator?.cancel()
        animator = ValueAnimator.ofFloat(animatedFill, newHealth.fill).apply {
            duration = 700
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                animatedFill = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val desiredH = (barHeight + dp(8f)).toInt()
        val h = resolveSize(desiredH, heightMeasureSpec)
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        try {
            val left = paddingLeft.toFloat()
            val right = (width - paddingRight).toFloat()
            val top = (height - barHeight) / 2f
            val bottom = top + barHeight

            trackRect.set(left, top, right, bottom)
            canvas.drawRoundRect(trackRect, corner, corner, trackPaint)

            val h = health
            if (h != null && h.max > 0) {
                fillPaint.color = colorFor(h.level)
                val fillRight = left + (right - left) * animatedFill
                fillRect.set(left, top, fillRight, bottom)
                canvas.drawRoundRect(fillRect, corner, corner, fillPaint)

                // Min threshold tick
                val minFraction = (h.min / h.max).toFloat().coerceIn(0f, 1f)
                val tickX = left + (right - left) * minFraction
                val tickW = dp(2f)
                canvas.drawRect(tickX - tickW / 2, top - dp(3f), tickX + tickW / 2, bottom + dp(3f), tickPaint)
            }
        } catch (e: Exception) {
            Log.e(tag, "Bar render error: ${e.message}", e)
        }
    }

    private fun colorFor(level: BudgetLevel): Int {
        val res = when (level) {
            BudgetLevel.ON_TRACK -> R.color.vault_green
            BudgetLevel.CAUTION, BudgetLevel.UNDER -> R.color.vault_amber
            BudgetLevel.OVER -> R.color.vault_red
            BudgetLevel.NONE -> R.color.divider
        }
        return ContextCompat.getColor(context, res)
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}

package com.iie.vaultquest.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.iie.vaultquest.R
import com.iie.vaultquest.domain.GoalStatus
import com.iie.vaultquest.domain.GoalZone
import java.text.NumberFormat
import java.util.Locale

/**
 * Custom circular gauge that visualises this month's spending against the user's
 * Minimum and Maximum goals (PoE requirement #3).
 *
 *  * The arc sweeps in proportion to spent / max.
 *  * Colour communicates the zone at a glance:
 *      GREEN  = safely between min and max
 *      AMBER  = below minimum or approaching the maximum
 *      RED    = maximum breached
 *  * Tick marks mark exactly where the Min and Max thresholds sit on the arc.
 *
 * All drawing is wrapped in try/catch so a rendering edge case can never crash
 * the host activity (requirement #6).
 */
class GoalGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val tag = "GoalGaugeView"
    private val startAngle = 135f
    private val sweepTotal = 270f
    private val strokeWidth = dp(16f)

    private val currency: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "ZA"))

    private var status: GoalStatus? = null
    private var animatedProgress = 0f
    private var animator: ValueAnimator? = null

    private val arcRect = RectF()

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = this@GoalGaugeView.strokeWidth
        color = ContextCompat.getColor(context, R.color.divider)
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = this@GoalGaugeView.strokeWidth
    }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        color = ContextCompat.getColor(context, R.color.vault_text_secondary)
    }

    private val centerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val statusTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    /** Supplies the current status and animates the arc to its new value. */
    fun setStatus(newStatus: GoalStatus) {
        status = newStatus
        animator?.cancel()
        animator = ValueAnimator.ofFloat(animatedProgress, newStatus.progressToMax).apply {
            duration = 800
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                animatedProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        try {
            val pad = strokeWidth / 2f + dp(6f)
            val size = minOf(width, height).toFloat()
            val left = (width - size) / 2f + pad
            val top = (height - size) / 2f + pad
            arcRect.set(left, top, left + size - 2 * pad, top + size - 2 * pad)

            // Background track
            canvas.drawArc(arcRect, startAngle, sweepTotal, false, trackPaint)

            val s = status
            progressPaint.color = colorForZone(s?.zone ?: GoalZone.UNSET)
            val sweep = sweepTotal * animatedProgress
            if (sweep > 0f) {
                canvas.drawArc(arcRect, startAngle, sweep, false, progressPaint)
            }

            // Min / Max threshold tick marks
            if (s != null && s.max > 0) {
                drawTick(canvas, (s.min / s.max).toFloat())
                drawTick(canvas, 1f) // max is the end of the arc
            }

            drawCenterText(canvas, s)
        } catch (e: Exception) {
            Log.e(tag, "Gauge render error: ${e.message}", e)
        }
    }

    private fun drawTick(canvas: Canvas, fraction: Float) {
        val f = fraction.coerceIn(0f, 1f)
        val angleDeg = startAngle + sweepTotal * f
        val angleRad = Math.toRadians(angleDeg.toDouble())
        val cx = arcRect.centerX()
        val cy = arcRect.centerY()
        val outer = arcRect.width() / 2f + strokeWidth / 2f
        val inner = arcRect.width() / 2f - strokeWidth / 2f
        val sx = cx + (inner * Math.cos(angleRad)).toFloat()
        val sy = cy + (inner * Math.sin(angleRad)).toFloat()
        val ex = cx + (outer * Math.cos(angleRad)).toFloat()
        val ey = cy + (outer * Math.sin(angleRad)).toFloat()
        canvas.drawLine(sx, sy, ex, ey, tickPaint)
    }

    private fun drawCenterText(canvas: Canvas, s: GoalStatus?) {
        val cx = arcRect.centerX()
        val cy = arcRect.centerY()

        val textPrimaryColor = resolveThemeColor(android.R.attr.textColorPrimary)
        val textSecondaryColor = resolveThemeColor(android.R.attr.textColorSecondary)

        centerTextPaint.color = textPrimaryColor
        statusTextPaint.color = textSecondaryColor

        val w = arcRect.width()
        if (s == null || s.zone == GoalZone.UNSET) {
            centerTextPaint.textSize = w * 0.18f
            canvas.drawText("—", cx, cy + w * 0.05f, centerTextPaint)
            
            statusTextPaint.textSize = w * 0.07f
            statusTextPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("No goals set", cx, cy + w * 0.18f, statusTextPaint)
            return
        }

        val titleSize = w * 0.065f
        val amountSize = w * 0.14f
        val detailsSize = w * 0.055f
        val rangeSize = w * 0.055f

        // 1. Title: "Monthly Spend"
        statusTextPaint.textSize = titleSize
        statusTextPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Monthly Spend", cx, cy - w * 0.15f, statusTextPaint)

        // 2. Amount: e.g. "R12 500,00"
        centerTextPaint.textSize = amountSize
        val spentAmount = currency.format(s.spent)
        canvas.drawText(spentAmount, cx, cy + w * 0.02f, centerTextPaint)

        // 3. Target Range: "Target: R20 000,00 – R30 000,00"
        statusTextPaint.textSize = rangeSize
        val rangeText = "Target: ${currency.format(s.min)} – ${currency.format(s.max)}"
        canvas.drawText(rangeText, cx, cy + w * 0.14f, statusTextPaint)

        // 4. Percentage: e.g. "41% of Max Limit"
        statusTextPaint.textSize = detailsSize
        val percent = if (s.max > 0.0) "${((s.spent / s.max) * 100).toInt()}% of Max Limit" else ""
        canvas.drawText(percent, cx, cy + w * 0.22f, statusTextPaint)
    }

    private fun colorForZone(zone: GoalZone): Int {
        val res = when (zone) {
            GoalZone.SAFE -> R.color.vault_green
            GoalZone.NEAR_LIMIT, GoalZone.UNDER -> R.color.vault_amber
            GoalZone.BREACHED -> R.color.vault_red
            GoalZone.UNSET -> R.color.divider
        }
        return ContextCompat.getColor(context, res)
    }

    private fun resolveThemeColor(attr: Int): Int {
        return try {
            val tv = android.util.TypedValue()
            context.theme.resolveAttribute(attr, tv, true)
            if (tv.resourceId != 0) {
                ContextCompat.getColor(context, tv.resourceId)
            } else {
                tv.data
            }
        } catch (e: Exception) {
            ContextCompat.getColor(context, R.color.vault_text_primary)
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}

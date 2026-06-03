package com.iie.vaultquest.domain

/** Where current spend sits relative to the Min/Max band — drives the linear budget bar. */
enum class BudgetLevel { NONE, UNDER, ON_TRACK, CAUTION, OVER }

data class BudgetHealth(
    val level: BudgetLevel,
    val spent: Double,
    val min: Double,
    val max: Double,
    /** Spent as a fraction of the maximum, clamped 0f..1f (for the bar fill). */
    val fill: Float
) {
    val headline: String
        get() = when (level) {
            BudgetLevel.NONE -> "No budget set"
            BudgetLevel.UNDER -> "Under your minimum"
            BudgetLevel.ON_TRACK -> "On track"
            BudgetLevel.CAUTION -> "Close to your limit"
            BudgetLevel.OVER -> "Over budget"
        }
}

/**
 * Pure, side-effect-free assessment so it can be unit tested without Android.
 *   spent > max          -> OVER     (red)
 *   spent >= 85% of max  -> CAUTION  (amber)
 *   min <= spent < 85%   -> ON_TRACK (green)
 *   spent < min          -> UNDER    (amber)
 */
object BudgetHealthEvaluator {

    const val CAUTION_FRACTION = 0.85

    fun assess(spent: Double, min: Double, max: Double): BudgetHealth {
        if (max <= 0.0) return BudgetHealth(BudgetLevel.NONE, spent, min, max, 0f)
        val fill = (spent / max).toFloat().coerceIn(0f, 1f)
        val level = when {
            spent > max -> BudgetLevel.OVER
            spent >= max * CAUTION_FRACTION -> BudgetLevel.CAUTION
            spent >= min -> BudgetLevel.ON_TRACK
            else -> BudgetLevel.UNDER
        }
        return BudgetHealth(level, spent, min, max, fill)
    }
}

package com.iie.vaultquest.domain

/**
 * Where the user's current spend sits relative to their Min/Max monthly goals.
 * Drives the colour-coded dashboard gauge (Custom requirement #3).
 */
enum class GoalZone { UNSET, UNDER, SAFE, NEAR_LIMIT, BREACHED }

data class GoalStatus(
    val zone: GoalZone,
    val spent: Double,
    val min: Double,
    val max: Double,
    /** Fraction of the maximum goal already spent, clamped to 0f..1f (for the arc sweep). */
    val progressToMax: Float
) {
    val label: String
        get() = when (zone) {
            GoalZone.UNSET -> "No goal set"
            GoalZone.UNDER -> "Below minimum"
            GoalZone.SAFE -> "On track"
            GoalZone.NEAR_LIMIT -> "Near limit"
            GoalZone.BREACHED -> "Over budget"
        }
}

/**
 * Pure, side-effect-free evaluation so it can be unit tested without Android.
 *
 * Zone rules (Min/Max are the monthly spending thresholds):
 *   spent  >  max            -> BREACHED   (red)
 *   spent  >= 90% of max     -> NEAR_LIMIT (amber)
 *   min <= spent < 90% max   -> SAFE       (green)
 *   spent  <  min            -> UNDER      (amber)
 */
object GoalStatusCalculator {

    const val NEAR_LIMIT_FRACTION = 0.9

    fun evaluate(spent: Double, min: Double, max: Double): GoalStatus {
        if (max <= 0.0) {
            return GoalStatus(GoalZone.UNSET, spent, min, max, 0f)
        }
        val progress = (spent / max).toFloat().coerceIn(0f, 1f)
        val zone = when {
            spent > max -> GoalZone.BREACHED
            spent >= max * NEAR_LIMIT_FRACTION -> GoalZone.NEAR_LIMIT
            spent >= min -> GoalZone.SAFE
            else -> GoalZone.UNDER
        }
        return GoalStatus(zone, spent, min, max, progress)
    }
}

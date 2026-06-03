package com.iie.vaultquest.domain

import java.util.concurrent.TimeUnit

/** A single point on the cumulative spending-trend line. */
data class TrendPoint(val dayOffset: Int, val cumulative: Double)

/**
 * Builds the cumulative spend-over-time series for the Reports line chart.
 *
 * Given a list of (timestamp, amount) expenses and the period start, it buckets
 * by whole-day offset from the start and returns a running total per day — i.e.
 * the trajectory the user is spending along, which is then compared against the
 * Min/Max goal lines. Pure and unit-testable.
 */
object SpendTrend {

    fun cumulativeByDay(expenses: List<Pair<Long, Double>>, startMillis: Long): List<TrendPoint> {
        if (expenses.isEmpty()) return emptyList()

        val dailyTotals = sortedMapOf<Int, Double>()
        expenses.forEach { (ts, amount) ->
            val offset = dayOffset(startMillis, ts).coerceAtLeast(0)
            dailyTotals[offset] = (dailyTotals[offset] ?: 0.0) + amount
        }

        val points = ArrayList<TrendPoint>()
        var running = 0.0
        for ((offset, total) in dailyTotals) {
            running += total
            points.add(TrendPoint(offset, running))
        }
        return points
    }

    private fun dayOffset(startMillis: Long, ts: Long): Int =
        TimeUnit.MILLISECONDS.toDays(ts - startMillis).toInt()
}

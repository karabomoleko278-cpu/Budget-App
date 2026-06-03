package com.iie.vaultquest.domain

import java.util.Calendar

/** User-selectable reporting window for the analytical graphs (requirement #2). */
enum class ReportPeriod(val displayName: String) {
    DAY("Day"),
    WEEK("Week"),
    MONTH("Month")
}

/**
 * Computes the inclusive start-of-period timestamp for a [ReportPeriod].
 * Takes the "now" instant as a parameter so the logic is deterministic and
 * unit-testable rather than reading the system clock directly.
 */
object PeriodRange {

    fun startOf(period: ReportPeriod, nowMillis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        when (period) {
            ReportPeriod.DAY -> { /* already at start of today */ }
            ReportPeriod.WEEK -> {
                val currentDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                val firstDay = cal.firstDayOfWeek
                var diff = currentDayOfWeek - firstDay
                if (diff < 0) {
                    diff += 7
                }
                cal.add(Calendar.DAY_OF_YEAR, -diff)
            }
            ReportPeriod.MONTH -> cal.set(Calendar.DAY_OF_MONTH, 1)
        }
        return cal.timeInMillis
    }

    /** Start of the current calendar month — used by the dashboard goal gauge. */
    fun startOfMonth(nowMillis: Long = System.currentTimeMillis()): Long =
        startOf(ReportPeriod.MONTH, nowMillis)
}

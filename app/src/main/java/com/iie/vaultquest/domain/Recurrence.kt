package com.iie.vaultquest.domain

import java.util.Calendar

/** How often a recurring transaction repeats (Custom Feature 2). */
enum class Frequency(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly");

    companion object {
        fun from(value: String): Frequency =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: MONTHLY
    }
}

/** Pure date math for advancing a recurring rule's next due date. */
object Recurrence {

    fun advance(fromMillis: Long, frequency: String): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = fromMillis }
        when (Frequency.from(frequency)) {
            Frequency.DAILY -> cal.add(Calendar.DAY_OF_YEAR, 1)
            Frequency.WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, 1)
            Frequency.MONTHLY -> cal.add(Calendar.MONTH, 1)
        }
        return cal.timeInMillis
    }

    /**
     * Advances [fromMillis] repeatedly until it is strictly after [nowMillis],
     * so a rule that lapsed while the app was closed catches up to the next
     * future occurrence (without spamming one entry per missed period).
     */
    fun nextAfter(fromMillis: Long, nowMillis: Long, frequency: String): Long {
        var next = fromMillis
        var guard = 0
        while (next <= nowMillis && guard < 1000) {
            next = advance(next, frequency)
            guard++
        }
        return next
    }
}

package com.iie.vaultquest

import com.iie.vaultquest.data.Entry
import com.iie.vaultquest.domain.CsvBuilder
import com.iie.vaultquest.domain.GoalStatusCalculator
import com.iie.vaultquest.domain.GoalZone
import com.iie.vaultquest.domain.PeriodRange
import com.iie.vaultquest.domain.ReportPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure business logic that powers the gauge, the analytical
 * graphs and the CSV export. These run on the JVM in the GitHub Actions pipeline
 * (`./gradlew test`).
 */
class DomainLogicTest {

    // ---------- Goal gauge zone logic (Requirement #3) ----------

    @Test
    fun unsetWhenNoMaxGoal() {
        val status = GoalStatusCalculator.evaluate(spent = 500.0, min = 0.0, max = 0.0)
        assertEquals(GoalZone.UNSET, status.zone)
        assertEquals(0f, status.progressToMax, 0.001f)
    }

    @Test
    fun safeZoneBetweenMinAndMax() {
        val status = GoalStatusCalculator.evaluate(spent = 1500.0, min = 1000.0, max = 3000.0)
        assertEquals(GoalZone.SAFE, status.zone)
        assertEquals(0.5f, status.progressToMax, 0.001f)
    }

    @Test
    fun underWhenBelowMinimum() {
        val status = GoalStatusCalculator.evaluate(spent = 400.0, min = 1000.0, max = 3000.0)
        assertEquals(GoalZone.UNDER, status.zone)
    }

    @Test
    fun nearLimitWithinNinetyPercentOfMax() {
        val status = GoalStatusCalculator.evaluate(spent = 2800.0, min = 1000.0, max = 3000.0)
        assertEquals(GoalZone.NEAR_LIMIT, status.zone)
    }

    @Test
    fun breachedWhenOverMaximum() {
        val status = GoalStatusCalculator.evaluate(spent = 3500.0, min = 1000.0, max = 3000.0)
        assertEquals(GoalZone.BREACHED, status.zone)
        assertEquals(1f, status.progressToMax, 0.001f) // clamped to 1.0
    }

    // ---------- CSV export (Custom Feature 2) ----------

    @Test
    fun csvHasHeaderAndRowPerEntry() {
        val entries = listOf(
            Entry(1, 1, 10, 0L, "10:00", "10:00", "Coffee", 25.0, null, false),
            Entry(2, 1, 11, 0L, "12:00", "12:00", "Salary", 5000.0, null, true)
        )
        val names = mapOf(10L to "Food", 11L to "Income")
        val csv = CsvBuilder.build(entries, names)
        val lines = csv.trim().split("\n")

        assertEquals("Date,Type,Category,Description,Amount", lines[0])
        assertEquals(3, lines.size) // header + 2 rows
        assertTrue(csv.contains("Expense"))
        assertTrue(csv.contains("Income"))
        assertTrue(csv.contains("25.00"))
    }

    @Test
    fun csvEscapesCommasInFields() {
        val entries = listOf(
            Entry(1, 1, 10, 0L, "10:00", "10:00", "Lunch, drinks", 80.0, null, false)
        )
        val csv = CsvBuilder.build(entries, mapOf(10L to "Food"))
        assertTrue("Comma field must be quoted", csv.contains("\"Lunch, drinks\""))
    }

    // ---------- Reporting period ranges (Requirement #2) ----------

    @Test
    fun periodStartsAreNeverInTheFuture() {
        val now = System.currentTimeMillis()
        val day = PeriodRange.startOf(ReportPeriod.DAY, now)
        val week = PeriodRange.startOf(ReportPeriod.WEEK, now)
        val month = PeriodRange.startOf(ReportPeriod.MONTH, now)

        assertTrue(day <= now)
        assertTrue(week <= day)   // week start is on/before today
        assertTrue(month <= day)  // month start is on/before today
    }
}

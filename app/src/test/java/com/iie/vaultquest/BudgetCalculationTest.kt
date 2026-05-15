package com.iie.vaultquest

import com.iie.vaultquest.data.Category
import com.iie.vaultquest.data.Entry
import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetCalculationTest {

    @Test
    fun testTotalCalculation() {
        val entries = listOf(
            Entry(1, 1, 1, 0L, "10:00", "11:00", "Coffee", 25.0, null),
            Entry(2, 1, 1, 0L, "12:00", "13:00", "Lunch", 75.0, null)
        )
        
        val total = entries.sumOf { it.amount }
        assertEquals(100.0, total, 0.001)
    }

    @Test
    fun testCategoryFiltering() {
        val entries = listOf(
            Entry(1, 1, 1, 0L, "10:00", "11:00", "Coffee", 25.0, null),
            Entry(2, 1, 2, 0L, "12:00", "13:00", "Bus", 15.0, null)
        )
        
        val foodEntries = entries.filter { it.categoryId == 1L }
        assertEquals(1, foodEntries.size)
        assertEquals(25.0, foodEntries[0].amount, 0.001)
    }
}

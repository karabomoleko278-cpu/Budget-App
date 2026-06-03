package com.iie.vaultquest.work

import android.util.Log
import com.iie.vaultquest.data.AppDao
import com.iie.vaultquest.data.Entry
import com.iie.vaultquest.data.RealtimeSyncManager
import com.iie.vaultquest.domain.Recurrence

/**
 * Materialises due [com.iie.vaultquest.data.RecurringTransaction] rules into real
 * [Entry] rows (Custom Feature 2). Runs both on app launch (guaranteed catch-up)
 * and from a daily WorkManager job (background). Idempotent enough to run often:
 * a rule only fires while its `nextDueDate` is in the past, then advances to the
 * next future occurrence.
 */
object RecurringEngine {

    private const val TAG = "RecurringEngine"

    suspend fun processDue(dao: AppDao, nowMillis: Long = System.currentTimeMillis()): Int {
        var created = 0
        try {
            val due = dao.getDueRecurring(nowMillis)
            Log.d(TAG, "Processing ${due.size} due recurring rule(s)")
            for (rule in due) {
                try {
                    val entry = Entry(
                        userId = rule.userId,
                        categoryId = rule.categoryId,
                        date = rule.nextDueDate,
                        startTime = "00:00",
                        endTime = "00:00",
                        description = "${rule.description} (recurring)",
                        amount = rule.amount,
                        photoPath = null,
                        isIncome = rule.isIncome
                    )
                    val id = dao.insertEntry(entry)
                    RealtimeSyncManager.pushEntry(entry.copy(id = id))

                    val next = Recurrence.nextAfter(rule.nextDueDate, nowMillis, rule.frequency)
                    dao.updateRecurring(rule.copy(nextDueDate = next))
                    created++
                    Log.d(TAG, "Posted recurring '${rule.description}', next due $next")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to post recurring rule ${rule.id}: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "processDue failed: ${e.message}", e)
        }
        return created
    }
}

package com.iie.budgetly.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.iie.budgetly.data.AppDatabase

/**
 * Daily background job that posts any due recurring transactions even when the
 * app isn't open (Custom Feature 2). Scheduled from BudgetlyApp via WorkManager.
 */
class RecurringWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val dao = AppDatabase.getDatabase(applicationContext).appDao()
            val created = RecurringEngine.processDue(dao)
            Log.d(TAG, "RecurringWorker created $created entries")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "RecurringWorker failed: ${e.message}", e)
            Result.success() // don't retry-storm; next daily run will catch up
        }
    }

    companion object {
        private const val TAG = "RecurringWorker"
        const val UNIQUE_NAME = "budgetly_recurring_daily"
    }
}

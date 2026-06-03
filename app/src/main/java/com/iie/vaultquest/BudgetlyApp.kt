package com.iie.vaultquest

import android.app.Application
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase
import com.iie.vaultquest.ui.NotificationHelper
import com.iie.vaultquest.work.RecurringWorker
import java.util.concurrent.TimeUnit

/**
 * Application entry point for Budgetly.
 *
 *  - Initialises Firebase and enables **Realtime Database disk persistence**, so
 *    reads/writes work offline and sync automatically on reconnect.
 *  - Creates the Budget Alerts notification channel (Custom Feature 1).
 *  - Schedules the daily recurring-transactions worker (Custom Feature 2).
 *
 * All start-up work is defensive — a Firebase/WorkManager hiccup never takes the
 * app down; it simply runs against the local Room cache.
 */
class BudgetlyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        initFirebase()
        NotificationHelper.createChannel(this)
        scheduleRecurringWorker()
    }

    private fun initFirebase() {
        try {
            FirebaseApp.initializeApp(this)
            // Must be set once, before any other Realtime Database usage.
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
            Log.d(TAG, "Firebase + Realtime Database persistence initialised")
        } catch (e: Exception) {
            Log.e(TAG, "Firebase init failed — running in local-only mode: ${e.message}", e)
        }
    }

    private fun scheduleRecurringWorker() {
        try {
            val request = PeriodicWorkRequestBuilder<RecurringWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                RecurringWorker.UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "Recurring-transactions worker scheduled (daily)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule recurring worker: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "BudgetlyApp"
    }
}

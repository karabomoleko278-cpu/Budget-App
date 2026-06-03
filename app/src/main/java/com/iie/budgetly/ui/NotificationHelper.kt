package com.iie.budgetly.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.iie.budgetly.R
import com.iie.budgetly.domain.BudgetHealthEvaluator
import com.iie.budgetly.domain.BudgetLevel
import java.text.NumberFormat
import java.util.Locale

/**
 * Custom Feature 1 — Budget Notifications.
 *
 * Posts a local notification when this month's spending gets close to (amber) or
 * exceeds (red) the user's maximum monthly goal. Called after an expense is saved.
 */
object NotificationHelper {

    private const val TAG = "NotificationHelper"
    const val CHANNEL_ID = "budget_alerts"
    private const val NOTIFICATION_ID = 4711
    private val currency = NumberFormat.getCurrencyInstance(Locale("en", "ZA"))

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Budget Alerts",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Warns you when you near or exceed your monthly budget" }
                context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
            } catch (e: Exception) {
                Log.e(TAG, "createChannel failed: ${e.message}", e)
            }
        }
    }

    /** Evaluates spend vs goals and posts an alert only when caution/over. */
    fun notifyBudgetStatus(context: Context, spent: Double, min: Double, max: Double) {
        try {
            val health = BudgetHealthEvaluator.assess(spent, min, max)
            val (title, text) = when (health.level) {
                BudgetLevel.OVER ->
                    "🔴 Over budget" to "You've spent ${currency.format(spent)} — over your ${currency.format(max)} limit."
                BudgetLevel.CAUTION ->
                    "⚠️ Close to your limit" to "You've spent ${currency.format(spent)} of ${currency.format(max)} this month."
                else -> return // on-track / under / none → no alert
            }
            post(context, title, text)
        } catch (e: Exception) {
            Log.e(TAG, "notifyBudgetStatus failed: ${e.message}", e)
        }
    }

    private fun post(context: Context, title: String, text: String) {
        // On Android 13+ posting requires the runtime POST_NOTIFICATIONS permission.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "POST_NOTIFICATIONS not granted — skipping alert")
            return
        }
        try {
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_target)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            Log.d(TAG, "Posted budget alert: $title")
        } catch (e: Exception) {
            Log.e(TAG, "post notification failed: ${e.message}", e)
        }
    }
}

package com.iie.vaultquest.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Lightweight per-user app settings (not transactional financial records):
 *  - the last authenticated user
 *  - whether Budget Notifications (Custom Feature 1) are enabled
 *  - the overall monthly Min/Max spending thresholds that drive the dashboard
 *    budget bar and the Reports goal lines. Stored here (not in the `goals`
 *    table) so they are not bound by the category foreign key, and mirrored to
 *    the Realtime Database.
 */
class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var areNotificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATIONS, value).apply()

    fun saveUser(userId: Long, username: String) {
        prefs.edit()
            .putLong(KEY_USER_ID, userId)
            .putString(KEY_USERNAME, username)
            .apply()
    }

    val savedUserId: Long get() = prefs.getLong(KEY_USER_ID, -1L)
    val savedUsername: String get() = prefs.getString(KEY_USERNAME, "") ?: ""

    // ---------------------------------------------------------------------
    // Overall monthly Min / Max spending goals (per user).
    // ---------------------------------------------------------------------
    fun setOverallGoals(userId: Long, min: Double, max: Double) {
        prefs.edit()
            .putString(keyMin(userId), min.toString())
            .putString(keyMax(userId), max.toString())
            .apply()
    }

    fun getOverallMin(userId: Long): Double =
        prefs.getString(keyMin(userId), null)?.toDoubleOrNull() ?: 0.0

    fun getOverallMax(userId: Long): Double =
        prefs.getString(keyMax(userId), null)?.toDoubleOrNull() ?: 0.0

    fun clearSession() {
        prefs.edit()
            .remove(KEY_USER_ID)
            .remove(KEY_USERNAME)
            .apply()
    }

    private fun keyMin(userId: Long) = "goal_min_$userId"
    private fun keyMax(userId: Long) = "goal_max_$userId"

    companion object {
        private const val PREFS_NAME = "budgetly_session"
        private const val KEY_NOTIFICATIONS = "notifications_enabled"
        private const val KEY_USER_ID = "saved_user_id"
        private const val KEY_USERNAME = "saved_username"
    }
}

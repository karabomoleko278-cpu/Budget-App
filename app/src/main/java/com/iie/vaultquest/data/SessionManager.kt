package com.iie.vaultquest.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Lightweight wrapper around [SharedPreferences] for per-user app settings
 * (not transactional financial records — those live in Room + Firestore):
 *  - whether the Biometric App Lock (Custom Feature 1) is enabled
 *  - the last authenticated user, so we can re-unlock biometrically without
 *    retyping the password
 *  - the overall monthly Min/Max spending thresholds that drive the dashboard
 *    gauge and the Reports goal lines. These are user-level thresholds (not tied
 *    to a category), so storing them here avoids the category foreign-key
 *    constraint on the `goals` table — they are also mirrored to Firestore.
 */
class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isBiometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC, false)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC, value).apply()

    /** Persist the last successfully authenticated user for biometric re-entry. */
    fun saveUser(userId: Long, username: String) {
        prefs.edit()
            .putLong(KEY_USER_ID, userId)
            .putString(KEY_USERNAME, username)
            .apply()
    }

    val savedUserId: Long get() = prefs.getLong(KEY_USER_ID, -1L)
    val savedUsername: String get() = prefs.getString(KEY_USERNAME, "") ?: ""
    fun hasSavedUser(): Boolean = savedUserId != -1L

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

    fun hasOverallGoals(userId: Long): Boolean = getOverallMax(userId) > 0.0

    /** Called on explicit logout — clears the biometric shortcut (goals persist per user). */
    fun clearSession() {
        prefs.edit()
            .remove(KEY_USER_ID)
            .remove(KEY_USERNAME)
            .apply()
    }

    private fun keyMin(userId: Long) = "goal_min_$userId"
    private fun keyMax(userId: Long) = "goal_max_$userId"

    companion object {
        private const val PREFS_NAME = "vault_session"
        private const val KEY_BIOMETRIC = "biometric_enabled"
        private const val KEY_USER_ID = "saved_user_id"
        private const val KEY_USERNAME = "saved_username"
    }
}

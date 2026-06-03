package com.iie.budgetly.data

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Offline-first synchronisation engine backed by **Firebase Realtime Database**
 * (a JSON tree), distinct from a document store.
 *
 * Room remains the single source of truth the UI observes; the Realtime Database
 * is a cloud mirror. Writes are fire-and-forget (`setValue` returns immediately
 * and completes on a worker thread) so the UI never blocks. With disk
 * persistence enabled in [com.iie.budgetly.BudgetlyApp], writes are buffered
 * while offline and flushed automatically on reconnect.
 *
 * Tree layout:
 *   users/{userId}/profile            { username, minGoal, maxGoal }
 *   users/{userId}/categories/{id}    { name }
 *   users/{userId}/entries/{id}       { ... }
 *   users/{userId}/goals/{id}         { ... }
 */
object RealtimeSyncManager {

    private const val TAG = "RealtimeSync"

    private val root: DatabaseReference? by lazy {
        try {
            FirebaseDatabase.getInstance().reference
        } catch (e: Exception) {
            Log.e(TAG, "Realtime Database unavailable — local-only mode: ${e.message}", e)
            null
        }
    }

    private fun userRef(userId: Long) = root?.child("users")?.child(userId.toString())

    // ---------------------------------------------------------------------
    // WRITE (non-blocking)
    // ---------------------------------------------------------------------
    fun pushUser(user: User) = safePush("user ${user.id}") {
        userRef(user.id)?.child("profile")?.child("username")?.setValue(user.username)
    }

    fun pushOverallGoal(userId: Long, min: Double, max: Double) = safePush("overall-goal $userId") {
        userRef(userId)?.child("profile")?.updateChildren(mapOf("minGoal" to min, "maxGoal" to max))
    }

    fun pushCategory(category: Category) = safePush("category ${category.id}") {
        userRef(category.userId)?.child("categories")?.child(category.id.toString())
            ?.setValue(mapOf("name" to category.name))
    }

    fun pushEntry(entry: Entry) = safePush("entry ${entry.id}") {
        userRef(entry.userId)?.child("entries")?.child(entry.id.toString())?.setValue(
            mapOf(
                "categoryId" to entry.categoryId,
                "date" to entry.date,
                "startTime" to entry.startTime,
                "endTime" to entry.endTime,
                "description" to entry.description,
                "amount" to entry.amount,
                "photoPath" to entry.photoPath,
                "isIncome" to entry.isIncome
            )
        )
    }

    fun pushGoal(goal: Goal) = safePush("goal ${goal.id}") {
        userRef(goal.userId)?.child("goals")?.child(goal.id.toString())?.setValue(
            mapOf(
                "categoryId" to goal.categoryId,
                "amount" to goal.amount,
                "minGoal" to goal.minGoal,
                "maxGoal" to goal.maxGoal
            )
        )
    }

    private inline fun safePush(label: String, block: () -> com.google.android.gms.tasks.Task<Void>?) {
        try {
            block()
                ?.addOnSuccessListener { Log.d(TAG, "Synced $label to cloud") }
                ?.addOnFailureListener { e -> Log.e(TAG, "Cloud sync queued/failed for $label: ${e.message}") }
        } catch (e: Exception) {
            Log.e(TAG, "Push error for $label: ${e.message}", e)
        }
    }

    // ---------------------------------------------------------------------
    // BACKUP — bulk upload local data (after login)
    // ---------------------------------------------------------------------
    suspend fun backupAll(userId: Long, dao: AppDao) = withContext(Dispatchers.IO) {
        try {
            dao.getUserById(userId)?.let { pushUser(it) }
            dao.getCategoriesOnce(userId).forEach { pushCategory(it) }
            dao.getEntriesOnce(userId).forEach { pushEntry(it) }
            dao.getGoalsOnce(userId).forEach { pushGoal(it) }
            Log.d(TAG, "backupAll dispatched for user $userId")
        } catch (e: Exception) {
            Log.e(TAG, "backupAll failed: ${e.message}", e)
        }
    }

    // ---------------------------------------------------------------------
    // PULL — hydrate Room from the cloud (idempotent upserts)
    // ---------------------------------------------------------------------
    suspend fun pullAll(userId: Long, dao: AppDao) = withContext(Dispatchers.IO) {
        val ref = userRef(userId) ?: run {
            Log.w(TAG, "pullAll skipped — Realtime Database not available")
            return@withContext
        }
        try {
            ref.child("categories").get().await().children.forEach { snap ->
                val id = snap.key?.toLongOrNull() ?: return@forEach
                dao.upsertCategory(Category(id = id, userId = userId, name = snap.str("name")))
            }

            ref.child("entries").get().await().children.forEach { snap ->
                val id = snap.key?.toLongOrNull() ?: return@forEach
                dao.upsertEntry(
                    Entry(
                        id = id,
                        userId = userId,
                        categoryId = snap.lng("categoryId"),
                        date = snap.lng("date"),
                        startTime = snap.str("startTime"),
                        endTime = snap.str("endTime"),
                        description = snap.str("description"),
                        amount = snap.dbl("amount"),
                        photoPath = snap.strOrNull("photoPath"),
                        isIncome = snap.bool("isIncome")
                    )
                )
            }

            ref.child("goals").get().await().children.forEach { snap ->
                val id = snap.key?.toLongOrNull() ?: return@forEach
                dao.upsertGoal(
                    Goal(
                        id = id,
                        userId = userId,
                        categoryId = snap.lng("categoryId"),
                        amount = snap.dbl("amount"),
                        minGoal = snap.dbl("minGoal"),
                        maxGoal = snap.dbl("maxGoal")
                    )
                )
            }
            Log.d(TAG, "pullAll completed for user $userId")
        } catch (e: Exception) {
            Log.e(TAG, "pullAll failed (using local cache): ${e.message}", e)
        }
    }

    // ---- robust readers (RTDB returns numbers as Long or Double) ----
    private fun DataSnapshot.dbl(field: String) = (child(field).value as? Number)?.toDouble() ?: 0.0
    private fun DataSnapshot.lng(field: String) = (child(field).value as? Number)?.toLong() ?: 0L
    private fun DataSnapshot.str(field: String) = child(field).value as? String ?: ""
    private fun DataSnapshot.strOrNull(field: String) = child(field).value as? String
    private fun DataSnapshot.bool(field: String) = child(field).value as? Boolean ?: false
}

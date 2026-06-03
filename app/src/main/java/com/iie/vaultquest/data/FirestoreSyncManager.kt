package com.iie.vaultquest.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Offline-first synchronisation engine.
 *
 * Room is the single source of truth the UI observes; Firestore is a cloud
 * mirror. Writes are fire-and-forget (the UI thread never blocks); while offline
 * the Firestore SDK queues them and flushes automatically on reconnect.
 *
 * Document layout (per user):
 *   users/{userId}                       (also holds overall minGoal / maxGoal)
 *   users/{userId}/categories/{id}
 *   users/{userId}/entries/{id}
 *   users/{userId}/goals/{id}
 *
 * Passwords are never written to the cloud — only the username.
 */
object FirestoreSyncManager {

    private const val TAG = "FirestoreSync"
    private const val USERS = "users"
    private const val CATEGORIES = "categories"
    private const val ENTRIES = "entries"
    private const val GOALS = "goals"

    private val firestore: FirebaseFirestore? by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.e(TAG, "Firestore unavailable — local-only mode: ${e.message}", e)
            null
        }
    }

    private fun userDoc(userId: Long) =
        firestore?.collection(USERS)?.document(userId.toString())

    // ---------------------------------------------------------------------
    // WRITE: mirror a single local row to the cloud (non-blocking).
    // ---------------------------------------------------------------------
    fun pushUser(user: User) = safePush("user ${user.id}") {
        userDoc(user.id)?.set(
            mapOf("username" to user.username, "updatedAt" to System.currentTimeMillis()),
            SetOptions.merge()
        )
    }

    fun pushCategory(category: Category) = safePush("category ${category.id}") {
        userDoc(category.userId)?.collection(CATEGORIES)?.document(category.id.toString())
            ?.set(mapOf("name" to category.name))
    }

    fun pushEntry(entry: Entry) = safePush("entry ${entry.id}") {
        userDoc(entry.userId)?.collection(ENTRIES)?.document(entry.id.toString())?.set(
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
        userDoc(goal.userId)?.collection(GOALS)?.document(goal.id.toString())?.set(
            mapOf(
                "categoryId" to goal.categoryId,
                "amount" to goal.amount,
                "minGoal" to goal.minGoal,
                "maxGoal" to goal.maxGoal
            )
        )
    }

    /** Overall monthly thresholds live on the user document (not the goals table). */
    fun pushOverallGoal(userId: Long, min: Double, max: Double) = safePush("overall-goal $userId") {
        userDoc(userId)?.set(
            mapOf("minGoal" to min, "maxGoal" to max),
            SetOptions.merge()
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
    // BACKUP: bulk push the whole local dataset (called after login).
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
    // PULL: hydrate Room from the cloud (idempotent upserts keyed by id).
    // ---------------------------------------------------------------------
    suspend fun pullAll(userId: Long, dao: AppDao) = withContext(Dispatchers.IO) {
        val root = userDoc(userId) ?: run {
            Log.w(TAG, "pullAll skipped — Firestore not available")
            return@withContext
        }
        try {
            root.collection(CATEGORIES).get().await().documents.forEach { doc ->
                val id = doc.id.toLongOrNull() ?: return@forEach
                dao.upsertCategory(Category(id = id, userId = userId, name = doc.getString("name").orEmpty()))
            }

            root.collection(ENTRIES).get().await().documents.forEach { doc ->
                val id = doc.id.toLongOrNull() ?: return@forEach
                dao.upsertEntry(
                    Entry(
                        id = id,
                        userId = userId,
                        categoryId = doc.getLong("categoryId") ?: 0L,
                        date = doc.getLong("date") ?: 0L,
                        startTime = doc.getString("startTime").orEmpty(),
                        endTime = doc.getString("endTime").orEmpty(),
                        description = doc.getString("description").orEmpty(),
                        amount = doc.getDouble("amount") ?: 0.0,
                        photoPath = doc.getString("photoPath"),
                        isIncome = doc.getBoolean("isIncome") ?: false
                    )
                )
            }

            root.collection(GOALS).get().await().documents.forEach { doc ->
                val id = doc.id.toLongOrNull() ?: return@forEach
                dao.upsertGoal(
                    Goal(
                        id = id,
                        userId = userId,
                        categoryId = doc.getLong("categoryId") ?: 0L,
                        amount = doc.getDouble("amount") ?: 0.0,
                        minGoal = doc.getDouble("minGoal") ?: 0.0,
                        maxGoal = doc.getDouble("maxGoal") ?: 0.0
                    )
                )
            }
            Log.d(TAG, "pullAll completed for user $userId")
        } catch (e: Exception) {
            Log.e(TAG, "pullAll failed (using local cache): ${e.message}", e)
        }
    }
}

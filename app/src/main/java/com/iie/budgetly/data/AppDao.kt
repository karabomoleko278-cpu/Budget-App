package com.iie.budgetly.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    // ---------------------------------------------------------------------
    // User operations
    // ---------------------------------------------------------------------
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertUser(user: User): Long

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): User?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: Long): User?

    // ---------------------------------------------------------------------
    // Category operations
    // ---------------------------------------------------------------------
    @Insert
    suspend fun insertCategory(category: Category): Long

    @Query("SELECT * FROM categories WHERE userId = :userId")
    fun getCategoriesForUser(userId: Long): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE userId = :userId")
    suspend fun getCategoriesOnce(userId: Long): List<Category>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: Long): Category?

    // ---------------------------------------------------------------------
    // Entry operations
    // ---------------------------------------------------------------------
    @Insert
    suspend fun insertEntry(entry: Entry): Long

    @Query("SELECT * FROM entries WHERE userId = :userId")
    fun getEntriesForUser(userId: Long): Flow<List<Entry>>

    @Query("SELECT * FROM entries WHERE userId = :userId")
    suspend fun getEntriesOnce(userId: Long): List<Entry>

    @Query("SELECT * FROM entries WHERE userId = :userId AND date BETWEEN :startDate AND :endDate")
    fun getEntriesForPeriod(userId: Long, startDate: Long, endDate: Long): Flow<List<Entry>>

    // ---------------------------------------------------------------------
    // Goal operations (per-category budgets)
    // ---------------------------------------------------------------------
    @Upsert
    suspend fun upsertGoal(goal: Goal): Long

    @Query("SELECT * FROM goals WHERE userId = :userId")
    fun getGoalsForUser(userId: Long): Flow<List<Goal>>

    @Query("SELECT * FROM goals WHERE userId = :userId")
    suspend fun getGoalsOnce(userId: Long): List<Goal>

    @Query("SELECT * FROM goals WHERE userId = :userId AND categoryId = :categoryId LIMIT 1")
    suspend fun getGoalForCategory(userId: Long, categoryId: Long): Goal?

    // ---------------------------------------------------------------------
    // Recurring transactions (Custom Feature 2)
    // ---------------------------------------------------------------------
    @Insert
    suspend fun insertRecurring(rule: RecurringTransaction): Long

    @Update
    suspend fun updateRecurring(rule: RecurringTransaction): Int

    @Delete
    suspend fun deleteRecurring(rule: RecurringTransaction): Int

    @Query("SELECT * FROM recurring WHERE userId = :userId AND active = 1")
    fun getRecurringForUser(userId: Long): Flow<List<RecurringTransaction>>

    /** Rules whose next occurrence is due (used by the scheduler). */
    @Query("SELECT * FROM recurring WHERE active = 1 AND nextDueDate <= :now")
    suspend fun getDueRecurring(now: Long): List<RecurringTransaction>

    // ---------------------------------------------------------------------
    // Upsert helpers used by the Realtime Database -> Room pull (idempotent).
    // ---------------------------------------------------------------------
    @Upsert
    suspend fun upsertUser(user: User): Long

    @Upsert
    suspend fun upsertCategory(category: Category): Long

    @Upsert
    suspend fun upsertEntry(entry: Entry): Long
}

package com.iie.vaultquest.data

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
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setGoals(goal: Goal): Long

    @Query("SELECT * FROM goals WHERE userId = :userId")
    fun getGoalsForUser(userId: Long): Flow<List<Goal>>

    @Query("SELECT * FROM goals WHERE userId = :userId")
    suspend fun getGoalsOnce(userId: Long): List<Goal>

    @Query("SELECT * FROM goals WHERE userId = :userId AND categoryId = :categoryId LIMIT 1")
    suspend fun getGoalForCategory(userId: Long, categoryId: Long): Goal?

    // ---------------------------------------------------------------------
    // Upsert helpers used by the Firestore -> Room pull (idempotent merge).
    // ---------------------------------------------------------------------
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUser(user: User)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(category: Category)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntry(entry: Entry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGoal(goal: Goal)
}

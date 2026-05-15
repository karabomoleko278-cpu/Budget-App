package com.iie.vaultquest.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    // User operations
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertUser(user: User): Long

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): User?

    // Category operations
    @Insert
    suspend fun insertCategory(category: Category): Long

    @Query("SELECT * FROM categories WHERE userId = :userId")
    fun getCategoriesForUser(userId: Long): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: Long): Category?

    // Entry operations
    @Insert
    suspend fun insertEntry(entry: Entry)

    @Query("SELECT * FROM entries WHERE userId = :userId")
    fun getEntriesForUser(userId: Long): Flow<List<Entry>>

    @Query("SELECT * FROM entries WHERE userId = :userId AND date BETWEEN :startDate AND :endDate")
    fun getEntriesForPeriod(userId: Long, startDate: Long, endDate: Long): Flow<List<Entry>>

    // Goal operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setGoals(goal: Goal)

    @Query("SELECT * FROM goals WHERE userId = :userId")
    fun getGoalsForUser(userId: Long): Flow<List<Goal>>

    @Query("SELECT * FROM goals WHERE userId = :userId AND categoryId = :categoryId LIMIT 1")
    suspend fun getGoalForCategory(userId: Long, categoryId: Long): Goal?
}

package com.iie.vaultquest.data

import kotlinx.coroutines.flow.Flow

class AppRepository(private val appDao: AppDao) {
    
    suspend fun insertUser(user: User) = appDao.insertUser(user)
    suspend fun getUserByUsername(username: String) = appDao.getUserByUsername(username)

    suspend fun insertCategory(category: Category) = appDao.insertCategory(category)
    fun getCategoriesForUser(userId: Long) = appDao.getCategoriesForUser(userId)
    suspend fun getCategoryById(id: Long) = appDao.getCategoryById(id)

    suspend fun insertEntry(entry: Entry) = appDao.insertEntry(entry)
    fun getEntriesForPeriod(userId: Long, startDate: Long, endDate: Long) = 
        appDao.getEntriesForPeriod(userId, startDate, endDate)

    suspend fun setGoals(goal: Goal) = appDao.setGoals(goal)
    fun getGoalsForUser(userId: Long) = appDao.getGoalsForUser(userId)
}

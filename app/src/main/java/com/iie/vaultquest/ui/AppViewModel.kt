package com.iie.vaultquest.ui

import androidx.lifecycle.*
import com.iie.vaultquest.data.*
import kotlinx.coroutines.launch

class AppViewModel(private val repository: AppRepository) : ViewModel() {

    private val _currentUser = MutableLiveData<User?>()
    val currentUser: LiveData<User?> = _currentUser

    fun login(user: User) {
        _currentUser.value = user
    }

    fun logout() {
        _currentUser.value = null
    }

    // Proxy methods to repository
    fun getCategories(userId: Long) = repository.getCategoriesForUser(userId).asLiveData()
    
    fun getEntries(userId: Long, start: Long, end: Long) = 
        repository.getEntriesForPeriod(userId, start, end).asLiveData()
        
    fun getGoals(userId: Long) = repository.getGoalsForUser(userId).asLiveData()

    fun addCategory(userId: Long, name: String) {
        viewModelScope.launch {
            repository.insertCategory(Category(userId = userId, name = name))
        }
    }

    fun addEntry(entry: Entry) {
        viewModelScope.launch {
            repository.insertEntry(entry)
        }
    }

    fun setGoals(userId: Long, min: Double, max: Double) {
        viewModelScope.launch {
            repository.setGoals(Goal(userId = userId, minGoal = min, maxGoal = max))
        }
    }
}

class AppViewModelFactory(private val repository: AppRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AppViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

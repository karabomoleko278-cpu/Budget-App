package com.iie.vaultquest

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.iie.vaultquest.data.AppDatabase
import com.iie.vaultquest.databinding.ActivityMainBinding
import com.iie.vaultquest.ui.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val db by lazy { AppDatabase.getDatabase(this) }
    private var userId: Long = -1
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "ZA"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userId = intent.getLongExtra("USER_ID", -1)
        val username = intent.getStringExtra("USERNAME") ?: "User"

        if (userId == -1L) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding.welcomeText.text = username

        setupClickListeners()
        updateDashboard()
    }

    override fun onResume() {
        super.onResume()
        updateDashboard()
    }

    private fun setupClickListeners() {
        binding.btnLogout.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
        binding.btnAddExpense.setOnClickListener {
            startActivity(Intent(this, AddEntryActivity::class.java).apply {
                putExtra("USER_ID", userId)
                putExtra("IS_INCOME", false)
            })
        }
        binding.btnAddIncome.setOnClickListener {
            startActivity(Intent(this, AddEntryActivity::class.java).apply {
                putExtra("USER_ID", userId)
                putExtra("IS_INCOME", true)
            })
        }
        
        binding.tabHome.setOnClickListener { /* Already here */ }
        binding.tabBudget.setOnClickListener {
            startActivity(Intent(this, EntryListActivity::class.java).putExtra("USER_ID", userId))
        }
        binding.tabReports.setOnClickListener {
            startActivity(Intent(this, ReportsActivity::class.java).putExtra("USER_ID", userId))
        }
        binding.tabProfile.setOnClickListener {
            startActivity(Intent(this, GoalSettingsActivity::class.java).putExtra("USER_ID", userId))
        }
        
        binding.btnViewAll.setOnClickListener {
            startActivity(Intent(this, EntryListActivity::class.java).putExtra("USER_ID", userId))
        }

        binding.btnManageGoals.setOnClickListener {
            startActivity(Intent(this, GoalSettingsActivity::class.java).putExtra("USER_ID", userId))
        }

        binding.btnSetGoalNow.setOnClickListener {
            startActivity(Intent(this, GoalSettingsActivity::class.java).putExtra("USER_ID", userId))
        }
    }

    private fun updateDashboard() {
        lifecycleScope.launch {
            combine(
                db.appDao().getEntriesForUser(userId),
                db.appDao().getGoalsForUser(userId),
                db.appDao().getCategoriesForUser(userId)
            ) { entries, goals, categories ->
                Triple(entries, goals, categories)
            }.collect { (entries, goals, categories) ->
                val currentMonthEntries = entries.filter { isCurrentMonth(it.date) }
                
                val totalIncome = currentMonthEntries.filter { it.isIncome }.sumOf { it.amount }
                val totalExpenses = currentMonthEntries.filter { !it.isIncome }.sumOf { it.amount }
                val savings = totalIncome - totalExpenses

                binding.totalSpent.text = currencyFormat.format(totalIncome - totalExpenses)
                binding.incomeValue.text = currencyFormat.format(totalIncome)
                binding.expensesValue.text = currencyFormat.format(totalExpenses)
                binding.savingsValue.text = currencyFormat.format(savings)

                val totalBudget = goals.sumOf { it.amount }

                if (totalBudget > 0) {
                    val status = when {
                        totalExpenses > totalBudget -> "📉 Over budget! Save more next month."
                        totalExpenses < totalBudget * 0.5 -> "🏆 Savings Master: Under goal!"
                        else -> "⭐ On Track: Budgeting like a pro!"
                    }
                    binding.goalStatus.text = status
                    binding.dashboardGoalText.text = "Monthly Budget: ${currencyFormat.format(totalBudget)}\nCurrently spent: ${currencyFormat.format(totalExpenses)}"
                } else {
                    binding.goalStatus.text = "Goal: Not Set"
                    binding.dashboardGoalText.text = "No monthly budget set. Start tracking to save more!"
                }

                // Update recent transactions
                val recent = entries.sortedByDescending { it.date }.take(5)
                val catMap = categories.associateBy { it.id }
                binding.recentTransactionsList.adapter = EntryAdapter(recent, catMap)
            }
        }
    }

    private fun isCurrentMonth(date: Long): Boolean {
        val cal = Calendar.getInstance()
        val currentMonth = cal.get(Calendar.MONTH)
        val currentYear = cal.get(Calendar.YEAR)
        
        cal.timeInMillis = date
        return cal.get(Calendar.MONTH) == currentMonth && cal.get(Calendar.YEAR) == currentYear
    }
}

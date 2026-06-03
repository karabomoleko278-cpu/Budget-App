package com.iie.vaultquest

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.iie.vaultquest.data.AppDatabase
import com.iie.vaultquest.data.FirestoreSyncManager
import com.iie.vaultquest.data.SessionManager
import com.iie.vaultquest.databinding.ActivityMainBinding
import com.iie.vaultquest.domain.GoalStatusCalculator
import com.iie.vaultquest.domain.GoalZone
import com.iie.vaultquest.ui.*
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.*

private const val TAG = "MainActivity"

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val db by lazy { AppDatabase.getDatabase(this) }
    private val session by lazy { SessionManager(this) }
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
        syncFromCloud()
        updateDashboard()
    }

    override fun onResume() {
        super.onResume()
        updateDashboard()
    }

    /**
     * Hydrate the local Room cache from Cloud Firestore on entry (off the main
     * thread). Room's Flows then push any new rows into the dashboard and gauge
     * reactively. Failures (e.g. offline) fall back to the local cache.
     */
    private fun syncFromCloud() {
        lifecycleScope.launch(CoroutineExceptionHandler { _, e ->
            Log.e(TAG, "Cloud sync error: ${e.message}", e)
        }) {
            FirestoreSyncManager.pullAll(userId, db.appDao())
        }
    }

    private fun setupClickListeners() {
        binding.btnLogout.setOnClickListener {
            session.clearSession()
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
        lifecycleScope.launch(CoroutineExceptionHandler { _, e ->
            Log.e(TAG, "Dashboard update failed: ${e.message}", e)
        }) {
            combine(
                db.appDao().getEntriesForUser(userId),
                db.appDao().getGoalsForUser(userId),
                db.appDao().getCategoriesForUser(userId)
            ) { entries, goals, categories ->
                Triple(entries, goals, categories)
            }.collect { (entries, _, categories) ->
                try {
                    val currentMonthEntries = entries.filter { isCurrentMonth(it.date) }

                    val totalIncome = currentMonthEntries.filter { it.isIncome }.sumOf { it.amount }
                    val totalExpenses = currentMonthEntries.filter { !it.isIncome }.sumOf { it.amount }
                    val savings = totalIncome - totalExpenses

                    binding.totalSpent.text = currencyFormat.format(totalIncome - totalExpenses)
                    binding.incomeValue.text = currencyFormat.format(totalIncome)
                    binding.expensesValue.text = currencyFormat.format(totalExpenses)
                    binding.savingsValue.text = currencyFormat.format(savings)

                    // ----- Visual goal gauge: spend vs Min/Max (from preferences) -----
                    val minGoal = session.getOverallMin(userId)
                    val maxGoal = session.getOverallMax(userId)
                    val status = GoalStatusCalculator.evaluate(totalExpenses, minGoal, maxGoal)
                    binding.goalGauge.setStatus(status)

                    if (status.zone == GoalZone.UNSET) {
                        binding.gaugeHint.text = "Set your monthly goals to activate tracking."
                        binding.goalStatus.text = "Goal: Not Set"
                        binding.dashboardGoalText.text = "No monthly goals set. Tap Manage to add Min & Max limits."
                    } else {
                        binding.gaugeHint.text = when (status.zone) {
                            GoalZone.SAFE -> "✅ Safe zone — between your min and max."
                            GoalZone.NEAR_LIMIT -> "⚠️ Approaching your maximum limit."
                            GoalZone.UNDER -> "🔵 Below your minimum spend target."
                            GoalZone.BREACHED -> "🔴 Maximum budget breached!"
                            else -> ""
                        }
                        binding.goalStatus.text = "${status.label} • ${currencyFormat.format(totalExpenses)}"
                        binding.dashboardGoalText.text =
                            "Monthly range: ${currencyFormat.format(minGoal)} – ${currencyFormat.format(maxGoal)}\n" +
                            "Spent so far: ${currencyFormat.format(totalExpenses)}"
                    }

                    val recent = entries.sortedByDescending { it.date }.take(5)
                    val catMap = categories.associateBy { it.id }
                    binding.recentTransactionsList.adapter = EntryAdapter(recent, catMap)
                } catch (e: Exception) {
                    Log.e(TAG, "Error rendering dashboard: ${e.message}", e)
                }
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

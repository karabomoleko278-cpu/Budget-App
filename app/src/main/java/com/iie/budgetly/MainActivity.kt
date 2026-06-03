package com.iie.budgetly

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.iie.budgetly.data.AppDatabase
import com.iie.budgetly.data.RealtimeSyncManager
import com.iie.budgetly.data.SessionManager
import com.iie.budgetly.databinding.ActivityMainBinding
import com.iie.budgetly.domain.BudgetHealthEvaluator
import com.iie.budgetly.domain.BudgetLevel
import com.iie.budgetly.ui.*
import com.iie.budgetly.work.RecurringEngine
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

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* best-effort */ }

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

        requestNotificationPermission()
        setupClickListeners()
        syncAndCatchUp()
        updateDashboard()
    }

    override fun onResume() {
        super.onResume()
        updateDashboard()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * On entry: pull the latest data from the Realtime Database into Room, and run
     * the recurring-transactions catch-up. Both off the main thread; Room Flows
     * update the dashboard reactively. Failures fall back to the local cache.
     */
    private fun syncAndCatchUp() {
        lifecycleScope.launch(CoroutineExceptionHandler { _, e ->
            Log.e(TAG, "Sync/catch-up error: ${e.message}", e)
        }) {
            RealtimeSyncManager.pullAll(userId, db.appDao())
            RecurringEngine.processDue(db.appDao())
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
                    val monthEntries = entries.filter { isCurrentMonth(it.date) }
                    val totalIncome = monthEntries.filter { it.isIncome }.sumOf { it.amount }
                    val totalExpenses = monthEntries.filter { !it.isIncome }.sumOf { it.amount }
                    val savings = totalIncome - totalExpenses

                    binding.totalSpent.text = currencyFormat.format(totalIncome - totalExpenses)
                    binding.incomeValue.text = currencyFormat.format(totalIncome)
                    binding.expensesValue.text = currencyFormat.format(totalExpenses)
                    binding.savingsValue.text = currencyFormat.format(savings)

                    // ----- Linear budget bar: spend vs Min/Max (from preferences) -----
                    val minGoal = session.getOverallMin(userId)
                    val maxGoal = session.getOverallMax(userId)
                    val health = BudgetHealthEvaluator.assess(totalExpenses, minGoal, maxGoal)
                    binding.budgetBar.setHealth(health)

                    if (health.level == BudgetLevel.NONE) {
                        binding.budgetBarHeadline.text = "Set a monthly budget to start tracking"
                        binding.budgetBarSub.text = "Tap Manage to add your Min & Max limits."
                        binding.goalStatus.text = "Goal: Not Set"
                        binding.dashboardGoalText.text = "No monthly goals set yet."
                    } else {
                        binding.budgetBarHeadline.text =
                            "${health.headline} • ${currencyFormat.format(totalExpenses)}"
                        binding.budgetBarSub.text =
                            "Budget range ${currencyFormat.format(minGoal)} – ${currencyFormat.format(maxGoal)}"
                        binding.goalStatus.text = health.headline
                        binding.dashboardGoalText.text =
                            "Spent ${currencyFormat.format(totalExpenses)} of ${currencyFormat.format(maxGoal)} this month."
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

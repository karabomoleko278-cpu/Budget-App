package com.iie.vaultquest.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.iie.vaultquest.R
import com.iie.vaultquest.data.AppDatabase
import com.iie.vaultquest.data.FirestoreSyncManager
import com.iie.vaultquest.data.Goal
import com.iie.vaultquest.data.SessionManager
import com.iie.vaultquest.databinding.ActivityGoalSettingsBinding
import com.iie.vaultquest.ui.security.BiometricAuthenticator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

private const val TAG = "GoalSettings"

class GoalSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGoalSettingsBinding
    private val db by lazy { AppDatabase.getDatabase(this) }
    private val session by lazy { SessionManager(this) }
    private var userId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGoalSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userId = intent.getLongExtra("USER_ID", -1)
        if (userId == -1L) userId = session.savedUserId
        if (userId == -1L) {
            Log.e(TAG, "No USER_ID — finishing")
            finish()
            return
        }

        binding.budgetsRecyclerView.layoutManager = LinearLayoutManager(this)

        binding.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        }

        binding.btnSetBudgets.setOnClickListener { showSetBudgetDialog() }
        binding.btnSaveGoals.setOnClickListener { saveOverallGoals() }
        binding.btnExportCsv.setOnClickListener { exportCsv() }
        binding.btnLogout.setOnClickListener { logout() }

        setupBiometricToggle()
    }

    override fun onResume() {
        super.onResume()
        loadOverallGoals()
        loadBudgets()
    }

    // ---------------------------------------------------------------------
    // Overall monthly Min / Max goals — stored in preferences (per user) to
    // avoid the category foreign-key constraint, and mirrored to Firestore.
    // ---------------------------------------------------------------------
    private fun loadOverallGoals() {
        try {
            val min = session.getOverallMin(userId)
            val max = session.getOverallMax(userId)
            if (min > 0) binding.editMinGoal.setText(plain(min))
            if (max > 0) binding.editMaxGoal.setText(plain(max))
        } catch (e: Exception) {
            Log.e(TAG, "loadOverallGoals failed: ${e.message}", e)
        }
    }

    private fun saveOverallGoals() {
        val min = binding.editMinGoal.text.toString().toDoubleOrNull() ?: 0.0
        val max = binding.editMaxGoal.text.toString().toDoubleOrNull() ?: 0.0

        if (max <= 0.0) {
            toast("Enter a maximum monthly goal")
            return
        }
        if (min > max) {
            toast("Minimum cannot exceed maximum")
            return
        }

        try {
            session.setOverallGoals(userId, min, max)
            FirestoreSyncManager.pushOverallGoal(userId, min, max)
            Log.d(TAG, "Overall goals saved: min=$min max=$max")
            toast("Monthly goals saved")
        } catch (e: Exception) {
            Log.e(TAG, "saveOverallGoals failed: ${e.message}", e)
            toast("Could not save goals")
        }
    }

    // ---------------------------------------------------------------------
    // Per-category budgets (stored in Room, valid category foreign keys)
    // ---------------------------------------------------------------------
    private fun loadBudgets() {
        lifecycleScope.launch {
            try {
                val categories = db.appDao().getCategoriesForUser(userId).first()
                if (categories.isEmpty()) return@launch

                val cal = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
                }
                val startOfMonth = cal.timeInMillis
                val monthEntries = db.appDao().getEntriesOnce(userId).filter { it.date >= startOfMonth && !it.isIncome }

                val budgetList = mutableListOf<BudgetProgress>()
                categories.forEach { category ->
                    val goal = db.appDao().getGoalForCategory(userId, category.id)
                    val spent = monthEntries.filter { it.categoryId == category.id }.sumOf { it.amount }
                    val limit = goal?.amount ?: 0.0
                    if (limit > 0 || spent > 0) {
                        budgetList.add(BudgetProgress(category.name, spent, limit))
                    }
                }
                binding.budgetsRecyclerView.adapter = BudgetAdapter(budgetList)
            } catch (e: Exception) {
                Log.e(TAG, "loadBudgets failed: ${e.message}", e)
            }
        }
    }

    private fun showSetBudgetDialog() {
        lifecycleScope.launch {
            try {
                val categories = db.appDao().getCategoriesForUser(userId).first()
                if (categories.isEmpty()) {
                    toast("Add a transaction first to create categories")
                    return@launch
                }

                val dialogView = layoutInflater.inflate(R.layout.dialog_set_budget, null)
                val spinner = dialogView.findViewById<Spinner>(R.id.dialogCategorySpinner)
                val amountInput = dialogView.findViewById<EditText>(R.id.dialogBudgetAmount)

                val adapter = ArrayAdapter(this@GoalSettingsActivity, android.R.layout.simple_spinner_item, categories.map { it.name })
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinner.adapter = adapter

                AlertDialog.Builder(this@GoalSettingsActivity)
                    .setTitle("Set Budget Limit")
                    .setView(dialogView)
                    .setPositiveButton("Save") { _, _ ->
                        val selectedCategory = categories[spinner.selectedItemPosition]
                        val amount = amountInput.text.toString().toDoubleOrNull()
                        if (amount != null && amount > 0) {
                            saveBudget(selectedCategory.id, amount)
                        } else {
                            toast("Invalid amount")
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } catch (e: Exception) {
                Log.e(TAG, "showSetBudgetDialog failed: ${e.message}", e)
            }
        }
    }

    private fun saveBudget(categoryId: Long, amount: Double) {
        lifecycleScope.launch {
            try {
                val existingGoal = db.appDao().getGoalForCategory(userId, categoryId)
                val goal = existingGoal?.copy(amount = amount)
                    ?: Goal(userId = userId, categoryId = categoryId, amount = amount)
                val id = db.appDao().setGoals(goal)
                FirestoreSyncManager.pushGoal(goal.copy(id = id))
                toast("Budget saved")
                loadBudgets()
            } catch (e: Exception) {
                Log.e(TAG, "saveBudget failed: ${e.message}", e)
                toast("Could not save budget")
            }
        }
    }

    // ---------------------------------------------------------------------
    // Custom Feature 1: Biometric App Lock toggle
    // ---------------------------------------------------------------------
    private fun setupBiometricToggle() {
        binding.switchBiometric.isChecked = session.isBiometricEnabled
        binding.switchBiometric.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val authenticator = BiometricAuthenticator(this)
                if (authenticator.canAuthenticate()) {
                    session.isBiometricEnabled = true
                    lifecycleScope.launch {
                        db.appDao().getUserById(userId)?.let { session.saveUser(userId, it.username) }
                    }
                    Log.d(TAG, "Biometric lock enabled")
                    toast("Biometric lock enabled")
                } else {
                    binding.switchBiometric.isChecked = false
                    toast("No fingerprint/face enrolled on this device")
                }
            } else {
                session.isBiometricEnabled = false
                Log.d(TAG, "Biometric lock disabled")
                toast("Biometric lock disabled")
            }
        }
    }

    // ---------------------------------------------------------------------
    // Custom Feature 2: CSV export
    // ---------------------------------------------------------------------
    private fun exportCsv() {
        lifecycleScope.launch {
            try {
                val entries = db.appDao().getEntriesOnce(userId)
                val categories = db.appDao().getCategoriesOnce(userId)
                val file = ExportManager.exportAndShare(this@GoalSettingsActivity, entries, categories)
                if (file == null) toast("Nothing to export yet")
            } catch (e: Exception) {
                Log.e(TAG, "exportCsv failed: ${e.message}", e)
                toast("Export failed")
            }
        }
    }

    private fun logout() {
        session.clearSession()
        Log.d(TAG, "User logged out")
        val intent = Intent(this, LoginActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }

    private fun plain(d: Double): String =
        if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

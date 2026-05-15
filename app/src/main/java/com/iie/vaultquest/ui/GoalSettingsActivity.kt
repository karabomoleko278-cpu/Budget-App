package com.iie.vaultquest.ui

import android.os.Bundle
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
import com.iie.vaultquest.data.Category
import com.iie.vaultquest.data.Goal
import com.iie.vaultquest.databinding.ActivityGoalSettingsBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

class GoalSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGoalSettingsBinding
    private val db by lazy { AppDatabase.getDatabase(this) }
    private var userId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGoalSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userId = intent.getLongExtra("USER_ID", -1)
        if (userId == -1L) finish()

        binding.budgetsRecyclerView.layoutManager = LinearLayoutManager(this)

        binding.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        }

        binding.btnSetBudgets.setOnClickListener {
            showSetBudgetDialog()
        }

        loadBudgets()
    }

    private fun loadBudgets() {
        lifecycleScope.launch {
            db.appDao().getCategoriesForUser(userId).collect { categories ->
                if (categories.isEmpty()) return@collect
                
                val budgetList = mutableListOf<BudgetProgress>()
                
                // For this month
                val cal = Calendar.getInstance()
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                val startOfMonth = cal.timeInMillis

                val entries = db.appDao().getEntriesForUser(userId).first()
                val monthEntries = entries.filter { it.date >= startOfMonth }
                
                categories.forEach { category ->
                    val goal = db.appDao().getGoalForCategory(userId, category.id)
                    val spent = monthEntries.filter { it.categoryId == category.id }.sumOf { it.amount }
                    val limit = goal?.amount ?: 0.0
                    
                    if (limit > 0 || spent > 0) {
                        budgetList.add(BudgetProgress(category.name, spent, limit))
                    }
                }
                
                binding.budgetsRecyclerView.adapter = BudgetAdapter(budgetList)
            }
        }
    }

    private fun showSetBudgetDialog() {
        lifecycleScope.launch {
            val categories = db.appDao().getCategoriesForUser(userId).first()
            if (categories.isEmpty()) {
                Toast.makeText(this@GoalSettingsActivity, "Please add categories first", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(this@GoalSettingsActivity, "Invalid amount", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun saveBudget(categoryId: Long, amount: Double) {
        lifecycleScope.launch {
            val existingGoal = db.appDao().getGoalForCategory(userId, categoryId)
            if (existingGoal != null) {
                db.appDao().setGoals(existingGoal.copy(amount = amount))
            } else {
                db.appDao().setGoals(Goal(userId = userId, categoryId = categoryId, amount = amount))
            }
            Toast.makeText(this@GoalSettingsActivity, "Budget saved", Toast.LENGTH_SHORT).show()
            loadBudgets()
        }
    }
}

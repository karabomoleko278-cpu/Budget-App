package com.iie.budgetly.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.iie.budgetly.R
import com.iie.budgetly.data.AppDatabase
import com.iie.budgetly.data.Category
import com.iie.budgetly.data.RealtimeSyncManager
import com.iie.budgetly.data.RecurringTransaction
import com.iie.budgetly.data.SessionManager
import com.iie.budgetly.databinding.ActivityRecurringBinding
import com.iie.budgetly.domain.Frequency
import com.iie.budgetly.domain.Recurrence
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "RecurringActivity"

class RecurringActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecurringBinding
    private val db by lazy { AppDatabase.getDatabase(this) }
    private val session by lazy { SessionManager(this) }
    private var userId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecurringBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userId = intent.getLongExtra("USER_ID", -1)
        if (userId == -1L) userId = session.savedUserId
        if (userId == -1L) {
            Log.e(TAG, "No USER_ID — finishing")
            finish()
            return
        }

        binding.recurringRecyclerView.layoutManager = LinearLayoutManager(this)

        binding.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        }

        binding.btnAddRecurring.setOnClickListener {
            showAddRecurringDialog()
        }

        observeRecurringTransactions()
    }

    private fun observeRecurringTransactions() {
        lifecycleScope.launch {
            try {
                combine(
                    db.appDao().getRecurringForUser(userId),
                    db.appDao().getCategoriesForUser(userId)
                ) { rules, cats ->
                    Pair(rules, cats)
                }.collect { (rules, cats) ->
                    val catMap = cats.associateBy { it.id }
                    if (rules.isEmpty()) {
                        binding.emptyState.visibility = View.VISIBLE
                        binding.recurringRecyclerView.visibility = View.GONE
                    } else {
                        binding.emptyState.visibility = View.GONE
                        binding.recurringRecyclerView.visibility = View.VISIBLE
                        binding.recurringRecyclerView.adapter = RecurringAdapter(rules, catMap) { rule ->
                            showDeleteConfirmation(rule)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to observe recurring transactions: ${e.message}", e)
            }
        }
    }

    private fun showDeleteConfirmation(rule: RecurringTransaction) {
        AlertDialog.Builder(this)
            .setTitle("Delete Recurring Transaction")
            .setMessage("Are you sure you want to delete '${rule.description}'? It will no longer post automatically.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    try {
                        db.appDao().deleteRecurring(rule)
                        Toast.makeText(this@RecurringActivity, "Recurring rule deleted", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to delete rule: ${e.message}", e)
                        Toast.makeText(this@RecurringActivity, "Error deleting rule", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddRecurringDialog() {
        lifecycleScope.launch {
            try {
                val categories = getOrCreateCategories()

                val dialogView = layoutInflater.inflate(R.layout.dialog_add_recurring, null)
                val descInput = dialogView.findViewById<EditText>(R.id.dialogRecurDescription)
                val amountInput = dialogView.findViewById<EditText>(R.id.dialogRecurAmount)
                val typeSpinner = dialogView.findViewById<Spinner>(R.id.dialogRecurTypeSpinner)
                val categorySpinner = dialogView.findViewById<Spinner>(R.id.dialogRecurCategorySpinner)
                val frequencySpinner = dialogView.findViewById<Spinner>(R.id.dialogRecurFrequencySpinner)
                val dateText = dialogView.findViewById<TextView>(R.id.dialogRecurDateText)
                val dateBtn = dialogView.findViewById<View>(R.id.dialogRecurDateButton)

                // Populate Type Spinner
                val typeAdapter = ArrayAdapter(this@RecurringActivity, android.R.layout.simple_spinner_item, listOf("Expense", "Income"))
                typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                typeSpinner.adapter = typeAdapter

                // Populate Category Spinner
                val categoryAdapter = ArrayAdapter(this@RecurringActivity, android.R.layout.simple_spinner_item, categories.map { it.name })
                categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                categorySpinner.adapter = categoryAdapter

                // Populate Frequency Spinner
                val freqAdapter = ArrayAdapter(this@RecurringActivity, android.R.layout.simple_spinner_item, listOf("Daily", "Weekly", "Monthly"))
                freqAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                frequencySpinner.adapter = freqAdapter

                // Setup Date Picker
                val selectedCal = Calendar.getInstance()
                val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                dateText.text = dateFormat.format(selectedCal.time)

                dateBtn.setOnClickListener {
                    DatePickerDialog(this@RecurringActivity, { _, y, m, d ->
                        selectedCal.set(y, m, d)
                        dateText.text = dateFormat.format(selectedCal.time)
                    }, selectedCal.get(Calendar.YEAR), selectedCal.get(Calendar.MONTH), selectedCal.get(Calendar.DAY_OF_MONTH)).show()
                }

                AlertDialog.Builder(this@RecurringActivity)
                    .setTitle("Add Recurring Transaction")
                    .setView(dialogView)
                    .setPositiveButton("Save") { _, _ ->
                        val desc = descInput.text.toString().trim()
                        val amount = amountInput.text.toString().toDoubleOrNull()
                        val isIncome = typeSpinner.selectedItemPosition == 1
                        val category = categories[categorySpinner.selectedItemPosition]
                        val frequency = frequencySpinner.selectedItem.toString().uppercase()

                        if (desc.isEmpty()) {
                            Toast.makeText(this@RecurringActivity, "Description is required", Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }
                        if (amount == null || amount <= 0) {
                            Toast.makeText(this@RecurringActivity, "Enter a valid amount", Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }

                        saveRecurringRule(desc, amount, isIncome, category.id, frequency, selectedCal.timeInMillis)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show dialog: ${e.message}", e)
            }
        }
    }

    private fun saveRecurringRule(
        description: String,
        amount: Double,
        isIncome: Boolean,
        categoryId: Long,
        frequency: String,
        nextDueDate: Long
    ) {
        lifecycleScope.launch {
            try {
                val rule = RecurringTransaction(
                    userId = userId,
                    categoryId = categoryId,
                    description = description,
                    amount = amount,
                    isIncome = isIncome,
                    frequency = frequency,
                    nextDueDate = nextDueDate,
                    active = true
                )
                db.appDao().insertRecurring(rule)
                Toast.makeText(this@RecurringActivity, "Recurring rule created", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save recurring rule: ${e.message}", e)
                Toast.makeText(this@RecurringActivity, "Error saving rule", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun getOrCreateCategories(): List<Category> {
        val list = db.appDao().getCategoriesOnce(userId)
        if (list.isEmpty()) {
            val defaults = listOf(
                "Salary", "Freelance", "Gift", "Allowance", "Other Income",
                "Food", "Transport", "Rent", "Groceries", "Entertainment", "Savings", "Emergency"
            )
            val created = mutableListOf<Category>()
            defaults.forEach { name ->
                val category = Category(userId = userId, name = name)
                val id = db.appDao().insertCategory(category)
                val categoryWithId = category.copy(id = id)
                RealtimeSyncManager.pushCategory(categoryWithId)
                created.add(categoryWithId)
            }
            return created
        }
        return list
    }
}

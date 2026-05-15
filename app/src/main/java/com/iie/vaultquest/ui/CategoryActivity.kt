package com.iie.vaultquest.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.iie.vaultquest.data.AppDatabase
import com.iie.vaultquest.data.Category
import com.iie.vaultquest.data.Goal
import com.iie.vaultquest.databinding.ActivityCategoryBinding
import kotlinx.coroutines.launch
import android.widget.EditText
import android.text.InputType
import androidx.appcompat.app.AlertDialog

class CategoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCategoryBinding
    private val db by lazy { AppDatabase.getDatabase(this) }
    private var userId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCategoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userId = intent.getLongExtra("USER_ID", -1)
        if (userId == -1L) finish()

        binding.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        }

        binding.categoryList.layoutManager = LinearLayoutManager(this)
        
        observeCategories()

        binding.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        }

        binding.btnAdd.setOnClickListener {
            val name = binding.categoryName.text.toString()
            if (name.isNotEmpty()) {
                lifecycleScope.launch {
                    val newId = db.appDao().insertCategory(Category(userId = userId, name = name))
                    binding.categoryName.text?.clear()
                    Toast.makeText(this@CategoryActivity, "Category added", Toast.LENGTH_SHORT).show()
                    promptForBudget(newId, name)
                }
            } else {
                Toast.makeText(this, "Enter a category name", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeCategories() {
        lifecycleScope.launch {
            db.appDao().getCategoriesForUser(userId).collect { categories ->
                binding.categoryList.adapter = CategoryAdapter(categories)
            }
        }
    }

    private fun promptForBudget(categoryId: Long, categoryName: String) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "R 0.00"
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle("Set Budget Limit")
            .setMessage("Do you want to set a monthly budget limit for $categoryName?")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val amountStr = input.text.toString()
                if (amountStr.isNotEmpty()) {
                    val amount = amountStr.toDoubleOrNull() ?: 0.0
                    if (amount > 0) {
                        lifecycleScope.launch {
                            db.appDao().setGoals(Goal(userId = userId, categoryId = categoryId, amount = amount))
                            Toast.makeText(this@CategoryActivity, "Budget saved", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Not Now", null)
            .show()
    }
}

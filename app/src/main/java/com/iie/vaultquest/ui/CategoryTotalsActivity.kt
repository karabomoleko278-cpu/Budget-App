package com.iie.vaultquest.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.iie.vaultquest.data.AppDatabase
import com.iie.vaultquest.databinding.ActivityCategoryTotalsBinding
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CategoryTotalsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCategoryTotalsBinding
    private val db by lazy { AppDatabase.getDatabase(this) }
    private var userId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCategoryTotalsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userId = intent.getLongExtra("USER_ID", -1)
        val start = intent.getLongExtra("START_DATE", 0)
        val end = intent.getLongExtra("END_DATE", 0)

        if (userId == -1L) finish()

        binding.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        }

        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        binding.periodText.text = "${sdf.format(Date(start))} - ${sdf.format(Date(end))}"

        binding.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        }

        binding.totalsList.layoutManager = LinearLayoutManager(this)

        calculateTotals(start, end)
    }

    private fun calculateTotals(start: Long, end: Long) {
        lifecycleScope.launch {
            combine(
                db.appDao().getEntriesForPeriod(userId, start, end),
                db.appDao().getCategoriesForUser(userId)
            ) { entries, cats ->
                val catMap = cats.associateBy { it.id }
                entries.groupBy { it.categoryId }
                    .map { (catId, entryList) ->
                        val catName = catMap[catId]?.name ?: "Unknown"
                        val sum = entryList.sumOf { it.amount }
                        catName to sum
                    }
            }.collect { totals ->
                binding.totalsList.adapter = TotalsAdapter(totals)
            }
        }
    }
}

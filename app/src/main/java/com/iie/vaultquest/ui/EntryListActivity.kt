package com.iie.vaultquest.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.iie.vaultquest.data.AppDatabase
import com.iie.vaultquest.databinding.ActivityEntryListBinding
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class EntryListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEntryListBinding
    private val db by lazy { AppDatabase.getDatabase(this) }
    private var userId: Long = -1
    
    private var startDate = Calendar.getInstance().apply { 
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
    }
    private var endDate = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEntryListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userId = intent.getLongExtra("USER_ID", -1)
        if (userId == -1L) finish()

        binding.entryList.layoutManager = LinearLayoutManager(this)
        
        updateDateButtons()
        observeEntries()

        binding.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        }

        binding.btnStartDate.setOnClickListener {
            showDatePicker(true)
        }

        binding.btnEndDate.setOnClickListener {
            showDatePicker(false)
        }

        binding.btnViewTotals.setOnClickListener {
            val intent = Intent(this, CategoryTotalsActivity::class.java).apply {
                putExtra("USER_ID", userId)
                putExtra("START_DATE", startDate.timeInMillis)
                putExtra("END_DATE", endDate.timeInMillis)
            }
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    private fun showDatePicker(isStart: Boolean) {
        val cal = if (isStart) startDate else endDate
        DatePickerDialog(this, { _, y, m, d ->
            cal.set(y, m, d)
            if (isStart) {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
            } else {
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
            }
            updateDateButtons()
            observeEntries()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun updateDateButtons() {
        val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
        binding.btnStartDate.text = sdf.format(startDate.time)
        binding.btnEndDate.text = sdf.format(endDate.time)
    }

    private fun observeEntries() {
        lifecycleScope.launch {
            combine(
                db.appDao().getCategoriesForUser(userId),
                db.appDao().getEntriesForPeriod(userId, startDate.timeInMillis, endDate.timeInMillis)
            ) { cats, list ->
                Pair(cats, list)
            }.collect { (cats, list) ->
                val catMap = cats.associateBy { it.id }
                binding.txtEmptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                binding.entryList.adapter = EntryAdapter(list, catMap)
            }
        }
    }
}

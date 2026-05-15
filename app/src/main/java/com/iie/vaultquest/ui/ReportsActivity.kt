package com.iie.vaultquest.ui

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.iie.vaultquest.data.AppDatabase
import com.iie.vaultquest.databinding.ActivityReportsBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

class ReportsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportsBinding
    private val db by lazy { AppDatabase.getDatabase(this) }
    private var userId: Long = -1

    private val periods = listOf("Today", "This Week", "This Month", "Last 3 Months", "Last 6 Months", "This Year")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userId = intent.getLongExtra("USER_ID", -1)
        if (userId == -1L) finish()

        binding.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        }

        setupChart()
        setupSpinner()
    }

    private fun setupChart() {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
        val textColor = typedValue.data

        binding.pieChart.apply {
            setUsePercentValues(true)
            description.isEnabled = false
            isDrawHoleEnabled = true
            setHoleColor(Color.TRANSPARENT)
            setTransparentCircleColor(Color.GRAY)
            setTransparentCircleAlpha(110)
            holeRadius = 58f
            transparentCircleRadius = 61f
            setDrawCenterText(true)
            rotationAngle = 0f
            isRotationEnabled = true
            isHighlightPerTapEnabled = true
            legend.isEnabled = true
            legend.textColor = textColor
        }
    }

    private fun setupSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, periods)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.periodSpinner.adapter = adapter

        binding.periodSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                loadDataForPeriod(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        // Default to "This Month"
        binding.periodSpinner.setSelection(2)
    }

    private fun loadDataForPeriod(periodIndex: Int) {
        lifecycleScope.launch {
            val cal = Calendar.getInstance()
            // Reset time to start of day for accurate filtering
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)

            when (periodIndex) {
                0 -> {} // Today: no changes needed
                1 -> cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek) // This week
                2 -> cal.set(Calendar.DAY_OF_MONTH, 1) // This month
                3 -> { cal.add(Calendar.MONTH, -3); cal.set(Calendar.DAY_OF_MONTH, 1) } // Last 3 Months
                4 -> { cal.add(Calendar.MONTH, -6); cal.set(Calendar.DAY_OF_MONTH, 1) } // Last 6 Months
                5 -> cal.set(Calendar.DAY_OF_YEAR, 1) // This year
            }

            val startDate = cal.timeInMillis

            val entries = db.appDao().getEntriesForUser(userId).first().filter { it.date >= startDate }
            val categories = db.appDao().getCategoriesForUser(userId).first()

            val spendingByCategory = mutableMapOf<String, Double>()
            var totalSpent = 0.0

            entries.forEach { entry ->
                val categoryName = categories.find { it.id == entry.categoryId }?.name ?: "Unknown"
                spendingByCategory[categoryName] = (spendingByCategory[categoryName] ?: 0.0) + entry.amount
                totalSpent += entry.amount
            }

            val format = NumberFormat.getCurrencyInstance(Locale("en", "ZA"))
            binding.totalSpentText.text = "Total: ${format.format(totalSpent)}"

            if (spendingByCategory.isEmpty()) {
                binding.pieChart.visibility = View.GONE
                binding.emptyStateText.visibility = View.VISIBLE
            } else {
                binding.pieChart.visibility = View.VISIBLE
                binding.emptyStateText.visibility = View.GONE
                updateChart(spendingByCategory, format.format(totalSpent))
            }
        }
    }

    private fun updateChart(spendingData: Map<String, Double>, totalText: String) {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
        val textColor = typedValue.data

        val pieEntries = ArrayList<PieEntry>()
        spendingData.forEach { (category, amount) ->
            pieEntries.add(PieEntry(amount.toFloat(), category))
        }

        val dataSet = PieDataSet(pieEntries, "Expenses")
        dataSet.sliceSpace = 3f
        dataSet.selectionShift = 5f
        
        // Add vibrant colors
        val colors = ArrayList<Int>()
        colors.addAll(ColorTemplate.MATERIAL_COLORS.toList())
        colors.addAll(ColorTemplate.JOYFUL_COLORS.toList())
        colors.addAll(ColorTemplate.PASTEL_COLORS.toList())
        dataSet.colors = colors

        val data = PieData(dataSet)
        data.setValueFormatter(PercentFormatter(binding.pieChart))
        data.setValueTextSize(12f)
        data.setValueTextColor(Color.WHITE)

        binding.pieChart.data = data
        binding.pieChart.centerText = "Spent\n$totalText"
        binding.pieChart.setCenterTextColor(textColor)
        binding.pieChart.invalidate()
    }
}

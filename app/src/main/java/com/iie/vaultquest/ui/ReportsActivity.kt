package com.iie.vaultquest.ui

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.iie.vaultquest.R
import com.iie.vaultquest.data.AppDatabase
import com.iie.vaultquest.data.SessionManager
import com.iie.vaultquest.databinding.ActivityReportsBinding
import com.iie.vaultquest.domain.PeriodRange
import com.iie.vaultquest.domain.ReportPeriod
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

private const val TAG = "ReportsActivity"

class ReportsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportsBinding
    private val db by lazy { AppDatabase.getDatabase(this) }
    private val session by lazy { SessionManager(this) }
    private var userId: Long = -1
    private val currency = NumberFormat.getCurrencyInstance(Locale("en", "ZA"))

    private var selectedPeriod = ReportPeriod.MONTH

    private val errorHandler = CoroutineExceptionHandler { _, e ->
        Log.e(TAG, "Unhandled coroutine error while building report: ${e.message}", e)
    }

    private val textColor: Int
        get() {
            val tv = android.util.TypedValue()
            theme.resolveAttribute(android.R.attr.textColorPrimary, tv, true)
            return tv.data
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userId = intent.getLongExtra("USER_ID", -1)
        if (userId == -1L) {
            Log.e(TAG, "No USER_ID supplied — finishing")
            finish()
            return
        }

        binding.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        }

        setupCharts()
        setupPeriodToggle()
    }

    private fun setupPeriodToggle() {
        binding.periodToggle.check(R.id.btnMonth)
        binding.periodToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            selectedPeriod = when (checkedId) {
                R.id.btnDay -> ReportPeriod.DAY
                R.id.btnWeek -> ReportPeriod.WEEK
                else -> ReportPeriod.MONTH
            }
            Log.d(TAG, "Period changed to $selectedPeriod")
            loadData()
        }
        loadData()
    }

    private fun setupCharts() {
        binding.barChart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setScaleEnabled(false)
            setPinchZoom(false)
            setDrawValueAboveBar(true)
            setFitBars(true)
            axisRight.isEnabled = false
            axisLeft.axisMinimum = 0f
            axisLeft.textColor = textColor
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            xAxis.granularity = 1f
            xAxis.textColor = textColor
        }

        binding.pieChart.apply {
            setUsePercentValues(true)
            description.isEnabled = false
            isDrawHoleEnabled = true
            setHoleColor(Color.TRANSPARENT)
            setTransparentCircleAlpha(0)
            holeRadius = 58f
            setDrawCenterText(true)
            legend.isEnabled = true
            legend.textColor = textColor
        }
    }

    private fun loadData() {
        lifecycleScope.launch(errorHandler) {
            try {
                val startDate = PeriodRange.startOf(selectedPeriod)
                val entries = db.appDao().getEntriesOnce(userId)
                    .filter { it.date >= startDate && !it.isIncome }
                val categories = db.appDao().getCategoriesOnce(userId)
                val minGoal = session.getOverallMin(userId)
                val maxGoal = session.getOverallMax(userId)

                val spendingByCategory = linkedMapOf<String, Double>()
                var totalSpent = 0.0
                entries.forEach { entry ->
                    val name = categories.find { it.id == entry.categoryId }?.name ?: "Other"
                    spendingByCategory[name] = (spendingByCategory[name] ?: 0.0) + entry.amount
                    totalSpent += entry.amount
                }

                binding.totalSpentText.text = "Total: ${currency.format(totalSpent)}"

                if (spendingByCategory.isEmpty()) {
                    binding.barChart.visibility = View.GONE
                    binding.pieChart.visibility = View.GONE
                    binding.emptyStateText.visibility = View.VISIBLE
                    binding.barChart.clear()
                    binding.pieChart.clear()
                } else {
                    binding.barChart.visibility = View.VISIBLE
                    binding.pieChart.visibility = View.VISIBLE
                    binding.emptyStateText.visibility = View.GONE
                    renderBarChart(spendingByCategory, minGoal, maxGoal)
                    renderPieChart(spendingByCategory, currency.format(totalSpent))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load report data: ${e.message}", e)
            }
        }
    }

    private fun renderBarChart(data: Map<String, Double>, minGoal: Double, maxGoal: Double) {
        try {
            val labels = data.keys.toList()
            val barEntries = labels.mapIndexed { i, name ->
                BarEntry(i.toFloat(), (data[name] ?: 0.0).toFloat())
            }

            val set = BarDataSet(barEntries, "Spent").apply {
                colors = ColorTemplate.MATERIAL_COLORS.toList()
                valueTextColor = textColor
                valueTextSize = 10f
            }
            val barData = BarData(set).apply { barWidth = 0.55f }

            binding.barChart.apply {
                this.data = barData
                xAxis.valueFormatter = IndexAxisValueFormatter(labels)
                xAxis.labelCount = labels.size
                xAxis.labelRotationAngle = if (labels.size > 4) -35f else 0f

                axisLeft.removeAllLimitLines()
                if (minGoal > 0) axisLeft.addLimitLine(goalLine(minGoal, "Min", R.color.vault_green))
                if (maxGoal > 0) axisLeft.addLimitLine(goalLine(maxGoal, "Max", R.color.vault_red))
                axisLeft.axisMaximum = maxOf(
                    (data.values.maxOrNull() ?: 0.0),
                    maxGoal
                ).toFloat() * 1.15f

                animateY(700)
                invalidate()
            }
            Log.d(TAG, "Bar chart rendered (${labels.size} categories, min=$minGoal max=$maxGoal)")
        } catch (e: Exception) {
            Log.e(TAG, "renderBarChart error: ${e.message}", e)
        }
    }

    private fun goalLine(value: Double, label: String, colorRes: Int): LimitLine {
        val labelColor = textColor
        return LimitLine(value.toFloat(), label).apply {
            lineColor = getColor(colorRes)
            lineWidth = 2f
            textColor = labelColor
            textSize = 10f
            enableDashedLine(12f, 6f, 0f)
            labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
        }
    }

    private fun renderPieChart(data: Map<String, Double>, totalText: String) {
        try {
            val pieEntries = data.map { (name, amount) -> PieEntry(amount.toFloat(), name) }
            val set = PieDataSet(pieEntries, "").apply {
                sliceSpace = 3f
                colors = (ColorTemplate.MATERIAL_COLORS.toList()
                        + ColorTemplate.JOYFUL_COLORS.toList())
            }
            val pieData = PieData(set).apply {
                setValueFormatter(PercentFormatter(binding.pieChart))
                setValueTextSize(11f)
                setValueTextColor(Color.WHITE)
            }
            binding.pieChart.apply {
                this.data = pieData
                centerText = "Spent\n$totalText"
                setCenterTextColor(textColor)
                animateY(700)
                invalidate()
            }
        } catch (e: Exception) {
            Log.e(TAG, "renderPieChart error: ${e.message}", e)
        }
    }
}

package com.iie.budgetly.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry as ChartEntry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.iie.budgetly.R
import com.iie.budgetly.data.AppDatabase
import com.iie.budgetly.data.SessionManager
import com.iie.budgetly.databinding.ActivityReportsBinding
import com.iie.budgetly.domain.SpendTrend
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

private const val TAG = "ReportsActivity"

class ReportsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportsBinding
    private val db by lazy { AppDatabase.getDatabase(this) }
    private val session by lazy { SessionManager(this) }
    private var userId: Long = -1
    private val currency = NumberFormat.getCurrencyInstance(Locale("en", "ZA"))

    /** 0 = Day, 1 = Week, 2 = Month */
    private var period = 2

    private val errorHandler = CoroutineExceptionHandler { _, e ->
        Log.e(TAG, "Report coroutine error: ${e.message}", e)
    }

    private val textColor: Int
        get() = getColor(R.color.ivory_cream)

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

        setupChart()
        
        binding.periodTabs.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                period = tab?.position ?: 2
                loadData()
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })
        
        // Default to Month (position 2)
        binding.periodTabs.getTabAt(2)?.select()
        loadData()
    }

    private fun setupChart() {
        binding.lineChart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setScaleEnabled(false)
            setPinchZoom(false)
            setTouchEnabled(true)
            axisRight.isEnabled = false
            axisLeft.axisMinimum = 0f
            axisLeft.textColor = textColor
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            xAxis.granularity = 1f
            xAxis.textColor = textColor
        }
    }

    private fun startOfPeriod(): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        when (period) {
            0 -> { /* today */ }
            1 -> cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
            else -> cal.set(Calendar.DAY_OF_MONTH, 1)
        }
        return cal.timeInMillis
    }

    private fun loadData() {
        lifecycleScope.launch(errorHandler) {
            try {
                val start = startOfPeriod()
                val expenses = db.appDao().getEntriesOnce(userId)
                    .filter { it.date >= start && !it.isIncome }
                val total = expenses.sumOf { it.amount }
                val minGoal = session.getOverallMin(userId)
                val maxGoal = session.getOverallMax(userId)

                binding.totalSpentText.text = "TOTAL: ${currency.format(total)}"

                val trend = SpendTrend.cumulativeByDay(expenses.map { it.date to it.amount }, start)
                if (trend.isEmpty()) {
                    binding.lineChart.visibility = View.GONE
                    binding.emptyStateText.visibility = View.VISIBLE
                    binding.lineChart.clear()
                } else {
                    binding.lineChart.visibility = View.VISIBLE
                    binding.emptyStateText.visibility = View.GONE
                    renderLine(trend.map { ChartEntry(it.dayOffset.toFloat(), it.cumulative.toFloat()) }, minGoal, maxGoal)
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadData failed: ${e.message}", e)
            }
        }
    }

    private fun renderLine(points: List<ChartEntry>, minGoal: Double, maxGoal: Double) {
        try {
            val set = LineDataSet(points, "Cumulative spend").apply {
                color = getColor(R.color.soft_gold)
                lineWidth = 3f
                setDrawCircles(true)
                setCircleColor(getColor(R.color.ivory_cream))
                circleRadius = 4f
                setDrawValues(false)
                setDrawFilled(true)
                fillColor = getColor(R.color.soft_gold)
                fillAlpha = 60
                mode = LineDataSet.Mode.CUBIC_BEZIER
            }

            binding.lineChart.apply {
                data = LineData(set)
                axisLeft.removeAllLimitLines()
                if (minGoal > 0) axisLeft.addLimitLine(goalLine(minGoal, "Min", R.color.income_green))
                if (maxGoal > 0) axisLeft.addLimitLine(goalLine(maxGoal, "Max", R.color.expense_red))
                val dataMax = points.maxOfOrNull { it.y }?.toDouble() ?: 0.0
                axisLeft.axisMaximum = (maxOf(dataMax, maxGoal) * 1.15).toFloat().coerceAtLeast(1f)
                animateX(700)
                invalidate()
            }
            Log.d(TAG, "Line chart rendered (${points.size} points, min=$minGoal max=$maxGoal)")
        } catch (e: Exception) {
            Log.e(TAG, "renderLine failed: ${e.message}", e)
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
}

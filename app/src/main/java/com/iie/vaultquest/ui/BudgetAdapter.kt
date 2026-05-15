package com.iie.vaultquest.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.iie.vaultquest.R
import java.text.NumberFormat
import java.util.Locale

data class BudgetProgress(
    val categoryName: String,
    val spent: Double,
    val limit: Double
)

class BudgetAdapter(private val budgets: List<BudgetProgress>) :
    RecyclerView.Adapter<BudgetAdapter.BudgetViewHolder>() {

    class BudgetViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val categoryName: TextView = view.findViewById(R.id.categoryName)
        val budgetAmount: TextView = view.findViewById(R.id.budgetAmount)
        val budgetProgress: ProgressBar = view.findViewById(R.id.budgetProgress)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BudgetViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_budget, parent, false)
        return BudgetViewHolder(view)
    }

    override fun onBindViewHolder(holder: BudgetViewHolder, position: Int) {
        val budget = budgets[position]
        holder.categoryName.text = budget.categoryName
        
        val format = NumberFormat.getCurrencyInstance(Locale("en", "ZA"))
        holder.budgetAmount.text = "${format.format(budget.spent)} / ${format.format(budget.limit)}"
        
        if (budget.limit > 0) {
            val progress = ((budget.spent / budget.limit) * 100).toInt()
            holder.budgetProgress.progress = progress.coerceIn(0, 100)
        } else {
            holder.budgetProgress.progress = 0
        }
    }

    override fun getItemCount() = budgets.size
}

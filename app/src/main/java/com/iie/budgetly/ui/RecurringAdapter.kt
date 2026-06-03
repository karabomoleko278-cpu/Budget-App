package com.iie.budgetly.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.iie.budgetly.R
import com.iie.budgetly.data.Category
import com.iie.budgetly.data.RecurringTransaction
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class RecurringAdapter(
    private val rules: List<RecurringTransaction>,
    private val categories: Map<Long, Category>,
    private val onDeleteClick: (RecurringTransaction) -> Unit
) : RecyclerView.Adapter<RecurringAdapter.RecurringViewHolder>() {

    class RecurringViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val desc: TextView = view.findViewById(R.id.txtDescription)
        val cat: TextView = view.findViewById(R.id.txtCategory)
        val frequencyAndDate: TextView = view.findViewById(R.id.txtFrequencyAndDate)
        val amount: TextView = view.findViewById(R.id.txtAmount)
        val icon: ImageView = view.findViewById(R.id.imgRecurring)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecurringViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recurring, parent, false)
        return RecurringViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecurringViewHolder, position: Int) {
        val rule = rules[position]
        val context = holder.itemView.context
        val format = NumberFormat.getCurrencyInstance(Locale("en", "ZA"))
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        holder.desc.text = rule.description
        val categoryName = categories[rule.categoryId]?.name ?: "Uncategorized"
        holder.cat.text = categoryName

        // Frequency and next due date
        val frequencyLabel = rule.frequency.lowercase().replaceFirstChar { it.titlecase() }
        val nextDueStr = dateFormat.format(Date(rule.nextDueDate))
        holder.frequencyAndDate.text = "$frequencyLabel • Next: $nextDueStr"

        if (rule.isIncome) {
            holder.amount.text = "+ ${format.format(rule.amount)}"
            holder.amount.setTextColor(context.getColor(R.color.income_green))
            holder.icon.setImageResource(R.drawable.ic_nav_wallet)
        } else {
            holder.amount.text = "- ${format.format(rule.amount)}"
            holder.amount.setTextColor(context.getColor(R.color.expense_red))
            holder.icon.setImageResource(R.drawable.ic_receipt)
        }

        holder.btnDelete.setOnClickListener {
            onDeleteClick(rule)
        }
    }

    override fun getItemCount() = rules.size
}

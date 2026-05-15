package com.iie.vaultquest.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.iie.vaultquest.R
import java.text.NumberFormat
import java.util.*

class TotalsAdapter(private val totals: List<Pair<String, Double>>) : RecyclerView.Adapter<TotalsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(android.R.id.text1)
        val amount: TextView = view.findViewById(android.R.id.text2)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (name, sum) = totals[position]
        holder.name.text = name
        holder.name.setTextColor(holder.itemView.context.getColor(R.color.white))
        
        val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "ZA"))
        holder.amount.text = currencyFormatter.format(sum)
        holder.amount.setTextColor(holder.itemView.context.getColor(R.color.secondary))
    }

    override fun getItemCount() = totals.size
}

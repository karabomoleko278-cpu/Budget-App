package com.iie.vaultquest.ui

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.iie.vaultquest.R
import com.iie.vaultquest.data.Category
import com.iie.vaultquest.data.Entry
import java.io.File
import java.text.NumberFormat
import java.util.*

class EntryAdapter(
    private val entries: List<Entry>,
    private val categories: Map<Long, Category>
) : RecyclerView.Adapter<EntryAdapter.EntryViewHolder>() {

    class EntryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val desc: TextView = view.findViewById(R.id.txtDescription)
        val cat: TextView = view.findViewById(R.id.txtCategory)
        val amount: TextView = view.findViewById(R.id.txtAmount)
        val time: TextView = view.findViewById(R.id.txtTime)
        val icon: ImageView = view.findViewById(R.id.imgEntry)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EntryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_entry, parent, false)
        return EntryViewHolder(view)
    }

    override fun onBindViewHolder(holder: EntryViewHolder, position: Int) {
        val entry = entries[position]
        val context = holder.itemView.context
        val format = NumberFormat.getCurrencyInstance(Locale("en", "ZA"))

        holder.desc.text = entry.description
        val categoryName = categories[entry.categoryId]?.name ?: "Uncategorized"
        holder.cat.text = categoryName
        
        if (entry.isIncome) {
            holder.amount.text = "+ ${format.format(entry.amount)}"
            holder.amount.setTextColor(context.getColor(R.color.vault_green))
        } else {
            holder.amount.text = "- ${format.format(entry.amount)}"
            holder.amount.setTextColor(context.getColor(R.color.vault_red))
        }
        
        holder.time.text = entry.startTime

        if (entry.photoPath != null) {
            try {
                holder.icon.setImageURI(Uri.fromFile(File(entry.photoPath)))
            } catch (e: Exception) {
                holder.icon.setImageResource(if (entry.isIncome) R.drawable.ic_vault else R.drawable.ic_receipt)
            }
        } else {
            holder.icon.setImageResource(if (entry.isIncome) R.drawable.ic_vault else R.drawable.ic_receipt)
        }
    }

    override fun getItemCount() = entries.size
}

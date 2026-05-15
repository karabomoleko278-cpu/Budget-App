package com.iie.vaultquest.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.iie.vaultquest.R
import com.iie.vaultquest.data.Category

class CategoryAdapter(private val categories: List<Category>) : RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.nameText.text = categories[position].name
        holder.nameText.setTextColor(parentColor(holder.itemView))
    }

    private fun parentColor(view: View): Int {
        return view.context.getColor(com.iie.vaultquest.R.color.white)
    }

    override fun getItemCount() = categories.size
}

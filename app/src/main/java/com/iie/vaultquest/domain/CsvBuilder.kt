package com.iie.vaultquest.domain

import com.iie.vaultquest.data.Entry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds an RFC-4180-style CSV document from a list of transactions.
 * Pure string logic so it is fully unit-testable (Custom Feature 2: CSV export).
 */
object CsvBuilder {

    private const val HEADER = "Date,Type,Category,Description,Amount"

    fun build(entries: List<Entry>, categoryNames: Map<Long, String>): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val sb = StringBuilder()
        sb.append(HEADER).append("\n")
        entries.sortedBy { it.date }.forEach { e ->
            val date = sdf.format(Date(e.date))
            val type = if (e.isIncome) "Income" else "Expense"
            val category = categoryNames[e.categoryId] ?: "Uncategorized"
            sb.append(date).append(",")
                .append(type).append(",")
                .append(escape(category)).append(",")
                .append(escape(e.description)).append(",")
                .append(String.format(Locale.US, "%.2f", e.amount))
                .append("\n")
        }
        return sb.toString()
    }

    /** Quote fields containing commas, quotes or newlines, per CSV rules. */
    private fun escape(field: String): String {
        return if (field.contains(',') || field.contains('"') || field.contains('\n')) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
    }
}

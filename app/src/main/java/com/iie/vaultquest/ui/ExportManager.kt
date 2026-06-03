package com.iie.vaultquest.ui

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import com.iie.vaultquest.data.Category
import com.iie.vaultquest.data.Entry
import com.iie.vaultquest.domain.CsvBuilder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Custom Feature 2 — exports the user's transactions to a CSV file and hands it
 * to the Android share sheet (email / Drive / WhatsApp …) via the app's
 * FileProvider. All IO is wrapped so a storage failure surfaces as a handled
 * error rather than a crash (requirement #6).
 */
object ExportManager {

    private const val TAG = "ExportManager"

    /**
     * @return the generated [File] on success, or null if the export failed.
     */
    fun exportAndShare(
        context: Context,
        entries: List<Entry>,
        categories: List<Category>
    ): File? {
        return try {
            if (entries.isEmpty()) {
                Log.w(TAG, "Export requested with no transactions")
                return null
            }

            val csv = CsvBuilder.build(entries, categories.associate { it.id to it.name })

            val dir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(dir, "Budgetly_Transactions_$stamp.csv")
            file.writeText(csv)
            Log.d(TAG, "CSV written: ${file.absolutePath} (${entries.size} rows)")

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Budgetly Transactions Export")
                putExtra(Intent.EXTRA_TEXT, "Attached is my Budgetly transaction history (${entries.size} entries).")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(shareIntent, "Export transactions")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            file
        } catch (e: Exception) {
            Log.e(TAG, "CSV export failed: ${e.message}", e)
            null
        }
    }
}

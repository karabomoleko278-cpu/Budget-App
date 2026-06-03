package com.iie.vaultquest

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings

/**
 * Application entry point for Budgetly.
 *
 * Initialises Firebase and enables Cloud Firestore offline persistence so every
 * read/write is served from a local on-disk cache first and synchronised with
 * the cloud when a connection is available — the UI never blocks on the network.
 *
 * Start-up is defensive: if Firebase cannot initialise (e.g. the bundled template
 * google-services.json has not been replaced) the app still launches and runs
 * fully against the local Room cache.
 */
class BudgetlyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        initFirebase()
    }

    private fun initFirebase() {
        try {
            FirebaseApp.initializeApp(this)

            val settings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(
                    PersistentCacheSettings.newBuilder()
                        .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                        .build()
                )
                .build()

            FirebaseFirestore.getInstance().firestoreSettings = settings
            Log.d(TAG, "Firebase + Firestore offline persistence initialised")
        } catch (e: Exception) {
            Log.e(TAG, "Firebase init failed — running in local-only mode: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "BudgetlyApp"
    }
}

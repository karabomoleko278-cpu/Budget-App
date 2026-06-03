package com.iie.vaultquest.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.iie.vaultquest.MainActivity
import com.iie.vaultquest.data.AppDatabase
import com.iie.vaultquest.data.FirestoreSyncManager
import com.iie.vaultquest.data.SessionManager
import com.iie.vaultquest.databinding.ActivityLoginBinding
import com.iie.vaultquest.ui.security.BiometricAuthenticator
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch

private const val TAG = "LoginActivity"

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val db by lazy { AppDatabase.getDatabase(this) }
    private val session by lazy { SessionManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener { attemptPasswordLogin() }
        binding.btnRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        maybePromptBiometric()
    }

    /**
     * Custom Feature 1 — if the user previously enabled the Biometric App Lock,
     * offer fingerprint/face unlock instead of retyping the password. The system
     * prompt's "Use password" button simply returns to the form below.
     */
    private fun maybePromptBiometric() {
        if (!session.isBiometricEnabled || !session.hasSavedUser()) return

        val authenticator = BiometricAuthenticator(this)
        if (!authenticator.canAuthenticate()) {
            Log.w(TAG, "Biometric enabled but unavailable — falling back to password")
            return
        }

        authenticator.authenticate(
            title = "Unlock Budgetly",
            subtitle = "Confirm it's you to access your budget",
            onSuccess = {
                Log.i(TAG, "Biometric unlock succeeded for ${session.savedUsername}")
                lifecycleScope.launch {
                    val user = db.appDao().getUserById(session.savedUserId)
                    if (user != null) {
                        goToDashboard(user.id, user.username)
                    } else {
                        session.clearSession()
                        Toast.makeText(this@LoginActivity, "Session expired. Please log in again.", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onFallback = { Log.d(TAG, "User chose password login") },
            onError = { msg -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
        )
    }

    private fun attemptPasswordLogin() {
        val username = binding.username.text.toString().trim()
        val password = binding.password.text.toString()

        Log.d(TAG, "Login attempt for user: $username")

        if (username.isEmpty() || password.isEmpty()) {
            Log.w(TAG, "Login failed: empty fields")
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(CoroutineExceptionHandler { _, e ->
            Log.e(TAG, "Login coroutine error: ${e.message}", e)
            Toast.makeText(this@LoginActivity, "Login error, please retry", Toast.LENGTH_SHORT).show()
        }) {
            val user = db.appDao().getUserByUsername(username)
            if (user != null && user.password == password) {
                Log.i(TAG, "Login successful for user: $username")
                session.saveUser(user.id, user.username)
                // Push any locally-captured data up to the cloud (offline-first backup).
                FirestoreSyncManager.backupAll(user.id, db.appDao())
                goToDashboard(user.id, user.username)
            } else {
                Log.e(TAG, "Login failed: invalid credentials for $username")
                Toast.makeText(this@LoginActivity, "Invalid credentials", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun goToDashboard(userId: Long, username: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("USER_ID", userId)
            putExtra("USERNAME", username)
        }
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}

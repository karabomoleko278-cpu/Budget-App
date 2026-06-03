package com.iie.vaultquest.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.iie.vaultquest.MainActivity
import com.iie.vaultquest.data.AppDatabase
import com.iie.vaultquest.data.RealtimeSyncManager
import com.iie.vaultquest.data.SessionManager
import com.iie.vaultquest.databinding.ActivityLoginBinding
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
    }

    private fun attemptPasswordLogin() {
        val username = binding.username.text.toString().trim()
        val password = binding.password.text.toString()

        Log.d(TAG, "Login attempt for user: $username")

        if (username.isEmpty() || password.isEmpty()) {
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
                // Upload anything captured offline before the cloud was reachable.
                RealtimeSyncManager.backupAll(user.id, db.appDao())
                val intent = Intent(this@LoginActivity, MainActivity::class.java).apply {
                    putExtra("USER_ID", user.id)
                    putExtra("USERNAME", user.username)
                }
                startActivity(intent)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                finish()
            } else {
                Log.e(TAG, "Login failed: invalid credentials for $username")
                Toast.makeText(this@LoginActivity, "Invalid credentials", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

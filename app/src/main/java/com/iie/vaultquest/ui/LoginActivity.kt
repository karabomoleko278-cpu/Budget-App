package com.iie.vaultquest.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import android.util.Log
import com.iie.vaultquest.MainActivity
import com.iie.vaultquest.data.AppDatabase
import com.iie.vaultquest.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

private const val TAG = "LoginActivity"

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val db by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener {
            val username = binding.username.text.toString()
            val password = binding.password.text.toString()

            Log.d(TAG, "Login attempt for user: $username")

            if (username.isEmpty() || password.isEmpty()) {
                Log.w(TAG, "Login failed: Empty fields")
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                // Fetch user from Room database
                val user = db.appDao().getUserByUsername(username)
                if (user != null && user.password == password) {
                    Log.i(TAG, "Login successful for user: $username")
                    // Intent to transition to Dashboard
                    val intent = Intent(this@LoginActivity, MainActivity::class.java).apply {
                        putExtra("USER_ID", user.id)
                        putExtra("USERNAME", user.username)
                    }
                    startActivity(intent)
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    finish()
                } else {
                    Log.e(TAG, "Login failed: Invalid credentials for $username")
                    Toast.makeText(this@LoginActivity, "Invalid credentials", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }
}

package com.iie.vaultquest.ui

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.iie.vaultquest.data.AppDatabase
import com.iie.vaultquest.data.RealtimeSyncManager
import com.iie.vaultquest.data.User
import com.iie.vaultquest.databinding.ActivityRegisterBinding
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch

private const val TAG = "RegisterActivity"

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val db by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRegister.setOnClickListener {
            val username = binding.username.text.toString()
            val password = binding.password.text.toString()
            val confirm = binding.confirmPassword.text.toString()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password != confirm) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch(CoroutineExceptionHandler { _, e ->
                Log.e(TAG, "Registration error: ${e.message}", e)
                Toast.makeText(this@RegisterActivity, "Registration failed, please retry", Toast.LENGTH_SHORT).show()
            }) {
                val existing = db.appDao().getUserByUsername(username)
                if (existing != null) {
                    Toast.makeText(this@RegisterActivity, "Username already exists", Toast.LENGTH_SHORT).show()
                } else {
                    val newId = db.appDao().insertUser(User(username = username, password = password))
                    Log.d(TAG, "Registered user '$username' id=$newId")
                    if (newId > 0) {
                        // Mirror to the cloud (password is intentionally NOT uploaded).
                        RealtimeSyncManager.pushUser(User(id = newId, username = username, password = ""))
                    }
                    Toast.makeText(this@RegisterActivity, "Account created successfully", Toast.LENGTH_SHORT).show()
                    finish()
                    overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
                }
            }
        }

        binding.btnLoginLink.setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        }
    }
}

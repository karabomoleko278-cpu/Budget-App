package com.iie.vaultquest.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.iie.vaultquest.data.AppDatabase
import com.iie.vaultquest.data.User
import com.iie.vaultquest.databinding.ActivityRegisterBinding
import kotlinx.coroutines.launch

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

            lifecycleScope.launch {
                val existing = db.appDao().getUserByUsername(username)
                if (existing != null) {
                    Toast.makeText(this@RegisterActivity, "Username already exists", Toast.LENGTH_SHORT).show()
                } else {
                    db.appDao().insertUser(User(username = username, password = password))
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

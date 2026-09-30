package com.abdelrahmanhazem

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class SignupActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        // Firebase
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        // Get the identity selected on the previous screen
        val identity = intent.getStringExtra("identity") ?: "Buyer"

        // Connect XML fields to Kotlin
        val nameInput = findViewById<EditText>(R.id.etName)
        val emailInput = findViewById<EditText>(R.id.etEmail)
        val passwordInput = findViewById<EditText>(R.id.etPassword)
        val ageInput = findViewById<EditText>(R.id.etAge)
        val signUpButton = findViewById<Button>(R.id.btnSignUp)
        val loginText = findViewById<TextView>(R.id.tvLogin)

        // Go to Login screen
        loginText.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }

        // Sign Up
        signUpButton.setOnClickListener {

            val name = nameInput.text.toString().trim()
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString()
            val age = ageInput.text.toString().trim()

            if (name.isEmpty() || email.isEmpty() || password.isEmpty() || age.isEmpty()) {
                Toast.makeText(
                    this,
                    "Please fill in all fields",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->

                    if (task.isSuccessful) {

                        val userId = auth.currentUser!!.uid

                        val user = mapOf(
                            "name" to name,
                            "email" to email,
                            "age" to age,
                            "identity" to identity
                        )

                        database.reference
                            .child("users")
                            .child(userId)
                            .setValue(user)
                            .addOnCompleteListener { databaseTask ->

                                if (databaseTask.isSuccessful) {

                                    Toast.makeText(
                                        this,
                                        "Account created successfully!",
                                        Toast.LENGTH_SHORT
                                    ).show()

                                    // User is already logged in by Firebase
                                    // Send them directly to Home
                                    val intent = Intent(
                                        this,
                                        HomeActivity::class.java
                                    )

                                    startActivity(intent)

                                    // Prevent returning to Sign Up with the Back button
                                    finish()

                                } else {

                                    Toast.makeText(
                                        this,
                                        "Account created, but profile could not be saved.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }

                    } else {

                        Toast.makeText(
                            this,
                            "Registration failed: ${task.exception?.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        }
    }
}
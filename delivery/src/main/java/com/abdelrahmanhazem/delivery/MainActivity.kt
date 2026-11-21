package com.abdelrahmanhazem.delivery

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        val emailInput = findViewById<EditText>(R.id.emailInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)
        val loginButton = findViewById<Button>(R.id.loginButton)
        val loginMessage = findViewById<TextView>(R.id.loginMessage)

        loginButton.setOnClickListener {

            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString()

            loginMessage.text = ""

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                loginMessage.text = "Please enter a valid email"
                return@setOnClickListener
            }

            if (password.isEmpty()) {
                loginMessage.text = "Please enter your password"
                return@setOnClickListener
            }

            loginButton.isEnabled = false
            loginButton.text = "Logging in..."

            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->

                    val userId = result.user?.uid

                    if (userId == null) {
                        auth.signOut()
                        loginButton.isEnabled = true
                        loginButton.text = "Sign in"
                        loginMessage.text = "Could not identify your account"
                        return@addOnSuccessListener
                    }

                    database.reference
                        .child("users")
                        .child(userId)
                        .child("identity")
                        .get()
                        .addOnSuccessListener { snapshot ->

                            val identity = snapshot.getValue(String::class.java)

                            if (identity.equals("Buyer", ignoreCase = true)) {

                                Toast.makeText(
                                    this,
                                    "Welcome to the Delivery App!",
                                    Toast.LENGTH_SHORT
                                ).show()

                                val intent = Intent(this, DeliveryHomeActivity::class.java)
                                startActivity(intent)
                                finish()

                            } else {

                                auth.signOut()

                                loginMessage.text =
                                    "Only existing Buyer accounts can access the Delivery App."

                                loginButton.isEnabled = true
                                loginButton.text = "Sign in"
                            }
                        }
                        .addOnFailureListener { error ->

                            auth.signOut()

                            loginMessage.text =
                                "Could not load your account. Please try again."

                            loginButton.isEnabled = true
                            loginButton.text = "Sign in"

                            Toast.makeText(
                                this,
                                error.message ?: "Database error",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                }
                .addOnFailureListener {

                    loginButton.isEnabled = true
                    loginButton.text = "Sign in"

                    loginMessage.text = "Incorrect email or password"
                }
        }
    }
}

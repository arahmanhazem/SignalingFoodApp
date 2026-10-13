package com.abdelrahmanhazem

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.database.FirebaseDatabase

/**
 * Log In screen, used by both paths.
 * After Firebase accepts the email/password, we read the user's role
 * (users/<uid>/identity). If it doesn't match the path they chose on the
 * Welcome screen, we sign them out and tell them to use the other path.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        // Which path is the user on? ("Buyer" or "Seller")
        val identity = intent.getStringExtra("identity") ?: "Buyer"

        findViewById<TextView>(R.id.tvRoleBadge).text =
            if (identity == "Seller") "SELLER ACCOUNT" else "BUYER ACCOUNT"

        val tilEmail = findViewById<TextInputLayout>(R.id.tilLoginEmail)
        val tilPassword = findViewById<TextInputLayout>(R.id.tilLoginPassword)
        val etEmail = findViewById<TextInputEditText>(R.id.etLoginEmail)
        val etPassword = findViewById<TextInputEditText>(R.id.etLoginPassword)
        val loginButton = findViewById<Button>(R.id.btnLogin)

        fun setLoading(loading: Boolean) {
            loginButton.isEnabled = !loading
            loginButton.text = if (loading) "Logging in…" else "Log in"
        }

        // Back arrow and "Sign up" link both return to the Sign Up screen
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvSignup).setOnClickListener { finish() }

        loginButton.setOnClickListener {
            val email = etEmail.text?.toString()?.trim() ?: ""
            val password = etPassword.text?.toString() ?: ""

            tilEmail.error = null
            tilPassword.error = null

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                tilEmail.error = "Please enter a valid email"
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                tilPassword.error = "Please enter your password"
                return@setOnClickListener
            }

            setLoading(true)

            // 1) Check email + password with Firebase Authentication
            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->
                    val userId = result.user!!.uid

                    // 2) Read this user's role from the database
                    database.reference.child("users").child(userId).child("identity").get()
                        .addOnSuccessListener { snapshot ->
                            val savedIdentity = snapshot.getValue(String::class.java)

                            if (savedIdentity.equals(identity, ignoreCase = true)) {
                                // Correct path: open the home screen
                                // (later: buyers will go to their own Buyer Home screen)
                                val home = Intent(this, HomeActivity::class.java)
                                home.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(home)
                            } else {
                                // Wrong path: log out immediately
                                auth.signOut()
                                setLoading(false)
                                val otherPath = if (identity == "Buyer") "I'm a seller" else "I want to order"
                                tilEmail.error = "This is a ${savedIdentity ?: "different"} account. " +
                                        "Go back and choose \"$otherPath\"."
                            }
                        }
                        .addOnFailureListener { e ->
                            auth.signOut()
                            setLoading(false)
                            Toast.makeText(this, "Could not load your account: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                }
                .addOnFailureListener { e ->
                    setLoading(false)
                    when (e) {
                        is FirebaseAuthInvalidUserException,
                        is FirebaseAuthInvalidCredentialsException ->
                            tilPassword.error = "Incorrect email or password"
                        else ->
                            Toast.makeText(this, "Login failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }
    }
}
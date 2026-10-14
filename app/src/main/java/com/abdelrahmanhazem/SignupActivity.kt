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
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.database.FirebaseDatabase

/**
 * Sign Up screen, used by both paths.
 * The role ("Buyer" or "Seller") comes from the Welcome screen and is saved
 * with the account under users/<uid>/identity.
 */
class SignupActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        // Which path did the user choose on the Welcome screen?
        val identity = intent.getStringExtra("identity") ?: "Buyer"

        // Show the role tag at the top
        findViewById<TextView>(R.id.tvRoleBadge).text =
            if (identity == "Seller") "SELLER ACCOUNT" else "BUYER ACCOUNT"

        // Input boxes (the layouts show the error messages)
        val tilName = findViewById<TextInputLayout>(R.id.tilName)
        val tilEmail = findViewById<TextInputLayout>(R.id.tilEmail)
        val tilAge = findViewById<TextInputLayout>(R.id.tilAge)
        val tilPassword = findViewById<TextInputLayout>(R.id.tilPassword)
        val etName = findViewById<TextInputEditText>(R.id.etName)
        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etAge = findViewById<TextInputEditText>(R.id.etAge)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val signUpButton = findViewById<Button>(R.id.btnSignUp)

        // Disables the button and changes its text while Firebase is working
        fun setLoading(loading: Boolean) {
            signUpButton.isEnabled = !loading
            signUpButton.text = if (loading) "Creating account…" else "Create account"
        }

        // Back arrow
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        // "Log in" link: open the Log In screen for the same path
        findViewById<TextView>(R.id.tvLogin).setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            intent.putExtra("identity", identity)
            startActivity(intent)
        }

        signUpButton.setOnClickListener {
            val name = etName.text?.toString()?.trim() ?: ""
            val email = etEmail.text?.toString()?.trim() ?: ""
            val age = etAge.text?.toString()?.trim() ?: ""
            val password = etPassword.text?.toString() ?: ""

            // Clear old errors
            tilName.error = null
            tilEmail.error = null
            tilAge.error = null
            tilPassword.error = null

            // --- Validation: show the error under the box that is wrong ---
            if (name.isEmpty()) {
                tilName.error = "Please enter your name"
                return@setOnClickListener
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                tilEmail.error = "Please enter a valid email"
                return@setOnClickListener
            }
            val ageNumber = age.toIntOrNull()
            if (ageNumber == null || ageNumber !in 1..120) {
                tilAge.error = "Please enter a valid age"
                return@setOnClickListener
            }
            if (password.length < 6) {
                tilPassword.error = "Use at least 6 characters"
                return@setOnClickListener
            }

            setLoading(true)

            // 1) Create the account in Firebase Authentication
            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->
                    val userId = result.user!!.uid

                    // 2) Save the profile in Realtime Database under users/<uid>
                    val user = mapOf(
                        "name" to name,
                        "email" to email,
                        "age" to age,
                        "identity" to identity
                    )

                    database.reference.child("users").child(userId).setValue(user)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Welcome, $name!", Toast.LENGTH_SHORT).show()

                            // Buyer → BuyerHome; Seller → SellerActivity (merged seller path)
                            val next = if (identity == "Seller") {
                                Intent(this, com.abdelrahmanhazem.seller.SellerActivity::class.java)
                            } else {
                                Intent(this, BuyerHomeActivity::class.java)
                            }
                            next.flags =
                                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(next)
                        }
                        .addOnFailureListener { e ->
                            setLoading(false)
                            Toast.makeText(this, "Could not save profile: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                }
                .addOnFailureListener { e ->
                    setLoading(false)
                    when (e) {
                        is FirebaseAuthUserCollisionException ->
                            tilEmail.error = "This email is already registered"
                        is FirebaseAuthWeakPasswordException ->
                            tilPassword.error = "This password is too weak"
                        else ->
                            Toast.makeText(this, "Sign up failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }
    }
}
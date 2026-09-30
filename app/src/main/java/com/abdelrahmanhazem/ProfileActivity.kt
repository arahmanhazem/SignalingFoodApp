package com.abdelrahmanhazem

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class ProfileActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        val nameText = findViewById<TextView>(R.id.tvProfileName)
        val emailText = findViewById<TextView>(R.id.tvProfileEmail)
        val passwordText = findViewById<TextView>(R.id.tvProfilePassword)
        val ageText = findViewById<TextView>(R.id.tvProfileAge)
        val identityText = findViewById<TextView>(R.id.tvProfileIdentity)
        val editProfileButton = findViewById<Button>(R.id.btnEditProfile)

        // Open Edit Profile screen
        editProfileButton.setOnClickListener {
            val intent = Intent(this, EditProfileActivity::class.java)
            startActivity(intent)
        }

        // Password stays hidden
        passwordText.text = "••••••••"

        val userId = auth.currentUser?.uid

        if (userId == null) {
            Toast.makeText(
                this,
                "No user is currently logged in.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // Load profile data
        loadProfileData(
            userId,
            nameText,
            emailText,
            ageText,
            identityText
        )
    }

    override fun onResume() {
        super.onResume()

        val userId = auth.currentUser?.uid ?: return

        val nameText = findViewById<TextView>(R.id.tvProfileName)
        val emailText = findViewById<TextView>(R.id.tvProfileEmail)
        val ageText = findViewById<TextView>(R.id.tvProfileAge)
        val identityText = findViewById<TextView>(R.id.tvProfileIdentity)

        // Reload profile whenever we return to this screen
        loadProfileData(
            userId,
            nameText,
            emailText,
            ageText,
            identityText
        )
    }

    private fun loadProfileData(
        userId: String,
        nameText: TextView,
        emailText: TextView,
        ageText: TextView,
        identityText: TextView
    ) {

        database.reference
            .child("users")
            .child(userId)
            .get()
            .addOnSuccessListener { snapshot ->

                if (snapshot.exists()) {

                    val name = snapshot.child("name").value?.toString()
                    val email = snapshot.child("email").value?.toString()
                    val age = snapshot.child("age").value?.toString()
                    val identity = snapshot.child("identity").value?.toString()

                    nameText.text = name ?: "Not available"
                    emailText.text = email ?: "Not available"
                    ageText.text = age ?: "Not available"
                    identityText.text = identity ?: "Not available"

                } else {

                    Toast.makeText(
                        this,
                        "Profile data not found.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .addOnFailureListener {

                Toast.makeText(
                    this,
                    "Failed to load profile.",
                    Toast.LENGTH_LONG
                ).show()
            }
    }
}
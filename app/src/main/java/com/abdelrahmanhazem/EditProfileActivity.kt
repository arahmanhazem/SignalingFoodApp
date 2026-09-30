package com.abdelrahmanhazem

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class EditProfileActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        val nameInput = findViewById<EditText>(R.id.etEditName)
        val ageInput = findViewById<EditText>(R.id.etEditAge)
        val passwordInput = findViewById<EditText>(R.id.etEditPassword)
        val saveButton = findViewById<Button>(R.id.btnSaveProfile)

        val currentUser = auth.currentUser

        if (currentUser == null) {
            Toast.makeText(
                this,
                "No user is currently logged in.",
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

        val userId = currentUser.uid

        // Load existing profile data
        database.reference
            .child("users")
            .child(userId)
            .get()
            .addOnSuccessListener { snapshot ->

                if (snapshot.exists()) {

                    nameInput.setText(
                        snapshot.child("name").value?.toString() ?: ""
                    )

                    ageInput.setText(
                        snapshot.child("age").value?.toString() ?: ""
                    )
                }
            }

        // Save changes
        saveButton.setOnClickListener {

            val newName = nameInput.text.toString().trim()
            val newAge = ageInput.text.toString().trim()
            val newPassword = passwordInput.text.toString()

            if (newName.isEmpty() || newAge.isEmpty()) {
                Toast.makeText(
                    this,
                    "Please fill in your name and age",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            // Update Name and Age in Realtime Database
            val updates = mapOf(
                "name" to newName,
                "age" to newAge
            )

            database.reference
                .child("users")
                .child(userId)
                .updateChildren(updates)
                .addOnSuccessListener {

                    // If password field is empty, keep the current password
                    if (newPassword.isEmpty()) {

                        Toast.makeText(
                            this,
                            "Profile updated successfully!",
                            Toast.LENGTH_SHORT
                        ).show()

                        finish()

                    } else {

                        // Update password in Firebase Authentication
                        currentUser.updatePassword(newPassword)
                            .addOnSuccessListener {

                                Toast.makeText(
                                    this,
                                    "Profile and password updated successfully!",
                                    Toast.LENGTH_SHORT
                                ).show()

                                finish()
                            }
                            .addOnFailureListener { exception ->

                                Toast.makeText(
                                    this,
                                    "Profile updated, but password could not be changed: ${exception.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                    }
                }
                .addOnFailureListener {

                    Toast.makeText(
                        this,
                        "Failed to update profile.",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }
}
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
        val saveButton = findViewById<Button>(R.id.btnSaveProfile)

        val userId = auth.currentUser?.uid

        if (userId == null) {
            Toast.makeText(
                this,
                "No user is currently logged in.",
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

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

            if (newName.isEmpty() || newAge.isEmpty()) {
                Toast.makeText(
                    this,
                    "Please fill in all fields",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val updates = mapOf(
                "name" to newName,
                "age" to newAge
            )

            database.reference
                .child("users")
                .child(userId)
                .updateChildren(updates)
                .addOnSuccessListener {

                    Toast.makeText(
                        this,
                        "Profile updated successfully!",
                        Toast.LENGTH_SHORT
                    ).show()

                    finish()
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
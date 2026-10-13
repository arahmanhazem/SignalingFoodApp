package com.abdelrahmanhazem

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

/**
 * Buyer Home screen (first screen of the buyer path after logging in).
 * Shows a personal greeting, search bar, categories and the shops section.
 * Shops and orders will be filled in Milestone 3.
 */
class BuyerHomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_buyer_home)

        // Only logged-in users can be here
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        val tvGreeting = findViewById<TextView>(R.id.tvGreeting)
        val tvAvatarInitial = findViewById<TextView>(R.id.tvAvatarInitial)

        // Read the buyer's name to personalise the greeting: "Hi, Lujayn"
        FirebaseDatabase.getInstance().reference
            .child("users").child(user.uid).child("name").get()
            .addOnSuccessListener { snapshot ->
                val firstName = (snapshot.getValue(String::class.java) ?: "")
                    .trim()
                    .substringBefore(" ")

                if (firstName.isNotEmpty()) {
                    tvGreeting.text = "Hi, $firstName"
                    tvAvatarInitial.text = firstName.take(1).uppercase()
                }
            }

        // Avatar opens the profile
        findViewById<View>(R.id.avatarButton).setOnClickListener { openProfile() }

        // Search is a placeholder until shops exist
        findViewById<View>(R.id.searchBar).setOnClickListener {
            Toast.makeText(this, "Search is coming soon", Toast.LENGTH_SHORT).show()
        }

        // Bottom navigation
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.selectedItemId = R.id.nav_home
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_orders -> {
                    Toast.makeText(this, "Orders are coming soon", Toast.LENGTH_SHORT).show()
                    false
                }
                R.id.nav_profile -> {
                    openProfile()
                    false // stay highlighted on Home when coming back
                }
                else -> false
            }
        }
    }

    private fun openProfile() {
        startActivity(Intent(this, ProfileActivity::class.java))
    }
}
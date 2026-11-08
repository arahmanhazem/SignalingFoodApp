package com.abdelrahmanhazem

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.abdelrahmanhazem.buyer.Shop
import com.abdelrahmanhazem.buyer.ShopAdapter
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener

/**
 * Buyer Home screen (first screen of the buyer path after logging in).
 * Shows a personal greeting and a grid of all shops (sellers), read live
 * from Firebase: users where identity = "Seller".
 */
class BuyerHomeActivity : AppCompatActivity() {

    private var shopsQuery: Query? = null
    private var shopsListener: ValueEventListener? = null

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

        val database = FirebaseDatabase.getInstance().reference
        val tvGreeting = findViewById<TextView>(R.id.tvGreeting)
        val tvAvatarInitial = findViewById<TextView>(R.id.tvAvatarInitial)

        // Read the buyer's name to personalise the greeting: "Hi, Lujayn"
        database.child("users").child(user.uid).child("name").get()
            .addOnSuccessListener { snapshot ->
                val firstName = (snapshot.getValue(String::class.java) ?: "")
                    .trim()
                    .substringBefore(" ")

                if (firstName.isNotEmpty()) {
                    tvGreeting.text = "Hi, $firstName"
                    tvAvatarInitial.text = firstName.take(1).uppercase()
                }
            }

        // --- Shops grid ---
        val rvShops = findViewById<RecyclerView>(R.id.rvShops)
        val progress = findViewById<ProgressBar>(R.id.progressShops)
        val emptyCard = findViewById<View>(R.id.cardEmptyShops)

        val adapter = ShopAdapter(this) { shop -> openShop(shop) }
        rvShops.layoutManager = GridLayoutManager(this, 2) // 2 cards per row
        rvShops.adapter = adapter

        // Live query: every seller account. Runs again whenever a seller is added or changed.
        val query = database.child("users").orderByChild("identity").equalTo("Seller")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val shops = snapshot.children
                    .map { Shop.from(it) }
                    .filter { it.shopName.isNotBlank() }
                    .sortedBy { it.shopName.lowercase() }

                adapter.submit(shops)
                progress.visibility = View.GONE
                emptyCard.visibility = if (shops.isEmpty()) View.VISIBLE else View.GONE
            }

            override fun onCancelled(error: DatabaseError) {
                progress.visibility = View.GONE
                emptyCard.visibility = View.VISIBLE
                Toast.makeText(
                    this@BuyerHomeActivity,
                    "Could not load shops: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        query.addValueEventListener(listener)
        shopsQuery = query
        shopsListener = listener

        // Avatar opens the profile
        findViewById<View>(R.id.avatarButton).setOnClickListener { openProfile() }

        // Search is a placeholder for now
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

    /** Opens a shop's products. (The Shop screen is the next step; for now it shows a message.) */
    private fun openShop(shop: Shop) {
        Toast.makeText(this, "Opening ${shop.shopName}…", Toast.LENGTH_SHORT).show()
    }

    private fun openProfile() {
        startActivity(Intent(this, ProfileActivity::class.java))
    }

    // Stop listening for shop changes when the screen closes
    override fun onDestroy() {
        super.onDestroy()
        val query = shopsQuery
        val listener = shopsListener
        if (query != null && listener != null) query.removeEventListener(listener)
    }
}
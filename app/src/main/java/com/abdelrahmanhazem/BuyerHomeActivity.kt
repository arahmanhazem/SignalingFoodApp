package com.abdelrahmanhazem

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.abdelrahmanhazem.buyer.Shop
import com.abdelrahmanhazem.buyer.ShopActivity
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
 * Shows a personal greeting, a search bar and a grid of all shops (sellers),
 * read live from Firebase: users where identity = "Seller".
 * Typing in the search bar filters the shops by shop name or owner name.
 */
class BuyerHomeActivity : AppCompatActivity() {

    private var shopsQuery: Query? = null
    private var shopsListener: ValueEventListener? = null

    /** Every shop from Firebase (the search filters this list). */
    private var allShops: List<Shop> = emptyList()

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
        val emptyTitle = findViewById<TextView>(R.id.tvEmptyShopsTitle)
        val emptyMessage = findViewById<TextView>(R.id.tvEmptyShopsMessage)
        val etSearch = findViewById<EditText>(R.id.etSearch)

        val adapter = ShopAdapter(this) { shop -> openShop(shop) }
        rvShops.layoutManager = GridLayoutManager(this, 2) // 2 cards per row
        rvShops.adapter = adapter

        /** Shows the shops that match the search text (all shops when it's empty). */
        fun showShops() {
            val text = etSearch.text.toString().trim()
            val shown = if (text.isEmpty()) allShops else allShops.filter {
                it.shopName.contains(text, ignoreCase = true) ||
                    it.ownerName.contains(text, ignoreCase = true)
            }
            adapter.submit(shown)

            emptyCard.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
            if (allShops.isEmpty()) {
                emptyTitle.text = "No shops yet"
                emptyMessage.text = "Sellers are setting up their shops.\nCheck back soon!"
            } else {
                emptyTitle.text = "No matches"
                emptyMessage.text = "No shop matches \"$text\"."
            }
        }

        // Live query: every seller account. Runs again whenever a seller is added or changed.
        val query = database.child("users").orderByChild("identity").equalTo("Seller")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                allShops = snapshot.children
                    .map { Shop.from(it) }
                    .filter { it.shopName.isNotBlank() }
                    .sortedBy { it.shopName.lowercase() }
                progress.visibility = View.GONE
                showShops()
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

        // --- Search: filter as the buyer types ---
        etSearch.doAfterTextChanged { showShops() }
        etSearch.setOnEditorActionListener { view, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                // Hide the keyboard when the buyer presses the search key
                getSystemService(InputMethodManager::class.java)
                    .hideSoftInputFromWindow(view.windowToken, 0)
                view.clearFocus()
                true
            } else {
                false
            }
        }

        // Avatar opens the profile
        findViewById<View>(R.id.avatarButton).setOnClickListener { openProfile() }

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

    /** Opens the shop's screen (Products + Cart tabs). */
    private fun openShop(shop: Shop) {
        startActivity(ShopActivity.intent(this, shop))
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

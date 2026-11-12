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
import com.abdelrahmanhazem.buyer.BuyerOrdersActivity
import com.abdelrahmanhazem.buyer.Shop
import com.abdelrahmanhazem.buyer.ShopActivity
import com.abdelrahmanhazem.buyer.ShopAdapter
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.chip.ChipGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener

/**
 * Buyer Home screen (first screen of the buyer path after logging in).
 * Greeting, search, sort chips and a live grid of shops (sellers).
 */
class BuyerHomeActivity : AppCompatActivity() {

    companion object {
        private const val SORT_RECOMMENDED = 0
        private const val SORT_AZ = 1
        private const val SORT_ZA = 2
        private const val SORT_MOST_ITEMS = 3
        private const val SORT_WITH_PHOTOS = 4
    }

    private var shopsQuery: Query? = null
    private var shopsListener: ValueEventListener? = null
    private var productsQuery: Query? = null
    private var productsListener: ValueEventListener? = null

    private var allShops: List<Shop> = emptyList()
    /** sellerId → how many products that shop has (for "Most items"). */
    private var itemCounts: Map<String, Int> = emptyMap()
    private var sortMode = SORT_RECOMMENDED

    private var showShopsFn: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_buyer_home)

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        val database = FirebaseDatabase.getInstance().reference
        val tvGreeting = findViewById<TextView>(R.id.tvGreeting)
        val tvAvatarInitial = findViewById<TextView>(R.id.tvAvatarInitial)

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

        val rvShops = findViewById<RecyclerView>(R.id.rvShops)
        val progress = findViewById<ProgressBar>(R.id.progressShops)
        val emptyCard = findViewById<View>(R.id.cardEmptyShops)
        val emptyTitle = findViewById<TextView>(R.id.tvEmptyShopsTitle)
        val emptyMessage = findViewById<TextView>(R.id.tvEmptyShopsMessage)
        val etSearch = findViewById<EditText>(R.id.etSearch)
        val chipGroupSort = findViewById<ChipGroup>(R.id.chipGroupSort)
        val tvShopCount = findViewById<TextView>(R.id.tvShopCount)

        val adapter = ShopAdapter(this) { shop -> openShop(shop) }
        rvShops.layoutManager = GridLayoutManager(this, 2)
        rvShops.adapter = adapter

        fun showShops() {
            val text = etSearch.text.toString().trim()
            var filtered = if (text.isEmpty()) allShops else allShops.filter {
                it.shopName.contains(text, ignoreCase = true) ||
                    it.ownerName.contains(text, ignoreCase = true)
            }
            if (sortMode == SORT_WITH_PHOTOS) {
                filtered = filtered.filter { it.photoUrl.isNotBlank() }
            }

            val shown = when (sortMode) {
                SORT_AZ -> filtered.sortedBy { it.shopName.lowercase() }
                SORT_ZA -> filtered.sortedByDescending { it.shopName.lowercase() }
                SORT_MOST_ITEMS -> filtered.sortedWith(
                    compareByDescending<Shop> { itemCounts[it.sellerId] ?: 0 }
                        .thenBy { it.shopName.lowercase() }
                )
                SORT_WITH_PHOTOS -> filtered.sortedBy { it.shopName.lowercase() }
                else -> filtered.sortedWith(
                    // Recommended: photo + richer inventory first (quick-commerce feel)
                    compareByDescending<Shop> { it.photoUrl.isNotBlank() }
                        .thenByDescending { itemCounts[it.sellerId] ?: 0 }
                        .thenBy { it.shopName.lowercase() }
                )
            }
            adapter.submit(shown)

            tvShopCount.text = when (shown.size) {
                0 -> ""
                1 -> "1 shop"
                else -> "${shown.size} shops"
            }

            emptyCard.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
            when {
                allShops.isEmpty() -> {
                    emptyTitle.text = "No shops yet"
                    emptyMessage.text = "Sellers are setting up their shops.\nCheck back soon!"
                }
                sortMode == SORT_WITH_PHOTOS && text.isEmpty() -> {
                    emptyTitle.text = "No photos yet"
                    emptyMessage.text = "No shops have uploaded a photo yet."
                }
                else -> {
                    emptyTitle.text = "No matches"
                    emptyMessage.text = if (text.isEmpty()) "Nothing matches this sort."
                    else "No shop matches \"$text\"."
                }
            }
        }
        showShopsFn = ::showShops

        chipGroupSort.setOnCheckedStateChangeListener { _, checkedIds ->
            sortMode = when (checkedIds.firstOrNull()) {
                R.id.chipSortAZ -> SORT_AZ
                R.id.chipSortZA -> SORT_ZA
                R.id.chipSortMostItems -> SORT_MOST_ITEMS
                R.id.chipSortWithPhotos -> SORT_WITH_PHOTOS
                else -> SORT_RECOMMENDED
            }
            showShops()
        }

        val shopsQ = database.child("users").orderByChild("identity").equalTo("Seller")
        val shopsL = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                allShops = snapshot.children
                    .map { Shop.from(it) }
                    .filter { it.shopName.isNotBlank() }
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
        shopsQ.addValueEventListener(shopsL)
        shopsQuery = shopsQ
        shopsListener = shopsL

        // Product counts power "Recommended" and "Most items"
        val productsQ = database.child("products")
        val productsL = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val counts = mutableMapOf<String, Int>()
                for (child in snapshot.children) {
                    val sellerId = child.child("sellerId").getValue(String::class.java).orEmpty()
                    if (sellerId.isNotBlank()) {
                        counts[sellerId] = (counts[sellerId] ?: 0) + 1
                    }
                }
                itemCounts = counts
                showShops()
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        productsQ.addValueEventListener(productsL)
        productsQuery = productsQ
        productsListener = productsL

        etSearch.doAfterTextChanged { showShops() }
        etSearch.setOnEditorActionListener { view, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                getSystemService(InputMethodManager::class.java)
                    .hideSoftInputFromWindow(view.windowToken, 0)
                view.clearFocus()
                true
            } else {
                false
            }
        }

        findViewById<View>(R.id.avatarButton).setOnClickListener { openProfile() }

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.selectedItemId = R.id.nav_home
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_orders -> {
                    startActivity(Intent(this, BuyerOrdersActivity::class.java))
                    false
                }
                R.id.nav_profile -> {
                    openProfile()
                    false
                }
                else -> false
            }
        }
    }

    private fun openShop(shop: Shop) {
        startActivity(ShopActivity.intent(this, shop))
    }

    private fun openProfile() {
        startActivity(Intent(this, ProfileActivity::class.java))
    }

    override fun onDestroy() {
        super.onDestroy()
        shopsListener?.let { shopsQuery?.removeEventListener(it) }
        productsListener?.let { productsQuery?.removeEventListener(it) }
        showShopsFn = null
    }
}

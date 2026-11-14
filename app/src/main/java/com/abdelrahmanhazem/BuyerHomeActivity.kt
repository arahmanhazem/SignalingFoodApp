package com.abdelrahmanhazem

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.abdelrahmanhazem.buyer.BuyerOrdersFragment
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

/** The activity owns one pager and one bottom navigation bar for all three pages. */
class BuyerHomeActivity : AppCompatActivity() {

    private lateinit var pager: ViewPager2

    private val pages = listOf(
        R.id.nav_home,
        R.id.nav_orders,
        R.id.nav_profile
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (FirebaseAuth.getInstance().currentUser == null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        // Reuse the bottom bar and its existing menu, colors, and labels.
        val template = layoutInflater.inflate(R.layout.activity_buyer_home, null)
        val navigation = template.findViewById<BottomNavigationView>(R.id.bottomNav)
        (navigation.parent as ViewGroup).removeView(navigation)

        val host = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            setBackgroundColor(getColor(R.color.background))
        }

        pager = ViewPager2(this).apply {
            // A stable framework ID lets Android restore the page after rotation.
            id = android.R.id.tabcontent
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
        }

        host.addView(
            pager,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )
        host.addView(navigation)
        setContentView(host)

        val backToHome = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                showPage(R.id.nav_home)
            }
        }
        onBackPressedDispatcher.addCallback(this, backToHome)

        pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = pages.size

            override fun createFragment(position: Int): Fragment =
                when (pages[position]) {
                    R.id.nav_home -> BuyerShopsFragment()
                    R.id.nav_orders -> BuyerOrdersFragment()
                    R.id.nav_profile -> BuyerProfileFragment()
                    else -> error("Unknown buyer page")
                }
        }

        // A swipe updates the bottom highlight without starting another activity.
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                navigation.menu.findItem(pages[position]).isChecked = true
                backToHome.isEnabled = position != 0
            }
        })

        navigation.setOnItemSelectedListener { item ->
            showPage(item.itemId)
            true
        }
    }

    fun showPage(menuId: Int) {
        val position = pages.indexOf(menuId)
        if (position >= 0) pager.setCurrentItem(position, true)
    }
}

/** First pager page: search, sort, and open the shops stored in Firebase. */
class BuyerShopsFragment : Fragment(R.layout.activity_buyer_home) {

    private var shopsQuery: Query? = null
    private var shopsListener: ValueEventListener? = null
    private var productsQuery: Query? = null
    private var productsListener: ValueEventListener? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Only the activity's shared bottom navigation should appear.
        val duplicateNav = view.findViewById<BottomNavigationView>(R.id.bottomNav)
        (duplicateNav.parent as ViewGroup).removeView(duplicateNav)
        view.fitsSystemWindows = false

        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseDatabase.getInstance().reference

        val greeting = view.findViewById<TextView>(R.id.tvGreeting)
        val initial = view.findViewById<TextView>(R.id.tvAvatarInitial)
        val list = view.findViewById<RecyclerView>(R.id.rvShops)
        val progress = view.findViewById<ProgressBar>(R.id.progressShops)
        val empty = view.findViewById<View>(R.id.cardEmptyShops)
        val emptyTitle = view.findViewById<TextView>(R.id.tvEmptyShopsTitle)
        val emptyMessage = view.findViewById<TextView>(R.id.tvEmptyShopsMessage)
        val search = view.findViewById<EditText>(R.id.etSearch)
        val filters = view.findViewById<ChipGroup>(R.id.chipGroupSort)
        val count = view.findViewById<TextView>(R.id.tvShopCount)

        val adapter = ShopAdapter(viewLifecycleOwner) { shop ->
            startActivity(ShopActivity.intent(requireContext(), shop))
        }

        list.layoutManager = GridLayoutManager(requireContext(), 2)
        list.adapter = adapter

        var shops: List<Shop> = emptyList()
        var productCounts: Map<String, Int> = emptyMap()
        var shopsLoaded = false

        fun render() {
            val text = search.text.toString().trim()
            val filter = filters.checkedChipId

            val matches = shops.filter { shop ->
                val matchesText = shop.shopName.contains(text, ignoreCase = true) ||
                        shop.ownerName.contains(text, ignoreCase = true)
                val matchesPhoto = filter != R.id.chipSortWithPhotos ||
                        shop.photoUrl.isNotBlank()
                matchesText && matchesPhoto
            }

            val shown = when (filter) {
                R.id.chipSortAZ, R.id.chipSortWithPhotos ->
                    matches.sortedBy { it.shopName.lowercase() }

                R.id.chipSortZA ->
                    matches.sortedByDescending { it.shopName.lowercase() }

                R.id.chipSortMostItems -> matches.sortedWith(
                    compareByDescending<Shop> { productCounts[it.sellerId] ?: 0 }
                        .thenBy { it.shopName.lowercase() }
                )

                else -> matches.sortedWith(
                    compareByDescending<Shop> { it.photoUrl.isNotBlank() }
                        .thenByDescending { productCounts[it.sellerId] ?: 0 }
                        .thenBy { it.shopName.lowercase() }
                )
            }

            adapter.submit(shown)

            count.text = when (shown.size) {
                0 -> ""
                1 -> "1 shop"
                else -> "${shown.size} shops"
            }

            empty.visibility =
                if (shopsLoaded && shown.isEmpty()) View.VISIBLE else View.GONE

            when {
                shops.isEmpty() -> {
                    emptyTitle.text = "No shops yet"
                    emptyMessage.text = "Sellers are setting up their shops."
                }
                filter == R.id.chipSortWithPhotos && text.isEmpty() -> {
                    emptyTitle.text = "No photos yet"
                    emptyMessage.text = "No shops have uploaded a photo yet."
                }
                else -> {
                    emptyTitle.text = "No matches"
                    emptyMessage.text = "Try another search or filter."
                }
            }
        }

        db.child("users").child(user.uid).child("name").get()
            .addOnSuccessListener { snapshot ->
                if (this.view !== view) return@addOnSuccessListener

                val name = snapshot.getValue(String::class.java)
                    .orEmpty().trim().substringBefore(" ")

                if (name.isNotEmpty()) {
                    greeting.text = "Hi, $name"
                    initial.text = name.take(1).uppercase()
                }
            }

        search.doAfterTextChanged { render() }
        filters.setOnCheckedStateChangeListener { _, _ -> render() }

        search.setOnEditorActionListener { field, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEARCH) {
                val keyboard = field.context.getSystemService(InputMethodManager::class.java)
                keyboard?.hideSoftInputFromWindow(field.windowToken, 0)
                field.clearFocus()
                true
            } else {
                false
            }
        }

        view.findViewById<View>(R.id.avatarButton).setOnClickListener {
            (requireActivity() as BuyerHomeActivity).showPage(R.id.nav_profile)
        }

        val shopRef = db.child("users").orderByChild("identity").equalTo("Seller")
        val shopUpdates = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                shops = snapshot.children.map { Shop.from(it) }
                    .filter { it.shopName.isNotBlank() }
                shopsLoaded = true
                progress.visibility = View.GONE
                render()
            }

            override fun onCancelled(error: DatabaseError) {
                progress.visibility = View.GONE
                Toast.makeText(view.context, error.message, Toast.LENGTH_LONG).show()
            }
        }

        shopsQuery = shopRef
        shopsListener = shopUpdates
        shopRef.addValueEventListener(shopUpdates)

        val productRef = db.child("products")
        val productUpdates = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                productCounts = snapshot.children
                    .mapNotNull { it.child("sellerId").getValue(String::class.java) }
                    .groupingBy { it }
                    .eachCount()
                render()
            }

            override fun onCancelled(error: DatabaseError) {
                // The shop list still works when inventory counts are unavailable.
            }
        }

        productsQuery = productRef
        productsListener = productUpdates
        productRef.addValueEventListener(productUpdates)
    }

    override fun onDestroyView() {
        shopsListener?.let { shopsQuery?.removeEventListener(it) }
        productsListener?.let { productsQuery?.removeEventListener(it) }
        super.onDestroyView()
    }
}
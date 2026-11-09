package com.abdelrahmanhazem.buyer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.abdelrahmanhazem.R
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener

/**
 * One shop: top bar + two swipeable tabs (ViewPager2 + TabLayout):
 *   0 = Products (ProductsFragment), 1 = Cart (CartFragment).
 * The Cart tab shows a badge with the number of items in the cart.
 */
class ShopActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_SELLER_ID = "sellerId"
        private const val EXTRA_SHOP_NAME = "shopName"
        private const val EXTRA_OWNER_NAME = "ownerName"

        /** The Intent that opens this screen for [shop]. */
        fun intent(context: Context, shop: Shop): Intent =
            Intent(context, ShopActivity::class.java)
                .putExtra(EXTRA_SELLER_ID, shop.sellerId)
                .putExtra(EXTRA_SHOP_NAME, shop.shopName)
                .putExtra(EXTRA_OWNER_NAME, shop.ownerName)
    }

    private var cartRef: DatabaseReference? = null
    private var cartListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shop)

        val buyerId = FirebaseAuth.getInstance().currentUser?.uid
        val sellerId = intent.getStringExtra(EXTRA_SELLER_ID).orEmpty()
        val shopName = intent.getStringExtra(EXTRA_SHOP_NAME).orEmpty()
        val ownerName = intent.getStringExtra(EXTRA_OWNER_NAME).orEmpty()
        if (buyerId == null || sellerId.isEmpty()) {
            finish()
            return
        }

        // Top bar
        findViewById<TextView>(R.id.tvShopTitle).text = shopName
        findViewById<TextView>(R.id.tvShopSubtitle).text =
            if (ownerName.isBlank()) "Fresh groceries" else "by $ownerName"
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        // Two swipeable tabs
        val tabs = findViewById<TabLayout>(R.id.tabs)
        val pager = findViewById<ViewPager2>(R.id.pager)
        pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 2
            override fun createFragment(position: Int): Fragment =
                if (position == 0) ProductsFragment.newInstance(sellerId, shopName)
                else CartFragment.newInstance(sellerId)
        }
        TabLayoutMediator(tabs, pager) { tab, position ->
            tab.text = if (position == 0) "Products" else "Cart"
        }.attach()

        // Cart badge: number of items, updated live
        val ref = Cart.ref(buyerId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val count = Cart.parse(snapshot).count
                val badge = tabs.getTabAt(1)?.orCreateBadge ?: return
                badge.backgroundColor = getColor(R.color.brand)
                badge.badgeTextColor = getColor(R.color.white)
                badge.number = count
                badge.isVisible = count > 0
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        cartRef = ref
        cartListener = listener
    }

    // Stop listening when the screen closes
    override fun onDestroy() {
        cartListener?.let { cartRef?.removeEventListener(it) }
        super.onDestroy()
    }
}
package com.abdelrahmanhazem.seller

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import android.view.View
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.abdelrahmanhazem.MainActivity
import com.abdelrahmanhazem.R
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener

/**
 * Seller home (Milestone 3): bottom navigation with
 *  - Products: the seller's items in a grid (tap one to edit it)
 *  - Add:      form to add a new item with a photo uploaded to S3
 *  - Orders:   incoming buyer orders (with a local notification on new ones)
 *  - Profile:  the existing shop profile screen from Milestone 2
 */
class SellerHomeActivity : AppCompatActivity() {

    companion object {
        private const val CHANNEL_ORDERS = "seller_orders"
        private const val NOTIF_REQ = 2001
    }

    private lateinit var bottomNav: BottomNavigationView
    private lateinit var pager: ViewPager2

    private val pageIds = listOf(
        R.id.nav_seller_products,
        R.id.nav_seller_add,
        R.id.nav_seller_orders
    )

    private var ordersQuery: Query? = null
    private var ordersListener: ValueEventListener? = null
    private val knownOrderIds = mutableSetOf<String>()
    private var ordersReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Only signed-in sellers can be here (SellerActivity checks the role before opening this)
        if (FirebaseAuth.getInstance().currentUser == null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_seller_home)
        bottomNav = findViewById(R.id.sellerBottomNav)

        createOrderChannel()
        askNotificationPermission()
        listenForNewOrders()

        pager = findViewById(R.id.sellerPager)

        pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = pageIds.size

            override fun createFragment(position: Int): Fragment {
                return when (pageIds[position]) {
                    R.id.nav_seller_products -> SellerProductsFragment()
                    R.id.nav_seller_add -> ProductFormFragment.newInstance()
                    R.id.nav_seller_orders -> SellerOrdersFragment()
                    else -> error("Unknown seller page")
                }
            }
        }

        // When the seller swipes, highlight the matching bottom tab.
        pager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    val selectedId = pageIds[position]
                    bottomNav.menu.findItem(selectedId).isChecked = true

                    if (selectedId == R.id.nav_seller_orders) {
                        bottomNav.removeBadge(selectedId)
                    }
                }
            }
        )

        // A tab tap moves the same pager. Profile opens its own activity.
        bottomNav.setOnItemSelectedListener { item ->
            if (item.itemId == R.id.nav_seller_profile) {
                startActivity(
                    Intent(this, SellerActivity::class.java)
                        .putExtra(SellerActivity.EXTRA_SHOW_PROFILE, true)
                )
                false
            } else {
                val position = pageIds.indexOf(item.itemId)
                if (position >= 0) {
                    pager.setCurrentItem(position, true)
                }
                position >= 0
            }
        }

        // Back from an edited product reveals the grid again.
        supportFragmentManager.addOnBackStackChangedListener {
            updateEditorVisibility()
        }
        updateEditorVisibility()
    }

    private fun updateEditorVisibility() {
        val editing = supportFragmentManager.findFragmentByTag("edit") != null
        val editor = findViewById<View>(R.id.sellerContainer)

        editor.visibility = if (editing) View.VISIBLE else View.GONE
        pager.visibility = if (editing) View.GONE else View.VISIBLE
        bottomNav.visibility = if (editing) View.GONE else View.VISIBLE
    }

    /** The Add screen calls this after it successfully saves a product. */
    fun showProducts() {
        pager.setCurrentItem(0, true)
    }

    /** Opens an existing product for editing and supports the Android Back button. */
    fun openEditor(productId: String) {
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(
                R.id.sellerContainer,
                ProductFormFragment.newInstance(productId),
                "edit"
            )
            .addToBackStack("edit")
            .commit()
    }

    private fun createOrderChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ORDERS,
            "Incoming orders",
            NotificationManager.IMPORTANCE_HIGH
        )
        channel.description = "Alerts when a buyer places an order"
        manager.createNotificationChannel(channel)
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) return
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIF_REQ)
    }

    /** Live listener: first load seeds known IDs; later new orders notify + badge. */
    private fun listenForNewOrders() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val q = FirebaseDatabase.getInstance().getReference(Order.NODE)
            .orderByChild("sellerId").equalTo(uid)
        val l = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val currentIds = snapshot.children.mapNotNull { it.key }.toSet()
                if (!ordersReady) {
                    knownOrderIds.clear()
                    knownOrderIds.addAll(currentIds)
                    ordersReady = true
                    updateOrdersBadge(0)
                    return
                }
                val newIds = currentIds - knownOrderIds
                knownOrderIds.clear()
                knownOrderIds.addAll(currentIds)
                if (newIds.isEmpty()) return

                // Show a badge on the Orders tab
                val badge = bottomNav.getOrCreateBadge(R.id.nav_seller_orders)
                badge.backgroundColor = getColor(R.color.brand)
                badge.badgeTextColor = getColor(R.color.white)
                badge.number = (badge.number.takeIf { badge.hasNumber() } ?: 0) + newIds.size
                badge.isVisible = true

                // One notification for the newest new order
                val newest = newIds.mapNotNull { id ->
                    snapshot.child(id).let { Order.fromSnapshot(it) }
                }.maxByOrNull { it.createdAt }
                if (newest != null) showOrderNotification(newest)
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        q.addValueEventListener(l)
        ordersQuery = q
        ordersListener = l
    }

    private fun updateOrdersBadge(count: Int) {
        if (count <= 0) {
            bottomNav.removeBadge(R.id.nav_seller_orders)
            return
        }
        val badge = bottomNav.getOrCreateBadge(R.id.nav_seller_orders)
        badge.backgroundColor = getColor(R.color.brand)
        badge.badgeTextColor = getColor(R.color.white)
        badge.number = count
        badge.isVisible = true
    }

    private fun showOrderNotification(order: Order) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val text = if (order.itemCount == 1) {
            "1 item · ${money(order.total)}"
        } else {
            "${order.itemCount} items · ${money(order.total)}"
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ORDERS)
            .setSmallIcon(R.drawable.ic_receipt)
            .setContentTitle("New order received")
            .setContentText(text)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${order.itemSummary}\n$text\n${order.status}")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify(order.id.hashCode(), notification)
    }

    private fun money(amount: Double): String =
        if (amount % 1.0 == 0.0) "EGP ${amount.toLong()}"
        else String.format(java.util.Locale.US, "EGP %.2f", amount)

    override fun onDestroy() {
        ordersListener?.let { ordersQuery?.removeEventListener(it) }
        super.onDestroy()
    }
}

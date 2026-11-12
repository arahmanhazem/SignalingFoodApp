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
import androidx.fragment.app.FragmentManager
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

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_seller_products -> {
                    showTab(SellerProductsFragment())
                    true
                }
                R.id.nav_seller_add -> {
                    showTab(ProductFormFragment.newInstance())
                    true
                }
                R.id.nav_seller_orders -> {
                    // Clear the badge when the seller opens Orders
                    bottomNav.removeBadge(R.id.nav_seller_orders)
                    showTab(SellerOrdersFragment())
                    true
                }
                R.id.nav_seller_profile -> {
                    startActivity(
                        Intent(this, SellerActivity::class.java)
                            .putExtra(SellerActivity.EXTRA_SHOW_PROFILE, true)
                    )
                    false // keep the current tab highlighted when coming back
                }
                else -> false
            }
        }

        if (savedInstanceState == null) {
            bottomNav.selectedItemId = R.id.nav_seller_products
        }
    }

    /** Replaces the screen content with a tab, closing any open Edit screen first. */
    private fun showTab(fragment: Fragment) {
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.beginTransaction()
            .replace(R.id.sellerContainer, fragment)
            .commit()
    }

    /** Called by the Add screen after saving a new product. */
    fun showProducts() {
        bottomNav.selectedItemId = R.id.nav_seller_products
    }

    /** Opens the Edit screen for one product; Back returns to the grid. */
    fun openEditor(productId: String) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.sellerContainer, ProductFormFragment.newInstance(productId))
            .addToBackStack("edit")
            .commit()
    }

    // ---------- New-order notifications ----------

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

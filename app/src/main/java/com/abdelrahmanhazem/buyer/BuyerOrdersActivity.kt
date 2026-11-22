package com.abdelrahmanhazem.buyer

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.abdelrahmanhazem.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener

class BuyerOrdersActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, BuyerOrdersFragment())
                .commit()
        }
    }
}

/** The same live orders list works as a pager page or a standalone screen. */
class BuyerOrdersFragment : Fragment(R.layout.activity_buyer_orders) {

    companion object {
        private const val CHANNEL_STATUS = "buyer_order_status"
    }

    private var query: Query? = null
    private var listener: ValueEventListener? = null

    /** orderId → last known status (used to detect seller/delivery updates). */
    private val knownStatuses = mutableMapOf<String, String>()
    private var primed = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val buyerId = FirebaseAuth.getInstance().currentUser?.uid
        if (buyerId == null) {
            requireActivity().finish()
            return
        }

        createStatusChannel()

        val list = view.findViewById<RecyclerView>(R.id.rvBuyerOrders)
        val progress = view.findViewById<ProgressBar>(R.id.progressBuyerOrders)
        val empty = view.findViewById<View>(R.id.emptyBuyerOrders)
        val count = view.findViewById<TextView>(R.id.tvBuyerOrderCount)

        view.findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        val adapter = BuyerOrderAdapter()
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter

        val orderRef = FirebaseDatabase.getInstance().reference
            .child(Order.NODE)
            .orderByChild("buyerId")
            .equalTo(buyerId)

        val updates = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val orders = snapshot.children
                    .map { Order.from(it) }
                    .filter { it.id.isNotBlank() }
                    .sortedByDescending { it.createdAt }

                if (!primed) {
                    knownStatuses.clear()
                    orders.forEach { knownStatuses[it.id] = it.status }
                    primed = true
                } else {
                    for (order in orders) {
                        val previous = knownStatuses[order.id]
                        if (previous != null && previous != order.status) {
                            notifyStatusChange(order)
                        }
                        knownStatuses[order.id] = order.status
                    }
                }

                adapter.submit(orders)
                progress.visibility = View.GONE
                empty.visibility = if (orders.isEmpty()) View.VISIBLE else View.GONE
                list.visibility = if (orders.isEmpty()) View.GONE else View.VISIBLE

                count.text = when (orders.size) {
                    0 -> "Nothing here yet"
                    1 -> "1 order"
                    else -> "${orders.size} orders"
                }
            }

            override fun onCancelled(error: DatabaseError) {
                progress.visibility = View.GONE
                Toast.makeText(
                    view.context,
                    "Could not load orders: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        query = orderRef
        listener = updates
        orderRef.addValueEventListener(updates)
    }

    /** Alert dialog + system notification when the seller/delivery updates status. */
    private fun notifyStatusChange(order: Order) {
        val ctx = context ?: return
        val title = "Order update · ${order.shopName}"
        val body = "${order.itemSummary}\nStatus: ${order.status}"

        MaterialAlertDialogBuilder(ctx)
            .setTitle(title)
            .setMessage(body)
            .setPositiveButton("OK", null)
            .show()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notification = NotificationCompat.Builder(ctx, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_receipt)
            .setContentTitle(title)
            .setContentText(order.status)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(ctx)
            .notify("buyer_status_${order.id}", order.id.hashCode(), notification)
    }

    private fun createStatusChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val ctx = context ?: return
        val manager = ctx.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_STATUS,
            "Order status updates",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        channel.description = "Alerts when your order status changes"
        manager.createNotificationChannel(channel)
    }

    override fun onDestroyView() {
        listener?.let { query?.removeEventListener(it) }
        super.onDestroyView()
    }
}

package com.abdelrahmanhazem.buyer

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.abdelrahmanhazem.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener

/**
 * Buyer Orders screen: live list of orders this buyer placed
 * (orders where buyerId = signed-in user). Newest first.
 * Status updates live when the seller approves or declines.
 */
class BuyerOrdersActivity : AppCompatActivity() {

    private var ordersQuery: Query? = null
    private var ordersListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_buyer_orders)

        val buyerId = FirebaseAuth.getInstance().currentUser?.uid
        if (buyerId == null) {
            finish()
            return
        }

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        val rv = findViewById<RecyclerView>(R.id.rvBuyerOrders)
        val progress = findViewById<ProgressBar>(R.id.progressBuyerOrders)
        val empty = findViewById<View>(R.id.emptyBuyerOrders)
        val tvCount = findViewById<TextView>(R.id.tvBuyerOrderCount)

        val adapter = BuyerOrderAdapter()
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        val query = FirebaseDatabase.getInstance().reference
            .child(Order.NODE)
            .orderByChild("buyerId")
            .equalTo(buyerId)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val orders = snapshot.children
                    .map { Order.from(it) }
                    .filter { it.id.isNotBlank() }
                    .sortedByDescending { it.createdAt }

                adapter.submit(orders)
                progress.visibility = View.GONE
                empty.visibility = if (orders.isEmpty()) View.VISIBLE else View.GONE
                rv.visibility = if (orders.isEmpty()) View.GONE else View.VISIBLE

                tvCount.text = when (orders.size) {
                    0 -> "Nothing here yet"
                    1 -> "1 order"
                    else -> "${orders.size} orders"
                }
            }

            override fun onCancelled(error: DatabaseError) {
                progress.visibility = View.GONE
                Toast.makeText(
                    this@BuyerOrdersActivity,
                    "Could not load orders: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        query.addValueEventListener(listener)
        ordersQuery = query
        ordersListener = listener
    }

    override fun onDestroy() {
        ordersListener?.let { ordersQuery?.removeEventListener(it) }
        super.onDestroy()
    }
}

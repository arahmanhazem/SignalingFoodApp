
package com.abdelrahmanhazem.delivery

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class AvailableOrdersActivity : AppCompatActivity() {

    private lateinit var ordersMessage: TextView
    private lateinit var ordersRecyclerView: RecyclerView

    private val database = FirebaseDatabase.getInstance().reference
    private val orders = mutableListOf<DeliveryOrder>()
    private lateinit var adapter: DeliveryOrderAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_available_orders)

        ordersMessage = findViewById(R.id.ordersMessage)
        ordersRecyclerView = findViewById(R.id.ordersRecyclerView)

        adapter = DeliveryOrderAdapter(orders) { order ->
            acceptOrder(order)
        }

        ordersRecyclerView.layoutManager = LinearLayoutManager(this)
        ordersRecyclerView.adapter = adapter

        loadAvailableOrders()
    }

    private fun loadAvailableOrders() {
        database.child("orders")
            .addValueEventListener(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {
                    orders.clear()

                    for (order in snapshot.children) {
                        val method = order.child("fulfillmentMethod")
                            .getValue(String::class.java)

                        val status = order.child("status")
                            .getValue(String::class.java)

                        val partnerId = order.child("deliveryPartnerId")
                            .getValue(String::class.java)

                        if (method.equals("Delivery", ignoreCase = true) &&
                            status.equals("Delivering", ignoreCase = true) &&
                            partnerId.isNullOrEmpty()
                        ) {
                            orders.add(
                                DeliveryOrder(
                                    id = order.key ?: "",
                                    shopName = order.child("shopName")
                                        .getValue(String::class.java) ?: "Unknown shop",
                                    deliveryAddress = order.child("deliveryAddress")
                                        .getValue(String::class.java) ?: "Address unavailable",
                                    deliveryFee = order.child("deliveryFee").value?.toString()
                                        ?: "0",
                                    total = order.child("total").value?.toString() ?: "0"
                                )
                            )
                        }
                    }

                    adapter.notifyDataSetChanged()

                    ordersMessage.text = if (orders.isEmpty()) {
                        "No orders currently need a delivery partner."
                    } else {
                        "${orders.size} available delivery order(s)"
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    ordersMessage.text = "Could not load orders: ${error.message}"
                }
            })
    }

    private fun acceptOrder(order: DeliveryOrder) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid

        if (userId == null) {
            Toast.makeText(this, "Please sign in again.", Toast.LENGTH_SHORT).show()
            return
        }

        database.child("orders").child(order.id).child("deliveryPartnerId")
            .runTransaction(object : Transaction.Handler {

                // DELIVERY APP BONUS: Atomically claim an unassigned delivery order.
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    val partnerId = currentData.value?.toString()

                    if (!partnerId.isNullOrEmpty()) {
                        return Transaction.abort()
                    }

                    currentData.value = userId
                    return Transaction.success(currentData)
                }
                override fun onComplete(
                    error: DatabaseError?,
                    committed: Boolean,
                    currentData: DataSnapshot?
                ) {
                    if (committed) {
                        Toast.makeText(
                            this@AvailableOrdersActivity,
                            "Order accepted successfully!",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            this@AvailableOrdersActivity,
                            error?.message ?: "This order is no longer available.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            })
    }
}

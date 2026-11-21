
package com.abdelrahmanhazem.delivery

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

data class MyDelivery(
    val id: String,
    val shopName: String,
    val address: String,
    val status: String
)

class MyDeliveriesActivity : AppCompatActivity() {

    private lateinit var message: TextView
    private lateinit var recyclerView: RecyclerView
    private val deliveries = mutableListOf<MyDelivery>()
    private lateinit var adapter: MyDeliveriesAdapter
    private val database = FirebaseDatabase.getInstance().reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_deliveries)

        message = findViewById(R.id.myDeliveriesMessage)
        recyclerView = findViewById(R.id.myDeliveriesRecyclerView)

        adapter = MyDeliveriesAdapter(deliveries) { delivery ->
            completeDelivery(delivery)
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        loadMyDeliveries()
    }

    private fun loadMyDeliveries() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid

        if (userId == null) {
            message.text = "Please sign in again."
            return
        }

        database.child("orders")
            .addValueEventListener(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {
                    deliveries.clear()

                    for (order in snapshot.children) {
                        val partnerId = order.child("deliveryPartnerId")
                            .getValue(String::class.java)

                        if (partnerId == userId) {
                            deliveries.add(
                                MyDelivery(
                                    id = order.key ?: "",
                                    shopName = order.child("shopName")
                                        .getValue(String::class.java) ?: "Unknown shop",
                                    address = order.child("deliveryAddress")
                                        .getValue(String::class.java) ?: "Address unavailable",
                                    status = order.child("status")
                                        .getValue(String::class.java) ?: "Unknown"
                                )
                            )
                        }
                    }

                    adapter.notifyDataSetChanged()

                    message.text = if (deliveries.isEmpty()) {
                        "You haven't accepted any deliveries yet."
                    } else {
                        "${deliveries.size} delivery order(s)"
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    message.text = "Could not load deliveries: ${error.message}"
                }
            })
    }


    private fun completeDelivery(delivery: MyDelivery) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // DELIVERY APP BONUS: Complete the order and award points in one atomic update.
        val updates = hashMapOf<String, Any>(
            "orders/${delivery.id}/status" to "Delivered",
            "orders/${delivery.id}/deliveredAt" to ServerValue.TIMESTAMP,
            "orders/${delivery.id}/deliveryPointsAwarded" to true,
            "users/$userId/deliveryPoints" to ServerValue.increment(50)
        )

        // DELIVERY APP BONUS: Firebase rules ensure this delivery can only be rewarded once.
        database.updateChildren(updates)
            .addOnSuccessListener {
                Toast.makeText(
                    this@MyDeliveriesActivity,
                    "Delivery completed! You earned 50 points.",
                    Toast.LENGTH_LONG
                ).show()
            }
            .addOnFailureListener { error ->
                Toast.makeText(
                    this@MyDeliveriesActivity,
                    error.message ?: "Could not complete this delivery.",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

}

class MyDeliveriesAdapter(
    private val deliveries: List<MyDelivery>,
    private val onComplete: (MyDelivery) -> Unit
) : RecyclerView.Adapter<MyDeliveriesAdapter.DeliveryViewHolder>() {

    class DeliveryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val shop: TextView = view.findViewById(R.id.myDeliveryShop)
        val address: TextView = view.findViewById(R.id.myDeliveryAddress)
        val status: TextView = view.findViewById(R.id.myDeliveryStatus)
        val button: Button = view.findViewById(R.id.completeDeliveryButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeliveryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_my_delivery, parent, false)
        return DeliveryViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeliveryViewHolder, position: Int) {
        val delivery = deliveries[position]

        holder.shop.text = delivery.shopName
        holder.address.text = "Delivery address: ${delivery.address}"
        holder.status.text = "Status: ${delivery.status}"

        val completed = delivery.status.equals("Delivered", ignoreCase = true)

        holder.button.visibility = if (completed) View.GONE else View.VISIBLE
        holder.button.setOnClickListener {
            onComplete(delivery)
        }
    }

    override fun getItemCount(): Int = deliveries.size
}

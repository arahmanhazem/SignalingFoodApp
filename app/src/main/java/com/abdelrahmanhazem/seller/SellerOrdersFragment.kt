package com.abdelrahmanhazem.seller

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.abdelrahmanhazem.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Query
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import java.util.Locale

class SellerOrdersFragment : Fragment(R.layout.fragment_seller_orders) {

    private var query: Query? = null
    private var listener: ValueEventListener? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvCount = view.findViewById<TextView>(R.id.tvOrderCount)
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmptyOrders)
        val progress = view.findViewById<ProgressBar>(R.id.progressOrders)
        val list = view.findViewById<RecyclerView>(R.id.rvOrders)

        val adapter = OrderAdapter(
            onAdvance = { order ->
                order.nextStatus()?.let { next ->
                    confirmStatus(order, next, "Set order to $next?")
                }
            },
            onDecline = { order ->
                confirmStatus(order, Order.STATUS_DECLINED, "Decline this order?")
            }
        )

        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val ordersQuery = FirebaseDatabase.getInstance()
            .getReference(Order.NODE)
            .orderByChild("sellerId")
            .equalTo(uid)

        val ordersListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val orders = snapshot.children
                    .mapNotNull { Order.fromSnapshot(it) }
                    .sortedWith(
                        compareByDescending<Order> { it.needsApproval() }
                            .thenByDescending { it.createdAt }
                    )

                adapter.submitList(orders)
                progress.visibility = View.GONE

                val pending = orders.count { it.needsApproval() }

                tvCount.text = when {
                    orders.isEmpty() -> "No orders yet"
                    pending == 0 && orders.size == 1 -> "1 order"
                    pending == 0 -> "${orders.size} orders"
                    else -> "$pending waiting · ${orders.size} total"
                }

                tvEmpty.visibility =
                    if (orders.isEmpty()) View.VISIBLE else View.GONE
            }

            override fun onCancelled(error: DatabaseError) {
                progress.visibility = View.GONE
                tvEmpty.visibility = View.VISIBLE
                tvEmpty.text = "Could not load orders: ${error.message}"
            }
        }

        query = ordersQuery
        listener = ordersListener
        ordersQuery.addValueEventListener(ordersListener)
    }

    private fun confirmStatus(order: Order, target: String, title: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage("${order.itemSummary}\n${money(order.total)}")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Confirm") { _, _ ->
                setStatus(order, target)
            }
            .show()
    }

    private fun setStatus(order: Order, target: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val orderRef = FirebaseDatabase.getInstance()
            .getReference(Order.NODE)
            .child(order.id)

        orderRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(data: MutableData): Transaction.Result {
                // Firebase may initially have no local copy of this order.
                // Returning the unchanged data lets it retry with server data.
                if (data.value == null) {
                    return Transaction.success(data)
                }

                val storedSeller = data.child("sellerId")
                    .getValue(String::class.java)

                val storedStatus = data.child("status")
                    .getValue(String::class.java)
                    .orEmpty()

                val storedMethod = data.child("fulfillmentMethod")
                    .getValue(String::class.java)
                    .orEmpty()

                // Reject an action if the order changed after the card was shown.
                if (storedSeller != uid || storedStatus != order.status) {
                    return Transaction.abort()
                }

                val currentOrder = order.copy(
                    status = storedStatus,
                    fulfillmentMethod = storedMethod
                )

                val advancing = target == currentOrder.nextStatus()
                val declining = target == Order.STATUS_DECLINED &&
                        currentOrder.needsApproval()

                if (!advancing && !declining) {
                    return Transaction.abort()
                }

                data.child("status").value = target
                data.child("updatedAt").value = ServerValue.TIMESTAMP

                return Transaction.success(data)
            }

            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                snapshot: DataSnapshot?
            ) {
                val message = when {
                    error != null -> "Could not update order: ${error.message}"
                    committed && snapshot?.child("status")?.value == target ->
                        "Order: $target"
                    else -> "This order changed. Check its latest status."
                }

                context?.let { ctx ->
                    Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
                }
            }
        }, false)
    }

    private fun money(amount: Double): String =
        if (amount % 1.0 == 0.0) {
            "EGP ${amount.toLong()}"
        } else {
            String.format(Locale.US, "EGP %.2f", amount)
        }

    override fun onDestroyView() {
        listener?.let { query?.removeEventListener(it) }
        query = null
        listener = null
        super.onDestroyView()
    }
}
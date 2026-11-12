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
import com.google.firebase.database.Query
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener

/**
 * Orders tab: live list of orders placed for the signed-in seller.
 * Pending orders can be Approved or Declined; the status is saved in Firebase.
 */
class SellerOrdersFragment : Fragment(R.layout.fragment_seller_orders) {

    private var query: Query? = null
    private var listener: ValueEventListener? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val tvCount = view.findViewById<TextView>(R.id.tvOrderCount)
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmptyOrders)
        val progress = view.findViewById<ProgressBar>(R.id.progressOrders)
        val list = view.findViewById<RecyclerView>(R.id.rvOrders)

        val adapter = OrderAdapter(
            onApprove = { order -> confirmStatus(order, Order.STATUS_APPROVED, "Approve this order?") },
            onDecline = { order -> confirmStatus(order, Order.STATUS_DECLINED, "Decline this order?") }
        )
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val q = FirebaseDatabase.getInstance().getReference(Order.NODE)
            .orderByChild("sellerId").equalTo(uid)

        val l = object : ValueEventListener {
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
                    pending == 1 -> "1 waiting · ${orders.size} total"
                    else -> "$pending waiting · ${orders.size} total"
                }
                tvEmpty.visibility = if (orders.isEmpty()) View.VISIBLE else View.GONE
            }

            override fun onCancelled(error: DatabaseError) {
                progress.visibility = View.GONE
                tvEmpty.visibility = View.VISIBLE
                tvEmpty.text = "Could not load orders: ${error.message}"
            }
        }
        q.addValueEventListener(l)
        query = q
        listener = l
    }

    /** Asks first, then writes the new status to orders/<id>. */
    private fun confirmStatus(order: Order, newStatus: String, title: String) {
        val message = "${order.itemSummary}\n${money(order.total)}"
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton("Cancel", null)
            .setPositiveButton(if (newStatus == Order.STATUS_APPROVED) "Approve" else "Decline") { _, _ ->
                setStatus(order.id, newStatus)
            }
            .show()
    }

    private fun setStatus(orderId: String, status: String) {
        FirebaseDatabase.getInstance().getReference(Order.NODE).child(orderId)
            .updateChildren(
                mapOf(
                    "status" to status,
                    "updatedAt" to ServerValue.TIMESTAMP
                )
            )
            .addOnSuccessListener {
                val label = if (status == Order.STATUS_APPROVED) "Order approved" else "Order declined"
                Toast.makeText(requireContext(), label, Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(
                    requireContext(),
                    "Could not update order: ${it.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun money(amount: Double): String =
        if (amount % 1.0 == 0.0) "EGP ${amount.toLong()}"
        else String.format(java.util.Locale.US, "EGP %.2f", amount)

    override fun onDestroyView() {
        listener?.let { query?.removeEventListener(it) }
        query = null
        listener = null
        super.onDestroyView()
    }
}

package com.abdelrahmanhazem.buyer

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.abdelrahmanhazem.R
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

    private var query: Query? = null
    private var listener: ValueEventListener? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val buyerId = FirebaseAuth.getInstance().currentUser?.uid
        if (buyerId == null) {
            requireActivity().finish()
            return
        }

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

    override fun onDestroyView() {
        listener?.let { query?.removeEventListener(it) }
        super.onDestroyView()
    }
}
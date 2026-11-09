package com.abdelrahmanhazem.seller

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.abdelrahmanhazem.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener

/**
 * Products tab: the signed-in seller's grocery items in a 2-column grid.
 * Uses a live Firebase listener, so adds/edits show up instantly here and in the buyer app.
 */
class SellerProductsFragment : Fragment(R.layout.fragment_seller_products) {

    private var query: Query? = null
    private var listener: ValueEventListener? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val tvCount = view.findViewById<TextView>(R.id.tvProductCount)
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmpty)
        val list = view.findViewById<RecyclerView>(R.id.rvProducts)

        val adapter = SellerProductAdapter(viewLifecycleOwner.lifecycleScope) { product ->
            (activity as? SellerHomeActivity)?.openEditor(product.id)
        }
        list.layoutManager = GridLayoutManager(requireContext(), 2)
        list.adapter = adapter

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val q = FirebaseDatabase.getInstance().getReference(Product.NODE)
            .orderByChild("sellerId").equalTo(uid)

        val l = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val products = snapshot.children
                    .mapNotNull { Product.fromSnapshot(it) }
                    .sortedBy { it.name.lowercase() }
                adapter.submitList(products)
                tvCount.text = when (products.size) {
                    1 -> "1 item"
                    else -> "${products.size} items"
                }
                tvEmpty.visibility = if (products.isEmpty()) View.VISIBLE else View.GONE
            }

            override fun onCancelled(error: DatabaseError) {
                tvEmpty.visibility = View.VISIBLE
                tvEmpty.text = "Could not load products: ${error.message}"
            }
        }
        q.addValueEventListener(l)
        query = q
        listener = l
    }

    override fun onDestroyView() {
        listener?.let { query?.removeEventListener(it) }
        query = null
        listener = null
        super.onDestroyView()
    }
}

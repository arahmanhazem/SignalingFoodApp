package com.abdelrahmanhazem.buyer

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.abdelrahmanhazem.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener

/**
 * Cart tab of a shop.
 * - Shows the buyer's cart live (carts/<buyerUid>): items, quantities, total.
 * - Clear cart, and Place order (writes orders/<id> and empties the cart together).
 * - Reads this shop's stock so "+" can't go above what the shop has.
 */
class CartFragment : Fragment(R.layout.fragment_cart) {

    companion object {
        private const val ARG_SELLER_ID = "sellerId"

        /** Creates the cart tab for one shop. */
        fun newInstance(sellerId: String) = CartFragment().apply {
            arguments = bundleOf(ARG_SELLER_ID to sellerId)
        }
    }

    private var cartRef: DatabaseReference? = null
    private var cartListener: ValueEventListener? = null
    private var stockQuery: Query? = null
    private var stockListener: ValueEventListener? = null

    private var state = Cart.State("", "", emptyList())
    private var stocks: Map<String, Int> = emptyMap()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sellerId = requireArguments().getString(ARG_SELLER_ID).orEmpty()
        val buyerId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val content = view.findViewById<View>(R.id.cartContent)
        val empty = view.findViewById<View>(R.id.emptyCart)
        val progress = view.findViewById<ProgressBar>(R.id.progressCart)
        val tvShop = view.findViewById<TextView>(R.id.tvCartShop)
        val tvCount = view.findViewById<TextView>(R.id.tvCartCount)
        val tvTotal = view.findViewById<TextView>(R.id.tvCartTotal)
        val btnClear = view.findViewById<Button>(R.id.btnClearCart)
        val btnPlace = view.findViewById<Button>(R.id.btnPlaceOrder)
        val rvCart = view.findViewById<RecyclerView>(R.id.rvCart)

        val adapter = CartAdapter(
            owner = viewLifecycleOwner,
            onPlus = { item ->
                val stock = stocks[item.productId]
                if (stock == null || item.qty >= stock) {
                    showError("No more of this item in stock.")
                    return@CartAdapter
                }
                Cart.ref(buyerId).child("items").child(item.productId).child("qty")
                    .setValue(ServerValue.increment(1))
                    .addOnFailureListener { showError("Could not update cart: ${it.message}") }
            },
            onMinus = { item ->
                Cart.removeOne(buyerId, item.productId, item.qty)
                    .addOnFailureListener { showError("Could not update cart: ${it.message}") }
            }
        )
        rvCart.layoutManager = LinearLayoutManager(requireContext())
        rvCart.adapter = adapter

        /** Redraws everything from the current cart + stock. */
        fun render() {
            progress.visibility = View.GONE
            val hasItems = state.items.isNotEmpty()
            content.visibility = if (hasItems) View.VISIBLE else View.GONE
            empty.visibility = if (hasItems) View.GONE else View.VISIBLE

            val shopLabel = state.shopName.ifBlank { "this shop" }
            tvShop.text = "From $shopLabel"
            tvCount.text = if (state.count == 1) "1 item in basket" else "${state.count} items in basket"
            tvTotal.text = egp(state.total)
            btnPlace.text = "Place order · ${egp(state.total)}"
            adapter.submit(state, stocks)
        }

        // --- The buyer's cart, live ---
        val ref = Cart.ref(buyerId)
        val cListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                state = Cart.parse(snapshot)
                render()
            }

            override fun onCancelled(error: DatabaseError) {
                progress.visibility = View.GONE
                showError("Could not load cart: ${error.message}")
            }
        }
        ref.addValueEventListener(cListener)
        cartRef = ref
        cartListener = cListener

        // --- This shop's stock (productId → quantity), live ---
        val query = FirebaseDatabase.getInstance().reference
            .child("products").orderByChild("sellerId").equalTo(sellerId)
        val sListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                stocks = snapshot.children.map { Product.from(it) }.associate { it.id to it.stock }
                render()
            }

            override fun onCancelled(error: DatabaseError) { /* stock limit just won't apply */ }
        }
        query.addValueEventListener(sListener)
        stockQuery = query
        stockListener = sListener

        // --- Clear cart (asks first) ---
        btnClear.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Clear your cart?")
                .setMessage("All items from ${state.shopName} will be removed.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear") { _, _ ->
                    Cart.clear(buyerId)
                        .addOnFailureListener { showError("Could not clear cart: ${it.message}") }
                }
                .show()
        }

        // --- Place order (asks first) ---
        btnPlace.setOnClickListener {
            val order = state
            if (order.items.isEmpty()) return@setOnClickListener

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Place your order?")
                .setMessage("${tvCount.text} from ${order.shopName}\nTotal: ${egp(order.total)}")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Place order") { _, _ ->
                    btnPlace.isEnabled = false
                    btnPlace.text = "Placing order…"
                    Cart.placeOrder(buyerId, order)
                        .addOnSuccessListener {
                            btnPlace.isEnabled = true
                            context?.let { ctx ->
                                MaterialAlertDialogBuilder(ctx)
                                    .setTitle("Order placed!")
                                    .setMessage("${order.shopName} received your order.\nStatus: ${Cart.FIRST_ORDER_STATUS}")
                                    .setPositiveButton("OK", null)
                                    .show()
                            }
                        }
                        .addOnFailureListener {
                            btnPlace.isEnabled = true
                            render()
                            showError("Could not place order: ${it.message}")
                        }
                }
                .show()
        }
    }

    private fun showError(message: String) {
        context?.let { Toast.makeText(it, message, Toast.LENGTH_LONG).show() }
    }

    // Stop listening when the tab's view is destroyed
    override fun onDestroyView() {
        cartListener?.let { cartRef?.removeEventListener(it) }
        stockListener?.let { stockQuery?.removeEventListener(it) }
        super.onDestroyView()
    }
}

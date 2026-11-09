package com.abdelrahmanhazem.buyer

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.abdelrahmanhazem.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener

/**
 * Products tab of a shop.
 * - Reads the shop's products live: products where sellerId = this shop.
 * - Reads the buyer's cart live, to show "+" or "− qty +" on each card.
 * - A cart holds items from ONE shop: adding from another shop asks first.
 */
class ProductsFragment : Fragment(R.layout.fragment_products) {

    companion object {
        private const val ARG_SELLER_ID = "sellerId"
        private const val ARG_SHOP_NAME = "shopName"

        /** Creates the tab for one shop. */
        fun newInstance(sellerId: String, shopName: String) = ProductsFragment().apply {
            arguments = bundleOf(ARG_SELLER_ID to sellerId, ARG_SHOP_NAME to shopName)
        }
    }

    private var productsQuery: Query? = null
    private var productsListener: ValueEventListener? = null
    private var cartRef: DatabaseReference? = null
    private var cartListener: ValueEventListener? = null
    private var cart = Cart.State("", "", emptyList())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sellerId = requireArguments().getString(ARG_SELLER_ID).orEmpty()
        val shopName = requireArguments().getString(ARG_SHOP_NAME).orEmpty()
        val buyerId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val rvProducts = view.findViewById<RecyclerView>(R.id.rvProducts)
        val progress = view.findViewById<ProgressBar>(R.id.progressProducts)
        val empty = view.findViewById<View>(R.id.emptyProducts)

        val adapter = ProductAdapter(
            owner = viewLifecycleOwner,
            onAdd = { product -> addToCart(buyerId, shopName, product) },
            onRemove = { product, qty ->
                Cart.removeOne(buyerId, product.id, qty)
                    .addOnFailureListener { showError("Could not update cart: ${it.message}") }
            }
        )
        rvProducts.layoutManager = GridLayoutManager(requireContext(), 2)
        rvProducts.adapter = adapter

        // --- The shop's products, live ---
        val query = FirebaseDatabase.getInstance().reference
            .child("products").orderByChild("sellerId").equalTo(sellerId)
        val pListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val products = snapshot.children
                    .map { Product.from(it) }
                    .filter { it.name.isNotBlank() }
                    .sortedBy { it.name.lowercase() }
                adapter.submitProducts(products)
                progress.visibility = View.GONE
                empty.visibility = if (products.isEmpty()) View.VISIBLE else View.GONE
            }

            override fun onCancelled(error: DatabaseError) {
                progress.visibility = View.GONE
                showError("Could not load products: ${error.message}")
            }
        }
        query.addValueEventListener(pListener)
        productsQuery = query
        productsListener = pListener

        // --- The buyer's cart, live (for the quantities on the cards) ---
        val ref = Cart.ref(buyerId)
        val cListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                cart = Cart.parse(snapshot)
                adapter.submitCart(cart)
            }

            override fun onCancelled(error: DatabaseError) {
                showError("Could not load cart: ${error.message}")
            }
        }
        ref.addValueEventListener(cListener)
        cartRef = ref
        cartListener = cListener
    }

    /** Adds one item; if the cart has items from another shop, asks before replacing it. */
    private fun addToCart(buyerId: String, shopName: String, product: Product) {
        val fromAnotherShop = cart.items.isNotEmpty() && cart.sellerId != product.sellerId
        if (!fromAnotherShop) {
            Cart.addOne(buyerId, shopName, product)
                .addOnFailureListener { showError("Could not add to cart: ${it.message}") }
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Start a new cart?")
            .setMessage("Your cart has items from ${cart.shopName}. Clear it and add ${product.name} from $shopName instead?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("New cart") { _, _ ->
                Cart.clear(buyerId)
                    .continueWithTask { Cart.addOne(buyerId, shopName, product) }
                    .addOnFailureListener { showError("Could not add to cart: ${it.message}") }
            }
            .show()
    }

    private fun showError(message: String) {
        context?.let { Toast.makeText(it, message, Toast.LENGTH_LONG).show() }
    }

    // Stop listening when the tab's view is destroyed
    override fun onDestroyView() {
        productsListener?.let { productsQuery?.removeEventListener(it) }
        cartListener?.let { cartRef?.removeEventListener(it) }
        super.onDestroyView()
    }
}
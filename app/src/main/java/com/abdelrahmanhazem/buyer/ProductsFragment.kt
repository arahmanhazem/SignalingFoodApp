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
import com.google.android.material.chip.ChipGroup
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
 * - Sort chips: A–Z, Z–A, price low→high, price high→low.
 * - A cart holds items from ONE shop: adding from another shop asks first.
 * - Sold-out items cannot be added; quantity cannot go above stock.
 */
class ProductsFragment : Fragment(R.layout.fragment_products) {

    companion object {
        private const val ARG_SELLER_ID = "sellerId"
        private const val ARG_SHOP_NAME = "shopName"

        private const val SORT_RECOMMENDED = 0
        private const val SORT_AZ = 1
        private const val SORT_ZA = 2
        private const val SORT_PRICE_LOW = 3
        private const val SORT_PRICE_HIGH = 4
        private const val SORT_IN_STOCK = 5

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
    private var allProducts: List<Product> = emptyList()
    private var sortMode = SORT_RECOMMENDED
    private var adapter: ProductAdapter? = null
    private var emptyView: View? = null
    private var progressView: ProgressBar? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sellerId = requireArguments().getString(ARG_SELLER_ID).orEmpty()
        val shopName = requireArguments().getString(ARG_SHOP_NAME).orEmpty()
        val buyerId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val rvProducts = view.findViewById<RecyclerView>(R.id.rvProducts)
        val progress = view.findViewById<ProgressBar>(R.id.progressProducts)
        val empty = view.findViewById<View>(R.id.emptyProducts)
        val chipGroup = view.findViewById<ChipGroup>(R.id.chipGroupProductSort)
        progressView = progress
        emptyView = empty

        val productAdapter = ProductAdapter(
            owner = viewLifecycleOwner,
            onAdd = { product -> addToCart(buyerId, shopName, product) },
            onRemove = { product, qty ->
                Cart.removeOne(buyerId, product.id, qty)
                    .addOnFailureListener { showError("Could not update cart: ${it.message}") }
            }
        )
        adapter = productAdapter
        rvProducts.layoutManager = GridLayoutManager(requireContext(), 2)
        rvProducts.adapter = productAdapter

        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            sortMode = when (checkedIds.firstOrNull()) {
                R.id.chipProductAZ -> SORT_AZ
                R.id.chipProductZA -> SORT_ZA
                R.id.chipProductPriceLow -> SORT_PRICE_LOW
                R.id.chipProductPriceHigh -> SORT_PRICE_HIGH
                R.id.chipProductInStock -> SORT_IN_STOCK
                else -> SORT_RECOMMENDED
            }
            showProducts()
        }

        // --- The shop's products, live ---
        val query = FirebaseDatabase.getInstance().reference
            .child("products").orderByChild("sellerId").equalTo(sellerId)
        val pListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                allProducts = snapshot.children
                    .map { Product.from(it) }
                    .filter { it.name.isNotBlank() }
                progress.visibility = View.GONE
                showProducts()
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
                productAdapter.submitCart(cart)
            }

            override fun onCancelled(error: DatabaseError) {
                showError("Could not load cart: ${error.message}")
            }
        }
        ref.addValueEventListener(cListener)
        cartRef = ref
        cartListener = cListener
    }

    /** Applies the selected sort / filter and refreshes the grid. */
    private fun showProducts() {
        val base = if (sortMode == SORT_IN_STOCK) {
            allProducts.filter { it.stock > 0 }
        } else {
            allProducts
        }
        val sorted = when (sortMode) {
            SORT_AZ -> base.sortedBy { it.name.lowercase() }
            SORT_ZA -> base.sortedByDescending { it.name.lowercase() }
            SORT_PRICE_LOW -> base.sortedBy { it.price }
            SORT_PRICE_HIGH -> base.sortedByDescending { it.price }
            SORT_IN_STOCK -> base.sortedBy { it.name.lowercase() }
            else -> base.sortedWith(
                // Recommended: in-stock first, then name
                compareByDescending<Product> { it.stock > 0 }
                    .thenBy { it.name.lowercase() }
            )
        }
        adapter?.submitProducts(sorted)
        emptyView?.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE
    }

    /** Adds one item; blocks sold-out / over-stock; asks before replacing another shop's cart. */
    private fun addToCart(buyerId: String, shopName: String, product: Product) {
        if (product.stock <= 0) {
            showError("This item is sold out.")
            return
        }
        val inCart = if (cart.sellerId == product.sellerId) cart.qtyOf(product.id) else 0
        if (inCart >= product.stock) {
            showError("No more of this item in stock.")
            return
        }

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
        adapter = null
        emptyView = null
        progressView = null
        super.onDestroyView()
    }
}

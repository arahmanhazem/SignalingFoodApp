package com.abdelrahmanhazem.buyer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import com.abdelrahmanhazem.R

/**
 * Fills the Products grid: one item_product card per Product.
 * Shows "+" when the product isn't in the cart, "− qty +" when it is,
 * and "Sold out" when there is no stock.
 */
class ProductAdapter(
    private val owner: LifecycleOwner,
    private val onAdd: (Product) -> Unit,
    private val onRemove: (Product, Int) -> Unit
) : RecyclerView.Adapter<ProductAdapter.Holder>() {

    private val products = mutableListOf<Product>()
    private var cart = Cart.State("", "", emptyList())

    /** New list of products from Firebase. */
    fun submitProducts(newProducts: List<Product>) {
        products.clear()
        products.addAll(newProducts)
        notifyDataSetChanged()
    }

    /** The cart changed: refresh the quantities shown on the cards. */
    fun submitCart(newCart: Cart.State) {
        cart = newCart
        notifyDataSetChanged()
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val photo: ImageView = view.findViewById(R.id.ivProductPhoto)
        val initial: TextView = view.findViewById(R.id.tvProductInitial)
        val name: TextView = view.findViewById(R.id.tvProductName)
        val stock: TextView = view.findViewById(R.id.tvProductStock)
        val price: TextView = view.findViewById(R.id.tvProductPrice)
        val add: TextView = view.findViewById(R.id.btnAdd)
        val stepper: View = view.findViewById(R.id.stepper)
        val minus: TextView = view.findViewById(R.id.btnMinus)
        val qty: TextView = view.findViewById(R.id.tvQty)
        val plus: TextView = view.findViewById(R.id.btnPlus)
        val soldOut: TextView = view.findViewById(R.id.tvSoldOut)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_product, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = products.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val product = products[position]
        // Only count the cart if it belongs to this shop
        val inCart = if (cart.sellerId == product.sellerId) cart.qtyOf(product.id) else 0

        holder.name.text = product.name
        holder.price.text = egp(product.price)
        holder.initial.text = product.name.take(1).uppercase().ifEmpty { "?" }
        holder.stock.text = if (product.stock > 0) "${product.stock} left" else "Out of stock"

        // Product photo: S3 folder products/<sellerUid>/
        PhotoLoader.load(owner, holder.photo, holder.initial, "products", product.sellerId, product.imageUrl)

        // Which control to show: Sold out / + / − qty +
        val soldOut = product.stock <= 0 && inCart == 0
        holder.soldOut.visibility = if (soldOut) View.VISIBLE else View.GONE
        holder.add.visibility = if (!soldOut && inCart == 0) View.VISIBLE else View.GONE
        holder.stepper.visibility = if (inCart > 0) View.VISIBLE else View.GONE
        holder.qty.text = inCart.toString()

        // Can't add more than the shop has
        val canAddMore = inCart < product.stock
        holder.plus.isEnabled = canAddMore
        holder.plus.alpha = if (canAddMore) 1f else 0.35f

        holder.add.setOnClickListener { onAdd(product) }
        holder.plus.setOnClickListener { if (canAddMore) onAdd(product) }
        holder.minus.setOnClickListener { onRemove(product, inCart) }
    }
}
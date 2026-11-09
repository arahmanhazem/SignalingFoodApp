package com.abdelrahmanhazem.seller

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.abdelrahmanhazem.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

/** Grid cards for the seller's products: photo, name, price and stock. Tap a card to edit it. */
class SellerProductAdapter(
    private val scope: CoroutineScope,
    private val onClick: (Product) -> Unit
) : ListAdapter<Product, SellerProductAdapter.Holder>(Diff) {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.ivProduct)
        val name: TextView = view.findViewById(R.id.tvProductName)
        val price: TextView = view.findViewById(R.id.tvProductPrice)
        val stock: TextView = view.findViewById(R.id.tvProductStock)
        var imageJob: Job? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_seller_product, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val product = getItem(position)
        holder.name.text = product.name
        holder.price.text = String.format(Locale.US, "EGP %.2f", product.price)
        holder.stock.text = if (product.quantity > 0) "${product.quantity} in stock" else "Out of stock"
        holder.stock.setTextColor(
            holder.itemView.context.getColor(
                if (product.quantity > 0) R.color.text_secondary else R.color.error
            )
        )
        holder.itemView.setOnClickListener { onClick(product) }

        // Load the photo from S3 (or memory) without blocking scrolling
        holder.imageJob?.cancel()
        val cached = ProductImages.cached(product.imageUrl)
        if (cached != null) {
            holder.image.setImageBitmap(cached)
        } else {
            holder.image.setImageResource(R.drawable.ic_shopping_bag)
            holder.imageJob = scope.launch {
                val bitmap = ProductImages.load(product)
                if (bitmap != null && holder.bindingAdapterPosition != RecyclerView.NO_POSITION &&
                    getItem(holder.bindingAdapterPosition).imageUrl == product.imageUrl
                ) {
                    holder.image.setImageBitmap(bitmap)
                }
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<Product>() {
        override fun areItemsTheSame(a: Product, b: Product) = a.id == b.id
        override fun areContentsTheSame(a: Product, b: Product) = a == b
    }
}

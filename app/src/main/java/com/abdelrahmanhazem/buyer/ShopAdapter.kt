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
 * Fills the buyer's shops grid: one item_shop card per Shop.
 * Tapping a card calls [onClick] with that shop.
 */
class ShopAdapter(
    private val owner: LifecycleOwner,
    private val onClick: (Shop) -> Unit
) : RecyclerView.Adapter<ShopAdapter.Holder>() {

    private val shops = mutableListOf<Shop>()

    /** Replaces the list shown in the grid. */
    fun submit(newShops: List<Shop>) {
        shops.clear()
        shops.addAll(newShops)
        notifyDataSetChanged()
    }

    /** Holds the views of one card, so findViewById runs once per card, not on every scroll. */
    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val photo: ImageView = view.findViewById(R.id.ivShopPhoto)
        val initial: TextView = view.findViewById(R.id.tvShopInitial)
        val name: TextView = view.findViewById(R.id.tvShopName)
        val ownerName: TextView = view.findViewById(R.id.tvShopOwner)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_shop, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = shops.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val shop = shops[position]

        holder.name.text = shop.shopName.ifBlank { "Unnamed shop" }
        holder.ownerName.text = if (shop.ownerName.isBlank()) "" else "by ${shop.ownerName}"
        holder.initial.text = shop.shopName.take(1).uppercase().ifEmpty { "?" }

        // Shop photo = the seller's profile photo, stored in S3 under seller-profiles/<sellerUid>/
        PhotoLoader.load(owner, holder.photo, holder.initial, "seller-profiles", shop.sellerId, shop.photoUrl)

        holder.itemView.setOnClickListener { onClick(shop) }
    }
}
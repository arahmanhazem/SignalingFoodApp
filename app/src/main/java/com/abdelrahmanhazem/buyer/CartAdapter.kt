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
 * Fills the Cart list: one item_cart row per cart item.
 * [stocks] (productId → available quantity) stops "+" going above the shop's stock.
 */
class CartAdapter(
    private val owner: LifecycleOwner,
    private val onPlus: (Cart.Item) -> Unit,
    private val onMinus: (Cart.Item) -> Unit
) : RecyclerView.Adapter<CartAdapter.Holder>() {

    private var state = Cart.State("", "", emptyList())
    private var stocks: Map<String, Int> = emptyMap()

    fun submit(newState: Cart.State, newStocks: Map<String, Int>) {
        state = newState
        stocks = newStocks
        notifyDataSetChanged()
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val photo: ImageView = view.findViewById(R.id.ivCartPhoto)
        val initial: TextView = view.findViewById(R.id.tvCartInitial)
        val name: TextView = view.findViewById(R.id.tvCartName)
        val unit: TextView = view.findViewById(R.id.tvCartUnit)
        val lineTotal: TextView = view.findViewById(R.id.tvCartLineTotal)
        val minus: TextView = view.findViewById(R.id.btnCartMinus)
        val qty: TextView = view.findViewById(R.id.tvCartQty)
        val plus: TextView = view.findViewById(R.id.btnCartPlus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_cart, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = state.items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = state.items[position]

        holder.name.text = item.name
        holder.unit.text = "${egp(item.price)} each"
        holder.lineTotal.text = egp(item.lineTotal)
        holder.qty.text = item.qty.toString()
        holder.initial.text = item.name.take(1).uppercase().ifEmpty { "?" }

        PhotoLoader.load(owner, holder.photo, holder.initial, "products", state.sellerId, item.imageUrl)

        // Only allow "+" when we know stock and qty is still below it
        val stock = stocks[item.productId]
        val canAddMore = stock != null && item.qty < stock
        holder.plus.isEnabled = canAddMore
        holder.plus.alpha = if (canAddMore) 1f else 0.35f

        holder.plus.setOnClickListener { if (canAddMore) onPlus(item) }
        holder.minus.setOnClickListener { onMinus(item) }
    }
}

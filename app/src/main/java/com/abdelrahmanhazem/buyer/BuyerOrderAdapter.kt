package com.abdelrahmanhazem.buyer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.abdelrahmanhazem.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Fills the buyer's Orders list: one item_buyer_order card per Order. */
class BuyerOrderAdapter : RecyclerView.Adapter<BuyerOrderAdapter.Holder>() {

    private val orders = mutableListOf<Order>()

    fun submit(newOrders: List<Order>) {
        orders.clear()
        orders.addAll(newOrders)
        notifyDataSetChanged()
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val status: TextView = view.findViewById(R.id.tvBuyerOrderStatus)
        val total: TextView = view.findViewById(R.id.tvBuyerOrderTotal)
        val shop: TextView = view.findViewById(R.id.tvBuyerOrderShop)
        val items: TextView = view.findViewById(R.id.tvBuyerOrderItems)
        val meta: TextView = view.findViewById(R.id.tvBuyerOrderMeta)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_buyer_order, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = orders.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val order = orders[position]
        val ctx = holder.itemView.context
        val status = order.status.ifBlank { Order.STATUS_WAITING }

        holder.status.text = status
        when (status) {
            Order.STATUS_APPROVED -> {
                holder.status.setBackgroundResource(R.drawable.bg_status_approved)
                holder.status.setTextColor(ContextCompat.getColor(ctx, R.color.success))
            }
            Order.STATUS_DECLINED -> {
                holder.status.setBackgroundResource(R.drawable.bg_status_declined)
                holder.status.setTextColor(ContextCompat.getColor(ctx, R.color.error))
            }
            else -> {
                holder.status.setBackgroundResource(R.drawable.bg_chip)
                holder.status.setTextColor(ContextCompat.getColor(ctx, R.color.brand_dark))
            }
        }

        holder.total.text = egp(order.total)
        holder.shop.text = order.shopName.ifBlank { "Shop" }
        holder.items.text = order.itemSummary

        val countLabel = if (order.itemCount == 1) "1 item" else "${order.itemCount} items"
        val timeLabel = if (order.createdAt > 0L) " · ${formatTime(order.createdAt)}" else ""
        holder.meta.text = countLabel + timeLabel
    }

    private fun formatTime(millis: Long): String {
        val fmt = SimpleDateFormat("dd MMM, HH:mm", Locale.US)
        return fmt.format(Date(millis))
    }
}

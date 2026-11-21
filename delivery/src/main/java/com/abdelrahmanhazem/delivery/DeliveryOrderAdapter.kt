
package com.abdelrahmanhazem.delivery

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class DeliveryOrder(
    val id: String,
    val shopName: String,
    val deliveryAddress: String,
    val deliveryFee: String,
    val total: String
)

class DeliveryOrderAdapter(
    private val orders: List<DeliveryOrder>,
    private val onAccept: (DeliveryOrder) -> Unit
) : RecyclerView.Adapter<DeliveryOrderAdapter.OrderViewHolder>() {

    class OrderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val shopName: TextView = view.findViewById(R.id.orderShopName)
        val address: TextView = view.findViewById(R.id.orderAddress)
        val fee: TextView = view.findViewById(R.id.orderFee)
        val total: TextView = view.findViewById(R.id.orderTotal)
        val acceptButton: Button = view.findViewById(R.id.acceptOrderButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_available_order, parent, false)
        return OrderViewHolder(view)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        val order = orders[position]

        holder.shopName.text = order.shopName
        holder.address.text = "Delivery address: ${order.deliveryAddress}"
        holder.fee.text = "Delivery fee: ${order.deliveryFee} EGP"
        holder.total.text = "Order total: ${order.total} EGP"

        holder.acceptButton.setOnClickListener {
            onAccept(order)
        }
    }

    override fun getItemCount(): Int = orders.size
}

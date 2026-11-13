package com.abdelrahmanhazem.seller

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.abdelrahmanhazem.R
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OrderAdapter(
    private val onAdvance: (Order) -> Unit,
    private val onDecline: (Order) -> Unit
) : RecyclerView.Adapter<OrderAdapter.Holder>() {

    private val orders = mutableListOf<Order>()

    fun submitList(newOrders: List<Order>) {
        orders.clear()
        orders.addAll(newOrders)
        notifyDataSetChanged()
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val status: TextView = view.findViewById(R.id.tvOrderStatus)
        val total: TextView = view.findViewById(R.id.tvOrderTotal)
        val items: TextView = view.findViewById(R.id.tvOrderItems)
        val meta: TextView = view.findViewById(R.id.tvOrderMeta)
        val actions: View = view.findViewById(R.id.orderActions)

        val approve: MaterialButton =
            view.findViewById(R.id.btnApproveOrder)

        val decline: MaterialButton =
            view.findViewById(R.id.btnDeclineOrder)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_order, parent, false)

        return Holder(view)
    }

    override fun getItemCount(): Int = orders.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val order = orders[position]
        val context = holder.itemView.context
        val pending = order.needsApproval()
        val next = order.nextStatus()

        holder.status.text = if (pending) Order.STATUS_WAITING else order.status

        when (order.status) {
            Order.STATUS_DECLINED -> {
                holder.status.setBackgroundResource(R.drawable.bg_status_declined)
                holder.status.setTextColor(
                    ContextCompat.getColor(context, R.color.error)
                )
            }

            Order.STATUS_APPROVED, Order.DELIVERED -> {
                holder.status.setBackgroundResource(R.drawable.bg_status_approved)
                holder.status.setTextColor(
                    ContextCompat.getColor(context, R.color.success)
                )
            }

            else -> {
                holder.status.setBackgroundResource(R.drawable.bg_chip)
                holder.status.setTextColor(
                    ContextCompat.getColor(context, R.color.brand_dark)
                )
            }
        }

        holder.total.text = money(order.total)
        holder.items.text = order.itemSummary

        val countLabel = if (order.itemCount == 1) {
            "1 item"
        } else {
            "${order.itemCount} items"
        }

        val timeLabel = if (order.createdAt > 0L) {
            " · ${formatTime(order.createdAt)}"
        } else {
            ""
        }

        holder.meta.text = countLabel + timeLabel

        // RecyclerView reuses cards, so reset both buttons on every bind.
        holder.actions.visibility =
            if (pending || next != null) View.VISIBLE else View.GONE

        holder.approve.visibility =
            if (next != null) View.VISIBLE else View.GONE

        holder.decline.visibility =
            if (pending) View.VISIBLE else View.GONE

        holder.approve.text = when (next) {
            Order.PREPARING -> "Accept order"
            Order.READY -> "Mark ready for pickup"
            Order.DELIVERING -> "Start delivery"
            Order.DELIVERED -> "Mark delivered"
            else -> ""
        }

        holder.approve.setOnClickListener { onAdvance(order) }
        holder.decline.setOnClickListener { onDecline(order) }
    }

    private fun money(amount: Double): String =
        if (amount % 1.0 == 0.0) {
            "EGP ${amount.toLong()}"
        } else {
            String.format(Locale.US, "EGP %.2f", amount)
        }

    private fun formatTime(millis: Long): String {
        val formatter = SimpleDateFormat("dd MMM, HH:mm", Locale.US)
        return formatter.format(Date(millis))
    }
}
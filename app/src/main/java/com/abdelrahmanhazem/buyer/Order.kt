package com.abdelrahmanhazem.buyer

import com.google.firebase.database.DataSnapshot

/**
 * One order placed by the buyer, stored in Firebase at:
 *
 *   orders/{orderId}
 *     buyerId, sellerId, shopName, total, status, createdAt
 *     items/{productId}: name, price, qty, imageUrl
 *
 * Status flow (Milestone 4):
 * Waiting for Seller Approval → Preparing → Ready for Pickup / Delivering → Delivered
 * (or Declined)
 */
data class Order(
    val id: String,
    val sellerId: String,
    val shopName: String,
    val total: Double,
    val status: String,
    val createdAt: Long,
    val itemCount: Int,
    val itemSummary: String
) {
    companion object {
        const val NODE = "orders"
        const val STATUS_WAITING = "Waiting for Seller Approval"
        const val PREPARING = "Preparing"
        const val READY = "Ready for Pickup"
        const val DELIVERING = "Delivering"
        const val DELIVERED = "Delivered"
        const val STATUS_DECLINED = "Declined"
        /** Older Milestone 3 value kept for backwards compatibility. */
        const val STATUS_APPROVED = "Approved"

        fun from(snapshot: DataSnapshot): Order {
            fun text(key: String) = snapshot.child(key).getValue(String::class.java).orEmpty()
            fun number(key: String): Double = when (val v = snapshot.child(key).value) {
                is Number -> v.toDouble()
                is String -> v.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }

            val items = snapshot.child("items").children.toList()
            val qtyTotal = items.sumOf { (it.child("qty").value as? Number)?.toInt() ?: 0 }
            val names = items.mapNotNull {
                it.child("name").getValue(String::class.java)?.takeIf { n -> n.isNotBlank() }
            }
            val summary = when {
                names.isEmpty() -> "No items"
                names.size == 1 -> names[0]
                else -> "${names[0]} + ${names.size - 1} more"
            }

            return Order(
                id = snapshot.key.orEmpty(),
                sellerId = text("sellerId"),
                shopName = text("shopName"),
                total = number("total"),
                status = text("status"),
                createdAt = number("createdAt").toLong(),
                itemCount = qtyTotal,
                itemSummary = summary
            )
        }
    }
}

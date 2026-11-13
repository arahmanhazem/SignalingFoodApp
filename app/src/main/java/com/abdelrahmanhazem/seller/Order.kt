package com.abdelrahmanhazem.seller

import com.google.firebase.database.DataSnapshot

data class Order(
    val id: String = "",
    val buyerId: String = "",
    val sellerId: String = "",
    val shopName: String = "",
    val total: Double = 0.0,
    val status: String = "",
    val createdAt: Long = 0L,
    val itemCount: Int = 0,
    val itemSummary: String = "",
    val fulfillmentMethod: String = ""
) {
    fun needsApproval(): Boolean =
        status.isBlank() || status == STATUS_WAITING

    fun nextStatus(): String? {
        if (needsApproval()) return PREPARING

        return when (status) {
            PREPARING, STATUS_APPROVED -> when (fulfillmentMethod) {
                "Delivery" -> DELIVERING
                "Pickup", "" -> READY
                else -> null
            }

            READY, DELIVERING -> DELIVERED
            else -> null
        }
    }

    companion object {
        const val NODE = "orders"

        const val STATUS_WAITING = "Waiting for Seller Approval"
        const val PREPARING = "Preparing"
        const val READY = "Ready for Pickup"
        const val DELIVERING = "Delivering"
        const val DELIVERED = "Delivered"
        const val STATUS_DECLINED = "Declined"

        // Milestone 3 used this value when the seller accepted an order.
        const val STATUS_APPROVED = "Approved"

        fun fromSnapshot(snapshot: DataSnapshot): Order? {
            val id = snapshot.key ?: return null

            fun text(key: String): String =
                snapshot.child(key).value?.toString().orEmpty()

            fun number(key: String): Double =
                when (val value = snapshot.child(key).value) {
                    is Number -> value.toDouble()
                    is String -> value.toDoubleOrNull() ?: 0.0
                    else -> 0.0
                }

            val items = snapshot.child("items").children.toList()

            val qtyTotal = items.sumOf {
                (it.child("qty").value as? Number)?.toInt() ?: 0
            }

            val names = items.mapNotNull {
                it.child("name")
                    .getValue(String::class.java)
                    ?.takeIf { name -> name.isNotBlank() }
            }

            val summary = when {
                names.isEmpty() -> "No items"
                names.size == 1 -> names[0]
                else -> "${names[0]} + ${names.size - 1} more"
            }

            return Order(
                id = id,
                buyerId = text("buyerId"),
                sellerId = text("sellerId"),
                shopName = text("shopName"),
                total = number("total"),
                status = text("status"),
                createdAt = number("createdAt").toLong(),
                itemCount = qtyTotal,
                itemSummary = summary,
                fulfillmentMethod = text("fulfillmentMethod")
            )
        }
    }
}
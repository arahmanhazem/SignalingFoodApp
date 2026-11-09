package com.abdelrahmanhazem.buyer

import com.google.firebase.database.DataSnapshot

/**
 * One grocery item sold by a shop.
 * Saved by the seller app at products/<productId> (one flat list),
 * with the owner's uid in the "sellerId" field.
 */
data class Product(
    val id: String,
    val sellerId: String,
    val name: String,
    val price: Double,
    val stock: Int,        // how many are available ("quantity" in the database)
    val imageUrl: String
) {
    companion object {
        /** Builds a Product from products/<productId>. */
        fun from(snapshot: DataSnapshot): Product = Product(
            id = snapshot.key.orEmpty(),
            sellerId = snapshot.child("sellerId").getValue(String::class.java).orEmpty(),
            name = snapshot.child("name").getValue(String::class.java).orEmpty(),
            price = snapshot.child("price").number(),
            stock = snapshot.child("quantity").number().toInt(),
            imageUrl = snapshot.child("imageUrl").getValue(String::class.java).orEmpty()
        )

        /** Reads a number even if it was saved as text ("35") instead of a number (35). */
        private fun DataSnapshot.number(): Double = when (val v = value) {
            is Number -> v.toDouble()
            is String -> v.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }
}
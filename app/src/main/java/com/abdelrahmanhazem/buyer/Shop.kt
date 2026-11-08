package com.abdelrahmanhazem.buyer

import com.google.firebase.database.DataSnapshot

/**
 * One shop shown on the buyer's home screen.
 * A shop is a seller account: users/<sellerUid> with identity = "Seller".
 */
data class Shop(
    val sellerId: String,
    val shopName: String,
    val ownerName: String,
    val photoUrl: String
) {
    companion object {
        /** Builds a Shop from a seller's node in the database (users/<sellerUid>). */
        fun from(snapshot: DataSnapshot): Shop = Shop(
            sellerId = snapshot.key.orEmpty(),
            shopName = snapshot.child("shopName").getValue(String::class.java).orEmpty(),
            ownerName = snapshot.child("name").getValue(String::class.java).orEmpty(),
            photoUrl = snapshot.child("photoUrl").getValue(String::class.java).orEmpty()
        )
    }
}
package com.abdelrahmanhazem.seller

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.ServerValue

/**
 * One grocery item, stored in Firebase Realtime Database at:
 *
 *   products/{productId}
 *     sellerId   : String   (UID of the seller who owns it)
 *     shopName   : String   (copied from the seller profile, handy for the buyer app)
 *     name       : String
 *     price      : Double
 *     quantity   : Int      (items in stock)
 *     imageUrl   : String   (S3 URL, folder "products/{sellerId}/...")
 *     createdAt  : Long     (server timestamp)
 *     updatedAt  : Long     (server timestamp)
 *
 * The buyer app reads the same node:
 *   FirebaseDatabase.getInstance().getReference("products")
 *       .orderByChild("sellerId").equalTo(shopUid)
 * and downloads images with ProductImages.photos.download(sellerId, imageUrl).
 */
data class Product(
    val id: String = "",
    val sellerId: String = "",
    val shopName: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val quantity: Int = 0,
    val imageUrl: String = ""
) {
    /** Fields to write to Firebase (id is the node key, so it is not stored inside). */
    fun toMap(isNew: Boolean): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "sellerId" to sellerId,
            "shopName" to shopName,
            "name" to name,
            "price" to price,
            "quantity" to quantity,
            "imageUrl" to imageUrl,
            "updatedAt" to ServerValue.TIMESTAMP
        )
        if (isNew) map["createdAt"] = ServerValue.TIMESTAMP
        return map
    }

    companion object {
        const val NODE = "products"

        /** Reads a product safely, even if a number was saved as text or a field is missing. */
        fun fromSnapshot(snapshot: DataSnapshot): Product? {
            val id = snapshot.key ?: return null
            fun text(key: String) = snapshot.child(key).value?.toString().orEmpty()
            return Product(
                id = id,
                sellerId = text("sellerId"),
                shopName = text("shopName"),
                name = text("name"),
                price = text("price").toDoubleOrNull() ?: 0.0,
                quantity = text("quantity").toDoubleOrNull()?.toInt() ?: 0,
                imageUrl = text("imageUrl")
            )
        }
    }
}

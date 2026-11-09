package com.abdelrahmanhazem.buyer

import com.google.android.gms.tasks.Task
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue

/**
 * The buyer's cart, stored live in Firebase:
 *
 * carts/<buyerUid>
 *     sellerId, shopName                  ← a cart holds items from ONE shop
 *     items/<productId>
 *         productId, name, price, imageUrl, qty
 *
 * Both the Products tab and the Cart tab listen to this node,
 * so any change shows up on both instantly.
 */
object Cart {

    const val FIRST_ORDER_STATUS = "Waiting for Seller Approval"

    private val db: DatabaseReference get() = FirebaseDatabase.getInstance().reference

    /** The buyer's cart node: carts/<buyerUid> */
    fun ref(buyerId: String): DatabaseReference = db.child("carts").child(buyerId)

    data class Item(
        val productId: String,
        val name: String,
        val price: Double,
        val imageUrl: String,
        val qty: Int
    ) {
        val lineTotal: Double get() = price * qty
    }

    data class State(
        val sellerId: String,
        val shopName: String,
        val items: List<Item>
    ) {
        val count: Int get() = items.sumOf { it.qty }
        val total: Double get() = items.sumOf { it.lineTotal }
        fun qtyOf(productId: String): Int = items.firstOrNull { it.productId == productId }?.qty ?: 0
    }

    /** Turns the carts/<buyerUid> snapshot into a State (empty if there is no cart). */
    fun parse(snapshot: DataSnapshot): State = State(
        sellerId = snapshot.child("sellerId").getValue(String::class.java).orEmpty(),
        shopName = snapshot.child("shopName").getValue(String::class.java).orEmpty(),
        items = snapshot.child("items").children.map { item ->
            Item(
                productId = item.key.orEmpty(),
                name = item.child("name").getValue(String::class.java).orEmpty(),
                price = (item.child("price").value as? Number)?.toDouble() ?: 0.0,
                imageUrl = item.child("imageUrl").getValue(String::class.java).orEmpty(),
                qty = (item.child("qty").value as? Number)?.toInt() ?: 0
            )
        }.filter { it.qty > 0 }.sortedBy { it.name.lowercase() }
    )

    /** Adds one of [product] to the cart (creates the item the first time). */
    fun addOne(buyerId: String, shopName: String, product: Product): Task<Void> {
        val p = "items/${product.id}"
        return ref(buyerId).updateChildren(
            mapOf(
                "sellerId" to product.sellerId,
                "shopName" to shopName,
                "$p/productId" to product.id,
                "$p/name" to product.name,
                "$p/price" to product.price,
                "$p/imageUrl" to product.imageUrl,
                // increment = safe even if two taps arrive at the same time
                "$p/qty" to ServerValue.increment(1)
            )
        )
    }

    /** Removes one of a product; removes the item completely when it reaches 0. */
    fun removeOne(buyerId: String, productId: String, currentQty: Int): Task<Void> {
        val item = ref(buyerId).child("items").child(productId)
        return if (currentQty <= 1) item.removeValue()
        else item.child("qty").setValue(ServerValue.increment(-1))
    }

    /** Empties the cart. */
    fun clear(buyerId: String): Task<Void> = ref(buyerId).removeValue()

    /**
     * Places the order: writes orders/<orderId> AND deletes the cart in ONE
     * multi-path update, so either both happen or neither does.
     */
    fun placeOrder(buyerId: String, state: State): Task<Void> {
        val orderId = requireNotNull(db.child("orders").push().key)
        val order = mapOf(
            "id" to orderId,
            "buyerId" to buyerId,
            "sellerId" to state.sellerId,
            "shopName" to state.shopName,
            "items" to state.items.associate { item ->
                item.productId to mapOf(
                    "productId" to item.productId,
                    "name" to item.name,
                    "price" to item.price,
                    "qty" to item.qty,
                    "imageUrl" to item.imageUrl
                )
            },
            "total" to state.total,
            "status" to FIRST_ORDER_STATUS,
            "createdAt" to ServerValue.TIMESTAMP
        )
        return db.updateChildren(
            mapOf(
                "orders/$orderId" to order,
                "carts/$buyerId" to null
            )
        )
    }
}
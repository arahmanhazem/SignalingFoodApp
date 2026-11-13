package com.abdelrahmanhazem.buyer

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue

object Cart {

    const val FIRST_ORDER_STATUS = "Waiting for Seller Approval"

    private val db: DatabaseReference
        get() = FirebaseDatabase.getInstance().reference

    fun ref(buyerId: String): DatabaseReference =
        db.child("carts").child(buyerId)

    data class Item(
        val productId: String,
        val name: String,
        val price: Double,
        val imageUrl: String,
        val qty: Int
    ) {
        val lineTotal: Double
            get() = price * qty
    }

    data class State(
        val sellerId: String,
        val shopName: String,
        val items: List<Item>
    ) {
        val count: Int
            get() = items.sumOf { it.qty }

        val total: Double
            get() = items.sumOf { it.lineTotal }

        fun qtyOf(productId: String): Int =
            items.firstOrNull { it.productId == productId }?.qty ?: 0
    }

    // Read the buyer's cart from its Firebase snapshot.
    fun parse(snapshot: DataSnapshot): State {
        val items = snapshot.child("items").children.map { item ->
            Item(
                productId = item.key.orEmpty(),
                name = item.child("name")
                    .getValue(String::class.java).orEmpty(),
                price = (item.child("price").value as? Number)
                    ?.toDouble() ?: 0.0,
                imageUrl = item.child("imageUrl")
                    .getValue(String::class.java).orEmpty(),
                qty = (item.child("qty").value as? Number)
                    ?.toInt() ?: 0
            )
        }.filter { it.qty > 0 }
            .sortedBy { it.name.lowercase() }

        return State(
            sellerId = snapshot.child("sellerId")
                .getValue(String::class.java).orEmpty(),
            shopName = snapshot.child("shopName")
                .getValue(String::class.java).orEmpty(),
            items = items
        )
    }

    // Add one unit. The Products screen also checks the cart against stock.
    fun addOne(
        buyerId: String,
        shopName: String,
        product: Product
    ): Task<Void> {
        if (product.stock <= 0) {
            return Tasks.forException(
                IllegalStateException("This item is sold out.")
            )
        }

        val path = "items/${product.id}"

        return ref(buyerId).updateChildren(
            mapOf(
                "sellerId" to product.sellerId,
                "shopName" to shopName,
                "$path/productId" to product.id,
                "$path/name" to product.name,
                "$path/price" to product.price,
                "$path/imageUrl" to product.imageUrl,
                "$path/qty" to ServerValue.increment(1L)
            )
        )
    }

    fun removeOne(
        buyerId: String,
        productId: String,
        currentQty: Int
    ): Task<Void> {
        val itemRef = ref(buyerId).child("items").child(productId)

        return if (currentQty <= 1) {
            itemRef.removeValue()
        } else {
            itemRef.child("qty").setValue(ServerValue.increment(-1L))
        }
    }

    fun clear(buyerId: String): Task<Void> =
        ref(buyerId).removeValue()

    // Submit the order, deduct stock and clear the cart in one Firebase write.
    fun placeOrder(
        buyerId: String,
        state: State,
        method: String,
        address: String,
        deliveryZone: String,
        deliveryFee: Double
    ): Task<Void> {
        if (state.items.isEmpty()) {
            return Tasks.forException(
                IllegalStateException("Your cart is empty.")
            )
        }

        if (state.items.any { it.qty <= 0 }) {
            return Tasks.forException(
                IllegalStateException("Your cart contains an invalid quantity.")
            )
        }

        val orderId = requireNotNull(db.child("orders").push().key)

        val orderItems = state.items.associate { item ->
            item.productId to mapOf(
                "productId" to item.productId,
                "name" to item.name,
                "price" to item.price,
                "qty" to item.qty,
                "imageUrl" to item.imageUrl
            )
        }

        val order = mapOf(
            "id" to orderId,
            "buyerId" to buyerId,
            "sellerId" to state.sellerId,
            "shopName" to state.shopName,
            "items" to orderItems,
            "fulfillmentMethod" to method,
            "deliveryAddress" to address,
            "deliveryZone" to deliveryZone,
            "deliveryFee" to deliveryFee,
            "subtotal" to state.total,
            "total" to state.total + deliveryFee,
            "status" to FIRST_ORDER_STATUS,
            "createdAt" to ServerValue.TIMESTAMP
        )

        val updates = mutableMapOf<String, Any?>(
            "orders/$orderId" to order,
            "checkoutIds/$buyerId" to orderId,
            "carts/$buyerId" to null
        )

        for (item in state.items) {
            val stockPath = "products/${item.productId}/quantity"
            updates[stockPath] = ServerValue.increment(-item.qty.toLong())
        }

        return db.updateChildren(updates)
    }
}
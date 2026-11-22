package com.abdelrahmanhazem.buyer

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import java.util.concurrent.atomic.AtomicInteger

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

    /**
     * Place order with server-side stock checks:
     * each product uses a Firebase transaction that only decrements when stock >= qty.
     * If any product fails, already-decremented items are restored and the order is aborted.
     */
    fun placeOrder(
        buyerId: String,
        state: State,
        method: String,
        address: String,
        deliveryZone: String,
        deliveryFee: Double,
        creditsToUse: Double = 0.0,
        // DELIVERY APP BONUS: Points selected by the buyer for this purchase
        pointsToUse: Int = 0
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

        val orderTotal = state.total + deliveryFee
        val pointsDiscount = pointsToUse / 10.0
        val totalDiscount = creditsToUse + pointsDiscount

        if (creditsToUse < 0 || pointsToUse < 0 || totalDiscount > orderTotal) {
            return Tasks.forException(
                IllegalArgumentException("Invalid rewards amount.")
            )
        }

        // Server-side: reserve stock with transactions before writing the order.
        return decrementAllStock(state.items).continueWithTask { stockTask ->
            if (!stockTask.isSuccessful) {
                return@continueWithTask Tasks.forException(
                    stockTask.exception
                        ?: IllegalStateException("Could not reserve stock.")
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
                "totalBeforeCredits" to orderTotal,
                "creditsUsed" to creditsToUse,
                "deliveryPointsUsed" to pointsToUse,
                "pointsDiscount" to pointsDiscount,
                "total" to orderTotal - totalDiscount,
                "status" to FIRST_ORDER_STATUS,
                "createdAt" to ServerValue.TIMESTAMP
            )

            val updates = mutableMapOf<String, Any?>(
                "orders/$orderId" to order,
                "checkoutIds/$buyerId" to orderId,
                "carts/$buyerId" to null
            )

            if (creditsToUse > 0) {
                updates["users/$buyerId/purchaseCredits"] =
                    ServerValue.increment(-creditsToUse)
            }

            if (pointsToUse > 0) {
                updates["users/$buyerId/deliveryPoints"] =
                    ServerValue.increment(-pointsToUse.toLong())
            }

            db.updateChildren(updates).addOnFailureListener {
                // Order write failed after stock was reserved — put stock back.
                restoreStock(state.items)
            }
        }
    }

    /** Decrement every cart line with a stock transaction. Restore all if any line fails. */
    private fun decrementAllStock(items: List<Item>): Task<Void> {
        if (items.isEmpty()) return Tasks.forResult(null)

        val remaining = AtomicInteger(items.size)
        val tcs = TaskCompletionSource<Void>()
        val reserved = mutableListOf<Item>()
        val lock = Any()
        var failed = false

        fun fail(error: Exception) {
            synchronized(lock) {
                if (failed) return
                failed = true
                val copy = reserved.toList()
                restoreStock(copy)
                tcs.trySetException(error)
            }
        }

        fun succeedOne(item: Item) {
            synchronized(lock) {
                if (failed) {
                    // Another item already failed — undo this one too.
                    restoreStock(listOf(item))
                    return
                }
                reserved.add(item)
                if (remaining.decrementAndGet() == 0) {
                    tcs.trySetResult(null)
                }
            }
        }

        for (item in items) {
            decrementOne(item.productId, item.qty)
                .addOnSuccessListener { ok ->
                    if (ok) succeedOne(item)
                    else fail(
                        IllegalStateException(
                            "Not enough stock for \"${item.name}\"."
                        )
                    )
                }
                .addOnFailureListener { fail(it as? Exception ?: Exception(it)) }
        }

        return tcs.task
    }

    /** Server-side: only decrements if current quantity >= qty. */
    private fun decrementOne(productId: String, qty: Int): Task<Boolean> {
        val tcs = TaskCompletionSource<Boolean>()
        db.child("products").child(productId)
            .runTransaction(object : Transaction.Handler {
                override fun doTransaction(data: MutableData): Transaction.Result {
                    // First pass may have no local data — succeed unchanged so Firebase retries.
                    if (data.value == null) return Transaction.success(data)

                    val current = when (val v = data.child("quantity").value) {
                        is Number -> v.toInt()
                        is String -> v.toIntOrNull() ?: 0
                        else -> 0
                    }
                    if (current < qty) return Transaction.abort()

                    data.child("quantity").value = current - qty
                    data.child("updatedAt").value = ServerValue.TIMESTAMP
                    return Transaction.success(data)
                }

                override fun onComplete(
                    error: DatabaseError?,
                    committed: Boolean,
                    currentData: DataSnapshot?
                ) {
                    if (error != null) tcs.setException(error.toException())
                    else tcs.setResult(committed)
                }
            }, false)
        return tcs.task
    }

    /** Put reserved stock back (used when order write fails or a later item fails). */
    private fun restoreStock(items: List<Item>) {
        if (items.isEmpty()) return
        val updates = mutableMapOf<String, Any>()
        for (item in items) {
            updates["products/${item.productId}/quantity"] =
                ServerValue.increment(item.qty.toLong())
        }
        db.updateChildren(updates)
    }
}

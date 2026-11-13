package com.abdelrahmanhazem.buyer

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.abdelrahmanhazem.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener

class CartFragment : Fragment(R.layout.fragment_cart) {

    companion object {
        private const val ARG_SELLER_ID = "sellerId"

        fun newInstance(sellerId: String) = CartFragment().apply {
            arguments = bundleOf(ARG_SELLER_ID to sellerId)
        }
    }

    // Delivery fees in EGP. Add or change supported districts here.
    private val deliveryFees = mapOf(
        "Nasr City" to 15.0,
        "New Cairo" to 25.0
    )

    private var cartRef: DatabaseReference? = null
    private var cartListener: ValueEventListener? = null
    private var stockQuery: Query? = null
    private var stockListener: ValueEventListener? = null

    private var state = Cart.State("", "", emptyList())
    private var stocks: Map<String, Int> = emptyMap()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sellerId = requireArguments().getString(ARG_SELLER_ID).orEmpty()
        val buyerId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val content = view.findViewById<View>(R.id.cartContent)
        val empty = view.findViewById<View>(R.id.emptyCart)
        val progress = view.findViewById<ProgressBar>(R.id.progressCart)
        val tvShop = view.findViewById<TextView>(R.id.tvCartShop)
        val tvCount = view.findViewById<TextView>(R.id.tvCartCount)
        val tvTotal = view.findViewById<TextView>(R.id.tvCartTotal)
        val btnClear = view.findViewById<Button>(R.id.btnClearCart)
        val btnPlace = view.findViewById<Button>(R.id.btnPlaceOrder)
        val rvCart = view.findViewById<RecyclerView>(R.id.rvCart)

        val adapter = CartAdapter(
            owner = viewLifecycleOwner,
            onPlus = { item ->
                val stock = stocks[item.productId]

                if (stock == null || item.qty >= stock) {
                    showError("No more of this item in stock.")
                } else {
                    Cart.ref(buyerId)
                        .child("items")
                        .child(item.productId)
                        .child("qty")
                        .setValue(ServerValue.increment(1))
                        .addOnFailureListener {
                            showError("Could not update cart: ${it.message}")
                        }
                }
            },
            onMinus = { item ->
                Cart.removeOne(buyerId, item.productId, item.qty)
                    .addOnFailureListener {
                        showError("Could not update cart: ${it.message}")
                    }
            }
        )

        rvCart.layoutManager = LinearLayoutManager(requireContext())
        rvCart.adapter = adapter

        fun render() {
            progress.visibility = View.GONE

            val hasItems = state.items.isNotEmpty()
            content.visibility = if (hasItems) View.VISIBLE else View.GONE
            empty.visibility = if (hasItems) View.GONE else View.VISIBLE

            val shopLabel = state.shopName.ifBlank { "this shop" }
            tvShop.text = "From $shopLabel"

            tvCount.text = if (state.count == 1) {
                "1 item in basket"
            } else {
                "${state.count} items in basket"
            }

            tvTotal.text = egp(state.total)
            btnPlace.text = "Place order · ${egp(state.total)}"
            adapter.submit(state, stocks)
        }

        // Keep the cart display synchronized with Firebase.
        val ref = Cart.ref(buyerId)
        val cartUpdates = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                state = Cart.parse(snapshot)
                render()
            }

            override fun onCancelled(error: DatabaseError) {
                progress.visibility = View.GONE
                showError("Could not load cart: ${error.message}")
            }
        }

        cartRef = ref
        cartListener = cartUpdates
        ref.addValueEventListener(cartUpdates)

        // Read the available stock for this shop.
        val query = FirebaseDatabase.getInstance().reference
            .child("products")
            .orderByChild("sellerId")
            .equalTo(sellerId)

        val stockUpdates = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                stocks = snapshot.children
                    .map { Product.from(it) }
                    .associate { it.id to it.stock }

                render()
            }

            override fun onCancelled(error: DatabaseError) {
                stocks = emptyMap()
                render()
                showError("Could not load stock: ${error.message}")
            }
        }

        stockQuery = query
        stockListener = stockUpdates
        query.addValueEventListener(stockUpdates)

        btnClear.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Clear your cart?")
                .setMessage("All items from ${state.shopName} will be removed.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear") { _, _ ->
                    Cart.clear(buyerId)
                        .addOnFailureListener {
                            showError("Could not clear cart: ${it.message}")
                        }
                }
                .show()
        }

        btnPlace.setOnClickListener {
            val order = state
            if (order.items.isEmpty()) return@setOnClickListener

            // Both pickup and delivery show the final total before submission.
            fun confirmOrder(
                method: String,
                address: String,
                district: String,
                fee: Double
            ) {
                val summary = buildString {
                    append("Method: $method\n")

                    if (method == "Delivery") {
                        append("Address: $address\n")
                        append("District: $district\n")
                    }

                    append("Subtotal: ${egp(order.total)}\n")
                    append("Delivery fee: ${egp(fee)}\n")
                    append("Final total: ${egp(order.total + fee)}")
                }

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Confirm your order")
                    .setMessage(summary)
                    .setNegativeButton("Cancel", null)

                    .setPositiveButton("Place order") confirm@{ _, _ ->
                        if (state != order) {
                            showError("Your cart changed. Please review it again.")
                            return@confirm
                        }

                        for (item in order.items) {
                            val available = stocks[item.productId]

                            if (available == null) {
                                showError("Could not verify stock for ${item.name}. Reopen the cart.")
                                return@confirm
                            }

                            if (item.qty <= 0 || item.qty > available) {
                                showError(
                                    "${item.name}: you requested ${item.qty}, " +
                                            "but only $available are available."
                                )
                                return@confirm
                            }
                        }
                        btnPlace.text = "Placing order…"

                        Cart.placeOrder(buyerId, order, method, address, district, fee)
                            .addOnSuccessListener {
                                btnPlace.isEnabled = true
                                notifyBuyerOfOrder(order.shopName)

                                context?.let { ctx ->
                                    MaterialAlertDialogBuilder(ctx)
                                        .setTitle("Order placed!")
                                        .setMessage("${order.shopName} received your order.")
                                        .setPositiveButton("OK", null)
                                        .show()
                                }
                            }
                            .addOnFailureListener {
                                btnPlace.isEnabled = true
                                render()
                                showError("Could not place order: ${it.message}")
                            }
                    }
                    .show()
            }

            // The last part of the typed address determines the delivery fee.
            fun askForAddress() {
                val addressInput = EditText(requireContext())
                addressInput.hint = "12 Abbas El Akkad Street, Nasr City"

                val supportedDistricts = deliveryFees.keys.joinToString(", ")

                val dialog = MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Enter your delivery address")
                    .setMessage(
                        "Type your building number and street, then a comma " +
                                "and your district.\n\nWe deliver to: $supportedDistricts."
                    )
                    .setView(addressInput)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Calculate delivery fee", null)
                    .show()

                // Leave the dialog open when the buyer needs to correct the address.
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                    val address = addressInput.text.toString().trim()
                    val parts = address.replace('،', ',').split(',')
                    val street = parts.dropLast(1).joinToString(",").trim()
                    val district = parts.last().trim()

                    val deliveryArea = deliveryFees.entries.firstOrNull {
                        it.key.equals(district, ignoreCase = true)
                    }

                    when {
                        parts.size < 2 || district.isBlank() -> {
                            addressInput.error = "Add a comma and your district at the end"
                        }

                        street.none { it.isDigit() } || street.none { it.isLetter() } -> {
                            addressInput.error = "Enter your building number and street name"
                        }

                        deliveryArea == null -> {
                            addressInput.error = "Available districts: $supportedDistricts"
                        }

                        else -> {
                            dialog.dismiss()
                            confirmOrder(
                                method = "Delivery",
                                address = address,
                                district = deliveryArea.key,
                                fee = deliveryArea.value
                            )
                        }
                    }
                }
            }

            val methods = arrayOf("Pickup", "Delivery")
            var selectedMethod = 0

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("How would you like to receive your order?")
                .setSingleChoiceItems(methods, selectedMethod) { _, choice ->
                    selectedMethod = choice
                }
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue") { _, _ ->
                    if (selectedMethod == 0) {
                        confirmOrder("Pickup", "", "", 0.0)
                    } else {
                        askForAddress()
                    }
                }
                .show()
        }
    }

    // Ask for permission when a successful checkout first needs a notification.
    private var pendingOrderMessage: String? = null

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val message = pendingOrderMessage
        pendingOrderMessage = null

        if (granted && message != null) {
            postOrderNotification(message)
        }
    }

    private fun notifyBuyerOfOrder(shopName: String) {
        val ctx = context ?: return
        val message = "$shopName received your order. Waiting for seller approval."

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingOrderMessage = message
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            postOrderNotification(message)
        }
    }

    private fun postOrderNotification(message: String) {
        val ctx = context ?: return
        val channelId = "buyer_order_confirmations"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Buyer order confirmations",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = ctx.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notification = NotificationCompat.Builder(ctx, channelId)
            .setSmallIcon(R.drawable.ic_receipt)
            .setContentTitle("Order placed")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .build()

        // Keep the latest buyer confirmation separate from seller notifications.
        NotificationManagerCompat.from(ctx)
            .notify("buyer_order_confirmation", 1, notification)
    }
    private fun showError(message: String) {
        context?.let { ctx ->
            Toast.makeText(ctx, message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        cartListener?.let { cartRef?.removeEventListener(it) }
        stockListener?.let { stockQuery?.removeEventListener(it) }
        super.onDestroyView()
    }
}
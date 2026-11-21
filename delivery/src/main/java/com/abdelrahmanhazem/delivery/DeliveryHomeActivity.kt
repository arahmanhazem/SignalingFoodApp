
package com.abdelrahmanhazem.delivery

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class DeliveryHomeActivity : AppCompatActivity() {

    private lateinit var pointsText: TextView
    private val database = FirebaseDatabase.getInstance().reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_delivery_home)

        pointsText = findViewById(R.id.pointsText)

        val availableOrdersButton =
            findViewById<Button>(R.id.availableOrdersButton)

        val myDeliveriesButton =
            findViewById<Button>(R.id.myDeliveriesButton)

        // Open Available Orders screen
        availableOrdersButton.setOnClickListener {
            startActivity(
                Intent(this, AvailableOrdersActivity::class.java)
            )
        }

        // Open My Deliveries screen
        myDeliveriesButton.setOnClickListener {
            startActivity(
                Intent(this, MyDeliveriesActivity::class.java)
            )
        }

        loadRewardPoints()
    }

    private fun loadRewardPoints() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid

        if (userId == null) {
            pointsText.text = "0 points"
            return
        }

        database.child("users")
            .child(userId)
            .child("deliveryPoints")
            .addValueEventListener(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {
                    val points = snapshot.getValue(Int::class.java) ?: 0
                    pointsText.text = "$points points"
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(
                        this@DeliveryHomeActivity,
                        "Could not load reward points",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }
}

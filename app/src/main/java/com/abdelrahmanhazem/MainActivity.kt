package com.abdelrahmanhazem

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.database.FirebaseDatabase

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Firebase connection test
        val database = FirebaseDatabase.getInstance()
        val testRef = database.getReference("connectionTest")
        testRef.setValue("Firebase Connected!")

        // Buyer button
        val buyerButton = findViewById<Button>(R.id.btnBuyer)

        buyerButton.setOnClickListener {
            val intent = Intent(this, SignupActivity::class.java)
            intent.putExtra("identity", "Buyer")
            startActivity(intent)
        }

        // Seller button
        val sellerButton = findViewById<Button>(R.id.btnSeller)

        sellerButton.setOnClickListener {
            val intent = Intent(this, SignupActivity::class.java)
            intent.putExtra("identity", "Seller")
            startActivity(intent)
        }

        // Keep the existing edge-to-edge setup
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )
            insets
        }
    }
}
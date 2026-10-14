package com.abdelrahmanhazem

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.abdelrahmanhazem.seller.SellerActivity
import com.google.android.material.card.MaterialCardView

/**
 * Welcome screen: Buyer path uses the existing Sign Up / Log In screens.
 * Seller path opens the merged SellerActivity (auth + shop profile + S3 photos).
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<MaterialCardView>(R.id.cardBuyer).setOnClickListener {
            val intent = Intent(this, SignupActivity::class.java)
            intent.putExtra("identity", "Buyer")
            startActivity(intent)
        }

        findViewById<MaterialCardView>(R.id.cardSeller).setOnClickListener {
            startActivity(Intent(this, SellerActivity::class.java))
        }
    }
}

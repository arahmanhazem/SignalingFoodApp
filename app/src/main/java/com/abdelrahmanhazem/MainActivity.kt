package com.abdelrahmanhazem

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

/**
 * Welcome screen: the user chooses which path to enter.
 * The chosen role ("Buyer" or "Seller") is passed to the Sign Up screen
 * as the "identity" extra, and saved with the account.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<MaterialCardView>(R.id.cardBuyer).setOnClickListener {
            openSignup("Buyer")
        }

        findViewById<MaterialCardView>(R.id.cardSeller).setOnClickListener {
            openSignup("Seller")
        }
    }

    /** Opens the Sign Up screen for the chosen role. */
    private fun openSignup(identity: String) {
        val intent = Intent(this, SignupActivity::class.java)
        intent.putExtra("identity", identity)
        startActivity(intent)
    }
}
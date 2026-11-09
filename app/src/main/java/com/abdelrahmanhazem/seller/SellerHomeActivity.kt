package com.abdelrahmanhazem.seller

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.abdelrahmanhazem.MainActivity
import com.abdelrahmanhazem.R
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth

/**
 * Seller home (Milestone 3): bottom navigation with
 *  - Products: the seller's items in a grid (tap one to edit it)
 *  - Add:      form to add a new item with a photo uploaded to S3
 *  - Profile:  the existing shop profile screen from Milestone 2
 */
class SellerHomeActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Only signed-in sellers can be here (SellerActivity checks the role before opening this)
        if (FirebaseAuth.getInstance().currentUser == null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_seller_home)
        bottomNav = findViewById(R.id.sellerBottomNav)

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_seller_products -> {
                    showTab(SellerProductsFragment())
                    true
                }
                R.id.nav_seller_add -> {
                    showTab(ProductFormFragment.newInstance())
                    true
                }
                R.id.nav_seller_profile -> {
                    startActivity(
                        Intent(this, SellerActivity::class.java)
                            .putExtra(SellerActivity.EXTRA_SHOW_PROFILE, true)
                    )
                    false // keep the current tab highlighted when coming back
                }
                else -> false
            }
        }

        if (savedInstanceState == null) {
            bottomNav.selectedItemId = R.id.nav_seller_products
        }
    }

    /** Replaces the screen content with a tab, closing any open Edit screen first. */
    private fun showTab(fragment: Fragment) {
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.beginTransaction()
            .replace(R.id.sellerContainer, fragment)
            .commit()
    }

    /** Called by the Add screen after saving a new product. */
    fun showProducts() {
        bottomNav.selectedItemId = R.id.nav_seller_products
    }

    /** Opens the Edit screen for one product; Back returns to the grid. */
    fun openEditor(productId: String) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.sellerContainer, ProductFormFragment.newInstance(productId))
            .addToBackStack("edit")
            .commit()
    }
}

package com.abdelrahmanhazem

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.abdelrahmanhazem.seller.ProfilePhotos
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private val photos = ProfilePhotos("buyer-profiles")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<Button>(R.id.btnEditProfile).setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener {
            auth.signOut()
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        val userId = auth.currentUser?.uid
        if (userId == null) {
            finish()
            return
        }

        loadProfileData(userId)
    }

    override fun onResume() {
        super.onResume()
        val userId = auth.currentUser?.uid ?: return
        loadProfileData(userId)
    }

    private fun loadProfileData(userId: String) {
        val nameText = findViewById<TextView>(R.id.tvProfileName)
        val emailText = findViewById<TextView>(R.id.tvProfileEmail)
        val ageText = findViewById<TextView>(R.id.tvProfileAge)
        val identityText = findViewById<TextView>(R.id.tvProfileIdentity)
        val photoView = findViewById<ShapeableImageView>(R.id.ivProfilePhoto)
        val photoStatus = findViewById<TextView>(R.id.tvPhotoStatus)
        photoStatus.visibility = View.GONE

        database.reference
            .child("users")
            .child(userId)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    Toast.makeText(this, "Profile data not found.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                nameText.text = snapshot.child("name").value?.toString() ?: "Not available"
                emailText.text = snapshot.child("email").value?.toString() ?: "Not available"
                ageText.text = snapshot.child("age").value?.toString() ?: "Not available"
                identityText.text =
                    snapshot.child("identity").value?.toString() ?: "Not available"

                val photoUrl = snapshot.child("photoUrl").value?.toString().orEmpty()
                if (photoUrl.isBlank()) {
                    photoView.setImageDrawable(null)
                    return@addOnSuccessListener
                }

                lifecycleScope.launch {
                    try {
                        val bitmap = withContext(Dispatchers.IO) {
                            photos.download(userId, photoUrl)
                        }
                        photoView.setImageBitmap(bitmap)
                    } catch (_: Exception) {
                        photoView.setImageDrawable(null)
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load profile.", Toast.LENGTH_SHORT).show()
            }
    }
}

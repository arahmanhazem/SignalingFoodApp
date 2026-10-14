package com.abdelrahmanhazem

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.abdelrahmanhazem.seller.ProfilePhotos
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class EditProfileActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var photoView: ShapeableImageView
    private lateinit var photoStatus: TextView

    private val photos = ProfilePhotos("buyer-profiles")
    private var selectedBytes: ByteArray? = null
    private var existingPhotoUrl: String = ""
    private var pendingPhotoUrl: String? = null
    private var busy = false

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null || busy) return@registerForActivityResult
        busy = true
        photoStatus.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    photos.prepare(applicationContext, uri)
                }
                selectedBytes = bytes
                pendingPhotoUrl = null
                photoView.setImageBitmap(
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                )
            } catch (e: Exception) {
                photoStatus.visibility = View.VISIBLE
                photoStatus.text = e.localizedMessage ?: "Could not open this photo."
            } finally {
                busy = false
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        photoView = findViewById(R.id.ivEditProfilePhoto)
        photoStatus = findViewById(R.id.tvEditPhotoStatus)
        photoStatus.visibility = View.GONE
        val nameInput = findViewById<TextInputEditText>(R.id.etEditName)
        val ageInput = findViewById<TextInputEditText>(R.id.etEditAge)
        val passwordInput = findViewById<TextInputEditText>(R.id.etEditPassword)
        val changePhotoButton = findViewById<MaterialButton>(R.id.btnChangePhoto)
        val saveButton = findViewById<Button>(R.id.btnSaveProfile)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        val currentUser = auth.currentUser
        if (currentUser == null) {
            finish()
            return
        }

        val userId = currentUser.uid

        database.reference.child("users").child(userId).get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) return@addOnSuccessListener
                nameInput.setText(snapshot.child("name").value?.toString() ?: "")
                ageInput.setText(snapshot.child("age").value?.toString() ?: "")
                existingPhotoUrl = snapshot.child("photoUrl").value?.toString().orEmpty()
                if (existingPhotoUrl.isNotBlank()) {
                    lifecycleScope.launch {
                        try {
                            val bitmap = withContext(Dispatchers.IO) {
                                photos.download(userId, existingPhotoUrl)
                            }
                            photoView.setImageBitmap(bitmap)
                        } catch (_: Exception) {
                            // Keep placeholder
                        }
                    }
                }
            }

        changePhotoButton.setOnClickListener {
            pickImage.launch("image/*")
        }

        saveButton.setOnClickListener {
            if (busy) return@setOnClickListener

            val newName = nameInput.text?.toString()?.trim().orEmpty()
            val newAge = ageInput.text?.toString()?.trim().orEmpty()
            val newPassword = passwordInput.text?.toString().orEmpty()

            if (newName.isEmpty() || newAge.isEmpty()) {
                Toast.makeText(this, "Please fill in your name and age", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            busy = true
            saveButton.isEnabled = false
            photoStatus.visibility = View.GONE
            lifecycleScope.launch {
                try {
                    val updates = mutableMapOf<String, Any>(
                        "name" to newName,
                        "age" to newAge
                    )

                    val bytes = selectedBytes
                    if (bytes != null) {
                        if (pendingPhotoUrl == null) {
                            pendingPhotoUrl = withContext(Dispatchers.IO) {
                                photos.upload(userId, bytes)
                            }
                        }
                        updates["photoUrl"] = requireNotNull(pendingPhotoUrl)
                    } else if (existingPhotoUrl.isNotBlank()) {
                        updates["photoUrl"] = existingPhotoUrl
                    }

                    database.reference.child("users").child(userId)
                        .updateChildren(updates).await()

                    if (pendingPhotoUrl != null) {
                        existingPhotoUrl = pendingPhotoUrl.orEmpty()
                    }
                    selectedBytes = null
                    pendingPhotoUrl = null

                    if (newPassword.isNotEmpty()) {
                        try {
                            currentUser.updatePassword(newPassword).await()
                        } catch (_: Exception) {
                            // Profile still saved
                        }
                    }
                    finish()
                } catch (e: Exception) {
                    photoStatus.visibility = View.VISIBLE
                    photoStatus.text = e.localizedMessage ?: "Failed to update profile."
                } finally {
                    busy = false
                    saveButton.isEnabled = true
                }
            }
        }
    }
}

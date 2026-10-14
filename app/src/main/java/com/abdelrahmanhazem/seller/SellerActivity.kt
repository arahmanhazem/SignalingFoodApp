package com.abdelrahmanhazem.seller

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.abdelrahmanhazem.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Seller path: sign in / sign up, then shop profile with photo upload. */
class SellerActivity : AppCompatActivity() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val users by lazy { FirebaseDatabase.getInstance().getReference("users") }
    private val photos = ProfilePhotos("seller-profiles")

    private var busy = false
    private var isSignup = false
    private var selectedPhoto: ByteArray? = null
    private var pendingPhotoUrl: String? = null
    private var existingPhotoUrl: String = ""

    private var photoView: ShapeableImageView? = null
    private var statusView: TextView? = null
    private val controls = mutableListOf<View>()

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null || busy) return@registerForActivityResult
        runTask {
            val bytes = withContext(Dispatchers.IO) { photos.prepare(applicationContext, uri) }
            selectedPhoto = bytes
            pendingPhotoUrl = null
            photoView?.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
            clearStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (busy) return
                if (photoView != null) {
                    finish()
                } else if (isSignup) {
                    showAuth(signup = false)
                } else {
                    finish()
                }
            }
        })
        showAuth(signup = false)
        if (auth.currentUser != null) runTask { openVerifiedProfile() }
    }

    private fun clearStatus() {
        statusView?.visibility = View.GONE
        statusView?.text = ""
    }

    private fun showError(message: String) {
        statusView?.visibility = View.VISIBLE
        statusView?.text = message
    }

    private fun runTask(work: suspend () -> Unit) {
        if (busy) return
        busy = true
        controls.forEach { it.isEnabled = false }
        clearStatus()
        lifecycleScope.launch {
            try {
                work()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                showError(e.localizedMessage ?: "Something went wrong. Please try again.")
            } finally {
                busy = false
                controls.forEach { it.isEnabled = true }
            }
        }
    }

    private fun showAuth(signup: Boolean) {
        isSignup = signup
        selectedPhoto = null
        pendingPhotoUrl = null
        existingPhotoUrl = ""
        photoView = null
        controls.clear()

        setContentView(R.layout.activity_seller_auth)

        val title = findViewById<TextView>(R.id.tvAuthTitle)
        val subtitle = findViewById<TextView>(R.id.tvAuthSubtitle)
        statusView = findViewById(R.id.tvAuthStatus)
        val signupFields = findViewById<LinearLayout>(R.id.signupFields)
        val etOwner = findViewById<TextInputEditText>(R.id.etOwnerName)
        val etShop = findViewById<TextInputEditText>(R.id.etShopName)
        val etPhone = findViewById<TextInputEditText>(R.id.etPhone)
        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val btnPrimary = findViewById<Button>(R.id.btnPrimary)
        val btnToggle = findViewById<MaterialButton>(R.id.btnToggleAuth)
        val btnReset = findViewById<MaterialButton>(R.id.btnResetPassword)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            if (!busy) finish()
        }

        title.text = if (signup) "Create seller account" else "Seller sign in"
        subtitle.text = "Manage your shop profile."
        signupFields.visibility = if (signup) View.VISIBLE else View.GONE
        btnPrimary.text = if (signup) "Create seller account" else "Sign in"
        btnToggle.text =
            if (signup) "Already registered? Sign in" else "New seller? Create account"
        btnReset.visibility = if (signup) View.GONE else View.VISIBLE
        clearStatus()

        controls += listOf(etOwner, etShop, etPhone, etEmail, etPassword, btnPrimary, btnToggle, btnReset)

        btnToggle.setOnClickListener { if (!busy) showAuth(!signup) }

        btnReset.setOnClickListener {
            val address = etEmail.text?.toString()?.trim().orEmpty()
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(address).matches()) {
                showError("Enter your email first.")
            } else runTask {
                auth.sendPasswordResetEmail(address).await()
                clearStatus()
            }
        }

        btnPrimary.setOnClickListener {
            val emailValue = etEmail.text?.toString()?.trim().orEmpty()
            val passwordValue = etPassword.text?.toString().orEmpty()
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(emailValue).matches() ||
                passwordValue.isEmpty()
            ) {
                showError("Enter a valid email and password.")
                return@setOnClickListener
            }
            if (signup) {
                val name = etOwner.text?.toString()?.trim().orEmpty()
                val shop = etShop.text?.toString()?.trim().orEmpty()
                val phone = etPhone.text?.toString()?.trim().orEmpty()
                if (name.isEmpty() || shop.isEmpty() || phone.isEmpty() || passwordValue.length < 6) {
                    showError("Fill in all fields. Password must be at least 6 characters.")
                    return@setOnClickListener
                }
            }

            runTask {
                if (signup) {
                    val user = requireNotNull(
                        auth.createUserWithEmailAndPassword(emailValue, passwordValue).await().user
                    )
                    try {
                        users.child(user.uid).setValue(
                            mapOf(
                                "name" to etOwner.text.toString().trim(),
                                "shopName" to etShop.text.toString().trim(),
                                "phone" to etPhone.text.toString().trim(),
                                "email" to emailValue,
                                "identity" to "Seller",
                                "address" to "",
                                "photoUrl" to ""
                            )
                        ).await()
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        val removed = runCatching { user.delete().await() }.isSuccess
                        auth.signOut()
                        error(
                            if (removed) {
                                "Could not create profile. Please try again."
                            } else {
                                "Could not create profile. Please try again later."
                            }
                        )
                    }
                } else {
                    auth.signInWithEmailAndPassword(emailValue, passwordValue).await()
                }
                openVerifiedProfile()
            }
        }
    }

    private suspend fun verifiedProfile(): DataSnapshot {
        val uid = auth.currentUser?.uid ?: error("Please sign in again.")
        val snapshot = users.child(uid).get().await()
        if (snapshot.child("identity").getValue(String::class.java) != "Seller") {
            auth.signOut()
            showAuth(signup = false)
            error("This account is not a seller account.")
        }
        return snapshot
    }

    private suspend fun openVerifiedProfile() {
        val data = verifiedProfile()
        selectedPhoto = null
        pendingPhotoUrl = null
        controls.clear()

        setContentView(R.layout.activity_seller_profile)

        photoView = findViewById(R.id.ivSellerPhoto)
        statusView = findViewById(R.id.tvProfileStatus)
        clearStatus()
        val tvSignedIn = findViewById<TextView>(R.id.tvSignedInAs)
        val etOwner = findViewById<TextInputEditText>(R.id.etOwnerName)
        val etShop = findViewById<TextInputEditText>(R.id.etShopName)
        val etPhone = findViewById<TextInputEditText>(R.id.etPhone)
        val etAddress = findViewById<TextInputEditText>(R.id.etAddress)
        val btnChange = findViewById<MaterialButton>(R.id.btnChangePhoto)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnReload = findViewById<MaterialButton>(R.id.btnReload)
        val btnLogout = findViewById<MaterialButton>(R.id.btnLogout)

        fun value(key: String) = data.child(key).value?.toString().orEmpty()

        tvSignedIn.text = auth.currentUser?.email.orEmpty()
        etOwner.setText(value("name"))
        etShop.setText(value("shopName"))
        etPhone.setText(value("phone"))
        etAddress.setText(value("address"))
        existingPhotoUrl = value("photoUrl")

        controls += listOf(
            etOwner, etShop, etPhone, etAddress, btnChange, btnSave, btnReload, btnLogout
        )

        btnChange.setOnClickListener { if (!busy) pickPhoto.launch("image/*") }
        btnReload.setOnClickListener { runTask { openVerifiedProfile() } }
        btnLogout.setOnClickListener {
            if (busy) return@setOnClickListener
            auth.signOut()
            showAuth(signup = false)
        }

        btnSave.setOnClickListener {
            val name = etOwner.text?.toString()?.trim().orEmpty()
            val shop = etShop.text?.toString()?.trim().orEmpty()
            val phone = etPhone.text?.toString()?.trim().orEmpty()
            if (name.isEmpty() || shop.isEmpty() || phone.isEmpty()) {
                showError("Owner name, shop name and phone are required.")
                return@setOnClickListener
            }
            runTask {
                verifiedProfile()
                val uid = requireNotNull(auth.currentUser).uid
                val updates = mutableMapOf<String, Any>(
                    "name" to name,
                    "shopName" to shop,
                    "phone" to phone,
                    "address" to etAddress.text?.toString()?.trim().orEmpty(),
                    "updatedAt" to ServerValue.TIMESTAMP
                )
                val bytes = selectedPhoto
                if (bytes != null) {
                    if (pendingPhotoUrl == null) {
                        pendingPhotoUrl = withContext(Dispatchers.IO) { photos.upload(uid, bytes) }
                    }
                    updates["photoUrl"] = requireNotNull(pendingPhotoUrl)
                    updates["photoUpdatedAt"] = ServerValue.TIMESTAMP
                }
                users.child(uid).updateChildren(updates).await()
                if (pendingPhotoUrl != null) {
                    existingPhotoUrl = pendingPhotoUrl.orEmpty()
                }
                selectedPhoto = null
                pendingPhotoUrl = null
                clearStatus()
            }
        }

        if (existingPhotoUrl.isNotBlank()) {
            try {
                val uid = requireNotNull(auth.currentUser).uid
                val bitmap = withContext(Dispatchers.IO) {
                    photos.download(uid, existingPhotoUrl)
                }
                photoView?.setImageBitmap(bitmap)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // Keep placeholder if download fails
            }
        }
    }
}

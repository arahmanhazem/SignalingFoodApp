package com.abdelrahmanhazem.seller

import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Seller-only entry point, using the original project's users/{uid}/identity schema. */
class SellerActivity : AppCompatActivity() {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val users by lazy { FirebaseDatabase.getInstance().getReference("users") }
    private val photos = ProfilePhotos()
    private lateinit var panel: LinearLayout
    private lateinit var status: TextView
    private val controls = mutableListOf<View>()
    private var busy = false
    private var screen = "login"
    private var selectedPhoto: ByteArray? = null
    // Retain an uploaded URL on database failure so retry does not upload a duplicate.
    private var pendingPhotoUrl: String? = null

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && screen == "profile" && !busy) runTask {
            val bytes = withContext(Dispatchers.IO) { photos.prepare(applicationContext, uri) }
            selectedPhoto = bytes
            pendingPhotoUrl = null
            photoView?.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
            status.text = "Photo ready (${bytes.size / 1000} KB). Tap Save changes to upload."
        }
    }
    private var photoView: ImageView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (busy) return
                if (screen == "signup") showAuth(false) else finish()
            }
        })
        showAuth(false)
        if (auth.currentUser != null) runTask { openVerifiedProfile() }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun page(title: String, subtitle: String) {
        controls.clear()
        photoView = null
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(246, 248, 245)) }
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(32))
        }
        scroll.addView(panel)
        setContentView(scroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(scroll)
        label("FOOD SELLER", 14)
        label(title, 30)
        label(subtitle, 16)
        status = label("", 14)
        status.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }

    private fun label(text: String, size: Int): TextView = TextView(this).also {
        it.text = text
        it.textSize = size.toFloat()
        it.setTextColor(Color.rgb(28, 54, 44))
        it.setPadding(0, dp(8), 0, dp(8))
        panel.addView(it)
    }

    private fun field(title: String, value: String = "", type: Int = InputType.TYPE_CLASS_TEXT): EditText {
        val caption = label(title, 14)
        return EditText(this).also {
            it.id = View.generateViewId()
            caption.labelFor = it.id
            it.inputType = type
            it.setSingleLine(true)
            it.setText(value)
            panel.addView(it, LinearLayout.LayoutParams(-1, dp(56)))
            controls.add(it)
        }
    }

    private fun button(title: String, action: () -> Unit) = Button(this).also {
        it.text = title
        it.isAllCaps = false
        panel.addView(it, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(10) })
        controls.add(it)
        it.setOnClickListener { if (!busy) action() }
    }

    private fun runTask(work: suspend () -> Unit) {
        if (busy) return
        busy = true
        controls.forEach { it.isEnabled = false }
        status.text = "Please wait…"
        lifecycleScope.launch {
            try { work() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) { status.text = e.localizedMessage ?: "Operation failed. Please try again." }
            finally {
                busy = false
                controls.forEach { it.isEnabled = true }
            }
        }
    }

    private fun showAuth(signup: Boolean) {
        screen = if (signup) "signup" else "login"
        selectedPhoto = null
        pendingPhotoUrl = null
        page(if (signup) "Create seller account" else "Seller sign in",
            "Manage your shop profile. Buyer accounts cannot enter this app.")
        val name = if (signup) field("Owner name") else null
        val shop = if (signup) field("Shop name") else null
        val phone = if (signup) field("Phone", type = InputType.TYPE_CLASS_PHONE) else null
        val email = field("Email", type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val password = field("Password", type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        button(if (signup) "Create seller account" else "Sign in") {
            val emailValue = email.text.toString().trim()
            val passwordValue = password.text.toString()
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(emailValue).matches() || passwordValue.isEmpty()) {
                status.text = "Enter a valid email and password."
            } else if (signup && (name!!.text.isBlank() || shop!!.text.isBlank() || phone!!.text.isBlank() || passwordValue.length < 6)) {
                status.text = "Enter owner name, shop name, phone, and a password of at least 6 characters."
            } else runTask {
                if (signup) {
                    val user = requireNotNull(auth.createUserWithEmailAndPassword(emailValue, passwordValue).await().user)
                    try {
                        users.child(user.uid).setValue(mapOf(
                            "name" to name!!.text.toString().trim(),
                            "shopName" to shop!!.text.toString().trim(),
                            "phone" to phone!!.text.toString().trim(),
                            "email" to emailValue, "identity" to "Seller", "address" to ""
                        )).await()
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        val removed = runCatching { user.delete().await() }.isSuccess
                        auth.signOut()
                        error(if (removed) "Profile creation failed; account rolled back. Check Firebase rules and retry."
                            else "Profile creation failed. Ask your Firebase administrator to repair/remove the incomplete account before retrying.")
                    }
                } else auth.signInWithEmailAndPassword(emailValue, passwordValue).await()
                openVerifiedProfile()
            }
        }
        button(if (signup) "Already registered? Sign in" else "New seller? Create account") { showAuth(!signup) }
        if (!signup) button("Send password reset email") {
            val address = email.text.toString().trim()
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(address).matches()) status.text = "Enter your email first."
            else runTask {
                auth.sendPasswordResetEmail(address).await()
                status.text = "Password reset requested. Check your email."
            }
        }
    }

    private suspend fun verifiedProfile(): DataSnapshot {
        val uid = auth.currentUser?.uid ?: error("Please sign in again.")
        val snapshot = users.child(uid).get().await()
        if (snapshot.child("identity").getValue(String::class.java) != "Seller") {
            auth.signOut()
            showAuth(false)
            error("This account is not registered as a Seller. Use the Buyer app or create a seller account.")
        }
        return snapshot
    }

    private suspend fun openVerifiedProfile() {
        val data = verifiedProfile()
        screen = "profile"
        selectedPhoto = null
        pendingPhotoUrl = null
        page("Your shop profile", "Signed in as ${auth.currentUser?.email.orEmpty()}")
        fun value(key: String) = data.child(key).value?.toString().orEmpty()
        val picture = ImageView(this).apply {
            contentDescription = "Seller profile photo"
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(android.R.drawable.ic_menu_camera)
            setBackgroundColor(Color.rgb(226, 235, 227))
        }
        photoView = picture
        panel.addView(picture, LinearLayout.LayoutParams(-1, dp(200)))
        button("Choose / change photo") { pickPhoto.launch("image/*") }
        label("Photos are compressed to less than 0.5 MB before upload.", 13)
        val name = field("Owner name", value("name"))
        val shop = field("Shop name", value("shopName"))
        val phone = field("Phone", value("phone"), InputType.TYPE_CLASS_PHONE)
        val address = field("Shop address", value("address"))
        // Keep Milestone 1's optional age field if it exists; updates preserve all other keys.
        val age = if (data.hasChild("age")) field("Age", value("age"), InputType.TYPE_CLASS_NUMBER) else null
        button("Save changes") {
            if (name.text.isBlank() || shop.text.isBlank() || phone.text.isBlank()) {
                status.text = "Owner name, shop name and phone are required."
            } else runTask {
                verifiedProfile()
                val uid = requireNotNull(auth.currentUser).uid
                val updates = mutableMapOf<String, Any>(
                    "name" to name.text.toString().trim(), "shopName" to shop.text.toString().trim(),
                    "phone" to phone.text.toString().trim(), "address" to address.text.toString().trim(),
                    "updatedAt" to ServerValue.TIMESTAMP
                )
                if (age != null) updates["age"] = age.text.toString().trim()
                val bytes = selectedPhoto
                if (bytes != null) {
                    if (pendingPhotoUrl == null) {
                        pendingPhotoUrl = withContext(Dispatchers.IO) { photos.upload(uid, bytes) }
                    }
                    updates["photoUrl"] = requireNotNull(pendingPhotoUrl)
                    updates["photoUpdatedAt"] = ServerValue.TIMESTAMP
                }
                // URL is only persisted after PutObject succeeds. Other M1 fields survive.
                users.child(uid).updateChildren(updates).await()
                selectedPhoto = null
                pendingPhotoUrl = null
                status.text = "Profile saved."
            }
        }
        button("Reload saved profile") { runTask { openVerifiedProfile() } }
        button("Send password reset email") {
            runTask {
                auth.sendPasswordResetEmail(requireNotNull(auth.currentUser?.email)).await()
                status.text = "Password reset requested. Check your email."
            }
        }
        button("Log out") { auth.signOut(); showAuth(false) }
        controls.forEach { it.isEnabled = false }
        val url = value("photoUrl")
        if (url.isNotBlank()) {
            try {
                val uid = requireNotNull(auth.currentUser).uid
                val bitmap = withContext(Dispatchers.IO) { photos.download(uid, url) }
                picture.setImageBitmap(bitmap)
                status.text = "Saved profile and S3 photo loaded."
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                status.text = "Profile loaded; photo download failed: ${e.localizedMessage}. Use Reload saved profile to retry."
            }
        } else status.text = "Profile loaded. Choose a photo to get started."
    }
}

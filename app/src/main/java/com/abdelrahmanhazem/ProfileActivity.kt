package com.abdelrahmanhazem

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.abdelrahmanhazem.seller.ProfilePhotos
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class ProfileActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, BuyerProfileFragment())
                .commit()
        }
    }
}

/** Shows the buyer's profile and refreshes it after editing. */
class BuyerProfileFragment : Fragment(R.layout.activity_profile) {

    private val photos = ProfilePhotos("buyer-profiles")
    private var loadJob: Job? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        view.findViewById<Button>(R.id.btnEditProfile).setOnClickListener {
            startActivity(Intent(requireContext(), EditProfileActivity::class.java))
        }

        view.findViewById<Button>(R.id.btnLogout).setOnClickListener {
            FirebaseAuth.getInstance().signOut()

            val intent = Intent(requireContext(), MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK

            startActivity(intent)
            requireActivity().finish()
        }
    }

    override fun onResume() {
        super.onResume()

        val page = view ?: return
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            requireActivity().finish()
            return
        }

        val photo = page.findViewById<ShapeableImageView>(R.id.ivProfilePhoto)
        page.findViewById<TextView>(R.id.tvPhotoStatus).visibility = View.GONE

        loadJob?.cancel()
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val profile = FirebaseDatabase.getInstance().reference
                    .child("users").child(uid).get().await()

                if (!profile.exists()) {
                    Toast.makeText(page.context, "Profile data not found.", Toast.LENGTH_SHORT)
                        .show()
                    return@launch
                }

                fun display(viewId: Int, field: String) {
                    page.findViewById<TextView>(viewId).text =
                        profile.child(field).value?.toString() ?: "Not available"
                }

                display(R.id.tvProfileName, "name")
                display(R.id.tvProfileEmail, "email")
                display(R.id.tvProfileAge, "age")
                display(R.id.tvProfileIdentity, "identity")

                val url = profile.child("photoUrl").value?.toString().orEmpty()
                photo.setImageDrawable(null)

                if (url.isNotBlank()) {
                    val bitmap = withContext(Dispatchers.IO) {
                        photos.download(uid, url)
                    }
                    photo.setImageBitmap(bitmap)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(page.context, "Could not load profile.", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }
}
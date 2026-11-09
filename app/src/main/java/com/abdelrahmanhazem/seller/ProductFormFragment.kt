package com.abdelrahmanhazem.seller

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.abdelrahmanhazem.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Add tab (no product id) or Edit screen (with product id).
 * The seller sets name, price, quantity and a photo from the camera or gallery.
 * The photo is compressed below 0.5 MB and uploaded to S3; its URL is saved in Firebase.
 */
class ProductFormFragment : Fragment(R.layout.fragment_product_form) {

    companion object {
        private const val ARG_ID = "productId"

        fun newInstance(productId: String? = null) = ProductFormFragment().apply {
            arguments = bundleOf(ARG_ID to productId)
        }
    }

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseDatabase.getInstance() }

    private val productId: String? get() = arguments?.getString(ARG_ID)
    private val isEdit get() = productId != null

    private var existing: Product? = null
    private var selectedPhoto: ByteArray? = null
    private var cameraUri: Uri? = null
    private var busy = false

    private lateinit var ivPhoto: ImageView
    private lateinit var etName: TextInputEditText
    private lateinit var etPrice: TextInputEditText
    private lateinit var etQuantity: TextInputEditText
    private lateinit var tvStatus: TextView
    private val controls = mutableListOf<View>()

    private val pickFromGallery =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) usePhoto(uri)
        }

    private val takePicture =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
            val uri = cameraUri
            if (saved && uri != null) usePhoto(uri)
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ivPhoto = view.findViewById(R.id.ivProductPhoto)
        etName = view.findViewById(R.id.etProductName)
        etPrice = view.findViewById(R.id.etProductPrice)
        etQuantity = view.findViewById(R.id.etProductQuantity)
        tvStatus = view.findViewById(R.id.tvFormStatus)
        val tvTitle = view.findViewById<TextView>(R.id.tvFormTitle)
        val btnGallery = view.findViewById<MaterialButton>(R.id.btnGallery)
        val btnCamera = view.findViewById<MaterialButton>(R.id.btnCamera)
        val btnSave = view.findViewById<Button>(R.id.btnSaveProduct)
        val btnDelete = view.findViewById<MaterialButton>(R.id.btnDeleteProduct)

        controls.clear()
        controls += listOf(etName, etPrice, etQuantity, btnGallery, btnCamera, btnSave, btnDelete)

        tvTitle.text = if (isEdit) "Edit product" else "Add a new product"
        btnSave.text = if (isEdit) "Save changes" else "Add product"
        btnDelete.visibility = if (isEdit) View.VISIBLE else View.GONE

        btnGallery.setOnClickListener { if (!busy) pickFromGallery.launch("image/*") }
        btnCamera.setOnClickListener { if (!busy) openCamera() }
        btnSave.setOnClickListener { save() }
        btnDelete.setOnClickListener { confirmDelete() }

        if (isEdit) runTask { loadExisting() }
    }

    // ---------- Helpers ----------

    private fun showError(message: String) {
        tvStatus.visibility = View.VISIBLE
        tvStatus.text = message
    }

    private fun clearStatus() {
        tvStatus.visibility = View.GONE
        tvStatus.text = ""
    }

    /** Runs work in the background, disables buttons meanwhile and shows any error. */
    private fun runTask(work: suspend () -> Unit) {
        if (busy) return
        busy = true
        controls.forEach { it.isEnabled = false }
        clearStatus()
        viewLifecycleOwner.lifecycleScope.launch {
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

    private fun openCamera() {
        val dir = File(requireContext().cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "product_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            requireContext(), "${requireContext().packageName}.fileprovider", file
        )
        cameraUri = uri
        takePicture.launch(uri)
    }

    /** Compresses the chosen photo below 0.5 MB and shows a preview. Uploads only on Save. */
    private fun usePhoto(uri: Uri) = runTask {
        val bytes = withContext(Dispatchers.IO) {
            ProductImages.photos.prepare(requireContext().applicationContext, uri)
        }
        selectedPhoto = bytes
        ivPhoto.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
    }

    // ---------- Load / save / delete ----------

    private suspend fun loadExisting() {
        val id = requireNotNull(productId)
        val snapshot = db.getReference(Product.NODE).child(id).get().await()
        val product = Product.fromSnapshot(snapshot)
        if (!snapshot.exists() || product == null || product.sellerId != auth.currentUser?.uid) {
            error("This product no longer exists.")
        }
        existing = product
        etName.setText(product.name)
        etPrice.setText(if (product.price % 1.0 == 0.0) product.price.toLong().toString() else product.price.toString())
        etQuantity.setText(product.quantity.toString())
        ProductImages.load(product)?.let { ivPhoto.setImageBitmap(it) }
    }

    private fun save() {
        val name = etName.text?.toString()?.trim().orEmpty()
        val price = etPrice.text?.toString()?.trim()?.toDoubleOrNull()
        val quantity = etQuantity.text?.toString()?.trim()?.toIntOrNull()

        when {
            name.isEmpty() -> return showError("Enter the product name.")
            price == null || price <= 0 -> return showError("Enter a valid price.")
            quantity == null || quantity < 0 -> return showError("Enter a valid quantity (0 or more).")
            !isEdit && selectedPhoto == null -> return showError("Add a product photo from the camera or gallery.")
        }

        runTask {
            val uid = auth.currentUser?.uid ?: error("Please sign in again.")
            val shopName = db.getReference("users").child(uid).child("shopName")
                .get().await().getValue(String::class.java).orEmpty()

            // Upload a new photo to S3 only if the seller picked one
            val bytes = selectedPhoto
            val imageUrl = if (bytes != null) ProductImages.upload(uid, bytes) else existing?.imageUrl.orEmpty()

            val products = db.getReference(Product.NODE)
            val id = productId ?: requireNotNull(products.push().key)
            val product = Product(
                id = id,
                sellerId = uid,
                shopName = shopName,
                name = name,
                price = price!!,
                quantity = quantity!!,
                imageUrl = imageUrl
            )

            if (isEdit) {
                products.child(id).updateChildren(product.toMap(isNew = false)).await()
                toast("Product updated")
                parentFragmentManager.popBackStack()
            } else {
                products.child(id).setValue(product.toMap(isNew = true)).await()
                toast("Product added")
                selectedPhoto = null
                etName.setText("")
                etPrice.setText("")
                etQuantity.setText("")
                ivPhoto.setImageResource(R.drawable.ic_shopping_bag)
                (activity as? SellerHomeActivity)?.showProducts()
            }
            selectedPhoto = null
        }
    }

    private fun confirmDelete() {
        if (busy) return
        AlertDialog.Builder(requireContext())
            .setTitle("Delete product?")
            .setMessage("Buyers will no longer see this item.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                runTask {
                    db.getReference(Product.NODE).child(requireNotNull(productId)).removeValue().await()
                    toast("Product deleted")
                    parentFragmentManager.popBackStack()
                }
            }
            .show()
    }

    private fun toast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}

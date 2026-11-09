package com.abdelrahmanhazem.buyer

import android.graphics.Bitmap
import android.util.LruCache
import android.view.View
import android.widget.ImageView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.abdelrahmanhazem.seller.ProfilePhotos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Loads private S3 photos into list items.
 * - Downloads with the team's ProfilePhotos class (signed S3 GetObject).
 * - Keeps downloaded photos in a memory cache so scrolling doesn't download them again.
 * - Shows [fallback] (e.g. the shop's first letter) until the photo is ready,
 *   or if it can't be loaded (no aws.properties, no internet, no photo yet).
 */
object PhotoLoader {

    // Use 1/8 of the app's memory for photos (sizes in KB)
    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    /**
     * @param folder   the S3 folder the photo was uploaded to, e.g. "seller-profiles" or "products"
     * @param ownerUid the user who uploaded it (the photo lives under <folder>/<ownerUid>/)
     */
    fun load(
        owner: LifecycleOwner,
        imageView: ImageView,
        fallback: View?,
        folder: String,
        ownerUid: String,
        url: String
    ) {
        // Remember which photo this view should show (views get reused while scrolling)
        imageView.tag = url

        if (url.isBlank()) {
            show(imageView, fallback, null)
            return
        }

        cache.get(url)?.let {
            show(imageView, fallback, it)
            return
        }

        show(imageView, fallback, null)
        owner.lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching { ProfilePhotos(folder).download(ownerUid, url) }.getOrNull()
            }
            if (bitmap != null) {
                cache.put(url, bitmap)
                // Only show it if this view still belongs to the same photo
                if (imageView.tag == url) show(imageView, fallback, bitmap)
            }
        }
    }

    private fun show(imageView: ImageView, fallback: View?, bitmap: Bitmap?) {
        imageView.setImageBitmap(bitmap)
        fallback?.visibility = if (bitmap == null) View.VISIBLE else View.GONE
    }
}
package com.abdelrahmanhazem.seller

import android.graphics.Bitmap
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Product photos live in the team S3 bucket under "products/{sellerId}/".
 * Reuses ProfilePhotos (same upload, download and 0.5 MB compression logic),
 * and keeps recently shown images in memory so the grid scrolls smoothly.
 */
object ProductImages {

    val photos = ProfilePhotos("products")

    // About 1/8 of the app's memory, measured in kilobytes
    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    fun cached(url: String): Bitmap? = cache.get(url)

    /** Downloads (or returns from cache) the image of a product. Call from a coroutine. */
    suspend fun load(product: Product): Bitmap? {
        if (product.imageUrl.isBlank()) return null
        cache.get(product.imageUrl)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching { photos.download(product.sellerId, product.imageUrl) }.getOrNull()
        }?.also { cache.put(product.imageUrl, it) }
    }

    /** Uploads already-compressed JPEG bytes and returns the S3 URL. Call from a coroutine. */
    suspend fun upload(sellerId: String, bytes: ByteArray): String =
        withContext(Dispatchers.IO) { photos.upload(sellerId, bytes) }
}

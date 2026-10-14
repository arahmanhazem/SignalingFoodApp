package com.abdelrahmanhazem.seller

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.abdelrahmanhazem.BuildConfig
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.auth.BasicSessionCredentials
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.ObjectMetadata
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.UUID

/**
 * Blocking image/S3 work: callers must use Dispatchers.IO.
 * @param folder S3 key prefix, e.g. "seller-profiles" or "buyer-profiles"
 */
class ProfilePhotos(
    private val folder: String = "seller-profiles"
) {
    companion object {
        const val MAX_BYTES = 500_000
    }

    private fun client(): AmazonS3Client {
        require(
            BuildConfig.AWS_ACCESS_KEY_ID.isNotBlank() &&
                BuildConfig.AWS_SECRET_ACCESS_KEY.isNotBlank() &&
                BuildConfig.AWS_BUCKET.isNotBlank() &&
                BuildConfig.AWS_REGION.isNotBlank()
        ) {
            "Fill in aws.properties at the project root, then rebuild the app."
        }
        val credentials: AWSCredentials = if (BuildConfig.AWS_SESSION_TOKEN.isBlank()) {
            BasicAWSCredentials(BuildConfig.AWS_ACCESS_KEY_ID, BuildConfig.AWS_SECRET_ACCESS_KEY)
        } else {
            BasicSessionCredentials(
                BuildConfig.AWS_ACCESS_KEY_ID,
                BuildConfig.AWS_SECRET_ACCESS_KEY,
                BuildConfig.AWS_SESSION_TOKEN
            )
        }
        return AmazonS3Client(credentials).apply {
            setRegion(Region.getRegion(Regions.fromName(BuildConfig.AWS_REGION)))
        }
    }

    private fun host() = "${BuildConfig.AWS_BUCKET}.s3.${BuildConfig.AWS_REGION}.amazonaws.com"

    fun upload(uid: String, bytes: ByteArray): String {
        require(bytes.isNotEmpty() && bytes.size < MAX_BYTES) { "Photo must be less than 0.5 MB." }
        val key = "$folder/$uid/${System.currentTimeMillis()}-${UUID.randomUUID()}.jpg"
        val s3 = client()
        try {
            val metadata = ObjectMetadata().apply {
                contentLength = bytes.size.toLong()
                contentType = "image/jpeg"
            }
            // Private object: do not request a public ACL. S3 signs the upload.
            bytes.inputStream().use { s3.putObject(BuildConfig.AWS_BUCKET, key, it, metadata) }
            return "https://${host()}/$key"
        } finally {
            s3.shutdown()
        }
    }

    fun download(uid: String, url: String): Bitmap {
        val uri = URI(url)
        require(
            uri.scheme == "https" && uri.host == host() && uri.query == null &&
                uri.fragment == null && uri.userInfo == null && uri.port == -1
        ) { "Unexpected photo URL." }
        val key = uri.path.removePrefix("/")
        require(key.startsWith("$folder/$uid/") && !key.contains("..")) {
            "Photo does not belong to this user."
        }
        val s3 = client()
        try {
            // The stable URL in Firebase identifies the private object; signed GetObject
            // uses team credentials rather than requiring a public bucket or expiring URL.
            val bytes = s3.getObject(BuildConfig.AWS_BUCKET, key).objectContent.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(out.size() + count < MAX_BYTES) { "Stored photo exceeds the size limit." }
                    out.write(buffer, 0, count)
                }
                out.toByteArray()
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            require(bounds.outWidth in 1..2048 && bounds.outHeight in 1..2048) {
                "Invalid stored image dimensions."
            }
            return requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) {
                "Cannot decode photo."
            }
        } finally {
            s3.shutdown()
        }
    }

    fun prepare(context: Context, uri: Uri): ByteArray {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Choose a valid image." }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
        var bitmap = resolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(
                it,
                null,
                BitmapFactory.Options().apply { inSampleSize = sample }
            )
        } ?: error("Cannot open this image.")
        val orientation = resolver.openInputStream(uri).use { input ->
            if (input == null) ExifInterface.ORIENTATION_NORMAL
            else ExifInterface(input).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        }
        if (!matrix.isIdentity) {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) bitmap.recycle()
            bitmap = rotated
        }
        try {
            while (true) {
                for (quality in listOf(88, 75, 60, 45, 30)) {
                    val out = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    if (out.size() < MAX_BYTES) return out.toByteArray()
                }
                require(bitmap.width > 64 && bitmap.height > 64) { "Cannot reduce image below 0.5 MB." }
                val smaller = Bitmap.createScaledBitmap(bitmap, bitmap.width / 2, bitmap.height / 2, true)
                bitmap.recycle()
                bitmap = smaller
            }
        } finally {
            bitmap.recycle()
        }
    }
}

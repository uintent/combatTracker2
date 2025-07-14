// File: ImageRepository.kt
package com.example.combattracker.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.example.combattracker.utils.FileStorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * ImageRepository - Manages all image file operations
 *
 * Purpose:
 * - Handles portrait and background image storage
 * - Processes images to correct size and aspect ratio
 * - Manages file lifecycle (save, delete, cleanup)
 * - Ensures consistent image handling across the app
 *
 * Requirements Reference:
 * From section 3.2.1: Image Support
 * - Supported Formats: Common formats (JPG, PNG, GIF, WebP)
 * - Aspect Ratio: All actor portraits standardized to 1:1.5 ratio (portrait orientation)
 * - Scaling: Images scaled and cropped to fit standardized portrait dimensions
 * From section 3.2.2: Background Customization
 * - Image scaled horizontally, vertical overflow cropped
 */
class ImageRepository(
    private val context: Context,
    private val fileStorageHelper: FileStorageHelper
) {

    companion object {
        // Portrait specifications
        private const val PORTRAIT_ASPECT_RATIO = 1.5f // Height = Width * 1.5
        private const val PORTRAIT_MAX_WIDTH = 600 // Pixels
        private const val PORTRAIT_MAX_HEIGHT = 900 // Pixels (600 * 1.5)
        private const val PORTRAIT_QUALITY = 85 // JPEG compression quality

        // Background specifications
        private const val BACKGROUND_MAX_WIDTH = 1920 // Full HD width
        private const val BACKGROUND_QUALITY = 90 // Higher quality for backgrounds

        // File size limits
        private const val MAX_IMAGE_FILE_SIZE = 10 * 1024 * 1024 // 10MB
    }

    // ========== Portrait Operations ==========

    /**
     * Save an actor portrait from URI
     *
     * Processing steps:
     * 1. Load image from URI
     * 2. Fix orientation based on EXIF data
     * 3. Scale and crop to 1:1.5 aspect ratio
     * 4. Save to internal storage
     *
     * @param sourceUri URI from image picker
     * @param actorName Actor name for filename generation
     * @return Relative path to saved portrait or null on failure
     */
    suspend fun saveActorPortrait(
        sourceUri: Uri,
        actorName: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            Timber.d("Saving portrait for actor: $actorName from $sourceUri")

            // Load and process the image
            val processedBitmap = loadAndProcessPortrait(sourceUri)
                ?: throw IOException("Failed to process portrait image")

            // Generate filename and save
            val filename = fileStorageHelper.generatePortraitFilename(actorName)
            val portraitFile = File(fileStorageHelper.getPortraitDirectory(), filename)

            // Save the processed bitmap
            saveBitmapToFile(processedBitmap, portraitFile, PORTRAIT_QUALITY)

            // Return relative path for database storage
            val relativePath = "portraits/$filename"
            Timber.d("Portrait saved successfully: $relativePath")

            relativePath

        } catch (e: Exception) {
            Timber.e(e, "Failed to save portrait for $actorName")
            null
        }
    }

    /**
     * Load and process portrait image
     *
     * @param sourceUri Source image URI
     * @return Processed bitmap with correct aspect ratio
     */
    private suspend fun loadAndProcessPortrait(sourceUri: Uri): Bitmap? {
        return context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
            // First decode bounds to calculate sample size
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)

            // Calculate sample size for memory efficiency
            options.inSampleSize = calculateInSampleSize(
                options.outWidth,
                options.outHeight,
                PORTRAIT_MAX_WIDTH,
                PORTRAIT_MAX_HEIGHT
            )

            // Decode the actual bitmap
            context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                options.inJustDecodeBounds = false
                val bitmap = BitmapFactory.decodeStream(stream, null, options)
                    ?: throw IOException("Failed to decode image")

                // Fix orientation
                val rotatedBitmap = fixImageOrientation(bitmap, sourceUri)

                // Scale and crop to portrait aspect ratio
                scaleAndCropToPortrait(rotatedBitmap)
            }
        }
    }

    /**
     * Scale and crop bitmap to portrait aspect ratio (1:1.5)
     *
     * @param source Source bitmap
     * @return Processed bitmap with correct aspect ratio
     */
    private fun scaleAndCropToPortrait(source: Bitmap): Bitmap {
        val sourceWidth = source.width
        val sourceHeight = source.height
        val sourceAspectRatio = sourceWidth.toFloat() / sourceHeight

        // Calculate target dimensions
        val targetWidth: Int
        val targetHeight: Int

        if (sourceAspectRatio > 1 / PORTRAIT_ASPECT_RATIO) {
            // Image is too wide, crop horizontally
            targetHeight = sourceHeight
            targetWidth = (targetHeight / PORTRAIT_ASPECT_RATIO).toInt()
        } else {
            // Image is too tall, crop vertically
            targetWidth = sourceWidth
            targetHeight = (targetWidth * PORTRAIT_ASPECT_RATIO).toInt()
        }

        // Calculate crop position (center crop)
        val xOffset = (sourceWidth - targetWidth) / 2
        val yOffset = (sourceHeight - targetHeight) / 2

        // Crop the bitmap
        val croppedBitmap = Bitmap.createBitmap(
            source,
            xOffset.coerceAtLeast(0),
            yOffset.coerceAtLeast(0),
            targetWidth.coerceAtMost(sourceWidth),
            targetHeight.coerceAtMost(sourceHeight)
        )

        // Scale to final size if needed
        return if (targetWidth > PORTRAIT_MAX_WIDTH || targetHeight > PORTRAIT_MAX_HEIGHT) {
            Bitmap.createScaledBitmap(
                croppedBitmap,
                PORTRAIT_MAX_WIDTH,
                PORTRAIT_MAX_HEIGHT,
                true
            ).also {
                if (croppedBitmap != source && croppedBitmap != it) {
                    croppedBitmap.recycle()
                }
            }
        } else {
            croppedBitmap
        }
    }

    // ========== Background Operations ==========

    /**
     * Save a background image from URI
     *
     * Processing:
     * - Scales image to fit screen width
     * - Maintains aspect ratio
     * - Crops vertical overflow
     *
     * @param sourceUri URI from image picker
     * @return Relative path to saved background or null on failure
     */
    suspend fun saveBackgroundImage(sourceUri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            Timber.d("Saving background image from $sourceUri")

            // Load and process the image
            val processedBitmap = loadAndProcessBackground(sourceUri)
                ?: throw IOException("Failed to process background image")

            // Generate filename and save
            val filename = fileStorageHelper.generateBackgroundFilename()
            val backgroundFile = File(fileStorageHelper.getBackgroundDirectory(), filename)

            // Save the processed bitmap
            saveBitmapToFile(processedBitmap, backgroundFile, BACKGROUND_QUALITY)

            // Return relative path
            val relativePath = "backgrounds/$filename"
            Timber.d("Background saved successfully: $relativePath")

            relativePath

        } catch (e: Exception) {
            Timber.e(e, "Failed to save background image")
            null
        }
    }

    /**
     * Load and process background image
     *
     * @param sourceUri Source image URI
     * @return Processed bitmap scaled for background use
     */
    private suspend fun loadAndProcessBackground(sourceUri: Uri): Bitmap? {
        return context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
            // Decode with appropriate sample size
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)

            // Calculate sample size
            options.inSampleSize = calculateInSampleSize(
                options.outWidth,
                options.outHeight,
                BACKGROUND_MAX_WIDTH,
                BACKGROUND_MAX_WIDTH // Use same for height to maintain ratio
            )

            // Decode the actual bitmap
            context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                options.inJustDecodeBounds = false
                val bitmap = BitmapFactory.decodeStream(stream, null, options)
                    ?: throw IOException("Failed to decode image")

                // Fix orientation
                val rotatedBitmap = fixImageOrientation(bitmap, sourceUri)

                // Scale to fit width
                scaleBackgroundToFitWidth(rotatedBitmap)
            }
        }
    }

    /**
     * Scale background image to fit screen width
     *
     * @param source Source bitmap
     * @return Scaled bitmap
     */
    private fun scaleBackgroundToFitWidth(source: Bitmap): Bitmap {
        val sourceWidth = source.width
        val sourceHeight = source.height

        // Calculate scale to fit width
        val scale = BACKGROUND_MAX_WIDTH.toFloat() / sourceWidth
        val targetHeight = (sourceHeight * scale).toInt()

        return Bitmap.createScaledBitmap(
            source,
            BACKGROUND_MAX_WIDTH,
            targetHeight,
            true
        ).also {
            if (source != it) {
                source.recycle()
            }
        }
    }

    // ========== Utility Operations ==========

    /**
     * Delete a portrait image
     *
     * @param portraitPath Relative path to portrait
     * @return True if deleted successfully
     */
    suspend fun deletePortrait(portraitPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val deleted = fileStorageHelper.deleteFile(portraitPath)
            if (deleted) {
                Timber.d("Deleted portrait: $portraitPath")
            }
            deleted
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete portrait: $portraitPath")
            false
        }
    }

    /**
     * Delete a background image
     *
     * @param backgroundPath Relative path to background
     * @return True if deleted successfully
     */
    suspend fun deleteBackground(backgroundPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val deleted = fileStorageHelper.deleteFile(backgroundPath)
            if (deleted) {
                Timber.d("Deleted background: $backgroundPath")
            }
            deleted
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete background: $backgroundPath")
            false
        }
    }

    /**
     * Clean up orphaned portraits not referenced in database
     *
     * @param usedPaths Set of portrait paths currently in use
     * @return Number of files deleted
     */
    suspend fun cleanupOrphanedPortraits(usedPaths: Set<String>): Int = withContext(Dispatchers.IO) {
        var deletedCount = 0

        try {
            val portraitDir = fileStorageHelper.getPortraitDirectory()
            portraitDir.listFiles()?.forEach { file ->
                val relativePath = "portraits/${file.name}"
                if (file.isFile && !usedPaths.contains(relativePath)) {
                    if (file.delete()) {
                        deletedCount++
                        Timber.d("Deleted orphaned portrait: ${file.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error during portrait cleanup")
        }

        deletedCount
    }

    /**
     * Get total storage used by images
     *
     * @return Storage size in bytes
     */
    suspend fun getTotalImageStorageUsed(): Long = withContext(Dispatchers.IO) {
        fileStorageHelper.getPortraitStorageUsed() + getBackgroundStorageUsed()
    }

    /**
     * Get storage used by backgrounds
     *
     * @return Storage size in bytes
     */
    private fun getBackgroundStorageUsed(): Long {
        return try {
            fileStorageHelper.getBackgroundDirectory()
                .walkTopDown()
                .filter { it.isFile }
                .map { it.length() }
                .sum()
        } catch (e: Exception) {
            0L
        }
    }

    // ========== Helper Methods ==========

    /**
     * Calculate appropriate sample size for bitmap decoding
     *
     * @param srcWidth Source image width
     * @param srcHeight Source image height
     * @param reqWidth Required width
     * @param reqHeight Required height
     * @return Sample size (power of 2)
     */
    private fun calculateInSampleSize(
        srcWidth: Int,
        srcHeight: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var inSampleSize = 1

        if (srcHeight > reqHeight || srcWidth > reqWidth) {
            val halfHeight = srcHeight / 2
            val halfWidth = srcWidth / 2

            while ((halfHeight / inSampleSize) >= reqHeight &&
                (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    /**
     * Fix image orientation based on EXIF data
     *
     * @param bitmap Source bitmap
     * @param imageUri Image URI for EXIF data
     * @return Correctly oriented bitmap
     */
    private fun fixImageOrientation(bitmap: Bitmap, imageUri: Uri): Bitmap {
        return try {
            context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )

                val matrix = Matrix()
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                    else -> return bitmap
                }

                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    .also { if (it != bitmap) bitmap.recycle() }
            } ?: bitmap
        } catch (e: Exception) {
            Timber.w(e, "Failed to read EXIF orientation")
            bitmap
        }
    }

    /**
     * Save bitmap to file
     *
     * @param bitmap Bitmap to save
     * @param file Target file
     * @param quality JPEG quality (1-100)
     */
    private fun saveBitmapToFile(bitmap: Bitmap, file: File, quality: Int) {
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.flush()
        }
        bitmap.recycle()
    }
}
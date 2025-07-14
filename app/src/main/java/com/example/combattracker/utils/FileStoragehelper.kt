// File: FileStorageHelper.kt
package com.example.combattracker.utils

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * FileStorageHelper - Centralized file management utility
 *
 * Purpose:
 * Manages all file operations for the app including:
 * - Creating and managing directory structure
 * - Generating unique filenames
 * - Copying files from content URIs to internal storage
 * - Cleaning up orphaned files
 * - Providing file URIs for sharing (if needed in future)
 *
 * Design Decisions:
 * - All files stored in app's internal storage for privacy
 * - Organized directory structure for different file types
 * - Consistent naming conventions for easy debugging
 * - No external storage permissions required
 *
 * Directory Structure:
 * /data/data/com.yourpackage.combattracker/files/
 * ├── portraits/      - Actor portrait images
 * ├── backgrounds/    - Custom background images
 * └── temp/          - Temporary files (cleaned on app start)
 *
 * @param context Application context for accessing internal storage
 */
class FileStorageHelper(private val context: Context) {

    /**
     * Directory names as constants
     * Using constants prevents typos and makes refactoring easier
     */
    companion object {
        private const val DIR_PORTRAITS = "portraits"
        private const val DIR_BACKGROUNDS = "backgrounds"
        private const val DIR_TEMP = "temp"

        /**
         * File extensions
         * We standardize on JPEG for photos and PNG for graphics
         */
        private const val EXTENSION_JPEG = ".jpg"
        private const val EXTENSION_PNG = ".png"

        /**
         * Date format for generating unique filenames
         * Format: yyyyMMdd_HHmmss (e.g., 20250713_143052)
         * This ensures files sort chronologically and are unique to the second
         */
        private const val DATE_FORMAT_FILENAME = "yyyyMMdd_HHmmss"

        /**
         * Buffer size for file copy operations
         * 8KB is a good balance between memory usage and performance
         */
        private const val COPY_BUFFER_SIZE = 8192
    }

    /**
     * Get the portraits directory
     *
     * Creates the directory if it doesn't exist
     *
     * @return File object representing the portraits directory
     * @throws IOException if directory can't be created
     */
    fun getPortraitDirectory(): File {
        return getOrCreateDirectory(DIR_PORTRAITS)
    }

    /**
     * Get the backgrounds directory
     *
     * Creates the directory if it doesn't exist
     *
     * @return File object representing the backgrounds directory
     * @throws IOException if directory can't be created
     */
    fun getBackgroundDirectory(): File {
        return getOrCreateDirectory(DIR_BACKGROUNDS)
    }

    /**
     * Get the temp directory
     *
     * Used for temporary files during image processing
     * Should be cleaned periodically
     *
     * @return File object representing the temp directory
     * @throws IOException if directory can't be created
     */
    fun getTempDirectory(): File {
        return getOrCreateDirectory(DIR_TEMP)
    }

    /**
     * Get or create a directory in internal storage
     *
     * @param dirName Name of the directory
     * @return File object for the directory
     * @throws IOException if directory can't be created
     */
    private fun getOrCreateDirectory(dirName: String): File {
        // Get the app's internal files directory
        // This is typically /data/data/com.example.combattracker/files/
        val directory = File(context.filesDir, dirName)

        // Create directory if it doesn't exist
        if (!directory.exists()) {
            val created = directory.mkdirs()
            if (!created) {
                throw IOException("Failed to create directory: ${directory.absolutePath}")
            }
            Timber.d("Created directory: ${directory.absolutePath}")
        }

        return directory
    }

    /**
     * Generate a unique filename for a portrait
     *
     * Format: portrait_[actorName]_[timestamp].jpg
     * Example: portrait_goblin_20250713_143052.jpg
     *
     * @param actorName Name of the actor (will be sanitized)
     * @return Unique filename
     */
    fun generatePortraitFilename(actorName: String): String {
        val sanitizedName = sanitizeFilename(actorName)
        val timestamp = SimpleDateFormat(DATE_FORMAT_FILENAME, Locale.US).format(Date())
        return "portrait_${sanitizedName}_$timestamp$EXTENSION_JPEG"
    }

    /**
     * Generate a unique filename for a background
     *
     * Format: background_[timestamp].jpg
     * Example: background_20250713_143052.jpg
     *
     * @return Unique filename
     */
    fun generateBackgroundFilename(): String {
        val timestamp = SimpleDateFormat(DATE_FORMAT_FILENAME, Locale.US).format(Date())
        return "background_$timestamp$EXTENSION_JPEG"
    }

    /**
     * Sanitize a filename by removing invalid characters
     *
     * Replaces spaces and special characters with underscores
     * Converts to lowercase for consistency
     * Limits length to prevent filesystem issues
     *
     * @param filename Original filename
     * @return Sanitized filename safe for filesystem
     */
    private fun sanitizeFilename(filename: String): String {
        return filename
            .toLowerCase(Locale.US)
            .replace(Regex("[^a-z0-9]"), "_") // Replace non-alphanumeric with underscore
            .replace(Regex("_+"), "_") // Replace multiple underscores with single
            .trim('_') // Remove leading/trailing underscores
            .take(50) // Limit length to 50 characters
    }

    /**
     * Copy a file from a content URI to internal storage
     *
     * This is the main method for importing images from the gallery
     * Handles all types of content URIs (media, downloads, etc.)
     *
     * @param sourceUri URI from image picker
     * @param destinationFile Target file in internal storage
     * @throws IOException if copy fails
     */
    suspend fun copyUriToFile(sourceUri: Uri, destinationFile: File) {
        Timber.d("Copying from $sourceUri to ${destinationFile.absolutePath}")

        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null

        try {
            // Open input stream from content URI
            inputStream = context.contentResolver.openInputStream(sourceUri)
                ?: throw IOException("Cannot open input stream for URI: $sourceUri")

            // Create output file's parent directories if needed
            destinationFile.parentFile?.mkdirs()

            // Open output stream to destination file
            outputStream = FileOutputStream(destinationFile)

            // Copy data using a buffer
            val buffer = ByteArray(COPY_BUFFER_SIZE)
            var bytesRead: Int
            var totalBytes = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytes += bytesRead
            }

            Timber.d("Successfully copied $totalBytes bytes")

        } catch (e: Exception) {
            // If copy failed, try to clean up the partial file
            if (destinationFile.exists()) {
                destinationFile.delete()
            }
            throw IOException("Failed to copy file from $sourceUri", e)

        } finally {
            // Always close streams
            try {
                inputStream?.close()
            } catch (e: IOException) {
                Timber.w("Failed to close input stream", e)
            }

            try {
                outputStream?.close()
            } catch (e: IOException) {
                Timber.w("Failed to close output stream", e)
            }
        }
    }

    /**
     * Delete a file from internal storage
     *
     * Safe deletion that logs but doesn't throw if file doesn't exist
     *
     * @param filePath Path relative to files directory
     * @return true if file was deleted, false if it didn't exist
     */
    fun deleteFile(filePath: String): Boolean {
        val file = File(context.filesDir, filePath)

        return if (file.exists()) {
            val deleted = file.delete()
            if (deleted) {
                Timber.d("Deleted file: ${file.absolutePath}")
            } else {
                Timber.w("Failed to delete file: ${file.absolutePath}")
            }
            deleted
        } else {
            Timber.d("File doesn't exist, nothing to delete: ${file.absolutePath}")
            false
        }
    }

    /**
     * Get a File object from a relative path
     *
     * @param relativePath Path relative to internal files directory
     * @return File object (may not exist)
     */
    fun getFile(relativePath: String): File {
        return File(context.filesDir, relativePath)
    }

    /**
     * Check if a file exists
     *
     * @param relativePath Path relative to internal files directory
     * @return true if file exists
     */
    fun fileExists(relativePath: String): Boolean {
        return getFile(relativePath).exists()
    }

    /**
     * Get the size of a file in bytes
     *
     * @param relativePath Path relative to internal files directory
     * @return File size in bytes, or 0 if file doesn't exist
     */
    fun getFileSize(relativePath: String): Long {
        val file = getFile(relativePath)
        return if (file.exists()) file.length() else 0L
    }

    /**
     * Clean up the temp directory
     *
     * Deletes all files in the temp directory
     * Should be called periodically (e.g., on app start)
     *
     * @return Number of files deleted
     */
    fun cleanTempDirectory(): Int {
        val tempDir = getTempDirectory()
        var deletedCount = 0

        tempDir.listFiles()?.forEach { file ->
            if (file.isFile && file.delete()) {
                deletedCount++
            }
        }

        if (deletedCount > 0) {
            Timber.d("Cleaned $deletedCount files from temp directory")
        }

        return deletedCount
    }

    /**
     * Get total storage used by the app
     *
     * Useful for showing storage usage in settings
     *
     * @return Total bytes used by all app files
     */
    fun getTotalStorageUsed(): Long {
        return calculateDirectorySize(context.filesDir)
    }

    /**
     * Get storage used by portraits
     *
     * @return Total bytes used by portrait images
     */
    fun getPortraitStorageUsed(): Long {
        return try {
            calculateDirectorySize(getPortraitDirectory())
        } catch (e: IOException) {
            0L
        }
    }

    /**
     * Calculate total size of a directory and its contents
     *
     * @param directory Directory to measure
     * @return Total size in bytes
     */
    private fun calculateDirectorySize(directory: File): Long {
        var size = 0L

        directory.walkTopDown().forEach { file ->
            if (file.isFile) {
                size += file.length()
            }
        }

        return size
    }

    /**
     * Format bytes to human readable string
     *
     * @param bytes Number of bytes
     * @return Formatted string (e.g., "1.5 MB")
     */
    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
            else -> String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }

    /**
     * Get a content URI for a file (for sharing)
     *
     * This would be used if we ever need to share files with other apps
     * Requires FileProvider configuration in AndroidManifest.xml
     *
     * @param file File to get URI for
     * @return Content URI that can be shared with other apps
     */
    fun getShareableUri(file: File): Uri {
        // Note: This requires FileProvider setup in AndroidManifest.xml
        // For now, this is just a placeholder
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
}
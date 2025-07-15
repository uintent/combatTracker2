// File: ImageUtils.kt
package com.example.combattracker.utils

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.net.Uri
import android.renderscript.*
import androidx.core.content.ContextCompat
import com.example.combattracker.R
import com.example.combattracker.data.model.ActorCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * ImageUtils - Image processing utilities for the Combat Tracker
 *
 * Purpose:
 * - Provides image manipulation functions
 * - Handles portrait processing and effects
 * - Creates placeholder images
 * - Applies visual states (greyed out, highlighted, etc.)
 *
 * Requirements Reference:
 * From section 3.2.1: Image Support
 * - Aspect Ratio: All actor portraits standardized to 1:1.5 ratio
 * - Placeholder Images: Default placeholder per actor category when no custom image provided
 * From section 3.4.1: Visual Feedback
 * - Completed State: Greyed out appearance
 * - Missing Initiative: Red overlay highlight
 * - Context Menu: Green tint overlay
 */
object ImageUtils {

    // ========== Constants ==========

    /**
     * Portrait aspect ratio (height / width)
     * 1.5 means height is 1.5x the width
     */
    const val PORTRAIT_ASPECT_RATIO = 1.5f

    /**
     * Standard portrait dimensions for UI display
     */
    const val PORTRAIT_WIDTH_DP = 80
    const val PORTRAIT_HEIGHT_DP = 120

    /**
     * Overlay alpha values
     */
    private const val GREY_OUT_ALPHA = 0.5f
    private const val RED_OVERLAY_ALPHA = 0.3f
    private const val GREEN_TINT_ALPHA = 0.5f
    private const val HIGHLIGHT_BORDER_WIDTH_DP = 3f

    /**
     * Corner radius for rounded portraits
     */
    private const val CORNER_RADIUS_DP = 8f

    // ========== Portrait Processing ==========

    /**
     * Process a portrait image to standard size and aspect ratio
     *
     * @param source Source bitmap
     * @param targetWidth Target width in pixels
     * @return Processed portrait bitmap
     */
    fun processPortrait(source: Bitmap, targetWidth: Int): Bitmap {
        val targetHeight = (targetWidth * PORTRAIT_ASPECT_RATIO).roundToInt()

        // Calculate scaling to fill target while maintaining aspect ratio
        val sourceAspect = source.width.toFloat() / source.height
        val targetAspect = targetWidth.toFloat() / targetHeight

        val scale = if (sourceAspect > targetAspect) {
            // Source is wider - scale by height
            targetHeight.toFloat() / source.height
        } else {
            // Source is taller - scale by width
            targetWidth.toFloat() / source.width
        }

        // Scale the bitmap
        val scaledWidth = (source.width * scale).roundToInt()
        val scaledHeight = (source.height * scale).roundToInt()
        val scaled = Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, true)

        // Center crop to target size
        val xOffset = (scaledWidth - targetWidth) / 2
        val yOffset = (scaledHeight - targetHeight) / 2

        return Bitmap.createBitmap(
            scaled,
            xOffset.coerceAtLeast(0),
            yOffset.coerceAtLeast(0),
            targetWidth,
            targetHeight
        ).also {
            if (scaled != source) scaled.recycle()
        }
    }

    /**
     * Create a rounded corner bitmap
     *
     * @param bitmap Source bitmap
     * @param cornerRadiusPx Corner radius in pixels
     * @return Bitmap with rounded corners
     */
    fun roundCorners(bitmap: Bitmap, cornerRadiusPx: Float): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val paint = Paint().apply {
            isAntiAlias = true
            shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }

        val rect = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, paint)

        return output
    }

    // ========== Placeholder Generation ==========

    /**
     * Generate a placeholder portrait for an actor category
     *
     * @param context Context for resources
     * @param category Actor category
     * @param width Width in pixels
     * @param height Height in pixels
     * @param actorName Optional name to display
     * @return Generated placeholder bitmap
     */
    fun generatePlaceholder(
        context: Context,
        category: ActorCategory,
        width: Int,
        height: Int,
        actorName: String? = null
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background color based on category
        val backgroundColor = when (category) {
            ActorCategory.PLAYER -> ContextCompat.getColor(context, R.color.placeholder_player)
            ActorCategory.NPC -> ContextCompat.getColor(context, R.color.placeholder_npc)
            ActorCategory.MONSTER -> ContextCompat.getColor(context, R.color.placeholder_monster)
            ActorCategory.OTHER -> ContextCompat.getColor(context, R.color.placeholder_other)
        }

        canvas.drawColor(backgroundColor)

        // Draw category icon
        val iconResId = when (category) {
            ActorCategory.PLAYER -> R.drawable.ic_category_player
            ActorCategory.NPC -> R.drawable.ic_category_npc
            ActorCategory.MONSTER -> R.drawable.ic_category_monster
            ActorCategory.OTHER -> R.drawable.ic_category_other
        }

        try {
            val icon = ContextCompat.getDrawable(context, iconResId)
            icon?.let {
                val iconSize = min(width, height) / 2
                val left = (width - iconSize) / 2
                val top = (height - iconSize) / 2
                it.setBounds(left, top, left + iconSize, top + iconSize)
                it.setTint(Color.WHITE)
                it.alpha = 128 // Semi-transparent
                it.draw(canvas)
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to draw category icon")
        }

        // Draw actor name if provided
        actorName?.let { name ->
            val paint = Paint().apply {
                color = Color.WHITE
                textSize = height * 0.08f
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
                typeface = Typeface.DEFAULT_BOLD
                setShadowLayer(2f, 1f, 1f, Color.BLACK)
            }

            // Draw at bottom of portrait
            val textY = height - (height * 0.1f)
            canvas.drawText(name, width / 2f, textY, paint)
        }

        return bitmap
    }

    // ========== Visual State Effects ==========

    /**
     * Apply grey-out effect for actors who have taken their turn
     *
     * @param bitmap Source bitmap
     * @return Greyed out bitmap
     */
    fun applyGreyOut(bitmap: Bitmap): Bitmap {
        val output = bitmap.copy(bitmap.config, true)
        val canvas = Canvas(output)

        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
                setSaturation(0f) // Remove color
            })
            alpha = (255 * GREY_OUT_ALPHA).toInt()
        }

        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        // Add dark overlay
        paint.colorFilter = null
        paint.color = Color.BLACK
        paint.alpha = 64
        canvas.drawRect(0f, 0f, output.width.toFloat(), output.height.toFloat(), paint)

        return output
    }

    /**
     * Apply red overlay for missing initiative
     *
     * @param bitmap Source bitmap
     * @return Bitmap with red overlay
     */
    fun applyRedOverlay(bitmap: Bitmap): Bitmap {
        val output = bitmap.copy(bitmap.config, true)
        val canvas = Canvas(output)

        val paint = Paint().apply {
            color = Color.RED
            alpha = (255 * RED_OVERLAY_ALPHA).toInt()
        }

        canvas.drawRect(0f, 0f, output.width.toFloat(), output.height.toFloat(), paint)

        // Add red border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.alpha = 255
        canvas.drawRect(2f, 2f, output.width - 2f, output.height - 2f, paint)

        return output
    }

    /**
     * Apply green tint for non-selected actors when context menu is open
     *
     * @param bitmap Source bitmap
     * @return Bitmap with green tint
     */
    fun applyGreenTint(bitmap: Bitmap): Bitmap {
        val output = bitmap.copy(bitmap.config, true)
        val canvas = Canvas(output)

        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
                // Add green tint
                set(floatArrayOf(
                    0.7f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 30f,
                    0f, 0f, 0.7f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                ))
            })
        }

        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        return output
    }

    /**
     * Apply highlight border for active actor
     *
     * @param bitmap Source bitmap
     * @param borderColor Border color
     * @param borderWidthPx Border width in pixels
     * @return Bitmap with highlight border
     */
    fun applyHighlightBorder(
        bitmap: Bitmap,
        borderColor: Int = Color.YELLOW,
        borderWidthPx: Float
    ): Bitmap {
        val output = Bitmap.createBitmap(
            bitmap.width + (borderWidthPx * 2).toInt(),
            bitmap.height + (borderWidthPx * 2).toInt(),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(output)

        // Draw border
        val paint = Paint().apply {
            color = borderColor
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, output.width.toFloat(), output.height.toFloat(), paint)

        // Draw original bitmap on top
        canvas.drawBitmap(bitmap, borderWidthPx, borderWidthPx, null)

        return output
    }


    // ========== Utility Functions ==========

    /**
     * Convert DP to pixels
     *
     * @param context Context for display metrics
     * @param dp Value in DP
     * @return Value in pixels
     */
    fun dpToPx(context: Context, dp: Float): Int {
        return (dp * context.resources.displayMetrics.density).roundToInt()
    }

    /**
     * Calculate optimal sample size for loading large images
     *
     * @param options BitmapFactory options with outWidth and outHeight set
     * @param reqWidth Required width
     * @param reqHeight Required height
     * @return Sample size (power of 2)
     */
    fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight &&
                halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    /**
     * Load bitmap from file with memory optimization
     *
     * @param file Image file
     * @param reqWidth Required width
     * @param reqHeight Required height
     * @return Loaded bitmap or null
     */
    suspend fun loadBitmapFromFile(
        file: File,
        reqWidth: Int,
        reqHeight: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // First decode with inJustDecodeBounds=true to check dimensions
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)

            // Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)

            // Decode bitmap with inSampleSize set
            options.inJustDecodeBounds = false
            BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load bitmap from file: ${file.absolutePath}")
            null
        }
    }

    /**
     * Tint a drawable
     *
     * @param drawable Source drawable
     * @param color Tint color
     * @return Tinted drawable
     */
    fun tintDrawable(drawable: Drawable, color: Int): Drawable {
        val wrappedDrawable = drawable.mutate()
        wrappedDrawable.setColorFilter(color, PorterDuff.Mode.SRC_IN)
        return wrappedDrawable
    }

    /**
     * Create a color state list for image tinting
     *
     * @param normalColor Normal state color
     * @param pressedColor Pressed state color
     * @param disabledColor Disabled state color
     * @return ColorStateList for tinting
     */
    fun createImageTintList(
        normalColor: Int,
        pressedColor: Int = normalColor,
        disabledColor: Int = Color.GRAY
    ): android.content.res.ColorStateList {
        val states = arrayOf(
            intArrayOf(-android.R.attr.state_enabled), // Disabled
            intArrayOf(android.R.attr.state_pressed),   // Pressed
            intArrayOf()                                 // Default
        )

        val colors = intArrayOf(disabledColor, pressedColor, normalColor)

        return android.content.res.ColorStateList(states, colors)
    }
}
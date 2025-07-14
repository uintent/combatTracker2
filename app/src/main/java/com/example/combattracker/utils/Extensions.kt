// File: Extensions.kt
package com.example.combattracker.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

/**
 * Extensions.kt - Kotlin extension functions for the Combat Tracker
 *
 * Purpose:
 * - Provides convenient extension functions for common operations
 * - Reduces boilerplate code throughout the app
 * - Improves code readability with more expressive syntax
 * - Centralizes common UI operations
 */

// ========== View Extensions ==========

/**
 * Make a view visible
 */
fun View.visible() {
    visibility = View.VISIBLE
}

/**
 * Make a view invisible (still takes up space)
 */
fun View.invisible() {
    visibility = View.INVISIBLE
}

/**
 * Make a view gone (doesn't take up space)
 */
fun View.gone() {
    visibility = View.GONE
}

/**
 * Make a view visible or gone based on condition
 *
 * @param condition If true, view is visible; if false, view is gone
 */
fun View.visibleIf(condition: Boolean) {
    visibility = if (condition) View.VISIBLE else View.GONE
}

/**
 * Make a view visible or invisible based on condition
 *
 * @param condition If true, view is visible; if false, view is invisible
 */
fun View.invisibleIf(condition: Boolean) {
    visibility = if (condition) View.INVISIBLE else View.VISIBLE
}

/**
 * Set click listener with debounce to prevent double-clicks
 *
 * @param debounceTime Time in milliseconds to ignore subsequent clicks
 * @param action Click action
 */
fun View.setDebounceClickListener(debounceTime: Long = 500L, action: () -> Unit) {
    var lastClickTime = 0L
    setOnClickListener {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime > debounceTime) {
            lastClickTime = currentTime
            action()
        }
    }
}

/**
 * Enable or disable a view
 *
 * @param enabled Whether the view should be enabled
 * @param alpha Optional alpha to apply when disabled
 */
fun View.setEnabledWithAlpha(enabled: Boolean, alpha: Float = 0.5f) {
    this.isEnabled = enabled
    this.alpha = if (enabled) 1.0f else alpha
}

// ========== Context Extensions ==========

/**
 * Get color from resources with compatibility
 */
fun Context.getColorCompat(@ColorRes colorRes: Int): Int {
    return ContextCompat.getColor(this, colorRes)
}

/**
 * Get drawable from resources with compatibility
 */
fun Context.getDrawableCompat(@DrawableRes drawableRes: Int): Drawable? {
    return ContextCompat.getDrawable(this, drawableRes)
}

/**
 * Show a toast message
 *
 * @param message Message to show
 * @param duration Toast duration (default: LENGTH_SHORT)
 */
fun Context.toast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}

/**
 * Show a toast message from string resource
 */
fun Context.toast(@StringRes messageRes: Int, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, messageRes, duration).show()
}

/**
 * Convert DP to pixels
 */
fun Context.dpToPx(dp: Float): Int {
    return (dp * resources.displayMetrics.density).roundToInt()
}

/**
 * Convert pixels to DP
 */
fun Context.pxToDp(px: Int): Float {
    return px / resources.displayMetrics.density
}

// ========== Activity Extensions ==========

/**
 * Hide keyboard
 */
fun Activity.hideKeyboard() {
    currentFocus?.let { view ->
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}

/**
 * Show keyboard for a specific view
 */
fun Activity.showKeyboard(view: View) {
    view.requestFocus()
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
}

/**
 * Start an activity with a lambda for intent configuration
 */
inline fun <reified T : Activity> Activity.startActivity(
    noinline init: Intent.() -> Unit = {}
) {
    val intent = Intent(this, T::class.java)
    intent.init()
    startActivity(intent)
}

/**
 * Start an activity for result with a lambda for intent configuration
 */
inline fun <reified T : Activity> Activity.startActivityForResult(
    requestCode: Int,
    noinline init: Intent.() -> Unit = {}
) {
    val intent = Intent(this, T::class.java)
    intent.init()
    startActivityForResult(intent, requestCode)
}

// ========== Fragment Extensions ==========

/**
 * Show a snackbar from a fragment
 */
fun Fragment.showSnackbar(
    message: String,
    duration: Int = Snackbar.LENGTH_SHORT,
    actionText: String? = null,
    action: (() -> Unit)? = null
) {
    view?.let { view ->
        val snackbar = Snackbar.make(view, message, duration)
        if (actionText != null && action != null) {
            snackbar.setAction(actionText) { action() }
        }
        snackbar.show()
    }
}

/**
 * Hide keyboard from a fragment
 */
fun Fragment.hideKeyboard() {
    activity?.hideKeyboard()
}

// ========== ViewGroup Extensions ==========

/**
 * Inflate a layout
 */
fun ViewGroup.inflate(@LayoutRes layoutRes: Int, attachToRoot: Boolean = false): View {
    return LayoutInflater.from(context).inflate(layoutRes, this, attachToRoot)
}

/**
 * Get all child views as a list
 */
fun ViewGroup.children(): List<View> {
    return (0 until childCount).map { getChildAt(it) }
}

// ========== EditText Extensions ==========

/**
 * Extension to observe text changes as a Flow with debounce
 * Useful for search functionality
 *
 * @param debounceMillis Debounce time in milliseconds
 * @return Flow of text changes
 */
fun EditText.textChanges(debounceMillis: Long = 300L): Flow<String> = callbackFlow {
    val textWatcher = object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {
            trySend(s?.toString() ?: "")
        }
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    }

    addTextChangedListener(textWatcher)

    awaitClose { removeTextChangedListener(textWatcher) }
}.debounce(debounceMillis)

/**
 * Set text and move cursor to end
 */
fun EditText.setTextWithCursor(text: String) {
    setText(text)
    setSelection(text.length)
}

/**
 * Clear text and hide keyboard
 */
fun EditText.clearAndHideKeyboard() {
    setText("")
    clearFocus()
    (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
        .hideSoftInputFromWindow(windowToken, 0)
}

// ========== ImageView Extensions ==========

/**
 * Load image from file using Glide
 *
 * @param file Image file
 * @param placeholder Placeholder drawable resource
 * @param error Error drawable resource
 */
fun ImageView.loadFromFile(
    file: File?,
    @DrawableRes placeholder: Int? = null,
    @DrawableRes error: Int? = null
) {
    Glide.with(this)
        .load(file)
        .apply {
            placeholder?.let { placeholder(it) }
            error?.let { error(it) }
        }
        .into(this)
}

/**
 * Load image from internal storage path
 *
 * @param relativePath Path relative to internal files directory
 * @param placeholder Placeholder drawable resource
 */
fun ImageView.loadFromInternalStorage(
    relativePath: String?,
    @DrawableRes placeholder: Int? = null
) {
    if (relativePath.isNullOrEmpty()) {
        placeholder?.let { setImageResource(it) }
        return
    }

    val file = File(context.filesDir, relativePath)
    loadFromFile(file, placeholder)
}

// ========== LiveData Extensions ==========

/**
 * Observe LiveData with a non-null value
 */
fun <T> LiveData<T>.observeNonNull(owner: LifecycleOwner, observer: (T) -> Unit) {
    this.observe(owner, Observer { value ->
        value?.let { observer(it) }
    })
}

/**
 * Observe LiveData only once
 */
fun <T> LiveData<T>.observeOnce(owner: LifecycleOwner, observer: Observer<T>) {
    observe(owner, object : Observer<T> {
        override fun onChanged(value: T) {
            observer.onChanged(value)
            removeObserver(this)
        }
    })
}

// ========== String Extensions ==========

/**
 * Capitalize first letter of each word
 */
fun String.titleCase(): String {
    return split(" ").joinToString(" ") { word ->
        word.lowercase().replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault())
            else it.toString()
        }
    }
}

/**
 * Truncate string with ellipsis
 *
 * @param maxLength Maximum length before truncation
 * @param ellipsis Ellipsis string (default: "...")
 */
fun String.truncate(maxLength: Int, ellipsis: String = "..."): String {
    return if (length <= maxLength) this
    else take(maxLength - ellipsis.length) + ellipsis
}

/**
 * Check if string is a valid number
 */
fun String.isNumber(): Boolean {
    return toIntOrNull() != null
}

// ========== Date Extensions ==========

/**
 * Format date to a readable string
 *
 * @param pattern Date format pattern
 */
fun Date.format(pattern: String = "MMM d, yyyy"): String {
    return SimpleDateFormat(pattern, Locale.getDefault()).format(this)
}

/**
 * Get relative time string (e.g., "2 hours ago")
 */
fun Date.toRelativeTimeString(): String {
    val now = Date()
    val diffMs = now.time - this.time
    val diffMinutes = diffMs / (1000 * 60)
    val diffHours = diffMs / (1000 * 60 * 60)
    val diffDays = diffMs / (1000 * 60 * 60 * 24)

    return when {
        diffMinutes < 1 -> "Just now"
        diffMinutes < 60 -> "$diffMinutes minutes ago"
        diffHours < 24 -> "$diffHours hours ago"
        diffDays < 7 -> "$diffDays days ago"
        else -> format("MMM d, yyyy")
    }
}

// ========== Collection Extensions ==========

/**
 * Update a single item in a list
 *
 * @param predicate Condition to find the item
 * @param transform Transform function for the found item
 * @return New list with the transformed item
 */
inline fun <T> List<T>.updateFirst(
    predicate: (T) -> Boolean,
    transform: (T) -> T
): List<T> {
    return map { item ->
        if (predicate(item)) transform(item) else item
    }
}

/**
 * Move an item in a list
 *
 * @param fromIndex Current index
 * @param toIndex Target index
 * @return New list with moved item
 */
fun <T> List<T>.move(fromIndex: Int, toIndex: Int): List<T> {
    if (fromIndex == toIndex) return this

    val mutable = toMutableList()
    val item = mutable.removeAt(fromIndex)
    mutable.add(toIndex, item)
    return mutable
}

// ========== RecyclerView Extensions ==========

/**
 * Submit list to ListAdapter with scroll to top option
 *
 * @param list New list
 * @param scrollToTop Whether to scroll to top after update
 */
fun <T, VH : RecyclerView.ViewHolder> ListAdapter<T, VH>.submitListWithScroll(
    list: List<T>?,
    recyclerView: RecyclerView,
    scrollToTop: Boolean = false
) {
    submitList(list) {
        if (scrollToTop && (list?.isNotEmpty() == true)) {
            recyclerView.scrollToPosition(0)
        }
    }
}

// ========== Number Extensions ==========

/**
 * Format number with ordinal suffix (1st, 2nd, 3rd, etc.)
 */
fun Int.toOrdinal(): String {
    val suffix = when {
        this % 100 in 11..13 -> "th"
        this % 10 == 1 -> "st"
        this % 10 == 2 -> "nd"
        this % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$this$suffix"
}

/**
 * Clamp a number between min and max values
 */
fun Int.clamp(min: Int, max: Int): Int {
    return coerceIn(min, max)
}

/**
 * Clamp a float between min and max values
 */
fun Float.clamp(min: Float, max: Float): Float {
    return coerceIn(min, max)
}

// Add these extension functions to your Extensions.kt file in the Activity Extensions section

/**
 * Navigate to encounter list activity
 */
fun Activity.navigateToEncounterList() {
    val intent = Intent(this, com.example.combattracker.ui.encounter.EncounterManageActivity::class.java)
    startActivity(intent)
}

/**
 * Show end encounter dialog
 */
fun Activity.showEndEncounterDialog() {
    androidx.appcompat.app.AlertDialog.Builder(this)
        .setTitle(Constants.Dialogs.END_ENCOUNTER_TITLE)
        .setMessage(Constants.Dialogs.END_ENCOUNTER_MESSAGE)
        .setPositiveButton(Constants.Dialogs.END_ENCOUNTER_SAVE) { _, _ ->
            // This should be handled by the activity
            if (this is com.example.combattracker.ui.combat.CombatTrackerActivity) {
                saveEncounter()
                finish()
            }
        }
        .setNegativeButton(Constants.Dialogs.END_ENCOUNTER_DISCARD) { _, _ ->
            finish()
        }
        .setNeutralButton(Constants.Dialogs.BUTTON_CANCEL, null)
        .show()
}

/**
 * Show save encounter dialog
 */
fun Activity.showSaveEncounterDialog() {
    // This should be implemented in the specific activity since it needs access to viewModel
    if (this is com.example.combattracker.ui.combat.CombatTrackerActivity) {
        saveEncounter()
    }
}
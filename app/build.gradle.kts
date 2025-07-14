// File: build.gradle.kts
// Location: combatTracker2/app/build.gradle.kts

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt") // For Room annotation processing
}

/**
 * Android Configuration Block
 * Defines how the Android app is built and what features it supports
 */
android {
    /**
     * Namespace is the package name for generated R class and BuildConfig
     * This should match your main package structure
     */
    namespace = "com.example.combattracker"

    /**
     * Compile SDK - The Android SDK version used to compile the app
     * Using 34 (Android 14) for access to latest APIs while maintaining compatibility
     */
    compileSdk = 34


    defaultConfig {
        /**
         * Application ID - Unique identifier for your app
         * Since we're not publishing, this can be anything
         */
        applicationId = "com.example.combattracker"

        /**
         * Min SDK - Minimum Android version supported
         * API 26 (Android 8.0) gives us:
         * - Java 8+ features without desugaring
         * - Autofill framework
         * - Notification channels
         * - Better file handling APIs
         *
         * Note: Requirements mention API 28 preference, but API 26 provides
         * nearly identical development experience with better device coverage
         */
        minSdk = 26

        /**
         * Target SDK - The Android version we're optimizing for
         * Always use the latest stable SDK for security and performance
         */
        targetSdk = 34

        /**
         * Version Code & Name - Not critical since we're not publishing
         * But good practice to maintain versioning
         */
        versionCode = 1
        versionName = "1.0.0"

        /**
         * Test runner for instrumented tests
         * AndroidJUnitRunner is the standard choice
         */
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        /**
         * Vector Drawable Support
         * Ensures vector images work on older Android versions
         * Important for our condition icons
         */
        vectorDrawables {
            useSupportLibrary = true
        }

        /**
         * Room Database Schema Export
         * Exports schema to JSON for version control and migrations
         * Critical for tracking database changes between versions
         */
        kapt {
            arguments {
                arg("room.schemaLocation", "$projectDir/schemas")
            }
        }
    }

    /**
     * Build Types
     * Since we're not publishing, we'll keep both simple
     */
    buildTypes {
        debug {
            /**
             * No minification for easier debugging
             * This is what we'll use most of the time
             */
            isMinifyEnabled = false
            isDebuggable = true
        }

        release {
            /**
             * Even for release, no minification since we're not publishing
             * This makes debugging production issues much easier
             */
            isMinifyEnabled = false
            isDebuggable = false
        }
    }

    /**
     * Compile Options
     * Configures Java compatibility
     */
    compileOptions {
        /**
         * Java 8 is minimum for modern Android development
         * Enables lambdas, streams, and other Java 8 features
         */
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    /**
     * Kotlin Options
     * Configures Kotlin compiler
     */
    kotlinOptions {
        /**
         * JVM target must match Java version
         */
        jvmTarget = "1.8"

        /**
         * Compiler arguments for better development experience
         */
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn", // Allows using experimental APIs
            "-Xjvm-default=all" // Better Java interop
        )
    }

    /**
     * Build Features
     * Enables specific Android features
     */
    buildFeatures {
        /**
         * View Binding - Type-safe view references
         * Better than findViewById, simpler than Data Binding
         * Generates binding classes for each layout
         */
        viewBinding = true

        /**
         * BuildConfig - Generates BuildConfig class with build information
         * Useful for conditional code based on build type
         */
        buildConfig = true

        /**
         * Compose is disabled as we're using traditional Views
         * This saves build time and APK size
         */
        compose = false
    }

    /**
     * Lint Options
     * Configure static code analysis
     */
    lint {
        /**
         * Don't abort on errors since we're not publishing
         * We'll see warnings but builds won't fail
         */
        abortOnError = false

        /**
         * Still check for issues to maintain code quality
         */
        checkReleaseBuilds = true

        /**
         * Disable specific checks that might be annoying during development
         */
        disable += setOf(
            "MissingTranslation", // We're only supporting English
            "ExtraTranslation",   // Same reason
            "InvalidPackage"      // Some libraries have issues with this
        )
    }
}

/**
 * Dependencies
 *
 * Organization:
 * 1. Android Core
 * 2. UI Components
 * 3. Architecture Components
 * 4. Database
 * 5. Image Loading
 * 6. Utilities
 * 7. Testing
 */
dependencies {

    // ========== Android Core Dependencies ==========

    /**
     * Core KTX - Kotlin extensions for Android framework classes
     * Makes Android APIs more Kotlin-friendly with extension functions
     * Example: view.isVisible instead of view.visibility = View.VISIBLE
     */
    implementation("androidx.core:core-ktx:1.12.0")

    /**
     * AppCompat - Backports modern Android features to older versions
     * Essential for consistent UI across Android versions
     * Provides AppCompatActivity which we'll extend
     */
    implementation("androidx.appcompat:appcompat:1.6.1")

    /**
     * Lifecycle Components - For ViewModel and LiveData
     * Version 2.7.0 includes newest lifecycle features
     * ViewModels survive configuration changes (rotation)
     */
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")

    // ========== UI Components ==========

    /**
     * Material Design 3 - Google's design system components
     * Provides:
     * - Bottom sheets (for actor context menu)
     * - Cards (for main menu)
     * - Dialogs with Material styling
     * - Floating action buttons
     * - Text input layouts
     */
    implementation("com.google.android.material:material:1.11.0")

    /**
     * ConstraintLayout - Flexible layout system
     * Perfect for complex landscape layouts where we need precise positioning
     * Better performance than nested LinearLayouts
     */
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    /**
     * RecyclerView - Efficient list display
     * Used for:
     * - Actor library list
     * - Encounter list
     * - Condition selection in context menu
     */
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    /**
     * CardView - Material Design cards
     * Used for main menu buttons and actor list items
     * Provides elevation and rounded corners
     */
    implementation("androidx.cardview:cardview:1.0.0")

    /**
     * Fragment KTX - Kotlin extensions for Fragments
     * Simplifies:
     * - Fragment transactions
     * - Fragment result API (for bottom sheet communication)
     * - ViewModel access in fragments
     */
    implementation("androidx.fragment:fragment-ktx:1.6.2")

    // ========== Architecture Components ==========

    /**
     * Activity KTX - Kotlin extensions for Activities
     * Provides:
     * - viewModels() delegate for easy ViewModel access
     * - Modern activity result API
     */
    implementation("androidx.activity:activity-ktx:1.8.2")

    // ========== Database (Room) ==========

    /**
     * Room - SQLite abstraction layer
     * Provides:
     * - Compile-time SQL verification
     * - Automatic object mapping
     * - Database migrations
     * - Coroutine support
     */
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion") // Adds coroutine support
    kapt("androidx.room:room-compiler:$roomVersion") // Generates implementation code

    /**
     * Room Testing - For database tests
     * Allows in-memory database for fast tests
     */
    testImplementation("androidx.room:room-testing:$roomVersion")

    // ========== Image Loading ==========

    /**
     * Glide - Fast and efficient image loading
     * Chosen because:
     * - Excellent memory management for large images
     * - Built-in image transformations
     * - Easy to implement 1:1.5 aspect ratio cropping
     * - Handles lifecycle automatically
     */
    implementation("com.github.bumptech.glide:glide:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0") // For @GlideModule

    /**
     * Glide Transformations - For image cropping/scaling
     * Provides CenterCrop, RoundedCorners, and custom transformations
     * We'll use this for portrait aspect ratio enforcement
     */
    implementation("jp.wasabeef:glide-transformations:4.3.0")

    // ========== Utilities ==========

    /**
     * Kotlin Coroutines - For async operations
     * Required for:
     * - Room database operations
     * - Image processing
     * - File I/O operations
     */
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    /**
     * Timber - Better logging than Log.d
     * Benefits:
     * - Automatic tag generation
     * - Can be disabled in release builds
     * - Consistent logging format
     */
    implementation("com.jakewharton.timber:timber:5.0.1")

    /**
     * SharedPreferences KTX - Kotlin extensions for preferences
     * Used for:
     * - Storing background image path
     * - User preferences
     * Simple key-value storage without DataStore complexity
     */
    implementation("androidx.preference:preference-ktx:1.2.1")

    // ========== Testing Dependencies ==========

    /**
     * JUnit - Basic unit testing framework
     */
    testImplementation("junit:junit:4.13.2")

    /**
     * AndroidX Test - For instrumented tests
     */
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    /**
     * Mockito - For mocking in tests
     * Kotlin extensions make it more pleasant to use
     */
    testImplementation("org.mockito:mockito-core:5.10.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")

    /**
     * Coroutines Test - For testing coroutine code
     * Provides TestDispatcher for controlling coroutine execution in tests
     */
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

    // Glide for image loading
    implementation 'com.github.bumptech.glide:glide:4.12.0'
    kapt 'com.github.bumptech.glide:compiler:4.12.0'

    // Material Components
    implementation 'com.google.android.material:material:1.9.0'
}
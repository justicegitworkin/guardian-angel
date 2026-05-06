import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

// Read BAKE_IN_API_KEY from local.properties (which is gitignored). If the
// developer added `safe.companion.anthropic.api.key=sk-ant-...` there, the key
// gets compiled into BuildConfig.DEFAULT_ANTHROPIC_API_KEY and the onboarding
// "Connect Safe Companion's brain" screen auto-skips. If the property is
// missing, the BuildConfig value is empty string and the user is asked to
// enter their own key (the original behaviour).
val bakedAnthropicApiKey: String = run {
    val f = rootProject.file("local.properties")
    if (f.exists()) {
        val props = Properties()
        f.inputStream().use { props.load(it) }
        props.getProperty("safe.companion.anthropic.api.key", "")
    } else ""
}

// Same pattern for ElevenLabs (the natural-voice TTS provider). Add
// `safe.companion.elevenlabs.api.key=...` to local.properties to skip the
// manual entry step in Settings → Voice Quality on every fresh install.
val bakedElevenLabsApiKey: String = run {
    val f = rootProject.file("local.properties")
    if (f.exists()) {
        val props = Properties()
        f.inputStream().use { props.load(it) }
        props.getProperty("safe.companion.elevenlabs.api.key", "")
    } else ""
}

// Beta feedback form URL. Set
//   safe.companion.feedback.form.url=https://docs.google.com/forms/d/e/<form-id>/viewform
// in local.properties. Use Google Forms' "Get pre-filled link" feature with
// our placeholder tokens APP_VERSION_HERE / DEVICE_HERE / OS_VERSION_HERE /
// FLAVOR_HERE in the device-info fields. The runtime code substitutes them.
// If the URL is blank, the in-app "Give Feedback" button is hidden.
val bakedFeedbackFormUrl: String = run {
    val f = rootProject.file("local.properties")
    if (f.exists()) {
        val props = Properties()
        f.inputStream().use { props.load(it) }
        props.getProperty("safe.companion.feedback.form.url", "")
    } else ""
}

android {
    namespace = "com.safeharborsecurity.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.safeharborsecurity.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Compile the Anthropic key from local.properties into BuildConfig so
        // the onboarding flow can auto-skip the API-key page on dev builds.
        // Inner quotes are part of the generated Java/Kotlin string literal.
        buildConfigField(
            "String",
            "DEFAULT_ANTHROPIC_API_KEY",
            "\"" + bakedAnthropicApiKey.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        )
        buildConfigField(
            "String",
            "DEFAULT_ELEVENLABS_API_KEY",
            "\"" + bakedElevenLabsApiKey.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        )
        buildConfigField(
            "String",
            "FEEDBACK_FORM_URL",
            "\"" + bakedFeedbackFormUrl.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        )
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Part D4: distribution flavors. The standard flavor is the production-ready
    // build. The beta flavor is for hand-out APKs to non-technical testers — it
    // has its own applicationId so it can sit alongside a production install.
    flavorDimensions += "distribution"
    productFlavors {
        create("standard") {
            dimension = "distribution"
            buildConfigField("Boolean", "DEMO_MODE_DEFAULT", "false")
            buildConfigField("Boolean", "SHOW_DEBUG_INFO", "false")
        }
        create("beta") {
            dimension = "distribution"
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-beta"
            buildConfigField("Boolean", "DEMO_MODE_DEFAULT", "true")
            buildConfigField("Boolean", "SHOW_DEBUG_INFO", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }

    testOptions {
        // Robolectric needs this to load AndroidManifest + resources during
        // unit tests. Without it Robolectric falls back to OS-only resources
        // and prints a warning per test class.
        unitTests.isIncludeAndroidResources = true
        // Some Robolectric internals rely on real Android stubs being
        // returned (not the default-throw). Without this, Settings.Secure
        // queries blow up instead of returning null.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")

    // Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    // ProcessLifecycleOwner — used by SafeHarborApp to suppress screen-scan
    // OCR while our own app is foregrounded (prevents nested scam alerts on
    // the chat with Grace).
    implementation("androidx.lifecycle:lifecycle-process:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.2")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Retrofit + OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")

    // Custom Tabs (for "Learn More" links)
    implementation("androidx.browser:browser:1.8.0")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Lifecycle ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    // Accompanist Permissions
    implementation("com.google.accompanist:accompanist-permissions:0.36.0")

    // Coil (image loading for Compose)
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Google Sign-In (Gmail OAuth)
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // Biometric
    implementation("androidx.biometric:biometric:1.1.0")

    // Security (EncryptedSharedPreferences)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // CameraX (QR scanner)
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    // ML Kit Barcode Scanning
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // ML Kit on-device text recognition (Item 2: screenshot OCR for SMS / payment-
    // app detection without NotificationListener or UsageStats)
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // Testing — unit (JVM)
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.13")
    // Robolectric lets us run "Android" classes (Log, Uri, regex...) on the
    // JVM without needing an emulator. We use it sparingly — pure-Kotlin
    // logic should not need it, but anything touching android.util.Log or
    // android.net.Uri does.
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("app.cash.turbine:turbine:1.1.0")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("com.google.truth:truth:1.4.4")

    // Testing — instrumented (device / emulator)
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("io.mockk:mockk-android:1.13.13")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

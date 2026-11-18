import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

// Room writes a JSON description of every database version into app/schemas/ (committed to git).
// Migration tests compare against these files, and SchemaHistoryTest makes sure a released
// version's schema never changes (IDEA.md §5a).
room {
    schemaDirectory("$projectDir/schemas")
}

// Release signing key (IDEA.md §5a).
// The key and its passwords live outside the project entirely, in <user home>/keys/:
//   noteapp-release.jks              — the key itself
//   noteapp-keystore.properties      — storeFile, storePassword, keyAlias, keyPassword
// Every build — debug and release — is signed with this one key, so the app on the tablet can
// always be updated without uninstalling. If the file is missing (e.g. another computer),
// builds fall back to the default debug key and a warning is printed.
val keystorePropertiesFile = File(System.getProperty("user.home"), "keys/noteapp-keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) keystorePropertiesFile.inputStream().use { load(it) }
}
val signingKeys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
val hasReleaseKey = signingKeys.all { !keystoreProperties.getProperty(it).isNullOrBlank() } &&
    file(keystoreProperties.getProperty("storeFile")).exists()
if (!hasReleaseKey) {
    logger.warn(
        "NoteApp: release signing key not found ($keystorePropertiesFile missing or incomplete). " +
            "Using the default debug key; don't install this build over one signed with the release key."
    )
}

android {
    namespace = "com.nzfrankwu.noteapp"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.nzfrankwu.noteapp"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Native code only for ARM tablets and phones (64- and 32-bit) and 64-bit x86 (emulators,
        // Chromebooks). 32-bit x86 devices are long gone; leaving them out saves ~10 MB (19 Nov).
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("noteappRelease") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            if (hasReleaseKey) signingConfig = signingConfigs.getByName("noteappRelease")
        }
        release {
            if (hasReleaseKey) signingConfig = signingConfigs.getByName("noteappRelease")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
                // Our own rules: see keep-rules.pro (no renaming: it crashed the release app at start).
                keepRules { files.add(file("keep-rules.pro")) }
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true // gives the app its own version name (shown in Settings → About)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    // Also used directly from step 0.4 (navigation). Declared here so the app ships a version new
    // enough for Room's migration-test helper: Android test builds must reuse the app's versions,
    // and the 1.7.3 that came in indirectly made MigrationTest crash.
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json) // reading Canvas API responses
    implementation(libs.okhttp) // Canvas API requests (GET only) and Google Drive backup
    implementation(libs.play.services.auth) // Google sign-in for Drive backup (scope drive.file)
    implementation(libs.androidx.datastore.preferences) // small settings (Canvas address, last sync)
    implementation(libs.androidx.work.runtime.ktx) // background Canvas sync (and Phase 6 backup)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    // Handwriting (Phase 2): low-latency stylus strokes, brushes and stroke rendering.
    // Strokes are SAVED in our own page format (PageInkCodec), not Ink's, so notes outlive the library.
    implementation(libs.androidx.ink.authoring)
    implementation(libs.androidx.ink.authoring.compose)
    implementation(libs.androidx.ink.brush)
    implementation(libs.androidx.ink.geometry)
    implementation(libs.androidx.ink.rendering)
    implementation(libs.androidx.ink.strokes)
    implementation(libs.androidx.input.motionprediction)
    // Export as PDF (Phase 3): keeps imported PDF pages as they are, with the ink on top.
    implementation(libs.pdfbox.android)
    implementation(libs.mlkit.digital.ink) // handwriting search, recognised on the device (PHASE-7.md 7.10)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver) // a fake Canvas server for tests
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
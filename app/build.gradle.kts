import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
}

// Helper to read .env.local, local.properties, and keystore.properties
val env = Properties().apply {
    val envFile = rootProject.file(".env.local")
    if (envFile.exists()) {
        envFile.inputStream().use { load(it) }
    }
}

val localProperties = Properties().apply {
    val localPropFile = rootProject.file("local.properties")
    if (localPropFile.exists()) {
        localPropFile.inputStream().use { load(it) }
    }
}

val keystoreProperties = Properties().apply {
    val keystorePropFile = rootProject.file("keystore.properties")
    if (keystorePropFile.exists()) {
        keystorePropFile.inputStream().use { load(it) }
    }
}

fun getPropOrEnv(key: String, default: String = ""): String {
    return keystoreProperties.getProperty(key)
        ?: localProperties.getProperty(key)
        ?: env.getProperty(key)
        ?: System.getenv(key)
        ?: default
}

fun getEnv(key: String, default: String = ""): String {
    return getPropOrEnv(key, default)
}

// Emit AppDatabase/<version>.json so MigrationTestHelper can validate a migration against
// Room's real expected schema. Both halves are required: the KSP arg supplies the output
// directory, and sourceSets below packages that directory into the androidTest APK's assets
// under the path the helper reads (<canonicalName>/<version>.json).
// This is what the Room Gradle plugin does implicitly; wired by hand to avoid the extra plugin.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "com.neochildclinic"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.neochildclinic"
        minSdk = 29
        targetSdk = 35
        versionCode = libs.versions.appVersionCode.get().toInt()
        versionName = libs.versions.appVersionName.get()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", "\"${getEnv("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${getEnv("SUPABASE_PUBLISHABLE_KEY")}\"")
        // Cloudflare Worker that mediates cloud backup/restore against R2 (see
        // cloudflare/backup-worker/). Empty means Cloud Backup/Restore are simply
        // unavailable in this build - Local Export/Import always work regardless.
        buildConfigField("String", "BACKUP_WORKER_URL", "\"${getEnv("BACKUP_WORKER_URL")}\"")

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        create("release") {
            val keystorePath = getPropOrEnv("KEYSTORE_FILE", getPropOrEnv("RELEASE_STORE_FILE"))
            val keystoreFile = when {
                keystorePath.isNotBlank() && file(keystorePath).exists() -> file(keystorePath)
                keystorePath.isNotBlank() && rootProject.file(keystorePath).exists() -> rootProject.file(keystorePath)
                rootProject.file("neochildclinic-release.jks").exists() -> rootProject.file("neochildclinic-release.jks")
                else -> null
            }
            if (keystoreFile != null && keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = getPropOrEnv("KEYSTORE_PASSWORD", getPropOrEnv("RELEASE_STORE_PASSWORD", ""))
                keyAlias = getPropOrEnv("KEY_ALIAS", getPropOrEnv("RELEASE_KEY_ALIAS", "key0"))
                keyPassword = getPropOrEnv("KEY_PASSWORD", getPropOrEnv("RELEASE_KEY_PASSWORD", storePassword ?: ""))
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            versionNameSuffix = "-debug"
            ndk {
                // Instrumented tests run on an x86_64 emulator in CI. defaultConfig pins
                // every build to arm64-v8a (release APKs ship arm64 only), which made
                // :app:connectedDebugAndroidTest report "No compatible devices connected"
                // even though the emulator had booted. Debug adds x86_64; release is
                // untouched by this block.
                abiFilters += listOf("arm64-v8a", "x86_64")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            val releaseSigning = signingConfigs.findByName("release")
            if (releaseSigning?.storeFile != null) {
                signingConfig = releaseSigning
            }
        }
    }

    sourceSets {
        // Feed the exported Room schemas to the instrumentation APK so
        // MigrationTestHelper can load <canonicalName>/<version>.json at runtime.
        getByName("androidTest") {
            assets.srcDir("$projectDir/schemas")
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = true
        disable += "NullSafeMutableLiveData"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // Any unit test that reaches android.util.Log (or Uri, or TextUtils) otherwise dies with
    // "Method e in android.util.Log not mocked" - which reads like a failure in the code under
    // test and makes a pass/fail assertion pass for the wrong reason. Returns defaults instead.
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}


dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.compose.material.icons.extended)
    
    // Supabase
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.storage)
    implementation(libs.supabase.functions)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    
    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    
    // Utilities
    implementation(libs.play.services.base)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // SQLCipher for Room Encryption
    implementation(libs.sqlcipher)

    // Security for encryption key management
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.biometric)

    // WorkManager for background sync
    implementation(libs.androidx.work.runtime.ktx)

    // Glance for Widget
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.datastore.preferences)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)

    // Firebase App Check (Removed as we move to Supabase)
    // implementation("com.google.firebase:firebase-appcheck-playintegrity")
    // implementation("com.google.firebase:firebase-appcheck-debug")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.runner)
    debugImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.compose.ui.tooling)
}

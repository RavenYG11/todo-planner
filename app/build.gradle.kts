import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// --- Signing permanen (ronde 3b / #88) -------------------------------------
// Bila file keystore.properties ada di root proyek (lokal, TIDAK di-commit),
// semua build (debug & release) ditandatangani dengan keystore permanen di
// luar repo agar update di HP selalu bisa di-install tanpa uninstall.
// Bila file tidak ada (mis. GitHub Actions), fallback ke signing debug bawaan.
// Password TIDAK PERNAH ditulis di file ini (file ini ter-push ke repo publik).
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
val usePermanentSigning = keystorePropertiesFile.exists()
if (usePermanentSigning) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "id.rama.todoplanner"
    compileSdk = 34

    defaultConfig {
        applicationId = "id.rama.todoplanner"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.0.1"
    }

    signingConfigs {
        if (usePermanentSigning) {
            create("permanent") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            if (usePermanentSigning) {
                signingConfig = signingConfigs.getByName("permanent")
            }
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (usePermanentSigning) {
                signingConfig = signingConfigs.getByName("permanent")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    // Room — database SQLite lokal (offline penuh)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

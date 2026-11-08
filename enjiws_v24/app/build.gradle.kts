plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.enjiws.music"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.enjiws.music"
        minSdk = 24
        targetSdk = 35
        versionCode = 24
        versionName = "2.4"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Dibutuhkan NewPipeExtractor (java.time dkk.) di Android < 8.
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("androidx.media3:media3-session:1.4.1")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    // Software FLAC decoder: improves FLAC playback on devices without native decoder support.
    implementation("androidx.media3:media3-decoder-flac:1.4.1")

    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.google.guava:guava:33.3.1-android")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.0.3")

    debugImplementation("androidx.compose.ui:ui-tooling")
}

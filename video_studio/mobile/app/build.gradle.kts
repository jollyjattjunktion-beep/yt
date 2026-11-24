plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.preetsports.studio"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.preetsports.studio"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        externalNativeBuild {
            cmake { cppFlags += listOf("-std=c++17", "-Wall", "-Wextra", "-Werror") }
        }
    }
    buildFeatures { compose = true }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    // PocketTTS Android inference runtime; pinned to the same ORT version as its C++ runtime.
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.23.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.fasterxml.jackson.core:jackson-core:2.18.2")
    implementation("org.jsoup:jsoup:1.18.3")
    // Maintained Android FFmpegKit continuation; LGPL build, pinned version.
    implementation("dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.pixelface.watch"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pixelface.watch"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Wear OS Compose
    implementation("androidx.wear.compose:compose-material:1.2.1")
    implementation("androidx.wear.compose:compose-foundation:1.2.1")
    implementation("androidx.wear.compose:compose-navigation:1.2.1")

    // Compose core
    implementation("androidx.compose.ui:ui:1.5.4")
    implementation("androidx.compose.ui:ui-tooling-preview:1.5.4")
    implementation("androidx.compose.runtime:runtime:1.5.4")
    implementation("androidx.compose.foundation:foundation:1.5.4")
    implementation("androidx.compose.material:material-icons-extended:1.5.4")

    // Activity + Lifecycle
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // Wear Data Layer
    implementation("com.google.android.gms:play-services-wearable:18.1.0")

    // Health Services (passive monitoring)
    implementation("androidx.health:health-services-client:1.1.0-alpha05")
    implementation("com.google.guava:guava:32.1.3-android")

    // Health Connect (removed per Fitbit decoupling plan)

    // WorkManager (for boot re-registration of passive listener)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Complications API (for serving pixel face to watch face)
    implementation("androidx.wear.watchface:watchface-complications-data-source:1.2.1")
    implementation("androidx.wear.watchface:watchface-complications-data:1.2.1")

    // Watch Face Service (Canvas renderer lives in same APK)
    implementation("androidx.wear.watchface:watchface:1.2.1")
    implementation("androidx.wear.watchface:watchface-style:1.2.1")
    implementation("androidx.wear.watchface:watchface-editor:1.2.1")
    implementation("androidx.wear.watchface:watchface-complications-rendering:1.2.1")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling:1.5.4")

    // Tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.3.1")
}

plugins {
    id("com.android.application")
}

android {
    namespace = "com.tamagotchi.wetpet.watchface.v2"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tamagotchi.wetpet.watchface.v2"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

// Pure WFF watch face — NO dependencies needed (resource-only APK)

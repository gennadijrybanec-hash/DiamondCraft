plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.craftengine.diamondcraft"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.craftengine.diamondcraft"
        minSdk = 26
        targetSdk = 36
        versionCode = 170
        versionName = "1.0-rc12"
    }

    signingConfigs {
        create("dev") {
            storeFile = file("diamondcraft-dev.keystore")
            storePassword = "diamondcraftdev"
            keyAlias = "diamondcraft-dev"
            keyPassword = "diamondcraftdev"
        }

        create("releaseUpload") {
            val keystorePath = System.getenv("DIAMONDCRAFT_KEYSTORE_PATH")
            if (!keystorePath.isNullOrBlank()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("DIAMONDCRAFT_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("DIAMONDCRAFT_KEY_ALIAS")
                keyPassword = System.getenv("DIAMONDCRAFT_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("dev")
        }
        getByName("release") {
            isMinifyEnabled = false
            if (!System.getenv("DIAMONDCRAFT_KEYSTORE_PATH").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("releaseUpload")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.compose.material3:material3:1.3.2")
    implementation("androidx.compose.ui:ui:1.7.8")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.8")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("com.android.billingclient:billing-ktx:9.1.0")
    debugImplementation("androidx.compose.ui:ui-tooling:1.7.8")
}

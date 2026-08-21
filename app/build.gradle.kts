plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.craftengine.diamondcraft"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.craftengine.diamondcraft"
        minSdk = 26
        targetSdk = 35
        versionCode = 120
        versionName = "1.0-rc2"
    }

    signingConfigs {
        create("dev") {
            storeFile = file("diamondcraft-dev.keystore")
            storePassword = "diamondcraftdev"
            keyAlias = "diamondcraft-dev"
            keyPassword = "diamondcraftdev"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("dev")
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
    implementation("androidx.compose.material3:material3:1.3.2")
    implementation("androidx.compose.ui:ui:1.7.8")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.8")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    debugImplementation("androidx.compose.ui:ui-tooling:1.7.8")
}

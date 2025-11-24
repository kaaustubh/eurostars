import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    id("kotlin-parcelize")
}

android {
    namespace = "com.sensars.eurostars"
    compileSdk {
        version = release(36)
    }

    splits {
        abi { isEnable = false }
        density { isEnable = false }
    }

    bundle {
        language {
            enableSplit = false
        }
    }

    defaultConfig {
        applicationId = "com.sensars.eurostars"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "0.0.5(5)"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        create("release") {
            val storeFileEnv = System.getenv("EUROSTARS_STORE_FILE")
            val storeFileProp = project.properties["EUROSTARS_STORE_FILE"]?.toString()
            storeFile = when {
                storeFileEnv != null -> file(storeFileEnv)
                storeFileProp != null -> file(storeFileProp)
                else -> file(rootProject.projectDir.resolve("eurostars-release.jks")) // Default to root directory
            }
            storePassword = System.getenv("EUROSTARS_STORE_PASSWORD") ?: project.properties["EUROSTARS_STORE_PASSWORD"].toString()
            keyAlias = System.getenv("EUROSTARS_KEY_ALIAS") ?: project.properties["EUROSTARS_KEY_ALIAS"].toString()
            keyPassword = System.getenv("EUROSTARS_KEY_PASSWORD") ?: project.properties["EUROSTARS_KEY_PASSWORD"].toString()
            enableV1Signing = true    // for older devices
            enableV2Signing = true    // required for modern devices
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            manifestPlaceholders["crashlyticsCollectionEnabled"] = "true"
            configure<CrashlyticsExtension> {
                // Disable mapping file upload since minification is disabled
                mappingFileUploadEnabled = false
                nativeSymbolUploadEnabled = false
            }

        }
        debug {
            manifestPlaceholders["crashlyticsCollectionEnabled"] = "false"
            configure<CrashlyticsExtension> {
                mappingFileUploadEnabled = false
                nativeSymbolUploadEnabled = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    // Compose
    implementation("androidx.compose.ui:ui:1.7.0")
    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("androidx.compose.material3:material3-window-size-class:1.3.0")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling:1.7.0")
    implementation("androidx.navigation:navigation-compose:2.8.0")

    // Lifecycle + Kotlin coroutines
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // DataStore (to persist selected role)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Timber logging
    implementation("com.jakewharton.timber:timber:5.0.1")

    // Firebase (ready for later epics)
    implementation(platform("com.google.firebase:firebase-bom:34.4.0"))
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-crashlytics")

    // Optional window size classes for tablet/phone responsive UI
    implementation("androidx.compose.material3:material3-window-size-class:1.3.0")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.activity:activity-ktx:1.9.2")
}

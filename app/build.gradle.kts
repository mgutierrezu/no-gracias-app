plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.gronorf.nogracias"
    compileSdk = 34  // Cambiado de 36 a 34 (más estable)

    defaultConfig {
        applicationId = "com.gronorf.nogracias"
        minSdk = 26  // Volver a 26 para evitar problemas con adaptive icons
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true  // Cambiado a true para optimizar
            isShrinkResources = true  // Agregar para reducir tamaño
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            isDebuggable = false  // Asegurar que no es debuggable
        }
        debug {
            isMinifyEnabled = false
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    // Eliminar buildFeatures compose (no lo usas)
    buildFeatures {
        compose = false
        buildConfig = true
    }

    // Configuración de firma (se añadirá después de crear keystore)
    signingConfigs {
        // release {
        //     storeFile file("path/to/keystore.jks")
        //     storePassword "your-keystore-password"
        //     keyAlias "your-key-alias"
        //     keyPassword "your-key-password"
        // }
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    // JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // Testing (solo para debug)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
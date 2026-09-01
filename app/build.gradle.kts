plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.mubashir.jarvis"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mubashir.jarvis"
        // Held up by :llama — see the note on its minSdk.
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"

        // Only ship the ABI the target device uses. llama.cpp native builds are
        // large, and every phone this app targets is arm64.
        ndk { abiFilters += "arm64-v8a" }
    }

    signingConfigs {
        // A fixed key, committed with the project, so every build installs over
        // the last one as a normal update. Without it AGP invents a new debug
        // key on each CI runner, the signatures differ, and updating means
        // uninstalling — which deletes the multi-gigabyte model with it.
        // Personal sideloaded app only; never reuse this for a store release.
        create("jarvis") {
            storeFile = file("keystore/jarvis.keystore")
            storePassword = "REMOVED-OLD-KEYSTORE-PASSWORD"
            keyAlias = "jarvis"
            keyPassword = "REMOVED-OLD-KEYSTORE-PASSWORD"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("jarvis")
        }
        release {
            signingConfig = signingConfigs.getByName("jarvis")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            // ggml dlopens its CPU backends by path out of the app's native
            // library directory. Left packaged, they stay inside the APK and
            // that directory is empty, so no backend is ever found and every
            // model fails to load.
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(project(":llama"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
}

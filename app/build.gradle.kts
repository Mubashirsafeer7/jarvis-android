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
        // CI passes the run number, so every build is orderable and the
        // updater can tell a newer APK from the one already installed. A local
        // build stays at 1 and claims to be a development build.
        versionCode = (project.findProperty("jarvisVersionCode") as String?)?.toInt() ?: 1
        versionName = (project.findProperty("jarvisVersionName") as String?) ?: "0.1-dev"

        // Only ship the ABI the target device uses. llama.cpp native builds are
        // large, and every phone this app targets is arm64.
        ndk { abiFilters += "arm64-v8a" }
    }

    // One key for every build, so an update installs over the last one instead
    // of demanding an uninstall — which would take the multi-gigabyte model with
    // it. AGP otherwise invents a fresh debug key on each CI runner.
    //
    // The key comes from a file CI writes out of a repository secret. Until that
    // secret exists the copy committed here is used, so nothing breaks in
    // between; once it does, the committed copy is removed from the repository
    // and its history, before any of this becomes public.
    val keystoreFromSecret = rootProject.file("release.keystore")
    val committedKeystore = file("keystore/jarvis.keystore")
    val signingKeystore = if (keystoreFromSecret.exists()) keystoreFromSecret else committedKeystore

    signingConfigs {
        create("jarvis") {
            storeFile = signingKeystore
            storePassword = (project.findProperty("jarvisKeystorePassword") as String?)
                ?: "REMOVED-OLD-KEYSTORE-PASSWORD"
            keyAlias = (project.findProperty("jarvisKeyAlias") as String?) ?: "jarvis"
            keyPassword = (project.findProperty("jarvisKeyPassword") as String?) ?: "REMOVED-OLD-KEYSTORE-PASSWORD"
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
        // The updater compares the running version against the newest release,
        // and with this off there is no BuildConfig class to read it from.
        buildConfig = true
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

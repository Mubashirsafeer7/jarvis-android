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
    // The key is written by CI out of a repository secret and is never in the
    // repository. The first one was committed here while the repo was private
    // and was still present when it went public, so it is gone and this is its
    // replacement: a new key, and a password that only ever exists as a secret
    // rather than as a literal in this file.
    //
    // Without the secret there is no signing config at all and AGP falls back to
    // its own debug key. That still builds and still runs; it just will not
    // install over a build signed with the real one, which is what the
    // fingerprint check in CI is there to catch.
    val releaseKeystore = rootProject.file("release.keystore")
    val keystorePassword: String? =
        (project.findProperty("jarvisKeystorePassword") as String?)
            ?: System.getenv("JARVIS_KEYSTORE_PASSWORD")

    signingConfigs {
        if (releaseKeystore.exists() && !keystorePassword.isNullOrBlank()) {
            create("jarvis") {
                storeFile = releaseKeystore
                storePassword = keystorePassword
                keyAlias = "jarvis"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        debug {
            signingConfigs.findByName("jarvis")?.let { signingConfig = it }
        }
        release {
            signingConfigs.findByName("jarvis")?.let { signingConfig = it }
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
    // The android.jar used for unit tests stubs org.json; without a real
    // implementation every JSONObject call throws "not mocked".
    testImplementation(libs.org.json)
}

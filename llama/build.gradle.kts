plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    // The JNI entry points in llama.cpp's Android example bind by name to
    // com.arm.aichat.internal.InferenceEngineImpl, so this module keeps that
    // package and consumes those sources directly from the submodule.
    namespace = "com.arm.aichat"
    compileSdk = 36
    ndkVersion = libs.versions.ndk.get()

    defaultConfig {
        // llama.cpp's Android logging header calls __android_log_is_loggable,
        // which the NDK marks as introduced in API 30. Anything lower fails to
        // compile rather than degrading at runtime.
        minSdk = 30
        ndk { abiFilters += "arm64-v8a" }

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DCMAKE_BUILD_TYPE=Release",
                    "-DBUILD_SHARED_LIBS=ON",
                    "-DLLAMA_BUILD_APP=OFF",
                    "-DLLAMA_BUILD_COMMON=ON",
                    "-DLLAMA_OPENSSL=OFF",
                    "-DGGML_NATIVE=OFF",
                    // Ship every CPU variant and pick one via dlopen at runtime,
                    // instead of baking in a single instruction-set baseline.
                    "-DGGML_BACKEND_DL=ON",
                    "-DGGML_CPU_ALL_VARIANTS=ON",
                    "-DGGML_LLAMAFILE=OFF",
                )
            }
        }
    }

    sourceSets["main"].java.srcDirs(
        "../third_party/llama.cpp/examples/llama.android/lib/src/main/java"
    )

    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
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
}

dependencies {
    // InferenceEngine exposes Flow in its public API, so consumers need this too.
    api(libs.kotlinx.coroutines.android)
}

cmake_minimum_required(VERSION 3.22.1)

project("ai-chat" LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED true)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED true)

# llama.cpp is vendored as a submodule pinned to a tag, and its Android example
# ships the JNI layer this module exposes to Kotlin. Build both from there
# rather than copying sources into this repo, so bumping the submodule is the
# only thing needed to move to a newer llama.cpp.
set(LLAMA_SRC ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/llama.cpp)
set(JNI_SRC ${LLAMA_SRC}/examples/llama.android/lib/src/main/cpp)

if(NOT EXISTS ${JNI_SRC}/ai_chat.cpp)
    message(FATAL_ERROR
        "llama.cpp submodule is missing. Run: git submodule update --init --recursive")
endif()

# Android 15 and later run with 16 KB memory pages. A shared library linked for
# 4 KB pages will not load on such a device at all, which includes every phone
# this app targets, so align everything — ggml's dlopen-ed CPU backends included.
add_link_options("-Wl,-z,max-page-size=16384")

if(DEFINED ANDROID_ABI)
    if(ANDROID_ABI STREQUAL "arm64-v8a")
        set(GGML_SYSTEM_ARCH "ARM")
        # KleidiAI supplies the i8mm/dotprod matmul kernels the target CPU has.
        set(GGML_CPU_KLEIDIAI ON)
        set(GGML_OPENMP ON)
    else()
        message(FATAL_ERROR "Unsupported ABI: ${ANDROID_ABI} (this app is arm64 only)")
    endif()
endif()

add_subdirectory(${LLAMA_SRC} build-llama)

add_library(${CMAKE_PROJECT_NAME} SHARED ${JNI_SRC}/ai_chat.cpp)

target_compile_definitions(${CMAKE_PROJECT_NAME} PRIVATE
        GGML_SYSTEM_ARCH=${GGML_SYSTEM_ARCH}
        GGML_CPU_KLEIDIAI=$<BOOL:${GGML_CPU_KLEIDIAI}>
        GGML_OPENMP=$<BOOL:${GGML_OPENMP}>)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        ${JNI_SRC}
        ${LLAMA_SRC}
        ${LLAMA_SRC}/common
        ${LLAMA_SRC}/include
        ${LLAMA_SRC}/ggml/include
        ${LLAMA_SRC}/ggml/src)

target_link_libraries(${CMAKE_PROJECT_NAME}
        llama
        llama-common
        android
        log)

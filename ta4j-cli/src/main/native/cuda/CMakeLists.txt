cmake_minimum_required(VERSION 3.28)
project(ta4j_cuda_accelerator LANGUAGES CXX CUDA)

if(NOT CMAKE_SIZEOF_VOID_P EQUAL 8)
    message(FATAL_ERROR "ta4j CUDA qualification requires x86_64")
endif()
if(NOT CMAKE_SYSTEM_PROCESSOR MATCHES "^(x86_64|AMD64|amd64)$")
    message(FATAL_ERROR "ta4j CUDA qualification requires x86_64, found ${CMAKE_SYSTEM_PROCESSOR}")
endif()
if(WIN32)
    set(JNI_PLATFORM_INCLUDE win32)
    set(NATIVE_RESOURCE_PLATFORM windows-x86_64)
elseif(UNIX AND NOT APPLE)
    set(JNI_PLATFORM_INCLUDE linux)
    set(NATIVE_RESOURCE_PLATFORM linux-x86_64)
else()
    message(FATAL_ERROR "ta4j CUDA qualification supports Windows and Linux x86_64 only")
endif()

if(NOT DEFINED JAVA_HOME)
    message(FATAL_ERROR "JAVA_HOME must identify the JDK used by Maven")
endif()
file(TO_CMAKE_PATH "${JAVA_HOME}" JAVA_HOME_NORMALIZED)
set(JNI_INCLUDE_DIRS "${JAVA_HOME_NORMALIZED}/include" "${JAVA_HOME_NORMALIZED}/include/${JNI_PLATFORM_INCLUDE}")
foreach(JNI_INCLUDE_DIR IN LISTS JNI_INCLUDE_DIRS)
    if(NOT EXISTS "${JNI_INCLUDE_DIR}/jni.h" AND JNI_INCLUDE_DIR STREQUAL "${JAVA_HOME_NORMALIZED}/include")
        message(FATAL_ERROR "jni.h was not found under ${JAVA_HOME_NORMALIZED}")
    endif()
endforeach()
find_package(CUDAToolkit 13.3 REQUIRED)

if(NOT DEFINED TA4J_CUDA_ARCHITECTURES)
    set(TA4J_CUDA_ARCHITECTURES "80-real;90-real;120-real;120-virtual")
endif()

add_library(ta4j-cuda-accelerator SHARED ta4j_cuda_jni.cu)
target_include_directories(ta4j-cuda-accelerator PRIVATE ${JNI_INCLUDE_DIRS})
target_link_libraries(ta4j-cuda-accelerator PRIVATE CUDA::cudart)
target_compile_features(ta4j-cuda-accelerator PRIVATE cxx_std_20)
target_compile_options(ta4j-cuda-accelerator PRIVATE
    $<$<COMPILE_LANGUAGE:CUDA>:--expt-relaxed-constexpr>
    $<$<AND:$<COMPILE_LANGUAGE:CUDA>,$<PLATFORM_ID:Windows>>:-Xcompiler=/Zc:preprocessor>)
set_target_properties(ta4j-cuda-accelerator PROPERTIES
    CUDA_ARCHITECTURES "${TA4J_CUDA_ARCHITECTURES}"
    CUDA_SEPARABLE_COMPILATION ON
    BUILD_RPATH_USE_ORIGIN ON
    INSTALL_RPATH "$ORIGIN"
    OUTPUT_NAME "ta4j-cuda-accelerator"
    RUNTIME_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/package/META-INF/native/${NATIVE_RESOURCE_PLATFORM}"
    RUNTIME_OUTPUT_DIRECTORY_RELEASE "${CMAKE_BINARY_DIR}/package/META-INF/native/${NATIVE_RESOURCE_PLATFORM}"
    LIBRARY_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/package/META-INF/native/${NATIVE_RESOURCE_PLATFORM}"
    LIBRARY_OUTPUT_DIRECTORY_RELEASE "${CMAKE_BINARY_DIR}/package/META-INF/native/${NATIVE_RESOURCE_PLATFORM}")

add_custom_command(TARGET ta4j-cuda-accelerator POST_BUILD
    COMMAND ${CMAKE_COMMAND}
        -DINPUT=$<TARGET_FILE:ta4j-cuda-accelerator>
        -DOUTPUT=$<TARGET_FILE:ta4j-cuda-accelerator>.sha256
        -P ${CMAKE_CURRENT_SOURCE_DIR}/write-checksum.cmake
    VERBATIM)

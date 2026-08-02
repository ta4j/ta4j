cmake_minimum_required(VERSION 3.28)
project(ta4j_cuda_accelerator LANGUAGES CXX CUDA)

if(NOT WIN32 OR NOT CMAKE_SIZEOF_VOID_P EQUAL 8)
    message(FATAL_ERROR "ta4j CUDA qualification currently supports Windows x86_64 only")
endif()

if(NOT DEFINED JAVA_HOME)
    message(FATAL_ERROR "JAVA_HOME must identify the JDK used by Maven")
endif()
file(TO_CMAKE_PATH "${JAVA_HOME}" JAVA_HOME_NORMALIZED)
set(JNI_INCLUDE_DIRS "${JAVA_HOME_NORMALIZED}/include" "${JAVA_HOME_NORMALIZED}/include/win32")
foreach(JNI_INCLUDE_DIR IN LISTS JNI_INCLUDE_DIRS)
    if(NOT EXISTS "${JNI_INCLUDE_DIR}/jni.h" AND JNI_INCLUDE_DIR STREQUAL "${JAVA_HOME_NORMALIZED}/include")
        message(FATAL_ERROR "jni.h was not found under ${JAVA_HOME_NORMALIZED}")
    endif()
endforeach()
find_package(CUDAToolkit 13.3 REQUIRED)

add_library(ta4j-cuda-accelerator SHARED ta4j_cuda_jni.cu)
target_include_directories(ta4j-cuda-accelerator PRIVATE ${JNI_INCLUDE_DIRS})
target_link_libraries(ta4j-cuda-accelerator PRIVATE CUDA::cudart)
target_compile_features(ta4j-cuda-accelerator PRIVATE cxx_std_20)
target_compile_options(ta4j-cuda-accelerator PRIVATE
    $<$<COMPILE_LANGUAGE:CUDA>:--expt-relaxed-constexpr>
    $<$<COMPILE_LANGUAGE:CUDA>:-Xcompiler=/Zc:preprocessor>)
set_target_properties(ta4j-cuda-accelerator PROPERTIES
    CUDA_ARCHITECTURES "120-real;120-virtual"
    CUDA_SEPARABLE_COMPILATION ON
    OUTPUT_NAME "ta4j-cuda-accelerator"
    RUNTIME_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/package/META-INF/native/windows-x86_64"
    RUNTIME_OUTPUT_DIRECTORY_RELEASE "${CMAKE_BINARY_DIR}/package/META-INF/native/windows-x86_64")

add_custom_command(TARGET ta4j-cuda-accelerator POST_BUILD
    COMMAND ${CMAKE_COMMAND}
        -DINPUT=$<TARGET_FILE:ta4j-cuda-accelerator>
        -DOUTPUT=$<TARGET_FILE:ta4j-cuda-accelerator>.sha256
        -P ${CMAKE_CURRENT_SOURCE_DIR}/write-checksum.cmake
    VERBATIM)

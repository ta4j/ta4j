cmake_minimum_required(VERSION 3.28)
project(ta4j_opencl_accelerator LANGUAGES C)

if(NOT CMAKE_SIZEOF_VOID_P EQUAL 8)
    message(FATAL_ERROR "ta4j OpenCL qualification requires a 64-bit target")
endif()
if(CMAKE_SYSTEM_PROCESSOR MATCHES "^(x86_64|AMD64|amd64)$")
    set(NATIVE_RESOURCE_PLATFORM linux-x86_64)
elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "^(aarch64|arm64|ARM64)$")
    set(NATIVE_RESOURCE_PLATFORM linux-aarch64)
else()
    message(FATAL_ERROR "ta4j OpenCL qualification requires Linux x86_64 or aarch64, found ${CMAKE_SYSTEM_PROCESSOR}")
endif()
if(DEFINED TA4J_EXPECTED_PLATFORM AND NOT TA4J_EXPECTED_PLATFORM STREQUAL NATIVE_RESOURCE_PLATFORM)
    message(FATAL_ERROR "ta4j OpenCL profile expects ${TA4J_EXPECTED_PLATFORM} resources, but this build host targets ${NATIVE_RESOURCE_PLATFORM}")
endif()
if(WIN32 OR APPLE)
    message(FATAL_ERROR "ta4j OpenCL qualification supports Linux only")
endif()

if(NOT DEFINED JAVA_HOME)
    message(FATAL_ERROR "JAVA_HOME must identify the JDK used by Maven")
endif()
file(TO_CMAKE_PATH "${JAVA_HOME}" JAVA_HOME_NORMALIZED)
set(JNI_INCLUDE_DIRS "${JAVA_HOME_NORMALIZED}/include" "${JAVA_HOME_NORMALIZED}/include/linux")
foreach(JNI_INCLUDE_DIR IN LISTS JNI_INCLUDE_DIRS)
    if(NOT EXISTS "${JNI_INCLUDE_DIR}/jni.h" AND JNI_INCLUDE_DIR STREQUAL "${JAVA_HOME_NORMALIZED}/include")
        message(FATAL_ERROR "jni.h was not found under ${JAVA_HOME_NORMALIZED}")
    endif()
endforeach()
find_package(OpenCL REQUIRED)

add_library(ta4j-opencl-accelerator SHARED ta4j_opencl_jni.c)
target_include_directories(ta4j-opencl-accelerator PRIVATE ${JNI_INCLUDE_DIRS})
target_link_libraries(ta4j-opencl-accelerator PRIVATE OpenCL::OpenCL pthread)
target_compile_features(ta4j-opencl-accelerator PRIVATE c_std_11)
set_target_properties(ta4j-opencl-accelerator PROPERTIES
    BUILD_RPATH_USE_ORIGIN ON
    INSTALL_RPATH "$ORIGIN"
    OUTPUT_NAME "ta4j-opencl-accelerator"
    RUNTIME_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/package/META-INF/native/${NATIVE_RESOURCE_PLATFORM}"
    RUNTIME_OUTPUT_DIRECTORY_RELEASE "${CMAKE_BINARY_DIR}/package/META-INF/native/${NATIVE_RESOURCE_PLATFORM}"
    LIBRARY_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/package/META-INF/native/${NATIVE_RESOURCE_PLATFORM}"
    LIBRARY_OUTPUT_DIRECTORY_RELEASE "${CMAKE_BINARY_DIR}/package/META-INF/native/${NATIVE_RESOURCE_PLATFORM}")

add_custom_command(TARGET ta4j-opencl-accelerator POST_BUILD
    COMMAND ${CMAKE_COMMAND}
        -DINPUT=$<TARGET_FILE:ta4j-opencl-accelerator>
        -DOUTPUT=$<TARGET_FILE:ta4j-opencl-accelerator>.sha256
        -P ${CMAKE_CURRENT_SOURCE_DIR}/write-checksum.cmake
    VERBATIM)

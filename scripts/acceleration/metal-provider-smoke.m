#import <Foundation/Foundation.h>
#import <Metal/Metal.h>
#include <jni.h>
#include <stdlib.h>
#include <string.h>

const char *ta4j_metal_device_name(void) {
    @autoreleasepool {
        id<MTLDevice> device = MTLCreateSystemDefaultDevice();
        if (device == nil) {
            return strdup("");
        }
        return strdup([[device name] UTF8String]);
    }
}

int ta4j_metal_self_test(void) {
    @autoreleasepool {
        id<MTLDevice> device = MTLCreateSystemDefaultDevice();
        return device == nil ? 0 : 1;
    }
}

JNIEXPORT jstring JNICALL Java_org_ta4j_acceleration_internal_providers_MetalAccelerationProviderFactory_nativeDeviceName(
        JNIEnv *environment, jclass type) {
    (void)type;
    char *device_name = (char *)ta4j_metal_device_name();
    jstring result = (*environment)->NewStringUTF(environment, device_name == NULL ? "" : device_name);
    free(device_name);
    return result;
}

JNIEXPORT jboolean JNICALL Java_org_ta4j_acceleration_internal_providers_MetalAccelerationProviderFactory_nativeSelfTest(
        JNIEnv *environment, jclass type) {
    (void)environment;
    (void)type;
    return ta4j_metal_self_test() == 1 ? JNI_TRUE : JNI_FALSE;
}

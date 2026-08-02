/*
 * SPDX-License-Identifier: MIT
 */

#import <Foundation/Foundation.h>
#import <Metal/Metal.h>
#include <jni.h>
#include <math.h>
#include <time.h>

static const jint ABI_VERSION = 1;
static id<MTLDevice> device;
static id<MTLComputePipelineState> forecastPipeline;
static id<MTLComputePipelineState> selfTestPipeline;
static id<MTLCommandQueue> commandQueue;
static NSString *initializationFailure;

static NSString *const kernelSource = @"#include <metal_stdlib>\n"
        "using namespace metal;\n"
        "ulong mix64(ulong value) {\n"
        "    value = (value ^ (value >> 30)) * 0xBF58476D1CE4E5B9UL;\n"
        "    value = (value ^ (value >> 27)) * 0x94D049BB133111EBUL;\n"
        "    return value ^ (value >> 31);\n"
        "}\n"
        "ulong path_state(ulong seed, uint decision, uint horizon, uint path) {\n"
        "    ulong value = seed;\n"
        "    value = mix64(value ^ (ulong(decision) * 0xD1B54A32D192ED03UL));\n"
        "    value = mix64(value ^ (ulong(horizon) * 0x94D049BB133111EBUL));\n"
        "    return mix64(value ^ (ulong(path) * 0xDB4F0B9175AE2165UL));\n"
        "}\n"
        "ulong next_long(thread ulong &state) {\n"
        "    state += 0x9E3779B97F4A7C15UL;\n"
        "    return mix64(state);\n"
        "}\n"
        "kernel void acceleration_self_test(device ulong *output [[buffer(0)]],\n"
        "        uint position [[thread_position_in_grid]]) {\n"
        "    if (position == 0u) output[0] = mix64(42UL);\n"
        "}\n"
        "uint next_index(thread ulong &state, uint bound) {\n"
        "    ulong candidate = next_long(state) >> 1;\n"
        "    ulong remainder = candidate % ulong(bound);\n"
        "    while (candidate - remainder + ulong(bound) - 1UL > 0x7FFFFFFFFFFFFFFFUL) {\n"
        "        candidate = next_long(state) >> 1;\n"
        "        remainder = candidate % ulong(bound);\n"
        "    }\n"
        "    return uint(remainder);\n"
        "}\n"
        "float next_unit(thread ulong &state) {\n"
        "    return float(next_long(state) >> 11) * 0x1.0p-53f;\n"
        "}\n"
        "float next_gaussian(thread ulong &state) {\n"
        "    float radius = sqrt(-2.0f * log(max(0x1.0p-24f, 1.0f - next_unit(state))));\n"
        "    return radius * cos(6.283185307179586f * next_unit(state));\n"
        "}\n"
        "kernel void forecast_terminal_prices(\n"
        "        device const int *stable [[buffer(0)]],\n"
        "        device const float *prices [[buffer(1)]],\n"
        "        device const float *means [[buffer(2)]],\n"
        "        device const float *drifts [[buffer(3)]],\n"
        "        device const float *variances [[buffer(4)]],\n"
        "        device const float *history [[buffer(5)]],\n"
        "        device float *output [[buffer(6)]],\n"
        "        constant uint &fromIndex [[buffer(7)]],\n"
        "        constant uint &decisionCount [[buffer(8)]],\n"
        "        constant uint &pathCount [[buffer(9)]],\n"
        "        constant uint &horizon [[buffer(10)]],\n"
        "        constant uint &lookback [[buffer(11)]],\n"
        "        constant ulong &seed [[buffer(12)]],\n"
        "        constant uint &shockModel [[buffer(13)]],\n"
        "        constant uint &volatilityMode [[buffer(14)]],\n"
        "        constant float &decay [[buffer(15)]],\n"
        "        uint position [[thread_position_in_grid]]) {\n"
        "    uint cellCount = decisionCount * pathCount;\n"
        "    if (position >= cellCount) return;\n"
        "    uint decision = position / pathCount;\n"
        "    uint path = position - decision * pathCount;\n"
        "    if (stable[decision] == 0) { output[position] = NAN; return; }\n"
        "    float mean = means[decision];\n"
        "    float drift = drifts[decision];\n"
        "    float variance = max(0.0f, variances[decision]);\n"
        "    float volatility = sqrt(variance);\n"
        "    float samplingMean = mean;\n"
        "    float samplingVolatility = volatility;\n"
        "    float cumulative = 0.0f;\n"
        "    ulong state = path_state(seed, fromIndex + decision, horizon, path);\n"
        "    for (uint step = 0; step < horizon; step++) {\n"
        "        float stepReturn;\n"
        "        if (shockModel == 2u) {\n"
        "            stepReturn = drift + volatility * next_gaussian(state);\n"
        "        } else {\n"
        "            uint sample = next_index(state, lookback);\n"
        "            float historical = history[decision * lookback + sample];\n"
        "            if (shockModel == 0u) {\n"
        "                stepReturn = historical;\n"
        "            } else {\n"
        "                float shock = samplingVolatility == 0.0f ? 0.0f\n"
        "                        : (historical - samplingMean) / samplingVolatility;\n"
        "                stepReturn = drift + volatility * shock;\n"
        "            }\n"
        "        }\n"
        "        cumulative += stepReturn;\n"
        "        if (volatilityMode == 1u) {\n"
        "            float deviation = stepReturn - mean;\n"
        "            mean = mean * decay + stepReturn * (1.0f - decay);\n"
        "            variance = variance * decay + deviation * deviation * (1.0f - decay);\n"
        "            volatility = sqrt(max(0.0f, variance));\n"
        "        }\n"
        "    }\n"
        "    float terminalPrice = prices[decision] * exp(cumulative);\n"
        "    output[position] = isfinite(terminalPrice) && terminalPrice > 0.0f ? terminalPrice : NAN;\n"
        "}\n";

static uint64_t nowNanos(void) {
    return clock_gettime_nsec_np(CLOCK_UPTIME_RAW);
}

static void initializeMetal(void) {
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        device = MTLCreateSystemDefaultDevice();
        if (device == nil) {
            initializationFailure = @"metal_device_unavailable";
            return;
        }
        NSError *error = nil;
        id<MTLLibrary> library = [device newLibraryWithSource:kernelSource options:nil error:&error];
        if (library == nil) {
            initializationFailure = [@"metal_library_compile_failed:"
                    stringByAppendingString:error.localizedDescription ?: @"unknown"];
            return;
        }
        id<MTLFunction> function = [library newFunctionWithName:@"forecast_terminal_prices"];
        forecastPipeline = [device newComputePipelineStateWithFunction:function error:&error];
        if (forecastPipeline == nil) {
            initializationFailure = [@"metal_pipeline_creation_failed:"
                    stringByAppendingString:error.localizedDescription ?: @"unknown"];
            return;
        }
        id<MTLFunction> selfTestFunction = [library newFunctionWithName:@"acceleration_self_test"];
        selfTestPipeline = [device newComputePipelineStateWithFunction:selfTestFunction error:&error];
        if (selfTestPipeline == nil) {
            initializationFailure = [@"metal_self_test_pipeline_creation_failed:"
                    stringByAppendingString:error.localizedDescription ?: @"unknown"];
            return;
        }
        commandQueue = [device newCommandQueue];
        if (commandQueue == nil) {
            initializationFailure = @"metal_command_queue_unavailable";
            return;
        }
        id<MTLBuffer> selfTestBuffer = [device newBufferWithLength:sizeof(uint64_t)
                options:MTLResourceStorageModeShared];
        id<MTLCommandBuffer> selfTestCommand = [commandQueue commandBuffer];
        id<MTLComputeCommandEncoder> selfTestEncoder = [selfTestCommand computeCommandEncoder];
        if (selfTestBuffer == nil || selfTestCommand == nil || selfTestEncoder == nil) {
            initializationFailure = @"metal_self_test_allocation_failed";
            return;
        }
        [selfTestEncoder setComputePipelineState:selfTestPipeline];
        [selfTestEncoder setBuffer:selfTestBuffer offset:0 atIndex:0];
        [selfTestEncoder dispatchThreads:MTLSizeMake(1, 1, 1) threadsPerThreadgroup:MTLSizeMake(1, 1, 1)];
        [selfTestEncoder endEncoding];
        [selfTestCommand commit];
        [selfTestCommand waitUntilCompleted];
        if (selfTestCommand.status == MTLCommandBufferStatusError
                || *((uint64_t *)selfTestBuffer.contents) != 0xA759EA27D4727622ULL) {
            initializationFailure = @"metal_device_self_test_failed";
        }
    });
}

static void throwIllegalState(JNIEnv *environment, NSString *message) {
    if ((*environment)->ExceptionCheck(environment)) {
        return;
    }
    jclass exceptionClass = (*environment)->FindClass(environment, "java/lang/IllegalStateException");
    if (exceptionClass != nil) {
        (*environment)->ThrowNew(environment, exceptionClass, message.UTF8String);
    }
}

static id<MTLBuffer> floatBuffer(JNIEnv *environment, jdoubleArray source, jsize count) {
    if (source == nil) {
        throwIllegalState(environment, @"metal_forecast_null_input");
        return nil;
    }
    id<MTLBuffer> buffer = [device newBufferWithLength:(NSUInteger)count * sizeof(float)
            options:MTLResourceStorageModeShared];
    if (buffer == nil) {
        return nil;
    }
    jdouble *values = (*environment)->GetDoubleArrayElements(environment, source, nil);
    if (values == nil) {
        return nil;
    }
    float *target = buffer.contents;
    for (jsize index = 0; index < count; index++) {
        target[index] = (float)values[index];
    }
    (*environment)->ReleaseDoubleArrayElements(environment, source, values, JNI_ABORT);
    return buffer;
}

JNIEXPORT jstring JNICALL
Java_org_ta4j_cli_acceleration_internal_providers_JniMetalNativeBridge_nativeProbe(
        JNIEnv *environment, jclass type, jint abiVersion) {
    @autoreleasepool {
        (void)type;
        if (abiVersion != ABI_VERSION) {
            return (*environment)->NewStringUTF(environment, "ERROR|||abi_mismatch");
        }
        initializeMetal();
        if (initializationFailure != nil) {
            NSString *failure = [NSString stringWithFormat:@"ERROR|||%@", initializationFailure];
            return (*environment)->NewStringUTF(environment, failure.UTF8String);
        }
        NSString *payload = [NSString stringWithFormat:@"OK|%@|%llu|ready", device.name,
                (unsigned long long)device.recommendedMaxWorkingSetSize];
        return (*environment)->NewStringUTF(environment, payload.UTF8String);
    }
}

JNIEXPORT jfloatArray JNICALL
Java_org_ta4j_cli_acceleration_internal_providers_JniMetalNativeBridge_nativeEvaluate(
        JNIEnv *environment, jclass type, jint abiVersion, jint fromInclusive, jint decisionCount, jint horizon,
        jint iterationCount, jint lookbackBarCount, jlong seed, jint shockModel, jint volatilityMode,
        jdouble volatilityDecayFactor, jintArray stableArray, jdoubleArray pricesArray, jdoubleArray meansArray,
        jdoubleArray driftsArray, jdoubleArray variancesArray, jdoubleArray historyArray, jlongArray timingsArray) {
    @autoreleasepool {
        (void)type;
        uint64_t totalStarted = nowNanos();
        initializeMetal();
        if (initializationFailure != nil) {
            throwIllegalState(environment, initializationFailure);
            return nil;
        }
        if (abiVersion != ABI_VERSION || fromInclusive < 0 || decisionCount <= 0 || horizon <= 0
                || iterationCount <= 0 || lookbackBarCount <= 0 || shockModel < 0 || shockModel > 2
                || volatilityMode < 0 || volatilityMode > 1 || volatilityDecayFactor <= 0.0
                || volatilityDecayFactor >= 1.0) {
            throwIllegalState(environment, @"invalid_metal_forecast_request");
            return nil;
        }
        if (stableArray == nil || pricesArray == nil || meansArray == nil || driftsArray == nil
                || variancesArray == nil || historyArray == nil || timingsArray == nil) {
            throwIllegalState(environment, @"metal_forecast_null_input");
            return nil;
        }
        NSUInteger cellCount = (NSUInteger)decisionCount * (NSUInteger)iterationCount;
        NSUInteger historyCount = (NSUInteger)decisionCount * (NSUInteger)lookbackBarCount;
        if (cellCount > INT32_MAX || historyCount > INT32_MAX
                || (*environment)->GetArrayLength(environment, stableArray) != decisionCount
                || (*environment)->GetArrayLength(environment, pricesArray) != decisionCount
                || (*environment)->GetArrayLength(environment, meansArray) != decisionCount
                || (*environment)->GetArrayLength(environment, driftsArray) != decisionCount
                || (*environment)->GetArrayLength(environment, variancesArray) != decisionCount
                || (*environment)->GetArrayLength(environment, historyArray) != (jsize)historyCount
                || (*environment)->GetArrayLength(environment, timingsArray) < 3) {
            throwIllegalState(environment, @"metal_forecast_input_size_mismatch");
            return nil;
        }

        uint64_t transferStarted = nowNanos();
        id<MTLBuffer> stableBuffer = [device newBufferWithLength:(NSUInteger)decisionCount * sizeof(jint)
                options:MTLResourceStorageModeShared];
        id<MTLBuffer> priceBuffer = floatBuffer(environment, pricesArray, decisionCount);
        id<MTLBuffer> meanBuffer = floatBuffer(environment, meansArray, decisionCount);
        id<MTLBuffer> driftBuffer = floatBuffer(environment, driftsArray, decisionCount);
        id<MTLBuffer> varianceBuffer = floatBuffer(environment, variancesArray, decisionCount);
        id<MTLBuffer> historyBuffer = floatBuffer(environment, historyArray, (jsize)historyCount);
        id<MTLBuffer> outputBuffer = [device newBufferWithLength:cellCount * sizeof(float)
                options:MTLResourceStorageModeShared];
        if (stableBuffer == nil || priceBuffer == nil || meanBuffer == nil || driftBuffer == nil
                || varianceBuffer == nil || historyBuffer == nil || outputBuffer == nil) {
            if ((*environment)->ExceptionCheck(environment)) {
                return nil;
            }
            throwIllegalState(environment, @"metal_forecast_buffer_allocation_failed");
            return nil;
        }
        (*environment)->GetIntArrayRegion(environment, stableArray, 0, decisionCount, stableBuffer.contents);
        if ((*environment)->ExceptionCheck(environment)) {
            return nil;
        }
        uint64_t transferNanos = nowNanos() - transferStarted;

        uint32_t nativeFrom = (uint32_t)fromInclusive;
        uint32_t nativeDecisions = (uint32_t)decisionCount;
        uint32_t nativePaths = (uint32_t)iterationCount;
        uint32_t nativeHorizon = (uint32_t)horizon;
        uint32_t nativeLookback = (uint32_t)lookbackBarCount;
        uint64_t nativeSeed = (uint64_t)seed;
        uint32_t nativeShock = (uint32_t)shockModel;
        uint32_t nativeVolatility = (uint32_t)volatilityMode;
        float nativeDecay = (float)volatilityDecayFactor;

        uint64_t kernelStarted = nowNanos();
        id<MTLCommandBuffer> commandBuffer = [commandQueue commandBuffer];
        id<MTLComputeCommandEncoder> encoder = [commandBuffer computeCommandEncoder];
        if (commandBuffer == nil || encoder == nil) {
            throwIllegalState(environment, @"metal_forecast_command_encoding_failed");
            return nil;
        }
        [encoder setComputePipelineState:forecastPipeline];
        [encoder setBuffer:stableBuffer offset:0 atIndex:0];
        [encoder setBuffer:priceBuffer offset:0 atIndex:1];
        [encoder setBuffer:meanBuffer offset:0 atIndex:2];
        [encoder setBuffer:driftBuffer offset:0 atIndex:3];
        [encoder setBuffer:varianceBuffer offset:0 atIndex:4];
        [encoder setBuffer:historyBuffer offset:0 atIndex:5];
        [encoder setBuffer:outputBuffer offset:0 atIndex:6];
        [encoder setBytes:&nativeFrom length:sizeof(nativeFrom) atIndex:7];
        [encoder setBytes:&nativeDecisions length:sizeof(nativeDecisions) atIndex:8];
        [encoder setBytes:&nativePaths length:sizeof(nativePaths) atIndex:9];
        [encoder setBytes:&nativeHorizon length:sizeof(nativeHorizon) atIndex:10];
        [encoder setBytes:&nativeLookback length:sizeof(nativeLookback) atIndex:11];
        [encoder setBytes:&nativeSeed length:sizeof(nativeSeed) atIndex:12];
        [encoder setBytes:&nativeShock length:sizeof(nativeShock) atIndex:13];
        [encoder setBytes:&nativeVolatility length:sizeof(nativeVolatility) atIndex:14];
        [encoder setBytes:&nativeDecay length:sizeof(nativeDecay) atIndex:15];
        MTLSize grid = MTLSizeMake(cellCount, 1, 1);
        MTLSize threads = MTLSizeMake(MIN(forecastPipeline.maxTotalThreadsPerThreadgroup, cellCount), 1, 1);
        [encoder dispatchThreads:grid threadsPerThreadgroup:threads];
        [encoder endEncoding];
        [commandBuffer commit];
        [commandBuffer waitUntilCompleted];
        if (commandBuffer.status == MTLCommandBufferStatusError) {
            NSString *message = [@"metal_forecast_command_failed:"
                    stringByAppendingString:commandBuffer.error.localizedDescription ?: @"unknown"];
            throwIllegalState(environment, message);
            return nil;
        }
        uint64_t kernelNanos = nowNanos() - kernelStarted;

        transferStarted = nowNanos();
        jfloatArray result = (*environment)->NewFloatArray(environment, (jsize)cellCount);
        if (result == nil) {
            return nil;
        }
        (*environment)->SetFloatArrayRegion(environment, result, 0, (jsize)cellCount, outputBuffer.contents);
        transferNanos += nowNanos() - transferStarted;
        jlong timings[] = {(jlong)((nowNanos() - totalStarted) / 1000ULL),
                (jlong)(transferNanos / 1000ULL), (jlong)(kernelNanos / 1000ULL)};
        (*environment)->SetLongArrayRegion(environment, timingsArray, 0, 3, timings);
        return result;
    }
}

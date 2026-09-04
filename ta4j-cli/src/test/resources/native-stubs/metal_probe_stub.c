/*
 * SPDX-License-Identifier: MIT
 *
 * Test-only JNI stub for JniMetalNativeBridge. It serves the payload stored
 * in /tmp/ta4j-metal-probe-payload.txt on every nativeProbe call so tests can
 * drive the real Java-side probe parsing with arbitrary hostile payloads. The
 * nativeEvaluate symbol must exist so the class verifies against this library,
 * but it is never called by the tests.
 */
#include <jni.h>

#include <stdio.h>

static jstring payload(JNIEnv* environment) {
    FILE* file = fopen("/tmp/ta4j-metal-probe-payload.txt", "r");
    if (file == NULL) {
        return (*environment)->NewStringUTF(environment, "ERROR|||stub payload file missing");
    }
    char buffer[4096];
    size_t length = fread(buffer, 1, sizeof(buffer) - 1, file);
    fclose(file);
    buffer[length] = '\0';
    return (*environment)->NewStringUTF(environment, buffer);
}

JNIEXPORT jstring JNICALL
Java_org_ta4j_cli_acceleration_internal_providers_JniMetalNativeBridge_nativeProbe(JNIEnv* environment, jclass,
                                                                                   jint abi_version) {
    (void)abi_version;
    return payload(environment);
}

JNIEXPORT jfloatArray JNICALL
Java_org_ta4j_cli_acceleration_internal_providers_JniMetalNativeBridge_nativeEvaluate(
        JNIEnv* environment, jclass, jint abi_version, jint from_inclusive, jint decision_count, jint horizon,
        jint iteration_count, jint lookback, jlong seed, jint shock_model, jint volatility_mode, jdouble decay,
        jintArray stable_array, jdoubleArray prices_array, jdoubleArray means_array, jdoubleArray drifts_array,
        jdoubleArray variances_array, jdoubleArray historical_returns_array, jlongArray timings_array) {
    (void)environment;
    (void)abi_version;
    (void)from_inclusive;
    (void)decision_count;
    (void)horizon;
    (void)iteration_count;
    (void)lookback;
    (void)seed;
    (void)shock_model;
    (void)volatility_mode;
    (void)decay;
    (void)stable_array;
    (void)prices_array;
    (void)means_array;
    (void)drifts_array;
    (void)variances_array;
    (void)historical_returns_array;
    (void)timings_array;
    return NULL;
}

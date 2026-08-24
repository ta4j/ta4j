/*
 * SPDX-License-Identifier: MIT
 */
#include <CL/cl.h>
#include <jni.h>

#include <float.h>
#include <limits.h>
#include <math.h>
#include <pthread.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#define ABI_VERSION 1
#define GOLDEN_GAMMA 0x9E3779B97F4A7C15ULL
#define DOUBLE_UNIT 0x1.0p-53
#define STATE_ERROR_BUFFER 512
// Mirrors OpenClCrossoverModel.QUALIFIED_MINIMUM_DEVICE_BYTES.
#define QUALIFIED_MINIMUM_DEVICE_BYTES 2147483648ULL

static const char KERNEL_SOURCE[] =
        "#pragma OPENCL EXTENSION cl_khr_fp64 : enable\n"
        "\n"
        "ulong mix64(ulong value) {\n"
        "    value = (value ^ (value >> 30)) * 0xBF58476D1CE4E5B9ULL;\n"
        "    value = (value ^ (value >> 27)) * 0x94D049BB133111EBULL;\n"
        "    return value ^ (value >> 31);\n"
        "}\n"
        "\n"
        "typedef struct {\n"
        "    ulong state;\n"
        "} path_random;\n"
        "\n"
        "void path_random_init(path_random* random, long seed, int decision_index, int horizon, int path_index) {\n"
        "    ulong value = (ulong)seed;\n"
        "    value = mix64(value ^ ((ulong)(uint)decision_index) * 0xD1B54A32D192ED03ULL);\n"
        "    value = mix64(value ^ ((ulong)(uint)horizon) * 0x94D049BB133111EBULL);\n"
        "    random->state = mix64(value ^ ((ulong)(uint)path_index) * 0xDB4F0B9175AE2165ULL);\n"
        "}\n"
        "\n"
        "ulong next_long(path_random* random) {\n"
        "    random->state += 0x9E3779B97F4A7C15ULL;\n"
        "    return mix64(random->state);\n"
        "}\n"
        "\n"
        "double next_double(path_random* random) {\n"
        "    return (double)(next_long(random) >> 11) * 0x1.0p-53;\n"
        "}\n"
        "\n"
        "int next_int(path_random* random, int bound) {\n"
        "    while (1) {\n"
        "        ulong candidate = next_long(random) >> 1;\n"
        "        ulong remainder = candidate % (ulong)bound;\n"
        "        ulong sum = candidate - remainder + (ulong)(bound - 1);\n"
        "        if (sum <= 0x7FFFFFFFFFFFFFFFULL) {\n"
        "            return (int)remainder;\n"
        "        }\n"
        "    }\n"
        "}\n"
        "\n"
        "double next_gaussian(path_random* random) {\n"
        "    double radius = sqrt(-2.0 * log(1.0 - next_double(random)));\n"
        "    return radius * cos(2.0 * 3.141592653589793238462643383279502884 * next_double(random));\n"
        "}\n"
        "\n"
        "__kernel void path_kernel(double price, double mean, double drift, double variance,\n"
        "                          __global const double* historical_returns, int lookback, int history_row,\n"
        "                          int decision_index, int horizon, int iteration_count, long seed,\n"
        "                          int shock_model, int volatility_mode, double decay, __global double* samples,\n"
        "                          __global int* status) {\n"
        "    int path_index = get_global_id(0);\n"
        "    if (path_index >= iteration_count) {\n"
        "        return;\n"
        "    }\n"
        "    path_random random;\n"
        "    path_random_init(&random, seed, decision_index, horizon, path_index);\n"
        "    double current_mean = mean;\n"
        "    double current_variance = variance;\n"
        "    double volatility = sqrt(variance);\n"
        "    double standardized_mean = mean;\n"
        "    double standardized_volatility = volatility;\n"
        "    double cumulative_return = 0.0;\n"
        "    for (int step = 0; step < horizon; ++step) {\n"
        "        double shock;\n"
        "        if (shock_model == 0) {\n"
        "            shock = historical_returns[history_row * lookback + next_int(&random, lookback)];\n"
        "        } else if (shock_model == 1) {\n"
        "            shock = standardized_volatility == 0.0\n"
        "                    ? 0.0\n"
        "                    : (historical_returns[history_row * lookback + next_int(&random, lookback)]\n"
        "                            - standardized_mean) / standardized_volatility;\n"
        "        } else {\n"
        "            shock = next_gaussian(&random);\n"
        "        }\n"
        "        double step_return = shock_model == 0 ? shock : drift + volatility * shock;\n"
        "        cumulative_return += step_return;\n"
        "        if (volatility_mode == 1) {\n"
        "            double deviation = step_return - current_mean;\n"
        "            current_mean = current_mean * decay + step_return * (1.0 - decay);\n"
        "            current_variance = current_variance * decay + deviation * deviation * (1.0 - decay);\n"
        "            volatility = sqrt(current_variance);\n"
        "        }\n"
        "    }\n"
        "    double growth = exp(cumulative_return);\n"
        "    double terminal = price * growth;\n"
        "    if (!isfinite(cumulative_return) || fabs(cumulative_return) > 700.0 || !isfinite(growth)\n"
        "            || !isfinite(terminal) || (terminal == 0.0 && growth != 0.0)) {\n"
        "        atomic_xchg(status, 2);\n"
        "        samples[path_index] = 0.0;\n"
        "        return;\n"
        "    }\n"
        "    samples[path_index] = terminal;\n"
        "}\n"
        "\n"
        "#define MOMENT_THREADS 256\n"
        "\n"
        "__kernel void moments_partial_kernel(__global const double* samples, int count,\n"
        "                                     __global double* partial_sums, __global double* partial_sqsums,\n"
        "                                     __global int* status) {\n"
        "    __local double block_sum[MOMENT_THREADS];\n"
        "    __local double block_sqsum[MOMENT_THREADS];\n"
        "    size_t lid = get_local_id(0);\n"
        "    size_t lsize = get_local_size(0);\n"
        "    double shift = samples[0];\n"
        "    double local_sum = 0.0;\n"
        "    double local_sqsum = 0.0;\n"
        "    for (int index = get_group_id(0) * (int)lsize + (int)lid; index < count;\n"
        "            index += get_num_groups(0) * (int)lsize) {\n"
        "        double value = samples[index];\n"
        "        if (!isfinite(value)) {\n"
        "            atomic_xchg(status, 2);\n"
        "            value = shift;\n"
        "        }\n"
        "        double centered = value - shift;\n"
        "        local_sum += centered;\n"
        "        local_sqsum += centered * centered;\n"
        "    }\n"
        "    block_sum[lid] = local_sum;\n"
        "    block_sqsum[lid] = local_sqsum;\n"
        "    barrier(CLK_LOCAL_MEM_FENCE);\n"
        "    for (size_t offset = lsize / 2; offset > 0; offset >>= 1) {\n"
        "        if (lid < offset) {\n"
        "            block_sum[lid] += block_sum[lid + offset];\n"
        "            block_sqsum[lid] += block_sqsum[lid + offset];\n"
        "        }\n"
        "        barrier(CLK_LOCAL_MEM_FENCE);\n"
        "    }\n"
        "    if (lid == 0) {\n"
        "        partial_sums[get_group_id(0)] = block_sum[0];\n"
        "        partial_sqsums[get_group_id(0)] = block_sqsum[0];\n"
        "    }\n"
        "}\n"
        "\n"
        "__kernel void moments_finalize_kernel(__global const double* partial_sums,\n"
        "                                      __global const double* partial_sqsums, int block_count, int count,\n"
        "                                      __global const double* samples, __global double* summary,\n"
        "                                      __global int* status) {\n"
        "    __local double sum_shared[MOMENT_THREADS];\n"
        "    __local double sqsum_shared[MOMENT_THREADS];\n"
        "    size_t lid = get_local_id(0);\n"
        "    size_t lsize = get_local_size(0);\n"
        "    double local_sum = 0.0;\n"
        "    double local_sqsum = 0.0;\n"
        "    for (int index = (int)lid; index < block_count; index += (int)lsize) {\n"
        "        local_sum += partial_sums[index];\n"
        "        local_sqsum += partial_sqsums[index];\n"
        "    }\n"
        "    sum_shared[lid] = local_sum;\n"
        "    sqsum_shared[lid] = local_sqsum;\n"
        "    barrier(CLK_LOCAL_MEM_FENCE);\n"
        "    for (size_t offset = lsize / 2; offset > 0; offset >>= 1) {\n"
        "        if (lid < offset) {\n"
        "            sum_shared[lid] += sum_shared[lid + offset];\n"
        "            sqsum_shared[lid] += sqsum_shared[lid + offset];\n"
        "        }\n"
        "        barrier(CLK_LOCAL_MEM_FENCE);\n"
        "    }\n"
        "    if (lid == 0 && *status == 0) {\n"
        "        double shift = samples[0];\n"
        "        double total_sum = sum_shared[0];\n"
        "        double total_sqsum = sqsum_shared[0];\n"
        "        double observations = (double)count;\n"
        "        double centered_mean = total_sum / observations;\n"
        "        double mean = shift + centered_mean;\n"
        "        double variance = total_sqsum / observations - centered_mean * centered_mean;\n"
        "        if (variance < 0.0) {\n"
        "            *status = 2;\n"
        "            return;\n"
        "        }\n"
        "        double standard_deviation = sqrt(variance);\n"
        "        if (!isfinite(mean) || !isfinite(standard_deviation)) {\n"
        "            *status = 2;\n"
        "            return;\n"
        "        }\n"
        "        summary[0] = mean;\n"
        "        summary[2] = standard_deviation;\n"
        "    }\n"
        "}\n"
        "\n"
        "double percentile(__global const double* sorted_samples, int count, double probability) {\n"
        "    if (count == 1) {\n"
        "        return sorted_samples[0];\n"
        "    }\n"
        "    double position = probability * (double)(count - 1);\n"
        "    int lower = (int)floor(position);\n"
        "    int upper = (int)ceil(position);\n"
        "    if (lower == upper) {\n"
        "        return sorted_samples[lower];\n"
        "    }\n"
        "    return sorted_samples[lower]\n"
        "            + (sorted_samples[upper] - sorted_samples[lower]) * (position - (double)lower);\n"
        "}\n"
        "\n"
        "__kernel void quantile_kernel(__global const double* sorted_samples, int count,\n"
        "                             __global const double* probabilities, int probability_count,\n"
        "                             __global double* summary, __global int* status) {\n"
        "    if (get_global_id(0) != 0 || *status != 0) {\n"
        "        return;\n"
        "    }\n"
        "    summary[1] = percentile(sorted_samples, count, 0.5);\n"
        "    for (int i = 0; i < probability_count; ++i) {\n"
        "        summary[3 + i] = percentile(sorted_samples, count, probabilities[i]);\n"
        "    }\n"
        "}\n"
        "\n"
        "__kernel void bitonic_sort_parallel(__global double* values, int n) {\n"
        "    int id = get_global_id(0);\n"
        "    for (int k = 2; k <= n; k <<= 1) {\n"
        "        for (int j = k >> 1; j > 0; j >>= 1) {\n"
        "            int ixj = id ^ j;\n"
        "            if (ixj > id) {\n"
        "                double a = values[id];\n"
        "                double b = values[ixj];\n"
        "                if ((id & k) == 0) {\n"
        "                    if (a > b) {\n"
        "                        values[id] = b;\n"
        "                        values[ixj] = a;\n"
        "                    }\n"
        "                } else {\n"
        "                    if (a < b) {\n"
        "                        values[id] = b;\n"
        "                        values[ixj] = a;\n"
        "                    }\n"
        "                }\n"
        "            }\n"
        "            barrier(CLK_GLOBAL_MEM_FENCE);\n"
        "        }\n"
        "    }\n"
        "}\n"
        "\n"
        "__kernel void bitonic_sort_serial(__global double* values, int n) {\n"
        "    if (get_global_id(0) != 0) {\n"
        "        return;\n"
        "    }\n"
        "    for (int k = 2; k <= n; k <<= 1) {\n"
        "        for (int j = k >> 1; j > 0; j >>= 1) {\n"
        "            for (int i = 0; i < n; ++i) {\n"
        "                int ixj = i ^ j;\n"
        "                if (ixj > i) {\n"
        "                    double a = values[i];\n"
        "                    double b = values[ixj];\n"
        "                    if ((i & k) == 0) {\n"
        "                        if (a > b) {\n"
        "                            values[i] = b;\n"
        "                            values[ixj] = a;\n"
        "                        }\n"
        "                    } else {\n"
        "                        if (a < b) {\n"
        "                            values[i] = b;\n"
        "                            values[ixj] = a;\n"
        "                        }\n"
        "                    }\n"
        "                }\n"
        "            }\n"
        "        }\n"
        "    }\n"
        "}\n"
        "\n"
        "__kernel void rng_self_test_kernel(__global int* bounded, __global double* gaussian) {\n"
        "    if (get_global_id(0) != 0) {\n"
        "        return;\n"
        "    }\n"
        "    path_random random;\n"
        "    path_random_init(&random, 42, 317, 12, 5);\n"
        "    *bounded = next_int(&random, 7);\n"
        "    path_random gaussian_random;\n"
        "    path_random_init(&gaussian_random, 42, 317, 12, 5);\n"
        "    *gaussian = next_gaussian(&gaussian_random);\n"
        "}\n";

typedef struct {
    int initialized;
    char failure[STATE_ERROR_BUFFER];
    cl_platform_id platform;
    cl_device_id device;
    cl_context context;
    cl_command_queue queue;
    cl_program program;
    cl_kernel path_kernel;
    cl_kernel moments_partial_kernel;
    cl_kernel moments_finalize_kernel;
    cl_kernel quantile_kernel;
    cl_kernel bitonic_parallel;
    cl_kernel bitonic_serial;
    cl_kernel rng_self_test;
    size_t max_work_group_size;
    char device_name[256];
    int cl_major;
    int cl_minor;
    cl_ulong global_memory;
    int gpu_device;
} opencl_state;

static opencl_state STATE;
static pthread_mutex_t STATE_MUTEX = PTHREAD_MUTEX_INITIALIZER;

static const char* cl_error_string(cl_int status) {
    switch (status) {
    case CL_SUCCESS:
        return "success";
    case CL_DEVICE_NOT_FOUND:
        return "device not found";
    case CL_DEVICE_NOT_AVAILABLE:
        return "device not available";
    case CL_COMPILER_NOT_AVAILABLE:
        return "compiler not available";
    case CL_MEM_OBJECT_ALLOCATION_FAILURE:
        return "memory object allocation failure";
    case CL_OUT_OF_RESOURCES:
        return "out of resources";
    case CL_OUT_OF_HOST_MEMORY:
        return "out of host memory";
    case CL_PROFILING_INFO_NOT_AVAILABLE:
        return "profiling info not available";
    case CL_MEM_COPY_OVERLAP:
        return "memory copy overlap";
    case CL_IMAGE_FORMAT_MISMATCH:
        return "image format mismatch";
    case CL_IMAGE_FORMAT_NOT_SUPPORTED:
        return "image format not supported";
    case CL_BUILD_PROGRAM_FAILURE:
        return "program build failure";
    case CL_MAP_FAILURE:
        return "map failure";
    case CL_MISALIGNED_SUB_BUFFER_OFFSET:
        return "misaligned sub-buffer offset";
    case CL_EXEC_STATUS_ERROR_FOR_EVENTS_IN_WAIT_LIST:
        return "execution status error";
    case CL_COMPILE_PROGRAM_FAILURE:
        return "program compile failure";
    case CL_LINKER_NOT_AVAILABLE:
        return "linker not available";
    case CL_LINK_PROGRAM_FAILURE:
        return "program link failure";
    case CL_DEVICE_PARTITION_FAILED:
        return "device partition failed";
    case CL_KERNEL_ARG_INFO_NOT_AVAILABLE:
        return "kernel argument info not available";
    case CL_INVALID_VALUE:
        return "invalid value";
    case CL_INVALID_DEVICE_TYPE:
        return "invalid device type";
    case CL_INVALID_PLATFORM:
        return "invalid platform";
    case CL_INVALID_DEVICE:
        return "invalid device";
    case CL_INVALID_CONTEXT:
        return "invalid context";
    case CL_INVALID_QUEUE_PROPERTIES:
        return "invalid queue properties";
    case CL_INVALID_COMMAND_QUEUE:
        return "invalid command queue";
    case CL_INVALID_HOST_PTR:
        return "invalid host pointer";
    case CL_INVALID_MEM_OBJECT:
        return "invalid memory object";
    case CL_INVALID_IMAGE_FORMAT_DESCRIPTOR:
        return "invalid image format descriptor";
    case CL_INVALID_IMAGE_SIZE:
        return "invalid image size";
    case CL_INVALID_SAMPLER:
        return "invalid sampler";
    case CL_INVALID_BINARY:
        return "invalid binary";
    case CL_INVALID_BUILD_OPTIONS:
        return "invalid build options";
    case CL_INVALID_PROGRAM:
        return "invalid program";
    case CL_INVALID_PROGRAM_EXECUTABLE:
        return "invalid program executable";
    case CL_INVALID_KERNEL_NAME:
        return "invalid kernel name";
    case CL_INVALID_KERNEL_DEFINITION:
        return "invalid kernel definition";
    case CL_INVALID_KERNEL:
        return "invalid kernel";
    case CL_INVALID_ARG_INDEX:
        return "invalid argument index";
    case CL_INVALID_ARG_VALUE:
        return "invalid argument value";
    case CL_INVALID_ARG_SIZE:
        return "invalid argument size";
    case CL_INVALID_KERNEL_ARGS:
        return "invalid kernel arguments";
    case CL_INVALID_WORK_DIMENSION:
        return "invalid work dimension";
    case CL_INVALID_WORK_GROUP_SIZE:
        return "invalid work group size";
    case CL_INVALID_WORK_ITEM_SIZE:
        return "invalid work item size";
    case CL_INVALID_GLOBAL_OFFSET:
        return "invalid global offset";
    case CL_INVALID_EVENT_WAIT_LIST:
        return "invalid event wait list";
    case CL_INVALID_EVENT:
        return "invalid event";
    case CL_INVALID_OPERATION:
        return "invalid operation";
    case CL_INVALID_GL_OBJECT:
        return "invalid GL object";
    case CL_INVALID_BUFFER_SIZE:
        return "invalid buffer size";
    case CL_INVALID_MIP_LEVEL:
        return "invalid mip level";
    case CL_INVALID_GLOBAL_WORK_SIZE:
        return "invalid global work size";
    case CL_INVALID_PROPERTY:
        return "invalid property";
    default:
        return "unknown OpenCL error";
    }
}

static double now_micros(void) {
    struct timespec value;
    clock_gettime(CLOCK_MONOTONIC, &value);
    return (double)value.tv_sec * 1000000.0 + (double)value.tv_nsec / 1000.0;
}

static void sanitize(char* value) {
    for (char* cursor = value; *cursor != '\0'; ++cursor) {
        if (*cursor == '|') {
            *cursor = '/';
        }
    }
}

static size_t next_power_of_two(size_t value) {
    size_t power = 1;
    while (power < value) {
        power <<= 1;
    }
    return power;
}

static void fail(char* error, size_t error_size, const char* message) {
    if (error != NULL && error_size > 0) {
        snprintf(error, error_size, "%s", message);
    }
}

static cl_int release_mem(cl_mem* memory) {
    if (*memory != NULL) {
        cl_int status = clReleaseMemObject(*memory);
        *memory = NULL;
        return status;
    }
    return CL_SUCCESS;
}

static cl_int make_buffer(cl_mem* out, size_t bytes, const char* operation, char* error, size_t error_size) {
    cl_int status = CL_SUCCESS;
    *out = clCreateBuffer(STATE.context, CL_MEM_READ_WRITE, bytes, NULL, &status);
    if (status != CL_SUCCESS || *out == NULL) {
        snprintf(error, error_size, "%s: %s", operation, cl_error_string(status));
        return status != CL_SUCCESS ? status : CL_INVALID_VALUE;
    }
    return CL_SUCCESS;
}

static int copy_doubles(JNIEnv* environment, jdoubleArray source, jsize expected, const char* name, double** out,
                        char* error, size_t error_size) {
    if (source == NULL || (*environment)->GetArrayLength(environment, source) != expected) {
        snprintf(error, error_size, "%s length mismatch", name);
        return 0;
    }
    *out = (double*)calloc((size_t)expected > 0 ? (size_t)expected : 1, sizeof(double));
    if (*out == NULL) {
        fail(error, error_size, "out of host memory");
        return 0;
    }
    (*environment)->GetDoubleArrayRegion(environment, source, 0, expected, *out);
    return 1;
}

static int copy_ints(JNIEnv* environment, jintArray source, jsize expected, const char* name, int** out, char* error,
                     size_t error_size) {
    if (source == NULL || (*environment)->GetArrayLength(environment, source) != expected) {
        snprintf(error, error_size, "%s length mismatch", name);
        return 0;
    }
    *out = (int*)calloc((size_t)expected > 0 ? (size_t)expected : 1, sizeof(int));
    if (*out == NULL) {
        fail(error, error_size, "out of host memory");
        return 0;
    }
    (*environment)->GetIntArrayRegion(environment, source, 0, expected, *out);
    return 1;
}

static void throw_java(JNIEnv* environment, const char* message) {
    if ((*environment)->ExceptionCheck(environment)) {
        return;
    }
    jclass type = (*environment)->FindClass(environment, "java/lang/IllegalStateException");
    if (type != NULL) {
        char bounded[1024];
        snprintf(bounded, sizeof(bounded), "%.1023s", message);
        (*environment)->ThrowNew(environment, type, bounded);
    }
}

#define CHECK_OPENCL(call, operation)                                                                                \
    do {                                                                                                             \
        cl_int _status = (call);                                                                                     \
        if (_status != CL_SUCCESS) {                                                                                 \
            snprintf(error, error_size, "%s: %s", (operation), cl_error_string(_status));                           \
            error_status = _status;                                                                                  \
            goto cleanup;                                                                                            \
        }                                                                                                            \
    } while (0)

static cl_int run_kernel_self_tests(char* error, size_t error_size) {
    cl_int error_status = CL_SUCCESS;
    cl_mem rng_bounded = NULL;
    cl_mem rng_gaussian = NULL;
    cl_mem history = NULL;
    cl_mem samples = NULL;
    cl_mem quantiles = NULL;
    cl_mem summary = NULL;
    cl_mem status_buffer = NULL;
    cl_mem moments_partials = NULL;
    cl_mem moments_sqpartials = NULL;
    cl_int bounded_value = -1;
    double gaussian_value = 0.0;
    int forecast_status = -1;
    double forecast_summary[4] = {0.0};
    size_t one = 1;
    size_t two = 2;
    double zero = 0.0;
    double half = 0.5;
    int zero_int = 0;
    cl_int padded_int = 2;
    cl_int iteration_arg = 2;
    cl_int count_arg = 2;
    cl_int probability_arg = 1;
    cl_int lookback_arg = 1;
    cl_int decision_arg = 0;
    cl_int horizon_arg = 1;
    cl_long seed_arg = 42;
    cl_int shock_arg = 2;
    cl_int volatility_arg = 0;
    double decay_arg = 0.94;
    double price_arg = 100.0;
    double zero_arg = 0.0;
    size_t local_sort = 2;
    int use_parallel = STATE.max_work_group_size >= 2;

    if (make_buffer(&rng_bounded, sizeof(int), "self-test RNG bounded buffer", error, error_size) != CL_SUCCESS) {
        error_status = CL_INVALID_VALUE;
        goto cleanup;
    }
    if (make_buffer(&rng_gaussian, sizeof(double), "self-test RNG Gaussian buffer", error, error_size) != CL_SUCCESS) {
        error_status = CL_INVALID_VALUE;
        goto cleanup;
    }
    CHECK_OPENCL(clSetKernelArg(STATE.rng_self_test, 0, sizeof(cl_mem), &rng_bounded), "self-test RNG arg bounded");
    CHECK_OPENCL(clSetKernelArg(STATE.rng_self_test, 1, sizeof(cl_mem), &rng_gaussian), "self-test RNG arg gaussian");
    CHECK_OPENCL(clEnqueueNDRangeKernel(STATE.queue, STATE.rng_self_test, 1, NULL, &one, NULL, 0, NULL, NULL),
                 "self-test RNG kernel launch");
    CHECK_OPENCL(clEnqueueReadBuffer(STATE.queue, rng_bounded, CL_TRUE, 0, sizeof(int), &bounded_value, 0, NULL,
                                     NULL), "self-test RNG bounded read");
    CHECK_OPENCL(clEnqueueReadBuffer(STATE.queue, rng_gaussian, CL_TRUE, 0, sizeof(double), &gaussian_value, 0, NULL,
                                     NULL), "self-test RNG Gaussian read");
    if (bounded_value != 2 || fabs(gaussian_value - (-1.3318445490451813)) > 1e-12) {
        fail(error, error_size, "deterministic RNG self-test mismatch");
        error_status = CL_INVALID_OPERATION;
        goto cleanup;
    }

    if (make_buffer(&history, sizeof(double), "self-test history buffer", error, error_size) != CL_SUCCESS) {
        error_status = CL_INVALID_VALUE;
        goto cleanup;
    }
    if (make_buffer(&samples, sizeof(double) * 2, "self-test samples buffer", error, error_size) != CL_SUCCESS) {
        error_status = CL_INVALID_VALUE;
        goto cleanup;
    }
    if (make_buffer(&quantiles, sizeof(double), "self-test quantile buffer", error, error_size) != CL_SUCCESS) {
        error_status = CL_INVALID_VALUE;
        goto cleanup;
    }
    if (make_buffer(&summary, sizeof(double) * 4, "self-test summary buffer", error, error_size) != CL_SUCCESS) {
        error_status = CL_INVALID_VALUE;
        goto cleanup;
    }
    if (make_buffer(&status_buffer, sizeof(int), "self-test status buffer", error, error_size) != CL_SUCCESS) {
        error_status = CL_INVALID_VALUE;
        goto cleanup;
    }
    if (make_buffer(&moments_partials, sizeof(double), "self-test moments partial buffer", error, error_size)
            != CL_SUCCESS) {
        error_status = CL_INVALID_VALUE;
        goto cleanup;
    }
    if (make_buffer(&moments_sqpartials, sizeof(double), "self-test moments sqpartial buffer", error, error_size)
            != CL_SUCCESS) {
        error_status = CL_INVALID_VALUE;
        goto cleanup;
    }
    CHECK_OPENCL(clEnqueueWriteBuffer(STATE.queue, status_buffer, CL_TRUE, 0, sizeof(int), &zero_int, 0, NULL, NULL),
                 "self-test status reset");
    CHECK_OPENCL(clEnqueueWriteBuffer(STATE.queue, history, CL_TRUE, 0, sizeof(double), &zero, 0, NULL, NULL),
                 "self-test history write");
    CHECK_OPENCL(clEnqueueWriteBuffer(STATE.queue, quantiles, CL_TRUE, 0, sizeof(double), &half, 0, NULL, NULL),
                 "self-test quantile write");

    CHECK_OPENCL(clSetKernelArg(STATE.path_kernel, 0, sizeof(double), &price_arg), "self-test arg price");
    CHECK_OPENCL(clSetKernelArg(STATE.path_kernel, 1, sizeof(double), &zero_arg), "self-test arg mean");
    CHECK_OPENCL(clSetKernelArg(STATE.path_kernel, 2, sizeof(double), &zero_arg), "self-test arg drift");
    CHECK_OPENCL(clSetKernelArg(STATE.path_kernel, 3, sizeof(double), &zero_arg), "self-test arg variance");
    CHECK_OPENCL(clSetKernelArg(STATE.path_kernel, 4, sizeof(cl_mem), &history), "self-test arg history");
    cl_int history_row_arg = 0;
    CHECK_OPENCL(clSetKernelArg(STATE.path_kernel, 5, sizeof(cl_int), &lookback_arg), "self-test arg lookback");
    CHECK_OPENCL(clSetKernelArg(STATE.path_kernel, 6, sizeof(cl_int), &history_row_arg), "self-test arg history row");
    CHECK_OPENCL(clSetKernelArg(STATE.path_kernel, 7, sizeof(cl_int), &decision_arg), "self-test arg decision");
    CHECK_OPENCL(clSetKernelArg(STATE.path_kernel, 8, sizeof(cl_int), &horizon_arg), "self-test arg horizon");
    CHECK_OPENCL(clSetKernelArg(STATE.path_kernel, 9, sizeof(cl_int), &iteration_arg), "self-test arg iterations");
    CHECK_OPENCL(clSetKernelArg(STATE.path_kernel, 10, sizeof(cl_long), &seed_arg), "self-test arg seed");
    CHECK_OPENCL(clSetKernelArg(STATE.path_kernel, 11, sizeof(cl_int), &shock_arg), "self-test arg shock");
    CHECK_OPENCL(clSetKernelArg(STATE.path_kernel, 12, sizeof(cl_int), &volatility_arg), "self-test arg volatility");
    CHECK_OPENCL(clSetKernelArg(STATE.path_kernel, 13, sizeof(double), &decay_arg), "self-test arg decay");
    CHECK_OPENCL(clSetKernelArg(STATE.path_kernel, 14, sizeof(cl_mem), &samples), "self-test arg samples");
    CHECK_OPENCL(clSetKernelArg(STATE.path_kernel, 15, sizeof(cl_mem), &status_buffer), "self-test arg status");
    CHECK_OPENCL(clEnqueueNDRangeKernel(STATE.queue, STATE.path_kernel, 1, NULL, &two, NULL, 0, NULL, NULL),
                 "self-test path kernel launch");

    size_t moment_global = use_parallel ? two : one;
    size_t* moment_local = use_parallel ? &two : NULL;
    CHECK_OPENCL(clSetKernelArg(STATE.moments_partial_kernel, 0, sizeof(cl_mem), &samples),
                 "self-test arg moments partial samples");
    CHECK_OPENCL(clSetKernelArg(STATE.moments_partial_kernel, 1, sizeof(cl_int), &count_arg),
                 "self-test arg moments partial count");
    CHECK_OPENCL(clSetKernelArg(STATE.moments_partial_kernel, 2, sizeof(cl_mem), &moments_partials),
                 "self-test arg moments partial sums");
    CHECK_OPENCL(clSetKernelArg(STATE.moments_partial_kernel, 3, sizeof(cl_mem), &moments_sqpartials),
                 "self-test arg moments partial sqsums");
    CHECK_OPENCL(clSetKernelArg(STATE.moments_partial_kernel, 4, sizeof(cl_mem), &status_buffer),
                 "self-test arg moments partial status");
    CHECK_OPENCL(clEnqueueNDRangeKernel(STATE.queue, STATE.moments_partial_kernel, 1, NULL, &moment_global,
                                       moment_local, 0, NULL, NULL), "self-test moments partial kernel launch");
    {
        cl_int block_count_arg = 1;
        CHECK_OPENCL(clSetKernelArg(STATE.moments_finalize_kernel, 0, sizeof(cl_mem), &moments_partials),
                     "self-test arg moments finalize sums");
        CHECK_OPENCL(clSetKernelArg(STATE.moments_finalize_kernel, 1, sizeof(cl_mem), &moments_sqpartials),
                     "self-test arg moments finalize sqsums");
        CHECK_OPENCL(clSetKernelArg(STATE.moments_finalize_kernel, 2, sizeof(cl_int), &block_count_arg),
                     "self-test arg moments finalize blocks");
        CHECK_OPENCL(clSetKernelArg(STATE.moments_finalize_kernel, 3, sizeof(cl_int), &count_arg),
                     "self-test arg moments finalize count");
        CHECK_OPENCL(clSetKernelArg(STATE.moments_finalize_kernel, 4, sizeof(cl_mem), &samples),
                     "self-test arg moments finalize samples");
        CHECK_OPENCL(clSetKernelArg(STATE.moments_finalize_kernel, 5, sizeof(cl_mem), &summary),
                     "self-test arg moments finalize summary");
        CHECK_OPENCL(clSetKernelArg(STATE.moments_finalize_kernel, 6, sizeof(cl_mem), &status_buffer),
                     "self-test arg moments finalize status");
        CHECK_OPENCL(clEnqueueNDRangeKernel(STATE.queue, STATE.moments_finalize_kernel, 1, NULL, &moment_global,
                                          moment_local, 0, NULL, NULL),
                     "self-test moments finalize kernel launch");
    }

    if (use_parallel) {
        CHECK_OPENCL(clSetKernelArg(STATE.bitonic_parallel, 0, sizeof(cl_mem), &samples), "self-test arg sort samples");
        CHECK_OPENCL(clSetKernelArg(STATE.bitonic_parallel, 1, sizeof(cl_int), &padded_int), "self-test arg sort n");
        CHECK_OPENCL(clEnqueueNDRangeKernel(STATE.queue, STATE.bitonic_parallel, 1, NULL, &local_sort, &local_sort,
                                            0, NULL, NULL), "self-test bitonic launch");
    } else {
        CHECK_OPENCL(clSetKernelArg(STATE.bitonic_serial, 0, sizeof(cl_mem), &samples), "self-test arg sort samples");
        CHECK_OPENCL(clSetKernelArg(STATE.bitonic_serial, 1, sizeof(cl_int), &padded_int), "self-test arg sort n");
        CHECK_OPENCL(clEnqueueNDRangeKernel(STATE.queue, STATE.bitonic_serial, 1, NULL, &one, NULL, 0, NULL, NULL),
                     "self-test bitonic serial launch");
    }

    CHECK_OPENCL(clSetKernelArg(STATE.quantile_kernel, 0, sizeof(cl_mem), &samples), "self-test arg quantile samples");
    CHECK_OPENCL(clSetKernelArg(STATE.quantile_kernel, 1, sizeof(cl_int), &count_arg), "self-test arg quantile count");
    CHECK_OPENCL(clSetKernelArg(STATE.quantile_kernel, 2, sizeof(cl_mem), &quantiles), "self-test arg quantile probs");
    CHECK_OPENCL(clSetKernelArg(STATE.quantile_kernel, 3, sizeof(cl_int), &probability_arg),
                 "self-test arg probability count");
    CHECK_OPENCL(clSetKernelArg(STATE.quantile_kernel, 4, sizeof(cl_mem), &summary), "self-test arg quantile summary");
    CHECK_OPENCL(clSetKernelArg(STATE.quantile_kernel, 5, sizeof(cl_mem), &status_buffer),
                 "self-test arg quantile status");
    CHECK_OPENCL(clEnqueueNDRangeKernel(STATE.queue, STATE.quantile_kernel, 1, NULL, &one, NULL, 0, NULL, NULL),
                 "self-test quantile kernel launch");
    CHECK_OPENCL(clFinish(STATE.queue), "self-test finish");

    CHECK_OPENCL(clEnqueueReadBuffer(STATE.queue, status_buffer, CL_TRUE, 0, sizeof(int), &forecast_status, 0, NULL,
                                     NULL), "self-test status read");
    CHECK_OPENCL(clEnqueueReadBuffer(STATE.queue, summary, CL_TRUE, 0, sizeof(forecast_summary), forecast_summary, 0,
                                     NULL, NULL), "self-test summary read");
    if (forecast_status != 0 || fabs(forecast_summary[0] - 100.0) > 1e-12 || fabs(forecast_summary[1] - 100.0) > 1e-12
            || forecast_summary[2] != 0.0 || fabs(forecast_summary[3] - 100.0) > 1e-12) {
        fail(error, error_size, "forecast kernel self-test mismatch");
        error_status = CL_INVALID_OPERATION;
        goto cleanup;
    }

cleanup:
    release_mem(&rng_bounded);
    release_mem(&rng_gaussian);
    release_mem(&status_buffer);
    release_mem(&history);
    release_mem(&samples);
    release_mem(&quantiles);
    release_mem(&summary);
    release_mem(&moments_partials);
    release_mem(&moments_sqpartials);
    return error_status;
}

static cl_int initialize_state(char* error, size_t error_size) {
    cl_int error_status = CL_SUCCESS;
    cl_platform_id* platforms = NULL;
    cl_uint platform_count = 0;
    cl_device_id selected = NULL;
    cl_platform_id first_fp64_platform = NULL;
    cl_device_id first_fp64_gpu = NULL;
    int selected_gpu = 0;
    char version[128];

    // Two-phase query so hosts exposing more platforms than any fixed bound
    // can never overflow a stack buffer: ask for the count, allocate exactly
    // that many IDs, then enumerate.
    error_status = clGetPlatformIDs(0, NULL, &platform_count);
    if (error_status != CL_SUCCESS || platform_count < 1) {
        fail(error, error_size, "no OpenCL platform");
        error_status = CL_DEVICE_NOT_FOUND;
        goto cleanup;
    }
    platforms = (cl_platform_id*)calloc(platform_count, sizeof(cl_platform_id));
    if (platforms == NULL) {
        fail(error, error_size, "out of host memory");
        error_status = CL_OUT_OF_HOST_MEMORY;
        goto cleanup;
    }
    error_status = clGetPlatformIDs(platform_count, platforms, &platform_count);
    if (error_status != CL_SUCCESS || platform_count < 1) {
        fail(error, error_size, "no OpenCL platform");
        error_status = CL_DEVICE_NOT_FOUND;
        goto cleanup;
    }
    // A host commonly exposes several platforms (vendor GPU runtime plus CPU
    // ICDs). Run a GPU pass across every platform first, then a CPU fallback
    // pass across every platform, so a CPU ICD listed before a vendor GPU ICD
    // can never hide a usable FP64 GPU.
    for (cl_uint pass = 0; pass < 2 && selected == NULL; ++pass) {
        if (pass == 1 && first_fp64_gpu != NULL) {
            // The GPU pass found FP64 GPUs but none met the 2 GiB qualification
            // floor; fall back to the first FP64 GPU so single-device or
            // all-small hosts keep the previous behavior (the Java model then
            // declines it with a zero predicted speedup).
            selected = first_fp64_gpu;
            selected_gpu = 1;
            STATE.platform = first_fp64_platform;
            break;
        }
        for (cl_uint platform_index = 0; platform_index < platform_count && selected == NULL; ++platform_index) {
            cl_uint device_count = 0;
            cl_device_id* devices = NULL;
            if (clGetDeviceIDs(platforms[platform_index], CL_DEVICE_TYPE_ALL, 0, NULL, &device_count) != CL_SUCCESS
                    || device_count < 1) {
                continue;
            }
            devices = (cl_device_id*)calloc(device_count, sizeof(cl_device_id));
            if (devices == NULL) {
                fail(error, error_size, "out of host memory");
                error_status = CL_OUT_OF_HOST_MEMORY;
                goto cleanup;
            }
            if (clGetDeviceIDs(platforms[platform_index], CL_DEVICE_TYPE_ALL, device_count, devices, NULL) != CL_SUCCESS) {
                free(devices);
                continue;
            }
            for (cl_uint index = 0; index < device_count; ++index) {
                cl_device_type device_type = 0;
                cl_ulong double_config = 0;
                cl_ulong global_memory = 0;
                clGetDeviceInfo(devices[index], CL_DEVICE_TYPE, sizeof(device_type), &device_type, NULL);
                clGetDeviceInfo(devices[index], CL_DEVICE_DOUBLE_FP_CONFIG, sizeof(double_config), &double_config, NULL);
                if (double_config == 0) {
                    continue;
                }
                int is_gpu = (device_type & CL_DEVICE_TYPE_GPU) != 0;
                if (pass == 0 && is_gpu) {
                    if (first_fp64_gpu == NULL) {
                        first_fp64_gpu = devices[index];
                        first_fp64_platform = platforms[platform_index];
                    }
                    clGetDeviceInfo(devices[index], CL_DEVICE_GLOBAL_MEM_SIZE, sizeof(global_memory), &global_memory,
                            NULL);
                    if (global_memory >= QUALIFIED_MINIMUM_DEVICE_BYTES) {
                        selected = devices[index];
                        selected_gpu = 1;
                        STATE.platform = platforms[platform_index];
                        break;
                    }
                } else if (pass == 1 && !is_gpu) {
                    selected = devices[index];
                    selected_gpu = 0;
                    STATE.platform = platforms[platform_index];
                    break;
                }
            }
            free(devices);
        }
    }
    if (selected == NULL) {
        fail(error, error_size, "no FP64-capable OpenCL device");
        error_status = CL_DEVICE_NOT_FOUND;
        goto cleanup;
    }
    STATE.device = selected;
    STATE.gpu_device = selected_gpu;

    {
        cl_context_properties properties[] = {CL_CONTEXT_PLATFORM, (cl_context_properties)STATE.platform, 0};
        STATE.context = clCreateContext(properties, 1, &STATE.device, NULL, NULL, &error_status);
        if (error_status != CL_SUCCESS || STATE.context == NULL) {
            fail(error, error_size, "context creation failed");
            goto cleanup;
        }
    }
    STATE.queue = clCreateCommandQueue(STATE.context, STATE.device, CL_QUEUE_PROFILING_ENABLE, &error_status);
    if (error_status != CL_SUCCESS || STATE.queue == NULL) {
        fail(error, error_size, "command queue creation failed");
        goto cleanup;
    }
    {
        const char* sources[] = {KERNEL_SOURCE};
        STATE.program = clCreateProgramWithSource(STATE.context, 1, sources, NULL, &error_status);
        if (error_status != CL_SUCCESS || STATE.program == NULL) {
            fail(error, error_size, "program source creation failed");
            goto cleanup;
        }
    }
    error_status = clBuildProgram(STATE.program, 1, &STATE.device, NULL, NULL, NULL);
    if (error_status != CL_SUCCESS) {
        size_t log_size = 0;
        char* log = NULL;
        clGetProgramBuildInfo(STATE.program, STATE.device, CL_PROGRAM_BUILD_LOG, 0, NULL, &log_size);
        if (log_size > 0) {
            log = (char*)calloc(log_size + 1, sizeof(char));
            if (log != NULL) {
                clGetProgramBuildInfo(STATE.program, STATE.device, CL_PROGRAM_BUILD_LOG, log_size, log, NULL);
                snprintf(error, error_size, "OpenCL kernel build failed: %.450s", log);
                sanitize(error);
                free(log);
            }
        } else {
            fail(error, error_size, "OpenCL kernel build failed");
        }
        goto cleanup;
    }
    STATE.path_kernel = clCreateKernel(STATE.program, "path_kernel", &error_status);
    if (error_status != CL_SUCCESS || STATE.path_kernel == NULL) {
        fail(error, error_size, "path kernel creation failed");
        goto cleanup;
    }
    STATE.moments_partial_kernel = clCreateKernel(STATE.program, "moments_partial_kernel", &error_status);
    if (error_status != CL_SUCCESS || STATE.moments_partial_kernel == NULL) {
        fail(error, error_size, "moments partial kernel creation failed");
        goto cleanup;
    }
    STATE.moments_finalize_kernel = clCreateKernel(STATE.program, "moments_finalize_kernel", &error_status);
    if (error_status != CL_SUCCESS || STATE.moments_finalize_kernel == NULL) {
        fail(error, error_size, "moments finalize kernel creation failed");
        goto cleanup;
    }
    STATE.quantile_kernel = clCreateKernel(STATE.program, "quantile_kernel", &error_status);
    if (error_status != CL_SUCCESS || STATE.quantile_kernel == NULL) {
        fail(error, error_size, "quantile kernel creation failed");
        goto cleanup;
    }
    STATE.bitonic_parallel = clCreateKernel(STATE.program, "bitonic_sort_parallel", &error_status);
    if (error_status != CL_SUCCESS || STATE.bitonic_parallel == NULL) {
        fail(error, error_size, "bitonic parallel kernel creation failed");
        goto cleanup;
    }
    STATE.bitonic_serial = clCreateKernel(STATE.program, "bitonic_sort_serial", &error_status);
    if (error_status != CL_SUCCESS || STATE.bitonic_serial == NULL) {
        fail(error, error_size, "bitonic serial kernel creation failed");
        goto cleanup;
    }
    STATE.rng_self_test = clCreateKernel(STATE.program, "rng_self_test_kernel", &error_status);
    if (error_status != CL_SUCCESS || STATE.rng_self_test == NULL) {
        fail(error, error_size, "RNG self-test kernel creation failed");
        goto cleanup;
    }

    if (clGetDeviceInfo(STATE.device, CL_DEVICE_MAX_WORK_GROUP_SIZE, sizeof(STATE.max_work_group_size),
                        &STATE.max_work_group_size, NULL) != CL_SUCCESS) {
        fail(error, error_size, "unable to query work group limit");
        goto cleanup;
    }
    if (clGetDeviceInfo(STATE.device, CL_DEVICE_VERSION, sizeof(version), version, NULL) == CL_SUCCESS) {
        int major = 0;
        int minor = 0;
        if (sscanf(version, "OpenCL %d.%d", &major, &minor) == 2) {
            STATE.cl_major = major;
            STATE.cl_minor = minor;
        }
    }
    if (clGetDeviceInfo(STATE.device, CL_DEVICE_NAME, sizeof(STATE.device_name), STATE.device_name, NULL)
            == CL_SUCCESS) {
        STATE.device_name[sizeof(STATE.device_name) - 1] = '\0';
    } else {
        snprintf(STATE.device_name, sizeof(STATE.device_name), "unknown OpenCL device");
    }
    clGetDeviceInfo(STATE.device, CL_DEVICE_GLOBAL_MEM_SIZE, sizeof(STATE.global_memory), &STATE.global_memory, NULL);

    if (run_kernel_self_tests(error, error_size) != CL_SUCCESS) {
        error_status = CL_INVALID_OPERATION;
        goto cleanup;
    }
    STATE.initialized = 1;

cleanup:
    if (error_status != CL_SUCCESS && STATE.initialized == 0) {
        if (platforms != NULL) {
            free(platforms);
            STATE.platform = NULL;
        }
        if (STATE.rng_self_test != NULL) {
            clReleaseKernel(STATE.rng_self_test);
            STATE.rng_self_test = NULL;
        }
        if (STATE.bitonic_serial != NULL) {
            clReleaseKernel(STATE.bitonic_serial);
            STATE.bitonic_serial = NULL;
        }
        if (STATE.bitonic_parallel != NULL) {
            clReleaseKernel(STATE.bitonic_parallel);
            STATE.bitonic_parallel = NULL;
        }
        if (STATE.quantile_kernel != NULL) {
            clReleaseKernel(STATE.quantile_kernel);
            STATE.quantile_kernel = NULL;
        }
        if (STATE.moments_finalize_kernel != NULL) {
            clReleaseKernel(STATE.moments_finalize_kernel);
            STATE.moments_finalize_kernel = NULL;
        }
        if (STATE.moments_partial_kernel != NULL) {
            clReleaseKernel(STATE.moments_partial_kernel);
            STATE.moments_partial_kernel = NULL;
        }
        if (STATE.path_kernel != NULL) {
            clReleaseKernel(STATE.path_kernel);
            STATE.path_kernel = NULL;
        }
        if (STATE.program != NULL) {
            clReleaseProgram(STATE.program);
            STATE.program = NULL;
        }
        if (STATE.queue != NULL) {
            clReleaseCommandQueue(STATE.queue);
            STATE.queue = NULL;
        }
        if (STATE.context != NULL) {
            clReleaseContext(STATE.context);
            STATE.context = NULL;
        }
    }
    return error_status;
}

static int ensure_state(char* error, size_t error_size) {
    if (STATE.initialized == 1) {
        return 1;
    }
    if (STATE.initialized == 2) {
        snprintf(error, error_size, "%s", STATE.failure);
        return 0;
    }
    if (initialize_state(error, error_size) != CL_SUCCESS) {
        STATE.initialized = 2;
        snprintf(STATE.failure, sizeof(STATE.failure), "%s", error);
        return 0;
    }
    return 1;
}

/*
 * Effective work-group size for the moments kernels: min(device max work
 * group size, 256, both kernel CL_KERNEL_WORK_GROUP_SIZE limits), rounded
 * down to a power of two. Shared by the probe (so the JVM preflight sizes
 * the partial buffers exactly) and by evaluate (which allocates them).
 */
static size_t effective_moment_threads(void)
{
    size_t threads = STATE.max_work_group_size < 256 ? STATE.max_work_group_size : 256;
    size_t kernel_limit = 0;
    if (clGetKernelWorkGroupInfo(STATE.moments_partial_kernel, STATE.device, CL_KERNEL_WORK_GROUP_SIZE,
                                 sizeof(kernel_limit), &kernel_limit, NULL) == CL_SUCCESS
        && kernel_limit > 0 && kernel_limit < threads) {
        threads = kernel_limit;
    }
    if (clGetKernelWorkGroupInfo(STATE.moments_finalize_kernel, STATE.device, CL_KERNEL_WORK_GROUP_SIZE,
                                 sizeof(kernel_limit), &kernel_limit, NULL) == CL_SUCCESS
        && kernel_limit > 0 && kernel_limit < threads) {
        threads = kernel_limit;
    }
    while ((threads & (threads - 1)) != 0) {
        --threads;
    }
    return threads;
}

JNIEXPORT jstring JNICALL
Java_org_ta4j_cli_acceleration_internal_providers_JniOpenClNativeBridge_nativeProbe(JNIEnv* environment, jclass,
                                                                                    jint abi_version) {
    char error[STATE_ERROR_BUFFER];
    char payload[1024];
    if (pthread_mutex_lock(&STATE_MUTEX) != 0) {
        return (*environment)->NewStringUTF(environment, "ERROR|||||||||0|unable to lock native state");
    }
    if (abi_version != ABI_VERSION) {
        pthread_mutex_unlock(&STATE_MUTEX);
        return (*environment)->NewStringUTF(environment, "ERROR|||||||||0|ABI mismatch");
    }
    if (!ensure_state(error, sizeof(error))) {
        char detail[768];
        // Sanitize the error text before embedding it so the envelope's pipe
        // delimiters survive; sanitizing the full payload would replace every
        // delimiter with '/' and make the probe metadata unparseable.
        sanitize(error);
        snprintf(detail, sizeof(detail), "ERROR|||||||||0|%.750s", error);
        pthread_mutex_unlock(&STATE_MUTEX);
        return (*environment)->NewStringUTF(environment, detail);
    }
    snprintf(payload, sizeof(payload), "OK|%s|%d|%d|%llu|%llu|0|0|%zu|%d|self-test passed", STATE.device_name,
             STATE.cl_major, STATE.cl_minor, (unsigned long long)STATE.global_memory,
             (unsigned long long)STATE.global_memory, effective_moment_threads(), STATE.gpu_device ? 1 : 0);
    pthread_mutex_unlock(&STATE_MUTEX);
    return (*environment)->NewStringUTF(environment, payload);
}

JNIEXPORT jdoubleArray JNICALL
Java_org_ta4j_cli_acceleration_internal_providers_JniOpenClNativeBridge_nativeEvaluate(
        JNIEnv* environment, jclass, jint abi_version, jint from_inclusive, jint decision_count, jint horizon,
        jint iteration_count, jint lookback, jlong seed, jint shock_model, jint volatility_mode, jdouble decay,
        jdoubleArray quantiles_array, jintArray stable_array, jdoubleArray prices_array, jdoubleArray means_array,
        jdoubleArray drifts_array, jdoubleArray variances_array, jdoubleArray historical_returns_array) {
    char error[STATE_ERROR_BUFFER];
    double total_start = now_micros();
    double transfer_micros = 0.0;
    double kernel_micros = 0.0;
    double reduction_micros = 0.0;
    jdoubleArray result = NULL;
    double* quantiles = NULL;
    int* stable = NULL;
    double* prices = NULL;
    double* means = NULL;
    double* drifts = NULL;
    double* variances = NULL;
    double* historical_returns = NULL;
    double* payload = NULL;
    double* padded_host = NULL;
    int* status_host = NULL;
    cl_mem device_samples = NULL;
    cl_mem device_history = NULL;
    cl_mem device_quantiles = NULL;
    cl_mem device_summary = NULL;
    cl_mem device_status = NULL;
    cl_mem device_partials = NULL;
    cl_mem device_partial_sqsums = NULL;
    jsize quantile_count = 0;
    size_t history_count = 0;
    size_t row_length = 0;
    size_t payload_size = 0;
    size_t padded = 0;
    size_t summary_size = 0;
    size_t one = 1;
    cl_int padded_int = 0;
    int use_parallel = 0;
    size_t moment_threads = 1;
    size_t path_blocks = 1;
    cl_event* events = NULL;
    int* event_kinds = NULL;
    int event_count = 0;

    if (pthread_mutex_lock(&STATE_MUTEX) != 0) {
        throw_java(environment, "unable to lock native state");
        return NULL;
    }
    if (abi_version != ABI_VERSION || decision_count < 1 || horizon < 1 || iteration_count < 1 || lookback < 1
            || shock_model < 0 || shock_model > 2 || volatility_mode < 0 || volatility_mode > 1
            || !(decay > 0.0 && decay < 1.0)) {
        throw_java(environment, "invalid OpenCL ABI or request metadata");
        pthread_mutex_unlock(&STATE_MUTEX);
        return NULL;
    }
    error[0] = '\0';
    if (!ensure_state(error, sizeof(error))) {
        throw_java(environment, error);
        pthread_mutex_unlock(&STATE_MUTEX);
        return NULL;
    }
    if (quantiles_array == NULL) {
        throw_java(environment, "quantiles must not be null");
        pthread_mutex_unlock(&STATE_MUTEX);
        return NULL;
    }
    quantile_count = (*environment)->GetArrayLength(environment, quantiles_array);
    if (quantile_count < 1) {
        throw_java(environment, "at least one quantile is required");
        pthread_mutex_unlock(&STATE_MUTEX);
        return NULL;
    }
    history_count = (size_t)decision_count * (size_t)lookback;
    row_length = 4U + (size_t)quantile_count;
    payload_size = 4U + (size_t)decision_count * row_length;
    summary_size = 3U + (size_t)quantile_count;
    padded = next_power_of_two((size_t)iteration_count);
    if (history_count > (size_t)INT32_MAX || payload_size > (size_t)INT32_MAX || padded > (size_t)INT32_MAX) {
        throw_java(environment, "forecast buffers exceed JNI limits");
        pthread_mutex_unlock(&STATE_MUTEX);
        return NULL;
    }
    padded_int = (cl_int)padded;
    use_parallel = padded <= STATE.max_work_group_size;
    moment_threads = effective_moment_threads();
    path_blocks = ((size_t)iteration_count + moment_threads - 1) / moment_threads;

    if (!copy_doubles(environment, quantiles_array, quantile_count, "quantiles", &quantiles, error, sizeof(error))
            || !copy_ints(environment, stable_array, decision_count, "stable", &stable, error, sizeof(error))
            || !copy_doubles(environment, prices_array, decision_count, "prices", &prices, error, sizeof(error))
            || !copy_doubles(environment, means_array, decision_count, "means", &means, error, sizeof(error))
            || !copy_doubles(environment, drifts_array, decision_count, "drifts", &drifts, error, sizeof(error))
            || !copy_doubles(environment, variances_array, decision_count, "variances", &variances, error,
                             sizeof(error))
            || !copy_doubles(environment, historical_returns_array, (jsize)history_count, "historicalReturns",
                             &historical_returns, error, sizeof(error))) {
        // Fall through to cleanup so the buffers allocated by the earlier
        // copies in this chain are released.
        throw_java(environment, error);
        goto cleanup;
    }

    payload = (double*)calloc(payload_size, sizeof(double));
    padded_host = (double*)malloc(padded * sizeof(double));
    status_host = (int*)calloc((size_t)decision_count, sizeof(int));
    if (payload == NULL || padded_host == NULL || status_host == NULL) {
        throw_java(environment, "out of host memory");
        goto cleanup;
    }
    for (size_t index = 0; index < padded; ++index) {
        padded_host[index] = DBL_MAX;
    }

    {
        cl_int create_status = CL_SUCCESS;
        device_samples = clCreateBuffer(STATE.context, CL_MEM_READ_WRITE, padded * sizeof(double), NULL,
                                        &create_status);
        if (create_status != CL_SUCCESS || device_samples == NULL) {
            snprintf(error, sizeof(error), "samples buffer creation: %s", cl_error_string(create_status));
            throw_java(environment, error);
            goto cleanup;
        }
        device_history = clCreateBuffer(STATE.context, CL_MEM_READ_WRITE, history_count * sizeof(double), NULL,
                                        &create_status);
        if (create_status != CL_SUCCESS || device_history == NULL) {
            snprintf(error, sizeof(error), "history buffer creation: %s", cl_error_string(create_status));
            throw_java(environment, error);
            goto cleanup;
        }
        device_quantiles = clCreateBuffer(STATE.context, CL_MEM_READ_WRITE, (size_t)quantile_count * sizeof(double),
                                          NULL, &create_status);
        if (create_status != CL_SUCCESS || device_quantiles == NULL) {
            snprintf(error, sizeof(error), "quantile buffer creation: %s", cl_error_string(create_status));
            throw_java(environment, error);
            goto cleanup;
        }
        device_summary = clCreateBuffer(STATE.context, CL_MEM_READ_WRITE, summary_size * sizeof(double), NULL,
                                        &create_status);
        if (create_status != CL_SUCCESS || device_summary == NULL) {
            snprintf(error, sizeof(error), "summary buffer creation: %s", cl_error_string(create_status));
            throw_java(environment, error);
            goto cleanup;
        }
        device_status = clCreateBuffer(STATE.context, CL_MEM_READ_WRITE, sizeof(int), NULL, &create_status);
        if (create_status != CL_SUCCESS || device_status == NULL) {
            snprintf(error, sizeof(error), "status buffer creation: %s", cl_error_string(create_status));
            throw_java(environment, error);
            goto cleanup;
        }
        device_partials = clCreateBuffer(STATE.context, CL_MEM_READ_WRITE, path_blocks * sizeof(double), NULL,
                                         &create_status);
        if (create_status != CL_SUCCESS || device_partials == NULL) {
            snprintf(error, sizeof(error), "moments partial buffer creation: %s", cl_error_string(create_status));
            throw_java(environment, error);
            goto cleanup;
        }
        device_partial_sqsums = clCreateBuffer(STATE.context, CL_MEM_READ_WRITE, path_blocks * sizeof(double), NULL,
                                               &create_status);
        if (create_status != CL_SUCCESS || device_partial_sqsums == NULL) {
            snprintf(error, sizeof(error), "moments sqpartial buffer creation: %s", cl_error_string(create_status));
            throw_java(environment, error);
            goto cleanup;
        }
    }

    // Event kinds: 0 = transfer, 1 = path sampling, 2 = reduction/sort/quantile.
    events = (cl_event*)calloc(8 * (size_t)decision_count + 3, sizeof(cl_event));
    event_kinds = (int*)calloc(8 * (size_t)decision_count + 3, sizeof(int));
    if (events == NULL || event_kinds == NULL) {
        throw_java(environment, "out of host memory for profiling events");
        goto cleanup;
    }
    {
        cl_int write_status = clEnqueueWriteBuffer(STATE.queue, device_quantiles, CL_FALSE, 0,
                                                   (size_t)quantile_count * sizeof(double), quantiles, 0, NULL,
                                                   &events[event_count]);
        if (write_status != CL_SUCCESS) {
            snprintf(error, sizeof(error), "quantile transfer: %s", cl_error_string(write_status));
            throw_java(environment, error);
            goto cleanup;
        }
        event_kinds[event_count++] = 0;
        write_status = clEnqueueWriteBuffer(STATE.queue, device_samples, CL_FALSE, 0, padded * sizeof(double),
                                            padded_host, 0, NULL, &events[event_count]);
        if (write_status != CL_SUCCESS) {
            snprintf(error, sizeof(error), "sample padding transfer: %s", cl_error_string(write_status));
            throw_java(environment, error);
            goto cleanup;
        }
        event_kinds[event_count++] = 0;
        write_status = clEnqueueWriteBuffer(STATE.queue, device_history, CL_FALSE, 0, history_count * sizeof(double),
                                            historical_returns, 0, NULL, &events[event_count]);
        if (write_status != CL_SUCCESS) {
            snprintf(error, sizeof(error), "historical return transfer: %s", cl_error_string(write_status));
            throw_java(environment, error);
            goto cleanup;
        }
        event_kinds[event_count++] = 0;
    }
    static const int zero_status = 0;

    for (int decision = 0; decision < decision_count; ++decision) {
        size_t row = 4U + (size_t)decision * row_length;
        if (stable[decision] == 0) {
            payload[row] = 1.0;
            continue;
        }
        {
            cl_int write_status = clEnqueueWriteBuffer(STATE.queue, device_status, CL_FALSE, 0, sizeof(int),
                                                       &zero_status, 0, NULL, &events[event_count]);
            if (write_status != CL_SUCCESS) {
                snprintf(error, sizeof(error), "status reset: %s", cl_error_string(write_status));
                throw_java(environment, error);
                goto cleanup;
            }
            event_kinds[event_count++] = 0;
        }

        {
            double price = prices[decision];
            double mean = means[decision];
            double drift = drifts[decision];
            double variance = variances[decision];
            cl_int lookback_arg = lookback;
            cl_int decision_arg = from_inclusive + decision;
            cl_int history_row_arg = (cl_int)decision;
            cl_int horizon_arg = horizon;
            cl_int iteration_arg = iteration_count;
            cl_long seed_arg = (cl_long)seed;
            cl_int shock_arg = shock_model;
            cl_int volatility_arg = volatility_mode;
            double decay_arg = decay;
            cl_int sort_n = padded_int;
            size_t global_paths = (size_t)iteration_count;
            size_t local_sort = padded;
            cl_int argument_error = CL_SUCCESS;

            argument_error = clSetKernelArg(STATE.path_kernel, 0, sizeof(double), &price);
            if (argument_error == CL_SUCCESS) {
                argument_error = clSetKernelArg(STATE.path_kernel, 1, sizeof(double), &mean);
            }
            if (argument_error == CL_SUCCESS) {
                argument_error = clSetKernelArg(STATE.path_kernel, 2, sizeof(double), &drift);
            }
            if (argument_error == CL_SUCCESS) {
                argument_error = clSetKernelArg(STATE.path_kernel, 3, sizeof(double), &variance);
            }
            if (argument_error == CL_SUCCESS) {
                argument_error = clSetKernelArg(STATE.path_kernel, 4, sizeof(cl_mem), &device_history);
            }
            if (argument_error == CL_SUCCESS) {
                argument_error = clSetKernelArg(STATE.path_kernel, 5, sizeof(cl_int), &lookback_arg);
            }
            if (argument_error == CL_SUCCESS) {
                argument_error = clSetKernelArg(STATE.path_kernel, 6, sizeof(cl_int), &history_row_arg);
            }
            if (argument_error == CL_SUCCESS) {
                argument_error = clSetKernelArg(STATE.path_kernel, 7, sizeof(cl_int), &decision_arg);
            }
            if (argument_error == CL_SUCCESS) {
                argument_error = clSetKernelArg(STATE.path_kernel, 8, sizeof(cl_int), &horizon_arg);
            }
            if (argument_error == CL_SUCCESS) {
                argument_error = clSetKernelArg(STATE.path_kernel, 9, sizeof(cl_int), &iteration_arg);
            }
            if (argument_error == CL_SUCCESS) {
                argument_error = clSetKernelArg(STATE.path_kernel, 10, sizeof(cl_long), &seed_arg);
            }
            if (argument_error == CL_SUCCESS) {
                argument_error = clSetKernelArg(STATE.path_kernel, 11, sizeof(cl_int), &shock_arg);
            }
            if (argument_error == CL_SUCCESS) {
                argument_error = clSetKernelArg(STATE.path_kernel, 12, sizeof(cl_int), &volatility_arg);
            }
            if (argument_error == CL_SUCCESS) {
                argument_error = clSetKernelArg(STATE.path_kernel, 13, sizeof(double), &decay_arg);
            }
            if (argument_error == CL_SUCCESS) {
                argument_error = clSetKernelArg(STATE.path_kernel, 14, sizeof(cl_mem), &device_samples);
            }
            if (argument_error == CL_SUCCESS) {
                argument_error = clSetKernelArg(STATE.path_kernel, 15, sizeof(cl_mem), &device_status);
            }
            if (argument_error != CL_SUCCESS) {
                snprintf(error, sizeof(error), "path kernel arguments: %s", cl_error_string(argument_error));
                throw_java(environment, error);
                goto cleanup;
            }
            {
                cl_int launch_status = clEnqueueNDRangeKernel(STATE.queue, STATE.path_kernel, 1, NULL, &global_paths,
                                                              NULL, 0, NULL, &events[event_count]);
                if (launch_status != CL_SUCCESS) {
                    snprintf(error, sizeof(error), "path kernel launch: %s", cl_error_string(launch_status));
                    throw_java(environment, error);
                    goto cleanup;
                }
                event_kinds[event_count++] = 1;
            }

            cl_int blocks_arg = (cl_int)path_blocks;
            argument_error = clSetKernelArg(STATE.moments_partial_kernel, 0, sizeof(cl_mem), &device_samples);
            if (argument_error == CL_SUCCESS) {
                argument_error = clSetKernelArg(STATE.moments_partial_kernel, 1, sizeof(cl_int), &iteration_arg);
            }
            if (argument_error == CL_SUCCESS) {
                argument_error =
                        clSetKernelArg(STATE.moments_partial_kernel, 2, sizeof(cl_mem), &device_partials);
            }
            if (argument_error == CL_SUCCESS) {
                argument_error = clSetKernelArg(STATE.moments_partial_kernel, 3, sizeof(cl_mem),
                                                &device_partial_sqsums);
            }
            if (argument_error == CL_SUCCESS) {
                argument_error = clSetKernelArg(STATE.moments_partial_kernel, 4, sizeof(cl_mem), &device_status);
            }
            if (argument_error != CL_SUCCESS) {
                snprintf(error, sizeof(error), "moments partial kernel arguments: %s",
                         cl_error_string(argument_error));
                throw_java(environment, error);
                goto cleanup;
            }
            {
                size_t global_moment = path_blocks * moment_threads;
                cl_int launch_status = clEnqueueNDRangeKernel(STATE.queue, STATE.moments_partial_kernel, 1, NULL,
                                                              &global_moment, &moment_threads, 0, NULL,
                                                              &events[event_count]);
                if (launch_status != CL_SUCCESS) {
                    snprintf(error, sizeof(error), "moments partial kernel launch: %s",
                             cl_error_string(launch_status));
                    throw_java(environment, error);
                    goto cleanup;
                }
                event_kinds[event_count++] = 2;
            }
            argument_error = clSetKernelArg(STATE.moments_finalize_kernel, 0, sizeof(cl_mem), &device_partials);
            if (argument_error == CL_SUCCESS) {
                argument_error = clSetKernelArg(STATE.moments_finalize_kernel, 1, sizeof(cl_mem),
                                                &device_partial_sqsums);
            }
            if (argument_error == CL_SUCCESS) {
                argument_error = clSetKernelArg(STATE.moments_finalize_kernel, 2, sizeof(cl_int), &blocks_arg);
            }
            if (argument_error == CL_SUCCESS) {
                argument_error = clSetKernelArg(STATE.moments_finalize_kernel, 3, sizeof(cl_int), &iteration_arg);
            }
            if (argument_error == CL_SUCCESS) {
                argument_error = clSetKernelArg(STATE.moments_finalize_kernel, 4, sizeof(cl_mem), &device_samples);
            }
            if (argument_error == CL_SUCCESS) {
                argument_error = clSetKernelArg(STATE.moments_finalize_kernel, 5, sizeof(cl_mem), &device_summary);
            }
            if (argument_error == CL_SUCCESS) {
                argument_error = clSetKernelArg(STATE.moments_finalize_kernel, 6, sizeof(cl_mem), &device_status);
            }
            if (argument_error != CL_SUCCESS) {
                snprintf(error, sizeof(error), "moments finalize kernel arguments: %s",
                         cl_error_string(argument_error));
                throw_java(environment, error);
                goto cleanup;
            }
            {
                cl_int launch_status = clEnqueueNDRangeKernel(STATE.queue, STATE.moments_finalize_kernel, 1, NULL,
                                                              &moment_threads, &moment_threads, 0, NULL,
                                                              &events[event_count]);
                if (launch_status != CL_SUCCESS) {
                    snprintf(error, sizeof(error), "moments finalize kernel launch: %s",
                             cl_error_string(launch_status));
                    throw_java(environment, error);
                    goto cleanup;
                }
                event_kinds[event_count++] = 2;
            }

            if (use_parallel) {
                argument_error = clSetKernelArg(STATE.bitonic_parallel, 0, sizeof(cl_mem), &device_samples);
                if (argument_error == CL_SUCCESS) {
                    argument_error = clSetKernelArg(STATE.bitonic_parallel, 1, sizeof(cl_int), &sort_n);
                }
                if (argument_error != CL_SUCCESS) {
                    snprintf(error, sizeof(error), "bitonic kernel arguments: %s", cl_error_string(argument_error));
                    throw_java(environment, error);
                    goto cleanup;
                }
                {
                    cl_int launch_status = clEnqueueNDRangeKernel(STATE.queue, STATE.bitonic_parallel, 1, NULL,
                                                                  &local_sort, &local_sort, 0, NULL,
                                                                  &events[event_count]);
                    if (launch_status != CL_SUCCESS) {
                        snprintf(error, sizeof(error), "bitonic kernel launch: %s", cl_error_string(launch_status));
                        throw_java(environment, error);
                        goto cleanup;
                    }
                }
            } else {
                argument_error = clSetKernelArg(STATE.bitonic_serial, 0, sizeof(cl_mem), &device_samples);
                if (argument_error == CL_SUCCESS) {
                    argument_error = clSetKernelArg(STATE.bitonic_serial, 1, sizeof(cl_int), &sort_n);
                }
                if (argument_error != CL_SUCCESS) {
                    snprintf(error, sizeof(error), "bitonic serial kernel arguments: %s",
                             cl_error_string(argument_error));
                    throw_java(environment, error);
                    goto cleanup;
                }
                {
                    cl_int launch_status = clEnqueueNDRangeKernel(STATE.queue, STATE.bitonic_serial, 1, NULL, &one,
                                                                  NULL, 0, NULL, &events[event_count]);
                    if (launch_status != CL_SUCCESS) {
                        snprintf(error, sizeof(error), "bitonic serial kernel launch: %s",
                                 cl_error_string(launch_status));
                        throw_java(environment, error);
                        goto cleanup;
                    }
                }
            }
            event_kinds[event_count++] = 2;
        }

        {
            cl_int count_arg = iteration_count;
            cl_int probability_arg = quantile_count;
            cl_int argument_error = clSetKernelArg(STATE.quantile_kernel, 0, sizeof(cl_mem), &device_samples);
            if (argument_error == CL_SUCCESS) {
                argument_error = clSetKernelArg(STATE.quantile_kernel, 1, sizeof(cl_int), &count_arg);
            }
            if (argument_error == CL_SUCCESS) {
                argument_error = clSetKernelArg(STATE.quantile_kernel, 2, sizeof(cl_mem), &device_quantiles);
            }
            if (argument_error == CL_SUCCESS) {
                argument_error = clSetKernelArg(STATE.quantile_kernel, 3, sizeof(cl_int), &probability_arg);
            }
            if (argument_error == CL_SUCCESS) {
                argument_error = clSetKernelArg(STATE.quantile_kernel, 4, sizeof(cl_mem), &device_summary);
            }
            if (argument_error == CL_SUCCESS) {
                argument_error = clSetKernelArg(STATE.quantile_kernel, 5, sizeof(cl_mem), &device_status);
            }
            if (argument_error != CL_SUCCESS) {
                snprintf(error, sizeof(error), "quantile kernel arguments: %s", cl_error_string(argument_error));
                throw_java(environment, error);
                goto cleanup;
            }
            {
                cl_int launch_status = clEnqueueNDRangeKernel(STATE.queue, STATE.quantile_kernel, 1, NULL, &one, NULL,
                                                              0, NULL, &events[event_count]);
                if (launch_status != CL_SUCCESS) {
                    snprintf(error, sizeof(error), "quantile kernel launch: %s", cl_error_string(launch_status));
                    throw_java(environment, error);
                    goto cleanup;
                }
                event_kinds[event_count++] = 2;
            }
        }

        {
            cl_int read_status = clEnqueueReadBuffer(STATE.queue, device_status, CL_FALSE, 0, sizeof(int),
                                                     &status_host[decision], 0, NULL, &events[event_count]);
            if (read_status != CL_SUCCESS) {
                snprintf(error, sizeof(error), "status transfer: %s", cl_error_string(read_status));
                throw_java(environment, error);
                goto cleanup;
            }
            event_kinds[event_count++] = 0;
            read_status = clEnqueueReadBuffer(STATE.queue, device_summary, CL_FALSE, 0, summary_size * sizeof(double),
                                              payload + row + 1, 0, NULL, &events[event_count]);
            if (read_status != CL_SUCCESS) {
                snprintf(error, sizeof(error), "summary transfer: %s", cl_error_string(read_status));
                throw_java(environment, error);
                goto cleanup;
            }
            event_kinds[event_count++] = 0;
        }
    }

    {
        cl_int finish_status = clFinish(STATE.queue);
        if (finish_status != CL_SUCCESS) {
            snprintf(error, sizeof(error), "forecast pipeline finish: %s", cl_error_string(finish_status));
            throw_java(environment, error);
            goto cleanup;
        }
    }
    transfer_micros = 0.0;
    kernel_micros = 0.0;
    reduction_micros = 0.0;
    while (event_count > 0) {
        --event_count;
        cl_ulong start_ns = 0;
        cl_ulong end_ns = 0;
        if (clGetEventProfilingInfo(events[event_count], CL_PROFILING_COMMAND_START, sizeof(start_ns), &start_ns,
                                    NULL)
                == CL_SUCCESS
            && clGetEventProfilingInfo(events[event_count], CL_PROFILING_COMMAND_END, sizeof(end_ns), &end_ns, NULL)
                == CL_SUCCESS) {
            double span = (double)(end_ns - start_ns) / 1000.0;
            if (event_kinds[event_count] == 1) {
                kernel_micros += span;
            } else if (event_kinds[event_count] == 2) {
                reduction_micros += span;
            } else {
                transfer_micros += span;
            }
        }
        clReleaseEvent(events[event_count]);
        events[event_count] = NULL;
    }
    free(events);
    events = NULL;
    free(event_kinds);
    event_kinds = NULL;
    for (int decision = 0; decision < decision_count; ++decision) {
        if (stable[decision] == 0) {
            continue;
        }
        size_t row = 4U + (size_t)decision * row_length;
        payload[row] = (double)status_host[decision];
        if (status_host[decision] != 0) {
            memset(payload + row + 1, 0, summary_size * sizeof(double));
        }
    }
    payload[1] = transfer_micros;
    payload[2] = kernel_micros;
    payload[3] = reduction_micros;
    payload[0] = now_micros() - total_start;
    result = (*environment)->NewDoubleArray(environment, (jsize)payload_size);
    if (result == NULL) {
        throw_java(environment, "unable to allocate JNI result array");
        goto cleanup;
    }
    (*environment)->SetDoubleArrayRegion(environment, result, 0, (jsize)payload_size, payload);

cleanup:
    /* Best-effort drain so in-flight transfers no longer reference host staging buffers. */
    if (STATE.queue != NULL) {
        clFinish(STATE.queue);
    }
    while (events != NULL && event_count > 0) {
        --event_count;
        if (events[event_count] != NULL) {
            clReleaseEvent(events[event_count]);
            events[event_count] = NULL;
        }
    }
    free(events);
    free(event_kinds);
    release_mem(&device_status);
    release_mem(&device_summary);
    release_mem(&device_quantiles);
    release_mem(&device_history);
    release_mem(&device_samples);
    release_mem(&device_partial_sqsums);
    release_mem(&device_partials);
    free(status_host);
    free(padded_host);
    free(payload);
    free(historical_returns);
    free(variances);
    free(drifts);
    free(means);
    free(prices);
    free(stable);
    free(quantiles);
    pthread_mutex_unlock(&STATE_MUTEX);
    return result;
}

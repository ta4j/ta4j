/*
 * SPDX-License-Identifier: MIT
 */
#include <cuda_runtime.h>
#include <jni.h>

#include <thrust/device_ptr.h>
#include <thrust/sort.h>

#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <limits>
#include <mutex>
#include <sstream>
#include <stdexcept>
#include <string>
#include <vector>

namespace {

constexpr int ABI_VERSION = 1;
constexpr std::uint64_t GOLDEN_GAMMA = 0x9E3779B97F4A7C15ULL;
constexpr double DOUBLE_UNIT = 0x1.0p-53;
constexpr int THREADS_PER_BLOCK = 256;
std::mutex execution_mutex;

void check_cuda(cudaError_t status, const char* operation) {
    if (status != cudaSuccess) {
        throw std::runtime_error(std::string(operation) + ": " + cudaGetErrorString(status));
    }
}

template <typename T>
class device_buffer {
public:
    explicit device_buffer(std::size_t count) : count_(count) {
        if (count_ > 0) {
            check_cuda(cudaMalloc(&value_, count_ * sizeof(T)), "cudaMalloc");
        }
    }

    ~device_buffer() {
        if (value_ != nullptr) {
            cudaFree(value_);
        }
    }

    device_buffer(const device_buffer&) = delete;
    device_buffer& operator=(const device_buffer&) = delete;

    T* get() { return value_; }
    const T* get() const { return value_; }
    std::size_t size() const { return count_; }

private:
    T* value_ = nullptr;
    std::size_t count_;
};

class cuda_event {
public:
    cuda_event() { check_cuda(cudaEventCreate(&value_), "cudaEventCreate"); }
    ~cuda_event() { cudaEventDestroy(value_); }
    cudaEvent_t get() const { return value_; }

private:
    cudaEvent_t value_{};
};

class cuda_stream {
public:
    cuda_stream() { check_cuda(cudaStreamCreateWithFlags(&value_, cudaStreamNonBlocking), "cudaStreamCreate"); }
    ~cuda_stream() { cudaStreamDestroy(value_); }
    cudaStream_t get() const { return value_; }

private:
    cudaStream_t value_{};
};

__host__ __device__ std::uint64_t mix64(std::uint64_t value) {
    value = (value ^ (value >> 30)) * 0xBF58476D1CE4E5B9ULL;
    value = (value ^ (value >> 27)) * 0x94D049BB133111EBULL;
    return value ^ (value >> 31);
}

class path_random {
public:
    __device__ path_random(std::int64_t seed, int decision_index, int horizon, int path_index) {
        std::uint64_t value = static_cast<std::uint64_t>(seed);
        value = mix64(value ^ (static_cast<std::uint64_t>(static_cast<std::uint32_t>(decision_index))
                               * 0xD1B54A32D192ED03ULL));
        value = mix64(value ^ (static_cast<std::uint64_t>(static_cast<std::uint32_t>(horizon))
                               * 0x94D049BB133111EBULL));
        state_ = mix64(value ^ (static_cast<std::uint64_t>(static_cast<std::uint32_t>(path_index))
                                * 0xDB4F0B9175AE2165ULL));
    }

    __device__ int next_int(int bound) {
        while (true) {
            std::uint64_t candidate = next_long() >> 1;
            std::uint64_t remainder = candidate % static_cast<std::uint64_t>(bound);
            std::uint64_t sum = candidate - remainder + static_cast<std::uint64_t>(bound - 1);
            if (sum <= static_cast<std::uint64_t>(std::numeric_limits<std::int64_t>::max())) {
                return static_cast<int>(remainder);
            }
        }
    }

    __device__ double next_gaussian() {
        double radius = sqrt(-2.0 * log(1.0 - next_double()));
        return radius * cos(2.0 * 3.141592653589793238462643383279502884 * next_double());
    }

private:
    __device__ double next_double() { return static_cast<double>(next_long() >> 11) * DOUBLE_UNIT; }

    __device__ std::uint64_t next_long() {
        state_ += GOLDEN_GAMMA;
        return mix64(state_);
    }

    std::uint64_t state_{};
};

__global__ void path_kernel(double price, double mean, double drift, double variance,
                            const double* historical_returns, int lookback, int decision_index,
                            int horizon, int iteration_count, std::int64_t seed, int shock_model,
                            int volatility_mode, double decay, double* samples, int* status) {
    int path_index = blockIdx.x * blockDim.x + threadIdx.x;
    if (path_index >= iteration_count) {
        return;
    }
    path_random random(seed, decision_index, horizon, path_index);
    double current_mean = mean;
    double current_variance = variance;
    double volatility = sqrt(variance);
    double standardized_mean = mean;
    double standardized_volatility = volatility;
    double cumulative_return = 0.0;
    for (int step = 0; step < horizon; ++step) {
        double shock;
        if (shock_model == 0) {
            shock = historical_returns[random.next_int(lookback)];
        } else if (shock_model == 1) {
            shock = standardized_volatility == 0.0
                    ? 0.0
                    : (historical_returns[random.next_int(lookback)] - standardized_mean) / standardized_volatility;
        } else {
            shock = random.next_gaussian();
        }
        double step_return = shock_model == 0 ? shock : drift + volatility * shock;
        cumulative_return += step_return;
        if (volatility_mode == 1) {
            double deviation = step_return - current_mean;
            current_mean = current_mean * decay + step_return * (1.0 - decay);
            current_variance = current_variance * decay + deviation * deviation * (1.0 - decay);
            volatility = sqrt(current_variance);
        }
    }
    double growth = exp(cumulative_return);
    double terminal = price * growth;
    if (!isfinite(cumulative_return) || fabs(cumulative_return) > 700.0 || !isfinite(growth) || !isfinite(terminal)
            || (terminal == 0.0 && growth != 0.0)) {
        atomicExch(status, 2);
        samples[path_index] = 0.0;
        return;
    }
    samples[path_index] = terminal;
}

__global__ void moments_kernel(const double* samples, int count, double* summary, int* status) {
    if (blockIdx.x != 0 || threadIdx.x != 0 || *status != 0) {
        return;
    }
    double mean = 0.0;
    double m2 = 0.0;
    for (int i = 0; i < count; ++i) {
        double value = samples[i];
        if (!isfinite(value)) {
            *status = 2;
            return;
        }
        double delta = value - mean;
        mean += delta / static_cast<double>(i + 1);
        m2 += delta * (value - mean);
    }
    double variance = m2 / static_cast<double>(count);
    double standard_deviation = variance <= 0.0 ? 0.0 : sqrt(variance);
    if (!isfinite(mean) || !isfinite(standard_deviation)) {
        *status = 2;
        return;
    }
    summary[0] = mean;
    summary[2] = standard_deviation;
}

__device__ double percentile(const double* sorted_samples, int count, double probability) {
    if (count == 1) {
        return sorted_samples[0];
    }
    double position = probability * static_cast<double>(count - 1);
    int lower = static_cast<int>(floor(position));
    int upper = static_cast<int>(ceil(position));
    if (lower == upper) {
        return sorted_samples[lower];
    }
    return sorted_samples[lower] + (sorted_samples[upper] - sorted_samples[lower])
            * (position - static_cast<double>(lower));
}

__global__ void quantile_kernel(const double* sorted_samples, int count, const double* probabilities,
                                int probability_count, double* summary, int* status) {
    if (blockIdx.x != 0 || threadIdx.x != 0 || *status != 0) {
        return;
    }
    summary[1] = percentile(sorted_samples, count, 0.5);
    for (int i = 0; i < probability_count; ++i) {
        summary[3 + i] = percentile(sorted_samples, count, probabilities[i]);
    }
}

__global__ void rng_self_test_kernel(int* bounded, double* gaussian) {
    if (blockIdx.x == 0 && threadIdx.x == 0) {
        path_random random(42, 317, 12, 5);
        *bounded = random.next_int(7);
        path_random gaussian_random(42, 317, 12, 5);
        *gaussian = gaussian_random.next_gaussian();
    }
}

float elapsed_micros(cuda_event& start, cuda_event& finish) {
    check_cuda(cudaEventSynchronize(finish.get()), "cudaEventSynchronize");
    float milliseconds = 0.0F;
    check_cuda(cudaEventElapsedTime(&milliseconds, start.get(), finish.get()), "cudaEventElapsedTime");
    return milliseconds * 1000.0F;
}

void throw_java(JNIEnv* environment, const std::string& message) {
    jclass type = environment->FindClass("java/lang/IllegalStateException");
    if (type != nullptr) {
        std::string bounded = message.substr(0, 1024);
        environment->ThrowNew(type, bounded.c_str());
    }
}

std::string sanitize(std::string value) {
    std::replace(value.begin(), value.end(), '|', '/');
    return value;
}

std::vector<double> copy_doubles(JNIEnv* environment, jdoubleArray source, jsize expected, const char* name) {
    if (source == nullptr || environment->GetArrayLength(source) != expected) {
        throw std::invalid_argument(std::string(name) + " length mismatch");
    }
    std::vector<double> values(static_cast<std::size_t>(expected));
    environment->GetDoubleArrayRegion(source, 0, expected, values.data());
    return values;
}

std::vector<int> copy_ints(JNIEnv* environment, jintArray source, jsize expected, const char* name) {
    if (source == nullptr || environment->GetArrayLength(source) != expected) {
        throw std::invalid_argument(std::string(name) + " length mismatch");
    }
    std::vector<int> values(static_cast<std::size_t>(expected));
    environment->GetIntArrayRegion(source, 0, expected, values.data());
    return values;
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_org_ta4j_cli_acceleration_internal_providers_JniCudaNativeBridge_nativeProbe(
        JNIEnv* environment, jclass, jint abi_version) {
    try {
        std::lock_guard<std::mutex> guard(execution_mutex);
        if (abi_version != ABI_VERSION) {
            return environment->NewStringUTF("ERROR|||||||0|ABI mismatch");
        }
        int driver_version = 0;
        int runtime_version = 0;
        int device_count = 0;
        check_cuda(cudaDriverGetVersion(&driver_version), "cudaDriverGetVersion");
        check_cuda(cudaRuntimeGetVersion(&runtime_version), "cudaRuntimeGetVersion");
        check_cuda(cudaGetDeviceCount(&device_count), "cudaGetDeviceCount");
        if (device_count < 1) {
            return environment->NewStringUTF("ERROR|||||||0|No CUDA devices");
        }
        check_cuda(cudaSetDevice(0), "cudaSetDevice");
        cudaDeviceProp properties{};
        check_cuda(cudaGetDeviceProperties(&properties, 0), "cudaGetDeviceProperties");
        std::size_t free_memory = 0;
        std::size_t total_memory = 0;
        check_cuda(cudaMemGetInfo(&free_memory, &total_memory), "cudaMemGetInfo");
        cuda_stream stream;
        cuda_event event;
        device_buffer<int> bounded(1);
        device_buffer<double> gaussian(1);
        rng_self_test_kernel<<<1, 1, 0, stream.get()>>>(bounded.get(), gaussian.get());
        check_cuda(cudaGetLastError(), "rng_self_test_kernel launch");
        int bounded_value = -1;
        double gaussian_value = 0.0;
        check_cuda(cudaMemcpyAsync(&bounded_value, bounded.get(), sizeof(int), cudaMemcpyDeviceToHost, stream.get()),
                   "self-test bounded copy");
        check_cuda(cudaMemcpyAsync(&gaussian_value, gaussian.get(), sizeof(double), cudaMemcpyDeviceToHost,
                                   stream.get()), "self-test Gaussian copy");
        check_cuda(cudaStreamSynchronize(stream.get()), "self-test synchronization");
        if (bounded_value != 2 || std::abs(gaussian_value - (-1.3318445490451813)) > 1e-12) {
            throw std::runtime_error("deterministic RNG self-test mismatch");
        }

        device_buffer<double> self_test_history(1);
        device_buffer<double> self_test_samples(2);
        device_buffer<double> self_test_quantiles(1);
        device_buffer<double> self_test_summary(4);
        device_buffer<int> self_test_status(1);
        double zero = 0.0;
        double median_probability = 0.5;
        check_cuda(cudaMemcpyAsync(self_test_history.get(), &zero, sizeof(double), cudaMemcpyHostToDevice,
                                   stream.get()), "forecast self-test history copy");
        check_cuda(cudaMemcpyAsync(self_test_quantiles.get(), &median_probability, sizeof(double),
                                   cudaMemcpyHostToDevice, stream.get()), "forecast self-test quantile copy");
        check_cuda(cudaMemsetAsync(self_test_status.get(), 0, sizeof(int), stream.get()),
                   "forecast self-test status reset");
        path_kernel<<<1, 2, 0, stream.get()>>>(100.0, 0.0, 0.0, 0.0, self_test_history.get(), 1, 0, 1, 2,
                                               42, 2, 0, 0.94, self_test_samples.get(), self_test_status.get());
        check_cuda(cudaGetLastError(), "forecast self-test path launch");
        moments_kernel<<<1, 1, 0, stream.get()>>>(self_test_samples.get(), 2, self_test_summary.get(),
                                                  self_test_status.get());
        check_cuda(cudaGetLastError(), "forecast self-test moments launch");
        thrust::device_ptr<double> self_test_begin(self_test_samples.get());
        thrust::sort(thrust::cuda::par.on(stream.get()), self_test_begin, self_test_begin + 2);
        quantile_kernel<<<1, 1, 0, stream.get()>>>(self_test_samples.get(), 2, self_test_quantiles.get(), 1,
                                                   self_test_summary.get(), self_test_status.get());
        check_cuda(cudaGetLastError(), "forecast self-test quantile launch");
        int forecast_status = -1;
        double forecast_summary[4]{};
        check_cuda(cudaMemcpyAsync(&forecast_status, self_test_status.get(), sizeof(int), cudaMemcpyDeviceToHost,
                                   stream.get()), "forecast self-test status copy");
        check_cuda(cudaMemcpyAsync(forecast_summary, self_test_summary.get(), sizeof(forecast_summary),
                                   cudaMemcpyDeviceToHost, stream.get()), "forecast self-test summary copy");
        check_cuda(cudaStreamSynchronize(stream.get()), "forecast self-test synchronization");
        if (forecast_status != 0 || std::abs(forecast_summary[0] - 100.0) > 1e-12
                || std::abs(forecast_summary[1] - 100.0) > 1e-12 || forecast_summary[2] != 0.0
                || std::abs(forecast_summary[3] - 100.0) > 1e-12) {
            throw std::runtime_error("forecast kernel self-test mismatch");
        }
        std::ostringstream payload;
        payload << "OK|" << sanitize(properties.name) << '|' << properties.major << '|' << properties.minor << '|'
                << free_memory << '|' << total_memory << '|' << driver_version << '|' << runtime_version
                << "|self-test passed";
        return environment->NewStringUTF(payload.str().c_str());
    } catch (const std::exception& exception) {
        std::string payload = "ERROR|||||||0|" + sanitize(exception.what());
        return environment->NewStringUTF(payload.c_str());
    }
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_org_ta4j_cli_acceleration_internal_providers_JniCudaNativeBridge_nativeEvaluate(
        JNIEnv* environment, jclass, jint abi_version, jint from_inclusive, jint decision_count, jint horizon,
        jint iteration_count, jint lookback, jlong seed, jint shock_model, jint volatility_mode, jdouble decay,
        jdoubleArray quantiles_array, jintArray stable_array, jdoubleArray prices_array, jdoubleArray means_array,
        jdoubleArray drifts_array, jdoubleArray variances_array, jdoubleArray historical_returns_array) {
    try {
        std::lock_guard<std::mutex> guard(execution_mutex);
        auto total_start = std::chrono::steady_clock::now();
        if (abi_version != ABI_VERSION || decision_count < 1 || horizon < 1 || iteration_count < 1 || lookback < 1
                || shock_model < 0 || shock_model > 2 || volatility_mode < 0 || volatility_mode > 1
                || !(decay > 0.0 && decay < 1.0)) {
            throw std::invalid_argument("invalid CUDA ABI or request metadata");
        }
        jsize quantile_count = environment->GetArrayLength(quantiles_array);
        if (quantile_count < 1) {
            throw std::invalid_argument("at least one quantile is required");
        }
        std::size_t history_count = static_cast<std::size_t>(decision_count) * static_cast<std::size_t>(lookback);
        if (history_count > static_cast<std::size_t>(std::numeric_limits<jsize>::max())) {
            throw std::invalid_argument("historical return buffer exceeds JNI limits");
        }
        std::vector<double> quantiles = copy_doubles(environment, quantiles_array, quantile_count, "quantiles");
        std::vector<int> stable = copy_ints(environment, stable_array, decision_count, "stable");
        std::vector<double> prices = copy_doubles(environment, prices_array, decision_count, "prices");
        std::vector<double> means = copy_doubles(environment, means_array, decision_count, "means");
        std::vector<double> drifts = copy_doubles(environment, drifts_array, decision_count, "drifts");
        std::vector<double> variances = copy_doubles(environment, variances_array, decision_count, "variances");
        std::vector<double> historical_returns = copy_doubles(environment, historical_returns_array,
                                                              static_cast<jsize>(history_count), "historicalReturns");

        int row_length = 4 + quantile_count;
        std::vector<double> payload(4 + static_cast<std::size_t>(decision_count) * row_length, 0.0);
        device_buffer<double> device_samples(static_cast<std::size_t>(iteration_count));
        device_buffer<double> device_history(static_cast<std::size_t>(lookback));
        device_buffer<double> device_quantiles(static_cast<std::size_t>(quantile_count));
        device_buffer<double> device_summary(static_cast<std::size_t>(3 + quantile_count));
        device_buffer<int> device_status(1);
        cuda_stream stream;
        check_cuda(cudaMemcpyAsync(device_quantiles.get(), quantiles.data(), quantile_count * sizeof(double),
                                   cudaMemcpyHostToDevice, stream.get()), "quantile transfer");
        check_cuda(cudaStreamSynchronize(stream.get()), "quantile synchronization");

        double transfer_micros = 0.0;
        double kernel_micros = 0.0;
        double reduction_micros = 0.0;
        std::vector<double> summary(static_cast<std::size_t>(3 + quantile_count));
        for (int decision = 0; decision < decision_count; ++decision) {
            std::size_t row = 4 + static_cast<std::size_t>(decision) * row_length;
            if (stable[decision] == 0) {
                payload[row] = 1.0;
                continue;
            }
            auto transfer_start = std::chrono::steady_clock::now();
            const double* history = historical_returns.data() + static_cast<std::size_t>(decision) * lookback;
            check_cuda(cudaMemcpyAsync(device_history.get(), history, lookback * sizeof(double), cudaMemcpyHostToDevice,
                                       stream.get()), "historical return transfer");
            check_cuda(cudaMemsetAsync(device_status.get(), 0, sizeof(int), stream.get()), "status reset");
            check_cuda(cudaStreamSynchronize(stream.get()), "input synchronization");
            transfer_micros += std::chrono::duration<double, std::micro>(
                    std::chrono::steady_clock::now() - transfer_start).count();

            cuda_event kernel_start;
            cuda_event kernel_finish;
            check_cuda(cudaEventRecord(kernel_start.get(), stream.get()), "kernel start event");
            int blocks = (iteration_count + THREADS_PER_BLOCK - 1) / THREADS_PER_BLOCK;
            path_kernel<<<blocks, THREADS_PER_BLOCK, 0, stream.get()>>>(
                    prices[decision], means[decision], drifts[decision], variances[decision], device_history.get(),
                    lookback, from_inclusive + decision, horizon, iteration_count, static_cast<std::int64_t>(seed),
                    shock_model, volatility_mode, decay, device_samples.get(), device_status.get());
            check_cuda(cudaGetLastError(), "forecast kernel launch");
            check_cuda(cudaEventRecord(kernel_finish.get(), stream.get()), "kernel finish event");
            kernel_micros += elapsed_micros(kernel_start, kernel_finish);

            cuda_event reduction_start;
            cuda_event reduction_finish;
            check_cuda(cudaEventRecord(reduction_start.get(), stream.get()), "reduction start event");
            moments_kernel<<<1, 1, 0, stream.get()>>>(device_samples.get(), iteration_count, device_summary.get(),
                                                     device_status.get());
            check_cuda(cudaGetLastError(), "moments kernel launch");
            check_cuda(cudaStreamSynchronize(stream.get()), "moments synchronization");
            thrust::device_ptr<double> begin(device_samples.get());
            thrust::sort(thrust::cuda::par.on(stream.get()), begin, begin + iteration_count);
            quantile_kernel<<<1, 1, 0, stream.get()>>>(device_samples.get(), iteration_count, device_quantiles.get(),
                                                      quantile_count, device_summary.get(), device_status.get());
            check_cuda(cudaGetLastError(), "quantile kernel launch");
            check_cuda(cudaEventRecord(reduction_finish.get(), stream.get()), "reduction finish event");
            reduction_micros += elapsed_micros(reduction_start, reduction_finish);

            int status = 0;
            auto output_start = std::chrono::steady_clock::now();
            check_cuda(cudaMemcpyAsync(&status, device_status.get(), sizeof(int), cudaMemcpyDeviceToHost, stream.get()),
                       "status transfer");
            check_cuda(cudaMemcpyAsync(summary.data(), device_summary.get(), summary.size() * sizeof(double),
                                       cudaMemcpyDeviceToHost, stream.get()), "summary transfer");
            check_cuda(cudaStreamSynchronize(stream.get()), "output synchronization");
            transfer_micros += std::chrono::duration<double, std::micro>(
                    std::chrono::steady_clock::now() - output_start).count();
            payload[row] = static_cast<double>(status);
            if (status == 0) {
                std::copy(summary.begin(), summary.end(), payload.begin() + static_cast<std::ptrdiff_t>(row + 1));
            }
        }
        payload[1] = transfer_micros;
        payload[2] = kernel_micros;
        payload[3] = reduction_micros;
        payload[0] = std::chrono::duration<double, std::micro>(
                std::chrono::steady_clock::now() - total_start).count();
        jdoubleArray result = environment->NewDoubleArray(static_cast<jsize>(payload.size()));
        if (result == nullptr) {
            throw std::runtime_error("unable to allocate JNI result array");
        }
        environment->SetDoubleArrayRegion(result, 0, static_cast<jsize>(payload.size()), payload.data());
        return result;
    } catch (const std::exception& exception) {
        throw_java(environment, exception.what());
        return nullptr;
    }
}

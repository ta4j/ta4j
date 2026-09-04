/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.acceleration.internal.providers;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.ta4j.core.acceleration.AccelerationRuntime;
import org.ta4j.core.acceleration.AccelerationRuntime.Assessment;
import org.ta4j.core.acceleration.AccelerationRuntime.Backend;
import org.ta4j.core.acceleration.AccelerationRuntime.Determinism;
import org.ta4j.core.acceleration.AccelerationRuntime.DiagnosticCode;
import org.ta4j.core.acceleration.AccelerationRuntime.KernelRequest;
import org.ta4j.core.acceleration.AccelerationRuntime.KernelResult;
import org.ta4j.core.acceleration.AccelerationRuntime.NumericEncoding;
import org.ta4j.core.acceleration.AccelerationRuntime.Operation;
import org.ta4j.core.acceleration.AccelerationRuntime.Provider;

/**
 * Template for sample-output native providers behind the versioned
 * {@link Operation#MONTE_CARLO_SHOCK_PATHS_V1} kernel contract.
 *
 * <p>
 * The core owns indicator recognition, eligibility, snapshotting, validation,
 * scalar fallback, and forecast reconstruction. Providers only answer two
 * questions over immutable {@link KernelRequest} primitives: {@link #assess},
 * which predicts the total offload cost without initializing native code, and
 * {@link #execute}, which returns the raw per-sample terminal prices.
 *
 * <p>
 * Assessment never loads libraries, creates contexts, or allocates request
 * buffers: library presence is answered from configuration and packaged
 * resources, and memory rejection runs on arithmetic alone before any input or
 * output materialization.
 *
 * @since 0.24.2
 */
abstract class ShockPathKernelProvider implements Provider {

    /** Quantile vector handed to sample kernels; core decodes quantiles itself. */
    private static final double[] NO_QUANTILES = new double[0];

    private final Backend backend;
    private final String providerId;
    private final String maxMemoryProperty;
    private final long defaultMaxMemoryBytes;
    private final boolean exactCapable;
    private final boolean approximateCapable;

    private volatile boolean resident;
    private volatile String probedDevice;
    private volatile long probedCeilingBytes;

    ShockPathKernelProvider(Backend backend, String providerId, String maxMemoryProperty, long defaultMaxMemoryBytes,
            boolean exactCapable, boolean approximateCapable) {
        this.backend = Objects.requireNonNull(backend, "backend must not be null");
        this.providerId = Objects.requireNonNull(providerId, "providerId must not be null");
        this.maxMemoryProperty = Objects.requireNonNull(maxMemoryProperty, "maxMemoryProperty must not be null");
        this.defaultMaxMemoryBytes = defaultMaxMemoryBytes;
        this.exactCapable = exactCapable;
        this.approximateCapable = approximateCapable;
    }

    @Override
    public final String providerId() {
        return providerId;
    }

    @Override
    public final Assessment assess(KernelRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (request.operation() != Operation.MONTE_CARLO_SHOCK_PATHS_V1) {
            return unsupported(DiagnosticCode.UNSUPPORTED,
                    providerId + " implements only MONTE_CARLO_SHOCK_PATHS_V1, not " + request.operation());
        }
        if (request.numeric() != NumericEncoding.FLOAT64) {
            return unsupported(DiagnosticCode.UNSUPPORTED,
                    providerId + " consumes FLOAT64 buffers, not " + request.numeric());
        }
        boolean exact = request.determinism() == Determinism.BITWISE_IDENTICAL;
        if (!exact && Double.isNaN(request.tolerance())) {
            return unsupported(DiagnosticCode.UNSUPPORTED,
                    providerId + " approximate requests require a finite positive tolerance");
        }
        if (exact ? !exactCapable : !approximateCapable) {
            return unsupported(DiagnosticCode.PROVIDER_UNAVAILABLE, accuracyDetail(request, exact));
        }
        if (!libraryPresent()) {
            return unsupported(DiagnosticCode.PROVIDER_UNAVAILABLE, libraryDetail());
        }
        Dimensions dimensions;
        try {
            dimensions = dimensions(request);
        } catch (ArithmeticException exception) {
            return unsupported(DiagnosticCode.UNSUPPORTED,
                    providerId + " request dimensions overflow: " + exception.getMessage());
        }
        long ceiling = memoryCeiling();
        if (ceiling <= 0L) {
            return unsupported(DiagnosticCode.PROVIDER_UNAVAILABLE, maxMemoryProperty + " must be > 0");
        }
        if (dimensions.bytesPerDecision() > ceiling) {
            return unsupported(DiagnosticCode.PROVIDER_UNAVAILABLE,
                    providerId + " needs %,d bytes per decision, above the %,d-byte provider ceiling"
                            .formatted(dimensions.bytesPerDecision(), ceiling));
        }
        String family = deviceFamily();
        long predicted = ShockPathQualification.predictedTotalNanos(backend, request.operation().version(), family,
                dimensions.steps(), dimensions.stagedBytes(), resident);
        long peak = Math.min(dimensions.peakBytes(), ceiling);
        return Assessment.supported(backend, deviceId(), predicted, peak, exact ? exactCapable : approximateCapable);
    }

    @Override
    public final KernelResult execute(KernelRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        long started = System.nanoTime();
        Dimensions dimensions = dimensions(request);
        SampleKernel kernel = ensureKernel();
        double[] params = request.params();
        List<double[]> inputs = request.inputs();
        double[] raw = new double[request.expectedOutputLength()];
        double totalMicros = 0d;
        int offset = 0;
        while (true) {
            int chunk = decisionsPerChunk(dimensions, memoryCeiling());
            int from = Math.addExact(request.fromInclusive(), offset);
            int count = Math.min(chunk, dimensions.decisions() - offset);
            NativeForecastRequest nativeRequest = nativeChunk(request, params, inputs, dimensions, from, count);
            SampleKernel.SampleResult chunkResult;
            try {
                chunkResult = kernel.evaluateSamples(nativeRequest);
            } catch (LinkageError | RuntimeException exception) {
                throw new NativeProviderException(backendName(), exception);
            }
            float[] samples = chunkResult.terminalPrices();
            int expected = Math.multiplyExact(count, dimensions.iterations());
            if (samples.length != expected) {
                throw new NativeProviderException(backendName() + " returned " + samples.length + " samples, expected "
                        + expected + " for " + count + " decisions");
            }
            for (int index = 0; index < samples.length; index++) {
                raw[offset * dimensions.iterations() + index] = samples[index];
            }
            totalMicros += chunkResult.totalMicros();
            offset += count;
            if (offset >= dimensions.decisions()) {
                break;
            }
        }
        resident = true;
        return new KernelResult(raw, true, System.nanoTime() - started);
    }

    /**
     * Answers library presence without loading native code: an explicit configured
     * path or a packaged classifier resource.
     */
    abstract boolean libraryPresent();

    /** Detail for the unavailable diagnostic when no library is present. */
    abstract String libraryDetail();

    /** Loads the library, probes the device, and returns the sample kernel. */
    abstract SampleKernel ensureKernel();

    /** Device family used for qualification when no probe has run yet. */
    String deviceFamily() {
        return System.getProperty(ShockPathQualification.familyProperty(backend), "generic")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    /** Device label reported in assessments. */
    String deviceId() {
        String probed = probedDevice;
        return backendName() + "/" + (probed == null ? deviceFamily() : probed);
    }

    /** Device name from the native probe, or null before the first execution. */
    final String probedDevice() {
        return probedDevice;
    }

    /**
     * Records a successful native probe; later assessments report the device and
     * honor its ceiling.
     */
    final void recordProbe(String device, long ceilingBytes) {
        probedDevice = Objects.requireNonNull(device, "device must not be null");
        probedCeilingBytes = Math.max(1L, ceilingBytes);
    }

    private String backendName() {
        return backend.name().toLowerCase(Locale.ROOT);
    }

    private Assessment unsupported(DiagnosticCode code, String detail) {
        return Assessment.unsupported(backend, deviceId(), code, providerId, detail);
    }

    /**
     * Explains why a request's accuracy mode is unsupported. Backends whose native
     * lane cannot serve versioned sample output override this.
     */
    String accuracyDetail(KernelRequest request, boolean exact) {
        if (exact) {
            return providerId + " is not exact-capable; exact requests stay scalar"
                    + (approximateCapable
                            ? " unless -D" + AccelerationRuntime.APPROXIMATE_TOLERANCE_PROPERTY
                                    + " opts into an approximate tolerance"
                            : "");
        }
        return providerId + " serves exact requests only and declines approximate tolerance " + request.tolerance();
    }

    private long memoryCeiling() {
        long configured = Long.getLong(maxMemoryProperty, defaultMaxMemoryBytes);
        long probed = probedCeilingBytes;
        return probed <= 0L ? configured : Math.min(configured, probed);
    }

    /**
     * Derives chunking dimensions from request primitives alone. The params layout
     * is owned by the core shock-path planner: shock model, volatility mode,
     * horizon, iteration count, lookback, decay factor. No buffers are allocated
     * here; rejection stays ahead of materialization.
     */
    private Dimensions dimensions(KernelRequest request) {
        double[] params = request.params();
        int horizon = (int) params[2];
        int iterations = (int) params[3];
        int lookback = (int) params[4];
        if (horizon < 1 || iterations < 1 || lookback < 1) {
            throw new ArithmeticException("kernel params must be positive");
        }
        int decisions = request.size();
        long steps = Math.multiplyExact(Math.multiplyExact((long) decisions, iterations), horizon);
        long perDecisionInputs = Math.addExact(Math.addExact(5L * Double.BYTES, Integer.BYTES),
                Math.multiplyExact((long) lookback, Double.BYTES));
        long stagedBytes = Math.multiplyExact(Math.multiplyExact((long) decisions, perDecisionInputs), 2L);
        long outputBytes = Math.multiplyExact(Math.multiplyExact((long) decisions, iterations), (long) Float.BYTES);
        long bytesPerDecision = Math.addExact(perDecisionInputs * 2L, Math.multiplyExact((long) iterations, 68L));
        long peakBytes = Math.addExact(Math.addExact(stagedBytes, outputBytes), 256L);
        return new Dimensions(decisions, horizon, iterations, lookback, steps, stagedBytes, bytesPerDecision,
                peakBytes);
    }

    private int decisionsPerChunk(Dimensions dimensions, long ceiling) {
        long capacity = ceiling / dimensions.bytesPerDecision();
        long nativeCellCapacity = Integer.MAX_VALUE / (long) dimensions.iterations();
        long nativeHistoryCapacity = Integer.MAX_VALUE / (long) dimensions.lookback();
        capacity = Math.min(capacity, Math.min(nativeCellCapacity, nativeHistoryCapacity));
        if (capacity < 1L) {
            throw new NativeProviderException(providerId + " needs " + dimensions.bytesPerDecision()
                    + " bytes per decision, above the " + ceiling + "-byte provider ceiling");
        }
        return (int) Math.min(capacity, Integer.MAX_VALUE);
    }

    private NativeForecastRequest nativeChunk(KernelRequest request, double[] params, List<double[]> inputs,
            Dimensions dimensions, int from, int count) {
        double[] prices = inputs.get(0);
        double[] means = inputs.get(1);
        double[] drifts = inputs.get(2);
        double[] variances = inputs.get(3);
        double[] windows = inputs.get(4);
        int base = from - request.fromInclusive();
        int[] stable = new int[count];
        Arrays.fill(stable, 1);
        double[] chunkPrices = Arrays.copyOfRange(prices, base, base + count);
        double[] chunkMeans = Arrays.copyOfRange(means, base, base + count);
        double[] chunkDrifts = Arrays.copyOfRange(drifts, base, base + count);
        double[] chunkVariances = Arrays.copyOfRange(variances, base, base + count);
        double[] chunkWindows = Arrays.copyOfRange(windows, base * dimensions.lookback(),
                (base + count) * dimensions.lookback());
        return new NativeForecastRequest(from, count, dimensions.horizon(), dimensions.iterations(),
                dimensions.lookback(), request.seed(), (int) params[0], (int) params[1], params[5], NO_QUANTILES,
                stable, chunkPrices, chunkMeans, chunkDrifts, chunkVariances, chunkWindows);
    }

    private record Dimensions(int decisions, int horizon, int iterations, int lookback, long steps, long stagedBytes,
            long bytesPerDecision, long peakBytes) {
    }
}

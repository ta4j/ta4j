/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.acceleration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BarSeries.BarSeriesChangeSnapshot;
import org.ta4j.core.Indicator;

/**
 * Scoped entry point for accelerated indicator evaluation.
 *
 * <p>
 * Providers never observe ta4j domain graphs. A {@link KernelIndicator}
 * snapshots a run of consecutive decision indexes into an immutable versioned
 * {@link KernelRequest} built exclusively from primitives, workload shapes, and
 * numeric contracts. Providers answer with raw primitives
 * ({@link KernelResult}); the runtime validates the raw output, reconstructs
 * domain values through the indicator's decoder, and falls back to the CPU on
 * any failure. The CPU lane runs the indicator's own {@link Kernel} over the
 * same request shape, so an accelerated value is the CPU value. Assessment
 * never initializes native code.
 *
 * <p>
 * Selection is cost-based. Every discovered provider is assessed; supported
 * assessments are ranked by predicted end-to-end cost with a documented stable
 * tie-break, compared against the kernel's scalar baseline (CPU crossover), and
 * executed best-first with per-attempt fallback. Failure isolation is keyed by
 * provider, device, and operation version.
 *
 * <p>
 * A read whose index cannot start a batch — an unavailable index, a window the
 * series no longer retains, a series changed mid-run — is computed on the CPU
 * and the next read tries again, so an early read cannot disable acceleration
 * for the rest of the scope. A workload that can never be batched, and a
 * request no provider accepts, falls back to the CPU for the whole scope.
 * Provider declines keep their typed diagnostic — provider id, accuracy or
 * library requirement, memory detail — instead of a generic code. When no
 * provider can run a request, the scope diagnostic keeps the first
 * provider-attributed decline.
 *
 * <h2>Eligible indicators</h2>
 * <p>
 * Every {@link KernelIndicator}. Today that is
 * {@link org.ta4j.core.indicators.forecast.MonteCarloPriceForecastIndicator},
 * whose kernel is {@link Operation#MONTE_CARLO_SHOCK_PATHS_V1}. Every other
 * indicator always runs on the CPU.
 *
 * <h2>Control</h2>
 * <ul>
 * <li>{@value #PROPERTY}: {@code auto} or {@code true} enables scopes; unset,
 * {@code off} or {@code false} (the default) keeps every run on the CPU without
 * touching provider code.</li>
 * <li>A {@link Provider} implementation on the classpath, discovered through
 * {@link ServiceLoader}. Without one, enabled scopes fall back to the CPU.</li>
 * <li>A scope: {@code BarSeriesManager} opens one around each run; other
 * callers use {@link #open(BarSeries, int, int)} with try-with-resources.
 * Values read outside a scope are computed on the CPU.</li>
 * <li>{@value #MAX_DEVICE_BYTES_PROPERTY} caps the per-request device memory
 * estimate (default 1 GiB); larger workloads are chunked or declined.</li>
 * <li>{@value #APPROXIMATE_TOLERANCE_PROPERTY} opts provider batches into
 * {@link Determinism#APPROXIMATE} results within that finite positive
 * tolerance; unset, every request is {@link Determinism#BITWISE_IDENTICAL}. See
 * {@link #approximateTolerance()}.</li>
 * <li>{@link #lastDiagnostic()} reports whether the current or last closed
 * scope accelerated and, if not, the typed reason.</li>
 * </ul>
 *
 * @since 0.26.1
 */
public final class AccelerationRuntime {

    /**
     * System property selecting the scoped acceleration runtime: {@code auto} or
     * {@code true} enables it, {@code off} or {@code false} disables it
     * (case-insensitive). The removed {@code cpu}, {@code metal}, {@code cuda},
     * {@code hybrid} and {@code required} modes, and any other value, are rejected.
     */
    public static final String PROPERTY = "ta4j.acceleration.enabled";

    /**
     * System property capping the per-request device memory estimate, in bytes,
     * before any provider is contacted. Defaults to 1 GiB.
     */
    public static final String MAX_DEVICE_BYTES_PROPERTY = "ta4j.acceleration.maxDeviceBytes";

    /**
     * System property opting execution into a finite positive approximate tolerance
     * that providers must meet against the scalar oracle. Unset (or invalid) leaves
     * exact, bitwise-identical execution as the only mode.
     */
    public static final String APPROXIMATE_TOLERANCE_PROPERTY = "ta4j.acceleration.approximateTolerance";

    private static final Logger LOG = LoggerFactory.getLogger(AccelerationRuntime.class);

    private static final long DEFAULT_MAX_DEVICE_BYTES = 1L << 30;

    /**
     * Minimum predicted end-to-end speedup over the scalar baseline before
     * automatic selection engages a provider: its predicted total cost must be at
     * most the scalar estimate divided by this factor. Smaller predicted gains stay
     * scalar with a {@link DiagnosticCode#CPU_FASTER} diagnostic.
     */
    static final double MIN_PREDICTED_SPEEDUP = 1.5d;

    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    /**
     * Diagnostic of the most recently closed automatic scope on this thread, so
     * callers can inspect why a finished run stayed on CPU.
     */
    private static final ThreadLocal<Diagnostic> LAST_CLOSED_DIAGNOSTIC = new ThreadLocal<>();

    /**
     * Guard against provider discovery iterators that keep failing: discovery
     * aborts after this many consecutive broken entries instead of looping.
     */
    private static final int MAX_CONSECUTIVE_DISCOVERY_FAILURES = 64;

    private static volatile List<Provider> discoveredProviders;

    private AccelerationRuntime() {
    }

    /**
     * Opens an acceleration scope for a backtest run range and binds it to the
     * current thread.
     *
     * @param series the run's series
     * @param from   inclusive run begin index
     * @param to     inclusive run end index
     * @return scope handle closing back to the enclosing scope
     * @since 0.26.1
     */
    public static Scope open(BarSeries series, int from, int to) {
        Objects.requireNonNull(series, "series must not be null");
        if (!enabled()) {
            Context previous = CURRENT.get();
            CURRENT.remove();
            return () -> CURRENT.set(previous);
        }
        Context previous = CURRENT.get();
        Context context = new Context(from, to, previous);
        CURRENT.set(context);
        return context;
    }

    /**
     * Returns the value the current scope's validated batch holds for an index,
     * planning and executing a batch starting at {@code index} when none covers it.
     *
     * @return accelerated value, or {@code null} to compute on the CPU
     */
    static <T> T batchValue(KernelIndicator<T> indicator, int index) {
        Context context = CURRENT.get();
        if (context == null || context.suspended) {
            return null;
        }
        return context.value(indicator, index);
    }

    /**
     * Builds the request for {@code rows} decision indexes starting at
     * {@code fromInclusive}, shared by the CPU lane and provider batches so both
     * run the same request shape. A finite positive {@code tolerance} selects
     * {@link Determinism#APPROXIMATE}; {@code NaN} keeps
     * {@link Determinism#BITWISE_IDENTICAL}.
     */
    static KernelRequest request(Kernel kernel, int fromInclusive, int rows, double[][] rowInputs, double[] window,
            double tolerance) {
        int windowLength = kernel.windowLength();
        List<double[]> inputs = new ArrayList<>(rowInputs.length + (windowLength > 0 ? 1 : 0));
        Collections.addAll(inputs, rowInputs);
        if (windowLength > 0) {
            inputs.add(window);
        }
        long scalarNanosPerRow = kernel.scalarNanosPerRow();
        long scalarNanos = scalarNanosPerRow <= 0L ? 0L : saturatedMultiply(scalarNanosPerRow, rows);
        long peakDeviceBytes = saturatedAdd(fixedDeviceBytes(kernel),
                saturatedMultiply(kernel.deviceBytesPerRow(), rows));
        Determinism determinism = Double.isNaN(tolerance) ? Determinism.BITWISE_IDENTICAL : Determinism.APPROXIMATE;
        return new KernelRequest(kernel.operation(), fromInclusive, fromInclusive + rows - 1, kernel.outputsPerRow(),
                NumericEncoding.FLOAT64, determinism, kernel.seed(), tolerance, kernel.params(), inputs, scalarNanos,
                peakDeviceBytes);
    }

    /** Device bytes of the window prefix shared by every row of a batch. */
    private static long fixedDeviceBytes(Kernel kernel) {
        return Math.max(0L, kernel.windowLength() - 1L) * Double.BYTES;
    }

    private static long saturatedMultiply(long left, long right) {
        long high = Math.multiplyHigh(left, right);
        long low = left * right;
        return high == 0L && low >= 0L ? low : Long.MAX_VALUE;
    }

    private static long saturatedAdd(long left, long right) {
        long sum = left + right;
        return sum < 0L ? Long.MAX_VALUE : sum;
    }

    /**
     * Returns the diagnostic of the currently open scope, or the diagnostic of the
     * most recently closed automatic scope on this thread when no scope is open —
     * for example after {@code BarSeriesManager} has returned.
     *
     * @return current or last-closed diagnostic, or empty when no automatic scope
     *         was ever closed on this thread
     * @since 0.26.1
     */
    public static Optional<Diagnostic> lastDiagnostic() {
        Context context = CURRENT.get();
        if (context != null) {
            return Optional.of(context.diagnostic);
        }
        Diagnostic diagnostic = LAST_CLOSED_DIAGNOSTIC.get();
        return diagnostic == null ? Optional.empty() : Optional.of(diagnostic);
    }

    static long maxDeviceBytes() {
        String configured = System.getProperty(MAX_DEVICE_BYTES_PROPERTY);
        if (configured == null || configured.isBlank()) {
            return DEFAULT_MAX_DEVICE_BYTES;
        }
        try {
            long parsed = Long.parseLong(configured.trim());
            return parsed > 0 ? parsed : DEFAULT_MAX_DEVICE_BYTES;
        } catch (NumberFormatException exception) {
            LOG.warn("Invalid {}='{}'; using default {}", MAX_DEVICE_BYTES_PROPERTY, configured,
                    DEFAULT_MAX_DEVICE_BYTES);
            return DEFAULT_MAX_DEVICE_BYTES;
        }
    }

    /**
     * Returns the opted-in approximate tolerance, or {@code NaN} when exact,
     * bitwise-identical execution is requested.
     *
     * <p>
     * The property controls the determinism contract of provider batch requests: a
     * finite positive value selects {@link Determinism#APPROXIMATE} with that
     * tolerance, while anything else — unset, blank, non-numeric, or non-positive —
     * keeps {@link Determinism#BITWISE_IDENTICAL} with {@code NaN} tolerance.
     * Invalid configuration never silently widens accuracy; it degrades to exact.
     *
     * @return finite positive approximate tolerance, or {@code NaN} for exact
     * @since 0.26.1
     */
    public static double approximateTolerance() {
        String configured = System.getProperty(APPROXIMATE_TOLERANCE_PROPERTY);
        if (configured == null || configured.isBlank()) {
            return Double.NaN;
        }
        double tolerance;
        try {
            tolerance = Double.parseDouble(configured.trim());
        } catch (NumberFormatException exception) {
            LOG.warn("Invalid {}='{}'; using exact execution", APPROXIMATE_TOLERANCE_PROPERTY, configured);
            return Double.NaN;
        }
        if (Double.isNaN(tolerance)) {
            return Double.NaN;
        }
        if (!Double.isFinite(tolerance) || tolerance <= 0d) {
            LOG.warn("{} must be a finite positive tolerance, was '{}'; using exact execution",
                    APPROXIMATE_TOLERANCE_PROPERTY, configured);
            return Double.NaN;
        }
        return tolerance;
    }

    static synchronized void useProvidersForTests(List<Provider> providers) {
        discoveredProviders = List.copyOf(providers);
    }

    static synchronized void resetProvidersForTests() {
        discoveredProviders = null;
        CURRENT.remove();
        LAST_CLOSED_DIAGNOSTIC.remove();
    }

    private static boolean enabled() {
        String configured = System.getProperty(PROPERTY);
        if (configured == null || configured.isBlank()) {
            return false;
        }
        return switch (configured.trim().toLowerCase(Locale.ROOT)) {
        case "off", "false" -> false;
        case "auto", "true" -> true;
        default -> throw new IllegalArgumentException(
                PROPERTY + " must be 'auto', 'true', 'off' or 'false', but was '" + configured + "'");
        };
    }

    private static List<Provider> providers() {
        List<Provider> providers = discoveredProviders;
        if (providers != null) {
            return providers;
        }
        synchronized (AccelerationRuntime.class) {
            providers = discoveredProviders;
            if (providers == null) {
                List<Provider> loaded;
                try {
                    loaded = loadProviders(ServiceLoader.load(Provider.class).iterator());
                } catch (LinkageError | RuntimeException exception) {
                    LOG.warn("Acceleration provider discovery failed: {}", failureMessage(exception));
                    loaded = List.of();
                }
                providers = loaded;
                discoveredProviders = providers;
            }
        }
        return providers;
    }

    /**
     * Iterates a provider discovery source, skipping entries that cannot be
     * instantiated instead of aborting the whole discovery, guarding against an
     * iterator that keeps failing, and always returning the resulting list so a
     * failed scan is not repeated per evaluation.
     *
     * @param iterator provider discovery iterator, typically from ServiceLoader
     * @return immutable discovered providers, possibly empty
     * @since 0.26.1
     */
    static List<Provider> loadProviders(Iterator<Provider> iterator) {
        List<Provider> loaded = new ArrayList<>();
        int consecutiveFailures = 0;
        while (true) {
            Provider provider;
            try {
                if (!iterator.hasNext()) {
                    break;
                }
                provider = iterator.next();
            } catch (ServiceConfigurationError exception) {
                consecutiveFailures++;
                if (consecutiveFailures >= MAX_CONSECUTIVE_DISCOVERY_FAILURES) {
                    LOG.warn("Acceleration provider discovery aborted after {} consecutive failures; last error: {}",
                            consecutiveFailures, String.valueOf(exception.getMessage()));
                    break;
                }
                LOG.warn("Skipping acceleration provider entry: {}", String.valueOf(exception.getMessage()));
                continue;
            }
            consecutiveFailures = 0;
            if (provider != null) {
                loaded.add(provider);
            }
        }
        return List.copyOf(loaded);
    }

    /**
     * Auto-closeable acceleration scope.
     *
     * @since 0.26.1
     */
    @FunctionalInterface
    public interface Scope extends AutoCloseable {

        /**
         * Closes the scope and restores any enclosing execution scope.
         *
         * @since 0.26.1
         */
        @Override
        void close();
    }

    /**
     * Versioned accelerated operation. New operations are admitted by core only.
     */
    public enum Operation {

        /** Monte Carlo shock-path kernel, contract version 1. */
        MONTE_CARLO_SHOCK_PATHS_V1(1);

        private final int version;

        Operation(int version) {
            this.version = version;
        }

        /**
         * Returns the operation contract version.
         *
         * @return contract version
         * @since 0.26.1
         */
        public int version() {
            return version;
        }
    }

    /** Primitive numeric encoding of kernel buffers. */
    public enum NumericEncoding {

        /** IEEE-754 binary64, matching {@code DoubleNum} scalar semantics. */
        FLOAT64
    }

    /** Determinism contract a kernel result must satisfy. */
    public enum Determinism {

        /**
         * Bitwise identical to the scalar oracle for the same request inputs.
         */
        BITWISE_IDENTICAL,

        /**
         * Within an explicitly requested numeric tolerance of the scalar oracle. Using
         * this contract requires a finite positive kernel-request tolerance. Providers
         * are responsible for qualifying this accuracy contract against the scalar
         * oracle. The runtime validates output shape, finiteness and series freshness;
         * it does not replay the scalar workload on every execution.
         */
        APPROXIMATE
    }

    /** Effective execution backend. */
    public enum Backend {

        /** Canonical scalar CPU fallback. */
        CPU,

        /** Apple Metal. */
        METAL,

        /** NVIDIA CUDA. */
        CUDA,

        /** Khronos OpenCL. */
        OPENCL
    }

    /**
     * Typed provider diagnostic.
     *
     * @param code       stable code
     * @param providerId provider identifier, or {@code none}
     * @param detail     concise detail
     * @since 0.26.1
     */
    public record Diagnostic(DiagnosticCode code, String providerId, String detail) {

        /** Validates a diagnostic. */
        public Diagnostic {
            Objects.requireNonNull(code, "code must not be null");
            Objects.requireNonNull(providerId, "providerId must not be null");
            Objects.requireNonNull(detail, "detail must not be null");
        }
    }

    /** Stable diagnostic and fallback codes. */
    public enum DiagnosticCode {

        /** Provider execution completed. */
        ACCELERATED,

        /** No optional provider artifact was present. */
        NO_PROVIDER,

        /** The workload can never be batched, or no kernel indicator was read. */
        UNSUPPORTED,

        /** A provider or device was unavailable. */
        PROVIDER_UNAVAILABLE,

        /** CPU was predicted to be faster. */
        CPU_FASTER,

        /** The series changed during provider work. */
        STALE_SERIES,

        /** Provider execution failed. */
        PROVIDER_FAILURE,

        /** Provider output did not cover the exact request. */
        INVALID_RESULT
    }

    /**
     * Immutable versioned kernel request built exclusively from primitives,
     * workload shapes, and numeric contracts. Providers receive no indicators,
     * series, {@code Num} graphs, or forecast types.
     *
     * @param operation               operation to execute
     * @param fromInclusive           first decision index in the batch
     * @param toInclusive             last decision index in the batch
     * @param outputsPerIndex         raw output values per decision index
     * @param numeric                 primitive encoding of every buffer
     * @param determinism             determinism contract the kernel must satisfy
     * @param seed                    base seed; per-index mixing is defined by the
     *                                operation contract
     * @param tolerance               finite positive tolerance for
     *                                {@link Determinism#APPROXIMATE} requests;
     *                                {@code NaN} for
     *                                {@link Determinism#BITWISE_IDENTICAL}
     * @param params                  operation parameters (ordinals, counts,
     *                                factors) defined by the operation contract
     * @param inputs                  read-only primitive input buffers
     * @param estimatedScalarNanos    scalar-baseline estimate for the crossover
     *                                comparison, non-positive when unknown
     * @param peakDeviceBytesEstimate declared peak device memory in bytes
     * @throws IllegalArgumentException if the range, output width, peak estimate or
     *                                  determinism/tolerance pairing is invalid
     * @since 0.26.1
     */
    public record KernelRequest(Operation operation, int fromInclusive, int toInclusive, int outputsPerIndex,
            NumericEncoding numeric, Determinism determinism, long seed, double tolerance, double[] params,
            List<double[]> inputs, long estimatedScalarNanos, long peakDeviceBytesEstimate) {
        /** Validates and defensively copies a kernel request. */
        public KernelRequest {
            Objects.requireNonNull(operation, "operation must not be null");
            Objects.requireNonNull(numeric, "numeric must not be null");
            Objects.requireNonNull(determinism, "determinism must not be null");
            Objects.requireNonNull(params, "params must not be null");
            Objects.requireNonNull(inputs, "inputs must not be null");
            if (fromInclusive > toInclusive || outputsPerIndex < 1) {
                throw new IllegalArgumentException(
                        "request range [" + fromInclusive + ", " + toInclusive + "] is invalid");
            }
            if (peakDeviceBytesEstimate < 0) {
                throw new IllegalArgumentException("peakDeviceBytesEstimate must be >= 0");
            }
            if (determinism == Determinism.APPROXIMATE ? !(Double.isFinite(tolerance) && tolerance > 0d)
                    : !Double.isNaN(tolerance)) {
                throw new IllegalArgumentException(determinism == Determinism.APPROXIMATE
                        ? "APPROXIMATE requests need a finite positive tolerance, was " + tolerance
                        : "BITWISE_IDENTICAL requests carry a NaN tolerance, was " + tolerance);
            }
            params = params.clone();
            List<double[]> copies = new ArrayList<>(inputs.size());
            for (double[] buffer : inputs) {
                copies.add(Objects.requireNonNull(buffer, "input buffer must not be null").clone());
            }
            inputs = List.copyOf(copies);
        }

        @Override
        public List<double[]> inputs() {
            List<double[]> copies = new ArrayList<>(inputs.size());
            for (double[] buffer : inputs) {
                copies.add(buffer.clone());
            }
            return List.copyOf(copies);
        }

        /**
         * Returns the number of decision indexes in the batch.
         *
         * @since 0.26.1
         */
        public int size() {
            return Math.addExact(Math.subtractExact(toInclusive, fromInclusive), 1);
        }

        /**
         * Returns the expected raw output length.
         *
         * @return {@code size() * outputsPerIndex}
         * @since 0.26.1
         */
        public int expectedOutputLength() {
            return Math.multiplyExact(size(), outputsPerIndex);
        }

        /**
         * Returns one element of an input buffer without copying it.
         *
         * @param buffer   input buffer index
         * @param position element position within the buffer
         * @return input value
         * @since 0.26.1
         */
        public double input(int buffer, int position) {
            return inputs.get(buffer)[position];
        }

        /**
         * Returns one operation parameter without copying the parameter array.
         *
         * @param index parameter index
         * @return parameter value
         * @since 0.26.1
         */
        public double param(int index) {
            return params[index];
        }

        /**
         * Returns a copy of the operation parameters.
         *
         * @return defensive copy, never the live buffer
         * @since 0.26.1
         */
        @Override
        public double[] params() {
            return params.clone();
        }
    }

    /**
     * Raw kernel output. Values are primitives; domain reconstruction is owned by
     * core.
     *
     * @param outputs           row-major raw outputs of length
     *                          {@code request.size() * outputsPerIndex}
     * @param nativeInitialized whether native code was initialized
     * @param elapsedNanos      provider-measured kernel time
     * @since 0.26.1
     */
    public record KernelResult(double[] outputs, boolean nativeInitialized, long elapsedNanos) {

        /** Validates and defensively copies a kernel result. */
        public KernelResult {
            Objects.requireNonNull(outputs, "outputs must not be null");
            outputs = outputs.clone();
            if (elapsedNanos < 0L) {
                throw new IllegalArgumentException("elapsedNanos must be >= 0");
            }
        }

        /**
         * Returns a copy of the raw kernel outputs.
         *
         * @return defensive copy, never the live buffer
         * @since 0.26.1
         */
        @Override
        public double[] outputs() {
            return outputs.clone();
        }
    }

    /**
     * Cost assessment for one provider and request. Produced without initializing
     * native code or performing observable side effects.
     *
     * @param supported           whether the provider can execute the request
     * @param backend             backend the provider would use
     * @param deviceId            stable device identifier
     * @param predictedTotalNanos predicted end-to-end nanoseconds including
     *                            transfer and launch overhead
     * @param peakDeviceBytes     provider-confirmed peak device memory
     * @param deterministic       whether the provider meets the request determinism
     *                            contract
     * @param diagnostic          explanation when unsupported
     * @since 0.26.1
     */
    public record Assessment(boolean supported, Backend backend, String deviceId, long predictedTotalNanos,
            long peakDeviceBytes, boolean deterministic, Diagnostic diagnostic) {

        /** Validates an assessment. */
        public Assessment {
            Objects.requireNonNull(backend, "backend must not be null");
            Objects.requireNonNull(deviceId, "deviceId must not be null");
            Objects.requireNonNull(diagnostic, "diagnostic must not be null");
        }

        /**
         * Creates a supported assessment.
         *
         * @since 0.26.1
         */
        public static Assessment supported(Backend backend, String deviceId, long predictedTotalNanos,
                long peakDeviceBytes, boolean deterministic) {
            return new Assessment(true, backend, deviceId, predictedTotalNanos, peakDeviceBytes, deterministic,
                    new Diagnostic(DiagnosticCode.ACCELERATED, "assessed", "supported"));
        }

        /**
         * Creates an unsupported assessment.
         *
         * @since 0.26.1
         */
        public static Assessment unsupported(Backend backend, String deviceId, DiagnosticCode code, String providerId,
                String detail) {
            return new Assessment(false, backend, deviceId, Long.MAX_VALUE, Long.MAX_VALUE, false,
                    new Diagnostic(code, providerId, detail));
        }
    }

    /**
     * Provider service interface. Implementations observe only
     * {@link KernelRequest} primitives and answer with raw primitives.
     *
     * <p>
     * Provider constructors must not probe devices or load native libraries, and
     * {@link #assess(KernelRequest)} must not initialize native code.
     *
     * @since 0.26.1
     */
    public interface Provider {

        /**
         * Returns the stable provider identifier, defaulting to the class name.
         *
         * @return provider id
         * @since 0.26.1
         */
        default String providerId() {
            return getClass().getName();
        }

        /**
         * Assesses a request without initializing native code.
         *
         * @param request immutable kernel request
         * @return cost assessment
         * @since 0.26.1
         */
        Assessment assess(KernelRequest request);

        /**
         * Executes a request and returns raw primitives.
         *
         * <p>
         * Implementations must enforce the request's numeric and determinism contracts,
         * including approximate tolerance. Returning finite output alone is not
         * sufficient conformance. Runtime structural validation is not an independent
         * numerical-accuracy check.
         *
         * @param request immutable kernel request
         * @return raw kernel output
         * @since 0.26.1
         */
        KernelResult execute(KernelRequest request);
    }

    private static final class Context implements Scope {

        private final int fromInclusive;
        private final int toInclusive;
        private final Context previous;
        private final long memoryLimitBytes;
        private final long startedNanos = System.nanoTime();
        private final IdentityHashMap<Indicator<?>, CachedBatch> batches = new IdentityHashMap<>();
        private final Set<Indicator<?>> scalarFallback = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Map<String, String> quarantine = new HashMap<>();

        private boolean suspended;
        private boolean requested;
        private Backend effectiveBackend = Backend.CPU;
        private String providerInUse = "none";
        private boolean nativeInitialized;
        private long providerElapsedNanos;
        private Diagnostic diagnostic = new Diagnostic(DiagnosticCode.UNSUPPORTED, "none",
                "no eligible acceleration request");

        private Context(int fromInclusive, int toInclusive, Context previous) {
            this.fromInclusive = fromInclusive;
            this.toInclusive = toInclusive;
            this.previous = previous;
            this.memoryLimitBytes = maxDeviceBytes();
        }

        @SuppressWarnings("unchecked")
        private <T> T value(KernelIndicator<T> indicator, int index) {
            Objects.requireNonNull(indicator, "indicator must not be null");
            if (index < fromInclusive || index > toInclusive || scalarFallback.contains(indicator)) {
                return null;
            }
            BarSeries indicatorSeries = indicator.getBarSeries();
            CachedBatch cached = batches.get(indicator);
            if (cached != null && (!cached.matchesCurrentSeries(indicatorSeries) || index > cached.toInclusive)) {
                batches.remove(indicator);
                cached = null;
            }
            if (cached == null) {
                Evaluation evaluation = evaluate(indicator, index);
                if (evaluation.batch() == null) {
                    if (evaluation.permanent()) {
                        scalarFallback.add(indicator);
                    }
                    return null;
                }
                batches.put(indicator, evaluation.batch());
                cached = evaluation.batch();
            }
            return (T) cached.value(index);
        }

        private <T> Evaluation evaluate(KernelIndicator<T> indicator, int index) {
            requested = true;
            BarSeries indicatorSeries = indicator.getBarSeries();
            if (indicatorSeries.getBarHistoryRevision() < 0L) {
                diagnostic = new Diagnostic(DiagnosticCode.UNSUPPORTED, "none",
                        "series does not track bar-data revisions; accelerated batches cannot be invalidated");
                return Evaluation.unsupported();
            }
            BarSeriesChangeSnapshot beforePlanning = indicatorSeries.getBarSeriesChangeSnapshot(-1L);
            KernelRequest request;
            try {
                Plan plan = plan(indicator, index);
                if (plan.ineligibleReason() != null) {
                    diagnostic = new Diagnostic(DiagnosticCode.UNSUPPORTED, "none", plan.ineligibleReason());
                    return Evaluation.unsupported();
                }
                request = plan.request();
            } catch (LinkageError | RuntimeException exception) {
                diagnostic = new Diagnostic(DiagnosticCode.UNSUPPORTED, "none", "planning failed for "
                        + indicator.getClass().getSimpleName() + ": " + failureMessage(exception));
                return Evaluation.unsupported();
            }
            if (request == null) {
                return Evaluation.declined();
            }
            if (request.peakDeviceBytesEstimate() > memoryLimitBytes) {
                diagnostic = new Diagnostic(DiagnosticCode.UNSUPPORTED, "none", "peak device estimate "
                        + request.peakDeviceBytesEstimate() + " exceeds budget " + memoryLimitBytes);
                return Evaluation.unsupported();
            }
            List<Provider> providers = providers();
            if (providers.isEmpty()) {
                diagnostic = new Diagnostic(DiagnosticCode.NO_PROVIDER, "none",
                        "no acceleration provider was discovered");
                return Evaluation.unsupported();
            }
            List<RankedProvider> candidates = assess(request, providers);
            if (candidates.isEmpty()) {
                // assess() already recorded the decisive provider decline.
                return Evaluation.unsupported();
            }
            int quarantinedSkipped = 0;
            for (RankedProvider candidate : candidates) {
                if (quarantine.containsKey(quarantineKey(candidate, request))) {
                    quarantinedSkipped++;
                    continue;
                }
                if (!matchesSeriesState(indicatorSeries, beforePlanning)) {
                    diagnostic = new Diagnostic(DiagnosticCode.STALE_SERIES, candidate.providerId,
                            "series changed after planning and before provider execution");
                    return Evaluation.declined();
                }
                KernelResult result = null;
                suspended = true;
                long started = System.nanoTime();
                try {
                    result = Objects.requireNonNull(candidate.provider.execute(request),
                            "acceleration provider returned null");
                    nativeInitialized |= result.nativeInitialized();
                } catch (LinkageError | RuntimeException exception) {
                    quarantine.put(quarantineKey(candidate, request), failureMessage(exception));
                    diagnostic = new Diagnostic(DiagnosticCode.PROVIDER_FAILURE, candidate.providerId,
                            failureMessage(exception));
                } finally {
                    providerElapsedNanos += System.nanoTime() - started;
                    suspended = false;
                }
                BarSeriesChangeSnapshot after = indicatorSeries.getBarSeriesChangeSnapshot(beforePlanning.revision());
                if (!sameSeriesState(beforePlanning, after)) {
                    diagnostic = new Diagnostic(DiagnosticCode.STALE_SERIES, candidate.providerId,
                            "series changed while the provider was evaluating");
                    return Evaluation.declined();
                }
                if (result == null) {
                    continue;
                }
                double[] rawOutputs = result.outputs;
                if (rawOutputs.length != request.expectedOutputLength() || !allFinite(rawOutputs)) {
                    quarantine.put(quarantineKey(candidate, request), "malformed raw output");
                    diagnostic = new Diagnostic(DiagnosticCode.INVALID_RESULT, candidate.providerId,
                            "provider output was malformed or non-finite for [%d, %d]"
                                    .formatted(request.fromInclusive(), request.toInclusive()));
                    continue;
                }
                List<Object> decoded;
                try {
                    decoded = decodeAll(request, rawOutputs, indicator);
                } catch (LinkageError | RuntimeException exception) {
                    quarantine.put(quarantineKey(candidate, request), failureMessage(exception));
                    diagnostic = new Diagnostic(DiagnosticCode.INVALID_RESULT, candidate.providerId,
                            failureMessage(exception));
                    continue;
                }
                // Decoding reconstructs every domain value and may run arbitrary
                // core-owned code; publishing requires the captured revision to
                // still be current, so a mutation during decoding cannot leak the
                // first stale forecast into the trading record.
                BarSeriesChangeSnapshot published = indicatorSeries.getBarSeriesChangeSnapshot(after.revision());
                if (!sameSeriesState(after, published)) {
                    diagnostic = new Diagnostic(DiagnosticCode.STALE_SERIES, candidate.providerId,
                            "series changed while decoded results were being prepared");
                    return Evaluation.declined();
                }
                effectiveBackend = candidate.assessment.backend();
                providerInUse = candidate.providerId;
                diagnostic = new Diagnostic(DiagnosticCode.ACCELERATED, providerInUse,
                        candidate.assessment.backend().name().toLowerCase(Locale.ROOT) + "/"
                                + candidate.assessment.deviceId() + " executed " + request.operation());
                return Evaluation.accelerated(
                        new CachedBatch(request.fromInclusive(), request.toInclusive(), decoded, published));
            }
            if (quarantinedSkipped == candidates.size()) {
                diagnostic = new Diagnostic(DiagnosticCode.PROVIDER_FAILURE, "none",
                        "every eligible provider is quarantined in this scope");
            }
            return Evaluation.unsupported();
        }

        /**
         * Snapshots the longest run of available decision indexes starting at
         * {@code index} that fits the host and device memory budgets.
         *
         * @return the request, a {@code null} request when {@code index} itself is
         *         unavailable, or the reason the workload can never be batched
         */
        private Plan plan(KernelIndicator<?> indicator, int index) {
            Kernel kernel = indicator.kernel();
            int windowLength = kernel.windowLength();
            int inputsPerRow = kernel.inputsPerRow();
            int outputsPerRow = kernel.outputsPerRow();
            long deviceBytesPerRow = kernel.deviceBytesPerRow();
            if (windowLength < 0 || inputsPerRow < 0 || outputsPerRow < 1 || deviceBytesPerRow < 0L) {
                return Plan.ineligible("kernel declares an invalid row shape");
            }
            BarSeries series = indicator.getBarSeries();
            long windowStart = (long) index - windowLength + 1L;
            if (index < series.getBeginIndex() || windowStart < series.getBeginIndex()) {
                return Plan.unavailable();
            }
            int prefixLength = Math.max(0, windowLength - 1);
            long rowHostBytes = (inputsPerRow + (windowLength > 0 ? 1L : 0L)) * Double.BYTES * 3L
                    + (long) outputsPerRow * Double.BYTES * 4L;
            long fixedHostBytes = (long) prefixLength * Double.BYTES * 3L;
            long hostRows = (Runtime.getRuntime().maxMemory() / 4L - fixedHostBytes) / rowHostBytes;
            long deviceRows = deviceBytesPerRow == 0L ? Long.MAX_VALUE
                    : (memoryLimitBytes - fixedDeviceBytes(kernel)) / deviceBytesPerRow;
            long arrayRows = Math.min(Integer.MAX_VALUE - (long) prefixLength, Integer.MAX_VALUE / outputsPerRow);
            long fittingRows = Math.min(Math.min(hostRows, deviceRows), arrayRows);
            if (fittingRows < 1L) {
                return Plan.ineligible("a single " + kernel.operation() + " row exceeds the host, device ("
                        + memoryLimitBytes + " bytes) or array budget");
            }
            int maxRows = (int) Math.min(fittingRows, (long) Math.min(toInclusive, series.getEndIndex()) - index + 1L);
            if (maxRows < 1) {
                return Plan.unavailable();
            }
            double[] rowInputs = new double[inputsPerRow];
            if (!indicator.snapshot(index, rowInputs)) {
                return Plan.unavailable();
            }
            double[] window = new double[prefixLength + Math.min(maxRows, 1024)];
            for (int offset = 0; offset < prefixLength; offset++) {
                double value = indicator.windowValue((int) windowStart + offset);
                if (Double.isNaN(value)) {
                    return Plan.unavailable();
                }
                window[offset] = value;
            }
            double[][] columns = new double[inputsPerRow][Math.min(maxRows, 1024)];
            int rows = 0;
            while (true) {
                int decisionIndex = index + rows;
                if (windowLength > 0) {
                    double newest = indicator.windowValue(decisionIndex);
                    if (Double.isNaN(newest)) {
                        break;
                    }
                    if (prefixLength + rows == window.length) {
                        window = Arrays.copyOf(window, prefixLength + grownCapacity(rows, maxRows));
                    }
                    window[prefixLength + rows] = newest;
                }
                if (inputsPerRow > 0 && rows == columns[0].length) {
                    int capacity = grownCapacity(rows, maxRows);
                    for (int buffer = 0; buffer < inputsPerRow; buffer++) {
                        columns[buffer] = Arrays.copyOf(columns[buffer], capacity);
                    }
                }
                for (int buffer = 0; buffer < inputsPerRow; buffer++) {
                    columns[buffer][rows] = rowInputs[buffer];
                }
                rows++;
                if (rows == maxRows || !indicator.snapshot(index + rows, rowInputs)) {
                    break;
                }
            }
            if (rows == 0) {
                return Plan.unavailable();
            }
            for (int buffer = 0; buffer < inputsPerRow; buffer++) {
                columns[buffer] = Arrays.copyOf(columns[buffer], rows);
            }
            double[] rowWindow = windowLength > 0 ? Arrays.copyOf(window, prefixLength + rows) : null;
            return new Plan(request(kernel, index, rows, columns, rowWindow, approximateTolerance()), null);
        }

        private static int grownCapacity(int rows, int maxRows) {
            return (int) Math.min(maxRows, Math.max(1L, rows * 2L));
        }

        private List<RankedProvider> assess(KernelRequest request, List<Provider> providers) {
            List<RankedProvider> candidates = new ArrayList<>();
            Diagnostic decline = null;
            boolean cpuFasterObserved = false;
            String cpuFasterProvider = "none";
            String cpuFasterDetail = "";
            for (Provider provider : providers) {
                Assessment assessment;
                String providerId;
                suspended = true;
                try {
                    providerId = Objects.requireNonNull(provider.providerId(), "providerId must not be null");
                    assessment = provider.assess(request);
                } catch (LinkageError | RuntimeException exception) {
                    String fallbackId = provider.getClass().getName();
                    decline = decline == null
                            ? new Diagnostic(DiagnosticCode.PROVIDER_FAILURE, fallbackId, failureMessage(exception))
                            : decline;
                    LOG.debug("Provider {} assessment failed: {}", fallbackId, exception.getMessage());
                    continue;
                } finally {
                    suspended = false;
                }
                Diagnostic rejection = assessmentRejection(request, providerId, assessment);
                if (rejection != null) {
                    // Keep the first provider-attributed explanation: an installed
                    // provider's accuracy, memory or qualification message must
                    // survive to the scope diagnostic instead of collapsing into
                    // "no provider supports".
                    decline = decline == null ? rejection : decline;
                    continue;
                }
                long baseline = request.estimatedScalarNanos();
                if (baseline > 0
                        && (double) assessment.predictedTotalNanos() * MIN_PREDICTED_SPEEDUP > (double) baseline) {
                    cpuFasterObserved = true;
                    cpuFasterProvider = providerId;
                    cpuFasterDetail = "predicted " + assessment.predictedTotalNanos() + "ns vs scalar baseline "
                            + baseline + "ns; automatic selection needs a " + MIN_PREDICTED_SPEEDUP
                            + "x predicted speedup";
                    continue;
                }
                candidates.add(new RankedProvider(provider, providerId, assessment));
            }
            candidates.sort(
                    Comparator.comparingLong((RankedProvider candidate) -> candidate.assessment.predictedTotalNanos())
                            .thenComparing(candidate -> candidate.assessment.backend().name())
                            .thenComparing(candidate -> candidate.assessment.deviceId())
                            .thenComparing(candidate -> candidate.providerId));
            if (candidates.isEmpty()) {
                if (decline != null) {
                    diagnostic = decline;
                } else if (cpuFasterObserved) {
                    diagnostic = new Diagnostic(DiagnosticCode.CPU_FASTER, cpuFasterProvider, cpuFasterDetail);
                } else {
                    diagnostic = new Diagnostic(DiagnosticCode.UNSUPPORTED, "none",
                            "no provider accepted " + request.operation());
                }
            }
            return candidates;
        }

        /**
         * Returns the typed reason a provider declined the request, or {@code null}
         * when the assessment is supported and within the scope budget.
         */
        private Diagnostic assessmentRejection(KernelRequest request, String providerId, Assessment assessment) {
            if (assessment == null) {
                return new Diagnostic(DiagnosticCode.INVALID_RESULT, providerId, "provider returned no assessment");
            }
            if (!assessment.supported()) {
                return assessment.diagnostic();
            }
            if (!assessment.deterministic()) {
                return new Diagnostic(DiagnosticCode.UNSUPPORTED, providerId,
                        "assessment does not meet the " + request.determinism() + " contract");
            }
            if (assessment.predictedTotalNanos() < 0) {
                return new Diagnostic(DiagnosticCode.INVALID_RESULT, providerId,
                        "assessment predicted a negative cost");
            }
            long peakBytes = Math.max(request.peakDeviceBytesEstimate(), assessment.peakDeviceBytes());
            if (peakBytes > memoryLimitBytes) {
                return new Diagnostic(DiagnosticCode.UNSUPPORTED, providerId,
                        "assessment peak of " + peakBytes + " bytes exceeds the " + memoryLimitBytes + "-byte budget");
            }
            return null;
        }

        private static List<Object> decodeAll(KernelRequest request, double[] rawOutputs,
                KernelIndicator<?> indicator) {
            List<Object> decoded = new ArrayList<>(request.size());
            // One slice is reused across indexes; decoders must not retain it.
            double[] slice = new double[request.outputsPerIndex()];
            for (int position = 0; position < request.size(); position++) {
                int index = request.fromInclusive() + position;
                System.arraycopy(rawOutputs, position * request.outputsPerIndex(), slice, 0, slice.length);
                Object value = indicator.decode(index, slice);
                if (value == null) {
                    throw new IllegalStateException(
                            "decoder returned null for index " + index + " of " + request.operation());
                }
                decoded.add(value);
            }
            return decoded;
        }

        private static String quarantineKey(RankedProvider candidate, KernelRequest request) {
            return candidate.providerId + "|" + candidate.assessment.deviceId() + "|" + request.operation() + "/v"
                    + request.operation().version();
        }

        @Override
        public void close() {
            if (CURRENT.get() == this) {
                if (previous == null) {
                    CURRENT.remove();
                } else {
                    CURRENT.set(previous);
                }
            }
            LAST_CLOSED_DIAGNOSTIC.set(diagnostic);
            long scopeNanos = System.nanoTime() - startedNanos;
            if (requested) {
                LOG.debug(
                        "ta4j acceleration requested=auto effectiveBackend={} provider={} code={} nativeInitialized={} providerNanos={} scopeNanos={} range=[{},{}] detail={}",
                        effectiveBackend.name().toLowerCase(Locale.ROOT), diagnostic.providerId(), diagnostic.code(),
                        nativeInitialized, providerElapsedNanos, scopeNanos, fromInclusive, toInclusive,
                        diagnostic.detail());
            }
        }
    }

    private static boolean allFinite(double[] outputs) {
        for (double output : outputs) {
            if (!Double.isFinite(output)) {
                return false;
            }
        }
        return true;
    }

    private record RankedProvider(Provider provider, String providerId, Assessment assessment) {
    }

    private static boolean sameSeriesState(BarSeriesChangeSnapshot left, BarSeriesChangeSnapshot right) {
        return left.revision() == right.revision() && left.removedThroughIndex() == right.removedThroughIndex()
                && left.maximumBarCount() == right.maximumBarCount() && left.endIndex() == right.endIndex();
    }

    private static boolean matchesSeriesState(BarSeries series, BarSeriesChangeSnapshot snapshot) {
        return sameSeriesState(series.getBarSeriesChangeSnapshot(snapshot.revision()), snapshot);
    }

    /**
     * Planned request of one decision index: a request, a {@code null} request when
     * the index is unavailable, or the reason the workload can never be batched.
     */
    private record Plan(KernelRequest request, String ineligibleReason) {

        private static Plan unavailable() {
            return new Plan(null, null);
        }

        private static Plan ineligible(String reason) {
            return new Plan(null, reason);
        }
    }

    /**
     * Result of one runtime evaluation attempt: a publishable batch, a permanent
     * decline sending the indicator to the CPU for the whole scope, or a transient
     * decline computing only the current read on the CPU.
     */
    private record Evaluation(CachedBatch batch, boolean permanent) {

        private static Evaluation accelerated(CachedBatch batch) {
            return new Evaluation(batch, false);
        }

        private static Evaluation unsupported() {
            return new Evaluation(null, true);
        }

        private static Evaluation declined() {
            return new Evaluation(null, false);
        }
    }

    private record CachedBatch(int fromInclusive, int toInclusive, List<?> values, BarSeriesChangeSnapshot snapshot) {

        private CachedBatch {
            values = List.copyOf(values);
            Objects.requireNonNull(snapshot, "snapshot must not be null");
        }

        private Object value(int index) {
            int offset = index - fromInclusive;
            return offset < 0 || offset >= values.size() ? null : values.get(offset);
        }

        private boolean matchesCurrentSeries(BarSeries series) {
            return matchesSeriesState(series, snapshot);
        }
    }

    private static String failureMessage(Throwable failure) {
        String message = failure.getMessage();
        return failure.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }
}

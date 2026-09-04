/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.acceleration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
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
import org.ta4j.core.num.NumFactory;

/**
 * Scoped entry point for accelerated indicator evaluation.
 *
 * <p>
 * Providers never observe ta4j domain graphs. Core-internal planners lower a
 * supported calculation into an immutable versioned {@link KernelRequest} built
 * exclusively from primitives, workload shapes, and numeric contracts.
 * Providers answer with raw primitives ({@link KernelResult}); the runtime
 * validates the raw output, reconstructs domain values through the owning
 * {@link NumFactory}, and falls back to the scalar lane on any failure. An
 * indicator that no planner claims never reaches provider code, and assessment
 * never initializes native code.
 *
 * <p>
 * Selection is cost-based. Every discovered provider is assessed; supported
 * assessments are ranked by predicted end-to-end cost with a documented stable
 * tie-break, compared against the planner's scalar baseline (CPU crossover),
 * and executed best-first with per-attempt fallback. Failure isolation is keyed
 * by provider, device, and operation version.
 *
 * @since 0.24.2
 */
public final class AccelerationRuntime {

    /**
     * System property selecting the scoped acceleration runtime ({@code off},
     * {@code auto}).
     */
    public static final String PROPERTY = "ta4j.acceleration.enabled";

    /**
     * System property capping the per-request device memory estimate, in bytes,
     * before any provider is contacted. Defaults to 1 GiB.
     */
    public static final String MAX_DEVICE_BYTES_PROPERTY = "ta4j.acceleration.maxDeviceBytes";

    /**
     * System property opting execution into an approximate tolerance, a finite
     * positive value compared against the scalar oracle. Unset (or invalid) leaves
     * exact, bitwise-identical execution as the only mode.
     */
    public static final String APPROXIMATE_TOLERANCE_PROPERTY = "ta4j.acceleration.approximateTolerance";

    private static final Logger LOG = LoggerFactory.getLogger(AccelerationRuntime.class);

    private static final long DEFAULT_MAX_DEVICE_BYTES = 1L << 30;

    /**
     * Safety margin applied to the CPU-crossover comparison: the best accelerator
     * must beat the scalar baseline by more than this fraction.
     */
    private static final double CPU_SAFETY_MARGIN = 0.10;

    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    private static final List<OperationPlanner> PLANNERS = new ArrayList<>();

    private static volatile List<Provider> discoveredProviders;

    private AccelerationRuntime() {
    }

    /**
     * Registers a core-internal operation planner.
     *
     * <p>
     * Core-internal extension point: planners are core-owned lowering code, not
     * provider extensions. Provider artifacts must implement {@link Provider}
     * instead and must never call this method. Registration is additive and
     * idempotent per planner class.
     *
     * @param planner planner to register
     * @since 0.24.2
     */
    public static synchronized void registerPlanner(OperationPlanner planner) {
        Objects.requireNonNull(planner, "planner must not be null");
        for (OperationPlanner registered : PLANNERS) {
            if (registered.getClass() == planner.getClass()) {
                return;
            }
        }
        PLANNERS.add(planner);
    }

    /**
     * Opens an acceleration scope for a backtest run range and binds it to the
     * current thread.
     *
     * @param series the backing series
     * @param from   inclusive run begin index
     * @param to     inclusive run end index
     * @return scope handle closing back to the enclosing scope
     * @since 0.24.2
     */
    public static Scope open(BarSeries series, int from, int to) {
        Objects.requireNonNull(series, "series must not be null");
        if (!enabled()) {
            Context previous = CURRENT.get();
            CURRENT.remove();
            return () -> CURRENT.set(previous);
        }
        Context previous = CURRENT.get();
        Context context = new Context(series, from, to, previous);
        CURRENT.set(context);
        return context;
    }

    /**
     * Returns an accelerated value from the current scope when one was successfully
     * produced.
     *
     * @param indicator indicator requesting a value
     * @param index     requested index
     * @param <T>       value type
     * @return accelerated value, or empty to use scalar evaluation
     * @since 0.24.2
     */
    public static <T> Optional<T> value(Indicator<T> indicator, int index) {
        Context context = CURRENT.get();
        if (context == null || context.suspended) {
            return Optional.empty();
        }
        return context.value(indicator, index);
    }

    /**
     * Returns the latest diagnostic of the current scope, if a scope is open.
     *
     * @return current diagnostic, or empty without an open scope
     * @since 0.24.2
     */
    public static Optional<Diagnostic> lastDiagnostic() {
        Context context = CURRENT.get();
        return context == null ? Optional.empty() : Optional.of(context.diagnostic);
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
     * The property controls the determinism contract core planners emit: a finite
     * positive value selects {@link Determinism#APPROXIMATE} with that tolerance,
     * while anything else — unset, blank, non-numeric, or non-positive — keeps
     * {@link Determinism#BITWISE_IDENTICAL} with {@code NaN} tolerance. Invalid
     * configuration never silently widens accuracy; it degrades to exact.
     *
     * @return finite positive approximate tolerance, or {@code NaN} for exact
     * @since 0.24.2
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
    }

    private static boolean enabled() {
        String configured = System.getProperty(PROPERTY);
        if (configured == null || configured.isBlank()) {
            return false;
        }
        return switch (configured.trim().toLowerCase(Locale.ROOT)) {
        case "off" -> false;
        case "auto" -> true;
        default ->
            throw new IllegalArgumentException(PROPERTY + " must be 'off' or 'auto', but was '" + configured + "'");
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
                List<Provider> loaded = new ArrayList<>();
                ServiceLoader.load(Provider.class).forEach(loaded::add);
                providers = List.copyOf(loaded);
                discoveredProviders = providers;
            }
        }
        return providers;
    }

    static synchronized void resetProvidersForTests(boolean clearPlanners) {
        discoveredProviders = null;
        if (clearPlanners) {
            PLANNERS.clear();
        }
        CURRENT.remove();
    }

    /**
     * Auto-closeable acceleration scope.
     *
     * @since 0.24.2
     */
    @FunctionalInterface
    public interface Scope extends AutoCloseable {

        /**
         * Closes the scope and restores any enclosing execution scope.
         *
         * @since 0.24.2
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
         * @since 0.24.2
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
         * this contract requires a finite positive kernel-request tolerance.
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
     * @since 0.24.2
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

        /** No planner claims the calculation, or the workload is ineligible. */
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
     * @param tolerance               numeric tolerance for validation, NaN when
     *                                exact
     * @param params                  operation parameters (ordinals, counts,
     *                                factors) defined by the operation contract
     * @param inputs                  read-only primitive input buffers
     * @param estimatedScalarNanos    planner scalar-baseline estimate for the
     *                                crossover comparison, non-positive when
     *                                unknown
     * @param peakDeviceBytesEstimate declared peak device memory in bytes
     * @since 0.24.2
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
            params = params.clone();
            List<double[]> copies = new ArrayList<>(inputs.size());
            for (double[] buffer : inputs) {
                copies.add(Objects.requireNonNull(buffer, "input buffer must not be null").clone());
            }
            inputs = List.copyOf(copies);
        }

        /**
         * Returns the number of decision indexes in the batch.
         *
         * @since 0.24.2
         */
        public int size() {
            return Math.addExact(Math.subtractExact(toInclusive, fromInclusive), 1);
        }

        /**
         * Returns the expected raw output length.
         *
         * @return {@code size() * outputsPerIndex}
         * @since 0.24.2
         */
        public int expectedOutputLength() {
            return Math.multiplyExact(size(), outputsPerIndex);
        }

        /**
         * Returns a copy of the operation parameters.
         *
         * @return defensive copy, never the live buffer
         * @since 0.24.2
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
     * @since 0.24.2
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
         * @since 0.24.2
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
     * @since 0.24.2
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
         * @since 0.24.2
         */
        public static Assessment supported(Backend backend, String deviceId, long predictedTotalNanos,
                long peakDeviceBytes, boolean deterministic) {
            return new Assessment(true, backend, deviceId, predictedTotalNanos, peakDeviceBytes, deterministic,
                    new Diagnostic(DiagnosticCode.ACCELERATED, "assessed", "supported"));
        }

        /**
         * Creates an unsupported assessment.
         *
         * @since 0.24.2
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
     * @since 0.24.2
     */
    public interface Provider {

        /**
         * Returns the stable provider identifier, defaulting to the class name.
         *
         * @return provider id
         * @since 0.24.2
         */
        default String providerId() {
            return getClass().getName();
        }

        /**
         * Assesses a request without initializing native code.
         *
         * @param request immutable kernel request
         * @return cost assessment
         * @since 0.24.2
         */
        Assessment assess(KernelRequest request);

        /**
         * Executes a request and returns raw primitives.
         *
         * @param request immutable kernel request
         * @return raw kernel output
         * @since 0.24.2
         */
        KernelResult execute(KernelRequest request);
    }

    private static final class Context implements Scope {

        private final BarSeries series;
        private final int fromInclusive;
        private final int toInclusive;
        private final Context previous;
        private final long startedNanos = System.nanoTime();
        private final IdentityHashMap<Indicator<?>, CachedBatch> batches = new IdentityHashMap<>();
        private final Set<Indicator<?>> scalarFallback = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Map<String, String> quarantine = new HashMap<>();

        private boolean suspended;
        private boolean providerAttempted;
        private Backend effectiveBackend = Backend.CPU;
        private String providerInUse = "none";
        private boolean nativeInitialized;
        private long providerElapsedNanos;
        private Diagnostic diagnostic = new Diagnostic(DiagnosticCode.UNSUPPORTED, "none",
                "no eligible acceleration request");

        private Context(BarSeries series, int fromInclusive, int toInclusive, Context previous) {
            this.series = series;
            this.fromInclusive = fromInclusive;
            this.toInclusive = toInclusive;
            this.previous = previous;
        }

        @SuppressWarnings("unchecked")
        private <T> Optional<T> value(Indicator<T> indicator, int index) {
            Objects.requireNonNull(indicator, "indicator must not be null");
            BarSeries indicatorSeries = indicator.getBarSeries();
            if (index < fromInclusive || index > toInclusive || fromInclusive < indicatorSeries.getBeginIndex()
                    || toInclusive > indicatorSeries.getEndIndex() || scalarFallback.contains(indicator)) {
                return Optional.empty();
            }
            CachedBatch cached = batches.get(indicator);
            if (cached != null && !cached.matchesCurrentSeries(indicatorSeries)) {
                batches.remove(indicator);
                cached = null;
            }
            if (cached == null) {
                cached = evaluate(indicator, index);
                if (cached == null) {
                    scalarFallback.add(indicator);
                    return Optional.empty();
                }
                batches.put(indicator, cached);
            }
            Object decoded = cached.value(index);
            return decoded == null ? Optional.empty() : Optional.of((T) decoded);
        }

        private <T> CachedBatch evaluate(Indicator<T> indicator, int index) {
            BarSeries indicatorSeries = indicator.getBarSeries();
            long revision = indicatorSeries.getBarHistoryRevision();
            if (revision < 0L) {
                diagnostic = new Diagnostic(DiagnosticCode.UNSUPPORTED, "none",
                        "series does not track bar-data revisions; accelerated batches cannot be invalidated");
                return null;
            }
            PlannedOperation planned;
            try {
                planned = plan(indicator, index);
            } catch (LinkageError | RuntimeException exception) {
                diagnostic = new Diagnostic(DiagnosticCode.UNSUPPORTED, "none", "planner failed for "
                        + indicator.getClass().getSimpleName() + ": " + failureMessage(exception));
                return null;
            }
            if (planned == null) {
                diagnostic = new Diagnostic(DiagnosticCode.UNSUPPORTED, "none",
                        "no operation planner claims " + indicator.getClass().getSimpleName());
                return null;
            }
            KernelRequest request = planned.request();
            if (request.peakDeviceBytesEstimate() > maxDeviceBytes()) {
                diagnostic = new Diagnostic(DiagnosticCode.UNSUPPORTED, "none", "peak device estimate "
                        + request.peakDeviceBytesEstimate() + " exceeds budget " + maxDeviceBytes());
                return null;
            }
            providerAttempted = true;
            List<Provider> providers;
            try {
                providers = providers();
            } catch (ServiceConfigurationError | LinkageError | RuntimeException exception) {
                diagnostic = new Diagnostic(DiagnosticCode.PROVIDER_FAILURE, "service-loader",
                        failureMessage(exception));
                return null;
            }
            if (providers.isEmpty()) {
                diagnostic = new Diagnostic(DiagnosticCode.NO_PROVIDER, "none",
                        "no acceleration provider was discovered");
                return null;
            }
            List<RankedProvider> candidates = assess(request, providers);
            if (candidates.isEmpty()) {
                if (diagnostic.code() != DiagnosticCode.CPU_FASTER) {
                    diagnostic = new Diagnostic(DiagnosticCode.NO_PROVIDER, "none",
                            "no provider supports " + request.operation());
                }
                return null;
            }
            BarSeriesChangeSnapshot before = indicatorSeries.getBarSeriesChangeSnapshot(revision);
            for (RankedProvider candidate : candidates) {
                if (quarantine.containsKey(quarantineKey(candidate, request))) {
                    continue;
                }
                KernelResult result;
                suspended = true;
                long started = System.nanoTime();
                try {
                    result = Objects.requireNonNull(candidate.provider.execute(request),
                            "acceleration provider returned null");
                } catch (LinkageError | RuntimeException exception) {
                    quarantine.put(quarantineKey(candidate, request), failureMessage(exception));
                    diagnostic = new Diagnostic(DiagnosticCode.PROVIDER_FAILURE, candidate.provider.providerId(),
                            failureMessage(exception));
                    continue;
                } finally {
                    providerElapsedNanos += System.nanoTime() - started;
                    suspended = false;
                }
                nativeInitialized |= result.nativeInitialized();
                double[] rawOutputs = result.outputs();
                if (rawOutputs.length != request.expectedOutputLength()) {
                    quarantine.put(quarantineKey(candidate, request), "malformed raw output");
                    diagnostic = new Diagnostic(DiagnosticCode.INVALID_RESULT, candidate.provider.providerId(),
                            "provider output did not exactly cover [%d, %d]".formatted(request.fromInclusive(),
                                    request.toInclusive()));
                    continue;
                }
                BarSeriesChangeSnapshot after = indicatorSeries.getBarSeriesChangeSnapshot(revision);
                if (!sameSeriesState(before, after)) {
                    diagnostic = new Diagnostic(DiagnosticCode.STALE_SERIES, candidate.provider.providerId(),
                            "series changed while the provider was evaluating");
                    continue;
                }
                List<Object> decoded;
                try {
                    decoded = decodeAll(request, rawOutputs, planned, indicatorSeries);
                } catch (LinkageError | RuntimeException exception) {
                    quarantine.put(quarantineKey(candidate, request), failureMessage(exception));
                    diagnostic = new Diagnostic(DiagnosticCode.INVALID_RESULT, candidate.provider.providerId(),
                            failureMessage(exception));
                    continue;
                }
                effectiveBackend = candidate.assessment.backend();
                providerInUse = candidate.provider.providerId();
                diagnostic = new Diagnostic(DiagnosticCode.ACCELERATED, providerInUse,
                        candidate.assessment.backend().name().toLowerCase(Locale.ROOT) + "/"
                                + candidate.assessment.deviceId() + " executed " + request.operation());
                return new CachedBatch(request.fromInclusive(), decoded, after);
            }
            return null;
        }

        private List<RankedProvider> assess(KernelRequest request, List<Provider> providers) {
            List<RankedProvider> candidates = new ArrayList<>();
            boolean cpuFasterObserved = false;
            String cpuFasterProvider = "none";
            String cpuFasterDetail = "";
            for (Provider provider : providers) {
                Assessment assessment;
                suspended = true;
                try {
                    assessment = provider.assess(request);
                } catch (LinkageError | RuntimeException exception) {
                    LOG.debug("Provider {} assessment failed: {}", provider.providerId(), exception.getMessage());
                    continue;
                } finally {
                    suspended = false;
                }
                if (assessment == null || !assessment.supported() || !assessment.deterministic()
                        || assessment.predictedTotalNanos() < 0 || Math.max(request.peakDeviceBytesEstimate(),
                                assessment.peakDeviceBytes()) > maxDeviceBytes()) {
                    continue;
                }
                long baseline = request.estimatedScalarNanos();
                if (baseline > 0 && (double) assessment.predictedTotalNanos() >= baseline * (1.0 - CPU_SAFETY_MARGIN)) {
                    cpuFasterObserved = true;
                    cpuFasterProvider = provider.providerId();
                    cpuFasterDetail = "predicted " + assessment.predictedTotalNanos() + "ns vs scalar baseline "
                            + baseline + "ns";
                    continue;
                }
                candidates.add(new RankedProvider(provider, assessment));
            }
            candidates.sort(
                    Comparator.comparingLong((RankedProvider candidate) -> candidate.assessment.predictedTotalNanos())
                            .thenComparing(candidate -> candidate.assessment.backend().name())
                            .thenComparing(candidate -> candidate.assessment.deviceId())
                            .thenComparing(candidate -> candidate.provider.providerId()));
            if (candidates.isEmpty() && cpuFasterObserved) {
                diagnostic = new Diagnostic(DiagnosticCode.CPU_FASTER, cpuFasterProvider, cpuFasterDetail);
            }
            return candidates;
        }

        private <T> PlannedOperation plan(Indicator<T> indicator, int index) {
            List<OperationPlanner> planners;
            synchronized (AccelerationRuntime.class) {
                planners = List.copyOf(PLANNERS);
            }
            for (OperationPlanner planner : planners) {
                PlannedOperation planned = planner.plan(indicator, index, toInclusive, series.numFactory());
                if (planned != null) {
                    return planned;
                }
            }
            return null;
        }

        private List<Object> decodeAll(KernelRequest request, double[] rawOutputs, PlannedOperation planned,
                BarSeries indicatorSeries) {
            NumFactory factory = indicatorSeries.numFactory();
            List<Object> decoded = new ArrayList<>(request.size());
            for (int position = 0; position < request.size(); position++) {
                int index = request.fromInclusive() + position;
                double[] slice = new double[request.outputsPerIndex()];
                System.arraycopy(rawOutputs, position * request.outputsPerIndex(), slice, 0, slice.length);
                Object value = planned.decoder().decode(slice, index, factory);
                if (value == null) {
                    throw new IllegalStateException(
                            "decoder returned null for index " + index + " of " + request.operation());
                }
                decoded.add(value);
            }
            return decoded;
        }

        private static String quarantineKey(RankedProvider candidate, KernelRequest request) {
            return candidate.provider.providerId() + "|" + candidate.assessment.deviceId() + "|" + request.operation()
                    + "/v" + request.operation().version();
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
            long scopeNanos = System.nanoTime() - startedNanos;
            if (providerAttempted) {
                LOG.debug(
                        "ta4j acceleration requested=auto effectiveBackend={} provider={} code={} nativeInitialized={} providerNanos={} scopeNanos={} range=[{},{}] detail={}",
                        effectiveBackend.name().toLowerCase(Locale.ROOT), diagnostic.providerId(), diagnostic.code(),
                        nativeInitialized, providerElapsedNanos, scopeNanos, fromInclusive, toInclusive,
                        diagnostic.detail());
            }
        }
    }

    private record RankedProvider(Provider provider, Assessment assessment) {
    }

    private static boolean sameSeriesState(BarSeriesChangeSnapshot left, BarSeriesChangeSnapshot right) {
        return left.revision() == right.revision() && left.removedThroughIndex() == right.removedThroughIndex()
                && left.maximumBarCount() == right.maximumBarCount() && left.endIndex() == right.endIndex();
    }

    private record CachedBatch(int fromInclusive, List<?> values, BarSeriesChangeSnapshot snapshot) {

        private CachedBatch {
            values = List.copyOf(values);
            Objects.requireNonNull(snapshot, "snapshot must not be null");
        }

        private Object value(int index) {
            int offset = index - fromInclusive;
            return offset < 0 || offset >= values.size() ? null : values.get(offset);
        }

        private boolean matchesCurrentSeries(BarSeries series) {
            return sameSeriesState(series.getBarSeriesChangeSnapshot(snapshot.revision()), snapshot);
        }
    }

    private static String failureMessage(Throwable failure) {
        String message = failure.getMessage();
        return failure.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }
}

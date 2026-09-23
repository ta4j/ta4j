/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.acceleration;

import java.util.ArrayList;
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
 * <p>
 * Planner declines are typed. A transient decline (a warm-up prefix, a stale
 * snapshot, a resource limit a shorter range could satisfy) only defers the
 * range to its retry index, so an early read cannot disable acceleration for
 * the rest of the scope; a permanent decline falls back to the scalar lane for
 * the whole scope. Provider assessment declines are scope-level decisions about
 * the request shape and are recorded for the scope as well, but their typed
 * diagnostic — provider id, accuracy or library requirement, memory detail — is
 * preserved instead of being replaced by a generic code. When no provider can
 * run a request, the scope diagnostic keeps the first provider-attributed
 * decline.
 *
 * @since 0.25.1
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

    private static final Logger LOG = LoggerFactory.getLogger(AccelerationRuntime.class);

    private static final long DEFAULT_MAX_DEVICE_BYTES = 1L << 30;

    /**
     * Safety margin applied to the CPU-crossover comparison: the best accelerator
     * must beat the scalar baseline by more than this fraction.
     */
    private static final double CPU_SAFETY_MARGIN = 0.10;

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

    /**
     * Published copy-on-write: readers never take the registration lock.
     */
    private static volatile List<OperationPlanner> planners = List.of();

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
     * @since 0.25.1
     */
    public static synchronized void registerPlanner(OperationPlanner planner) {
        Objects.requireNonNull(planner, "planner must not be null");
        for (OperationPlanner registered : planners) {
            if (registered.getClass() == planner.getClass()) {
                return;
            }
        }
        List<OperationPlanner> updated = new ArrayList<>(planners);
        updated.add(planner);
        planners = List.copyOf(updated);
    }

    /**
     * Opens an acceleration scope for a backtest run range and binds it to the
     * current thread.
     *
     * @param series the run's series
     * @param from   inclusive run begin index
     * @param to     inclusive run end index
     * @return scope handle closing back to the enclosing scope
     * @since 0.25.1
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
     * Returns an accelerated value from the current scope when one was successfully
     * produced.
     *
     * @param indicator indicator requesting a value
     * @param index     requested index
     * @param <T>       value type
     * @return accelerated value, or empty to use scalar evaluation
     * @since 0.25.1
     */
    public static <T> Optional<T> value(Indicator<T> indicator, int index) {
        Context context = CURRENT.get();
        if (context == null || context.suspended) {
            return Optional.empty();
        }
        return context.value(indicator, index);
    }

    /**
     * Returns the diagnostic of the currently open scope, or the diagnostic of the
     * most recently closed automatic scope on this thread when no scope is open —
     * for example after {@code BarSeriesManager} has returned.
     *
     * @return current or last-closed diagnostic, or empty when no automatic scope
     *         was ever closed on this thread
     * @since 0.25.1
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
     * @since 0.25.1
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
     * @since 0.25.1
     */
    @FunctionalInterface
    public interface Scope extends AutoCloseable {

        /**
         * Closes the scope and restores any enclosing execution scope.
         *
         * @since 0.25.1
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
         * @since 0.25.1
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
        BITWISE_IDENTICAL
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
     * @since 0.25.1
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
     * @since 0.25.1
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
         * @since 0.25.1
         */
        public int size() {
            return Math.addExact(Math.subtractExact(toInclusive, fromInclusive), 1);
        }

        /**
         * Returns the expected raw output length.
         *
         * @return {@code size() * outputsPerIndex}
         * @since 0.25.1
         */
        public int expectedOutputLength() {
            return Math.multiplyExact(size(), outputsPerIndex);
        }

        /**
         * Returns a copy of the operation parameters.
         *
         * @return defensive copy, never the live buffer
         * @since 0.25.1
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
     * @since 0.25.1
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
         * @since 0.25.1
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
     * @since 0.25.1
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
         * @since 0.25.1
         */
        public static Assessment supported(Backend backend, String deviceId, long predictedTotalNanos,
                long peakDeviceBytes, boolean deterministic) {
            return new Assessment(true, backend, deviceId, predictedTotalNanos, peakDeviceBytes, deterministic,
                    new Diagnostic(DiagnosticCode.ACCELERATED, "assessed", "supported"));
        }

        /**
         * Creates an unsupported assessment.
         *
         * @since 0.25.1
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
     * @since 0.25.1
     */
    public interface Provider {

        /**
         * Returns the stable provider identifier, defaulting to the class name.
         *
         * @return provider id
         * @since 0.25.1
         */
        default String providerId() {
            return getClass().getName();
        }

        /**
         * Assesses a request without initializing native code.
         *
         * @param request immutable kernel request
         * @return cost assessment
         * @since 0.25.1
         */
        Assessment assess(KernelRequest request);

        /**
         * Executes a request and returns raw primitives.
         *
         * @param request immutable kernel request
         * @return raw kernel output
         * @since 0.25.1
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
        private final IdentityHashMap<Indicator<?>, Integer> retries = new IdentityHashMap<>();
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
        private <T> Optional<T> value(Indicator<T> indicator, int index) {
            Objects.requireNonNull(indicator, "indicator must not be null");
            BarSeries indicatorSeries = indicator.getBarSeries();
            if (index < fromInclusive || index > toInclusive || fromInclusive < indicatorSeries.getBeginIndex()
                    || toInclusive > indicatorSeries.getEndIndex() || scalarFallback.contains(indicator)
                    || retryPending(indicator, index)) {
                return Optional.empty();
            }
            CachedBatch cached = batches.get(indicator);
            if (cached != null && (!cached.matchesCurrentSeries(indicatorSeries) || index > cached.toInclusive)) {
                batches.remove(indicator);
                retries.remove(indicator);
                cached = null;
            }
            if (cached == null) {
                Evaluation evaluation = evaluate(indicator, index);
                if (evaluation.batch() == null) {
                    if (evaluation.permanent()) {
                        scalarFallback.add(indicator);
                    } else {
                        retries.put(indicator, evaluation.retryFromIndex());
                    }
                    return Optional.empty();
                }
                batches.put(indicator, evaluation.batch());
                retries.remove(indicator);
                cached = evaluation.batch();
            }
            Object decoded = cached.value(index);
            return decoded == null ? Optional.empty() : Optional.of((T) decoded);
        }

        private boolean retryPending(Indicator<?> indicator, int index) {
            Integer retryFromIndex = retries.get(indicator);
            return retryFromIndex != null && index < retryFromIndex;
        }

        private <T> Evaluation evaluate(Indicator<T> indicator, int index) {
            requested = true;
            BarSeries indicatorSeries = indicator.getBarSeries();
            BarSeriesChangeSnapshot beforePlanning = indicatorSeries.getBarSeriesChangeSnapshot(-1L);
            if (indicatorSeries.getBarHistoryRevision() < 0L) {
                diagnostic = new Diagnostic(DiagnosticCode.UNSUPPORTED, "none",
                        "series does not track bar-data revisions; accelerated batches cannot be invalidated");
                return Evaluation.unsupported();
            }
            PlanAttempt attempt;
            try {
                attempt = plan(indicator, index);
            } catch (LinkageError | RuntimeException exception) {
                diagnostic = new Diagnostic(DiagnosticCode.UNSUPPORTED, "none", "planner failed for "
                        + indicator.getClass().getSimpleName() + ": " + failureMessage(exception));
                return Evaluation.unsupported();
            }
            if (!attempt.isPlanned()) {
                PlanDecline decline = attempt.decline();
                diagnostic = new Diagnostic(DiagnosticCode.UNSUPPORTED, "none", decline.detail());
                return decline.permanent() ? Evaluation.unsupported() : Evaluation.retryFrom(decline.retryFromIndex());
            }
            PlannedOperation planned = attempt.operation();
            KernelRequest request = planned.request();
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
                    return Evaluation.retryFrom(index + 1);
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
                    return Evaluation.retryFrom(index + 1);
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
                    decoded = decodeAll(request, rawOutputs, planned, indicatorSeries);
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
                    return Evaluation.retryFrom(index + 1);
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
                if (baseline > 0 && (double) assessment.predictedTotalNanos() >= baseline * (1.0 - CPU_SAFETY_MARGIN)) {
                    cpuFasterObserved = true;
                    cpuFasterProvider = providerId;
                    cpuFasterDetail = "predicted " + assessment.predictedTotalNanos() + "ns vs scalar baseline "
                            + baseline + "ns";
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

        private PlanAttempt plan(Indicator<?> indicator, int index) {
            List<OperationPlanner> snapshot = planners;
            PlanDecline retryable = null;
            PlanDecline permanent = null;
            for (OperationPlanner planner : snapshot) {
                PlanAttempt attempt = Objects.requireNonNull(planner.plan(indicator, index, toInclusive,
                        indicator.getBarSeries().numFactory(), memoryLimitBytes), "planner returned no attempt");
                if (attempt.isPlanned()) {
                    return attempt;
                }
                PlanDecline decline = attempt.decline();
                if (!decline.permanent()) {
                    if (retryable == null || decline.retryFromIndex() < retryable.retryFromIndex()) {
                        retryable = decline;
                    }
                } else if (permanent == null && !decline.equals(PlanDecline.unclaimed())) {
                    permanent = decline;
                }
            }
            PlanDecline decline = retryable != null ? retryable
                    : permanent != null ? permanent
                            : PlanDecline
                                    .unsupported("no operation planner claims " + indicator.getClass().getSimpleName());
            return PlanAttempt.declined(decline);
        }

        private List<Object> decodeAll(KernelRequest request, double[] rawOutputs, PlannedOperation planned,
                BarSeries indicatorSeries) {
            NumFactory factory = indicatorSeries.numFactory();
            List<Object> decoded = new ArrayList<>(request.size());
            // One slice is reused across indexes; decoders must not retain it.
            double[] slice = new double[request.outputsPerIndex()];
            for (int position = 0; position < request.size(); position++) {
                int index = request.fromInclusive() + position;
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
     * Result of one runtime evaluation attempt: a publishable batch, or the typed
     * disposition of a decline. A permanent decline sends the indicator to the
     * scope's scalar lane; a transient one only defers it to a retry index.
     */
    private record Evaluation(CachedBatch batch, boolean permanent, int retryFromIndex) {

        private static Evaluation accelerated(CachedBatch batch) {
            return new Evaluation(batch, false, -1);
        }

        private static Evaluation unsupported() {
            return new Evaluation(null, true, -1);
        }

        private static Evaluation retryFrom(int retryFromIndex) {
            return new Evaluation(null, false, retryFromIndex);
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

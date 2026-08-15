/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.acceleration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
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
 * Transparent, opt-in execution boundary for batched indicator acceleration.
 *
 * <p>
 * Applications do not call this type directly. {@code BarSeriesManager} opens a
 * bounded execution scope for every backtest run, and explicitly supported
 * indicators ask the current scope for an accelerated value before falling back
 * to scalar {@link Indicator#getValue(int)} evaluation. Optional providers are
 * discovered through {@link ServiceLoader} only after
 * {@code -Dta4j.acceleration=auto} and an eligible indicator request. Omitting
 * the property, or setting it to {@code off}, retains the ordinary scalar path
 * without provider discovery.
 *
 * <p>
 * This deliberately is not a general indicator graph compiler. Providers must
 * reject every graph family they do not understand, and scalar
 * {@link Indicator#getValue(int)} evaluation remains the fallback oracle.
 *
 * <p>
 * Cached accelerated batches are bound to
 * {@link BarSeries.BarSeriesChangeSnapshot} change snapshots: any revision,
 * retained-range, capacity, or end-index change invalidates the batch before
 * the next cached read, and series that do not track revisions are never
 * accelerated.
 *
 * @since 0.24.2
 */
public final class AccelerationRuntime {

    /** JVM property controlling transparent acceleration. */
    public static final String PROPERTY = "ta4j.acceleration";

    private static final Logger LOG = LoggerFactory.getLogger(AccelerationRuntime.class);
    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();
    private static final Scope DISABLED_SCOPE = () -> {
    };

    private static volatile List<Provider> discoveredProviders;

    private AccelerationRuntime() {
    }

    /**
     * Opens a bounded acceleration scope for one backtest execution.
     *
     * @param series        series being evaluated
     * @param fromInclusive first run index
     * @param toInclusive   last run index
     * @return scope to close after execution
     * @since 0.24.2
     */
    public static Scope open(BarSeries series, int fromInclusive, int toInclusive) {
        Objects.requireNonNull(series, "series must not be null");
        if (!enabled()) {
            Context previous = CURRENT.get();
            if (previous == null) {
                return DISABLED_SCOPE;
            }
            CURRENT.remove();
            return () -> CURRENT.set(previous);
        }
        Context previous = CURRENT.get();
        Context context = new Context(fromInclusive, toInclusive, previous);
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
                loaded.sort(Comparator.comparing(provider -> provider.getClass().getName()));
                providers = List.copyOf(loaded);
                discoveredProviders = providers;
            }
        }
        return providers;
    }

    static void useProvidersForTests(List<Provider> providers) {
        discoveredProviders = List.copyOf(providers);
    }

    static void resetProvidersForTests() {
        discoveredProviders = null;
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
     * Lazy ServiceLoader provider contract implemented by optional artifacts.
     *
     * <p>
     * Provider constructors must not probe devices or load native libraries.
     *
     * @since 0.24.2
     */
    public interface Provider {

        /**
         * Evaluates an eligible request or returns a typed non-executed result.
         *
         * @param request immutable request
         * @param <T>     value type
         * @return provider decision
         * @since 0.24.2
         */
        <T> Result<T> evaluate(Request<T> request);
    }

    /**
     * Immutable provider request.
     *
     * @param indicator     concrete indicator
     * @param fromInclusive first requested index
     * @param toInclusive   last requested index
     * @param <T>           value type
     * @since 0.24.2
     */
    public record Request<T>(Indicator<T> indicator, int fromInclusive, int toInclusive) {

        /**
         * Validates a request.
         *
         * @since 0.24.2
         */
        public Request {
            Objects.requireNonNull(indicator, "indicator must not be null");
            BarSeries series = Objects.requireNonNull(indicator.getBarSeries(), "indicator series must not be null");
            if (fromInclusive < series.getBeginIndex() || toInclusive > series.getEndIndex()
                    || fromInclusive > toInclusive) {
                throw new IllegalArgumentException("Acceleration range [%d, %d] is outside retained series [%d, %d]"
                        .formatted(fromInclusive, toInclusive, series.getBeginIndex(), series.getEndIndex()));
            }
        }

        /**
         * Returns the indicator's read-only series view.
         *
         * @return source series for the request
         * @since 0.24.2
         */
        public BarSeries series() {
            return indicator.getBarSeries();
        }

        /**
         * @return exact number of requested values
         * @since 0.24.2
         */
        public int size() {
            return Math.addExact(Math.subtractExact(toInclusive, fromInclusive), 1);
        }
    }

    /**
     * Provider decision and, when executed, ordered values for the exact request.
     *
     * @param status            decision status
     * @param backend           effective backend
     * @param values            exact ordered values when executed
     * @param nativeInitialized whether native code was initialized
     * @param elapsedNanos      provider wall time
     * @param diagnostic        typed decision detail
     * @param <T>               value type
     * @since 0.24.2
     */
    public record Result<T>(Status status, Backend backend, List<T> values, boolean nativeInitialized,
            long elapsedNanos, Diagnostic diagnostic) {

        /**
         * Validates and defensively copies a result.
         *
         * @since 0.24.2
         */
        public Result {
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(backend, "backend must not be null");
            values = List.copyOf(Objects.requireNonNull(values, "values must not be null"));
            Objects.requireNonNull(diagnostic, "diagnostic must not be null");
            if (elapsedNanos < 0L) {
                throw new IllegalArgumentException("elapsedNanos must be >= 0");
            }
            if (status != Status.EXECUTED && !values.isEmpty()) {
                throw new IllegalArgumentException("Non-executed acceleration results must not contain values");
            }
        }

        /**
         * Creates a successful provider result.
         *
         * @param backend           effective GPU backend
         * @param values            exact ordered values
         * @param nativeInitialized whether native code initialized
         * @param elapsedNanos      provider elapsed time
         * @param diagnostic        execution detail
         * @param <T>               value type
         * @return executed result
         * @since 0.24.2
         */
        public static <T> Result<T> executed(Backend backend, List<T> values, boolean nativeInitialized,
                long elapsedNanos, Diagnostic diagnostic) {
            return new Result<>(Status.EXECUTED, backend, values, nativeInitialized, elapsedNanos, diagnostic);
        }

        /**
         * Creates a non-executed provider decision.
         *
         * @param status     skipped, unavailable, or failed status
         * @param backend    provider backend
         * @param diagnostic decision detail
         * @param <T>        value type
         * @return non-executed result
         * @since 0.24.2
         */
        public static <T> Result<T> notExecuted(Status status, Backend backend, Diagnostic diagnostic) {
            if (status == Status.EXECUTED) {
                throw new IllegalArgumentException("Use executed(...) for executed results");
            }
            return new Result<>(status, backend, List.of(), false, 0L, diagnostic);
        }
    }

    /** Provider decision status. @since 0.24.2 */
    public enum Status {
        /** GPU values were produced. */
        EXECUTED,
        /** The request was valid but CPU was predicted to be faster. */
        SKIPPED,
        /** No usable provider/device was available. */
        UNAVAILABLE,
        /** Provider execution failed and scalar fallback is required. */
        FAILED
    }

    /** Effective execution backend. @since 0.24.2 */
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

        /** Validates a diagnostic. @since 0.24.2 */
        public Diagnostic {
            Objects.requireNonNull(code, "code must not be null");
            Objects.requireNonNull(providerId, "providerId must not be null");
            Objects.requireNonNull(detail, "detail must not be null");
        }
    }

    /** Stable diagnostic and fallback codes. @since 0.24.2 */
    public enum DiagnosticCode {
        /** GPU execution completed. */
        ACCELERATED,
        /** No optional provider artifact was present. */
        NO_PROVIDER,
        /** The graph or numeric representation was unsupported. */
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

    private static final class Context implements Scope {

        private final int fromInclusive;
        private final int toInclusive;
        private final Context previous;
        private final long startedNanos = System.nanoTime();
        private final IdentityHashMap<Indicator<?>, CachedBatch> batches = new IdentityHashMap<>();
        private final Set<Indicator<?>> scalarFallback = Collections.newSetFromMap(new IdentityHashMap<>());

        private boolean suspended;
        private boolean providerAttempted;
        private Backend effectiveBackend = Backend.CPU;
        private boolean nativeInitialized;
        private long providerElapsedNanos;
        private Diagnostic diagnostic = new Diagnostic(DiagnosticCode.UNSUPPORTED, "none",
                "no eligible acceleration request");

        private Context(int fromInclusive, int toInclusive, Context previous) {
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
            Object value = cached.value(index);
            return value == null ? Optional.empty() : Optional.of((T) value);
        }

        private <T> CachedBatch evaluate(Indicator<T> indicator, int index) {
            BarSeries indicatorSeries = indicator.getBarSeries();
            long revision = indicatorSeries.getBarHistoryRevision();
            if (revision < 0L) {
                diagnostic = new Diagnostic(DiagnosticCode.UNSUPPORTED, "none",
                        "series does not track bar-data revisions; accelerated batches cannot be invalidated");
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
            Request<T> request = new Request<>(indicator, index, toInclusive);
            BarSeriesChangeSnapshot before = indicatorSeries.getBarSeriesChangeSnapshot(revision);
            for (Provider provider : providers) {
                Result<T> result;
                suspended = true;
                long started = System.nanoTime();
                try {
                    result = Objects.requireNonNull(provider.evaluate(request), "acceleration provider returned null");
                } catch (LinkageError | RuntimeException exception) {
                    diagnostic = new Diagnostic(DiagnosticCode.PROVIDER_FAILURE, provider.getClass().getName(),
                            failureMessage(exception));
                    continue;
                } finally {
                    providerElapsedNanos += System.nanoTime() - started;
                    suspended = false;
                }
                diagnostic = result.diagnostic();
                nativeInitialized |= result.nativeInitialized();
                if (result.status() != Status.EXECUTED) {
                    continue;
                }
                if (result.backend() == Backend.CPU || result.values().size() != request.size()
                        || result.values().stream().anyMatch(Objects::isNull)) {
                    diagnostic = new Diagnostic(DiagnosticCode.INVALID_RESULT, result.diagnostic().providerId(),
                            "provider result did not exactly cover [%d, %d]".formatted(request.fromInclusive(),
                                    request.toInclusive()));
                    continue;
                }
                BarSeriesChangeSnapshot after = indicatorSeries.getBarSeriesChangeSnapshot(revision);
                if (!sameSeriesState(before, after)) {
                    diagnostic = new Diagnostic(DiagnosticCode.STALE_SERIES, result.diagnostic().providerId(),
                            "series changed while the provider was evaluating");
                    continue;
                }
                effectiveBackend = result.backend();
                return new CachedBatch(request.fromInclusive(), result.values(), after);
            }
            return null;
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

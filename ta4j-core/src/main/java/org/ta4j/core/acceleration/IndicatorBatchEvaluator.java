/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.acceleration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;

/**
 * Dependency-free explicit batch evaluator for ta4j indicators.
 *
 * <p>
 * This core evaluator preserves scalar semantics by calling
 * {@link Indicator#getValue(int)} for every requested index. Optional
 * accelerator modules can reuse the same request/result contracts and add
 * adapter/provider planning, but scalar evaluation remains the correctness
 * oracle.
 *
 * @since 0.23.1
 */
public final class IndicatorBatchEvaluator {

    private IndicatorBatchEvaluator() {
    }

    /**
     * Evaluates with the JVM property configuration captured on first use.
     *
     * @param indicator     indicator to evaluate
     * @param fromInclusive first bar index
     * @param toInclusive   last bar index
     * @param <T>           indicator value type
     * @return ordered batch result
     * @since 0.23.1
     */
    public static <T> IndicatorBatchResult<T> evaluate(Indicator<T> indicator, int fromInclusive, int toInclusive) {
        return evaluate(indicator, fromInclusive, toInclusive, SystemConfigHolder.CONFIG);
    }

    /**
     * Evaluates with an explicit configuration.
     *
     * @param indicator     indicator to evaluate
     * @param fromInclusive first bar index
     * @param toInclusive   last bar index
     * @param config        acceleration configuration
     * @param <T>           indicator value type
     * @return ordered batch result
     * @since 0.23.1
     */
    public static <T> IndicatorBatchResult<T> evaluate(Indicator<T> indicator, int fromInclusive, int toInclusive,
            AccelerationConfig config) {
        IndicatorBatchRequest<T> request = new IndicatorBatchRequest<>(indicator, fromInclusive, toInclusive, config);
        return evaluateScalar(request, scalarDiagnostics(config));
    }

    private static <T> IndicatorBatchResult<T> evaluateScalar(IndicatorBatchRequest<T> request,
            AccelerationDiagnostics diagnostics) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(diagnostics, "diagnostics must not be null");
        validateRange(request.indicator(), request.fromInclusive(), request.toInclusive());
        SnapshotIdentity before = SnapshotIdentity.capture(request.indicator().getBarSeries());
        List<IndexedIndicatorValue<T>> values = new ArrayList<>(request.toInclusive() - request.fromInclusive() + 1);
        for (int index = request.fromInclusive(); index <= request.toInclusive(); index++) {
            values.add(new IndexedIndicatorValue<>(index, request.indicator().getValue(index)));
        }
        SnapshotIdentity after = SnapshotIdentity.capture(request.indicator().getBarSeries());
        if (!before.equals(after)) {
            throw new AccelerationException(AccelerationDiagnosticCode.STALE_SNAPSHOT,
                    "BarSeries changed while evaluating indicator batch");
        }
        return new IndicatorBatchResult<>(values, diagnostics);
    }

    private static <T> void validateRange(Indicator<T> indicator, int fromInclusive, int toInclusive) {
        Objects.requireNonNull(indicator, "indicator must not be null");
        BarSeries series = Objects.requireNonNull(indicator.getBarSeries(), "indicator bar series must not be null");
        if (series.isEmpty()) {
            throw new IllegalArgumentException("Cannot evaluate an empty BarSeries");
        }
        if (fromInclusive < series.getBeginIndex() || toInclusive > series.getEndIndex()) {
            throw new IllegalArgumentException("Batch range must be within the series begin/end indexes");
        }
    }

    private static AccelerationDiagnostics scalarDiagnostics(AccelerationConfig config) {
        AccelerationConfig checked = Objects.requireNonNull(config, "config must not be null");
        if (checked.required() && checked.mode().canUseDevice()) {
            throw new AccelerationException(AccelerationDiagnosticCode.REQUIRED_PROVIDER_UNAVAILABLE,
                    "Core batch evaluator has no device provider for required %s mode".formatted(checked.mode()));
        }
        List<AccelerationDiagnostic> diagnostics = new ArrayList<>();
        if (checked.mode() == AccelerationMode.OFF) {
            diagnostics.add(AccelerationDiagnostic.of(AccelerationDiagnosticCode.ACCELERATION_OFF,
                    "Acceleration is disabled; scalar CPU evaluation was used"));
        } else if (checked.mode() == AccelerationMode.CPU) {
            diagnostics.add(AccelerationDiagnostic.of(AccelerationDiagnosticCode.CPU_REQUESTED,
                    "CPU evaluation was requested explicitly"));
        } else {
            diagnostics.add(AccelerationDiagnostic.of(AccelerationDiagnosticCode.UNSUPPORTED_GRAPH,
                    "No optional accelerator module selected this request; scalar CPU fallback was used"));
        }
        diagnostics.add(AccelerationDiagnostic.of(AccelerationDiagnosticCode.CPU_EVALUATED,
                "Values were produced by Indicator#getValue(int)"));
        return new AccelerationDiagnostics(checked.mode(), AccelerationMode.CPU, "cpu", "scalar-cpu", false,
                diagnostics);
    }

    private record SnapshotIdentity(long revision, int beginIndex, int endIndex, int removedBarsCount) {

        private static SnapshotIdentity capture(BarSeries series) {
            return new SnapshotIdentity(series.getBarHistoryRevision(), series.getBeginIndex(), series.getEndIndex(),
                    series.getRemovedBarsCount());
        }
    }

    private static final class SystemConfigHolder {
        private static final AccelerationConfig CONFIG = AccelerationConfig.fromSystemProperties();
    }
}

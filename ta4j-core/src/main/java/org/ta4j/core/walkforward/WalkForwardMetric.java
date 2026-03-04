/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Metric evaluated on a set of walk-forward observations.
 *
 * @param <P> prediction payload type
 * @param <O> realized outcome type
 * @since 0.22.4
 */
public interface WalkForwardMetric<P, O> {

    /**
     * @return unique metric name
     * @since 0.22.4
     */
    String name();

    /**
     * Computes metric value for provided observations.
     *
     * @param observations observations to evaluate
     * @return metric value, or {@link Double#NaN} when unavailable
     * @since 0.22.4
     */
    double compute(List<WalkForwardObservation<P, O>> observations);

    /**
     * Groups observations by originating snapshot key while preserving encounter
     * order.
     *
     * @param observations observations to group
     * @param <P>          prediction payload type
     * @param <O>          realized outcome type
     * @return snapshot-key grouped observations
     * @since 0.22.4
     */
    static <P, O> Map<String, List<WalkForwardObservation<P, O>>> groupBySnapshot(
            List<WalkForwardObservation<P, O>> observations) {
        Map<String, List<WalkForwardObservation<P, O>>> grouped = new LinkedHashMap<>();
        for (WalkForwardObservation<P, O> observation : observations) {
            String key = observation.snapshot().snapshotKey();
            grouped.computeIfAbsent(key, unused -> new ArrayList<>()).add(observation);
        }
        return grouped;
    }

    /**
     * Clamps the provided value to {@code [0,1]} and maps {@link Double#NaN} to
     * {@code 0.0}.
     *
     * @param value input value
     * @return clamped value
     * @since 0.22.4
     */
    static double clamp01(double value) {
        if (Double.isNaN(value)) {
            return 0.0;
        }
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }

    /**
     * Computes base-2 logarithm.
     *
     * @param value input value
     * @return base-2 logarithm
     * @since 0.22.4
     */
    static double log2(double value) {
        return Math.log(value) / Math.log(2.0);
    }
}

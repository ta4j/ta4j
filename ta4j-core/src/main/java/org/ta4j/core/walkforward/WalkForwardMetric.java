/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

import java.util.List;

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
}

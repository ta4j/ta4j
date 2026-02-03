/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import org.ta4j.core.analysis.frequency.SampleSummary;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Annualization mode for ratio criteria.
 *
 * @since 0.22.2
 */
public enum Annualization {
    PERIOD, ANNUALIZED;

    /**
     * Applies annualization to a per-period ratio, if needed.
     *
     * @param perPeriod  the per-period ratio value
     * @param summary    summary metadata used for annualization factors
     * @param numFactory the numeric factory used for calculations
     * @return the annualized ratio or the input value when annualization is not
     *         requested
     * @since 0.22.2
     */
    public Num apply(Num perPeriod, SampleSummary summary, NumFactory numFactory) {
        if (this == PERIOD) {
            return perPeriod;
        }
        return summary.annualizationFactor(numFactory).map(perPeriod::multipliedBy).orElse(perPeriod);
    }
}

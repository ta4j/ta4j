/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

/**
 * Defines whether a rolling statistic should use sample or population formulas.
 *
 * @since 0.22.4
 */
public enum SampleType {

    /** Uses sample formulas, e.g. variance divisor {@code n - 1}. */
    SAMPLE,
    /** Uses population formulas, e.g. variance divisor {@code n}. */
    POPULATION;

    /**
     * @return {@code true} when this type is {@link #SAMPLE}
     * @since 0.22.4
     */
    public boolean isSample() {
        return this == SAMPLE;
    }
}

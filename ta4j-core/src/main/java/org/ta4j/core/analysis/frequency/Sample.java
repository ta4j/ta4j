/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.frequency;

import java.util.Objects;
import org.ta4j.core.num.Num;

/**
 * Single observation for frequency-aware statistics.
 *
 * <p>
 * Each sample includes the observed {@code value} alongside the elapsed time in
 * years ({@code deltaYears}) since the previous observation. The time delta is
 * used to derive annualization factors when summarizing unevenly spaced series.
 * </p>
 *
 * @param value      the observed numeric value
 * @param deltaYears the elapsed time in years since the previous observation
 * @since 0.22.2
 */
public record Sample(Num value, Num deltaYears) {
    /**
     * Creates a sample with the provided value and elapsed time in years.
     *
     * @param value      the observed numeric value
     * @param deltaYears the elapsed time in years since the previous observation
     * @throws NullPointerException if {@code value} or {@code deltaYears} is
     *                              {@code null}
     */
    public Sample {
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(deltaYears, "deltaYears must not be null");
    }
}

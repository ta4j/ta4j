/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;

/**
 * Utility methods for validating indicator composition contracts.
 *
 * @since 0.22.2
 */
public final class IndicatorUtils {

    private IndicatorUtils() {
    }

    /**
     * Ensures all indicators reference the same {@link BarSeries} instance.
     *
     * <p>
     * Series matching is based on instance identity, not {@code equals()}.
     *
     * @param firstIndicator       first indicator to validate
     * @param secondIndicator      second indicator to validate
     * @param additionalIndicators additional indicators to validate
     * @return shared bar series instance
     * @throws NullPointerException     if any indicator is {@code null} or does not
     *                                  reference a bar series
     * @throws IllegalArgumentException if indicators use different series instances
     * @since 0.22.2
     */
    public static BarSeries requireSameSeries(Indicator<?> firstIndicator, Indicator<?> secondIndicator,
            Indicator<?>... additionalIndicators) {
        Indicator<?> validatedFirst = Objects.requireNonNull(firstIndicator, "firstIndicator must not be null");
        Indicator<?> validatedSecond = Objects.requireNonNull(secondIndicator, "secondIndicator must not be null");
        Indicator<?>[] validatedAdditional = Objects.requireNonNull(additionalIndicators,
                "additionalIndicators must not be null");

        BarSeries sharedSeries = requireSeries(validatedFirst, "firstIndicator");
        ensureSameSeries(sharedSeries, validatedSecond, "secondIndicator");

        for (int i = 0; i < validatedAdditional.length; i++) {
            ensureSameSeries(sharedSeries, validatedAdditional[i], "additionalIndicators[" + i + "]");
        }

        return sharedSeries;
    }

    private static BarSeries requireSeries(Indicator<?> indicator, String argumentName) {
        return Objects.requireNonNull(indicator.getBarSeries(), argumentName + " must reference a bar series");
    }

    private static void ensureSameSeries(BarSeries expectedSeries, Indicator<?> indicator, String argumentName) {
        Indicator<?> validatedIndicator = Objects.requireNonNull(indicator, argumentName + " must not be null");
        BarSeries actualSeries = requireSeries(validatedIndicator, argumentName);
        if (actualSeries != expectedSeries) {
            throw new IllegalArgumentException("Indicators must share the same BarSeries instance");
        }
    }
}

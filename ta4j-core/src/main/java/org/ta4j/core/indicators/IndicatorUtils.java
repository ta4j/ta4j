/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Utility methods for validating indicator composition contracts.
 *
 * @since 0.22.3
 */
public final class IndicatorUtils {

    private IndicatorUtils() {
    }

    /**
     * Ensures all indicators reference the same {@link BarSeries} instance.
     *
     * @param firstIndicator       first indicator to validate
     * @param secondIndicator      second indicator to validate
     * @param additionalIndicators additional indicators to validate
     * @return shared bar series instance
     * @throws NullPointerException     if any indicator is {@code null} or does not
     *                                  reference a bar series
     * @throws IllegalArgumentException if indicators use different series instances
     * @since 0.22.3
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

    /**
     * Ensures an indicator reference is present.
     *
     * @param indicator    indicator reference
     * @param argumentName argument name used for error messaging
     * @param <T>          indicator value type
     * @return validated indicator
     * @throws IllegalArgumentException if {@code indicator} is {@code null}
     * @since 0.22.3
     */
    public static <T> Indicator<T> requireIndicator(Indicator<T> indicator, String argumentName) {
        if (indicator == null) {
            throw new IllegalArgumentException(argumentName + " must not be null");
        }
        return indicator;
    }

    /**
     * Returns whether the provided number is null or NaN.
     * <p>
     * This guards both logical-NaN and raw {@link Double#NaN} delegate cases.
     *
     * @param value numeric value to validate
     * @return {@code true} when invalid, otherwise {@code false}
     * @since 0.22.3
     */
    public static boolean isInvalid(Num value) {
        return value == null || value.isNaN() || Double.isNaN(value.doubleValue());
    }

    private static BarSeries requireSeries(Indicator<?> indicator, String argumentName) {
        return Objects.requireNonNull(indicator.getBarSeries(), argumentName + " must reference a bar series");
    }

    private static void ensureSameSeries(BarSeries expectedSeries, Indicator<?> indicator, String argumentName) {
        Indicator<?> validatedIndicator = Objects.requireNonNull(indicator, argumentName + " must not be null");
        BarSeries actualSeries = requireSeries(validatedIndicator, argumentName);
        if (actualSeries != expectedSeries) {
            throw new IllegalArgumentException("Indicators must share the same bar series instance");
        }
    }
}

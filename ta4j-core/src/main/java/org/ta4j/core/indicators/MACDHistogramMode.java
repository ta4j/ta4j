/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import java.util.Objects;

import org.ta4j.core.num.Num;

/**
 * Histogram polarity modes for MACD-style indicators.
 *
 * @since 0.22.3
 */
public enum MACDHistogramMode {

    /** Histogram is computed as {@code macd - signal}. */
    MACD_MINUS_SIGNAL,
    /** Histogram is computed as {@code signal - macd}. */
    SIGNAL_MINUS_MACD;

    /**
     * Computes the histogram value for the configured polarity.
     *
     * @param macdValue   MACD line value
     * @param signalValue signal line value
     * @return histogram value
     * @since 0.22.3
     */
    public Num compute(Num macdValue, Num signalValue) {
        Num validatedMacdValue = Objects.requireNonNull(macdValue, "macdValue must not be null");
        Num validatedSignalValue = Objects.requireNonNull(signalValue, "signalValue must not be null");
        if (this == SIGNAL_MINUS_MACD) {
            return validatedSignalValue.minus(validatedMacdValue);
        }
        return validatedMacdValue.minus(validatedSignalValue);
    }
}

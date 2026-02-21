/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.num.Num;

/**
 * Histogram polarity modes for MACD-style indicators.
 *
 * @since 0.22.2
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
     * @since 0.22.2
     */
    public Num compute(Num macdValue, Num signalValue) {
        if (this == SIGNAL_MINUS_MACD) {
            return signalValue.minus(macdValue);
        }
        return macdValue.minus(signalValue);
    }
}

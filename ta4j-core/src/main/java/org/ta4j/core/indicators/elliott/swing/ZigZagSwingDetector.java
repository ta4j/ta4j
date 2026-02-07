/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.swing;

import java.util.List;
import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottSwingIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.zigzag.ZigZagStateIndicator;
import org.ta4j.core.num.Num;

/**
 * Swing detector backed by the ZigZag state indicator.
 *
 * <p>
 * Use this detector when you want adaptive pivot confirmation based on a
 * reversal threshold (fixed or indicator-driven). It is a good choice for
 * volatile markets where fixed window fractals are too rigid.
 *
 * @since 0.22.2
 */
public final class ZigZagSwingDetector implements SwingDetector {

    private final Indicator<Num> reversalIndicator;

    /**
     * Creates a detector with a custom reversal indicator.
     *
     * @param reversalIndicator reversal threshold indicator
     * @since 0.22.2
     */
    public ZigZagSwingDetector(final Indicator<Num> reversalIndicator) {
        this.reversalIndicator = Objects.requireNonNull(reversalIndicator, "reversalIndicator");
    }

    /**
     * Creates a detector using ATR with the given period.
     *
     * @param series    source series
     * @param atrPeriod ATR lookback period
     * @return ZigZag swing detector
     * @since 0.22.2
     */
    public static ZigZagSwingDetector atrBased(final BarSeries series, final int atrPeriod) {
        Objects.requireNonNull(series, "series");
        return new ZigZagSwingDetector(new ATRIndicator(series, atrPeriod));
    }

    @Override
    public SwingDetectorResult detect(final BarSeries series, final int index, final ElliottDegree degree) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(degree, "degree");
        if (reversalIndicator.getBarSeries() != series) {
            throw new IllegalArgumentException("reversalIndicator must share the same BarSeries instance");
        }
        if (series.isEmpty()) {
            return new SwingDetectorResult(List.of(), List.of());
        }
        final int clampedIndex = Math.max(series.getBeginIndex(), Math.min(index, series.getEndIndex()));
        final Indicator<Num> price = new ClosePriceIndicator(series);
        final ZigZagStateIndicator state = new ZigZagStateIndicator(price, reversalIndicator);
        final ElliottSwingIndicator indicator = ElliottSwingIndicator.zigZag(state, price, degree);
        return SwingDetectorResult.fromSwings(indicator.getValue(clampedIndex));
    }

    /**
     * @return reversal indicator used by this detector
     * @since 0.22.2
     */
    public Indicator<Num> getReversalIndicator() {
        return reversalIndicator;
    }
}

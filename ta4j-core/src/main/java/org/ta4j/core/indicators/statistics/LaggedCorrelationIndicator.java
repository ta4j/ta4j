/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Rolling lag-aware cross-correlation indicator.
 *
 * <p>
 * The indicator applies Pearson-style correlation to aligned samples where the
 * first indicator is shifted by {@code lag} bars against the second indicator.
 * Positive lag means the first indicator leads the second, so samples compare
 * {@code first[t - lag]} with {@code second[t]}. Negative lag means the first
 * indicator trails the second. Calculations never read beyond the current
 * index; negative lags move the second series' latest sample back far enough to
 * keep both sides historical. Lags with an absolute value greater than or equal
 * to {@code barCount} are valid; they compare non-overlapping windows and
 * require more historical data before the indicator becomes stable.
 * </p>
 *
 * @since 0.22.7
 */
public class LaggedCorrelationIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> first;
    private final Indicator<Num> second;
    private final int barCount;
    private final int lag;

    /**
     * Constructor.
     *
     * @param first    first numeric indicator
     * @param second   second numeric indicator
     * @param barCount rolling window length, must be at least 2
     * @param lag      bars by which the first indicator leads ({@code > 0}) or
     *                 trails ({@code < 0}) the second indicator
     * @throws IllegalArgumentException if {@code barCount < 2}, the absolute lag is
     *                                  too large to index safely, or indicators use
     *                                  different series
     * @throws NullPointerException     if an indicator is null
     * @since 0.22.7
     */
    public LaggedCorrelationIndicator(Indicator<Num> first, Indicator<Num> second, int barCount, int lag) {
        super(first);
        IndicatorUtils.requireSameSeries(first, second);
        this.first = first;
        this.second = second;
        this.barCount = CorrelationWindowSupport.validateBarCount(barCount);
        this.lag = CorrelationWindowSupport.validateLag(lag, this.barCount);
    }

    @Override
    protected Num calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN.NaN;
        }
        double[][] window = CorrelationWindowSupport.laggedWindow(first, second, index, barCount, lag);
        if (window == null) {
            return NaN.NaN;
        }
        return CorrelationWindowSupport.pearson(getBarSeries().numFactory(), window[0], window[1]);
    }

    @Override
    public int getCountOfUnstableBars() {
        return CorrelationWindowSupport.laggedUnstableBars(barCount, lag, first, second);
    }
}

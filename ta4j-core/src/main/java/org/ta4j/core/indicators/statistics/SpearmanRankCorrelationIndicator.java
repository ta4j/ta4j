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
 * Rolling Spearman rank correlation indicator.
 *
 * <p>
 * Spearman correlation measures monotonic association by replacing the raw
 * values in each rolling window with their ranks and then applying Pearson
 * correlation to those ranks. Equal values receive their average rank, making
 * tied samples deterministic and symmetric.
 * </p>
 *
 * @since 0.22.7
 */
public class SpearmanRankCorrelationIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> first;
    private final Indicator<Num> second;
    private final int barCount;

    /**
     * Constructor.
     *
     * @param first    first numeric indicator
     * @param second   second numeric indicator
     * @param barCount rolling window length, must be at least 2
     * @throws IllegalArgumentException if {@code barCount < 2} or indicators use
     *                                  different series
     * @throws NullPointerException     if an indicator is null
     * @since 0.22.7
     */
    public SpearmanRankCorrelationIndicator(Indicator<Num> first, Indicator<Num> second, int barCount) {
        super(first);
        IndicatorUtils.requireSameSeries(first, second);
        this.first = first;
        this.second = second;
        this.barCount = CorrelationWindowSupport.validateBarCount(barCount);
    }

    @Override
    protected Num calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN.NaN;
        }
        double[][] window = CorrelationWindowSupport.pairedWindow(first, second, index, barCount);
        if (window == null) {
            return NaN.NaN;
        }
        double[] firstRanks = CorrelationWindowSupport.averageRanks(window[0]);
        double[] secondRanks = CorrelationWindowSupport.averageRanks(window[1]);
        return CorrelationWindowSupport.pearson(getBarSeries().numFactory(), firstRanks, secondRanks);
    }

    @Override
    public int getCountOfUnstableBars() {
        return CorrelationWindowSupport.unstableBars(barCount, first, second);
    }
}

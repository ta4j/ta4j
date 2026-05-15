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
 * Rolling Kendall tau-b correlation indicator.
 *
 * <p>
 * Kendall tau-b evaluates ordinal association by comparing every pair of
 * observations in the rolling window. Concordant pairs increase the score,
 * discordant pairs decrease it, and ties on one side are included in the
 * denominator correction. Ties on both sides do not add association evidence.
 * </p>
 *
 * @since 0.22.7
 */
public class KendallTauIndicator extends CachedIndicator<Num> {

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
    public KendallTauIndicator(Indicator<Num> first, Indicator<Num> second, int barCount) {
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

        int concordant = 0;
        int discordant = 0;
        int firstTies = 0;
        int secondTies = 0;
        for (int i = 0; i < barCount - 1; i++) {
            for (int j = i + 1; j < barCount; j++) {
                int firstComparison = Double.compare(window[0][i], window[0][j]);
                int secondComparison = Double.compare(window[1][i], window[1][j]);
                if (firstComparison == 0 && secondComparison == 0) {
                    continue;
                }
                if (firstComparison == 0) {
                    firstTies++;
                } else if (secondComparison == 0) {
                    secondTies++;
                } else if (firstComparison == secondComparison) {
                    concordant++;
                } else {
                    discordant++;
                }
            }
        }

        double numerator = (double) concordant - (double) discordant;
        double firstDenominator = (double) concordant + (double) discordant + (double) firstTies;
        double secondDenominator = (double) concordant + (double) discordant + (double) secondTies;
        double denominator = Math.sqrt(firstDenominator * secondDenominator);
        if (denominator <= 0.0 || Double.isNaN(denominator) || Double.isInfinite(denominator)) {
            return NaN.NaN;
        }
        return getBarSeries().numFactory().numOf(numerator / denominator);
    }

    @Override
    public int getCountOfUnstableBars() {
        return CorrelationWindowSupport.unstableBars(barCount, first, second);
    }
}

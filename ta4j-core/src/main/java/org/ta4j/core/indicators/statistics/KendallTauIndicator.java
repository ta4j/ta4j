/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

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
        CorrelationWindowSupport.NumericWindow window = CorrelationWindowSupport.pairedWindow(first, second, index,
                barCount);
        if (window == null) {
            return NaN.NaN;
        }

        long concordant = 0L;
        long discordant = 0L;
        long firstTies = 0L;
        long secondTies = 0L;
        for (int i = 0; i < window.sampleCount() - 1; i++) {
            for (int j = i + 1; j < window.sampleCount(); j++) {
                int firstComparison = window.firstValues()[i].compareTo(window.firstValues()[j]);
                int secondComparison = window.secondValues()[i].compareTo(window.secondValues()[j]);
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

        NumFactory numFactory = getBarSeries().numFactory();
        Num numerator = numFactory.numOf(concordant).minus(numFactory.numOf(discordant));
        Num firstDenominator = numFactory.numOf(concordant)
                .plus(numFactory.numOf(discordant))
                .plus(numFactory.numOf(firstTies));
        Num secondDenominator = numFactory.numOf(concordant)
                .plus(numFactory.numOf(discordant))
                .plus(numFactory.numOf(secondTies));
        Num denominatorSquared = firstDenominator.multipliedBy(secondDenominator);
        if (!CorrelationWindowSupport.isFinite(denominatorSquared) || !denominatorSquared.isPositive()) {
            return NaN.NaN;
        }
        Num denominator = denominatorSquared.sqrt();
        if (!CorrelationWindowSupport.isFinite(denominator) || denominator.isZero()) {
            return NaN.NaN;
        }
        Num result = numerator.dividedBy(denominator);
        return CorrelationWindowSupport.isFinite(result) ? result : NaN.NaN;
    }

    @Override
    public int getCountOfUnstableBars() {
        return CorrelationWindowSupport.unstableBars(barCount, first, second);
    }
}

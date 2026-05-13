/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import java.util.Objects;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Variance indicator.
 *
 * <p>
 * The default constructor computes <b>sample variance</b> (divisor
 * {@code n - 1}) for rolling windows. Use {@link #ofPopulation(Indicator, int)}
 * (or the {@link SampleType} constructor) when population variance is required.
 * </p>
 */
public class VarianceIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final int barCount;
    private final SampleType sampleType;

    /**
     * Constructor using {@link SampleType#SAMPLE}.
     *
     * @param indicator the indicator
     * @param barCount  the time frame
     * @since 0.22.4
     */
    public VarianceIndicator(Indicator<Num> indicator, int barCount) {
        this(indicator, barCount, SampleType.SAMPLE);
    }

    /**
     * Constructor.
     *
     * @param indicator  the indicator
     * @param barCount   the time frame
     * @param sampleType sample/population variance selection
     * @since 0.22.4
     */
    public VarianceIndicator(Indicator<Num> indicator, int barCount, SampleType sampleType) {
        super(indicator);
        this.indicator = Objects.requireNonNull(indicator, "indicator must not be null");
        this.barCount = Math.max(barCount, 1);
        this.sampleType = Objects.requireNonNull(sampleType, "sampleType must not be null");
    }

    /**
     * Creates an indicator using sample variance ({@code n - 1} divisor).
     *
     * @param indicator the indicator
     * @param barCount  the time frame
     * @return a sample-variance indicator
     * @since 0.22.4
     */
    public static VarianceIndicator ofSample(Indicator<Num> indicator, int barCount) {
        return new VarianceIndicator(indicator, barCount, SampleType.SAMPLE);
    }

    /**
     * Creates an indicator using population variance ({@code n} divisor).
     *
     * @param indicator the indicator
     * @param barCount  the time frame
     * @return a population-variance indicator
     * @since 0.22.4
     */
    public static VarianceIndicator ofPopulation(Indicator<Num> indicator, int barCount) {
        return new VarianceIndicator(indicator, barCount, SampleType.POPULATION);
    }

    @Override
    protected Num calculate(int index) {
        final int startIndex = Math.max(Math.max(0, getBarSeries().getBeginIndex()), index - barCount + 1);
        final int numberOfObservations = index - startIndex + 1;
        final NumFactory numFactory = getBarSeries().numFactory();
        Num variance = numFactory.zero();
        Num average = averageValue(startIndex, index);
        for (int i = startIndex; i <= index; i++) {
            Num pow = indicator.getValue(i).minus(average).pow(2);
            variance = variance.plus(pow);
        }
        final int divisor = sampleType.isSample() ? numberOfObservations - 1 : numberOfObservations;
        if (divisor <= 0) {
            return numFactory.zero();
        }
        return variance.dividedBy(numFactory.numOf(divisor));
    }

    private Num averageValue(int startIndex, int endIndex) {
        Num sum = getBarSeries().numFactory().zero();
        for (int i = startIndex; i <= endIndex; i++) {
            sum = sum.plus(indicator.getValue(i));
        }
        return sum.dividedBy(getBarSeries().numFactory().numOf(endIndex - startIndex + 1));
    }

    @Override
    public int getCountOfUnstableBars() {
        return indicator.getCountOfUnstableBars() + barCount - 1;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount + " sampleType: " + sampleType;
    }
}

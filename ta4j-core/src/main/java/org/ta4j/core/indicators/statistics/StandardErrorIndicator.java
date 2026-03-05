/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import java.util.Objects;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Standard error indicator.
 */
public class StandardErrorIndicator extends CachedIndicator<Num> {

    private final int barCount;
    private final StandardDeviationIndicator sdev;

    /**
     * Constructor using {@link SampleType#POPULATION} for backward compatibility.
     *
     * @param indicator the indicator
     * @param barCount  the time frame
     */
    public StandardErrorIndicator(Indicator<Num> indicator, int barCount) {
        this(indicator, barCount, SampleType.POPULATION);
    }

    /**
     * Constructor.
     *
     * @param indicator  the indicator
     * @param barCount   the time frame
     * @param sampleType sample/population variance selection
     * @since 0.22.4
     */
    public StandardErrorIndicator(Indicator<Num> indicator, int barCount, SampleType sampleType) {
        super(indicator);
        this.barCount = Math.max(barCount, 1);
        this.sdev = Objects.requireNonNull(sampleType, "sampleType must not be null").isSample()
                ? StandardDeviationIndicator.ofSample(indicator, this.barCount)
                : StandardDeviationIndicator.ofPopulation(indicator, this.barCount);
    }

    /**
     * Creates an indicator using sample standard deviation.
     *
     * @param indicator the indicator
     * @param barCount  the time frame
     * @return a sample-standard-error indicator
     * @since 0.22.4
     */
    public static StandardErrorIndicator ofSample(Indicator<Num> indicator, int barCount) {
        return new StandardErrorIndicator(indicator, barCount, SampleType.SAMPLE);
    }

    /**
     * Creates an indicator using population standard deviation.
     *
     * @param indicator the indicator
     * @param barCount  the time frame
     * @return a population-standard-error indicator
     * @since 0.22.4
     */
    public static StandardErrorIndicator ofPopulation(Indicator<Num> indicator, int barCount) {
        return new StandardErrorIndicator(indicator, barCount, SampleType.POPULATION);
    }

    @Override
    protected Num calculate(int index) {
        final int startIndex = Math.max(0, index - this.barCount + 1);
        final int numberOfObservations = index - startIndex + 1;
        return sdev.getValue(index).dividedBy(getBarSeries().numFactory().numOf(numberOfObservations).sqrt());
    }

    @Override
    public int getCountOfUnstableBars() {
        return sdev.getCountOfUnstableBars();
    }
}

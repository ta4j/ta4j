/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import java.util.Objects;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.num.Num;

/**
 * Sigma-Indicator (also called, "z-score" or "standard score").
 *
 * @see <a href=
 *      "http://www.statisticshowto.com/probability-and-statistics/z-score/">Z-score</a>
 */
public class SigmaIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> ref;
    private final int barCount;

    private final SMAIndicator mean;
    private final StandardDeviationIndicator sd;

    /**
     * Constructor using {@link SampleType#POPULATION} for backward compatibility.
     *
     * @param ref      the indicator
     * @param barCount the time frame
     */
    public SigmaIndicator(Indicator<Num> ref, int barCount) {
        this(ref, barCount, SampleType.POPULATION);
    }

    /**
     * Constructor.
     *
     * @param ref        the indicator
     * @param barCount   the time frame
     * @param sampleType sample/population variance selection
     * @since 0.22.4
     */
    public SigmaIndicator(Indicator<Num> ref, int barCount, SampleType sampleType) {
        super(ref);
        this.ref = ref;
        this.barCount = barCount;
        this.mean = new SMAIndicator(ref, barCount);
        this.sd = Objects.requireNonNull(sampleType, "sampleType must not be null").isSample()
                ? StandardDeviationIndicator.ofSample(ref, barCount)
                : StandardDeviationIndicator.ofPopulation(ref, barCount);
    }

    /**
     * Creates a sigma indicator using sample standard deviation.
     *
     * @param ref      the indicator
     * @param barCount the time frame
     * @return a sample sigma indicator
     * @since 0.22.4
     */
    public static SigmaIndicator ofSample(Indicator<Num> ref, int barCount) {
        return new SigmaIndicator(ref, barCount, SampleType.SAMPLE);
    }

    /**
     * Creates a sigma indicator using population standard deviation.
     *
     * @param ref      the indicator
     * @param barCount the time frame
     * @return a population sigma indicator
     * @since 0.22.4
     */
    public static SigmaIndicator ofPopulation(Indicator<Num> ref, int barCount) {
        return new SigmaIndicator(ref, barCount, SampleType.POPULATION);
    }

    @Override
    protected Num calculate(int index) {
        // z-score = (ref - mean) / sd
        return (ref.getValue(index).minus(mean.getValue(index))).dividedBy(sd.getValue(index));
    }

    @Override
    public int getCountOfUnstableBars() {
        return Math.max(mean.getCountOfUnstableBars(), sd.getCountOfUnstableBars());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}

/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import java.util.Objects;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Standard deviation indicator.
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:standard_deviation_volatility">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:standard_deviation_volatility</a>
 */
public class StandardDeviationIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final int barCount;
    private final SampleType sampleType;
    private final transient VarianceIndicator variance;

    /**
     * Constructor using {@link SampleType#POPULATION} for backward compatibility.
     *
     * @param indicator the indicator
     * @param barCount  the time frame
     */
    public StandardDeviationIndicator(Indicator<Num> indicator, int barCount) {
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
    public StandardDeviationIndicator(Indicator<Num> indicator, int barCount, SampleType sampleType) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = barCount;
        this.sampleType = Objects.requireNonNull(sampleType, "sampleType must not be null");
        this.variance = this.sampleType.isSample() ? VarianceIndicator.ofSample(indicator, barCount)
                : VarianceIndicator.ofPopulation(indicator, barCount);
    }

    /**
     * Creates an indicator using sample variance ({@code n - 1} divisor).
     *
     * @param indicator the indicator
     * @param barCount  the time frame
     * @return a sample-standard-deviation indicator
     * @since 0.22.4
     */
    public static StandardDeviationIndicator ofSample(Indicator<Num> indicator, int barCount) {
        return new StandardDeviationIndicator(indicator, barCount, SampleType.SAMPLE);
    }

    /**
     * Creates an indicator using population variance ({@code n} divisor).
     *
     * @param indicator the indicator
     * @param barCount  the time frame
     * @return a population-standard-deviation indicator
     * @since 0.22.4
     */
    public static StandardDeviationIndicator ofPopulation(Indicator<Num> indicator, int barCount) {
        return new StandardDeviationIndicator(indicator, barCount, SampleType.POPULATION);
    }

    @Override
    protected Num calculate(int index) {
        return variance.getValue(index).sqrt();
    }

    @Override
    public int getCountOfUnstableBars() {
        return variance.getCountOfUnstableBars();
    }
}

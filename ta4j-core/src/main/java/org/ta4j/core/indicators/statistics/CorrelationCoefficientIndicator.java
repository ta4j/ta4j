/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import java.util.Objects;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Correlation coefficient indicator.
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:correlation_coeffici">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:correlation_coeffici</a>
 */
public class CorrelationCoefficientIndicator extends CachedIndicator<Num> {

    private final int barCount;
    private final SampleType sampleType;
    private final VarianceIndicator variance1;
    private final VarianceIndicator variance2;
    private final CovarianceIndicator covariance;

    /**
     * Constructor using {@link SampleType#POPULATION} for backward compatibility.
     *
     * @param indicator1 the first indicator
     * @param indicator2 the second indicator
     * @param barCount   the time frame
     */
    public CorrelationCoefficientIndicator(Indicator<Num> indicator1, Indicator<Num> indicator2, int barCount) {
        this(indicator1, indicator2, barCount, SampleType.POPULATION);
    }

    /**
     * Constructor.
     *
     * @param indicator1 the first indicator
     * @param indicator2 the second indicator
     * @param barCount   the time frame
     * @param sampleType sample/population variance selection
     * @since 0.22.4
     */
    public CorrelationCoefficientIndicator(Indicator<Num> indicator1, Indicator<Num> indicator2, int barCount,
            SampleType sampleType) {
        super(indicator1);
        this.barCount = Math.max(barCount, 1);
        this.sampleType = Objects.requireNonNull(sampleType, "sampleType must not be null");
        this.variance1 = this.sampleType.isSample() ? VarianceIndicator.ofSample(indicator1, this.barCount)
                : VarianceIndicator.ofPopulation(indicator1, this.barCount);
        this.variance2 = this.sampleType.isSample() ? VarianceIndicator.ofSample(indicator2, this.barCount)
                : VarianceIndicator.ofPopulation(indicator2, this.barCount);
        this.covariance = new CovarianceIndicator(indicator1, indicator2, this.barCount);
    }

    /**
     * Creates an indicator using sample variance/covariance normalization.
     *
     * @param indicator1 the first indicator
     * @param indicator2 the second indicator
     * @param barCount   the time frame
     * @return a sample correlation coefficient indicator
     * @since 0.22.4
     */
    public static CorrelationCoefficientIndicator ofSample(Indicator<Num> indicator1, Indicator<Num> indicator2,
            int barCount) {
        return new CorrelationCoefficientIndicator(indicator1, indicator2, barCount, SampleType.SAMPLE);
    }

    /**
     * Creates an indicator using population variance/covariance normalization.
     *
     * @param indicator1 the first indicator
     * @param indicator2 the second indicator
     * @param barCount   the time frame
     * @return a population correlation coefficient indicator
     * @since 0.22.4
     */
    public static CorrelationCoefficientIndicator ofPopulation(Indicator<Num> indicator1, Indicator<Num> indicator2,
            int barCount) {
        return new CorrelationCoefficientIndicator(indicator1, indicator2, barCount, SampleType.POPULATION);
    }

    @Override
    protected Num calculate(int index) {
        Num cov = covariance.getValue(index);
        if (sampleType.isSample()) {
            final int startIndex = Math.max(0, index - barCount + 1);
            final int numberOfObservations = index - startIndex + 1;
            if (numberOfObservations > 1) {
                final var numFactory = getBarSeries().numFactory();
                cov = cov.multipliedBy(numFactory.numOf(numberOfObservations))
                        .dividedBy(numFactory.numOf(numberOfObservations - 1));
            }
        }
        Num var1 = variance1.getValue(index);
        Num var2 = variance2.getValue(index);
        Num multipliedSqrt = var1.multipliedBy(var2).sqrt();
        return cov.dividedBy(multipliedSqrt);
    }

    @Override
    public int getCountOfUnstableBars() {
        int unstableBars = Math.max(variance1.getCountOfUnstableBars(), variance2.getCountOfUnstableBars());
        return Math.max(unstableBars, covariance.getCountOfUnstableBars());
    }
}

/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators.statistics;

import java.util.function.BiFunction;

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

    private final VarianceIndicator variance1;
    private final VarianceIndicator variance2;
    private final CovarianceIndicator covariance;

    /**
     * Constructor.
     *
     * @param indicator1 the first indicator
     * @param indicator2 the second indicator
     * @param barCount   the time frame
     * @param type       sample/population
     */
    public CorrelationCoefficientIndicator(final Indicator<Num> indicator1, final Indicator<Num> indicator2,
            final int barCount, final Type type) {
        super(indicator1);
        final BiFunction<Indicator<Num>, Integer, VarianceIndicator> varianceProvider = type.isSample()
                ? VarianceIndicator::ofSample
                : VarianceIndicator::ofPopulation;
        this.variance1 = varianceProvider.apply(indicator1, barCount);
        this.variance2 = varianceProvider.apply(indicator2, barCount);
        this.covariance = new CovarianceIndicator(indicator1, indicator2, barCount);
    }

    public static CorrelationCoefficientIndicator ofSample(final Indicator<Num> indicator1,
            final Indicator<Num> indicator2, final int barCount) {
        return new CorrelationCoefficientIndicator(indicator1, indicator2, barCount, Type.SAMPLE);
    }

    public static CorrelationCoefficientIndicator ofPopulation(final Indicator<Num> indicator1,
            final Indicator<Num> indicator2, final int barCount) {
        return new CorrelationCoefficientIndicator(indicator1, indicator2, barCount, Type.POPULATION);
    }

    @Override
    protected Num calculate(final int index) {
        final Num cov = this.covariance.getValue(index);
        final Num var1 = this.variance1.getValue(index);
        final Num var2 = this.variance2.getValue(index);
        final Num multipliedSqrt = var1.multipliedBy(var2).sqrt();
        return cov.dividedBy(multipliedSqrt);
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}

/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)
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
package eu.verdelhan.ta4j.indicators.statistics;

import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.indicators.CachedIndicator;

/**
 * Correlation coefficient indicator.
 * <p>
 * See also: http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:correlation_coeffici
 */
public class CorrelationCoefficientIndicator extends CachedIndicator<Decimal> {

    private VarianceIndicator variance1;
    
    private VarianceIndicator variance2;
    
    private CovarianceIndicator covariance;
    
    /**
     * Constructor.
     * @param indicator1 the first indicator
     * @param indicator2 the second indicator
     * @param timeFrame the time frame
     */
    public CorrelationCoefficientIndicator(Indicator<Decimal> indicator1, Indicator<Decimal> indicator2, int timeFrame) {
        super(indicator1);
        variance1 = new VarianceIndicator(indicator1, timeFrame);
        variance2 = new VarianceIndicator(indicator2, timeFrame);
        covariance = new CovarianceIndicator(indicator1, indicator2, timeFrame);
    }

    @Override
    protected Decimal calculate(int index) {
        Decimal cov = covariance.getValue(index);
        Decimal var1 = variance1.getValue(index);
        Decimal var2 = variance2.getValue(index);
        
        return cov.dividedBy(var1.multipliedBy(var2).sqrt());
    }
}

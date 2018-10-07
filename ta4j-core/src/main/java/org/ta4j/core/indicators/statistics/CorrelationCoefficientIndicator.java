package org.ta4j.core.indicators.statistics;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Correlation coefficient indicator.
 * <p/>
 * @see <a href="http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:correlation_coeffici">
 * http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:correlation_coeffici</a>
 */
public class CorrelationCoefficientIndicator extends CachedIndicator<Num> {

    private VarianceIndicator variance1;
    
    private VarianceIndicator variance2;
    
    private CovarianceIndicator covariance;
    
    /**
     * Constructor.
     * @param indicator1 the first indicator
     * @param indicator2 the second indicator
     * @param barCount the time frame
     */
    public CorrelationCoefficientIndicator(Indicator<Num> indicator1, Indicator<Num> indicator2, int barCount) {
        super(indicator1);
        variance1 = new VarianceIndicator(indicator1, barCount);
        variance2 = new VarianceIndicator(indicator2, barCount);
        covariance = new CovarianceIndicator(indicator1, indicator2, barCount);
    }

    @Override
    protected Num calculate(int index) {
        Num cov = covariance.getValue(index);
        Num var1 = variance1.getValue(index);
        Num var2 = variance2.getValue(index);
        Num var1_2_sqrt = var1.multipliedBy(var2).sqrt();
        return cov.dividedBy(var1_2_sqrt);



    }
}

package org.ta4j.core.indicators.statistics;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;
/**
 * Standard deviation indicator.
 * <p/>
 * @see <a href="http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:standard_deviation_volatility">
 *     http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:standard_deviation_volatility</a>
 */
public class StandardDeviationIndicator extends CachedIndicator<Num> {

    private VarianceIndicator variance;

    /**
     * Constructor.
     * @param indicator the indicator
     * @param barCount the time frame
     */
    public StandardDeviationIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        variance = new VarianceIndicator(indicator, barCount);
    }

    @Override
    protected Num calculate(int index) {
        return variance.getValue(index).sqrt();
    }
}

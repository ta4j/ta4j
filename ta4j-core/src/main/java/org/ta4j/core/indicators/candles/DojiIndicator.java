package org.ta4j.core.indicators.candles;

import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.AbsoluteIndicator;
import org.ta4j.core.num.Num;

/**
 * Doji indicator.
 * </p>
 * A candle/bar is considered Doji if its body height is lower than the average multiplied by a factor.
 * @see <a href="http://stockcharts.com/school/doku.php?id=chart_school:chart_analysis:introduction_to_candlesticks#doji">
 *     http://stockcharts.com/school/doku.php?id=chart_school:chart_analysis:introduction_to_candlesticks#doji</a>
 */
public class DojiIndicator extends CachedIndicator<Boolean> {

    /** Body height */
    private final Indicator<Num> bodyHeightInd;
    /** Average body height */
    private final SMAIndicator averageBodyHeightInd;

    private final Num factor;

    /**
     * Constructor.
     * @param series a time series
     * @param barCount the number of bars used to calculate the average body height
     * @param bodyFactor the factor used when checking if a candle is Doji
     */
    public DojiIndicator(TimeSeries series, int barCount, double bodyFactor) {
        super(series);
        bodyHeightInd = new AbsoluteIndicator(new RealBodyIndicator(series));
        averageBodyHeightInd = new SMAIndicator(bodyHeightInd, barCount);
        factor = numOf(bodyFactor);
    }

    @Override
    protected Boolean calculate(int index) {
        if (index < 1) {
            return bodyHeightInd.getValue(index).isZero();
        }

        Num averageBodyHeight = averageBodyHeightInd.getValue(index-1);
        Num currentBodyHeight = bodyHeightInd.getValue(index);

        return currentBodyHeight.isLessThan(averageBodyHeight.multipliedBy(factor));
    }
}

package org.ta4j.core.indicators;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.helpers.TypicalPriceIndicator;
import org.ta4j.core.indicators.statistics.MeanDeviationIndicator;
import org.ta4j.core.num.Num;

/**
 * Commodity Channel Index (CCI) indicator.
 * <p/>
 *
 * @see <a href="http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:commodity_channel_in">
 * http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:commodity_channel_in</a>
 */
public class CCIIndicator extends CachedIndicator<Num> {

    private final Num FACTOR;
    private final TypicalPriceIndicator typicalPriceInd;
    private final SMAIndicator smaInd;
    private final MeanDeviationIndicator meanDeviationInd;
    private final int barCount;

    /**
     * Constructor.
     *
     * @param series   the time series
     * @param barCount the time frame (normally 20)
     */
    public CCIIndicator(TimeSeries series, int barCount) {
        super(series);
        FACTOR = numOf(0.015);
        typicalPriceInd = new TypicalPriceIndicator(series);
        smaInd = new SMAIndicator(typicalPriceInd, barCount);
        meanDeviationInd = new MeanDeviationIndicator(typicalPriceInd, barCount);
        this.barCount = barCount;
    }

    @Override
    protected Num calculate(int index) {
        final Num typicalPrice = typicalPriceInd.getValue(index);
        final Num typicalPriceAvg = smaInd.getValue(index);
        final Num meanDeviation = meanDeviationInd.getValue(index);
        if (meanDeviation.isZero()) {
            return numOf(0);
        }
        return (typicalPrice.minus(typicalPriceAvg)).dividedBy(meanDeviation.multipliedBy(FACTOR));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}

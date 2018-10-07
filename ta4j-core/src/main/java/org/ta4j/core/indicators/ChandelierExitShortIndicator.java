package org.ta4j.core.indicators;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.num.Num;

/**
 * The Chandelier Exit (short) Indicator.
 * @see <a href="http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:chandelier_exit">
 *     http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:chandelier_exit</a>
 */
public class ChandelierExitShortIndicator extends CachedIndicator<Num> {

    private final LowestValueIndicator low;
    private final ATRIndicator atr;
    private final Num k;

    /**
     * Constructor.
     * @param series the time series
     */
    public ChandelierExitShortIndicator(TimeSeries series) {
        this(series, 22, 3d);
    }
    
    /**
     * Constructor.
     * @param series the time series
     * @param barCount the time frame (usually 22)
     * @param k the K multiplier for ATR (usually 3.0)
     */
    public ChandelierExitShortIndicator(TimeSeries series, int barCount, double k) {
        super(series);
        low = new LowestValueIndicator(new LowPriceIndicator(series), barCount);
        atr = new ATRIndicator(series, barCount);
        this.k = numOf(k);
    }

    @Override
    protected Num calculate(int index) {
        return low.getValue(index).plus(atr.getValue(index).multipliedBy(k));
    }
}

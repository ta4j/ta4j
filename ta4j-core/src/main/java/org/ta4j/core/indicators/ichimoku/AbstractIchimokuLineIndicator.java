package org.ta4j.core.indicators.ichimoku;

import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.num.Num;

/**
 * An abstract class for Ichimoku clouds indicators.
 * </p>
 * @see <a href="http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ichimoku_cloud">
 *     http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ichimoku_cloud</a>
 */
public abstract class AbstractIchimokuLineIndicator extends CachedIndicator<Num>{

    /** The period high */
    private final Indicator<Num> periodHigh;

    /** The period low */
    private final Indicator<Num> periodLow;

    /**
     * Contructor.
     * @param series the series
     * @param barCount the time frame
     */
    public AbstractIchimokuLineIndicator(TimeSeries series, int barCount) {
        super(series);
        periodHigh = new HighestValueIndicator(new HighPriceIndicator(series), barCount);
        periodLow = new LowestValueIndicator(new LowPriceIndicator(series), barCount);
    }

    @Override
    protected Num calculate(int index) {
        return periodHigh.getValue(index).plus(periodLow.getValue(index)).dividedBy(numOf(2));
    }
}

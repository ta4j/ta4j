package org.ta4j.core.indicators.adx;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.MMAIndicator;
import org.ta4j.core.indicators.helpers.MinusDMIndicator;
import org.ta4j.core.num.Num;

/**
 * -DI indicator.
 * Part of the Directional Movement System
 * <p>
 * </p>
 * @see <a href="http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:average_directional_index_adx">
 * http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:average_directional_index_adx</a>
 */
public class MinusDIIndicator extends CachedIndicator<Num> {

    private final MMAIndicator avgMinusDMIndicator;
    private final ATRIndicator atrIndicator;
    private final int barCount;

    public MinusDIIndicator(TimeSeries series, int barCount) {
        super(series);
        this.barCount = barCount;
        this.avgMinusDMIndicator = new MMAIndicator(new MinusDMIndicator(series), barCount);
        this.atrIndicator = new ATRIndicator(series, barCount);
    }

    @Override
    protected Num calculate(int index) {
        return avgMinusDMIndicator.getValue(index).dividedBy(atrIndicator.getValue(index)).multipliedBy(numOf(100));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}

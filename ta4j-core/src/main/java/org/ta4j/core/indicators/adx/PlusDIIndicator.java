package org.ta4j.core.indicators.adx;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.MMAIndicator;
import org.ta4j.core.indicators.helpers.PlusDMIndicator;
import org.ta4j.core.num.Num;

/**
 * +DI indicator.
 * Part of the Directional Movement System
 * <p>
 * </p>
 * @see <a href="http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:average_directional_index_adx">
 * http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:average_directional_index_adx</a>
 */
public class PlusDIIndicator extends CachedIndicator<Num> {

    private final MMAIndicator avgPlusDMIndicator;
    private final ATRIndicator atrIndicator;
    private final int barCount;

    public PlusDIIndicator(TimeSeries series, int barCount) {
        super(series);
        this.avgPlusDMIndicator = new MMAIndicator(new PlusDMIndicator(series), barCount);
        this.atrIndicator = new ATRIndicator(series, barCount);
        this.barCount = barCount;
    }

    @Override
    protected Num calculate(int index) {
        return avgPlusDMIndicator.getValue(index).dividedBy(atrIndicator.getValue(index)).multipliedBy(numOf(100));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "barCount: " + barCount;
    }
}

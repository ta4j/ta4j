package org.ta4j.core.indicators.keltner;

import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.helpers.TypicalPriceIndicator;
import org.ta4j.core.num.Num;

/**
 * Keltner Channel (middle line) indicator
 * @see <a href="http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:keltner_channels">
 *     http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:keltner_channels</a>
 */
public class KeltnerChannelMiddleIndicator extends CachedIndicator<Num> {

    private final EMAIndicator emaIndicator;

    public KeltnerChannelMiddleIndicator(TimeSeries series, int barCountEMA) {
        this(new TypicalPriceIndicator(series), barCountEMA);
    }

    public KeltnerChannelMiddleIndicator(Indicator<Num> indicator, int barCountEMA) {
        super(indicator);
        emaIndicator = new EMAIndicator(indicator, barCountEMA);
    }

    @Override
    protected Num calculate(int index) {
        return emaIndicator.getValue(index);
    }

}

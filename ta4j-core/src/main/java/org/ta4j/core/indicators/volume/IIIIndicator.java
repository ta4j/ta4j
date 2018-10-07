package org.ta4j.core.indicators.volume;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.num.Num;

/**
 * Intraday Intensity Index
 * see https://www.investopedia.com/terms/i/intradayintensityindex.asp
 *     https://www.investopedia.com/terms/i/intradayintensityindex.asp
 */
public class IIIIndicator extends CachedIndicator<Num> {


    private ClosePriceIndicator closePriceIndicator;

    private HighPriceIndicator highPriceIndicator;

    private LowPriceIndicator lowPriceIndicator;

    private VolumeIndicator volumeIndicator;

    public IIIIndicator(TimeSeries series) {
        super(series);
        closePriceIndicator = new ClosePriceIndicator(series);
        highPriceIndicator = new HighPriceIndicator(series);
        lowPriceIndicator = new LowPriceIndicator(series);
        volumeIndicator = new VolumeIndicator(series);
    }
    @Override
    protected Num calculate(int index) {

        if (index == getTimeSeries().getBeginIndex()) {
            return numOf(0);
        }
        Num closePrice = numOf(2).multipliedBy(closePriceIndicator.getValue(index));
        Num highmlow = highPriceIndicator.getValue(index).minus(lowPriceIndicator.getValue(index));
        Num highplow = highPriceIndicator.getValue(index).plus(lowPriceIndicator.getValue(index));

        return (closePrice.minus(highplow)).dividedBy(highmlow.multipliedBy(volumeIndicator.getValue(index)));

    }
}

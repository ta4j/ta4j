package eu.verdelhan.ta4j.indicators.volume;

import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.CachedIndicator;
import eu.verdelhan.ta4j.indicators.simple.TypicalPriceIndicator;
import eu.verdelhan.ta4j.indicators.simple.VolumeIndicator;

/**
 * The Volume weighted average price (VWAP) Indicator.
 * @see http://www.investopedia.com/articles/trading/11/trading-with-vwap-mvwap.asp
 */
public class VWAPIndicator extends CachedIndicator<Decimal> {

    private final int timeFrame;
    
    private final Indicator<Decimal> typicalPrice;
    
    private final Indicator<Decimal> volume;
    
    /**
     * Constructor.
     *
     * @param series the series
     * @param timeFrame the time frame
     */
    public VWAPIndicator(TimeSeries series, int timeFrame) {
        super(series);
        this.timeFrame = timeFrame;
        typicalPrice = new TypicalPriceIndicator(series);
        volume = new VolumeIndicator(series);
    }

    @Override
    protected Decimal calculate(int index) {
        if (index <= 0)
        {
            return typicalPrice.getValue(index);
        }
        int startIndex = Math.max(0, index - timeFrame + 1);
        Decimal cumulativeTPV = Decimal.ZERO;
        Decimal cumulativeVolume = Decimal.ZERO;
        for (int i = startIndex; i <= index; i++) {
            Decimal currentVolume = volume.getValue(i);
            cumulativeTPV = cumulativeTPV.plus(typicalPrice.getValue(i).multipliedBy(currentVolume));
            cumulativeVolume = cumulativeVolume.plus(currentVolume);
        }
        return cumulativeTPV.dividedBy(cumulativeVolume);
    }

}

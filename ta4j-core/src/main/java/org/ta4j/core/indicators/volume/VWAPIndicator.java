package org.ta4j.core.indicators.volume;

import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.TypicalPriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.num.Num;

/**
 * The volume-weighted average price (VWAP) Indicator.
 * @see <a href="http://www.investopedia.com/articles/trading/11/trading-with-vwap-mvwap.asp">
 *     http://www.investopedia.com/articles/trading/11/trading-with-vwap-mvwap.asp</a>
 * @see <a href="http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:vwap_intraday">
 *     http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:vwap_intraday</a>
 * @see <a href="https://en.wikipedia.org/wiki/Volume-weighted_average_price">
 *     https://en.wikipedia.org/wiki/Volume-weighted_average_price</a>
 */
public class VWAPIndicator extends CachedIndicator<Num> {

    private final int barCount;
    
    private final Indicator<Num> typicalPrice;
    
    private final Indicator<Num> volume;

    private final Num ZERO;
    
    /**
     * Constructor.
     * @param series the series
     * @param barCount the time frame
     */
    public VWAPIndicator(TimeSeries series, int barCount) {
        super(series);
        this.barCount = barCount;
        typicalPrice = new TypicalPriceIndicator(series);
        volume = new VolumeIndicator(series);
        this.ZERO = numOf(0);
    }

    @Override
    protected Num calculate(int index) {
        if (index <= 0) {
            return typicalPrice.getValue(index);
        }
        int startIndex = Math.max(0, index - barCount + 1);
        Num cumulativeTPV = ZERO;
        Num cumulativeVolume = ZERO;
        for (int i = startIndex; i <= index; i++) {
            Num currentVolume = volume.getValue(i);
            cumulativeTPV = cumulativeTPV.plus(typicalPrice.getValue(i).multipliedBy(currentVolume));
            cumulativeVolume = cumulativeVolume.plus(currentVolume);
        }
        return cumulativeTPV.dividedBy(cumulativeVolume);
    }
    
    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}

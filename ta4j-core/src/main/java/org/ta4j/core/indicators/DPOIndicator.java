package org.ta4j.core.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

/**
 * The Detrended Price Oscillator (DPO) indicator.
 * <p>
 * The Detrended Price Oscillator (DPO) is an indicator designed to remove trend
 * from price and make it easier to identify cycles. DPO does not extend to the
 * last date because it is based on a displaced moving average. However,
 * alignment with the most recent is not an issue because DPO is not a momentum
 * oscillator. Instead, DPO is used to identify cycles highs/lows and estimate
 * cycle length.
 *
 * In short, DPO(20) equals price 11 days ago less the 20-day SMA.
 * </p>
 * @see <a href="http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:detrended_price_osci">
 *     http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:detrended_price_osci</a>
 */
public class DPOIndicator extends CachedIndicator<Num> {

    private final int barCount;
    private final int timeShift;
    private final Indicator<Num> price;
    private final SMAIndicator sma;
    
    /**
     * Constructor.
     * @param series the series
     * @param barCount the time frame
     */
    public DPOIndicator(TimeSeries series, int barCount) {
        this(new ClosePriceIndicator(series), barCount);
    }
    
    /**
     * Constructor.
     * @param price the price
     * @param barCount the time frame
     */
    public DPOIndicator(Indicator<Num> price, int barCount) {
        super(price);
        this.barCount = barCount;
        timeShift = barCount / 2 + 1;
        this.price = price;
        sma = new SMAIndicator(price, this.barCount);
    }

    @Override
    protected Num calculate(int index) {
        return price.getValue(index).minus(sma.getValue(index-timeShift));
    }
    
    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}

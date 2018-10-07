package org.ta4j.core.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Chande's Range Action Verification Index (RAVI) indicator.
 * 
 * To preserve trend direction, default calculation does not use absolute value.
 */
public class RAVIIndicator extends CachedIndicator<Num> {

    private final SMAIndicator shortSma;
    private final SMAIndicator longSma;
   
    /**
     * Constructor.
     * @param price the price
     * @param shortSmaBarCount the time frame for the short SMA (usually 7)
     * @param longSmaBarCount the time frame for the long SMA (usually 65)
     */
    public RAVIIndicator(Indicator<Num> price, int shortSmaBarCount, int longSmaBarCount) {
        super(price);
        shortSma = new SMAIndicator(price, shortSmaBarCount);
        longSma = new SMAIndicator(price, longSmaBarCount);
    }

    @Override
    protected Num calculate(int index) {
        Num shortMA = shortSma.getValue(index);
        Num longMA = longSma.getValue(index);
        return shortMA.minus(longMA)
                .dividedBy(longMA)
                .multipliedBy(numOf(100));
    }
}

package org.ta4j.core.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Zero-lag exponential moving average indicator.
 * <p>
 * </p>
 * @see <a href="http://www.fmlabs.com/reference/default.htm?url=ZeroLagExpMA.htm">
 * href="http://www.fmlabs.com/reference/default.htm?url=ZeroLagExpMA.htm</a>
 */
public class ZLEMAIndicator extends RecursiveCachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final int barCount;
    private final Num k;
    private final int lag;

    public ZLEMAIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = barCount;
        k = numOf(2).dividedBy(numOf(barCount + 1));
        lag = (barCount - 1) / 2;
    }

    @Override
    protected Num calculate(int index) {
        if (index + 1 < barCount) {
            // Starting point of the ZLEMA
            return new SMAIndicator(indicator, barCount).getValue(index);
        }
        if (index == 0) {
            // If the barCount is bigger than the indicator's value count
            return indicator.getValue(0);
        }
        Num zlemaPrev = getValue(index - 1);
        return k.multipliedBy(numOf(2).multipliedBy(indicator.getValue(index)).minus(indicator.getValue(index - lag)))
                .plus(numOf(1).minus(k).multipliedBy(zlemaPrev));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}

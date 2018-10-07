package org.ta4j.core.indicators.helpers;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.adx.MinusDIIndicator;
import org.ta4j.core.indicators.adx.PlusDIIndicator;
import org.ta4j.core.num.Num;

/**
 * DX indicator.
 * <p>
 * </p>
 */
public class DXIndicator extends CachedIndicator<Num> {

    private final int barCount;
    private final PlusDIIndicator plusDIIndicator;
    private final MinusDIIndicator minusDIIndicator;

    public DXIndicator(TimeSeries series, int barCount) {
        super(series);
        this.barCount = barCount;
        plusDIIndicator = new PlusDIIndicator(series, barCount);
        minusDIIndicator = new MinusDIIndicator(series, barCount);
    }

    @Override
    protected Num calculate(int index) {
        Num pdiValue = plusDIIndicator.getValue(index);
        Num mdiValue = minusDIIndicator.getValue(index);
        if (pdiValue.plus(mdiValue).equals(numOf(0))) {
            return numOf(0);
        }
        return pdiValue.minus(mdiValue).abs().dividedBy(pdiValue.plus(mdiValue)).multipliedBy(numOf(100));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}

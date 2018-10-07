package org.ta4j.core.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Simple moving average (SMA) indicator.
 * </p>
 */
public class SMAIndicator extends CachedIndicator<Num> {

    private static final long serialVersionUID = 653601631245729997L;
    private final Indicator<Num> indicator;

    private final int barCount;

    public SMAIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = barCount;
    }

    @Override
    protected Num calculate(int index) {
        Num sum = getTimeSeries().numOf(0);
        for (int i = Math.max(0, index - barCount + 1); i <= index; i++) {
            sum = sum.plus(indicator.getValue(i));
        }

        final int realBarCount = Math.min(barCount, index + 1);
        return sum.dividedBy(getTimeSeries().numOf(realBarCount));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }

}

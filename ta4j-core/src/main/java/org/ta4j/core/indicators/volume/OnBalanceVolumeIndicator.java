package org.ta4j.core.indicators.volume;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.num.Num;

/**
 * On-balance volume indicator.
 * </p>
 */
public class OnBalanceVolumeIndicator extends RecursiveCachedIndicator<Num> {

    private static final long serialVersionUID = -5870953997596403170L;

    public OnBalanceVolumeIndicator(TimeSeries series) {
        super(series);
    }

    @Override
    protected Num calculate(int index) {
        if (index == 0) {
            return numOf(0);
        }
        Num yesterdayClose = getTimeSeries().getBar(index - 1).getClosePrice();
        Num todayClose = getTimeSeries().getBar(index).getClosePrice();

        if (yesterdayClose.isGreaterThan(todayClose)) {
            return getValue(index - 1).minus(getTimeSeries().getBar(index).getVolume());
        } else if (yesterdayClose.isLessThan(todayClose)) {
            return getValue(index - 1).plus(getTimeSeries().getBar(index).getVolume());
        }
        return getValue(index - 1);
    }
}

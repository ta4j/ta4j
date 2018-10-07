package org.ta4j.core.indicators;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.helpers.TRIndicator;
import org.ta4j.core.num.Num;

/**
 * Average true range indicator.
 * <p/>
 */
public class ATRIndicator extends CachedIndicator<Num> {

    private final MMAIndicator averageTrueRangeIndicator;

    public ATRIndicator(TimeSeries series, int barCount) {
        super(series);
        this.averageTrueRangeIndicator = new MMAIndicator(new TRIndicator(series), barCount);
    }

    @Override
    protected Num calculate(int index) {
        return averageTrueRangeIndicator.getValue(index);
    }
}

package org.ta4j.core.indicators.volume;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Rate of change of volume (ROCVIndicator) indicator.
 * Aka. Momentum of Volume
 * </p>
 * The ROCVIndicator calculation compares the current volume with the volume "n" periods ago.
 */
public class ROCVIndicator extends CachedIndicator<Num> {

    private static final long serialVersionUID = 6366365574748347534L;

    private final int barCount;

    private final Num HUNDRED;

    /**
     * Constructor.
     *
     * @param series the time series
     * @param barCount the time frame
     */
    public ROCVIndicator(TimeSeries series, int barCount) {
        super(series);
        this.barCount = barCount;
        this.HUNDRED = numOf(100);
    }

    @Override
    protected Num calculate(int index) {
        int nIndex = Math.max(index - barCount, 0);
        Num nPeriodsAgoValue = getTimeSeries().getBar(nIndex).getVolume();
        Num currentValue = getTimeSeries().getBar(index).getVolume();
        return currentValue.minus(nPeriodsAgoValue)
                .dividedBy(nPeriodsAgoValue)
                .multipliedBy(HUNDRED);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}

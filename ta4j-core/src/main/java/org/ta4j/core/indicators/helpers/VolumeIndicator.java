package org.ta4j.core.indicators.helpers;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Volume indicator.
 * </p>
 */
public class VolumeIndicator extends CachedIndicator<Num> {

    private int barCount;

    public VolumeIndicator(TimeSeries series) {
        this(series, 1);
    }

    public VolumeIndicator(TimeSeries series, int barCount) {
        super(series);
        this.barCount = barCount;
    }

    @Override
    protected Num calculate(int index) {
        int startIndex = Math.max(0, index - barCount + 1);
        Num sumOfVolume = numOf(0);
        for (int i = startIndex; i <= index; i++) {
            sumOfVolume = sumOfVolume.plus(getTimeSeries().getBar(i).getVolume());
        }
        return sumOfVolume;
    }
}
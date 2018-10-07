package org.ta4j.core.indicators.adx;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.MMAIndicator;
import org.ta4j.core.indicators.helpers.DXIndicator;
import org.ta4j.core.num.Num;

/**
 * ADX indicator.
 * Part of the Directional Movement System
 * <p>
 * </p>
 */
public class ADXIndicator extends CachedIndicator<Num> {

    private final MMAIndicator averageDXIndicator;
    private final int diBarCount;
    private final int adxBarCount;

    public ADXIndicator(TimeSeries series, int diBarCount, int adxBarCount) {
        super(series);
        this.diBarCount = diBarCount;
        this.adxBarCount = adxBarCount;
        this.averageDXIndicator = new MMAIndicator(new DXIndicator(series, diBarCount), adxBarCount);
    }

    public ADXIndicator(TimeSeries series, int barCount) {
        this(series, barCount, barCount);
    }

    @Override
    protected Num calculate(int index) {
        return averageDXIndicator.getValue(index);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " diBarCount: " + diBarCount + " adxBarCount: " + adxBarCount;
    }
}

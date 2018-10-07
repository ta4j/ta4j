package org.ta4j.core.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.helpers.*;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.num.Num;

/**
 * William's R indicator.
 * </p>
 */
public class WilliamsRIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final int barCount;
    private final HighPriceIndicator highPriceIndicator;
    private final LowPriceIndicator lowPriceIndicator;
    private final Num multiplier;

    public WilliamsRIndicator(TimeSeries timeSeries, int barCount) {
        this(new ClosePriceIndicator(timeSeries), barCount, new HighPriceIndicator(timeSeries), new LowPriceIndicator(
                timeSeries));
    }

    public WilliamsRIndicator(Indicator<Num> indicator, int barCount,
                              HighPriceIndicator highPriceIndicator, LowPriceIndicator lowPriceIndicator) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = barCount;
        this.highPriceIndicator = highPriceIndicator;
        this.lowPriceIndicator = lowPriceIndicator;
        this.multiplier = numOf(-100);
    }

    @Override
    protected Num calculate(int index) {
        HighestValueIndicator highestHigh = new HighestValueIndicator(highPriceIndicator, barCount);
        LowestValueIndicator lowestMin = new LowestValueIndicator(lowPriceIndicator, barCount);

        Num highestHighPrice = highestHigh.getValue(index);
        Num lowestLowPrice = lowestMin.getValue(index);

        return ((highestHighPrice.minus(indicator.getValue(index)))
                .dividedBy(highestHighPrice.minus(lowestLowPrice)))
                .multipliedBy(multiplier);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}

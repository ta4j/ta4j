package org.ta4j.core.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.helpers.*;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.num.Num;


/**
 * Stochastic oscillator K.
 * </p>
 * Receives timeSeries and barCount and calculates the StochasticOscillatorKIndicator
 * over ClosePriceIndicator, or receives an indicator, HighPriceIndicator and
 * LowPriceIndicator and returns StochasticOsiclatorK over this indicator.
 */
public class StochasticOscillatorKIndicator extends CachedIndicator<Num> {
    private final Indicator<Num> indicator;

    private final int barCount;

    private HighPriceIndicator highPriceIndicator;

    private LowPriceIndicator lowPriceIndicator;

    public StochasticOscillatorKIndicator(TimeSeries timeSeries, int barCount) {
        this(new ClosePriceIndicator(timeSeries), barCount, new HighPriceIndicator(timeSeries), new LowPriceIndicator(
                timeSeries));
    }

    public StochasticOscillatorKIndicator(Indicator<Num> indicator, int barCount,
                                          HighPriceIndicator highPriceIndicator, LowPriceIndicator lowPriceIndicator) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = barCount;
        this.highPriceIndicator = highPriceIndicator;
        this.lowPriceIndicator = lowPriceIndicator;
    }

    @Override
    protected Num calculate(int index) {
        HighestValueIndicator highestHigh = new HighestValueIndicator(highPriceIndicator, barCount);
        LowestValueIndicator lowestMin = new LowestValueIndicator(lowPriceIndicator, barCount);

        Num highestHighPrice = highestHigh.getValue(index);
        Num lowestLowPrice = lowestMin.getValue(index);

        return indicator.getValue(index).minus(lowestLowPrice)
                .dividedBy(highestHighPrice.minus(lowestLowPrice))
                .multipliedBy(numOf(100));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}

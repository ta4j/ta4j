/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.*;
import org.ta4j.core.num.Num;

/**
 * Stochastic oscillator K.
 */
public class StochasticOscillatorKIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final Indicator<Num> highPriceIndicator;
    private final Indicator<Num> lowPriceIndicator;
    private final int barCount;

    /**
     * Constructor with:
     *
     * <ul>
     * <li>{@code indicator} = {@link ClosePriceIndicator}
     * <li>{@code highPriceIndicator} = {@link HighPriceIndicator}
     * <li>{@code lowPriceIndicator} = {@link LowPriceIndicator}
     * </ul>
     *
     * @param barSeries the bar series
     * @param barCount  the time frame
     */
    public StochasticOscillatorKIndicator(BarSeries barSeries, int barCount) {
        this(new ClosePriceIndicator(barSeries), barCount, new HighPriceIndicator(barSeries),
                new LowPriceIndicator(barSeries));
    }

    /**
     * Constructor.
     *
     * @param indicator          the {@link Indicator}
     * @param barCount           the time frame
     * @param highPriceIndicator the {@link Indicator}
     * @param lowPriceIndicator  the {@link Indicator}
     */
    public StochasticOscillatorKIndicator(Indicator<Num> indicator, int barCount, Indicator<Num> highPriceIndicator,
            Indicator<Num> lowPriceIndicator) {
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

        return indicator.getValue(index)
                .minus(lowestLowPrice)
                .dividedBy(highestHighPrice.minus(lowestLowPrice))
                .multipliedBy(getBarSeries().numFactory().hundred());
    }

    @Override
    public int getCountOfUnstableBars() {
        int baseUnstable = Math.max(indicator.getCountOfUnstableBars(),
                Math.max(highPriceIndicator.getCountOfUnstableBars(), lowPriceIndicator.getCountOfUnstableBars()));
        return baseUnstable + barCount - 1;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}

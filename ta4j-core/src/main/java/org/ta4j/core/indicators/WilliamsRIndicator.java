/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.num.Num;

/**
 * William's R indicator.
 *
 * @see <a href=
 *      "https://www.investopedia.com/terms/w/williamsr.asp">https://www.investopedia.com/terms/w/williamsr.asp</a>
 */
public class WilliamsRIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> closePriceIndicator;
    private final int barCount;
    private final HighPriceIndicator highPriceIndicator;
    private final LowPriceIndicator lowPriceIndicator;
    private final Num multiplier;
    private final transient HighestValueIndicator highestHigh;
    private final transient LowestValueIndicator lowestLow;

    /**
     * Constructor.
     *
     * @param barSeries the bar series
     * @param barCount  the time frame
     */
    public WilliamsRIndicator(BarSeries barSeries, int barCount) {
        this(new ClosePriceIndicator(barSeries), barCount, new HighPriceIndicator(barSeries),
                new LowPriceIndicator(barSeries));
    }

    /**
     * Constructor.
     *
     * @param closePriceIndicator the {@link ClosePriceIndicator}
     * @param barCount            the time frame for {@code highPriceIndicator} and
     *                            {@code lowPriceIndicator}
     * @param highPriceIndicator  the {@link HighPriceIndicator}
     * @param lowPriceIndicator   the {@link LowPriceIndicator}
     */
    public WilliamsRIndicator(ClosePriceIndicator closePriceIndicator, int barCount,
            HighPriceIndicator highPriceIndicator, LowPriceIndicator lowPriceIndicator) {
        super(closePriceIndicator, highPriceIndicator, lowPriceIndicator);
        this.closePriceIndicator = closePriceIndicator;
        this.barCount = barCount;
        this.highPriceIndicator = highPriceIndicator;
        this.lowPriceIndicator = lowPriceIndicator;
        this.multiplier = getBarSeries().numFactory().numOf(-100);
        this.highestHigh = new HighestValueIndicator(highPriceIndicator, barCount);
        this.lowestLow = new LowestValueIndicator(lowPriceIndicator, barCount);
    }

    @Override
    protected Num calculate(int index) {
        Num highestHighPrice = highestHigh.getValue(index);
        Num lowestLowPrice = lowestLow.getValue(index);

        return ((highestHighPrice.minus(closePriceIndicator.getValue(index)))
                .dividedBy(highestHighPrice.minus(lowestLowPrice))).multipliedBy(multiplier);
    }

    @Override
    public int getCountOfUnstableBars() {
        int baseUnstable = Math.max(closePriceIndicator.getCountOfUnstableBars(),
                Math.max(highPriceIndicator.getCountOfUnstableBars(), lowPriceIndicator.getCountOfUnstableBars()));
        return baseUnstable + barCount - 1;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}

/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.numeric.BinaryOperationIndicator;
import org.ta4j.core.num.Num;

import static org.ta4j.core.num.NaN.NaN;

/**
 * Hull moving average (HMA) indicator.
 *
 * @see <a href="http://alanhull.com/hull-moving-average">
 *      http://alanhull.com/hull-moving-average</a>
 */
public class HMAIndicator extends CachedIndicator<Num> {

    private final int barCount;
    private final WMAIndicator sqrtWma;

    /**
     * Constructor.
     *
     * @param indicator the {@link Indicator}
     * @param barCount  the time frame
     */
    public HMAIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.barCount = barCount;

        final var halfWma = new WMAIndicator(indicator, barCount / 2);
        final var origWma = new WMAIndicator(indicator, barCount);

        final var indicatorForSqrtWma = BinaryOperationIndicator
                .difference(BinaryOperationIndicator.product(halfWma, 2), origWma);
        this.sqrtWma = new WMAIndicator(indicatorForSqrtWma,
                getBarSeries().numFactory().numOf(barCount).sqrt().intValue());
    }

    @Override
    protected Num calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN;
        }
        return sqrtWma.getValue(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return sqrtWma.getCountOfUnstableBars();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }

}

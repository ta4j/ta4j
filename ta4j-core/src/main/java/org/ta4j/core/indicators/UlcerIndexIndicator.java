/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Ulcer index indicator.
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ulcer_index">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ulcer_index</a>
 * @see <a href=
 *      "https://en.wikipedia.org/wiki/Ulcer_index">https://en.wikipedia.org/wiki/Ulcer_index</a>
 */
public class UlcerIndexIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final int barCount;

    /**
     * Constructor.
     *
     * @param indicator the indicator
     * @param barCount  the time frame
     */
    public UlcerIndexIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = barCount;
    }

    @Override
    protected Num calculate(int index) {
        final int startIndex = Math.max(0, index - barCount + 1);
        final int numberOfObservations = index - startIndex + 1;
        final var numFactory = getBarSeries().numFactory();
        Num squaredAverage = numFactory.zero();
        Num highestValue = indicator.getValue(startIndex);
        for (int i = startIndex; i <= index; i++) {
            Num currentValue = indicator.getValue(i);
            if (currentValue.isGreaterThan(highestValue)) {
                highestValue = currentValue;
            }
            Num percentageDrawdown = currentValue.minus(highestValue)
                    .dividedBy(highestValue)
                    .multipliedBy(numFactory.hundred());
            squaredAverage = squaredAverage.plus(percentageDrawdown.pow(2));
        }
        squaredAverage = squaredAverage.dividedBy(numFactory.numOf(numberOfObservations));
        return squaredAverage.sqrt();
    }

    @Override
    public int getCountOfUnstableBars() {
        return indicator.getCountOfUnstableBars() + barCount - 1;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}

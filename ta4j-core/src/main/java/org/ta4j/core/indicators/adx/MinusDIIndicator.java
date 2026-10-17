/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.adx;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.averages.MMAIndicator;
import org.ta4j.core.num.Num;

/**
 * -DI indicator.
 *
 * <p>
 * Part of the Directional Movement System.
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:average_directional_index_adx">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:average_directional_index_adx</a>
 * @see <a href=
 *      "https://www.investopedia.com/terms/a/adx.asp">https://www.investopedia.com/terms/a/adx.asp</a>
 */
public class MinusDIIndicator extends CachedIndicator<Num> {

    private final int barCount;
    private final transient ATRIndicator atrIndicator;
    private final transient MinusDMIndicator minusDMIndicator;
    private final transient MMAIndicator avgMinusDMIndicator;

    /**
     * Constructor.
     *
     * @param series   the bar series
     * @param barCount the bar count for {@link #atrIndicator} and
     *                 {@link #avgMinusDMIndicator}
     */
    public MinusDIIndicator(BarSeries series, int barCount) {
        this(barCount, new MinusDMIndicator(series), new ATRIndicator(series, barCount));
    }

    private MinusDIIndicator(int barCount, MinusDMIndicator minusDMIndicator, ATRIndicator atrIndicator) {
        this(barCount, minusDMIndicator, atrIndicator, new MMAIndicator(minusDMIndicator, barCount));
    }

    private MinusDIIndicator(int barCount, MinusDMIndicator minusDMIndicator, ATRIndicator atrIndicator,
            MMAIndicator avgMinusDMIndicator) {
        super(atrIndicator, avgMinusDMIndicator);
        this.barCount = barCount;
        this.atrIndicator = atrIndicator;
        this.minusDMIndicator = minusDMIndicator;
        this.avgMinusDMIndicator = avgMinusDMIndicator;
    }

    @Override
    protected Num calculate(int index) {
        Num atrIndicatorValue = atrIndicator.getValue(index);
        Num averageDirectionalMovement = avgMinusDMIndicator.getValue(index);
        if (atrIndicatorValue.equals(getBarSeries().numFactory().zero())) {
            return getBarSeries().numFactory().zero();
        }

        return averageDirectionalMovement.dividedBy(atrIndicatorValue)
                .multipliedBy(getBarSeries().numFactory().hundred());
    }

    @Override
    public int getCountOfUnstableBars() {
        int dmUnstableBars = minusDMIndicator.getCountOfUnstableBars() + avgMinusDMIndicator.getCountOfUnstableBars();
        return Math.max(atrIndicator.getCountOfUnstableBars(), dmUnstableBars);
    }

    /**
     * @return {@code true}, because the Wilder averages retain full-history state
     * @since 0.26.1
     */
    @Override
    protected boolean hasRecursiveDependencies() {
        return true;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}

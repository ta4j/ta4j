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
    private final ATRIndicator atrIndicator;
    private final MinusDMIndicator minusDMIndicator;
    private final MMAIndicator avgMinusDMIndicator;

    /**
     * Constructor.
     *
     * @param series   the bar series
     * @param barCount the bar count for {@link #atrIndicator} and
     *                 {@link #avgMinusDMIndicator}
     */
    public MinusDIIndicator(BarSeries series, int barCount) {
        super(series);
        this.barCount = barCount;
        this.atrIndicator = new ATRIndicator(series, barCount);
        this.minusDMIndicator = new MinusDMIndicator(series);
        this.avgMinusDMIndicator = new MMAIndicator(minusDMIndicator, barCount);
    }

    @Override
    protected Num calculate(int index) {
        final var atrIndicatorValue = atrIndicator.getValue(index);
        if (atrIndicatorValue.equals(getBarSeries().numFactory().zero())) {
            return getBarSeries().numFactory().zero();
        }

        return avgMinusDMIndicator.getValue(index)
                .dividedBy(atrIndicatorValue)
                .multipliedBy(getBarSeries().numFactory().hundred());
    }

    @Override
    public int getCountOfUnstableBars() {
        int dmUnstableBars = minusDMIndicator.getCountOfUnstableBars() + avgMinusDMIndicator.getCountOfUnstableBars();
        return Math.max(atrIndicator.getCountOfUnstableBars(), dmUnstableBars);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}

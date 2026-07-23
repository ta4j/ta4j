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
 * +DI indicator.
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
public class PlusDIIndicator extends CachedIndicator<Num> {

    private final int barCount;
    private final transient ATRIndicator atrIndicator;
    private final transient PlusDMIndicator plusDMIndicator;
    private final transient MMAIndicator avgPlusDMIndicator;

    /**
     * Constructor.
     *
     * @param series   the bar series
     * @param barCount the bar count for {@link #atrIndicator} and
     *                 {@link #avgPlusDMIndicator}
     */
    public PlusDIIndicator(BarSeries series, int barCount) {
        this(PlusDIIndicator.class, series, barCount);
    }

    /**
     * Constructor for subclasses that provide their own audited cache identity.
     *
     * @param identityClass exact concrete indicator class eligible for sharing
     * @param series        the bar series
     * @param barCount      the bar count for {@link #atrIndicator} and
     *                      {@link #avgPlusDMIndicator}
     * @since 0.23.1
     */
    protected PlusDIIndicator(Class<?> identityClass, BarSeries series, int barCount) {
        super(series, identityOfExact(identityClass, barCount));
        this.barCount = barCount;
        this.atrIndicator = new ATRIndicator(series, barCount);
        this.plusDMIndicator = new PlusDMIndicator(series);
        this.avgPlusDMIndicator = new MMAIndicator(plusDMIndicator, barCount);
    }

    @Override
    protected Num calculate(int index) {
        final var atrIndicatorValue = atrIndicator.getValue(index);
        if (atrIndicatorValue.equals(getBarSeries().numFactory().zero())) {
            return getBarSeries().numFactory().zero();
        }

        return avgPlusDMIndicator.getValue(index)
                .dividedBy(atrIndicatorValue)
                .multipliedBy(getBarSeries().numFactory().hundred());
    }

    @Override
    public int getCountOfUnstableBars() {
        int dmUnstableBars = plusDMIndicator.getCountOfUnstableBars() + avgPlusDMIndicator.getCountOfUnstableBars();
        return Math.max(atrIndicator.getCountOfUnstableBars(), dmUnstableBars);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}

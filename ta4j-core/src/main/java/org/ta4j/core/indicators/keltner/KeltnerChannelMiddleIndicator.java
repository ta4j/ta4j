/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.keltner;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.TypicalPriceIndicator;
import org.ta4j.core.num.Num;

import static org.ta4j.core.num.NaN.NaN;

/**
 * Keltner Channel (middle line) indicator.
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:keltner_channels">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:keltner_channels</a>
 */
public class KeltnerChannelMiddleIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final transient EMAIndicator emaIndicator;
    private final int barCount;

    /**
     * Constructor.
     *
     * @param series      the bar series
     * @param barCountEMA the bar count for the {@link EMAIndicator}
     */
    public KeltnerChannelMiddleIndicator(BarSeries series, int barCountEMA) {
        this(new TypicalPriceIndicator(series), barCountEMA);
    }

    /**
     * Constructor.
     *
     * @param indicator   the {@link Indicator}
     * @param barCountEMA the bar count for the {@link EMAIndicator}
     */
    public KeltnerChannelMiddleIndicator(Indicator<Num> indicator, int barCountEMA) {
        this(indicator, barCountEMA, new EMAIndicator(indicator, barCountEMA));
    }

    private KeltnerChannelMiddleIndicator(Indicator<Num> indicator, int barCountEMA, EMAIndicator emaIndicator) {
        super(emaIndicator);
        this.indicator = indicator;
        this.barCount = barCountEMA;
        this.emaIndicator = emaIndicator;
    }

    @Override
    protected Num calculate(int index) {
        Num average = emaIndicator.getValue(index);
        if (index < getCountOfUnstableBars()) {
            return NaN;
        }
        return average;
    }

    @Override
    public int getCountOfUnstableBars() {
        return indicator.getCountOfUnstableBars() + getBarCount();
    }

    /**
     * @return {@code true}, because the EMA retains full-history state
     * @since 0.26.1
     */
    @Override
    protected boolean hasRecursiveDependencies() {
        return true;
    }

    /** @return the bar count of {@link #emaIndicator} */
    public int getBarCount() {
        return barCount;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + getBarCount();
    }
}

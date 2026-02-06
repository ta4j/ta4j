/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.keltner;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicator;
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
public class KeltnerChannelMiddleIndicator extends AbstractIndicator<Num> {

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
        super(indicator.getBarSeries());
        this.indicator = indicator;
        this.barCount = barCountEMA;
        this.emaIndicator = new EMAIndicator(indicator, barCountEMA);
    }

    @Override
    public Num getValue(int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN;
        }
        return emaIndicator.getValue(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return indicator.getCountOfUnstableBars() + getBarCount();
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

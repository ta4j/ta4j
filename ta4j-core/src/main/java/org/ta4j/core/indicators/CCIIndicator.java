/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.TypicalPriceIndicator;
import org.ta4j.core.indicators.statistics.MeanDeviationIndicator;
import org.ta4j.core.num.Num;

/**
 * Commodity Channel Index (CCI) indicator.
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:commodity_channel_in">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:commodity_channel_in</a>
 */
public class CCIIndicator extends CachedIndicator<Num> {

    private final Num factor;
    private final TypicalPriceIndicator typicalPriceInd;
    private final SMAIndicator smaInd;
    private final MeanDeviationIndicator meanDeviationInd;
    private final int barCount;

    /**
     * Constructor.
     *
     * @param series   the bar series
     * @param barCount the time frame (normally 20)
     */
    public CCIIndicator(BarSeries series, int barCount) {
        super(series);
        this.factor = getBarSeries().numFactory().numOf(0.015);
        this.typicalPriceInd = new TypicalPriceIndicator(series);
        this.smaInd = new SMAIndicator(typicalPriceInd, barCount);
        this.meanDeviationInd = new MeanDeviationIndicator(typicalPriceInd, barCount);
        this.barCount = barCount;
    }

    @Override
    protected Num calculate(int index) {
        final Num typicalPrice = typicalPriceInd.getValue(index);
        final Num typicalPriceAvg = smaInd.getValue(index);
        final Num meanDeviation = meanDeviationInd.getValue(index);
        if (meanDeviation.isZero()) {
            return getBarSeries().numFactory().zero();
        }
        return (typicalPrice.minus(typicalPriceAvg)).dividedBy(meanDeviation.multipliedBy(factor));
    }

    @Override
    public int getCountOfUnstableBars() {
        return barCount;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}

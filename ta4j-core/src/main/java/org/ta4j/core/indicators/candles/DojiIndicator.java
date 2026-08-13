/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.candles;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.numeric.UnaryOperationIndicator;
import org.ta4j.core.num.Num;

/**
 * Doji indicator.
 *
 * <p>
 * A candle/bar is considered Doji if its body height is lower than the average
 * multiplied by a factor.
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:chart_analysis:introduction_to_candlesticks#doji">
 *      http://stockcharts.com/school/doku.php?id=chart_school:chart_analysis:introduction_to_candlesticks#doji</a>
 */
public class DojiIndicator extends CachedIndicator<Boolean> {

    /** Body height. */
    private final transient Indicator<Num> bodyHeightInd;

    /** Average body height. */
    private final transient SMAIndicator averageBodyHeightInd;

    /** The number of bars used to calculate the average body height. */
    private final int barCount;

    /** The raw factor used when checking if a candle is Doji. */
    private final double bodyFactor;

    /** The factor used when checking if a candle is Doji. */
    private final transient Num factor;

    /**
     * Constructor.
     *
     * @param series     the bar series
     * @param barCount   the number of bars used to calculate the average body
     *                   height
     * @param bodyFactor the factor used when checking if a candle is Doji
     */
    public DojiIndicator(BarSeries series, int barCount, double bodyFactor) {
        super(series);
        this.barCount = barCount;
        this.bodyFactor = bodyFactor;
        this.bodyHeightInd = UnaryOperationIndicator.abs(new RealBodyIndicator(series));
        this.averageBodyHeightInd = new SMAIndicator(bodyHeightInd, barCount);
        this.factor = getBarSeries().numFactory().numOf(bodyFactor);
    }

    @Override
    protected Boolean calculate(int index) {
        if (index < 1) {
            return bodyHeightInd.getValue(index).isZero();
        }

        Num averageBodyHeight = averageBodyHeightInd.getValue(index - 1);
        Num currentBodyHeight = bodyHeightInd.getValue(index);

        return currentBodyHeight.isLessThan(averageBodyHeight.multipliedBy(factor));
    }

    @Override
    public int getCountOfUnstableBars() {
        return averageBodyHeightInd.getCountOfUnstableBars() + 1;
    }
}

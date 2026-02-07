/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.numeric.BinaryOperationIndicator;
import org.ta4j.core.num.Num;

/**
 * Mass index indicator.
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:mass_index">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:mass_index</a>
 */
public class MassIndexIndicator extends CachedIndicator<Num> {

    private final transient EMAIndicator singleEma;
    private final transient EMAIndicator doubleEma;
    private final int emaBarCount;
    private final int barCount;

    /**
     * Constructor.
     *
     * @param series      the bar series
     * @param emaBarCount the time frame for EMAs (usually 9)
     * @param barCount    the time frame
     */
    public MassIndexIndicator(BarSeries series, int emaBarCount, int barCount) {
        super(series);
        final var highLowDifferential = BinaryOperationIndicator.difference(new HighPriceIndicator(series),
                new LowPriceIndicator(series));
        this.emaBarCount = emaBarCount;
        this.barCount = barCount;
        this.singleEma = new EMAIndicator(highLowDifferential, emaBarCount);
        this.doubleEma = new EMAIndicator(singleEma, emaBarCount); // Not the same formula as DoubleEMAIndicator
    }

    @Override
    protected Num calculate(int index) {
        final int startIndex = Math.max(0, index - barCount + 1);
        Num massIndex = getBarSeries().numFactory().zero();
        for (int i = startIndex; i <= index; i++) {
            Num emaRatio = singleEma.getValue(i).dividedBy(doubleEma.getValue(i));
            massIndex = massIndex.plus(emaRatio);
        }
        return massIndex;
    }

    @Override
    public int getCountOfUnstableBars() {
        int emaUnstableBars = singleEma.getCountOfUnstableBars() + doubleEma.getCountOfUnstableBars();
        return emaUnstableBars + barCount - 1;
    }
}

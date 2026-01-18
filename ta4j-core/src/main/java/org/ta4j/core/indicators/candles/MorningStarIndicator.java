/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators.candles;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.trend.DownTrendIndicator;
import org.ta4j.core.num.Num;

/**
 * Morning star candle indicator.
 *
 * @see <a href="https://www.investopedia.com/terms/m/morningstar.asp">
 *      https://www.investopedia.com/terms/m/morningstar.asp</a>
 */
public class MorningStarIndicator extends CachedIndicator<Boolean> {

    private final DownTrendIndicator trendIndicator;
    private final RealBodyIndicator realBodyIndicator;
    private Num smallBodyThresholdPercentage;
    private Num bigBodyThresholdPercentage;

    /**
     * Constructor.
     *
     * @param series the bar series
     */
    public MorningStarIndicator(final BarSeries series) {
        super(series);
        this.trendIndicator = new DownTrendIndicator(series);
        this.realBodyIndicator = new RealBodyIndicator(series);
        this.smallBodyThresholdPercentage = series.numFactory().numOf(0.015);
        this.bigBodyThresholdPercentage = series.numFactory().numOf(0.03);
    }

    @Override
    protected Boolean calculate(int index) {
        if (index < 2) {
            // Morning star is a 3-candle pattern
            return false;
        }
        Bar firstBar = getBarSeries().getBar(index - 2);
        Bar secondBar = getBarSeries().getBar(index - 1);
        Bar thirdBar = getBarSeries().getBar(index);
        Num firstBarPercentage = this.realBodyIndicator.getValue(index - 2).abs().dividedBy(firstBar.getOpenPrice());
        Num secondBarPercentage = this.realBodyIndicator.getValue(index - 1).abs().dividedBy(secondBar.getOpenPrice());
        Num thirdBarPercentage = this.realBodyIndicator.getValue(index).abs().dividedBy(thirdBar.getOpenPrice());

        return firstBar.isBearish() && firstBarPercentage.isGreaterThanOrEqual(bigBodyThresholdPercentage)
                && secondBar.getOpenPrice().isLessThan(firstBar.getClosePrice())
                && secondBarPercentage.isLessThanOrEqual(smallBodyThresholdPercentage)
                && thirdBar.getClosePrice().isLessThan(firstBar.getOpenPrice()) && thirdBar.isBullish()
                && thirdBarPercentage.isGreaterThanOrEqual(bigBodyThresholdPercentage)
                && this.trendIndicator.getValue(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}

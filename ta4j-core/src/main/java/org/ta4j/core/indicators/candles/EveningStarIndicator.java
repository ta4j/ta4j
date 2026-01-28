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
import org.ta4j.core.indicators.trend.UpTrendIndicator;
import org.ta4j.core.num.Num;

/**
 * Evening star candle indicator.
 *
 * @see <a href="https://www.investopedia.com/terms/e/eveningstar.asp">
 *      https://www.investopedia.com/terms/e/eveningstar.asp</a>
 * @since 0.22.2
 */
public class EveningStarIndicator extends CachedIndicator<Boolean> {

    private final UpTrendIndicator trendIndicator;
    private final RealBodyIndicator realBodyIndicator;
    private final Num smallBodyThresholdPercentage;
    private final Num bigBodyThresholdPercentage;

    /**
     * Constructor.
     *
     * @param series the bar series
     */
    public EveningStarIndicator(final BarSeries series) {
        this(series, series.numFactory().numOf(0.015), series.numFactory().numOf(0.03));
    }

    /**
     * Constructor.
     *
     * @param series                       the bar series
     * @param smallBodyThresholdPercentage percentage to determine whether a candle
     *                                     has a small body or not
     * @param bigBodyThresholdPercentage   percentage to determine whether a candle
     *                                     has a big body or not
     */
    public EveningStarIndicator(final BarSeries series, final Num smallBodyThresholdPercentage,
            final Num bigBodyThresholdPercentage) {
        super(series);
        this.trendIndicator = new UpTrendIndicator(series);
        this.realBodyIndicator = new RealBodyIndicator(series);
        this.smallBodyThresholdPercentage = smallBodyThresholdPercentage;
        this.bigBodyThresholdPercentage = bigBodyThresholdPercentage;
    }

    @Override
    protected Boolean calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return false;
        }
        Bar firstBar = getBarSeries().getBar(index - 2);
        Bar secondBar = getBarSeries().getBar(index - 1);
        Bar thirdBar = getBarSeries().getBar(index);
        Num firstBarPercentage = this.realBodyIndicator.getValue(index - 2).abs().dividedBy(firstBar.getOpenPrice());
        Num secondBarPercentage = this.realBodyIndicator.getValue(index - 1).abs().dividedBy(secondBar.getOpenPrice());
        Num thirdBarPercentage = this.realBodyIndicator.getValue(index).abs().dividedBy(thirdBar.getOpenPrice());
        Num firstBarMiddlePoint = firstBar.getClosePrice()
                .minus(firstBar.getOpenPrice())
                .dividedBy(getBarSeries().numFactory().numOf(2))
                .plus(firstBar.getOpenPrice());

        return firstBar.isBullish() && firstBarPercentage.isGreaterThanOrEqual(bigBodyThresholdPercentage)
                && secondBar.getOpenPrice().isGreaterThan(firstBar.getClosePrice())
                && secondBarPercentage.isLessThanOrEqual(smallBodyThresholdPercentage)
                && thirdBar.getClosePrice().isLessThan(firstBarMiddlePoint) && thirdBar.isBearish()
                && thirdBarPercentage.isGreaterThanOrEqual(bigBodyThresholdPercentage)
                && this.trendIndicator.getValue(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return 2;
    }
}

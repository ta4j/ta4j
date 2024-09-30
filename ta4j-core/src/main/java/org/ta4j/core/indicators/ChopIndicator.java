/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
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
package org.ta4j.core.indicators;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.num.Num;

/**
 * The "CHOP" index is used to indicate side-ways markets.
 *
 * <pre>
 * 100++ * LOG10( SUM(ATR(1), n) / ( MaxHi(n) - MinLo(n) ) ) / LOG10(n),
 * with n = User defined period length.
 * LOG10(n) = base-10 LOG of n
 * ATR(1) = Average True
 * Range (Period of 1) SUM(ATR(1), n) = Sum of the Average True Range over past n bars
 * MaxHi(n) = The highest high over past n bars
 *
 * ++ usually this index is between 0 and 100, but could be scaled differently
 * by the 'scaleTo' arg of the constructor
 * </pre>
 *
 * @see <a href=
 *      "https://www.tradingview.com/wiki/Choppiness_Index_(CHOP)">https://www.tradingview.com/wiki/Choppiness_Index_(CHOP)</a>
 *
 * @apiNote Minimal deviations in last decimal places possible. During the
 *          calculations this indicator converts {@link Num Decimal /BigDecimal}
 *          to to {@link Double double}
 */
public class ChopIndicator extends CachedIndicator<Num> {

    private final ATRIndicator atrIndicator;
    private final int timeFrame;
    private final Num log10n;
    private final HighestValueIndicator hvi;
    private final LowestValueIndicator lvi;
    private final Num scaleUpTo;

    /**
     * Constructor.
     *
     * @param barSeries   the bar series
     * @param ciTimeFrame time-frame often something like '14'
     * @param scaleTo     maximum value to scale this oscillator, usually '1' or
     *                    '100'
     */
    public ChopIndicator(BarSeries barSeries, int ciTimeFrame, int scaleTo) {
        super(barSeries);
        this.atrIndicator = new ATRIndicator(barSeries, 1); // ATR(1) = Average True Range (Period of 1)
        this.hvi = new HighestValueIndicator(new HighPriceIndicator(barSeries), ciTimeFrame);
        this.lvi = new LowestValueIndicator(new LowPriceIndicator(barSeries), ciTimeFrame);
        this.timeFrame = ciTimeFrame;
        this.log10n = getBarSeries().numFactory().numOf(Math.log10(ciTimeFrame));
        this.scaleUpTo = getBarSeries().numFactory().numOf(scaleTo);
    }

    @Override
    public Num calculate(int index) {
        Num summ = atrIndicator.getValue(index);
        for (int i = 1; i < timeFrame; ++i) {
            summ = summ.plus(atrIndicator.getValue(index - i));
        }
        Num a = summ.dividedBy((hvi.getValue(index).minus(lvi.getValue(index))));
        // TODO: implement Num.log10(Num)
        return scaleUpTo.multipliedBy(getBarSeries().numFactory().numOf(Math.log10(a.doubleValue()))).dividedBy(log10n);
    }

    @Override
    public int getUnstableBars() {
        return timeFrame;
    }
}

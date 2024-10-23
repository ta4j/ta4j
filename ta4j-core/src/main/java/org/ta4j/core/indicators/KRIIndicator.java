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
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.CombineIndicator;
import org.ta4j.core.indicators.helpers.TransformIndicator;
import org.ta4j.core.num.Num;

/**
 * Kairi Relative Index (KRI) indicator by LazyBear.
 *
 * @see <a href=
 *      "https://www.tradingview.com/script/xzRPAboO-Indicator-Kairi-Relative-Index-KRI/">TradingView</a>
 */
public class KRIIndicator extends AbstractIndicator<Num> {
    private final Indicator<Num> kriIndicator;
    private final int barCount;

    public KRIIndicator(BarSeries series, int barCount) {
        this(new ClosePriceIndicator(series), barCount);
    }

    public KRIIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator.getBarSeries());

        Indicator<Num> smaIndicator = new SMAIndicator(indicator, barCount);
        Indicator<Num> difference = CombineIndicator.minus(indicator, smaIndicator);
        Indicator<Num> quotient = CombineIndicator.divide(difference, smaIndicator);
        this.kriIndicator = TransformIndicator.multiply(quotient, 100);
        this.barCount = barCount;
    }

    @Override
    public Num getValue(int index) {
        return kriIndicator.getValue(index);
    }

    @Override
    public int getUnstableBars() {
        return barCount;
    }
}

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
package org.ta4j.core.bars;

import org.ta4j.core.Bar;
import org.ta4j.core.BaseBar;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/*
 * Heikin-Ashi bar builder
 * @see <a href="https://www.investopedia.com/trading/heikin-ashi-better-candlestick/">Heikin-Ashi</a>
 */
public class HeikinAshiBarBuilder extends TimeBarBuilder {
    private Num previousHeikinAshiOpenPrice;
    private Num previousHeikinAshiClosePrice;

    public HeikinAshiBarBuilder() {
        super();
    }

    public HeikinAshiBarBuilder(NumFactory numFactory) {
        super(numFactory);
    }

    public HeikinAshiBarBuilder previousHeikinAshiOpenPrice(Num previousOpen) {
        previousHeikinAshiOpenPrice = previousOpen;
        return this;
    }

    public HeikinAshiBarBuilder previousHeikinAshiClosePrice(Num previousClose) {
        previousHeikinAshiClosePrice = previousClose;
        return this;
    }

    @Override
    public Bar build() {
        if (previousHeikinAshiOpenPrice == null || previousHeikinAshiClosePrice == null) {
            return super.build();
        } else {
            var numFactory = openPrice.getNumFactory();
            var heikinAshiClose = openPrice.plus(highPrice)
                    .plus(lowPrice)
                    .plus(closePrice)
                    .dividedBy(numFactory.numOf(4));
            var heikinAshiOpen = previousHeikinAshiOpenPrice.plus(previousHeikinAshiClosePrice)
                    .dividedBy(numFactory.numOf(2));
            var heikinAshiHigh = highPrice.max(heikinAshiOpen).max(heikinAshiClose);
            var heikinAshiLow = lowPrice.min(heikinAshiOpen).min(heikinAshiClose);
            return new BaseBar(timePeriod, beginTime, endTime, heikinAshiOpen, heikinAshiHigh, heikinAshiLow,
                    heikinAshiClose, volume, amount, trades);
        }
    }

}
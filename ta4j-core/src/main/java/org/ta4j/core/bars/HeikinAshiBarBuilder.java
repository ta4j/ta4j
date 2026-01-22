/*
 * SPDX-License-Identifier: MIT
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
/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)
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
package eu.verdelhan.ta4j.indicators.trackers;

import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.indicators.CachedIndicator;

/**
 * The Kaufman's Adaptive Moving Average (KAMA)  Indicator.
 * 
 * @see http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:kaufman_s_adaptive_moving_average
 */
public class KAMAIndicator extends CachedIndicator<Decimal> {

    private final Indicator<Decimal> price;
    
    private final int timeFrameEffectiveRatio;
    
    private final Decimal fastest;
    
    private final Decimal slowest;
    
    /**
     * Constructor.
     *
     * @param price the price
     * @param timeFrameEffectiveRatio the time frame of the effective ratio (usually 10)
     * @param timeFrameFast the time frame fast (usually 2)
     * @param timeFrameSlow the time frame slow (usually 30)
     */
    public KAMAIndicator(Indicator<Decimal> price, int timeFrameEffectiveRatio, int timeFrameFast, int timeFrameSlow) {
        super(price);
        this.price = price;
        this.timeFrameEffectiveRatio = timeFrameEffectiveRatio;
        fastest = Decimal.TWO.dividedBy(Decimal.valueOf(timeFrameFast + 1));
        slowest = Decimal.TWO.dividedBy(Decimal.valueOf(timeFrameSlow + 1));
    }

    @Override
    protected Decimal calculate(int index) {
        Decimal currentPrice = price.getValue(index);
        if (index < timeFrameEffectiveRatio) {
            return currentPrice;
        }
        /*
         * Efficiency Ratio (ER)
         * ER = Change/Volatility
         * Change = ABS(Close - Close (10 periods ago))
         * Volatility = Sum10(ABS(Close - Prior Close))
         * Volatility is the sum of the absolute value of the last ten price changes (Close - Prior Close).
         */
        int startChangeIndex = Math.max(0, index - timeFrameEffectiveRatio);
        Decimal change = currentPrice.minus(price.getValue(startChangeIndex)).abs();
        Decimal volatility = Decimal.ZERO;
        for (int i = startChangeIndex; i < index; i++) {
            volatility = volatility.plus(price.getValue(i + 1).minus(price.getValue(i)).abs());
        }
        Decimal er = change.dividedBy(volatility);
        /*
         * Smoothing Constant (SC)
         * SC = [ER x (fastest SC - slowest SC) + slowest SC]2
         * SC = [ER x (2/(2+1) - 2/(30+1)) + 2/(30+1)]2
         */
        Decimal sc = er.multipliedBy(fastest.minus(slowest)).plus(slowest).pow(2);
        /*
         * KAMA
         * Current KAMA = Prior KAMA + SC x (Price - Prior KAMA)
         */
        Decimal priorKAMA = getValue(index - 1);
        return priorKAMA.plus(sc.multipliedBy(currentPrice.minus(priorKAMA)));
    }

}

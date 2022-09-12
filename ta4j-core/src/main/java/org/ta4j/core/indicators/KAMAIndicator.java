/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
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

import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * The Kaufman's Adaptive Moving Average (KAMA) Indicator.
 * * 考夫曼的自适应移动平均线 (KAMA) 指标。
 * 
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:kaufman_s_adaptive_moving_average">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:kaufman_s_adaptive_moving_average</a>
 */
public class KAMAIndicator extends RecursiveCachedIndicator<Num> {

    private final Indicator<Num> price;

    private final int barCountEffectiveRatio;

    private final Num fastest;

    private final Num slowest;

    /**
     * Constructor.
     *
     * @param price                  the price
     *                               价格
     * @param barCountEffectiveRatio the time frame of the effective ratio (usually 10)
     *                               有效比率的时间范围（通常为 10）
     * @param barCountFast           the time frame fast (usually 2)
     *                               时间框架快（通常为 2）
     * @param barCountSlow           the time frame slow (usually 30)
     *                               时间框架慢（通常为 30）
     */
    public KAMAIndicator(Indicator<Num> price, int barCountEffectiveRatio, int barCountFast, int barCountSlow) {
        super(price);
        this.price = price;
        this.barCountEffectiveRatio = barCountEffectiveRatio;
        fastest = numOf(2).dividedBy(numOf(barCountFast + 1));
        slowest = numOf(2).dividedBy(numOf(barCountSlow + 1));
    }

    /**
     * Constructor with default values: <br/>
      具有默认值的构造函数：<br/>
      - barCountEffectiveRatio=10 <br/>
      - barCountFast=2 <br/>
      - barCountSlow=30
     *
     * @param price the priceindicator
     *              价格指标
     */
    public KAMAIndicator(Indicator<Num> price) {
        this(price, 10, 2, 30);
    }

    @Override
    protected Num calculate(int index) {
        Num currentPrice = price.getValue(index);
        if (index < barCountEffectiveRatio) {
            return currentPrice;
        }
        /*
         * Efficiency Ratio (ER) ER = Change/Volatility Change = ABS(Close - Close (10 periods ago)) Volatility = Sum10(ABS(Close - Prior Close)) Volatility is the sum of the absolute value of the last ten price changes (Close - Prior Close).
         * * 效率比 (ER) ER = 变化/波动率变化 = ABS(收盘价 - 收盘价 (10 期前)) 波动率 = Sum10(ABS(收盘价 - 之前收盘价)) 波动率是最近十次价格变化的绝对值之和 （关闭 - 之前关闭）。
         */
        int startChangeIndex = Math.max(0, index - barCountEffectiveRatio);
        Num change = currentPrice.minus(price.getValue(startChangeIndex)).abs();
        Num volatility = numOf(0);
        for (int i = startChangeIndex; i < index; i++) {
            volatility = volatility.plus(price.getValue(i + 1).minus(price.getValue(i)).abs());
        }
        Num er = change.dividedBy(volatility);
        /*
         * Smoothing Constant (SC) SC = [ER x (fastest SC - slowest SC) + slowest SC]2 SC = [ER x (2/(2+1) - 2/(30+1)) + 2/(30+1)]2
         * * 平滑常数 (SC) SC = [ER x (最快 SC - 最慢 SC) + 最慢 SC]2 SC = [ER x (2/(2+1) - 2/(30+1)) + 2/(30 +1)]2
         */
        Num sc = er.multipliedBy(fastest.minus(slowest)).plus(slowest).pow(2);
        /*
         * KAMA Current KAMA = Prior KAMA + SC x (Price - Prior KAMA)
         * * KAMA 当前 KAMA = 先前 KAMA + SC x（价格 - 先前 KAMA）
         */
        Num priorKAMA = getValue(index - 1);
        return priorKAMA.plus(sc.multipliedBy(currentPrice.minus(priorKAMA)));
    }

}

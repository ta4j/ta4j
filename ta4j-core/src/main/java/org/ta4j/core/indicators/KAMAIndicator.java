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
 * 考夫曼自适应移动平均线（Kaufman's Adaptive Moving Average，KAMA）是一种技术指标，用于平滑资产的价格数据，并根据市场的波动性调整移动平均线的参数，以适应不同的市场条件。
 *
 * KAMA指标的主要特点是其能够自动调整其平滑参数，以适应市场的波动性。相比传统的简单移动平均线（SMA）或指数移动平均线（EMA），KAMA能够更好地适应市场的变化，减少滞后性，提高对价格趋势的敏感度。
 *
 * KAMA的计算过程相对复杂，主要包括以下几个步骤：
 *
 * 1. 计算真实波幅（True Range）：通常是最高价与最低价之间的差值，用于衡量市场的波动性。
 * 2. 计算效率比率（Efficiency Ratio）：用于衡量价格变化的方向性和强度。
 * 3. 计算平滑参数（Smoothing Constant）：根据效率比率和市场的波动性来调整平滑参数，以实现自适应性。
 * 4. 计算KAMA值：将平滑参数应用于价格数据，计算出KAMA指标的值。
 *
 * KAMA指标的数值通常用于识别价格的趋势方向和市场的买卖信号。当KAMA值上升时，可能暗示着价格上升趋势的开始，为买入信号；当KAMA值下降时，可能暗示着价格下降趋势的开始，为卖出信号。
 *
 * 总的来说，KAMA是一种适应性强、对市场变化敏感的移动平均线指标，能够有效平滑价格数据并识别价格的趋势方向。交易者可以将KAMA与其他技术指标和价格模式结合使用，以制定更可靠的交易策略。
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

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

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.num.Num;

/**
 * The "CHOP" index is used to indicate side-ways markets see
 * “CHOP”指数用于指示横向市场
 * <a href= "https://www.tradingview.com/wiki/Choppiness_Index_(CHOP)">https://www.tradingview.com/wiki/Choppiness_Index_(CHOP)</a>
 * 100++ * LOG10( SUM(ATR(1), n) / ( MaxHi(n) - MinLo(n) ) ) / LOG10(n) n = User
 defined period length. LOG10(n) = base-10 LOG of n ATR(1) = Average True
 Range (Period of 1) SUM(ATR(1), n) = Sum of the Average True Range over past
  n bars MaxHi(n) = The highest high over past n bars

 100++ * LOG10( SUM(ATR(1), n) / ( MaxHi(n) - MinLo(n) ) ) / LOG10(n) n = 用户
 定义的周期长度。 LOG10(n) = n 的以 10 为底的 LOG ATR(1) = 平均真
 Range (Period of 1) SUM(ATR(1), n) = 过去平均真实范围的总和
 n 根柱 MaxHi(n) = 过去 n 根柱的最高点
 *
 * ++ usually this index is between 0 and 100, but could be scaled differently
  by the 'scaleTo' arg of the constructor
 ++ 通常这个索引在 0 到 100 之间，但可以不同地缩放
 通过构造函数的 'scaleTo' 参数



 CHOP指数（Choppiness Index）用于指示市场是否处于横盘或震荡状态。它由E.W. Dreiss开发，旨在衡量市场的趋势性或震荡性。

 Choppiness Index根据最近一段时间内的价格行为计算市场的震荡程度。它的值范围从0到100。

 - 当CHOP指数接近100时，表示市场处于强劲的趋势。
 - 相反，当CHOP指数接近0时，表示市场处于横盘或震荡状态。

 交易者通常使用CHOP指数来帮助确定市场是否从趋势性阶段转变为横盘阶段，反之亦然。在横盘市场中，交易者可能会选择范围交易策略，而在趋势市场中，他们可能会更倾向于趋势跟踪策略。因此，CHOP指数可以成为根据市场情况调整交易策略的有价值工具。


 *
 * @apiNote Minimal deviations in last decimal places possible. During the   calculations this indicator converts {@link Num Decimal /BigDecimal}   to to {@link Double double}
 * * @apiNote 最后一位小数的偏差可能最小。 在计算期间，该指标将 {@link Num Decimal /BigDecimal} 转换为 {@link Double double}
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
     * @param barSeries   the bar series {@link BarSeries}
     *                    酒吧系列{@link BarSeries}
     * @param ciTimeFrame time-frame often something like '14'
     *                    时间范围通常类似于“14”
     * @param scaleTo     maximum value to scale this oscillator, usually '1' or '100'
     *                    缩放此振荡器的最大值，通常为“1”或“100”
     */
    public ChopIndicator(BarSeries barSeries, int ciTimeFrame, int scaleTo) {
        super(barSeries);
        // ATR(1) = Average True Range (Period of 1)
        // ATR(1) = 平均真实范围（1 的周期）
        this.atrIndicator = new ATRIndicator(barSeries, 1);
        hvi = new HighestValueIndicator(new HighPriceIndicator(barSeries), ciTimeFrame);
        lvi = new LowestValueIndicator(new LowPriceIndicator(barSeries), ciTimeFrame);
        this.timeFrame = ciTimeFrame;
        this.log10n = numOf(Math.log10(ciTimeFrame));
        this.scaleUpTo = numOf(scaleTo);
    }

    @Override
    public Num calculate(int index) {
        Num summ = atrIndicator.getValue(index);
        for (int i = 1; i < timeFrame; ++i) {
            summ = summ.plus(atrIndicator.getValue(index - i));
        }
        Num a = summ.dividedBy((hvi.getValue(index).minus(lvi.getValue(index))));
        // TODO: implement Num.log10(Num)
        // TODO: 实现 Num.log10(Num)
        return scaleUpTo.multipliedBy(numOf(Math.log10(a.doubleValue()))).dividedBy(log10n);
    }
}

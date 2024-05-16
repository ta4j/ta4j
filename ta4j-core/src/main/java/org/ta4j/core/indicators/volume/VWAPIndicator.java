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
package org.ta4j.core.indicators.volume;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.TypicalPriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.num.Num;

/**
 * The volume-weighted average price (VWAP) Indicator.
 * 成交量加权平均价格 （VWAP） 指标。
 *
 * 成交量加权平均价格（Volume-Weighted Average Price，VWAP）指标是一种常用的技术指标，用于衡量资产的平均交易价格，其计算基于成交量的加权平均。VWAP指标是交易者们在分析市场趋势和价格走势时经常使用的工具之一。
 *
 * VWAP指标的计算方法如下：
 *
 * 1. 对于给定的时间段（通常是一天），首先计算每个交易周期的成交量加权平均价格（Typical Price Volume，TPV）：
 *      TPV = TP * 成交量
 *    其中，\( TP \) 表示典型价格，\( 成交量 \) 表示该交易周期的成交量。
 *
 * 2. 计算总成交量（Total Volume）：
 *    将给定时间段内的所有交易周期的成交量相加。
 *
 * 3. 计算总成交量加权平均价格（Total VWAP）：
 *    将给定时间段内所有交易周期的成交量加权平均价格相加，然后除以总成交量。
 *      VWAP =  sum (TPV)} / 总成交量
 *
 * VWAP指标反映了整个交易时间段内的平均交易价格，并且由于其采用了成交量加权的方式，更好地反映了市场中实际发生的交易情况。
 *
 * VWAP指标常被用作参考，帮助交易者判断某一资产在特定时间段内的相对价值。例如，在交易过程中，交易者可能会将当前价格与VWAP进行比较，以判断当前价格是高于还是低于平均水平，从而做出买入或卖出的决策。此外，VWAP也经常被机构投资者用作衡量其交易绩效的指标之一。
 * 
 * @see <a href=
 *      "http://www.investopedia.com/articles/trading/11/trading-with-vwap-mvwap.asp">
 *      http://www.investopedia.com/articles/trading/11/trading-with-vwap-mvwap.asp</a>
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:vwap_intraday">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:vwap_intraday</a>
 * @see <a href="https://en.wikipedia.org/wiki/Volume-weighted_average_price">
 *      https://en.wikipedia.org/wiki/Volume-weighted_average_price</a>
 */
public class VWAPIndicator extends CachedIndicator<Num> {

    private final int barCount;
    private final Indicator<Num> typicalPrice;
    private final Indicator<Num> volume;
    private final Num zero;

    /**
     * Constructor.
     * 
     * @param series   the series 系列
     * @param barCount the time frame 時間範圍
     */
    public VWAPIndicator(BarSeries series, int barCount) {
        super(series);
        this.barCount = barCount;
        this.typicalPrice = new TypicalPriceIndicator(series);
        this.volume = new VolumeIndicator(series);
        this.zero = numOf(0);
    }

    @Override
    protected Num calculate(int index) {
        if (index <= 0) {
            return typicalPrice.getValue(index);
        }
        int startIndex = Math.max(0, index - barCount + 1);
        Num cumulativeTPV = zero;
        Num cumulativeVolume = zero;
        for (int i = startIndex; i <= index; i++) {
            Num currentVolume = volume.getValue(i);
            cumulativeTPV = cumulativeTPV.plus(typicalPrice.getValue(i).multipliedBy(currentVolume));
            cumulativeVolume = cumulativeVolume.plus(currentVolume);
        }
        return cumulativeTPV.dividedBy(cumulativeVolume);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}

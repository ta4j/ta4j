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

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.num.Num;

/**
 * The Moving volume weighted average price (MVWAP) Indicator.
 * 移动量加权平均价格 (MVWAP) 指标。
 *
 * 移动成交量加权平均价格指标（Moving Volume Weighted Average Price，MVWAP）是一种技术指标，用于衡量资产价格的平均价格，其计算基于成交量的加权平均。与简单移动平均价格（SMA）不同，MVWAP考虑了每个价格的成交量，因此更加反映了交易活动的强度和资金流动性。
 *
 * MVWAP指标的计算过程如下：
 *
 * 1. 对于每个交易周期，计算成交量加权的典型价格（Typical Price Volume，TPV）：
 *      TPV = TP * 成交量
 *    其中，\( TP \) 表示典型价格，\( 成交量 \) 表示该交易周期的成交量。
 *
 * 2. 计算移动成交量加权平均价格（MVWAP）：
 *    - 对于每个移动窗口期，计算成交量加权的平均价格，其长度可根据需求选择。一般情况下，MVWAP的窗口期较长，以更好地反映长期趋势。
 *    \[ MVWAP = \frac{\sum_{i=1}^{n} TPV_i}{\sum_{i=1}^{n} 成交量_i} \]
 *    其中，\( n \) 表示移动窗口期的长度，\( TPV_i \) 和 \( 成交量_i \) 分别表示第 \( i \) 个交易周期的成交量加权的典型价格和成交量。
 *
 * MVWAP指标提供了一个考虑成交量的平均价格，因此更加反映了市场中的交易活动情况。它常被用来识别价格的趋势和支撑/阻力水平，以及判断交易者的入场和离场时机。
 * 
 * @see <a href=
 *      "http://www.investopedia.com/articles/trading/11/trading-with-vwap-mvwap.asp">
 *      http://www.investopedia.com/articles/trading/11/trading-with-vwap-mvwap.asp</a>
 */
public class MVWAPIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> sma;

    /**
     * Constructor.
     * 
     * @param vwap     the vwap
     *                 vwap
     * @param barCount the time frame
     *                 時間範圍
     */
    public MVWAPIndicator(VWAPIndicator vwap, int barCount) {
        super(vwap);
        this.sma = new SMAIndicator(vwap, barCount);
    }

    @Override
    protected Num calculate(int index) {
        return sma.getValue(index);
    }

}

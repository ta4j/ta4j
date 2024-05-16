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
import org.ta4j.core.indicators.helpers.TypicalPriceIndicator;
import org.ta4j.core.indicators.statistics.MeanDeviationIndicator;
import org.ta4j.core.num.Num;

/**
 * Commodity Channel Index (CCI) indicator.
 * 商品通道指数 (CCI) 指标。
 *
 * 商品通道指数（Commodity Channel Index，CCI）是一种技术指标，用于衡量资产价格与其统计的平均价格之间的偏离程度，从而帮助交易者确认价格的超买和超卖情况，以及可能的趋势反转点。
 *
 * CCI指标的计算基于资产的典型价格和统计的平均价格之间的差异。典型价格通常是最高价、最低价和收盘价的平均值。CCI指标的计算步骤如下：
 *
 * 1. 计算典型价格（Typical Price）：
 *     TP =  最高价 +  最低价  +  收盘价 / 3
 *
 * 2. 计算统计的平均价格（Simple Moving Average，SMA）：
 *    - 选择一个固定的时间周期（例如20个交易周期）。
 *    - 计算最近一段时间内的典型价格的简单移动平均值。
 *
 * 3. 计算平均偏差（Mean Deviation）：
 *    - 计算最近一段时间内每个典型价格与其对应的统计的平均价格之间的差异的绝对值。
 *    - 取这些绝对值的平均值。
 *
 * 4. 计算商品通道指数（Commodity Channel Index）：
 *      CCI =  TP - SMA(TP) / 0.015 * 平均偏差
 *
 * CCI指标的数值范围通常在-100到+100之间，数值越高表示价格与其统计的平均价格相比偏离程度越大，可能暗示着超买的情况；反之，数值越低表示价格偏离程度较小，可能暗示着超卖的情况。
 *
 * 交易者通常使用CCI指标来确认价格的超买和超卖情况，并结合其他技术指标一起使用，以辅助他们的交易决策。例如，当CCI指标超过+100时，可能暗示着价格超买，可能发生价格下跌；而当CCI指标低于-100时，可能暗示着价格超卖，可能发生价格上涨。
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:commodity_channel_in">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:commodity_channel_in</a>
 */
public class CCIIndicator extends CachedIndicator<Num> {

    private final Num factor;
    private final TypicalPriceIndicator typicalPriceInd;
    private final SMAIndicator smaInd;
    private final MeanDeviationIndicator meanDeviationInd;
    private final int barCount;

    /**
     * Constructor.
     *
     * @param series   the bar series 酒吧系列
     * @param barCount the time frame (normally 20)
     *                 时间范围（通常为 20）
     */
    public CCIIndicator(BarSeries series, int barCount) {
        super(series);
        factor = numOf(0.015);
        typicalPriceInd = new TypicalPriceIndicator(series);
        smaInd = new SMAIndicator(typicalPriceInd, barCount);
        meanDeviationInd = new MeanDeviationIndicator(typicalPriceInd, barCount);
        this.barCount = barCount;
    }

    @Override
    protected Num calculate(int index) {
        final Num typicalPrice = typicalPriceInd.getValue(index);
        final Num typicalPriceAvg = smaInd.getValue(index);
        final Num meanDeviation = meanDeviationInd.getValue(index);
        if (meanDeviation.isZero()) {
            return numOf(0);
        }
        return (typicalPrice.minus(typicalPriceAvg)).dividedBy(meanDeviation.multipliedBy(factor));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}

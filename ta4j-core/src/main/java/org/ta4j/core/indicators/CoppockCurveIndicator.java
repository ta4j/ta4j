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
import org.ta4j.core.indicators.helpers.SumIndicator;
import org.ta4j.core.num.Num;

/**
 * Coppock Curve indicator.
 * 科波克曲线指标。
 *
 * 科普克曲线（Coppock Curve）是一种趋势追踪指标，旨在识别长期趋势的变化。它由爱德华·科普克（Edward Coppock）于1962年开发，最初用于识别股票市场的买入信号。
 *
 * 科普克曲线的计算过程涉及以下步骤：
 *
 * 1. 首先，计算两个不同时间段的加权移动平均线（WMA）：
 *    - 第一个移动平均线通常使用11个月的时间周期。
 *    - 第二个移动平均线通常使用14个月的时间周期。
 *
 * 2. 然后，将这两个加权移动平均线相加，并应用一个固定的滞后因子（通常为10），得到科普克曲线的值。
 *
 * 科普克曲线的数值通常用作长期趋势的参考。当科普克曲线从底部向上穿越零线时，这被视为买入信号，暗示着市场可能迎来长期上升趋势。相反，当科普克曲线从顶部向下穿越零线时，这被视为卖出信号，暗示着市场可能进入长期下降趋势。
 *
 * 科普克曲线通常用于分析股票市场，但也可以应用于其他金融市场，如外汇市场和商品市场。交易者可以结合其他技术指标和价格模式来使用科普克曲线，以辅助他们的交易决策，并确认长期趋势的变化。
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:coppock_curve">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:coppock_curve</a>
 */
public class CoppockCurveIndicator extends CachedIndicator<Num> {

    private final WMAIndicator wma;

    /**
     * Constructor with default values: <br/>
      - longRoCBarCount=14 <br/>
      - shortRoCBarCount=11 <br/>
      - wmaBarCount=10
     具有默认值的构造函数：<br/>
     - longRoCBarCount=14 <br/>
     - shortRoCBarCount=11 <br/>
     - wmaBarCount=10
     *
     * @param indicator the indicator 指标
     */
    public CoppockCurveIndicator(Indicator<Num> indicator) {
        this(indicator, 14, 11, 10);
    }

    /**
     * Constructor.
     * 
     * @param indicator        the indicator (usually close price)
     *                         指标（通常是收盘价）
     * @param longRoCBarCount  the time frame for long term RoC
     *                         长期的时间框架 Roc
     *                         长期中华民国的时间框架
     * @param shortRoCBarCount the time frame for short term RoC
     *                         短期时间框架 RoC
     * @param wmaBarCount      the time frame (for WMA)
     *                         时间范围（对于 WMA）
     */
    public CoppockCurveIndicator(Indicator<Num> indicator, int longRoCBarCount, int shortRoCBarCount, int wmaBarCount) {
        super(indicator);
        SumIndicator sum = new SumIndicator(new ROCIndicator(indicator, longRoCBarCount),
                new ROCIndicator(indicator, shortRoCBarCount));
        wma = new WMAIndicator(sum, wmaBarCount);
    }

    @Override
    protected Num calculate(int index) {
        return wma.getValue(index);
    }
}

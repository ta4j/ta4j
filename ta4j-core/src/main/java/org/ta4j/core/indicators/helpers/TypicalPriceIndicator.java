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
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Typical price indicator.
 * 典型的价格指标。
 *
 * 典型价格指标（Typical Price Indicator）是一种用于衡量金融资产价格的指标，它通常用于技术分析中。典型价格指标是由典型价格计算得出的，典型价格是指在一定时间内资产的价格的加权平均值，它包括当日的最高价、最低价和收盘价。
 *
 * 典型价格指标的计算公式如下：
 *
 *  TP = (H + L + C) / 3
 *
 * 其中：
 * - \( TP \) 是典型价格（Typical Price）。
 * - \( H \) 是当日的最高价（High）。
 * - \( L \) 是当日的最低价（Low）。
 * - \( C \) 是当日的收盘价（Close）。
 *
 * 典型价格指标可以提供对资产价格的中心趋势的估计，因为它综合了当日价格的三个重要价位。它通常用于与其他技术指标一起使用，例如移动平均线、MACD等，以提供更全面的市场分析和交易信号。
 *
 * 典型价格指标在技术分析中具有广泛的应用，特别是在短期交易和日内交易中，可以帮助交易者更好地理解市场的价格动态和趋势方向。
 *
 */
public class TypicalPriceIndicator extends CachedIndicator<Num> {

    public TypicalPriceIndicator(BarSeries series) {
        super(series);
    }

    @Override
    protected Num calculate(int index) {
        final Bar bar = getBarSeries().getBar(index);
        final Num highPrice = bar.getHighPrice();
        final Num lowPrice = bar.getLowPrice();
        final Num closePrice = bar.getClosePrice();
        return highPrice.plus(lowPrice).plus(closePrice).dividedBy(numOf(3));
    }
}
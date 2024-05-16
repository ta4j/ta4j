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

import java.time.ZonedDateTime;
import java.util.function.Function;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

/**
 * DateTime indicator.
 * 日期时间指示器。
 *
 * DateTime indicator可能指的是在金融交易中使用日期和时间来进行分析或者标记特定的交易时间点。在这种情况下，日期和时间可以作为一种指标，用于确定交易信号的发生时间、分析市场活动的时间模式等。
 *
 * 在技术分析中，日期和时间可以用于以下方面：
 *
 * 1. **交易时机的确定：** 根据特定日期或时间点的模式或趋势，确定交易信号的发生时间。例如，在某些特定的日期或时间段内，市场可能会表现出更高的波动性或者更强的趋势，这可以作为交易时机的参考。
 *
 * 2. **季节性分析：** 分析市场在不同季节、月份或者时间段内的表现。某些季节性因素或者周期性因素可能会影响市场的表现，例如，股市中的“月底效应”或者“季节性窗口效应”。
 *
 * 3. **时间窗口分析：** 将时间划分为不同的窗口，例如交易日的不同时段（开盘、盘中、收盘）、交易周的不同日期（星期一、星期五）等，分析不同时间窗口内市场的行为特征。
 *
 * 4. **时间序列分析：** 使用时间序列分析方法，如ARIMA模型、指数平滑等，对时间序列数据进行建模和预测，以识别未来的市场走势。
 *
 * 总的来说，日期和时间在金融交易中扮演着重要的角色，可以作为分析市场行为、确定交易时机和制定交易策略的重要参考。
 *
 */
public class DateTimeIndicator extends CachedIndicator<ZonedDateTime> {

    private final Function<Bar, ZonedDateTime> action;

    public DateTimeIndicator(BarSeries barSeries) {
        this(barSeries, Bar::getBeginTime);
    }

    public DateTimeIndicator(BarSeries barSeries, Function<Bar, ZonedDateTime> action) {
        super(barSeries);
        this.action = action;
    }

    @Override
    protected ZonedDateTime calculate(int index) {
        Bar bar = getBarSeries().getBar(index);
        return this.action.apply(bar);
    }
}

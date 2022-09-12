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
package org.ta4j.core.aggregator;

import org.ta4j.core.BarSeries;

/**
 * Bar aggregator interface to aggregate list of bars into another list of bars.
 * 条形聚合器接口将条形列表聚合到另一个条形列表中。
 */
public interface BarSeriesAggregator {

    /**
     * Aggregates bar series.
     * 聚合条系列。
     *
     * @param series series to aggregate
     *               @param series 要聚合的系列
     * @return aggregated series
     * @return 聚合系列
     */
    default BarSeries aggregate(BarSeries series) {
        return aggregate(series, series.getName());
    }

    /**
     * Aggregates bar series.
     * 聚合条系列。
     *
     * @param series               series to aggregate
     *                             要聚合的系列
     * @param aggregatedSeriesName name for aggregated series
     *                             聚合系列的名称
     * @return aggregated series with specified name
     *                          具有指定名称的聚合系列
     */
    BarSeries aggregate(BarSeries series, String aggregatedSeriesName);
}

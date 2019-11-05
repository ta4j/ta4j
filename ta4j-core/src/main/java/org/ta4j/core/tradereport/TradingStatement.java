/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2019 Ta4j Organization & respective
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
package org.ta4j.core.tradereport;

import org.ta4j.core.Strategy;

/**
 * This class represents trading statement report which contains trade and
 * performance statistics
 */
public class TradingStatement {

    private final Strategy strategy;
    private final TradeStatsReport tradeStatsReport;
    private final PerformanceReport performanceReport;

    public TradingStatement(Strategy strategy, TradeStatsReport tradeStatsReport, PerformanceReport performanceReport) {
        this.strategy = strategy;
        this.tradeStatsReport = tradeStatsReport;
        this.performanceReport = performanceReport;
    }

    public Strategy getStrategy() {
        return strategy;
    }

    public TradeStatsReport getTradeStatsReport() {
        return tradeStatsReport;
    }

    public PerformanceReport getPerformanceReport() {
        return performanceReport;
    }
}

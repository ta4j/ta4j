/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
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
package org.ta4j.core.reports;

import org.ta4j.core.Strategy;

/**
 * Represents a trading statement report containing position and performance
 * statistics.
 */
public class TradingStatement {

    private final Strategy strategy;
    private final PositionStatsReport positionStatsReport;
    private final PerformanceReport performanceReport;

    /**
     * Constructor.
     *
     * @param strategy            the {@link Strategy}
     * @param positionStatsReport the {@link PositionStatsReport}
     * @param performanceReport   the {@link PerformanceReport}
     */
    public TradingStatement(Strategy strategy, PositionStatsReport positionStatsReport,
            PerformanceReport performanceReport) {
        this.strategy = strategy;
        this.positionStatsReport = positionStatsReport;
        this.performanceReport = performanceReport;
    }

    /** @return {@link #strategy} */
    public Strategy getStrategy() {
        return strategy;
    }

    /** @return {@link #positionStatsReport} */
    public PositionStatsReport getPositionStatsReport() {
        return positionStatsReport;
    }

    /** @return {@link #performanceReport} */
    public PerformanceReport getPerformanceReport() {
        return performanceReport;
    }
}

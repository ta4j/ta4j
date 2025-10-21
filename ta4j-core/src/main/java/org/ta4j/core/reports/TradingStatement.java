/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
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

import com.google.gson.Gson;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;

/**
 * Represents a trading statement report containing position and performance
 * statistics.
 */
public class TradingStatement {

    public final PositionStatsReport positionStatsReport;
    public final PerformanceReport performanceReport;
    public final TradingRecord tradingRecord;
    public final Strategy strategy;

    /**
     * Constructs a TradingStatement with the specified strategy, trading record,
     * position statistics report, and performance report.
     *
     * @param strategy            the trading strategy used to generate the
     *                            statement
     * @param tradingRecord       the record of all trading operations
     * @param positionStatsReport the report containing position-related statistics
     * @param performanceReport   the report containing performance metrics
     */
    public TradingStatement(Strategy strategy, TradingRecord tradingRecord, PositionStatsReport positionStatsReport,
            PerformanceReport performanceReport) {
        this.positionStatsReport = positionStatsReport;
        this.performanceReport = performanceReport;
        this.tradingRecord = tradingRecord;
        this.strategy = strategy;
    }

    /**
     * Constructs a TradingStatement with the specified strategy, position
     * statistics report, and performance report. The trading record is set to null
     * by default.
     *
     * @param strategy            the trading strategy used to generate the
     *                            statement
     * @param positionStatsReport the report containing position-related statistics
     * @param performanceReport   the report containing performance metrics
     */
    public TradingStatement(Strategy strategy, PositionStatsReport positionStatsReport,
            PerformanceReport performanceReport) {
        this(strategy, null, positionStatsReport, performanceReport);
    }

    /**
     * @return {@link #strategy}
     */
    public Strategy getStrategy() {
        return strategy;
    }

    /**
     * @return {@link #positionStatsReport}
     */
    public PositionStatsReport getPositionStatsReport() {
        return positionStatsReport;
    }

    /**
     * @return {@link #performanceReport}
     */
    public PerformanceReport getPerformanceReport() {
        return performanceReport;
    }

    /**
     * @return {@link #tradingRecord}
     */
    public TradingRecord getTradingRecord() {
        return tradingRecord;
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}

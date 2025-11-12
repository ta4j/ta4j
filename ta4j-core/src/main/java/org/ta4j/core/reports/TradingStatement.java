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

import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;

/**
 * Represents a trading statement that provides access to position statistics,
 * performance metrics, trading records, and the strategy used during trading.
 * This interface defines methods to retrieve key components of a trading
 * statement for analysis and reporting purposes.
 *
 * @since 0.19
 */
public interface TradingStatement {

    /**
     * Returns the position statistics report.
     *
     * @return the position statistics report
     */
    public PositionStatsReport getPositionStatsReport();

    /**
     * Returns the performance report.
     *
     * @return the performance report
     */
    public BasePerformanceReport getPerformanceReport();

    /**
     * Returns the trading record.
     *
     * @return the trading record, or null if not provided
     */
    public TradingRecord getTradingRecord();

    /**
     * Returns the trading strategy.
     *
     * @return the strategy used to generate this statement
     */
    public Strategy getStrategy();
}

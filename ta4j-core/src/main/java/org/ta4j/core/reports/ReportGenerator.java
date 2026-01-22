/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.reports;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;

/**
 * Generic interface for generating trading reports.
 *
 * @param <T> type of report to be generated
 */
public interface ReportGenerator<T> {

    /**
     * Generates a report based on the {@code tradingRecord}.
     *
     * @param tradingRecord the trading record (not null)
     * @param series        the bar series (not null)
     * @return generated report
     */
    T generate(Strategy strategy, TradingRecord tradingRecord, BarSeries series);
}

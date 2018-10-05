package org.ta4j.core.tradereport;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.TradingRecord;

/**
 * Generic interface for building trade reports
 *
 * @param <T> type of report to be build
 */
public interface ReportBuilder<T> {

    /**
     * Builds report
     *
     * @param tradingRecord a trading record which is a source to generate report, not null
     * @param series        a time series, not null
     * @return built report
     */
    T buildReport(TradingRecord tradingRecord, TimeSeries series);
}

package org.ta4j.core.tradereport;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.TradingRecord;

/**
 * Generic interface for generating trade reports
 *
 * @param <T> type of report to be generated
 */
public interface ReportGenerator<T> {

    /**
     * Generate report
     *
     * @param tradingRecord a trading record which is a source to generate report, not null
     * @param series        a time series, not null
     * @return generated report
     */
    T generate(TradingRecord tradingRecord, TimeSeries series);
}

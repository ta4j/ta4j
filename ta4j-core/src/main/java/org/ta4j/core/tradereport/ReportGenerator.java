package org.ta4j.core.tradereport;

import org.ta4j.core.TradingRecord;
import org.ta4j.core.BarSeries;

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
     * @param series        a bar series, not null
     * @return generated report
     */
    T generate(TradingRecord tradingRecord, BarSeries series);
}

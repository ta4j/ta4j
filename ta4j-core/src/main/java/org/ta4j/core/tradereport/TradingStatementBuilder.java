package org.ta4j.core.tradereport;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.TradingRecord;

/**
 * This class builds TradingStatement basis on provided trading report and time series
 *
 * @see TradingStatement
 */
public class TradingStatementBuilder implements ReportBuilder<TradingStatement> {

    private final PerformanceReportBuilder performanceReportBuilder = new PerformanceReportBuilder();
    private final TradeStatsReportBuilder tradeStatsReportBuilder = new TradeStatsReportBuilder();

    @Override
    public TradingStatement buildReport(TradingRecord tradingRecord, TimeSeries series) {
        final PerformanceReport performanceReport = performanceReportBuilder.buildReport(tradingRecord, series);
        final TradeStatsReport tradeStatsReport = tradeStatsReportBuilder.buildReport(tradingRecord, series);
        return new TradingStatement(tradeStatsReport, performanceReport);
    }
}

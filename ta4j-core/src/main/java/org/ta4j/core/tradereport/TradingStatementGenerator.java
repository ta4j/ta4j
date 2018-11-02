package org.ta4j.core.tradereport;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.TradingRecord;

/**
 * This class generates TradingStatement basis on provided trading report and time series
 *
 * @see TradingStatement
 */
public class TradingStatementGenerator implements ReportGenerator<TradingStatement> {

    private final PerformanceReportGenerator performanceReportGenerator = new PerformanceReportGenerator();
    private final TradeStatsReportGenerator tradeStatsReportGenerator = new TradeStatsReportGenerator();

    @Override
    public TradingStatement generate(TradingRecord tradingRecord, TimeSeries series) {
        final PerformanceReport performanceReport = performanceReportGenerator.generate(tradingRecord, series);
        final TradeStatsReport tradeStatsReport = tradeStatsReportGenerator.generate(tradingRecord, series);
        return new TradingStatement(tradeStatsReport, performanceReport);
    }
}

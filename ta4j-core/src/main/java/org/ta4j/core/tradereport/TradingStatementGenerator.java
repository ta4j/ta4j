package org.ta4j.core.tradereport;

import org.ta4j.core.TradingRecord;
import org.ta4j.core.BarSeries;

/**
 * This class generates TradingStatement basis on provided trading report and bar series
 *
 * @see TradingStatement
 */
public class TradingStatementGenerator implements ReportGenerator<TradingStatement> {

    private final PerformanceReportGenerator performanceReportGenerator = new PerformanceReportGenerator();
    private final TradeStatsReportGenerator tradeStatsReportGenerator = new TradeStatsReportGenerator();

    @Override
    public TradingStatement generate(TradingRecord tradingRecord, BarSeries series) {
        final PerformanceReport performanceReport = performanceReportGenerator.generate(tradingRecord, series);
        final TradeStatsReport tradeStatsReport = tradeStatsReportGenerator.generate(tradingRecord, series);
        return new TradingStatement(tradeStatsReport, performanceReport);
    }
}

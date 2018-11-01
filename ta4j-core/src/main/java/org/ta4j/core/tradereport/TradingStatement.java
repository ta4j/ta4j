package org.ta4j.core.tradereport;

/**
 * This class represents trading statement report which contains trade and performance statistics
 */
public class TradingStatement {

    private final TradeStatsReport tradeStatsReport;
    private final PerformanceReport performanceReport;

    public TradingStatement(TradeStatsReport tradeStatsReport, PerformanceReport performanceReport) {
        this.tradeStatsReport = tradeStatsReport;
        this.performanceReport = performanceReport;
    }

    public TradeStatsReport getTradeStatsReport() {
        return tradeStatsReport;
    }

    public PerformanceReport getPerformanceReport() {
        return performanceReport;
    }
}

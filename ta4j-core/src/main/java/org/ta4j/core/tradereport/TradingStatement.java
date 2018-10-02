package org.ta4j.core.tradereport;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.TradingRecord;

public class TradingStatement {

    private final TradeStatsReport tradeStatsReport;
    private final PerformanceReport performanceReport;

    public TradingStatement(TradingRecord tradingRecord, TimeSeries series) {
        this.tradeStatsReport = new TradeStatsReport(tradingRecord, series);
        this.performanceReport = new PerformanceReport(tradingRecord, series);
    }

    public TradeStatsReport getTradeStatsReport() {
        return tradeStatsReport;
    }

    public PerformanceReport getPerformanceReport() {
        return performanceReport;
    }
}

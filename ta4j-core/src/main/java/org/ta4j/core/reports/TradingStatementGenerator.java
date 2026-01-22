/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.reports;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;

/**
 * Generates a {@link BaseTradingStatement} based on the provided trading record
 * and bar series.
 */
public record TradingStatementGenerator(PerformanceReportGenerator performanceReportGenerator,
        PositionStatsReportGenerator positionStatsReportGenerator) implements ReportGenerator<TradingStatement> {

    /**
     * Constructor with new {@link PerformanceReportGenerator} and new
     * {@link PositionStatsReportGenerator}.
     */
    public TradingStatementGenerator() {
        this(new PerformanceReportGenerator(), new PositionStatsReportGenerator());
    }

    /**
     * Constructor.
     *
     * @param performanceReportGenerator   the {@link PerformanceReportGenerator}
     * @param positionStatsReportGenerator the {@link PositionStatsReportGenerator}
     */
    public TradingStatementGenerator {
    }

    @Override
    public TradingStatement generate(Strategy strategy, TradingRecord tradingRecord, BarSeries series) {
        final BasePerformanceReport performanceReport = performanceReportGenerator.generate(strategy, tradingRecord,
                series);
        final PositionStatsReport positionStatsReport = positionStatsReportGenerator.generate(strategy, tradingRecord,
                series);
        return new BaseTradingStatement(strategy, tradingRecord, positionStatsReport, performanceReport);
    }
}

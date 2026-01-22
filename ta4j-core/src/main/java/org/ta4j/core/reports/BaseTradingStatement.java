/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.reports;

import com.google.gson.Gson;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Base implementation of {@link TradingStatement} containing position
 * statistics, performance metrics, trading record, and strategy.
 *
 * @since 0.19
 */
public class BaseTradingStatement implements TradingStatement {

    public final PositionStatsReport positionStatsReport;
    public final BasePerformanceReport performanceReport;
    public final TradingRecord tradingRecord;
    public final Strategy strategy;
    private final Map<AnalysisCriterion, Num> criterionScores;

    /**
     * Constructs a trading statement with strategy, trading record, position
     * statistics, and performance report.
     *
     * @param strategy            the trading strategy used to generate the
     *                            statement
     * @param tradingRecord       the record of all trading operations
     * @param positionStatsReport the report containing position-related statistics
     * @param performanceReport   the report containing performance metrics
     */
    public BaseTradingStatement(Strategy strategy, TradingRecord tradingRecord, PositionStatsReport positionStatsReport,
            BasePerformanceReport performanceReport) {
        this(strategy, tradingRecord, positionStatsReport, performanceReport, null);
    }

    /**
     * Constructs a trading statement with strategy, trading record, position
     * statistics, performance report, and optional criterion scores.
     *
     * @param strategy            the trading strategy used to generate the
     *                            statement
     * @param tradingRecord       the record of all trading operations
     * @param positionStatsReport the report containing position-related statistics
     * @param performanceReport   the report containing performance metrics
     * @param criterionScores     optional map of criterion scores (may be null or
     *                            empty)
     */
    public BaseTradingStatement(Strategy strategy, TradingRecord tradingRecord, PositionStatsReport positionStatsReport,
            BasePerformanceReport performanceReport, Map<AnalysisCriterion, Num> criterionScores) {
        this.positionStatsReport = positionStatsReport;
        this.performanceReport = performanceReport;
        this.tradingRecord = tradingRecord;
        this.strategy = strategy;
        this.criterionScores = criterionScores == null || criterionScores.isEmpty() ? Collections.emptyMap()
                : Collections.unmodifiableMap(new HashMap<>(criterionScores));
    }

    /**
     * Constructs a trading statement with strategy, position statistics, and
     * performance report. The trading record is set to null.
     *
     * @param strategy            the trading strategy used to generate the
     *                            statement
     * @param positionStatsReport the report containing position-related statistics
     * @param performanceReport   the report containing performance metrics
     */
    public BaseTradingStatement(Strategy strategy, PositionStatsReport positionStatsReport,
            BasePerformanceReport performanceReport) {
        this(strategy, null, positionStatsReport, performanceReport);
    }

    /**
     * Returns the trading strategy.
     *
     * @return the strategy used to generate this statement
     */
    public Strategy getStrategy() {
        return strategy;
    }

    /**
     * Returns the position statistics report.
     *
     * @return the position statistics report
     */
    @Override
    public PositionStatsReport getPositionStatsReport() {
        return positionStatsReport;
    }

    /**
     * Returns the performance report.
     *
     * @return the performance report
     */
    @Override
    public BasePerformanceReport getPerformanceReport() {
        return performanceReport;
    }

    /**
     * Returns the trading record.
     *
     * @return the trading record, or null if not provided
     */
    @Override
    public TradingRecord getTradingRecord() {
        return tradingRecord;
    }

    @Override
    public Map<AnalysisCriterion, Num> getCriterionScores() {
        return criterionScores;
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}

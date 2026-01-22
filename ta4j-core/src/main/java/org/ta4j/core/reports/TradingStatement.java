/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.reports;

import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Represents a trading statement that provides access to position statistics,
 * performance metrics, trading records, and the strategy used during trading.
 * This interface defines methods to retrieve key components of a trading
 * statement for analysis and reporting purposes.
 *
 * @since 0.19
 */
public interface TradingStatement {

    /**
     * Returns the position statistics report.
     *
     * @return the position statistics report
     */
    public PositionStatsReport getPositionStatsReport();

    /**
     * Returns the performance report.
     *
     * @return the performance report
     */
    public BasePerformanceReport getPerformanceReport();

    /**
     * Returns the trading record.
     *
     * @return the trading record, or null if not provided
     */
    public TradingRecord getTradingRecord();

    /**
     * Returns the trading strategy.
     *
     * @return the strategy used to generate this statement
     */
    public Strategy getStrategy();

    /**
     * Returns the criterion score for the specified analysis criterion, if
     * available.
     * <p>
     * Criterion scores are typically populated when using
     * {@link org.ta4j.core.backtest.BacktestExecutionResult#getTopStrategies(int, AnalysisCriterion...)}
     * or similar methods that calculate and store criterion values for ranking
     * purposes.
     *
     * @param criterion the analysis criterion to get the score for
     * @return an Optional containing the criterion score if available, empty
     *         otherwise
     * @since 0.19
     */
    default Optional<Num> getCriterionScore(AnalysisCriterion criterion) {
        Map<AnalysisCriterion, Num> scores = getCriterionScores();
        return Optional.ofNullable(scores.get(criterion));
    }

    /**
     * Returns an unmodifiable map of all stored criterion scores.
     * <p>
     * Criterion scores are typically populated when using
     * {@link org.ta4j.core.backtest.BacktestExecutionResult#getTopStrategies(int, AnalysisCriterion...)}
     * or similar methods that calculate and store criterion values for ranking
     * purposes.
     *
     * @return an unmodifiable map of criterion to score mappings, empty if no
     *         scores are stored
     * @since 0.19
     */
    default Map<AnalysisCriterion, Num> getCriterionScores() {
        return Collections.emptyMap();
    }
}

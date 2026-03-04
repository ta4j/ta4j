/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;
import org.ta4j.core.reports.TradingStatement;
import org.ta4j.core.walkforward.WalkForwardConfig;
import org.ta4j.core.walkforward.WalkForwardRuntimeReport;
import org.ta4j.core.walkforward.WalkForwardSplit;

/**
 * Wraps walk-forward execution output for one strategy.
 *
 * @param barSeries     series used for execution
 * @param strategy      evaluated strategy
 * @param config        walk-forward configuration
 * @param folds         fold-level execution results
 * @param runtimeReport aggregate runtime report across folds
 * @since 0.22.4
 */
public record StrategyWalkForwardExecutionResult(BarSeries barSeries, Strategy strategy, WalkForwardConfig config,
        List<FoldResult> folds, WalkForwardRuntimeReport runtimeReport) {

    /**
     * Creates a validated result.
     *
     * @param barSeries     series used for execution
     * @param strategy      evaluated strategy
     * @param config        walk-forward configuration
     * @param folds         fold-level execution results
     * @param runtimeReport aggregate runtime report across folds
     * @since 0.22.4
     */
    public StrategyWalkForwardExecutionResult {
        barSeries = Objects.requireNonNull(barSeries, "barSeries");
        strategy = Objects.requireNonNull(strategy, "strategy");
        config = Objects.requireNonNull(config, "config");
        folds = List.copyOf(Objects.requireNonNull(folds, "folds"));
        runtimeReport = Objects.requireNonNull(runtimeReport, "runtimeReport");
    }

    /**
     * @return the optional holdout fold execution result
     * @since 0.22.4
     */
    public Optional<FoldResult> holdoutFold() {
        return folds.stream().filter(fold -> fold.split().holdout()).findFirst();
    }

    /**
     * @return all non-holdout folds
     * @since 0.22.4
     */
    public List<FoldResult> inSampleFolds() {
        return folds.stream().filter(fold -> !fold.split().holdout()).toList();
    }

    /**
     * @return all holdout folds
     * @since 0.22.4
     */
    public List<FoldResult> outOfSampleFolds() {
        return folds.stream().filter(fold -> fold.split().holdout()).toList();
    }

    /**
     * @return fold trading statements in execution order
     * @since 0.22.4
     */
    public List<TradingStatement> tradingStatements() {
        return folds.stream().map(FoldResult::tradingStatement).toList();
    }

    /**
     * Fold-level walk-forward execution output.
     *
     * @param split            fold boundary metadata
     * @param tradingStatement generated trading statement for the fold's test
     *                         window
     * @param runtime          fold runtime duration
     * @since 0.22.4
     */
    public record FoldResult(WalkForwardSplit split, TradingStatement tradingStatement, Duration runtime) {

        /**
         * Creates a validated fold result.
         *
         * @param split            fold boundary metadata
         * @param tradingStatement generated trading statement for the fold
         * @param runtime          fold runtime duration
         * @since 0.22.4
         */
        public FoldResult {
            split = Objects.requireNonNull(split, "split");
            tradingStatement = Objects.requireNonNull(tradingStatement, "tradingStatement");
            runtime = Objects.requireNonNull(runtime, "runtime");
            if (runtime.isNegative()) {
                throw new IllegalArgumentException("runtime must be >= 0");
            }
        }
    }
}

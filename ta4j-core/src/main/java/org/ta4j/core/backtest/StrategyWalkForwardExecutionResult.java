/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;
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
        List<FoldResult> folds,
        WalkForwardRuntimeReport runtimeReport) implements TradingStatementExecutionResult<WalkForwardRuntimeReport> {

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
    @Override
    public List<TradingStatement> tradingStatements() {
        return folds.stream().map(FoldResult::tradingStatement).toList();
    }

    /**
     * @return fold trading records in execution order
     * @since 0.22.4
     */
    @Override
    public List<TradingRecord> tradingRecords() {
        return TradingStatementExecutionResult.super.tradingRecords();
    }

    /**
     * Evaluates one criterion for every fold.
     *
     * @param criterion analysis criterion
     * @return criterion values in fold execution order
     * @since 0.22.4
     */
    @Override
    public List<Num> criterionValues(AnalysisCriterion criterion) {
        return TradingStatementExecutionResult.super.criterionValues(criterion);
    }

    /**
     * Evaluates one criterion for every in-sample fold.
     *
     * @param criterion analysis criterion
     * @return criterion values in fold execution order
     * @since 0.22.4
     */
    public List<Num> inSampleCriterionValues(AnalysisCriterion criterion) {
        return criterionValuesFor(criterion, inSampleFolds());
    }

    /**
     * Evaluates one criterion for every out-of-sample fold.
     *
     * @param criterion analysis criterion
     * @return criterion values in fold execution order
     * @since 0.22.4
     */
    public List<Num> outOfSampleCriterionValues(AnalysisCriterion criterion) {
        return criterionValuesFor(criterion, outOfSampleFolds());
    }

    /**
     * Evaluates one criterion for the holdout fold when present.
     *
     * @param criterion analysis criterion
     * @return optional criterion value for the holdout fold
     * @since 0.22.4
     */
    public Optional<Num> holdoutCriterionValue(AnalysisCriterion criterion) {
        Objects.requireNonNull(criterion, "criterion");
        return holdoutFold().map(fold -> criterion.calculate(barSeries, fold.tradingRecord()));
    }

    /**
     * Evaluates one criterion and returns values keyed by fold id.
     *
     * @param criterion analysis criterion
     * @return ordered fold-id to criterion value map
     * @since 0.22.4
     */
    public Map<String, Num> criterionValuesByFold(AnalysisCriterion criterion) {
        Objects.requireNonNull(criterion, "criterion");
        Map<String, Num> values = new LinkedHashMap<>();
        for (FoldResult fold : folds) {
            values.put(fold.split().foldId(), criterion.calculate(barSeries, fold.tradingRecord()));
        }
        return Collections.unmodifiableMap(values);
    }

    private List<Num> criterionValuesFor(AnalysisCriterion criterion, List<FoldResult> selectedFolds) {
        Objects.requireNonNull(criterion, "criterion");
        return selectedFolds.stream().map(fold -> criterion.calculate(barSeries, fold.tradingRecord())).toList();
    }

    /**
     * Fold-level walk-forward execution output.
     *
     * @param split            fold boundary metadata
     * @param tradingRecord    generated trading record for the fold's test window
     * @param tradingStatement generated trading statement for the fold's test
     *                         window
     * @param runtime          fold runtime duration
     * @since 0.22.4
     */
    public record FoldResult(WalkForwardSplit split, TradingRecord tradingRecord, TradingStatement tradingStatement,
            Duration runtime) {

        /**
         * Creates a validated fold result.
         *
         * @param split            fold boundary metadata
         * @param tradingRecord    generated trading record for the fold
         * @param tradingStatement generated trading statement for the fold
         * @param runtime          fold runtime duration
         * @since 0.22.4
         */
        public FoldResult {
            split = Objects.requireNonNull(split, "split");
            tradingRecord = Objects.requireNonNull(tradingRecord, "tradingRecord");
            tradingStatement = Objects.requireNonNull(tradingStatement, "tradingStatement");
            runtime = Objects.requireNonNull(runtime, "runtime");
            if (runtime.isNegative()) {
                throw new IllegalArgumentException("runtime must be >= 0");
            }
        }
    }
}

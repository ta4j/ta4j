/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
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
import org.ta4j.core.walkforward.WalkForwardRunResult;
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
 * @param foldFailures  per-fold execution failures encountered during the run;
 *                      healthy folds remain fully represented in {@code folds}
 * @since 0.22.4
 */
public record StrategyWalkForwardExecutionResult(BarSeries barSeries, Strategy strategy, WalkForwardConfig config,
        List<FoldResult> folds, WalkForwardRuntimeReport runtimeReport,
        List<WalkForwardRunResult.FoldFailure> foldFailures)
        implements
            TradingStatementExecutionResult<WalkForwardRuntimeReport> {

    /**
     * Creates a validated result with no recorded fold failures.
     *
     * @param barSeries     series used for execution
     * @param strategy      evaluated strategy
     * @param config        walk-forward configuration
     * @param folds         fold-level execution results
     * @param runtimeReport aggregate runtime report across folds
     * @since 0.22.4
     */
    public StrategyWalkForwardExecutionResult(BarSeries barSeries, Strategy strategy, WalkForwardConfig config,
            List<FoldResult> folds, WalkForwardRuntimeReport runtimeReport) {
        this(barSeries, strategy, config, folds, runtimeReport, List.of());
    }

    /**
     * Creates a validated result.
     *
     * @param barSeries     series used for execution
     * @param strategy      evaluated strategy
     * @param config        walk-forward configuration
     * @param folds         fold-level execution results
     * @param runtimeReport aggregate runtime report across folds
     * @param foldFailures  per-fold execution failures encountered during the run
     * @since 0.22.4
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "The result borrows the caller's live series so "
            + "fold criteria evaluate the same instance execution observed; folds own all derived state.")
    public StrategyWalkForwardExecutionResult {
        Objects.requireNonNull(barSeries, "barSeries must not be null");
        strategy = StrategySnapshots.copy(strategy);
        config = Objects.requireNonNull(config, "config");
        folds = List.copyOf(Objects.requireNonNull(folds, "folds"));
        runtimeReport = Objects.requireNonNull(runtimeReport, "runtimeReport");
        foldFailures = foldFailures == null ? List.of() : List.copyOf(foldFailures);
    }

    @Override
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "Returns the borrowed caller series by contract.")
    public BarSeries barSeries() {
        return barSeries;
    }

    @Override
    public Strategy strategy() {
        return StrategySnapshots.copy(strategy);
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

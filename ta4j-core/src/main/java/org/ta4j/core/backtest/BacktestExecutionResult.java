/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.Duration;
import java.time.Instant;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.Bar;
import org.ta4j.core.BarBuilder;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.ConcurrentBarSeries;
import org.ta4j.core.Strategy;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.reports.BaseTradingStatement;
import org.ta4j.core.reports.TradingStatement;
import org.ta4j.core.serialization.DurationTypeAdapter;

import java.util.*;

/**
 * Wraps the outcome of a {@link BacktestExecutor} run including runtime
 * metrics.
 *
 * @since 0.19
 */
public record BacktestExecutionResult(BarSeries barSeries, List<TradingStatement> tradingStatements,
        BacktestRuntimeReport runtimeReport,
        List<StrategyFailure> strategyFailures) implements TradingStatementExecutionResult<BacktestRuntimeReport> {

    /**
     * Ensures properties are non-null and snapshots mutable input collections.
     *
     * @param barSeries         the bar series used for backtesting
     * @param tradingStatements successful trading statements in the order of the
     *                          supplied strategies
     * @param runtimeReport     runtime statistics for the execution
     * @param strategyFailures  strategies skipped because execution failed
     */
    public BacktestExecutionResult {
        barSeries = snapshotSeries(Objects.requireNonNull(barSeries, "barSeries must not be null"));
        tradingStatements = List
                .copyOf(Objects.requireNonNull(tradingStatements, "tradingStatements must not be null"));
        runtimeReport = Objects.requireNonNull(runtimeReport, "runtimeReport must not be null");
        strategyFailures = List.copyOf(Objects.requireNonNull(strategyFailures, "strategyFailures must not be null"));
    }

    static BarSeries snapshot(BarSeries source) {
        return snapshotSeries(Objects.requireNonNull(source, "source must not be null"));
    }

    static BacktestExecutionResult capture(BarSeries source, List<TradingStatement> tradingStatements,
            BacktestRuntimeReport runtimeReport, List<StrategyFailure> strategyFailures, BarSeries baseline) {
        if (!(baseline instanceof FrozenBarSeries frozenBaseline)) {
            throw new IllegalArgumentException("baseline must be a frozen result series");
        }
        if (source instanceof ConcurrentBarSeries concurrent) {
            return concurrent.withReadLock(
                    () -> captureStable(source, tradingStatements, runtimeReport, strategyFailures, frozenBaseline));
        }
        return captureStable(source, tradingStatements, runtimeReport, strategyFailures, frozenBaseline);
    }

    private static BacktestExecutionResult captureStable(BarSeries source, List<TradingStatement> tradingStatements,
            BacktestRuntimeReport runtimeReport, List<StrategyFailure> strategyFailures, FrozenBarSeries baseline) {
        if (!baseline.matches(source)) {
            throw new IllegalStateException("Bar series changed during backtest; result ownership is ambiguous");
        }
        return new BacktestExecutionResult(baseline, tradingStatements, runtimeReport, strategyFailures);
    }

    private static BarSeries snapshotSeries(BarSeries source) {
        if (source instanceof FrozenBarSeries) {
            return source;
        }
        if (source instanceof ConcurrentBarSeries concurrent) {
            return concurrent.withReadLock(() -> snapshotSeriesUnlocked(source));
        }
        return snapshotSeriesUnlocked(source);
    }

    private static BarSeries snapshotSeriesUnlocked(BarSeries source) {
        List<Bar> frozenBars = source.getBarData().stream().<Bar>map(ImmutableBar::new).toList();
        return new FrozenBarSeries(source, frozenBars);
    }

    private static final class FrozenBarSeries implements BarSeries {
        private final String name;
        private final NumFactory numFactory;
        private final List<Bar> bars;
        private final int beginIndex;
        private final int endIndex;
        private final int removedBarsCount;
        private final int maximumBarCount;
        private final long revision;

        private FrozenBarSeries(BarSeries source, List<Bar> bars) {
            this.name = source.getName();
            this.numFactory = source.numFactory();
            this.bars = List.copyOf(bars);
            this.beginIndex = source.getBeginIndex();
            this.endIndex = source.getEndIndex();
            this.removedBarsCount = source.getRemovedBarsCount();
            this.maximumBarCount = source.getMaximumBarCount();
            this.revision = source.getBarHistoryRevision();
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public BarBuilder barBuilder() {
            throw new UnsupportedOperationException("Result bar series is immutable");
        }

        @Override
        public NumFactory numFactory() {
            return numFactory;
        }

        private boolean matches(BarSeries source) {
            if (!Objects.equals(name, source.getName()) || beginIndex != source.getBeginIndex()
                    || endIndex != source.getEndIndex() || removedBarsCount != source.getRemovedBarsCount()
                    || maximumBarCount != source.getMaximumBarCount() || revision != source.getBarHistoryRevision()
                    || getBarCount() != source.getBarCount()) {
                return false;
            }
            List<Bar> sourceBars = source.getBarData();
            if (bars.size() != sourceBars.size()) {
                return false;
            }
            for (int i = 0; i < bars.size(); i++) {
                if (!sameBar(bars.get(i), sourceBars.get(i))) {
                    return false;
                }
            }
            return true;
        }

        private static boolean sameBar(Bar left, Bar right) {
            return Objects.equals(left.getTimePeriod(), right.getTimePeriod())
                    && Objects.equals(left.getBeginTime(), right.getBeginTime())
                    && Objects.equals(left.getEndTime(), right.getEndTime())
                    && Objects.equals(left.getOpenPrice(), right.getOpenPrice())
                    && Objects.equals(left.getHighPrice(), right.getHighPrice())
                    && Objects.equals(left.getLowPrice(), right.getLowPrice())
                    && Objects.equals(left.getClosePrice(), right.getClosePrice())
                    && Objects.equals(left.getVolume(), right.getVolume())
                    && Objects.equals(left.getAmount(), right.getAmount()) && left.getTrades() == right.getTrades();
        }

        @Override
        public Bar getBar(int index) {
            long position = (long) index - removedBarsCount;
            if (position < 0) {
                if (index < 0 || bars.isEmpty()) {
                    throw new IndexOutOfBoundsException("Index is outside the frozen result series: " + index);
                }
                position = 0;
            }
            if (position >= bars.size()) {
                throw new IndexOutOfBoundsException("Index is outside the frozen result series: " + index);
            }
            return bars.get((int) position);
        }

        @Override
        public int getBarCount() {
            if (endIndex < 0) {
                return 0;
            }
            return endIndex - Math.max(removedBarsCount, beginIndex) + 1;
        }

        @Override
        public List<Bar> getBarData() {
            return bars;
        }

        @Override
        public long getBarHistoryRevision() {
            return revision;
        }

        @Override
        public BarSeriesChangeSnapshot getBarSeriesChangeSnapshot(long sinceRevision) {
            return new BarSeriesChangeSnapshot(revision, -1, removedBarsCount - 1, maximumBarCount, endIndex);
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException("Result bar series is immutable");
        }

        @Override
        public int getBeginIndex() {
            return beginIndex;
        }

        @Override
        public int getEndIndex() {
            return endIndex;
        }

        @Override
        public int getMaximumBarCount() {
            return maximumBarCount;
        }

        @Override
        public void setMaximumBarCount(int maximumBarCount) {
            throw new UnsupportedOperationException("Result bar series is immutable");
        }

        @Override
        public int getRemovedBarsCount() {
            return removedBarsCount;
        }

        @Override
        public void addBar(Bar bar, boolean replace) {
            throw new UnsupportedOperationException("Result bar series is immutable");
        }

        @Override
        public void addTrade(Num tradeVolume, Num tradePrice) {
            throw new UnsupportedOperationException("Result bar series is immutable");
        }

        @Override
        public void addPrice(Num price) {
            throw new UnsupportedOperationException("Result bar series is immutable");
        }

        @Override
        public BarSeries getSubSeries(int startIndex, int endIndex) {
            if (startIndex < 0 || startIndex >= endIndex) {
                throw new IllegalArgumentException("Subseries requires 0 <= startIndex < endIndex");
            }
            int retainedStart = Math.max(startIndex, beginIndex);
            int from = Math.min(bars.size(), Math.max(0, retainedStart - removedBarsCount));
            int to = (int) Math.max(from,
                    Math.min(bars.size(), Math.min((long) endIndex, (long) this.endIndex + 1) - removedBarsCount));
            List<Bar> selected = bars.subList(from, to);
            BarSeries view = new BaseBarSeriesBuilder().withName(name)
                    .withNumFactory(numFactory)
                    .withMaxBarCount(maximumBarCount)
                    .withBeginIndex(removedBarsCount > 0 ? retainedStart : 0)
                    .withBars(selected)
                    .build();
            return new FrozenBarSeries(view, selected);
        }
    }

    private static final class ImmutableBar implements Bar {
        private final Duration timePeriod;
        private final Instant beginTime;
        private final Instant endTime;
        private final Num openPrice;
        private final Num highPrice;
        private final Num lowPrice;
        private final Num closePrice;
        private final Num volume;
        private final Num amount;
        private final long trades;

        private ImmutableBar(Bar source) {
            this.timePeriod = source.getTimePeriod();
            this.beginTime = source.getBeginTime();
            this.endTime = source.getEndTime();
            this.openPrice = source.getOpenPrice();
            this.highPrice = source.getHighPrice();
            this.lowPrice = source.getLowPrice();
            this.closePrice = source.getClosePrice();
            this.volume = source.getVolume();
            this.amount = source.getAmount();
            this.trades = source.getTrades();
        }

        @Override
        public Duration getTimePeriod() {
            return timePeriod;
        }

        @Override
        public Instant getBeginTime() {
            return beginTime;
        }

        @Override
        public Instant getEndTime() {
            return endTime;
        }

        @Override
        public Num getOpenPrice() {
            return openPrice;
        }

        @Override
        public Num getHighPrice() {
            return highPrice;
        }

        @Override
        public Num getLowPrice() {
            return lowPrice;
        }

        @Override
        public Num getClosePrice() {
            return closePrice;
        }

        @Override
        public Num getVolume() {
            return volume;
        }

        @Override
        public Num getAmount() {
            return amount;
        }

        @Override
        public long getTrades() {
            return trades;
        }

        @Override
        public void addTrade(Num tradeVolume, Num tradePrice) {
            throw new UnsupportedOperationException("Result bars are immutable");
        }

        @Override
        public void addPrice(Num price) {
            throw new UnsupportedOperationException("Result bars are immutable");
        }
    }

    /**
     * Creates a result with no recorded strategy failures.
     *
     * @param barSeries         the bar series used for backtesting
     * @param tradingStatements successful trading statements
     * @param runtimeReport     runtime statistics for the execution
     */
    public BacktestExecutionResult(BarSeries barSeries, List<TradingStatement> tradingStatements,
            BacktestRuntimeReport runtimeReport) {
        this(barSeries, tradingStatements, runtimeReport, List.of());
    }

    /**
     * Describes a strategy that was skipped because its execution failed.
     *
     * @param strategy the strategy that failed
     * @param cause    the exception thrown while evaluating the strategy
     * @since 0.24.2
     */
    public record StrategyFailure(Strategy strategy, RuntimeException cause) {

        /**
         * Ensures the failure description is complete.
         *
         * @param strategy the strategy that failed
         * @param cause    the exception thrown while evaluating the strategy
         * @since 0.24.2
         */
        @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "StrategyFailure intentionally captures the "
                + "original strategy reference and exception for fail-loud backtest diagnostics; the strategy is "
                + "identified by reference and exceptions are never mutated after capture")
        public StrategyFailure {
            Objects.requireNonNull(strategy, "strategy must not be null");
            Objects.requireNonNull(cause, "cause must not be null");
        }

        /**
         * @return the strategy that failed
         * @since 0.24.2
         */
        @Override
        @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "StrategyFailure intentionally exposes the "
                + "original strategy reference for fail-loud backtest diagnostics")
        public Strategy strategy() {
            return strategy;
        }

        /**
         * @return the exception thrown while evaluating the strategy
         * @since 0.24.2
         */
        @Override
        @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "StrategyFailure intentionally exposes the "
                + "original exception for fail-loud backtest diagnostics; exceptions are never mutated after capture")
        public RuntimeException cause() {
            return cause;
        }
    }

    @Override
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "Returns the borrowed caller series by contract.")
    public BarSeries barSeries() {
        return barSeries;
    }

    @Override
    public List<TradingStatement> tradingStatements() {
        return List.copyOf(tradingStatements);
    }

    /**
     * Returns the top strategies sorted by the provided analysis criteria in order
     * of importance.
     * <p>
     * This method preserves the legacy lexicographic behavior where the first
     * criterion is primary and later criteria are tie-breakers. For weighted and
     * normalized ranking, use
     * {@link #getTopStrategiesWeighted(int, RankingProfile)} or
     * {@link #getTopStrategiesWeighted(int, TradingStatementExecutionResult.WeightedCriterion...)}.
     * </p>
     *
     * @param limit    the maximum number of strategies to return
     * @param criteria the analysis criteria to sort by, in order of importance
     *                 (first criterion is primary, second breaks ties, etc.)
     * @return a list of the top trading statements sorted by the criteria
     * @throws NullPointerException     if criteria is null
     * @throws IllegalArgumentException if criteria is empty or limit is negative
     * @since 0.19
     */
    public List<TradingStatement> getTopStrategies(int limit, AnalysisCriterion... criteria) {
        Objects.requireNonNull(criteria, "criteria must not be null");
        if (criteria.length == 0) {
            throw new IllegalArgumentException("At least one criterion must be provided");
        }
        if (limit < 0) {
            throw new IllegalArgumentException("limit must not be negative");
        }

        return getTopStrategies(limit, Arrays.asList(criteria));
    }

    /**
     * Returns the top strategies sorted by the provided analysis criteria in order
     * of importance.
     * <p>
     * This method preserves the legacy lexicographic behavior where the first
     * criterion is primary and later criteria are tie-breakers. For weighted and
     * normalized ranking, use
     * {@link #getTopStrategiesWeighted(int, RankingProfile)} or
     * {@link #getTopStrategiesWeighted(int, TradingStatementExecutionResult.WeightedCriterion...)}.
     * </p>
     * <p>
     * Performance: Uses a hybrid approach that selects the optimal algorithm based
     * on the limit size relative to the total number of strategies. For small
     * limits ({@literal <} 25% of total), uses a heap-based partial sort O(n log
     * k). For larger limits, uses a full sort O(n log n) which is more
     * cache-friendly.
     *
     * @param limit    the maximum number of strategies to return
     * @param criteria the analysis criteria to sort by, in order of importance
     *                 (first criterion is primary, second breaks ties, etc.)
     * @return a list of the top trading statements sorted by the criteria
     * @throws NullPointerException     if criteria is null
     * @throws IllegalArgumentException if criteria is empty or limit is negative
     * @since 0.19
     */
    public List<TradingStatement> getTopStrategies(int limit, List<AnalysisCriterion> criteria) {
        Objects.requireNonNull(criteria, "criteria must not be null");
        if (criteria.isEmpty()) {
            throw new IllegalArgumentException("At least one criterion must be provided");
        }
        if (limit < 0) {
            throw new IllegalArgumentException("limit must not be negative");
        }

        // Early returns for edge cases
        if (limit == 0 || tradingStatements.isEmpty()) {
            return Collections.emptyList();
        }

        int effectiveLimit = Math.min(limit, tradingStatements.size());

        // Pre-calculate criterion values for all statements using IdentityHashMap
        // (faster than HashMap for object identity)
        Map<TradingStatement, List<Num>> criterionValuesMap = new IdentityHashMap<>(tradingStatements.size());
        Map<TradingStatement, Map<AnalysisCriterion, Num>> criterionScoresMap = new IdentityHashMap<>(
                tradingStatements.size());
        for (TradingStatement statement : tradingStatements) {
            List<Num> values = new ArrayList<>(criteria.size());
            Map<AnalysisCriterion, Num> scores = new HashMap<>(criteria.size());
            for (AnalysisCriterion criterion : criteria) {
                Num value = criterion.calculate(barSeries, statement.getTradingRecord());
                values.add(value);
                scores.put(criterion, value);
            }
            criterionValuesMap.put(statement, values);
            criterionScoresMap.put(statement, scores);
        }

        Comparator<TradingStatement> comparator = createComparator(criteria, criterionValuesMap);

        // Use heap-based partial sort for small limits (more efficient O(n log k))
        // Use full sort for large limits (more cache-friendly)
        List<TradingStatement> topStatements;
        if (effectiveLimit < tradingStatements.size() / 4) {
            topStatements = selectTopKWithHeap(tradingStatements, effectiveLimit, comparator);
        } else {
            topStatements = selectTopKWithSort(tradingStatements, effectiveLimit, comparator);
        }

        // Attach criterion scores to the returned statements
        return attachCriterionScores(topStatements, criterionScoresMap);
    }

    /**
     * Returns the top strategies using weighted, normalized criterion ranking.
     * <p>
     * Multipliers are normalized internally so any positive scale is accepted (for
     * example {@code 1.5/1.1/0.8} and {@code 15/11/8} produce equivalent weight
     * proportions).
     * </p>
     *
     * @param limit   the maximum number of strategies to return
     * @param profile weighted ranking profile
     * @return the top trading statements ordered by composite weighted score
     * @throws NullPointerException     if profile is null
     * @throws IllegalArgumentException if limit is negative
     * @since 0.22.4
     */
    public List<TradingStatement> getTopStrategiesWeighted(int limit, RankingProfile profile) {
        if (limit < 0) {
            throw new IllegalArgumentException("limit must not be negative");
        }
        if (limit == 0 || tradingStatements.isEmpty()) {
            return Collections.emptyList();
        }

        List<RankedTradingStatement> rankedStatements = rankTradingStatements(profile);
        return attachRankedCriterionScores(rankedStatements, limit);
    }

    /**
     * Returns the top strategies using weighted, normalized criterion ranking with
     * the default normalizer and missing-value policy.
     *
     * <p>
     * This overload is the shortest path for weighted ranking when callers already
     * know their criteria and relative weights.
     * </p>
     *
     * @param limit    the maximum number of strategies to return
     * @param criteria weighted criteria to normalize and combine
     * @return the top trading statements ordered by composite weighted score
     * @throws NullPointerException     if criteria is null
     * @throws IllegalArgumentException if limit is negative
     * @since 0.22.4
     */
    public List<TradingStatement> getTopStrategiesWeighted(int limit,
            TradingStatementExecutionResult.WeightedCriterion... criteria) {
        return getTopStrategiesWeighted(limit, RankingProfile.weighted(criteria));
    }

    /**
     * Attaches criterion scores to trading statements by creating new
     * BaseTradingStatement instances with the scores included.
     *
     * @param statements         the trading statements to attach scores to
     * @param criterionScoresMap map of statement to criterion scores
     * @return list of trading statements with criterion scores attached
     */
    private List<TradingStatement> attachCriterionScores(List<TradingStatement> statements,
            Map<TradingStatement, Map<AnalysisCriterion, Num>> criterionScoresMap) {
        List<TradingStatement> result = new ArrayList<>(statements.size());
        for (TradingStatement statement : statements) {
            Map<AnalysisCriterion, Num> scores = criterionScoresMap.get(statement);
            result.add(attachCriterionScores(statement, scores));
        }
        return result;
    }

    private List<TradingStatement> attachRankedCriterionScores(List<RankedTradingStatement> rankedStatements,
            int limit) {
        int effectiveLimit = Math.min(limit, rankedStatements.size());
        List<TradingStatement> statementsWithScores = new ArrayList<>(effectiveLimit);
        for (int i = 0; i < effectiveLimit; i++) {
            RankedTradingStatement rankedStatement = rankedStatements.get(i);
            statementsWithScores.add(attachCriterionScores(rankedStatement.statement(), rankedStatement.rawScores()));
        }
        return statementsWithScores;
    }

    private TradingStatement attachCriterionScores(TradingStatement statement, Map<AnalysisCriterion, Num> scores) {
        if (statement instanceof BaseTradingStatement && scores != null && !scores.isEmpty()) {
            BaseTradingStatement baseStatement = (BaseTradingStatement) statement;
            return new BaseTradingStatement(baseStatement.strategy, baseStatement.tradingRecord,
                    baseStatement.positionStatsReport, baseStatement.performanceReport, scores);
        }
        return statement;
    }

    /**
     * Creates a comparator that sorts trading statements by multiple criteria in
     * order of importance.
     *
     * @param criteria           the analysis criteria to sort by
     * @param criterionValuesMap pre-calculated criterion values for each statement
     * @return a comparator for trading statements
     */
    private Comparator<TradingStatement> createComparator(List<AnalysisCriterion> criteria,
            Map<TradingStatement, List<Num>> criterionValuesMap) {
        return (statement1, statement2) -> {
            List<Num> values1 = criterionValuesMap.get(statement1);
            List<Num> values2 = criterionValuesMap.get(statement2);

            for (int i = 0; i < criteria.size(); i++) {
                AnalysisCriterion criterion = criteria.get(i);
                Num value1 = values1.get(i);
                Num value2 = values2.get(i);

                // Use criterion's betterThan method to determine order
                if (criterion.betterThan(value1, value2)) {
                    return -1; // statement1 is better, should come first
                } else if (criterion.betterThan(value2, value1)) {
                    return 1; // statement2 is better, should come first
                }
                // If equal, continue to next criterion
            }
            return 0; // All criteria equal
        };
    }

    /**
     * Selects top k strategies using a min-heap (priority queue). Efficient for
     * small k relative to n: O(n log k).
     *
     * @param statements the trading statements to select from
     * @param k          the number of top strategies to return
     * @param comparator the comparator to determine ranking
     * @return a list of the top k trading statements
     */
    private List<TradingStatement> selectTopKWithHeap(List<TradingStatement> statements, int k,
            Comparator<TradingStatement> comparator) {
        // Use a min-heap with reversed comparator (so worst of the top-k is at root)
        PriorityQueue<TradingStatement> heap = new PriorityQueue<>(k + 1, comparator.reversed());

        for (TradingStatement statement : statements) {
            heap.offer(statement);
            if (heap.size() > k) {
                heap.poll(); // Remove the worst element from top-k
            }
        }

        // Extract results and sort them in correct order (best-first)
        List<TradingStatement> result = new ArrayList<>(heap);
        result.sort(comparator);
        return result;
    }

    /**
     * Selects top k strategies using full sort. Efficient for large k relative to
     * n: O(n log n).
     *
     * @param statements the trading statements to select from
     * @param k          the number of top strategies to return
     * @param comparator the comparator to determine ranking
     * @return a list of the top k trading statements
     */
    private List<TradingStatement> selectTopKWithSort(List<TradingStatement> statements, int k,
            Comparator<TradingStatement> comparator) {
        List<TradingStatement> sorted = new ArrayList<>(statements);
        sorted.sort(comparator);
        return sorted.subList(0, k);
    }

    @Override
    public String toString() {
        Gson gson = new GsonBuilder().registerTypeAdapter(Duration.class, new DurationTypeAdapter()).create();

        JsonObject json = new JsonObject();
        json.addProperty("barSeriesName", barSeries.getName());
        json.addProperty("tradingStatementsCount", tradingStatements.size());
        json.add("runtimeReport", JsonParser.parseString(runtimeReport.toString()).getAsJsonObject());
        return gson.toJson(json);
    }

}

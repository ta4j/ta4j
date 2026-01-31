/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.num.Num;
import org.ta4j.core.reports.BaseTradingStatement;
import org.ta4j.core.reports.TradingStatement;
import org.ta4j.core.serialization.DurationTypeAdapter;

import java.time.Duration;
import java.util.*;

/**
 * Wraps the outcome of a {@link BacktestExecutor} run including runtime
 * metrics.
 *
 * @since 0.19
 */
public record BacktestExecutionResult(BarSeries barSeries, List<TradingStatement> tradingStatements,
        BacktestRuntimeReport runtimeReport) {

    /**
     * Ensures properties are non-null.
     *
     * @param barSeries         the bar series used for backtesting
     * @param tradingStatements produced trading statements in the order of the
     *                          supplied strategies
     * @param runtimeReport     runtime statistics for the execution
     */
    public BacktestExecutionResult {
        barSeries = Objects.requireNonNull(barSeries, "barSeries must not be null");
        tradingStatements = Objects.requireNonNull(tradingStatements, "tradingStatements must not be null");
        runtimeReport = Objects.requireNonNull(runtimeReport, "runtimeReport must not be null");
    }

    /**
     * Returns the top strategies sorted by the provided analysis criteria in order
     * of importance.
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
            if (statement instanceof BaseTradingStatement && scores != null && !scores.isEmpty()) {
                // Create a new BaseTradingStatement with the criterion scores attached
                BaseTradingStatement baseStatement = (BaseTradingStatement) statement;
                BaseTradingStatement statementWithScores = new BaseTradingStatement(baseStatement.strategy,
                        baseStatement.tradingRecord, baseStatement.positionStatsReport, baseStatement.performanceReport,
                        scores);
                result.add(statementWithScores);
            } else {
                // If not a BaseTradingStatement or no scores, return as-is
                result.add(statement);
            }
        }
        return result;
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

/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.backtest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.num.Num;
import org.ta4j.core.reports.TradingStatement;
import org.ta4j.core.serialization.DurationTypeAdapter;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

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
     *
     * @param limit    the maximum number of strategies to return
     * @param criteria the analysis criteria to sort by, in order of importance
     *                 (first criterion is primary, second breaks ties, etc.)
     * @return a list of the top trading statements sorted by the criteria
     * @throws NullPointerException     if criteria is null
     * @throws IllegalArgumentException if criteria is empty or limit is negative
     */
    public List<TradingStatement> getTopStrategies(int limit, List<AnalysisCriterion> criteria) {
        Objects.requireNonNull(criteria, "criteria must not be null");
        if (criteria.isEmpty()) {
            throw new IllegalArgumentException("At least one criterion must be provided");
        }
        if (limit < 0) {
            throw new IllegalArgumentException("limit must not be negative");
        }

        // Pre-calculate criterion values for all statements to avoid recalculation
        // during sorting
        Map<TradingStatement, List<Num>> criterionValuesMap = new HashMap<>();
        for (TradingStatement statement : tradingStatements) {
            List<Num> values = new ArrayList<>(criteria.size());
            for (AnalysisCriterion criterion : criteria) {
                Num value = criterion.calculate(barSeries, statement.getTradingRecord());
                values.add(value);
            }
            criterionValuesMap.put(statement, values);
        }

        // Sort by criteria in order of importance
        Comparator<TradingStatement> comparator = (statement1, statement2) -> {
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

        return tradingStatements.stream().sorted(comparator).limit(limit).collect(Collectors.toList());
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

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
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import org.ta4j.core.Strategy;
import org.ta4j.core.serialization.DurationTypeAdapter;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Captures runtime statistics collected while executing strategies with the
 * {@link BacktestExecutor}.
 *
 * @since 0.19
 */
public record BacktestRuntimeReport(Duration overallRuntime, Duration minStrategyRuntime, Duration maxStrategyRuntime,
        Duration averageStrategyRuntime, Duration medianStrategyRuntime, List<StrategyRuntime> strategyRuntimes) {

    /**
     * Exclusion strategy for JSON serialization that excludes the strategyRuntimes
     * field.
     */
    private static final ExclusionStrategy STRATEGY_RUNTIMES_EXCLUSION = new ExclusionStrategy() {
        @Override
        public boolean shouldSkipField(FieldAttributes f) {
            return f.getName().equals("strategyRuntimes");
        }

        @Override
        public boolean shouldSkipClass(Class<?> clazz) {
            return false;
        }
    };

    /**
     * Constructor that ensures {@link #strategyRuntimes} is non-null.
     *
     * @param overallRuntime         total wall clock time to execute all strategies
     * @param minStrategyRuntime     minimum runtime measured for a single strategy
     * @param maxStrategyRuntime     maximum runtime measured for a single strategy
     * @param averageStrategyRuntime average runtime measured for the executed
     *                               strategies
     * @param medianStrategyRuntime  median runtime measured for the executed
     *                               strategies
     * @param strategyRuntimes       individual runtime per evaluated strategy
     */
    public BacktestRuntimeReport {
        strategyRuntimes = List.copyOf(Objects.requireNonNull(strategyRuntimes, "strategyRuntimes must not be null"));
    }

    /**
     * Creates an empty runtime report.
     *
     * @return runtime report with zeroed statistics
     * @since 0.19
     */
    public static BacktestRuntimeReport empty() {
        return new BacktestRuntimeReport(Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO,
                List.of());
    }

    /**
     * Provides the number of strategies that were evaluated.
     *
     * @return strategy runtime count
     * @since 0.19
     */
    public int strategyCount() {
        return strategyRuntimes.size();
    }

    /**
     * Returns a JSON string representation of this {@code BacktestRuntimeReport}
     * instance. The JSON format includes runtime statistics but excludes the
     * detailed strategy runtimes list.
     *
     * @return a JSON string representing the current state of this report
     */
    @Override
    public String toString() {
        Gson gson = new GsonBuilder().registerTypeAdapter(Duration.class, new DurationTypeAdapter())
                .setExclusionStrategies(STRATEGY_RUNTIMES_EXCLUSION)
                .create();
        return gson.toJson(this);
    }

    /**
     * Records the runtime for an individual strategy evaluation.
     *
     * @param strategy strategy instance that was evaluated
     * @param runtime  elapsed time for the evaluation
     * @since 0.19
     */
    public record StrategyRuntime(Strategy strategy, Duration runtime) {
    }
}

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

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.Strategy;

/**
 * Captures runtime statistics collected while executing strategies with the
 * {@link BacktestExecutor}.
 */
public record BacktestRuntimeReport(Duration overallRuntime, Duration minStrategyRuntime, Duration maxStrategyRuntime,
        Duration averageStrategyRuntime, Duration medianStrategyRuntime, List<StrategyRuntime> strategyRuntimes) {

    /**
     * Creates an empty runtime report.
     *
     * @return runtime report with zeroed statistics
     */
    public static BacktestRuntimeReport empty() {
        return new BacktestRuntimeReport(Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO,
                List.of());
    }

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
     * Provides the number of strategies that were evaluated.
     *
     * @return strategy runtime count
     */
    public int strategyCount() {
        return strategyRuntimes.size();
    }

    /**
     * Records the runtime for an individual strategy evaluation.
     *
     * @param strategy strategy instance that was evaluated
     * @param runtime  elapsed time for the evaluation
     */
    public record StrategyRuntime(Strategy strategy, Duration runtime) {
    }
}

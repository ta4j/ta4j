/*
 * SPDX-License-Identifier: MIT
 */
/**
 * Backtesting execution infrastructure.
 *
 * <p>
 * This package defines how strategies are executed over historical series and how
 * runs are ranked or compared.
 * </p>
 *
 * <p>
 * Choose by workload:
 * </p>
 * <ul>
 * <li>{@link org.ta4j.core.backtest.BarSeriesManager} for one strategy over one series</li>
 * <li>{@link org.ta4j.core.backtest.BacktestExecutor} for many strategies, telemetry, and ranking</li>
 * <li>{@link org.ta4j.core.backtest.TradeExecutionModel} implementations to align fill semantics
 * with your simulation assumptions</li>
 * </ul>
 */
package org.ta4j.core.backtest;

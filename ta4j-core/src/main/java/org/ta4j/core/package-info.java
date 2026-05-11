/*
 * SPDX-License-Identifier: MIT
 */
/**
 * Core domain model and execution surface for ta4j.
 *
 * <p>
 * This package provides the central contracts for:
 * </p>
 * <ul>
 * <li>market state ({@link org.ta4j.core.BarSeries}, {@link org.ta4j.core.Bar})</li>
 * <li>signal generation ({@link org.ta4j.core.Indicator}, {@link org.ta4j.core.Rule},
 * {@link org.ta4j.core.Strategy})</li>
 * <li>execution state ({@link org.ta4j.core.TradingRecord}, {@link org.ta4j.core.Trade},
 * {@link org.ta4j.core.TradeFill})</li>
 * <li>strategy evaluation ({@link org.ta4j.core.AnalysisCriterion})</li>
 * </ul>
 *
 * <p>
 * Entrypoint choices:
 * </p>
 * <ul>
 * <li>Use {@link org.ta4j.core.backtest.BarSeriesManager} for single-strategy runs.</li>
 * <li>Use {@link org.ta4j.core.backtest.BacktestExecutor} for large candidate sets and ranking.</li>
 * <li>Use {@link org.ta4j.core.BaseTradingRecord} with fill operations for broker-confirmed live workflows.</li>
 * </ul>
 */
package org.ta4j.core;
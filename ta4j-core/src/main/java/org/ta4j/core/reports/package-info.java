/*
 * SPDX-License-Identifier: MIT
 */
/**
 * Report and statement types for backtest and live-style evaluation output.
 *
 * <p>
 * Use this package to turn {@link org.ta4j.core.TradingRecord} results into
 * structured summaries for strategy comparison, regression checks, and
 * operational dashboards.
 * </p>
 *
 * <p>
 * Start with:
 * </p>
 * <ul>
 * <li>{@link org.ta4j.core.reports.TradingStatementGenerator} for per-run
 * statements</li>
 * <li>{@link org.ta4j.core.reports.BaseTradingStatement} for position/criteria
 * summaries</li>
 * <li>{@link org.ta4j.core.reports.BasePerformanceReport} for aggregate
 * performance views</li>
 * </ul>
 */
package org.ta4j.core.reports;
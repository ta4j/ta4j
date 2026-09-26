/*
 * SPDX-License-Identifier: MIT
 */
/**
 * Static target-weight, multi-asset portfolio backtesting.
 *
 * <p>
 * The workflow mirrors ta4j's single-series API:
 * {@link org.ta4j.core.portfolio.PortfolioSeries} aligns named bar series by
 * common end time, {@link org.ta4j.core.portfolio.PortfolioAllocation} holds
 * the target weights (the remainder is cash), and
 * {@link org.ta4j.core.portfolio.PortfolioSeriesManager} runs the allocation
 * with a {@link org.ta4j.core.portfolio.RebalancePolicy} and a transaction cost
 * model, returning an immutable
 * {@link org.ta4j.core.portfolio.PortfolioExecutionResult}.
 * </p>
 *
 * <p>
 * Allocation optimizers (minimum variance, HRP, and similar) are out of scope
 * here; they can produce a {@code PortfolioAllocation} for the same workflow.
 * </p>
 *
 * @since 0.25.1
 */
package org.ta4j.core.portfolio;

/*
 * SPDX-License-Identifier: MIT
 */
/**
 * Analysis criteria.
 *
 * <p>
 * This package contains different criteria which can be used to calculate the
 * performance of a {@link org.ta4j.core.Strategy trading strategy} and to
 * compare two {@link org.ta4j.core.Strategy trading strategies} to each other.
 * <p>
 * <b>Return Representation System:</b>
 * <p>
 * Return-based and ratio-producing criteria use a unified representation system
 * for formatting returns and ratios:
 * <ul>
 * <li><b>{@link ReturnRepresentation}</b>: Defines return format types
 * (MULTIPLICATIVE, DECIMAL, PERCENTAGE, LOG) and provides conversion methods
 * between formats.
 * <li><b>{@link ReturnRepresentationPolicy}</b>: Manages the global default
 * return representation used across all criteria. Can be configured via system
 * property {@code -Dta4j.returns.representation=DECIMAL} or programmatically.
 * <li><b>{@link org.ta4j.core.analysis.Returns}</b>: Calculates return rates
 * from positions/trading records and formats them according to a specified
 * representation.
 * </ul>
 * <p>
 * <b>Return-based criteria</b> (e.g.,
 * {@link org.ta4j.core.criteria.pnl.NetReturnCriterion}) default to
 * <em>multiplicative</em> returns (neutral value {@code 1.0}). The default can
 * be changed globally via {@link ReturnRepresentationPolicy} or per-criterion
 * by passing a {@link ReturnRepresentation} to the criterion constructor.
 * <p>
 * <b>Ratio-producing criteria</b> (e.g., {@link VersusEnterAndHoldCriterion},
 * {@link org.ta4j.core.criteria.drawdown.ReturnOverMaxDrawdownCriterion},
 * {@link PositionsRatioCriterion}) default to <em>decimal</em> representation
 * (ratios are typically expressed as decimals, e.g., 0.5 for 50%). These
 * criteria convert ratio outputs to the configured representation format. For
 * example, a ratio of 0.5 (50% better) can be expressed as:
 * <ul>
 * <li>DECIMAL: 0.5 (50% better)
 * <li>PERCENTAGE: 50.0 (50% better)
 * <li>MULTIPLICATIVE: 1.5 (1 + 0.5 = 1.5, meaning 50% better)
 * </ul>
 * See each ratio-producing criterion's javadoc for specific examples and usage
 * guidance.
 * <p>
 * <b>Risk-adjusted ratios</b> include {@link SharpeRatioCriterion},
 * {@link SortinoRatioCriterion}, {@link CalmarRatioCriterion}, and
 * {@link OmegaRatioCriterion}, which combine return observations with
 * volatility, downside, drawdown, or threshold asymmetry lenses.
 */
package org.ta4j.core.criteria;

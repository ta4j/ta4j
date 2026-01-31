/*
 * SPDX-License-Identifier: MIT
 */
/**
 * SuperTrend indicator and its components.
 *
 * <p>
 * SuperTrend is a popular trend-following indicator developed by Olivier Seban.
 * It uses ATR (Average True Range) to create dynamic support and resistance
 * bands that adapt to market volatility.
 *
 * <p>
 * The package contains:
 * <ul>
 * <li>{@link org.ta4j.core.indicators.supertrend.SuperTrendIndicator} - The
 * main indicator that provides the current SuperTrend value and trend direction
 * methods</li>
 * <li>{@link org.ta4j.core.indicators.supertrend.SuperTrendUpperBandIndicator}
 * - The dynamic resistance band (used in downtrends)</li>
 * <li>{@link org.ta4j.core.indicators.supertrend.SuperTrendLowerBandIndicator}
 * - The dynamic support band (used in uptrends)</li>
 * </ul>
 *
 * @see <a href="https://www.investopedia.com/supertrend-indicator-7976167">
 *      Investopedia: SuperTrend Indicator</a>
 */
package org.ta4j.core.indicators.supertrend;

/*
 * SPDX-License-Identifier: MIT
 */
/**
 * {@link org.ta4j.core.num.Num Num} interface and implementations of
 * {@link org.ta4j.core.num.NaN NaN}, {@link org.ta4j.core.num.DoubleNum
 * DoubleNum} and {@link org.ta4j.core.num.DecimalNum PrecisionNum}.
 *
 * <p>
 * The {@link org.ta4j.core.num.Num Num interface} enables the use of different
 * delegates (Double, {@link java.math.BigDecimal BigDecimal}, ...) for storage
 * and calculations in {@link org.ta4j.core.BarSeries BarSeries},
 * {@link org.ta4j.core.Bar Bars}, {@link org.ta4j.core.Indicator Indicators}
 * and {@link org.ta4j.core.criteria.AbstractAnalysisCriterion
 * AnalysisCriterions}.
 *
 * <p>
 * Utility methods for value validation:
 * <ul>
 * <li>{@link org.ta4j.core.num.Num#isNaNOrNull(Num)} - Checks if a value is
 * null or NaN</li>
 * <li>{@link org.ta4j.core.num.Num#isValid(Num)} - Checks if a value is valid
 * (not null and not NaN), the logical complement of {@code isNaNOrNull()}</li>
 * </ul>
 */
package org.ta4j.core.num;

/*
 * SPDX-License-Identifier: MIT
 */
/**
 * Forecast projection contracts and point-value adapters.
 * <p>
 * Projection indicators turn a hidden state into a forward forecast summary.
 * Point adapters expose one summary field, such as median or a quantile, as a
 * normal {@code Indicator<Num>} for rule and indicator composition.
 *
 * @since 0.22.9
 */
package org.ta4j.core.indicators.forecast.projection;

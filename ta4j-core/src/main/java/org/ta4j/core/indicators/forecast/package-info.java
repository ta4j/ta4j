/*
 * SPDX-License-Identifier: MIT
 */
/**
 * Forecast indicators and distribution adapters.
 * <p>
 * Forecast values are made at a decision index and must only depend on source
 * data available at or before that index. Evaluation code may compare those
 * forecasts with later realized bars, but forecast indicators in this package
 * must not read future source values while producing {@code getValue(i)}.
 *
 * @since 0.22.9
 */
package org.ta4j.core.indicators.forecast;

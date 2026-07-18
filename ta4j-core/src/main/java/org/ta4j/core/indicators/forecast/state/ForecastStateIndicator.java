/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast.state;

import org.ta4j.core.Indicator;

/**
 * Indicator that returns hidden state used by forecast projection indicators.
 *
 * @param <S> forecast state type
 * @since 0.22.9
 */
public interface ForecastStateIndicator<S extends ForecastState> extends Indicator<S> {
}

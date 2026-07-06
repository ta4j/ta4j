/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import org.ta4j.core.Indicator;

/**
 * Provides hidden state used by forecast projection providers.
 *
 * @param <S> state type
 * @since 0.22.9
 */
public interface ForecastStateProvider<S> extends Indicator<S> {
}

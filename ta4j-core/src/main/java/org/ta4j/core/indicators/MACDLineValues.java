/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.num.Num;

/**
 * Bundle of MACD line values for a single index.
 *
 * @param macd      MACD value
 * @param signal    signal-line value
 * @param histogram histogram value
 * @since 0.22.3
 * @deprecated use {@link org.ta4j.core.indicators.macd.MACDLineValues}
 */
@Deprecated(since = "0.22.3", forRemoval = false)
public record MACDLineValues(Num macd, Num signal, Num histogram) {
}

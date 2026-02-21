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
 * @since 0.22.2
 */
public record MACDLineValues(Num macd, Num signal, Num histogram) {
}

/*
 * SPDX-License-Identifier: MIT
 */
/**
 * Wyckoff cycle analysis indicators.
 *
 * <p>
 * The Wyckoff method is a technical analysis approach that interprets price,
 * volume, and structure to classify market phases into accumulation and
 * distribution cycles (commonly labeled phases A through E).
 *
 * <p>
 * <b>Entry points</b>:
 * <ul>
 * <li>{@link org.ta4j.core.indicators.wyckoff.WyckoffCycleFacade}:
 * indicator-style, per-bar access for strategy composition.</li>
 * <li>{@link org.ta4j.core.indicators.wyckoff.WyckoffCycleAnalysis}: one-shot
 * analysis that returns a
 * {@link org.ta4j.core.indicators.wyckoff.WyckoffCycleAnalysisResult} snapshot,
 * optionally across multiple degrees/configurations.</li>
 * </ul>
 *
 * @see <a href=
 *      "https://www.investopedia.com/terms/w/wyckoff-method.asp">Investopedia:
 *      Wyckoff Method</a>
 * @since 0.22.3
 */
package org.ta4j.core.indicators.wyckoff;

/*
 * SPDX-License-Identifier: MIT
 */
/**
 * ZigZag and swing-segmentation indicators.
 *
 * <p>
 * Use this package to compress price action into structural swing points for
 * pattern and trendline workflows.
 * {@link org.ta4j.core.indicators.zigzag.ZigZagStateIndicator} accepts separate
 * high/low sources for OHLC-aware pivots and anchors dynamic reversal
 * thresholds at the candidate extreme, so later volatility changes cannot move
 * the confirmation boundary.
 * </p>
 */
package org.ta4j.core.indicators.zigzag;

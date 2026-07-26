/*
 * SPDX-License-Identifier: MIT
 */
/**
 * Elliott Wave swing detection and swing-structure utilities.
 *
 * <p>
 * Use this package to configure swing extraction behavior before downstream
 * Elliott scenario generation and validation. The types here are detector,
 * configuration, pivot, and filter support types rather than first-class
 * {@link org.ta4j.core.Indicator} implementations; first-class indicators keep
 * the {@code *Indicator} naming convention. ZigZag and fractal detectors cover
 * sharp or fixed-window turns,
 * {@link org.ta4j.core.indicators.elliott.swing.SlopeChangeSwingDetector}
 * targets rounded turns, and tolerant composite detection provides quorum
 * consensus without requiring exact pivot-index agreement.
 * </p>
 */
package org.ta4j.core.indicators.elliott.swing;

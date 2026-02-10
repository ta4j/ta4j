/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.swing;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.elliott.ElliottDegree;

/**
 * Detects Elliott swing pivots and constructs swing sequences.
 *
 * <p>
 * Implement this interface to plug custom swing detection algorithms into
 * {@link org.ta4j.core.indicators.elliott.ElliottWaveAnalysis}.
 *
 * @since 0.22.2
 */
@FunctionalInterface
public interface SwingDetector {

    /**
     * Detects swings up to the supplied bar index.
     *
     * @param series source bar series
     * @param index  bar index to evaluate
     * @param degree Elliott degree metadata for generated swings
     * @return detection result containing pivots and swings
     * @since 0.22.2
     */
    SwingDetectorResult detect(BarSeries series, int index, ElliottDegree degree);
}

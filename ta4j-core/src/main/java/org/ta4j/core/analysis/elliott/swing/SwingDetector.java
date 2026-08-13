/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott.swing;

import java.util.List;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.elliott.ElliottDegree;

/**
 * Detects Elliott swing pivots and constructs swing sequences.
 *
 * <p>
 * Implement this interface to plug custom swing detection algorithms into
 * {@link org.ta4j.core.indicators.elliott.ElliottWaveAnalysisRunner}.
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

    /**
     * Detects confirmed pivots without requiring callers to select Elliott degree
     * metadata.
     *
     * <p>
     * The degree argument in {@link #detect(BarSeries, int, ElliottDegree)} labels
     * derived Elliott swings and must not change pivot detection. This view allows
     * the detector family to back general recent-swing indicators without leaking
     * that metadata into their API.
     *
     * @param series source bar series
     * @param index  bar index to evaluate
     * @return ordered confirmed pivots
     * @since 0.23.1
     */
    default List<SwingPivot> detectPivots(final BarSeries series, final int index) {
        return detect(series, index, ElliottDegree.MINUETTE).pivots();
    }
}

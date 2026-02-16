/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.swing;

import java.util.List;
import java.util.Objects;

import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottSwing;

/**
 * Captures detected swing pivots and swings for a given index.
 *
 * <p>
 * Use this record to return both pivot-level and swing-level views from a
 * {@link SwingDetector}. It is especially handy when downstream consumers need
 * pivot diagnostics or to reconstitute swings.
 *
 * @param pivots ordered list of detected pivots
 * @param swings ordered list of swings derived from pivots
 * @since 0.22.2
 */
public record SwingDetectorResult(List<SwingPivot> pivots, List<ElliottSwing> swings) {

    public SwingDetectorResult {
        pivots = pivots == null ? List.of() : List.copyOf(pivots);
        swings = swings == null ? List.of() : List.copyOf(swings);
    }

    /**
     * Creates a result from a swing list by deriving pivot data.
     *
     * @param swings detected swings
     * @return detection result including derived pivots
     * @since 0.22.2
     */
    public static SwingDetectorResult fromSwings(final List<ElliottSwing> swings) {
        Objects.requireNonNull(swings, "swings");
        final List<SwingPivot> pivots = SwingDetectorSupport.pivotsFromSwings(swings);
        return new SwingDetectorResult(pivots, swings);
    }

    /**
     * Creates a result from pivots by deriving swings.
     *
     * @param pivots detected pivots
     * @param degree Elliott degree metadata
     * @return detection result including derived swings
     * @since 0.22.2
     */
    public static SwingDetectorResult fromPivots(final List<SwingPivot> pivots, final ElliottDegree degree) {
        Objects.requireNonNull(degree, "degree");
        final List<SwingPivot> normalized = SwingDetectorSupport.normalizePivots(pivots);
        final List<ElliottSwing> swings = SwingDetectorSupport.swingsFromPivots(normalized, degree);
        return new SwingDetectorResult(normalized, swings);
    }

    /**
     * @return {@code true} when no swings were detected
     * @since 0.22.2
     */
    public boolean isEmpty() {
        return swings.isEmpty();
    }
}

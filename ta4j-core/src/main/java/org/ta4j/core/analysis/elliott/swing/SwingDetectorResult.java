/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott.swing;

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
 * <p>
 * Pivots and swings are mutually consistent views of the same zigzag: the
 * swings must form a contiguous chain in which every swing starts at the
 * preceding swing's exact destination, and when both are non-empty, the
 * supplied pivots must equal the pivot chain derivable from the supplied swings
 * (the derivation used by {@link #fromSwings} and the inverse of
 * {@link #fromPivots}). A disconnected chain or mismatched pair is rejected
 * with an {@link IllegalArgumentException}.
 *
 * @param pivots ordered list of detected pivots
 * @param swings ordered list of swings derived from pivots
 * @throws IllegalArgumentException when the swings are not a contiguous chain,
 *                                  or when both pivots and swings are non-empty
 *                                  and mutually inconsistent
 * @since 0.22.2
 */
public record SwingDetectorResult(List<SwingPivot> pivots, List<ElliottSwing> swings) {

    public SwingDetectorResult {
        pivots = pivots == null ? List.of() : List.copyOf(pivots);
        swings = swings == null ? List.of() : List.copyOf(swings);
        // The pivot-chain derivation only inspects the first swing's origin and
        // each swing's destination, so reject disconnected chains explicitly:
        // every swing must start at the preceding swing's exact destination.
        for (int i = 1; i < swings.size(); i++) {
            final ElliottSwing previous = swings.get(i - 1);
            final ElliottSwing current = swings.get(i);
            if (current.fromIndex() != previous.toIndex() || !current.fromPrice().equals(previous.toPrice())) {
                throw new IllegalArgumentException("swings must form a contiguous chain: swing " + i + " starts at ("
                        + current.fromIndex() + ", " + current.fromPrice() + ") but swing " + (i - 1) + " ends at ("
                        + previous.toIndex() + ", " + previous.toPrice() + ")");
            }
        }
        if (!pivots.isEmpty() && !swings.isEmpty()) {
            final List<SwingPivot> derivedPivots = SwingDetectorSupport.pivotsFromSwings(swings);
            if (!derivedPivots.equals(pivots)) {
                throw new IllegalArgumentException("pivots and swings are inconsistent: pivots derived from the "
                        + "supplied swings " + derivedPivots + " do not match the supplied pivots " + pivots);
            }
        }
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

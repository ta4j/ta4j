/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import java.util.List;
import java.util.Objects;

import org.ta4j.core.num.Num;

/**
 * One bounded, deterministic topology candidate: a contiguous window of
 * confirmed pivots evaluated against a grammar and direction.
 *
 * <p>
 * All leg arithmetic stays in the series' own {@link Num} domain so
 * {@code DecimalNum} precision survives every decision comparison; callers that
 * render observations may still narrow to {@code double} for display.
 *
 * @param grammar   grammar the window was evaluated against
 * @param direction declared trend direction of the hypothesis
 * @param pivots    contiguous confirmed pivots; {@code grammar.legCount() + 1}
 *                  entries with strictly increasing indices and alternating
 *                  types
 */
record TopologyCandidate(TopologyGrammar grammar, WaveDirection direction, List<ConfirmedPivot> pivots) {

    TopologyCandidate {
        Objects.requireNonNull(grammar, "grammar");
        Objects.requireNonNull(direction, "direction");
        pivots = pivots == null ? List.of() : List.copyOf(pivots);
        if (pivots.size() != grammar.requiredPivots()) {
            throw new IllegalArgumentException(
                    grammar + " requires " + grammar.requiredPivots() + " pivots but got " + pivots.size());
        }
        for (int i = 1; i < pivots.size(); i++) {
            final ConfirmedPivot previous = pivots.get(i - 1);
            final ConfirmedPivot current = pivots.get(i);
            if (current.pivotIndex() <= previous.pivotIndex()) {
                throw new IllegalArgumentException("candidate pivots must have strictly increasing indices");
            }
            if (current.type() == previous.type()) {
                throw new IllegalArgumentException("candidate pivots must alternate types");
            }
        }
    }

    /**
     * @return price of the pivot at the start of leg {@code legIndex}
     */
    Num legStartPrice(final int legIndex) {
        return pivots.get(legIndex).price();
    }

    /**
     * @return price of the pivot at the end of leg {@code legIndex}
     */
    Num legEndPrice(final int legIndex) {
        return pivots.get(legIndex + 1).price();
    }

    /**
     * @return signed size of leg {@code legIndex}; positive in trend direction
     */
    Num legSize(final int legIndex) {
        final Num size = legEndPrice(legIndex).minus(legStartPrice(legIndex));
        return direction == WaveDirection.BULLISH ? size : size.negate();
    }

    /**
     * @return index of the first bar spanned by this candidate
     */
    int startBarIndex() {
        return pivots.get(0).pivotIndex();
    }

    /**
     * @return index of the last bar spanned by this candidate
     */
    int endBarIndex() {
        return pivots.get(pivots.size() - 1).pivotIndex();
    }
}

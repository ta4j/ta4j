/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott.topology;

/**
 * Structural wave grammars recognized by the experimental topology kernel.
 *
 * <p>
 * A grammar is defined purely by leg topology over contiguous alternating
 * pivots: {@code MOTIVE_5} is five legs with three thrusts in the declared
 * trend direction, {@code CORRECTIVE_3} is three countertrend legs, and
 * {@code CYCLE_5_3} is a complete motive followed by a corrective block.
 */
public enum TopologyGrammar {

    /** Five alternating legs containing three trend-direction thrusts. */
    MOTIVE_5(5),

    /** Three alternating countertrend legs. */
    CORRECTIVE_3(3),

    /** A complete motive followed by a three-leg corrective block. */
    CYCLE_5_3(8);

    private final int legCount;

    TopologyGrammar(final int legCount) {
        this.legCount = legCount;
    }

    /**
     * @return number of legs; a candidate needs {@code legCount + 1} pivots
     */
    public int legCount() {
        return legCount;
    }

    /**
     * @return minimum confirmed pivots required to attempt a complete match
     */
    public int requiredPivots() {
        return legCount + 1;
    }
}

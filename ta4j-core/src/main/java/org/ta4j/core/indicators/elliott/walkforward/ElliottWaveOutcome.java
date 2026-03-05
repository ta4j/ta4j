/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.walkforward;

import org.ta4j.core.num.Num;

/**
 * Fixed-horizon realized outcome for Elliott scenario evaluation.
 *
 * @param eventOutcome         event ordering outcome
 * @param phaseProgression     coarse phase progression proxy
 * @param realizedReturn       horizon return from decision close to horizon
 *                             close
 * @param reachedPrimaryTarget whether primary target was reached in the horizon
 * @param breachedInvalidation whether invalidation was breached in the horizon
 * @since 0.22.4
 */
public record ElliottWaveOutcome(EventOutcome eventOutcome, PhaseProgression phaseProgression, Num realizedReturn,
        boolean reachedPrimaryTarget, boolean breachedInvalidation) {

    /**
     * Event ordering outcome for fixed-horizon evaluation.
     *
     * @since 0.22.4
     */
    public enum EventOutcome {
        TARGET_FIRST, INVALIDATION_FIRST, NEITHER
    }

    /**
     * Coarse phase progression proxy.
     *
     * @since 0.22.4
     */
    public enum PhaseProgression {
        ADVANCING, STALLED, REVERSING, UNKNOWN
    }
}

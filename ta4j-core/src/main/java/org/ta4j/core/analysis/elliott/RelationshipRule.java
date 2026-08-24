/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import org.ta4j.core.BarSeries;

/**
 * One independently selectable relationship rule over a topology candidate.
 *
 * <p>
 * Rules never change topology extraction; they only observe a candidate and
 * return structured evidence so each premise stays separately ablatable.
 */
interface RelationshipRule {

    /**
     * @return stable rule identifier used in evidence ledgers and reports
     */
    String id();

    /**
     * Evaluates this rule against one complete topology candidate.
     *
     * @param candidate candidate to observe; rules must be pure functions of it
     * @return structured evidence with explicit state and rationale
     */
    RuleEvidence evaluate(TopologyCandidate candidate);

    /**
     * Evaluates this rule against one candidate on the series its pivots were
     * observed on. Rules that bind series-scoped state such as momentum indicators
     * must override this and key their state by series; the default delegates to
     * {@link #evaluate(TopologyCandidate)}.
     *
     * @param candidate candidate to observe
     * @param series    the evaluated series the candidate was extracted from
     * @return structured evidence with explicit state and rationale
     */
    default RuleEvidence evaluate(final TopologyCandidate candidate, final BarSeries series) {
        return evaluate(candidate);
    }
}

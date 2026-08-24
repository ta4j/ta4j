/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

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
}

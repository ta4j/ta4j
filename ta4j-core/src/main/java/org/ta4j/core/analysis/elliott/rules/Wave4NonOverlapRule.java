/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott.rules;

import org.ta4j.core.analysis.elliott.topology.*;

import org.ta4j.core.num.Num;
import java.util.List;

/**
 * Validates that wave 4 does not overlap the price extreme terminating wave 1.
 */
final class Wave4NonOverlapRule implements RelationshipRule {

    @Override
    public String id() {
        return "wave4-nonoverlap";
    }

    @Override
    public RuleEvidence evaluate(final TopologyCandidate candidate) {
        if (!(candidate.grammar() == TopologyGrammar.MOTIVE_5 || candidate.grammar() == TopologyGrammar.CYCLE_5_3)) {
            return RuleEvidence.notApplicable(id(), "wave 4 overlap protection applies only to five-wave grammars");
        }

        final Num wave1ExtremePrice = candidate.pivots().get(1).price();
        final Num wave4ExtremePrice = candidate.pivots().get(4).price();
        final List<String> observations = List.of("wave 1 extreme price=" + wave1ExtremePrice,
                "wave 4 extreme price=" + wave4ExtremePrice);
        final boolean doesNotOverlap = candidate.direction() == WaveDirection.BULLISH
                ? wave4ExtremePrice.isGreaterThan(wave1ExtremePrice)
                : wave4ExtremePrice.isLessThan(wave1ExtremePrice);
        if (doesNotOverlap) {
            return RuleEvidence.pass(id(), observations, "wave 4 does not overlap the wave 1 extreme");
        }
        return RuleEvidence.fail(id(), observations, "wave 4 overlaps or breaches the wave 1 extreme");
    }

}

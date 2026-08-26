/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott.rules;

import org.ta4j.core.analysis.elliott.topology.*;

import java.util.List;

import org.ta4j.core.num.Num;

/**
 * Validates that wave 2 does not retrace through the origin of wave 1.
 */
final class Wave2OriginRule implements RelationshipRule {

    @Override
    public String id() {
        return "wave2-origin";
    }

    @Override
    public RuleEvidence evaluate(final TopologyCandidate candidate) {
        if (!isApplicable(candidate)) {
            return RuleEvidence.notApplicable(id(), "wave 2 origin protection applies only to five-wave grammars");
        }

        final Num originPrice = candidate.pivots().get(0).price();
        final Num wave2EndPrice = candidate.pivots().get(2).price();
        final List<String> observations = List.of("origin price=" + originPrice, "wave 2 end price=" + wave2EndPrice);
        final boolean holdsOrigin = candidate.direction() == WaveDirection.BULLISH
                ? wave2EndPrice.isGreaterThan(originPrice)
                : wave2EndPrice.isLessThan(originPrice);
        if (holdsOrigin) {
            return RuleEvidence.pass(id(), observations, "wave 2 holds the wave 1 origin");
        }
        return RuleEvidence.fail(id(), observations, "wave 2 crosses or touches the wave 1 origin");
    }

    private boolean isApplicable(final TopologyCandidate candidate) {
        return candidate.grammar() == TopologyGrammar.MOTIVE_5 || candidate.grammar() == TopologyGrammar.CYCLE_5_3;
    }
}

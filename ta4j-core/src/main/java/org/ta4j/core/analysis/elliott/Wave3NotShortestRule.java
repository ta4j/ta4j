/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import org.ta4j.core.num.Num;
import java.util.List;

/**
 * Rejects a motive whose third wave is strictly shorter than waves 1 and 5.
 */
final class Wave3NotShortestRule implements RelationshipRule {

    @Override
    public String id() {
        return "wave3-not-shortest";
    }

    @Override
    public RuleEvidence evaluate(final TopologyCandidate candidate) {
        if (!isApplicable(candidate)) {
            return RuleEvidence.notApplicable(id(), "wave 3 length comparison applies only to five-wave grammars");
        }
        if (candidate.pivots().size() < TopologyGrammar.MOTIVE_5.requiredPivots()) {
            return RuleEvidence.pending(id(), List.of(),
                    "wave 3 length comparison awaits the complete five-wave candidate");
        }

        // Magnitudes compared in Num domain; doubleValue only for the
        // observation strings below.
        final Num wave1Leg = candidate.legSize(0);
        final Num wave3Leg = candidate.legSize(2);
        final Num wave5Leg = candidate.legSize(4);
        if (magnitude(wave3Leg).isLessThan(magnitude(wave1Leg))
                && magnitude(wave3Leg).isLessThan(magnitude(wave5Leg))) {
            return RuleEvidence.fail(id(), observations(wave1Leg, wave3Leg, wave5Leg),
                    "wave 3 is strictly the shortest motive wave");
        }
        return RuleEvidence.pass(id(), observations(wave1Leg, wave3Leg, wave5Leg),
                "wave 3 is not strictly the shortest motive wave");
    }

    private static List<String> observations(final Num wave1Leg, final Num wave3Leg, final Num wave5Leg) {
        return List.of("wave 1 magnitude=" + magnitude(wave1Leg), "wave 3 magnitude=" + magnitude(wave3Leg),
                "wave 5 magnitude=" + magnitude(wave5Leg));
    }

    private static Num magnitude(final Num leg) {
        return leg.isNegative() ? leg.negate() : leg;
    }

    private boolean isApplicable(final TopologyCandidate candidate) {
        return candidate.grammar() == TopologyGrammar.MOTIVE_5 || candidate.grammar() == TopologyGrammar.CYCLE_5_3;
    }
}

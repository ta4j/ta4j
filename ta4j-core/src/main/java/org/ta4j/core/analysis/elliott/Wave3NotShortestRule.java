/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import java.util.List;

import org.ta4j.core.num.Num;

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
        if (candidate.grammar() != TopologyGrammar.MOTIVE_5 && candidate.grammar() != TopologyGrammar.CYCLE_5_3) {
            return RuleEvidence.notApplicable(id(), "wave 3 length comparison applies only to five-wave grammars");
        }
        if (candidate.pivots().size() < TopologyGrammar.MOTIVE_5.requiredPivots()) {
            return RuleEvidence.pending(id(), List.of(),
                    "wave 3 length comparison awaits the complete five-wave candidate");
        }

        // Magnitudes compared in Num domain; primitive-backed overflow must not
        // collapse distinct legs into an indeterminate ordering.
        final Num wave1Magnitude = magnitude(candidate.legSize(0));
        final Num wave3Magnitude = magnitude(candidate.legSize(2));
        final Num wave5Magnitude = magnitude(candidate.legSize(4));
        if (!Num.isFinite(wave1Magnitude) || !Num.isFinite(wave3Magnitude) || !Num.isFinite(wave5Magnitude)) {
            return RuleEvidence.unavailable(id(), "wave 3 length comparison requires finite leg magnitudes");
        }
        if (wave3Magnitude.isLessThan(wave1Magnitude) && wave3Magnitude.isLessThan(wave5Magnitude)) {
            return RuleEvidence.fail(id(), observations(wave1Magnitude, wave3Magnitude, wave5Magnitude),
                    "wave 3 is strictly the shortest motive wave");
        }
        return RuleEvidence.pass(id(), observations(wave1Magnitude, wave3Magnitude, wave5Magnitude),
                "wave 3 is not strictly the shortest motive wave");
    }

    private static List<String> observations(final Num wave1Magnitude, final Num wave3Magnitude,
            final Num wave5Magnitude) {
        return List.of("wave 1 magnitude=" + wave1Magnitude, "wave 3 magnitude=" + wave3Magnitude,
                "wave 5 magnitude=" + wave5Magnitude);
    }

    private static Num magnitude(final Num leg) {
        return leg.isNegative() ? leg.negate() : leg;
    }

}

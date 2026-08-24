/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

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

        final double wave1Magnitude = Math.abs(candidate.legSize(0));
        final double wave3Magnitude = Math.abs(candidate.legSize(2));
        final double wave5Magnitude = Math.abs(candidate.legSize(4));
        final List<String> observations = List.of("wave 1 magnitude=" + wave1Magnitude,
                "wave 3 magnitude=" + wave3Magnitude, "wave 5 magnitude=" + wave5Magnitude);
        if (wave3Magnitude < wave1Magnitude && wave3Magnitude < wave5Magnitude) {
            return RuleEvidence.fail(id(), observations, "wave 3 is strictly the shortest motive wave");
        }
        return RuleEvidence.pass(id(), observations, "wave 3 is not strictly the shortest motive wave");
    }

    private boolean isApplicable(final TopologyCandidate candidate) {
        return candidate.grammar() == TopologyGrammar.MOTIVE_5 || candidate.grammar() == TopologyGrammar.CYCLE_5_3;
    }
}

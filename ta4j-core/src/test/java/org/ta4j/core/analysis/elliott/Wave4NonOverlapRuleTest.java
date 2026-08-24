/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.analysis.elliott.swing.SwingPivotType;
import org.ta4j.core.num.DoubleNum;

class Wave4NonOverlapRuleTest {

    private final Wave4NonOverlapRule rule = new Wave4NonOverlapRule();

    @Test
    void passesWhenBullishWave4StaysAboveWave1High() {
        final RuleEvidence evidence = rule.evaluate(candidate(WaveDirection.BULLISH, 100, 120, 110, 150, 125, 170));

        assertThat(evidence.state()).isEqualTo(EvidenceState.PASS);
        assertThat(evidence.observations()).contains("wave 1 extreme price=120.0", "wave 4 extreme price=125.0");
    }

    @Test
    void failsWhenBullishWave4TouchesWave1High() {
        final RuleEvidence evidence = rule.evaluate(candidate(WaveDirection.BULLISH, 100, 120, 110, 150, 120, 170));

        assertThat(evidence.state()).isEqualTo(EvidenceState.FAIL);
    }

    @Test
    void mirrorsBullishAndBearishStates() {
        final RuleEvidence bullish = rule.evaluate(
                candidate(WaveDirection.BULLISH, 100, 120, 110, 150, 120, 170));
        final RuleEvidence bearish = rule.evaluate(
                candidate(WaveDirection.BEARISH, 100, 80, 90, 50, 80, 30));

        assertThat(bearish.state()).isEqualTo(bullish.state());
    }

    @Test
    void doesNotApplyToCorrectiveGrammar() {
        final RuleEvidence evidence = rule.evaluate(candidate(WaveDirection.BULLISH, 100, 110, 105, 115));

        assertThat(evidence.state()).isEqualTo(EvidenceState.NOT_APPLICABLE);
    }

    private static TopologyCandidate candidate(final WaveDirection direction, final double... prices) {
        final TopologyGrammar grammar = grammarFor(prices.length);
        final List<ConfirmedPivot> pivots = new ArrayList<>(prices.length);
        final SwingPivotType firstType = direction == WaveDirection.BULLISH ? SwingPivotType.LOW : SwingPivotType.HIGH;
        for (int index = 0; index < prices.length; index++) {
            final SwingPivotType type = index % 2 == 0 ? firstType
                    : firstType == SwingPivotType.HIGH ? SwingPivotType.LOW : SwingPivotType.HIGH;
            pivots.add(new ConfirmedPivot(index, index, DoubleNum.valueOf(prices[index]), type));
        }
        return new TopologyCandidate(grammar, direction, pivots);
    }

    private static TopologyGrammar grammarFor(final int priceCount) {
        return switch (priceCount) {
        case 4 -> TopologyGrammar.CORRECTIVE_3;
        case 6 -> TopologyGrammar.MOTIVE_5;
        case 9 -> TopologyGrammar.CYCLE_5_3;
        default -> throw new IllegalArgumentException("unexpected price count: " + priceCount);
        };
    }
}

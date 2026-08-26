/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class RuleEvidenceTest {

    @Test
    void rejectsNaNScoredEvidence() {
        assertThatThrownBy(() -> RuleEvidence.scored("wave5-divergence", Double.NaN, List.of(), "explanation"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("score");
    }

    @Test
    void acceptsExactBoundaryScores() {
        assertThat(RuleEvidence.scored("wave5-divergence", 0.0d, List.of(), "explanation").score()).contains(0.0d);
        assertThat(RuleEvidence.scored("wave5-divergence", 1.0d, List.of(), "explanation").score()).contains(1.0d);
    }

    @Test
    void rejectsScoresOnNonPassingEvidence() {
        final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new RuleEvidence("wave5-divergence", EvidenceState.FAIL, Optional.of(0.5d), List.of(),
                        "explanation"));
        assertThat(exception).hasMessageContaining("PASS");
    }

    @Test
    void keepsInteriorScoreUnchanged() {
        assertThat(RuleEvidence.scored("wave5-divergence", 0.42d, List.of(), "explanation").score()).contains(0.42d);
    }

    @Test
    void clampsScoreJustBelowZeroOntoTheBoundary() {
        // Within the epsilon tolerance arithmetic noise is accepted, but it
        // must never be stored outside [0, 1] or report metrics would reject
        // the aggregated evidence later.
        final RuleEvidence evidence = RuleEvidence.scored("wave5-divergence", -5e-10, List.of(), "explanation");

        assertThat(evidence.score()).contains(0.0d);
    }

    @Test
    void clampsScoreJustAboveOneOntoTheBoundary() {
        final RuleEvidence evidence = RuleEvidence.scored("wave5-divergence", 1.0d + 5e-10, List.of(), "explanation");

        assertThat(evidence.score()).contains(1.0d);
    }

    @Test
    void rejectsScoreBelowZero() {
        assertThatThrownBy(() -> RuleEvidence.scored("wave5-divergence", -1e-8, List.of(), "explanation"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("score");
    }

    @Test
    void rejectsScoreAboveOne() {
        assertThatThrownBy(() -> RuleEvidence.scored("wave5-divergence", 1.0d + 1e-8, List.of(), "explanation"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("score");
    }

    @Test
    void rejectsInfiniteScoredEvidence() {
        assertThatThrownBy(
                () -> RuleEvidence.scored("wave5-divergence", Double.POSITIVE_INFINITY, List.of(), "explanation"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("score");
        assertThatThrownBy(
                () -> RuleEvidence.scored("wave5-divergence", Double.NEGATIVE_INFINITY, List.of(), "explanation"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("score");
    }
}

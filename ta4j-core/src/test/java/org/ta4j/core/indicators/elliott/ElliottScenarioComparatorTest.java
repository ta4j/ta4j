/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

class ElliottScenarioComparatorTest {

    private NumFactory numFactory;
    private ElliottScenarioComparator comparator;

    @BeforeEach
    void setUp() {
        numFactory = DecimalNumFactory.getInstance();
        comparator = new ElliottScenarioComparator(numFactory);
    }

    @Test
    void identicalScenariosHaveZeroDivergence() {
        ElliottScenario s1 = createScenario("s1", 0.8, ElliottPhase.WAVE3, true, ScenarioType.IMPULSE);
        ElliottScenario s2 = createScenario("s2", 0.7, ElliottPhase.WAVE3, true, ScenarioType.IMPULSE);

        Num divergence = comparator.divergenceScore(s1, s2);

        assertThat(divergence.doubleValue()).isCloseTo(0.0, within(0.01));
    }

    @Test
    void differentPhasesIncreaseDivergence() {
        ElliottScenario s1 = createScenario("s1", 0.8, ElliottPhase.WAVE3, true, ScenarioType.IMPULSE);
        ElliottScenario s2 = createScenario("s2", 0.7, ElliottPhase.WAVE4, true, ScenarioType.IMPULSE);

        Num divergence = comparator.divergenceScore(s1, s2);

        assertThat(divergence.doubleValue()).isGreaterThan(0.1);
    }

    @Test
    void impulseVsCorrectiveHighDivergence() {
        ElliottScenario impulse = createScenario("imp", 0.8, ElliottPhase.WAVE3, true, ScenarioType.IMPULSE);
        ElliottScenario corrective = createScenario("corr", 0.7, ElliottPhase.CORRECTIVE_A, true,
                ScenarioType.CORRECTIVE_ZIGZAG);

        Num divergence = comparator.divergenceScore(impulse, corrective);

        assertThat(divergence.doubleValue()).isGreaterThan(0.4);
    }

    @Test
    void oppositeDirectionsHighDivergence() {
        ElliottScenario bullish = createScenario("bull", 0.8, ElliottPhase.WAVE3, true, ScenarioType.IMPULSE);
        ElliottScenario bearish = createScenario("bear", 0.7, ElliottPhase.WAVE3, false, ScenarioType.IMPULSE);

        Num divergence = comparator.divergenceScore(bullish, bearish);

        assertThat(divergence.doubleValue()).isGreaterThan(0.2);
    }

    @Test
    void consensusPhaseWhenAllAgree() {
        ElliottScenario s1 = createScenario("s1", 0.9, ElliottPhase.WAVE3, true, ScenarioType.IMPULSE);
        ElliottScenario s2 = createScenario("s2", 0.8, ElliottPhase.WAVE3, true, ScenarioType.IMPULSE);

        ElliottPhase consensus = comparator.consensusPhase(List.of(s1, s2));

        assertThat(consensus).isEqualTo(ElliottPhase.WAVE3);
    }

    @Test
    void noConsensusWhenDisagreement() {
        ElliottScenario s1 = createScenario("s1", 0.9, ElliottPhase.WAVE3, true, ScenarioType.IMPULSE);
        ElliottScenario s2 = createScenario("s2", 0.8, ElliottPhase.WAVE4, true, ScenarioType.IMPULSE);

        ElliottPhase consensus = comparator.consensusPhase(List.of(s1, s2));

        assertThat(consensus).isEqualTo(ElliottPhase.NONE);
    }

    @Test
    void averageConfidence() {
        ElliottScenario s1 = createScenario("s1", 0.8, ElliottPhase.WAVE3, true, ScenarioType.IMPULSE);
        ElliottScenario s2 = createScenario("s2", 0.6, ElliottPhase.WAVE4, true, ScenarioType.IMPULSE);

        Num average = comparator.averageConfidence(List.of(s1, s2));

        assertThat(average.doubleValue()).isCloseTo(0.7, within(0.01));
    }

    @Test
    void averageConfidencePreservesNumPrecision() {
        NumFactory highPrecision = DecimalNumFactory.getInstance(32);
        ElliottScenarioComparator preciseComparator = new ElliottScenarioComparator(highPrecision);

        Num scoreA = highPrecision.numOf("0.12345678901234567890");
        Num scoreB = highPrecision.numOf("0.22345678901234567890");
        ElliottConfidence confidenceA = new ElliottConfidence(scoreA, scoreA, scoreA, scoreA, scoreA, scoreA, "Test");
        ElliottConfidence confidenceB = new ElliottConfidence(scoreB, scoreB, scoreB, scoreB, scoreB, scoreB, "Test");

        ElliottScenario scenarioA = ElliottScenario.builder()
                .id("a")
                .currentPhase(ElliottPhase.WAVE1)
                .confidence(confidenceA)
                .type(ScenarioType.IMPULSE)
                .build();
        ElliottScenario scenarioB = ElliottScenario.builder()
                .id("b")
                .currentPhase(ElliottPhase.WAVE1)
                .confidence(confidenceB)
                .type(ScenarioType.IMPULSE)
                .build();

        Num average = preciseComparator.averageConfidence(List.of(scenarioA, scenarioB));

        assertThat(average).isEqualByComparingTo(highPrecision.numOf("0.17345678901234567890"));
    }

    @Test
    void directionalConsensusWhenSameDirection() {
        ElliottScenario s1 = createScenario("s1", 0.9, ElliottPhase.WAVE3, true, ScenarioType.IMPULSE);
        ElliottScenario s2 = createScenario("s2", 0.8, ElliottPhase.WAVE4, true, ScenarioType.IMPULSE);

        boolean consensus = comparator.hasDirectionalConsensus(List.of(s1, s2));

        assertThat(consensus).isTrue();
    }

    @Test
    void noDirectionalConsensusWhenOpposite() {
        ElliottScenario bullish = createScenario("bull", 0.9, ElliottPhase.WAVE3, true, ScenarioType.IMPULSE);
        ElliottScenario bearish = createScenario("bear", 0.8, ElliottPhase.WAVE3, false, ScenarioType.IMPULSE);

        boolean consensus = comparator.hasDirectionalConsensus(List.of(bullish, bearish));

        assertThat(consensus).isFalse();
    }

    @Test
    void sharedInvalidationReturnsConservativeForBullish() {
        ElliottScenario s1 = createScenarioWithInvalidation("s1", 0.8, true, numFactory.numOf(100));
        ElliottScenario s2 = createScenarioWithInvalidation("s2", 0.7, true, numFactory.numOf(95));

        Num shared = comparator.sharedInvalidation(List.of(s1, s2));

        // For bullish, should return the lower (more conservative) invalidation
        assertThat(shared.doubleValue()).isCloseTo(95, within(0.01));
    }

    @Test
    void sharedInvalidationReturnsConservativeForBearish() {
        ElliottScenario s1 = createScenarioWithInvalidation("s1", 0.8, false, numFactory.numOf(100));
        ElliottScenario s2 = createScenarioWithInvalidation("s2", 0.7, false, numFactory.numOf(105));

        Num shared = comparator.sharedInvalidation(List.of(s1, s2));

        // For bearish, should return the higher (more conservative) invalidation
        assertThat(shared.doubleValue()).isCloseTo(105, within(0.01));
    }

    @Test
    void sharedInvalidationReturnsNaNForMixedDirections() {
        ElliottScenario bullish = createScenarioWithInvalidation("bull", 0.8, true, numFactory.numOf(95));
        ElliottScenario bearish = createScenarioWithInvalidation("bear", 0.7, false, numFactory.numOf(105));

        Num shared = comparator.sharedInvalidation(List.of(bullish, bearish));

        // Mixed directions produce incompatible invalidation levels
        assertThat(shared.isNaN()).isTrue();
    }

    @Test
    void commonTargetRange() {
        ElliottScenario s1 = createScenarioWithTarget("s1", numFactory.numOf(150));
        ElliottScenario s2 = createScenarioWithTarget("s2", numFactory.numOf(160));

        Num[] range = comparator.commonTargetRange(List.of(s1, s2));

        assertThat(range).hasSize(2);
        assertThat(range[0].doubleValue()).isCloseTo(150, within(0.01));
        assertThat(range[1].doubleValue()).isCloseTo(160, within(0.01));
    }

    @Test
    void compareSummaryOutput() {
        ElliottScenario s1 = createScenario("scenario-1", 0.85, ElliottPhase.WAVE3, true, ScenarioType.IMPULSE);
        ElliottScenario s2 = createScenario("scenario-2", 0.65, ElliottPhase.WAVE4, true, ScenarioType.IMPULSE);

        String summary = comparator.compareSummary(s1, s2);

        assertThat(summary).contains("scenario-1");
        assertThat(summary).contains("scenario-2");
        assertThat(summary).contains("WAVE3");
        assertThat(summary).contains("WAVE4");
        assertThat(summary).contains("Divergence");
    }

    private ElliottScenario createScenario(String id, double confidence, ElliottPhase phase, boolean bullish,
            ScenarioType type) {
        List<ElliottSwing> swings = bullish
                ? List.of(new ElliottSwing(0, 5, numFactory.numOf(100), numFactory.numOf(120), ElliottDegree.MINOR))
                : List.of(new ElliottSwing(0, 5, numFactory.numOf(120), numFactory.numOf(100), ElliottDegree.MINOR));

        return ElliottScenario.builder()
                .id(id)
                .currentPhase(phase)
                .swings(swings)
                .confidence(createConfidence(confidence))
                .type(type)
                .build();
    }

    private ElliottScenario createScenarioWithInvalidation(String id, double confidence, boolean bullish,
            Num invalidation) {
        List<ElliottSwing> swings = bullish
                ? List.of(new ElliottSwing(0, 5, numFactory.numOf(100), numFactory.numOf(120), ElliottDegree.MINOR))
                : List.of(new ElliottSwing(0, 5, numFactory.numOf(120), numFactory.numOf(100), ElliottDegree.MINOR));

        return ElliottScenario.builder()
                .id(id)
                .currentPhase(ElliottPhase.WAVE3)
                .swings(swings)
                .confidence(createConfidence(confidence))
                .type(ScenarioType.IMPULSE)
                .invalidationPrice(invalidation)
                .build();
    }

    private ElliottScenario createScenarioWithTarget(String id, Num target) {
        return ElliottScenario.builder()
                .id(id)
                .currentPhase(ElliottPhase.WAVE3)
                .confidence(createConfidence(0.7))
                .type(ScenarioType.IMPULSE)
                .primaryTarget(target)
                .build();
    }

    private ElliottConfidence createConfidence(double overall) {
        Num score = numFactory.numOf(overall);
        return new ElliottConfidence(score, score, score, score, score, score, "Test");
    }
}

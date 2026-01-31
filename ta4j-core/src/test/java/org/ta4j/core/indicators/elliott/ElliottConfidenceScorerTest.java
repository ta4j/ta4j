/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

class ElliottConfidenceScorerTest {

    private NumFactory numFactory;
    private ElliottConfidenceScorer scorer;

    @BeforeEach
    void setUp() {
        numFactory = DecimalNumFactory.getInstance();
        scorer = new ElliottConfidenceScorer(numFactory);
    }

    @Test
    void emptySwingsReturnsZeroConfidence() {
        ElliottConfidence confidence = scorer.score(List.of(), ElliottPhase.WAVE1, null);

        assertThat(confidence.overall().doubleValue()).isZero();
        assertThat(confidence.primaryReason()).isEqualTo("No valid structure");
    }

    @Test
    void nonePhaseReturnsZeroConfidence() {
        List<ElliottSwing> swings = createIdealImpulseSwings();
        ElliottConfidence confidence = scorer.score(swings, ElliottPhase.NONE, null);

        assertThat(confidence.overall().doubleValue()).isZero();
    }

    @Test
    void idealImpulseStructureScoresWell() {
        List<ElliottSwing> swings = createIdealImpulseSwings();
        ElliottConfidence confidence = scorer.score(swings, ElliottPhase.WAVE5, null);

        assertThat(confidence.overall().doubleValue()).isGreaterThan(0.5);
        assertThat(confidence.isValid()).isTrue();
    }

    @Test
    void fibonacciScoreForIdealRatios() {
        // Wave 1: 100 -> 120 (amp = 20)
        // Wave 2: 120 -> 108 (amp = 12, ratio = 0.6 - ideal for retracement)
        List<ElliottSwing> swings = List.of(
                new ElliottSwing(0, 5, numFactory.numOf(100), numFactory.numOf(120), ElliottDegree.MINOR),
                new ElliottSwing(5, 10, numFactory.numOf(120), numFactory.numOf(108), ElliottDegree.MINOR));

        Num fibScore = scorer.scoreFibonacci(swings, ElliottPhase.WAVE2);

        // 0.6 retracement is within 0.382-0.786 range
        assertThat(fibScore.doubleValue()).isGreaterThan(0.5);
    }

    @Test
    void fibonacciScoreReturnsZeroForCorrectivePhase() {
        // Fibonacci ratio rules are specific to impulse waves
        // Create swings that would score well if evaluated for impulse waves
        List<ElliottSwing> swings = List.of(
                new ElliottSwing(0, 5, numFactory.numOf(100), numFactory.numOf(120), ElliottDegree.MINOR),
                new ElliottSwing(5, 10, numFactory.numOf(120), numFactory.numOf(108), ElliottDegree.MINOR));

        // Test all corrective phases
        Num fibScoreA = scorer.scoreFibonacci(swings, ElliottPhase.CORRECTIVE_A);
        Num fibScoreB = scorer.scoreFibonacci(swings, ElliottPhase.CORRECTIVE_B);
        Num fibScoreC = scorer.scoreFibonacci(swings, ElliottPhase.CORRECTIVE_C);

        // Should return zero for all corrective phases
        assertThat(fibScoreA.doubleValue()).isZero();
        assertThat(fibScoreB.doubleValue()).isZero();
        assertThat(fibScoreC.doubleValue()).isZero();
    }

    @Test
    void fibonacciScoreWorksForAllImpulsePhases() {
        // Fibonacci ratios should be evaluated for all impulse phases
        List<ElliottSwing> swings = List.of(
                new ElliottSwing(0, 5, numFactory.numOf(100), numFactory.numOf(120), ElliottDegree.MINOR),
                new ElliottSwing(5, 10, numFactory.numOf(120), numFactory.numOf(108), ElliottDegree.MINOR));

        // Test all impulse phases - should evaluate Fibonacci ratios, not return zero
        Num fibScore1 = scorer.scoreFibonacci(swings, ElliottPhase.WAVE1);
        Num fibScore2 = scorer.scoreFibonacci(swings, ElliottPhase.WAVE2);
        Num fibScore3 = scorer.scoreFibonacci(swings, ElliottPhase.WAVE3);
        Num fibScore4 = scorer.scoreFibonacci(swings, ElliottPhase.WAVE4);
        Num fibScore5 = scorer.scoreFibonacci(swings, ElliottPhase.WAVE5);

        // All should evaluate Fibonacci ratios (not zero)
        // With ideal ratios, score should be > 0.5
        assertThat(fibScore1.doubleValue()).isGreaterThan(0.0);
        assertThat(fibScore2.doubleValue()).isGreaterThan(0.5);
        assertThat(fibScore3.doubleValue()).isGreaterThan(0.0);
        assertThat(fibScore4.doubleValue()).isGreaterThan(0.0);
        assertThat(fibScore5.doubleValue()).isGreaterThan(0.0);
    }

    @Test
    void fibonacciScorePenalizesUnderExtendedWaveThree() {
        // Ideal wave 3 extension versus a shallow (under-extended) wave 3.
        List<ElliottSwing> ideal = List.of(
                new ElliottSwing(0, 5, numFactory.numOf(100), numFactory.numOf(120), ElliottDegree.MINOR),
                new ElliottSwing(5, 10, numFactory.numOf(120), numFactory.numOf(108), ElliottDegree.MINOR),
                new ElliottSwing(10, 15, numFactory.numOf(108), numFactory.numOf(140.36), ElliottDegree.MINOR));

        List<ElliottSwing> underExtended = List.of(
                new ElliottSwing(0, 5, numFactory.numOf(100), numFactory.numOf(120), ElliottDegree.MINOR),
                new ElliottSwing(5, 10, numFactory.numOf(120), numFactory.numOf(108), ElliottDegree.MINOR),
                new ElliottSwing(10, 15, numFactory.numOf(108), numFactory.numOf(125), ElliottDegree.MINOR));

        Num idealScore = scorer.scoreFibonacci(ideal, ElliottPhase.WAVE3);
        Num underScore = scorer.scoreFibonacci(underExtended, ElliottPhase.WAVE3);

        assertThat(idealScore.doubleValue()).isGreaterThan(underScore.doubleValue());
        assertThat(underScore.doubleValue()).isLessThan(0.7);
    }

    @Test
    void fibonacciScoreReturnsZeroForNonePhase() {
        // NONE phase should return zero
        List<ElliottSwing> swings = List.of(
                new ElliottSwing(0, 5, numFactory.numOf(100), numFactory.numOf(120), ElliottDegree.MINOR),
                new ElliottSwing(5, 10, numFactory.numOf(120), numFactory.numOf(108), ElliottDegree.MINOR));

        Num fibScore = scorer.scoreFibonacci(swings, ElliottPhase.NONE);

        // NONE is not an impulse phase, so should return zero
        assertThat(fibScore.doubleValue()).isZero();
    }

    @Test
    void timeProportionScoreForBalancedWaves() {
        // Wave 1: 5 bars, Wave 3: 7 bars (wave 3 >= wave 1)
        List<ElliottSwing> swings = List.of(
                new ElliottSwing(0, 5, numFactory.numOf(100), numFactory.numOf(120), ElliottDegree.MINOR),
                new ElliottSwing(5, 8, numFactory.numOf(120), numFactory.numOf(110), ElliottDegree.MINOR),
                new ElliottSwing(8, 15, numFactory.numOf(110), numFactory.numOf(150), ElliottDegree.MINOR));

        Num timeScore = scorer.scoreTimeProportions(swings, ElliottPhase.WAVE3);

        assertThat(timeScore.doubleValue()).isGreaterThan(0.5);
    }

    @Test
    void timeProportionScoreReturnsNeutralForCorrectivePhase() {
        // Time proportion rules are specific to impulse waves
        // Create swings that would score well if evaluated for impulse waves
        List<ElliottSwing> swings = List.of(
                new ElliottSwing(0, 5, numFactory.numOf(100), numFactory.numOf(120), ElliottDegree.MINOR), // Wave 1: 5
                                                                                                           // bars
                new ElliottSwing(5, 8, numFactory.numOf(120), numFactory.numOf(110), ElliottDegree.MINOR),
                new ElliottSwing(8, 15, numFactory.numOf(110), numFactory.numOf(150), ElliottDegree.MINOR)); // Wave 3:
                                                                                                             // 7 bars

        // Test all corrective phases
        Num timeScoreA = scorer.scoreTimeProportions(swings, ElliottPhase.CORRECTIVE_A);
        Num timeScoreB = scorer.scoreTimeProportions(swings, ElliottPhase.CORRECTIVE_B);
        Num timeScoreC = scorer.scoreTimeProportions(swings, ElliottPhase.CORRECTIVE_C);

        // Should return neutral score (0.5) for all corrective phases
        assertThat(timeScoreA.doubleValue()).isCloseTo(0.5, org.assertj.core.api.Assertions.within(0.01));
        assertThat(timeScoreB.doubleValue()).isCloseTo(0.5, org.assertj.core.api.Assertions.within(0.01));
        assertThat(timeScoreC.doubleValue()).isCloseTo(0.5, org.assertj.core.api.Assertions.within(0.01));
    }

    @Test
    void timeProportionScoreWorksForAllImpulsePhases() {
        // Time proportions should be evaluated for all impulse phases
        List<ElliottSwing> swings = List.of(
                new ElliottSwing(0, 5, numFactory.numOf(100), numFactory.numOf(120), ElliottDegree.MINOR), // Wave 1: 5
                                                                                                           // bars
                new ElliottSwing(5, 8, numFactory.numOf(120), numFactory.numOf(110), ElliottDegree.MINOR),
                new ElliottSwing(8, 15, numFactory.numOf(110), numFactory.numOf(150), ElliottDegree.MINOR)); // Wave 3:
                                                                                                             // 7 bars

        // Test all impulse phases - should evaluate time proportions, not return
        // neutral
        Num timeScore1 = scorer.scoreTimeProportions(swings, ElliottPhase.WAVE1);
        Num timeScore2 = scorer.scoreTimeProportions(swings, ElliottPhase.WAVE2);
        Num timeScore3 = scorer.scoreTimeProportions(swings, ElliottPhase.WAVE3);
        Num timeScore4 = scorer.scoreTimeProportions(swings, ElliottPhase.WAVE4);
        Num timeScore5 = scorer.scoreTimeProportions(swings, ElliottPhase.WAVE5);

        // All should evaluate time proportions (not neutral 0.5)
        // With wave 3 >= wave 1, score should be > 0.5
        assertThat(timeScore1.doubleValue()).isGreaterThan(0.5);
        assertThat(timeScore2.doubleValue()).isGreaterThan(0.5);
        assertThat(timeScore3.doubleValue()).isGreaterThan(0.5);
        assertThat(timeScore4.doubleValue()).isGreaterThan(0.5);
        assertThat(timeScore5.doubleValue()).isGreaterThan(0.5);
    }

    @Test
    void timeProportionScoreReturnsNeutralForNonePhase() {
        // NONE phase should return neutral score
        List<ElliottSwing> swings = List.of(
                new ElliottSwing(0, 5, numFactory.numOf(100), numFactory.numOf(120), ElliottDegree.MINOR),
                new ElliottSwing(5, 8, numFactory.numOf(120), numFactory.numOf(110), ElliottDegree.MINOR),
                new ElliottSwing(8, 15, numFactory.numOf(110), numFactory.numOf(150), ElliottDegree.MINOR));

        Num timeScore = scorer.scoreTimeProportions(swings, ElliottPhase.NONE);

        // NONE is not an impulse phase, so should return neutral
        assertThat(timeScore.doubleValue()).isCloseTo(0.5, org.assertj.core.api.Assertions.within(0.01));
    }

    @Test
    void alternationScoreForContrastingWaves() {
        // Wave 2 shallow (ratio 0.3), Wave 4 deep (ratio 0.7) - good alternation
        List<ElliottSwing> swings = List.of(
                new ElliottSwing(0, 5, numFactory.numOf(100), numFactory.numOf(130), ElliottDegree.MINOR), // amp 30
                new ElliottSwing(5, 8, numFactory.numOf(130), numFactory.numOf(121), ElliottDegree.MINOR), // amp 9,
                                                                                                           // 0.3
                new ElliottSwing(8, 15, numFactory.numOf(121), numFactory.numOf(180), ElliottDegree.MINOR), // amp 59
                new ElliottSwing(15, 20, numFactory.numOf(180), numFactory.numOf(139), ElliottDegree.MINOR)); // amp 41,
                                                                                                              // 0.7

        Num altScore = scorer.scoreAlternation(swings, ElliottPhase.WAVE4);

        // Large difference in retracement ratios = good alternation
        assertThat(altScore.doubleValue()).isGreaterThan(0.3);
    }

    @Test
    void alternationScoreReturnsNeutralForCorrectivePhase() {
        // Alternation only applies to impulse waves, not corrective waves
        // Create swings that would show good alternation if evaluated
        List<ElliottSwing> swings = List.of(
                new ElliottSwing(0, 5, numFactory.numOf(100), numFactory.numOf(130), ElliottDegree.MINOR), // amp 30
                new ElliottSwing(5, 8, numFactory.numOf(130), numFactory.numOf(121), ElliottDegree.MINOR), // amp 9,
                                                                                                           // 0.3
                new ElliottSwing(8, 15, numFactory.numOf(121), numFactory.numOf(180), ElliottDegree.MINOR), // amp 59
                new ElliottSwing(15, 20, numFactory.numOf(180), numFactory.numOf(139), ElliottDegree.MINOR)); // amp 41,
                                                                                                              // 0.7

        // Test all corrective phases
        Num altScoreA = scorer.scoreAlternation(swings, ElliottPhase.CORRECTIVE_A);
        Num altScoreB = scorer.scoreAlternation(swings, ElliottPhase.CORRECTIVE_B);
        Num altScoreC = scorer.scoreAlternation(swings, ElliottPhase.CORRECTIVE_C);

        // Should return neutral score (0.5) for all corrective phases
        assertThat(altScoreA.doubleValue()).isCloseTo(0.5, org.assertj.core.api.Assertions.within(0.01));
        assertThat(altScoreB.doubleValue()).isCloseTo(0.5, org.assertj.core.api.Assertions.within(0.01));
        assertThat(altScoreC.doubleValue()).isCloseTo(0.5, org.assertj.core.api.Assertions.within(0.01));
    }

    @Test
    void alternationScoreWorksForAllImpulsePhases() {
        // Alternation should be evaluated for all impulse phases (WAVE1-WAVE5)
        List<ElliottSwing> swings = List.of(
                new ElliottSwing(0, 5, numFactory.numOf(100), numFactory.numOf(130), ElliottDegree.MINOR), // amp 30
                new ElliottSwing(5, 8, numFactory.numOf(130), numFactory.numOf(121), ElliottDegree.MINOR), // amp 9,
                                                                                                           // 0.3
                new ElliottSwing(8, 15, numFactory.numOf(121), numFactory.numOf(180), ElliottDegree.MINOR), // amp 59
                new ElliottSwing(15, 20, numFactory.numOf(180), numFactory.numOf(139), ElliottDegree.MINOR)); // amp 41,
                                                                                                              // 0.7

        // Test all impulse phases - should evaluate alternation, not return neutral
        Num altScore1 = scorer.scoreAlternation(swings, ElliottPhase.WAVE1);
        Num altScore2 = scorer.scoreAlternation(swings, ElliottPhase.WAVE2);
        Num altScore3 = scorer.scoreAlternation(swings, ElliottPhase.WAVE3);
        Num altScore4 = scorer.scoreAlternation(swings, ElliottPhase.WAVE4);
        Num altScore5 = scorer.scoreAlternation(swings, ElliottPhase.WAVE5);

        // All should evaluate alternation (not neutral 0.5)
        // With contrasting waves, score should be > 0.3
        assertThat(altScore1.doubleValue()).isGreaterThan(0.3);
        assertThat(altScore2.doubleValue()).isGreaterThan(0.3);
        assertThat(altScore3.doubleValue()).isGreaterThan(0.3);
        assertThat(altScore4.doubleValue()).isGreaterThan(0.3);
        assertThat(altScore5.doubleValue()).isGreaterThan(0.3);
    }

    @Test
    void alternationScoreReturnsNeutralForNonePhase() {
        // NONE phase should return neutral score
        List<ElliottSwing> swings = List.of(
                new ElliottSwing(0, 5, numFactory.numOf(100), numFactory.numOf(130), ElliottDegree.MINOR),
                new ElliottSwing(5, 8, numFactory.numOf(130), numFactory.numOf(121), ElliottDegree.MINOR),
                new ElliottSwing(8, 15, numFactory.numOf(121), numFactory.numOf(180), ElliottDegree.MINOR),
                new ElliottSwing(15, 20, numFactory.numOf(180), numFactory.numOf(139), ElliottDegree.MINOR));

        Num altScore = scorer.scoreAlternation(swings, ElliottPhase.NONE);

        // NONE is not an impulse phase, so should return neutral
        assertThat(altScore.doubleValue()).isCloseTo(0.5, org.assertj.core.api.Assertions.within(0.01));
    }

    @Test
    void completenessScoreForFullStructure() {
        List<ElliottSwing> swings = createIdealImpulseSwings();

        Num compScore = scorer.scoreCompleteness(swings, ElliottPhase.WAVE5);

        // 5 waves out of 5 expected = 1.0 + bonus
        assertThat(compScore.doubleValue()).isGreaterThan(0.99);
    }

    @Test
    void completenessScoreForPartialStructure() {
        List<ElliottSwing> swings = List.of(
                new ElliottSwing(0, 5, numFactory.numOf(100), numFactory.numOf(120), ElliottDegree.MINOR),
                new ElliottSwing(5, 10, numFactory.numOf(120), numFactory.numOf(110), ElliottDegree.MINOR),
                new ElliottSwing(10, 15, numFactory.numOf(110), numFactory.numOf(140), ElliottDegree.MINOR));

        Num compScore = scorer.scoreCompleteness(swings, ElliottPhase.WAVE3);

        // 3 waves out of 5 expected = 0.6
        assertThat(compScore.doubleValue()).isCloseTo(0.6, org.assertj.core.api.Assertions.within(0.01));
    }

    @Test
    void customWeightsAffectOverallScore() {
        // Create scorer with all weight on Fibonacci
        ElliottConfidenceScorer customScorer = new ElliottConfidenceScorer(numFactory, 1.0, 0.0, 0.0, 0.0, 0.0);

        List<ElliottSwing> swings = createIdealImpulseSwings();
        ElliottConfidence confidence = customScorer.score(swings, ElliottPhase.WAVE5, null);

        // Overall should equal the Fibonacci score with 100% weight
        assertThat(confidence.overall().doubleValue()).isCloseTo(confidence.fibonacciScore().doubleValue(),
                org.assertj.core.api.Assertions.within(0.01));
    }

    private List<ElliottSwing> createIdealImpulseSwings() {
        // Create a plausible 5-wave impulse structure
        return List.of(new ElliottSwing(0, 5, numFactory.numOf(100), numFactory.numOf(120), ElliottDegree.MINOR), // Wave
                                                                                                                  // 1
                new ElliottSwing(5, 10, numFactory.numOf(120), numFactory.numOf(108), ElliottDegree.MINOR), // Wave 2
                new ElliottSwing(10, 20, numFactory.numOf(108), numFactory.numOf(155), ElliottDegree.MINOR), // Wave 3
                new ElliottSwing(20, 25, numFactory.numOf(155), numFactory.numOf(140), ElliottDegree.MINOR), // Wave 4
                new ElliottSwing(25, 30, numFactory.numOf(140), numFactory.numOf(170), ElliottDegree.MINOR)); // Wave 5
    }
}

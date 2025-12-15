/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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

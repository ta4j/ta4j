/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.indicators.elliott.confidence.ConfidenceModel;
import org.ta4j.core.indicators.elliott.confidence.ConfidenceProfiles;
import org.ta4j.core.indicators.elliott.confidence.ElliottConfidenceBreakdown;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

class ElliottScenarioGeneratorTest {

    private NumFactory numFactory;
    private ElliottScenarioGenerator generator;

    @BeforeEach
    void setUp() {
        numFactory = DecimalNumFactory.getInstance();
        generator = new ElliottScenarioGenerator(numFactory);
    }

    @Test
    void emptySwingsReturnsEmptySet() {
        ElliottScenarioSet set = generator.generate(List.of(), ElliottDegree.MINOR, null);

        assertThat(set.isEmpty()).isTrue();
    }

    @Test
    void generatesImpulseScenarios() {
        List<ElliottSwing> swings = createAlternatingSwings();

        ElliottScenarioSet set = generator.generate(swings, ElliottDegree.MINOR, null, 10);

        assertThat(set.isEmpty()).isFalse();
        assertThat(set.barIndex()).isEqualTo(10);

        // Should find at least one impulse scenario
        boolean hasImpulse = set.all().stream().anyMatch(s -> s.type() == ScenarioType.IMPULSE);
        assertThat(hasImpulse).isTrue();
    }

    @Test
    void generatesCorrectiveScenarios() {
        // Create swings that could be interpreted as corrective
        List<ElliottSwing> swings = List.of(
                new ElliottSwing(0, 5, numFactory.numOf(120), numFactory.numOf(100), ElliottDegree.MINOR), // A down
                new ElliottSwing(5, 10, numFactory.numOf(100), numFactory.numOf(115), ElliottDegree.MINOR), // B up
                new ElliottSwing(10, 15, numFactory.numOf(115), numFactory.numOf(90), ElliottDegree.MINOR)); // C down

        ElliottScenarioSet set = generator.generate(swings, ElliottDegree.MINOR, null);

        // Should find corrective scenarios
        boolean hasCorrective = set.all().stream().anyMatch(s -> s.type().isCorrective());
        assertThat(hasCorrective).isTrue();
    }

    @Test
    void generatesTriangleCorrectiveScenarios() {
        ElliottScenarioGenerator triangleGenerator = new ElliottScenarioGenerator(numFactory, 0.0, 10,
                ConfidenceProfiles.defaultModel(numFactory), PatternSet.of(ScenarioType.CORRECTIVE_TRIANGLE));
        List<ElliottSwing> triangleLike = List.of(
                new ElliottSwing(0, 5, numFactory.numOf(120), numFactory.numOf(100), ElliottDegree.MINOR),
                new ElliottSwing(5, 10, numFactory.numOf(100), numFactory.numOf(114), ElliottDegree.MINOR),
                new ElliottSwing(10, 15, numFactory.numOf(114), numFactory.numOf(104), ElliottDegree.MINOR),
                new ElliottSwing(15, 20, numFactory.numOf(104), numFactory.numOf(111), ElliottDegree.MINOR),
                new ElliottSwing(20, 25, numFactory.numOf(111), numFactory.numOf(106), ElliottDegree.MINOR));

        ElliottScenarioSet set = triangleGenerator.generate(triangleLike, ElliottDegree.MINOR, null);

        assertThat(set.all()).anyMatch(scenario -> scenario.type() == ScenarioType.CORRECTIVE_TRIANGLE);
    }

    @Test
    void generatesComplexCorrectiveScenarios() {
        ElliottScenarioGenerator complexGenerator = new ElliottScenarioGenerator(numFactory, 0.0, 10,
                ConfidenceProfiles.defaultModel(numFactory), PatternSet.of(ScenarioType.CORRECTIVE_COMPLEX));
        List<ElliottSwing> complexLike = List.of(
                new ElliottSwing(0, 5, numFactory.numOf(120), numFactory.numOf(100), ElliottDegree.MINOR),
                new ElliottSwing(5, 10, numFactory.numOf(100), numFactory.numOf(124), ElliottDegree.MINOR),
                new ElliottSwing(10, 15, numFactory.numOf(124), numFactory.numOf(108), ElliottDegree.MINOR),
                new ElliottSwing(15, 20, numFactory.numOf(108), numFactory.numOf(130), ElliottDegree.MINOR));

        ElliottScenarioSet set = complexGenerator.generate(complexLike, ElliottDegree.MINOR, null);

        assertThat(set.all()).anyMatch(scenario -> scenario.type() == ScenarioType.CORRECTIVE_COMPLEX);
    }

    @Test
    void aggregatesNoisyRawSwingsIntoImpulseDecomposition() {
        ElliottScenarioGenerator decompositionGenerator = new ElliottScenarioGenerator(numFactory, 0.0, 10,
                ConfidenceProfiles.defaultModel(numFactory), PatternSet.of(ScenarioType.IMPULSE));
        List<ElliottSwing> noisyImpulse = List.of(
                new ElliottSwing(0, 5, numFactory.numOf(100), numFactory.numOf(120), ElliottDegree.MINOR),
                new ElliottSwing(5, 10, numFactory.numOf(120), numFactory.numOf(95), ElliottDegree.MINOR),
                new ElliottSwing(10, 15, numFactory.numOf(95), numFactory.numOf(126), ElliottDegree.MINOR),
                new ElliottSwing(15, 20, numFactory.numOf(126), numFactory.numOf(112), ElliottDegree.MINOR),
                new ElliottSwing(20, 25, numFactory.numOf(112), numFactory.numOf(145), ElliottDegree.MINOR),
                new ElliottSwing(25, 30, numFactory.numOf(145), numFactory.numOf(132), ElliottDegree.MINOR),
                new ElliottSwing(30, 35, numFactory.numOf(132), numFactory.numOf(168), ElliottDegree.MINOR),
                new ElliottSwing(35, 40, numFactory.numOf(168), numFactory.numOf(150), ElliottDegree.MINOR),
                new ElliottSwing(40, 45, numFactory.numOf(150), numFactory.numOf(190), ElliottDegree.MINOR));

        ElliottScenarioSet set = decompositionGenerator.generate(noisyImpulse, ElliottDegree.MINOR, null, 45);

        assertThat(set.all()).anyMatch(scenario -> scenario.type() == ScenarioType.IMPULSE
                && scenario.currentPhase() == ElliottPhase.WAVE5 && scenario.startIndex() == 0
                && scenario.swings().get(0).toIndex() == 15 && scenario.swings().size() == 5);
    }

    @Test
    void aggregatesNoisyRawSwingsIntoCorrectiveDecomposition() {
        ElliottScenarioGenerator decompositionGenerator = new ElliottScenarioGenerator(numFactory, 0.0, 10,
                ConfidenceProfiles.defaultModel(numFactory), PatternSet.of(ScenarioType.CORRECTIVE_ZIGZAG,
                        ScenarioType.CORRECTIVE_FLAT, ScenarioType.CORRECTIVE_COMPLEX));
        List<ElliottSwing> noisyCorrection = List.of(
                new ElliottSwing(0, 5, numFactory.numOf(200), numFactory.numOf(170), ElliottDegree.MINOR),
                new ElliottSwing(5, 10, numFactory.numOf(170), numFactory.numOf(185), ElliottDegree.MINOR),
                new ElliottSwing(10, 15, numFactory.numOf(185), numFactory.numOf(160), ElliottDegree.MINOR),
                new ElliottSwing(15, 20, numFactory.numOf(160), numFactory.numOf(180), ElliottDegree.MINOR),
                new ElliottSwing(20, 25, numFactory.numOf(180), numFactory.numOf(150), ElliottDegree.MINOR),
                new ElliottSwing(25, 30, numFactory.numOf(150), numFactory.numOf(162), ElliottDegree.MINOR),
                new ElliottSwing(30, 35, numFactory.numOf(162), numFactory.numOf(128), ElliottDegree.MINOR));

        ElliottScenarioSet set = decompositionGenerator.generate(noisyCorrection, ElliottDegree.MINOR, null, 35);

        assertThat(set.all()).anyMatch(scenario -> scenario.type().isCorrective()
                && scenario.currentPhase() == ElliottPhase.CORRECTIVE_C && scenario.startIndex() == 0
                && scenario.swings().get(0).toIndex() == 15 && scenario.swings().size() == 3);
    }

    @Test
    void scenariosSortedByConfidence() {
        List<ElliottSwing> swings = createAlternatingSwings();

        ElliottScenarioSet set = generator.generate(swings, ElliottDegree.MINOR, null);

        List<ElliottScenario> all = set.all();
        if (all.size() >= 2) {
            for (int i = 1; i < all.size(); i++) {
                double prev = all.get(i - 1).confidenceScore().doubleValue();
                double curr = all.get(i).confidenceScore().doubleValue();
                assertThat(prev).isGreaterThan(curr - 0.001);
            }
        }
    }

    @Test
    void prunesLowConfidenceScenarios() {
        // Use a high minimum confidence threshold
        ElliottScenarioGenerator strictGenerator = new ElliottScenarioGenerator(numFactory, 0.9, 5);

        List<ElliottSwing> swings = createAlternatingSwings();
        ElliottScenarioSet set = strictGenerator.generate(swings, ElliottDegree.MINOR, null);

        // All scenarios should meet the minimum threshold
        for (ElliottScenario scenario : set.all()) {
            assertThat(scenario.confidenceScore().doubleValue()).isGreaterThan(0.89);
        }
    }

    @Test
    void minConfidenceUsesNumPrecision() {
        NumFactory highPrecision = DecimalNumFactory.getInstance(32);
        Num borderline = highPrecision.numOf("0.69999999999999999999");
        ConfidenceModel model = (swings, phase, channel, scenarioType) -> {
            ElliottConfidence confidence = new ElliottConfidence(borderline, borderline, borderline, borderline,
                    borderline, borderline, "Test");
            return new ElliottConfidenceBreakdown(confidence, List.of());
        };

        ElliottScenarioGenerator preciseGenerator = new ElliottScenarioGenerator(highPrecision, 0.7, 5, model,
                PatternSet.of(ScenarioType.IMPULSE));

        List<ElliottSwing> swings = List
                .of(new ElliottSwing(0, 1, highPrecision.one(), highPrecision.two(), ElliottDegree.MINOR));

        ElliottScenarioSet set = preciseGenerator.generate(swings, ElliottDegree.MINOR, null, 1);

        assertThat(set.isEmpty()).isTrue();
    }

    @Test
    void limitsMaxScenarios() {
        ElliottScenarioGenerator limitedGenerator = new ElliottScenarioGenerator(numFactory, 0.0, 2);

        List<ElliottSwing> swings = createAlternatingSwings();
        ElliottScenarioSet set = limitedGenerator.generate(swings, ElliottDegree.MINOR, null);

        assertThat(set.size()).isLessThan(3);
    }

    @Test
    void invalidStructureRejected() {
        // Create swings where wave 2 retraces below wave 1 start (invalid for bullish)
        List<ElliottSwing> invalidSwings = List.of(
                new ElliottSwing(0, 5, numFactory.numOf(100), numFactory.numOf(120), ElliottDegree.MINOR), // Wave 1 up
                new ElliottSwing(5, 10, numFactory.numOf(120), numFactory.numOf(90), ElliottDegree.MINOR)); // Wave 2
                                                                                                            // below
                                                                                                            // start

        ElliottScenarioSet set = generator.generate(invalidSwings, ElliottDegree.MINOR, null);

        // Should not have a high-confidence impulse scenario starting at index 0
        boolean hasValidImpulse = set.all()
                .stream()
                .filter(s -> s.type() == ScenarioType.IMPULSE && s.startIndex() == 0)
                .anyMatch(ElliottScenario::isHighConfidence);
        assertThat(hasValidImpulse).isFalse();
    }

    @Test
    void structureScoreSoftensImpulseRuleBreachesInsteadOfHardRejectingThem() {
        ElliottScenarioGenerator permissiveGenerator = new ElliottScenarioGenerator(numFactory, 0.0, 10);
        List<ElliottSwing> mildlyInvalid = List.of(
                new ElliottSwing(0, 5, numFactory.numOf(100), numFactory.numOf(120), ElliottDegree.MINOR),
                new ElliottSwing(5, 10, numFactory.numOf(120), numFactory.numOf(98), ElliottDegree.MINOR));
        List<ElliottSwing> severelyInvalid = List.of(
                new ElliottSwing(0, 5, numFactory.numOf(100), numFactory.numOf(120), ElliottDegree.MINOR),
                new ElliottSwing(5, 10, numFactory.numOf(120), numFactory.numOf(70), ElliottDegree.MINOR));

        double mildScore = permissiveGenerator.scoreImpulseStructure(mildlyInvalid, ElliottPhase.WAVE2);
        double severeScore = permissiveGenerator.scoreImpulseStructure(severelyInvalid, ElliottPhase.WAVE2);
        ElliottScenarioSet set = permissiveGenerator.generate(mildlyInvalid, ElliottDegree.MINOR, null);

        assertThat(mildScore).isGreaterThan(severeScore);
        assertThat(severeScore).isZero();
        assertThat(set.all())
                .anyMatch(scenario -> scenario.type() == ScenarioType.IMPULSE && scenario.startIndex() == 0);
        assertThat(set.all())
                .filteredOn(scenario -> scenario.type() == ScenarioType.IMPULSE && scenario.startIndex() == 0)
                .allMatch(scenario -> !scenario.isHighConfidence());
    }

    @Test
    void generatesProjectionTargets() {
        List<ElliottSwing> swings = createAlternatingSwings();

        ElliottScenarioSet set = generator.generate(swings, ElliottDegree.MINOR, null);

        // Base case scenario should have targets
        if (set.base().isPresent()) {
            ElliottScenario baseCase = set.base().get();
            // Scenarios beyond wave 1 should have targets
            if (baseCase.waveCount() >= 2) {
                assertThat(baseCase.fibonacciTargets()).isNotEmpty();
            }
        }
    }

    @Test
    void scenariosHaveInvalidationLevels() {
        List<ElliottSwing> swings = createAlternatingSwings();

        ElliottScenarioSet set = generator.generate(swings, ElliottDegree.MINOR, null);

        for (ElliottScenario scenario : set.all()) {
            // Scenarios with swings should have invalidation levels
            if (scenario.waveCount() > 0) {
                assertThat(scenario.invalidationPrice()).isNotNull();
            }
        }
    }

    @Test
    void exploresImpulseStartsBeyondTheFirstThreeSwings() {
        ElliottScenarioGenerator wideSearchGenerator = new ElliottScenarioGenerator(numFactory, 0.0, 20);
        List<ElliottSwing> swings = List.of(
                new ElliottSwing(0, 5, numFactory.hundred(), numFactory.numOf(110), ElliottDegree.MINOR),
                new ElliottSwing(5, 10, numFactory.numOf(110), numFactory.numOf(120), ElliottDegree.MINOR),
                new ElliottSwing(10, 15, numFactory.numOf(120), numFactory.numOf(130), ElliottDegree.MINOR),
                new ElliottSwing(15, 20, numFactory.numOf(130), numFactory.numOf(150), ElliottDegree.MINOR),
                new ElliottSwing(20, 25, numFactory.numOf(150), numFactory.numOf(140), ElliottDegree.MINOR),
                new ElliottSwing(25, 30, numFactory.numOf(140), numFactory.numOf(170), ElliottDegree.MINOR),
                new ElliottSwing(30, 35, numFactory.numOf(170), numFactory.numOf(155), ElliottDegree.MINOR),
                new ElliottSwing(35, 40, numFactory.numOf(155), numFactory.numOf(190), ElliottDegree.MINOR));

        ElliottScenarioSet set = wideSearchGenerator.generate(swings, ElliottDegree.MINOR, null, 40);

        assertThat(set.all()).anyMatch(scenario -> scenario.type() == ScenarioType.IMPULSE
                && scenario.currentPhase() == ElliottPhase.WAVE5 && scenario.startIndex() == 3);
    }

    private List<ElliottSwing> createAlternatingSwings() {
        // Create properly alternating swings for impulse detection
        return List.of(new ElliottSwing(0, 5, numFactory.numOf(100), numFactory.numOf(120), ElliottDegree.MINOR),
                new ElliottSwing(5, 10, numFactory.numOf(120), numFactory.numOf(110), ElliottDegree.MINOR),
                new ElliottSwing(10, 15, numFactory.numOf(110), numFactory.numOf(140), ElliottDegree.MINOR),
                new ElliottSwing(15, 20, numFactory.numOf(140), numFactory.numOf(125), ElliottDegree.MINOR),
                new ElliottSwing(20, 25, numFactory.numOf(125), numFactory.numOf(160), ElliottDegree.MINOR));
    }
}

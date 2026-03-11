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
    void decompositionSearchStillFindsImpulseAcrossLongSwingHistory() {
        ElliottScenarioGenerator decompositionGenerator = new ElliottScenarioGenerator(numFactory, 0.0, 10,
                ConfidenceProfiles.defaultModel(numFactory), PatternSet.of(ScenarioType.IMPULSE));
        List<ElliottSwing> longNoisyImpulse = List.of(
                new ElliottSwing(0, 2, numFactory.numOf(100), numFactory.numOf(108), ElliottDegree.MINOR),
                new ElliottSwing(2, 4, numFactory.numOf(108), numFactory.numOf(104), ElliottDegree.MINOR),
                new ElliottSwing(4, 6, numFactory.numOf(104), numFactory.numOf(120), ElliottDegree.MINOR),
                new ElliottSwing(6, 8, numFactory.numOf(120), numFactory.numOf(112), ElliottDegree.MINOR),
                new ElliottSwing(8, 10, numFactory.numOf(112), numFactory.numOf(126), ElliottDegree.MINOR),
                new ElliottSwing(10, 12, numFactory.numOf(126), numFactory.numOf(119), ElliottDegree.MINOR),
                new ElliottSwing(12, 14, numFactory.numOf(119), numFactory.numOf(142), ElliottDegree.MINOR),
                new ElliottSwing(14, 16, numFactory.numOf(142), numFactory.numOf(135), ElliottDegree.MINOR),
                new ElliottSwing(16, 18, numFactory.numOf(135), numFactory.numOf(150), ElliottDegree.MINOR),
                new ElliottSwing(18, 20, numFactory.numOf(150), numFactory.numOf(144), ElliottDegree.MINOR),
                new ElliottSwing(20, 22, numFactory.numOf(144), numFactory.numOf(162), ElliottDegree.MINOR),
                new ElliottSwing(22, 24, numFactory.numOf(162), numFactory.numOf(154), ElliottDegree.MINOR),
                new ElliottSwing(24, 26, numFactory.numOf(154), numFactory.numOf(178), ElliottDegree.MINOR),
                new ElliottSwing(26, 28, numFactory.numOf(178), numFactory.numOf(168), ElliottDegree.MINOR),
                new ElliottSwing(28, 30, numFactory.numOf(168), numFactory.numOf(190), ElliottDegree.MINOR),
                new ElliottSwing(30, 32, numFactory.numOf(190), numFactory.numOf(182), ElliottDegree.MINOR),
                new ElliottSwing(32, 34, numFactory.numOf(182), numFactory.numOf(205), ElliottDegree.MINOR));

        ElliottScenarioSet set = decompositionGenerator.generate(longNoisyImpulse, ElliottDegree.MINOR, null, 34);

        assertThat(set.all()).anyMatch(
                scenario -> scenario.type() == ScenarioType.IMPULSE && scenario.id().startsWith("impulse-decomp")
                        && scenario.currentPhase() == ElliottPhase.WAVE5 && scenario.swings().size() == 5);
    }

    @Test
    void decompositionSearchPrunesNonAlternatingCutPathsBeforeFullScoring() {
        ElliottScenarioGenerator decompositionGenerator = new ElliottScenarioGenerator(numFactory, 0.0, 10,
                ConfidenceProfiles.defaultModel(numFactory), PatternSet.of(ScenarioType.IMPULSE));
        List<ElliottSwing> longNoisyImpulse = List.of(
                new ElliottSwing(0, 2, numFactory.numOf(100), numFactory.numOf(108), ElliottDegree.MINOR),
                new ElliottSwing(2, 4, numFactory.numOf(108), numFactory.numOf(104), ElliottDegree.MINOR),
                new ElliottSwing(4, 6, numFactory.numOf(104), numFactory.numOf(120), ElliottDegree.MINOR),
                new ElliottSwing(6, 8, numFactory.numOf(120), numFactory.numOf(112), ElliottDegree.MINOR),
                new ElliottSwing(8, 10, numFactory.numOf(112), numFactory.numOf(126), ElliottDegree.MINOR),
                new ElliottSwing(10, 12, numFactory.numOf(126), numFactory.numOf(119), ElliottDegree.MINOR),
                new ElliottSwing(12, 14, numFactory.numOf(119), numFactory.numOf(142), ElliottDegree.MINOR),
                new ElliottSwing(14, 16, numFactory.numOf(142), numFactory.numOf(135), ElliottDegree.MINOR),
                new ElliottSwing(16, 18, numFactory.numOf(135), numFactory.numOf(150), ElliottDegree.MINOR),
                new ElliottSwing(18, 20, numFactory.numOf(150), numFactory.numOf(144), ElliottDegree.MINOR),
                new ElliottSwing(20, 22, numFactory.numOf(144), numFactory.numOf(162), ElliottDegree.MINOR),
                new ElliottSwing(22, 24, numFactory.numOf(162), numFactory.numOf(154), ElliottDegree.MINOR),
                new ElliottSwing(24, 26, numFactory.numOf(154), numFactory.numOf(178), ElliottDegree.MINOR),
                new ElliottSwing(26, 28, numFactory.numOf(178), numFactory.numOf(168), ElliottDegree.MINOR),
                new ElliottSwing(28, 30, numFactory.numOf(168), numFactory.numOf(190), ElliottDegree.MINOR),
                new ElliottSwing(30, 32, numFactory.numOf(190), numFactory.numOf(182), ElliottDegree.MINOR),
                new ElliottSwing(32, 34, numFactory.numOf(182), numFactory.numOf(205), ElliottDegree.MINOR));

        ElliottScenarioSet set = decompositionGenerator.generate(longNoisyImpulse, ElliottDegree.MINOR, null, 34);
        ElliottAnalysisResult.AnalysisDiagnostics diagnostics = decompositionGenerator.lastDiagnostics();

        assertThat(set.all()).anyMatch(
                scenario -> scenario.type() == ScenarioType.IMPULSE && scenario.id().startsWith("impulse-decomp")
                        && scenario.currentPhase() == ElliottPhase.WAVE5 && scenario.swings().size() == 5);
        assertThat(diagnostics.impulseDecompositionBranchCount())
                .isLessThan(naiveImpulseDecompositionBranchCount(longNoisyImpulse.size()));
        assertThat(diagnostics.impulseDecompositionPrunedBranchCount()).isPositive();
        assertThat(diagnostics.totalImpulseDecompositionBranchCount()).isEqualTo(
                diagnostics.impulseDecompositionBranchCount() + diagnostics.impulseDecompositionPrunedBranchCount());
        assertThat(diagnostics.impulsePruningHitRate()).isGreaterThan(0.0);
    }

    @Test
    void decompositionSearchPrunesWaveTwoInvalidationBeforeFullScoring() {
        ElliottScenarioGenerator decompositionGenerator = new ElliottScenarioGenerator(numFactory, 0.0, 10,
                ConfidenceProfiles.defaultModel(numFactory), PatternSet.of(ScenarioType.IMPULSE));
        List<ElliottSwing> invalidWaveTwoImpulse = List.of(
                new ElliottSwing(0, 2, numFactory.numOf(100), numFactory.numOf(120), ElliottDegree.MINOR),
                new ElliottSwing(2, 4, numFactory.numOf(120), numFactory.numOf(95), ElliottDegree.MINOR),
                new ElliottSwing(4, 6, numFactory.numOf(95), numFactory.numOf(130), ElliottDegree.MINOR),
                new ElliottSwing(6, 8, numFactory.numOf(130), numFactory.numOf(97), ElliottDegree.MINOR),
                new ElliottSwing(8, 10, numFactory.numOf(97), numFactory.numOf(145), ElliottDegree.MINOR),
                new ElliottSwing(10, 12, numFactory.numOf(145), numFactory.numOf(96), ElliottDegree.MINOR),
                new ElliottSwing(12, 14, numFactory.numOf(96), numFactory.numOf(160), ElliottDegree.MINOR),
                new ElliottSwing(14, 16, numFactory.numOf(160), numFactory.numOf(98), ElliottDegree.MINOR),
                new ElliottSwing(16, 18, numFactory.numOf(98), numFactory.numOf(175), ElliottDegree.MINOR));

        ElliottScenarioSet set = decompositionGenerator.generate(invalidWaveTwoImpulse, ElliottDegree.MINOR, null, 18);
        ElliottAnalysisResult.AnalysisDiagnostics diagnostics = decompositionGenerator.lastDiagnostics();

        assertThat(set.all())
                .noneMatch(scenario -> scenario.id().startsWith("impulse-decomp") && scenario.startIndex() == 0);
        assertThat(diagnostics.impulseDecompositionPrunedBranchCount()).isPositive();
        assertThat(diagnostics.totalImpulseDecompositionBranchCount())
                .isLessThan(naiveImpulseDecompositionBranchCount(invalidWaveTwoImpulse.size()));
    }

    @Test
    void decompositionSearchPrunesWaveFourOverlapBeforeFullScoring() {
        ElliottScenarioGenerator decompositionGenerator = new ElliottScenarioGenerator(numFactory, 0.0, 10,
                ConfidenceProfiles.defaultModel(numFactory), PatternSet.of(ScenarioType.IMPULSE));
        List<ElliottSwing> overlappingWaveFourImpulse = List.of(
                new ElliottSwing(0, 2, numFactory.numOf(100), numFactory.numOf(120), ElliottDegree.MINOR),
                new ElliottSwing(2, 4, numFactory.numOf(120), numFactory.numOf(108), ElliottDegree.MINOR),
                new ElliottSwing(4, 6, numFactory.numOf(108), numFactory.numOf(135), ElliottDegree.MINOR),
                new ElliottSwing(6, 8, numFactory.numOf(135), numFactory.numOf(112), ElliottDegree.MINOR),
                new ElliottSwing(8, 10, numFactory.numOf(112), numFactory.numOf(150), ElliottDegree.MINOR),
                new ElliottSwing(10, 12, numFactory.numOf(150), numFactory.numOf(115), ElliottDegree.MINOR),
                new ElliottSwing(12, 14, numFactory.numOf(115), numFactory.numOf(168), ElliottDegree.MINOR),
                new ElliottSwing(14, 16, numFactory.numOf(168), numFactory.numOf(118), ElliottDegree.MINOR),
                new ElliottSwing(16, 18, numFactory.numOf(118), numFactory.numOf(182), ElliottDegree.MINOR));

        ElliottScenarioSet set = decompositionGenerator.generate(overlappingWaveFourImpulse, ElliottDegree.MINOR, null,
                18);
        ElliottAnalysisResult.AnalysisDiagnostics diagnostics = decompositionGenerator.lastDiagnostics();

        assertThat(set.all()).noneMatch(scenario -> scenario.id().startsWith("impulse-decomp")
                && scenario.startIndex() == 0 && scenario.currentPhase() == ElliottPhase.WAVE5);
        assertThat(diagnostics.impulseDecompositionPrunedBranchCount()).isPositive();
        assertThat(diagnostics.totalImpulseDecompositionBranchCount())
                .isLessThan(naiveImpulseDecompositionBranchCount(overlappingWaveFourImpulse.size()));
    }

    @Test
    void decompositionSearchPrunesWaveBRetracementBeforeFullScoring() {
        ElliottScenarioGenerator decompositionGenerator = new ElliottScenarioGenerator(numFactory, 0.0, 10,
                ConfidenceProfiles.defaultModel(numFactory), PatternSet.of(ScenarioType.CORRECTIVE_ZIGZAG,
                        ScenarioType.CORRECTIVE_FLAT, ScenarioType.CORRECTIVE_COMPLEX));
        ElliottFibonacciValidator fibValidator = new ElliottFibonacciValidator(numFactory);
        List<ElliottSwing> invalidWaveBCorrection = List.of(
                new ElliottSwing(0, 2, numFactory.numOf(200), numFactory.numOf(150), ElliottDegree.MINOR),
                new ElliottSwing(2, 4, numFactory.numOf(150), numFactory.numOf(198), ElliottDegree.MINOR),
                new ElliottSwing(4, 6, numFactory.numOf(198), numFactory.numOf(142), ElliottDegree.MINOR),
                new ElliottSwing(6, 8, numFactory.numOf(142), numFactory.numOf(190), ElliottDegree.MINOR),
                new ElliottSwing(8, 10, numFactory.numOf(190), numFactory.numOf(138), ElliottDegree.MINOR),
                new ElliottSwing(10, 12, numFactory.numOf(138), numFactory.numOf(184), ElliottDegree.MINOR),
                new ElliottSwing(12, 14, numFactory.numOf(184), numFactory.numOf(126), ElliottDegree.MINOR));

        ElliottScenarioSet set = decompositionGenerator.generate(invalidWaveBCorrection, ElliottDegree.MINOR, null, 14);
        ElliottAnalysisResult.AnalysisDiagnostics diagnostics = decompositionGenerator.lastDiagnostics();

        assertThat(set.all())
                .filteredOn(scenario -> scenario.id().startsWith("corrective-decomp")
                        && scenario.currentPhase() == ElliottPhase.CORRECTIVE_C)
                .allMatch(scenario -> fibValidator.isWaveBRetracementValid(scenario.swings().get(0),
                        scenario.swings().get(1)));
        assertThat(diagnostics.correctiveDecompositionPrunedBranchCount()).isPositive();
        assertThat(diagnostics.totalCorrectiveDecompositionBranchCount())
                .isLessThan(naiveCorrectiveDecompositionBranchCount(invalidWaveBCorrection.size()));
        assertThat(diagnostics.correctivePruningHitRate()).isGreaterThan(0.0);
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
                && scenario.currentPhase() == ElliottPhase.WAVE5 && scenario.startIndex() == 15);
    }

    @Test
    void pruningRetainsStartDiverseScenariosWhenLaterStartsHaveHigherConfidence() {
        ConfidenceModel laterStartBiasedModel = (swings, phase, channel, scenarioType) -> {
            double rawScore = 0.2 + (swings.getFirst().fromIndex() / 40.0);
            double boundedScore = Math.min(0.95, rawScore);
            Num score = numFactory.numOf(boundedScore);
            ElliottConfidence confidence = new ElliottConfidence(score, score, score, score, score, score,
                    "Later starts receive higher raw confidence");
            return new ElliottConfidenceBreakdown(confidence, List.of());
        };
        ElliottScenarioGenerator wideGenerator = new ElliottScenarioGenerator(numFactory, 0.0, 200,
                laterStartBiasedModel, PatternSet.of(ScenarioType.IMPULSE));
        ElliottScenarioGenerator limitedGenerator = new ElliottScenarioGenerator(numFactory, 0.0, 4,
                laterStartBiasedModel, PatternSet.of(ScenarioType.IMPULSE));
        List<ElliottSwing> swings = List.of(
                new ElliottSwing(0, 5, numFactory.numOf(100), numFactory.numOf(120), ElliottDegree.MINOR),
                new ElliottSwing(5, 10, numFactory.numOf(120), numFactory.numOf(110), ElliottDegree.MINOR),
                new ElliottSwing(10, 15, numFactory.numOf(110), numFactory.numOf(130), ElliottDegree.MINOR),
                new ElliottSwing(15, 20, numFactory.numOf(130), numFactory.numOf(118), ElliottDegree.MINOR),
                new ElliottSwing(20, 25, numFactory.numOf(118), numFactory.numOf(142), ElliottDegree.MINOR),
                new ElliottSwing(25, 30, numFactory.numOf(142), numFactory.numOf(129), ElliottDegree.MINOR),
                new ElliottSwing(30, 35, numFactory.numOf(129), numFactory.numOf(158), ElliottDegree.MINOR),
                new ElliottSwing(35, 40, numFactory.numOf(158), numFactory.numOf(146), ElliottDegree.MINOR),
                new ElliottSwing(40, 45, numFactory.numOf(146), numFactory.numOf(175), ElliottDegree.MINOR));

        ElliottScenarioSet allCandidates = wideGenerator.generate(swings, ElliottDegree.MINOR, null, 45);
        ElliottScenarioSet limitedSet = limitedGenerator.generate(swings, ElliottDegree.MINOR, null, 45);

        int earliestStart = allCandidates.all().stream().mapToInt(ElliottScenario::startIndex).min().orElseThrow();

        assertThat(allCandidates.all()).hasSizeGreaterThan(4);
        assertThat(allCandidates.all().stream().limit(4).map(ElliottScenario::startIndex).toList())
                .doesNotContain(earliestStart);
        assertThat(limitedSet.all().stream().map(ElliottScenario::startIndex).toList()).contains(earliestStart);
        assertThat(limitedSet.all().stream().map(ElliottScenario::startIndex).distinct().count()).isGreaterThan(1);
    }

    private List<ElliottSwing> createAlternatingSwings() {
        // Create properly alternating swings for impulse detection
        return List.of(new ElliottSwing(0, 5, numFactory.numOf(100), numFactory.numOf(120), ElliottDegree.MINOR),
                new ElliottSwing(5, 10, numFactory.numOf(120), numFactory.numOf(110), ElliottDegree.MINOR),
                new ElliottSwing(10, 15, numFactory.numOf(110), numFactory.numOf(140), ElliottDegree.MINOR),
                new ElliottSwing(15, 20, numFactory.numOf(140), numFactory.numOf(125), ElliottDegree.MINOR),
                new ElliottSwing(20, 25, numFactory.numOf(125), numFactory.numOf(160), ElliottDegree.MINOR));
    }

    private int naiveImpulseDecompositionBranchCount(int swingCount) {
        int total = 0;
        for (int startIndex = 0; startIndex < swingCount; startIndex++) {
            int segmentSize = swingCount - startIndex;
            int internalPivots = segmentSize - 1;
            for (int waveCount = 2; waveCount <= Math.min(5, segmentSize); waveCount++) {
                total += combinations(internalPivots, waveCount - 1);
            }
        }
        return total;
    }

    private int naiveCorrectiveDecompositionBranchCount(int swingCount) {
        int total = 0;
        for (int startIndex = 0; startIndex < swingCount; startIndex++) {
            int segmentSize = swingCount - startIndex;
            int internalPivots = segmentSize - 1;
            for (int waveCount = 2; waveCount <= Math.min(3, segmentSize); waveCount++) {
                total += combinations(internalPivots, waveCount - 1);
            }
        }
        return total;
    }

    private int combinations(int n, int k) {
        if (k < 0 || k > n) {
            return 0;
        }
        if (k == 0 || k == n) {
            return 1;
        }
        long numerator = 1;
        long denominator = 1;
        int effectiveK = Math.min(k, n - k);
        for (int i = 1; i <= effectiveK; i++) {
            numerator *= (n - effectiveK + i);
            denominator *= i;
        }
        return Math.toIntExact(numerator / denominator);
    }
}

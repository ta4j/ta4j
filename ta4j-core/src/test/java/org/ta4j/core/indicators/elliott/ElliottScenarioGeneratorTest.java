/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.num.DecimalNumFactory;
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

    private List<ElliottSwing> createAlternatingSwings() {
        // Create properly alternating swings for impulse detection
        return List.of(new ElliottSwing(0, 5, numFactory.numOf(100), numFactory.numOf(120), ElliottDegree.MINOR),
                new ElliottSwing(5, 10, numFactory.numOf(120), numFactory.numOf(110), ElliottDegree.MINOR),
                new ElliottSwing(10, 15, numFactory.numOf(110), numFactory.numOf(140), ElliottDegree.MINOR),
                new ElliottSwing(15, 20, numFactory.numOf(140), numFactory.numOf(125), ElliottDegree.MINOR),
                new ElliottSwing(20, 25, numFactory.numOf(125), numFactory.numOf(160), ElliottDegree.MINOR));
    }
}

/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

class ElliottScenarioTest {

    private final NumFactory numFactory = DecimalNumFactory.getInstance();

    @Test
    void builderCreatesValidScenario() {
        ElliottConfidence confidence = createConfidence(0.8);

        ElliottScenario scenario = ElliottScenario.builder()
                .id("test-1")
                .currentPhase(ElliottPhase.WAVE3)
                .confidence(confidence)
                .degree(ElliottDegree.INTERMEDIATE)
                .type(ScenarioType.IMPULSE)
                .invalidationPrice(numFactory.numOf(100))
                .primaryTarget(numFactory.numOf(150))
                .build();

        assertThat(scenario.id()).isEqualTo("test-1");
        assertThat(scenario.currentPhase()).isEqualTo(ElliottPhase.WAVE3);
        assertThat(scenario.isHighConfidence()).isTrue();
        assertThat(scenario.type()).isEqualTo(ScenarioType.IMPULSE);
    }

    @Test
    void bullishScenarioDetection() {
        List<ElliottSwing> bullishSwings = List
                .of(new ElliottSwing(0, 5, numFactory.numOf(100), numFactory.numOf(120), ElliottDegree.MINOR));

        ElliottScenario scenario = ElliottScenario.builder()
                .id("bullish-1")
                .currentPhase(ElliottPhase.WAVE1)
                .swings(bullishSwings)
                .confidence(createConfidence(0.7))
                .type(ScenarioType.IMPULSE)
                .build();

        assertThat(scenario.isBullish()).isTrue();
        assertThat(scenario.isBearish()).isFalse();
    }

    @Test
    void bearishScenarioDetection() {
        List<ElliottSwing> bearishSwings = List
                .of(new ElliottSwing(0, 5, numFactory.numOf(120), numFactory.numOf(100), ElliottDegree.MINOR));

        ElliottScenario scenario = ElliottScenario.builder()
                .id("bearish-1")
                .currentPhase(ElliottPhase.WAVE1)
                .swings(bearishSwings)
                .confidence(createConfidence(0.7))
                .type(ScenarioType.IMPULSE)
                .build();

        assertThat(scenario.isBullish()).isFalse();
        assertThat(scenario.isBearish()).isTrue();
    }

    @Test
    void invalidationByPrice() {
        List<ElliottSwing> bullishSwings = List
                .of(new ElliottSwing(0, 5, numFactory.numOf(100), numFactory.numOf(120), ElliottDegree.MINOR));

        ElliottScenario scenario = ElliottScenario.builder()
                .id("test-1")
                .currentPhase(ElliottPhase.WAVE2)
                .swings(bullishSwings)
                .confidence(createConfidence(0.7))
                .type(ScenarioType.IMPULSE)
                .invalidationPrice(numFactory.numOf(100))
                .build();

        // For bullish, price below invalidation = invalid
        assertThat(scenario.isInvalidatedBy(numFactory.numOf(95))).isTrue();
        assertThat(scenario.isInvalidatedBy(numFactory.numOf(105))).isFalse();
    }

    @Test
    void expectsCompletion() {
        ElliottScenario wave5 = ElliottScenario.builder()
                .id("wave5")
                .currentPhase(ElliottPhase.WAVE5)
                .confidence(createConfidence(0.6))
                .type(ScenarioType.IMPULSE)
                .build();

        ElliottScenario waveC = ElliottScenario.builder()
                .id("waveC")
                .currentPhase(ElliottPhase.CORRECTIVE_C)
                .confidence(createConfidence(0.6))
                .type(ScenarioType.CORRECTIVE_ZIGZAG)
                .build();

        ElliottScenario wave3 = ElliottScenario.builder()
                .id("wave3")
                .currentPhase(ElliottPhase.WAVE3)
                .confidence(createConfidence(0.6))
                .type(ScenarioType.IMPULSE)
                .build();

        assertThat(wave5.expectsCompletion()).isTrue();
        assertThat(waveC.expectsCompletion()).isTrue();
        assertThat(wave3.expectsCompletion()).isFalse();
    }

    @Test
    void waveCount() {
        List<ElliottSwing> swings = List.of(
                new ElliottSwing(0, 5, numFactory.numOf(100), numFactory.numOf(120), ElliottDegree.MINOR),
                new ElliottSwing(5, 10, numFactory.numOf(120), numFactory.numOf(110), ElliottDegree.MINOR),
                new ElliottSwing(10, 15, numFactory.numOf(110), numFactory.numOf(140), ElliottDegree.MINOR));

        ElliottScenario scenario = ElliottScenario.builder()
                .id("test")
                .currentPhase(ElliottPhase.WAVE3)
                .swings(swings)
                .confidence(createConfidence(0.7))
                .type(ScenarioType.IMPULSE)
                .build();

        assertThat(scenario.waveCount()).isEqualTo(3);
    }

    @Test
    void hasKnownDirectionWithSwings() {
        List<ElliottSwing> swings = List
                .of(new ElliottSwing(0, 5, numFactory.numOf(100), numFactory.numOf(120), ElliottDegree.MINOR));

        ElliottScenario scenario = ElliottScenario.builder()
                .id("test")
                .currentPhase(ElliottPhase.WAVE1)
                .swings(swings)
                .confidence(createConfidence(0.7))
                .type(ScenarioType.IMPULSE)
                .build();

        assertThat(scenario.hasKnownDirection()).isTrue();
        assertThat(scenario.bullishDirection()).isTrue();
    }

    @Test
    void hasKnownDirectionWithExplicitDirection() {
        ElliottScenario bullishScenario = ElliottScenario.builder()
                .id("bullish-explicit")
                .currentPhase(ElliottPhase.WAVE1)
                .confidence(createConfidence(0.7))
                .type(ScenarioType.IMPULSE)
                .bullishDirection(true)
                .build();

        assertThat(bullishScenario.hasKnownDirection()).isTrue();
        assertThat(bullishScenario.isBullish()).isTrue();
        assertThat(bullishScenario.isBearish()).isFalse();

        ElliottScenario bearishScenario = ElliottScenario.builder()
                .id("bearish-explicit")
                .currentPhase(ElliottPhase.WAVE1)
                .confidence(createConfidence(0.7))
                .type(ScenarioType.IMPULSE)
                .bullishDirection(false)
                .build();

        assertThat(bearishScenario.hasKnownDirection()).isTrue();
        assertThat(bearishScenario.isBullish()).isFalse();
        assertThat(bearishScenario.isBearish()).isTrue();
    }

    @Test
    void emptySwingsWithoutExplicitDirectionHasUnknownDirection() {
        ElliottScenario scenario = ElliottScenario.builder()
                .id("no-direction")
                .currentPhase(ElliottPhase.WAVE1)
                .confidence(createConfidence(0.7))
                .type(ScenarioType.IMPULSE)
                .build();

        assertThat(scenario.hasKnownDirection()).isFalse();
        assertThat(scenario.swings()).isEmpty();
        assertThat(scenario.bullishDirection()).isNull();
    }

    @Test
    void isBullishThrowsForUnknownDirection() {
        ElliottScenario scenario = ElliottScenario.builder()
                .id("no-direction")
                .currentPhase(ElliottPhase.WAVE1)
                .confidence(createConfidence(0.7))
                .type(ScenarioType.IMPULSE)
                .build();

        IllegalStateException exception = assertThrows(IllegalStateException.class, scenario::isBullish);
        assertThat(exception.getMessage()).contains("no-direction");
        assertThat(exception.getMessage()).contains("no swings and no explicit direction");
    }

    @Test
    void isBearishThrowsForUnknownDirection() {
        ElliottScenario scenario = ElliottScenario.builder()
                .id("no-direction")
                .currentPhase(ElliottPhase.WAVE1)
                .confidence(createConfidence(0.7))
                .type(ScenarioType.IMPULSE)
                .build();

        IllegalStateException exception = assertThrows(IllegalStateException.class, scenario::isBearish);
        assertThat(exception.getMessage()).contains("no-direction");
        assertThat(exception.getMessage()).contains("no swings and no explicit direction");
    }

    @Test
    void isInvalidatedByThrowsForUnknownDirection() {
        ElliottScenario scenario = ElliottScenario.builder()
                .id("no-direction")
                .currentPhase(ElliottPhase.WAVE1)
                .confidence(createConfidence(0.7))
                .type(ScenarioType.IMPULSE)
                .invalidationPrice(numFactory.numOf(100))
                .build();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> scenario.isInvalidatedBy(numFactory.numOf(95)));
        assertThat(exception.getMessage()).contains("no-direction");
        assertThat(exception.getMessage()).contains("no swings and no explicit direction");
    }

    @Test
    void isInvalidatedByWithExplicitDirectionWorks() {
        ElliottScenario bullishScenario = ElliottScenario.builder()
                .id("bullish-explicit")
                .currentPhase(ElliottPhase.WAVE2)
                .confidence(createConfidence(0.7))
                .type(ScenarioType.IMPULSE)
                .invalidationPrice(numFactory.numOf(100))
                .bullishDirection(true)
                .build();

        // For bullish, price below invalidation = invalid
        assertThat(bullishScenario.isInvalidatedBy(numFactory.numOf(95))).isTrue();
        assertThat(bullishScenario.isInvalidatedBy(numFactory.numOf(105))).isFalse();

        ElliottScenario bearishScenario = ElliottScenario.builder()
                .id("bearish-explicit")
                .currentPhase(ElliottPhase.WAVE2)
                .confidence(createConfidence(0.7))
                .type(ScenarioType.IMPULSE)
                .invalidationPrice(numFactory.numOf(100))
                .bullishDirection(false)
                .build();

        // For bearish, price above invalidation = invalid
        assertThat(bearishScenario.isInvalidatedBy(numFactory.numOf(105))).isTrue();
        assertThat(bearishScenario.isInvalidatedBy(numFactory.numOf(95))).isFalse();
    }

    @Test
    void isInvalidatedByReturnsFalseForNullInputs() {
        List<ElliottSwing> swings = List
                .of(new ElliottSwing(0, 5, numFactory.numOf(100), numFactory.numOf(120), ElliottDegree.MINOR));

        ElliottScenario scenario = ElliottScenario.builder()
                .id("test")
                .currentPhase(ElliottPhase.WAVE2)
                .swings(swings)
                .confidence(createConfidence(0.7))
                .type(ScenarioType.IMPULSE)
                .invalidationPrice(numFactory.numOf(100))
                .build();

        // Null price should return false
        assertThat(scenario.isInvalidatedBy(null)).isFalse();
    }

    @Test
    void isInvalidatedByReturnsFalseForNullInvalidationPrice() {
        List<ElliottSwing> swings = List
                .of(new ElliottSwing(0, 5, numFactory.numOf(100), numFactory.numOf(120), ElliottDegree.MINOR));

        ElliottScenario scenario = ElliottScenario.builder()
                .id("test")
                .currentPhase(ElliottPhase.WAVE2)
                .swings(swings)
                .confidence(createConfidence(0.7))
                .type(ScenarioType.IMPULSE)
                // No invalidation price set (null)
                .build();

        // When invalidation price is null, cannot be invalidated
        assertThat(scenario.isInvalidatedBy(numFactory.numOf(95))).isFalse();
    }

    @Test
    void directionDerivedFromSwingsOverridesExplicitWhenSwingsProvided() {
        // When swings are provided, direction should be derived from them,
        // even if an explicit direction was set
        List<ElliottSwing> bearishSwings = List
                .of(new ElliottSwing(0, 5, numFactory.numOf(120), numFactory.numOf(100), ElliottDegree.MINOR));

        ElliottScenario scenario = ElliottScenario.builder()
                .id("test")
                .currentPhase(ElliottPhase.WAVE1)
                .swings(bearishSwings)
                .confidence(createConfidence(0.7))
                .type(ScenarioType.IMPULSE)
                .bullishDirection(true) // Explicitly set bullish, but swings are bearish
                .build();

        // Direction should be derived from swings (bearish) because swings are present
        // The compact constructor derives direction from swings when provided
        assertThat(scenario.hasKnownDirection()).isTrue();
        assertThat(scenario.isBearish()).isTrue();
    }

    private ElliottConfidence createConfidence(double overall) {
        Num score = numFactory.numOf(overall);
        return new ElliottConfidence(score, score, score, score, score, score, "Test");
    }
}

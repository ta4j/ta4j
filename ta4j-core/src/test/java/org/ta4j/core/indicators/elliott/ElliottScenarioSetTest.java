/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

class ElliottScenarioSetTest {

    private final NumFactory numFactory = DecimalNumFactory.getInstance();

    @Test
    void emptySet() {
        ElliottScenarioSet set = ElliottScenarioSet.empty(10);

        assertThat(set.isEmpty()).isTrue();
        assertThat(set.size()).isZero();
        assertThat(set.barIndex()).isEqualTo(10);
        assertThat(set.base()).isEmpty();
        assertThat(set.alternatives()).isEmpty();
    }

    @Test
    void scenariosSortedByConfidence() {
        ElliottScenario low = createScenario("low", 0.3, ElliottPhase.WAVE2);
        ElliottScenario high = createScenario("high", 0.9, ElliottPhase.WAVE3);
        ElliottScenario medium = createScenario("medium", 0.6, ElliottPhase.WAVE4);

        ElliottScenarioSet set = ElliottScenarioSet.of(List.of(low, medium, high), 0);

        assertThat(set.base()).isPresent();
        assertThat(set.base().get().id()).isEqualTo("high");
        assertThat(set.alternatives()).hasSize(2);
        assertThat(set.alternatives().get(0).id()).isEqualTo("medium");
        assertThat(set.alternatives().get(1).id()).isEqualTo("low");
    }

    @Test
    void filterByPhase() {
        ElliottScenario wave3 = createScenario("w3", 0.8, ElliottPhase.WAVE3);
        ElliottScenario wave4 = createScenario("w4", 0.6, ElliottPhase.WAVE4);

        ElliottScenarioSet set = ElliottScenarioSet.of(List.of(wave3, wave4), 0);
        ElliottScenarioSet filtered = set.byPhase(ElliottPhase.WAVE3);

        assertThat(filtered.size()).isEqualTo(1);
        assertThat(filtered.base().get().currentPhase()).isEqualTo(ElliottPhase.WAVE3);
    }

    @Test
    void filterByType() {
        ElliottScenario impulse = createScenario("impulse", 0.8, ElliottPhase.WAVE3, ScenarioType.IMPULSE);
        ElliottScenario zigzag = createScenario("zigzag", 0.7, ElliottPhase.CORRECTIVE_A,
                ScenarioType.CORRECTIVE_ZIGZAG);

        ElliottScenarioSet set = ElliottScenarioSet.of(List.of(impulse, zigzag), 0);
        ElliottScenarioSet impulseOnly = set.byType(ScenarioType.IMPULSE);

        assertThat(impulseOnly.size()).isEqualTo(1);
        assertThat(impulseOnly.base().get().type()).isEqualTo(ScenarioType.IMPULSE);
    }

    @Test
    void highConfidenceCount() {
        ElliottScenario high1 = createScenario("h1", 0.9, ElliottPhase.WAVE3);
        ElliottScenario high2 = createScenario("h2", 0.75, ElliottPhase.WAVE4);
        ElliottScenario low = createScenario("low", 0.4, ElliottPhase.WAVE2);

        ElliottScenarioSet set = ElliottScenarioSet.of(List.of(high1, high2, low), 0);

        assertThat(set.highConfidenceCount()).isEqualTo(2);
        assertThat(set.lowConfidenceCount()).isZero();
    }

    @Test
    void consensusWhenAllAgree() {
        ElliottScenario s1 = createScenario("s1", 0.9, ElliottPhase.WAVE3);
        ElliottScenario s2 = createScenario("s2", 0.8, ElliottPhase.WAVE3);

        ElliottScenarioSet set = ElliottScenarioSet.of(List.of(s1, s2), 0);

        assertThat(set.consensus()).isEqualTo(ElliottPhase.WAVE3);
    }

    @Test
    void noConsensusWhenDisagreement() {
        ElliottScenario s1 = createScenario("s1", 0.9, ElliottPhase.WAVE3);
        ElliottScenario s2 = createScenario("s2", 0.8, ElliottPhase.WAVE4);

        ElliottScenarioSet set = ElliottScenarioSet.of(List.of(s1, s2), 0);

        assertThat(set.consensus()).isEqualTo(ElliottPhase.NONE);
    }

    @Test
    void confidenceSpread() {
        ElliottScenario high = createScenario("high", 0.9, ElliottPhase.WAVE3);
        ElliottScenario medium = createScenario("medium", 0.5, ElliottPhase.WAVE4);

        ElliottScenarioSet set = ElliottScenarioSet.of(List.of(high, medium), 0);

        assertThat(set.confidenceSpread()).isCloseTo(0.4, within(0.01));
    }

    @Test
    void trendBiasReturnsUnknownForEmptySet() {
        ElliottScenarioSet set = ElliottScenarioSet.empty(0);

        ElliottTrendBias bias = set.trendBias();

        assertThat(bias.isUnknown()).isTrue();
        assertThat(bias.score()).isNaN();
        assertThat(bias.totalScenarios()).isZero();
    }

    @Test
    void trendBiasWeightsBullishAndBearishScenarios() {
        ElliottScenario bullish = ElliottScenario.builder()
                .id("bull")
                .currentPhase(ElliottPhase.WAVE3)
                .confidence(createConfidence(0.8))
                .type(ScenarioType.IMPULSE)
                .bullishDirection(true)
                .build();
        ElliottScenario bearish = ElliottScenario.builder()
                .id("bear")
                .currentPhase(ElliottPhase.WAVE3)
                .confidence(createConfidence(0.4))
                .type(ScenarioType.IMPULSE)
                .bullishDirection(false)
                .build();
        ElliottScenario unknown = ElliottScenario.builder()
                .id("unknown")
                .currentPhase(ElliottPhase.WAVE3)
                .confidence(createConfidence(0.6))
                .type(ScenarioType.IMPULSE)
                .build();

        ElliottScenarioSet set = ElliottScenarioSet.of(List.of(bullish, bearish, unknown), 0);

        ElliottTrendBias bias = set.trendBias();

        assertThat(bias.isBullish()).isTrue();
        assertThat(bias.score()).isCloseTo(0.333, within(0.01));
        assertThat(bias.knownDirectionCount()).isEqualTo(2);
        assertThat(bias.totalScenarios()).isEqualTo(3);
        assertThat(bias.consensus()).isTrue();
    }

    @Test
    void strongConsensusWithLargeSpread() {
        ElliottScenario high = createScenario("high", 0.9, ElliottPhase.WAVE3);
        ElliottScenario low = createScenario("low", 0.5, ElliottPhase.WAVE4);

        ElliottScenarioSet set = ElliottScenarioSet.of(List.of(high, low), 0);

        assertThat(set.hasStrongConsensus()).isTrue();
    }

    @Test
    void invalidationFiltering() {
        List<ElliottSwing> bullishSwings = List
                .of(new ElliottSwing(0, 5, numFactory.numOf(100), numFactory.numOf(120), ElliottDegree.MINOR));

        ElliottScenario s1 = ElliottScenario.builder()
                .id("s1")
                .currentPhase(ElliottPhase.WAVE2)
                .swings(bullishSwings)
                .confidence(createConfidence(0.8))
                .type(ScenarioType.IMPULSE)
                .invalidationPrice(numFactory.numOf(100))
                .build();

        ElliottScenario s2 = ElliottScenario.builder()
                .id("s2")
                .currentPhase(ElliottPhase.WAVE3)
                .swings(bullishSwings)
                .confidence(createConfidence(0.7))
                .type(ScenarioType.IMPULSE)
                .invalidationPrice(numFactory.numOf(90))
                .build();

        ElliottScenarioSet set = ElliottScenarioSet.of(List.of(s1, s2), 0);

        // Price at 95 invalidates s1 (invalidation at 100) but not s2 (invalidation at
        // 90)
        List<ElliottScenario> invalidated = set.invalidatedBy(numFactory.numOf(95));
        assertThat(invalidated).hasSize(1);
        assertThat(invalidated.get(0).id()).isEqualTo("s1");

        ElliottScenarioSet valid = set.validAt(numFactory.numOf(95));
        assertThat(valid.size()).isEqualTo(1);
        assertThat(valid.base().get().id()).isEqualTo("s2");
    }

    @Test
    void summary() {
        ElliottScenario s1 = createScenario("primary", 0.85, ElliottPhase.WAVE3);
        ElliottScenario s2 = createScenario("alt", 0.6, ElliottPhase.WAVE4);

        ElliottScenarioSet set = ElliottScenarioSet.of(List.of(s1, s2), 0);
        String summary = set.summary();

        assertThat(summary).contains("2 scenario(s)");
        assertThat(summary).contains("Base case=WAVE3");
        assertThat(summary).contains("1 alternative");
    }

    private ElliottScenario createScenario(String id, double confidence, ElliottPhase phase) {
        return createScenario(id, confidence, phase, ScenarioType.IMPULSE);
    }

    private ElliottScenario createScenario(String id, double confidence, ElliottPhase phase, ScenarioType type) {
        return ElliottScenario.builder()
                .id(id)
                .currentPhase(phase)
                .confidence(createConfidence(confidence))
                .type(type)
                .build();
    }

    private ElliottConfidence createConfidence(double overall) {
        Num score = numFactory.numOf(overall);
        return new ElliottConfidence(score, score, score, score, score, score, "Test");
    }
}

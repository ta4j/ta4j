/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules.elliott;

import java.util.Collections;
import java.util.List;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.elliott.ElliottConfidence;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottPhase;
import org.ta4j.core.indicators.elliott.ElliottScenario;
import org.ta4j.core.indicators.elliott.ElliottScenarioSet;
import org.ta4j.core.indicators.elliott.ElliottSwing;
import org.ta4j.core.indicators.elliott.ScenarioType;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

final class ElliottScenarioRuleTestSupport {

    private ElliottScenarioRuleTestSupport() {
    }

    static BarSeries buildSeries(final NumFactory numFactory) {
        MockBarSeriesBuilder builder = new MockBarSeriesBuilder();
        builder.withName("elliott-rule-test");
        builder.withNumFactory(numFactory);
        builder.withData(100, 150, 200, 240, 260, 280);
        return builder.build();
    }

    static ElliottScenario buildBullishScenario(final NumFactory numFactory, final ElliottPhase phase,
            final double confidenceValue) {
        List<ElliottSwing> swings = List.of(
                new ElliottSwing(0, 4, numFactory.hundred(), numFactory.numOf(150), ElliottDegree.PRIMARY),
                new ElliottSwing(4, 6, numFactory.numOf(150), numFactory.numOf(130), ElliottDegree.PRIMARY),
                new ElliottSwing(6, 12, numFactory.numOf(130), numFactory.numOf(200), ElliottDegree.PRIMARY),
                new ElliottSwing(12, 16, numFactory.numOf(200), numFactory.numOf(180), ElliottDegree.PRIMARY),
                new ElliottSwing(16, 20, numFactory.numOf(180), numFactory.numOf(240), ElliottDegree.PRIMARY));
        Num target = numFactory.numOf(240);
        return ElliottScenario.builder()
                .id("bullish")
                .currentPhase(phase)
                .swings(swings)
                .confidence(buildConfidence(numFactory, confidenceValue))
                .degree(ElliottDegree.PRIMARY)
                .invalidationPrice(numFactory.numOf(120))
                .primaryTarget(target)
                .fibonacciTargets(List.of(target))
                .type(ScenarioType.IMPULSE)
                .startIndex(0)
                .build();
    }

    static ElliottScenario buildBearishScenario(final NumFactory numFactory, final ElliottPhase phase,
            final double confidenceValue) {
        List<ElliottSwing> swings = List.of(
                new ElliottSwing(0, 4, numFactory.numOf(200), numFactory.numOf(150), ElliottDegree.PRIMARY),
                new ElliottSwing(4, 6, numFactory.numOf(150), numFactory.numOf(170), ElliottDegree.PRIMARY),
                new ElliottSwing(6, 12, numFactory.numOf(170), numFactory.hundred(), ElliottDegree.PRIMARY),
                new ElliottSwing(12, 16, numFactory.hundred(), numFactory.numOf(120), ElliottDegree.PRIMARY),
                new ElliottSwing(16, 20, numFactory.numOf(120), numFactory.numOf(80), ElliottDegree.PRIMARY));
        Num target = numFactory.numOf(80);
        return ElliottScenario.builder()
                .id("bearish")
                .currentPhase(phase)
                .swings(swings)
                .confidence(buildConfidence(numFactory, confidenceValue))
                .degree(ElliottDegree.PRIMARY)
                .invalidationPrice(numFactory.numOf(210))
                .primaryTarget(target)
                .fibonacciTargets(List.of(target))
                .type(ScenarioType.IMPULSE)
                .startIndex(0)
                .build();
    }

    static ElliottScenario buildCorrectiveScenario(final NumFactory numFactory, final ElliottPhase phase,
            final double confidenceValue) {
        List<ElliottSwing> swings = List.of(
                new ElliottSwing(0, 3, numFactory.hundred(), numFactory.numOf(120), ElliottDegree.PRIMARY),
                new ElliottSwing(3, 6, numFactory.numOf(120), numFactory.numOf(110), ElliottDegree.PRIMARY),
                new ElliottSwing(6, 9, numFactory.numOf(110), numFactory.numOf(130), ElliottDegree.PRIMARY),
                new ElliottSwing(9, 12, numFactory.numOf(130), numFactory.numOf(115), ElliottDegree.PRIMARY),
                new ElliottSwing(12, 15, numFactory.numOf(115), numFactory.numOf(125), ElliottDegree.PRIMARY));
        Num target = numFactory.numOf(125);
        return ElliottScenario.builder()
                .id("corrective")
                .currentPhase(phase)
                .swings(swings)
                .confidence(buildConfidence(numFactory, confidenceValue))
                .degree(ElliottDegree.PRIMARY)
                .invalidationPrice(numFactory.numOf(95))
                .primaryTarget(target)
                .fibonacciTargets(List.of(target))
                .type(ScenarioType.CORRECTIVE_TRIANGLE)
                .startIndex(0)
                .build();
    }

    static ElliottScenario buildShortWaveScenario(final NumFactory numFactory) {
        List<ElliottSwing> swings = List.of(
                new ElliottSwing(0, 1, numFactory.hundred(), numFactory.numOf(120), ElliottDegree.PRIMARY),
                new ElliottSwing(1, 2, numFactory.numOf(120), numFactory.numOf(110), ElliottDegree.PRIMARY),
                new ElliottSwing(2, 4, numFactory.numOf(110), numFactory.numOf(140), ElliottDegree.PRIMARY),
                new ElliottSwing(4, 5, numFactory.numOf(140), numFactory.numOf(130), ElliottDegree.PRIMARY),
                new ElliottSwing(5, 6, numFactory.numOf(130), numFactory.numOf(160), ElliottDegree.PRIMARY));
        Num target = numFactory.numOf(160);
        return ElliottScenario.builder()
                .id("short")
                .currentPhase(ElliottPhase.WAVE3)
                .swings(swings)
                .confidence(buildConfidence(numFactory, 0.8))
                .degree(ElliottDegree.PRIMARY)
                .invalidationPrice(numFactory.numOf(90))
                .primaryTarget(target)
                .fibonacciTargets(List.of(target))
                .type(ScenarioType.IMPULSE)
                .startIndex(0)
                .build();
    }

    static ElliottConfidence buildConfidence(final NumFactory numFactory, final double value) {
        Num score = numFactory.numOf(value);
        return new ElliottConfidence(score, score, score, score, score, score, "Test");
    }

    static Indicator<Num> constantIndicator(final BarSeries series, final Num value) {
        return new MockIndicator(series, Collections.nCopies(series.getBarCount(), value));
    }

    static Indicator<ElliottScenarioSet> fixedScenarioIndicator(final BarSeries series,
            final ElliottScenarioSet scenarioSet) {
        return new FixedScenarioIndicator(series, scenarioSet);
    }

    private static final class FixedScenarioIndicator implements Indicator<ElliottScenarioSet> {

        private final BarSeries series;
        private final ElliottScenarioSet scenarioSet;

        private FixedScenarioIndicator(final BarSeries series, final ElliottScenarioSet scenarioSet) {
            this.series = series;
            this.scenarioSet = scenarioSet;
        }

        @Override
        public ElliottScenarioSet getValue(final int index) {
            return scenarioSet;
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

        @Override
        public BarSeries getBarSeries() {
            return series;
        }
    }
}

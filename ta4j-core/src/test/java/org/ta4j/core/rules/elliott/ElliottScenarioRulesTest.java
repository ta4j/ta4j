/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules.elliott;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.elliott.ElliottConfidence;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottPhase;
import org.ta4j.core.indicators.elliott.ElliottScenario;
import org.ta4j.core.indicators.elliott.ElliottScenarioSet;
import org.ta4j.core.indicators.elliott.ElliottSwing;
import org.ta4j.core.indicators.elliott.ScenarioType;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

class ElliottScenarioRulesTest {

    private BarSeries series;
    private NumFactory numFactory;

    @BeforeEach
    void setUp() {
        numFactory = DecimalNumFactory.getInstance();
        MockBarSeriesBuilder builder = new MockBarSeriesBuilder();
        builder.withName("elliott-rule-test");
        builder.withNumFactory(numFactory);
        builder.withData(100, 150, 200, 240, 260, 280);
        series = builder.build();
    }

    @Test
    void validRuleRequiresScenarioAndPrice() {
        ElliottScenario scenario = buildBullishScenario(ElliottPhase.WAVE3, 0.8);
        ElliottScenarioSet set = ElliottScenarioSet.of(List.of(scenario), series.getEndIndex());
        Indicator<ElliottScenarioSet> indicator = new FixedScenarioIndicator(series, set);
        Indicator<Num> close = new ClosePriceIndicator(series);

        ElliottScenarioValidRule rule = new ElliottScenarioValidRule(indicator, close);
        assertThat(rule.isSatisfied(series.getEndIndex(), null)).isTrue();

        ElliottScenarioSet emptySet = ElliottScenarioSet.empty(series.getEndIndex());
        ElliottScenarioValidRule missingRule = new ElliottScenarioValidRule(
                new FixedScenarioIndicator(series, emptySet), close);
        assertThat(missingRule.isSatisfied(series.getEndIndex(), null)).isFalse();

        Indicator<Num> nanPrice = constantIndicator(NaN.NaN);
        ElliottScenarioValidRule nanRule = new ElliottScenarioValidRule(indicator, nanPrice);
        assertThat(nanRule.isSatisfied(series.getEndIndex(), null)).isFalse();
    }

    @Test
    void impulsePhaseRuleMatchesConfiguredPhases() {
        ElliottScenario scenario = buildBullishScenario(ElliottPhase.WAVE3, 0.8);
        ElliottScenarioSet set = ElliottScenarioSet.of(List.of(scenario), series.getEndIndex());
        Indicator<ElliottScenarioSet> indicator = new FixedScenarioIndicator(series, set);

        ElliottImpulsePhaseRule rule = new ElliottImpulsePhaseRule(indicator, ElliottPhase.WAVE3, ElliottPhase.WAVE5);
        assertThat(rule.isSatisfied(series.getEndIndex(), null)).isTrue();

        ElliottScenario wave2Scenario = buildBullishScenario(ElliottPhase.WAVE2, 0.8);
        ElliottScenarioSet wave2Set = ElliottScenarioSet.of(List.of(wave2Scenario), series.getEndIndex());
        ElliottImpulsePhaseRule wave2Rule = new ElliottImpulsePhaseRule(new FixedScenarioIndicator(series, wave2Set),
                ElliottPhase.WAVE3, ElliottPhase.WAVE5);
        assertThat(wave2Rule.isSatisfied(series.getEndIndex(), null)).isFalse();
    }

    @Test
    void confidenceRuleHonorsThreshold() {
        ElliottScenario scenario = buildBullishScenario(ElliottPhase.WAVE3, 0.8);
        ElliottScenarioSet set = ElliottScenarioSet.of(List.of(scenario), series.getEndIndex());
        Indicator<ElliottScenarioSet> indicator = new FixedScenarioIndicator(series, set);

        ElliottScenarioConfidenceRule rule = new ElliottScenarioConfidenceRule(indicator, 0.7);
        assertThat(rule.isSatisfied(series.getEndIndex(), null)).isTrue();

        ElliottScenarioConfidenceRule strictRule = new ElliottScenarioConfidenceRule(indicator, 0.9);
        assertThat(strictRule.isSatisfied(series.getEndIndex(), null)).isFalse();
    }

    @Test
    void directionRuleMatchesScenarioDirection() {
        ElliottScenario bullishScenario = buildBullishScenario(ElliottPhase.WAVE3, 0.8);
        ElliottScenarioSet bullishSet = ElliottScenarioSet.of(List.of(bullishScenario), series.getEndIndex());
        Indicator<ElliottScenarioSet> indicator = new FixedScenarioIndicator(series, bullishSet);

        ElliottScenarioDirectionRule bullishRule = new ElliottScenarioDirectionRule(indicator, true);
        ElliottScenarioDirectionRule bearishRule = new ElliottScenarioDirectionRule(indicator, false);

        assertThat(bullishRule.isSatisfied(series.getEndIndex(), null)).isTrue();
        assertThat(bearishRule.isSatisfied(series.getEndIndex(), null)).isFalse();
    }

    @Test
    void trendBiasRuleRequiresDirectionAndStrength() {
        ElliottScenario bullishScenario = buildBullishScenario(ElliottPhase.WAVE3, 0.8);
        ElliottScenarioSet bullishSet = ElliottScenarioSet.of(List.of(bullishScenario), series.getEndIndex());
        Indicator<ElliottScenarioSet> bullishIndicator = new FixedScenarioIndicator(series, bullishSet);

        ElliottTrendBiasRule bullishRule = new ElliottTrendBiasRule(bullishIndicator, true, 0.2);
        assertThat(bullishRule.isSatisfied(series.getEndIndex(), null)).isTrue();

        ElliottScenario bearishScenario = buildBearishScenario(ElliottPhase.WAVE3, 0.8);
        ElliottScenarioSet neutralSet = ElliottScenarioSet.of(List.of(bullishScenario, bearishScenario),
                series.getEndIndex());
        Indicator<ElliottScenarioSet> neutralIndicator = new FixedScenarioIndicator(series, neutralSet);

        ElliottTrendBiasRule neutralRule = new ElliottTrendBiasRule(neutralIndicator, true, 0.2);
        assertThat(neutralRule.isSatisfied(series.getEndIndex(), null)).isFalse();
    }

    @Test
    void alternationRuleEnforcesMinimumRatio() {
        ElliottScenario scenario = buildBullishScenario(ElliottPhase.WAVE5, 0.8);
        ElliottScenarioSet set = ElliottScenarioSet.of(List.of(scenario), series.getEndIndex());
        Indicator<ElliottScenarioSet> indicator = new FixedScenarioIndicator(series, set);

        ElliottScenarioAlternationRule rule = new ElliottScenarioAlternationRule(indicator, 1.5);
        assertThat(rule.isSatisfied(series.getEndIndex(), null)).isTrue();

        ElliottScenarioAlternationRule strictRule = new ElliottScenarioAlternationRule(indicator, 2.5);
        assertThat(strictRule.isSatisfied(series.getEndIndex(), null)).isFalse();
    }

    @Test
    void riskRewardRuleEvaluatesTargetsAndStops() {
        ElliottScenario scenario = buildBullishScenario(ElliottPhase.WAVE3, 0.8);
        ElliottScenarioSet set = ElliottScenarioSet.of(List.of(scenario), series.getEndIndex());
        Indicator<ElliottScenarioSet> indicator = new FixedScenarioIndicator(series, set);
        Indicator<Num> close = constantIndicator(numFactory.numOf(150));

        ElliottScenarioRiskRewardRule rule = new ElliottScenarioRiskRewardRule(indicator, close, true, 3.0);
        assertThat(rule.isSatisfied(series.getEndIndex(), null)).isTrue();

        ElliottScenarioRiskRewardRule strictRule = new ElliottScenarioRiskRewardRule(indicator, close, true, 6.0);
        assertThat(strictRule.isSatisfied(series.getEndIndex(), null)).isFalse();
    }

    @Test
    void targetReachedRuleDetectsHit() {
        ElliottScenario scenario = buildBullishScenario(ElliottPhase.WAVE5, 0.8);
        ElliottScenarioSet set = ElliottScenarioSet.of(List.of(scenario), series.getEndIndex());
        Indicator<ElliottScenarioSet> indicator = new FixedScenarioIndicator(series, set);
        Indicator<Num> close = constantIndicator(numFactory.numOf(240));

        ElliottScenarioTargetReachedRule rule = new ElliottScenarioTargetReachedRule(indicator, close, true);
        assertThat(rule.isSatisfied(series.getEndIndex(), null)).isTrue();
    }

    @Test
    void stopViolationRuleDetectsBreach() {
        ElliottScenario scenario = buildBullishScenario(ElliottPhase.WAVE3, 0.8);
        ElliottScenarioSet set = ElliottScenarioSet.of(List.of(scenario), series.getEndIndex());
        Indicator<ElliottScenarioSet> indicator = new FixedScenarioIndicator(series, set);
        Indicator<Num> close = constantIndicator(numFactory.numOf(120));

        ElliottScenarioStopViolationRule rule = new ElliottScenarioStopViolationRule(indicator, close, true);
        assertThat(rule.isSatisfied(series.getEndIndex(), null)).isTrue();
    }

    @Test
    void invalidationRuleTriggersWhenPriceBreaksLevel() {
        ElliottScenario scenario = buildBullishScenario(ElliottPhase.WAVE3, 0.8);
        ElliottScenarioSet set = ElliottScenarioSet.of(List.of(scenario), series.getEndIndex());
        Indicator<ElliottScenarioSet> indicator = new FixedScenarioIndicator(series, set);
        Indicator<Num> close = constantIndicator(numFactory.numOf(110));

        ElliottScenarioInvalidationRule rule = new ElliottScenarioInvalidationRule(indicator, close);
        assertThat(rule.isSatisfied(series.getEndIndex(), null)).isTrue();
    }

    @Test
    void completionRuleMatchesCompletionPhases() {
        ElliottScenario scenario = buildBullishScenario(ElliottPhase.WAVE5, 0.8);
        ElliottScenarioSet set = ElliottScenarioSet.of(List.of(scenario), series.getEndIndex());
        Indicator<ElliottScenarioSet> indicator = new FixedScenarioIndicator(series, set);

        ElliottScenarioCompletionRule rule = new ElliottScenarioCompletionRule(indicator);
        assertThat(rule.isSatisfied(series.getEndIndex(), null)).isTrue();
    }

    @Test
    void timeStopRuleFiresAfterDuration() {
        ElliottScenario scenario = buildShortWaveScenario();
        ElliottScenarioSet set = ElliottScenarioSet.of(List.of(scenario), series.getEndIndex());
        Indicator<ElliottScenarioSet> indicator = new FixedScenarioIndicator(series, set);

        ElliottScenarioTimeStopRule rule = new ElliottScenarioTimeStopRule(indicator, 1.5);
        TradingRecord record = new BaseTradingRecord();
        record.enter(series.getBeginIndex());

        assertThat(rule.isSatisfied(series.getBeginIndex() + 3, record)).isTrue();
    }

    private ElliottScenario buildBullishScenario(final ElliottPhase phase, final double confidenceValue) {
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
                .confidence(buildConfidence(confidenceValue))
                .degree(ElliottDegree.PRIMARY)
                .invalidationPrice(numFactory.numOf(120))
                .primaryTarget(target)
                .fibonacciTargets(List.of(target))
                .type(ScenarioType.IMPULSE)
                .startIndex(0)
                .build();
    }

    private ElliottScenario buildBearishScenario(final ElliottPhase phase, final double confidenceValue) {
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
                .confidence(buildConfidence(confidenceValue))
                .degree(ElliottDegree.PRIMARY)
                .invalidationPrice(numFactory.numOf(210))
                .primaryTarget(target)
                .fibonacciTargets(List.of(target))
                .type(ScenarioType.IMPULSE)
                .startIndex(0)
                .build();
    }

    private ElliottScenario buildShortWaveScenario() {
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
                .confidence(buildConfidence(0.8))
                .degree(ElliottDegree.PRIMARY)
                .invalidationPrice(numFactory.numOf(90))
                .primaryTarget(target)
                .fibonacciTargets(List.of(target))
                .type(ScenarioType.IMPULSE)
                .startIndex(0)
                .build();
    }

    private ElliottConfidence buildConfidence(final double value) {
        Num score = numFactory.numOf(value);
        return new ElliottConfidence(score, score, score, score, score, score, "Test");
    }

    private Indicator<Num> constantIndicator(final Num value) {
        return new MockIndicator(series, Collections.nCopies(series.getBarCount(), value));
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

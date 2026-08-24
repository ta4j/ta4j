/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.analysis.elliott.swing.SwingPivotType;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

class Wave5MomentumDivergenceRuleTest {

    @Test
    void scoresBullishDivergence() {
        final Wave5MomentumDivergenceRule rule = new Wave5MomentumDivergenceRule(
                momentum(0, 0, 0, 10, 8, 0));
        final RuleEvidence evidence = rule.evaluate(
                candidate(WaveDirection.BULLISH, 100, 110, 105, 120, 125, 130));

        assertThat(evidence.state()).isEqualTo(EvidenceState.PASS);
        assertThat(evidence.score()).hasValue(0.2d);
        assertThat(evidence.observations()).contains("wave 3 momentum=10.0", "wave 5 momentum=8.0",
                "wave 3 end price=120.0", "wave 5 end price=125.0");
    }

    @Test
    void scoresBearishDivergenceWithTheSameStrength() {
        final Wave5MomentumDivergenceRule rule = new Wave5MomentumDivergenceRule(
                momentum(0, 0, 0, 10, 12, 0));
        final RuleEvidence evidence = rule.evaluate(
                candidate(WaveDirection.BEARISH, 100, 90, 95, 80, 75, 70));

        assertThat(evidence.state()).isEqualTo(EvidenceState.PASS);
        assertThat(evidence.score()).hasValue(0.2d);
    }

    @Test
    void passesWithoutScoreWhenPriceAndMomentumAreAligned() {
        final Wave5MomentumDivergenceRule rule = new Wave5MomentumDivergenceRule(
                momentum(0, 0, 0, 10, 12, 0));
        final RuleEvidence evidence = rule.evaluate(
                candidate(WaveDirection.BULLISH, 100, 110, 105, 120, 125, 130));

        assertThat(evidence.state()).isEqualTo(EvidenceState.PASS);
        assertThat(evidence.score()).isEmpty();
        assertThat(evidence.explanation()).contains("aligned");
        assertThat(evidence.observations()).contains("aligned");
    }

    @Test
    void returnsUnavailableForMissingMomentum() {
        final Wave5MomentumDivergenceRule rule = new Wave5MomentumDivergenceRule(
                momentum(DoubleNum.valueOf(0), DoubleNum.valueOf(0), DoubleNum.valueOf(0),
                        DoubleNum.valueOf(10), NaN.NaN, DoubleNum.valueOf(0)));
        final RuleEvidence evidence = rule.evaluate(
                candidate(WaveDirection.BULLISH, 100, 110, 105, 120, 125, 130));

        assertThat(evidence.state()).isEqualTo(EvidenceState.UNAVAILABLE);
        assertThat(evidence.score()).isEmpty();
    }

    @Test
    void returnsUnavailableBelowMomentumUnstableRange() {
        final BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3, 4, 5, 6).build();
        final Indicator<Num> momentum = new SMAIndicator(new ClosePriceIndicator(series), 5);
        final Wave5MomentumDivergenceRule rule = new Wave5MomentumDivergenceRule(momentum);

        final RuleEvidence evidence = rule.evaluate(
                candidate(WaveDirection.BULLISH, 100, 110, 105, 120, 125, 130));

        assertThat(momentum.getCountOfUnstableBars()).isGreaterThan(3);
        assertThat(evidence.state()).isEqualTo(EvidenceState.UNAVAILABLE);
    }

    @Test
    void doesNotApplyToCorrectiveGrammar() {
        final Wave5MomentumDivergenceRule rule = new Wave5MomentumDivergenceRule(
                momentum(0, 0, 0, 10, 8, 0));

        final RuleEvidence evidence = rule.evaluate(candidate(WaveDirection.BULLISH, 100, 110, 105, 115));

        assertThat(evidence.state()).isEqualTo(EvidenceState.NOT_APPLICABLE);
    }

    @Test
    void requiresCallerProvidedMomentum() {
        assertThatThrownBy(() -> new Wave5MomentumDivergenceRule(null)).isInstanceOf(NullPointerException.class);
    }

    private static Indicator<Num> momentum(final double... values) {
        final double[] seriesValues = new double[values.length];
        final BarSeries series = new MockBarSeriesBuilder().withData(seriesValues).build();
        final List<Num> momentumValues = new ArrayList<>(values.length);
        for (double value : values) {
            momentumValues.add(DoubleNum.valueOf(value));
        }
        return new MockIndicator(series, momentumValues);
    }

    private static Indicator<Num> momentum(final Num... values) {
        final double[] seriesValues = new double[values.length];
        final BarSeries series = new MockBarSeriesBuilder().withData(seriesValues).build();
        return new MockIndicator(series, List.of(values));
    }

    private static TopologyCandidate candidate(final WaveDirection direction, final double... prices) {
        final TopologyGrammar grammar = grammarFor(prices.length);
        final List<ConfirmedPivot> pivots = new ArrayList<>(prices.length);
        final SwingPivotType firstType = direction == WaveDirection.BULLISH ? SwingPivotType.LOW : SwingPivotType.HIGH;
        for (int index = 0; index < prices.length; index++) {
            final SwingPivotType type = index % 2 == 0 ? firstType
                    : firstType == SwingPivotType.HIGH ? SwingPivotType.LOW : SwingPivotType.HIGH;
            pivots.add(new ConfirmedPivot(index, index, DoubleNum.valueOf(prices[index]), type));
        }
        return new TopologyCandidate(grammar, direction, pivots);
    }

    private static TopologyGrammar grammarFor(final int priceCount) {
        return switch (priceCount) {
        case 4 -> TopologyGrammar.CORRECTIVE_3;
        case 6 -> TopologyGrammar.MOTIVE_5;
        case 9 -> TopologyGrammar.CYCLE_5_3;
        default -> throw new IllegalArgumentException("unexpected price count: " + priceCount);
        };
    }
}

/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.analysis.elliott.swing.SwingPivotType;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

class Wave5MomentumDivergenceRuleTest {

    @Test
    void scoresBullishDivergenceAtTheWave5Endpoint() {
        // Momentum index 5 is the wave-5 endpoint (pivot 5), not pivot 4.
        final Indicator<Num> momentum = momentum(0, 0, 0, 10, 8, 0);
        final Wave5MomentumDivergenceRule rule = new Wave5MomentumDivergenceRule(momentum);
        final RuleEvidence evidence = rule.evaluate(candidate(WaveDirection.BULLISH, 100, 110, 105, 120, 125, 130),
                momentum.getBarSeries());

        assertThat(evidence.state()).isEqualTo(EvidenceState.PASS);
        assertThat(evidence.score()).hasValue(1.0d);
        assertThat(evidence.observations()).contains("wave 3 momentum=10.0", "wave 5 momentum=0.0",
                "wave 3 end price=120.0", "wave 5 end price=130.0");
    }

    @Test
    void scoresBearishDivergenceWithTheSameStrength() {
        final Indicator<Num> momentum = momentum(0, 0, 0, 10, 8, 14);
        final Wave5MomentumDivergenceRule rule = new Wave5MomentumDivergenceRule(momentum);
        final RuleEvidence evidence = rule.evaluate(candidate(WaveDirection.BEARISH, 100, 90, 95, 80, 75, 70),
                momentum.getBarSeries());

        assertThat(evidence.state()).isEqualTo(EvidenceState.PASS);
        assertThat(evidence.score()).hasValue(0.4d);
    }

    @Test
    void failsWhenPriceAndMomentumAreAligned() {
        // The rule premise IS divergence. Alignment is a failed premise (never
        // a pass) so the +wave5-divergence ablation rung measures how often
        // five-wave candidates actually diverge instead of reporting a
        // constant 100% pass rate.
        final Indicator<Num> momentum = momentum(0, 0, 0, 10, 0, 11);
        final Wave5MomentumDivergenceRule rule = new Wave5MomentumDivergenceRule(momentum);
        final RuleEvidence evidence = rule.evaluate(candidate(WaveDirection.BULLISH, 100, 110, 105, 120, 125, 130),
                momentum.getBarSeries());

        assertThat(evidence.state()).isEqualTo(EvidenceState.FAIL);
        assertThat(evidence.score()).isEmpty();
        assertThat(evidence.explanation()).contains("aligned");
        assertThat(evidence.observations()).contains("aligned");
    }

    @Test
    void readsMomentumAtPivotFiveNotPivotFour() {
        // If momentum were read at pivot 4 (value 0), the bullish case would
        // diverge; at the true wave-5 endpoint (pivot 5) it stays aligned.
        final Indicator<Num> momentum = momentum(0, 0, 0, 10, 0, 10);
        final Wave5MomentumDivergenceRule rule = new Wave5MomentumDivergenceRule(momentum);
        final RuleEvidence evidence = rule.evaluate(candidate(WaveDirection.BULLISH, 100, 110, 105, 120, 125, 130),
                momentum.getBarSeries());

        assertThat(evidence.observations()).contains("wave 5 momentum=10.0");
        assertThat(evidence.observations()).contains("aligned");
    }

    @Test
    void returnsUnavailableForMissingMomentum() {
        final Indicator<Num> momentum = momentum(DoubleNum.valueOf(0), DoubleNum.valueOf(0), DoubleNum.valueOf(0),
                DoubleNum.valueOf(10), DoubleNum.valueOf(9), NaN.NaN);
        final Wave5MomentumDivergenceRule rule = new Wave5MomentumDivergenceRule(momentum);
        final RuleEvidence evidence = rule.evaluate(candidate(WaveDirection.BULLISH, 100, 110, 105, 120, 125, 130),
                momentum.getBarSeries());

        assertThat(evidence.state()).isEqualTo(EvidenceState.UNAVAILABLE);
        assertThat(evidence.score()).isEmpty();
    }

    @Test
    void returnsUnavailableForInfiniteEndpointMomentum() {
        // An infinite DoubleNum endpoint is neither NaN nor null: without an
        // explicit finite check the divergence arithmetic either manufactures
        // a scored pass or produces infinity/infinity = NaN and aborts the
        // study. Both signs, at either endpoint, must stay unscored.
        final Indicator<Num> positiveInfinityAtWave5 = momentum(DoubleNum.valueOf(0), DoubleNum.valueOf(0),
                DoubleNum.valueOf(0), DoubleNum.valueOf(10), DoubleNum.valueOf(8),
                DoubleNum.valueOf(Double.POSITIVE_INFINITY));
        final Wave5MomentumDivergenceRule positiveRule = new Wave5MomentumDivergenceRule(positiveInfinityAtWave5);
        final RuleEvidence positiveEvidence = positiveRule.evaluate(
                candidate(WaveDirection.BULLISH, 100, 110, 105, 120, 125, 130), positiveInfinityAtWave5.getBarSeries());

        assertThat(positiveEvidence.state()).isEqualTo(EvidenceState.UNAVAILABLE);
        assertThat(positiveEvidence.score()).isEmpty();

        final Indicator<Num> negativeInfinityAtWave3 = momentum(DoubleNum.valueOf(0), DoubleNum.valueOf(0),
                DoubleNum.valueOf(0), DoubleNum.valueOf(Double.NEGATIVE_INFINITY), DoubleNum.valueOf(8),
                DoubleNum.valueOf(0));
        final Wave5MomentumDivergenceRule negativeRule = new Wave5MomentumDivergenceRule(negativeInfinityAtWave3);
        final RuleEvidence negativeEvidence = negativeRule.evaluate(
                candidate(WaveDirection.BULLISH, 100, 110, 105, 120, 125, 130), negativeInfinityAtWave3.getBarSeries());

        assertThat(negativeEvidence.state()).isEqualTo(EvidenceState.UNAVAILABLE);
        assertThat(negativeEvidence.score()).isEmpty();
    }

    @Test
    void returnsUnavailableBelowMomentumUnstableRange() {
        final BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3, 4, 5, 6).build();
        final Indicator<Num> momentum = new SMAIndicator(new ClosePriceIndicator(series), 5);
        final Wave5MomentumDivergenceRule rule = new Wave5MomentumDivergenceRule(momentum);

        final RuleEvidence evidence = rule.evaluate(candidate(WaveDirection.BULLISH, 100, 110, 105, 120, 125, 130),
                momentum.getBarSeries());

        assertThat(momentum.getCountOfUnstableBars()).isGreaterThan(3);
        assertThat(evidence.state()).isEqualTo(EvidenceState.UNAVAILABLE);
    }

    @Test
    void bindsMomentumPerEvaluatedSeries() {
        final Indicator<Num> diverging = momentum(0, 0, 0, 10, 8, 0);
        final Indicator<Num> aligned = momentum(0, 0, 0, 10, 0, 11);
        final BarSeries divergingSeries = diverging.getBarSeries();
        final BarSeries alignedSeries = aligned.getBarSeries();
        final Wave5MomentumDivergenceRule rule = new Wave5MomentumDivergenceRule(
                series -> series == divergingSeries ? diverging : aligned);

        // One runner instance studies several series: each evaluated series
        // must observe its own bound momentum, never another series' values.
        final RuleEvidence divergingEvidence = rule
                .evaluate(candidate(WaveDirection.BULLISH, 100, 110, 105, 120, 125, 130), divergingSeries);
        final RuleEvidence alignedEvidence = rule
                .evaluate(candidate(WaveDirection.BULLISH, 100, 110, 105, 120, 125, 130), alignedSeries);

        assertThat(divergingEvidence.state()).isEqualTo(EvidenceState.PASS);
        assertThat(divergingEvidence.score()).hasValue(1.0d);
        assertThat(divergingEvidence.observations()).contains("wave 5 momentum=0.0");
        assertThat(alignedEvidence.state()).isEqualTo(EvidenceState.FAIL);
        assertThat(alignedEvidence.score()).isEmpty();
        assertThat(alignedEvidence.observations()).contains("aligned");
    }

    @Test
    void distinguishesEqualButDistinctSeriesInBoundedCache() {
        final BarSeries firstSeries = equalSeries();
        final BarSeries secondSeries = equalSeries();
        final Indicator<Num> firstMomentum = momentum(firstSeries, 0, 0, 0, 10, 8, 0);
        final Indicator<Num> secondMomentum = momentum(secondSeries, 0, 0, 0, 10, 0, 11);
        final Wave5MomentumDivergenceRule rule = new Wave5MomentumDivergenceRule(
                series -> series == firstSeries ? firstMomentum : secondMomentum);

        final RuleEvidence firstEvidence = rule.evaluate(candidate(WaveDirection.BULLISH, 100, 110, 105, 120, 125, 130),
                firstSeries);
        final RuleEvidence secondEvidence = rule
                .evaluate(candidate(WaveDirection.BULLISH, 100, 110, 105, 120, 125, 130), secondSeries);

        assertThat(firstEvidence.state()).isEqualTo(EvidenceState.PASS);
        assertThat(secondEvidence.state()).isEqualTo(EvidenceState.FAIL);
    }

    @Test
    void rejectsFactoryBoundToAnotherSeries() {
        final Indicator<Num> foreign = momentum(0, 0, 0, 10, 8, 0);
        final BarSeries studied = new MockBarSeriesBuilder().withData(1, 2, 3, 4, 5, 6).build();

        final Wave5MomentumDivergenceRule rule = new Wave5MomentumDivergenceRule(series -> foreign);

        assertThatThrownBy(() -> rule.evaluate(candidate(WaveDirection.BULLISH, 100, 110, 105, 120, 125, 130), studied))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("different series");
    }

    @Test
    void boundedCacheEvictsOldSeriesButKeepsResultsCorrect() {
        // More distinct series than MAX_CACHED_SERIES flow through one rule
        // instance: eviction must not corrupt or cross-wire any binding.
        final List<Indicator<Num>> momenta = new ArrayList<>();
        final List<BarSeries> series = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            final Indicator<Num> bound = momentum(0, 0, 0, 10, 8, index % 2 == 0 ? 0 : 20);
            momenta.add(bound);
            series.add(bound.getBarSeries());
        }
        final Wave5MomentumDivergenceRule rule = new Wave5MomentumDivergenceRule(
                bound -> momenta.stream().filter(m -> m.getBarSeries() == bound).findFirst().orElseThrow());

        for (int round = 0; round < 2; round++) {
            for (int index = 0; index < series.size(); index++) {
                final RuleEvidence evidence = rule
                        .evaluate(candidate(WaveDirection.BULLISH, 100, 110, 105, 120, 125, 130), series.get(index));
                if (index % 2 == 0) {
                    assertThat(evidence.state()).isEqualTo(EvidenceState.PASS);
                    assertThat(evidence.score()).hasValue(1.0d);
                } else {
                    assertThat(evidence.state()).isEqualTo(EvidenceState.FAIL);
                    assertThat(evidence.observations()).contains("aligned");
                }
            }
        }
    }

    @Test
    void doesNotApplyToCorrectiveGrammar() {
        final Indicator<Num> momentum = momentum(0, 0, 0, 10, 8, 0);
        final Wave5MomentumDivergenceRule rule = new Wave5MomentumDivergenceRule(momentum);
        final RuleEvidence evidence = rule.evaluate(candidate(WaveDirection.BULLISH, 100, 110, 105, 115),
                momentum.getBarSeries());

        assertThat(evidence.state()).isEqualTo(EvidenceState.NOT_APPLICABLE);
    }

    @Test
    void requiresCallerProvidedMomentum() {
        assertThatThrownBy(() -> new Wave5MomentumDivergenceRule((Indicator<Num>) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void keepsDivergenceArithmeticInNumDomainBeyondDoubleRange() {
        // Wave-3 momentum 1e400 overflows double: magnitude difference and
        // ratio must be computed in the Num domain, otherwise both become
        // infinity, their ratio NaN, and scored evidence construction aborts.
        final Indicator<Num> momentum = momentum(DecimalNum.valueOf("0"), DecimalNum.valueOf("0"),
                DecimalNum.valueOf("0"), DecimalNum.valueOf("1e400"), DecimalNum.valueOf("8e399"),
                DecimalNum.valueOf("0"));
        final Wave5MomentumDivergenceRule rule = new Wave5MomentumDivergenceRule(momentum);
        final RuleEvidence evidence = rule.evaluate(candidate(WaveDirection.BULLISH, 100, 110, 105, 120, 125, 130),
                momentum.getBarSeries());

        assertThat(evidence.state()).isEqualTo(EvidenceState.PASS);
        assertThat(evidence.score()).hasValue(1.0d);
    }

    @Test
    void rejectsForeignSeriesForFixedMomentumIndicator() {
        final Indicator<Num> momentum = momentum(0, 0, 0, 10, 8, 0);
        final Wave5MomentumDivergenceRule rule = new Wave5MomentumDivergenceRule(momentum);
        final BarSeries other = new MockBarSeriesBuilder().withData(1, 2, 3, 4, 5, 6).build();

        assertThatThrownBy(() -> rule.evaluate(candidate(WaveDirection.BULLISH, 100, 110, 105, 120, 125, 130), other))
                .isInstanceOf(IllegalArgumentException.class);

        // The bound series itself still evaluates normally.
        assertThat(
                rule.evaluate(candidate(WaveDirection.BULLISH, 100, 110, 105, 120, 125, 130), momentum.getBarSeries())
                        .state())
                .isEqualTo(EvidenceState.PASS);
    }

    @Test
    void inapplicableGrammarSkipsForeignSeriesBinding() {
        // CORRECTIVE_3 is never scored by this rule, so evaluation must return
        // NOT_APPLICABLE without consulting (or failing on) a momentum
        // indicator bound to a different series.
        final Indicator<Num> momentum = momentum(0, 0, 0, 10, 8, 0);
        final Wave5MomentumDivergenceRule rule = new Wave5MomentumDivergenceRule(momentum);
        final BarSeries other = new MockBarSeriesBuilder().withData(1, 2, 3, 4, 5, 6).build();

        final RuleEvidence evidence = rule.evaluate(candidate(WaveDirection.BULLISH, 100, 110, 105, 120), other);
        assertThat(evidence.state()).isEqualTo(EvidenceState.NOT_APPLICABLE);
    }

    private static Indicator<Num> momentum(final BarSeries series, final double... values) {
        final List<Num> momentumValues = new ArrayList<>(values.length);
        for (double value : values) {
            momentumValues.add(DoubleNum.valueOf(value));
        }
        return new MockIndicator(series, momentumValues);
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

    private static BarSeries equalSeries() {
        final BarSeries template = new MockBarSeriesBuilder().withData(new double[6]).build();
        return new EqualBarSeries(template.getBarData());
    }

    private static final class EqualBarSeries extends BaseBarSeries {

        private EqualBarSeries(final List<Bar> bars) {
            super("equal-series", bars);
        }

        @Override
        public boolean equals(final Object other) {
            return other instanceof EqualBarSeries;
        }

        @Override
        public int hashCode() {
            return 1;
        }
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

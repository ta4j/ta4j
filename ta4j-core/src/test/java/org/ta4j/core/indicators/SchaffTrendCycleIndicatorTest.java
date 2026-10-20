/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.ta4j.core.indicators.IndicatorSerializationRoundTripTestSupport.serializationSeries;
import static org.ta4j.core.indicators.IndicatorSerializationRoundTripTestSupport.stableIndexes;

import java.util.List;
import org.ta4j.core.BarSeries;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class SchaffTrendCycleIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public SchaffTrendCycleIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void calculatesSchaffTrendCycle() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(44.34, 44.09, 44.15, 43.61, 44.33, 44.83, 45.10, 45.42, 45.84, 46.08, 45.89, 46.03, 45.61,
                        46.28, 46.28, 46.00, 46.03, 46.41, 46.22, 45.64)
                .build();
        final var closePrice = new ClosePriceIndicator(series);
        // Use smaller parameters to allow testing after unstable period with limited
        // data
        final var indicator = new SchaffTrendCycleIndicator(closePrice, 3, 5, 3, 2);

        int unstableBars = indicator.getCountOfUnstableBars();
        // Unstable period: 5 + 3 + 2 + 3 + 2 = 15
        assertThat(unstableBars).isEqualTo(15);

        // During unstable period, should return NaN
        for (int i = 0; i < unstableBars; i++) {
            assertThat(Num.isNaNOrNull(indicator.getValue(i))).isTrue();
        }

        // After unstable period, verify values are valid and in expected range
        for (int i = unstableBars; i < series.getBarCount(); i++) {
            Num value = indicator.getValue(i);
            assertThat(Num.isNaNOrNull(value)).isFalse();
            // Schaff Trend Cycle values should be between 0 and 100
            assertThat(value.doubleValue()).isBetween(0.0, 100.0);
        }
    }

    @Test
    public void returnsPreviousValueWhenRangeIsZero() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(5, 5, 5, 5, 5, 5).build();
        final var closePrice = new ClosePriceIndicator(series);
        final var indicator = new SchaffTrendCycleIndicator(closePrice, 3, 5, 3, 2);

        for (int i = 1; i < series.getBarCount(); i++) {
            assertThat(indicator.getValue(i)).isEqualByComparingTo(indicator.getValue(i - 1));
        }
    }

    @Test
    public void returnsValidValuesForNormalData() {
        // Test that normal data produces valid (non-NaN) results after unstable period
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25)
                .build();
        final var closePrice = new ClosePriceIndicator(series);
        final var indicator = new SchaffTrendCycleIndicator(closePrice, 5, 10, 3, 3);

        // After unstable period, values should be valid (not NaN)
        int unstableBars = indicator.getCountOfUnstableBars();
        for (int i = unstableBars; i < series.getBarCount(); i++) {
            Num value = indicator.getValue(i);
            assertThat(value.isNaN()).isFalse();
            // Schaff Trend Cycle values should be between 0 and 100
            assertThat(value.doubleValue()).isBetween(0.0, 100.0);
        }
    }

    @Test
    public void returnsNaNDuringUnstablePeriod() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25)
                .build();
        final var closePrice = new ClosePriceIndicator(series);
        final var indicator = new SchaffTrendCycleIndicator(closePrice, 5, 10, 3, 3);

        int unstableBars = indicator.getCountOfUnstableBars();
        // Unstable period: 10 + 3 + 3 + 3 + 3 = 22
        assertThat(unstableBars).isEqualTo(22);

        // All indices before unstable period should return NaN
        for (int i = 0; i < unstableBars; i++) {
            assertThat(Num.isNaNOrNull(indicator.getValue(i))).isTrue();
        }
    }

    @Test
    public void continuesLiveAndEveryRetainedValueAfterHeadAdvances() {
        for (int capacity : new int[] { 32, 64 }) {
            BarSeries bounded = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
            BarSeries unbounded = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
            bounded.setMaximumBarCount(capacity);
            SchaffTrendCycleIndicator continued = new SchaffTrendCycleIndicator(new ClosePriceIndicator(bounded), 3, 5,
                    3, 2);
            SchaffTrendCycleIndicator reference = new SchaffTrendCycleIndicator(new ClosePriceIndicator(unbounded), 3,
                    5, 3, 2);
            for (int i = 0; i < 160; i++) {
                double close = 100 + 10 * Math.sin(i * 0.5) + 0.3 * i;
                bounded.barBuilder().closePrice(close).add();
                unbounded.barBuilder().closePrice(close).add();
                assertThat(continued.getValue(i)).as("live index %s, capacity %s", i, capacity)
                        .isEqualByComparingTo(reference.getValue(i));
                for (int retained = bounded.getBeginIndex(); retained <= i; retained++) {
                    assertThat(continued.getValue(retained)).as("retained index %s at %s", retained, i)
                            .isEqualByComparingTo(reference.getValue(retained));
                }
            }
        }
    }

    @Test
    public void carriesNonFlatValueAcrossLongFlatStretchButFreshInstanceReseeds() {
        BarSeries bounded = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        BarSeries unbounded = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        bounded.setMaximumBarCount(32);
        SchaffTrendCycleIndicator continued = new SchaffTrendCycleIndicator(new ClosePriceIndicator(bounded), 1, 3, 3,
                1);
        SchaffTrendCycleIndicator reference = new SchaffTrendCycleIndicator(new ClosePriceIndicator(unbounded), 1, 3, 3,
                1);
        // The slow EMA has an exact half multiplier: 4 -> 0 seeds it at 2,
        // then constant closes of 2 make MACD exactly zero. Both stochastic
        // stages must carry the non-flat 100 long after that event is evicted.
        for (int i = 0; i < 128; i++) {
            int close = i < 20 ? 4 : i == 20 ? 0 : 2;
            bounded.barBuilder().closePrice(close).add();
            unbounded.barBuilder().closePrice(close).add();
            assertThat(continued.getValue(i)).isEqualByComparingTo(reference.getValue(i));
        }
        assertThat(continued.getValue(127)).isEqualByComparingTo(numFactory.hundred());
        SchaffTrendCycleIndicator fresh = new SchaffTrendCycleIndicator(new ClosePriceIndicator(bounded), 1, 3, 3, 1);
        BarSeries retained = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = bounded.getBeginIndex(); i <= bounded.getEndIndex(); i++) {
            retained.barBuilder().closePrice(bounded.getBar(i).getClosePrice()).add();
        }
        SchaffTrendCycleIndicator reseeded = new SchaffTrendCycleIndicator(new ClosePriceIndicator(retained), 1, 3, 3,
                1);
        assertThat(fresh.getValue(127)).isEqualByComparingTo(reseeded.getValue(retained.getEndIndex()))
                .isEqualByComparingTo(numFactory.zero());
    }

    @Test
    public void explicitUpstreamResetStillInvalidatesTheCompleteChain() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        StochasticIndicator resettingSource = new StochasticIndicator(new ClosePriceIndicator(series), 4);
        SchaffTrendCycleIndicator indicator = new SchaffTrendCycleIndicator(resettingSource, 3, 5, 3, 2);
        for (int i = 0; i < 96; i++) {
            series.barBuilder().closePrice(100 + 10 * Math.sin(i * 0.5) + i * 0.3).add();
            indicator.getValue(i);
        }
        Num before = indicator.getValue(95);
        series.setMaximumBarCount(32);
        SchaffTrendCycleIndicator fresh = new SchaffTrendCycleIndicator(
                new StochasticIndicator(new ClosePriceIndicator(series), 4), 3, 5, 3, 2);
        Num after = indicator.getValue(95);
        assertThat(after).isEqualByComparingTo(fresh.getValue(95)).isNotEqualByComparingTo(before);
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertThat(indicator.getValue(i)).isEqualByComparingTo(fresh.getValue(i));
        }
    }

    @Test
    public void terminalMutationAndAppendContinueFromUpdatedValues() {
        BarSeries bounded = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        BarSeries unbounded = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        bounded.setMaximumBarCount(32);
        SchaffTrendCycleIndicator indicator = new SchaffTrendCycleIndicator(new ClosePriceIndicator(bounded), 3, 5, 3,
                2);
        SchaffTrendCycleIndicator reference = new SchaffTrendCycleIndicator(new ClosePriceIndicator(unbounded), 3, 5, 3,
                2);
        for (int i = 0; i < 96; i++) {
            double close = 100 + 10 * Math.sin(i * 0.5);
            bounded.barBuilder().closePrice(close).add();
            unbounded.barBuilder().closePrice(close).add();
            assertThat(indicator.getValue(i)).isEqualByComparingTo(reference.getValue(i));
        }
        Num before = indicator.getValue(95);
        bounded.getLastBar().addPrice(numFactory.numOf(150));
        unbounded.getLastBar().addPrice(numFactory.numOf(150));
        assertThat(indicator.getValue(95)).isEqualByComparingTo(reference.getValue(95)).isNotEqualByComparingTo(before);
        Num mutated = indicator.getValue(95);
        bounded.addBar(bounded.barBuilder().endTime(bounded.getLastBar().getEndTime()).closePrice(80).build(), true);
        unbounded.addBar(unbounded.barBuilder().endTime(unbounded.getLastBar().getEndTime()).closePrice(80).build(),
                true);
        assertThat(indicator.getValue(95)).isEqualByComparingTo(reference.getValue(95)).isNotEqualByComparingTo(before);
        bounded.barBuilder().closePrice(90).add();
        unbounded.barBuilder().closePrice(90).add();
        assertThat(indicator.getValue(96)).isEqualByComparingTo(reference.getValue(96));
        assertThat(indicator.getValue(95)).isEqualByComparingTo(reference.getValue(95));
    }

    @Test
    public void invalidInputRecoversOnAContinuedSeries() {
        BarSeries bounded = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        BarSeries unbounded = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        bounded.setMaximumBarCount(32);
        Indicator<Num> boundedSource = sourceWithMissingBar(bounded);
        Indicator<Num> unboundedSource = sourceWithMissingBar(unbounded);
        SchaffTrendCycleIndicator indicator = new SchaffTrendCycleIndicator(boundedSource, 3, 5, 3, 2);
        SchaffTrendCycleIndicator reference = new SchaffTrendCycleIndicator(unboundedSource, 3, 5, 3, 2);
        for (int i = 0; i < 96; i++) {
            bounded.barBuilder().closePrice(100 + 10 * Math.sin(i * 0.5)).add();
            unbounded.barBuilder().closePrice(100 + 10 * Math.sin(i * 0.5)).add();
            assertThat(indicator.getValue(i)).isEqualByComparingTo(reference.getValue(i));
            if (i == 48) {
                assertThat(indicator.getValue(i).isNaN()).isTrue();
            }
        }
        assertThat(Num.isFinite(indicator.getValue(95))).isTrue();
    }

    private Indicator<Num> sourceWithMissingBar(BarSeries series) {
        return new ClosePriceIndicator(series) {
            @Override
            public Num getValue(int index) {
                return index == 48 ? NaN.NaN : super.getValue(index);
            }
        };
    }

    @Test
    public void headAdvanceDuringColdReadReseedsAtTheNewHead() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.setMaximumBarCount(32);
        for (int i = 0; i < 128; i++) {
            series.barBuilder().closePrice(100 + 10 * Math.sin(i * 0.5)).add();
        }
        Indicator<Num> advancing = new ClosePriceIndicator(series) {
            private boolean triggered;

            @Override
            public Num getValue(int index) {
                if (index == 102 && !triggered) {
                    triggered = true;
                    for (int i = 128; i < 144; i++) {
                        series.barBuilder().closePrice(100 + 10 * Math.sin(i * 0.5)).add();
                    }
                }
                return super.getValue(index);
            }
        };
        SchaffTrendCycleIndicator raced = new SchaffTrendCycleIndicator(advancing, 3, 5, 3, 2);
        Num result = raced.getValue(127);
        assertThat(series.getBeginIndex()).isEqualTo(112);
        SchaffTrendCycleIndicator fresh = new SchaffTrendCycleIndicator(new ClosePriceIndicator(series), 3, 5, 3, 2);
        assertThat(result).isEqualByComparingTo(fresh.getValue(127));
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertThat(raced.getValue(i)).as("index %s after head-advance race", i)
                    .isEqualByComparingTo(fresh.getValue(i));
        }
    }

    @Test
    public void steadyAppendWorkDoesNotGrowWithRetentionCapacity() {
        int[] small = countSteadyAppendWork(32);
        int[] large = countSteadyAppendWork(128);
        assertThat(small).containsExactly(large);
        assertThat(small[0]).isEqualTo(64);
        assertThat(small[1]).isBetween(1, 64 * 4);
    }

    private int[] countSteadyAppendWork(int capacity) {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.setMaximumBarCount(capacity);
        int[] reads = { 0 };
        Indicator<Num> source = new ClosePriceIndicator(series) {
            @Override
            public Num getValue(int index) {
                reads[0]++;
                return super.getValue(index);
            }
        };
        int[] calculations = { 0 };
        SchaffTrendCycleIndicator indicator = new SchaffTrendCycleIndicator(source, 3, 5, 3, 2) {
            @Override
            protected Num calculate(int index) {
                calculations[0]++;
                return super.calculate(index);
            }
        };
        for (int i = 0; i < 256; i++) {
            series.barBuilder().closePrice(100 + 10 * Math.sin(i * 0.5)).add();
            assertThat(Num.isFinite(indicator.getValue(i))).isEqualTo(i >= indicator.getCountOfUnstableBars());
        }
        // Cold initialization and the first head advance are excluded deliberately.
        reads[0] = 0;
        calculations[0] = 0;
        for (int i = 256; i < 320; i++) {
            series.barBuilder().closePrice(100 + 10 * Math.sin(i * 0.5)).add();
            assertThat(Num.isFinite(indicator.getValue(i))).isTrue();
        }
        return new int[] { calculations[0], reads[0] };
    }

    @Test
    public void validatesPeriodsAndPreservesDefaultConfiguration() {
        BarSeries series = serializationSeries(numFactory);
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        for (int[] periods : new int[][] { { 0, 5, 3, 2 }, { 3, 0, 3, 2 }, { 3, 5, 0, 2 }, { 3, 5, 3, 0 },
                { 5, 5, 3, 2 }, { 6, 5, 3, 2 } }) {
            assertThrows(IllegalArgumentException.class,
                    () -> new SchaffTrendCycleIndicator(close, periods[0], periods[1], periods[2], periods[3]));
        }
        SchaffTrendCycleIndicator defaults = new SchaffTrendCycleIndicator(close);
        SchaffTrendCycleIndicator explicit = new SchaffTrendCycleIndicator(close, 23, 50, 10, 3);
        assertThat(defaults.getCountOfUnstableBars()).isEqualTo(76);
        assertThat(defaults.toDescriptor()).isEqualTo(explicit.toDescriptor());
        assertThat(defaults.getValue(series.getEndIndex()))
                .isEqualByComparingTo(explicit.getValue(series.getEndIndex()));
    }

    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        BarSeries series = serializationSeries(numFactory);
        ClosePriceIndicator close = new ClosePriceIndicator(series);

        return List.of(
                serializationFixture(series, new SchaffTrendCycleIndicator(close, 3, 6, 4, 2), stableIndexes(series)));
    }

}

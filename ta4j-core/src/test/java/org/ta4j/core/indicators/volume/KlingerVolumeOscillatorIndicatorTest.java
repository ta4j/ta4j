/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import static org.ta4j.core.indicators.IndicatorSerializationRoundTripTestSupport.serializationSeries;
import static org.ta4j.core.indicators.IndicatorSerializationRoundTripTestSupport.stableIndexes;

import java.util.List;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.ConcurrentBarSeriesBuilder;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Regression tests for {@link KlingerVolumeOscillatorIndicator}.
 */
public class KlingerVolumeOscillatorIndicatorTest extends AbstractIndicatorTest<BarSeries, Num> {

    private static final double[] BULLISH_EXPECTED = { -9139.65225161, 2505.12759431, 9624.87726624, 12684.19073606,
            16564.67757807, 17382.50889971 };

    private static final double[] BEARISH_EXPECTED = { 9345.65237103, -2472.19724983, -9674.66125703, -12734.79507168,
            -16657.62635653, -17455.73721387 };

    private static final double[] SIDEWAYS_EXPECTED = { 58541.66666667, 18215.27777778, 35153.93518519, 1574.49845679,
            23452.26980453, -6480.51804698 };

    private static final double[] BULLISH_250X_EXPECTED = { -22849.130629025, 6262.818985775, 24062.1931656,
            31710.47684015, 41411.693945175, 43456.272249275 };

    private static final double[] BEARISH_250X_EXPECTED = { 23364.130927575, -6180.493124575, -24186.653142575,
            -31836.9876792, -41644.065891325, -43639.343034675 };

    private static final double[] SIDEWAYS_250X_EXPECTED = { 146354.166666675, 45538.19444445, 87884.837962975,
            3936.246141975, 58630.674511325, -16201.29511745 };

    private static final double[][] BULLISH_OHLCV = { { 100, 102, 103, 99, 1000 }, { 102, 104, 105, 101, 1100 },
            { 104, 107, 108, 103, 1200 }, { 107, 109, 110, 106, 1300 }, { 109, 112, 113, 108, 1400 },
            { 112, 114, 115, 111, 1500 }, { 114, 117, 118, 113, 1600 }, { 117, 119, 120, 116, 1700 },
            { 119, 121, 122, 118, 1800 }, { 121, 124, 125, 120, 1900 }, { 124, 126, 127, 123, 2000 },
            { 126, 129, 130, 125, 2100 } };

    private static final double[][] BEARISH_OHLCV = { { 130, 128, 131, 127, 1050 }, { 128, 126, 129, 125, 1150 },
            { 126, 123, 127, 122, 1250 }, { 123, 121, 124, 120, 1350 }, { 121, 118, 122, 117, 1450 },
            { 118, 116, 119, 115, 1550 }, { 116, 113, 117, 112, 1650 }, { 113, 111, 114, 110, 1750 },
            { 111, 109, 112, 108, 1850 }, { 109, 106, 110, 105, 1950 }, { 106, 104, 107, 103, 2050 },
            { 104, 101, 105, 100, 2150 } };

    private static final double[][] SIDEWAYS_OHLCV = { { 100, 101, 102, 99, 1000 }, { 101, 100, 102, 99, 980 },
            { 100, 101, 102, 99, 1020 }, { 101, 100, 102, 99, 1000 }, { 100, 101, 102, 99, 990 },
            { 101, 100, 102, 99, 1010 }, { 100, 101, 102, 99, 995 }, { 101, 100, 102, 99, 1005 },
            { 100, 101, 102, 99, 1000 }, { 101, 100, 102, 99, 1002 }, { 100, 101, 102, 99, 998 },
            { 101, 100, 102, 99, 1001 } };

    public KlingerVolumeOscillatorIndicatorTest(final NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void matchesSpreadsheetReferenceValuesWithDefaultScaleAcrossScenarios() {
        // Spreadsheet references were generated with Klinger VF, EMA(3), EMA(5),
        // and KVO = EMA(3) - EMA(5).
        assertScenarioMatches(bullish(numFactory), BULLISH_EXPECTED);
        assertScenarioMatches(bearish(numFactory), BEARISH_EXPECTED);
        assertScenarioMatches(sideways(numFactory), SIDEWAYS_EXPECTED);
    }

    @Test
    public void matchesSpreadsheetReferenceValuesWithCustomScaleAcrossScenarios() {
        // Spreadsheet references were generated using the same input bars and periods,
        // with Klinger VF scale multiplier set to 250.
        assertScenarioMatches(bullish(numFactory), BULLISH_250X_EXPECTED, 250);
        assertScenarioMatches(bearish(numFactory), BEARISH_250X_EXPECTED, 250);
        assertScenarioMatches(sideways(numFactory), SIDEWAYS_250X_EXPECTED, 250);
    }

    @Test
    public void supportsSourceIndicatorConstructorWithCustomScale() {
        final BarSeries series = bullish(numFactory);
        final KlingerVolumeOscillatorIndicator bySeries = new KlingerVolumeOscillatorIndicator(series, 3, 5, 250);
        final KlingerVolumeOscillatorIndicator byIndicators = new KlingerVolumeOscillatorIndicator(
                new HighPriceIndicator(series), new LowPriceIndicator(series), new ClosePriceIndicator(series),
                new VolumeIndicator(series), 3, 5, 250);

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertNumEquals(bySeries.getValue(i), byIndicators.getValue(i));
        }
    }

    @Test
    public void reportsExpectedUnstableBoundary() {
        final BarSeries series = bullish(numFactory);
        final KlingerVolumeOscillatorIndicator indicator = new KlingerVolumeOscillatorIndicator(series, 3, 5);

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(6);
        assertThat(Num.isNaNOrNull(indicator.getValue(5))).isTrue();
        assertThat(Num.isNaNOrNull(indicator.getValue(6))).isFalse();
    }

    @Test
    public void continuesNegativeTrendAndCumulativeMeasurementThroughZeroVolumePlateau() {
        // Equal-basis bars retain the negative direction established at indexes
        // 1..2, even after those bars leave the window. Zero volume resets the
        // EMAs, not the trend or cumulative measurement.
        final BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(10).closePrice(10).highPrice(14).lowPrice(10).volume(1000).add(); // basis 34
        series.barBuilder().openPrice(9).closePrice(9).highPrice(13).lowPrice(9).volume(1000).add(); // basis 31
        series.barBuilder().openPrice(9).closePrice(9).highPrice(12).lowPrice(9).volume(1000).add(); // basis 30
        for (int i = 0; i < 68; i++) {
            series.barBuilder().openPrice(9).closePrice(9).highPrice(12).lowPrice(9).volume(0).add(); // basis 30
        }
        final KlingerVolumeOscillatorIndicator oscillator = new KlingerVolumeOscillatorIndicator(series, 3, 5);
        assertThat(Num.isNaNOrNull(oscillator.getValue(70))).isTrue();

        series.barBuilder().openPrice(9).closePrice(9).highPrice(12).lowPrice(9).volume(1000).add(); // index 71
        series.barBuilder().openPrice(9).closePrice(9).highPrice(12).lowPrice(9).volume(1000).add(); // index 72
        series.setMaximumBarCount(12);
        assertThat(series.getBeginIndex()).isEqualTo(61);

        // Cumulative measurement is 218 at index 71 and 221 at index 72.
        // Both EMAs recover at 71; their multipliers differ by 1/6 at 72.
        assertNumEquals(numFactory.numOf(-300000d / (218d * 221d)), oscillator.getValue(72), 1e-9);
    }

    @Test
    public void preservesComputedRetainedValuesInsteadOfReanchoringToFreshHistory() {
        final BarSeries series = klingerPlateauSeries();
        final KlingerVolumeOscillatorIndicator oscillator = new KlingerVolumeOscillatorIndicator(series, 3, 5);
        final Num preAdvanceValue = oscillator.getValue(72);
        assertThat(Num.isNaNOrNull(preAdvanceValue)).isFalse();

        series.setMaximumBarCount(12);
        assertThat(series.getBeginIndex()).isEqualTo(61);

        final BarSeries advancedSeries = klingerPlateauSeries();
        advancedSeries.setMaximumBarCount(12);
        final KlingerVolumeOscillatorIndicator cold = new KlingerVolumeOscillatorIndicator(advancedSeries, 3, 5);

        final Num postAdvanceValue = oscillator.getValue(72);
        assertThat(postAdvanceValue).isEqualByComparingTo(preAdvanceValue);
        assertThat(postAdvanceValue.isEqual(cold.getValue(72))).isFalse();
    }

    @Test
    public void continuesAcrossWindowsShorterThanWarmup() {
        for (int capacity : new int[] { 2, 3, 8, 32 }) {
            BarSeries bounded = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
            bounded.setMaximumBarCount(capacity);
            BarSeries unbounded = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
            KlingerVolumeOscillatorIndicator continued = new KlingerVolumeOscillatorIndicator(bounded, 3, 5);
            KlingerVolumeOscillatorIndicator reference = new KlingerVolumeOscillatorIndicator(unbounded, 3, 5);
            for (int index = 0; index < 160; index++) {
                appendContinuationBar(bounded, index);
                appendContinuationBar(unbounded, index);
                assertSameNumOrNaN(reference.getValue(index), continued.getValue(index));
                for (int retained = bounded.getBeginIndex(); retained < index; retained++) {
                    assertSameNumOrNaN(reference.getValue(retained), continued.getValue(retained));
                }
            }
        }
    }

    @Test
    public void freshAndLostPredecessorChainsSeedFromIndependentRetainedHistory() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.setMaximumBarCount(12);
        KlingerVolumeOscillatorIndicator continued = new KlingerVolumeOscillatorIndicator(series, 3, 5);
        for (int index = 0; index < 32; index++) {
            appendContinuationBar(series, index);
            continued.getValue(index);
        }
        assertFreshRetainedReference(series, new KlingerVolumeOscillatorIndicator(series, 3, 5));

        // Skip enough observations to lose every predecessor, not just its inputs.
        for (int index = 32; index < 64; index++) {
            appendContinuationBar(series, index);
        }
        assertFreshRetainedReference(series, continued);
    }

    @Test
    public void restartsTheCompleteWarmupAfterSeedingAtAPositiveRetainedHead() {
        for (boolean readBeforeGap : new boolean[] { false, true }) {
            BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
            series.setMaximumBarCount(12);
            KlingerVolumeOscillatorIndicator actual = new KlingerVolumeOscillatorIndicator(series, 3, 5);
            for (int index = 0; index < 32; index++) {
                appendContinuationBar(series, index + 45);
                if (readBeforeGap && index == 15) {
                    actual.getValue(index);
                }
            }
            if (!readBeforeGap) {
                actual = new KlingerVolumeOscillatorIndicator(series, 3, 5);
            }
            int beginIndex = series.getBeginIndex();
            assertThat(Num.isNaNOrNull(actual.getValue(beginIndex + 5))).isTrue();
            assertThat(Num.isFinite(actual.getValue(beginIndex + 6))).isTrue();
            assertFreshRetainedReference(series, actual);
        }
    }

    private void assertFreshRetainedReference(BarSeries series, KlingerVolumeOscillatorIndicator actual) {
        BarSeries independent = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withBars(series.getBarData())
                .build();
        KlingerVolumeOscillatorIndicator reference = new KlingerVolumeOscillatorIndicator(independent, 3, 5);
        int beginIndex = series.getBeginIndex();
        assertThat(actual.getCountOfUnstableBars()).isEqualTo(6);
        assertSameNumOrNaN(reference.getValue(series.getEndIndex() - beginIndex),
                actual.getValue(series.getEndIndex()));
        for (int index = beginIndex; index <= series.getEndIndex(); index++) {
            assertSameNumOrNaN(reference.getValue(index - beginIndex), actual.getValue(index));
        }
    }

    @Test
    public void explicitResetOnEitherPriceRebuildsTheWholeContinuedChain() {
        for (boolean resetHigh : new boolean[] { true, false }) {
            BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
            KlingerVolumeOscillatorIndicator continued = resettingPriceOscillator(series, resetHigh);
            for (int index = 0; index < 80; index++) {
                appendContinuationBar(series, index);
                continued.getValue(index);
            }
            Num before = continued.getValue(79);
            series.setMaximumBarCount(24);
            KlingerVolumeOscillatorIndicator fresh = resettingPriceOscillator(series, resetHigh);
            Num rebuilt = continued.getValue(79);
            assertThat(Num.isFinite(rebuilt)).isTrue();
            assertSameNumOrNaN(fresh.getValue(79), rebuilt);
            assertThat(rebuilt.isEqual(before)).isFalse();
            for (int index = series.getBeginIndex(); index <= series.getEndIndex(); index++) {
                assertSameNumOrNaN(fresh.getValue(index), continued.getValue(index));
            }
        }
    }

    private KlingerVolumeOscillatorIndicator resettingPriceOscillator(BarSeries series, boolean resetHigh) {
        Indicator<Num> resetting = new CachedIndicator<Num>(series) {
            @Override
            protected Num calculate(int index) {
                Num offset = numFactory.numOf(getBarSeries().getBeginIndex());
                return resetHigh ? getBarSeries().getBar(index).getHighPrice().plus(offset)
                        : getBarSeries().getBar(index).getLowPrice().minus(offset);
            }

            @Override
            protected boolean requiresFullCacheInvalidationAfterHeadAdvance() {
                return true;
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }
        };
        return new KlingerVolumeOscillatorIndicator(resetHigh ? resetting : new HighPriceIndicator(series),
                resetHigh ? new LowPriceIndicator(series) : resetting, new ClosePriceIndicator(series),
                new VolumeIndicator(series), 3, 5);
    }

    @Test
    public void terminalMutationThenAppendPreservesTheRevisedHistory() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.setMaximumBarCount(2);
        BarSeries unbounded = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        KlingerVolumeOscillatorIndicator continued = new KlingerVolumeOscillatorIndicator(series, 3, 5);
        KlingerVolumeOscillatorIndicator reference = new KlingerVolumeOscillatorIndicator(unbounded, 3, 5);
        for (int index = 0; index < 90; index++) {
            appendContinuationBar(series, index);
            appendContinuationBar(unbounded, index);
            assertSameNumOrNaN(reference.getValue(index), continued.getValue(index));
            if (index >= 10 && index % 3 == 0) {
                for (BarSeries mutated : new BarSeries[] { series, unbounded }) {
                    mutated.getLastBar().addTrade(numFactory.numOf(7), numFactory.numOf(50 + index % 7));
                    mutated.getLastBar().addPrice(numFactory.numOf(25 - index % 4));
                }
                assertSameNumOrNaN(reference.getValue(index), continued.getValue(index));
            }
        }
    }

    @Test
    public void steadyAppendSourceWorkDoesNotGrowWithRetentionCapacity() {
        int baselineWork = -1;
        for (int capacity : new int[] { 2, 8, 32, 128 }) {
            BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
            series.setMaximumBarCount(capacity);
            AtomicInteger reads = new AtomicInteger();
            Indicator<Num> high = new HighPriceIndicator(series) {
                @Override
                public Num getValue(int index) {
                    reads.incrementAndGet();
                    return super.getValue(index);
                }
            };
            KlingerVolumeOscillatorIndicator oscillator = new KlingerVolumeOscillatorIndicator(high,
                    new LowPriceIndicator(series), new ClosePriceIndicator(series), new VolumeIndicator(series), 3, 5);
            for (int index = 0; index < 160; index++) {
                appendContinuationBar(series, index);
                oscillator.getValue(index);
            }
            int before = reads.get();
            for (int index = 160; index < 240; index++) {
                appendContinuationBar(series, index);
                int beforeAppendRead = reads.get();
                oscillator.getValue(index);
                assertThat(reads.get() - beforeAppendRead).isLessThanOrEqualTo(8);
            }
            int work = reads.get() - before;
            if (baselineWork < 0) {
                baselineWork = work;
            } else {
                assertThat(work).isEqualTo(baselineWork);
            }
        }
    }

    @Test
    public void retriesTheWholeChainWhenConcurrentPruningRemovesAnInputMidRead() throws Exception {
        BarSeries series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory).build();
        series.setMaximumBarCount(24);
        for (int index = 0; index < 24; index++) {
            appendContinuationBar(series, index);
        }
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean armed = new AtomicBoolean(true);
        Indicator<Num> close = new AbstractIndicator<Num>(series) {
            @Override
            public Num getValue(int index) {
                if (index == 19 && armed.compareAndSet(true, false)) {
                    entered.countDown();
                    try {
                        assertTrue(release.await(5, TimeUnit.SECONDS), "head-race source was not released");
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(interrupted);
                    }
                }
                return getBarSeries().getBar(index).getClosePrice();
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }
        };
        KlingerVolumeOscillatorIndicator raced = new KlingerVolumeOscillatorIndicator(new HighPriceIndicator(series),
                new LowPriceIndicator(series), close, new VolumeIndicator(series), 3, 5);
        ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "klinger-head-race");
            thread.setDaemon(true);
            return thread;
        });
        try {
            Future<Num> read = executor.submit(() -> raced.getValue(23));
            assertTrue(entered.await(5, TimeUnit.SECONDS), "head-race source was not reached");
            for (int index = 24; index < 48; index++) {
                appendContinuationBar(series, index);
            }
            release.countDown();
            KlingerVolumeOscillatorIndicator fresh = new KlingerVolumeOscillatorIndicator(series, 3, 5);
            assertSameNumOrNaN(fresh.getValue(23), read.get(5, TimeUnit.SECONDS));
            assertFreshRetainedReference(series, raced);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private static void appendContinuationBar(BarSeries series, int index) {
        int phase = index % 80;
        double basis = phase < 3 ? 120 - phase * 3 : phase < 45 ? 114 : 114 + 3 * ((phase - 45) % 9);
        double measurement = 2 + index % 5;
        double close = basis / 3;
        double volume = phase >= 15 && phase < 28 ? 0 : 1000 + index % 11 * 37;
        series.barBuilder()
                .timePeriod(Duration.ofSeconds(1))
                .endTime(Instant.EPOCH.plusSeconds(index + 1L))
                .openPrice(close)
                .closePrice(close)
                .highPrice(close + measurement / 2)
                .lowPrice(close - measurement / 2)
                .volume(volume)
                .add();
    }

    private BarSeries klingerPlateauSeries() {
        final BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(10).closePrice(10).highPrice(14).lowPrice(10).volume(1000).add(); // basis 34
        series.barBuilder().openPrice(9).closePrice(9).highPrice(13).lowPrice(9).volume(1000).add(); // basis 31
        series.barBuilder().openPrice(9).closePrice(9).highPrice(12).lowPrice(9).volume(1000).add(); // basis 30
        for (int i = 0; i < 70; i++) {
            series.barBuilder().openPrice(9).closePrice(9).highPrice(12).lowPrice(9).volume(1000).add(); // basis 30
        }
        return series;
    }

    @Test
    public void throwsForInvalidPeriods() {
        final BarSeries series = bullish(numFactory);

        assertThrows(IllegalArgumentException.class, () -> new KlingerVolumeOscillatorIndicator(series, 0, 5));
        assertThrows(IllegalArgumentException.class, () -> new KlingerVolumeOscillatorIndicator(series, 5, 5));
        assertThrows(IllegalArgumentException.class, () -> new KlingerVolumeOscillatorIndicator(series, 6, 5));
    }

    @Test
    public void throwsForInvalidScaleMultiplier() {
        final BarSeries series = bullish(numFactory);

        assertThrows(IllegalArgumentException.class, () -> new KlingerVolumeOscillatorIndicator(series, 3, 5, null));
        assertThrows(IllegalArgumentException.class, () -> new KlingerVolumeOscillatorIndicator(series, 3, 5, 0));
        assertThrows(IllegalArgumentException.class, () -> new KlingerVolumeOscillatorIndicator(series, 3, 5, -100));
        assertThrows(IllegalArgumentException.class,
                () -> new KlingerVolumeOscillatorIndicator(series, 3, 5, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> new KlingerVolumeOscillatorIndicator(series, 3, 5, Double.POSITIVE_INFINITY));
    }

    @Test
    public void toStringIncludesConfiguredPeriodsAndScale() {
        final KlingerVolumeOscillatorIndicator indicator = new KlingerVolumeOscillatorIndicator(bullish(numFactory), 3,
                5, 250);

        assertThat(indicator.toString()).contains("shortPeriod: 3")
                .contains("longPeriod: 5")
                .contains("scaleMultiplier: 250");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void serializationRoundTripPreservesValuesAndUnstableBars() {
        final BarSeries series = bullish(numFactory);
        final KlingerVolumeOscillatorIndicator original = new KlingerVolumeOscillatorIndicator(series, 3, 5, 250);

        final String json = original.toJson();
        final Indicator<Num> restoredBase = (Indicator<Num>) Indicator.fromJson(series, json);

        assertThat(restoredBase).isInstanceOf(KlingerVolumeOscillatorIndicator.class);
        assertThat(restoredBase.toDescriptor()).isEqualTo(original.toDescriptor());
        assertThat(restoredBase.getCountOfUnstableBars()).isEqualTo(original.getCountOfUnstableBars());

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertSameNumOrNaN(original.getValue(i), restoredBase.getValue(i));
        }
    }

    private static BarSeries bullish(final NumFactory numFactory) {
        return buildSeries(numFactory, BULLISH_OHLCV);
    }

    private static BarSeries bearish(final NumFactory numFactory) {
        return buildSeries(numFactory, BEARISH_OHLCV);
    }

    private static BarSeries sideways(final NumFactory numFactory) {
        return buildSeries(numFactory, SIDEWAYS_OHLCV);
    }

    private static BarSeries buildSeries(final NumFactory numFactory, final double[][] ohlcvData) {
        final BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        for (double[] row : ohlcvData) {
            series.barBuilder()
                    .openPrice(row[0])
                    .closePrice(row[1])
                    .highPrice(row[2])
                    .lowPrice(row[3])
                    .volume(row[4])
                    .add();
        }

        return series;
    }

    private void assertScenarioMatches(final BarSeries series, final double[] expectedStableValues) {
        final KlingerVolumeOscillatorIndicator indicator = new KlingerVolumeOscillatorIndicator(series, 3, 5);
        assertScenarioMatchesWithIndicator(series, expectedStableValues, indicator);
    }

    private void assertScenarioMatches(final BarSeries series, final double[] expectedStableValues,
            final double scaleMultiplier) {
        final KlingerVolumeOscillatorIndicator indicator = new KlingerVolumeOscillatorIndicator(series, 3, 5,
                scaleMultiplier);
        assertScenarioMatchesWithIndicator(series, expectedStableValues, indicator);
    }

    private void assertScenarioMatchesWithIndicator(final BarSeries series, final double[] expectedStableValues,
            final KlingerVolumeOscillatorIndicator indicator) {
        final int unstableBars = indicator.getCountOfUnstableBars();

        assertThat(unstableBars).isEqualTo(6);
        assertThat(expectedStableValues.length).isEqualTo(series.getBarCount() - unstableBars);

        for (int i = series.getBeginIndex(); i < unstableBars; i++) {
            assertThat(Num.isNaNOrNull(indicator.getValue(i))).isTrue();
        }

        for (int i = unstableBars; i <= series.getEndIndex(); i++) {
            assertNumEquals(expectedStableValues[i - unstableBars], indicator.getValue(i));
        }
    }

    private void assertSameNumOrNaN(final Num expected, final Num actual) {
        if (Num.isNaNOrNull(expected) || Num.isNaNOrNull(actual)) {
            assertThat(Num.isNaNOrNull(actual)).isEqualTo(Num.isNaNOrNull(expected));
            return;
        }
        assertThat(actual).isEqualByComparingTo(expected);
    }

    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        BarSeries series = serializationSeries(numFactory);
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        HighPriceIndicator high = new HighPriceIndicator(series);
        LowPriceIndicator low = new LowPriceIndicator(series);
        VolumeIndicator volume = new VolumeIndicator(series);

        return List.of(serializationFixture(series,
                new KlingerVolumeOscillatorIndicator(high, low, close, volume, 5, 9, 1), stableIndexes(series)));
    }

}

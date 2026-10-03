/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott.swing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.junit.jupiter.api.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarBuilder;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.ConcurrentBarSeries;
import org.ta4j.core.ConstrainedSeriesSupport;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottSwing;
import org.ta4j.core.indicators.elliott.ElliottSwingIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.zigzag.ZigZagStateIndicator;
import org.ta4j.core.mocks.MockBarBuilderFactory;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

class AdaptiveZigZagSwingDetectorTest {

    @Test
    void adaptiveThresholdKeepsSwingsWhenAtrIsLarge() {
        BarSeries series = buildVolatileRangeSeries();
        int endIndex = series.getEndIndex();

        List<ElliottSwing> baseline = baselineZigZagSwings(series, endIndex);
        AdaptiveZigZagConfig config = new AdaptiveZigZagConfig(1, 1.0, 0.0, 20.0, 1);
        AdaptiveZigZagSwingDetector adaptiveDetector = new AdaptiveZigZagSwingDetector(config);
        SwingDetectorResult adaptive = adaptiveDetector.detect(series, endIndex, ElliottDegree.PRIMARY);

        assertThat(adaptive.swings()).isNotEmpty();
        assertThat(adaptive.swings().size()).isGreaterThanOrEqualTo(baseline.size());
    }

    @Test
    void reusesIndicatorStateAcrossRepeatedLiveDetection() {
        CountingBarSeries series = buildLiveSeries(300);
        AdaptiveZigZagConfig config = AdaptiveZigZagConfig.defaults();
        assertThat(config).isEqualTo(new AdaptiveZigZagConfig(14, 1.0, 0.0, 0.0, 3));
        AdaptiveZigZagSwingDetector detector = new AdaptiveZigZagSwingDetector(config);

        SwingDetectorResult initial = detector.detect(series, series.getEndIndex(), ElliottDegree.SUB_MINUETTE);
        series.resetBarReads();
        series.resetCopiedBars();

        SwingDetectorResult repeated = detector.detect(series, series.getEndIndex(), ElliottDegree.SUB_MINUETTE);

        assertThat(repeated).isEqualTo(initial);
        assertThat(series.barReads()).isLessThan(20);
        assertThat(series.copiedBars()).isLessThan(20);

        appendBar(series, 301);
        series.resetBarReads();
        series.resetCopiedBars();
        SwingDetectorResult advanced = detector.detect(series, series.getEndIndex(), ElliottDegree.SUB_MINUETTE);
        long advancedCopiedBars = series.copiedBars();
        SwingDetectorResult fresh = new AdaptiveZigZagSwingDetector(config).detect(series, series.getEndIndex(),
                ElliottDegree.SUB_MINUETTE);

        assertThat(advanced).isEqualTo(fresh);
        assertThat(advancedCopiedBars).isLessThan(20);

        SwingDetectorResult differentDegree = detector.detect(series, series.getEndIndex(), ElliottDegree.PRIMARY);
        assertThat(differentDegree.swings()).isNotEmpty().allMatch(swing -> swing.degree() == ElliottDegree.PRIMARY);
    }

    @Test
    void cachedDetectorPreservesCausalityForHistoricalQueries() {
        CountingBarSeries series = buildLiveSeries(80);
        AdaptiveZigZagConfig config = new AdaptiveZigZagConfig(1, 1.0, 0.25, 0.25, 1);
        AdaptiveZigZagSwingDetector cachedDetector = new AdaptiveZigZagSwingDetector(config);
        cachedDetector.detect(series, series.getEndIndex(), ElliottDegree.SUB_MINUETTE);

        for (int index = series.getBeginIndex(); index <= series.getEndIndex(); index++) {
            SwingDetectorResult freshResult = new AdaptiveZigZagSwingDetector(config).detect(series, index,
                    ElliottDegree.SUB_MINUETTE);
            SwingDetectorResult cachedResult = cachedDetector.detect(series, index, ElliottDegree.SUB_MINUETTE);

            assertThat(cachedResult).as("historical index %s", index).isEqualTo(freshResult);
        }
    }

    @Test
    void rollingWindowAdvancesCachedStateWithoutAFullRescan() {
        CountingBarSeries series = buildLiveSeries(80);
        series.setMaximumBarCount(80);
        AdaptiveZigZagConfig config = new AdaptiveZigZagConfig(1, 1.0, 0.25, 0.25, 1);
        AdaptiveZigZagSwingDetector reusedDetector = new AdaptiveZigZagSwingDetector(config);
        reusedDetector.detect(series, series.getEndIndex(), ElliottDegree.SUB_MINUETTE);

        appendBar(series, 101.0d);
        series.resetBarReads();
        series.resetCopiedBars();
        SwingDetectorResult advanced = reusedDetector.detect(series, series.getEndIndex(), ElliottDegree.SUB_MINUETTE);

        assertThat(advanced.swings()).isNotEmpty();
        assertThat(series.barReads()).isLessThan(series.getBarCount() / 2L);
        assertThat(series.copiedBars()).isLessThan(20);
    }

    @Test
    void rebuildsCachedStateWhenTheSeriesHistoryIsReplaced() {
        BarSeries series = buildVolatileRangeSeries();
        AdaptiveZigZagConfig config = new AdaptiveZigZagConfig(1, 1.0, 0.0, 20.0, 1);
        AdaptiveZigZagSwingDetector reusedDetector = new AdaptiveZigZagSwingDetector(config);
        SwingDetectorResult original = reusedDetector.detect(series, series.getEndIndex(), ElliottDegree.PRIMARY);
        assertThat(original.swings()).isNotEmpty();

        series.clear();
        Duration period = Duration.ofDays(1);
        Instant time = Instant.parse("2025-01-01T00:00:00Z");
        for (int index = 0; index < 6; index++) {
            double close = 100.0 + index;
            series.barBuilder()
                    .timePeriod(period)
                    .endTime(time.plus(period.multipliedBy(index)))
                    .openPrice(close)
                    .highPrice(close + 0.1)
                    .lowPrice(close - 0.1)
                    .closePrice(close)
                    .volume(1000.0)
                    .add();
        }

        SwingDetectorResult expected = new AdaptiveZigZagSwingDetector(config).detect(series, series.getEndIndex(),
                ElliottDegree.PRIMARY);
        SwingDetectorResult actual = reusedDetector.detect(series, series.getEndIndex(), ElliottDegree.PRIMARY);

        assertThat(expected.swings()).isEmpty();
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void rebuildsCachedStateWhenTheTerminalBarIsReplaced() {
        BarSeries series = buildVolatileRangeSeries();
        AdaptiveZigZagConfig config = new AdaptiveZigZagConfig(1, 1.0, 0.0, 20.0, 1);
        AdaptiveZigZagSwingDetector reusedDetector = new AdaptiveZigZagSwingDetector(config);
        SwingDetectorResult original = reusedDetector.detect(series, series.getEndIndex(), ElliottDegree.PRIMARY);
        Bar lastBar = series.getLastBar();
        Bar replacement = series.barBuilder()
                .timePeriod(lastBar.getTimePeriod())
                .endTime(lastBar.getEndTime())
                .openPrice(60.0)
                .highPrice(61.0)
                .lowPrice(59.0)
                .closePrice(60.0)
                .volume(1000.0)
                .build();
        series.addBar(replacement, true);

        SwingDetectorResult expected = new AdaptiveZigZagSwingDetector(config).detect(series, series.getEndIndex(),
                ElliottDegree.PRIMARY);
        SwingDetectorResult actual = reusedDetector.detect(series, series.getEndIndex(), ElliottDegree.PRIMARY);

        assertThat(expected).isNotEqualTo(original);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void rebuildsCachedStateWhenAnInteriorBarIsReplaced() {
        BaseBarSeries series = (BaseBarSeries) buildVolatileRangeSeries();
        AdaptiveZigZagConfig config = new AdaptiveZigZagConfig(1, 1.0, 0.0, 20.0, 1);
        AdaptiveZigZagSwingDetector reusedDetector = new AdaptiveZigZagSwingDetector(config);
        SwingDetectorResult original = reusedDetector.detect(series, series.getEndIndex(), ElliottDegree.PRIMARY);
        Bar replacedBar = series.getBar(2);
        Bar replacement = series.barBuilder()
                .timePeriod(replacedBar.getTimePeriod())
                .endTime(replacedBar.getEndTime())
                .openPrice(135.0)
                .highPrice(136.0)
                .lowPrice(134.0)
                .closePrice(135.0)
                .volume(1000.0)
                .build();
        series.replaceBar(2, replacement);

        SwingDetectorResult expected = new AdaptiveZigZagSwingDetector(config).detect(series, series.getEndIndex(),
                ElliottDegree.PRIMARY);
        SwingDetectorResult actual = reusedDetector.detect(series, series.getEndIndex(), ElliottDegree.PRIMARY);

        assertThat(expected).isNotEqualTo(original);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void rebuildsCachedStateWhenHistoryIsReplacedBeforeSeriesGrowth() {
        BaseBarSeries series = (BaseBarSeries) buildVolatileRangeSeries();
        AdaptiveZigZagConfig config = new AdaptiveZigZagConfig(1, 1.0, 0.0, 20.0, 1);
        AdaptiveZigZagSwingDetector reusedDetector = new AdaptiveZigZagSwingDetector(config);
        SwingDetectorResult original = reusedDetector.detect(series, series.getEndIndex(), ElliottDegree.PRIMARY);
        Bar replacedBar = series.getBar(2);
        Bar replacement = series.barBuilder()
                .timePeriod(replacedBar.getTimePeriod())
                .endTime(replacedBar.getEndTime())
                .openPrice(135.0)
                .highPrice(136.0)
                .lowPrice(134.0)
                .closePrice(135.0)
                .volume(1000.0)
                .build();
        series.replaceBar(2, replacement);
        appendBar(series, 151.0);

        SwingDetectorResult expected = new AdaptiveZigZagSwingDetector(config).detect(series, series.getEndIndex(),
                ElliottDegree.PRIMARY);
        SwingDetectorResult actual = reusedDetector.detect(series, series.getEndIndex(), ElliottDegree.PRIMARY);

        assertThat(expected).isNotEqualTo(original);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void rebuildsCachedStateWhenTheLiveBarIsUpdatedThroughTheSeries() {
        BaseBarSeries series = (BaseBarSeries) buildVolatileRangeSeries();
        AdaptiveZigZagConfig config = new AdaptiveZigZagConfig(1, 1.0, 0.0, 20.0, 1);
        AdaptiveZigZagSwingDetector reusedDetector = new AdaptiveZigZagSwingDetector(config);
        SwingDetectorResult original = reusedDetector.detect(series, series.getEndIndex(), ElliottDegree.PRIMARY);

        series.addPrice(60.0);

        SwingDetectorResult expected = new AdaptiveZigZagSwingDetector(config).detect(series, series.getEndIndex(),
                ElliottDegree.PRIMARY);
        SwingDetectorResult actual = reusedDetector.detect(series, series.getEndIndex(), ElliottDegree.PRIMARY);

        assertThat(expected).isNotEqualTo(original);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void rebuildsCachedStateForUntrackedSeriesReturningStableRawList() {
        UntrackedBarSeries series = new UntrackedBarSeries();
        Duration period = Duration.ofDays(1);
        Instant time = Instant.parse("2024-01-01T00:00:00Z");
        double[] closes = { 100.0, 130.0, 90.0, 140.0, 80.0, 150.0 };
        for (int index = 0; index < closes.length; index++) {
            double close = closes[index];
            series.barBuilder()
                    .timePeriod(period)
                    .endTime(time.plus(period.multipliedBy(index + 1L)))
                    .openPrice(close)
                    .highPrice(close + 1.0)
                    .lowPrice(close - 1.0)
                    .closePrice(close)
                    .volume(1000.0)
                    .add();
        }
        AdaptiveZigZagConfig config = new AdaptiveZigZagConfig(1, 1.0, 0.0, 20.0, 1);
        AdaptiveZigZagSwingDetector reusedDetector = new AdaptiveZigZagSwingDetector(config);
        SwingDetectorResult original = reusedDetector.detect(series, series.getEndIndex(), ElliottDegree.PRIMARY);

        Bar replacedBar = series.getBar(2);
        Bar replacement = series.barBuilder()
                .timePeriod(replacedBar.getTimePeriod())
                .endTime(replacedBar.getEndTime())
                .openPrice(135.0)
                .highPrice(136.0)
                .lowPrice(134.0)
                .closePrice(135.0)
                .volume(1000.0)
                .build();
        series.replaceBar(2, replacement);

        SwingDetectorResult expected = new AdaptiveZigZagSwingDetector(config).detect(series, series.getEndIndex(),
                ElliottDegree.PRIMARY);
        SwingDetectorResult actual = reusedDetector.detect(series, series.getEndIndex(), ElliottDegree.PRIMARY);

        assertThat(expected).isNotEqualTo(original);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void rebuildsAfterRetainedValueMutationWithoutRevisionOrIdentityChange() {
        assertRetainedMutationIsRebuilt(false);
    }

    @Test
    void rebuildsAfterRetainedValueMutationFollowedByAppend() {
        assertRetainedMutationIsRebuilt(true);
    }

    @Test
    void revalidatesRetainedValuesAfterUnlockedEvaluation() {
        UntrackedBarSeries series = new UntrackedBarSeries();
        for (Bar bar : buildVolatileRangeSeries().getBarData()) {
            series.addBar(bar);
        }
        AdaptiveZigZagConfig config = new AdaptiveZigZagConfig(1, 1.0, 0.0, 20.0, 1);
        AdaptiveZigZagSwingDetector warmed = new AdaptiveZigZagSwingDetector(config);
        warmed.detect(series, 0, ElliottDegree.PRIMARY);
        SwingDetectorResult before = new AdaptiveZigZagSwingDetector(config).detect(series, series.getEndIndex(),
                ElliottDegree.PRIMARY);
        Bar interior = series.getBar(2);
        series.mutateOnUnlockedRead(4, () -> interior.addPrice(series.numFactory().numOf(170)));

        SwingDetectorResult result = warmed.detect(series, series.getEndIndex(), ElliottDegree.PRIMARY);
        SwingDetectorResult fresh = new AdaptiveZigZagSwingDetector(config).detect(series, series.getEndIndex(),
                ElliottDegree.PRIMARY);

        assertThat(series.mutationTriggered).isTrue();
        assertThat(fresh).isNotEqualTo(before);
        assertThat(result).isEqualTo(fresh);
    }

    @Test
    void contendedDetectionDoesNotWaitOnOwnerWhileHoldingSeriesReadLock() throws Exception {
        BarSeries source = buildVolatileRangeSeries();
        CountDownLatch paused = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Thread> detectionThread = new AtomicReference<>();
        ReentrantReadWriteLock seriesLock = new ReentrantReadWriteLock();
        ConcurrentBarSeries series = ConstrainedSeriesSupport.seriesWithReadWriteLock(source, seriesLock, index -> {
            if (index == 3 && Thread.currentThread() == detectionThread.get() && paused.getCount() > 0) {
                paused.countDown();
                try {
                    assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                }
            }
        });
        series.setMaximumBarCount(Integer.MAX_VALUE);
        AdaptiveZigZagConfig config = new AdaptiveZigZagConfig(1, 1.0, 0.0, 20.0, 1);
        AdaptiveZigZagSwingDetector detector = new AdaptiveZigZagSwingDetector(config);
        detector.detect(series, 0, ElliottDegree.PRIMARY);
        SwingDetectorResult expected = new AdaptiveZigZagSwingDetector(config).detect(source, source.getEndIndex(),
                ElliottDegree.PRIMARY);
        ExecutorService threads = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "adaptive-detector-lock-order");
            thread.setDaemon(true);
            return thread;
        });
        try {
            Future<SwingDetectorResult> detection = threads.submit(() -> {
                detectionThread.set(Thread.currentThread());
                return detector.detect(series, source.getEndIndex(), ElliottDegree.PRIMARY);
            });
            assertThat(paused.await(5, TimeUnit.SECONDS)).isTrue();
            // A writer progresses while the detector evaluates its graph unlocked.
            threads.submit(() -> appendBar(series, 151.0)).get(2, TimeUnit.SECONDS);
            // A caller holding the series lock must not wait on the busy detector.
            Future<SwingDetectorResult> nested = threads.submit(() -> series
                    .withReadLock(() -> detector.detect(series, source.getEndIndex(), ElliottDegree.PRIMARY)));
            assertThat(nested.get(2, TimeUnit.SECONDS)).isEqualTo(expected);
            release.countDown();
            assertThat(detection.get(5, TimeUnit.SECONDS)).isEqualTo(expected);
            assertThat(seriesLock.getReadLockCount()).isZero();
        } finally {
            release.countDown();
            threads.shutdownNow();
        }
    }

    private void assertRetainedMutationIsRebuilt(final boolean append) {
        UntrackedBarSeries series = new UntrackedBarSeries();
        for (Bar bar : buildVolatileRangeSeries().getBarData()) {
            series.addBar(bar);
        }
        AdaptiveZigZagConfig config = new AdaptiveZigZagConfig(1, 1.0, 0.0, 20.0, 1);
        AdaptiveZigZagSwingDetector warmed = new AdaptiveZigZagSwingDetector(config);
        SwingDetectorResult before = warmed.detect(series, series.getEndIndex(), ElliottDegree.PRIMARY);
        Bar interior = series.getBar(2);
        Bar terminal = series.getLastBar();
        int begin = series.getBeginIndex();
        int end = series.getEndIndex();

        interior.addPrice(series.numFactory().numOf(170));

        assertThat(series.getBar(2)).isSameAs(interior);
        assertThat(series.getLastBar()).isSameAs(terminal);
        assertThat(series.getBeginIndex()).isEqualTo(begin);
        assertThat(series.getEndIndex()).isEqualTo(end);
        assertThat(series.getBarHistoryRevision()).isEqualTo(-1L);
        if (append) {
            appendBar(series, 151.0);
        }
        SwingDetectorResult fresh = new AdaptiveZigZagSwingDetector(config).detect(series, series.getEndIndex(),
                ElliottDegree.PRIMARY);
        assertThat(fresh).isNotEqualTo(before);
        assertThat(warmed.detect(series, series.getEndIndex(), ElliottDegree.PRIMARY)).isEqualTo(fresh);
    }

    private List<ElliottSwing> baselineZigZagSwings(BarSeries series, int endIndex) {
        ClosePriceIndicator price = new ClosePriceIndicator(series);
        ATRIndicator atr = new ATRIndicator(series, 1);
        ZigZagStateIndicator state = new ZigZagStateIndicator(price, atr);
        ElliottSwingIndicator indicator = ElliottSwingIndicator.zigZag(state, price, ElliottDegree.PRIMARY);
        return indicator.getValue(endIndex);
    }

    private BarSeries buildVolatileRangeSeries() {
        BarSeries series = new MockBarSeriesBuilder().withName("AdaptiveZigZagTest").build();
        Duration period = Duration.ofDays(1);
        Instant time = Instant.parse("2024-01-01T00:00:00Z");
        double[] closes = { 100.0, 130.0, 90.0, 140.0, 80.0, 150.0 };
        for (int i = 0; i < closes.length; i++) {
            series.barBuilder()
                    .timePeriod(period)
                    .endTime(time.plus(period.multipliedBy(i)))
                    .openPrice(closes[i])
                    .highPrice(closes[i] + 1.0)
                    .lowPrice(closes[i] - 1.0)
                    .closePrice(closes[i])
                    .volume(1000.0)
                    .add();
        }
        return series;
    }

    private CountingBarSeries buildLiveSeries(final int barCount) {
        CountingBarSeries series = new CountingBarSeries();
        Duration period = Duration.ofMinutes(1);
        Instant time = Instant.parse("2024-01-01T00:00:00Z");
        for (int index = 0; index < barCount; index++) {
            double close = 100.0 + Math.sin(index * 0.2);
            series.barBuilder()
                    .timePeriod(period)
                    .endTime(time.plus(period.multipliedBy(index + 1L)))
                    .openPrice(close)
                    .highPrice(close + 0.2)
                    .lowPrice(close - 0.2)
                    .closePrice(close)
                    .volume(1000.0)
                    .add();
        }
        return series;
    }

    private static void appendBar(final BarSeries series, final double close) {
        Bar lastBar = series.getLastBar();
        series.barBuilder()
                .timePeriod(lastBar.getTimePeriod())
                .endTime(lastBar.getEndTime().plus(lastBar.getTimePeriod()))
                .openPrice(close)
                .highPrice(close + 0.2)
                .lowPrice(close - 0.2)
                .closePrice(close)
                .volume(1000.0)
                .add();
    }

    private static final class CountingBarSeries extends BaseBarSeries {

        private long barReads;
        private long copiedBars;

        private CountingBarSeries() {
            super("AdaptiveZigZagLiveTest", List.of());
        }

        @Override
        public BarBuilder barBuilder() {
            return new MockBarBuilderFactory().createBarBuilder(this);
        }

        @Override
        public Bar getBar(final int index) {
            barReads++;
            return super.getBar(index);
        }

        @Override
        public List<Bar> getBarData() {
            copiedBars += getBarCount();
            return super.getBarData();
        }

        private long barReads() {
            return barReads;
        }

        private void resetBarReads() {
            barReads = 0;
        }

        private long copiedBars() {
            return copiedBars;
        }

        private void resetCopiedBars() {
            copiedBars = 0;
        }
    }

    private static final class UntrackedBarSeries extends BaseBarSeries {

        private int readScopeDepth;
        private int triggerIndex = -1;
        private Runnable mutation;
        private boolean mutationTriggered;

        private void mutateOnUnlockedRead(final int index, final Runnable action) {
            triggerIndex = index;
            mutation = action;
        }

        @Override
        public Bar getBar(final int index) {
            if (index == triggerIndex && mutation != null && readScopeDepth == 0) {
                Runnable action = mutation;
                mutation = null;
                mutationTriggered = true;
                action.run();
            }
            return super.getBar(index);
        }

        @Override
        public void withReadLock(final Runnable action) {
            readScopeDepth++;
            try {
                action.run();
            } finally {
                readScopeDepth--;
            }
        }

        @Override
        public <T> T withReadLock(final Supplier<T> action) {
            readScopeDepth++;
            try {
                return action.get();
            } finally {
                readScopeDepth--;
            }
        }

        private final List<Bar> exposedBars = new ArrayList<>();

        private UntrackedBarSeries() {
            super("UntrackedAdaptiveZigZagTest", List.of());
        }

        @Override
        public BarBuilder barBuilder() {
            return new MockBarBuilderFactory().createBarBuilder(this);
        }

        @Override
        public synchronized long getBarHistoryRevision() {
            return -1L;
        }

        @Override
        public synchronized BarSeriesChangeSnapshot getBarSeriesChangeSnapshot(final long sinceRevision) {
            return new BarSeriesChangeSnapshot(-1L, -1, getRemovedBarsCount() - 1, getMaximumBarCount(), getEndIndex());
        }

        @Override
        public List<Bar> getBarData() {
            return exposedBars;
        }

        @Override
        public void addBar(final Bar bar, final boolean replace) {
            super.addBar(bar, replace);
            syncExposedBars();
        }

        @Override
        public void replaceBar(final int index, final Bar bar) {
            super.replaceBar(index, bar);
            syncExposedBars();
        }

        private void syncExposedBars() {
            exposedBars.clear();
            exposedBars.addAll(super.getBarData());
        }
    }

}

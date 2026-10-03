/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ta4j.core.num.NaN.NaN;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BarSeries.BarSeriesChangeSnapshot;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class AbstractRecentSwingIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public AbstractRecentSwingIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void shouldRetainLegacyTwoArgumentConstructor() throws NoSuchMethodException {
        assertThat(AbstractRecentSwingIndicator.class.getDeclaredConstructor(Indicator.class, int.class)).isNotNull();
    }

    @Test
    public void shouldInvalidateCacheAfterReleasingSwingTrackerOnHistoryReset() {
        final BarSeries series = seriesFromCloses(1, 2, 3, 4, 5);
        final int[] latestSwingIndexes = { -1, -1, 2, 2, 2 };
        final LockCheckingSwingIndicator indicator = new LockCheckingSwingIndicator(new ClosePriceIndicator(series),
                latestSwingIndexes);
        indicator.getSwingPointIndexesUpTo(series.getEndIndex());

        series.setMaximumBarCount(3);

        indicator.getSwingPointIndexesUpTo(series.getEndIndex());

        assertThat(indicator.wasInvalidatedOutsideTrackerMonitor()).isTrue();
    }

    @Test
    public void shouldExposeSwingIndexesAndValuesMonotonically() {
        final var series = seriesFromCloses(1, 2, 3, 4, 5, 6, 7);
        final int[] latestSwingIndexes = { -1, -1, 2, 2, 2, 5, 5 };
        final var indicator = new FixedSwingIndicator(new ClosePriceIndicator(series), latestSwingIndexes);

        assertThat(indicator.getSwingPointIndexesUpTo(2)).containsExactly(2);
        assertThat(indicator.getSwingPointIndexesUpTo(5)).containsExactly(2, 5);
        // Only swing points at or before the requested index are returned
        assertThat(indicator.getSwingPointIndexesUpTo(3)).containsExactly(2);

        assertThat(indicator.getLatestSwingIndex(4)).isEqualTo(2);
        assertThat(indicator.getLatestSwingIndex(6)).isEqualTo(5);
        assertThat(indicator.getValue(6)).isEqualByComparingTo(numOf(6));
    }

    @Test
    public void shouldPurgeSwingIndexesThatFallBeforeSeriesBegin() {
        final var series = seriesFromCloses(1, 2, 3, 4, 5, 6, 7);
        final int[] latestSwingIndexes = { -1, -1, 2, 2, 2, 5, 5 };
        final var indicator = new FixedSwingIndicator(new ClosePriceIndicator(series), latestSwingIndexes);

        assertThat(indicator.getSwingPointIndexesUpTo(series.getEndIndex())).containsExactly(2, 5);

        series.setMaximumBarCount(2); // beginIndex will advance to drop the swing at index 2
        final int endIndexAfterPurge = series.getEndIndex();
        assertThat(indicator.getSwingPointIndexesUpTo(endIndexAfterPurge)).containsExactly(5);
        assertThat(indicator.getLatestSwingIndex(endIndexAfterPurge)).isEqualTo(5);
        assertThat(indicator.getValue(endIndexAfterPurge)).isNotEqualTo(NaN);
    }

    @Test
    public void shouldFilterSwingPointsByIndexParameter() {
        // Test the specific regression: getSwingPointIndexesUpTo should filter by index
        final var series = seriesFromCloses(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        // Swing points at indices: 2, 5, 8
        final int[] latestSwingIndexes = { -1, -1, 2, 2, 2, 5, 5, 5, 8, 8 };
        final var indicator = new FixedSwingIndicator(new ClosePriceIndicator(series), latestSwingIndexes);

        // First call with larger index - discovers all swings
        assertThat(indicator.getSwingPointIndexesUpTo(9)).containsExactly(2, 5, 8);

        // Call with smaller index - should only return swings up to that index
        assertThat(indicator.getSwingPointIndexesUpTo(4)).containsExactly(2);
        assertThat(indicator.getSwingPointIndexesUpTo(6)).containsExactly(2, 5);
        assertThat(indicator.getSwingPointIndexesUpTo(7)).containsExactly(2, 5);
        assertThat(indicator.getSwingPointIndexesUpTo(8)).containsExactly(2, 5, 8);

        // Verify filtering works regardless of call order
        assertThat(indicator.getSwingPointIndexesUpTo(3)).containsExactly(2);
        assertThat(indicator.getSwingPointIndexesUpTo(1)).isEmpty();
    }

    @Test
    public void shouldFilterSwingPointsAtBoundaries() {
        final var series = seriesFromCloses(1, 2, 3, 4, 5, 6, 7);
        final int[] latestSwingIndexes = { -1, -1, 2, 2, 2, 5, 5 };
        final var indicator = new FixedSwingIndicator(new ClosePriceIndicator(series), latestSwingIndexes);

        // Test at exact swing point boundaries
        assertThat(indicator.getSwingPointIndexesUpTo(2)).containsExactly(2);
        assertThat(indicator.getSwingPointIndexesUpTo(3)).containsExactly(2);
        assertThat(indicator.getSwingPointIndexesUpTo(4)).containsExactly(2);
        assertThat(indicator.getSwingPointIndexesUpTo(5)).containsExactly(2, 5);
        assertThat(indicator.getSwingPointIndexesUpTo(6)).containsExactly(2, 5);
    }

    @Test
    public void shouldReturnEmptyListWhenNoSwingPointsUpToIndex() {
        final var series = seriesFromCloses(1, 2, 3, 4, 5, 6, 7);
        // First swing point is at index 2
        final int[] latestSwingIndexes = { -1, -1, 2, 2, 2, 5, 5 };
        final var indicator = new FixedSwingIndicator(new ClosePriceIndicator(series), latestSwingIndexes);

        // Before any swing points are discovered
        assertThat(indicator.getSwingPointIndexesUpTo(0)).isEmpty();
        assertThat(indicator.getSwingPointIndexesUpTo(1)).isEmpty();

        // After discovering swings, requesting before first swing should still return
        // empty
        indicator.getSwingPointIndexesUpTo(6); // Discover swings
        assertThat(indicator.getSwingPointIndexesUpTo(1)).isEmpty();
    }

    @Test
    public void shouldFilterCorrectlyWithMultipleSwingPoints() {
        final var series = seriesFromCloses(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
        // Swing points at indices: 1, 4, 7, 10
        final int[] latestSwingIndexes = { -1, 1, 1, 1, 4, 4, 4, 7, 7, 7, 10, 10 };
        final var indicator = new FixedSwingIndicator(new ClosePriceIndicator(series), latestSwingIndexes);

        // Discover all swings first
        assertThat(indicator.getSwingPointIndexesUpTo(11)).containsExactly(1, 4, 7, 10);

        // Test various filtering scenarios
        assertThat(indicator.getSwingPointIndexesUpTo(0)).isEmpty();
        assertThat(indicator.getSwingPointIndexesUpTo(1)).containsExactly(1);
        assertThat(indicator.getSwingPointIndexesUpTo(2)).containsExactly(1);
        assertThat(indicator.getSwingPointIndexesUpTo(3)).containsExactly(1);
        assertThat(indicator.getSwingPointIndexesUpTo(4)).containsExactly(1, 4);
        assertThat(indicator.getSwingPointIndexesUpTo(5)).containsExactly(1, 4);
        assertThat(indicator.getSwingPointIndexesUpTo(6)).containsExactly(1, 4);
        assertThat(indicator.getSwingPointIndexesUpTo(7)).containsExactly(1, 4, 7);
        assertThat(indicator.getSwingPointIndexesUpTo(8)).containsExactly(1, 4, 7);
        assertThat(indicator.getSwingPointIndexesUpTo(9)).containsExactly(1, 4, 7);
        assertThat(indicator.getSwingPointIndexesUpTo(10)).containsExactly(1, 4, 7, 10);
    }

    @Test
    public void shouldMaintainFilteringConsistencyRegardlessOfCallOrder() {
        final var series = seriesFromCloses(1, 2, 3, 4, 5, 6, 7, 8, 9);
        final int[] latestSwingIndexes = { -1, -1, 2, 2, 2, 5, 5, 5, 8 };
        final var indicator = new FixedSwingIndicator(new ClosePriceIndicator(series), latestSwingIndexes);

        // Call in ascending order
        assertThat(indicator.getSwingPointIndexesUpTo(2)).containsExactly(2);
        assertThat(indicator.getSwingPointIndexesUpTo(5)).containsExactly(2, 5);
        assertThat(indicator.getSwingPointIndexesUpTo(8)).containsExactly(2, 5, 8);

        // Create a new indicator and call in descending order
        final var indicator2 = new FixedSwingIndicator(new ClosePriceIndicator(series), latestSwingIndexes);
        assertThat(indicator2.getSwingPointIndexesUpTo(8)).containsExactly(2, 5, 8);
        assertThat(indicator2.getSwingPointIndexesUpTo(5)).containsExactly(2, 5);
        assertThat(indicator2.getSwingPointIndexesUpTo(2)).containsExactly(2);

        // Create a new indicator and call in mixed order
        final var indicator3 = new FixedSwingIndicator(new ClosePriceIndicator(series), latestSwingIndexes);
        assertThat(indicator3.getSwingPointIndexesUpTo(5)).containsExactly(2, 5);
        assertThat(indicator3.getSwingPointIndexesUpTo(2)).containsExactly(2);
        assertThat(indicator3.getSwingPointIndexesUpTo(8)).containsExactly(2, 5, 8);
        assertThat(indicator3.getSwingPointIndexesUpTo(4)).containsExactly(2);
    }

    @Test
    public void shouldFilterByConfirmationIndexRatherThanPivotIndex() {
        final BarSeries series = seriesFromCloses(10, 12, 15, 13, 11);
        final int[] latestSwingIndexes = { -1, -1, -1, -1, 2 };
        final FixedSwingIndicator indicator = new FixedSwingIndicator(new ClosePriceIndicator(series),
                latestSwingIndexes);

        assertThat(indicator.getSwingPointIndexesUpTo(4)).containsExactly(2);
        assertThat(indicator.getLatestSwingConfirmationIndex(4)).isEqualTo(4);

        assertThat(indicator.getSwingPointIndexesUpTo(2)).isEmpty();
        assertThat(indicator.getLatestSwingIndex(2)).isEqualTo(-1);
        assertThat(indicator.getLatestSwingConfirmationIndex(2)).isEqualTo(-1);
    }

    @Test
    public void shouldRewindConfirmedSwingsWhenTheTerminalBarIsReplaced() {
        final BarSeries series = seriesFromCloses(10, 12, 15, 13, 11);
        final RecentFractalSwingHighIndicator indicator = new RecentFractalSwingHighIndicator(series, 2);

        assertThat(indicator.getSwingPointIndexesUpTo(series.getEndIndex())).containsExactly(2);

        final Bar lastBar = series.getLastBar();
        final Bar replacement = series.barBuilder()
                .timePeriod(lastBar.getTimePeriod())
                .endTime(lastBar.getEndTime())
                .openPrice(16)
                .highPrice(16)
                .lowPrice(16)
                .closePrice(16)
                .build();
        series.addBar(replacement, true);

        assertThat(indicator.getSwingPointIndexesUpTo(series.getEndIndex())).isEmpty();
        assertThat(indicator.getLatestSwingIndex(series.getEndIndex())).isEqualTo(-1);
    }

    @Test
    public void shouldReturnNaNWhenSwingPriceIsNaN() {
        final var series = seriesFromCloses(1, 2, 3, 4, 5);
        final int[] latestSwingIndexes = { -1, 1, 1, 3, 3 };
        final var nanPriceIndicator = new NaNPriceIndicator(series, new ClosePriceIndicator(series), 1);
        final var indicator = new FixedSwingIndicator(nanPriceIndicator, latestSwingIndexes);

        assertThat(indicator.getValue(2)).isEqualByComparingTo(NaN);
        assertThat(indicator.getSwingPointIndexesUpTo(4)).containsExactly(1, 3);
    }

    @Test
    public void shouldRetainSwingsWhenDetectorReportsNegativeWithoutPurge() {
        final var series = seriesFromCloses(1, 2, 3, 4, 5, 6);
        // Swings at 2 then 4; detector returns -1 at index 4 but should not clear
        final int[] latestSwingIndexes = { -1, -1, 2, 2, -1, 4 };
        final var indicator = new FixedSwingIndicator(new ClosePriceIndicator(series), latestSwingIndexes);

        assertThat(indicator.getSwingPointIndexesUpTo(3)).containsExactly(2);
        assertThat(indicator.getSwingPointIndexesUpTo(4)).containsExactly(2);
        assertThat(indicator.getSwingPointIndexesUpTo(5)).containsExactly(2, 4);
    }

    @Test
    public void shouldKeepFallbackHistorySnapshotCurrentDuringBackwardScans() {
        final UntrackedBarSeries series = new UntrackedBarSeries();
        for (int close = 1; close <= 5; close++) {
            addTimedBar(series, close);
        }
        final int[] latestSwingIndexes = { -1, -1, 2, 2, 2, 2 };
        final CountingSwingIndicator indicator = new CountingSwingIndicator(new ClosePriceIndicator(series),
                latestSwingIndexes);

        assertThat(indicator.getSwingPointIndexesUpTo(4)).containsExactly(2);
        final int detectionCountAfterInitialScan = indicator.detectionCount();

        addTimedBar(series, 6);
        assertThat(indicator.getSwingPointIndexesUpTo(3)).containsExactly(2);
        assertThat(indicator.detectionCount()).isEqualTo(detectionCountAfterInitialScan);

        final Bar lastBar = series.getLastBar();
        final Bar replacement = series.barBuilder()
                .timePeriod(lastBar.getTimePeriod())
                .endTime(lastBar.getEndTime())
                .openPrice(7)
                .closePrice(7)
                .highPrice(7)
                .lowPrice(7)
                .build();
        series.addBar(replacement, true);

        assertThat(indicator.getSwingPointIndexesUpTo(3)).containsExactly(2);
        assertThat(indicator.detectionCount()).isGreaterThan(detectionCountAfterInitialScan);
    }

    @Test
    public void shouldRevalidateRetainedValuesBeforeRepeatedSwingQueries() {
        final UntrackedBarSeries series = new UntrackedBarSeries();
        for (int close : new int[] { 5, 10, 5, 1, 1 }) {
            addTimedBar(series, close);
        }
        final RecentFractalSwingHighIndicator indicator = new RecentFractalSwingHighIndicator(series, 1);
        final Bar mutated = series.getBar(2);
        final int begin = series.getBeginIndex();
        final int end = series.getEndIndex();
        assertThat(indicator.getSwingPointIndexesUpTo(end)).containsExactly(1);
        assertThat(indicator.getValue(3)).isEqualByComparingTo(series.numFactory().numOf(10));
        mutated.addPrice(series.numFactory().numOf(20));
        assertThat(series.getBar(2)).isSameAs(mutated);
        assertThat(series.getBeginIndex()).isEqualTo(begin);
        assertThat(series.getEndIndex()).isEqualTo(end);
        final RecentFractalSwingHighIndicator fresh = new RecentFractalSwingHighIndicator(series, 1);
        assertThat(fresh.getSwingPointIndexesUpTo(end)).containsExactly(2);
        // Historical outer-cache hits must validate even before querying the tracker.
        assertThat(indicator.getValue(3)).isEqualByComparingTo(fresh.getValue(3));
        assertThat(indicator.getSwingPointIndexesUpTo(end)).isEqualTo(fresh.getSwingPointIndexesUpTo(end));
        assertThat(indicator.getLatestSwingIndex(end)).isEqualTo(fresh.getLatestSwingIndex(end));
        assertThat(indicator.getLatestSwingConfirmationIndex(end))
                .isEqualTo(fresh.getLatestSwingConfirmationIndex(end));
    }

    @Test
    public void shouldRevalidateRetainedValuesBeforeAppendScanning() {
        final UntrackedBarSeries series = new UntrackedBarSeries();
        for (int close : new int[] { 5, 10, 5, 1, 1 }) {
            addTimedBar(series, close);
        }
        final RecentFractalSwingHighIndicator indicator = new RecentFractalSwingHighIndicator(series, 1);
        assertThat(indicator.getSwingPointIndexes()).containsExactly(1);
        series.getBar(2).addPrice(series.numFactory().numOf(20));
        addTimedBar(series, 1);
        final RecentFractalSwingHighIndicator fresh = new RecentFractalSwingHighIndicator(series, 1);
        assertThat(fresh.getSwingPointIndexes()).containsExactly(2);
        assertThat(indicator.getSwingPointIndexes()).isEqualTo(fresh.getSwingPointIndexes());
        assertThat(indicator.getValue(series.getEndIndex())).isEqualByComparingTo(fresh.getValue(series.getEndIndex()));
    }

    @Test
    public void shouldRetryWhenRetainedValuesChangeDuringUnlockedScan() {
        final ScopedUntrackedBarSeries series = new ScopedUntrackedBarSeries();
        for (int close : new int[] { 5, 10, 5, 1, 1 }) {
            addTimedBar(series, close);
        }
        final MutatingSwingIndicator warmed = new MutatingSwingIndicator(series, true);
        final MutatingSwingIndicator fresh = new MutatingSwingIndicator(series, false);
        assertThat(warmed.getSwingPointIndexes()).isEmpty();
        assertThat(warmed.getSwingPointIndexes()).isEqualTo(fresh.getSwingPointIndexes());
        assertThat(warmed.invalidatedOutsideMonitor).isTrue();
        assertThat(warmed.detections).isGreaterThan(series.getBarCount());
    }

    @Test
    public void shouldClearSourcesBeforeAnotherReaderReplaysAResetTracker() throws Exception {
        final UntrackedBarSeries series = new UntrackedBarSeries();
        for (int close : new int[] { 5, 10, 5, 1, 1 }) {
            addTimedBar(series, close);
        }
        final BlockingInvalidationSwingIndicator indicator = new BlockingInvalidationSwingIndicator(series);
        assertThat(indicator.getSwingPointIndexes()).containsExactly(1);
        series.getBar(2).addPrice(series.numFactory().numOf(20));
        indicator.blockNextInvalidation.set(true);
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            final Future<List<Integer>> first = executor.submit(indicator::getSwingPointIndexes);
            assertThat(indicator.invalidating.await(5, TimeUnit.SECONDS)).isTrue();
            final Future<List<Integer>> second = executor.submit(indicator::getSwingPointIndexes);
            assertThat(second.get(5, TimeUnit.SECONDS)).containsExactly(2);
            indicator.release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).containsExactly(2);
        } finally {
            indicator.release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    public void shouldKeepRevisionAwareValidationIndependentOfRetainedHistorySize() {
        assertThat(repeatedQueryBarReads(40)).isEqualTo(repeatedQueryBarReads(5)).isLessThan(20);
    }

    private int repeatedQueryBarReads(int size) {
        final CountingBarSeries series = new CountingBarSeries();
        for (int close = 1; close <= size; close++) {
            addTimedBar(series, close);
        }
        final RecentFractalSwingHighIndicator indicator = new RecentFractalSwingHighIndicator(series, 1);
        final int index = series.getEndIndex() - 1;
        indicator.getValue(index);
        series.reads = 0;
        for (int query = 0; query < 3; query++) {
            indicator.getValue(index);
        }
        return series.reads;
    }

    private BarSeries seriesFromCloses(double... closes) {
        final var seriesBuilder = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (double close : closes) {
            seriesBuilder.barBuilder().openPrice(close).closePrice(close).highPrice(close).lowPrice(close).add();
        }
        return seriesBuilder;
    }

    private void addTimedBar(final BarSeries series, final Number close) {
        final int nextIndex = series.getEndIndex() + 1;
        series.barBuilder()
                .timePeriod(Duration.ofMinutes(1))
                .endTime(Instant.EPOCH.plus(Duration.ofMinutes(nextIndex + 1)))
                .openPrice(close)
                .closePrice(close)
                .highPrice(close)
                .lowPrice(close)
                .add();
    }

    private static class FixedSwingIndicator extends AbstractRecentSwingIndicator {

        private final List<Integer> latestSwingIndexes;

        private FixedSwingIndicator(Indicator<Num> priceIndicator, int[] latestSwingIndexes) {
            super(priceIndicator, 0);
            this.latestSwingIndexes = new ArrayList<>(Arrays.stream(latestSwingIndexes).boxed().toList());
        }

        @Override
        protected int detectLatestSwingIndex(int index) {
            if (index < 0) {
                return -1;
            }
            if (index >= latestSwingIndexes.size()) {
                return latestSwingIndexes.get(latestSwingIndexes.size() - 1);
            }
            return latestSwingIndexes.get(index);
        }
    }

    private static final class LockCheckingSwingIndicator extends FixedSwingIndicator {

        private boolean invalidatedOutsideTrackerMonitor;

        private LockCheckingSwingIndicator(Indicator<Num> priceIndicator, int[] latestSwingIndexes) {
            super(priceIndicator, latestSwingIndexes);
        }

        @Override
        protected void invalidateCache() {
            invalidatedOutsideTrackerMonitor = !holdsSwingPointTrackerMonitor();
            super.invalidateCache();
        }

        private boolean wasInvalidatedOutsideTrackerMonitor() {
            return invalidatedOutsideTrackerMonitor;
        }
    }

    private static final class CountingSwingIndicator extends FixedSwingIndicator {

        private int detectionCount;

        private CountingSwingIndicator(final Indicator<Num> priceIndicator, final int[] latestSwingIndexes) {
            super(priceIndicator, latestSwingIndexes);
        }

        @Override
        protected int detectLatestSwingIndex(final int index) {
            detectionCount++;
            return super.detectLatestSwingIndex(index);
        }

        private int detectionCount() {
            return detectionCount;
        }
    }

    private static class UntrackedBarSeries extends BaseBarSeries {

        private UntrackedBarSeries() {
            super("untracked", new ArrayList<>());
        }

        @Override
        public long getBarHistoryRevision() {
            return -1L;
        }

        @Override
        public BarSeriesChangeSnapshot getBarSeriesChangeSnapshot(long sinceRevision) {
            return new BarSeriesChangeSnapshot(-1L, -1, getRemovedBarsCount() - 1, getMaximumBarCount(), getEndIndex());
        }
    }

    private static final class ScopedUntrackedBarSeries extends UntrackedBarSeries {
        private boolean inReadScope;

        @Override
        public <T> T withReadLock(Supplier<T> action) {
            final boolean previous = inReadScope;
            inReadScope = true;
            try {
                return action.get();
            } finally {
                inReadScope = previous;
            }
        }
    }

    private static final class MutatingSwingIndicator extends AbstractRecentSwingIndicator {
        private final ScopedUntrackedBarSeries series;
        private boolean mutate;
        private boolean invalidatedOutsideMonitor;
        private int detections;

        private MutatingSwingIndicator(ScopedUntrackedBarSeries series, boolean mutate) {
            super(new ClosePriceIndicator(series), 0);
            this.series = series;
            this.mutate = mutate;
        }

        @Override
        protected int detectLatestSwingIndex(int index) {
            assertThat(series.inReadScope).isFalse();
            detections++;
            final int result = index >= 2 && series.getBar(1).getHighPrice().isLessThan(series.numFactory().numOf(15))
                    ? 1
                    : -1;
            if (index == series.getEndIndex() && mutate) {
                mutate = false;
                series.getBar(1).addPrice(series.numFactory().numOf(20));
            }
            return result;
        }

        @Override
        protected void invalidateCache() {
            invalidatedOutsideMonitor = !holdsSwingPointTrackerMonitor();
            super.invalidateCache();
        }
    }

    private static final class CachedHighPriceIndicator extends CachedIndicator<Num> {
        private final HighPriceIndicator source;

        private CachedHighPriceIndicator(BarSeries series) {
            super(new HighPriceIndicator(series));
            source = new HighPriceIndicator(series);
        }

        @Override
        protected Num calculate(int index) {
            return source.getValue(index);
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }
    }

    private static final class BlockingInvalidationSwingIndicator extends RecentFractalSwingHighIndicator {
        private final AtomicBoolean blockNextInvalidation = new AtomicBoolean();
        private final CountDownLatch invalidating = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        private BlockingInvalidationSwingIndicator(BarSeries series) {
            super(new CachedHighPriceIndicator(series), 1, 1, 0);
        }

        @Override
        protected void invalidateCache() {
            assertThat(holdsSwingPointTrackerMonitor()).isFalse();
            if (blockNextInvalidation.compareAndSet(true, false)) {
                invalidating.countDown();
                try {
                    assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                }
            }
            super.invalidateCache();
        }
    }

    private static final class CountingBarSeries extends BaseBarSeries {
        private int reads;

        private CountingBarSeries() {
            super("tracked", new ArrayList<>());
        }

        @Override
        public Bar getBar(int index) {
            reads++;
            return super.getBar(index);
        }
    }

    private static final class NaNPriceIndicator extends CachedIndicator<Num> {

        private final Indicator<Num> delegate;
        private final int nanIndex;

        private NaNPriceIndicator(BarSeries series, Indicator<Num> delegate, int nanIndex) {
            super(series);
            this.delegate = delegate;
            this.nanIndex = nanIndex;
        }

        @Override
        protected Num calculate(int index) {
            if (index == nanIndex) {
                return NaN;
            }
            return delegate.getValue(index);
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }
    }
}

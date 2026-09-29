/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.analysis.EquityCurveMode;
import org.ta4j.core.bars.TimeBarBuilderFactory;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.mocks.MockBarBuilderFactory;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Test support for series whose retention or logical bounds require
 * package-private constructors. Analysis tests share these factories without
 * exposing additional production constructors.
 */
public final class ConstrainedSeriesSupport {

    public record CriterionWindowFixture(String name, BarSeries series, TradingRecord tradingRecord, Position position,
            BarSeries equivalentSeries, TradingRecord equivalentRecord, Position equivalentPosition,
            TradingRecord markedEquivalentRecord, Position markedEquivalentPosition) {

        public TradingRecord equivalentRecord(EquityCurveMode mode) {
            return mode == EquityCurveMode.MARK_TO_MARKET ? markedEquivalentRecord : equivalentRecord;
        }

        public Position equivalentPosition(EquityCurveMode mode) {
            return mode == EquityCurveMode.MARK_TO_MARKET ? markedEquivalentPosition : equivalentPosition;
        }
    }

    private ConstrainedSeriesSupport() {
    }

    /** Builds a retained series backed by the supplied read/write lock. */
    public static ConcurrentBarSeries seriesWithReadWriteLock(BarSeries source, ReadWriteLock readWriteLock) {
        ConcurrentBarSeries series = new ConcurrentBarSeries("observed-read-lease",
                new ArrayList<>(source.getBarData()), source.getBeginIndex(), source.getEndIndex(), false,
                source.numFactory(), new MockBarBuilderFactory(), readWriteLock);
        series.setMaximumBarCount(source.getBarCount());
        return series;
    }

    /**
     * Builds a retained series backed by the supplied read/write lock that runs
     * {@code beforeBarRead} with the requested index before each
     * {@link BarSeries#getBar(int)} call acquires the lock, so a test can pause a
     * reader in the middle of an indicator evaluation.
     */
    public static ConcurrentBarSeries seriesWithReadWriteLock(BarSeries source, ReadWriteLock readWriteLock,
            IntConsumer beforeBarRead) {
        ConcurrentBarSeries series = new ConcurrentBarSeries("observed-bar-reads", new ArrayList<>(source.getBarData()),
                source.getBeginIndex(), source.getEndIndex(), false, source.numFactory(), new MockBarBuilderFactory(),
                readWriteLock) {
            @Override
            public Bar getBar(int i) {
                beforeBarRead.accept(i);
                return super.getBar(i);
            }
        };
        series.setMaximumBarCount(source.getBarCount());
        return series;
    }

    /**
     * Builds a constrained series holding every {@code closes} bar in raw storage
     * while exposing only the logical window {@code [0, endIndex]}. The trailing
     * bars after {@code endIndex} stay addressable so analyses can price exits that
     * landed beyond the window.
     *
     * @param name       the series name
     * @param numFactory the number factory
     * @param endIndex   the last index of the exposed logical window
     * @param closes     the raw close prices, at least {@code endIndex + 1} entries
     * @return the constrained series
     */
    public static BarSeries trailingConstrainedSeries(String name, NumFactory numFactory, int endIndex,
            double... closes) {
        BarSeries source = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(closes).build();
        return new BaseBarSeries(name, List.copyOf(source.getBarData()), 0, endIndex, true, numFactory,
                new TimeBarBuilderFactory());
    }

    /**
     * Builds a series whose raw bars remain retained while its logical window is
     * empty. Raw index zero is still addressable for direct trade exits.
     *
     * @param name       the series name
     * @param numFactory the number factory
     * @param closes     the retained raw close prices
     * @return a constrained series with an empty logical window
     */
    public static BarSeries emptyLogicalSeries(String name, NumFactory numFactory, double... closes) {
        BarSeries source = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(closes).build();
        return new BaseBarSeries(name, List.copyOf(source.getBarData()), 0, -1, true, numFactory,
                new TimeBarBuilderFactory());
    }

    /**
     * Builds a series with an explicit logical/raw index offset.
     *
     * @param name             the series name
     * @param numFactory       the number factory
     * @param beginIndex       first logical index
     * @param endIndex         last logical index
     * @param removedBarsCount number of leading raw indexes
     * @param closes           raw close prices
     * @return the offset series
     */
    public static BarSeries offsetSeries(String name, NumFactory numFactory, int beginIndex, int endIndex,
            int removedBarsCount, double... closes) {
        BarSeries source = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(closes).build();
        return new BaseBarSeries(name, List.copyOf(source.getBarData()), beginIndex, endIndex, removedBarsCount, false,
                numFactory, new TimeBarBuilderFactory());
    }

    /**
     * Narrows a fully retained series to a logical window, keeping its bars and
     * their times.
     *
     * @param name       the series name
     * @param source     the series whose bars are reused, starting at index 0
     * @param beginIndex first logical index
     * @param endIndex   last logical index
     * @return the offset series
     */
    public static BarSeries offsetSeries(String name, BarSeries source, int beginIndex, int endIndex) {
        return new BaseBarSeries(name, List.copyOf(source.getBarData()), beginIndex, endIndex, 0, false,
                source.numFactory(), new TimeBarBuilderFactory());
    }

    /**
     * Builds a constrained one-bar series whose single bar sits at
     * {@link Integer#MAX_VALUE}, exposing the terminal index to analyses that must
     * survive loop arithmetic on the last representable index.
     *
     * @param name       the series name
     * @param numFactory the number factory
     * @param close      the raw close price of the terminal bar
     * @return the constrained terminal series
     */
    public static BarSeries terminalOneBarSeries(String name, NumFactory numFactory, double close) {
        BarSeries source = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(close).build();
        return new BaseBarSeries(name, List.copyOf(source.getBarData()), Integer.MAX_VALUE, Integer.MAX_VALUE,
                Integer.MAX_VALUE, true, numFactory, new TimeBarBuilderFactory());
    }

    /**
     * Builds a rolling window containing all but the last close. When armed, the
     * next read lease appends the last bar immediately before acquiring the lock,
     * reproducing retention advancement at the materialization boundary.
     */
    public static ConcurrentBarSeries rollingSeriesWithAppendBeforeReadLock(NumFactory numFactory,
            AtomicBoolean appendBeforeLock, double... closes) {
        BarSeries source = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(closes).build();
        int initialSize = source.getBarCount() - 1;
        ConcurrentBarSeries series = new ConcurrentBarSeries("rolling-read-lease",
                new ArrayList<>(source.getBarData().subList(0, initialSize)), 0, initialSize - 1, false, numFactory,
                new MockBarBuilderFactory()) {
            @Override
            public void withReadLock(Runnable action) {
                appendBeforeLease();
                super.withReadLock(action);
            }

            @Override
            public <T> T withReadLock(Supplier<T> action) {
                appendBeforeLease();
                return super.withReadLock(action);
            }

            private void appendBeforeLease() {
                if (appendBeforeLock.compareAndSet(true, false)) {
                    addBar(source.getBar(initialSize));
                }
            }
        };
        series.setMaximumBarCount(initialSize);
        return series;
    }

    /**
     * Appends the last close immediately after the next outermost read lease,
     * reproducing retention advancement between materialization and consumption
     * when a caller fails to hold one coherent lease.
     */
    public static ConcurrentBarSeries rollingSeriesWithAppendAfterReadLock(NumFactory numFactory,
            AtomicBoolean appendAfterLock, double... closes) {
        BarSeries source = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(closes).build();
        int initialSize = source.getBarCount() - 1;
        ConcurrentBarSeries series = new ConcurrentBarSeries("rolling-read-lease",
                new ArrayList<>(source.getBarData().subList(0, initialSize)), 0, initialSize - 1, false, numFactory,
                new MockBarBuilderFactory()) {
            private int readDepth;

            @Override
            public void withReadLock(Runnable action) {
                readDepth++;
                try {
                    super.withReadLock(action);
                } finally {
                    appendAfterOutermostLease();
                }
            }

            @Override
            public <T> T withReadLock(Supplier<T> action) {
                readDepth++;
                try {
                    return super.withReadLock(action);
                } finally {
                    appendAfterOutermostLease();
                }
            }

            private void appendAfterOutermostLease() {
                if (--readDepth == 0 && appendAfterLock.compareAndSet(true, false)) {
                    addBar(source.getBar(initialSize));
                }
            }
        };
        series.setMaximumBarCount(initialSize);
        return series;
    }

    /**
     * Waits up to five seconds for a latch, failing the test if it is not released.
     *
     * @param latch the latch to await
     */
    public static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("latch was not released");
            }
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interruption);
        }
    }

    /**
     * Close-price indicator whose first cache miss pauses while holding its cache
     * lock, so lock-order tests can hold that lock deterministically.
     */
    public static final class PausingCloseIndicator extends CachedIndicator<Num> {

        private final CountDownLatch holdingCache;
        private final CountDownLatch release;

        /**
         * @param series       the series
         * @param holdingCache counted down once the first miss holds the cache lock
         * @param release      awaited before that miss reads its bar
         */
        public PausingCloseIndicator(BarSeries series, CountDownLatch holdingCache, CountDownLatch release) {
            super(series);
            this.holdingCache = holdingCache;
            this.release = release;
        }

        @Override
        protected Num calculate(int index) {
            if (holdingCache.getCount() > 0) {
                holdingCache.countDown();
                awaitLatch(release);
            }
            return getBarSeries().getBar(index).getClosePrice();
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }
    }

    /** Returns paired criterion inputs for each window shape and edge position. */
    public static List<CriterionWindowFixture> criterionWindowFixtures(NumFactory numFactory) {
        double[] closes = { 100d, 80d, 120d, 90d, 110d, 55d };
        BarSeries fullSource = yearlySeries(numFactory, closes);
        List<Bar> fullBars = fullSource.getBarData();
        List<CriterionWindowFixture> fixtures = new ArrayList<>();
        String[] shapes = { "constrained-begin", "pruned", "bounded-record", "constrained-end" };
        String[] placements = { "before-begin", "straddling-begin", "same-bar-at-begin", "same-bar-at-end",
                "exit-after-end", "open-at-end", "entry-after-end" };
        int noExit = Integer.MIN_VALUE;

        for (String shape : shapes) {
            int begin = shape.equals("constrained-end") ? 0 : 2;
            int end = 4;
            boolean boundedRecord = shape.equals("bounded-record");
            BarSeries windowedSeries;
            if (shape.equals("constrained-begin")) {
                windowedSeries = new BaseBarSeries(shape, new ArrayList<>(fullBars), begin, end, true, numFactory,
                        new TimeBarBuilderFactory());
            } else if (shape.equals("pruned")) {
                windowedSeries = yearlySeries(numFactory, 100d, 80d, 120d, 90d, 110d);
                windowedSeries.setMaximumBarCount(3);
            } else if (boundedRecord) {
                windowedSeries = new BaseBarSeries(shape, new ArrayList<>(fullBars), 0, 5, false, numFactory,
                        new TimeBarBuilderFactory());
            } else {
                windowedSeries = new BaseBarSeries(shape, new ArrayList<>(fullBars), begin, end, true, numFactory,
                        new TimeBarBuilderFactory());
            }

            int windowSize = end - begin + 1;
            List<Bar> logicalBars = shape.equals("constrained-end") ? fullBars.subList(0, windowSize)
                    : shape.equals("pruned") ? windowedSeries.getBarData() : fullBars.subList(begin, end + 1);
            BarSeries equivalentSeries;
            if (shape.equals("pruned")) {
                equivalentSeries = new BaseBarSeries(shape + "-equivalent",
                        new ArrayList<>(windowedSeries.getBarData()), begin, end, begin, false, numFactory,
                        new TimeBarBuilderFactory());
            } else {
                equivalentSeries = new BaseBarSeries(shape + "-equivalent", new ArrayList<>(logicalBars), 0,
                        windowSize - 1, false, numFactory, new TimeBarBuilderFactory());
            }
            int[] entries = { begin - 2, begin - 1, begin, end, end - 1, end - 1, end + 1 };
            int[] exits = { begin - 1, begin + 1, begin, end, end + 1, noExit, noExit };

            for (int i = 0; i < placements.length; i++) {
                if (begin == 0 && i < 2) {
                    continue;
                }
                boolean beforeBegin = i == 0;
                boolean carriesPrunedHistory = shape.equals("pruned");
                int entryIndex = entries[i];
                int exitIndex = exits[i];
                double entryPrice = closeAt(entryIndex, closes);
                double exitPrice = exitIndex == noExit ? entryPrice : closeAt(exitIndex, closes);
                BaseTradingRecord record = criterionRecord(numFactory, entryIndex, entryPrice, exitIndex, exitPrice,
                        boundedRecord ? begin : null, boundedRecord ? end : null);
                int shiftedEntry = Math.max(0, entryIndex - begin);
                double shiftedEntryPrice = entryIndex < begin ? closeAt(begin, closes) : entryPrice;
                int shiftedExit = exitIndex == noExit ? noExit : exitIndex - begin;
                if (shiftedExit >= windowSize) {
                    shiftedExit = noExit;
                }
                int equivalentEntry = carriesPrunedHistory ? entryIndex : shiftedEntry;
                int equivalentExit = carriesPrunedHistory ? exitIndex : shiftedExit;
                double equivalentEntryPrice = carriesPrunedHistory ? entryPrice : shiftedEntryPrice;
                BaseTradingRecord equivalentRecord;
                BaseTradingRecord markedEquivalentRecord;
                if (beforeBegin && !carriesPrunedHistory) {
                    equivalentRecord = boundedRecord
                            ? new BaseTradingRecord(TradeType.BUY, 0, windowSize - 1, new ZeroCostModel(),
                                    new ZeroCostModel())
                            : new BaseTradingRecord();
                    markedEquivalentRecord = equivalentRecord;
                } else {
                    equivalentRecord = criterionRecord(numFactory, equivalentEntry, entryPrice, equivalentExit,
                            exitPrice, boundedRecord ? 0 : null, boundedRecord ? windowSize - 1 : null);
                    markedEquivalentRecord = criterionRecord(numFactory, equivalentEntry, equivalentEntryPrice,
                            equivalentExit, exitPrice, boundedRecord ? 0 : null, boundedRecord ? windowSize - 1 : null);
                }
                Position position = boundedRecord ? null
                        : record.getPositions().isEmpty() ? record.getCurrentPosition() : record.getPositions().get(0);
                Position equivalentPosition = boundedRecord || (beforeBegin && !carriesPrunedHistory) ? null
                        : equivalentRecord.getPositions().isEmpty() ? equivalentRecord.getCurrentPosition()
                                : equivalentRecord.getPositions().get(0);
                Position markedEquivalentPosition = boundedRecord || (beforeBegin && !carriesPrunedHistory) ? null
                        : markedEquivalentRecord.getPositions().isEmpty() ? markedEquivalentRecord.getCurrentPosition()
                                : markedEquivalentRecord.getPositions().get(0);
                fixtures.add(new CriterionWindowFixture(shape + "/" + placements[i], windowedSeries, record, position,
                        equivalentSeries, equivalentRecord, equivalentPosition, markedEquivalentRecord,
                        markedEquivalentPosition));
            }
        }
        return List.copyOf(fixtures);
    }

    private static BaseTradingRecord criterionRecord(NumFactory numFactory, int entryIndex, double entryPrice,
            int exitIndex, double exitPrice, Integer startIndex, Integer endIndex) {
        BaseTradingRecord record = startIndex == null
                ? new BaseTradingRecord(TradeType.BUY, new ZeroCostModel(), new ZeroCostModel())
                : new BaseTradingRecord(TradeType.BUY, startIndex, endIndex, new ZeroCostModel(), new ZeroCostModel());
        record.enter(entryIndex, numFactory.numOf(entryPrice), numFactory.one());
        if (exitIndex != Integer.MIN_VALUE) {
            record.exit(exitIndex, numFactory.numOf(exitPrice), numFactory.one());
        }
        return record;
    }

    private static double closeAt(int index, double[] closes) {
        return index < 0 ? closes[0] : closes[Math.min(index, closes.length - 1)];
    }

    private static BarSeries yearlySeries(NumFactory numFactory, double... closes) {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        Instant start = Instant.parse("2020-01-01T00:00:00Z");
        for (int i = 0; i < closes.length; i++) {
            double close = closes[i];
            series.addBar(series.barBuilder()
                    .timePeriod(Duration.ofDays(365))
                    .endTime(start.plus(Duration.ofDays(365L * i)))
                    .openPrice(close)
                    .highPrice(close)
                    .lowPrice(close)
                    .closePrice(close)
                    .volume(1)
                    .build());
        }
        return series;
    }

}

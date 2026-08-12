/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.DoubleStream;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.statistics.event.EventSynchronizationIndicator.Result;
import org.ta4j.core.indicators.statistics.event.EventSynchronizationIndicator.Result.Match;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.indicators.helpers.CrossIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.serialization.IndicatorSerialization;

/**
 * Rolling-window semantics of {@link EventSynchronizationIndicator}: closed
 * trailing windows, boundary censoring, {@code NaN} until the window is fully
 * available, event-index cache extension, and constructor validation.
 */
public class EventSynchronizationIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private static final int SERIES_BARS = 40;

    public EventSynchronizationIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    private BarSeries series() {
        return series(SERIES_BARS);
    }

    private BarSeries series(int barCount) {
        double[] prices = DoubleStream.iterate(1.0, d -> d + 1.0).limit(barCount).toArray();
        return new MockBarSeriesBuilder().withNumFactory(numFactory).withData(prices).build();
    }

    private Indicator<Boolean> events(BarSeries series, int unstableBars, int... indexes) {
        boolean[] mask = new boolean[series.getBarCount() == 0 ? 0 : series.getEndIndex() + 1];
        for (int index : indexes) {
            mask[index] = true;
        }
        return new CachedIndicator<Boolean>(series) {
            @Override
            protected Boolean calculate(int index) {
                return mask[index];
            }

            @Override
            public int getCountOfUnstableBars() {
                return unstableBars;
            }
        };
    }

    private Indicator<Boolean> thresholdSignal(BarSeries series, double threshold) {
        return new AbstractIndicator<Boolean>(series) {
            @Override
            public Boolean getValue(int index) {
                return series.getBar(index).getClosePrice().isGreaterThan(series.numFactory().numOf(threshold));
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }
        };
    }

    private EventSynchronizationIndicator indicator(Indicator<Boolean> predicted, Indicator<Boolean> reference,
            int barCount, int maxLeadBars, int maxLagBars) {
        return new EventSynchronizationIndicator(predicted, reference, barCount, maxLeadBars, maxLagBars);
    }

    @Test
    public void constructorValidationRejectsInvalidInputs() {
        BarSeries first = series();
        BarSeries second = series();
        Indicator<Boolean> signal = events(first, 0, 5);
        assertThrows(NullPointerException.class, () -> new EventSynchronizationIndicator(null, signal, 5, 0));
        assertThrows(NullPointerException.class, () -> new EventSynchronizationIndicator(signal, null, 5, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new EventSynchronizationIndicator(signal, events(second, 0, 5), 5, 0));
        assertThrows(IllegalArgumentException.class, () -> new EventSynchronizationIndicator(signal, signal, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new EventSynchronizationIndicator(signal, signal, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new EventSynchronizationIndicator(signal, signal, 5, -1));
        assertThrows(IllegalArgumentException.class, () -> new EventSynchronizationIndicator(signal, signal, 5, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> new EventSynchronizationIndicator(signal, signal, 5, -1, 0));
    }

    @Test
    public void unstableBoundaryCombinesSourceBoundariesAndWindow() {
        BarSeries series = series();
        Indicator<Boolean> predicted = events(series, 3, 10);
        Indicator<Boolean> reference = events(series, 1, 10);
        assertEquals(3 + 20 - 1, indicator(predicted, reference, 20, 0, 0).getCountOfUnstableBars());
        assertEquals(3 + 5 - 1, indicator(predicted, reference, 5, 1, 1).getCountOfUnstableBars());
    }

    @Test
    public void valueIsNaNUntilTheWindowIsFullyStable() {
        BarSeries series = series();
        Indicator<Boolean> signal = events(series, 0, 3);
        EventSynchronizationIndicator indicator = indicator(signal, signal, 5, 0, 0);
        assertEquals(4, indicator.getCountOfUnstableBars());
        // Below the boundary the window [index - 4, index] contains unstable bars.
        assertNumEquals(Double.NaN, indicator.getValue(3));
        Result unstable = indicator.getResult(3);
        assertNumEquals(Double.NaN, unstable.f1Score());
        assertEquals(0, unstable.predictedCount());
        assertEquals(-1, unstable.windowStartIndex());
        assertFalse(unstable.windowAvailable());
        // At the boundary the complete window [0, 4] is evaluated: both events at 3
        // coincide exactly.
        assertNumEquals(1.0, indicator.getValue(4));
        Result stable = indicator.getResult(4);
        assertEquals(0, stable.windowStartIndex());
        assertEquals(4, stable.windowEndIndex());
        assertTrue(stable.windowAvailable());
        assertEquals(List.of(new Match(3, 3)), stable.matches());
    }

    @Test
    public void cachedValueDoesNotOutliveWindowAfterHeadRemoval() {
        BarSeries series = series(20);
        Indicator<Boolean> predicted = events(series, 0, 15);
        Indicator<Boolean> reference = events(series, 0, 15);
        EventSynchronizationIndicator indicator = indicator(predicted, reference, 10, 0, 0);

        // Index 19 is evaluated while the window [10, 19] is fully available.
        assertNumEquals(1.0, indicator.getValue(19));

        series.setMaximumBarCount(5); // begin index advances to 15

        // Index 19 stays in-domain, but its window now reaches below the
        // retained head: the cached F1 must not outlive the window it was
        // computed from, and getResult must agree.
        assertTrue(indicator.getValue(19).isNaN());
        assertFalse(indicator.getResult(19).windowAvailable());
    }

    @Test
    public void unstableWindowNeverReadsTheSourceBelowItsBoundary() {
        BarSeries series = series();
        Indicator<Boolean> predicted = new AbstractIndicator<Boolean>(series) {
            @Override
            public Boolean getValue(int index) {
                if (index < 4) {
                    throw new AssertionError("indicator must not read below the unstable boundary");
                }
                return index == 4;
            }

            @Override
            public int getCountOfUnstableBars() {
                return 4;
            }
        };
        Indicator<Boolean> reference = events(series, 0, 4);
        // Boundary = 4 + 5 - 1 = 8: every index below 8 must resolve to NaN without
        // ever consulting the throwing source.
        EventSynchronizationIndicator indicator = indicator(predicted, reference, 5, 0, 0);
        for (int i = 0; i < 8; i++) {
            assertNumEquals(Double.NaN, indicator.getValue(i));
        }
        assertNumEquals(1.0, indicator.getValue(8));
    }

    @Test
    public void windowIsTheClosedTrailingRangeAndCensorsBoundaryEvents() {
        BarSeries series = series();
        // A prediction near the window end must not match a reference that occurs
        // after the window end, even when the tolerance window would reach it.
        Indicator<Boolean> predicted = events(series, 0, 9);
        Indicator<Boolean> reference = events(series, 0, 11);

        EventSynchronizationIndicator.Result before = indicator(predicted, reference, 5, 5, 5).getResult(10);
        assertEquals(6, before.windowStartIndex());
        assertEquals(10, before.windowEndIndex());
        assertEquals(1, before.predictedCount());
        assertEquals(0, before.referenceCount());
        assertEquals(0, before.matchedCount());
        assertEquals(List.of(9), before.unmatchedPredictedIndexes());

        // One bar later the same window length now contains the reference event.
        EventSynchronizationIndicator.Result after = indicator(predicted, reference, 5, 5, 5).getResult(11);
        assertEquals(7, after.windowStartIndex());
        assertEquals(1, after.matchedCount());
        assertEquals(List.of(new Match(9, 11)), after.matches());
        assertEquals(2, after.matches().get(0).offsetBars());
    }

    @Test
    public void eventsOutsideTheWindowDoNotParticipate() {
        BarSeries series = series();
        Indicator<Boolean> predicted = events(series, 0, 0, 9);
        Indicator<Boolean> reference = events(series, 0, 9);
        // Window [5, 9]: the predicted event at 0 is outside and must not inflate
        // the denominator.
        Result result = indicator(predicted, reference, 5, 0, 0).getResult(9);
        assertEquals(5, result.windowStartIndex());
        assertEquals(1, result.predictedCount());
        assertEquals(1, result.referenceCount());
        assertEquals(1, result.matchedCount());
        assertTrue(result.unmatchedPredictedIndexes().isEmpty());
        assertNumEquals(1.0, result.f1Score());
    }

    @Test
    public void referenceBeforeWindowStartIsCensored() {
        BarSeries series = series();
        Indicator<Boolean> predicted = events(series, 0, 7);
        Indicator<Boolean> reference = events(series, 0, 3);
        // Window [6, 10]: the reference event at 3 is before the window and counts
        // as neither a match nor a false negative.
        Result result = indicator(predicted, reference, 5, 5, 5).getResult(10);
        assertEquals(1, result.predictedCount());
        assertEquals(0, result.referenceCount());
        assertEquals(0, result.matchedCount());
        assertEquals(0, result.falseNegatives());
        assertNumEquals(0.0, result.f1Score());
    }

    @Test
    public void windowReachingBelowTheSeriesBeginIsUndefined() {
        BarSeries series = series(40);
        series.setMaximumBarCount(30);
        assertEquals(10, series.getBeginIndex());
        Indicator<Boolean> signal = events(series, 0, 12);
        EventSynchronizationIndicator indicator = indicator(signal, signal, 20, 0, 0);
        // Window [1, 20] reaches below the retained begin index 10.
        Result undefined = indicator.getResult(20);
        assertNumEquals(Double.NaN, undefined.f1Score());
        assertEquals(0, undefined.predictedCount());
        // Window [10, 29] is fully available.
        Result defined = indicator.getResult(29);
        assertEquals(10, defined.windowStartIndex());
        assertEquals(1, defined.matchedCount());
        assertNumEquals(1.0, defined.f1Score());
    }

    @Test
    public void evaluationBeyondTheSeriesEndIsUndefined() {
        BarSeries series = series();
        Indicator<Boolean> signal = events(series, 0, 38);
        EventSynchronizationIndicator indicator = indicator(signal, signal, 5, 0, 0);
        assertNumEquals(1.0, indicator.getValue(39));
        assertNumEquals(Double.NaN, indicator.getValue(40));
        Result undefined = indicator.getResult(40);
        assertEquals(36, undefined.windowStartIndex());
        assertEquals(40, undefined.windowEndIndex());
        assertNumEquals(Double.NaN, undefined.f1Score());
        assertEquals(0, undefined.predictedCount());
    }

    @Test
    public void rollingSeriesWindowReachingDroppedBarsIsUndefined() {
        BarSeries series = series(40);
        series.setMaximumBarCount(20);
        assertEquals(20, series.getBeginIndex());
        Indicator<Boolean> signal = events(series, 0, 25);
        // Window [15, 39] reaches below the retained begin index 20.
        assertNumEquals(Double.NaN, indicator(signal, signal, 25, 0, 0).getValue(39));
        // Window [20, 39] is fully available.
        assertNumEquals(1.0, indicator(signal, signal, 20, 0, 0).getValue(39));
    }

    @Test
    public void prunedScalarIndexesStayUndefinedAfterHeadDrop() {
        // The inherited CachedIndicator contract remaps indexes below the
        // retained begin index to the first retained bar; this indicator's
        // window semantics keep them undefined instead, matching getResult's
        // availability gate.
        BarSeries series = series(40);
        Indicator<Boolean> signal = events(series, 0, 12, 25);
        EventSynchronizationIndicator indicator = indicator(signal, signal, 1, 0, 0);
        assertNumEquals(1.0, indicator.getValue(12));
        series.setMaximumBarCount(15); // begin index advances to 25, pruning 12
        assertEquals(25, series.getBeginIndex());
        assertNumEquals(Double.NaN, indicator.getValue(12));
        assertNumEquals(1.0, indicator.getValue(25));
        assertFalse(indicator.getResult(12).windowAvailable());
    }

    @Test
    public void eventCacheExtendsWhenBarsAreAdded() {
        BarSeries series = series(20);
        Indicator<Boolean> predicted = new AbstractIndicator<Boolean>(series) {
            @Override
            public Boolean getValue(int index) {
                return index == 5;
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }
        };
        Indicator<Boolean> reference = new AbstractIndicator<Boolean>(series) {
            @Override
            public Boolean getValue(int index) {
                return index == 5 || index == 22;
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }
        };
        EventSynchronizationIndicator indicator = indicator(predicted, reference, 20, 0, 0);
        Result before = indicator.getResult(19);
        assertEquals(1, before.referenceCount());
        assertEquals(1, before.matchedCount());
        assertNumEquals(1.0, before.f1Score());

        // Extend the series; the event cache must pick up the new reference event
        // at 22 instead of serving a stale scan.
        Instant endTime = Instant.EPOCH.plus(Duration.ofDays(20));
        for (int i = 20; i <= 24; i++) {
            endTime = endTime.plus(Duration.ofDays(1));
            series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(endTime)
                    .openPrice(1d)
                    .closePrice(1d)
                    .highPrice(1d)
                    .lowPrice(1d)
                    .volume(1d)
                    .add();
        }
        Result after = indicator.getResult(24);
        assertEquals(5, after.windowStartIndex());
        assertEquals(2, after.referenceCount());
        assertEquals(1, after.matchedCount());
        assertEquals(List.of(22), after.unmatchedReferenceIndexes());
        assertNumEquals(2.0 / 3.0, after.f1Score());
    }

    @Test
    public void replacedEndBarIsRescanned() {
        BarSeries series = series(20);
        Indicator<Boolean> signal = new AbstractIndicator<Boolean>(series) {
            @Override
            public Boolean getValue(int index) {
                return series.getBar(index).getClosePrice().isGreaterThan(series.numFactory().numOf(19.5));
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }
        };
        EventSynchronizationIndicator indicator = indicator(signal, signal, 20, 0, 0);
        // Window [0, 19]: the last close (20) exceeds the threshold, so both
        // streams fire at 19 and match exactly.
        Result before = indicator.getResult(19);
        assertEquals(1, before.matchedCount());
        assertNumEquals(1.0, before.f1Score());

        // A live forming bar is revised in place instead of appended: replacing
        // the end bar must re-read its event state, not serve the stale scan.
        series.addBar(series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(Instant.EPOCH.plus(Duration.ofDays(19)))
                .closePrice(10d)
                .build(), true);
        Result after = indicator.getResult(19);
        assertEquals(0, after.predictedCount());
        assertEquals(0, after.referenceCount());
        assertEquals(0, after.matchedCount());
        assertNumEquals(Double.NaN, after.f1Score());
    }

    @Test
    public void repeatedEvaluationIsDeterministicAndEqualsTheCachedValue() {
        BarSeries series = series();
        Indicator<Boolean> predicted = events(series, 0, 4, 9, 14);
        Indicator<Boolean> reference = events(series, 0, 5, 10, 15);
        EventSynchronizationIndicator indicator = indicator(predicted, reference, 20, 1, 0);
        Result first = indicator.getResult(19);
        Result second = indicator.getResult(19);
        assertEquals(first, second);
        assertEquals(0, first.windowStartIndex());
        assertNumEquals(1.0, indicator.getValue(19));
        assertEquals(indicator.getValue(19), first.f1Score());
    }

    @Test
    public void bothEmptyWindowYieldsUndefinedMetrics() {
        BarSeries series = series();
        Indicator<Boolean> empty = events(series, 0);
        Result result = indicator(empty, empty, 5, 0, 0).getResult(9);
        assertEquals(0, result.predictedCount());
        assertEquals(0, result.referenceCount());
        assertNumEquals(Double.NaN, result.precision());
        assertNumEquals(Double.NaN, result.recall());
        assertNumEquals(Double.NaN, result.f1Score());
        assertTrue(result.windowAvailable());
    }

    @Test
    public void partiallyEmptyWindowKeepsTheDocumentedSemantics() {
        BarSeries series = series();
        Indicator<Boolean> empty = events(series, 0);
        Indicator<Boolean> reference = events(series, 0, 5);
        Result result = indicator(empty, reference, 5, 0, 0).getResult(5);
        assertNumEquals(Double.NaN, result.precision());
        assertNumEquals(0.0, result.recall());
        assertNumEquals(0.0, result.f1Score());
    }

    @Test
    public void resultListsAreImmutable() {
        BarSeries series = series();
        Indicator<Boolean> signal = events(series, 0, 5);
        Result result = indicator(signal, signal, 5, 0, 0).getResult(9);
        assertThrows(UnsupportedOperationException.class, () -> result.matches().add(new Match(0, 0)));
        assertThrows(UnsupportedOperationException.class, () -> result.unmatchedPredictedIndexes().add(1));
        assertThrows(UnsupportedOperationException.class, () -> result.unmatchedReferenceIndexes().add(1));
    }

    @Test
    public void historicalReplaceInvalidatesTheEventIndexCache() {
        BarSeries series = series(); // closes 1..40
        Indicator<Boolean> predicted = thresholdSignal(series, 15.5); // events at 15..39
        Indicator<Boolean> reference = events(series, 0, 15, 16, 17, 18, 19);
        EventSynchronizationIndicator indicator = indicator(predicted, reference, 20, 0, 0);
        // Window [0, 19]: predicted and reference both carry events 15..19.
        Result before = indicator.getResult(19);
        assertEquals(5, before.predictedCount());
        assertEquals(5, before.matchedCount());
        assertNumEquals(1.0, before.f1Score());
        assertNumEquals(1.0, indicator.getValue(19));

        // Replace a retained bar inside the scanned range, flipping its predicted
        // event membership. The event-index caches must observe the published
        // revision change and rescan instead of serving the stale event at 16.
        ((BaseBarSeries) series).replaceBar(16,
                series.barBuilder()
                        .timePeriod(Duration.ofDays(1))
                        .endTime(Instant.EPOCH.plus(Duration.ofDays(17)))
                        .closePrice(10d)
                        .build());

        Result after = indicator.getResult(19);
        assertEquals(4, after.predictedCount());
        assertEquals(4, after.matchedCount());
        assertEquals(1, after.falseNegatives());
        assertEquals(List.of(16), after.unmatchedReferenceIndexes());
        assertEquals(List.of(15, 17, 18, 19), after.matches().stream().map(Match::referenceIndex).toList());
        assertNumEquals(8d / 9d, after.f1Score());
        assertNumEquals(8d / 9d, indicator.getValue(19));
    }

    @Test
    public void clearAndRebuildInvalidatesTheEventIndexCache() {
        BarSeries series = series(); // closes 1..40
        Indicator<Boolean> signal = thresholdSignal(series, 15.5);
        EventSynchronizationIndicator indicator = indicator(signal, signal, 20, 0, 0);
        // Window [0, 19] with events 15..19.
        Result before = indicator.getResult(19);
        assertEquals(5, before.predictedCount());
        assertNumEquals(1.0, before.f1Score());

        // Clear and rebuild from index zero with a different event pattern. The
        // new bars reuse absolute indexes 0..24, so only revision-based
        // invalidation can prevent the old cached events from leaking.
        series.clear();
        Instant endTime = Instant.EPOCH;
        for (int i = 0; i < 25; i++) {
            endTime = endTime.plus(Duration.ofDays(1));
            double close = i < 10 ? 5d : 22d;
            series.addBar(
                    series.barBuilder().timePeriod(Duration.ofDays(1)).endTime(endTime).closePrice(close).build());
        }

        // Window [5, 24] contains events at 10..24 (15 events).
        Result after = indicator.getResult(24);
        assertEquals(15, after.predictedCount());
        assertEquals(15, after.matchedCount());
        assertEquals(5, after.windowStartIndex());
        assertEquals(24, after.windowEndIndex());
        assertNumEquals(1.0, after.f1Score());
        assertNumEquals(1.0, indicator.getValue(24));
    }

    @Test
    public void windowAvailableDistinguishesUnavailableFromEmptyWindows() {
        BarSeries series = series();
        Indicator<Boolean> signal = events(series, 0, 5);
        EventSynchronizationIndicator indicator = indicator(signal, signal, 5, 0, 0);
        // Before the unstable boundary the window [ -1, 3] is unavailable.
        assertFalse(indicator.getResult(3).windowAvailable());
        // Evaluated windows with events are available.
        assertTrue(indicator.getResult(9).windowAvailable());
        // An available window whose streams contain no events keeps zero counts
        // and NaN metrics but is still available.
        Result empty = indicator(events(series, 0), events(series, 0), 5, 0, 0).getResult(9);
        assertTrue(empty.windowAvailable());
        assertEquals(0, empty.predictedCount());
        assertEquals(0, empty.referenceCount());
        assertNumEquals(Double.NaN, empty.f1Score());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void jsonRoundTripRestoresAnEquivalentIndicator() {
        BarSeries series = series();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        Indicator<Boolean> predicted = new CrossIndicator(
                new ConstantIndicator<>(series, series.numFactory().numOf(10)), close);
        Indicator<Boolean> reference = new CrossIndicator(
                new ConstantIndicator<>(series, series.numFactory().numOf(12)), close);
        EventSynchronizationIndicator indicator = indicator(predicted, reference, 20, 5, 5);

        Indicator<?> descriptorCopy = IndicatorSerialization.fromDescriptor(series, indicator.toDescriptor());
        Indicator<?> jsonCopy = Indicator.fromJson(series, indicator.toJson());

        assertEquals(indicator.toDescriptor(), descriptorCopy.toDescriptor());
        assertEquals(indicator.toDescriptor(), jsonCopy.toDescriptor());
        assertTrue(jsonCopy instanceof EventSynchronizationIndicator);
        EventSynchronizationIndicator restored = (EventSynchronizationIndicator) jsonCopy;
        // Crossings at 10 (predicted) and 12 (reference) match within the
        // tolerance window [6, 25].
        assertNumEquals(1.0, indicator.getValue(25));
        assertNumEquals(1.0, restored.getValue(25));
        assertEquals(indicator.getResult(25), restored.getResult(25));
    }

    @Test
    public void unstableBoundarySaturationDoesNotLeakPastIntOverflow() {
        // Sources unstable through Integer.MAX_VALUE - 1 saturate the published
        // boundary at Integer.MAX_VALUE; with barCount = 2 the true boundary is
        // MAX_VALUE + 1, which no int index can reach. The availability check
        // must not let the saturated count make the window at MAX_VALUE look
        // complete.
        BarSeries series = series(1);
        BarSeries atMaxSeries = new BaseBarSeries(series.getName(), series.getBarData()) {
            @Override
            public int getBeginIndex() {
                return Integer.MAX_VALUE;
            }

            @Override
            public int getEndIndex() {
                return Integer.MAX_VALUE;
            }
        };
        Indicator<Boolean> unstable = new AbstractIndicator<Boolean>(atMaxSeries) {
            @Override
            public Boolean getValue(int index) {
                return index == Integer.MAX_VALUE;
            }

            @Override
            public int getCountOfUnstableBars() {
                return Integer.MAX_VALUE;
            }
        };
        EventSynchronizationIndicator indicator = indicator(unstable, unstable, 2, 0, 0);
        // The combined boundary overflows int and saturates...
        assertEquals(Integer.MAX_VALUE, indicator.getCountOfUnstableBars());
        // ...but the window [MAX_VALUE - 1, MAX_VALUE] still contains the
        // unstable bar MAX_VALUE - 1 and must not be evaluated.
        Result result = indicator.getResult(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE - 1, result.windowStartIndex());
        assertNumEquals(Double.NaN, result.f1Score());
        assertEquals(0, result.predictedCount());
        assertFalse(result.windowAvailable());
        assertNumEquals(Double.NaN, indicator.getValue(Integer.MAX_VALUE));
    }

    @Test
    public void eventCacheStaysBoundedByTheRollingWindow() {
        // One event per bar over a history long enough to cross the rolling
        // eviction threshold: without window eviction the event caches would
        // grow unbounded and eventually trip the matcher's capacity limit.
        int barCount = 100;
        BarSeries series = series(barCount);
        Indicator<Boolean> everyBar = new AbstractIndicator<Boolean>(series) {
            @Override
            public Boolean getValue(int index) {
                return Boolean.TRUE;
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }
        };
        EventSynchronizationIndicator indicator = indicator(everyBar, everyBar, 10, 0, 0);
        for (int i = 0; i < barCount; i++) {
            indicator.getValue(i);
        }
        // Rolling evaluation evicts everything below the last window.
        assertTrue("predictedEvents.size=" + indicator.predictedEvents.size, indicator.predictedEvents.size <= 10);
        assertTrue("referenceEvents.size=" + indicator.referenceEvents.size, indicator.referenceEvents.size <= 10);
        assertNumEquals(1.0, indicator.getValue(barCount - 1));
        // A backward evaluation below the eviction frontier resets the caches
        // and rescans from scratch; results stay correct and the bound holds.
        assertNumEquals(1.0, indicator.getResult(barCount - 21).f1Score());
        assertTrue("predictedEvents.size=" + indicator.predictedEvents.size, indicator.predictedEvents.size <= 10);
        assertTrue("referenceEvents.size=" + indicator.referenceEvents.size, indicator.referenceEvents.size <= 10);
    }

    @Test
    public void firstRequestAtDistantIndexKeepsCacheBounded() {
        // A first evaluation that jumps far ahead of the current scan frontier
        // must evict below the requested window while catching up: with a signal
        // firing every bar, a 100-bar catch-up would otherwise retain 100
        // events even though the requested window is only 10 bars wide.
        int barCount = 100;
        BarSeries series = series(barCount);
        Indicator<Boolean> everyBar = new AbstractIndicator<Boolean>(series) {
            @Override
            public Boolean getValue(int index) {
                return Boolean.TRUE;
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }
        };
        EventSynchronizationIndicator indicator = indicator(everyBar, everyBar, 10, 0, 0);
        assertNumEquals(1.0, indicator.getResult(barCount - 1).f1Score());
        assertTrue("predictedEvents.size=" + indicator.predictedEvents.size, indicator.predictedEvents.size <= 10);
        assertTrue("referenceEvents.size=" + indicator.referenceEvents.size, indicator.referenceEvents.size <= 10);
    }

    @Test
    public void windowWiderThanHalfTheMatcherCapacityStillBoundsTheCache() {
        // The cache mechanics under test are factory-independent (int arrays
        // and Boolean signals), so the heavy ~6.3M-index catch-up scan runs
        // once instead of once per numeric factory; the small-scale eviction
        // tests above already cover both factories.
        if (!(numFactory instanceof DoubleNumFactory)) {
            return;
        }
        // A window wider than half the matcher capacity (8,000,000 cells) makes
        // the rolling eviction threshold exceed the events array's growth
        // ceiling: without capping the threshold, a catch-up over more than
        // 4,194,304 events that all predate the window would fill the array and
        // throw before the first eviction. The capped threshold keeps the cache
        // bounded and the event-free window evaluable.
        int windowSize = 2_097_153;
        int lastEventIndex = 4_194_304;
        BarSeries series = series(1);
        BaseBarSeries proxy = new BaseBarSeries(series.getName(), series.getBarData()) {
            @Override
            public int getBeginIndex() {
                return 0;
            }

            @Override
            public int getEndIndex() {
                return lastEventIndex + windowSize;
            }
        };
        Indicator<Boolean> earlyOnly = new AbstractIndicator<Boolean>(proxy) {
            @Override
            public Boolean getValue(int index) {
                return index <= lastEventIndex;
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }
        };
        EventSynchronizationIndicator indicator = indicator(earlyOnly, earlyOnly, windowSize, 0, 0);
        // The requested window [lastEventIndex + 1, lastEventIndex + windowSize]
        // is event-free and far above the initial scan frontier: the catch-up
        // must evict instead of accumulating the 4,194,305 earlier events.
        indicator.getResult(lastEventIndex + windowSize);
        // Every cached event predates the window, so the caches end up empty;
        // the backing arrays must shrink back to the initial capacity instead
        // of retaining ~32 MiB of dead storage until the end of the run.
        assertEquals(0, indicator.predictedEvents.size);
        assertEquals(16, indicator.predictedEvents.events.length);
        assertEquals(0, indicator.referenceEvents.size);
        assertEquals(16, indicator.referenceEvents.events.length);
    }

    @Test
    public void minimalEventCacheIsReusedAcrossRollingEvictions() {
        // A one-bar window whose latest bar is event-free evicts to empty on
        // every evaluation; the cache previously allocated a fresh 16-cell
        // backing array per bar (allocation churn on the hot rolling path).
        // An already-minimal array must be reused instead.
        BarSeries series = series();
        Indicator<Boolean> signal = events(series, 0, 0, 1, 2);
        EventSynchronizationIndicator indicator = indicator(signal, signal, 1, 0, 0);
        indicator.getValue(2);
        int[] backing = indicator.predictedEvents.events;
        assertEquals(16, backing.length);
        for (int i = 3; i < 40; i++) {
            indicator.getValue(i);
            assertSame("minimal cache must not be reallocated on rolling evictions", backing,
                    indicator.predictedEvents.events);
            assertEquals(0, indicator.predictedEvents.size);
        }
    }

    @Test
    public void failedScanRollsBackSoARetryNeverAppendsEventsTwice() {
        // A signal that throws mid-catch-up leaves the cache partially
        // appended; without a rollback the retry scans the same range again
        // and appends every pre-failure event twice, doubling the matches.
        // The result after a failed attempt must equal a clean first pass.
        BarSeries series = series();
        Indicator<Boolean> predicted = events(series, 0, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);
        Indicator<Boolean> reference = new AbstractIndicator<Boolean>(series) {
            private boolean threw;

            @Override
            public Boolean getValue(int index) {
                if (index == 13 && !threw) {
                    threw = true;
                    throw new IllegalStateException("boom");
                }
                return index <= 15;
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }
        };
        EventSynchronizationIndicator failing = indicator(predicted, reference, 5, 0, 0);
        assertThrows(IllegalStateException.class, () -> failing.getValue(15));
        Result afterRetry = failing.getResult(15);

        EventSynchronizationIndicator clean = indicator(predicted,
                events(series, 0, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15), 5, 0, 0);
        assertEquals(clean.getResult(15), afterRetry);
    }


    @Test
    public void concurrentRandomAccessEvaluationMatchesSequentialResults() throws Exception {
        // Concurrent evaluations for different indexes must never observe the
        // event caches between a reset/eviction and the following rescan: both
        // event windows are captured inside the coordination lock, so every
        // concurrent result equals the sequential one.
        int barCount = 1_200;
        BarSeries series = series(barCount);
        Indicator<Boolean> everyBar = new AbstractIndicator<Boolean>(series) {
            @Override
            public Boolean getValue(int index) {
                return Boolean.TRUE;
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }
        };
        EventSynchronizationIndicator indicator = indicator(everyBar, everyBar, 10, 0, 0);
        int[] indexes = new int[500];
        Random rnd = new Random(42L);
        for (int i = 0; i < indexes.length; i++) {
            indexes[i] = 9 + rnd.nextInt(barCount - 18);
        }
        Map<Integer, ResultSnapshot> baseline = new HashMap<>();
        for (int index : indexes) {
            baseline.put(index, new ResultSnapshot(indicator.getResult(index)));
        }
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < 4; t++) {
                final long seed = 1000L + t;
                futures.add(pool.submit(() -> {
                    Random random = new Random(seed);
                    for (int i = 0; i < 400; i++) {
                        int index = indexes[random.nextInt(indexes.length)];
                        ResultSnapshot expected = baseline.get(index);
                        ResultSnapshot actual = new ResultSnapshot(indicator.getResult(index));
                        if (!expected.equals(actual)) {
                            throw new AssertionError(
                                    "index " + index + ": expected " + expected + " but was " + actual);
                        }
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private record ResultSnapshot(int windowStartIndex, int predictedCount, int referenceCount, int matchedCount,
            double f1Score) {

        ResultSnapshot(Result result) {
            this(result.windowStartIndex(), result.predictedCount(), result.referenceCount(), result.matchedCount(),
                    result.f1Score().doubleValue());
        }
    }

    @Test
    public void evaluationAtMaximumBarIndexTerminatesWithoutOverflow() {
        // A rolling series may legally reach getEndIndex() == Integer.MAX_VALUE;
        // the indicator must evaluate the single-bar window there and terminate.
        BarSeries series = series(1);
        BarSeries atMaxSeries = new BaseBarSeries(series.getName(), series.getBarData()) {
            @Override
            public int getBeginIndex() {
                return Integer.MAX_VALUE;
            }

            @Override
            public int getEndIndex() {
                return Integer.MAX_VALUE;
            }
        };
        Indicator<Boolean> atMax = new AbstractIndicator<Boolean>(atMaxSeries) {
            @Override
            public Boolean getValue(int index) {
                return index == Integer.MAX_VALUE;
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }
        };
        Result result = indicator(atMax, atMax, 1, 0, 0).getResult(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, result.windowStartIndex());
        assertEquals(1, result.predictedCount());
        assertEquals(1, result.referenceCount());
        assertEquals(1, result.matchedCount());
        assertEquals(List.of(new Match(Integer.MAX_VALUE, Integer.MAX_VALUE)), result.matches());
        assertEquals(0, result.matches().get(0).offsetBars());
        assertNumEquals(1.0, result.f1Score());
    }
}

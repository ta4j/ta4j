/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.ReturnIndicator;
import org.ta4j.core.indicators.averages.EWMAIndicator;
import org.ta4j.core.indicators.forecast.state.ReturnForecastState;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.indicators.helpers.LogReturnIndicator;
import org.ta4j.core.indicators.statistics.EwmaVarianceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class EwmaReturnForecastStateIndicatorTest
        extends AbstractIndicatorTest<LogReturnIndicator, ReturnForecastState> {

    public EwmaReturnForecastStateIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void initializesRollingMeanStateAfterWarmup() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 121, 133.1).build();
        LogReturnIndicator returns = new LogReturnIndicator(series);
        EwmaReturnForecastStateIndicator stateIndicator = new EwmaReturnForecastStateIndicator(returns, 2, 0.5,
                EwmaReturnForecastStateIndicator.DriftMode.ROLLING_MEAN);

        assertSame(returns, stateIndicator.getReturnIndicator());
        assertEquals(ReturnRepresentation.LOG, stateIndicator.getReturnRepresentation());
        assertEquals(2, stateIndicator.getCountOfUnstableBars());
        assertTrue(stateIndicator.getValue(1).mean().isNaN());
        ReturnForecastState state = stateIndicator.getValue(2);

        assertTrue(state.isStable());
        assertEquals(2, state.observationCount());
        assertNumEquals(Math.log(1.1), state.mean());
        assertNumEquals(Math.log(1.1), state.drift());
        assertNumEquals(0d, state.variance());
        assertNumEquals(0d, state.volatility());
    }

    @Test
    public void initializesVarianceWithPopulationWindow() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3).build();
        FixedReturnIndicator returns = new FixedReturnIndicator(series, ReturnRepresentation.LOG, numOf(0), numOf(1),
                numOf(3));
        EwmaReturnForecastStateIndicator stateIndicator = new EwmaReturnForecastStateIndicator(returns, 3, 0.5,
                EwmaReturnForecastStateIndicator.DriftMode.ROLLING_MEAN);

        ReturnForecastState state = stateIndicator.getValue(2);

        assertTrue(state.isStable());
        assertNumEquals(4d / 3d, state.mean());
        assertNumEquals(14d / 9d, state.variance());
    }

    @Test
    public void zeroDriftModeKeepsMeanButUsesZeroDrift() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 121).build();
        LogReturnIndicator returns = new LogReturnIndicator(series);
        EwmaReturnForecastStateIndicator stateIndicator = new EwmaReturnForecastStateIndicator(returns, 2, 0.5);

        ReturnForecastState state = stateIndicator.getValue(2);

        assertTrue(state.isStable());
        assertNumEquals(Math.log(1.1), state.mean());
        assertNumEquals(0, state.drift());
    }

    @Test
    public void recursiveUpdateIsStableWhenLateIndexIsRequestedFirst() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 121, 140).build();
        LogReturnIndicator returns = new LogReturnIndicator(series);
        EwmaReturnForecastStateIndicator stateIndicator = new EwmaReturnForecastStateIndicator(returns, 2, 0.5,
                EwmaReturnForecastStateIndicator.DriftMode.ROLLING_MEAN);

        ReturnForecastState state = stateIndicator.getValue(3);

        assertTrue(state.isStable());
        assertEquals(3, state.observationCount());
        assertTrue(state.volatility().isPositive());
    }

    @Test
    public void invalidReturnsKeepStateUnstableUntilWindowIsValid() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 0, 100, 110, 121)
                .build();
        LogReturnIndicator returns = new LogReturnIndicator(series);
        EwmaReturnForecastStateIndicator stateIndicator = new EwmaReturnForecastStateIndicator(returns, 2, 0.5,
                EwmaReturnForecastStateIndicator.DriftMode.ROLLING_MEAN);

        assertTrue(stateIndicator.getValue(2).mean().isNaN());
        assertTrue(stateIndicator.getValue(3).mean().isNaN());
        ReturnForecastState recovered = stateIndicator.getValue(4);
        assertTrue(recovered.isStable());
        assertEquals(2, recovered.observationCount());
    }

    @Test
    public void rejectsNonLogReturnRepresentations() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3).build();
        for (ReturnRepresentation representation : List.of(ReturnRepresentation.DECIMAL,
                ReturnRepresentation.PERCENTAGE, ReturnRepresentation.MULTIPLICATIVE)) {
            FixedReturnIndicator returns = new FixedReturnIndicator(series, representation, numOf(0), numOf(1),
                    numOf(3));

            assertThrows(IllegalArgumentException.class, () -> new EwmaReturnForecastStateIndicator(returns, 2, 0.5));
        }
    }

    @Test
    public void rejectsInvalidDecayFactors() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3).build();
        LogReturnIndicator returns = new LogReturnIndicator(series);

        assertThrows(IllegalArgumentException.class, () -> new EwmaReturnForecastStateIndicator(returns, 2, 0));
        assertThrows(IllegalArgumentException.class, () -> new EwmaReturnForecastStateIndicator(returns, 2, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new EwmaReturnForecastStateIndicator(returns, 2, Double.NaN));
    }

    @Test
    public void sharedMeanReAnchorsWithVarianceAfterPrune() {
        // The state must pair the variance with the same EWMA mean the
        // variance re-anchors on prune; a separate mean estimator would keep
        // its pre-prune recursion and publish a stale mean/drift next to the
        // re-anchored variance.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 1.1051709180756477, 1.22140275816017, 1.3498588075760032, 1.4918246976412703,
                        1.6487212707001282, 1.8221188003905089, 2.0137527074704766, 5.4739473917272, 14.879731724872837,
                        40.44730436006742)
                .build();
        LogReturnIndicator returns = new LogReturnIndicator(series);
        EwmaReturnForecastStateIndicator stateIndicator = new EwmaReturnForecastStateIndicator(returns, 3, 0.9,
                EwmaReturnForecastStateIndicator.DriftMode.ROLLING_MEAN);

        // Warm the caches against the full prefix so stale recursions would
        // surface after the prune.
        assertTrue(stateIndicator.getValue(10).isStable());

        series.setMaximumBarCount(6);
        series.addBar(series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(series.getLastBar().getEndTime().plus(Duration.ofDays(1)))
                .openPrice(42.52108155356342)
                .highPrice(42.52108155356342)
                .lowPrice(42.52108155356342)
                .closePrice(42.52108155356342)
                .build());
        series.addBar(series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(series.getLastBar().getEndTime().plus(Duration.ofDays(1)))
                .openPrice(44.70118357203251)
                .highPrice(44.70118357203251)
                .lowPrice(44.70118357203251)
                .closePrice(44.70118357203251)
                .build());
        ReturnForecastState state = stateIndicator.getValue(12);
        assertTrue(state.isStable());
        // The observation count re-anchors past the retained head: the head's
        // log return is an artificial zero computed against the pruned
        // predecessor, so seven pruned bars plus the one-bar lookback make
        // the sixth retained bar report the five returns its mean and
        // variance actually fold.
        assertEquals(5, state.observationCount());

        // Fresh estimators built after the prune re-anchor from the retained
        // head; the stale full-history mean (~0.26) would fail this check.
        assertNumEquals(new EWMAIndicator(returns, 3, 0.9).getValue(12), state.mean(), 1e-9);
        assertNumEquals(new EwmaVarianceIndicator(returns, 3, 0.9).getValue(12), state.variance(), 1e-9);

        // A retained index whose state was cached before the prune must be
        // recomputed from the re-anchored estimators: the indicator's own
        // cache must not serve the pre-prune stable state.
        ReturnForecastState retainedState = stateIndicator.getValue(10);
        assertTrue(retainedState.isStable());
        assertNumEquals(new EWMAIndicator(returns, 3, 0.9).getValue(10), retainedState.mean(), 1e-9);
        assertNumEquals(new EwmaVarianceIndicator(returns, 3, 0.9).getValue(10), retainedState.variance(), 1e-9);
        assertEquals(3, retainedState.observationCount());
    }

    @Test
    public void prunedHeadArtificialReturnIsNotCounted() {
        // The retained head's log return is computed against the pruned
        // predecessor, so it is an artificial zero: the observation count
        // must restart past it instead of folding it into the moments.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 121, 133.1).build();
        LogReturnIndicator returns = new LogReturnIndicator(series);
        EwmaReturnForecastStateIndicator stateIndicator = new EwmaReturnForecastStateIndicator(returns, 2, 0.5,
                EwmaReturnForecastStateIndicator.DriftMode.ROLLING_MEAN);

        // Warm the pre-prune caches so the prune exercises the invalidation.
        assertTrue(stateIndicator.getValue(3).isStable());

        series.setMaximumBarCount(2);
        series.addBar(series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(series.getLastBar().getEndTime().plus(Duration.ofDays(1)))
                .openPrice(146.41)
                .highPrice(146.41)
                .lowPrice(146.41)
                .closePrice(146.41)
                .build());
        series.addBar(series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(series.getLastBar().getEndTime().plus(Duration.ofDays(1)))
                .openPrice(161.051)
                .highPrice(161.051)
                .lowPrice(161.051)
                .closePrice(161.051)
                .build());

        // Four bars were pruned in total, so the retained head is index 4
        // and the count restarts at index 5 (head + one log-return bar);
        // only two bars are retained, so index 5 is the last readable one.
        ReturnForecastState headState = stateIndicator.getValue(4);
        assertFalse(headState.isStable());
        assertEquals(0, headState.observationCount());
        assertEquals(1, stateIndicator.getValue(5).observationCount());
    }

    @Test
    public void publishedBarMutationRefreshesCachedState() {
        // Mutating the published end bar (addPrice replaces its close and
        // bumps the series bar-history revision): the cached state must
        // refresh on the revision change instead of publishing the state
        // computed from the superseded close.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 121, 133.1).build();
        LogReturnIndicator returns = new LogReturnIndicator(series);
        EwmaReturnForecastStateIndicator stateIndicator = new EwmaReturnForecastStateIndicator(returns, 2, 0.5,
                EwmaReturnForecastStateIndicator.DriftMode.ROLLING_MEAN);

        ReturnForecastState before = stateIndicator.getValue(3);
        assertTrue(before.isStable());

        series.addPrice(120);

        ReturnForecastState after = stateIndicator.getValue(3);
        assertTrue(after.isStable());
        assertFalse(after.mean().isEqual(before.mean()));
    }

    @Test
    public void publishedBarReplacedBetweenMeanAndVarianceReadsRetriesToConsistentState() {
        // A concurrent writer replacing the published end bar mid-read must not
        // publish a state whose mean was read from the superseded close while the
        // variance was read from the replacement close. The MutatingReturnIndicator
        // replaces the close only after the observation-count and mean reads have
        // consumed the superseded value, so the retry bracket in getValue(3) must
        // re-read against the new revision and match a reference built over the
        // post-replacement close sequence.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 121, 133.1).build();
        MutatingReturnIndicator mutatingReturns = new MutatingReturnIndicator(series, 3, numFactory.numOf(120));
        EwmaReturnForecastStateIndicator stateIndicator = new EwmaReturnForecastStateIndicator(mutatingReturns, 2, 0.5,
                EwmaReturnForecastStateIndicator.DriftMode.ROLLING_MEAN);

        ReturnForecastState result = stateIndicator.getValue(3);

        BarSeries referenceSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 110, 121, 120)
                .build();
        EwmaReturnForecastStateIndicator referenceIndicator = new EwmaReturnForecastStateIndicator(
                new LogReturnIndicator(referenceSeries), 2, 0.5,
                EwmaReturnForecastStateIndicator.DriftMode.ROLLING_MEAN);
        ReturnForecastState reference = referenceIndicator.getValue(3);

        assertTrue(result.isStable());
        assertTrue(reference.isStable());
        assertNumEquals(reference.mean(), result.mean());
        assertNumEquals(reference.variance(), result.variance());
    }

    @Test
    public void revisionlessInteriorCloseMutationRefreshesSameIndexState() {
        BarSeries series = revisionlessSeries(100, 110, 121, 133.1, 146.41, 161.051);
        EwmaReturnForecastStateIndicator warmed = rollingMeanState(new LogReturnIndicator(series));
        assertTrue(warmed.getValue(4).isStable());
        Bar interiorBar = series.getBar(2);
        Bar endBar = series.getLastBar();

        interiorBar.addPrice(series.numFactory().numOf(150));

        assertSame(interiorBar, series.getBar(2));
        assertSame(endBar, series.getLastBar());
        assertEquals(0, series.getBeginIndex());
        assertEquals(5, series.getEndIndex());
        assertEquals(-1L, series.getBarHistoryRevision());
        assertEquals(-1L, series.getBarSeriesChangeSnapshot(-1L).revision());
        assertStateEquals(rollingMeanState(new LogReturnIndicator(series)).getValue(4), warmed.getValue(4));
    }

    @Test
    public void revisionlessInvalidInteriorCloseMutationRefreshesStateAfterAppend() {
        BarSeries series = revisionlessSeries(100, 110, 121, 133.1, 146.41, 161.051);
        EwmaReturnForecastStateIndicator warmed = rollingMeanState(new LogReturnIndicator(series));
        assertEquals(5, warmed.getValue(5).observationCount());
        series.getBar(2).addPrice(series.numFactory().zero());
        appendClose(series, 177.1561);

        ReturnForecastState fresh = rollingMeanState(new LogReturnIndicator(series)).getValue(6);
        assertTrue(fresh.isStable());
        assertEquals(3, fresh.observationCount());
        assertStateEquals(fresh, warmed.getValue(6));
    }

    @Test
    public void revisionlessAppendedReturnCachedBeforeMutationRefreshesExtendedState() {
        assertAppendedReturnRefreshesState(false, 0);
    }

    @Test
    public void revisionlessFiniteAppendedReturnMutationRefreshesMeanDriftAndVariance() {
        assertAppendedReturnRefreshesState(false, 180);
    }

    @Test
    public void revisionlessEarlierStateReadDoesNotAcceptStaleAppendedReturn() {
        assertAppendedReturnRefreshesState(true, 0);
    }

    private void assertAppendedReturnRefreshesState(boolean readEarlierState, double changedClose) {
        BarSeries series = revisionlessSeries(100, 110, 121, 133.1);
        LogReturnIndicator returns = new LogReturnIndicator(series);
        EwmaReturnForecastStateIndicator warmed = rollingMeanState(returns);
        ReturnForecastState prefix = warmed.getValue(3);
        appendClose(series, 146.41);
        appendClose(series, 161.051);
        appendClose(series, 177.1561);
        assertNumEquals(Math.log(1.1), returns.getValue(4));
        assertNumEquals(Math.log(1.1), returns.getValue(5));
        Bar interiorBar = series.getBar(4);
        Bar endBar = series.getLastBar();

        interiorBar.addPrice(series.numFactory().numOf(changedClose));
        if (readEarlierState) {
            assertStateEquals(prefix, warmed.getValue(3));
        }

        assertSame(interiorBar, series.getBar(4));
        assertSame(endBar, series.getLastBar());
        assertEquals(0, series.getBeginIndex());
        assertEquals(6, series.getEndIndex());
        assertEquals(-1L, series.getBarHistoryRevision());
        ReturnForecastState fresh = rollingMeanState(new LogReturnIndicator(series)).getValue(6);
        assertEquals(changedClose == 0 ? 1 : 6, fresh.observationCount());
        for (int read = 0; read < 2; read++) {
            assertStateEquals(fresh, warmed.getValue(6));
        }
        appendClose(series, 194.87171);
        assertStateEquals(rollingMeanState(new LogReturnIndicator(series)).getValue(7), warmed.getValue(7));
    }

    @Test
    public void revisionlessAppendDuringReadDoesNotAcceptStaleUnconsumedReturns() {
        BarSeries series = revisionlessSeries(100, 110, 121, 133.1);
        LogReturnIndicator delegate = new LogReturnIndicator(series);
        ReturnIndicator returns = new ReturnIndicator() {
            private boolean appended;

            @Override
            public Num getValue(int index) {
                if (index == 4 && !appended) {
                    appended = true;
                    appendClose(series, 161.051);
                    appendClose(series, 177.1561);
                    assertNumEquals(Math.log(1.1), delegate.getValue(5));
                    series.getBar(5).addPrice(series.numFactory().zero());
                }
                return delegate.getValue(index);
            }

            @Override
            public BarSeries getBarSeries() {
                return series;
            }

            @Override
            public ReturnRepresentation getReturnRepresentation() {
                return ReturnRepresentation.LOG;
            }

            @Override
            public int getCountOfUnstableBars() {
                return delegate.getCountOfUnstableBars();
            }

            @Override
            public List<Indicator<?>> getDependencies() {
                return List.of(delegate);
            }
        };
        EwmaReturnForecastStateIndicator warmed = rollingMeanState(returns);
        assertTrue(warmed.getValue(3).isStable());
        appendClose(series, 146.41);

        assertTrue(warmed.getValue(4).isStable());

        assertEquals(6, series.getEndIndex());
        assertStateEquals(rollingMeanState(new LogReturnIndicator(series)).getValue(5), warmed.getValue(5));
    }

    @Test
    public void revisionlessInteriorMutationDuringMomentReadsRetriesCoherentState() {
        AtomicBoolean inReadScope = new AtomicBoolean();
        BarSeries series = instrumentedSeries(true, new AtomicInteger(), inReadScope, 100, 110, 121, 133.1, 146.41,
                161.051);
        ReturnIndicator mutatingReturns = new ReturnIndicator() {
            private int targetReads;

            @Override
            public Num getValue(int index) {
                assertFalse("Return graph evaluated inside source read scope", inReadScope.get());
                // Count and shared mean consume the original prefix first.
                // Change an interior close when variance starts reading it.
                if (index == 4 && ++targetReads == 3) {
                    series.getBar(2).addPrice(series.numFactory().numOf(150));
                }
                if (index < 1) {
                    return NaN.NaN;
                }
                return series.getBar(index).getClosePrice().dividedBy(series.getBar(index - 1).getClosePrice()).log();
            }

            @Override
            public BarSeries getBarSeries() {
                return series;
            }

            @Override
            public ReturnRepresentation getReturnRepresentation() {
                return ReturnRepresentation.LOG;
            }

            @Override
            public int getCountOfUnstableBars() {
                return 1;
            }
        };

        ReturnForecastState result = rollingMeanState(mutatingReturns).getValue(4);

        assertNumEquals(150, series.getBar(2).getClosePrice());
        assertStateEquals(rollingMeanState(new LogReturnIndicator(series)).getValue(4), result);
    }

    @Test
    public void revisionAwareCachedReadsDoNotScanRetainedHistory() {
        assertEquals(cachedReadBarCount(6), cachedReadBarCount(40));
    }

    private int cachedReadBarCount(int barCount) {
        double[] closes = new double[barCount];
        for (int index = 0; index < barCount; index++) {
            closes[index] = 100 + index;
        }
        AtomicInteger barReads = new AtomicInteger();
        BarSeries series = instrumentedSeries(false, barReads, new AtomicBoolean(), closes);
        EwmaReturnForecastStateIndicator indicator = rollingMeanState(new LogReturnIndicator(series));
        indicator.getValue(barCount - 2);
        barReads.set(0);

        for (int read = 0; read < 3; read++) {
            indicator.getValue(barCount - 2);
        }
        return barReads.get();
    }

    private EwmaReturnForecastStateIndicator rollingMeanState(ReturnIndicator returns) {
        return new EwmaReturnForecastStateIndicator(returns, 2, 0.5,
                EwmaReturnForecastStateIndicator.DriftMode.ROLLING_MEAN);
    }

    private static void assertStateEquals(ReturnForecastState expected, ReturnForecastState actual) {
        assertEquals(expected.isStable(), actual.isStable());
        assertEquals(expected.observationCount(), actual.observationCount());
        assertNumEquals(expected.mean(), actual.mean());
        assertNumEquals(expected.drift(), actual.drift());
        assertNumEquals(expected.variance(), actual.variance());
    }

    private BarSeries revisionlessSeries(double... closes) {
        return instrumentedSeries(true, new AtomicInteger(), new AtomicBoolean(), closes);
    }

    private BarSeries instrumentedSeries(boolean revisionless, AtomicInteger barReads, AtomicBoolean inReadScope,
            double... closes) {
        BarSeries delegate = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(closes).build();
        // Adapt the public series contract only; both revision entry points must
        // report unsupported changes, even though the backing fixture tracks them.
        return (BarSeries) Proxy.newProxyInstance(BarSeries.class.getClassLoader(), new Class<?>[] { BarSeries.class },
                (proxy, method, arguments) -> {
                    if (revisionless && method.getName().equals("getBarHistoryRevision")) {
                        return -1L;
                    }
                    if (revisionless && method.getName().equals("getBarSeriesChangeSnapshot")) {
                        return new BarSeries.BarSeriesChangeSnapshot(-1L, -1, delegate.getRemovedBarsCount() - 1,
                                delegate.getMaximumBarCount(), delegate.getEndIndex());
                    }
                    if (method.getName().equals("getBar")) {
                        barReads.incrementAndGet();
                    }
                    boolean readScope = method.getName().equals("withReadLock");
                    boolean previousReadScope = inReadScope.get();
                    if (readScope) {
                        inReadScope.set(true);
                    }
                    try {
                        return method.invoke(delegate, arguments);
                    } catch (InvocationTargetException failure) {
                        throw failure.getCause();
                    } finally {
                        if (readScope) {
                            inReadScope.set(previousReadScope);
                        }
                    }
                });
    }

    private static void appendClose(BarSeries series, double close) {
        series.addBar(series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(series.getLastBar().getEndTime().plus(Duration.ofDays(1)))
                .openPrice(close)
                .highPrice(close)
                .lowPrice(close)
                .closePrice(close)
                .build());
    }

    private static final class MutatingReturnIndicator implements ReturnIndicator {

        private final LogReturnIndicator delegate;
        private final BarSeries series;
        private final int targetIndex;
        private final Num staleReturn;
        private final Num replacementClose;
        private int targetIndexReads;

        private MutatingReturnIndicator(BarSeries series, int targetIndex, Num replacementClose) {
            this.series = series;
            this.delegate = new LogReturnIndicator(series);
            this.targetIndex = targetIndex;
            this.staleReturn = delegate.getValue(targetIndex);
            this.replacementClose = replacementClose;
        }

        @Override
        public Num getValue(int index) {
            // Replace the published end bar only on the second read of the target
            // index, so the mean consumes the superseded close while the variance
            // (read third) consumes the replacement close. This is the torn state
            // the retry bracket must not publish.
            if (index == targetIndex && ++targetIndexReads == 2) {
                series.addPrice(replacementClose);
                return staleReturn;
            }
            return delegate.getValue(index);
        }

        @Override
        public BarSeries getBarSeries() {
            return series;
        }

        @Override
        public ReturnRepresentation getReturnRepresentation() {
            return ReturnRepresentation.LOG;
        }

        @Override
        public int getCountOfUnstableBars() {
            return delegate.getCountOfUnstableBars();
        }
    }

    private static final class FixedReturnIndicator extends FixedIndicator<Num> implements ReturnIndicator {

        private final ReturnRepresentation representation;

        private FixedReturnIndicator(BarSeries series, ReturnRepresentation representation, Num... values) {
            super(series, values);
            this.representation = representation;
        }

        @Override
        public ReturnRepresentation getReturnRepresentation() {
            return representation;
        }
    }
}

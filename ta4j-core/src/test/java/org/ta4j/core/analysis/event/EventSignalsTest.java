/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.stream.DoubleStream;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class EventSignalsTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public EventSignalsTest(NumFactory numFactory) {
        super(numFactory);
    }

    private BarSeries series(int barCount) {
        double[] prices = DoubleStream.iterate(1.0, d -> d + 1.0).limit(barCount).toArray();
        return new MockBarSeriesBuilder().withNumFactory(numFactory).withData(prices).build();
    }

    @Test
    public void indicatorAdapterTreatsOnlyTrueAsEvents() {
        BarSeries series = series(5);
        Boolean[] values = { null, Boolean.FALSE, Boolean.TRUE, Boolean.FALSE, Boolean.TRUE };
        Indicator<Boolean> indicator = new CachedIndicator<Boolean>(series) {
            @Override
            protected Boolean calculate(int index) {
                return values[index];
            }

            @Override
            public int getCountOfUnstableBars() {
                return 1;
            }
        };

        EventSignal signal = EventSignals.fromIndicator(indicator);
        assertSame(indicator.getBarSeries(), signal.getBarSeries());
        assertEquals(1, signal.getCountOfUnstableBars());
        assertFalse(signal.isEvent(0));
        assertFalse(signal.isEvent(1));
        assertTrue(signal.isEvent(2));
        assertFalse(signal.isEvent(3));
        assertTrue(signal.isEvent(4));
    }

    @Test
    public void predicateAdapterEvaluatesExplicitly() {
        BarSeries series = series(10);
        EventSignal signal = EventSignals.fromPredicate(series, 2, i -> i % 3 == 0);
        assertEquals(series, signal.getBarSeries());
        assertEquals(2, signal.getCountOfUnstableBars());
        assertTrue(signal.isEvent(3));
        assertTrue(signal.isEvent(6));
        assertFalse(signal.isEvent(4));
    }

    @Test
    public void unstableBoundaryIsRespectedBySupport() {
        BarSeries series = series(10);
        EventSynchronizationResult result = EventSynchronizationSupport.synchronize(
                EventSignals.fromPredicate(series, 6, i -> i == 5 || i == 7),
                EventSignals.fromPredicate(series, 0, i -> i == 7), 0, 9, 0, 0);
        assertEquals(6, result.effectiveStartIndex());
        assertEquals(1, result.predictedCount());
        assertEquals(1, result.matchedCount());
    }

    @Test
    public void invalidAdapterArgumentsAreRejected() {
        BarSeries series = series(5);
        assertThrows(NullPointerException.class, () -> EventSignals.fromIndicator(null));
        assertThrows(NullPointerException.class, () -> EventSignals.fromPredicate(series, 0, null));
        assertThrows(NullPointerException.class, () -> EventSignals.fromPredicate(null, 0, i -> false));
        assertThrows(IllegalArgumentException.class, () -> EventSignals.fromPredicate(series, -1, i -> false));
    }
}

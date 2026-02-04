/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.wyckoff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ta4j.core.num.NaN.NaN;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class WyckoffStructureTrackerTest extends AbstractIndicatorTest<BarSeries, Num> {

    private BarSeries series;

    public WyckoffStructureTrackerTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBar(series, 9.4, 10.0, 9.0, 9.6);
        addBar(series, 10.5, 11.2, 9.5, 11.0);
        addBar(series, 9.5, 10.0, 8.8, 9.0);
        addBar(series, 9.6, 10.4, 9.1, 9.7);
        addBar(series, 11.0, 11.6, 10.7, 11.5);
    }

    @Test
    public void shouldReturnEmptySnapshotForIndicesOutsideSeries() {
        var tracker = new WyckoffStructureTracker(series, 1, 1, 0, numOf(0.05));

        var beforeBegin = tracker.snapshot(series.getBeginIndex() - 1);
        assertThat(beforeBegin.rangeHigh().isNaN()).isTrue();
        assertThat(beforeBegin.rangeLow().isNaN()).isTrue();

        var afterEnd = tracker.snapshot(series.getEndIndex() + 1);
        assertThat(afterEnd.rangeHigh().isNaN()).isTrue();
        assertThat(afterEnd.rangeLow().isNaN()).isTrue();
    }

    @Test
    public void shouldTrackRangeAndBreakouts() {
        var tracker = new WyckoffStructureTracker(series, 1, 1, 0, numOf(0.05));

        var snapshot = tracker.snapshot(3);
        assertThat(snapshot.rangeHigh()).isEqualByComparingTo(numOf(11.2));
        assertThat(snapshot.rangeLow()).isEqualByComparingTo(numOf(8.8));
        assertThat(snapshot.inRange()).isTrue();
        assertThat(snapshot.brokeAboveRange()).isFalse();
        assertThat(snapshot.brokeBelowRange()).isFalse();

        var breakout = tracker.snapshot(4);
        assertThat(breakout.rangeHigh()).isEqualByComparingTo(numOf(11.2));
        assertThat(breakout.rangeLow()).isEqualByComparingTo(numOf(8.8));
        assertThat(breakout.brokeAboveRange()).isTrue();
        assertThat(breakout.brokeBelowRange()).isFalse();
        assertThat(breakout.inRange()).isFalse();
    }

    @Test
    public void shouldReturnEmptySnapshotWhenCloseIsNaN() {
        var nanSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        nanSeries.barBuilder().openPrice(1).highPrice(1.1).lowPrice(0.9).closePrice(NaN).volume(100).add();
        var tracker = new WyckoffStructureTracker(nanSeries, 1, 1, 0, numOf(0.05));

        var snapshot = tracker.snapshot(nanSeries.getEndIndex());
        assertThat(snapshot.rangeHigh().isNaN()).isTrue();
        assertThat(snapshot.rangeLow().isNaN()).isTrue();
        assertThat(snapshot.inRange()).isFalse();
    }

    @Test
    public void shouldDetectBreakdownWithToleranceAndPreserveRangeIndices() {
        var tracker = new WyckoffStructureTracker(series, 1, 1, 1, numOf(0.1));

        var initial = tracker.snapshot(4);
        assertThat(initial.rangeHighIndex()).isEqualTo(1);
        assertThat(initial.rangeLowIndex()).isEqualTo(2);

        series.barBuilder().openPrice(8.9).highPrice(9.1).lowPrice(8.2).closePrice(8.4).volume(120).add();

        var breakdown = tracker.snapshot(series.getEndIndex());
        assertThat(breakdown.rangeHigh()).isEqualByComparingTo(numOf(11.6));
        assertThat(breakdown.rangeLow()).isEqualByComparingTo(numOf(8.8));
        assertThat(breakdown.brokeBelowRange()).isTrue();
        assertThat(breakdown.inRange()).isFalse();
        assertThat(breakdown.rangeHighIndex()).isEqualTo(4);
        assertThat(breakdown.rangeLowIndex()).isEqualTo(initial.rangeLowIndex());
    }

    private void addBar(BarSeries target, double open, double high, double low, double close) {
        target.barBuilder().openPrice(open).highPrice(high).lowPrice(low).closePrice(close).volume(100).add();
    }
}

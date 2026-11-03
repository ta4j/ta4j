/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class TimeSegmentedVolumeIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries series;

    public TimeSegmentedVolumeIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @BeforeEach
    public void setUp() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        series.barBuilder().openPrice(10).closePrice(10).highPrice(10).lowPrice(10).volume(0).add();
        series.barBuilder().openPrice(9).closePrice(9).highPrice(9).lowPrice(9).volume(10).add();
        series.barBuilder().openPrice(10).closePrice(10).highPrice(10).lowPrice(10).volume(10).add();
        series.barBuilder().openPrice(11).closePrice(11).highPrice(11).lowPrice(11).volume(10).add();
        series.barBuilder().openPrice(12).closePrice(12).highPrice(12).lowPrice(12).volume(10).add();
        series.barBuilder().openPrice(11).closePrice(11).highPrice(11).lowPrice(11).volume(10).add();
        series.barBuilder().openPrice(11).closePrice(11).highPrice(11).lowPrice(11).volume(10).add();
    }

    @Test
    public void whenIndexIsLessThanBarCount_returnNaN() {
        var tsv = new TimeSegmentedVolumeIndicator(series, 5);

        assertTrue(tsv.getValue(0).isNaN());
        assertTrue(tsv.getValue(1).isNaN());
        assertTrue(tsv.getValue(2).isNaN());
        assertTrue(tsv.getValue(3).isNaN());
        assertTrue(tsv.getValue(4).isNaN());
        assertFalse(tsv.getValue(5).isNaN());
    }

    @Test
    public void whenBarCountIsOne_returnCorrectValue() {
        var tsv = new TimeSegmentedVolumeIndicator(series, 1);

        assertTrue(tsv.getValue(0).isNaN());
        assertNumEquals(-10, tsv.getValue(1));
        assertNumEquals(10, tsv.getValue(2));
        assertNumEquals(10, tsv.getValue(3));
        assertNumEquals(10, tsv.getValue(4));
        assertNumEquals(-10, tsv.getValue(5));
    }

    @Test
    public void whenBarCountIsThree_returnCorrectValue() {
        var tsv = new TimeSegmentedVolumeIndicator(series, 3);

        assertTrue(tsv.getValue(0).isNaN());
        assertTrue(tsv.getValue(1).isNaN());
        assertTrue(tsv.getValue(2).isNaN());
        assertNumEquals(10, tsv.getValue(3));
        assertNumEquals(30, tsv.getValue(4));
        assertNumEquals(10, tsv.getValue(5));
        assertNumEquals(0, tsv.getValue(6));
    }

    @Test
    public void recoversSmallContributionsWhenLargeContributionLeaves() {
        var bars = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        bars.barBuilder().openPrice(0).closePrice(0).highPrice(0).lowPrice(0).volume(0).add();
        for (int i = 1; i <= 10001; i++) {
            Number volume = i == 1 ? 1_000_000_000 : i == 10001 ? 10.1 : 0.0000004;
            bars.barBuilder().openPrice(i).closePrice(i).highPrice(i).lowPrice(i).volume(volume).add();
        }
        var rolling = new TimeSegmentedVolumeIndicator(bars, 10000);
        rolling.getValue(10000);

        assertNumEquals(10.1039996, rolling.getValue(10001));
    }
}

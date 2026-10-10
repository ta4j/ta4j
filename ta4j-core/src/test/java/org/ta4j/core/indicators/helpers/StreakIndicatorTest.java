/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class StreakIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries data;

    public StreakIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        data = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10, 11, 12, 11, 10, 9, 8, 9, 10, 10, 11, 12, 13)
                .build();
    }

    @Test
    public void streakUsingClosePrice() {
        var streak = new StreakIndicator(new ClosePriceIndicator(data));
        // First bar: NaN (unstable period)
        assertNumEquals(NaN.NaN, streak.getValue(0));
        // Up streak: 1, 2
        assertNumEquals(1, streak.getValue(1));
        assertNumEquals(2, streak.getValue(2));
        // Down streak: -1, -2, -3
        assertNumEquals(-1, streak.getValue(3));
        assertNumEquals(-2, streak.getValue(4));
        assertNumEquals(-3, streak.getValue(5));
        assertNumEquals(-4, streak.getValue(6));
        // Up streak: 1, 2, 3
        assertNumEquals(1, streak.getValue(7));
        assertNumEquals(2, streak.getValue(8));
        // No change: 0
        assertNumEquals(0, streak.getValue(9));
        // Up streak: 1, 2, 3
        assertNumEquals(1, streak.getValue(10));
        assertNumEquals(2, streak.getValue(11));
        assertNumEquals(3, streak.getValue(12));
    }

    @Test
    public void streakResetsOnDirectionChange() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 11, 12, 11, 10).build();
        var streak = new StreakIndicator(new ClosePriceIndicator(series));
        // First bar: NaN (unstable period)
        assertNumEquals(NaN.NaN, streak.getValue(0));
        // Up streak
        assertNumEquals(1, streak.getValue(1));
        assertNumEquals(2, streak.getValue(2));
        // Resets to -1 when direction changes
        assertNumEquals(-1, streak.getValue(3));
        assertNumEquals(-2, streak.getValue(4));
    }

    @Test
    public void streakHandlesNoChange() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 10, 10, 11, 11, 12).build();
        var streak = new StreakIndicator(new ClosePriceIndicator(series));
        // First bar: NaN (unstable period)
        assertNumEquals(NaN.NaN, streak.getValue(0));
        // No change: zeros
        assertNumEquals(0, streak.getValue(1));
        assertNumEquals(0, streak.getValue(2));
        // Up streak starts
        assertNumEquals(1, streak.getValue(3));
        // No change: resets to 0
        assertNumEquals(0, streak.getValue(4));
        // Up streak starts again
        assertNumEquals(1, streak.getValue(5));
    }

    @Test
    public void streakWithSingleBar() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10).build();
        var streak = new StreakIndicator(new ClosePriceIndicator(series));
        // First bar: NaN (unstable period)
        assertNumEquals(NaN.NaN, streak.getValue(0));
    }

    @Test
    public void streakWithTwoBars() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 11).build();
        var streak = new StreakIndicator(new ClosePriceIndicator(series));
        // First bar: NaN (unstable period)
        assertNumEquals(NaN.NaN, streak.getValue(0));
        assertNumEquals(1, streak.getValue(1));
    }

    @Test
    public void streakWithAlternatingValues() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 11, 10, 11, 10, 11).build();
        var streak = new StreakIndicator(new ClosePriceIndicator(series));
        // First bar: NaN (unstable period)
        assertNumEquals(NaN.NaN, streak.getValue(0));
        assertNumEquals(1, streak.getValue(1));
        assertNumEquals(-1, streak.getValue(2));
        assertNumEquals(1, streak.getValue(3));
        assertNumEquals(-1, streak.getValue(4));
        assertNumEquals(1, streak.getValue(5));
    }

    @Test
    public void streakWithLongUpStreak() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 11, 12, 13, 14, 15, 16).build();
        var streak = new StreakIndicator(new ClosePriceIndicator(series));
        // First bar: NaN (unstable period)
        assertNumEquals(NaN.NaN, streak.getValue(0));
        assertNumEquals(1, streak.getValue(1));
        assertNumEquals(2, streak.getValue(2));
        assertNumEquals(3, streak.getValue(3));
        assertNumEquals(4, streak.getValue(4));
        assertNumEquals(5, streak.getValue(5));
        assertNumEquals(6, streak.getValue(6));
    }

    @Test
    public void streakWithLongDownStreak() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(16, 15, 14, 13, 12, 11, 10).build();
        var streak = new StreakIndicator(new ClosePriceIndicator(series));
        // First bar: NaN (unstable period)
        assertNumEquals(NaN.NaN, streak.getValue(0));
        assertNumEquals(-1, streak.getValue(1));
        assertNumEquals(-2, streak.getValue(2));
        assertNumEquals(-3, streak.getValue(3));
        assertNumEquals(-4, streak.getValue(4));
        assertNumEquals(-5, streak.getValue(5));
        assertNumEquals(-6, streak.getValue(6));
    }

    @Test
    public void unstableBars() {
        var streak = new StreakIndicator(new ClosePriceIndicator(data));
        assertThat(streak.getCountOfUnstableBars()).isEqualTo(1);
    }

    @Test
    public void returnsNaNDuringUnstablePeriod() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20)
                .build();
        var streak = new StreakIndicator(new ClosePriceIndicator(series));

        int unstableBars = streak.getCountOfUnstableBars();
        assertThat(unstableBars).isEqualTo(1);

        // All indices before unstable period should return NaN
        for (int i = 0; i < unstableBars; i++) {
            assertThat(streak.getValue(i).isNaN()).isTrue();
        }
    }
}

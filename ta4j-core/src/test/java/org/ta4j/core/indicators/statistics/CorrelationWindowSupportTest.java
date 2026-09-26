/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.List;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class CorrelationWindowSupportTest {

    @Test
    public void unstableBarsClampsTwoSourceOverflow() {
        BarSeries series = new MockBarSeriesBuilder().withData(1).build();
        Indicator<Num> first = indicator(series, Integer.MAX_VALUE - 3);
        Indicator<Num> second = indicator(series, 0);

        int unstableBars = CorrelationWindowSupport.unstableBars(5, first, second);

        assertEquals(Integer.MAX_VALUE, unstableBars);
    }

    @Test
    public void unstableBarsClampsThreeSourceOverflow() {
        BarSeries series = new MockBarSeriesBuilder().withData(1).build();
        Indicator<Num> first = indicator(series, 0);
        Indicator<Num> second = indicator(series, Integer.MAX_VALUE - 3);
        Indicator<Num> third = indicator(series, 0);

        int unstableBars = CorrelationWindowSupport.unstableBars(5, first, second, third);

        assertEquals(Integer.MAX_VALUE, unstableBars);
    }

    @Test
    public void acceptsBarCountAtSharedCeiling() {
        assertEquals(10_000_000, CorrelationWindowSupport.validateBarCount(10_000_000));
    }

    @Test
    public void rejectsBarCountAboveSharedCeiling() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> CorrelationWindowSupport.validateBarCount(10_000_001));
        assertEquals("barCount exceeds the maximum window length", e.getMessage());
    }

    private Indicator<Num> indicator(BarSeries series, int unstableBars) {
        NumFactory numFactory = series.numFactory();
        return new MockIndicator(series, unstableBars, List.of(numFactory.one()));
    }
}

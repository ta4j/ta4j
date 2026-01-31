/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.ichimoku;

import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;

import java.util.stream.IntStream;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class IchimokuChikouSpanIndicatorTest extends AbstractIndicatorTest<BarSeries, Num> {

    public IchimokuChikouSpanIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    private BarSeries barSeries(int count) {
        return new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(IntStream.range(0, count).mapToDouble(Double::valueOf).boxed().collect(toList()))
                .build();
    }

    @Test
    public void testCalculateWithDefaultParam() {
        final BarSeries barSeries = barSeries(27);

        final var indicator = new IchimokuChikouSpanIndicator(barSeries);

        assertEquals(numOf(26), indicator.getValue(0));
        assertEquals(NaN.NaN, indicator.getValue(1));
        assertEquals(NaN.NaN, indicator.getValue(2));
        assertEquals(NaN.NaN, indicator.getValue(3));
        assertEquals(NaN.NaN, indicator.getValue(4));
        assertEquals(NaN.NaN, indicator.getValue(5));
        assertEquals(NaN.NaN, indicator.getValue(6));
        assertEquals(NaN.NaN, indicator.getValue(7));
        assertEquals(NaN.NaN, indicator.getValue(8));
        assertEquals(NaN.NaN, indicator.getValue(9));
        assertEquals(NaN.NaN, indicator.getValue(10));
        assertEquals(NaN.NaN, indicator.getValue(11));
        assertEquals(NaN.NaN, indicator.getValue(12));
        assertEquals(NaN.NaN, indicator.getValue(13));
        assertEquals(NaN.NaN, indicator.getValue(14));
        assertEquals(NaN.NaN, indicator.getValue(15));
        assertEquals(NaN.NaN, indicator.getValue(16));
        assertEquals(NaN.NaN, indicator.getValue(17));
        assertEquals(NaN.NaN, indicator.getValue(18));
        assertEquals(NaN.NaN, indicator.getValue(19));
        assertEquals(NaN.NaN, indicator.getValue(20));
        assertEquals(NaN.NaN, indicator.getValue(21));
        assertEquals(NaN.NaN, indicator.getValue(22));
        assertEquals(NaN.NaN, indicator.getValue(23));
        assertEquals(NaN.NaN, indicator.getValue(24));
        assertEquals(NaN.NaN, indicator.getValue(25));
        assertEquals(NaN.NaN, indicator.getValue(26));
    }

    @Test
    public void testCalculateWithSpecifiedValue() {
        final BarSeries barSeries = barSeries(11);

        final var indicator = new IchimokuChikouSpanIndicator(barSeries, 3);

        assertEquals(numOf(3), indicator.getValue(0));
        assertEquals(numOf(4), indicator.getValue(1));
        assertEquals(numOf(5), indicator.getValue(2));
        assertEquals(numOf(6), indicator.getValue(3));
        assertEquals(numOf(7), indicator.getValue(4));
        assertEquals(numOf(8), indicator.getValue(5));
        assertEquals(numOf(9), indicator.getValue(6));
        assertEquals(numOf(10), indicator.getValue(7));
        assertEquals(NaN.NaN, indicator.getValue(8));
        assertEquals(NaN.NaN, indicator.getValue(9));
        assertEquals(NaN.NaN, indicator.getValue(10));
    }

}

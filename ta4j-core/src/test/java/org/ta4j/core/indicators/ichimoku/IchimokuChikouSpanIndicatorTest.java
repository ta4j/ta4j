package org.ta4j.core.indicators.ichimoku;

import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BaseTimeSeries;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;

public class IchimokuChikouSpanIndicatorTest extends AbstractIndicatorTest<TimeSeries, Num> {

    public IchimokuChikouSpanIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    private Bar bar(int i) {
        return new MockBar(i, this::numOf);
    }

    private TimeSeries timeSeries(int count) {
        final List<Bar> bars = IntStream.range(0, count).boxed().map(this::bar).collect(toList());
        return new BaseTimeSeries(bars);
    }

    @Test
    public void testCalculateWithDefaultParam() {
        final TimeSeries timeSeries = timeSeries(27);

        final IchimokuChikouSpanIndicator indicator = new IchimokuChikouSpanIndicator(timeSeries);

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
        final TimeSeries timeSeries = timeSeries(11);

        final IchimokuChikouSpanIndicator indicator = new IchimokuChikouSpanIndicator(timeSeries, 3);

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
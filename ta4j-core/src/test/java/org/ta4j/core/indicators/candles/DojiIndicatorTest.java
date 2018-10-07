package org.ta4j.core.indicators.candles;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.mocks.MockTimeSeries;
import org.ta4j.core.num.Num;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DojiIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    private TimeSeries series;

    public DojiIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        List<Bar> bars = new ArrayList<Bar>();
        // open, close, high, low
        bars.add(new MockBar(19, 19, 22, 16,numFunction));
        bars.add(new MockBar(10, 18, 20, 10,numFunction));
        bars.add(new MockBar(17, 20, 21, 17,numFunction));
        bars.add(new MockBar(15, 15.1, 16, 14,numFunction));
        bars.add(new MockBar(15, 11, 15, 8,numFunction));
        bars.add(new MockBar(11, 12, 12, 10,numFunction));
        series = new MockTimeSeries(bars);
    }

    @Test
    public void getValueAtIndex0() {
        DojiIndicator doji = new DojiIndicator(new MockTimeSeries(numFunction, 0d), 10, 0.03);
        assertTrue(doji.getValue(0));

        doji = new DojiIndicator(new MockTimeSeries(numFunction, 1d), 10, 0.03);
        assertFalse(doji.getValue(0));
    }

    @Test
    public void getValue() {
        DojiIndicator doji = new DojiIndicator(series, 3, 0.1);
        assertTrue(doji.getValue(0));
        assertFalse(doji.getValue(1));
        assertFalse(doji.getValue(2));
        assertTrue(doji.getValue(3));
        assertFalse(doji.getValue(4));
        assertFalse(doji.getValue(5));
    }
}

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

public class ThreeWhiteSoldiersIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    private TimeSeries series;

    public ThreeWhiteSoldiersIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        List<Bar> bars = new ArrayList<Bar>();
        // open, close, high, low
        bars.add(new MockBar(19, 19, 22, 15,numFunction));
        bars.add(new MockBar(10, 18, 20, 8,numFunction));
        bars.add(new MockBar(17, 16, 21, 15,numFunction));
        bars.add(new MockBar(15.6, 18, 18.1, 14,numFunction));
        bars.add(new MockBar(16, 19.9, 20, 15,numFunction));
        bars.add(new MockBar(16.8, 23, 23, 16.7,numFunction));
        bars.add(new MockBar(17, 25, 25, 17,numFunction));
        bars.add(new MockBar(23, 16.8, 24, 15,numFunction));
        series = new MockTimeSeries(bars);
    }

    @Test
    public void getValue() {
        ThreeWhiteSoldiersIndicator tws = new ThreeWhiteSoldiersIndicator(series, 3, series.numOf(0.1));
        assertFalse(tws.getValue(0));
        assertFalse(tws.getValue(1));
        assertFalse(tws.getValue(2));
        assertFalse(tws.getValue(3));
        assertFalse(tws.getValue(4));
        assertTrue(tws.getValue(5));
        assertFalse(tws.getValue(6));
        assertFalse(tws.getValue(7));
    }
}

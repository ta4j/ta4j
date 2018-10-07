package org.ta4j.core.indicators.candles;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.mocks.MockTimeSeries;
import org.ta4j.core.num.Num;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.ta4j.core.TestUtils.assertNumEquals;

public class RealBodyIndicatorTest extends AbstractIndicatorTest<TimeSeries,Num>{

    private TimeSeries series;

    public RealBodyIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        List<Bar> bars = new ArrayList<Bar>();
        // open, close, high, low
        bars.add(new MockBar(10, 18, 20, 10,numFunction));
        bars.add(new MockBar(17, 20, 21, 17,numFunction));
        bars.add(new MockBar(15, 15, 16, 14,numFunction));
        bars.add(new MockBar(15, 11, 15, 8, numFunction));
        bars.add(new MockBar(11, 12, 12, 10,numFunction));
        series = new MockTimeSeries(bars);
    }

    @Test
    public void getValue() {
        RealBodyIndicator body = new RealBodyIndicator(series);
        assertNumEquals(8, body.getValue(0));
        assertNumEquals(3, body.getValue(1));
        assertNumEquals(0, body.getValue(2));
        assertNumEquals(-4, body.getValue(3));
        assertNumEquals(1, body.getValue(4));
    }
}

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

public class BullishEngulfingIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private TimeSeries series;

    public BullishEngulfingIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        List<Bar> bars = new ArrayList<Bar>();
        // open, close, high, low
        bars.add(new MockBar(10, 18, 20, 10,numFunction));
        bars.add(new MockBar(17, 16, 19, 15,numFunction));
        bars.add(new MockBar(15, 18, 19, 14,numFunction));
        bars.add(new MockBar(15, 11, 15, 8,numFunction));
        bars.add(new MockBar(11, 12, 12, 10,numFunction));
        series = new MockTimeSeries(bars);
    }

    @Test
    public void getValue() {
        BullishEngulfingIndicator bep = new BullishEngulfingIndicator(series);
        assertFalse(bep.getValue(0));
        assertFalse(bep.getValue(1));
        assertTrue(bep.getValue(2));
        assertFalse(bep.getValue(3));
        assertFalse(bep.getValue(4));
    }
}

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

public class BullishHaramiIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private TimeSeries series;

    public BullishHaramiIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        List<Bar> bars = new ArrayList<Bar>();
        // open, close, high, low
        bars.add(new MockBar(10, 18, 20, 10,numFunction));
        bars.add(new MockBar(21, 15, 22, 14,numFunction));
        bars.add(new MockBar(17, 20, 21, 17,numFunction));
        bars.add(new MockBar(15, 11, 15, 8,numFunction));
        bars.add(new MockBar(11, 12, 12, 10,numFunction));
        series = new MockTimeSeries(bars);
    }

    @Test
    public void getValue() {
        BullishHaramiIndicator bhp = new BullishHaramiIndicator(series);
        assertFalse(bhp.getValue(0));
        assertFalse(bhp.getValue(1));
        assertTrue(bhp.getValue(2));
        assertFalse(bhp.getValue(3));
        assertFalse(bhp.getValue(4));
    }
}

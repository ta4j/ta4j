package org.ta4j.core.indicators.helpers;

import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.mocks.MockTimeSeries;
import org.ta4j.core.num.Num;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.ta4j.core.TestUtils.assertNumEquals;

public class PlusDMIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num>{

    public PlusDMIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Test
    public void zeroDirectionalMovement()
    {
        MockBar yesterdayBar = new MockBar(0, 0, 10, 2,numFunction);
        MockBar todayBar = new MockBar(0, 0, 6, 6,numFunction);
        List<Bar> bars = new ArrayList<Bar>();
        bars.add(yesterdayBar);
        bars.add(todayBar);
        MockTimeSeries series = new MockTimeSeries(bars);
        PlusDMIndicator dup = new PlusDMIndicator(series);
        assertNumEquals(0, dup.getValue(1));
    }

    @Test
    public void zeroDirectionalMovement2() {
        MockBar yesterdayBar = new MockBar(0, 0, 6, 12,numFunction);
        MockBar todayBar = new MockBar(0, 0, 12, 6,numFunction);
        List<Bar> bars = new ArrayList<Bar>();
        bars.add(yesterdayBar);
        bars.add(todayBar);
        MockTimeSeries series = new MockTimeSeries(bars);
        PlusDMIndicator dup = new PlusDMIndicator(series);
        assertNumEquals(0, dup.getValue(1));
    }

    @Test
    public void zeroDirectionalMovement3() {
        MockBar yesterdayBar = new MockBar(0, 0, 6, 20,numFunction);
        MockBar todayBar = new MockBar(0, 0, 12, 4,numFunction);
        List<Bar> bars = new ArrayList<Bar>();
        bars.add(yesterdayBar);
        bars.add(todayBar);
        MockTimeSeries series = new MockTimeSeries(bars);
        PlusDMIndicator dup = new PlusDMIndicator(series);
        assertNumEquals(0, dup.getValue(1));
    }

    @Test
    public void positiveDirectionalMovement() {
        MockBar yesterdayBar = new MockBar(0, 0, 6, 6,numFunction);
        MockBar todayBar = new MockBar(0, 0, 12, 4,numFunction);
        List<Bar> bars = new ArrayList<Bar>();
        bars.add(yesterdayBar);
        bars.add(todayBar);
        MockTimeSeries series = new MockTimeSeries(bars);
        PlusDMIndicator dup = new PlusDMIndicator(series);
        assertNumEquals(6, dup.getValue(1));
    }
}

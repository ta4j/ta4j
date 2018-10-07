package org.ta4j.core.indicators;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.mocks.MockTimeSeries;
import org.ta4j.core.num.Num;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.ta4j.core.TestUtils.assertNumEquals;

public class AccelerationDecelerationIndicatorTest extends AbstractIndicatorTest<Indicator<Num>,Num>{

    private TimeSeries series;

    public AccelerationDecelerationIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {

        List<Bar> bars = new ArrayList<Bar>();

        bars.add(new MockBar(0, 0, 16, 8,numFunction));
        bars.add(new MockBar(0, 0, 12, 6,numFunction));
        bars.add(new MockBar(0, 0, 18, 14,numFunction));
        bars.add(new MockBar(0, 0, 10, 6,numFunction));
        bars.add(new MockBar(0, 0, 8, 4,numFunction));

        series = new MockTimeSeries(bars);
    }

    @Test
    public void calculateWithSma2AndSma3() {
        AccelerationDecelerationIndicator acceleration = new AccelerationDecelerationIndicator(series, 2, 3);

        assertNumEquals(0, acceleration.getValue(0));
        assertNumEquals(0, acceleration.getValue(1));
        assertNumEquals(0.08333333333, acceleration.getValue(2));
        assertNumEquals(0.41666666666, acceleration.getValue(3));
        assertNumEquals(-2, acceleration.getValue(4));
    }

    @Test
    public void withSma1AndSma2() {
        AccelerationDecelerationIndicator acceleration = new AccelerationDecelerationIndicator(series, 1, 2);

        assertNumEquals(0, acceleration.getValue(0));
        assertNumEquals(0, acceleration.getValue(1));
        assertNumEquals(0, acceleration.getValue(2));
        assertNumEquals(0, acceleration.getValue(3));
        assertNumEquals(0, acceleration.getValue(4));
    }

    @Test
    public void withSmaDefault() {
        AccelerationDecelerationIndicator acceleration = new AccelerationDecelerationIndicator(series);

        assertNumEquals(0, acceleration.getValue(0));
        assertNumEquals(0, acceleration.getValue(1));
        assertNumEquals(0, acceleration.getValue(2));
        assertNumEquals(0, acceleration.getValue(3));
        assertNumEquals(0, acceleration.getValue(4));
    }
}

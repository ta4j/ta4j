package org.ta4j.core.indicators.volume;

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

import static org.ta4j.core.TestUtils.assertNumEquals;

public class ROCVIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

	TimeSeries series;

    public ROCVIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
    		List<Bar> bars = new ArrayList<Bar>();
        bars.add(new MockBar(1355.69, 1000,numFunction));
        bars.add(new MockBar(1325.51, 3000,numFunction));
        bars.add(new MockBar(1335.02, 3500,numFunction));
        bars.add(new MockBar(1313.72, 2200,numFunction));
        bars.add(new MockBar(1319.99, 2300,numFunction));
        bars.add(new MockBar(1331.85, 200,numFunction));
        bars.add(new MockBar(1329.04, 2700,numFunction));
        bars.add(new MockBar(1362.16, 5000,numFunction));
        bars.add(new MockBar(1365.51, 1000,numFunction));
        bars.add(new MockBar(1374.02, 2500,numFunction));
        series = new MockTimeSeries(bars);
    }

    @Test
    public void test() {
        ROCVIndicator roc = new ROCVIndicator(series, 3);

        assertNumEquals(0, roc.getValue(0));
        assertNumEquals(200, roc.getValue(1));
        assertNumEquals(250, roc.getValue(2));
        assertNumEquals(120, roc.getValue(3));
        assertNumEquals(-23.333333333333332, roc.getValue(4));
        assertNumEquals(-94.28571428571429, roc.getValue(5));
        assertNumEquals(22.727272727272727, roc.getValue(6));
        assertNumEquals(117.3913043478261, roc.getValue(7));
        assertNumEquals(400, roc.getValue(8));
        assertNumEquals(-7.407407407407407, roc.getValue(9));
    }
}

package org.ta4j.core.indicators.helpers;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BaseTimeSeries;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.num.Num;

import static org.ta4j.core.TestUtils.assertNumEquals;

public class ConstantIndicatorTest {
    private ConstantIndicator<Num> constantIndicator;

    @Before
    public void setUp() {
        TimeSeries series = new BaseTimeSeries();
        constantIndicator = new ConstantIndicator<Num>(series, series.numOf(30.33));
    }

    @Test
    public void constantIndicator() {
        assertNumEquals("30.33", constantIndicator.getValue(0));
        assertNumEquals("30.33", constantIndicator.getValue(1));
        assertNumEquals("30.33", constantIndicator.getValue(10));
        assertNumEquals("30.33", constantIndicator.getValue(30));
    }
}

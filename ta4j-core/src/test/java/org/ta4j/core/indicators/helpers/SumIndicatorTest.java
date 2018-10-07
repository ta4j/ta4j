package org.ta4j.core.indicators.helpers;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BaseTimeSeries;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.num.Num;

import static org.ta4j.core.TestUtils.assertNumEquals;

public class SumIndicatorTest {

    private SumIndicator sumIndicator;
    
    @Before
    public void setUp() {
        TimeSeries series = new BaseTimeSeries();
        ConstantIndicator<Num> constantIndicator = new ConstantIndicator<>(series, series.numOf(6));
        FixedIndicator<Num> mockIndicator = new FixedIndicator<Num>(series,
                series.numOf(-2.0),
                series.numOf(0.00),
                series.numOf(1.00),
                series.numOf(2.53),
                series.numOf(5.87),
                series.numOf(6.00),
                series.numOf(10.0)
        );
        FixedIndicator<Num> mockIndicator2 = new FixedIndicator<Num>(series,
                series.numOf(0),
                series.numOf(1),
                series.numOf(2),
                series.numOf(3),
                series.numOf(10),
                series.numOf(-42),
                series.numOf(-1337)
        );
        sumIndicator = new SumIndicator(constantIndicator, mockIndicator, mockIndicator2);
    }

    @Test
    public void getValue() {
        assertNumEquals("4.0", sumIndicator.getValue(0));
        assertNumEquals("7.0", sumIndicator.getValue(1));
        assertNumEquals("9.0", sumIndicator.getValue(2));
        assertNumEquals("11.53", sumIndicator.getValue(3));
        assertNumEquals("21.87", sumIndicator.getValue(4));
        assertNumEquals("-30.0", sumIndicator.getValue(5));
        assertNumEquals("-1321.0", sumIndicator.getValue(6));
    }
}

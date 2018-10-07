package org.ta4j.core.indicators.helpers;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BaseTimeSeries;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.num.PrecisionNum;
import org.ta4j.core.num.Num;

import java.util.function.Function;

import static org.ta4j.core.TestUtils.assertNumEquals;

public class DifferenceIndicatorTest {

    private DifferenceIndicator differenceIndicator;
    
    @Before
    public void setUp() {
        Function<Number, Num> numFunction = PrecisionNum::valueOf;

        TimeSeries series = new BaseTimeSeries();
        FixedIndicator<Num> mockIndicator = new FixedIndicator<Num>(series,
                numFunction.apply(-2.0),
                numFunction.apply(0.00),
                numFunction.apply(1.00),
                numFunction.apply(2.53),
                numFunction.apply(5.87),
                numFunction.apply(6.00),
                numFunction.apply(10.0)
        );
        ConstantIndicator<Num> constantIndicator = new ConstantIndicator<Num>(series, numFunction.apply(6));
        differenceIndicator = new DifferenceIndicator(constantIndicator, mockIndicator);
    }

    @Test
    public void getValue() {
        assertNumEquals("8", differenceIndicator.getValue(0));
        assertNumEquals("6", differenceIndicator.getValue(1));
        assertNumEquals("5", differenceIndicator.getValue(2));
        assertNumEquals("3.47", differenceIndicator.getValue(3));
        assertNumEquals("0.13", differenceIndicator.getValue(4));
        assertNumEquals("0", differenceIndicator.getValue(5));
        assertNumEquals("-4", differenceIndicator.getValue(6));
    }
}

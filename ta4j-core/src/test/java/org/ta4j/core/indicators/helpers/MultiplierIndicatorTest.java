package org.ta4j.core.indicators.helpers;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BaseTimeSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.num.Num;

import java.util.function.Function;

import static org.ta4j.core.TestUtils.assertNumEquals;

public class MultiplierIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private MultiplierIndicator multiplierIndicator;

    public MultiplierIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        TimeSeries series = new BaseTimeSeries.SeriesBuilder().withNumTypeOf(numFunction).build();
        ConstantIndicator<Num> constantIndicator = new ConstantIndicator<Num>(series, numFunction.apply(6));
        multiplierIndicator = new MultiplierIndicator(constantIndicator, 0.75);
    }

    @Test
    public void constantIndicator() {
        assertNumEquals("4.5", multiplierIndicator.getValue(10));
        assertNumEquals("4.5", multiplierIndicator.getValue(1));
        assertNumEquals("4.5", multiplierIndicator.getValue(0));
        assertNumEquals("4.5", multiplierIndicator.getValue(30));
    }
}

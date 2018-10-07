package org.ta4j.core.indicators.helpers;

import org.junit.Test;
import org.ta4j.core.BaseTimeSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.num.Num;

import java.util.function.Function;

import static org.ta4j.core.TestUtils.assertNumEquals;

public class AbsoluteIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public AbsoluteIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Test
    public void constantIndicators() {
        TimeSeries series = new BaseTimeSeries();
        AbsoluteIndicator positiveInd = new AbsoluteIndicator(new ConstantIndicator<Num>(series, numFunction.apply(1337)));
        AbsoluteIndicator zeroInd = new AbsoluteIndicator(new ConstantIndicator<Num>(series, numFunction.apply(0)));
        AbsoluteIndicator negativeInd = new AbsoluteIndicator(new ConstantIndicator<Num>(series, numFunction.apply(-42.42)));
        for (int i = 0; i < 10; i++) {
            assertNumEquals(1337, positiveInd.getValue(i));
            assertNumEquals(0, zeroInd.getValue(i));
            assertNumEquals(42.42, negativeInd.getValue(i));
        }
    }
}

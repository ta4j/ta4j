package org.ta4j.core.indicators;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeries;
import org.ta4j.core.num.Num;

import java.util.function.Function;

import static org.ta4j.core.TestUtils.assertNumEquals;

public class MACDIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public MACDIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    private BarSeries data;

    @Before
    public void setUp() {
        data = new MockBarSeries(numFunction, 37.08, 36.7, 36.11, 35.85, 35.71, 36.04, 36.41, 37.67, 38.01, 37.79,
                36.83, 37.10, 38.01, 38.50, 38.99);
    }

    @Test(expected = IllegalArgumentException.class)
    public void throwsErrorOnIllegalArguments() {
        new MACDIndicator(new ClosePriceIndicator(data), 10, 5);
    }


    @Test
    public void macdUsingPeriod5And10() {
        MACDIndicator macdIndicator = new MACDIndicator(new ClosePriceIndicator(data), 5, 10);
        assertNumEquals(0.0, macdIndicator.getValue(0));
        assertNumEquals(-0.05757, macdIndicator.getValue(1));
        assertNumEquals(-0.17488, macdIndicator.getValue(2));
        assertNumEquals(-0.26766, macdIndicator.getValue(3));
        assertNumEquals(-0.32326, macdIndicator.getValue(4));
        assertNumEquals(-0.28399, macdIndicator.getValue(5));
        assertNumEquals(-0.18930, macdIndicator.getValue(6));
        assertNumEquals(0.06472, macdIndicator.getValue(7));
        assertNumEquals(0.25087, macdIndicator.getValue(8));
        assertNumEquals(0.30387, macdIndicator.getValue(9));
        assertNumEquals(0.16891, macdIndicator.getValue(10));

        assertNumEquals(36.4098, macdIndicator.getLongTermEma().getValue(5));
        assertNumEquals(36.1258, macdIndicator.getShortTermEma().getValue(5));

        assertNumEquals(37.0118, macdIndicator.getLongTermEma().getValue(10));
        assertNumEquals(37.1807, macdIndicator.getShortTermEma().getValue(10));
    }
}
package org.ta4j.core.indicators;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockTimeSeries;
import org.ta4j.core.num.Num;

import java.util.function.Function;

import static org.ta4j.core.TestUtils.assertNumEquals;


public class LWMAIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {


    private TimeSeries data;

    public LWMAIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        data = new MockTimeSeries(numFunction,
                37.08,36.7,36.11,35.85,35.71,36.04,36.41,37.67,38.01,37.79,36.83
        );
    }

    @Test
    public void lwmaUsingBarCount5UsingClosePrice() {
        LWMAIndicator lwma = new LWMAIndicator(new ClosePriceIndicator(data), 5);
        assertNumEquals(0.0, lwma.getValue(0));
        assertNumEquals(0.0, lwma.getValue(1));
        assertNumEquals(0.0, lwma.getValue(2));
        assertNumEquals(0.0, lwma.getValue(3));
        assertNumEquals(36.0506, lwma.getValue(4));
        assertNumEquals(35.9673, lwma.getValue(5));
        assertNumEquals(36.0766, lwma.getValue(6));
        assertNumEquals(36.6253, lwma.getValue(7));
        assertNumEquals(37.1833, lwma.getValue(8));
        assertNumEquals(37.5240, lwma.getValue(9));
        assertNumEquals(37.4060, lwma.getValue(10));
    }
}

package org.ta4j.core.indicators.statistics;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockTimeSeries;
import org.ta4j.core.num.Num;

import java.util.function.Function;

import static org.ta4j.core.TestUtils.assertNumEquals;

public class StandardErrorIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    private TimeSeries data;

    public StandardErrorIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        data = new MockTimeSeries(numFunction, 10, 20, 30, 40, 50, 40, 40, 50, 40, 30, 20, 10);
    }

    @Test
    public void usingBarCount5UsingClosePrice() {
        StandardErrorIndicator se = new StandardErrorIndicator(new ClosePriceIndicator(data), 5);

        assertNumEquals(0, se.getValue(0));
        assertNumEquals(3.5355, se.getValue(1));
        assertNumEquals(4.714, se.getValue(2));
        assertNumEquals(5.5902, se.getValue(3));
        assertNumEquals(6.3246, se.getValue(4));
        assertNumEquals(4.5607, se.getValue(5));
        assertNumEquals(2.8284, se.getValue(6));
        assertNumEquals(2.1909, se.getValue(7));
        assertNumEquals(2.1909, se.getValue(8));
        assertNumEquals(2.8284, se.getValue(9));
        assertNumEquals(4.5607, se.getValue(10));
        assertNumEquals(6.3246, se.getValue(11));
    }

    @Test
    public void shouldBeZeroWhenBarCountIs1() {
        StandardErrorIndicator se = new StandardErrorIndicator(new ClosePriceIndicator(data), 1);
        assertNumEquals(0, se.getValue(1));
        assertNumEquals(0, se.getValue(3));
    }
}

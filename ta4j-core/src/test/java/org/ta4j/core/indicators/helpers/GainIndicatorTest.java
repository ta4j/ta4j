package org.ta4j.core.indicators.helpers;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockTimeSeries;
import org.ta4j.core.num.Num;

import java.util.function.Function;

import static org.ta4j.core.TestUtils.assertNumEquals;

public class GainIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private TimeSeries data;

    public GainIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        data = new MockTimeSeries(numFunction, 1, 2, 3, 4, 3, 4, 7, 4, 3, 3, 5, 3, 2);
    }

    @Test
    public void gainUsingClosePrice() {
        GainIndicator gain = new GainIndicator(new ClosePriceIndicator(data));
        assertNumEquals(0, gain.getValue(0));
        assertNumEquals(1, gain.getValue(1));
        assertNumEquals(1, gain.getValue(2));
        assertNumEquals(1, gain.getValue(3));
        assertNumEquals(0, gain.getValue(4));
        assertNumEquals(1, gain.getValue(5));
        assertNumEquals(3, gain.getValue(6));
        assertNumEquals(0, gain.getValue(7));
        assertNumEquals(0, gain.getValue(8));
        assertNumEquals(0, gain.getValue(9));
        assertNumEquals(2, gain.getValue(10));
        assertNumEquals(0, gain.getValue(11));
        assertNumEquals(0, gain.getValue(12));
    }
}

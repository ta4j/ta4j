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

public class DoubleEMAIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private ClosePriceIndicator closePrice;

    public DoubleEMAIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        TimeSeries data = new MockTimeSeries(numFunction,
                0.73, 0.72, 0.86, 0.72, 0.62,
                0.76, 0.84, 0.69, 0.65, 0.71,
                0.53, 0.73, 0.77, 0.67, 0.68
        );
        closePrice = new ClosePriceIndicator(data);
    }

    @Test
    public void doubleEMAUsingBarCount5UsingClosePrice() {
        DoubleEMAIndicator doubleEma = new DoubleEMAIndicator(closePrice, 5);

        assertNumEquals(0.73, doubleEma.getValue(0));
        assertNumEquals(0.7244, doubleEma.getValue(1));
        assertNumEquals(0.7992, doubleEma.getValue(2));

        assertNumEquals(0.7858, doubleEma.getValue(6));
        assertNumEquals(0.7374, doubleEma.getValue(7));
        assertNumEquals(0.6884, doubleEma.getValue(8));

        assertNumEquals(0.7184, doubleEma.getValue(12));
        assertNumEquals(0.6939, doubleEma.getValue(13));
        assertNumEquals(0.6859, doubleEma.getValue(14));
    }
}

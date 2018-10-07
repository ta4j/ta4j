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

public class TripleEMAIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num>{

    private ClosePriceIndicator closePrice;

    public TripleEMAIndicatorTest(Function<Number, Num> numFunction) {
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
    public void tripleEMAUsingBarCount5UsingClosePrice() {
        TripleEMAIndicator tripleEma = new TripleEMAIndicator(closePrice, 5);

        assertNumEquals(0.73, tripleEma.getValue(0));
        assertNumEquals(0.7229, tripleEma.getValue(1));
        assertNumEquals(0.8185, tripleEma.getValue(2));

        assertNumEquals(0.8027, tripleEma.getValue(6));
        assertNumEquals(0.7328, tripleEma.getValue(7));
        assertNumEquals(0.6725, tripleEma.getValue(8));

        assertNumEquals(0.7386, tripleEma.getValue(12));
        assertNumEquals(0.6994, tripleEma.getValue(13));
        assertNumEquals(0.6876, tripleEma.getValue(14));
    }
}

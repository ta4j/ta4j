package org.ta4j.core.indicators.helpers;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockTimeSeries;
import org.ta4j.core.num.Num;

import java.util.function.Function;

import static junit.framework.TestCase.assertEquals;

public class ClosePriceIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    private ClosePriceIndicator closePrice;

    TimeSeries timeSeries;

    public ClosePriceIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        timeSeries = new MockTimeSeries(numFunction);
        closePrice = new ClosePriceIndicator(timeSeries);
    }

    @Test
    public void indicatorShouldRetrieveBarClosePrice() {
        for (int i = 0; i < 10; i++) {
            assertEquals(closePrice.getValue(i), timeSeries.getBar(i).getClosePrice());
        }
    }
}

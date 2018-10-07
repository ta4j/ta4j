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

public class HighPriceIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    private HighPriceIndicator highPriceIndicator;

    TimeSeries timeSeries;

    public HighPriceIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        timeSeries = new MockTimeSeries(numFunction);
        highPriceIndicator = new HighPriceIndicator(timeSeries);

    }

    @Test
    public void indicatorShouldRetrieveBarMaxPrice() {
        for (int i = 0; i < 10; i++) {
            assertEquals(highPriceIndicator.getValue(i), timeSeries.getBar(i).getHighPrice());
        }
    }
}

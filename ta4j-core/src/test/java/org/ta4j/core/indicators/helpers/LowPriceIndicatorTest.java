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

public class LowPriceIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    private LowPriceIndicator lowPriceIndicator;

    TimeSeries timeSeries;

    public LowPriceIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        timeSeries = new MockTimeSeries(numFunction);
        lowPriceIndicator = new LowPriceIndicator(timeSeries);
    }

    @Test
    public void indicatorShouldRetrieveBarMinPrice() {
        for (int i = 0; i < 10; i++) {
            assertEquals(lowPriceIndicator.getValue(i), timeSeries.getBar(i).getLowPrice());
        }
    }
}

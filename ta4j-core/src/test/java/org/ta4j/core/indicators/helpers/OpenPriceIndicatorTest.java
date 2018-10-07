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

public class OpenPriceIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    private OpenPriceIndicator openPriceIndicator;

    TimeSeries timeSeries;

    public OpenPriceIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        timeSeries = new MockTimeSeries(numFunction);
        openPriceIndicator = new OpenPriceIndicator(timeSeries);
    }

    @Test
    public void indicatorShouldRetrieveBarOpenPrice() {
        for (int i = 0; i < 10; i++) {
            assertEquals(openPriceIndicator.getValue(i), timeSeries.getBar(i).getOpenPrice());
        }
    }
}

package org.ta4j.core.indicators.helpers;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockTimeSeries;
import org.ta4j.core.num.Num;

import java.util.function.Function;

import static junit.framework.TestCase.assertEquals;

public class TypicalPriceIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private TypicalPriceIndicator typicalPriceIndicator;

    TimeSeries timeSeries;

    public TypicalPriceIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        timeSeries = new MockTimeSeries(numFunction);
        typicalPriceIndicator = new TypicalPriceIndicator(timeSeries);
    }

    @Test
    public void indicatorShouldRetrieveBarMaxPrice() {
        for (int i = 0; i < 10; i++) {
            Bar bar = timeSeries.getBar(i);
            Num typicalPrice = bar.getHighPrice().plus(bar.getLowPrice()).plus(bar.getClosePrice())
                    .dividedBy(timeSeries.numOf(3));
            assertEquals(typicalPrice, typicalPriceIndicator.getValue(i));
        }
    }
}

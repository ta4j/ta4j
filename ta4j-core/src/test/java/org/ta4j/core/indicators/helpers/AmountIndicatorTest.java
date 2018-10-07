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

public class AmountIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private AmountIndicator amountIndicator;

    TimeSeries timeSeries;

    public AmountIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        timeSeries = new MockTimeSeries(numFunction);
        amountIndicator = new AmountIndicator(timeSeries);
    }

    @Test
    public void indicatorShouldRetrieveBarAmountPrice() {
        for (int i = 0; i < 10; i++) {
            assertEquals(amountIndicator.getValue(i), timeSeries.getBar(i).getAmount());
        }
    }
}

package org.ta4j.core.indicators.helpers;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockTimeSeries;
import org.ta4j.core.num.Num;

import java.util.function.Function;

import static org.junit.Assert.assertEquals;

public class TradeCountIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    private TradeCountIndicator tradeIndicator;

    TimeSeries timeSeries;

    public TradeCountIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        timeSeries = new MockTimeSeries(numFunction);
        tradeIndicator = new TradeCountIndicator(timeSeries);
    }

    @Test
    public void indicatorShouldRetrieveBarTrade() {
        for (int i = 0; i < 10; i++) {
            assertEquals((int) tradeIndicator.getValue(i), timeSeries.getBar(i).getTrades());
        }
    }
}

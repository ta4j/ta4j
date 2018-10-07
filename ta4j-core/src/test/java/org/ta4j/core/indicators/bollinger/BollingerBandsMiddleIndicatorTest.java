package org.ta4j.core.indicators.bollinger;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockTimeSeries;
import org.ta4j.core.num.Num;

import java.util.function.Function;

import static junit.framework.TestCase.assertEquals;

public class BollingerBandsMiddleIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    private TimeSeries data;

    public BollingerBandsMiddleIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        data = new MockTimeSeries(numFunction, 1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2);
    }

    @Test
    public void bollingerBandsMiddleUsingSMA() {
        SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(data), 3);
        BollingerBandsMiddleIndicator bbmSMA = new BollingerBandsMiddleIndicator(sma);

        for (int i = 0; i < data.getBarCount(); i++) {
            assertEquals(sma.getValue(i), bbmSMA.getValue(i));
        }
    }
}

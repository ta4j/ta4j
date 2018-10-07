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
import static org.ta4j.core.TestUtils.assertNumEquals;

public class PriceVariationIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private PriceVariationIndicator variationIndicator;

    private TimeSeries timeSeries;

    public PriceVariationIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        timeSeries = new MockTimeSeries(numFunction);
        variationIndicator = new PriceVariationIndicator(timeSeries);
    }

    @Test
    public void indicatorShouldRetrieveBarVariation() {
        assertNumEquals(1, variationIndicator.getValue(0));
        for (int i = 1; i < 10; i++) {
            Num previousBarClosePrice = timeSeries.getBar(i - 1).getClosePrice();
            Num currentBarClosePrice = timeSeries.getBar(i).getClosePrice();
            assertEquals(variationIndicator.getValue(i), currentBarClosePrice.dividedBy(previousBarClosePrice));
        }
    }
}

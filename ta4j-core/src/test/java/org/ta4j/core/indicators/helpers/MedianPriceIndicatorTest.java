package org.ta4j.core.indicators.helpers;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.mocks.MockTimeSeries;
import org.ta4j.core.num.Num;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static junit.framework.TestCase.assertEquals;

public class MedianPriceIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    private MedianPriceIndicator average;

    TimeSeries timeSeries;

    public MedianPriceIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        List<Bar> bars = new ArrayList<Bar>();

        bars.add(new MockBar(0, 0, 16, 8,numFunction));
        bars.add(new MockBar(0, 0, 12, 6,numFunction));
        bars.add(new MockBar(0, 0, 18, 14,numFunction));
        bars.add(new MockBar(0, 0, 10, 6,numFunction));
        bars.add(new MockBar(0, 0, 32, 6,numFunction));
        bars.add(new MockBar(0, 0, 2, 2,numFunction));
        bars.add(new MockBar(0, 0, 0, 0,numFunction));
        bars.add(new MockBar(0, 0, 8, 1,numFunction));
        bars.add(new MockBar(0, 0, 83, 32,numFunction));
        bars.add(new MockBar(0, 0, 9, 3,numFunction));


        this.timeSeries = new MockTimeSeries(bars);
        average = new MedianPriceIndicator(timeSeries);
    }

    @Test
    public void indicatorShouldRetrieveBarClosePrice() {
        Num result;
        for (int i = 0; i < 10; i++) {
            result = timeSeries.getBar(i).getHighPrice().plus(timeSeries.getBar(i).getLowPrice())
                    .dividedBy(timeSeries.numOf(2));
            assertEquals(average.getValue(i), result);
        }
    }
}

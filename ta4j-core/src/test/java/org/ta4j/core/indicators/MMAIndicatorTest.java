package org.ta4j.core.indicators;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.*;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.mocks.MockTimeSeries;
import org.ta4j.core.num.Num;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertIndicatorEquals;

public class MMAIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private ExternalIndicatorTest xls;

    public MMAIndicatorTest(Function<Number, Num> numFunction) throws Exception {
        super((data, params) -> new MMAIndicator(data, (int) params[0]),numFunction);
        xls = new XLSIndicatorTest(this.getClass(), "MMA.xls", 6, numFunction);
    }

    private TimeSeries data;

    @Before
    public void setUp() {
        data = new MockTimeSeries(numFunction,
                64.75, 63.79, 63.73,
                63.73, 63.55, 63.19,
                63.91, 63.85, 62.95,
                63.37, 61.33, 61.51);
    }

    @Test
    public void firstValueShouldBeEqualsToFirstDataValue() throws Exception {
        Indicator<Num> actualIndicator = getIndicator(new ClosePriceIndicator(data), 1);
        assertEquals(64.75, actualIndicator.getValue(0).doubleValue(), TestUtils.GENERAL_OFFSET);
    }

    @Test
    public void mmaUsingBarCount10UsingClosePrice() throws Exception {
        Indicator<Num> actualIndicator = getIndicator(new ClosePriceIndicator(data), 10);
        assertEquals(63.9983, actualIndicator.getValue(9).doubleValue(), TestUtils.GENERAL_OFFSET);
        assertEquals(63.7315, actualIndicator.getValue(10).doubleValue(), TestUtils.GENERAL_OFFSET);
        assertEquals(63.5093, actualIndicator.getValue(11).doubleValue(), TestUtils.GENERAL_OFFSET);
    }

    @Test
    public void stackOverflowError() throws Exception {
        List<Bar> bigListOfBars = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            bigListOfBars.add(new MockBar(i,numFunction));
        }
        MockTimeSeries bigSeries = new MockTimeSeries(bigListOfBars);
        ClosePriceIndicator closePrice = new ClosePriceIndicator(bigSeries);
        Indicator<Num> actualIndicator = getIndicator(closePrice, 10);
        // if a StackOverflowError is thrown here, then the RecursiveCachedIndicator does not work as intended.
        assertEquals(9990.0, actualIndicator.getValue(9999).doubleValue(), TestUtils.GENERAL_OFFSET);
    }

    @Test
    public void testAgainstExternalData() throws Exception {
        Indicator<Num> xlsClose = new ClosePriceIndicator(xls.getSeries());
        Indicator<Num> actualIndicator;

        actualIndicator = getIndicator(xlsClose, 1);
        assertIndicatorEquals(xls.getIndicator(1), actualIndicator);
        assertEquals(329.0, actualIndicator.getValue(actualIndicator.getTimeSeries().getEndIndex()).doubleValue(), TestUtils.GENERAL_OFFSET);

        actualIndicator = getIndicator(xlsClose, 3);
        assertIndicatorEquals(xls.getIndicator(3), actualIndicator);
        assertEquals(327.2900, actualIndicator.getValue(actualIndicator.getTimeSeries().getEndIndex()).doubleValue(), TestUtils.GENERAL_OFFSET);

        actualIndicator = getIndicator(xlsClose, 13);
        assertIndicatorEquals(xls.getIndicator(13), actualIndicator);
        assertEquals(326.9696, actualIndicator.getValue(actualIndicator.getTimeSeries().getEndIndex()).doubleValue(), TestUtils.GENERAL_OFFSET);
    }

}

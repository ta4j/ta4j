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
import static org.ta4j.core.TestUtils.assertNumEquals;

public class EMAIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private ExternalIndicatorTest xls;

    public EMAIndicatorTest(Function<Number, Num> numFunction) throws Exception {
        super((data, params) -> new EMAIndicator(data, (int) params[0]), numFunction);
        xls = new XLSIndicatorTest(this.getClass(), "EMA.xls", 6, numFunction);
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
        Indicator<Num> indicator = getIndicator(new ClosePriceIndicator(data), 1);
        assertNumEquals(64.75, indicator.getValue(0));
    }

    @Test
    public void usingBarCount10UsingClosePrice() throws Exception {
        Indicator<Num> indicator = getIndicator(new ClosePriceIndicator(data), 10);
        assertNumEquals(63.6948, indicator.getValue(9));
        assertNumEquals(63.2648, indicator.getValue(10));
        assertNumEquals(62.9457, indicator.getValue(11));
    }

    @Test
    public void stackOverflowError() throws Exception {
        List<Bar> bigListOfBars = new ArrayList<Bar>();
        for (int i = 0; i < 10000; i++) {
            bigListOfBars.add(new MockBar(i,numFunction));
        }
        MockTimeSeries bigSeries = new MockTimeSeries(bigListOfBars);
        Indicator<Num> indicator = getIndicator(new ClosePriceIndicator(bigSeries), 10);
        // if a StackOverflowError is thrown here, then the RecursiveCachedIndicator does not work as intended.
        assertNumEquals(9994.5, indicator.getValue(9999));
    }

    @Test
    public void externalData() throws Exception {
        TimeSeries xlsSeries = xls.getSeries();
        Indicator<Num> closePrice = new ClosePriceIndicator(xlsSeries);
        Indicator<Num> indicator;

        indicator = getIndicator(closePrice, 1);
        assertIndicatorEquals(xls.getIndicator(1), indicator);
        assertEquals(329.0, indicator.getValue(indicator.getTimeSeries().getEndIndex()).doubleValue(), TestUtils.GENERAL_OFFSET);

        indicator = getIndicator(closePrice, 3);
        assertIndicatorEquals(xls.getIndicator(3), indicator);
        assertEquals(327.7748, indicator.getValue(indicator.getTimeSeries().getEndIndex()).doubleValue(), TestUtils.GENERAL_OFFSET);

        indicator = getIndicator(closePrice, 13);
        assertIndicatorEquals(xls.getIndicator(13), indicator);
        assertEquals(327.4076, indicator.getValue(indicator.getTimeSeries().getEndIndex()).doubleValue(), TestUtils.GENERAL_OFFSET);
    }

}

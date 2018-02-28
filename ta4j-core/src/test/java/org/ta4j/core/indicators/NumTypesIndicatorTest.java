package org.ta4j.core.indicators;

import org.junit.Test;
import org.ta4j.core.ExternalIndicatorTest;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.BigDecimalNum;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;

import static org.ta4j.core.TestUtils.assertIndicatorMatches;
import static org.ta4j.core.TestUtils.assertIndicatorNotMatches;

public class NumTypesIndicatorTest {

    ExternalIndicatorTest xls;
    Indicator<Num> doubleClose;
    Indicator<Num> bigDecimalClose;
    Indicator<Num> doubleSMA;
    Indicator<Num> bigDecimalSMA;

    public NumTypesIndicatorTest() throws Exception {
        // get the close series here so they don't pollute the unit tests below
        // this also isolates the Exception to the constructor
        xls = new XLSIndicatorTest(this.getClass(), "GSPC_1970_2017.xls", 6, BigDecimalNum::valueOf);
        doubleClose = new ClosePriceIndicator(xls.getSeries(DoubleNum::valueOf));
        bigDecimalClose = new ClosePriceIndicator(xls.getSeries(BigDecimalNum::valueOf));
        doubleSMA = getDoubleSMA();
        bigDecimalSMA = getBigDecimalSMA();
    }

    @Test
    public void testPassDefault() {
        assertIndicatorMatches(bigDecimalSMA, doubleSMA);
        assertIndicatorMatches(doubleSMA, bigDecimalSMA);
    }

    @Test
    public void testPass5() {
        assertIndicatorMatches(bigDecimalSMA, doubleSMA, 5);
        assertIndicatorMatches(doubleSMA, bigDecimalSMA, 5);
    }

    @Test(expected = AssertionError.class)
    public void testPass6() {
        // bombs out at index 1247
        assertIndicatorMatches(bigDecimalSMA, doubleSMA, 6);
    }

    @Test(expected = AssertionError.class)
    public void testPass7() {
        // bombs out at index 900
        assertIndicatorMatches(bigDecimalSMA, doubleSMA, 7);
    }

    @Test(expected = AssertionError.class)
    public void testPass8() {
        // bombs out at index 1!
        assertIndicatorMatches(bigDecimalSMA, doubleSMA, 8);
    }

    @Test
    public void testFail5() {
        assertIndicatorNotMatches(bigDecimalSMA, doubleSMA, 6);
        assertIndicatorNotMatches(doubleSMA, bigDecimalSMA, 6);
    }

    @Test(expected = AssertionError.class)
    public void testFail4() {
        assertIndicatorNotMatches(bigDecimalSMA, doubleSMA, 4);
    }

    // wrapper method for SMA of DoubleNum close values, to distinguish methods in profiler
    private Indicator<Num> getDoubleSMA() {
        return getSMA(doubleClose);
    }

    // wrapper method for SMA of BigDecimalNum close values, to distinguish methods in profiler
    private Indicator<Num> getBigDecimalSMA() {
        return getSMA(bigDecimalClose);
    }

    private Indicator<Num> getSMA(Indicator<Num> close) {
        Indicator<Num> sma = new SMAIndicator(close, 200);
        for (int i = close.getTimeSeries().getBeginIndex(); i < close.getTimeSeries().getEndIndex(); i++) {
            sma.getValue(i);
        }
        return sma;
    }

}

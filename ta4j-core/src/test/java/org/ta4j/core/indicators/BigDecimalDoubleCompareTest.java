package org.ta4j.core.indicators;

import org.junit.Test;
import org.ta4j.core.ExternalIndicatorTest;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.BigDecimalNum;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.ta4j.core.TestUtils.assertIndicatorEquals;
import static org.ta4j.core.TestUtils.assertIndicatorNotEquals;

public class BigDecimalDoubleCompareTest {

    ExternalIndicatorTest xls;
    Indicator<Num> doubleClose;
    Indicator<Num> bigDecimalClose;
    protected Logger log;

    public BigDecimalDoubleCompareTest() throws Exception {
        // initiate the logger here so it doesn't pollute the unit tests below
        log = LoggerFactory.getLogger(getClass());
        // let the system settle down
        Thread.sleep(5000);
        // get the close series here so they don't pollute the unit tests below
        xls = new XLSIndicatorTest(this.getClass(), "GSPC_1970_2017.xls", 6, BigDecimalNum::valueOf);
        doubleClose = new ClosePriceIndicator(xls.getSeries(DoubleNum::valueOf));
        bigDecimalClose = new ClosePriceIndicator(xls.getSeries(BigDecimalNum::valueOf));
    }

    // DON'T TRUST the JUnit reported runtimes!
    // Must use a profiler because the first test always runs slower for some unknown reason.
    // With a profiler, can focus on just the methods that you care about.

    @Test
    public void performance() throws Exception {
        // put these two together so that the constructor only runs once
        // because with call tracing active, constructor takes a long time to run
        doubleNum();
        bigDecimalNum();
    }

    private void doubleNum() throws Exception {
        Indicator<Num> doubleSMA = new SMAIndicator(doubleClose, 200);
        for (int i = doubleClose.getTimeSeries().getBeginIndex(); i < doubleClose.getTimeSeries().getEndIndex(); i++) {
            doubleSMA.getValue(i);
        }
    }

    private void bigDecimalNum() throws Exception {
        Indicator<Num> bigDecimalSMA = new SMAIndicator(bigDecimalClose, 200);
        for (int i = bigDecimalClose.getTimeSeries().getBeginIndex(); i < bigDecimalClose.getTimeSeries().getEndIndex(); i++) {
            bigDecimalSMA.getValue(i);
        }
    }

    //@Test
    public void precision() throws Exception {
        Indicator<Num> bigDecimalSMA = new SMAIndicator(bigDecimalClose, 200);
        Indicator<Num> doubleSMA = new SMAIndicator(doubleClose, 200);
        assertIndicatorEquals(bigDecimalSMA, doubleSMA, BigDecimalNum.valueOf("0.00000000001"));
        assertIndicatorNotEquals(bigDecimalSMA, doubleSMA, BigDecimalNum.valueOf("0.000000000001"));
    }

}

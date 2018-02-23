package org.ta4j.core.indicators;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.ta4j.core.ExternalIndicatorTest;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.BigDecimalNum;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)

public class BigDecimalDoubleCompareTest {

    ExternalIndicatorTest xls;
    Indicator<Num> dnClose;
    Indicator<Num> bdnClose;
    protected Logger log;

    public BigDecimalDoubleCompareTest() throws Exception {
        // initiate the logger here so it doesn't pollute the unit tests below
        log = LoggerFactory.getLogger(getClass());
        // let the system settle down
        Thread.sleep(10000);
        // get the close series here so they don't pollute the unit tests below
        xls = new XLSIndicatorTest(this.getClass(), "GSPC_1970_2017.xls", 6, BigDecimalNum::valueOf);
        dnClose = new ClosePriceIndicator(xls.getSeries(DoubleNum::valueOf));
        bdnClose = new ClosePriceIndicator(xls.getSeries(BigDecimalNum::valueOf));
    }

    // DON'T TRUST the JUnit reported runtimes!
    // Must use a profiler because the first test always runs slower for some unknown reason.
    // With a profiler, can focus on just the methods that you care about.

    @Test
    public void SMA02dn() throws Exception {
        Indicator<Num> dnInd = new SMAIndicator(dnClose, 200);
        for (int i = dnClose.getTimeSeries().getBeginIndex(); i < dnClose.getTimeSeries().getEndIndex(); i++) {
            Num value = dnInd.getValue(i);
        }
    }

    @Test
    public void SMA01bdn() throws Exception {
        Indicator<Num> bdnInd = new SMAIndicator(bdnClose, 200);
        for (int i = bdnClose.getTimeSeries().getBeginIndex(); i < bdnClose.getTimeSeries().getEndIndex(); i++) {
            Num value = bdnInd.getValue(i);
        }
    }

}

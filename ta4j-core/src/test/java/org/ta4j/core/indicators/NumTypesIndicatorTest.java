package org.ta4j.core.indicators;

import org.junit.Test;
import org.ta4j.core.ExternalIndicatorTest;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.BigDecimalNum;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;
import static org.ta4j.core.TestUtils.assertIndicatorEquals;
import static org.ta4j.core.TestUtils.assertIndicatorNotEquals;

public class NumTypesIndicatorTest {

    ExternalIndicatorTest xls;
    Indicator<Num> doubleClose;
    Indicator<Num> bigDecimalClose;

    public NumTypesIndicatorTest() throws Exception {
        // get the close series here so they don't pollute the unit tests below
        xls = new XLSIndicatorTest(this.getClass(), "GSPC_1970_2017.xls", 6, BigDecimalNum::valueOf);
        doubleClose = new ClosePriceIndicator(xls.getSeries(DoubleNum::valueOf));
        bigDecimalClose = new ClosePriceIndicator(xls.getSeries(BigDecimalNum::valueOf));
    }

    @Test
    public void test() throws Exception {
        Indicator<Num> doubleSMA = getDoubleSMA();
        Indicator<Num> bigDecimalSMA = getBigDecimalSMA();
        assertIndicatorEquals(bigDecimalSMA, doubleSMA,
                BigDecimalNum.valueOf("0.00000000001"));
        assertIndicatorNotEquals(bigDecimalSMA, doubleSMA,
                BigDecimalNum.valueOf("0.000000000001"));
    }

    // wrapper method for SMA of DoubleNum close values, to distinguish methods in profiler
    private Indicator<Num> getDoubleSMA() throws Exception {
        return getSMA(doubleClose);
    }

    // wrapper method for SMA of BigDecimalNum close values, to distinguish methods in profiler
    private Indicator<Num> getBigDecimalSMA() throws Exception {
        return getSMA(bigDecimalClose);
    }

    private Indicator<Num> getSMA(Indicator<Num> close) throws Exception {
        Indicator<Num> sma = new SMAIndicator(close, 200);
        for (int i = close.getTimeSeries().getBeginIndex(); i < close.getTimeSeries().getEndIndex(); i++) {
            sma.getValue(i);
        }
        return sma;
    }

}

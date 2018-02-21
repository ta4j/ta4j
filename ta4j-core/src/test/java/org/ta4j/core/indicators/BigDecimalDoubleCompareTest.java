package org.ta4j.core.indicators;

import static org.ta4j.core.TestUtils.assertIndicatorEquals;
import static org.ta4j.core.TestUtils.assertIndicatorNotEquals;

import org.junit.Test;
import org.ta4j.core.ExternalIndicatorTest;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.BigDecimalNum;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;

public class BigDecimalDoubleCompareTest {

    ExternalIndicatorTest xls = new XLSIndicatorTest(this.getClass(), "SMA.xls", 6, BigDecimalNum::valueOf);

    @Test
    public void SMA() throws Exception {
        Indicator<Num> bdnClose = new ClosePriceIndicator(xls.getSeries(BigDecimalNum::valueOf));
        Indicator<Num> dnClose = new ClosePriceIndicator(xls.getSeries(DoubleNum::valueOf));
        Indicator<Num> bdnIndicator = new SMAIndicator(bdnClose, 200);
        Indicator<Num> dnIndicator = new SMAIndicator(dnClose, 200);
        assertIndicatorEquals(bdnIndicator, dnIndicator, BigDecimalNum.valueOf("0.000000000001"));
        assertIndicatorNotEquals(bdnIndicator, dnIndicator, BigDecimalNum.valueOf("0.0000000000001"));
    }

    @Test
    public void RSI() throws Exception {
        Indicator<Num> bdnClose = new ClosePriceIndicator(xls.getSeries(BigDecimalNum::valueOf));
        Indicator<Num> dnClose = new ClosePriceIndicator(xls.getSeries(DoubleNum::valueOf));
        Indicator<Num> bdnIndicator = new RSIIndicator(bdnClose, 14);
        Indicator<Num> dnIndicator = new RSIIndicator(dnClose, 14);
        assertIndicatorEquals(bdnIndicator, dnIndicator, BigDecimalNum.valueOf("0.0000000000001"));
        assertIndicatorNotEquals(bdnIndicator, dnIndicator, BigDecimalNum.valueOf("0.00000000000001"));
    }

}

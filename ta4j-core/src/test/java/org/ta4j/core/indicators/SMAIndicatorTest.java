/*
  The MIT License (MIT)

  Copyright (c) 2014-2017 Marc de Verdelhan, Ta4j Organization & respective authors (see AUTHORS)

  Permission is hereby granted, free of charge, to any person obtaining a copy of
  this software and associated documentation files (the "Software"), to deal in
  the Software without restriction, including without limitation the rights to
  use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
  the Software, and to permit persons to whom the Software is furnished to do so,
  subject to the following conditions:

  The above copyright notice and this permission notice shall be included in all
  copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
  FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
  IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
  CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.ExternalIndicatorTest;
import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockTimeSeries;
import org.ta4j.core.num.BigDecimalNum;
import org.ta4j.core.num.Num;

import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertIndicatorEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

public class SMAIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private ExternalIndicatorTest xls;

    public SMAIndicatorTest(Function<Number, Num> numFunction) throws Exception {
        super((data, params) -> new SMAIndicator((Indicator<Num>) data, (int) params[0]), numFunction);
        xls = new XLSIndicatorTest(this.getClass(), "SMA.xls", 6, numFunction);
    }

    private TimeSeries data;

    @Before
    public void setUp() {
        data = new MockTimeSeries(numFunction, 1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2);
    }

    @Test
    public void testPass() {
        // maximum precision to pass DoubleNum (and BigDecimalNum)
        usingTimeFrame3UsingClosePrice(BigDecimalNum.valueOf("0.000000000000001", 64));
    }

    @Test(expected = java.lang.AssertionError.class)
    public void testFail() {
        // minimum precision to fail BigDecimalNum (and DoubleNum)
        usingTimeFrame3UsingClosePrice(BigDecimalNum.valueOf("0.00000000000000000000000000000001", 64));
    }

    public void usingTimeFrame3UsingClosePrice(Num delta) {
        Indicator<Num> indicator = getIndicator(new ClosePriceIndicator(data), 3);

        assertNumEquals("1", indicator.getValue(0), delta);
        assertNumEquals("1.5", indicator.getValue(1), delta);
        assertNumEquals("2", indicator.getValue(2), delta);
        assertNumEquals("3", indicator.getValue(3), delta);
        assertNumEquals("3.333333333333333333333333333333333333333333333333333333333333333", indicator.getValue(4), delta);
        assertNumEquals("3.666666666666666666666666666666666666666666666666666666666666667", indicator.getValue(5), delta);
        assertNumEquals("4", indicator.getValue(6), delta);
        assertNumEquals("4.333333333333333333333333333333333333333333333333333333333333333", indicator.getValue(7), delta);
        assertNumEquals("4", indicator.getValue(8), delta);
        assertNumEquals("3.333333333333333333333333333333333333333333333333333333333333333", indicator.getValue(9), delta);
        assertNumEquals("3.333333333333333333333333333333333333333333333333333333333333333", indicator.getValue(10), delta);
        assertNumEquals("3.333333333333333333333333333333333333333333333333333333333333333", indicator.getValue(11), delta);
        assertNumEquals("3", indicator.getValue(12), delta);
    }

    @Test
    public void whenTimeFrameIs1ResultShouldBeIndicatorValue() throws Exception {
        Indicator<Num> indicator = getIndicator(new ClosePriceIndicator(data), 1);
        for (int i = 0; i < data.getBarCount(); i++) {
            assertEquals(data.getBar(i).getClosePrice(), indicator.getValue(i));
        }
    }

    @Test
    public void externalDataPass() throws Exception {
        externalData(BigDecimalNum.valueOf("0.000000000001", 64));
    }

    @Test(expected = AssertionError.class)
    public void externalDataFail() throws Exception {
        externalData(BigDecimalNum.valueOf("0.0000000000001", 64));
    }

    public void externalData(Num delta) throws Exception {
        Indicator<Num> xlsClose = new ClosePriceIndicator(xls.getSeries());
        Indicator<Num> actualIndicator;

        actualIndicator = getIndicator(xlsClose, 1);
        assertIndicatorEquals(xls.getIndicator(1), actualIndicator, delta);
        assertNumEquals("329", actualIndicator.getValue(actualIndicator.getTimeSeries().getEndIndex()), delta);

        actualIndicator = getIndicator(xlsClose, 3);
        assertIndicatorEquals(xls.getIndicator(3), actualIndicator, delta);
        assertNumEquals("326.6333333333333333333333333333333333333333333333333333333333333", actualIndicator.getValue(actualIndicator.getTimeSeries().getEndIndex()), delta);

        actualIndicator = getIndicator(xlsClose, 13);
        assertIndicatorEquals(xls.getIndicator(13), actualIndicator, delta);
        assertNumEquals("327.7846153846153846153846153846153846153846153846153846153846154", actualIndicator.getValue(actualIndicator.getTimeSeries().getEndIndex()), delta);
    }

}

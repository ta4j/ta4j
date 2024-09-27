/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators.statistics;

import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class SimpleLinearRegressionIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private Indicator<Num> closePrice;

    public SimpleLinearRegressionIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        double[] data = { 10, 20, 30, 40, 30, 40, 30, 20, 30, 50, 60, 70, 80 };
        closePrice = new ClosePriceIndicator(
                new MockBarSeriesBuilder().withNumFactory(numFactory).withData(data).build());
    }

    @Test
    public void notComputedLinearRegression() {

        var linearReg = new SimpleLinearRegressionIndicator(closePrice, 0);
        assertTrue(linearReg.getValue(0).isNaN());
        assertTrue(linearReg.getValue(1).isNaN());
        assertTrue(linearReg.getValue(2).isNaN());

        linearReg = new SimpleLinearRegressionIndicator(closePrice, 1);
        assertTrue(linearReg.getValue(0).isNaN());
        assertTrue(linearReg.getValue(1).isNaN());
        assertTrue(linearReg.getValue(2).isNaN());
    }

    @Test
    public void calculateLinearRegressionWithLessThan2ObservationsReturnsNaN() {
        var reg = new SimpleLinearRegressionIndicator(closePrice, 0);
        assertTrue(reg.getValue(0).isNaN());
        assertTrue(reg.getValue(3).isNaN());
        assertTrue(reg.getValue(6).isNaN());
        assertTrue(reg.getValue(9).isNaN());
        reg = new SimpleLinearRegressionIndicator(closePrice, 1);
        assertTrue(reg.getValue(0).isNaN());
        assertTrue(reg.getValue(3).isNaN());
        assertTrue(reg.getValue(6).isNaN());
        assertTrue(reg.getValue(9).isNaN());
    }

    @Test
    public void calculateLinearRegressionOn4Observations() {

        var reg = new SimpleLinearRegressionIndicator(closePrice, 4);
        assertNumEquals(20, reg.getValue(1));
        assertNumEquals(30, reg.getValue(2));

        SimpleRegression origReg = buildSimpleRegression(10, 20, 30, 40);
        assertNumEquals(40, reg.getValue(3));
        assertNumEquals(origReg.predict(3), reg.getValue(3));

        origReg = buildSimpleRegression(30, 40, 30, 40);
        assertNumEquals(origReg.predict(3), reg.getValue(5));

        origReg = buildSimpleRegression(30, 20, 30, 50);
        assertNumEquals(origReg.predict(3), reg.getValue(9));
    }

    @Test
    public void calculateLinearRegression() {
        double[] values = { 1, 2, 1.3, 3.75, 2.25 };
        var indicator = new ClosePriceIndicator(
                new MockBarSeriesBuilder().withNumFactory(numFactory).withData(values).build());
        var reg = new SimpleLinearRegressionIndicator(indicator, 5);

        SimpleRegression origReg = buildSimpleRegression(values);
        assertNumEquals(origReg.predict(4), reg.getValue(4));
    }

    /**
     * @param values values
     * @return a simple linear regression based on provided values
     */
    private static SimpleRegression buildSimpleRegression(double... values) {
        SimpleRegression simpleReg = new SimpleRegression();
        for (int i = 0; i < values.length; i++) {
            simpleReg.addData(i, values[i]);
        }
        return simpleReg;
    }
}

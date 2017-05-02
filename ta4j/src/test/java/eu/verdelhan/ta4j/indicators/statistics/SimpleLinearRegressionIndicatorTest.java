/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)
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
package eu.verdelhan.ta4j.indicators.statistics;

import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.Indicator;
import static eu.verdelhan.ta4j.TATestsUtils.assertDecimalEquals;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class SimpleLinearRegressionIndicatorTest {

    private double[] data;
    
    private Indicator<Decimal> closePrice;
    
    @Before
    public void setUp() {
        data = new double[] {10, 20, 30, 40, 30, 40, 30, 20, 30, 50, 60, 70, 80};
        closePrice = new ClosePriceIndicator(new MockTimeSeries(data));
    }

    @Test
    public void notComputedLinearRegression() {

        SimpleLinearRegressionIndicator linearReg = new SimpleLinearRegressionIndicator(closePrice, 0);
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
        SimpleLinearRegressionIndicator reg = new SimpleLinearRegressionIndicator(closePrice, 0);
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

        SimpleLinearRegressionIndicator reg = new SimpleLinearRegressionIndicator(closePrice, 4);
        assertDecimalEquals(reg.getValue(1), 20);
        assertDecimalEquals(reg.getValue(2), 30);
        
        SimpleRegression origReg = buildSimpleRegression(10, 20, 30, 40);
        assertDecimalEquals(reg.getValue(3), 40);
        assertDecimalEquals(reg.getValue(3), origReg.predict(3));
        
        origReg = buildSimpleRegression(30, 40, 30, 40);
        assertDecimalEquals(reg.getValue(5), origReg.predict(3));
        
        origReg = buildSimpleRegression(30, 20, 30, 50);
        assertDecimalEquals(reg.getValue(9), origReg.predict(3));
    }
    
    @Test
    public void calculateLinearRegression() {
        double[] values = new double[] { 1, 2, 1.3, 3.75, 2.25 };
        ClosePriceIndicator indicator = new ClosePriceIndicator(new MockTimeSeries(values));
        SimpleLinearRegressionIndicator reg = new SimpleLinearRegressionIndicator(indicator, 5);
        
        SimpleRegression origReg = buildSimpleRegression(values);
        assertDecimalEquals(reg.getValue(4), origReg.predict(4));
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

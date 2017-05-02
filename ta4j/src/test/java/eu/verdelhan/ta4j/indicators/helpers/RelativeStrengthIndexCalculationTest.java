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
package eu.verdelhan.ta4j.indicators.helpers;

import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import org.junit.Before;
import org.junit.Test;

import static eu.verdelhan.ta4j.TATestsUtils.assertDecimalEquals;
import static org.junit.Assert.assertEquals;

public class RelativeStrengthIndexCalculationTest {

    private TimeSeries gains;
    private TimeSeries losses;

    @Before
    public void prepare() {
        gains = new MockTimeSeries(1, 1, 0.8, 0.84, 0.672, 0.5376, 0.43008);
        losses = new MockTimeSeries(2, 0, 0.2, 0.16, 0.328, 0.4624, 0.36992);
    }

    @Test
    public void rsiCalculationFromMockedGainsAndLosses() {
        RelativeStrengthIndexCalculation rsiCalc = new RelativeStrengthIndexCalculation(
            new ClosePriceIndicator(gains),
            new ClosePriceIndicator(losses)
        );

        assertDecimalEquals(rsiCalc.getValue(2), 80.0);
        assertDecimalEquals(rsiCalc.getValue(3), 84.0);
        assertDecimalEquals(rsiCalc.getValue(4), 67.2);
        assertDecimalEquals(rsiCalc.getValue(5), 53.76);
        assertDecimalEquals(rsiCalc.getValue(6), 53.76);

    }

    @Test
    public void rsiCalcFirstValueShouldBeZero() {
        RelativeStrengthIndexCalculation rsiCalc = new RelativeStrengthIndexCalculation(
            new ClosePriceIndicator(gains),
            new ClosePriceIndicator(losses)
        );

        assertEquals(Decimal.ZERO, rsiCalc.getValue(0));
    }

    @Test
    public void rsiCalcHundredIfNoLoss() {
        RelativeStrengthIndexCalculation rsiCalc = new RelativeStrengthIndexCalculation(
            new ClosePriceIndicator(gains),
            new ClosePriceIndicator(losses)
        );

        assertEquals(Decimal.HUNDRED, rsiCalc.getValue(1));
    }

}

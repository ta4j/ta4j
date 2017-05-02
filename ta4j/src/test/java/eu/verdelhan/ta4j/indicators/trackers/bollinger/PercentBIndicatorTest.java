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
package eu.verdelhan.ta4j.indicators.trackers.bollinger;

import eu.verdelhan.ta4j.Decimal;
import static eu.verdelhan.ta4j.TATestsUtils.assertDecimalEquals;

import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class PercentBIndicatorTest {

    private TimeSeries data;

    private ClosePriceIndicator closePrice;

    @Before
    public void setUp() {
        data = new MockTimeSeries(
                10, 12, 15, 14, 17,
                20, 21, 20, 20, 19,
                20, 17, 12, 12, 9,
                8, 9, 10, 9, 10
        );
        closePrice = new ClosePriceIndicator(data);
    }

    @Test
    public void percentBUsingSMAAndStandardDeviation() {

        PercentBIndicator pcb = new PercentBIndicator(closePrice, 5, Decimal.TWO);
        
        assertTrue(pcb.getValue(0).isNaN());
        assertDecimalEquals(pcb.getValue(1), 0.75);
        assertDecimalEquals(pcb.getValue(2), 0.8244);
        assertDecimalEquals(pcb.getValue(3), 0.6627);
        assertDecimalEquals(pcb.getValue(4), 0.8517);
        assertDecimalEquals(pcb.getValue(5), 0.90328);
        assertDecimalEquals(pcb.getValue(6), 0.83);
        assertDecimalEquals(pcb.getValue(7), 0.6552);
        assertDecimalEquals(pcb.getValue(8), 0.5737);
        assertDecimalEquals(pcb.getValue(9), 0.1047);
        assertDecimalEquals(pcb.getValue(10), 0.5);
        assertDecimalEquals(pcb.getValue(11), 0.0284);
        assertDecimalEquals(pcb.getValue(12), 0.0344);
        assertDecimalEquals(pcb.getValue(13), 0.2064);
        assertDecimalEquals(pcb.getValue(14), 0.1835);
        assertDecimalEquals(pcb.getValue(15), 0.2131);
        assertDecimalEquals(pcb.getValue(16), 0.3506);
        assertDecimalEquals(pcb.getValue(17), 0.5737);
        assertDecimalEquals(pcb.getValue(18), 0.5);
        assertDecimalEquals(pcb.getValue(19), 0.7673);
    }
}

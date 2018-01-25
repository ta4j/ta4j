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
package org.ta4j.core.indicators.bollinger;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockTimeSeries;

import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TATestsUtils.assertNumEquals;

public class PercentBIndicatorTest {

    private ClosePriceIndicator closePrice;

    @Before
    public void setUp() {
        TimeSeries data = new MockTimeSeries(
                10, 12, 15, 14, 17,
                20, 21, 20, 20, 19,
                20, 17, 12, 12, 9,
                8, 9, 10, 9, 10
        );
        closePrice = new ClosePriceIndicator(data);
    }

    @Test
    public void percentBUsingSMAAndStandardDeviation() {

        PercentBIndicator pcb = new PercentBIndicator(closePrice, 5, 2);

        assertTrue(pcb.getValue(0).isNaN());
        assertNumEquals(pcb.getValue(1), 0.75);
        assertNumEquals(pcb.getValue(2), 0.8244);
        assertNumEquals(pcb.getValue(3), 0.6627);
        assertNumEquals(pcb.getValue(4), 0.8517);
        assertNumEquals(pcb.getValue(5), 0.90328);
        assertNumEquals(pcb.getValue(6), 0.83);
        assertNumEquals(pcb.getValue(7), 0.6552);
        assertNumEquals(pcb.getValue(8), 0.5737);
        assertNumEquals(pcb.getValue(9), 0.1047);
        assertNumEquals(pcb.getValue(10), 0.5);
        assertNumEquals(pcb.getValue(11), 0.0284);
        assertNumEquals(pcb.getValue(12), 0.0344);
        assertNumEquals(pcb.getValue(13), 0.2064);
        assertNumEquals(pcb.getValue(14), 0.1835);
        assertNumEquals(pcb.getValue(15), 0.2131);
        assertNumEquals(pcb.getValue(16), 0.3506);
        assertNumEquals(pcb.getValue(17), 0.5737);
        assertNumEquals(pcb.getValue(18), 0.5);
        assertNumEquals(pcb.getValue(19), 0.7673);
    }
}

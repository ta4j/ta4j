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
package org.ta4j.core.indicators.bollinger;

import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class PercentBIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private ClosePriceIndicator closePrice;

    public PercentBIndicatorTest(final NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        final var data = new MockBarSeriesBuilder().withNumFactory(this.numFactory)
                .withData(10, 12, 15, 14, 17, 20, 21, 20, 20, 19, 20, 17, 12, 12, 9, 8, 9, 10, 9, 10)
                .build();
        this.closePrice = new ClosePriceIndicator(data);
    }

    @Test
    public void percentBUsingSMAAndStandardDeviation() {
        final var pcb = new PercentBIndicator(this.closePrice, 5, 2);

        assertTrue(pcb.getValue(0).isNaN());
        assertNumEquals(0.6768, pcb.getValue(1)); // [10,12]
        assertNumEquals(0.7649, pcb.getValue(2)); // [10,12,15]
        assertNumEquals(0.6409, pcb.getValue(3)); // [10,12,15,14]
        assertNumEquals(0.8146, pcb.getValue(4)); // [10,12,15,14,17]
        assertNumEquals(0.8607, pcb.getValue(5)); // [12,15,14,17,20]
        assertNumEquals(0.7951, pcb.getValue(6)); // [15,14,17,20,21]
        assertNumEquals(0.6388, pcb.getValue(7)); // [14,17,20,21,20]
        assertNumEquals(0.5659, pcb.getValue(8)); // [17,20,21,20,20]
        assertNumEquals(0.1464, pcb.getValue(9)); // [20,21,20,20,19]
        assertNumEquals(0.5000, pcb.getValue(10)); // [21,20,20,19,20]
        assertNumEquals(0.0782, pcb.getValue(11)); // [20,20,19,20,17]
        assertNumEquals(0.0835, pcb.getValue(12)); // [20,19,20,17,12]
        assertNumEquals(0.2373, pcb.getValue(13)); // [19,20,17,12,12]
        assertNumEquals(0.2169, pcb.getValue(14)); // [20,17,12,12,9]
        assertNumEquals(0.2433, pcb.getValue(15)); // [17,12,12,9,8]
        assertNumEquals(0.3663, pcb.getValue(16)); // [12,12,9,8,9]
        assertNumEquals(0.5659, pcb.getValue(17)); // [12,9,8,9,10]
        assertNumEquals(0.5000, pcb.getValue(18)); // [9,8,9,10,9]
        assertNumEquals(0.7390, pcb.getValue(19)); // [8,9,10,9,10]
    }
}

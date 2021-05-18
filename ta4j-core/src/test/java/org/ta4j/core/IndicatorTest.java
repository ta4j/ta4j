/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
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
package org.ta4j.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeries;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.Num;

public class IndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    double[] typicalPrices = { 23.98, 23.92, 23.79, 23.67, 23.54, 23.36, 23.65, 23.72, 24.16, 23.91, 23.81, 23.92,
            23.74, 24.68, 24.94, 24.93, 25.10, 25.12, 25.20, 25.06, 24.50, 24.31, 24.57, 24.62, 24.49, 24.37, 24.41,
            24.35, 23.75, 24.09 };
    BarSeries data;

    public IndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        data = new MockBarSeries(numFunction, typicalPrices);
    }

    @Test
    public void toDouble() {
        List<Num> expectedValues = Arrays.stream(typicalPrices)
                .mapToObj(numFunction::apply)
                .collect(Collectors.toList());
        MockIndicator closePriceMockIndicator = new MockIndicator(data, expectedValues);

        int barCount = 10, index = 20;
        Double[] doubles = Indicator.toDouble(closePriceMockIndicator, index, barCount);
        assertTrue(doubles.length == barCount);

        for (int i = 0; i < barCount; i++) {
            assertTrue(typicalPrices[i + 11] == doubles[i]);
        }
    }

    @Test
    public void shouldProvideStream() {
        List<Num> expectedValues = Arrays.stream(typicalPrices)
                .mapToObj(numFunction::apply)
                .collect(Collectors.toList());
        MockIndicator closePriceMockIndicator = new MockIndicator(data, expectedValues);

        Stream<Num> stream = closePriceMockIndicator.stream();
        List<Num> collectedValues = stream.collect(Collectors.toList());

        Assert.assertNotNull(stream);
        Assert.assertNotNull(collectedValues);
        assertEquals(30, collectedValues.size());
        for (int i = 0; i < data.getBarCount(); i++) {
            assertNumEquals(typicalPrices[i], collectedValues.get(i));
        }
    }

}

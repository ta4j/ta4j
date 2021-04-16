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
package org.ta4j.core.utils;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.mocks.MockBarSeries;
import org.ta4j.core.num.Num;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.ta4j.core.TestUtils.assertNumEquals;

public class IndicatorUtilsTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private final double[] typicalPrices = new double[] { 23.98, 23.92, 23.79, 23.67, 23.54, 23.36, 23.65, 23.72, 24.16,
            23.91, 23.81, 23.92, 23.74, 24.68, 24.94, 24.93, 25.10, 25.12, 25.20, 25.06, 24.50, 24.31, 24.57, 24.62,
            24.49, 24.37, 24.41, 24.35, 23.75, 24.09 };

    private MockBarSeries series;

    /**
     * Constructor.
     *
     * @param function
     */
    public IndicatorUtilsTest(Function<Number, Num> function) {
        super(function);
    }

    @Before
    public void setUp() {
        ArrayList<Bar> bars = new ArrayList<>();
        for (Double price : typicalPrices) {
            bars.add(new MockBar(price, price, price, price, numFunction));
        }
        series = new MockBarSeries(bars);
    }

    @Test
    public void shouldProvideStream() {
        ClosePriceIndicator indicator = new ClosePriceIndicator(series);

        Stream<Num> stream = IndicatorUtils.streamOf(indicator);
        List<Num> collectedValues = stream.collect(Collectors.toList());

        Assert.assertNotNull(stream);
        Assert.assertNotNull(collectedValues);
        Assert.assertEquals(30, collectedValues.size());
        assertNumEquals(typicalPrices[0], collectedValues.get(0));
        assertNumEquals(typicalPrices[1], collectedValues.get(1));
        assertNumEquals(typicalPrices[2], collectedValues.get(2));
        assertNumEquals(typicalPrices[10], collectedValues.get(10));
        assertNumEquals(typicalPrices[20], collectedValues.get(20));
        assertNumEquals(typicalPrices[29], collectedValues.get(29));
    }

    @Test
    public void shouldProvideStreamWithMapping() {
        ClosePriceIndicator indicator = new ClosePriceIndicator(series);

        final Function<IndicatorUtils.IndicatorContext<Num>, ValueWithTimestamp> mapFunction = (IndicatorUtils.IndicatorContext<Num> ctx) -> new ValueWithTimestamp(
                indicator.getValue(ctx.getIndex()),
                ctx.getIndicator().getBarSeries().getBar(ctx.getIndex()).getEndTime().toInstant());

        Stream<ValueWithTimestamp> stream = IndicatorUtils.streamOf(indicator, mapFunction);
        List<ValueWithTimestamp> collectedValues = stream.collect(Collectors.toList());

        Assert.assertNotNull(stream);
        Assert.assertNotNull(collectedValues);
        Assert.assertEquals(30, collectedValues.size());

    }

    static class ValueWithTimestamp {
        private final Num value;
        private final Instant timestamp;

        public ValueWithTimestamp(Num value, Instant timestamp) {
            this.value = value;
            this.timestamp = timestamp;
        }

        public Num getValue() {
            return value;
        }

        public Instant getTimestamp() {
            return timestamp;
        }
    }
}
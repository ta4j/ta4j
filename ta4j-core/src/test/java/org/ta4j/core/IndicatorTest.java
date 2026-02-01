/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class IndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    double[] typicalPrices = { 23.98, 23.92, 23.79, 23.67, 23.54, 23.36, 23.65, 23.72, 24.16, 23.91, 23.81, 23.92,
            23.74, 24.68, 24.94, 24.93, 25.10, 25.12, 25.20, 25.06, 24.50, 24.31, 24.57, 24.62, 24.49, 24.37, 24.41,
            24.35, 23.75, 24.09 };
    BarSeries data;

    public IndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        data = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(typicalPrices).build();
    }

    @Test
    public void toDouble() {
        List<Num> expectedValues = Arrays.stream(typicalPrices)
                .mapToObj(numFactory::numOf)
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
                .mapToObj(numFactory::numOf)
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

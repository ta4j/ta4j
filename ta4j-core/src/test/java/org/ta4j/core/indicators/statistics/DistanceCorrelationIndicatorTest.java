/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.List;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class DistanceCorrelationIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public DistanceCorrelationIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void returnsOneForPerfectAffineRelationship() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4).build();
        Indicator<Num> first = indicator(series, 1, 2, 3, 4);
        Indicator<Num> second = indicator(series, 2, 4, 6, 8);
        DistanceCorrelationIndicator correlation = new DistanceCorrelationIndicator(first, second, 4);

        assertTrue(correlation.getValue(2).isNaN());
        assertNumEquals(numOf(1), correlation.getValue(3), 1.0e-12);
        assertEquals(3, correlation.getCountOfUnstableBars());
    }

    @Test
    public void returnsNaNWhenDistancesHaveNoVariance() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 1, 1, 1).build();
        Indicator<Num> constant = indicator(series, 1, 1, 1, 1);
        Indicator<Num> changing = indicator(series, 1, 2, 3, 4);
        DistanceCorrelationIndicator correlation = new DistanceCorrelationIndicator(constant, changing, 4);

        assertTrue(correlation.getValue(3).isNaN());
    }

    @Test
    public void rejectsInvalidBarCount() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2).build();
        Indicator<Num> indicator = indicator(series, 1, 2);

        assertThrows(IllegalArgumentException.class, () -> new DistanceCorrelationIndicator(indicator, indicator, 1));
    }

    private Indicator<Num> indicator(BarSeries series, Number... values) {
        List<Num> nums = java.util.Arrays.stream(values).map(numFactory::numOf).toList();
        return new MockIndicator(series, nums);
    }
}

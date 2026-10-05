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
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class LaggedCorrelationIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public LaggedCorrelationIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void positiveLagMeansFirstIndicatorLeadsSecond() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5, 6).build();
        Indicator<Num> leading = indicator(series, 1, 2, 3, 4, 5, 6);
        Indicator<Num> following = indicator(series, 0, 1, 2, 3, 4, 5);
        LaggedCorrelationIndicator correlation = new LaggedCorrelationIndicator(leading, following, 5, 1);

        assertTrue(correlation.getValue(4).isNaN());
        assertNumEquals(1, correlation.getValue(5));
        assertEquals(5, correlation.getCountOfUnstableBars());
    }

    @Test
    public void negativeLagUsesOnlyKnownHistoryWhenFirstIndicatorTrailsSecond() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(0, 1, 2, 3, 4, 5).build();
        Indicator<Num> trailing = indicator(series, 0, 1, 2, 3, 4, 5);
        Indicator<Num> leading = indicator(series, 1, 2, 3, 4, 5, 6);
        LaggedCorrelationIndicator correlation = new LaggedCorrelationIndicator(trailing, leading, 5, -1);

        assertNumEquals(1, correlation.getValue(5));
    }

    @Test
    public void returnsNaNWhenLaggedWindowHasNoVariance() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 1, 1, 1, 1, 1).build();
        Indicator<Num> constant = indicator(series, 1, 1, 1, 1, 1, 1);
        Indicator<Num> changing = indicator(series, 1, 2, 3, 4, 5, 6);
        LaggedCorrelationIndicator correlation = new LaggedCorrelationIndicator(constant, changing, 5, 1);

        assertTrue(correlation.getValue(5).isNaN());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void serializesAndRestoresFromJson() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5, 6).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        SMAIndicator average = new SMAIndicator(close, 2);
        LaggedCorrelationIndicator correlation = new LaggedCorrelationIndicator(close, average, 4, 1);

        Indicator<Num> restored = (Indicator<Num>) Indicator.fromJson(series, correlation.toJson());

        assertTrue(restored instanceof LaggedCorrelationIndicator);
        assertNumEquals(correlation.getValue(5), restored.getValue(5), 1.0e-12);
    }

    @Test
    public void rejectsInvalidBarCount() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2).build();
        Indicator<Num> indicator = indicator(series, 1, 2);

        assertThrows(IllegalArgumentException.class, () -> new LaggedCorrelationIndicator(indicator, indicator, 1, 0));
    }

    @Test
    public void rejectsLagThatCannotBeIndexedSafely() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2).build();
        Indicator<Num> indicator = indicator(series, 1, 2);

        assertThrows(IllegalArgumentException.class,
                () -> new LaggedCorrelationIndicator(indicator, indicator, 2, Integer.MIN_VALUE));
    }

    private Indicator<Num> indicator(BarSeries series, Number... values) {
        List<Num> nums = java.util.Arrays.stream(values).map(numFactory::numOf).toList();
        return new MockIndicator(series, nums);
    }
}

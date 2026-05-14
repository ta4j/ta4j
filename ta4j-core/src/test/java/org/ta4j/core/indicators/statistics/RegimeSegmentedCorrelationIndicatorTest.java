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
import org.ta4j.core.indicators.helpers.FixedBooleanIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class RegimeSegmentedCorrelationIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public RegimeSegmentedCorrelationIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void correlatesOnlyActiveRegimeSamples() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5).build();
        Indicator<Num> first = indicator(series, 1, 2, 3, 4, 5);
        Indicator<Num> second = indicator(series, 2, 99, 6, 99, 10);
        Indicator<Boolean> regime = new FixedBooleanIndicator(series, true, false, true, false, true);
        RegimeSegmentedCorrelationIndicator correlation = new RegimeSegmentedCorrelationIndicator(first, second, regime,
                5);

        assertTrue(correlation.getValue(3).isNaN());
        assertNumEquals(1, correlation.getValue(4));
        assertEquals(4, correlation.getCountOfUnstableBars());
    }

    @Test
    public void returnsNaNWhenRegimeHasTooFewSamples() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5).build();
        Indicator<Num> first = indicator(series, 1, 2, 3, 4, 5);
        Indicator<Num> second = indicator(series, 2, 4, 6, 8, 10);
        Indicator<Boolean> regime = new FixedBooleanIndicator(series, false, false, false, false, true);
        RegimeSegmentedCorrelationIndicator correlation = new RegimeSegmentedCorrelationIndicator(first, second, regime,
                5);

        assertTrue(correlation.getValue(4).isNaN());
    }

    @Test
    public void returnsNaNWhenRegimeIsNeverActive() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5).build();
        Indicator<Num> first = indicator(series, 1, 2, 3, 4, 5);
        Indicator<Num> second = indicator(series, 2, 4, 6, 8, 10);
        Indicator<Boolean> regime = new FixedBooleanIndicator(series, false, false, false, false, false);
        RegimeSegmentedCorrelationIndicator correlation = new RegimeSegmentedCorrelationIndicator(first, second, regime,
                5);

        assertTrue(correlation.getValue(4).isNaN());
    }

    @Test
    public void returnsNaNWhenActiveRegimeSampleIsNaN() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5).build();
        Indicator<Num> first = new MockIndicator(series,
                List.of(numFactory.one(), numFactory.two(), NaN.NaN, numFactory.numOf(4), numFactory.numOf(5)));
        Indicator<Num> second = indicator(series, 2, 4, 6, 8, 10);
        Indicator<Boolean> regime = new FixedBooleanIndicator(series, false, false, true, true, true);
        RegimeSegmentedCorrelationIndicator correlation = new RegimeSegmentedCorrelationIndicator(first, second, regime,
                5);

        assertTrue(correlation.getValue(4).isNaN());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void serializesAndRestoresFromJson() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        SMAIndicator average = new SMAIndicator(close, 2);
        Indicator<Boolean> regime = new FixedBooleanIndicator(series, false, true, true, true, true);
        RegimeSegmentedCorrelationIndicator correlation = new RegimeSegmentedCorrelationIndicator(close, average,
                regime, 4);

        Indicator<Num> restored = (Indicator<Num>) Indicator.fromJson(series, correlation.toJson());

        assertTrue(restored instanceof RegimeSegmentedCorrelationIndicator);
        assertNumEquals(correlation.getValue(4), restored.getValue(4), 1.0e-12);
    }

    @Test
    public void rejectsInvalidBarCount() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2).build();
        Indicator<Num> indicator = indicator(series, 1, 2);
        Indicator<Boolean> regime = new FixedBooleanIndicator(series, true, true);

        assertThrows(IllegalArgumentException.class,
                () -> new RegimeSegmentedCorrelationIndicator(indicator, indicator, regime, 1));
    }

    private Indicator<Num> indicator(BarSeries series, Number... values) {
        List<Num> nums = java.util.Arrays.stream(values).map(numFactory::numOf).toList();
        return new MockIndicator(series, nums);
    }
}

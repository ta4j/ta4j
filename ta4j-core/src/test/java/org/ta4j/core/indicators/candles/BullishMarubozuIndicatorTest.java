/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.candles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.indicators.IndicatorSerializationRoundTripTestSupport.serializationSeries;
import static org.ta4j.core.indicators.IndicatorSerializationRoundTripTestSupport.stableIndexes;

import java.util.List;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class BullishMarubozuIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    public BullishMarubozuIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void detectsBullishMarubozuWithDefaults() {
        // Baseline of five body-10 candles yields a prior average body of 10, so a
        // body strictly above 10 with no shadows satisfies the pattern.
        BarSeries series = marubozuSeries(5, 10.1, 0, 0, true);
        BullishMarubozuIndicator indicator = new BullishMarubozuIndicator(series);

        assertFalse(indicator.getValue(4));
        assertTrue(indicator.getValue(5));
    }

    @Test
    public void bodyBoundaryIsStrict() {
        assertTrue(new BullishMarubozuIndicator(marubozuSeries(5, 10.1, 0, 0, true)).getValue(5));
        assertFalse(new BullishMarubozuIndicator(marubozuSeries(5, 10.0, 0, 0, true)).getValue(5));
        assertFalse(new BullishMarubozuIndicator(marubozuSeries(5, 9.9, 0, 0, true)).getValue(5));
    }

    @Test
    public void doesNotTriggerForBearishCandle() {
        assertFalse(new BullishMarubozuIndicator(marubozuSeries(5, 10.1, 0, 0, false)).getValue(5));
    }

    @Test
    public void shadowBoundaryIsInclusive() {
        assertTrue(new BullishMarubozuIndicator(marubozuSeries(5, 10.1, 1.0, 1.0, true)).getValue(5));
        assertFalse(new BullishMarubozuIndicator(marubozuSeries(5, 10.1, 1.01, 0, true)).getValue(5));
        assertFalse(new BullishMarubozuIndicator(marubozuSeries(5, 10.1, 0, 1.01, true)).getValue(5));
    }

    @Test
    public void customAveragePeriodShiftsWarmUpBoundary() {
        BarSeries series = marubozuSeries(3, 10.1, 0, 0, true);
        BullishMarubozuIndicator indicator = new BullishMarubozuIndicator(series, 3);
        assertEquals(3, indicator.getCountOfUnstableBars());

        assertFalse(indicator.getValue(2));
        assertTrue(indicator.getValue(3));
    }

    @Test
    public void rejectsAveragePeriodBelowOne() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        assertThrows(IllegalArgumentException.class, () -> new BullishMarubozuIndicator(series, 0));
    }

    @Test
    public void contextBeforeBaselineWindowDoesNotChangeResult() {
        // Pattern candle at index 6 with period 3: the baseline window is [3, 5],
        // so bars before index 3 must not influence the result.
        BarSeries control = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 3; i++) {
            addBar(control, 10, 0, 0);
        }
        for (int i = 0; i < 3; i++) {
            addBar(control, 10, 0, 0);
        }
        addBar(control, 10.1, 0, 0);

        BarSeries varied = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBar(varied, 50, 10, 10);
        addBar(varied, 2, 4, 22);
        addBar(varied, 99, 2, 9);
        for (int i = 0; i < 3; i++) {
            addBar(varied, 10, 0, 0);
        }
        addBar(varied, 10.1, 0, 0);

        BullishMarubozuIndicator controlIndicator = new BullishMarubozuIndicator(control, 3);
        BullishMarubozuIndicator variedIndicator = new BullishMarubozuIndicator(varied, 3);

        assertTrue(controlIndicator.getValue(6));
        assertTrue(variedIndicator.getValue(6));
    }

    @Test
    public void rollingSeriesWithNonzeroBeginIndex() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 10; i++) {
            addBar(series, 10, 0, 0);
        }
        for (int i = 0; i < 5; i++) {
            addBar(series, 10, 0, 0);
        }
        addBar(series, 10.1, 0, 0);
        for (int i = 0; i < 4; i++) {
            addBar(series, 10, 0, 0);
        }
        series.setMaximumBarCount(10);

        BullishMarubozuIndicator indicator = new BullishMarubozuIndicator(series);

        assertEquals(10, series.getBeginIndex());
        assertFalse(indicator.getValue(14));
        assertTrue(indicator.getValue(15));
    }

    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        BarSeries series = serializationSeries(numFactory);
        return List.of(serializationFixture(series, new BullishMarubozuIndicator(series), stableIndexes(series)),
                serializationFixture(series, new BullishMarubozuIndicator(series, 5), stableIndexes(series)));
    }

    /**
     * Builds {@code period} body-10 baseline candles followed by one pattern candle
     * with the given body, shadows, and direction (open 0/close body for bullish,
     * open body/close 0 for bearish).
     */
    private BarSeries marubozuSeries(int period, double body, double upperShadow, double lowerShadow, boolean bullish) {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < period; i++) {
            addBar(series, 10, 0, 0);
        }
        final double open = bullish ? 0 : body;
        final double close = bullish ? body : 0;
        final double high = Math.max(open, close) + upperShadow;
        final double low = Math.min(open, close) - lowerShadow;
        series.barBuilder().openPrice(open).closePrice(close).highPrice(high).lowPrice(low).add();
        return series;
    }

    private void addBar(BarSeries series, double body, double upperShadow, double lowerShadow) {
        final double open = 0;
        final double close = body;
        final double high = close + upperShadow;
        final double low = open - lowerShadow;
        series.barBuilder().openPrice(open).closePrice(close).highPrice(high).lowPrice(low).add();
    }
}

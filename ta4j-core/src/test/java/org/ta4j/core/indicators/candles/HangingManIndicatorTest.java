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

public class HangingManIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    public HangingManIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void detectsHangingManWithDefaults() {
        // Baseline of five body-10/range-10 candles yields a body threshold of
        // 0.5 * 10 = 5, a shadow threshold of 2.0 * 10 = 20, and a range threshold
        // of 0.1 * 10 = 1. Body 1, lower shadow 21, upper shadow 0, body top equal
        // to the prior high 10: hanging man.
        BarSeries series = hangingManSeries(5, 1.0, 0, 21, 10);
        HangingManIndicator indicator = new HangingManIndicator(series);

        assertFalse(indicator.getValue(4));
        assertTrue(indicator.getValue(5));
    }

    @Test
    public void bodyBoundaryIsStrict() {
        assertTrue(new HangingManIndicator(hangingManSeries(5, 4.9, 0, 21, 10)).getValue(5));
        assertFalse(new HangingManIndicator(hangingManSeries(5, 5.0, 0, 21, 10)).getValue(5));
        assertFalse(new HangingManIndicator(hangingManSeries(5, 6.0, 0, 21, 10)).getValue(5));
    }

    @Test
    public void lowerShadowBoundaryIsStrict() {
        assertTrue(new HangingManIndicator(hangingManSeries(5, 1.0, 0, 21, 10)).getValue(5));
        assertFalse(new HangingManIndicator(hangingManSeries(5, 1.0, 0, 20, 10)).getValue(5));
        assertFalse(new HangingManIndicator(hangingManSeries(5, 1.0, 0, 19, 10)).getValue(5));
    }

    @Test
    public void upperShadowBoundaryIsInclusive() {
        assertTrue(new HangingManIndicator(hangingManSeries(5, 1.0, 1.0, 21, 10)).getValue(5));
        assertFalse(new HangingManIndicator(hangingManSeries(5, 1.0, 1.01, 21, 10)).getValue(5));
    }

    @Test
    public void bodyTopNearBoundaryIsInclusive() {
        assertTrue(new HangingManIndicator(hangingManSeries(5, 1.0, 0, 21, 11.0)).getValue(5));
        assertFalse(new HangingManIndicator(hangingManSeries(5, 1.0, 0, 21, 12.0)).getValue(5));
    }

    @Test
    public void customAveragePeriodShiftsWarmUpBoundary() {
        BarSeries series = hangingManSeries(3, 1.0, 0, 21, 10);
        HangingManIndicator indicator = new HangingManIndicator(series, 3);

        assertFalse(indicator.getValue(2));
        assertTrue(indicator.getValue(3));
    }

    @Test
    public void rejectsAveragePeriodBelowOne() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        assertThrows(IllegalArgumentException.class, () -> new HangingManIndicator(series, 0));
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
        addHangingManBar(control, 1.0, 0, 21, 10);

        BarSeries varied = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBar(varied, 50, 10, 10);
        addBar(varied, 2, 4, 22);
        addBar(varied, 99, 2, 9);
        for (int i = 0; i < 3; i++) {
            addBar(varied, 10, 0, 0);
        }
        addHangingManBar(varied, 1.0, 0, 21, 10);

        HangingManIndicator controlIndicator = new HangingManIndicator(control, 3);
        HangingManIndicator variedIndicator = new HangingManIndicator(varied, 3);

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
        addHangingManBar(series, 1.0, 0, 21, 10);
        for (int i = 0; i < 4; i++) {
            addBar(series, 10, 0, 0);
        }
        series.setMaximumBarCount(10);

        HangingManIndicator indicator = new HangingManIndicator(series);

        assertEquals(10, series.getBeginIndex());
        assertFalse(indicator.getValue(14));
        assertTrue(indicator.getValue(15));
    }

    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        BarSeries series = serializationSeries(numFactory);
        return List.of(serializationFixture(series, new HangingManIndicator(series), stableIndexes(series)),
                serializationFixture(series, new HangingManIndicator(series, 5), stableIndexes(series)));
    }

    /**
     * Builds {@code period} body-10/range-10 baseline candles (prior high 10)
     * followed by one pattern candle with the given body, upper shadow, lower
     * shadow, and body top (the pattern candle is bearish: open equals the body
     * top).
     */
    private BarSeries hangingManSeries(int period, double body, double upperShadow, double lowerShadow,
            double bodyTop) {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < period; i++) {
            addBar(series, 10, 0, 0);
        }
        final double open = bodyTop;
        final double close = bodyTop - body;
        final double high = open + upperShadow;
        final double low = close - lowerShadow;
        series.barBuilder().openPrice(open).closePrice(close).highPrice(high).lowPrice(low).add();
        return series;
    }

    private void addHangingManBar(BarSeries series, double body, double upperShadow, double lowerShadow,
            double bodyTop) {
        final double open = bodyTop;
        final double close = bodyTop - body;
        final double high = open + upperShadow;
        final double low = close - lowerShadow;
        series.barBuilder().openPrice(open).closePrice(close).highPrice(high).lowPrice(low).add();
    }

    private void addBar(BarSeries series, double body, double upperShadow, double lowerShadow) {
        final double open = 0;
        final double close = body;
        final double high = close + upperShadow;
        final double low = open - lowerShadow;
        series.barBuilder().openPrice(open).closePrice(close).highPrice(high).lowPrice(low).add();
    }
}

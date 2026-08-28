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

public class ShootingStarIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    public ShootingStarIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void detectsShootingStarWithDefaults() {
        // Baseline of five body-10/range-10 candles yields a body threshold of
        // 0.5 * 10 = 5, a shadow threshold of 2.0 * 10 = 20, and a range threshold
        // of 0.1 * 10 = 1. Body 1, upper shadow 21, lower shadow 0, and an open
        // strictly above the prior close 10: shooting star.
        BarSeries series = shootingStarSeries(5, 1.0, 21, 0, 10.1);
        ShootingStarIndicator indicator = new ShootingStarIndicator(series);

        assertFalse(indicator.getValue(4));
        assertTrue(indicator.getValue(5));
    }

    @Test
    public void bodyBoundaryIsStrict() {
        assertTrue(new ShootingStarIndicator(shootingStarSeries(5, 4.9, 21, 0, 10.1)).getValue(5));
        assertFalse(new ShootingStarIndicator(shootingStarSeries(5, 5.0, 21, 0, 10.1)).getValue(5));
        assertFalse(new ShootingStarIndicator(shootingStarSeries(5, 6.0, 21, 0, 10.1)).getValue(5));
    }

    @Test
    public void upperShadowBoundaryIsStrict() {
        assertTrue(new ShootingStarIndicator(shootingStarSeries(5, 1.0, 21, 0, 10.1)).getValue(5));
        assertFalse(new ShootingStarIndicator(shootingStarSeries(5, 1.0, 20, 0, 10.1)).getValue(5));
        assertFalse(new ShootingStarIndicator(shootingStarSeries(5, 1.0, 19, 0, 10.1)).getValue(5));
    }

    @Test
    public void lowerShadowBoundaryIsInclusive() {
        assertTrue(new ShootingStarIndicator(shootingStarSeries(5, 1.0, 21, 1.0, 10.1)).getValue(5));
        assertFalse(new ShootingStarIndicator(shootingStarSeries(5, 1.0, 21, 1.01, 10.1)).getValue(5));
    }

    @Test
    public void gapBoundaryIsStrict() {
        assertTrue(new ShootingStarIndicator(shootingStarSeries(5, 1.0, 21, 0, 10.1)).getValue(5));
        assertFalse(new ShootingStarIndicator(shootingStarSeries(5, 1.0, 21, 0, 10.0)).getValue(5));
        assertFalse(new ShootingStarIndicator(shootingStarSeries(5, 1.0, 21, 0, 9.9)).getValue(5));
    }

    @Test
    public void customAveragePeriodShiftsWarmUpBoundary() {
        BarSeries series = shootingStarSeries(3, 1.0, 21, 0, 10.1);
        ShootingStarIndicator indicator = new ShootingStarIndicator(series, 3);

        assertFalse(indicator.getValue(2));
        assertTrue(indicator.getValue(3));
    }

    @Test
    public void rejectsAveragePeriodBelowOne() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        assertThrows(IllegalArgumentException.class, () -> new ShootingStarIndicator(series, 0));
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
        addShootingStarBar(control, 1.0, 21, 0, 10.1);

        BarSeries varied = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBar(varied, 50, 10, 10);
        addBar(varied, 2, 4, 22);
        addBar(varied, 99, 2, 9);
        for (int i = 0; i < 3; i++) {
            addBar(varied, 10, 0, 0);
        }
        addShootingStarBar(varied, 1.0, 21, 0, 10.1);

        ShootingStarIndicator controlIndicator = new ShootingStarIndicator(control, 3);
        ShootingStarIndicator variedIndicator = new ShootingStarIndicator(varied, 3);

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
        addShootingStarBar(series, 1.0, 21, 0, 10.1);
        for (int i = 0; i < 4; i++) {
            addBar(series, 10, 0, 0);
        }
        series.setMaximumBarCount(10);

        ShootingStarIndicator indicator = new ShootingStarIndicator(series);

        assertEquals(10, series.getBeginIndex());
        assertFalse(indicator.getValue(14));
        assertTrue(indicator.getValue(15));
    }

    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        BarSeries series = serializationSeries(numFactory);
        return List.of(serializationFixture(series, new ShootingStarIndicator(series), stableIndexes(series)),
                serializationFixture(series, new ShootingStarIndicator(series, 5), stableIndexes(series)));
    }

    /**
     * Builds {@code period} body-10/range-10 baseline candles (prior close 10)
     * followed by one pattern candle with the given body, upper shadow, lower
     * shadow, and open price (the pattern candle is bearish: close equals open
     * minus the body).
     */
    private BarSeries shootingStarSeries(int period, double body, double upperShadow, double lowerShadow, double open) {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < period; i++) {
            addBar(series, 10, 0, 0);
        }
        final double close = open - body;
        final double high = open + upperShadow;
        final double low = close - lowerShadow;
        series.barBuilder().openPrice(open).closePrice(close).highPrice(high).lowPrice(low).add();
        return series;
    }

    private void addShootingStarBar(BarSeries series, double body, double upperShadow, double lowerShadow,
            double open) {
        final double close = open - body;
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

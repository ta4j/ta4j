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

import java.time.Duration;
import java.util.List;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.NonFiniteBar;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class HammerIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    public HammerIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void detectsHammerWithDefaults() {
        // Baseline of five body-10/range-10 candles yields a body threshold of
        // 0.5 * 10 = 5, a shadow threshold of 2.0 * 10 = 20, and a range threshold
        // of 0.1 * 10 = 1. Body 1, lower shadow 21, upper shadow 0, body bottom
        // equal to the prior low 0: hammer.
        BarSeries series = hammerSeries(5, 1.0, 0, 21, 0);
        HammerIndicator indicator = new HammerIndicator(series);

        assertFalse(indicator.getValue(4));
        assertTrue(indicator.getValue(5));
    }

    @Test
    public void bodyBoundaryIsStrict() {
        assertTrue(new HammerIndicator(hammerSeries(5, 4.9, 0, 21, 0)).getValue(5));
        assertFalse(new HammerIndicator(hammerSeries(5, 5.0, 0, 21, 0)).getValue(5));
        assertFalse(new HammerIndicator(hammerSeries(5, 6.0, 0, 21, 0)).getValue(5));
    }

    @Test
    public void lowerShadowBoundaryIsStrict() {
        assertTrue(new HammerIndicator(hammerSeries(5, 1.0, 0, 21, 0)).getValue(5));
        assertFalse(new HammerIndicator(hammerSeries(5, 1.0, 0, 20, 0)).getValue(5));
        assertFalse(new HammerIndicator(hammerSeries(5, 1.0, 0, 19, 0)).getValue(5));
    }

    @Test
    public void upperShadowBoundaryIsInclusive() {
        assertTrue(new HammerIndicator(hammerSeries(5, 1.0, 1.0, 21, 0)).getValue(5));
        assertFalse(new HammerIndicator(hammerSeries(5, 1.0, 1.01, 21, 0)).getValue(5));
    }

    @Test
    public void bodyBottomNearBoundaryIsInclusive() {
        assertTrue(new HammerIndicator(hammerSeries(5, 1.0, 0, 21, 1.0)).getValue(5));
        assertFalse(new HammerIndicator(hammerSeries(5, 1.0, 0, 21, 2.0)).getValue(5));
    }

    @Test
    public void customAveragePeriodShiftsWarmUpBoundary() {
        BarSeries series = hammerSeries(3, 1.0, 0, 21, 0);
        HammerIndicator indicator = new HammerIndicator(series, 3);
        assertEquals(3, indicator.getCountOfUnstableBars());

        assertFalse(indicator.getValue(2));
        assertTrue(indicator.getValue(3));
    }

    @Test
    public void rejectsAveragePeriodBelowOne() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        assertThrows(IllegalArgumentException.class, () -> new HammerIndicator(series, 0));
    }

    @Test
    public void nonFinitePriorLowIsNotAHammer() {
        DoubleNumFactory doubleFactory = DoubleNumFactory.getInstance();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(doubleFactory).build();
        for (int i = 0; i < 4; i++) {
            addBar(series, 10, 0, 0);
        }
        series.addBar(new NonFiniteBar(series.getBar(series.getEndIndex()).getEndTime().minus(Duration.ofHours(12)),
                doubleFactory.numOf(0), doubleFactory.numOf(10), doubleFactory.numOf(Double.NaN),
                doubleFactory.numOf(10)));
        addHammerBar(series, 1.0, 0, 21, 0);

        assertFalse(new HammerIndicator(series).getValue(5));
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
        addHammerBar(control, 1.0, 0, 21, 0);

        BarSeries varied = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBar(varied, 50, 10, 10);
        addBar(varied, 2, 4, 22);
        addBar(varied, 99, 2, 9);
        for (int i = 0; i < 3; i++) {
            addBar(varied, 10, 0, 0);
        }
        addHammerBar(varied, 1.0, 0, 21, 0);

        HammerIndicator controlIndicator = new HammerIndicator(control, 3);
        HammerIndicator variedIndicator = new HammerIndicator(varied, 3);

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
        addHammerBar(series, 1.0, 0, 21, 0);
        for (int i = 0; i < 4; i++) {
            addBar(series, 10, 0, 0);
        }
        series.setMaximumBarCount(10);

        HammerIndicator indicator = new HammerIndicator(series);

        assertEquals(10, series.getBeginIndex());
        assertFalse(indicator.getValue(14));
        assertTrue(indicator.getValue(15));
    }

    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        BarSeries series = serializationSeries(numFactory);
        return List.of(serializationFixture(series, new HammerIndicator(series), stableIndexes(series)),
                serializationFixture(series, new HammerIndicator(series, 5), stableIndexes(series)));
    }

    /**
     * Builds {@code period} body-10/range-10 baseline candles (prior low 0)
     * followed by one pattern candle with the given body, upper shadow, lower
     * shadow, and body bottom (the pattern candle is bearish: close equals the body
     * bottom).
     */
    private BarSeries hammerSeries(int period, double body, double upperShadow, double lowerShadow, double bodyBottom) {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < period; i++) {
            addBar(series, 10, 0, 0);
        }
        final double open = bodyBottom + body;
        final double close = bodyBottom;
        final double high = open + upperShadow;
        final double low = close - lowerShadow;
        series.barBuilder().openPrice(open).closePrice(close).highPrice(high).lowPrice(low).add();
        return series;
    }

    private void addHammerBar(BarSeries series, double body, double upperShadow, double lowerShadow,
            double bodyBottom) {
        final double open = bodyBottom + body;
        final double close = bodyBottom;
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

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
import org.ta4j.core.indicators.helpers.BooleanTransformIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.NonFiniteBar;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class DojiIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    public DojiIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void detectsDojiAtInclusiveBoundaryWithDefaults() {
        // Baseline of five range-10 candles yields a prior average range of 10, so
        // the doji threshold is 0.1 * 10 = 1.0. A body of exactly 1.0 is a doji.
        BarSeries series = dojiSeries(5, 1.0);
        DojiIndicator indicator = new DojiIndicator(series);

        assertFalse(indicator.getValue(4));
        assertTrue(indicator.getValue(5));
    }

    @Test
    public void rejectsBodyAboveThreshold() {
        BarSeries series = dojiSeries(5, 1.01);

        assertFalse(new DojiIndicator(series).getValue(5));
    }

    @Test
    public void boundaryIsInclusive() {
        assertTrue(new DojiIndicator(dojiSeries(5, 1.0)).getValue(5));
        assertFalse(new DojiIndicator(dojiSeries(5, 1.01)).getValue(5));
    }

    @Test
    public void customAveragePeriodShiftsWarmUpBoundary() {
        BarSeries series = dojiSeries(3, 1.0);
        DojiIndicator indicator = new DojiIndicator(series, 3, 0.1);
        assertEquals(3, indicator.getCountOfUnstableBars());

        assertFalse(indicator.getValue(2));
        assertTrue(indicator.getValue(3));
    }

    @Test
    public void customRangeFactorScalesThreshold() {
        assertTrue(new DojiIndicator(dojiSeries(5, 2.0), 5, 0.2).getValue(5));
        assertFalse(new DojiIndicator(dojiSeries(5, 2.01), 5, 0.2).getValue(5));
    }

    @Test
    public void zeroBodyAgainstZeroBaselineIsDoji() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 6; i++) {
            addBar(series, 0, 0, 0);
        }

        assertTrue(new DojiIndicator(series).getValue(5));
    }

    @Test
    public void signedZeroRangeFactorBehavesLikeZero() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 6; i++) {
            addBar(series, 0, 0, 0);
        }

        assertTrue(new DojiIndicator(series, 5, -0.0d).getValue(5));
    }

    @Test
    public void oversizedRangeFactorTreatsEveryFiniteBodyAsDoji() {
        // rangeFactor * averageRange overflows the DoubleNum threshold product to
        // positive infinity; the threshold is then unbounded, so any finite body
        // qualifies.
        assertTrue(new DojiIndicator(dojiSeries(5, 2.0), 5, Double.MAX_VALUE).getValue(5));
    }

    @Test
    public void rejectsInvalidParameters() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        assertThrows(IllegalArgumentException.class, () -> new DojiIndicator(series, 0, 0.1));
        assertThrows(IllegalArgumentException.class, () -> new DojiIndicator(series, 5, -0.1));
        assertThrows(IllegalArgumentException.class, () -> new DojiIndicator(series, 5, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new DojiIndicator(series, 5, Double.POSITIVE_INFINITY));
    }

    @Test
    public void nonFiniteBodyIsNotADoji() {
        DoubleNumFactory doubleFactory = DoubleNumFactory.getInstance();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(doubleFactory).build();
        for (int i = 0; i < 5; i++) {
            addBar(series, 10, 0, 0);
        }
        series.addBar(new NonFiniteBar(series.getBar(series.getEndIndex()).getEndTime().minus(Duration.ofHours(12)),
                doubleFactory.numOf(Double.NaN), doubleFactory.numOf(10), doubleFactory.numOf(0),
                doubleFactory.numOf(Double.NaN)));

        assertFalse(new DojiIndicator(series).getValue(5));
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
        addBar(control, 1.0, 0, 0);

        BarSeries varied = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBar(varied, 50, 10, 10);
        addBar(varied, 2, 4, 22);
        addBar(varied, 99, 2, 9);
        for (int i = 0; i < 3; i++) {
            addBar(varied, 10, 0, 0);
        }
        addBar(varied, 1.0, 0, 0);

        DojiIndicator controlIndicator = new DojiIndicator(control, 3, 0.1);
        DojiIndicator variedIndicator = new DojiIndicator(varied, 3, 0.1);

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
        addBar(series, 1.0, 0, 0);
        for (int i = 0; i < 4; i++) {
            addBar(series, 10, 0, 0);
        }
        series.setMaximumBarCount(10);

        DojiIndicator indicator = new DojiIndicator(series);

        assertEquals(10, series.getBeginIndex());
        assertFalse(indicator.getValue(14));
        assertTrue(indicator.getValue(15));
    }

    @Test
    public void dependentCacheInvalidatesWhenHeadAdvanceLeavesBaseline() {
        // A cached dependent indicator must observe a pattern becoming false when
        // the head advances past its baseline window, even when its own cached
        // entry survives the eviction.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 5; i++) {
            addBar(series, 10, 0, 0);
        }
        addBar(series, 1.0, 0, 0); // index 5: doji at the inclusive boundary
        DojiIndicator indicator = new DojiIndicator(series);
        BooleanTransformIndicator<Boolean> dependent = new BooleanTransformIndicator<>(indicator, value -> value);

        assertTrue(indicator.getValue(5));
        assertTrue(dependent.getValue(5));

        // Slide the head past the [0, 4] baseline window of index 5.
        series.setMaximumBarCount(5);
        assertEquals(1, series.getBeginIndex());

        assertFalse(indicator.getValue(5));
        assertFalse(dependent.getValue(5));
    }

    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        BarSeries series = serializationSeries(numFactory);
        return List.of(serializationFixture(series, new DojiIndicator(series), stableIndexes(series)),
                serializationFixture(series, new DojiIndicator(series, 5, 0.1), stableIndexes(series)));
    }

    /**
     * Builds {@code averagePeriod} range-10 baseline candles followed by one candle
     * with the given body.
     */
    private BarSeries dojiSeries(int averagePeriod, double body) {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < averagePeriod; i++) {
            addBar(series, 10, 0, 0);
        }
        addBar(series, body, 0, 0);
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

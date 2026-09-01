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
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class EveningStarIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    /**
     * Pattern index: bars 0-9 adaptive baseline, 10-12 pattern.
     */
    private static final int PATTERN_INDEX = 12;

    public EveningStarIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void matchesValidEveningStar() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 20, 30, 31, 19); // first: bullish long body
        addBar(series, 31, 31.5, 32, 30.5); // star: short body, real body gaps up
        addBar(series, 28, 24, 29, 23); // third: bearish long body (4 > baseline 3.3), close below 50% penetration (25)

        assertTrue(new EveningStarIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void nonFiniteThirdCandleDoesNotMatch() {
        NumFactory doubleFactory = DoubleNumFactory.getInstance();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(doubleFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 20, 30, 31, 19); // first: bullish long body
        addBar(series, 31, 31.5, 32, 30.5); // star: short body, real body gaps up
        // Third candle is bearish and its finite close reaches the penetration
        // threshold, but its open is positive infinity: undefined data.
        addBar(series, Double.POSITIVE_INFINITY, 24, Double.POSITIVE_INFINITY, 23);

        assertFalse(new EveningStarIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void penetrationLevelStaysFiniteWhenFirstBodyOverflows() {
        // A DoubleNum bullish first candle from -MAX to MAX/2 has a body
        // magnitude of 1.5 * MAX, which overflows to positive infinity. The
        // penetration level must still come out as the finite midpoint MAX/4.
        // DecimalNum does not overflow, so this test is DoubleNum-only.
        NumFactory doubleFactory = DoubleNumFactory.getInstance();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(doubleFactory).build();
        addBaselineBars(series, 5);
        addBar(series, -Double.MAX_VALUE, Double.MAX_VALUE / 2, Double.MAX_VALUE / 2, -Double.MAX_VALUE);
        addBar(series, 0.75 * Double.MAX_VALUE, 0.75 * Double.MAX_VALUE - 1, 0.75 * Double.MAX_VALUE,
                0.75 * Double.MAX_VALUE - 1);
        addBar(series, Double.MAX_VALUE / 8, -Double.MAX_VALUE / 2, Double.MAX_VALUE / 8, -Double.MAX_VALUE / 2);

        assertTrue(new EveningStarIndicator(series, 1, 0.5).getValue(7));
    }

    @Test
    public void thirdBarMustHaveLongBody() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 20, 30, 31, 19); // first: bullish long body
        addBar(series, 31, 31.5, 32, 30.5); // star: short body, real body gaps up
        addBar(series, 25, 24.5, 26, 24); // third: bearish, close at penetration, body 0.5 < baseline 2

        assertFalse(new EveningStarIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void firstBarMustBeBullish() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 30, 20, 31, 19); // first: bearish
        addBar(series, 31, 31.5, 32, 30.5);
        addBar(series, 26, 24, 27, 23);

        assertFalse(new EveningStarIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void firstBarMustHaveLongBody() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 21, 20, 22, 19); // first: body 1, not above the average body of 2
        addBar(series, 31, 31.5, 32, 30.5);
        addBar(series, 26, 24, 27, 23);

        assertFalse(new EveningStarIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void starMustHaveShortBody() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 20, 30, 31, 19);
        addBar(series, 31.2, 33, 33.5, 30.5); // star: body 1.8, short-body threshold is 1.8
        addBar(series, 26, 24, 27, 23);

        assertFalse(new EveningStarIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void starBodyMustGapStrictlyAboveFirstBody() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 20, 30, 31, 19);
        addBar(series, 30, 30.5, 31, 29.5); // star: body bottom 30, not above first body top 30
        addBar(series, 26, 24, 27, 23);

        assertFalse(new EveningStarIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void starGapBoundaryIsStrict() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 20, 30, 31, 19);
        addBar(series, 29.5, 30, 30.5, 29); // star: body bottom exactly at first body top 30
        addBar(series, 26, 24, 27, 23);

        assertFalse(new EveningStarIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void thirdBarMustBeBearish() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 20, 30, 31, 19);
        addBar(series, 31, 31.5, 32, 30.5);
        addBar(series, 20, 24, 25, 19.5); // third: bullish, close still at 24

        assertFalse(new EveningStarIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void signedZeroEndpointsDoNotFormAStrictGap() {
        // The first body top is -0.0 and the star body bottom is +0.0:
        // numerically equal endpoints must not satisfy the strict real-body
        // gap, which DoubleNum would otherwise accept because it orders
        // +0.0 above -0.0.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, -5, -0.0, 1, -6); // first: bullish long, body top -0.0
        addBar(series, 0.0, 1, 2, -1); // star: short, body bottom +0.0
        addBar(series, 1, -6, 2, -7); // third: bearish long close below the level

        assertFalse(new EveningStarIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void thirdCloseAbovePenetrationLevelFails() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 20, 30, 31, 19);
        addBar(series, 31, 31.5, 32, 30.5);
        addBar(series, 27, 25.5, 28, 24.5); // third: close above penetration level 25

        assertFalse(new EveningStarIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void thirdCloseExactlyAtPenetrationLevelMatches() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 20, 30, 31, 19);
        addBar(series, 31, 31.5, 32, 30.5);
        addBar(series, 29, 25, 30, 24); // third: close exactly at penetration level 25

        assertTrue(new EveningStarIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void customPenetrationBoundaryUsesFactoryArithmetic() {
        NumFactory highPrecisionFactory = DecimalNumFactory.getInstance(34);
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(highPrecisionFactory).build();
        for (int i = 0; i < 3; i++) {
            addBar(series, 1, 1.2, 1.3, 0.9);
        }
        addBar(series, 0.3, 1.3, 1.4, 0.2); // first: bullish long body
        addBar(series, 1.4, 1.5, 1.55, 1.35); // star: short body, real body gaps up
        addBar(series, 1.2, 0.4, 1.3, 0.3); // third: bearish close at the 90% penetration boundary

        assertTrue(new EveningStarIndicator(series, 3, 0.9).getValue(5));
    }

    @Test
    public void contextBeforeBaselineDoesNotChangeResult() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 5; i++) {
            addBar(series, 50, 100, 120, 40); // free context: outside pattern and baseline windows
        }
        addBaselineBars(series, 5);
        addBar(series, 20, 30, 31, 19);
        addBar(series, 31, 31.5, 32, 30.5);
        addBar(series, 28, 24, 29, 23);

        assertTrue(new EveningStarIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void contextAfterPatternDoesNotChangeResult() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 20, 30, 31, 19);
        addBar(series, 31, 31.5, 32, 30.5);
        addBar(series, 28, 24, 29, 23);
        addBar(series, 50, 100, 120, 40); // outside the pattern window

        assertTrue(new EveningStarIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void falseBeforeWarmUpBoundaryAndTrueAtBoundary() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 5);
        addBar(series, 20, 30, 31, 19); // index 5: first
        addBar(series, 31, 31.5, 32, 30.5); // index 6: star
        addBar(series, 28, 24, 29, 23); // index 7: third

        EveningStarIndicator indicator = new EveningStarIndicator(series);
        assertEquals(7, indicator.getCountOfUnstableBars());
        for (int i = 0; i < indicator.getCountOfUnstableBars(); i++) {
            assertFalse("expected false at " + i, indicator.getValue(i));
        }
        assertTrue(indicator.getValue(7));
    }

    @Test
    public void matchesWithNonZeroBeginIndexAtWarmUpBoundary() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10); // indexes 0-9, evicted
        addBaselineBars(series, 5); // indexes 10-14: baseline of the retained series
        addBar(series, 20, 30, 31, 19); // index 15: first
        addBar(series, 31, 31.5, 32, 30.5); // index 16: star
        addBar(series, 28, 24, 29, 23); // index 17: third
        addBaselineBars(series, 2);
        series.setMaximumBarCount(10);

        EveningStarIndicator indicator = new EveningStarIndicator(series);
        assertFalse(indicator.getValue(16));
        assertTrue(indicator.getValue(17));
    }

    @Test
    public void customPenetrationShiftsPenetrationLevel() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 3);
        addBar(series, 20, 25, 26, 19); // index 3: first, body 5 -> level 20 at penetration 1.0
        addBar(series, 31, 31.5, 32, 30.5); // index 4: star
        addBar(series, 21, 20.5, 22, 20); // index 5: third, close 20.5 above the full-body level 20

        EveningStarIndicator indicator = new EveningStarIndicator(series, 3, 1.0);
        assertFalse(indicator.getValue(5));

        addBar(series, 20, 25, 26, 19); // index 6: first of a fresh window
        addBar(series, 31, 31.5, 32, 30.5); // index 7: star
        addBar(series, 22.5, 19.5, 23, 19); // index 8: third, body 3 above baseline average 2, close below level 20
        assertTrue(indicator.getValue(8));
    }

    @Test
    public void rejectsInvalidAveragePeriod() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        assertThrows(IllegalArgumentException.class, () -> new EveningStarIndicator(series, 0, 0.5));
    }

    @Test
    public void rejectsInvalidPenetration() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        assertThrows(IllegalArgumentException.class, () -> new EveningStarIndicator(series, 5, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new EveningStarIndicator(series, 5, 0));
        assertThrows(IllegalArgumentException.class, () -> new EveningStarIndicator(series, 5, -0.5));
        assertThrows(IllegalArgumentException.class, () -> new EveningStarIndicator(series, 5, 1.5));
    }

    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        BarSeries series = serializationSeries(numFactory);
        return List.of(serializationFixture(series, new EveningStarIndicator(series), stableIndexes(series)),
                serializationFixture(series, new EveningStarIndicator(series, 3, 0.75), stableIndexes(series)));
    }

    private void addBaselineBars(BarSeries series, int count) {
        for (int i = 0; i < count; i++) {
            addBar(series, 10, 12, 13, 9); // bullish, body 2, range 4
        }
    }

    private void addBar(BarSeries series, double open, double close, double high, double low) {
        series.barBuilder().openPrice(open).closePrice(close).highPrice(high).lowPrice(low).add();
    }
}

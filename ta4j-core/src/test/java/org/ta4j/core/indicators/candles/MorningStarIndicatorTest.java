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
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class MorningStarIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    /**
     * Pattern index: bars 0-9 adaptive baseline, 10-12 pattern.
     */
    private static final int PATTERN_INDEX = 12;

    public MorningStarIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void matchesValidMorningStar() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 30, 20, 31, 19); // first: bearish long body
        addBar(series, 18, 18.5, 19, 17); // star: short body, real body gaps down
        addBar(series, 19, 26, 27, 18); // third: bullish close above 50% penetration (25)

        assertTrue(new MorningStarIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void nonFiniteThirdCandleDoesNotMatch() {
        NumFactory doubleFactory = DoubleNumFactory.getInstance();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(doubleFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 30, 20, 31, 19); // first: bearish long body
        addBar(series, 18, 18.5, 19, 17); // star: short body, real body gaps down
        // Third candle is bullish and its finite close reaches the penetration
        // threshold, but its open is negative infinity: undefined data.
        addBar(series, Double.NEGATIVE_INFINITY, 26, 27, Double.NEGATIVE_INFINITY);

        assertFalse(new MorningStarIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void firstBarMustBeBearish() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 20, 30, 31, 19); // first: bullish
        addBar(series, 18, 18.5, 19, 17);
        addBar(series, 19, 26, 27, 18);

        assertFalse(new MorningStarIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void firstBarMustHaveLongBody() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 21, 20, 22, 19); // first: body 1, not above the average body of 2
        addBar(series, 18, 18.5, 19, 17);
        addBar(series, 19, 26, 27, 18);

        assertFalse(new MorningStarIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void starMustHaveShortBody() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 30, 20, 31, 19);
        addBar(series, 17, 18.8, 19, 16.5); // star: body 1.8, short-body threshold is 1.8
        addBar(series, 19, 26, 27, 18);

        assertFalse(new MorningStarIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void starBodyMustGapStrictlyBelowFirstBody() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 30, 20, 31, 19);
        addBar(series, 20, 20.5, 21, 19.5); // star: body top 20.5, not below first body bottom 20
        addBar(series, 19, 26, 27, 18);

        assertFalse(new MorningStarIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void starGapBoundaryIsStrict() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 30, 20, 31, 19);
        addBar(series, 19.5, 20, 20.5, 19); // star: body top exactly at first body bottom 20
        addBar(series, 19, 26, 27, 18);

        assertFalse(new MorningStarIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void thirdBarMustBeBullish() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 30, 20, 31, 19);
        addBar(series, 18, 18.5, 19, 17);
        addBar(series, 27, 26, 28, 25); // third: bearish, close still at 26

        assertFalse(new MorningStarIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void thirdCloseBelowPenetrationLevelFails() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 30, 20, 31, 19);
        addBar(series, 18, 18.5, 19, 17);
        addBar(series, 19, 24.9, 27, 18); // third: close below penetration level 25

        assertFalse(new MorningStarIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void thirdBarMustHaveLongBody() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 30, 20, 31, 19); // first: bearish long body
        addBar(series, 18, 18.5, 19, 17); // star: short body, real body gaps down
        addBar(series, 24.5, 25, 26, 24); // third: bullish, close at penetration, body 0.5 < baseline 2

        assertFalse(new MorningStarIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void thirdCloseExactlyAtPenetrationLevelMatches() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 30, 20, 31, 19);
        addBar(series, 18, 18.5, 19, 17);
        addBar(series, 19, 25, 27, 18); // third: close exactly at penetration level 25

        assertTrue(new MorningStarIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void contextBeforeBaselineDoesNotChangeResult() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 5; i++) {
            addBar(series, 50, 100, 120, 40); // free context: outside pattern and baseline windows
        }
        addBaselineBars(series, 5);
        addBar(series, 30, 20, 31, 19);
        addBar(series, 18, 18.5, 19, 17);
        addBar(series, 19, 26, 27, 18);

        assertTrue(new MorningStarIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void contextAfterPatternDoesNotChangeResult() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 30, 20, 31, 19);
        addBar(series, 18, 18.5, 19, 17);
        addBar(series, 19, 26, 27, 18);
        addBar(series, 50, 100, 120, 40); // outside the pattern window

        assertTrue(new MorningStarIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void falseBeforeWarmUpBoundaryAndTrueAtBoundary() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 5);
        addBar(series, 30, 20, 31, 19); // index 5: first
        addBar(series, 18, 18.5, 19, 17); // index 6: star
        addBar(series, 19, 26, 27, 18); // index 7: third

        MorningStarIndicator indicator = new MorningStarIndicator(series);
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
        addBar(series, 30, 20, 31, 19); // index 15: first
        addBar(series, 18, 18.5, 19, 17); // index 16: star
        addBar(series, 19, 26, 27, 18); // index 17: third
        addBaselineBars(series, 2);
        series.setMaximumBarCount(10);

        MorningStarIndicator indicator = new MorningStarIndicator(series);
        assertFalse(indicator.getValue(16));
        assertTrue(indicator.getValue(17));
    }

    @Test
    public void customPenetrationShiftsPenetrationLevel() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 3);
        addBar(series, 25, 20, 26, 19); // index 3: first, body 5 -> level 25 at penetration 1.0
        addBar(series, 18, 18.5, 19, 17); // index 4: star
        addBar(series, 24, 24.5, 25.5, 23); // index 5: third, close 24.5 below the full-body level 25

        MorningStarIndicator indicator = new MorningStarIndicator(series, 3, 1.0);
        assertFalse(indicator.getValue(5));

        addBar(series, 25, 20, 26, 19); // index 6: first of a fresh window
        addBar(series, 18, 18.5, 19, 17); // index 7: star
        addBar(series, 23.5, 26, 27, 23); // index 8: third, body 2.5 above baseline average 2, close above level 25
        assertTrue(indicator.getValue(8));
    }

    @Test
    public void rejectsInvalidAveragePeriod() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        assertThrows(IllegalArgumentException.class, () -> new MorningStarIndicator(series, 0, 0.5));
    }

    @Test
    public void rejectsInvalidPenetration() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        assertThrows(IllegalArgumentException.class, () -> new MorningStarIndicator(series, 5, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new MorningStarIndicator(series, 5, 0));
        assertThrows(IllegalArgumentException.class, () -> new MorningStarIndicator(series, 5, -0.5));
        assertThrows(IllegalArgumentException.class, () -> new MorningStarIndicator(series, 5, 1.5));
    }

    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        BarSeries series = serializationSeries(numFactory);
        return List.of(serializationFixture(series, new MorningStarIndicator(series), stableIndexes(series)),
                serializationFixture(series, new MorningStarIndicator(series, 3, 0.75), stableIndexes(series)));
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

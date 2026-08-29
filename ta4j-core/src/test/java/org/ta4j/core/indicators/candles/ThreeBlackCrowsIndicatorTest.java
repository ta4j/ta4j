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

public class ThreeBlackCrowsIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    /** Pattern index: bars 0-8 baseline, white at 9, three crows at 10-12. */
    private static final int PATTERN_INDEX = 12;

    public ThreeBlackCrowsIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void matchesValidThreeBlackCrows() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 9);
        addBar(series, 19, 22, 23, 19); // index 9: white candle, range 4 keeps the crow threshold at 0.4
        addBar(series, 21, 18, 21, 17.9); // index 10: first crow, lower shadow 0.1
        addBar(series, 20.5, 17, 20.5, 16.95); // index 11: second crow, declining
        addBar(series, 19, 16, 19, 15.9); // index 12: third crow, declining

        assertTrue(new ThreeBlackCrowsIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void whiteCandleMustBeBullish() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 9);
        addBar(series, 22, 19, 23, 18); // index 9: white candle, bearish
        addBar(series, 21, 18, 21, 17.9);
        addBar(series, 20.5, 17, 20.5, 16.95);
        addBar(series, 19, 16, 19, 15.9);

        assertFalse(new ThreeBlackCrowsIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void firstCrowMustBeBearish() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 9);
        addBar(series, 19, 22, 23, 19);
        addBar(series, 18, 21, 22, 17.5); // index 10: first crow, bullish
        addBar(series, 20.5, 17, 20.5, 16.95);
        addBar(series, 19, 16, 19, 15.9);

        assertFalse(new ThreeBlackCrowsIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void firstCrowMustHaveShortLowerShadow() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 9);
        addBar(series, 19, 22, 23, 19);
        addBar(series, 21, 18, 21, 17); // index 10: lower shadow 1.0, above 0.1 x 4.0
        addBar(series, 20.5, 17, 20.5, 16.95);
        addBar(series, 19, 16, 19, 15.9);

        assertFalse(new ThreeBlackCrowsIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void firstCrowOpenBoundaryIsStrict() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 9);
        addBar(series, 19, 22, 23, 19);
        addBar(series, 23, 18, 23, 17.9); // index 10: open exactly at the white high 23
        addBar(series, 20.5, 17, 20.5, 16.95);
        addBar(series, 19, 16, 19, 15.9);

        assertFalse(new ThreeBlackCrowsIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void secondCrowOpenMustBeInsidePreviousBody() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 9);
        addBar(series, 19, 22, 23, 19);
        addBar(series, 21, 18, 21, 17.9);
        addBar(series, 22, 17, 22, 16.95); // index 11: open above the previous open 21
        addBar(series, 19, 16, 19, 15.9);

        assertFalse(new ThreeBlackCrowsIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void secondCrowCloseMustDecline() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 9);
        addBar(series, 19, 22, 23, 19);
        addBar(series, 21, 18, 21, 17.9);
        addBar(series, 20.5, 18.5, 20.5, 16.95); // index 11: close not below 18
        addBar(series, 19, 16, 19, 15.9);

        assertFalse(new ThreeBlackCrowsIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void thirdCrowOpenMustBeInsidePreviousBody() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 9);
        addBar(series, 19, 22, 23, 19);
        addBar(series, 21, 18, 21, 17.9);
        addBar(series, 20.5, 17, 20.5, 16.95);
        addBar(series, 16.5, 16, 16.5, 15.9); // index 12: open below the previous close 17

        assertFalse(new ThreeBlackCrowsIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void thirdCrowCloseMustDecline() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 9);
        addBar(series, 19, 22, 23, 19);
        addBar(series, 21, 18, 21, 17.9);
        addBar(series, 20.5, 17, 20.5, 16.95);
        addBar(series, 19, 17.5, 19, 15.9); // index 12: close not below 17

        assertFalse(new ThreeBlackCrowsIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void shortShadowBoundaryIsInclusive() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 9);
        addBar(series, 19, 22, 23, 19);
        addBar(series, 21, 18, 21, 17.6); // index 10: lower shadow exactly 0.4 = 0.1 x range 4
        addBar(series, 20.5, 17, 20.5, 16.95);
        addBar(series, 19, 16, 19, 15.9);

        assertTrue(new ThreeBlackCrowsIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void contextBeforeBaselineDoesNotChangeResult() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 5; i++) {
            addBar(series, 50, 100, 120, 40); // free context: outside pattern and baseline windows
        }
        addBaselineBars(series, 4);
        addBar(series, 19, 22, 23, 19);
        addBar(series, 21, 18, 21, 17.9);
        addBar(series, 20.5, 17, 20.5, 16.95);
        addBar(series, 19, 16, 19, 15.9);

        assertTrue(new ThreeBlackCrowsIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void contextAfterPatternDoesNotChangeResult() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 9);
        addBar(series, 19, 22, 23, 19);
        addBar(series, 21, 18, 21, 17.9);
        addBar(series, 20.5, 17, 20.5, 16.95);
        addBar(series, 19, 16, 19, 15.9);
        addBar(series, 50, 100, 120, 40); // outside the pattern window

        assertTrue(new ThreeBlackCrowsIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void falseBeforeWarmUpBoundaryAndTrueAtBoundary() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 5);
        addBar(series, 19, 22, 23, 19); // index 5: white
        addBar(series, 21, 18, 21, 17.9); // index 6: first crow
        addBar(series, 20.5, 17, 20.5, 16.95); // index 7: second crow
        addBar(series, 19, 16, 19, 15.9); // index 8: third crow

        ThreeBlackCrowsIndicator indicator = new ThreeBlackCrowsIndicator(series);
        assertEquals(7, indicator.getCountOfUnstableBars());
        for (int i = 0; i < indicator.getCountOfUnstableBars(); i++) {
            assertFalse("expected false at " + i, indicator.getValue(i));
        }
        assertFalse(indicator.getValue(7)); // guard released, but the white candle at 4 makes this fail
        assertTrue(indicator.getValue(8));
    }

    @Test
    public void matchesWithNonZeroBeginIndexAtWarmUpBoundary() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10); // indexes 0-9, evicted
        addBaselineBars(series, 5); // indexes 10-14: baseline of the retained series
        addBar(series, 19, 22, 23, 19); // index 15: white
        addBar(series, 21, 18, 21, 17.9); // index 16: first crow
        addBar(series, 20.5, 17, 20.5, 16.95); // index 17: second crow
        addBar(series, 19, 16, 19, 15.9); // index 18: third crow
        addBaselineBars(series, 2);
        series.setMaximumBarCount(10);

        ThreeBlackCrowsIndicator indicator = new ThreeBlackCrowsIndicator(series);
        assertFalse(indicator.getValue(17));
        assertTrue(indicator.getValue(18));
    }

    @Test
    public void rejectsInvalidAveragePeriod() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        assertThrows(IllegalArgumentException.class, () -> new ThreeBlackCrowsIndicator(series, 0));
    }

    @Test
    public void nonFiniteShadowIsNotCrows() {
        DoubleNumFactory doubleFactory = DoubleNumFactory.getInstance();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(doubleFactory).build();
        addBaselineBars(series, 9);
        addBar(series, 19, 22, 23, 19); // index 9: white
        addBar(series, 21, 18, 21, 17.9); // index 10: first crow
        addBar(series, 20.5, 17, 20.5, 16.95); // index 11: second crow
        series.addBar(new NonFiniteBar(series.getBar(series.getEndIndex()).getEndTime(), doubleFactory.numOf(19),
                doubleFactory.numOf(19), doubleFactory.numOf(Double.NaN), doubleFactory.numOf(16))); // index 12: third
                                                                                                     // crow with NaN
                                                                                                     // low

        assertFalse(new ThreeBlackCrowsIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void nonFiniteLeadingWhiteCandleIsNotCrows() {
        DoubleNumFactory doubleFactory = DoubleNumFactory.getInstance();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(doubleFactory).build();
        addBaselineBars(series, 9);
        series.addBar(new NonFiniteBar(series.getBar(series.getEndIndex()).getEndTime().minus(Duration.ofHours(12)),
                doubleFactory.numOf(Double.NEGATIVE_INFINITY), doubleFactory.numOf(23), doubleFactory.numOf(19),
                doubleFactory.numOf(22))); // index 9: white candle with infinite open but finite close/high/low
        addBar(series, 21, 18, 21, 17.9); // index 10: first crow
        addBar(series, 20.5, 17, 20.5, 16.95); // index 11: second crow
        addBar(series, 19, 16, 19, 15.9); // index 12: third crow

        assertFalse(new ThreeBlackCrowsIndicator(series).getValue(PATTERN_INDEX));
    }

    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        BarSeries series = serializationSeries(numFactory);
        return List.of(serializationFixture(series, new ThreeBlackCrowsIndicator(series), stableIndexes(series)),
                serializationFixture(series, new ThreeBlackCrowsIndicator(series, 3), stableIndexes(series)));
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

/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.candles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;
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

public class BearishKickerIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    public BearishKickerIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    private static void addBar(BarSeries series, double open, double close, double high, double low) {
        series.barBuilder().openPrice(open).closePrice(close).highPrice(high).lowPrice(low).add();
    }

    // 5 baseline bars of body 10 / range 10 (open 0, close 10), then a bullish
    // marubozu at 5 (body 20, no shadows) and a bearish marubozu at 6 (body 14,
    // shadows 0.5) gapping strictly below it; first stable index is 6.
    private BarSeries kickerSeries(double prevOpen, double prevClose, double prevHigh, double prevLow, double currOpen,
            double currClose, double currHigh, double currLow) {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 5; i++) {
            addBar(series, 0, 10, 10, 0);
        }
        addBar(series, prevOpen, prevClose, prevHigh, prevLow);
        addBar(series, currOpen, currClose, currHigh, currLow);
        return series;
    }

    @Test
    public void shouldDetectPatternWhenAllConditionsAreSatisfied() {
        BearishKickerIndicator indicator = new BearishKickerIndicator(kickerSeries(5, 25, 25, 5, 0, -14, 0.5, -14.5));
        assertThat(indicator.getValue(6)).isTrue();
    }

    @Test
    public void shouldCountUnstableBars() {
        BearishKickerIndicator indicator = new BearishKickerIndicator(kickerSeries(5, 25, 25, 5, 0, -14, 0.5, -14.5));
        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(6);
    }

    @Test
    public void shouldReturnFalseBeforeStableBoundary() {
        BearishKickerIndicator indicator = new BearishKickerIndicator(kickerSeries(5, 25, 25, 5, 0, -14, 0.5, -14.5));
        for (int i = 0; i < 6; i++) {
            assertThat(indicator.getValue(i)).isFalse();
        }
        assertThat(indicator.getValue(6)).isTrue();
    }

    @Test
    public void shouldNotDetectPatternWhenPreviousCandleIsBearish() {
        BearishKickerIndicator indicator = new BearishKickerIndicator(kickerSeries(25, 5, 25, 5, 0, -14, 0.5, -14.5));
        assertThat(indicator.getValue(6)).isFalse();
    }

    @Test
    public void shouldNotDetectPatternWhenPreviousBodyIsNotLong() {
        // previous body exactly at the prior-average body (10): strict > required
        BearishKickerIndicator indicator = new BearishKickerIndicator(kickerSeries(5, 15, 15, 5, 0, -14, 0.5, -14.5));
        assertThat(indicator.getValue(6)).isFalse();
    }

    @Test
    public void shouldNotDetectPatternWhenPreviousUpperShadowIsNotShort() {
        BearishKickerIndicator indicator = new BearishKickerIndicator(kickerSeries(5, 25, 27, 5, 0, -14, 0.5, -14.5));
        assertThat(indicator.getValue(6)).isFalse();
    }

    @Test
    public void shouldNotDetectPatternWhenPreviousLowerShadowIsNotShort() {
        BearishKickerIndicator indicator = new BearishKickerIndicator(kickerSeries(5, 25, 25, 3, 0, -14, 0.5, -14.5));
        assertThat(indicator.getValue(6)).isFalse();
    }

    @Test
    public void shouldNotDetectPatternWhenCurrentCandleIsBullish() {
        BearishKickerIndicator indicator = new BearishKickerIndicator(kickerSeries(5, 25, 25, 5, -10, 4, 4.5, -10.5));
        assertThat(indicator.getValue(6)).isFalse();
    }

    @Test
    public void shouldNotDetectPatternWhenCurrentBodyIsNotLong() {
        // current body exactly at the prior-average body (12): strict > required
        BearishKickerIndicator indicator = new BearishKickerIndicator(kickerSeries(5, 25, 25, 5, 0, -12, 0.5, -12.5));
        assertThat(indicator.getValue(6)).isFalse();
    }

    @Test
    public void shouldNotDetectPatternWhenCurrentUpperShadowIsNotShort() {
        BearishKickerIndicator indicator = new BearishKickerIndicator(kickerSeries(5, 25, 25, 5, 0, -14, 2, -14.5));
        assertThat(indicator.getValue(6)).isFalse();
    }

    @Test
    public void shouldNotDetectPatternWhenBodyGapIsNotStrict() {
        // second bodyTop (5) equals the first bodyBottom (5): strict < required
        BearishKickerIndicator indicator = new BearishKickerIndicator(kickerSeries(5, 25, 25, 5, 5, -9, 5.5, -9.5));
        assertThat(indicator.getValue(6)).isFalse();
    }

    @Test
    public void contextOutsidePatternAndBaselineWindowsDoesNotChangeResult() {
        BarSeries downtrend = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        BarSeries uptrend = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 6; i++) {
            addBar(downtrend, 60 - i, 52 - i, 61 - i, 51 - i);
            addBar(uptrend, i, i + 8, i + 10, i);
        }
        for (int i = 0; i < 5; i++) {
            addBar(downtrend, 0, 10, 10, 0);
            addBar(uptrend, 0, 10, 10, 0);
        }
        addBar(downtrend, 5, 25, 25, 5);
        addBar(downtrend, 0, -14, 0.5, -14.5);
        addBar(uptrend, 5, 25, 25, 5);
        addBar(uptrend, 0, -14, 0.5, -14.5);
        // bars 0..5 lie outside the pattern window (11, 12) and the threshold
        // baseline windows [6..10] and [7..11]; varying them must not matter.
        assertThat(new BearishKickerIndicator(downtrend).getValue(12)).isTrue();
        assertThat(new BearishKickerIndicator(uptrend).getValue(12)).isTrue();
    }

    @Test
    public void rollingSeriesAdvancesBeginIndexWithoutChangingBoundary() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 10; i++) {
            addBar(series, 0, 10, 10, 0);
        }
        for (int i = 0; i < 5; i++) {
            addBar(series, 0, 10, 10, 0); // baseline at 10..14
        }
        addBar(series, 5, 25, 25, 5); // previous at index 15
        addBar(series, 0, -14, 0.5, -14.5); // current at index 16
        for (int i = 0; i < 7; i++) {
            addBar(series, 0, 10, 10, 0);
        }
        series.setMaximumBarCount(14); // beginIndex advances to 10
        BearishKickerIndicator indicator = new BearishKickerIndicator(series);
        assertThat(indicator.getValue(15)).isFalse();
        assertThat(indicator.getValue(16)).isTrue();
    }

    @Test
    public void shouldRespectCustomAveragePeriod() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBar(series, 0, 10, 10, 0);
        addBar(series, 0, 10, 10, 0);
        addBar(series, 0, 10, 10, 0);
        addBar(series, 5, 25, 25, 5); // previous at index 3
        addBar(series, 0, -14, 0.5, -14.5); // current at index 4
        BearishKickerIndicator indicator = new BearishKickerIndicator(series, 3);
        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(4);
        assertThat(indicator.getValue(3)).isFalse();
        assertThat(indicator.getValue(4)).isTrue();
    }

    @Test
    public void shouldRejectInvalidConstructorArguments() {
        assertThrows(NullPointerException.class, () -> new BearishKickerIndicator(null));
        assertThrows(IllegalArgumentException.class,
                () -> new BearishKickerIndicator(kickerSeries(5, 25, 25, 5, 0, -14, 0.5, -14.5), 0));
    }

    @Test
    public void nonFiniteShadowsAreNotAKicker() {
        // a NaN shadow must never read as negligible: the marubozu confirmation
        // fails before any gap comparison
        DoubleNumFactory doubleFactory = DoubleNumFactory.getInstance();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(doubleFactory).build();
        for (int i = 0; i < 5; i++) {
            addBar(series, 0, 10, 10, 0);
        }
        addBar(series, 5, 25, 25, 5); // bullish marubozu at 5
        series.addBar(new NonFiniteBar(series.getBar(series.getEndIndex()).getEndTime().minus(Duration.ofHours(12)),
                doubleFactory.numOf(0), doubleFactory.numOf(0.5), doubleFactory.numOf(Double.NaN),
                doubleFactory.numOf(-14)));

        assertThat(new BearishKickerIndicator(series).getValue(6)).isFalse();
    }

    @Test
    public void nullLowBarIsNotAKickerRatherThanThrowing() {
        // A bar without a low price has no lower-shadow geometry; the shadow
        // indicators would dereference the null price.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 7; i++) {
            addBar(series, 0, 10, 10, 0);
        }
        series.barBuilder().openPrice(5).closePrice(25).highPrice(25).lowPrice((Num) null).add();
        series.barBuilder().openPrice(46).closePrice(30).highPrice(46.5).lowPrice(29.5).add();

        assertThat(new BearishKickerIndicator(series).getValue(8)).isFalse();
    }

    @Test
    public void nullOpenOnPreviousBarIsNotAKickerRatherThanThrowing() {
        // A bar without an open price has no body geometry; the shadow
        // indicators would dereference the null endpoint before the non-finite
        // guard.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 7; i++) {
            addBar(series, 0, 10, 10, 0);
        }
        series.barBuilder().openPrice((Num) null).closePrice(25).highPrice(25).lowPrice(5).add();
        series.barBuilder().openPrice(46).closePrice(30).highPrice(46.5).lowPrice(29.5).add();

        assertThat(new BearishKickerIndicator(series).getValue(8)).isFalse();
    }

    @Test
    public void nullCloseOnCurrentBarIsNotAKickerRatherThanThrowing() {
        // A bar without a close price has no body geometry; the shadow
        // indicators would dereference the null endpoint before the non-finite
        // guard.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 7; i++) {
            addBar(series, 0, 10, 10, 0);
        }
        series.barBuilder().openPrice(5).closePrice(25).highPrice(25).lowPrice(5).add();
        series.barBuilder().openPrice(46).closePrice((Num) null).highPrice(46.5).lowPrice(29.5).add();

        assertThat(new BearishKickerIndicator(series).getValue(8)).isFalse();
    }

    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        BarSeries series = serializationSeries(numFactory);
        return List.of(serializationFixture(series, new BearishKickerIndicator(series), stableIndexes(series)),
                serializationFixture(series, new BearishKickerIndicator(series, 3), stableIndexes(series)));
    }
}

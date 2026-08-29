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

public class BullishKickerIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    public BullishKickerIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    private static void addBar(BarSeries series, double open, double close, double high, double low) {
        series.barBuilder().openPrice(open).closePrice(close).highPrice(high).lowPrice(low).add();
    }

    // 5 baseline bars of body 10 / range 10 (open 0, close 10), then a bearish
    // marubozu at 5 (body 20, no shadows) and a bullish marubozu at 6 (body 16,
    // shadows 0.5) gapping strictly above it; first stable index is 6.
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
        BullishKickerIndicator indicator = new BullishKickerIndicator(kickerSeries(25, 5, 25, 5, 30, 46, 46.5, 29.5));
        assertThat(indicator.getValue(6)).isTrue();
    }

    @Test
    public void shouldCountUnstableBars() {
        BullishKickerIndicator indicator = new BullishKickerIndicator(kickerSeries(25, 5, 25, 5, 30, 46, 46.5, 29.5));
        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(6);
    }

    @Test
    public void shouldReturnFalseBeforeStableBoundary() {
        BullishKickerIndicator indicator = new BullishKickerIndicator(kickerSeries(25, 5, 25, 5, 30, 46, 46.5, 29.5));
        for (int i = 0; i < 6; i++) {
            assertThat(indicator.getValue(i)).isFalse();
        }
        assertThat(indicator.getValue(6)).isTrue();
    }

    @Test
    public void shouldNotDetectPatternWhenPreviousCandleIsBullish() {
        BullishKickerIndicator indicator = new BullishKickerIndicator(
                kickerSeries(5, 25, 25.5, 4.5, 30, 46, 46.5, 29.5));
        assertThat(indicator.getValue(6)).isFalse();
    }

    @Test
    public void shouldNotDetectPatternWhenPreviousBodyIsNotLong() {
        // previous body exactly at the prior-average body (10): strict > required
        BullishKickerIndicator indicator = new BullishKickerIndicator(kickerSeries(15, 5, 15, 5, 30, 46, 46.5, 29.5));
        assertThat(indicator.getValue(6)).isFalse();
    }

    @Test
    public void shouldNotDetectPatternWhenPreviousUpperShadowIsNotShort() {
        BullishKickerIndicator indicator = new BullishKickerIndicator(kickerSeries(25, 5, 27, 5, 30, 46, 46.5, 29.5));
        assertThat(indicator.getValue(6)).isFalse();
    }

    @Test
    public void shouldNotDetectPatternWhenPreviousLowerShadowIsNotShort() {
        BullishKickerIndicator indicator = new BullishKickerIndicator(kickerSeries(25, 5, 25, 3, 30, 46, 46.5, 29.5));
        assertThat(indicator.getValue(6)).isFalse();
    }

    @Test
    public void shouldNotDetectPatternWhenCurrentCandleIsBearish() {
        BullishKickerIndicator indicator = new BullishKickerIndicator(kickerSeries(25, 5, 25, 5, 46, 30, 46.5, 29.5));
        assertThat(indicator.getValue(6)).isFalse();
    }

    @Test
    public void shouldNotDetectPatternWhenCurrentBodyIsNotLong() {
        // current body exactly at the prior-average body (12): strict > required
        BullishKickerIndicator indicator = new BullishKickerIndicator(kickerSeries(25, 5, 25, 5, 30, 42, 42.5, 29.5));
        assertThat(indicator.getValue(6)).isFalse();
    }

    @Test
    public void shouldNotDetectPatternWhenCurrentUpperShadowIsNotShort() {
        BullishKickerIndicator indicator = new BullishKickerIndicator(kickerSeries(25, 5, 25, 5, 30, 46, 48, 29.5));
        assertThat(indicator.getValue(6)).isFalse();
    }

    @Test
    public void shouldNotDetectPatternWhenBodyGapIsNotStrict() {
        // second bodyBottom (25) equals the first bodyTop (25): strict > required
        BullishKickerIndicator indicator = new BullishKickerIndicator(kickerSeries(25, 5, 25, 5, 25, 41, 41.5, 24.5));
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
        addBar(downtrend, 25, 5, 25, 5);
        addBar(downtrend, 30, 46, 46.5, 29.5);
        addBar(uptrend, 25, 5, 25, 5);
        addBar(uptrend, 30, 46, 46.5, 29.5);
        // bars 0..5 lie outside the pattern window (11, 12) and the threshold
        // baseline windows [6..10] and [7..11]; varying them must not matter.
        assertThat(new BullishKickerIndicator(downtrend).getValue(12)).isTrue();
        assertThat(new BullishKickerIndicator(uptrend).getValue(12)).isTrue();
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
        addBar(series, 25, 5, 25, 5); // previous at index 15
        addBar(series, 30, 46, 46.5, 29.5); // current at index 16
        for (int i = 0; i < 7; i++) {
            addBar(series, 0, 10, 10, 0);
        }
        series.setMaximumBarCount(14); // beginIndex advances to 10
        BullishKickerIndicator indicator = new BullishKickerIndicator(series);
        assertThat(indicator.getValue(15)).isFalse();
        assertThat(indicator.getValue(16)).isTrue();
    }

    @Test
    public void cachedMatchIsInvalidatedWhenBaselineWindowRollsPast() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 10; i++) {
            addBar(series, 0, 10, 10, 0);
        }
        for (int i = 0; i < 5; i++) {
            addBar(series, 0, 10, 10, 0); // baseline at 10..14
        }
        addBar(series, 25, 5, 25, 5); // previous at index 15
        addBar(series, 30, 46, 46.5, 29.5); // current at index 16
        for (int i = 0; i < 7; i++) {
            addBar(series, 0, 10, 10, 0);
        }
        BullishKickerIndicator indicator = new BullishKickerIndicator(series);
        assertThat(indicator.getValue(16)).isTrue();
        series.setMaximumBarCount(9); // beginIndex advances past the baseline window
        assertThat(indicator.getValue(16)).isFalse();
    }

    @Test
    public void shouldRespectCustomAveragePeriod() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBar(series, 0, 10, 10, 0);
        addBar(series, 0, 10, 10, 0);
        addBar(series, 0, 10, 10, 0);
        addBar(series, 25, 5, 25, 5); // previous at index 3
        addBar(series, 30, 46, 46.5, 29.5); // current at index 4
        BullishKickerIndicator indicator = new BullishKickerIndicator(series, 3);
        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(4);
        assertThat(indicator.getValue(3)).isFalse();
        assertThat(indicator.getValue(4)).isTrue();
    }

    @Test
    public void shouldRejectInvalidConstructorArguments() {
        assertThrows(NullPointerException.class, () -> new BullishKickerIndicator(null));
        assertThrows(IllegalArgumentException.class,
                () -> new BullishKickerIndicator(kickerSeries(25, 5, 25, 5, 30, 46, 46.5, 29.5), 0));
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
        addBar(series, 25, 5, 25, 5); // bearish marubozu at 5
        series.addBar(new NonFiniteBar(series.getBar(series.getEndIndex()).getEndTime().minus(Duration.ofHours(12)),
                doubleFactory.numOf(30), doubleFactory.numOf(Double.NaN), doubleFactory.numOf(29.5),
                doubleFactory.numOf(46)));

        assertThat(new BullishKickerIndicator(series).getValue(6)).isFalse();
    }

    @Test
    public void nullHighBarIsNotAKickerRatherThanThrowing() {
        // A bar without a high price has no upper-shadow geometry; the shadow
        // indicators would dereference the null price.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 7; i++) {
            addBar(series, 0, 10, 10, 0);
        }
        series.barBuilder().openPrice(25).closePrice(5).highPrice((Num) null).lowPrice(5).add();
        series.barBuilder().openPrice(30).closePrice(46).highPrice(46.5).lowPrice(29.5).add();

        assertThat(new BullishKickerIndicator(series).getValue(8)).isFalse();
    }

    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        BarSeries series = serializationSeries(numFactory);
        return List.of(serializationFixture(series, new BullishKickerIndicator(series), stableIndexes(series)),
                serializationFixture(series, new BullishKickerIndicator(series, 3), stableIndexes(series)));
    }
}

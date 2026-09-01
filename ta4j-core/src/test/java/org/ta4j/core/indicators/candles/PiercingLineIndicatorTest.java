/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.candles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;
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

public class PiercingLineIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    public PiercingLineIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    private static void addBar(BarSeries series, double open, double close, double high, double low) {
        series.barBuilder().openPrice(open).closePrice(close).highPrice(high).lowPrice(low).add();
    }

    // 5 baseline bars of body 10 / range 10 (open 0, close 10), then a long
    // bearish first body at 5 (30..10, low 10) and a bullish second body at 6
    // opening below that low and closing above 50% of the first body;
    // with the default period and penetration the first stable index is 6.
    private BarSeries piercingSeries(double firstOpen, double firstClose, double secondOpen, double secondClose) {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 5; i++) {
            addBar(series, 0, 10, 10, 0);
        }
        addBar(series, firstOpen, firstClose, Math.max(firstOpen, firstClose), Math.min(firstOpen, firstClose));
        addBar(series, secondOpen, secondClose, Math.max(secondOpen, secondClose) + 1,
                Math.min(secondOpen, secondClose) - 1);
        return series;
    }

    @Test
    public void shouldDetectPatternWhenAllConditionsAreSatisfied() {
        PiercingLineIndicator indicator = new PiercingLineIndicator(piercingSeries(30, 10, 8, 24));
        assertThat(indicator.getValue(6)).isTrue();
    }

    @Test
    public void shouldCountUnstableBars() {
        PiercingLineIndicator indicator = new PiercingLineIndicator(piercingSeries(30, 10, 8, 24));
        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(6);
    }

    @Test
    public void shouldReturnFalseBeforeStableBoundary() {
        PiercingLineIndicator indicator = new PiercingLineIndicator(piercingSeries(30, 10, 8, 24));
        for (int i = 0; i < 6; i++) {
            assertThat(indicator.getValue(i)).isFalse();
        }
        assertThat(indicator.getValue(6)).isTrue();
    }

    @Test
    public void shouldNotDetectPatternWhenFirstCandleIsBullish() {
        PiercingLineIndicator indicator = new PiercingLineIndicator(piercingSeries(10, 30, 8, 24));
        assertThat(indicator.getValue(6)).isFalse();
    }

    @Test
    public void shouldNotDetectPatternWhenFirstBodyIsNotLong() {
        // first body exactly at the prior-average body (10): strict > required
        PiercingLineIndicator indicator = new PiercingLineIndicator(piercingSeries(30, 20, 8, 24));
        assertThat(indicator.getValue(6)).isFalse();
    }

    @Test
    public void shouldNotDetectPatternWhenSecondCandleIsBearish() {
        PiercingLineIndicator indicator = new PiercingLineIndicator(piercingSeries(30, 10, 8, 4));
        assertThat(indicator.getValue(6)).isFalse();
    }

    @Test
    public void shouldNotDetectPatternWhenOpenDoesNotGapBelowFirstLow() {
        // second open (10) equals the first low (10): strict < required
        PiercingLineIndicator indicator = new PiercingLineIndicator(piercingSeries(30, 10, 10, 24));
        assertThat(indicator.getValue(6)).isFalse();
    }

    @Test
    public void missingFirstCandleLowDoesNotMatch() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 5; i++) {
            addBar(series, 0, 10, 10, 0);
        }
        // Long bearish first candle whose low is missing: the gap cannot be
        // evaluated, so the pattern must be rejected instead of throwing.
        series.barBuilder().openPrice(30).closePrice(10).highPrice(30).add();
        series.barBuilder().openPrice(8).closePrice(24).highPrice(24).lowPrice(7).add();

        assertThat(new PiercingLineIndicator(series).getValue(6)).isFalse();
    }

    @Test
    public void shouldNotCountSignedZeroGapBelowFirstLow() {
        // First low and second open are +0.0 and -0.0: the sign bit must not
        // count as a strict gap below the first low, regardless of num factory.
        PiercingLineIndicator indicator = new PiercingLineIndicator(piercingSeries(20, +0.0, -0.0, 10));
        assertThat(indicator.getValue(6)).isFalse();

        // Control: a genuine gap below the first low still matches.
        PiercingLineIndicator control = new PiercingLineIndicator(piercingSeries(20, +0.0, -5, 10));
        assertThat(control.getValue(6)).isTrue();
    }

    @Test
    public void shouldNotDetectPatternWhenCloseDoesNotReachPenetration() {
        // required close is 10 + 0.5 * 20 = 20; 19 is not deep enough
        PiercingLineIndicator indicator = new PiercingLineIndicator(piercingSeries(30, 10, 8, 19));
        assertThat(indicator.getValue(6)).isFalse();
    }

    @Test
    public void shouldDetectPatternWhenCloseIsExactlyOnPenetration() {
        // penetration is inclusive: a close exactly at 20 still matches
        PiercingLineIndicator indicator = new PiercingLineIndicator(piercingSeries(30, 10, 8, 20));
        assertThat(indicator.getValue(6)).isTrue();
    }

    @Test
    public void shouldDetectPatternWhenClosePenetratesDeeper() {
        PiercingLineIndicator indicator = new PiercingLineIndicator(piercingSeries(30, 10, 8, 26));
        assertThat(indicator.getValue(6)).isTrue();
    }

    @Test
    public void shouldNotDetectPatternWhenCloseEngulfsFirstOpen() {
        // the close must stay strictly below the first open, otherwise the
        // second body engulfs the first and the pattern is not a piercing line
        PiercingLineIndicator indicator = new PiercingLineIndicator(piercingSeries(30, 10, 8, 30));
        assertThat(indicator.getValue(6)).isFalse();
        assertThat(new PiercingLineIndicator(piercingSeries(30, 10, 8, 31)).getValue(6)).isFalse();
    }

    @Test
    public void detectsFiniteExtremePenetrationWithoutOverflow() {
        // The first body's finite span overflows DoubleNum when subtracted, but
        // its half-way penetration point is exactly zero.
        PiercingLineIndicator indicator = new PiercingLineIndicator(
                piercingSeries(9e307, -9e307, -Double.MAX_VALUE, 0));

        assertThat(indicator.getValue(6)).isTrue();
    }

    @Test
    public void signedZeroCloseCannotSatisfyAntiEngulfing() {
        // DoubleNum orders -0.0 below +0.0, but both close/open values are
        // numerically equal and must not meet the strict anti-engulfing clause.
        PiercingLineIndicator indicator = new PiercingLineIndicator(piercingSeries(+0.0, -20, -25, -0.0));

        assertThat(indicator.getValue(6)).isFalse();
    }

    @Test
    public void shouldRespectConfiguredPenetration() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 5; i++) {
            addBar(series, 0, 10, 10, 0);
        }
        addBar(series, 30, 10, 30, 10);
        addBar(series, 8, 16, 17, 7);
        // required close with penetration 0.25 is 10 + 0.25 * 20 = 15
        assertThat(new PiercingLineIndicator(series, 5, 0.25).getValue(6)).isTrue();
        // required close with the default penetration 0.5 is 20
        assertThat(new PiercingLineIndicator(series).getValue(6)).isFalse();
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
        addBar(downtrend, 30, 10, 30, 10);
        addBar(downtrend, 8, 24, 25, 7);
        addBar(uptrend, 30, 10, 30, 10);
        addBar(uptrend, 8, 24, 25, 7);
        // bars 0..5 lie outside the pattern window (11, 12) and the threshold
        // baseline windows [6..10] and [7..11]; varying them must not matter.
        assertThat(new PiercingLineIndicator(downtrend).getValue(12)).isTrue();
        assertThat(new PiercingLineIndicator(uptrend).getValue(12)).isTrue();
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
        addBar(series, 30, 10, 30, 10); // first candle at index 15
        addBar(series, 8, 24, 25, 7); // second candle at index 16
        for (int i = 0; i < 7; i++) {
            addBar(series, 0, 10, 10, 0);
        }
        series.setMaximumBarCount(14); // beginIndex advances to 10
        PiercingLineIndicator indicator = new PiercingLineIndicator(series);
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
        addBar(series, 30, 10, 30, 10); // first candle at index 15
        addBar(series, 8, 24, 25, 7); // second candle at index 16
        for (int i = 0; i < 7; i++) {
            addBar(series, 0, 10, 10, 0);
        }
        PiercingLineIndicator indicator = new PiercingLineIndicator(series);
        assertThat(indicator.getValue(16)).isTrue();
        series.setMaximumBarCount(9); // beginIndex advances past the baseline window
        assertThat(indicator.getValue(16)).isFalse();
    }

    @Test
    public void shouldRespectCustomAveragePeriodAndPenetration() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBar(series, 0, 10, 10, 0);
        addBar(series, 0, 10, 10, 0);
        addBar(series, 0, 10, 10, 0);
        addBar(series, 30, 10, 30, 10); // first candle at index 3
        addBar(series, 8, 24, 25, 7); // second candle at index 4
        PiercingLineIndicator indicator = new PiercingLineIndicator(series, 3, 0.25);
        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(4);
        assertThat(indicator.getValue(3)).isFalse();
        assertThat(indicator.getValue(4)).isTrue();
    }

    @Test
    public void shouldRejectInvalidConstructorArguments() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 5; i++) {
            addBar(series, 0, 10, 10, 0);
        }
        assertThrows(NullPointerException.class, () -> new PiercingLineIndicator(null));
        assertThrows(IllegalArgumentException.class, () -> new PiercingLineIndicator(series, 0, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new PiercingLineIndicator(series, -1, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new PiercingLineIndicator(series, 5, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new PiercingLineIndicator(series, 5, -0.5));
        assertThrows(IllegalArgumentException.class, () -> new PiercingLineIndicator(series, 5, 1.5));
        assertThrows(IllegalArgumentException.class, () -> new PiercingLineIndicator(series, 5, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> new PiercingLineIndicator(series, 5, Double.POSITIVE_INFINITY));
        // a full-body penetration engulfs the first body, so no close can
        // satisfy the anti-engulfing clause and 1.0 is rejected outright
        assertThrows(IllegalArgumentException.class, () -> new PiercingLineIndicator(series, 5, 1.0));
    }

    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        BarSeries series = serializationSeries(numFactory);
        return List.of(serializationFixture(series, new PiercingLineIndicator(series), stableIndexes(series)),
                serializationFixture(series, new PiercingLineIndicator(series, 3, 0.25), stableIndexes(series)));
    }
}

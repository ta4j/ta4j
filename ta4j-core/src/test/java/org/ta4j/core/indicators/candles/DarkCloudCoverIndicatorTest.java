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

public class DarkCloudCoverIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    public DarkCloudCoverIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    private static void addBar(BarSeries series, double open, double close, double high, double low) {
        series.barBuilder().openPrice(open).closePrice(close).highPrice(high).lowPrice(low).add();
    }

    // 5 baseline bars of body 10 / range 10 (open 0, close 10), then a long
    // bullish first body at 5 (10..30, high 30) and a bearish second body at 6
    // opening above that high and closing through 50% of the first body;
    // with the default period and penetration the first stable index is 6.
    private BarSeries darkCloudSeries(double firstOpen, double firstClose, double secondOpen, double secondClose) {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 5; i++) {
            addBar(series, 0, 10, 10, 0);
        }
        addBar(series, firstOpen, firstClose, Math.max(firstOpen, firstClose), Math.min(firstOpen, firstClose));
        addBar(series, secondOpen, secondClose, Math.max(secondOpen, secondClose),
                Math.min(secondOpen, secondClose) - 1);
        return series;
    }

    @Test
    public void shouldDetectPatternWhenAllConditionsAreSatisfied() {
        DarkCloudCoverIndicator indicator = new DarkCloudCoverIndicator(darkCloudSeries(10, 30, 32, 18));
        assertThat(indicator.getValue(6)).isTrue();
    }

    @Test
    public void shouldCountUnstableBars() {
        DarkCloudCoverIndicator indicator = new DarkCloudCoverIndicator(darkCloudSeries(10, 30, 32, 18));
        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(6);
    }

    @Test
    public void shouldReturnFalseBeforeStableBoundary() {
        DarkCloudCoverIndicator indicator = new DarkCloudCoverIndicator(darkCloudSeries(10, 30, 32, 18));
        for (int i = 0; i < 6; i++) {
            assertThat(indicator.getValue(i)).isFalse();
        }
        assertThat(indicator.getValue(6)).isTrue();
    }

    @Test
    public void shouldNotDetectPatternWhenFirstCandleIsBearish() {
        DarkCloudCoverIndicator indicator = new DarkCloudCoverIndicator(darkCloudSeries(30, 10, 32, 18));
        assertThat(indicator.getValue(6)).isFalse();
    }

    @Test
    public void shouldNotDetectPatternWhenFirstBodyIsNotLong() {
        // first body exactly at the prior-average body (10): strict > required
        DarkCloudCoverIndicator indicator = new DarkCloudCoverIndicator(darkCloudSeries(20, 30, 32, 18));
        assertThat(indicator.getValue(6)).isFalse();
    }

    @Test
    public void shouldNotDetectPatternWhenSecondCandleIsBullish() {
        DarkCloudCoverIndicator indicator = new DarkCloudCoverIndicator(darkCloudSeries(10, 30, 32, 40));
        assertThat(indicator.getValue(6)).isFalse();
    }

    @Test
    public void shouldNotDetectPatternWhenOpenDoesNotGapAboveFirstHigh() {
        // second open (30) equals the first high (30): strict > required
        DarkCloudCoverIndicator indicator = new DarkCloudCoverIndicator(darkCloudSeries(10, 30, 30, 18));
        assertThat(indicator.getValue(6)).isFalse();
    }

    @Test
    public void missingFirstCandleHighDoesNotMatch() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 5; i++) {
            addBar(series, 0, 10, 10, 0);
        }
        // Long bullish first candle whose high is missing: the gap cannot be
        // evaluated, so the pattern must be rejected instead of throwing.
        series.barBuilder().openPrice(10).closePrice(30).lowPrice(10).add();
        series.barBuilder().openPrice(32).closePrice(18).highPrice(32).lowPrice(17).add();

        assertThat(new DarkCloudCoverIndicator(series).getValue(6)).isFalse();
    }

    @Test
    public void shouldNotDetectPatternWhenCloseDoesNotReachPenetration() {
        // required close is 30 - 0.5 * 20 = 20; 21 is not deep enough
        DarkCloudCoverIndicator indicator = new DarkCloudCoverIndicator(darkCloudSeries(10, 30, 32, 21));
        assertThat(indicator.getValue(6)).isFalse();
    }

    @Test
    public void shouldDetectPatternWhenCloseIsExactlyOnPenetration() {
        // penetration is inclusive: a close exactly at 20 still matches
        DarkCloudCoverIndicator indicator = new DarkCloudCoverIndicator(darkCloudSeries(10, 30, 32, 20));
        assertThat(indicator.getValue(6)).isTrue();
    }

    @Test
    public void shouldDetectPatternWhenClosePenetratesDeeper() {
        DarkCloudCoverIndicator indicator = new DarkCloudCoverIndicator(darkCloudSeries(10, 30, 32, 15));
        assertThat(indicator.getValue(6)).isTrue();
    }

    @Test
    public void shouldNotDetectPatternWhenCloseEngulfsFirstOpen() {
        // the close must stay strictly above the first open, otherwise the
        // second body engulfs the first and the pattern is not a dark cloud cover
        DarkCloudCoverIndicator indicator = new DarkCloudCoverIndicator(darkCloudSeries(10, 30, 32, 10));
        assertThat(indicator.getValue(6)).isFalse();
        assertThat(new DarkCloudCoverIndicator(darkCloudSeries(10, 30, 32, 8)).getValue(6)).isFalse();
    }

    @Test
    public void shouldRespectConfiguredPenetration() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 5; i++) {
            addBar(series, 0, 10, 10, 0);
        }
        addBar(series, 10, 30, 30, 10);
        addBar(series, 32, 24, 32, 23);
        // required close with penetration 0.25 is 30 - 0.25 * 20 = 25
        assertThat(new DarkCloudCoverIndicator(series, 5, 0.25).getValue(6)).isTrue();
        // required close with the default penetration 0.5 is 20
        assertThat(new DarkCloudCoverIndicator(series).getValue(6)).isFalse();
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
        addBar(downtrend, 10, 30, 30, 10);
        addBar(downtrend, 32, 18, 32, 17);
        addBar(uptrend, 10, 30, 30, 10);
        addBar(uptrend, 32, 18, 32, 17);
        // bars 0..5 lie outside the pattern window (11, 12) and the threshold
        // baseline windows [6..10] and [7..11]; varying them must not matter.
        assertThat(new DarkCloudCoverIndicator(downtrend).getValue(12)).isTrue();
        assertThat(new DarkCloudCoverIndicator(uptrend).getValue(12)).isTrue();
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
        addBar(series, 10, 30, 30, 10); // first candle at index 15
        addBar(series, 32, 18, 32, 17); // second candle at index 16
        for (int i = 0; i < 7; i++) {
            addBar(series, 0, 10, 10, 0);
        }
        series.setMaximumBarCount(14); // beginIndex advances to 10
        DarkCloudCoverIndicator indicator = new DarkCloudCoverIndicator(series);
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
        addBar(series, 10, 30, 30, 10); // first candle at index 15
        addBar(series, 32, 18, 32, 17); // second candle at index 16
        for (int i = 0; i < 7; i++) {
            addBar(series, 0, 10, 10, 0);
        }
        DarkCloudCoverIndicator indicator = new DarkCloudCoverIndicator(series);
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
        addBar(series, 10, 30, 30, 10); // first candle at index 3
        addBar(series, 32, 18, 32, 17); // second candle at index 4
        DarkCloudCoverIndicator indicator = new DarkCloudCoverIndicator(series, 3, 0.25);
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
        assertThrows(NullPointerException.class, () -> new DarkCloudCoverIndicator(null));
        assertThrows(IllegalArgumentException.class, () -> new DarkCloudCoverIndicator(series, 0, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new DarkCloudCoverIndicator(series, -1, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new DarkCloudCoverIndicator(series, 5, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new DarkCloudCoverIndicator(series, 5, -0.5));
        assertThrows(IllegalArgumentException.class, () -> new DarkCloudCoverIndicator(series, 5, 1.5));
        assertThrows(IllegalArgumentException.class, () -> new DarkCloudCoverIndicator(series, 5, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> new DarkCloudCoverIndicator(series, 5, Double.POSITIVE_INFINITY));
        // a penetration of exactly 1.0 is the inclusive upper bound
        assertThat(new DarkCloudCoverIndicator(series, 5, 1.0).getValue(0)).isFalse();
    }

    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        BarSeries series = serializationSeries(numFactory);
        return List.of(serializationFixture(series, new DarkCloudCoverIndicator(series), stableIndexes(series)),
                serializationFixture(series, new DarkCloudCoverIndicator(series, 3, 0.25), stableIndexes(series)));
    }
}

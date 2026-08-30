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

public class BullishHaramiIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    public BullishHaramiIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    private static void addBar(BarSeries series, double open, double close) {
        series.barBuilder()
                .openPrice(open)
                .closePrice(close)
                .highPrice(Math.max(open, close))
                .lowPrice(Math.min(open, close))
                .add();
    }

    // 5 baseline bars of body 10 (open 0, close 10), then a long bearish body
    // (prevOpen, prevClose) at 5 and a short body (currOpen, currClose) at 6;
    // with the default period the first stable index is 6.
    private BarSeries haramiSeries(double prevOpen, double prevClose, double currOpen, double currClose) {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 5; i++) {
            addBar(series, 0, 10);
        }
        addBar(series, prevOpen, prevClose);
        addBar(series, currOpen, currClose);
        return series;
    }

    @Test
    public void shouldDetectPatternWhenAllConditionsAreSatisfied() {
        BullishHaramiIndicator indicator = new BullishHaramiIndicator(haramiSeries(25, 5, 20, 22));
        assertThat(indicator.getValue(6)).isTrue();
    }

    @Test
    public void shouldCountUnstableBars() {
        BullishHaramiIndicator indicator = new BullishHaramiIndicator(haramiSeries(25, 5, 20, 22));
        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(6);
    }

    @Test
    public void shouldReturnFalseBeforeStableBoundary() {
        BullishHaramiIndicator indicator = new BullishHaramiIndicator(haramiSeries(25, 5, 20, 22));
        for (int i = 0; i < 6; i++) {
            assertThat(indicator.getValue(i)).isFalse();
        }
        assertThat(indicator.getValue(6)).isTrue();
    }

    @Test
    public void shouldNotDetectPatternWhenPreviousCandleIsBullish() {
        BullishHaramiIndicator indicator = new BullishHaramiIndicator(haramiSeries(5, 25, 20, 22));
        assertThat(indicator.getValue(6)).isFalse();
    }

    @Test
    public void shouldTreatSignedZeroContainmentAsInclusive() {
        // Baseline bodies of 9 so that the previous body 10 counts as long.
        // Bearish previous body [0.0, 10] and bullish current body [-0.0, 2]
        // share a numerically zero bottom: containment is inclusive, so the
        // sign bit must not break it regardless of num factory.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 5; i++) {
            addBar(series, 0, 9);
        }
        addBar(series, 10, +0.0);
        addBar(series, -0.0, 2);
        assertThat(new BullishHaramiIndicator(series).getValue(6)).isTrue();

        // Control: containment across genuinely distinct non-zero levels.
        BarSeries control = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 5; i++) {
            addBar(control, 0, 9);
        }
        addBar(control, 10, 0.0);
        addBar(control, 1, 2);
        assertThat(new BullishHaramiIndicator(control).getValue(6)).isTrue();
    }

    @Test
    public void shouldNotDetectPatternWhenPreviousBodyIsNotLong() {
        // previous body exactly at the prior-average body (10): strict > required
        BullishHaramiIndicator indicator = new BullishHaramiIndicator(haramiSeries(15, 5, 20, 22));
        assertThat(indicator.getValue(6)).isFalse();
    }

    @Test
    public void shouldNotDetectPatternWhenCurrentBodyIsNotShort() {
        // current body exactly at half the prior-average body (6): strict < required
        BullishHaramiIndicator indicator = new BullishHaramiIndicator(haramiSeries(25, 5, 19, 25));
        assertThat(indicator.getValue(6)).isFalse();
    }

    @Test
    public void shouldNotDetectPatternWhenCurrentBodyTopExceedsPreviousTop() {
        BullishHaramiIndicator indicator = new BullishHaramiIndicator(haramiSeries(25, 5, 26, 28));
        assertThat(indicator.getValue(6)).isFalse();
    }

    @Test
    public void shouldNotDetectPatternWhenCurrentBodyBottomIsBelowPreviousBottom() {
        BullishHaramiIndicator indicator = new BullishHaramiIndicator(haramiSeries(25, 5, 3, 5));
        assertThat(indicator.getValue(6)).isFalse();
    }

    @Test
    public void shouldDetectPatternWhenCurrentBodySharesPreviousEndpoint() {
        // containment is inclusive: the current bodyTop equals the previous one
        BullishHaramiIndicator indicator = new BullishHaramiIndicator(haramiSeries(25, 5, 23, 25));
        assertThat(indicator.getValue(6)).isTrue();
    }

    @Test
    public void shouldNotDetectPatternWhenSecondCandleIsNotBullish() {
        // the reversal color is part of the contract: the second candle must
        // be bullish, opposite the first
        BullishHaramiIndicator indicator = new BullishHaramiIndicator(haramiSeries(25, 5, 22, 20));
        assertThat(indicator.getValue(6)).isFalse();
    }

    @Test
    public void contextOutsidePatternAndBaselineWindowsDoesNotChangeResult() {
        BarSeries downtrend = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        BarSeries uptrend = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 6; i++) {
            addBar(downtrend, 60 - i, 52 - i);
            addBar(uptrend, i, i + 8);
        }
        for (int i = 0; i < 5; i++) {
            addBar(downtrend, 0, 10);
            addBar(uptrend, 0, 10);
        }
        addBar(downtrend, 25, 5);
        addBar(downtrend, 20, 22);
        addBar(uptrend, 25, 5);
        addBar(uptrend, 20, 22);
        // bars 0..5 lie outside the pattern window (11, 12) and the threshold
        // baseline windows [6..10] and [7..11]; varying them must not matter.
        assertThat(new BullishHaramiIndicator(downtrend).getValue(12)).isTrue();
        assertThat(new BullishHaramiIndicator(uptrend).getValue(12)).isTrue();
    }

    @Test
    public void rollingSeriesAdvancesBeginIndexWithoutChangingBoundary() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 10; i++) {
            addBar(series, 0, 10);
        }
        for (int i = 0; i < 5; i++) {
            addBar(series, 0, 10); // baseline at 10..14
        }
        addBar(series, 25, 5); // previous at index 15
        addBar(series, 20, 22); // current at index 16
        for (int i = 0; i < 7; i++) {
            addBar(series, 0, 10);
        }
        series.setMaximumBarCount(14); // beginIndex advances to 10
        BullishHaramiIndicator indicator = new BullishHaramiIndicator(series);
        assertThat(indicator.getValue(15)).isFalse();
        assertThat(indicator.getValue(16)).isTrue();
    }

    @Test
    public void cachedMatchIsInvalidatedWhenBaselineWindowRollsPast() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 10; i++) {
            addBar(series, 0, 10);
        }
        for (int i = 0; i < 5; i++) {
            addBar(series, 0, 10); // baseline at 10..14
        }
        addBar(series, 25, 5); // previous at index 15
        addBar(series, 20, 22); // current at index 16
        for (int i = 0; i < 7; i++) {
            addBar(series, 0, 10);
        }
        BullishHaramiIndicator indicator = new BullishHaramiIndicator(series);
        assertThat(indicator.getValue(16)).isTrue();
        series.setMaximumBarCount(9); // beginIndex advances past the baseline window
        assertThat(indicator.getValue(16)).isFalse();
    }

    @Test
    public void shouldRespectCustomAveragePeriod() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBar(series, 0, 10);
        addBar(series, 0, 10);
        addBar(series, 0, 10);
        addBar(series, 25, 5); // previous at index 3
        addBar(series, 20, 22); // current at index 4
        BullishHaramiIndicator indicator = new BullishHaramiIndicator(series, 3);
        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(4);
        assertThat(indicator.getValue(3)).isFalse();
        assertThat(indicator.getValue(4)).isTrue();
    }

    @Test
    public void shouldRejectInvalidConstructorArguments() {
        assertThrows(NullPointerException.class, () -> new BullishHaramiIndicator(null));
        assertThrows(IllegalArgumentException.class, () -> new BullishHaramiIndicator(haramiSeries(25, 5, 20, 22), 0));
    }

    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        BarSeries series = serializationSeries(numFactory);
        return List.of(serializationFixture(series, new BullishHaramiIndicator(series), stableIndexes(series)),
                serializationFixture(series, new BullishHaramiIndicator(series, 3), stableIndexes(series)));
    }
}

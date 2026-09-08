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

public class BearishHaramiIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    public BearishHaramiIndicatorTest(NumFactory numFactory) {
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

    // 5 baseline bars of body 10 (open 0, close 10), then a long bullish body
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
        BearishHaramiIndicator indicator = new BearishHaramiIndicator(haramiSeries(5, 25, 22, 20));
        assertThat(indicator.getValue(6)).isTrue();
    }

    @Test
    public void shouldCountUnstableBars() {
        BearishHaramiIndicator indicator = new BearishHaramiIndicator(haramiSeries(5, 25, 22, 20));
        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(6);
    }

    @Test
    public void shouldReturnFalseBeforeStableBoundary() {
        BearishHaramiIndicator indicator = new BearishHaramiIndicator(haramiSeries(5, 25, 22, 20));
        for (int i = 0; i < 6; i++) {
            assertThat(indicator.getValue(i)).isFalse();
        }
        assertThat(indicator.getValue(6)).isTrue();
    }

    @Test
    public void shouldNotDetectPatternWhenPreviousCandleIsBearish() {
        BearishHaramiIndicator indicator = new BearishHaramiIndicator(haramiSeries(25, 5, 22, 20));
        assertThat(indicator.getValue(6)).isFalse();
    }

    @Test
    public void shouldTreatSignedZeroContainmentAsInclusive() {
        // Baseline bodies of 9 so that the previous body 10 counts as long.
        // Bullish previous body [0.0, 10] and bearish current body [-0.0, 2]
        // share a numerically zero bottom: containment is inclusive, so the
        // sign bit must not break it regardless of num factory.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 5; i++) {
            addBar(series, 0, 9);
        }
        addBar(series, 0.0, 10);
        addBar(series, 2, -0.0);
        assertThat(new BearishHaramiIndicator(series).getValue(6)).isTrue();

        // Control: containment across genuinely distinct non-zero levels.
        BarSeries control = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 5; i++) {
            addBar(control, 0, 9);
        }
        addBar(control, 0.0, 10);
        addBar(control, 2, 1);
        assertThat(new BearishHaramiIndicator(control).getValue(6)).isTrue();
    }

    @Test
    public void shouldNotDetectPatternWhenPreviousBodyIsNotLong() {
        // previous body exactly at the prior-average body (10): strict > required
        BearishHaramiIndicator indicator = new BearishHaramiIndicator(haramiSeries(5, 15, 22, 20));
        assertThat(indicator.getValue(6)).isFalse();
    }

    @Test
    public void shouldNotDetectPatternWhenCurrentBodyIsNotShort() {
        // current body exactly at half the prior-average body (6): strict < required
        BearishHaramiIndicator indicator = new BearishHaramiIndicator(haramiSeries(5, 25, 25, 19));
        assertThat(indicator.getValue(6)).isFalse();
    }

    @Test
    public void shouldNotDetectPatternWhenCurrentBodyTopExceedsPreviousTop() {
        BearishHaramiIndicator indicator = new BearishHaramiIndicator(haramiSeries(5, 25, 28, 26));
        assertThat(indicator.getValue(6)).isFalse();
    }

    @Test
    public void shouldNotDetectPatternWhenCurrentBodyBottomIsBelowPreviousBottom() {
        BearishHaramiIndicator indicator = new BearishHaramiIndicator(haramiSeries(5, 25, 5, 3));
        assertThat(indicator.getValue(6)).isFalse();
    }

    @Test
    public void shouldDetectPatternWhenCurrentBodySharesPreviousEndpoint() {
        // containment is inclusive: the current bodyTop equals the previous one
        BearishHaramiIndicator indicator = new BearishHaramiIndicator(haramiSeries(5, 25, 25, 23));
        assertThat(indicator.getValue(6)).isTrue();
    }

    @Test
    public void shouldNotDetectPatternWhenSecondCandleIsNotBearish() {
        // the reversal color is part of the contract: the second candle must
        // be bearish, opposite the first
        BearishHaramiIndicator indicator = new BearishHaramiIndicator(haramiSeries(5, 25, 20, 22));
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
        addBar(downtrend, 5, 25);
        addBar(downtrend, 22, 20);
        addBar(uptrend, 5, 25);
        addBar(uptrend, 22, 20);
        // bars 0..5 lie outside the pattern window (11, 12) and the threshold
        // baseline windows [6..10] and [7..11]; varying them must not matter.
        assertThat(new BearishHaramiIndicator(downtrend).getValue(12)).isTrue();
        assertThat(new BearishHaramiIndicator(uptrend).getValue(12)).isTrue();
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
        addBar(series, 5, 25); // previous at index 15
        addBar(series, 22, 20); // current at index 16
        for (int i = 0; i < 7; i++) {
            addBar(series, 0, 10);
        }
        series.setMaximumBarCount(14); // beginIndex advances to 10
        BearishHaramiIndicator indicator = new BearishHaramiIndicator(series);
        assertThat(indicator.getValue(15)).isFalse();
        assertThat(indicator.getValue(16)).isTrue();
    }

    @Test
    public void shouldRespectCustomAveragePeriod() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBar(series, 0, 10);
        addBar(series, 0, 10);
        addBar(series, 0, 10);
        addBar(series, 5, 25); // previous at index 3
        addBar(series, 22, 20); // current at index 4
        BearishHaramiIndicator indicator = new BearishHaramiIndicator(series, 3);
        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(4);
        assertThat(indicator.getValue(3)).isFalse();
        assertThat(indicator.getValue(4)).isTrue();
    }

    @Test
    public void shouldRejectInvalidConstructorArguments() {
        assertThrows(NullPointerException.class, () -> new BearishHaramiIndicator(null));
        assertThrows(IllegalArgumentException.class, () -> new BearishHaramiIndicator(haramiSeries(5, 25, 22, 20), 0));
    }

    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        BarSeries series = serializationSeries(numFactory);
        return List.of(serializationFixture(series, new BearishHaramiIndicator(series), stableIndexes(series)),
                serializationFixture(series, new BearishHaramiIndicator(series, 3), stableIndexes(series)));
    }
}

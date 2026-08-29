/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.candles;

import static org.assertj.core.api.Assertions.assertThat;
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

public class BullishEngulfingIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    public BullishEngulfingIndicatorTest(NumFactory numFactory) {
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

    // previous bearish body (30, 20), current bullish body (15, 35) engulfing it
    private BarSeries engulfingSeries(double prevOpen, double prevClose, double currOpen, double currClose) {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBar(series, prevOpen, prevClose);
        addBar(series, currOpen, currClose);
        return series;
    }

    @Test
    public void shouldDetectPatternWhenAllConditionsAreSatisfied() {
        BullishEngulfingIndicator indicator = new BullishEngulfingIndicator(engulfingSeries(30, 20, 15, 35));
        assertThat(indicator.getValue(1)).isTrue();
    }

    @Test
    public void shouldCountUnstableBars() {
        assertThat(new BullishEngulfingIndicator(engulfingSeries(30, 20, 15, 35)).getCountOfUnstableBars())
                .isEqualTo(1);
    }

    @Test
    public void shouldReturnFalseBeforeStableBoundary() {
        BullishEngulfingIndicator indicator = new BullishEngulfingIndicator(engulfingSeries(30, 20, 15, 35));
        assertThat(indicator.getValue(0)).isFalse();
        assertThat(indicator.getValue(1)).isTrue();
    }

    @Test
    public void shouldNotDetectPatternWhenPreviousCandleIsBullish() {
        BullishEngulfingIndicator indicator = new BullishEngulfingIndicator(engulfingSeries(20, 30, 15, 35));
        assertThat(indicator.getValue(1)).isFalse();
    }

    @Test
    public void shouldNotDetectPatternWhenCurrentCandleIsBearish() {
        BullishEngulfingIndicator indicator = new BullishEngulfingIndicator(engulfingSeries(30, 20, 35, 15));
        assertThat(indicator.getValue(1)).isFalse();
    }

    @Test
    public void shouldNotDetectPatternWhenCurrentBodyDoesNotCoverPreviousTop() {
        BullishEngulfingIndicator indicator = new BullishEngulfingIndicator(engulfingSeries(30, 20, 15, 28));
        assertThat(indicator.getValue(1)).isFalse();
    }

    @Test
    public void shouldNotDetectPatternWhenCurrentBodyDoesNotCoverPreviousBottom() {
        BullishEngulfingIndicator indicator = new BullishEngulfingIndicator(engulfingSeries(30, 20, 22, 35));
        assertThat(indicator.getValue(1)).isFalse();
    }

    @Test
    public void shouldNotDetectPatternWhenBodiesAreIdentical() {
        // containment holds but no endpoint is strictly exceeded
        BullishEngulfingIndicator indicator = new BullishEngulfingIndicator(engulfingSeries(30, 20, 20, 30));
        assertThat(indicator.getValue(1)).isFalse();
    }

    @Test
    public void shouldDetectPatternWhenEndpointIsShared() {
        // containment is inclusive: the current bodyBottom equals the previous one
        BullishEngulfingIndicator indicator = new BullishEngulfingIndicator(engulfingSeries(30, 20, 20, 35));
        assertThat(indicator.getValue(1)).isTrue();
    }

    @Test
    public void nonFiniteEndpointsDoNotMatch() {
        NumFactory doubleFactory = DoubleNumFactory.getInstance();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(doubleFactory).build();
        series.barBuilder()
                .openPrice(doubleFactory.numOf(Double.POSITIVE_INFINITY))
                .closePrice(doubleFactory.numOf(10))
                .highPrice(doubleFactory.numOf(Double.POSITIVE_INFINITY))
                .lowPrice(doubleFactory.numOf(10))
                .add();
        series.barBuilder()
                .openPrice(doubleFactory.numOf(0))
                .closePrice(doubleFactory.numOf(Double.POSITIVE_INFINITY))
                .highPrice(doubleFactory.numOf(Double.POSITIVE_INFINITY))
                .lowPrice(doubleFactory.numOf(0))
                .add();

        assertThat(new BullishEngulfingIndicator(series).getValue(1)).isFalse();
    }

    @Test
    public void contextOutsidePatternWindowDoesNotChangeResult() {
        BarSeries uptrend = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        BarSeries downtrend = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 11; i++) {
            addBar(uptrend, i, i + 8);
            addBar(downtrend, 60 - i, 52 - i);
        }
        addBar(uptrend, 30, 20);
        addBar(uptrend, 15, 35);
        addBar(downtrend, 30, 20);
        addBar(downtrend, 15, 35);
        // bars 0..10 lie outside the pattern window (11, 12)
        assertThat(new BullishEngulfingIndicator(uptrend).getValue(12)).isTrue();
        assertThat(new BullishEngulfingIndicator(downtrend).getValue(12)).isTrue();
    }

    @Test
    public void rollingSeriesAdvancesBeginIndexWithoutChangingBoundary() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 10; i++) {
            addBar(series, 0, 10);
        }
        addBar(series, 30, 20); // previous at index 10
        addBar(series, 15, 35); // current at index 11
        for (int i = 0; i < 12; i++) {
            addBar(series, 0, 10);
        }
        series.setMaximumBarCount(14); // beginIndex advances to 10
        BullishEngulfingIndicator indicator = new BullishEngulfingIndicator(series);
        assertThat(indicator.getValue(10)).isFalse();
        assertThat(indicator.getValue(11)).isTrue();
    }

    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        BarSeries series = serializationSeries(numFactory);
        return List.of(serializationFixture(series, new BullishEngulfingIndicator(series), stableIndexes(series)));
    }
}

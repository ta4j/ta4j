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

public class BearishEngulfingIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    public BearishEngulfingIndicatorTest(NumFactory numFactory) {
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

    // previous bullish body (20, 30), current bearish body (35, 15) engulfing it
    private BarSeries engulfingSeries(double prevOpen, double prevClose, double currOpen, double currClose) {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBar(series, prevOpen, prevClose);
        addBar(series, currOpen, currClose);
        return series;
    }

    @Test
    public void shouldDetectPatternWhenAllConditionsAreSatisfied() {
        BearishEngulfingIndicator indicator = new BearishEngulfingIndicator(engulfingSeries(20, 30, 35, 15));
        assertThat(indicator.getValue(1)).isTrue();
    }

    @Test
    public void shouldCountUnstableBars() {
        assertThat(new BearishEngulfingIndicator(engulfingSeries(20, 30, 35, 15)).getCountOfUnstableBars())
                .isEqualTo(1);
    }

    @Test
    public void shouldReturnFalseBeforeStableBoundary() {
        BearishEngulfingIndicator indicator = new BearishEngulfingIndicator(engulfingSeries(20, 30, 35, 15));
        assertThat(indicator.getValue(0)).isFalse();
        assertThat(indicator.getValue(1)).isTrue();
    }

    @Test
    public void shouldNotDetectPatternWhenPreviousCandleIsBearish() {
        BearishEngulfingIndicator indicator = new BearishEngulfingIndicator(engulfingSeries(30, 20, 35, 15));
        assertThat(indicator.getValue(1)).isFalse();
    }

    @Test
    public void shouldNotDetectPatternWhenCurrentCandleIsBullish() {
        BearishEngulfingIndicator indicator = new BearishEngulfingIndicator(engulfingSeries(20, 30, 15, 35));
        assertThat(indicator.getValue(1)).isFalse();
    }

    @Test
    public void shouldNotDetectPatternWhenCurrentBodyDoesNotCoverPreviousTop() {
        BearishEngulfingIndicator indicator = new BearishEngulfingIndicator(engulfingSeries(20, 30, 28, 15));
        assertThat(indicator.getValue(1)).isFalse();
    }

    @Test
    public void shouldNotDetectPatternWhenCurrentBodyDoesNotCoverPreviousBottom() {
        BearishEngulfingIndicator indicator = new BearishEngulfingIndicator(engulfingSeries(20, 30, 35, 22));
        assertThat(indicator.getValue(1)).isFalse();
    }

    @Test
    public void shouldNotDetectPatternWhenBodiesAreIdentical() {
        // containment holds but no endpoint is strictly exceeded
        BearishEngulfingIndicator indicator = new BearishEngulfingIndicator(engulfingSeries(20, 30, 20, 30));
        assertThat(indicator.getValue(1)).isFalse();
    }

    @Test
    public void shouldDetectPatternWhenEndpointIsShared() {
        // containment is inclusive: the current bodyBottom equals the previous one
        BearishEngulfingIndicator indicator = new BearishEngulfingIndicator(engulfingSeries(20, 30, 35, 20));
        assertThat(indicator.getValue(1)).isTrue();
    }

    @Test
    public void nonFiniteEndpointsDoNotMatch() {
        NumFactory doubleFactory = DoubleNumFactory.getInstance();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(doubleFactory).build();
        series.barBuilder()
                .openPrice(doubleFactory.numOf(0))
                .closePrice(doubleFactory.numOf(Double.POSITIVE_INFINITY))
                .highPrice(doubleFactory.numOf(Double.POSITIVE_INFINITY))
                .lowPrice(doubleFactory.numOf(0))
                .add();
        series.barBuilder()
                .openPrice(doubleFactory.numOf(Double.POSITIVE_INFINITY))
                .closePrice(doubleFactory.numOf(10))
                .highPrice(doubleFactory.numOf(Double.POSITIVE_INFINITY))
                .lowPrice(doubleFactory.numOf(10))
                .add();

        assertThat(new BearishEngulfingIndicator(series).getValue(1)).isFalse();
    }

    @Test
    public void contextOutsidePatternWindowDoesNotChangeResult() {
        BarSeries uptrend = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        BarSeries downtrend = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 11; i++) {
            addBar(uptrend, i, i + 8);
            addBar(downtrend, 60 - i, 52 - i);
        }
        addBar(uptrend, 20, 30);
        addBar(uptrend, 35, 15);
        addBar(downtrend, 20, 30);
        addBar(downtrend, 35, 15);
        // bars 0..10 lie outside the pattern window (11, 12)
        assertThat(new BearishEngulfingIndicator(uptrend).getValue(12)).isTrue();
        assertThat(new BearishEngulfingIndicator(downtrend).getValue(12)).isTrue();
    }

    @Test
    public void rollingSeriesAdvancesBeginIndexWithoutChangingBoundary() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 10; i++) {
            addBar(series, 0, 10);
        }
        addBar(series, 20, 30); // previous at index 10
        addBar(series, 35, 15); // current at index 11
        for (int i = 0; i < 12; i++) {
            addBar(series, 0, 10);
        }
        series.setMaximumBarCount(14); // beginIndex advances to 10
        BearishEngulfingIndicator indicator = new BearishEngulfingIndicator(series);
        assertThat(indicator.getValue(10)).isFalse();
        assertThat(indicator.getValue(11)).isTrue();
    }

    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        BarSeries series = serializationSeries(numFactory);
        return List.of(serializationFixture(series, new BearishEngulfingIndicator(series), stableIndexes(series)));
    }
}

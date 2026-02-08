/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.indicators.helpers.DifferenceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class ZScoreIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries series;

    public ZScoreIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().closePrice(10).openPrice(10).highPrice(10).lowPrice(10).volume(1).add();
        series.barBuilder().closePrice(12).openPrice(12).highPrice(12).lowPrice(12).volume(1).add();
    }

    @Test
    public void shouldComputeZScoreFromConstantInputs() {
        var deviation = new ConstantIndicator<>(series, numFactory.numOf(5));
        var std = new ConstantIndicator<>(series, numFactory.numOf(2));
        var zScore = new ZScoreIndicator(deviation, std);

        assertNumEquals(2.5, zScore.getValue(0));
        assertNumEquals(2.5, zScore.getValue(1));
    }

    @Test
    public void shouldReturnNaNWhenStandardDeviationIsZero() {
        var deviation = new ConstantIndicator<>(series, numFactory.numOf(1));
        var std = new ConstantIndicator<>(series, numFactory.zero());
        var zScore = new ZScoreIndicator(deviation, std);

        assertThat(zScore.getValue(0).isNaN()).isTrue();
    }

    @Test
    public void shouldRespectUnstableBarsFromInputs() {
        var closePrice = new ClosePriceIndicator(series);
        var deviation = new DifferenceIndicator(closePrice);
        var std = new ConstantIndicator<>(series, numFactory.one());
        var zScore = new ZScoreIndicator(deviation, std);

        assertThat(zScore.getCountOfUnstableBars()).isEqualTo(1);
        assertThat(zScore.getValue(0).isNaN()).isTrue();
        assertNumEquals(2.0, zScore.getValue(1));
    }

    @Test
    public void shouldRoundTripSerializeAndDeserialize() {
        var deviation = new ConstantIndicator<>(series, numFactory.numOf(5));
        var std = new ConstantIndicator<>(series, numFactory.numOf(2));
        var zScore = new ZScoreIndicator(deviation, std);

        String json = zScore.toJson();
        Indicator<?> restored = Indicator.fromJson(series, json);

        assertThat(restored).isInstanceOf(ZScoreIndicator.class);
        assertThat(restored.toDescriptor()).isEqualTo(zScore.toDescriptor());
        @SuppressWarnings("unchecked")
        Indicator<Num> restoredIndicator = (Indicator<Num>) restored;
        assertThat(restoredIndicator.getValue(1)).isEqualByComparingTo(zScore.getValue(1));
    }
}

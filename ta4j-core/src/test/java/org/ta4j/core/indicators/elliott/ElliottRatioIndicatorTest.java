/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.indicators.elliott.ElliottRatio.RatioType;
import org.ta4j.core.indicators.elliott.StubSwingIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

class ElliottRatioIndicatorTest {

    @Test
    void returnsNanWhenSwingsInsufficient() {
        var series = new MockBarSeriesBuilder().build();
        series.barBuilder().openPrice(10).highPrice(10).lowPrice(10).closePrice(10).volume(0).add();
        series.barBuilder().openPrice(11).highPrice(11).lowPrice(11).closePrice(11).volume(0).add();

        var swingIndicator = new ElliottSwingIndicator(series, 1, ElliottDegree.MINOR);
        var ratioIndicator = new ElliottRatioIndicator(swingIndicator);

        var ratio = ratioIndicator.getValue(series.getEndIndex());
        assertThat(ratio.type()).isEqualTo(RatioType.NONE);
        assertThat(ratio.value().isNaN()).isTrue();
    }

    @Test
    void computesRetracementRatioFromSeries() {
        var series = new MockBarSeriesBuilder().build();
        double[] closes = { 10, 12, 11, 13, 12, 14, 13 };
        for (double close : closes) {
            series.barBuilder().openPrice(close).highPrice(close).lowPrice(close).closePrice(close).volume(0).add();
        }
        var swingIndicator = new ElliottSwingIndicator(series, 1, ElliottDegree.MINOR);
        var ratioIndicator = new ElliottRatioIndicator(swingIndicator);

        var ratio = ratioIndicator.getValue(5);
        assertThat(ratio.type()).isEqualTo(RatioType.RETRACEMENT);
        assertThat(ratio.value()).isEqualByComparingTo(series.numFactory().numOf(0.5));

        var tolerance = series.numFactory().numOf(0.2);
        assertThat(ratioIndicator.isNearLevel(5, series.numFactory().numOf(0.5), tolerance)).isTrue();
    }

    @Test
    void computesExtensionRatioFromSeries() {
        var series = new MockBarSeriesBuilder().build();
        double[] closes = { 10, 12, 11, 13, 12, 14, 13 };
        for (double close : closes) {
            series.barBuilder().openPrice(close).highPrice(close).lowPrice(close).closePrice(close).volume(0).add();
        }
        var swingIndicator = new ElliottSwingIndicator(series, 1, ElliottDegree.MINOR);
        var ratioIndicator = new ElliottRatioIndicator(swingIndicator);

        var ratio = ratioIndicator.getValue(series.getEndIndex());
        assertThat(ratio.type()).isEqualTo(RatioType.EXTENSION);
        assertThat(ratio.value()).isEqualByComparingTo(series.numFactory().one());

        var tolerance = series.numFactory().numOf(0.01);
        assertThat(ratioIndicator.isNearLevel(series.getEndIndex(), series.numFactory().one(), tolerance)).isTrue();
    }

    @Test
    void computesExtensionRatios() {
        var series = new MockBarSeriesBuilder().build();
        double[] closes = { 10, 12, 14, 16, 18 };
        for (double close : closes) {
            series.barBuilder().openPrice(close).highPrice(close).lowPrice(close).closePrice(close).volume(0).add();
        }
        var swings = new ArrayList<List<ElliottSwing>>();
        var factory = series.numFactory();
        swings.add(List.of());
        swings.add(List.of());
        swings.add(List.of(new ElliottSwing(0, 1, factory.numOf(10), factory.numOf(12), ElliottDegree.MINOR)));
        swings.add(List.of(new ElliottSwing(0, 1, factory.numOf(10), factory.numOf(12), ElliottDegree.MINOR),
                new ElliottSwing(1, 2, factory.numOf(12), factory.numOf(14), ElliottDegree.MINOR)));
        swings.add(List.of(new ElliottSwing(0, 1, factory.numOf(10), factory.numOf(12), ElliottDegree.MINOR),
                new ElliottSwing(1, 2, factory.numOf(12), factory.numOf(14), ElliottDegree.MINOR),
                new ElliottSwing(2, 3, factory.numOf(14), factory.numOf(16), ElliottDegree.MINOR)));
        swings.add(List.of(new ElliottSwing(0, 1, factory.numOf(10), factory.numOf(12), ElliottDegree.MINOR),
                new ElliottSwing(1, 2, factory.numOf(12), factory.numOf(14), ElliottDegree.MINOR),
                new ElliottSwing(2, 3, factory.numOf(14), factory.numOf(16), ElliottDegree.MINOR),
                new ElliottSwing(3, 4, factory.numOf(16), factory.numOf(18), ElliottDegree.MINOR)));

        var swingIndicator = new StubSwingIndicator(series, swings);
        var ratioIndicator = new ElliottRatioIndicator(swingIndicator);

        var ratio = ratioIndicator.getValue(series.getEndIndex());
        assertThat(ratio.type()).isEqualTo(RatioType.EXTENSION);
        var expected = factory.numOf(2).dividedBy(factory.numOf(2));
        assertThat(ratio.value()).isEqualByComparingTo(expected);
    }
}

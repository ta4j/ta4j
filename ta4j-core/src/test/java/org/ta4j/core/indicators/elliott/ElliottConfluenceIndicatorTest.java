/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators.elliott;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.indicators.elliott.ElliottRatio.RatioType;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

class ElliottConfluenceIndicatorTest {

    @Test
    void aggregatesRatioAndChannelMatches() {
        var series = new MockBarSeriesBuilder().build();
        double[] closes = { 10, 12, 9, 13, 8, 14, 7, 15, 6 };
        for (double close : closes) {
            series.barBuilder().openPrice(close).highPrice(close).lowPrice(close).closePrice(close).volume(0).add();
        }

        var swingIndicator = new ElliottSwingIndicator(series, 1, ElliottDegree.MINOR);
        var ratioIndicator = new ElliottRatioIndicator(swingIndicator);
        var channelIndicator = new ElliottChannelIndicator(swingIndicator);
        var priceIndicator = new ClosePriceIndicator(series);

        var factory = series.numFactory();
        var retracements = List.of(factory.numOf(1.0));
        var extensions = List.of(factory.numOf(1.272));
        var ratioTolerance = factory.numOf(0.2);
        var channelTolerance = factory.numOf(0.5);
        var minimumScore = factory.numOf(2);

        var indicator = new ElliottConfluenceIndicator(priceIndicator, ratioIndicator, channelIndicator, retracements,
                extensions, ratioTolerance, channelTolerance, minimumScore);

        var index = series.getEndIndex();
        var score = indicator.getValue(index);
        assertThat(score).isEqualByComparingTo(factory.numOf(2));
        assertThat(indicator.isConfluent(index)).isTrue();

        var ratio = ratioIndicator.getValue(index);
        assertThat(ratio.type()).isEqualTo(RatioType.EXTENSION);
    }

    @Test
    void defaultConfigurationProducesScore() {
        var series = new MockBarSeriesBuilder().build();
        double[] closes = { 10, 12, 9, 13, 8, 14, 7, 15, 6 };
        for (double close : closes) {
            series.barBuilder().openPrice(close).highPrice(close).lowPrice(close).closePrice(close).volume(0).add();
        }

        var swingIndicator = new ElliottSwingIndicator(series, 1, ElliottDegree.MINOR);
        var ratioIndicator = new ElliottRatioIndicator(swingIndicator);
        var channelIndicator = new ElliottChannelIndicator(swingIndicator);
        var priceIndicator = new ClosePriceIndicator(series);

        var indicator = new ElliottConfluenceIndicator(priceIndicator, ratioIndicator, channelIndicator);
        var score = indicator.getValue(series.getEndIndex());
        assertThat(score.isGreaterThanOrEqual(series.numFactory().numOf(1))).isTrue();
    }
}

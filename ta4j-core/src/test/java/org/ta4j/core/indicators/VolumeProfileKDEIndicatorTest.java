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
package org.ta4j.core.indicators;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.indicators.supportresistance.VolumeProfileKDEIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class VolumeProfileKDEIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public VolumeProfileKDEIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void shouldHighlightDominantVolumeNode() {
        BarSeries series = buildSeries(new double[] { 10, 10.5, 11 }, new double[] { 150, 60, 25 });
        var price = new ClosePriceIndicator(series);
        var volume = new VolumeIndicator(series, 1);

        var indicator = new VolumeProfileKDEIndicator(price, volume, 0, numOf(0.5));

        Num lowerDensity = indicator.getDensityAtPrice(2, numOf(10));
        Num higherDensity = indicator.getDensityAtPrice(2, numOf(11));

        assertThat(lowerDensity.isGreaterThan(higherDensity)).isTrue();
        assertThat(indicator.getModePrice(2)).isEqualByComparingTo(numOf(10));
    }

    @Test
    public void shouldRespectLookbackLength() {
        BarSeries series = buildSeries(new double[] { 10, 10.5, 11 }, new double[] { 150, 60, 25 });
        var price = new ClosePriceIndicator(series);
        var volume = new VolumeIndicator(series, 1);

        var full = new VolumeProfileKDEIndicator(price, volume, 0, numOf(0.5));
        var truncated = new VolumeProfileKDEIndicator(price, volume, 2, numOf(0.5));

        Num fullDensity = full.getDensityAtPrice(2, numOf(10));
        Num truncatedDensity = truncated.getDensityAtPrice(2, numOf(10));

        assertThat(truncatedDensity.isLessThan(fullDensity)).isTrue();
    }

    @Test
    public void shouldSumVolumesWhenBandwidthIsZero() {
        BarSeries series = buildSeries(new double[] { 10, 10, 11 }, new double[] { 100, 200, 50 });
        var price = new ClosePriceIndicator(series);
        var volume = new VolumeIndicator(series, 1);

        var indicator = new VolumeProfileKDEIndicator(price, volume, 0, numOf(0));

        assertThat(indicator.getDensityAtPrice(2, numOf(10))).isEqualByComparingTo(numOf(300));
        assertThat(indicator.getDensityAtPrice(2, numOf(11))).isEqualByComparingTo(numOf(50));
    }

    private BarSeries buildSeries(double[] closes, double[] volumes) {
        var builder = new MockBarSeriesBuilder().withNumFactory(numFactory);
        BarSeries barSeries = builder.build();
        for (int i = 0; i < closes.length; i++) {
            barSeries.barBuilder()
                    .closePrice(closes[i])
                    .openPrice(closes[i])
                    .highPrice(closes[i])
                    .lowPrice(closes[i])
                    .volume(volumes[i])
                    .add();
        }
        return barSeries;
    }
}

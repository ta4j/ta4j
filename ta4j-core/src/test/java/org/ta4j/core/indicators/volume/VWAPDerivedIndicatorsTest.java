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
package org.ta4j.core.indicators.volume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class VWAPDerivedIndicatorsTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries series;
    private ClosePriceIndicator closePrice;
    private VolumeIndicator volume;
    private VWAPIndicator vwap;

    public VWAPDerivedIndicatorsTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().closePrice(10).openPrice(10).highPrice(10).lowPrice(10).volume(1).add();
        series.barBuilder().closePrice(12).openPrice(12).highPrice(12).lowPrice(12).volume(2).add();
        series.barBuilder().closePrice(11).openPrice(11).highPrice(11).lowPrice(11).volume(3).add();
        series.barBuilder().closePrice(14).openPrice(14).highPrice(14).lowPrice(14).volume(4).add();
        closePrice = new ClosePriceIndicator(series);
        volume = new VolumeIndicator(series);
        vwap = new VWAPIndicator(closePrice, volume, 3);
    }

    @Test
    public void vwapStandardDeviationAndDeviation() {
        var std = new VWAPStandardDeviationIndicator(vwap);
        var deviation = new VWAPDeviationIndicator(closePrice, vwap);

        assertNumEquals(0.6872, std.getValue(2));
        assertNumEquals(1.3426, std.getValue(3));

        assertNumEquals(-0.1667, deviation.getValue(2));
        assertNumEquals(1.4444, deviation.getValue(3));

        assertThat(std.getWindowStartIndex(3)).isEqualTo(1);
    }

    @Test
    public void vwapZScore() {
        var std = new VWAPStandardDeviationIndicator(vwap);
        var deviation = new VWAPDeviationIndicator(closePrice, vwap);
        var zScore = new VWAPZScoreIndicator(deviation, std);

        assertNumEquals(1.0759, zScore.getValue(3));

        var flatSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        flatSeries.barBuilder().closePrice(5).openPrice(5).highPrice(5).lowPrice(5).volume(10).add();
        flatSeries.barBuilder().closePrice(5).openPrice(5).highPrice(5).lowPrice(5).volume(8).add();
        var flatClose = new ClosePriceIndicator(flatSeries);
        var flatVolume = new VolumeIndicator(flatSeries);
        var flatVwap = new VWAPIndicator(flatClose, flatVolume, 2);
        var flatStd = new VWAPStandardDeviationIndicator(flatVwap);
        var flatDeviation = new VWAPDeviationIndicator(flatClose, flatVwap);
        var flatZScore = new VWAPZScoreIndicator(flatDeviation, flatStd);

        assertThat(flatZScore.getValue(1).isNaN()).isTrue();
    }

    @Test
    public void vwapBands() {
        var std = new VWAPStandardDeviationIndicator(vwap);
        var upper = new VWAPBandIndicator(vwap, std, 2, VWAPBandIndicator.BandType.UPPER);
        var lower = new VWAPBandIndicator(vwap, std, 2, VWAPBandIndicator.BandType.LOWER);

        assertNumEquals(12.5411, upper.getValue(2));
        assertNumEquals(9.7923, lower.getValue(2));
        assertNumEquals(15.2407, upper.getValue(3));
        assertNumEquals(9.8704, lower.getValue(3));

        var invalidSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        invalidSeries.barBuilder().closePrice(10).openPrice(10).highPrice(10).lowPrice(10).volume(1).add();
        invalidSeries.barBuilder().closePrice(11).openPrice(11).highPrice(11).lowPrice(11).volume(-1).add();
        var invalidClose = new ClosePriceIndicator(invalidSeries);
        var invalidVolume = new VolumeIndicator(invalidSeries);
        var invalidVwap = new VWAPIndicator(invalidClose, invalidVolume, 2);
        var invalidStd = new VWAPStandardDeviationIndicator(invalidVwap);
        var invalidUpper = new VWAPBandIndicator(invalidVwap, invalidStd, 1, VWAPBandIndicator.BandType.UPPER);

        assertThat(invalidUpper.getValue(1).isNaN()).isTrue();
    }
}

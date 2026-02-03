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
package org.ta4j.core.indicators.wyckoff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ta4j.core.num.NaN.NaN;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class WyckoffVolumeProfileTest extends AbstractIndicatorTest<BarSeries, Num> {

    private BarSeries series;

    public WyckoffVolumeProfileTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBar(series, 100);
        addBar(series, 110);
        addBar(series, 120);
        addBar(series, 80);
        addBar(series, 60);
        addBar(series, 420);
    }

    @Test
    public void shouldIdentifyClimaxAndDryUpConditions() {
        var profile = new WyckoffVolumeProfile(series, 2, 4, numOf(1.4), numOf(0.8));

        var dryUpSnapshot = profile.snapshot(4);
        var expectedDryUpRatio = numOf(80).plus(numOf(60))
                .dividedBy(numOf(2))
                .dividedBy(numOf(110).plus(numOf(120)).plus(numOf(80)).plus(numOf(60)).dividedBy(numOf(4)));
        assertThat(dryUpSnapshot.relativeVolume()).isEqualByComparingTo(expectedDryUpRatio);
        assertThat(dryUpSnapshot.dryUp()).isTrue();
        assertThat(dryUpSnapshot.climax()).isFalse();

        var climaxSnapshot = profile.snapshot(5);
        var expectedClimaxRatio = numOf(60).plus(numOf(420))
                .dividedBy(numOf(2))
                .dividedBy(numOf(120).plus(numOf(80)).plus(numOf(60)).plus(numOf(420)).dividedBy(numOf(4)));
        assertThat(climaxSnapshot.relativeVolume()).isEqualByComparingTo(expectedClimaxRatio);
        assertThat(climaxSnapshot.climax()).isTrue();
        assertThat(climaxSnapshot.dryUp()).isFalse();
    }

    @Test
    public void shouldReturnEmptySnapshotWhenVolumeIsNaN() {
        var nanSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        nanSeries.barBuilder().openPrice(1).highPrice(1.1).lowPrice(0.9).closePrice(1.0).volume(NaN).add();
        var profile = new WyckoffVolumeProfile(nanSeries, 1, 2, numOf(1.2), numOf(0.8));

        var snapshot = profile.snapshot(nanSeries.getEndIndex());
        assertThat(snapshot.volume().isNaN()).isTrue();
        assertThat(snapshot.relativeVolume().isNaN()).isTrue();
        assertThat(snapshot.climax()).isFalse();
        assertThat(snapshot.dryUp()).isFalse();
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRequirePositiveShortWindow() {
        new WyckoffVolumeProfile(series, 0, 1, numOf(1.2), numOf(0.8));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRequireLongWindowNotShorterThanShortWindow() {
        new WyckoffVolumeProfile(series, 3, 2, numOf(1.2), numOf(0.8));
    }

    @Test
    public void shouldHandleUnavailableAveragesGracefully() {
        var shortOnlySeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        shortOnlySeries.barBuilder().openPrice(1).highPrice(1.1).lowPrice(0.9).closePrice(1.0).volume(0).add();
        var profile = new WyckoffVolumeProfile(shortOnlySeries, 1, 1, numOf(1.2), numOf(0.8));

        var zeroVolumeSnapshot = profile.snapshot(shortOnlySeries.getEndIndex());
        assertThat(zeroVolumeSnapshot.volume()).isEqualByComparingTo(numOf(0));
        assertThat(zeroVolumeSnapshot.relativeVolume().isNaN()).isTrue();
        assertThat(zeroVolumeSnapshot.climax()).isFalse();
        assertThat(zeroVolumeSnapshot.dryUp()).isFalse();

        var missingAverageSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        missingAverageSeries.barBuilder().openPrice(1).highPrice(1.1).lowPrice(0.9).closePrice(1.0).volume(10).add();
        var missingAverageProfile = new WyckoffVolumeProfile(missingAverageSeries, 1, 3, numOf(1.2), numOf(0.8));

        var missingAverageSnapshot = missingAverageProfile.snapshot(missingAverageSeries.getEndIndex());
        assertThat(missingAverageSnapshot.volume()).isEqualByComparingTo(numOf(10));
        assertThat(missingAverageSnapshot.relativeVolume()).isEqualByComparingTo(numOf(1));
        assertThat(missingAverageSnapshot.climax()).isFalse();
        assertThat(missingAverageSnapshot.dryUp()).isFalse();
    }

    private void addBar(BarSeries target, double volume) {
        target.barBuilder().openPrice(1).highPrice(1.1).lowPrice(0.9).closePrice(1.0).volume(volume).add();
    }
}

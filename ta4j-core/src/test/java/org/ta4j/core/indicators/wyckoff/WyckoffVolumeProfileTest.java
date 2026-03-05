/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.wyckoff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;
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

    /**
     * Creates a new WyckoffVolumeProfileTest instance.
     */
    public WyckoffVolumeProfileTest(NumFactory numFactory) {
        super(numFactory);
    }

    /**
     * Initializes the test fixtures used by these scenarios.
     */
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

    /**
     * Verifies that identify climax and dry up conditions.
     */
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

    /**
     * Verifies that return empty snapshot when volume is na n.
     */
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

    /**
     * Verifies that require positive short window.
     */
    @Test
    public void shouldRequirePositiveShortWindow() {
        assertThrows(IllegalArgumentException.class,
                () -> new WyckoffVolumeProfile(series, 0, 1, numOf(1.2), numOf(0.8)));
    }

    /**
     * Verifies that require long window not shorter than short window.
     */
    @Test
    public void shouldRequireLongWindowNotShorterThanShortWindow() {
        assertThrows(IllegalArgumentException.class,
                () -> new WyckoffVolumeProfile(series, 3, 2, numOf(1.2), numOf(0.8)));
    }

    /**
     * Verifies that handle unavailable averages gracefully.
     */
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

    /**
     * Adds bar.
     */
    private void addBar(BarSeries target, double volume) {
        target.barBuilder().openPrice(1).highPrice(1.1).lowPrice(0.9).closePrice(1.0).volume(volume).add();
    }
}

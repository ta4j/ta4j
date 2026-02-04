/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.wyckoff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ta4j.core.num.NaN.NaN;

import java.util.EnumSet;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class WyckoffEventDetectorTest extends AbstractIndicatorTest<BarSeries, Num> {

    private BarSeries series;

    public WyckoffEventDetectorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBar(series, 11.0, 12.0, 10.5, 11.5);
        addBar(series, 10.5, 11.0, 10.0, 10.6);
        addBar(series, 9.8, 10.2, 9.0, 9.4);
        addBar(series, 10.0, 10.5, 9.5, 10.2);
        addBar(series, 10.6, 11.2, 10.4, 11.3);
        addBar(series, 10.2, 10.6, 10.0, 10.4);
        addBar(series, 9.6, 9.9, 9.1, 9.3);
    }

    @Test
    public void shouldDetectClimaxExtremesBeforeRangeForms() {
        var detector = new WyckoffEventDetector(series, numOf(0.05));
        var structure = new WyckoffStructureTracker.StructureSnapshot(NaN, NaN, -1, -1, NaN, false, false, false);
        var volume = new WyckoffVolumeProfile.VolumeSnapshot(numOf(9000), numOf(2.0), true, false);

        EnumSet<WyckoffEvent> events = detector.detect(2, structure, volume, WyckoffPhase.UNKNOWN);

        assertThat(events).containsExactly(WyckoffEvent.SELLING_CLIMAX);
    }

    @Test
    public void shouldDetectRangeBreakoutAndSignOfStrength() {
        var detector = new WyckoffEventDetector(series, numOf(0.05));
        var structure = new WyckoffStructureTracker.StructureSnapshot(numOf(9.0), numOf(11.0), 2, 4, numOf(11.3), false,
                true, false);
        var volume = new WyckoffVolumeProfile.VolumeSnapshot(numOf(1200), numOf(1.6), true, false);
        var previous = new WyckoffPhase(WyckoffCycleType.ACCUMULATION, WyckoffPhaseType.PHASE_C, 0.7, -1);

        EnumSet<WyckoffEvent> events = detector.detect(4, structure, volume, previous);

        assertThat(events).containsExactlyInAnyOrder(WyckoffEvent.RANGE_BREAKOUT, WyckoffEvent.SIGN_OF_STRENGTH);
    }

    @Test
    public void shouldDetectDryUpSupportAndSecondaryTest() {
        var detector = new WyckoffEventDetector(series, numOf(0.05));
        var structure = new WyckoffStructureTracker.StructureSnapshot(numOf(10.0), numOf(11.0), 3, 4, numOf(10.3), true,
                false, false);
        var dryUpVolume = new WyckoffVolumeProfile.VolumeSnapshot(numOf(800), numOf(0.7), false, true);

        EnumSet<WyckoffEvent> dryUpEvents = detector.detect(5, structure, dryUpVolume,
                new WyckoffPhase(WyckoffCycleType.ACCUMULATION, WyckoffPhaseType.PHASE_B, 0.55, -1));
        assertThat(dryUpEvents).contains(WyckoffEvent.LAST_POINT_OF_SUPPORT);
        assertThat(dryUpEvents).doesNotContain(WyckoffEvent.SECONDARY_TEST);

        var neutralVolume = new WyckoffVolumeProfile.VolumeSnapshot(numOf(900), numOf(1.0), false, false);
        EnumSet<WyckoffEvent> neutralEvents = detector.detect(5, structure, neutralVolume,
                new WyckoffPhase(WyckoffCycleType.ACCUMULATION, WyckoffPhaseType.PHASE_B, 0.55, -1));
        assertThat(neutralEvents).contains(WyckoffEvent.SECONDARY_TEST);
    }

    @Test
    public void shouldDetectUpthrustAfterDistribution() {
        var detector = new WyckoffEventDetector(series, numOf(0.05));
        var structure = new WyckoffStructureTracker.StructureSnapshot(numOf(10.0), numOf(11.5), 3, 4, numOf(11.6),
                false, true, false);
        var volume = new WyckoffVolumeProfile.VolumeSnapshot(numOf(600), numOf(0.6), false, true);

        EnumSet<WyckoffEvent> events = detector.detect(4, structure, volume,
                new WyckoffPhase(WyckoffCycleType.DISTRIBUTION, WyckoffPhaseType.PHASE_C, 0.7, -1));

        assertThat(events).contains(WyckoffEvent.UPTHRUST_AFTER_DISTRIBUTION);
        assertThat(events).contains(WyckoffEvent.RANGE_BREAKOUT);
        assertThat(events).contains(WyckoffEvent.UPTHRUST);
    }

    @Test
    public void shouldOnlyFlagClimaxesWhenCompatibleWithPriorCycle() {
        var detector = new WyckoffEventDetector(series, numOf(0.05));
        var structure = new WyckoffStructureTracker.StructureSnapshot(numOf(10.0), numOf(11.5), 2, 4, numOf(10.05),
                true, false, false);
        var volume = new WyckoffVolumeProfile.VolumeSnapshot(numOf(600), numOf(2.0), true, false);

        EnumSet<WyckoffEvent> distributionEvents = detector.detect(3, structure, volume,
                new WyckoffPhase(WyckoffCycleType.DISTRIBUTION, WyckoffPhaseType.PHASE_B, 0.6, -1));
        assertThat(distributionEvents).doesNotContain(WyckoffEvent.SELLING_CLIMAX);

        EnumSet<WyckoffEvent> accumulationEvents = detector.detect(3, structure, volume,
                new WyckoffPhase(WyckoffCycleType.ACCUMULATION, WyckoffPhaseType.PHASE_B, 0.6, -1));
        assertThat(accumulationEvents).contains(WyckoffEvent.SELLING_CLIMAX);
    }

    @Test
    public void shouldDetectOnlyNewExtremesBeforeRangeForms() {
        var detector = new WyckoffEventDetector(series, numOf(0.05));
        var structure = new WyckoffStructureTracker.StructureSnapshot(NaN, NaN, -1, -1, NaN, false, false, false);
        var volume = new WyckoffVolumeProfile.VolumeSnapshot(numOf(1000), numOf(2.0), true, false);

        EnumSet<WyckoffEvent> firstBarEvents = detector.detect(0, structure, volume, WyckoffPhase.UNKNOWN);
        assertThat(firstBarEvents).contains(WyckoffEvent.BUYING_CLIMAX);
        assertThat(firstBarEvents).contains(WyckoffEvent.SELLING_CLIMAX);

        EnumSet<WyckoffEvent> secondBarEvents = detector.detect(1, structure, volume, WyckoffPhase.UNKNOWN);
        assertThat(secondBarEvents).containsExactly(WyckoffEvent.SELLING_CLIMAX);
    }

    private void addBar(BarSeries target, double open, double high, double low, double close) {
        target.barBuilder().openPrice(open).highPrice(high).lowPrice(low).closePrice(close).volume(1000).add();
    }
}

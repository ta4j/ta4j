/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.wyckoff;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class WyckoffPhaseIndicatorTest extends AbstractIndicatorTest<BarSeries, Num> {

    private BarSeries accumulationSeries;
    private BarSeries distributionSeries;

    public WyckoffPhaseIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        accumulationSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBar(accumulationSeries, 101, 102, 100, 101, 800);
        addBar(accumulationSeries, 103, 104, 101, 103, 900);
        addBar(accumulationSeries, 100, 101, 99, 100, 900);
        addBar(accumulationSeries, 82, 83, 79, 80, 5000);
        addBar(accumulationSeries, 92, 93, 85, 91, 1500);
        addBar(accumulationSeries, 84, 85, 80, 82, 500);
        addBar(accumulationSeries, 95, 96, 90, 93, 1500);
        addBar(accumulationSeries, 108, 111, 105, 110, 2600);
        addBar(accumulationSeries, 112, 115, 109, 114, 1800);

        distributionSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBar(distributionSeries, 80, 83, 79, 82, 800);
        addBar(distributionSeries, 82, 85, 81, 84, 900);
        addBar(distributionSeries, 81, 83, 80, 81, 850);
        addBar(distributionSeries, 86, 88, 85, 87, 1100);
        addBar(distributionSeries, 93, 96, 92, 95, 3200);
        addBar(distributionSeries, 94, 95, 93, 95.5, 1200);
        addBar(distributionSeries, 94, 95, 93, 94.8, 500);
        addBar(distributionSeries, 89, 90, 78, 79, 2800);
        addBar(distributionSeries, 82, 83, 80, 81, 1500);
    }

    @Test
    public void shouldProgressThroughAccumulationLifecycle() {
        var indicator = WyckoffPhaseIndicator.builder(accumulationSeries)
                .withSwingConfiguration(1, 1, 0)
                .withVolumeWindows(1, 4)
                .withTolerances(numOf(0.02), numOf(0.05))
                .withVolumeThresholds(numOf(1.4), numOf(0.6))
                .build();

        var phaseA = indicator.getValue(3);
        assertThat(phaseA.cycleType()).isEqualTo(WyckoffCycleType.ACCUMULATION);
        assertThat(phaseA.phaseType()).isEqualTo(WyckoffPhaseType.PHASE_A);
        assertThat(phaseA.confidence()).isGreaterThanOrEqualTo(0.4);
        assertThat(phaseA.latestEventIndex()).isEqualTo(3);

        var phaseB = indicator.getValue(4);
        assertThat(phaseB.phaseType()).isEqualTo(WyckoffPhaseType.PHASE_B);
        assertThat(phaseB.confidence()).isGreaterThanOrEqualTo(0.55);

        var phaseC = indicator.getValue(5);
        assertThat(phaseC.phaseType()).isEqualTo(WyckoffPhaseType.PHASE_C);
        assertThat(phaseC.confidence()).isGreaterThan(0.69);
        assertThat(indicator.getTradingRangeLow(5)).isEqualByComparingTo(numOf(79));
        assertThat(indicator.getTradingRangeHigh(5)).isEqualByComparingTo(numOf(104));

        var phaseE = indicator.getValue(7);
        assertThat(phaseE.phaseType()).isEqualTo(WyckoffPhaseType.PHASE_E);
        assertThat(phaseE.confidence()).isGreaterThanOrEqualTo(0.95);
        assertThat(indicator.getLastPhaseTransitionIndex(7)).isEqualTo(7);
    }

    @Test
    public void shouldProgressThroughDistributionLifecycle() {
        var indicator = WyckoffPhaseIndicator.builder(distributionSeries)
                .withSwingConfiguration(1, 1, 0)
                .withVolumeWindows(1, 4)
                .withTolerances(numOf(0.02), numOf(0.05))
                .withVolumeThresholds(numOf(1.4), numOf(0.6))
                .build();

        var phaseA = indicator.getValue(4);
        assertThat(phaseA.cycleType()).isEqualTo(WyckoffCycleType.DISTRIBUTION);
        assertThat(phaseA.phaseType()).isEqualTo(WyckoffPhaseType.PHASE_A);
        assertThat(phaseA.latestEventIndex()).isEqualTo(4);

        var phaseB = indicator.getValue(5);
        assertThat(phaseB.phaseType()).isEqualTo(WyckoffPhaseType.PHASE_B);
        assertThat(phaseB.confidence()).isGreaterThanOrEqualTo(0.55);

        var phaseD = indicator.getValue(6);
        assertThat(phaseD.phaseType()).isEqualTo(WyckoffPhaseType.PHASE_D);
        assertThat(phaseD.confidence()).isGreaterThanOrEqualTo(0.85);

        var phaseE = indicator.getValue(7);
        assertThat(phaseE.phaseType()).isEqualTo(WyckoffPhaseType.PHASE_E);
        assertThat(phaseE.confidence()).isGreaterThanOrEqualTo(0.95);
        assertThat(indicator.getLastPhaseTransitionIndex(7)).isEqualTo(7);
    }

    @Test
    public void shouldExposeUnstableBarCountFromConfiguration() {
        var indicator = WyckoffPhaseIndicator.builder(accumulationSeries)
                .withSwingConfiguration(2, 3, 0)
                .withVolumeWindows(3, 12)
                .withTolerances(numOf(0.02), numOf(0.05))
                .withVolumeThresholds(numOf(1.4), numOf(0.6))
                .build();

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(11);
    }

    @Test
    public void shouldReturnUnknownWhenNoEventsDetected() {
        var quietSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBar(quietSeries, 10, 10.2, 9.9, 10.1, 100);
        addBar(quietSeries, 10.3, 10.4, 10.0, 10.2, 120);

        var indicator = WyckoffPhaseIndicator.builder(quietSeries)
                .withSwingConfiguration(3, 3, 0)
                .withVolumeWindows(2, 4)
                .withTolerances(numOf(0.02), numOf(0.05))
                .withVolumeThresholds(numOf(10.0), numOf(0.1))
                .build();

        var phase = indicator.getValue(quietSeries.getEndIndex());
        assertThat(phase).isEqualTo(WyckoffPhase.UNKNOWN);
        assertThat(indicator.getLastPhaseTransitionIndex(quietSeries.getEndIndex())).isEqualTo(-1);
    }

    @Test
    public void shouldRoundTripSerializeAndDeserialize() {
        var indicator = WyckoffPhaseIndicator.builder(accumulationSeries)
                .withSwingConfiguration(1, 1, 0)
                .withVolumeWindows(1, 2)
                .withTolerances(numOf(0.02), numOf(0.05))
                .withVolumeThresholds(numOf(1.4), numOf(0.6))
                .build();

        int index = accumulationSeries.getBeginIndex() + 3;
        WyckoffPhase expected = indicator.getValue(index);

        String json = indicator.toJson();
        Indicator<?> restored = Indicator.fromJson(accumulationSeries, json);

        assertThat(restored).isInstanceOf(WyckoffPhaseIndicator.class);
        var restoredIndicator = (WyckoffPhaseIndicator) restored;
        assertThat(restoredIndicator.toDescriptor()).isEqualTo(indicator.toDescriptor());
        assertThat(restoredIndicator.getValue(index)).isEqualTo(expected);
        assertThat(restoredIndicator.getLastPhaseTransitionIndex(index))
                .isEqualTo(indicator.getLastPhaseTransitionIndex(index));
    }

    private void addBar(BarSeries series, double open, double high, double low, double close, double volume) {
        series.barBuilder().openPrice(open).highPrice(high).lowPrice(low).closePrice(close).volume(volume).add();
    }
}

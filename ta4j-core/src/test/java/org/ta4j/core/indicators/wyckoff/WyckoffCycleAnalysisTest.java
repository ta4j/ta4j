/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.wyckoff;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class WyckoffCycleAnalysisTest extends AbstractIndicatorTest<BarSeries, Num> {

    private BarSeries series;

    public WyckoffCycleAnalysisTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBar(series, 101, 102, 100, 101, 800);
        addBar(series, 103, 104, 101, 103, 900);
        addBar(series, 100, 101, 99, 100, 900);
        addBar(series, 82, 83, 79, 80, 5000);
        addBar(series, 92, 93, 85, 91, 1500);
        addBar(series, 84, 85, 80, 82, 500);
        addBar(series, 95, 96, 90, 93, 1500);
        addBar(series, 108, 111, 105, 110, 2600);
        addBar(series, 112, 115, 109, 114, 1800);
    }

    @Test
    public void shouldRunSingleDegreeAnalysisAndReturnTransitions() {
        WyckoffCycleAnalysis analysis = WyckoffCycleAnalysis.builder()
                .withSwingConfiguration(1, 1, 0)
                .withVolumeWindows(1, 4)
                .withTolerances(0.02, 0.05)
                .withVolumeThresholds(1.4, 0.6)
                .build();

        WyckoffCycleAnalysisResult result = analysis.analyze(series);

        assertThat(result.baseDegreeOffset()).isEqualTo(0);
        assertThat(result.baseAnalysis()).isPresent();

        WyckoffCycleAnalysisResult.DegreeAnalysis base = result.baseAnalysis().get();
        assertThat(base.degreeOffset()).isEqualTo(0);
        assertThat(base.configuration().volumeLongWindow()).isEqualTo(4);

        WyckoffCycleAnalysisResult.CycleSnapshot snapshot = base.cycleSnapshot();
        assertThat(snapshot.unstableBars()).isEqualTo(3);
        assertThat(snapshot.startIndex()).isEqualTo(series.getBeginIndex() + snapshot.unstableBars());
        assertThat(snapshot.endIndex()).isEqualTo(series.getEndIndex());
        assertThat(snapshot.finalPhase().cycleType()).isEqualTo(WyckoffCycleType.ACCUMULATION);

        assertThat(snapshot.transitions()).extracting(WyckoffCycleAnalysisResult.PhaseTransition::index)
                .containsExactly(3, 4, 5, 7);
        assertThat(snapshot.transitions().get(0).phase().phaseType()).isEqualTo(WyckoffPhaseType.PHASE_A);
    }

    private void addBar(BarSeries target, double open, double high, double low, double close, double volume) {
        target.barBuilder().openPrice(open).highPrice(high).lowPrice(low).closePrice(close).volume(volume).add();
    }
}

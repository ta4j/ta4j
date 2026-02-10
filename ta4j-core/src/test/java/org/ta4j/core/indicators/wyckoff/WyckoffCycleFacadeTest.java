/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.wyckoff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class WyckoffCycleFacadeTest extends AbstractIndicatorTest<BarSeries, Num> {

    private BarSeries series;

    public WyckoffCycleFacadeTest(NumFactory numFactory) {
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
    public void shouldExposePhaseAndTradingRangeInformation() {
        WyckoffCycleFacade facade = WyckoffCycleFacade.builder(series)
                .withSwingConfiguration(1, 1, 0)
                .withVolumeWindows(1, 4)
                .withTolerances(numOf(0.02), numOf(0.05))
                .withVolumeThresholds(numOf(1.4), numOf(0.6))
                .build();

        WyckoffPhase phaseA = facade.phase().getValue(3);
        assertThat(phaseA.cycleType()).isEqualTo(WyckoffCycleType.ACCUMULATION);
        assertThat(phaseA.phaseType()).isEqualTo(WyckoffPhaseType.PHASE_A);

        WyckoffPhase phaseC = facade.phase().getValue(5);
        assertThat(phaseC.phaseType()).isEqualTo(WyckoffPhaseType.PHASE_C);
        assertThat(facade.tradingRangeLow(5)).isEqualByComparingTo(numOf(79));
        assertThat(facade.tradingRangeHigh(5)).isEqualByComparingTo(numOf(104));

        WyckoffPhase phaseE = facade.phase().getValue(7);
        assertThat(phaseE.phaseType()).isEqualTo(WyckoffPhaseType.PHASE_E);
        assertThat(facade.lastPhaseTransitionIndex(7)).isEqualTo(7);
    }

    @Test
    public void shouldRejectInvalidSwingConfiguration() {
        assertThrows(IllegalArgumentException.class,
                () -> WyckoffCycleFacade.builder(series).withSwingConfiguration(0, 1, 0));
    }

    private void addBar(BarSeries target, double open, double high, double low, double close, double volume) {
        target.barBuilder().openPrice(open).highPrice(high).lowPrice(low).closePrice(close).volume(volume).add();
    }
}

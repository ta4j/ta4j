/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.wyckoff;

import java.util.Locale;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.wyckoff.WyckoffCycleAnalysis;
import org.ta4j.core.indicators.wyckoff.WyckoffCycleFacade;
import org.ta4j.core.indicators.wyckoff.WyckoffCycleType;
import org.ta4j.core.indicators.wyckoff.WyckoffPhase;
import ta4jexamples.datasources.CsvFileBarSeriesDataSource;

/**
 * Demonstrates Wyckoff cycle indicator-suite and one-shot analysis entry
 * points.
 */
public final class WyckoffCycleIndicatorSuiteDemo {

    private static final Logger LOG = LogManager.getLogger(WyckoffCycleIndicatorSuiteDemo.class);

    /**
     * Creates a new WyckoffCycleIndicatorSuiteDemo instance.
     */
    private WyckoffCycleIndicatorSuiteDemo() {
    }

    /**
     * Executes the demo entry point.
     */
    public static void main(String[] args) {
        BarSeries series = CsvFileBarSeriesDataSource.loadSeriesFromFile();

        var numFactory = series.numFactory();
        WyckoffCycleFacade facade = WyckoffCycleFacade.builder(series)
                .withSwingConfiguration(3, 3, 1)
                .withVolumeWindows(5, 20)
                .withTolerances(numFactory.numOf(0.02), numFactory.numOf(0.05))
                .withVolumeThresholds(numFactory.numOf(1.5), numFactory.numOf(0.7))
                .build();

        int begin = Math.max(series.getBeginIndex() + facade.unstableBars(), series.getBeginIndex());
        for (int i = begin; i <= series.getEndIndex(); i++) {
            WyckoffPhase phase = facade.phase(i);
            if (phase.cycleType() == WyckoffCycleType.UNKNOWN) {
                continue;
            }
            if (facade.lastPhaseTransitionIndex(i) != i) {
                continue;
            }
            var bar = series.getBar(i);
            var low = facade.tradingRangeLow(i);
            var high = facade.tradingRangeHigh(i);
            LOG.info("{} -> {} {} (confidence={}, range=[{}, {}])", bar.getEndTime(), phase.cycleType(),
                    phase.phaseType(), String.format(Locale.US, "%.2f", phase.confidence()), low, high);
        }

        var analysis = WyckoffCycleAnalysis.builder()
                .withSwingConfiguration(3, 3, 1)
                .withVolumeWindows(5, 20)
                .withTolerances(0.02, 0.05)
                .withVolumeThresholds(1.5, 0.7)
                .build();

        var result = analysis.analyze(series);
        result.baseAnalysis()
                .ifPresent(base -> LOG.info("One-shot analysis transitions: {}",
                        base.cycleSnapshot().transitions().size()));
    }
}

/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.wyckoff;

import java.util.Locale;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.wyckoff.WyckoffCycleType;
import org.ta4j.core.indicators.wyckoff.WyckoffPhase;
import org.ta4j.core.indicators.wyckoff.WyckoffPhaseIndicator;
import ta4jexamples.datasources.CsvFileBarSeriesDataSource;

/**
 * Demonstrates how to infer Wyckoff phases from a bar series.
 */
public final class WyckoffCycleExample {

    private static final Logger LOG = LogManager.getLogger(WyckoffCycleExample.class);

    private WyckoffCycleExample() {
    }

    public static void main(String[] args) {
        BarSeries series = CsvFileBarSeriesDataSource.loadSeriesFromFile();

        var numFactory = series.numFactory();
        WyckoffPhaseIndicator indicator = WyckoffPhaseIndicator.builder(series)
                .withSwingConfiguration(3, 3, 1)
                .withVolumeWindows(5, 20)
                .withTolerances(numFactory.numOf(0.02), numFactory.numOf(0.05))
                .withVolumeThresholds(numFactory.numOf(1.5), numFactory.numOf(0.7))
                .build();

        int begin = Math.max(series.getBeginIndex() + indicator.getCountOfUnstableBars(), series.getBeginIndex());
        for (int i = begin; i <= series.getEndIndex(); i++) {
            WyckoffPhase phase = indicator.getValue(i);
            if (phase.cycleType() == WyckoffCycleType.UNKNOWN) {
                continue;
            }
            if (indicator.getLastPhaseTransitionIndex(i) != i) {
                continue;
            }
            var bar = series.getBar(i);
            var low = indicator.getTradingRangeLow(i);
            var high = indicator.getTradingRangeHigh(i);
            LOG.info("{} -> {} {} (confidence={}, range=[{}, {}])", bar.getEndTime(), phase.cycleType(),
                    phase.phaseType(), String.format(Locale.US, "%.2f", phase.confidence()), low, high);
        }
    }
}

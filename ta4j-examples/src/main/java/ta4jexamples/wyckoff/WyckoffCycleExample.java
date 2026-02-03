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
package ta4jexamples.wyckoff;

import java.util.Locale;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.wyckoff.WyckoffCycleType;
import org.ta4j.core.indicators.wyckoff.WyckoffPhase;
import org.ta4j.core.indicators.wyckoff.WyckoffPhaseIndicator;
import ta4jexamples.loaders.CsvBarsLoader;

/**
 * Demonstrates how to infer Wyckoff phases from a bar series.
 */
public final class WyckoffCycleExample {

    private WyckoffCycleExample() {
    }

    public static void main(String[] args) {
        BarSeries series = CsvBarsLoader.loadAppleIncSeries();

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
            System.out.printf(Locale.US, "%s -> %s %s (confidence=%.2f, range=[%s, %s])%n", bar.getEndTime(),
                    phase.cycleType(), phase.phaseType(), phase.confidence(), low, high);
        }
    }
}

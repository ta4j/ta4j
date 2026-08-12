/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.statistics.EventSynchronizationIndicator;
import org.ta4j.core.indicators.statistics.EventSynchronizationIndicator.Result;
import org.ta4j.core.indicators.statistics.EventSynchronizationIndicator.Result.Match;
import org.ta4j.core.indicators.NetMomentumIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.indicators.helpers.CrossIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.indicators.zigzag.ZigZagPivotHighIndicator;
import org.ta4j.core.indicators.zigzag.ZigZagPivotLowIndicator;
import org.ta4j.core.indicators.zigzag.ZigZagStateIndicator;

import ta4jexamples.datasources.JsonFileBarSeriesDataSource;

/**
 * Demonstrates scoring Net Momentum zero crossings against causal ZigZag
 * swing-confirmation events with {@link EventSynchronizationIndicator}.
 *
 * <p>
 * The fixture is a committed, ossified Coinbase BTC daily dataset loaded from
 * the examples classpath, so the demo is fully reproducible and needs no
 * network access. Two workflows are scored:
 * </p>
 * <ul>
 * <li>swing highs: the momentum battery crossing <em>below</em> zero is the
 * predicted event; {@link ZigZagPivotHighIndicator} turning {@code true} is the
 * reference event</li>
 * <li>swing lows: the momentum battery crossing <em>above</em> zero is the
 * predicted event; {@link ZigZagPivotLowIndicator} turning {@code true} is the
 * reference event</li>
 * </ul>
 *
 * <p>
 * Each workflow builds a rolling indicator whose {@code getValue(index)} is the
 * F1 score of the closed trailing window {@code [index - barCount + 1, index]};
 * the demo evaluates the terminal window covering the whole stable history and
 * prints the full match diagnostics through {@code getResult(index)}. Because
 * the crossing indicators report 6 unstable bars, the terminal window starts at
 * the crossing boundary instead of 0; windows that include unavailable history
 * resolve to {@code NaN} rather than silently shrinking.
 *
 * <p>
 * <strong>Confirmation-time semantics:</strong> the ZigZag Boolean indicators
 * are {@code true} at the bar where a prior pivot becomes confirmed — the first
 * bar at which the reversal is causally known. The historical pivot bar may be
 * several bars earlier; the F1 calculation and all matches operate on the
 * confirmation indexes. The example prints the historical pivot index only as a
 * diagnostic; projecting a confirmation back to its pivot index is look-ahead
 * information and must never enter a fitness calculation.
 *
 * @see EventSynchronizationIndicator
 */
final class EventSynchronizationExample {

    private static final Logger LOG = LogManager.getLogger(EventSynchronizationExample.class);

    /** Committed daily BTC dataset from the examples classpath. */
    private static final String DATASET_RESOURCE = "Coinbase-BTC-USD-PT1D-20230616_20231011.json";

    private EventSynchronizationExample() {
    }

    private static BarSeries loadDataset() {
        BarSeries series = JsonFileBarSeriesDataSource.DEFAULT_INSTANCE.loadSeries(DATASET_RESOURCE);
        if (series == null || series.isEmpty()) {
            throw new IllegalStateException("Required dataset '" + DATASET_RESOURCE + "' is missing or empty");
        }
        return series;
    }

    /**
     * Scores of the two demo workflows.
     *
     * @param swingHighs swing-high workflow result
     * @param swingLows  swing-low workflow result
     */
    record DemoResult(Result swingHighs, Result swingLows) {
    }

    /**
     * Runs the demo and returns the two workflow results.
     *
     * @return the swing-high and swing-low terminal-window results
     */
    static DemoResult run() {
        BarSeries series = loadDataset();
        int lastIndex = series.getEndIndex();

        ClosePriceIndicator close = new ClosePriceIndicator(series);
        NetMomentumIndicator momentum = new NetMomentumIndicator(
                NumericIndicator.of(close).minus(new PreviousValueIndicator(close, 1)), 5, 0);
        ZigZagStateIndicator zigZagState = new ZigZagStateIndicator(close, 60);
        ZigZagPivotHighIndicator swingHighConfirmation = new ZigZagPivotHighIndicator(zigZagState);
        ZigZagPivotLowIndicator swingLowConfirmation = new ZigZagPivotLowIndicator(zigZagState);

        // CrossIndicator is a Boolean indicator whose getCountOfUnstableBars()
        // is max(child unstable bars) + 1, so the crossing's own warm-up
        // boundary is honored instead of borrowing the momentum battery's.
        Indicator<Boolean> belowZeroCrosses = new CrossIndicator(momentum,
                new ConstantIndicator<>(series, series.numFactory().zero()));
        Indicator<Boolean> aboveZeroCrosses = new CrossIndicator(
                new ConstantIndicator<>(series, series.numFactory().zero()), momentum);

        // The terminal window is the closed trailing range ending at the last
        // bar and starting at the crossing boundary: windows that include the
        // unstable prefix would be NaN, never silently truncated.
        int stableStart = Math.max(series.getBeginIndex(),
                Math.max(belowZeroCrosses.getCountOfUnstableBars(), swingHighConfirmation.getCountOfUnstableBars()));
        int barCount = lastIndex - stableStart + 1;

        EventSynchronizationIndicator swingHighs = new EventSynchronizationIndicator(belowZeroCrosses,
                swingHighConfirmation, barCount, 12, 12);
        EventSynchronizationIndicator swingLows = new EventSynchronizationIndicator(aboveZeroCrosses,
                swingLowConfirmation, barCount, 12, 12);
        Result highResult = swingHighs.getResult(lastIndex);
        Result lowResult = swingLows.getResult(lastIndex);

        LOG.info("Swing highs: predicted={} reference={} matched={} precision={} recall={} F1={} meanOffset={}",
                highResult.predictedCount(), highResult.referenceCount(), highResult.matchedCount(),
                highResult.precision(), highResult.recall(), highResult.f1Score(), highResult.meanSignedOffset());
        logMatchDiagnostics(highResult, swingHighConfirmation, swingLowConfirmation, true);

        LOG.info("Swing lows: predicted={} reference={} matched={} precision={} recall={} F1={} meanOffset={}",
                lowResult.predictedCount(), lowResult.referenceCount(), lowResult.matchedCount(), lowResult.precision(),
                lowResult.recall(), lowResult.f1Score(), lowResult.meanSignedOffset());
        logMatchDiagnostics(lowResult, swingHighConfirmation, swingLowConfirmation, false);

        return new DemoResult(highResult, lowResult);
    }

    private static void logMatchDiagnostics(Result result, ZigZagPivotHighIndicator swingHighConfirmation,
            ZigZagPivotLowIndicator swingLowConfirmation, boolean swingHighs) {
        List<String> diagnostics = new ArrayList<>();
        for (Match match : result.matches()) {
            int confirmationIndex = match.referenceIndex();
            int pivotIndex = swingHighs ? swingHighConfirmation.getLatestSwingHighIndex(confirmationIndex)
                    : swingLowConfirmation.getLatestSwingLowIndex(confirmationIndex);
            // The pivot index is a diagnostic only; scoring stays on the
            // confirmation index (the first causally available bar).
            diagnostics.add("predicted@" + match.predictedIndex() + " -> confirmed@" + confirmationIndex + " (pivot@"
                    + pivotIndex + ", offset " + match.offsetBars() + ")");
        }
        LOG.info("Swing {} matches (confirmation indexes; pivot indexes are diagnostics): {}",
                swingHighs ? "high" : "low", diagnostics);
    }

    /**
     * Runs the example.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        run();
    }
}

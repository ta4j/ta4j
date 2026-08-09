/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.analysis.event.EventMatch;
import org.ta4j.core.analysis.event.EventSignal;
import org.ta4j.core.analysis.event.EventSignals;
import org.ta4j.core.analysis.event.EventSynchronizationConfig;
import org.ta4j.core.analysis.event.EventSynchronizationConfig.EmptyEventPolicy;
import org.ta4j.core.analysis.event.EventSynchronizationConfig.HistoryPolicy;
import org.ta4j.core.analysis.event.EventSynchronizationEvaluator;
import org.ta4j.core.analysis.event.EventSynchronizationResult;
import org.ta4j.core.indicators.NetMomentumIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.indicators.zigzag.ZigZagPivotHighIndicator;
import org.ta4j.core.indicators.zigzag.ZigZagPivotLowIndicator;
import org.ta4j.core.indicators.zigzag.ZigZagStateIndicator;
import org.ta4j.core.num.DoubleNumFactory;

/**
 * Demonstrates scoring Net Momentum zero crossings against causal ZigZag
 * swing-confirmation events with the event-synchronization API.
 *
 * <p>
 * The fixture is a deterministic synthetic sine series so the demo is fully
 * reproducible and needs no network access. Two workflows are scored:
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
 * <strong>Confirmation-time semantics:</strong> the ZigZag Boolean indicators
 * are {@code true} at the bar where a prior pivot becomes confirmed — the first
 * bar at which the reversal is causally known. The historical pivot bar may be
 * several bars earlier; the F1 calculation and all matches operate on the
 * confirmation indexes. The example prints the historical pivot index only as a
 * diagnostic; projecting a confirmation back to its pivot index is look-ahead
 * information and must never enter a fitness calculation.
 *
 * @see EventSynchronizationEvaluator
 */
public final class EventSynchronizationExample {

    private static final Logger LOG = LogManager.getLogger(EventSynchronizationExample.class);

    private static final int BARS = 200;

    private EventSynchronizationExample() {
    }

    private static BarSeries sineSeries() {
        BarSeries series = new BaseBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance()).build();
        Duration timePeriod = Duration.ofDays(1);
        Instant endTime = Instant.EPOCH;
        for (int i = 0; i < BARS; i++) {
            endTime = endTime.plus(timePeriod);
            double close = 1000.0 + 100.0 * Math.sin(2.0 * Math.PI * i / 20.0);
            series.barBuilder()
                    .timePeriod(timePeriod)
                    .endTime(endTime)
                    .openPrice(close)
                    .closePrice(close)
                    .highPrice(close)
                    .lowPrice(close)
                    .volume(1d)
                    .add();
        }
        return series;
    }

    /**
     * Scores of the two demo workflows.
     *
     * @param swingHighs swing-high workflow result
     * @param swingLows  swing-low workflow result
     */
    record DemoResult(EventSynchronizationResult swingHighs, EventSynchronizationResult swingLows) {
    }

    /**
     * Runs the demo and returns the two workflow results.
     *
     * @return the swing-high and swing-low evaluation results
     */
    static DemoResult run() {
        BarSeries series = sineSeries();

        ClosePriceIndicator close = new ClosePriceIndicator(series);
        NetMomentumIndicator momentum = new NetMomentumIndicator(
                NumericIndicator.of(close).minus(new PreviousValueIndicator(close, 1)), 5, 0);
        ZigZagStateIndicator zigZagState = new ZigZagStateIndicator(close, 60);
        ZigZagPivotHighIndicator swingHighConfirmation = new ZigZagPivotHighIndicator(zigZagState);
        ZigZagPivotLowIndicator swingLowConfirmation = new ZigZagPivotLowIndicator(zigZagState);

        EventSignal belowZeroCrosses = EventSignals.fromRule(series, NumericIndicator.of(momentum).crossedUnder(0),
                momentum.getCountOfUnstableBars());
        EventSignal aboveZeroCrosses = EventSignals.fromRule(series, NumericIndicator.of(momentum).crossedOver(0),
                momentum.getCountOfUnstableBars());

        EventSynchronizationConfig config = new EventSynchronizationConfig(12, 12, HistoryPolicy.CLAMP,
                EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY);
        EventSynchronizationEvaluator evaluator = new EventSynchronizationEvaluator();

        EventSynchronizationResult swingHighs = evaluator.evaluate(belowZeroCrosses,
                EventSignals.fromIndicator(swingHighConfirmation), 0, BARS - 1, config);
        EventSynchronizationResult swingLows = evaluator.evaluate(aboveZeroCrosses,
                EventSignals.fromIndicator(swingLowConfirmation), 0, BARS - 1, config);

        LOG.info("Swing highs: predicted={} reference={} matched={} precision={} recall={} F1={} meanOffset={}",
                swingHighs.predictedCount(), swingHighs.referenceCount(), swingHighs.matchedCount(),
                swingHighs.precision(), swingHighs.recall(), swingHighs.f1Score(), swingHighs.meanSignedOffset());
        logMatchDiagnostics(swingHighs, swingHighConfirmation, swingLowConfirmation, true);

        LOG.info("Swing lows: predicted={} reference={} matched={} precision={} recall={} F1={} meanOffset={}",
                swingLows.predictedCount(), swingLows.referenceCount(), swingLows.matchedCount(), swingLows.precision(),
                swingLows.recall(), swingLows.f1Score(), swingLows.meanSignedOffset());
        logMatchDiagnostics(swingLows, swingHighConfirmation, swingLowConfirmation, false);

        return new DemoResult(swingHighs, swingLows);
    }

    private static void logMatchDiagnostics(EventSynchronizationResult result,
            ZigZagPivotHighIndicator swingHighConfirmation, ZigZagPivotLowIndicator swingLowConfirmation,
            boolean swingHighs) {
        List<String> diagnostics = new ArrayList<>();
        for (EventMatch match : result.matches()) {
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

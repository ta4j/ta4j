/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.analysis.event.EventSynchronizationConfig.EmptyEventPolicy;
import org.ta4j.core.analysis.event.EventSynchronizationConfig.HistoryPolicy;
import org.ta4j.core.indicators.NetMomentumIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.indicators.zigzag.ZigZagPivotHighIndicator;
import org.ta4j.core.indicators.zigzag.ZigZagPivotLowIndicator;
import org.ta4j.core.indicators.zigzag.ZigZagStateIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DoubleNumFactory;

/**
 * Integration coverage for the CF-453 workflow: Net Momentum zero crossings
 * scored against causal ZigZag swing-confirmation events on a deterministic
 * synthetic series, without any {@link org.ta4j.core.TradingRecord}.
 *
 * <p>
 * The ZigZag Boolean indicators mark the confirmation bar (the first bar where
 * the reversal becomes causally known), not the historical pivot bar; all
 * assertions operate on confirmation indexes. On this fixture the momentum
 * battery lags swing-high confirmations by 11 bars and leads swing-low
 * confirmations by 9 bars, so the two workflows exercise the lag and lead sides
 * of the tolerance windows, and the deterministic outcomes are asserted
 * exactly.
 */
@Tag("integration")
class EventSynchronizationIntegrationTest {

    private static final int BARS = 200;

    private BarSeries sineSeries() {
        double[] closes = new double[BARS];
        for (int i = 0; i < BARS; i++) {
            closes[i] = 1000.0 + 100.0 * Math.sin(2.0 * Math.PI * i / 20.0);
        }
        return new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance()).withData(closes).build();
    }

    private NetMomentumIndicator momentum(BarSeries series) {
        // One-bar close change through the momentum battery; deterministic zero
        // crossings 5 bars after each extreme on the synthetic fixture.
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        return new NetMomentumIndicator(NumericIndicator.of(close).minus(new PreviousValueIndicator(close, 1)), 5, 0);
    }

    private EventSignal highPredictions(BarSeries series, NetMomentumIndicator momentum) {
        return EventSignals.fromRule(series, NumericIndicator.of(momentum).crossedUnder(0),
                momentum.getCountOfUnstableBars());
    }

    private EventSignal lowPredictions(BarSeries series, NetMomentumIndicator momentum) {
        return EventSignals.fromRule(series, NumericIndicator.of(momentum).crossedOver(0),
                momentum.getCountOfUnstableBars());
    }

    private static EventSynchronizationConfig config(int maxLeadBars, int maxLagBars) {
        // CLAMP: the momentum signal has a nonzero unstable-bar boundary, and the
        // full-series requests intentionally start below it; silent intersection
        // is CLAMP's documented behavior.
        return new EventSynchronizationConfig(maxLeadBars, maxLagBars, HistoryPolicy.CLAMP,
                EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY);
    }

    @Test
    void momentumZeroCrossesScoreAgainstCausalZigZagConfirmations() {
        BarSeries series = sineSeries();
        NetMomentumIndicator momentum = momentum(series);
        ZigZagStateIndicator state = new ZigZagStateIndicator(new ClosePriceIndicator(series), 60);
        EventSynchronizationEvaluator evaluator = new EventSynchronizationEvaluator();

        // Swing highs: below-zero crossings at 20..180 lag the high confirmations
        // at 9..189 by exactly 11 bars; the confirmation at 189 is unmatched.
        EventSynchronizationResult swingHighs = evaluator.evaluate(highPredictions(series, momentum),
                EventSignals.fromIndicator(new ZigZagPivotHighIndicator(state)), 0, BARS - 1, config(5, 12));
        assertEquals(9, swingHighs.predictedCount());
        assertEquals(10, swingHighs.referenceCount());
        assertEquals(9, swingHighs.matchedCount());
        List<EventMatch> expectedHighs = List.of(new EventMatch(20, 9, -11), new EventMatch(40, 29, -11),
                new EventMatch(60, 49, -11), new EventMatch(80, 69, -11), new EventMatch(100, 89, -11),
                new EventMatch(120, 109, -11), new EventMatch(140, 129, -11), new EventMatch(160, 149, -11),
                new EventMatch(180, 169, -11));
        assertEquals(expectedHighs, swingHighs.matches());
        assertEquals(List.of(189), swingHighs.unmatchedReferenceIndexes());
        assertEquals(0, swingHighs.exactMatchCount());
        assertEquals(1.0, swingHighs.precision().doubleValue(), 1e-12);
        assertEquals(0.9, swingHighs.recall().doubleValue(), 1e-12);
        assertEquals(18.0 / 19.0, swingHighs.f1Score().doubleValue(), 1e-12);

        // Swing lows: above-zero crossings at 10..190 lead the low confirmations
        // at 19..199 by exactly 9 bars; the confirmation at index 3 lies below
        // the momentum unstable boundary and is excluded from extraction.
        EventSynchronizationResult swingLows = evaluator.evaluate(lowPredictions(series, momentum),
                EventSignals.fromIndicator(new ZigZagPivotLowIndicator(state)), 0, BARS - 1, config(12, 5));
        assertEquals(10, swingLows.predictedCount());
        assertEquals(10, swingLows.referenceCount());
        assertEquals(10, swingLows.matchedCount());
        List<EventMatch> expectedLows = List.of(new EventMatch(10, 19, 9), new EventMatch(30, 39, 9),
                new EventMatch(50, 59, 9), new EventMatch(70, 79, 9), new EventMatch(90, 99, 9),
                new EventMatch(110, 119, 9), new EventMatch(130, 139, 9), new EventMatch(150, 159, 9),
                new EventMatch(170, 179, 9), new EventMatch(190, 199, 9));
        assertEquals(expectedLows, swingLows.matches());
        assertTrue(swingLows.unmatchedReferenceIndexes().isEmpty());
        assertEquals(0, swingLows.exactMatchCount());
        assertEquals(1.0, swingLows.precision().doubleValue(), 1e-12);
        assertEquals(1.0, swingLows.recall().doubleValue(), 1e-12);
        assertEquals(1.0, swingLows.f1Score().doubleValue(), 1e-12);

        // Repeated evaluation is structurally equal (deterministic matching).
        EventSynchronizationResult again = evaluator.evaluate(highPredictions(series, momentum),
                EventSignals.fromIndicator(new ZigZagPivotHighIndicator(state)), 0, BARS - 1, config(5, 12));
        assertEquals(swingHighs, again);
    }

    @Test
    void trainingAndValidationWindowsDoNotMatchAcrossBoundary() {
        BarSeries series = sineSeries();
        NetMomentumIndicator momentum = momentum(series);
        ZigZagStateIndicator state = new ZigZagStateIndicator(new ClosePriceIndicator(series), 60);
        EventSynchronizationEvaluator evaluator = new EventSynchronizationEvaluator();
        // The fixture places a below-zero crossing at 80/100 and a high
        // confirmation at 89/109: an 11-bar window would reach across a boundary
        // at 98 if events were allowed to leak between windows.
        int boundary = 98;

        EventSynchronizationResult train = evaluator.evaluate(highPredictions(series, momentum),
                EventSignals.fromIndicator(new ZigZagPivotHighIndicator(state)), 0, boundary, config(5, 12));
        EventSynchronizationResult validation = evaluator.evaluate(highPredictions(series, momentum),
                EventSignals.fromIndicator(new ZigZagPivotHighIndicator(state)), boundary + 1, BARS - 1, config(5, 12));

        // The fixture's deterministic split: the training window holds the
        // (20..80) x (9..69) pairs and the validation window the (120..180) x
        // (109..169) pairs (offset -11; the crossing at 100 has no eligible
        // confirmation inside its 5-bar lead window); nothing crosses the
        // boundary at 98.
        assertEquals(List.of(new EventMatch(20, 9, -11), new EventMatch(40, 29, -11), new EventMatch(60, 49, -11),
                new EventMatch(80, 69, -11)), train.matches());
        assertEquals(List.of(new EventMatch(120, 109, -11), new EventMatch(140, 129, -11),
                new EventMatch(160, 149, -11), new EventMatch(180, 169, -11)), validation.matches());
    }
}

/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * of the tolerance windows.
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
        return new EventSynchronizationConfig(maxLeadBars, maxLagBars, HistoryPolicy.STRICT,
                EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY);
    }

    @Test
    void momentumZeroCrossesScoreAgainstCausalZigZagConfirmations() {
        BarSeries series = sineSeries();
        NetMomentumIndicator momentum = momentum(series);
        ZigZagStateIndicator state = new ZigZagStateIndicator(new ClosePriceIndicator(series), 60);
        EventSynchronizationEvaluator evaluator = new EventSynchronizationEvaluator();

        // Swing highs: below-zero crossings lag the high confirmations by 11 bars.
        EventSynchronizationResult swingHighs = evaluator.evaluate(highPredictions(series, momentum),
                EventSignals.fromIndicator(new ZigZagPivotHighIndicator(state)), 0, BARS - 1, config(5, 12));
        assertTrue(swingHighs.matchedCount() >= 3,
                "expected swing-high confirmations matched, got " + swingHighs.matches());
        for (EventMatch match : swingHighs.matches()) {
            assertTrue(match.offsetBars() >= -12 && match.offsetBars() <= 5,
                    "swing-high offset outside the tolerance window: " + match);
            assertTrue(match.offsetBars() < 0, "swing-high crossings must lag confirmations on this fixture: " + match);
        }
        assertTrue(swingHighs.precision().doubleValue() > 0.0);
        assertTrue(swingHighs.recall().doubleValue() > 0.0);
        assertEquals(swingHighs.predictedCount(),
                swingHighs.matchedCount() + swingHighs.unmatchedPredictedIndexes().size());

        // Swing lows: above-zero crossings lead the low confirmations by 9 bars.
        EventSynchronizationResult swingLows = evaluator.evaluate(lowPredictions(series, momentum),
                EventSignals.fromIndicator(new ZigZagPivotLowIndicator(state)), 0, BARS - 1, config(12, 5));
        assertTrue(swingLows.matchedCount() >= 3,
                "expected swing-low confirmations matched, got " + swingLows.matches());
        for (EventMatch match : swingLows.matches()) {
            assertTrue(match.offsetBars() >= -5 && match.offsetBars() <= 12,
                    "swing-low offset outside the tolerance window: " + match);
            assertTrue(match.offsetBars() > 0, "swing-low crossings must lead confirmations on this fixture: " + match);
        }
        assertTrue(swingLows.precision().doubleValue() > 0.0);
        assertTrue(swingLows.recall().doubleValue() > 0.0);

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

        for (EventMatch match : train.matches()) {
            assertTrue(match.predictedIndex() <= boundary && match.referenceIndex() <= boundary,
                    "train match must stay inside the training window, but " + match);
        }
        for (EventMatch match : validation.matches()) {
            assertTrue(match.predictedIndex() > boundary && match.referenceIndex() > boundary,
                    "validation match must stay inside the validation window, but " + match);
        }
        assertTrue(validation.matchedCount() >= 1,
                "expected a validation-side match near the boundary, got " + validation.matches());
    }
}

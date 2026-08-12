/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
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
 *
 * <p>
 * Each window is the closed trailing range
 * {@code [index - barCount + 1, index]}; the crossing indicators report 6
 * unstable bars, so the one-shot full-series evaluation uses the stable window
 * {@code [6, BARS - 1]} (barCount 194) instead of starting at bar 0.
 */
@Tag("integration")
class EventSynchronizationIntegrationTest {

    private static final int BARS = 200;
    /** The crossing indicators' own unstable boundary: momentum unstable 5 + 1. */
    private static final int STABLE_START = 6;
    private static final int FULL_BAR_COUNT = BARS - STABLE_START;

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

    private Indicator<Boolean> highPredictions(NetMomentumIndicator momentum) {
        return new CrossIndicator(momentum,
                new ConstantIndicator<>(momentum.getBarSeries(), momentum.getBarSeries().numFactory().zero()));
    }

    private Indicator<Boolean> lowPredictions(NetMomentumIndicator momentum) {
        return new CrossIndicator(
                new ConstantIndicator<>(momentum.getBarSeries(), momentum.getBarSeries().numFactory().zero()),
                momentum);
    }

    @Test
    void momentumZeroCrossesScoreAgainstCausalZigZagConfirmations() {
        BarSeries series = sineSeries();
        NetMomentumIndicator momentum = momentum(series);
        ZigZagStateIndicator state = new ZigZagStateIndicator(new ClosePriceIndicator(series), 60);

        // Swing highs: below-zero crossings at 20..180 lag the high confirmations
        // at 9..189 by exactly 11 bars; the confirmation at 189 is unmatched.
        Result swingHighs = new EventSynchronizationIndicator(highPredictions(momentum),
                new ZigZagPivotHighIndicator(state), FULL_BAR_COUNT, 5, 12).getResult(BARS - 1);
        assertEquals(STABLE_START, swingHighs.windowStartIndex());
        assertEquals(9, swingHighs.predictedCount());
        assertEquals(10, swingHighs.referenceCount());
        assertEquals(9, swingHighs.matchedCount());
        List<Match> expectedHighs = List.of(new Match(20, 9), new Match(40, 29), new Match(60, 49), new Match(80, 69),
                new Match(100, 89), new Match(120, 109), new Match(140, 129), new Match(160, 149), new Match(180, 169));
        assertEquals(expectedHighs, swingHighs.matches());
        assertEquals(List.of(189), swingHighs.unmatchedReferenceIndexes());
        assertEquals(0, swingHighs.exactMatchCount());
        assertEquals(1.0, swingHighs.precision().doubleValue(), 1e-12);
        assertEquals(0.9, swingHighs.recall().doubleValue(), 1e-12);
        assertEquals(18.0 / 19.0, swingHighs.f1Score().doubleValue(), 1e-12);

        // Swing lows: above-zero crossings at 10..190 lead the low confirmations
        // at 19..199 by exactly 9 bars; the confirmation at index 3 lies below
        // the momentum unstable boundary and is outside the evaluated window.
        Result swingLows = new EventSynchronizationIndicator(lowPredictions(momentum),
                new ZigZagPivotLowIndicator(state), FULL_BAR_COUNT, 12, 5).getResult(BARS - 1);
        assertEquals(10, swingLows.predictedCount());
        assertEquals(10, swingLows.referenceCount());
        assertEquals(10, swingLows.matchedCount());
        List<Match> expectedLows = List.of(new Match(10, 19), new Match(30, 39), new Match(50, 59), new Match(70, 79),
                new Match(90, 99), new Match(110, 119), new Match(130, 139), new Match(150, 159), new Match(170, 179),
                new Match(190, 199));
        assertEquals(expectedLows, swingLows.matches());
        assertTrue(swingLows.unmatchedReferenceIndexes().isEmpty());
        assertEquals(0, swingLows.exactMatchCount());
        assertEquals(1.0, swingLows.precision().doubleValue(), 1e-12);
        assertEquals(1.0, swingLows.recall().doubleValue(), 1e-12);
        assertEquals(1.0, swingLows.f1Score().doubleValue(), 1e-12);

        // Repeated evaluation is structurally equal (deterministic matching).
        Result again = new EventSynchronizationIndicator(highPredictions(momentum), new ZigZagPivotHighIndicator(state),
                FULL_BAR_COUNT, 5, 12).getResult(BARS - 1);
        assertEquals(swingHighs, again);
    }

    @Test
    void trainingAndValidationWindowsDoNotMatchAcrossBoundary() {
        BarSeries series = sineSeries();
        NetMomentumIndicator momentum = momentum(series);
        ZigZagStateIndicator state = new ZigZagStateIndicator(new ClosePriceIndicator(series), 60);
        // The fixture places a below-zero crossing at 80/100 and a high
        // confirmation at 89/109: an 11-bar window would reach across a boundary
        // at 98 if events were allowed to leak between windows.
        int boundary = 98;

        Result train = new EventSynchronizationIndicator(highPredictions(momentum), new ZigZagPivotHighIndicator(state),
                boundary - STABLE_START + 1, 5, 12).getResult(boundary);
        Result validation = new EventSynchronizationIndicator(highPredictions(momentum),
                new ZigZagPivotHighIndicator(state), BARS - 1 - boundary, 5, 12).getResult(BARS - 1);

        // The fixture's deterministic split: the training window holds the
        // (20..80) x (9..69) pairs and the validation window the (120..180) x
        // (109..169) pairs (offset -11; the crossing at 100 has no eligible
        // confirmation inside its 5-bar lead window); nothing crosses the
        // boundary at 98.
        assertEquals(STABLE_START, train.windowStartIndex());
        assertEquals(boundary, train.windowEndIndex());
        assertEquals(List.of(new Match(20, 9), new Match(40, 29), new Match(60, 49), new Match(80, 69)),
                train.matches());
        assertEquals(boundary + 1, validation.windowStartIndex());
        assertEquals(List.of(new Match(120, 109), new Match(140, 129), new Match(160, 149), new Match(180, 169)),
                validation.matches());
    }
}

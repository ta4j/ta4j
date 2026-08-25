/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.analysis.elliott.swing.SwingDetector;
import org.ta4j.core.analysis.elliott.swing.SwingDetectors;
import org.ta4j.core.analysis.elliott.swing.SwingPivot;
import org.ta4j.core.analysis.elliott.swing.SwingPivotType;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.DoubleNumFactory;

class ConfirmationTrackerTest {

    @Test
    void recordsFirstConfirmationIndexPerPivot() {
        final Map<Integer, List<SwingPivot>> script = new HashMap<>();
        script.put(0, List.of());
        script.put(1, List.of());
        script.put(2, List.of());
        script.put(3, List.of(pivot(1, 20)));
        for (int asOf = 4; asOf <= 6; asOf++) {
            script.put(asOf, List.of(pivot(1, 20), pivot(4, 12)));
        }
        final BarSeries series = seriesWithBars(7);

        final PivotHistory history = new ConfirmationTracker(scripted(script)).observe(series);

        assertThat(history.size()).isEqualTo(2);
        assertThat(history.pivots().get(0).pivotIndex()).isEqualTo(1);
        assertThat(history.pivots().get(0).confirmationIndex()).isEqualTo(3);
        assertThat(history.pivots().get(1).pivotIndex()).isEqualTo(4);
        assertThat(history.pivots().get(1).confirmationIndex()).isEqualTo(4);
    }

    @Test
    void replacesTrailingPivotRevisionsAndMovesConfirmation() {
        final Map<Integer, List<SwingPivot>> script = new HashMap<>();
        script.put(0, List.of());
        script.put(1, List.of());
        script.put(2, List.of(pivot(0, 10)));
        script.put(3, List.of(pivot(0, 10)));
        // Trailing pivot at index 3 appears at 13 then is revised to 11.5.
        script.put(4, List.of(pivot(0, 10), pivot(3, 13)));
        script.put(5, List.of(pivot(0, 10), pivot(3, 11.5)));
        final BarSeries series = seriesWithBars(6);

        final PivotHistory history = new ConfirmationTracker(scripted(script)).observe(series);

        assertThat(history.size()).isEqualTo(2);
        assertThat(history.pivots().get(1).pivotIndex()).isEqualTo(3);
        assertThat(history.pivots().get(1).price()).isEqualTo(DoubleNum.valueOf(11.5));
        assertThat(history.pivots().get(1).confirmationIndex()).isEqualTo(5);
    }

    @Test
    void rejectsRepricedFrozenPredecessorAfterTrailingWithdrawal() {
        final SwingPivot low0 = new SwingPivot(0, DoubleNum.valueOf(10), SwingPivotType.LOW);
        final SwingPivot high1 = new SwingPivot(1, DoubleNum.valueOf(20), SwingPivotType.HIGH);
        final Map<Integer, List<SwingPivot>> script = new HashMap<>();
        script.put(0, List.of(low0));
        script.put(1, List.of(low0, high1));
        // HIGH@1 is retractable, but LOW@0 is already frozen by its successor.
        // A repriced predecessor must not become silently retractable after the
        // trailing withdrawal.
        script.put(2, List.of(new SwingPivot(0, DoubleNum.valueOf(11), SwingPivotType.LOW)));

        final IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> new ConfirmationTracker(scripted(script)).observe(seriesWithBars(3)));

        assertTrue(exception.getMessage().contains("contradicted frozen pivot history"), exception.getMessage());
    }

    @Test
    void replaysAHighestPossibleBarIndexWithoutWrapping() {
        final int lastIndex = Integer.MAX_VALUE;
        final SwingPivot pivot = new SwingPivot(lastIndex, DoubleNum.valueOf(20), SwingPivotType.HIGH);
        final Map<Integer, List<SwingPivot>> script = Map.of(lastIndex, List.of(pivot));

        final PivotHistory history = new ConfirmationTracker(scripted(script))
                .observe(indexedSeries(seriesWithBars(1), lastIndex));

        assertThat(history.pivots()).extracting(ConfirmedPivot::pivotIndex).containsExactly(lastIndex);
    }

    @Test
    void dropsTrailingPivotsThatDisappearBeforeAnySuccessorIsConfirmed() {
        final Map<Integer, List<SwingPivot>> script = new HashMap<>();
        script.put(0, List.of());
        script.put(1, List.of(pivot(0, 10)));
        // Detector withdraws its provisional trailing pivot entirely.
        script.put(2, List.of());
        for (int asOf = 3; asOf <= 4; asOf++) {
            script.put(asOf, List.of(pivot(1, 15)));
        }
        final BarSeries series = seriesWithBars(5);

        final PivotHistory history = new ConfirmationTracker(scripted(script)).observe(series);

        assertThat(history.size()).isEqualTo(1);
        assertThat(history.pivots().get(0).pivotIndex()).isEqualTo(1);
        assertThat(history.pivots().get(0).confirmationIndex()).isEqualTo(3);
    }

    @Test
    void failsClosedWhenAFrozenNonTrailingPivotIsContradicted() {
        final Map<Integer, List<SwingPivot>> script = new HashMap<>();
        for (int asOf = 0; asOf <= 3; asOf++) {
            script.put(asOf, asOf < 3 ? List.of() : List.of(pivot(0, 10), pivot(3, 14)));
        }
        // Non-trailing pivot 0 revised after a successor exists: contradiction.
        script.put(4, List.of(pivot(0, 9.5), pivot(3, 14)));
        final BarSeries series = seriesWithBars(5);

        assertThatThrownBy(() -> new ConfirmationTracker(scripted(script)).observe(series))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("contradicted");
    }

    @Test
    void failsClosedWhenAFrozenNonTrailingPivotIsWithdrawn() {
        final Map<Integer, List<SwingPivot>> script = new HashMap<>();
        for (int asOf = 0; asOf <= 3; asOf++) {
            script.put(asOf, asOf < 3 ? List.of() : List.of(pivot(0, 10), pivot(3, 14)));
        }
        // Interior pivot 0 vanishes entirely once pivot 3 exists: withdrawal,
        // not contradiction. Strict causality must fail closed.
        script.put(4, List.of(pivot(3, 14)));
        final BarSeries series = seriesWithBars(5);

        assertThatThrownBy(() -> new ConfirmationTracker(scripted(script)).observe(series))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("withdrew non-trailing pivot");
    }

    @Test
    void rejectsReadmissionThatWouldNormalizeAwayFrozenDominator() {
        final SwingPivot low0 = new SwingPivot(0, DoubleNum.valueOf(10), SwingPivotType.LOW);
        final SwingPivot low1 = new SwingPivot(1, DoubleNum.valueOf(9), SwingPivotType.LOW);
        final SwingPivot high2 = new SwingPivot(2, DoubleNum.valueOf(20), SwingPivotType.HIGH);
        final Map<Integer, List<SwingPivot>> script = new HashMap<>();
        script.put(0, List.of(low0));
        // LOW@1 collapses LOW@0, then HIGH@2 freezes LOW@1 as the interior pivot.
        script.put(1, List.of(low0, low1));
        script.put(2, List.of(low0, low1, high2));
        // LOW@0 becomes newly dominant while both its former dominator and its
        // successor remain reported. Re-admission must not rewrite LOW@1.
        script.put(3, List.of(new SwingPivot(0, DoubleNum.valueOf(8), SwingPivotType.LOW), low1, high2));

        final IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> new ConfirmationTracker(scripted(script)).observeReplay(seriesWithBars(4)));

        assertTrue(exception.getMessage().contains("normalize away frozen pivot 1"), exception.getMessage());
    }

    @Test
    void repeatedlyReportedDominatedPivotStaysCollapsed() {
        final SwingPivot low1 = new SwingPivot(1, DoubleNum.valueOf(10), SwingPivotType.LOW);
        final SwingPivot high3 = new SwingPivot(3, DoubleNum.valueOf(20), SwingPivotType.HIGH);
        final SwingPivot high5 = new SwingPivot(5, DoubleNum.valueOf(25), SwingPivotType.HIGH);
        final Map<Integer, List<SwingPivot>> script = new HashMap<>();
        script.put(0, List.of());
        script.put(1, List.of(low1));
        script.put(3, List.of(low1, high3));
        script.put(5, List.of(low1, high3, high5));
        script.put(6, List.of(low1, high3, high5));

        final ConfirmationTracker.CausalReplay replay = new ConfirmationTracker(scripted(script))
                .observeReplay(seriesWithBars(7));

        // asOf=5 collapses the dominated HIGH at index 3 into the more extreme
        // HIGH at index 5. The detector keeps reporting it cumulatively; the
        // collapsed pivot must never re-enter the tracked order (which would
        // append an older index behind a newer one and break PivotHistory).
        assertThat(replay.at(4)).extracting(ConfirmedPivot::pivotIndex).containsExactly(1, 3);
        assertThat(replay.at(5)).extracting(ConfirmedPivot::pivotIndex).containsExactly(1, 5);
        assertThat(replay.at(6)).extracting(ConfirmedPivot::pivotIndex).containsExactly(1, 5);
    }

    @Test
    void withdrawingDominatorReconsidersCollapsedPivot() {
        final SwingPivot low1 = new SwingPivot(1, DoubleNum.valueOf(10), SwingPivotType.LOW);
        final SwingPivot high3 = new SwingPivot(3, DoubleNum.valueOf(20), SwingPivotType.HIGH);
        final SwingPivot high5 = new SwingPivot(5, DoubleNum.valueOf(25), SwingPivotType.HIGH);
        final Map<Integer, List<SwingPivot>> script = new HashMap<>();
        script.put(0, List.of());
        script.put(1, List.of(low1));
        script.put(3, List.of(low1, high3));
        script.put(5, List.of(low1, high3, high5));
        script.put(6, List.of(low1, high3, high5));
        // Winner 5 withdrawn while its dominated pivot 3 keeps being reported:
        // the history must reconsider 3 instead of staying empty forever.
        script.put(7, List.of(low1, high3));

        final ConfirmationTracker.CausalReplay replay = new ConfirmationTracker(scripted(script))
                .observeReplay(seriesWithBars(8));

        assertThat(replay.at(6)).extracting(ConfirmedPivot::pivotIndex).containsExactly(1, 5);
        assertThat(replay.at(7)).extracting(ConfirmedPivot::pivotIndex).containsExactly(1, 3);
    }

    @Test
    void revisedDominatorHandsDominanceBackToCollapsedPivot() {
        // HIGH@1=10 collapses behind HIGH@3=12; the detector then revises the
        // still-trailing pivot 3 down to 9 while continuing to report pivot 1.
        // Suppression must be judged against today's prices: pivot 1 is the
        // more extreme high again and re-enters the history.
        final Map<Integer, List<SwingPivot>> script = new HashMap<>();
        script.put(0, List.of());
        script.put(1, List.of(new SwingPivot(1, DoubleNum.valueOf(10), SwingPivotType.HIGH)));
        script.put(3, List.of(new SwingPivot(1, DoubleNum.valueOf(10), SwingPivotType.HIGH),
                new SwingPivot(3, DoubleNum.valueOf(12), SwingPivotType.HIGH)));
        script.put(4, List.of(new SwingPivot(1, DoubleNum.valueOf(10), SwingPivotType.HIGH),
                new SwingPivot(3, DoubleNum.valueOf(9), SwingPivotType.HIGH)));

        final ConfirmationTracker.CausalReplay replay = new ConfirmationTracker(scripted(script))
                .observeReplay(seriesWithBars(5));

        assertThat(replay.at(3)).extracting(ConfirmedPivot::pivotIndex).containsExactly(3);
        assertThat(replay.at(4)).extracting(ConfirmedPivot::pivotIndex).containsExactly(1);
    }

    @Test
    void rejectsMultiPivotWithdrawalPastFrozenBoundary() {
        final SwingPivot low1 = new SwingPivot(1, DoubleNum.valueOf(10), SwingPivotType.LOW);
        final SwingPivot high3 = new SwingPivot(3, DoubleNum.valueOf(20), SwingPivotType.HIGH);
        final SwingPivot low5 = new SwingPivot(5, DoubleNum.valueOf(5), SwingPivotType.LOW);
        final Map<Integer, List<SwingPivot>> script = new HashMap<>();
        script.put(0, List.of());
        script.put(1, List.of(low1));
        script.put(3, List.of(low1, high3));
        script.put(5, List.of(low1, high3, low5));
        // Withdrawing both trailing pivots in one update rewrites history
        // frozen by pivot 5's confirmation; only pivot 5 itself is retractable.
        script.put(6, List.of(low1));

        final ConfirmationTracker tracker = new ConfirmationTracker(scripted(script));

        final IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> tracker.observeReplay(seriesWithBars(7)));
        assertTrue(exception.getMessage().contains("withdrew frozen pivot"), exception.getMessage());
    }

    @Test
    void withdrawingDominatedSameTypePivotKeepsNormalizedHistory() {
        final Map<Integer, List<SwingPivot>> script = new HashMap<>();
        for (int asOf = 0; asOf <= 2; asOf++) {
            script.put(asOf, List.of());
        }
        for (int asOf = 3; asOf <= 4; asOf++) {
            script.put(asOf, List.of(pivot(0, 10), pivot(3, 20)));
        }
        // Two consecutive same-type highs appear at once: snapshot
        // normalization keeps only the more extreme high, so the dominated
        // pivot at index 3 is never visible in any emitted version.
        script.put(5, List.of(pivot(0, 10), pivot(3, 20), pivot(5, 25)));
        // The detector withdraws the dominated interior high while keeping its
        // normalized winner: every emitted view stays [L0, H25] and no frozen
        // history violation may fire.
        script.put(6, List.of(pivot(0, 10), pivot(5, 25)));
        final BarSeries series = seriesWithBars(7);

        final ConfirmationTracker.CausalReplay replay = new ConfirmationTracker(scripted(script)).observeReplay(series);

        assertThat(replay.at(4).stream().map(ConfirmedPivot::pivotIndex).toList()).containsExactly(0, 3);
        assertThat(replay.at(5).stream().map(ConfirmedPivot::pivotIndex).toList()).containsExactly(0, 5);
        assertThat(replay.at(6).stream().map(ConfirmedPivot::pivotIndex).toList()).containsExactly(0, 5);
        assertThat(replay.history().pivots().stream().map(ConfirmedPivot::pivotIndex).toList()).containsExactly(0, 5);
    }

    @Test
    void replayViewsPreserveEarlierCausalState() {
        final Map<Integer, List<SwingPivot>> script = new HashMap<>();
        for (int asOf = 0; asOf <= 4; asOf++) {
            script.put(asOf, asOf < 3 ? List.of() : List.of(pivot(1, 20), pivot(2, 12)));
        }
        // Trailing revision at asOf=4 moves pivot 2's price; earlier views
        // must keep showing the pre-revision state.
        script.put(4, List.of(pivot(1, 20), pivot(2, 13)));
        final BarSeries series = seriesWithBars(5);

        final ConfirmationTracker.CausalReplay replay = new ConfirmationTracker(scripted(script)).observeReplay(series);

        assertThat(replay.at(2)).isEmpty();
        assertThat(replay.at(3).stream().map(ConfirmedPivot::pivotIndex).toList()).containsExactly(1, 2);
        // The final history reflects the last reconciled state (revised price).
        assertThat(replay.history().pivots().get(1).price().doubleValue()).isEqualTo(13.0d);
        assertThat(replay.versionAsOf()).isSortedAccordingTo(Integer::compareTo);
    }

    @Test
    void producesIdenticalHistoriesForIdenticalScripts() {
        final Map<Integer, List<SwingPivot>> script = new HashMap<>();
        for (int asOf = 0; asOf <= 4; asOf++) {
            script.put(asOf, asOf < 3 ? List.of() : List.of(pivot(1, 20), pivot(3, 12)));
        }
        final BarSeries series = seriesWithBars(5);

        final PivotHistory first = new ConfirmationTracker(scripted(script)).observe(series);
        final PivotHistory second = new ConfirmationTracker(scripted(script)).observe(series);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void tracksRealFractalDetectorCausallyOverAZigZagSeries() {
        final double[] closes = { 1, 3, 5, 7, 5, 3, 2, 4, 6, 8, 10, 8, 6 };
        final BarSeries series = ohlcSeriesAround(closes);
        final SwingDetector fractal = SwingDetectors.fractal(2);

        final PivotHistory history = new ConfirmationTracker(fractal).observe(series);

        assertThat(history.size()).isGreaterThanOrEqualTo(3);
        for (int i = 0; i < history.pivots().size(); i++) {
            final ConfirmedPivot pivot = history.pivots().get(i);
            assertThat(pivot.confirmationIndex()).isGreaterThanOrEqualTo(pivot.pivotIndex());
            if (i > 0) {
                assertThat(pivot.pivotIndex()).isGreaterThan(history.pivots().get(i - 1).pivotIndex());
                assertThat(pivot.type()).isNotEqualTo(history.pivots().get(i - 1).type());
            }
        }
        // No state appears before confirmation: one bar before the newest
        // pivot's confirmation, that pivot is still absent from the view.
        final ConfirmedPivot newest = history.pivots().get(history.pivots().size() - 1);
        if (newest.pivotIndex() < newest.confirmationIndex()) {
            assertThat(history.asOf(newest.confirmationIndex() - 1)
                    .stream()
                    .noneMatch(pivot -> pivot.pivotIndex() == newest.pivotIndex())).isTrue();
        }
    }

    private static ScriptedDetector scripted(final Map<Integer, List<SwingPivot>> script) {
        return new ScriptedDetector(script);
    }

    private static SwingPivot pivot(final int index, final double price) {
        final SwingPivotType type = index % 2 == 0 ? SwingPivotType.LOW : SwingPivotType.HIGH;
        return new SwingPivot(index, DoubleNum.valueOf(price), type);
    }

    /**
     * Builds bars whose high/low straddle the close so fractal detection sees the
     * zigzag on both price channels (close-only mocks give every bar an identical
     * zero low).
     */
    private static BarSeries ohlcSeriesAround(final double[] closes) {
        final BarSeries series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance()).build();
        for (int i = 0; i < closes.length; i++) {
            final double close = closes[i];
            series.barBuilder()
                    .endTime(Instant.EPOCH.plus(Duration.ofMinutes(i + 1)))
                    .openPrice(close)
                    .highPrice(close + 0.5)
                    .lowPrice(close - 0.5)
                    .closePrice(close)
                    .add();
        }
        return series;
    }

    private static BarSeries seriesWithBars(final int count) {
        final double[] closes = new double[count];
        for (int i = 0; i < count; i++) {
            closes[i] = 100.0 + i;
        }
        return new MockBarSeriesBuilder().withData(closes).build();
    }

    private static BarSeries indexedSeries(final BarSeries delegate, final int index) {
        return (BarSeries) Proxy.newProxyInstance(BarSeries.class.getClassLoader(), new Class<?>[] { BarSeries.class },
                (proxy, method, args) -> {
                    return switch (method.getName()) {
                    case "getBeginIndex", "getEndIndex" -> index;
                    case "getBar" -> delegate.getBar(0);
                    default -> method.invoke(delegate, args);
                    };
                });
    }

    private static final class ScriptedDetector implements SwingDetector {

        private final Map<Integer, List<SwingPivot>> script;

        private ScriptedDetector(final Map<Integer, List<SwingPivot>> script) {
            this.script = Map.copyOf(script);
        }

        @Override
        public org.ta4j.core.analysis.elliott.swing.SwingDetectorResult detect(final BarSeries series, final int index,
                final ElliottDegree degree) {
            throw new UnsupportedOperationException("scripted detector only supports detectPivots");
        }

        @Override
        public List<SwingPivot> detectPivots(final BarSeries series, final int index) {
            return script.getOrDefault(index,
                    script.getOrDefault(
                            script.keySet().stream().filter(key -> key <= index).max(Integer::compareTo).orElseThrow(),
                            List.of()));
        }
    }

    @Test
    void equalRetainedExtremeSuppressesOlderEqualPivotWithoutChurn() {
        final SwingPivot high1 = new SwingPivot(1, DoubleNum.valueOf(10), SwingPivotType.HIGH);
        final SwingPivot high3 = new SwingPivot(3, DoubleNum.valueOf(10), SwingPivotType.HIGH);
        final Map<Integer, List<SwingPivot>> script = new HashMap<>();
        script.put(0, List.of());
        script.put(1, List.of(high1));
        // Cumulative detector keeps reporting both equal highs; normalization
        // retains the later one, which now also dominates the older equal one.
        for (int bar = 3; bar <= 6; bar++) {
            script.put(bar, List.of(high1, high3));
        }

        final ConfirmationTracker tracker = new ConfirmationTracker(scripted(script));
        final ConfirmationTracker.CausalReplay replay = tracker.observeReplay(seriesWithBars(7));

        assertThat(replay.at(3)).extracting(ConfirmedPivot::pivotIndex).containsExactly(3);
        assertThat(replay.at(6)).extracting(ConfirmedPivot::pivotIndex).containsExactly(3);
        // Exactly two changes (confirmation at 1, collapse at 3); no per-bar churn.
        assertEquals(2, replay.versionAsOf().length);
        // Accessor returns a copy; mutating it cannot corrupt later lookups.
        replay.versionAsOf()[0] = Integer.MAX_VALUE;
        assertThat(replay.at(6)).extracting(ConfirmedPivot::pivotIndex).containsExactly(3);
    }
}

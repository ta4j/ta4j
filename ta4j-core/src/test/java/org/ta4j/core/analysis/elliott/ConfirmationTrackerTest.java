/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}

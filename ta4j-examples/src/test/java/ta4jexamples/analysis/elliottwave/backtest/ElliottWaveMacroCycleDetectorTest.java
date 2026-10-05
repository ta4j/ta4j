/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.elliottwave.backtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottPhase;
import org.ta4j.core.indicators.elliott.ElliottSwing;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NumFactory;

/**
 * Unit coverage for the pure macro-cycle detector conversion heuristics.
 *
 * @since 0.22.4
 */
@Timeout(value = 1, unit = TimeUnit.SECONDS)
class ElliottWaveMacroCycleDetectorTest {

    private static final Instant START = Instant.parse("2024-01-01T00:00:00Z");
    private static final NumFactory NUM_FACTORY = DoubleNumFactory.getInstance();

    @Test
    void toPivotsPreservesSwingEndpointsAndDirectionFlags() {
        final BarSeries series = seriesWithCloses("detector-pivots", 100.0, 150.0, 80.0, 180.0);
        final List<ElliottSwing> swings = List.of(swing(0, 1, 100.0, 150.0), swing(1, 2, 150.0, 80.0),
                swing(2, 3, 80.0, 180.0));

        final List<ElliottWaveMacroCycleDetector.Pivot> pivots = ElliottWaveMacroCycleDetector.toPivots(series, swings);

        assertEquals(4, pivots.size());
        assertPivot(pivots.get(0), 0, 0, 100.0, false);
        assertPivot(pivots.get(1), 1, 1, 150.0, true);
        assertPivot(pivots.get(2), 2, 2, 80.0, false);
        assertPivot(pivots.get(3), 3, 3, 180.0, true);
    }

    @Test
    void toPivotsReturnsEmptyListWhenNoSwingsAreAvailable() {
        final BarSeries series = seriesWithCloses("empty-pivots", 100.0, 101.0);

        assertTrue(ElliottWaveMacroCycleDetector.toPivots(series, List.of()).isEmpty());
    }

    @Test
    void detectMacroDrawdownsKeepsOnlyMaterialMatureDrawdowns() {
        final List<ElliottWaveMacroCycleDetector.Pivot> pivots = List.of(pivot(0, 0, 90.0, false),
                pivot(1, 10, 200.0, true), pivot(2, 160, 130.0, false), pivot(3, 220, 250.0, true),
                pivot(4, 370, 80.0, false), pivot(5, 560, 300.0, true), pivot(6, 620, 120.0, false));

        final List<ElliottWaveMacroCycleDetector.MacroDrawdown> drawdowns = ElliottWaveMacroCycleDetector
                .detectMacroDrawdowns(pivots);

        assertEquals(1, drawdowns.size());
        assertEquals(pivots.get(3), drawdowns.getFirst().top());
        assertEquals(pivots.get(4), drawdowns.getFirst().trough());
        assertEquals(0.68, drawdowns.getFirst().drawdownFraction(), 1.0e-10);
    }

    @Test
    void detectMacroDrawdownsDoesNotSearchPastTheNextHigherHigh() {
        final List<ElliottWaveMacroCycleDetector.Pivot> pivots = List.of(pivot(0, 0, 100.0, false),
                pivot(1, 10, 200.0, true), pivot(2, 20, 250.0, true), pivot(3, 200, 50.0, false));

        final List<ElliottWaveMacroCycleDetector.MacroDrawdown> drawdowns = ElliottWaveMacroCycleDetector
                .detectMacroDrawdowns(pivots);

        assertEquals(1, drawdowns.size());
        assertEquals(pivots.get(2), drawdowns.getFirst().top());
        assertEquals(pivots.get(3), drawdowns.getFirst().trough());
    }

    @Test
    void toAnchorsMarksLastMacroPairAsHoldoutAndAssignsExpectedPhases() {
        final List<ElliottWaveMacroCycleDetector.MacroDrawdown> drawdowns = List
                .of(macroDrawdown(0, 10, 200.0, 160, 80.0), macroDrawdown(2, 220, 300.0, 370, 90.0));

        final List<ElliottWaveAnchorCalibrationHarness.Anchor> anchors = ElliottWaveMacroCycleDetector
                .toAnchors(drawdowns);

        assertEquals(4, anchors.size());
        assertAnchor(anchors.get(0), "inferred-top-1", ElliottWaveAnchorCalibrationHarness.AnchorType.TOP,
                ElliottWaveAnchorRegistry.AnchorPartition.VALIDATION, Set.of(ElliottPhase.WAVE5));
        assertAnchor(anchors.get(1), "inferred-bottom-1", ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM,
                ElliottWaveAnchorRegistry.AnchorPartition.VALIDATION, Set.of(ElliottPhase.CORRECTIVE_C));
        assertAnchor(anchors.get(2), "inferred-top-2", ElliottWaveAnchorCalibrationHarness.AnchorType.TOP,
                ElliottWaveAnchorRegistry.AnchorPartition.HOLDOUT, Set.of(ElliottPhase.WAVE5));
        assertAnchor(anchors.get(3), "inferred-bottom-2", ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM,
                ElliottWaveAnchorRegistry.AnchorPartition.HOLDOUT, Set.of(ElliottPhase.CORRECTIVE_C));
    }

    private static BarSeries seriesWithCloses(final String name, final double... closes) {
        final BarSeries series = new BaseBarSeriesBuilder().withName(name).withNumFactory(NUM_FACTORY).build();
        for (int index = 0; index < closes.length; index++) {
            series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(START.plus(Duration.ofDays(index)))
                    .openPrice(closes[index])
                    .highPrice(closes[index])
                    .lowPrice(closes[index])
                    .closePrice(closes[index])
                    .volume(1.0)
                    .amount(closes[index])
                    .trades(1)
                    .add();
        }
        return series;
    }

    private static ElliottSwing swing(final int fromIndex, final int toIndex, final double fromPrice,
            final double toPrice) {
        return new ElliottSwing(fromIndex, toIndex, NUM_FACTORY.numOf(fromPrice), NUM_FACTORY.numOf(toPrice),
                ElliottDegree.MINOR);
    }

    private static ElliottWaveMacroCycleDetector.Pivot pivot(final int index, final long dayOffset, final double price,
            final boolean high) {
        return new ElliottWaveMacroCycleDetector.Pivot(index, START.plus(Duration.ofDays(dayOffset)), price, high);
    }

    private static ElliottWaveMacroCycleDetector.MacroDrawdown macroDrawdown(final int sequence,
            final long topDayOffset, final double topPrice, final long troughDayOffset, final double troughPrice) {
        final ElliottWaveMacroCycleDetector.Pivot top = pivot(sequence * 2, topDayOffset, topPrice, true);
        final ElliottWaveMacroCycleDetector.Pivot trough = pivot(sequence * 2 + 1, troughDayOffset, troughPrice, false);
        return new ElliottWaveMacroCycleDetector.MacroDrawdown(top, trough, (topPrice - troughPrice) / topPrice);
    }

    private static void assertPivot(final ElliottWaveMacroCycleDetector.Pivot pivot, final int expectedIndex,
            final long expectedDayOffset, final double expectedPrice, final boolean expectedHigh) {
        assertEquals(expectedIndex, pivot.index());
        assertEquals(START.plus(Duration.ofDays(expectedDayOffset)), pivot.at());
        assertEquals(expectedPrice, pivot.price(), 1.0e-10);
        assertEquals(expectedHigh, pivot.high());
    }

    private static void assertAnchor(final ElliottWaveAnchorCalibrationHarness.Anchor anchor, final String expectedId,
            final ElliottWaveAnchorCalibrationHarness.AnchorType expectedType,
            final ElliottWaveAnchorRegistry.AnchorPartition expectedPartition, final Set<ElliottPhase> expectedPhases) {
        assertEquals(expectedId, anchor.id());
        assertEquals(expectedType, anchor.type());
        assertEquals(expectedPartition, anchor.partition());
        assertEquals(expectedPhases, anchor.expectedPhases());
        assertFalse(anchor.provenance().isBlank());
    }
}

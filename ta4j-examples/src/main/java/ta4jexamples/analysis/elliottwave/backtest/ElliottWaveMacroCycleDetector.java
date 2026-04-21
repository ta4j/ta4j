/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.elliottwave.backtest;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.elliott.ElliottAnalysisResult;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottLogicProfile;
import org.ta4j.core.indicators.elliott.ElliottPhase;
import org.ta4j.core.indicators.elliott.ElliottSwing;
import org.ta4j.core.indicators.elliott.ElliottWaveAnalysisRunner;
import org.ta4j.core.indicators.elliott.ElliottWaveAnalysisResult;

/**
 * Infers broad macro-cycle anchors directly from a series-wide Elliott swing
 * set.
 *
 * <p>
 * This detector is the anchor-free path for {@link ElliottWaveMacroCycleDemo}.
 * It runs the same full-history core analysis profile used by the BTC macro
 * demo, then collapses the processed swing pivots into major cycle turns by
 * keeping only all-time-high peaks that are followed by severe drawdowns before
 * the next higher high. The detector keeps the deepest corrective low inside
 * that regime, then rejects short-lived crashes that never mature into broad
 * macro cycles. The resulting top/bottom chain is converted into an
 * {@link ElliottWaveAnchorCalibrationHarness.AnchorRegistry} so the existing
 * historical chart and JSON report flow can be reused unchanged.
 *
 * <p>
 * The heuristic is intentionally simple:
 * <ul>
 * <li>build a full-history orthodox swing map with no curated anchors</li>
 * <li>track each new all-time-high pivot</li>
 * <li>keep only peaks that lead to a material trough before the next higher
 * high</li>
 * </ul>
 *
 * <p>
 * For BTC full-history runs this naturally recovers the committed 2011, 2013,
 * 2017, and 2021 macro tops plus their following corrective lows closely enough
 * to compare against the truth-set registry.
 *
 * @since 0.22.7
 */
final class ElliottWaveMacroCycleDetector {

    private static final String INFERRED_REGISTRY_VERSION = "inferred-macro-cycle-anchors-v1";
    private static final int HOLDOUT_ANCHOR_COUNT = 2;
    private static final double MIN_MACRO_DRAWDOWN_FRACTION = 0.55;
    private static final Duration MIN_MACRO_SPAN = Duration.ofDays(120);
    private static final Duration DEFAULT_TOLERANCE = Duration.ofDays(45);

    private ElliottWaveMacroCycleDetector() {
    }

    /**
     * Infers a macro-cycle anchor registry for the supplied series.
     *
     * @param series series to analyze
     * @return inferred anchor registry
     * @since 0.22.7
     */
    static ElliottWaveAnchorCalibrationHarness.AnchorRegistry inferAnchorRegistry(final BarSeries series) {
        Objects.requireNonNull(series, "series");
        final ElliottWaveAnalysisResult analysis = buildRunner().analyze(series);
        final ElliottAnalysisResult baseAnalysis = analysis.analysisFor(ElliottDegree.MINOR)
                .orElseThrow(() -> new IllegalStateException("Missing base-degree MINOR analysis"))
                .analysis();
        final List<Pivot> pivots = toPivots(series, baseAnalysis.rawSwings());
        final List<MacroDrawdown> macroDrawdowns = detectMacroDrawdowns(pivots);
        if (macroDrawdowns.isEmpty()) {
            throw new IllegalStateException("Unable to infer macro-cycle anchors from processed swings");
        }
        return new ElliottWaveAnchorCalibrationHarness.AnchorRegistry(INFERRED_REGISTRY_VERSION,
                datasetResource(series), inferredProvenance(series), toAnchors(macroDrawdowns));
    }

    private static ElliottWaveAnalysisRunner buildRunner() {
        return ElliottWaveAnalysisRunner.builder()
                .degree(ElliottDegree.MINOR)
                .logicProfile(ElliottLogicProfile.ORTHODOX_CLASSICAL)
                .maxScenarios(ElliottLogicProfile.ORTHODOX_CLASSICAL.maxScenarios())
                .minConfidence(0.0)
                .seriesSelector((inputSeries, ignoredDegree) -> inputSeries)
                .build();
    }

    private static List<Pivot> toPivots(final BarSeries series, final List<ElliottSwing> processedSwings) {
        if (processedSwings == null || processedSwings.isEmpty()) {
            return List.of();
        }
        final List<Pivot> pivots = new ArrayList<>(processedSwings.size() + 1);
        final ElliottSwing firstSwing = processedSwings.getFirst();
        pivots.add(new Pivot(firstSwing.fromIndex(), series.getBar(firstSwing.fromIndex()).getEndTime(),
                firstSwing.fromPrice().doubleValue(), !firstSwing.isRising()));
        for (final ElliottSwing swing : processedSwings) {
            pivots.add(new Pivot(swing.toIndex(), series.getBar(swing.toIndex()).getEndTime(),
                    swing.toPrice().doubleValue(), swing.isRising()));
        }
        return List.copyOf(pivots);
    }

    private static List<MacroDrawdown> detectMacroDrawdowns(final List<Pivot> pivots) {
        final List<Pivot> allTimeHighs = new ArrayList<>();
        double bestHigh = Double.NEGATIVE_INFINITY;
        for (final Pivot pivot : pivots) {
            if (!pivot.high() || pivot.price() <= bestHigh) {
                continue;
            }
            allTimeHighs.add(pivot);
            bestHigh = pivot.price();
        }

        final List<MacroDrawdown> drawdowns = new ArrayList<>();
        for (int index = 0; index < allTimeHighs.size(); index++) {
            final Pivot top = allTimeHighs.get(index);
            final Instant nextHigherHighTime = index + 1 < allTimeHighs.size() ? allTimeHighs.get(index + 1).at()
                    : null;
            Pivot trough = lowestLowAfter(top, pivots, nextHigherHighTime);
            if (trough == null) {
                continue;
            }
            final double drawdownFraction = (top.price() - trough.price()) / top.price();
            final Duration span = Duration.between(top.at(), trough.at());
            if (drawdownFraction >= MIN_MACRO_DRAWDOWN_FRACTION && span.compareTo(MIN_MACRO_SPAN) >= 0) {
                drawdowns.add(new MacroDrawdown(top, trough, drawdownFraction));
            }
        }
        return List.copyOf(drawdowns);
    }

    private static Pivot lowestLowAfter(final Pivot top, final List<Pivot> pivots, final Instant nextHigherHighTime) {
        Pivot trough = null;
        for (final Pivot pivot : pivots) {
            if (pivot.at().isBefore(top.at()) || pivot.at().equals(top.at()) || pivot.high()) {
                continue;
            }
            if (nextHigherHighTime != null && !pivot.at().isBefore(nextHigherHighTime)) {
                break;
            }
            if (trough == null || pivot.price() < trough.price()) {
                trough = pivot;
            }
        }
        return trough;
    }

    private static List<ElliottWaveAnchorCalibrationHarness.Anchor> toAnchors(
            final List<MacroDrawdown> macroDrawdowns) {
        final List<ElliottWaveAnchorCalibrationHarness.Anchor> anchors = new ArrayList<>(macroDrawdowns.size() * 2);
        for (int index = 0; index < macroDrawdowns.size(); index++) {
            final MacroDrawdown drawdown = macroDrawdowns.get(index);
            anchors.add(toAnchor(index, drawdown.top(), ElliottWaveAnchorCalibrationHarness.AnchorType.TOP));
            anchors.add(toAnchor(index, drawdown.trough(), ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM));
        }

        final int validationCutoff = Math.max(0, anchors.size() - HOLDOUT_ANCHOR_COUNT);
        final List<ElliottWaveAnchorCalibrationHarness.Anchor> partitioned = new ArrayList<>(anchors.size());
        for (int index = 0; index < anchors.size(); index++) {
            final ElliottWaveAnchorCalibrationHarness.Anchor anchor = anchors.get(index);
            final ElliottWaveAnchorRegistry.AnchorPartition partition = index < validationCutoff
                    ? ElliottWaveAnchorRegistry.AnchorPartition.VALIDATION
                    : ElliottWaveAnchorRegistry.AnchorPartition.HOLDOUT;
            partitioned.add(new ElliottWaveAnchorCalibrationHarness.Anchor(anchor.id(), anchor.type(), anchor.at(),
                    anchor.toleranceBefore(), anchor.toleranceAfter(), anchor.expectedPhases(), partition,
                    anchor.provenance()));
        }
        return List.copyOf(partitioned);
    }

    private static ElliottWaveAnchorCalibrationHarness.Anchor toAnchor(final int sequence, final Pivot pivot,
            final ElliottWaveAnchorCalibrationHarness.AnchorType type) {
        final Set<ElliottPhase> expectedPhases = type == ElliottWaveAnchorCalibrationHarness.AnchorType.TOP
                ? EnumSet.of(ElliottPhase.WAVE5)
                : EnumSet.of(ElliottPhase.CORRECTIVE_C);
        final String direction = type == ElliottWaveAnchorCalibrationHarness.AnchorType.TOP ? "top" : "bottom";
        return new ElliottWaveAnchorCalibrationHarness.Anchor("inferred-" + direction + "-" + (sequence + 1), type,
                pivot.at(), DEFAULT_TOLERANCE, DEFAULT_TOLERANCE, expectedPhases,
                ElliottWaveAnchorRegistry.AnchorPartition.VALIDATION,
                "Inferred from full-history orthodox Elliott swings and macro drawdown turns.");
    }

    private static String datasetResource(final BarSeries series) {
        final String name = series.getName();
        return name == null || name.isBlank() ? "in-memory-series" : name;
    }

    private static String inferredProvenance(final BarSeries series) {
        return "Inferred from full-history orthodox Elliott swing pivots for " + datasetResource(series)
                + " without a curated anchor registry.";
    }

    private record Pivot(int index, Instant at, double price, boolean high) {

        private Pivot {
            Objects.requireNonNull(at, "at");
            if (index < 0) {
                throw new IllegalArgumentException("index must be >= 0");
            }
            if (!Double.isFinite(price)) {
                throw new IllegalArgumentException("price must be finite");
            }
        }
    }

    private record MacroDrawdown(Pivot top, Pivot trough, double drawdownFraction) {

        private MacroDrawdown {
            Objects.requireNonNull(top, "top");
            Objects.requireNonNull(trough, "trough");
            if (!top.high()) {
                throw new IllegalArgumentException("top must be a high pivot");
            }
            if (trough.high()) {
                throw new IllegalArgumentException("trough must be a low pivot");
            }
            if (trough.index() <= top.index()) {
                throw new IllegalArgumentException("trough must follow top");
            }
            if (!Double.isFinite(drawdownFraction) || drawdownFraction <= 0.0) {
                throw new IllegalArgumentException("drawdownFraction must be positive and finite");
            }
        }
    }
}

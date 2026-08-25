/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.analysis.elliott.swing.SwingDetector;
import org.ta4j.core.analysis.elliott.swing.SwingDetectorResult;
import org.ta4j.core.analysis.elliott.swing.SwingDetectors;
import org.ta4j.core.analysis.elliott.swing.SwingPivot;
import org.ta4j.core.analysis.elliott.swing.SwingPivotType;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;

class StudyRunnerTest {

    private static final long SEED = 5_252_026L;

    @Test
    void calibrationCannotTouchForbiddenDate() {
        final StudyRunner.Partitions invalid = new StudyRunner.Partitions(
                List.of(new StudyRunner.Partition("calibration", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2)),
                        new StudyRunner.Partition("validation", LocalDate.of(2024, 1, 3), LocalDate.of(2024, 1, 4)),
                        new StudyRunner.Partition("holdout", LocalDate.of(2024, 1, 5), LocalDate.of(2024, 1, 6))),
                LocalDate.of(2024, 1, 1));
        final StudyRunner runner = new StudyRunner(StudyRunnerTest::detectorFactory, List.of(TopologyGrammar.MOTIVE_5),
                List.of(), configuration(invalid, 1));

        assertThrows(IllegalStateException.class, () -> runner.evaluate(buildSeries(20), 0, 19));
    }

    @Test
    void evaluatesAtHighestPossibleBarIndexWithoutWrapping() {
        final int lastIndex = Integer.MAX_VALUE;
        final StudyRunner.Configuration configuration = new StudyRunner.Configuration(
                StudyRunner.Partitions.lockedDefault(), "max-index-test", SEED, List.of(2), 1, List.of(), "test",
                List.of());
        final StudyRunner runner = new StudyRunner(StudyRunnerTest::detectorFactory, List.of(TopologyGrammar.MOTIVE_5),
                List.of(), configuration);

        final StudyReport report = runner.evaluate("MAX", indexedSeries(buildSeries(1), lastIndex), lastIndex,
                lastIndex);

        assertTrue(report.h1()
                .modes()
                .get(0)
                .partitions()
                .stream()
                .anyMatch(partition -> partition.evaluationCount() == 1L));
    }

    @Test
    void sameSeedProducesIdenticalReport() {
        final StudyRunner.Configuration configuration = configuration(StudyRunner.Partitions.lockedDefault(), 2);
        final StudyRunner firstRunner = new StudyRunner(StudyRunnerTest::detectorFactory, grammars(), rules(),
                configuration);
        final StudyRunner secondRunner = new StudyRunner(StudyRunnerTest::detectorFactory, grammars(), rules(),
                configuration);

        final String first = firstRunner.evaluate("BTC", buildSeries(24), 0, 23).toJson();
        final String second = secondRunner.evaluate("BTC", buildSeries(24), 0, 23).toJson();

        assertEquals(first, second);
    }

    @Test
    void futureBarsDoNotChangeEarlierEvaluation() {
        final StudyRunner.Configuration configuration = configuration(StudyRunner.Partitions.lockedDefault(), 2);
        final StudyRunner runner = new StudyRunner(StudyRunnerTest::detectorFactory, grammars(), rules(),
                configuration);

        final String prefix = runner.evaluate("BTC", buildSeries(24), 0, 19).toJson();
        final String appended = runner.evaluate("BTC", buildSeries(40), 0, 19).toJson();

        assertEquals(prefix, appended);
    }

    @Test
    void fractalDetectorDoesNotLeakAppendedBars() {
        final StudyRunner.Partitions partitions = StudyRunner.Partitions.lockedDefault();
        final StudyRunner.Configuration configuration = new StudyRunner.Configuration(partitions, "fingerprint", SEED,
                List.of(2), 1,
                List.of(new DetectorRobustnessMatrix.DetectorSpec("fractal", () -> SwingDetectors.fractal(2))),
                "test-fractal", null);
        final StudyRunner runner = new StudyRunner(() -> SwingDetectors.fractal(2), grammars(), rules(), configuration);

        final String prefix = runner.evaluate("BTC", buildSeries(24), 0, 19).toJson();
        final String appended = runner.evaluate("BTC", buildSeries(40), 0, 19).toJson();

        assertEquals(prefix, appended);
    }

    @Test
    void ablationModesContainExactlyTheirSelectedRule() {
        final StudyRunner.Configuration configuration = configuration(StudyRunner.Partitions.lockedDefault(), 1);
        final StudyRunner runner = new StudyRunner(StudyRunnerTest::detectorFactory, grammars(), rules(),
                configuration);
        final StudyReport report = runner.evaluate("BTC", buildSeries(24), 0, 23);

        final StudyReport.ModeReport first = report.ablations()
                .stream()
                .filter(mode -> "+first".equals(mode.mode()))
                .findFirst()
                .orElseThrow();
        final StudyReport.ModeReport second = report.ablations()
                .stream()
                .filter(mode -> "+second".equals(mode.mode()))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of("first"), first.activeRuleIds());
        assertEquals(List.of("second"), second.activeRuleIds());
        assertFalse(first.activeRuleIds().contains("second"));
        assertFalse(second.activeRuleIds().contains("first"));
    }

    @Test
    void syntheticIntegrationProducesAllStudyModesAndSeparatedHypotheses() {
        final StudyRunner.Configuration configuration = configuration(StudyRunner.Partitions.lockedDefault(), 2);
        final StudyRunner runner = new StudyRunner(StudyRunnerTest::detectorFactory, grammars(), rules(),
                configuration);
        final StudyReport report = runner.evaluate("BTC", buildSeries(24), 0, 23);

        // H1 is exactly the preregistered MOTIVE_5 topology claim; additional
        // caller grammars belong to the competing-grammar section. H2 is
        // exactly the ablation ladder (topology-only, one rung per rule,
        // classical-all).
        assertEquals(1, report.h1().modes().size());
        assertEquals(List.of("MOTIVE_5"), report.h1().modes().stream().map(StudyReport.ModeReport::grammar).toList());
        assertEquals(4, report.h2().modes().size());
        assertEquals(List.of("topology-only", "+first", "+second", "classical-all"),
                report.h2().modes().stream().map(StudyReport.ModeReport::mode).toList());
        assertEquals("H1", report.h1().id());
        assertEquals("H2", report.h2().id());
        assertEquals(7, report.competingGrammars().size());
        assertTrue(report.competingGrammars()
                .stream()
                .anyMatch(mode -> "competing-change-point-baseline".equals(mode.mode())));
        assertFalse(report.ablations().isEmpty());
        assertEquals(1, report.robustness().detectors().size());
        // Robustness serialization must carry detector results, not an empty
        // array (regression: detectors were dropped from the JSON payload).
        assertTrue(report.toJson().contains("\"name\":\"synthetic\""));
        assertEquals(List.of("MOTIVE_5", "CYCLE_5_3"),
                report.nulls().stream().map(StudyReport.NullReport::grammar).toList());
        assertEquals(List.of(2, 2), report.nulls().stream().map(StudyReport.NullReport::blockLength).toList());
        assertEquals(2, report.nulls().get(0).members().size());
        assertEquals(report.nulls().get(0).partitions().size(),
                report.nulls().get(0).members().get(0).partitions().size());
        assertTrue(report.toJson().contains("\"members\""));
        assertTrue(report.toJson().contains("protocolFingerprint"));
        assertTrue(report.toJson().contains("evidencePassRate"));
        final StudyReport.PartitionMetrics topologyPartition = report.h1()
                .modes()
                .get(0)
                .partitions()
                .stream()
                .filter(partition -> partition.evaluationCount() > 0)
                .findFirst()
                .orElseThrow();
        assertEquals(0L, topologyPartition.evidenceEvaluationCount());
        assertTrue(Double.isNaN(topologyPartition.evidencePassRate()));
        assertTrue(Double.isNaN(topologyPartition.jointPassRate()));
        assertTrue(report.toJson().contains("\"evidencePassRate\":null"));
        assertTrue(report.toJson().contains("\"jointPassRate\":null"));

        final StudyReport.ModeReport classical = report.ablations()
                .stream()
                .filter(mode -> "classical-all".equals(mode.mode()))
                .findFirst()
                .orElseThrow();
        final StudyReport.PartitionMetrics jointPartition = classical.partitions()
                .stream()
                .filter(partition -> partition.jointEvaluationCount() > 0)
                .findFirst()
                .orElseThrow();
        assertEquals(classical.activeRuleIds().size(),
                jointPartition.evidenceEvaluationCount() / jointPartition.jointEvaluationCount());
        assertTrue(report.toJson().contains("\"jointEvaluationCount\""));
    }

    @Test
    void topologyModesRecordPerBarEvaluationsAndFiniteBounds() {
        final StudyRunner.Configuration configuration = configuration(StudyRunner.Partitions.lockedDefault(), 2);
        final StudyRunner runner = new StudyRunner(StudyRunnerTest::detectorFactory, grammars(), rules(),
                configuration);
        final StudyReport report = runner.evaluate("BTC", buildSeries(24), 0, 23);

        // Regression: the topology recording loop once computed analyses without
        // accumulating them, leaving every H1/H2 partition at zero evaluations
        // while the competing baselines still counted observations.
        final StudyReport.ModeReport motive = report.h1()
                .modes()
                .stream()
                .filter(mode -> "MOTIVE_5".equals(mode.grammar()))
                .findFirst()
                .orElseThrow();
        assertTrue(motive.partitions().stream().anyMatch(p -> p.evaluationCount() > 0));
        assertTrue(report.h2()
                .modes()
                .stream()
                .flatMap(mode -> mode.partitions().stream())
                .anyMatch(p -> p.evaluationCount() > 0));
        assertFalse(report.toJson().contains("Infinity"));
        // Null baselines must cover both preregistered hypothesis grammars.
        assertTrue(report.toJson().contains("\"grammar\":\"MOTIVE_5\""));
        assertTrue(report.toJson().contains("\"grammar\":\"CYCLE_5_3\""));
        assertTrue(report.toJson().contains("\"primaryDetector\":\"synthetic-primary\""));

        final StudyReport.NullReport cycleNulls = report.nulls()
                .stream()
                .filter(nullReport -> "CYCLE_5_3".equals(nullReport.grammar()))
                .findFirst()
                .orElseThrow();
        assertEquals(4, cycleNulls.modes().size());
        assertTrue(cycleNulls.modes().stream().allMatch(mode -> mode.members().size() == 2));
        assertTrue(cycleNulls.modes().stream().anyMatch(mode -> mode.activeRuleIds().size() == rules().size()));
        assertTrue(report.toJson().contains("\"modes\""));
    }

    @Test
    void calibrationNullBaselineIgnoresFutureReturns() {
        final StudyRunner.Partitions partitions = new StudyRunner.Partitions(
                List.of(new StudyRunner.Partition("calibration", LocalDate.of(2018, 1, 1), LocalDate.of(2018, 1, 12)),
                        new StudyRunner.Partition("validation", LocalDate.of(2018, 1, 13), LocalDate.of(2018, 1, 20)),
                        new StudyRunner.Partition("holdout", LocalDate.of(2018, 1, 21), LocalDate.of(2018, 1, 31))),
                LocalDate.of(2024, 1, 1));
        final StudyRunner.Configuration configuration = configuration(partitions, 2);
        final StudyRunner runner = new StudyRunner(StudyRunnerTest::detectorFactory, grammars(), rules(),
                configuration);

        // Identical through the calibration window; radically different after.
        final BarSeries baseSeries = buildSeries(24);
        final BarSeries mutatedTail = buildMutatedSeries(24, 12, 3.0d);

        final StudyReport baseReport = runner.evaluate("BTC", baseSeries, 0, 23);
        final StudyReport mutatedReport = runner.evaluate("BTC", mutatedTail, 0, 23);

        // Regression: null ensembles were once drawn from the full causal
        // window and split by date afterwards, so calibration baselines
        // incorporated validation and holdout returns.
        final StudyReport.NullReport baseNulls = baseReport.nulls()
                .stream()
                .filter(report -> "MOTIVE_5".equals(report.grammar()))
                .findFirst()
                .orElseThrow();
        final StudyReport.NullReport mutatedNulls = mutatedReport.nulls()
                .stream()
                .filter(report -> "MOTIVE_5".equals(report.grammar()))
                .findFirst()
                .orElseThrow();
        final StudyReport.PartitionMetrics baseCalibration = baseNulls.partitions()
                .stream()
                .filter(metrics -> "calibration".equals(metrics.partition()))
                .findFirst()
                .orElseThrow();
        final StudyReport.PartitionMetrics mutatedCalibration = mutatedNulls.partitions()
                .stream()
                .filter(metrics -> "calibration".equals(metrics.partition()))
                .findFirst()
                .orElseThrow();
        assertTrue(baseCalibration.evaluationCount() > 0);
        assertEquals(baseCalibration, mutatedCalibration);
    }

    @Test
    void competingAlternativeGrammarSeparatesFormingFromNoMatch() {
        final StudyRunner.Configuration configuration = configuration(StudyRunner.Partitions.lockedDefault(), 1);
        final StudyRunner fallingRunner = new StudyRunner(StudyRunnerTest::scriptedDetector, grammars(), rules(),
                configuration);
        final StudyReport fallingReport = fallingRunner.evaluate("BTC", buildFallingSeries(24), 0, 23);

        // Regression: a two-pivot suffix satisfies one orientation of the
        // leading leg for every non-flat tail, which made noMatchRate
        // unreachable and inflated forming counts. A falling zigzag now reports
        // no-match against "5+5" because it contradicts any directional leading
        // segment inside the observable window.
        final StudyReport.ModeReport fivePlusFive = fallingReport.competingGrammars()
                .stream()
                .filter(mode -> "competing-5+5".equals(mode.mode()))
                .findFirst()
                .orElseThrow();
        assertEquals(0, fivePlusFive.partitions().get(0).completeCount());
        assertEquals(0, fivePlusFive.partitions().get(0).formingCount());
        assertTrue(fivePlusFive.partitions().get(0).noMatchCount() > 0);

        // A directional run whose junction stays the window extreme forms.
        final StudyRunner trendingRunner = new StudyRunner(StudyRunnerTest::scriptedDetector, grammars(), rules(),
                configuration);
        final StudyReport trendingReport = trendingRunner.evaluate("BTC", buildTrendingSeries(24), 0, 23);
        final StudyReport.ModeReport threePlusThree = trendingReport.competingGrammars()
                .stream()
                .filter(mode -> "competing-3+3".equals(mode.mode()))
                .findFirst()
                .orElseThrow();
        assertEquals(0, threePlusThree.partitions().get(0).completeCount());
        assertTrue(threePlusThree.partitions().get(0).formingCount() > 0);
        assertTrue(threePlusThree.partitions().get(0).labelStabilityJaccard() < 1.0d);
    }

    @Test
    void defaultFingerprintIsAnHonestUnpinnedToken() {
        assertEquals("in-kernel-default-unpinned", StudyRunner.Configuration.lockedDefault().protocolFingerprint());
    }

    @Test
    void robustnessDefaultsMatchTheFrozenProtocolMatrix() {
        assertEquals(List.of("fractal-w3", "fractal-w5", "prominence-default", "slope-change-w5"),
                DetectorRobustnessMatrix.defaults().stream().map(DetectorRobustnessMatrix.DetectorSpec::name).toList());
    }

    @Test
    void competingGrammarsAreSeparatedByJunctionExtremity() {
        // Regression: with odd first segments, 3+3, 5+5 and 7+3 reduced to
        // identical strict alternation over 11 pivots. The junction pivot must
        // now be the window extreme on the leading trend side.

        final List<ConfirmedPivot> fiveFiveShape = alternatingWindow(
                new double[] { 10, 12, 11, 14, 12, 16, 13, 15, 13.5d, 15.5d, 14 });
        assertEquals(1, StudyRunner.AlternativeGrammar.of("5+5").matches(fiveFiveShape).size());
        assertTrue(StudyRunner.AlternativeGrammar.of("7+3").matches(fiveFiveShape).isEmpty());

        final List<ConfirmedPivot> sevenThreeShape = alternatingWindow(
                new double[] { 10, 12, 11, 14, 12, 15, 13, 17, 14, 16, 15 });
        assertEquals(1, StudyRunner.AlternativeGrammar.of("7+3").matches(sevenThreeShape).size());
        assertTrue(StudyRunner.AlternativeGrammar.of("5+5").matches(sevenThreeShape).isEmpty());
    }

    @Test
    void alternativeGrammarsDropBoundaryOnlyCompleteMatches() {
        final List<ConfirmedPivot> pivots = alternatingWindow(
                new double[] { 0, 10, 5, 20, 8, 15, 10, 18, 12, 25, 14, 22, 16 });

        assertEquals(List.of("6-12"), StudyRunner.AlternativeGrammar.of("3+3").matches(pivots));
    }

    @Test
    void alternativeGrammarsRejectTheWrongOriginPivotType() {
        final List<ConfirmedPivot> bearish = alternatingWindow(
                new double[] { 14, 12, 13, 10, 12, 8, 11, 9, 10, 8.5d, 9 });
        bearish.set(0, new ConfirmedPivot(0, 1, DoubleNum.valueOf(14), SwingPivotType.LOW));

        assertTrue(StudyRunner.AlternativeGrammar.of("5+5").matches(bearish).isEmpty());
    }

    private static List<ConfirmedPivot> alternatingWindow(final double[] prices) {
        final List<ConfirmedPivot> pivots = new ArrayList<>();
        for (int i = 0; i < prices.length; i++) {
            pivots.add(new ConfirmedPivot(i, i + 1, DoubleNum.valueOf(prices[i]),
                    i % 2 == 0 ? SwingPivotType.LOW : SwingPivotType.HIGH));
        }
        return pivots;
    }

    private static List<TopologyGrammar> grammars() {
        return List.of(TopologyGrammar.MOTIVE_5, TopologyGrammar.CORRECTIVE_3, TopologyGrammar.CYCLE_5_3);
    }

    private static List<RelationshipRule> rules() {
        return List.of(rule("first"), rule("second"));
    }

    private static RelationshipRule rule(final String id) {
        return new RelationshipRule() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public RuleEvidence evaluate(final TopologyCandidate candidate) {
                return RuleEvidence.pass(id, List.of("synthetic"), "synthetic pass");
            }
        };
    }

    private static StudyRunner.Configuration configuration(final StudyRunner.Partitions partitions,
            final int ensembleSize) {
        return new StudyRunner.Configuration(partitions,
                "b92d667cdbf951aac8d0519006a31e097bc88d26e399b04dd9a89e6353729100", SEED, List.of(2), ensembleSize,
                List.of(new DetectorRobustnessMatrix.DetectorSpec("synthetic", StudyRunnerTest::detectorFactory)),
                "synthetic-primary", null);
    }

    private static SwingDetector detectorFactory() {
        return (series, index, degree) -> {
            final int[] pivotIndices = { 1, 3, 5, 7, 9, 11, 13, 15, 17 };
            final List<SwingPivot> pivots = new ArrayList<>();
            for (int pivotIndex : pivotIndices) {
                if (pivotIndex <= index && pivotIndex < series.getBarCount()) {
                    final SwingPivotType type = pivotIndex % 4 == 1 ? SwingPivotType.LOW : SwingPivotType.HIGH;
                    pivots.add(new SwingPivot(pivotIndex, series.getBar(pivotIndex).getClosePrice(), type));
                }
            }
            return new SwingDetectorResult(pivots, List.of());
        };
    }

    /**
     * Like {@link #buildSeries(int)} but scales every close from {@code fromIndex}
     * on by {@code factor}.
     */
    private static BarSeries buildMutatedSeries(final int count, final int fromIndex, final double factor) {
        final BarSeries series = new BaseBarSeriesBuilder().withName("synthetic-mutated").build();
        final Instant start = Instant.parse("2018-01-01T00:00:00Z");
        for (int index = 0; index < count; index++) {
            final double close = syntheticClose(index) * (index >= fromIndex ? factor : 1.0d);
            series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(start.plus(Duration.ofDays(index + 1)))
                    .openPrice(close)
                    .highPrice(close + 1)
                    .lowPrice(Math.max(0.01d, close - 1))
                    .closePrice(close)
                    .volume(1)
                    .amount(close)
                    .trades(1)
                    .add();
        }
        return series;
    }

    private static BarSeries buildSeries(final int count) {
        final BarSeries series = new BaseBarSeriesBuilder().withName("synthetic").build();
        final Instant start = Instant.parse("2018-01-01T00:00:00Z");
        for (int index = 0; index < count; index++) {
            final double close = syntheticClose(index);
            series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(start.plus(Duration.ofDays(index + 1)))
                    .openPrice(close)
                    .highPrice(close + 1)
                    .lowPrice(close - 1)
                    .closePrice(close)
                    .volume(1)
                    .amount(close)
                    .trades(1)
                    .add();
        }
        return series;
    }

    private static double syntheticClose(final int index) {
        return switch (index) {
        case 1 -> 100;
        case 3 -> 120;
        case 5 -> 110;
        case 7 -> 140;
        case 9 -> 130;
        case 11 -> 160;
        case 13 -> 150;
        case 15 -> 180;
        case 17 -> 170;
        default -> 100 + index;
        };
    }

    @Test
    void alternativeGrammarRetiresHistoricalMatches() {
        final StudyRunner.Partitions partitions = new StudyRunner.Partitions(
                List.of(new StudyRunner.Partition("calibration", LocalDate.of(2018, 1, 1), LocalDate.of(2018, 2, 15))),
                LocalDate.of(2024, 1, 1));
        final StudyRunner.Configuration configuration = configuration(partitions, 1);
        final StudyRunner runner = new StudyRunner(StudyRunnerTest::scriptedDetector, grammars(), rules(),
                configuration);

        // Two sequential complete "3+3" windows: placements beyond one
        // pattern-length behind the newest pivot are retired, so late bars are
        // judged on live hypotheses instead of staying frozen in AMBIGUOUS.
        final StudyReport report = runner.evaluate("BTC", buildDoublePatternSeries(42), 0, 41);
        final StudyReport.ModeReport threePlusThree = report.competingGrammars()
                .stream()
                .filter(mode -> "competing-3+3".equals(mode.mode()))
                .findFirst()
                .orElseThrow();
        final StudyReport.PartitionMetrics calibration = threePlusThree.partitions().get(0);
        assertTrue(calibration.completeCount() > 0);
        // Overlapping live placements still compete (ambiguity exists), stale
        // history retires (completes return after the overlap), and gaps
        // between placements report no-match.
        assertTrue(calibration.ambiguousCount() > 0);
        assertTrue(calibration.noMatchCount() > 0);
        // Shifting placement identities across adjacent ambiguous bars must
        // register as instability, not a constant-token Jaccard of 1.
        assertTrue(calibration.labelStabilityJaccard() < 1.0d);
    }

    /**
     * Detector scripting alternating LOW/HIGH pivots at every odd bar index, priced
     * by that bar's close; the series data alone shapes the pattern.
     */
    private static SwingDetector scriptedDetector() {
        return (series, index, degree) -> {
            final List<SwingPivot> pivots = new ArrayList<>();
            for (int pivotIndex = 1; pivotIndex <= index && pivotIndex < series.getBarCount(); pivotIndex += 2) {
                final SwingPivotType type = pivotIndex % 4 == 1 ? SwingPivotType.LOW : SwingPivotType.HIGH;
                pivots.add(new SwingPivot(pivotIndex, series.getBar(pivotIndex).getClosePrice(), type));
            }
            return new SwingDetectorResult(pivots, List.of());
        };
    }

    @Test
    void nullBaselineRestrictsToRequestedRange() {
        final StudyRunner.Partitions partitions = new StudyRunner.Partitions(
                List.of(new StudyRunner.Partition("calibration", LocalDate.of(2018, 1, 1), LocalDate.of(2018, 1, 12)),
                        new StudyRunner.Partition("validation", LocalDate.of(2018, 1, 13), LocalDate.of(2018, 1, 20)),
                        new StudyRunner.Partition("holdout", LocalDate.of(2018, 1, 21), LocalDate.of(2018, 1, 31))),
                LocalDate.of(2024, 1, 1));
        final StudyRunner.Configuration configuration = configuration(partitions, 1);
        final StudyRunner runner = new StudyRunner(StudyRunnerTest::detectorFactory, grammars(), rules(),
                configuration);

        // Requesting bars 6..11 must keep null members on the same recording
        // window as the real modes; they used to record from their first bar,
        // populating partitions the real report never observed.
        final BarSeries series = buildWickSeries();
        final StudyReport report = runner.evaluate("BTC", series, 6, 11);
        final StudyReport.NullReport nulls = report.nulls()
                .stream()
                .filter(nullReport -> "MOTIVE_5".equals(nullReport.grammar()))
                .findFirst()
                .orElseThrow();
        final long calibrationBars = nulls.partitions()
                .stream()
                .mapToLong(StudyReport.PartitionMetrics::evaluationCount)
                .sum();
        assertEquals(6L, calibrationBars);
    }

    @Test
    void movingWindowNullBaselineTranslatesRequestedStart() {
        final StudyRunner.Partitions partitions = new StudyRunner.Partitions(
                List.of(new StudyRunner.Partition("calibration", LocalDate.of(2018, 1, 1), LocalDate.of(2018, 1, 12)),
                        new StudyRunner.Partition("validation", LocalDate.of(2018, 1, 13), LocalDate.of(2018, 1, 20)),
                        new StudyRunner.Partition("holdout", LocalDate.of(2018, 1, 21), LocalDate.of(2018, 1, 31))),
                LocalDate.of(2024, 1, 1));
        final StudyRunner runner = new StudyRunner(StudyRunnerTest::detectorFactory, grammars(), rules(),
                configuration(partitions, 1));

        // A rolling window whose first eight bars were dropped: beginIndex=8,
        // so requested source bars 10..15 must map to member coordinates
        // 2..7. Recording them at their untranslated source indices used to
        // fall behind every member bar and observe nothing.
        final BarSeries series = buildRollingWindowSeries(20, 12);
        assertEquals(8, series.getBeginIndex());
        final StudyReport report = runner.evaluate("BTC", series, 10, 15);
        final StudyReport.NullReport nulls = report.nulls()
                .stream()
                .filter(nullReport -> "MOTIVE_5".equals(nullReport.grammar()))
                .findFirst()
                .orElseThrow();
        long calibrationBars = 0;
        long validationBars = 0;
        StudyReport.PartitionMetrics calibrationMetrics = null;
        StudyReport.PartitionMetrics validationMetrics = null;
        for (final StudyReport.PartitionMetrics metrics : nulls.partitions()) {
            if ("calibration".equals(metrics.partition())) {
                calibrationBars += metrics.evaluationCount();
                calibrationMetrics = metrics;
            } else if ("validation".equals(metrics.partition())) {
                validationBars += metrics.evaluationCount();
                validationMetrics = metrics;
            }
        }
        assertEquals(2L, calibrationBars);
        assertEquals(4L, validationBars);
        // Null members are rebased sub-series; recorded bounds must be
        // translated back to source coordinates so null and real metric
        // bounds are comparable (member 2..3 == source 10..11, member
        // 4..7 == source 12..15).
        assertNotNull(calibrationMetrics);
        assertEquals(10, calibrationMetrics.fromIndex());
        assertEquals(11, calibrationMetrics.toIndex());
        assertNotNull(validationMetrics);
        assertEquals(12, validationMetrics.fromIndex());
        assertEquals(15, validationMetrics.toIndex());
    }

    @Test
    void rejectsDuplicateNullBlockLengths() {
        final StudyRunner.Partitions partitions = new StudyRunner.Partitions(
                List.of(new StudyRunner.Partition("calibration", LocalDate.of(2018, 1, 1), LocalDate.of(2018, 1, 31))),
                LocalDate.of(2024, 1, 1));
        // A duplicated block length would run the identical ensemble twice and
        // double-count it in every aggregate.
        assertThrows(IllegalArgumentException.class, () -> new StudyRunner.Configuration(partitions,
                "b92d667cdbf951aac8d0519006a31e097bc88d26e399b04dd9a89e6353729100", SEED, List.of(2, 2), 1,
                List.of(new DetectorRobustnessMatrix.DetectorSpec("synthetic", StudyRunnerTest::detectorFactory)),
                "synthetic-primary", null));
    }

    @Test
    void rejectsUnknownCompetingMode() {
        final StudyRunner.Partitions partitions = new StudyRunner.Partitions(
                List.of(new StudyRunner.Partition("calibration", LocalDate.of(2018, 1, 1), LocalDate.of(2018, 1, 31))),
                LocalDate.of(2024, 1, 1));
        // A frozen protocol must never silently widen its declared competing set.
        assertThrows(IllegalArgumentException.class,
                () -> new StudyRunner.Configuration(partitions,
                        "b92d667cdbf951aac8d0519006a31e097bc88d26e399b04dd9a89e6353729100", SEED, List.of(2), 1,
                        List.of(), "synthetic-primary", List.of("undeclared-mode")));
    }

    @Test
    void rejectsDuplicateCompetingModes() {
        final StudyRunner.Partitions partitions = new StudyRunner.Partitions(
                List.of(new StudyRunner.Partition("calibration", LocalDate.of(2018, 1, 1), LocalDate.of(2018, 1, 31))),
                LocalDate.of(2024, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new StudyRunner.Configuration(partitions,
                        "b92d667cdbf951aac8d0519006a31e097bc88d26e399b04dd9a89e6353729100", SEED, List.of(2), 1,
                        List.of(), "synthetic-primary", List.of("3+3", "3+3")));
    }

    @Test
    void declaredCompetingModesRunExactly() {
        final StudyRunner.Partitions partitions = new StudyRunner.Partitions(
                List.of(new StudyRunner.Partition("calibration", LocalDate.of(2018, 1, 1), LocalDate.of(2018, 1, 31))),
                LocalDate.of(2024, 1, 1));
        final StudyRunner.Configuration configuration = new StudyRunner.Configuration(partitions,
                "b92d667cdbf951aac8d0519006a31e097bc88d26e399b04dd9a89e6353729100", SEED, List.of(2), 1, List.of(),
                "synthetic-primary", List.of("3+3"));
        final StudyRunner runner = new StudyRunner(StudyRunnerTest::detectorFactory, grammars(), rules(),
                configuration);
        final StudyReport report = runner.evaluate("BTC", buildSeries(24), 0, 23);

        assertEquals(List.of("3+3"), report.competingGrammars().stream().map(StudyReport.ModeReport::grammar).toList());
    }

    @Test
    void rejectsDuplicateGrammarEntries() {
        final StudyRunner.Partitions partitions = new StudyRunner.Partitions(
                List.of(new StudyRunner.Partition("calibration", LocalDate.of(2018, 1, 1), LocalDate.of(2018, 1, 31))),
                LocalDate.of(2024, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new StudyRunner(StudyRunnerTest::detectorFactory,
                List.of(TopologyGrammar.MOTIVE_5, TopologyGrammar.MOTIVE_5), rules(), configuration(partitions, 1)));
    }

    @Test
    void nullsRecordNothingWhenEvaluationWindowPrecedesSeries() {
        // toIndex below the series begin clamps to an empty evaluation window;
        // real modes record nothing, so null partitions must stay empty too
        // instead of comparing against full-series member observations.
        final StudyRunner.Configuration configuration = configuration(StudyRunner.Partitions.lockedDefault(), 2);
        final StudyRunner runner = new StudyRunner(StudyRunnerTest::detectorFactory, grammars(), rules(),
                configuration);
        final StudyReport report = runner.evaluate("BTC", buildSeries(24), -4, -1);

        assertFalse(report.nulls().isEmpty());
        for (final StudyReport.NullReport nullReport : report.nulls()) {
            for (final StudyReport.PartitionMetrics partition : nullReport.partitions()) {
                assertEquals(0L, partition.evaluationCount(), () -> "non-empty null partition " + partition);
            }
        }
    }

    @Test
    void rejectsGrammarsOmittingDeclaredH1Grammar() {
        // H1 is declared over MOTIVE_5; a configuration without it must be
        // rejected instead of emitting an H1 section that never measured it.
        final StudyRunner.Partitions partitions = new StudyRunner.Partitions(
                List.of(new StudyRunner.Partition("calibration", LocalDate.of(2018, 1, 1), LocalDate.of(2018, 1, 31))),
                LocalDate.of(2024, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new StudyRunner(StudyRunnerTest::detectorFactory,
                List.of(TopologyGrammar.CORRECTIVE_3), rules(), configuration(partitions, 1)));
    }

    @Test
    void prefixEvaluationIgnoresContradictionsBeyondRequestedEnd() {
        // Cumulative odd-bar pivots whose interior pivot at index 3 is
        // silently re-priced from asOf=9 onward.
        final SwingDetector contradicting = (series, index, degree) -> {
            final List<SwingPivot> pivots = new ArrayList<>();
            for (int pivotIndex = 1; pivotIndex <= index && pivotIndex < series.getBarCount(); pivotIndex += 2) {
                final SwingPivotType type = pivotIndex % 4 == 1 ? SwingPivotType.LOW : SwingPivotType.HIGH;
                double price = series.getBar(pivotIndex).getClosePrice().doubleValue();
                if (index >= 9 && pivotIndex == 3) {
                    price += 100;
                }
                pivots.add(new SwingPivot(pivotIndex, DoubleNum.valueOf(price), type));
            }
            return new SwingDetectorResult(pivots, List.of());
        };
        final StudyRunner.Partitions partitions = new StudyRunner.Partitions(
                List.of(new StudyRunner.Partition("calibration", LocalDate.of(2018, 1, 1), LocalDate.of(2018, 1, 31))),
                LocalDate.of(2024, 1, 1));
        final BarSeries series = buildWickSeries();

        final StudyReport prefix = new StudyRunner(() -> contradicting, grammars(), rules(),
                configuration(partitions, 1)).evaluate("BTC", series, 0, 8);
        assertFalse(prefix.competingGrammars().isEmpty());

        // The same contradiction inside the requested range still fails loud.
        assertThrows(IllegalStateException.class,
                () -> new StudyRunner(() -> contradicting, grammars(), rules(), configuration(partitions, 1))
                        .evaluate("BTC", series, 0, 9));
    }

    @Test
    void partialGrammarRequiresDirectionOnInternalLegs() {
        // Pivots [100, 110, 110, 120]: the middle leading leg is flat, so no
        // honest forming claim survives; only the uncommitted trailing leg may
        // stay undecided.
        final StudyRunner.Partitions partitions = new StudyRunner.Partitions(
                List.of(new StudyRunner.Partition("calibration", LocalDate.of(2018, 1, 1), LocalDate.of(2018, 1, 31))),
                LocalDate.of(2024, 1, 1));
        final StudyRunner runner = new StudyRunner(StudyRunnerTest::detectorFactory, grammars(), rules(),
                configuration(partitions, 1));
        final BarSeries series = buildOddPivotSeries(new double[] { 100, 110, 110, 120 }, 10);

        final StudyReport report = runner.evaluate("BTC", series, 0, 9);
        final StudyReport.ModeReport threePlusThree = report.competingGrammars()
                .stream()
                .filter(mode -> "competing-3+3".equals(mode.mode()))
                .findFirst()
                .orElseThrow();
        final StudyReport.PartitionMetrics calibration = threePlusThree.partitions().get(0);
        assertEquals(0, calibration.formingCount());
        assertTrue(calibration.noMatchCount() > 0);
    }

    private static BarSeries buildRollingWindowSeries(final int total, final int retained) {
        final BarSeries series = new BaseBarSeriesBuilder().withName("rolling-window").build();
        series.setMaximumBarCount(retained);
        final Instant start = Instant.parse("2018-01-01T00:00:00Z");
        for (int index = 0; index < total; index++) {
            series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(start.plus(Duration.ofDays(index + 1)))
                    .openPrice(100 + index)
                    .highPrice(101 + index)
                    .lowPrice(99 + index)
                    .closePrice(100 + index)
                    .volume(1)
                    .amount(100 + index)
                    .trades(1)
                    .add();
        }
        return series;
    }

    private static BarSeries buildOddPivotSeries(final double[] pivotCloses, final int count) {
        final BarSeries series = new BaseBarSeriesBuilder().withName("odd-pivots").build();
        final Instant start = Instant.parse("2018-01-01T00:00:00Z");
        int nextPivot = 0;
        for (int index = 0; index < count; index++) {
            final boolean isPivot = index % 2 == 1 && nextPivot < pivotCloses.length;
            final double close = isPivot ? pivotCloses[nextPivot++] : 90 + index;
            series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(start.plus(Duration.ofDays(index + 1)))
                    .openPrice(Math.max(0.01d, close - 2))
                    .highPrice(close + 1)
                    .lowPrice(Math.max(0.01d, close - 1))
                    .closePrice(close)
                    .volume(1)
                    .amount(close)
                    .trades(1)
                    .add();
        }
        return series;
    }

    /**
     * Two sequential rising zigzags whose per-window junctions stay extreme, so
     * both halves complete as disjoint "3+3" placements.
     */
    private static BarSeries buildDoublePatternSeries(final int count) {
        // Three acts: an initial bullish "3+3", an overlapping bearish and
        // bullish pair that both satisfy the origin/extremity rules, then a
        // fresh bullish placement that completes after the earlier windows
        // leave the one-pattern-length horizon.
        final double[] pivotCloses = { 100, 106, 102, 112, 98, 118, 110, 116, 108, 96, 118, 110, 111, 105, 90, 96, 92,
                102, 94, 100, 92 };
        final double[] prices = new double[count];
        int pivotCursor = 0;
        for (int index = 0; index < count; index++) {
            prices[index] = index % 2 == 1 && pivotCursor < pivotCloses.length ? pivotCloses[pivotCursor++]
                    : 90 + index;
        }
        final BarSeries series = new BaseBarSeriesBuilder().withName("synthetic-double").build();
        final Instant start = Instant.parse("2018-01-01T00:00:00Z");
        for (int index = 0; index < count; index++) {
            final double close = prices[index];
            series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(start.plus(Duration.ofDays(index + 1)))
                    .openPrice(close)
                    .highPrice(close + 1)
                    .lowPrice(Math.max(0.01d, close - 1))
                    .closePrice(close)
                    .volume(1)
                    .amount(close)
                    .trades(1)
                    .add();
        }
        return series;
    }

    /**
     * Series whose per-bar wick ratios and close-to-close returns are all distinct.
     */
    private static BarSeries buildWickSeries() {
        final double[] closes = { 100, 101.5, 99.2, 104.1, 102.3, 107.8, 105.2, 110.9, 108.4, 113.6, 111.1, 116.9 };
        final BarSeries series = new BaseBarSeriesBuilder().withName("synthetic-wicks").build();
        final Instant start = Instant.parse("2018-01-01T00:00:00Z");
        for (int index = 0; index < closes.length; index++) {
            final double close = closes[index];
            final double high = close * (1 + (index % 5 + 1) / 50.0);
            final double low = close * (1 - (index % 3 + 1) / 60.0);
            series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(start.plus(Duration.ofDays(index + 1)))
                    .openPrice(low + (high - low) / 3)
                    .highPrice(high)
                    .lowPrice(low)
                    .closePrice(close)
                    .volume(1)
                    .amount(close)
                    .trades(1)
                    .add();
        }
        return series;
    }

    private static BarSeries buildFallingSeries(final int count) {
        final double[] prices = { 130, 100, 128, 90, 126, 85, 124, 75, 122, 70, 120, 60, 118, 58, 116, 56, 114, 54, 112,
                52, 110, 50, 108, 48 };
        final BarSeries series = new BaseBarSeriesBuilder().withName("synthetic-falling").build();
        final Instant start = Instant.parse("2018-01-01T00:00:00Z");
        for (int index = 0; index < count; index++) {
            final double close = prices[index];
            series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(start.plus(Duration.ofDays(index + 1)))
                    .openPrice(close)
                    .highPrice(close + 1)
                    .lowPrice(Math.max(0.01d, close - 1))
                    .closePrice(close)
                    .volume(1)
                    .amount(close)
                    .trades(1)
                    .add();
        }
        return series;
    }

    private static BarSeries buildTrendingSeries(final int count) {
        final double[] prices = { 90, 100, 95, 108, 98, 102, 96, 115, 104, 110, 101, 112, 106, 118, 109, 121, 112, 124,
                115, 127, 118, 130, 121, 133 };
        final BarSeries series = new BaseBarSeriesBuilder().withName("synthetic-trending").build();
        final Instant start = Instant.parse("2018-01-01T00:00:00Z");
        for (int index = 0; index < count; index++) {
            final double close = prices[index];
            series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(start.plus(Duration.ofDays(index + 1)))
                    .openPrice(close)
                    .highPrice(close + 1)
                    .lowPrice(Math.max(0.01d, close - 1))
                    .closePrice(close)
                    .volume(1)
                    .amount(close)
                    .trades(1)
                    .add();
        }
        return series;
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

}

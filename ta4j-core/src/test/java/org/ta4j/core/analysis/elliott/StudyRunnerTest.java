/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.analysis.elliott.ConfirmedPivot;
import org.ta4j.core.analysis.elliott.swing.SwingDetector;
import org.ta4j.core.analysis.elliott.swing.SwingPivotType;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.analysis.elliott.swing.SwingDetectorResult;
import org.ta4j.core.analysis.elliott.swing.SwingPivot;
import org.ta4j.core.analysis.elliott.swing.SwingPivotType;
import org.ta4j.core.analysis.elliott.swing.SwingDetectors;

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
                "test-fractal");
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

        // H1 is exactly the caller-declared grammar set; H2 is exactly the
        // ablation ladder (topology-only, one rung per rule, classical-all).
        assertEquals(3, report.h1().modes().size());
        assertEquals(List.of("MOTIVE_5", "CORRECTIVE_3", "CYCLE_5_3"),
                report.h1().modes().stream().map(StudyReport.ModeReport::grammar).toList());
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
        assertTrue(report.toJson().contains("protocolFingerprint"));
        assertTrue(report.toJson().contains("evidencePassRate"));
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
                "synthetic-primary");
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

    @Test
    void bootstrapMemberShapeTravelsWithSampledReturns() {
        final BarSeries source = buildWickSeries();
        final List<BarSeries> members = BlockBootstrapNulls.generate(source, 3, 1, 7L);
        final BarSeries member = members.get(0);

        // Regression: intrabar shape used to stay in original chronology, so
        // every member inherited the real series' wick sequence. Each member
        // bar must carry the OHLC ratios of the source bar whose close-to-close
        // return was drawn for that position.
        final double[] sourceReturns = new double[source.getBarCount() - 1];
        for (int offset = 1; offset < source.getBarCount(); offset++) {
            sourceReturns[offset - 1] = Math.log(source.getBar(offset).getClosePrice().doubleValue()
                    / source.getBar(offset - 1).getClosePrice().doubleValue());
        }
        boolean sawRelocatedShape = false;
        for (int offset = 1; offset < member.getBarCount(); offset++) {
            final double drawnReturn = Math.log(member.getBar(offset).getClosePrice().doubleValue()
                    / member.getBar(offset - 1).getClosePrice().doubleValue());
            int shapePosition = -1;
            for (int candidate = 0; candidate < sourceReturns.length; candidate++) {
                if (Math.abs(sourceReturns[candidate] - drawnReturn) < 1e-12) {
                    shapePosition = candidate + 1;
                    break;
                }
            }
            assertTrue(shapePosition >= 0, "member return not drawn from the observed tape at offset " + offset);
            final double expectedRatio = source.getBar(shapePosition).getHighPrice().doubleValue()
                    / source.getBar(shapePosition).getClosePrice().doubleValue();
            final double actualRatio = member.getBar(offset).getHighPrice().doubleValue()
                    / member.getBar(offset).getClosePrice().doubleValue();
            assertEquals(expectedRatio, actualRatio, 1e-9, "wick ratio not traveling with sampled return");
            final double chronologicalRatio = source.getBar(offset).getHighPrice().doubleValue()
                    / source.getBar(offset).getClosePrice().doubleValue();
            if (Math.abs(chronologicalRatio - actualRatio) > 1e-9) {
                sawRelocatedShape = true;
            }
        }
        assertTrue(sawRelocatedShape, "sampling never relocated a wick shape; test lost discriminating power");
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

    /**
     * Two sequential rising zigzags whose per-window junctions stay extreme, so
     * both halves complete as disjoint "3+3" placements.
     */
    private static BarSeries buildDoublePatternSeries(final int count) {
        // Three acts: a clean bullish "3+3" (pivots 0-6), an overlapping pair
        // sharing pivot 6 (bearish 6-12 and bullish 7-13 both match while
        // live), then a fresh bullish placement (14-20) that completes only
        // after the earlier windows have left the one-pattern-length horizon.
        final double[] pivotCloses = { 100, 106, 102, 112, 104, 110, 106, 101, 107, 96, 118, 110, 111, 105, 90, 96,
                92, 102, 94, 100, 92 };
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
}

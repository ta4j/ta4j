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
import org.ta4j.core.analysis.elliott.swing.SwingDetector;
import org.ta4j.core.analysis.elliott.swing.SwingDetectorResult;
import org.ta4j.core.analysis.elliott.swing.SwingPivot;
import org.ta4j.core.analysis.elliott.swing.SwingPivotType;
import org.ta4j.core.analysis.elliott.swing.SwingDetectors;

class ElliottStudyRunnerTest {

    private static final long SEED = 5_252_026L;

    @Test
    void calibrationCannotTouchForbiddenDate() {
        final StudyRunner.Partitions invalid = new StudyRunner.Partitions(
                List.of(new StudyRunner.Partition("calibration", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2)),
                        new StudyRunner.Partition("validation", LocalDate.of(2024, 1, 3), LocalDate.of(2024, 1, 4)),
                        new StudyRunner.Partition("holdout", LocalDate.of(2024, 1, 5), LocalDate.of(2024, 1, 6))),
                LocalDate.of(2024, 1, 1));
        final StudyRunner runner = new StudyRunner(ElliottStudyRunnerTest::detectorFactory,
                List.of(TopologyGrammar.MOTIVE_5), List.of(), configuration(invalid, 1));

        assertThrows(IllegalStateException.class, () -> runner.evaluate(buildSeries(20), 0, 19));
    }

    @Test
    void sameSeedProducesIdenticalReport() {
        final StudyRunner.Configuration configuration = configuration(StudyRunner.Partitions.lockedDefault(), 2);
        final StudyRunner firstRunner = new StudyRunner(ElliottStudyRunnerTest::detectorFactory, grammars(), rules(),
                configuration);
        final StudyRunner secondRunner = new StudyRunner(ElliottStudyRunnerTest::detectorFactory, grammars(), rules(),
                configuration);

        final String first = firstRunner.evaluate("BTC", buildSeries(24), 0, 23).toJson();
        final String second = secondRunner.evaluate("BTC", buildSeries(24), 0, 23).toJson();

        assertEquals(first, second);
    }

    @Test
    void futureBarsDoNotChangeEarlierEvaluation() {
        final StudyRunner.Configuration configuration = configuration(StudyRunner.Partitions.lockedDefault(), 2);
        final StudyRunner runner = new StudyRunner(ElliottStudyRunnerTest::detectorFactory, grammars(), rules(),
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
                List.of(new DetectorRobustnessMatrix.DetectorSpec("fractal", () -> SwingDetectors.fractal(2))));
        final StudyRunner runner = new StudyRunner(() -> SwingDetectors.fractal(2), grammars(), rules(), configuration);

        final String prefix = runner.evaluate("BTC", buildSeries(24), 0, 19).toJson();
        final String appended = runner.evaluate("BTC", buildSeries(40), 0, 19).toJson();

        assertEquals(prefix, appended);
    }

    @Test
    void ablationModesContainExactlyTheirSelectedRule() {
        final StudyRunner.Configuration configuration = configuration(StudyRunner.Partitions.lockedDefault(), 1);
        final StudyRunner runner = new StudyRunner(ElliottStudyRunnerTest::detectorFactory, grammars(), rules(),
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
        final StudyRunner runner = new StudyRunner(ElliottStudyRunnerTest::detectorFactory, grammars(), rules(),
                configuration);
        final StudyReport report = runner.evaluate("BTC", buildSeries(24), 0, 23);

        assertFalse(report.h1().modes().isEmpty());
        assertFalse(report.h2().modes().isEmpty());
        assertEquals("H1", report.h1().id());
        assertEquals("H2", report.h2().id());
        assertEquals(7, report.competingGrammars().size());
        assertTrue(report.competingGrammars()
                .stream()
                .anyMatch(mode -> "competing-change-point-baseline".equals(mode.mode())));
        assertFalse(report.ablations().isEmpty());
        assertEquals(1, report.robustness().detectors().size());
        assertEquals(List.of(2), report.nulls().stream().map(StudyReport.NullReport::blockLength).toList());
        assertTrue(report.toJson().contains("protocolFingerprint"));
        assertTrue(report.toJson().contains("evidencePassRate"));
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
                List.of(new DetectorRobustnessMatrix.DetectorSpec("synthetic",
                        ElliottStudyRunnerTest::detectorFactory)));
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
}

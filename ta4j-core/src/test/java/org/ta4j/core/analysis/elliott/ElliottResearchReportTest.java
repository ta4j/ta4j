/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.analysis.elliott.ElliottResearchReport.CoverageRow;
import org.ta4j.core.analysis.elliott.ElliottResearchReport.Metric;
import org.ta4j.core.analysis.elliott.ElliottResearchReport.NullReference;
import org.ta4j.core.analysis.elliott.ElliottResearchReport.PairedDifference;
import org.ta4j.core.analysis.elliott.ElliottResearchReport.Row;
import org.ta4j.core.analysis.elliott.StudyReport.NullMemberMetrics;
import org.ta4j.core.analysis.elliott.StudyReport.PartitionMetrics;
import org.ta4j.core.analysis.elliott.swing.SwingDetector;
import org.ta4j.core.analysis.elliott.swing.SwingDetectorResult;
import org.ta4j.core.analysis.elliott.swing.SwingPivot;
import org.ta4j.core.analysis.elliott.swing.SwingPivotType;

class ElliottResearchReportTest {

    private static final long SEED = 5_252_026L;

    @Test
    void nullReferenceUsesNearestRankOverFiniteMembersOnly() {
        final double[] values = new double[43];
        for (int index = 0; index < 40; index++) {
            values[(index * 7) % 40] = index + 1;
        }
        values[40] = Double.NaN;
        values[41] = Double.POSITIVE_INFINITY;
        values[42] = Double.NaN;

        final NullReference reference = ElliottResearchReport.summarize(values, 45);

        assertEquals(45, reference.requested());
        assertEquals(40, reference.valid());
        assertEquals(5, reference.unavailable());
        assertEquals(1.0d, reference.low());
        assertEquals(20.0d, reference.median());
        assertEquals(39.0d, reference.high());
        assertEquals(4.0d / 41.0d, ElliottResearchReport.referenceRank(38.0d, values));
        assertEquals(1.0d / 41.0d, ElliottResearchReport.referenceRank(41.0d, values));
        assertEquals(1.0d, ElliottResearchReport.referenceRank(0.0d, values));
    }

    @Test
    void nullReferenceOfSmallAndTiedSamplesIsExact() {
        final NullReference small = ElliottResearchReport.summarize(new double[] { 5, 1, 4, 2, 3 }, 5);
        assertEquals(1.0d, small.low());
        assertEquals(3.0d, small.median());
        assertEquals(5.0d, small.high());

        final double[] ties = { 1, 1, 1, 1 };
        final NullReference tied = ElliottResearchReport.summarize(ties, 4);
        assertEquals(1.0d, tied.median());
        assertEquals(5.0d / 5.0d, ElliottResearchReport.referenceRank(1.0d, ties));
        assertEquals(1.0d / 5.0d, ElliottResearchReport.referenceRank(1.5d, ties));

        assertThrows(IllegalArgumentException.class, () -> ElliottResearchReport.summarize(new double[3], 2));
    }

    @Test
    void emptyAndAllUndefinedSamplesAreUnavailableWithCountsPreserved() {
        final NullReference empty = ElliottResearchReport.summarize(new double[0], 8);
        assertFalse(empty.available());
        assertEquals(8, empty.requested());
        assertEquals(0, empty.valid());
        assertEquals(8, empty.unavailable());
        assertTrue(Double.isNaN(empty.median()) && Double.isNaN(empty.low()) && Double.isNaN(empty.high()));

        final double[] undefined = { Double.NaN, Double.NaN, Double.NaN };
        final NullReference allNaN = ElliottResearchReport.summarize(undefined, 4);
        assertFalse(allNaN.available());
        assertEquals(4, allNaN.unavailable());
        assertTrue(Double.isNaN(allNaN.median()));
        assertTrue(Double.isNaN(ElliottResearchReport.referenceRank(0.5d, undefined)));
        assertTrue(Double.isNaN(ElliottResearchReport.referenceRank(Double.NaN, new double[] { 1.0d })));
    }

    @Test
    void pairedDifferencesOnlyPairSameMemberWithinSamePartition() {
        final List<NullMemberMetrics> full = List.of(member(0, "calibration", 0.5d), member(1, "calibration", 0.25d),
                member(0, "holdout", 0.75d), member(1, "holdout", Double.NaN));
        final List<NullMemberMetrics> ablated = List.of(member(1, "holdout", 0.5d), member(0, "holdout", 0.5d),
                member(1, "calibration", 0.25d), member(0, "calibration", 0.25d));

        final List<PairedDifference> differences = ElliottResearchReport.pairedDifferences(full, ablated,
                Metric.COMPLETE_OCCUPANCY);

        assertEquals(4, differences.size());
        assertEquals(new PairedDifference("calibration", 0, 0.25d), differences.get(0));
        assertEquals(new PairedDifference("calibration", 1, 0.0d), differences.get(1));
        assertEquals(new PairedDifference("holdout", 0, 0.25d), differences.get(2));
        assertEquals("holdout", differences.get(3).partition());
        assertTrue(Double.isNaN(differences.get(3).difference()));

        final List<NullMemberMetrics> otherTape = List.of(member(0, "calibration", 0.25d),
                member(1, "calibration", 0.25d), member(0, "holdout", 0.5d), member(1, "validation", 0.5d));
        final IllegalArgumentException crossPartition = assertThrows(IllegalArgumentException.class,
                () -> ElliottResearchReport.pairedDifferences(full, otherTape, Metric.COMPLETE_OCCUPANCY));
        assertTrue(crossPartition.getMessage().contains("holdout#1"));
        assertThrows(IllegalArgumentException.class,
                () -> ElliottResearchReport.pairedDifferences(otherTape, full, Metric.COMPLETE_OCCUPANCY));
    }

    @Test
    void csvRoundTripPreservesAvailableUnavailableAndNotComparableRows(@TempDir final Path directory) {
        final List<Row> rows = List.of(
                new Row("btc,\"daily\"", "BTC", "h2", "all-rules", "CYCLE_5_3", List.of("first", "+second"), "primary",
                        "kernel-topology+evidence", "holdout", "evidencePassRate", 8, 0.1234567890123d, 3L, 7L, 6, 8, 2,
                        0.5d, 0.0000123d, 1.0d, 0.1234567890123d - 0.5d, 3.0d / 7.0d, ElliottResearchReport.AVAILABLE),
                new Row("btc,\"daily\"", "BTC", "h2", "all-rules", "CYCLE_5_3", List.of("first", "+second"), "primary",
                        "kernel-topology+evidence", "validation", "jointPassRate", 8, Double.NaN, 0L, 0L, 0, 8, 8,
                        Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, ElliottResearchReport.UNAVAILABLE),
                new Row("btc,\"daily\"", "BTC", "robustness", "line\nbreak", "MOTIVE_5", List.of(), "alt", "detector",
                        "holdout", "confirmationLagBars", -1, 2.5d, null, null, 0, 0, 0, Double.NaN, Double.NaN,
                        Double.NaN, Double.NaN, Double.NaN, ElliottResearchReport.NOT_COMPARABLE));
        final Path file = directory.resolve("nested/comparisons.csv");

        ElliottResearchReport.writeCsv(file, rows);

        assertEquals(rows, ElliottResearchReport.readCsv(file));
        final String text = read(file);
        assertTrue(text.contains("\"btc,\"\"daily\"\"\""));
        assertFalse(text.contains("E-"), "numbers must be plain decimal text");
    }

    @Test
    void readCsvRejectsHeaderAndKeyTampering(@TempDir final Path directory) throws IOException {
        final Row row = new Row("d", "A", "h1", "topology-only", "MOTIVE_5", List.of(), "p", "kernel-topology", "cal",
                "ambiguousRate", -1, 0.5d, 1L, 2L, 0, 0, 0, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                ElliottResearchReport.NOT_COMPARABLE);
        final Path file = directory.resolve("comparisons.csv");
        ElliottResearchReport.writeCsv(file, List.of(row));

        Files.writeString(file, read(file).replace("d|h1|topology-only|p|cal|ambiguousRate|b-",
                "d|h1|topology-only|p|cal|noMatchRate|b-"), StandardCharsets.UTF_8);
        final IllegalArgumentException tampered = assertThrows(IllegalArgumentException.class,
                () -> ElliottResearchReport.readCsv(file));
        assertTrue(tampered.getMessage().contains(file + ":2"));

        ElliottResearchReport.writeCsv(file, List.of(row));
        Files.writeString(file, read(file).replace(",0.5,", ",half,"), StandardCharsets.UTF_8);
        final IllegalArgumentException malformedNumber = assertThrows(IllegalArgumentException.class,
                () -> ElliottResearchReport.readCsv(file));
        assertTrue(malformedNumber.getMessage().startsWith(file + ":2: "), malformedNumber.getMessage());

        Files.writeString(file, "wrong,header\r\n", StandardCharsets.UTF_8);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> ElliottResearchReport.readCsv(file)).getMessage()
                .contains(file + ":1"));
    }

    @Test
    void parseKeyRoundTripsAndRejectsMalformedKeys() {
        final String key = "btc|h2|+first|primary|holdout|completeOccupancyRate|b8";
        final ElliottResearchReport.Key parsed = ElliottResearchReport.parseKey(key);
        assertEquals(8, parsed.nullBlockLength());
        assertEquals("+first", parsed.mode());
        assertEquals(key, parsed.toString());
        assertEquals(-1, ElliottResearchReport.parseKey("a|b|c|d|e|f|b-").nullBlockLength());
        assertEquals(Integer.MAX_VALUE,
                ElliottResearchReport.parseKey("a|b|c|d|e|f|b" + Integer.MAX_VALUE).nullBlockLength());

        for (final String malformed : new String[] { "a|b|c|d|e|f", "a|b|c|d|e|f|b0", "a|b|c|d|e|f|8", "a||c|d|e|f|b8",
                "a|b|c|d|e|f|g|b8", "a|b|c|d|e|f|b2147483648" }) {
            final IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> ElliottResearchReport.parseKey(malformed), malformed);
            assertTrue(failure.getMessage().contains("dataset|section|mode|detector|partition|metric|b<blockLength>"));
        }
        assertThrows(IllegalArgumentException.class, () -> ElliottResearchReport.parseKey(null));
    }

    @Test
    void comparisonsOfAStudyReportHaveUniqueRoundTrippingKeysAndScopedNullSummaries(@TempDir final Path directory) {
        final StudyRunner runner = new StudyRunner(ElliottResearchReportTest::detectorFactory, grammars(), rules(),
                configuration());
        final StudyReport report = runner.evaluate("BTC", buildSeries(24), 0, 23);

        final List<Row> rows = ElliottResearchReport.comparisons("btc-daily", report);

        final Set<String> keys = new HashSet<>();
        for (final Row row : rows) {
            assertTrue(keys.add(row.key()), row.key());
            assertEquals(row.key(), ElliottResearchReport.parseKey(row.key()).toString());
            assertEquals("btc-daily", ElliottResearchReport.parseKey(row.key()).dataset());
            assertFalse(row.metric().equals("matchRate"));
        }
        assertEquals(Set.of("h1", "h2", "competing", "robustness"),
                rows.stream().map(Row::section).collect(java.util.stream.Collectors.toSet()));

        for (final PartitionMetrics expected : report.h1().modes().get(0).partitions()) {
            final Row row = find(rows, "h1", "topology-only", expected.partition(), "completeOccupancyRate", 2);
            assertEquals(expected.completeOccupancyRate(), row.observed());
            assertEquals(expected.completeCount(), row.numerator());
            assertEquals(expected.evaluationCount(), row.denominator());
            final double[] members = report.nulls()
                    .get(0)
                    .members()
                    .stream()
                    .filter(member -> member.partition().equals(expected.partition()))
                    .mapToDouble(member -> member.partitions().get(0).completeOccupancyRate())
                    .toArray();
            final NullReference reference = ElliottResearchReport.summarize(members, 2);
            assertEquals(reference.valid(), row.validNullMembers());
            assertEquals(2, row.requestedNullMembers());
            assertEquals(reference.unavailable(), row.unavailableNullMembers());
            assertEquals(reference.median(), row.nullMedian());
            if (Double.isFinite(expected.completeOccupancyRate()) && reference.available()) {
                assertEquals(ElliottResearchReport.AVAILABLE, row.availability());
                assertEquals(expected.completeOccupancyRate() - reference.median(), row.observedMinusNullMedian());
                assertEquals(ElliottResearchReport.referenceRank(expected.completeOccupancyRate(), members),
                        row.empiricalReferenceRank());
            } else {
                assertEquals(ElliottResearchReport.UNAVAILABLE, row.availability());
            }
        }

        assertTrue(rows.stream()
                .noneMatch(row -> "topology-only".equals(row.mode()) && "h2".equals(row.section())
                        && row.metric().startsWith("evidence")));
        assertTrue(rows.stream()
                .anyMatch(row -> "all-rules".equals(row.mode()) && "jointPassRate".equals(row.metric())
                        && row.nullBlockLength() == 2));
        assertTrue(rows.stream()
                .filter(row -> "robustness".equals(row.section()))
                .allMatch(row -> row.nullBlockLength() == -1 && !row.metric().startsWith("evidence")
                        && !ElliottResearchReport.AVAILABLE.equals(row.availability())));

        final Path file = directory.resolve("comparisons.csv");
        ElliottResearchReport.writeCsv(file, rows);
        assertEquals(rows, ElliottResearchReport.readCsv(file));
    }

    @Test
    void summaryNotesMissingEvidenceAndListsRecaptureCommand() {
        final Row available = new Row("d1", "A", "h1", "topology-only", "MOTIVE_5", List.of(), "p", "kernel-topology",
                "cal", "completeOccupancyRate", 2, 0.5d, 5L, 10L, 2, 2, 0, 0.25d, 0.2d, 0.3d, 0.25d, 1.0d / 3.0d,
                ElliottResearchReport.AVAILABLE);
        final Row omitted = new Row("d1", "A", "h1", "topology-only", "MOTIVE_5", List.of(), "p", "kernel-topology",
                "cal", "noMatchRate", -1, 0.5d, 5L, 10L, 0, 0, 0, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, ElliottResearchReport.NOT_COMPARABLE);
        final List<CoverageRow> coverage = List.of(new CoverageRow("d1", "A", "2020-01-01", "2020-12-31", "2020-01-02",
                "2020-12-30", 364, "partial", "gap | missing"));

        final Row otherDataset = new Row("d2", "B", "h1", "topology-only", "MOTIVE_5", List.of(), "p",
                "kernel-topology", "cal", "completeOccupancyRate", 2, 0.5d, 5L, 10L, 2, 2, 0, 0.25d, 0.2d, 0.3d, 0.25d,
                1.0d / 3.0d, ElliottResearchReport.AVAILABLE);
        final List<Row> rows = List.of(available, omitted, otherDataset);

        final String off = ElliottResearchReport.summaryMarkdown("Run", coverage, rows, Set.of(),
                "research recapture --trace real d1");
        final String mixed = ElliottResearchReport.summaryMarkdown("Run", coverage, rows, Set.of("d2"),
                "research recapture --trace real d1");
        final String on = ElliottResearchReport.summaryMarkdown("Run", coverage, rows, Set.of("d1", "d2"), "unused");

        assertTrue(off.contains("evidence not captured"));
        assertTrue(off.contains("Evidence not captured for d1, d2."));
        assertTrue(off.contains("research recapture --trace real d1"));
        assertTrue(off.contains("not a confidence interval"));
        assertTrue(off.contains("not a p-value"));
        assertTrue(off.contains("`d1\\|h1\\|topology-only\\|p\\|cal\\|completeOccupancyRate\\|b2`"));
        assertTrue(off.contains("gap \\| missing"));
        assertTrue(off.contains("1 unavailable or not-comparable row(s) omitted"));
        assertFalse(off.contains("noMatchRate"));
        // Evidence is judged per dataset: one verified trace does not vouch for
        // another.
        final String d1Section = mixed.substring(mixed.indexOf("### d1"), mixed.indexOf("### d2"));
        final String d2Section = mixed.substring(mixed.indexOf("### d2"), mixed.indexOf("## Evidence"));
        assertTrue(d1Section.contains("evidence not captured"), d1Section);
        assertTrue(d2Section.contains("trace captured"), d2Section);
        assertFalse(d2Section.contains("evidence not captured"), d2Section);
        assertTrue(mixed.contains("Evidence not captured for d1."), mixed);
        assertFalse(on.contains("evidence not captured"));
        assertFalse(on.contains("unused"));
        assertThrows(IllegalArgumentException.class,
                () -> ElliottResearchReport.summaryMarkdown("Run", coverage, List.of(available), Set.of("d2"), " "));
    }

    @Test
    void coverageCsvQuotesMessages(@TempDir final Path directory) {
        final Path file = directory.resolve("coverage.csv");

        ElliottResearchReport.writeCoverage(file, List.of(new CoverageRow("d1", "A", "2020-01-01", "2020-12-31", null,
                null, 0, "failed", "source, \"unreadable\"")));

        assertEquals("dataset,asset,requestedFrom,requestedTo,effectiveFrom,effectiveTo,bars,status,message\r\n"
                + "d1,A,2020-01-01,2020-12-31,,,0,failed,\"source, \"\"unreadable\"\"\"\r\n", read(file));
        assertThrows(IllegalArgumentException.class, () -> new CoverageRow("d", "A", "", "", "", "", 0, "done", ""));
    }

    private static Row find(final List<Row> rows, final String section, final String mode, final String partition,
            final String metric, final int blockLength) {
        return rows.stream()
                .filter(row -> row.section().equals(section) && row.mode().equals(mode)
                        && row.partition().equals(partition) && row.metric().equals(metric)
                        && row.nullBlockLength() == blockLength)
                .findFirst()
                .orElseThrow();
    }

    private static String read(final Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (final IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static NullMemberMetrics member(final int memberIndex, final String partition, final double occupancy) {
        final long evaluations = Double.isNaN(occupancy) ? 0 : 4;
        final long complete = Double.isNaN(occupancy) ? 0 : Math.round(occupancy * 4);
        final PartitionMetrics metrics = new PartitionMetrics(partition, 0, 3, evaluations, complete, 0, 0,
                evaluations - complete, 0, 0, evaluations == 0 ? Double.NaN : (double) complete / evaluations,
                evaluations == 0 ? Double.NaN : 0.0d,
                evaluations == 0 ? Double.NaN : (double) (evaluations - complete) / evaluations, Double.NaN, Double.NaN,
                0, 0, 0, 0, 0, 0, Double.NaN, 0, 0, Double.NaN, List.of());
        return new NullMemberMetrics(memberIndex, partition, List.of(metrics));
    }

    private static List<TopologyGrammar> grammars() {
        return List.of(TopologyGrammar.MOTIVE_5, TopologyGrammar.CORRECTIVE_3, TopologyGrammar.CYCLE_5_3);
    }

    private static List<RelationshipRule> rules() {
        final List<RelationshipRule> rules = new ArrayList<>();
        for (final String id : List.of("first", "second")) {
            rules.add(new RelationshipRule() {
                @Override
                public String id() {
                    return id;
                }

                @Override
                public RuleEvidence evaluate(final TopologyCandidate candidate) {
                    return RuleEvidence.pass(id, List.of("synthetic"), "synthetic pass");
                }
            });
        }
        return rules;
    }

    private static StudyRunner.Configuration configuration() {
        return new StudyRunner.Configuration(StudyRunner.Partitions.lockedDefault(),
                "b92d667cdbf951aac8d0519006a31e097bc88d26e399b04dd9a89e6353729100", SEED, List.of(2), 2,
                List.of(new DetectorRobustnessMatrix.DetectorSpec("synthetic",
                        ElliottResearchReportTest::detectorFactory)),
                "synthetic-primary", null);
    }

    private static SwingDetector detectorFactory() {
        return (series, index, degree) -> {
            final int[] pivotIndices = { 1, 3, 5, 7, 9, 11, 13, 15, 17 };
            final List<SwingPivot> pivots = new ArrayList<>();
            for (final int pivotIndex : pivotIndices) {
                if (pivotIndex >= series.getBeginIndex() && pivotIndex <= index && pivotIndex <= series.getEndIndex()) {
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

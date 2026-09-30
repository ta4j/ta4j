/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import org.ta4j.core.analysis.elliott.StudyReport.PartitionMetrics;

/**
 * Compact comparison rows, null reference summaries, CSV and Markdown output
 * for one Elliott research run.
 *
 * <p>
 * Every row compares one observed {@link StudyReport} metric of one partition
 * with the member-level values of the matching null ensemble of the same
 * partition. Null summaries are a non-inferential reference band and never a
 * confidence interval for the observed effect; the reported rank is an
 * empirical reference rank and never an exact bootstrap p-value.
 * </p>
 *
 * @since 0.25.1
 */
final class ElliottResearchReport {

    static final String AVAILABLE = "available";
    static final String UNAVAILABLE = "unavailable";
    static final String NOT_COMPARABLE = "not-comparable";

    private static final double LOW_QUANTILE = 0.025d;
    private static final double MEDIAN_QUANTILE = 0.5d;
    private static final double HIGH_QUANTILE = 0.975d;
    private static final double RANK_EPSILON = 1.0e-9d;
    private static final MathContext DISPLAY_PRECISION = new MathContext(4);
    private static final String EVIDENCE_NOT_CAPTURED = "evidence not captured";
    private static final Set<String> AVAILABILITIES = Set.of(AVAILABLE, UNAVAILABLE, NOT_COMPARABLE);
    private static final List<String> ROW_HEADER = List.of("key", "dataset", "asset", "section", "mode", "grammar",
            "activeRules", "detector", "policy", "partition", "metric", "nullBlockLength", "observed", "numerator",
            "denominator", "validNullMembers", "requestedNullMembers", "unavailableNullMembers", "nullMedian",
            "nullLow", "nullHigh", "observedMinusNullMedian", "empiricalReferenceRank", "availability");
    private static final List<String> COVERAGE_HEADER = List.of("dataset", "asset", "requestedFrom", "requestedTo",
            "effectiveFrom", "effectiveTo", "bars", "status", "message");
    private static final Set<String> COVERAGE_STATUSES = Set.of("complete", "partial", "failed");

    private ElliottResearchReport() {
    }

    /**
     * Report metrics that become comparison rows; {@code matchRate} is
     * intentionally absent because the study reports occupancy, not matches.
     */
    enum Metric {
        COMPLETE_OCCUPANCY("completeOccupancyRate", false, PartitionMetrics::completeOccupancyRate,
                PartitionMetrics::completeCount, PartitionMetrics::evaluationCount),
        AMBIGUOUS("ambiguousRate", false, PartitionMetrics::ambiguousRate, PartitionMetrics::ambiguousCount,
                PartitionMetrics::evaluationCount),
        NO_MATCH("noMatchRate", false, PartitionMetrics::noMatchRate, PartitionMetrics::noMatchCount,
                PartitionMetrics::evaluationCount),
        CONFIRMATION_LAG("confirmationLagBars", false, PartitionMetrics::confirmationLagBars, null, null),
        LABEL_STABILITY("labelStabilityJaccard", false, PartitionMetrics::labelStabilityJaccard, null, null),
        EVIDENCE_PASS("evidencePassRate", true, PartitionMetrics::evidencePassRate, PartitionMetrics::evidencePassCount,
                PartitionMetrics::evidenceEvaluationCount),
        JOINT_PASS("jointPassRate", true, PartitionMetrics::jointPassRate, PartitionMetrics::jointPassCount,
                PartitionMetrics::jointEvaluationCount);

        private final String id;
        private final boolean evidence;
        private final Function<PartitionMetrics, Double> value;
        private final Function<PartitionMetrics, Long> numerator;
        private final Function<PartitionMetrics, Long> denominator;

        Metric(final String id, final boolean evidence, final Function<PartitionMetrics, Double> value,
                final Function<PartitionMetrics, Long> numerator, final Function<PartitionMetrics, Long> denominator) {
            this.id = id;
            this.evidence = evidence;
            this.value = value;
            this.numerator = numerator;
            this.denominator = denominator;
        }

        String id() {
            return id;
        }

        double value(final PartitionMetrics metrics) {
            return value.apply(metrics);
        }

        /** @return numerator support, or {@code null} when the report has none */
        Long numerator(final PartitionMetrics metrics) {
            return numerator == null ? null : numerator.apply(metrics);
        }

        /** @return denominator support, or {@code null} when the report has none */
        Long denominator(final PartitionMetrics metrics) {
            return denominator == null ? null : denominator.apply(metrics);
        }
    }

    /**
     * Parsed stable comparison key.
     *
     * @param nullBlockLength null block length, or {@code -1} when not applicable
     */
    record Key(String dataset, String section, String mode, String detector, String partition, String metric,
            int nullBlockLength) {

        Key {
            requireKeyPart(dataset, "dataset");
            requireKeyPart(section, "section");
            requireKeyPart(mode, "mode");
            requireKeyPart(detector, "detector");
            requireKeyPart(partition, "partition");
            requireKeyPart(metric, "metric");
            if (nullBlockLength < -1 || nullBlockLength == 0) {
                throw new IllegalArgumentException("nullBlockLength must be positive or -1, was " + nullBlockLength);
            }
        }

        @Override
        public String toString() {
            return String.join("|", dataset, section, mode, detector, partition, metric,
                    nullBlockLength < 0 ? "b-" : "b" + nullBlockLength);
        }
    }

    /**
     * One compact comparison of an observed metric against its null reference.
     *
     * @param numerator   observed numerator, or {@code null} when not reported
     * @param denominator observed support, or {@code null} when not reported
     */
    record Row(String dataset, String asset, String section, String mode, String grammar, List<String> activeRules,
            String detector, String policy, String partition, String metric, int nullBlockLength, double observed,
            Long numerator, Long denominator, int validNullMembers, int requestedNullMembers,
            int unavailableNullMembers, double nullMedian, double nullLow, double nullHigh,
            double observedMinusNullMedian, double empiricalReferenceRank, String availability) {

        Row {
            requireText(asset, "asset");
            requireText(grammar, "grammar");
            requireText(policy, "policy");
            activeRules = List.copyOf(activeRules);
            for (final String rule : activeRules) {
                if (rule.isBlank() || rule.contains(";")) {
                    throw new IllegalArgumentException("active rule ids must be non-blank without ';': " + rule);
                }
            }
            if (!AVAILABILITIES.contains(availability)) {
                throw new IllegalArgumentException("unknown availability: " + availability);
            }
            new Key(dataset, section, mode, detector, partition, metric, nullBlockLength);
        }

        /** @return stable inspection key of this row */
        String key() {
            return new Key(dataset, section, mode, detector, partition, metric, nullBlockLength).toString();
        }
    }

    /**
     * Dataset coverage entry.
     *
     * @param requestedFrom ISO-8601 requested start, blank when unknown
     * @param requestedTo   ISO-8601 requested end, blank when unknown
     * @param effectiveFrom ISO-8601 effective start, blank when unknown
     * @param effectiveTo   ISO-8601 effective end, blank when unknown
     * @param status        {@code complete}, {@code partial} or {@code failed}
     */
    record CoverageRow(String dataset, String asset, String requestedFrom, String requestedTo, String effectiveFrom,
            String effectiveTo, int bars, String status, String message) {

        CoverageRow {
            requireText(dataset, "dataset");
            requireText(asset, "asset");
            requestedFrom = requestedFrom == null ? "" : requestedFrom;
            requestedTo = requestedTo == null ? "" : requestedTo;
            effectiveFrom = effectiveFrom == null ? "" : effectiveFrom;
            effectiveTo = effectiveTo == null ? "" : effectiveTo;
            message = message == null ? "" : message;
            if (bars < 0) {
                throw new IllegalArgumentException("bars must not be negative");
            }
            if (!COVERAGE_STATUSES.contains(status)) {
                throw new IllegalArgumentException("unknown coverage status: " + status);
            }
        }
    }

    /**
     * Member-level null reference summary.
     *
     * @param requested   configured ensemble size B
     * @param valid       members with a finite value
     * @param unavailable requested members without a finite value
     * @param median      nearest-rank median, NaN when {@code valid == 0}
     * @param low         nearest-rank 2.5% quantile, NaN when {@code valid == 0}
     * @param high        nearest-rank 97.5% quantile, NaN when {@code valid == 0}
     */
    record NullReference(int requested, int valid, int unavailable, double median, double low, double high) {

        boolean available() {
            return valid > 0;
        }
    }

    /**
     * Difference of two modes for one generated null member.
     *
     * @param partition   partition both members belong to
     * @param memberIndex generated member index within that partition
     * @param difference  minuend minus subtrahend, NaN when either is undefined
     */
    record PairedDifference(String partition, int memberIndex, double difference) {
    }

    private record NullSource(int blockLength, int requested, List<StudyReport.NullMemberMetrics> members) {
    }

    /**
     * Parses a comparison key produced by {@link Row#key()}.
     *
     * @param text key text
     * @return the seven key parts
     * @throws IllegalArgumentException with the expected format when malformed
     */
    static Key parseKey(final String text) {
        final String expected = "expected dataset|section|mode|detector|partition|metric|b<blockLength> "
                + "(seven non-blank '|'-separated parts, last 'b-' or 'b<positive integer>')";
        if (text == null) {
            throw new IllegalArgumentException("Malformed comparison key: null; " + expected);
        }
        final String[] parts = text.split("\\|", -1);
        if (parts.length != 7) {
            throw new IllegalArgumentException(
                    "Malformed comparison key '" + text + "': found " + parts.length + " part(s); " + expected);
        }
        for (final String part : parts) {
            if (part.isBlank()) {
                throw new IllegalArgumentException("Malformed comparison key '" + text + "': blank part; " + expected);
            }
        }
        final String block = parts[6];
        final int blockLength;
        if ("b-".equals(block)) {
            blockLength = -1;
        } else if (block.matches("b[1-9][0-9]{0,8}")) {
            blockLength = Integer.parseInt(block.substring(1));
        } else {
            throw new IllegalArgumentException(
                    "Malformed comparison key '" + text + "': bad block token '" + block + "'; " + expected);
        }
        return new Key(parts[0], parts[1], parts[2], parts[3], parts[4], parts[5], blockLength);
    }

    /**
     * Builds the comparison rows of one dataset report.
     *
     * @param datasetId dataset identifier used in keys and file names
     * @param report    study report
     * @return rows with unique keys in report order
     */
    static List<Row> comparisons(final String datasetId, final StudyReport report) {
        Objects.requireNonNull(report, "report");
        final String asset = report.assetId();
        final String detector = report.primaryDetector();
        final List<Row> rows = new ArrayList<>();
        for (final StudyReport.ModeReport mode : report.h1().modes()) {
            rows.addAll(modeRows(datasetId, asset, "h1", mode, detector, grammarPolicy(mode),
                    grammarSources(report, mode.grammar())));
        }
        for (final StudyReport.ModeReport mode : report.h2().modes()) {
            rows.addAll(modeRows(datasetId, asset, "h2", mode, detector,
                    mode.activeRuleIds().isEmpty() ? "kernel-topology" : "kernel-topology+evidence",
                    modeSources(report, mode)));
        }
        for (final StudyReport.ModeReport mode : report.competingGrammars()) {
            rows.addAll(modeRows(datasetId, asset, "competing", mode, detector, grammarPolicy(mode),
                    grammarSources(report, mode.grammar())));
        }
        for (final StudyReport.DetectorResult result : report.robustness().detectors()) {
            rows.addAll(modeRows(datasetId, asset, "robustness", result.mode(), result.name(), "robustness-detector",
                    List.of()));
        }
        final Set<String> keys = new HashSet<>();
        for (final Row row : rows) {
            if (!keys.add(row.key())) {
                throw new IllegalStateException("Duplicate comparison key: " + row.key());
            }
        }
        return List.copyOf(rows);
    }

    private static String grammarPolicy(final StudyReport.ModeReport mode) {
        final boolean kernel = Arrays.stream(TopologyGrammar.values())
                .anyMatch(grammar -> grammar.name().equals(mode.grammar()));
        return kernel ? "kernel-topology" : "unmatched-recognizer";
    }

    private static List<NullSource> grammarSources(final StudyReport report, final String grammar) {
        final List<NullSource> sources = new ArrayList<>();
        for (final StudyReport.NullReport nullReport : report.nulls()) {
            if (nullReport.grammar().equals(grammar)) {
                sources.add(new NullSource(nullReport.blockLength(), nullReport.ensembleSize(), nullReport.members()));
            }
        }
        return sources;
    }

    private static List<NullSource> modeSources(final StudyReport report, final StudyReport.ModeReport mode) {
        final List<NullSource> sources = new ArrayList<>();
        for (final StudyReport.NullReport nullReport : report.nulls()) {
            if (!nullReport.grammar().equals(mode.grammar())) {
                continue;
            }
            for (final StudyReport.NullModeReport nullMode : nullReport.modes()) {
                if (nullMode.mode().equals(mode.mode())) {
                    sources.add(
                            new NullSource(nullReport.blockLength(), nullReport.ensembleSize(), nullMode.members()));
                }
            }
        }
        return sources;
    }

    private static List<Row> modeRows(final String datasetId, final String asset, final String section,
            final StudyReport.ModeReport mode, final String detector, final String policy,
            final List<NullSource> sources) {
        final boolean withEvidence = !mode.activeRuleIds().isEmpty();
        final List<Row> rows = new ArrayList<>();
        for (final PartitionMetrics partition : mode.partitions()) {
            for (final Metric metric : Metric.values()) {
                if (metric.evidence && !withEvidence) {
                    continue;
                }
                final double observed = metric.value(partition);
                final Long numerator = metric.numerator(partition);
                final Long denominator = metric.denominator(partition);
                if (sources.isEmpty()) {
                    rows.add(new Row(datasetId, asset, section, mode.mode(), mode.grammar(), mode.activeRuleIds(),
                            detector, policy, partition.partition(), metric.id(), -1, observed, numerator, denominator,
                            0, 0, 0, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                            Double.isFinite(observed) ? NOT_COMPARABLE : UNAVAILABLE));
                    continue;
                }
                for (final NullSource source : sources) {
                    final double[] memberValues = source.members()
                            .stream()
                            .filter(member -> member.partition().equals(partition.partition()))
                            .mapToDouble(member -> metric.value(member.partitions().get(0)))
                            .toArray();
                    final NullReference reference = summarize(memberValues, source.requested());
                    final boolean comparable = Double.isFinite(observed) && reference.available();
                    rows.add(new Row(datasetId, asset, section, mode.mode(), mode.grammar(), mode.activeRuleIds(),
                            detector, policy, partition.partition(), metric.id(), source.blockLength(), observed,
                            numerator, denominator, reference.valid(), reference.requested(), reference.unavailable(),
                            reference.median(), reference.low(), reference.high(),
                            comparable ? observed - reference.median() : Double.NaN,
                            comparable ? referenceRank(observed, memberValues) : Double.NaN,
                            comparable ? AVAILABLE : UNAVAILABLE));
                }
            }
        }
        return rows;
    }

    /**
     * Summarizes member-level null values with equal weight per finite member.
     * Quantiles are nearest rank {@code x[ceil(q * B) - 1]} over the sorted finite
     * values, where B is the finite member count; no existing project helper uses
     * that convention.
     *
     * @param memberValues one value per generated member; non-finite is undefined
     * @param requested    configured ensemble size
     * @return counts plus median and 2.5%/97.5% reference quantiles
     */
    static NullReference summarize(final double[] memberValues, final int requested) {
        final double[] finite = Arrays.stream(memberValues).filter(Double::isFinite).sorted().toArray();
        if (memberValues.length > requested) {
            throw new IllegalArgumentException(
                    "member values (" + memberValues.length + ") exceed requested members (" + requested + ")");
        }
        final int valid = finite.length;
        if (valid == 0) {
            return new NullReference(requested, 0, requested, Double.NaN, Double.NaN, Double.NaN);
        }
        return new NullReference(requested, valid, requested - valid, nearestRank(finite, MEDIAN_QUANTILE),
                nearestRank(finite, LOW_QUANTILE), nearestRank(finite, HIGH_QUANTILE));
    }

    /**
     * Upper-tail empirical reference rank {@code (1 + count(null >= observed)) /
     * (B + 1)} over finite members; not an exact bootstrap p-value.
     *
     * @param observed     observed value
     * @param memberValues one value per generated member; non-finite is ignored
     * @return rank, or NaN when the observed value or every member is undefined
     */
    static double referenceRank(final double observed, final double[] memberValues) {
        if (!Double.isFinite(observed)) {
            return Double.NaN;
        }
        int valid = 0;
        int atLeast = 0;
        for (final double value : memberValues) {
            if (Double.isFinite(value)) {
                valid++;
                if (value >= observed) {
                    atLeast++;
                }
            }
        }
        return valid == 0 ? Double.NaN : (1.0d + atLeast) / (valid + 1.0d);
    }

    private static double nearestRank(final double[] sorted, final double quantile) {
        final int rank = (int) Math.ceil(quantile * sorted.length - RANK_EPSILON);
        return sorted[Math.min(sorted.length, Math.max(1, rank)) - 1];
    }

    /**
     * Pairs two null-mode member lists per generated member and partition. A member
     * is only paired with the member of the same index in the same partition;
     * matching member numbers of different partition tapes are never a pair.
     *
     * @param minuend    members of the first mode
     * @param subtrahend members of the second mode
     * @param metric     metric to difference
     * @return one difference per pair in minuend order
     * @throws IllegalArgumentException when a member has no same-partition
     *                                  counterpart or a list repeats a member
     */
    static List<PairedDifference> pairedDifferences(final List<StudyReport.NullMemberMetrics> minuend,
            final List<StudyReport.NullMemberMetrics> subtrahend, final Metric metric) {
        final Map<String, StudyReport.NullMemberMetrics> counterparts = new HashMap<>();
        for (final StudyReport.NullMemberMetrics member : subtrahend) {
            if (counterparts.put(memberId(member), member) != null) {
                throw new IllegalArgumentException("Repeated null member " + memberId(member) + " in subtrahend");
            }
        }
        final Set<String> paired = new HashSet<>();
        final List<PairedDifference> differences = new ArrayList<>(minuend.size());
        for (final StudyReport.NullMemberMetrics member : minuend) {
            final StudyReport.NullMemberMetrics counterpart = counterparts.get(memberId(member));
            if (counterpart == null || !paired.add(memberId(member))) {
                throw new IllegalArgumentException("Null member " + memberId(member)
                        + " has no unique counterpart in the same partition; cross-partition pairing is refused");
            }
            final double left = metric.value(member.partitions().get(0));
            final double right = metric.value(counterpart.partitions().get(0));
            differences.add(new PairedDifference(member.partition(), member.memberIndex(),
                    Double.isFinite(left) && Double.isFinite(right) ? left - right : Double.NaN));
        }
        if (paired.size() != counterparts.size()) {
            throw new IllegalArgumentException(
                    "Subtrahend contains null members without a same-partition counterpart in the minuend");
        }
        return List.copyOf(differences);
    }

    private static String memberId(final StudyReport.NullMemberMetrics member) {
        return member.partition() + "#" + member.memberIndex();
    }

    /**
     * Writes comparison rows as RFC 4180 CSV. Numbers are plain decimal text;
     * undefined values and inapplicable block lengths are empty cells.
     *
     * @param path destination file; parent directories are created
     * @param rows rows to write
     */
    static void writeCsv(final Path path, final List<Row> rows) {
        final List<List<String>> table = new ArrayList<>(rows.size());
        for (final Row row : rows) {
            table.add(List.of(row.key(), row.dataset(), row.asset(), row.section(), row.mode(), row.grammar(),
                    String.join(";", row.activeRules()), row.detector(), row.policy(), row.partition(), row.metric(),
                    row.nullBlockLength() < 0 ? "" : Integer.toString(row.nullBlockLength()), plain(row.observed()),
                    row.numerator() == null ? "" : row.numerator().toString(),
                    row.denominator() == null ? "" : row.denominator().toString(),
                    count(row.validNullMembers(), row.requestedNullMembers()),
                    count(row.requestedNullMembers(), row.requestedNullMembers()),
                    count(row.unavailableNullMembers(), row.requestedNullMembers()), plain(row.nullMedian()),
                    plain(row.nullLow()), plain(row.nullHigh()), plain(row.observedMinusNullMedian()),
                    plain(row.empiricalReferenceRank()), row.availability()));
        }
        writeTable(path, ROW_HEADER, table);
    }

    /**
     * Reads rows written by {@link #writeCsv(Path, List)}.
     *
     * @param path CSV file
     * @return rows equal to the written rows
     * @throws IllegalArgumentException naming file and line when malformed
     */
    static List<Row> readCsv(final Path path) {
        final List<List<String>> table = parseCsv(path);
        if (table.isEmpty() || !table.get(0).equals(ROW_HEADER)) {
            throw new IllegalArgumentException(path + ":1: unexpected header, expected " + ROW_HEADER);
        }
        final List<Row> rows = new ArrayList<>(table.size() - 1);
        for (int line = 1; line < table.size(); line++) {
            final List<String> cells = table.get(line);
            final String where = path + ":" + (line + 1);
            if (cells.size() != ROW_HEADER.size()) {
                throw new IllegalArgumentException(
                        where + ": expected " + ROW_HEADER.size() + " cells but found " + cells.size());
            }
            try {
                final Row row = new Row(cells.get(1), cells.get(2), cells.get(3), cells.get(4), cells.get(5),
                        cells.get(6).isEmpty() ? List.of() : List.of(cells.get(6).split(";", -1)), cells.get(7),
                        cells.get(8), cells.get(9), cells.get(10),
                        cells.get(11).isEmpty() ? -1 : Integer.parseInt(cells.get(11)), number(cells.get(12)),
                        cells.get(13).isEmpty() ? null : Long.valueOf(cells.get(13)),
                        cells.get(14).isEmpty() ? null : Long.valueOf(cells.get(14)),
                        Integer.parseInt(cells.get(15).isEmpty() ? "0" : cells.get(15)),
                        Integer.parseInt(cells.get(16).isEmpty() ? "0" : cells.get(16)),
                        Integer.parseInt(cells.get(17).isEmpty() ? "0" : cells.get(17)), number(cells.get(18)),
                        number(cells.get(19)), number(cells.get(20)), number(cells.get(21)), number(cells.get(22)),
                        cells.get(23));
                if (!row.key().equals(cells.get(0))) {
                    throw new IllegalArgumentException("key column '" + cells.get(0) + "' does not match row fields");
                }
                rows.add(row);
            } catch (final IllegalArgumentException exception) {
                throw new IllegalArgumentException(where + ": " + exception.getMessage(), exception);
            }
        }
        return List.copyOf(rows);
    }

    /**
     * Writes the dataset coverage table as RFC 4180 CSV.
     *
     * @param path     destination file; parent directories are created
     * @param coverage coverage entries
     */
    static void writeCoverage(final Path path, final List<CoverageRow> coverage) {
        final List<List<String>> table = new ArrayList<>(coverage.size());
        for (final CoverageRow row : coverage) {
            table.add(List.of(row.dataset(), row.asset(), row.requestedFrom(), row.requestedTo(), row.effectiveFrom(),
                    row.effectiveTo(), Integer.toString(row.bars()), row.status(), row.message()));
        }
        writeTable(path, COVERAGE_HEADER, table);
    }

    /**
     * Renders the compact run summary.
     *
     * @param title            document title
     * @param coverage         dataset coverage entries
     * @param rows             comparison rows of all datasets
     * @param traceCaptured    whether the observation trace was captured
     * @param recaptureCommand exact command that captures the trace; required when
     *                         {@code traceCaptured} is false
     * @return Markdown text
     */
    static String summaryMarkdown(final String title, final List<CoverageRow> coverage, final List<Row> rows,
            final boolean traceCaptured, final String recaptureCommand) {
        if (!traceCaptured && (recaptureCommand == null || recaptureCommand.isBlank())) {
            throw new IllegalArgumentException("recaptureCommand is required when the trace was not captured");
        }
        final StringBuilder markdown = new StringBuilder();
        markdown.append("# ").append(title).append("\n\n");
        markdown.append("- The null band is a non-inferential reference band (member-level nearest-rank 2.5%-97.5% ")
                .append("quantiles, equal weight per finite member); it is not a confidence interval.\n");
        markdown.append("- The rank is an empirical reference rank, (1 + count(null >= observed)) / (valid + 1); ")
                .append("it is not a p-value.\n");
        markdown.append("- Rows are per partition; null members of different partitions are never pooled.\n\n");
        markdown.append("## Coverage\n\n");
        markdown.append("| dataset | asset | requested | effective | bars | status | message |\n");
        markdown.append("|---|---|---|---|---|---|---|\n");
        for (final CoverageRow row : coverage) {
            markdown.append("| ")
                    .append(cell(row.dataset()))
                    .append(" | ")
                    .append(cell(row.asset()))
                    .append(" | ")
                    .append(cell(range(row.requestedFrom(), row.requestedTo())))
                    .append(" | ")
                    .append(cell(range(row.effectiveFrom(), row.effectiveTo())))
                    .append(" | ")
                    .append(row.bars())
                    .append(" | ")
                    .append(row.status())
                    .append(" | ")
                    .append(cell(row.message()))
                    .append(" |\n");
        }
        final Map<String, List<Row>> byDataset = new LinkedHashMap<>();
        for (final Row row : rows) {
            byDataset.computeIfAbsent(row.dataset(), ignored -> new ArrayList<>()).add(row);
        }
        markdown.append("\n## Comparisons\n");
        for (final Map.Entry<String, List<Row>> entry : byDataset.entrySet()) {
            final List<Row> available = entry.getValue()
                    .stream()
                    .filter(row -> AVAILABLE.equals(row.availability()))
                    .toList();
            markdown.append("\n### ").append(entry.getKey()).append("\n\n");
            markdown.append("| key | observed | n/d | null median | band 2.5%-97.5% | obs - median | rank | ")
                    .append("valid/requested | evidence |\n");
            markdown.append("|---|---|---|---|---|---|---|---|---|\n");
            for (final Row row : available) {
                markdown.append("| `")
                        .append(row.key().replace("|", "\\|"))
                        .append("` | ")
                        .append(display(row.observed()))
                        .append(" | ")
                        .append(support(row))
                        .append(" | ")
                        .append(display(row.nullMedian()))
                        .append(" | ")
                        .append(display(row.nullLow()))
                        .append(" - ")
                        .append(display(row.nullHigh()))
                        .append(" | ")
                        .append(display(row.observedMinusNullMedian()))
                        .append(" | ")
                        .append(display(row.empiricalReferenceRank()))
                        .append(" | ")
                        .append(row.validNullMembers())
                        .append("/")
                        .append(row.requestedNullMembers())
                        .append(" | ")
                        .append(traceCaptured ? "trace captured" : EVIDENCE_NOT_CAPTURED)
                        .append(" |\n");
            }
            final int others = entry.getValue().size() - available.size();
            if (others > 0) {
                markdown.append("\n")
                        .append(others)
                        .append(" unavailable or not-comparable row(s) omitted; see comparisons.csv.\n");
            }
        }
        if (!traceCaptured) {
            markdown.append("\n## Evidence\n\nEvidence not captured for these rows. Recapture with:\n\n```\n")
                    .append(recaptureCommand)
                    .append("\n```\n");
        }
        return markdown.toString();
    }

    private static String support(final Row row) {
        return row.numerator() == null || row.denominator() == null ? "-" : row.numerator() + "/" + row.denominator();
    }

    private static String range(final String from, final String to) {
        return from.isEmpty() && to.isEmpty() ? "" : from + " to " + to;
    }

    private static String display(final double value) {
        return Double.isFinite(value)
                ? BigDecimal.valueOf(value).round(DISPLAY_PRECISION).stripTrailingZeros().toPlainString()
                : "-";
    }

    private static String cell(final String text) {
        return text.replace("|", "\\|").replace("\r", " ").replace("\n", " ");
    }

    private static String plain(final double value) {
        if (!Double.isFinite(value)) {
            return "";
        }
        return value == 0.0d ? "0" : BigDecimal.valueOf(value).toPlainString();
    }

    private static String count(final int value, final int requested) {
        return requested == 0 ? "" : Integer.toString(value);
    }

    private static double number(final String cell) {
        return cell.isEmpty() ? Double.NaN : Double.parseDouble(cell);
    }

    private static void writeTable(final Path path, final List<String> header, final List<List<String>> table) {
        try {
            final Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                writeLine(writer, header);
                for (final List<String> cells : table) {
                    writeLine(writer, cells);
                }
            }
        } catch (final IOException exception) {
            throw new UncheckedIOException("Unable to write " + path, exception);
        }
    }

    private static void writeLine(final BufferedWriter writer, final List<String> cells) throws IOException {
        for (int index = 0; index < cells.size(); index++) {
            if (index > 0) {
                writer.write(',');
            }
            final String cell = cells.get(index);
            if (cell.indexOf(',') >= 0 || cell.indexOf('"') >= 0 || cell.indexOf('\r') >= 0
                    || cell.indexOf('\n') >= 0) {
                writer.write('"');
                writer.write(cell.replace("\"", "\"\""));
                writer.write('"');
            } else {
                writer.write(cell);
            }
        }
        writer.write("\r\n");
    }

    private static List<List<String>> parseCsv(final Path path) {
        final String text;
        try {
            text = Files.readString(path, StandardCharsets.UTF_8);
        } catch (final IOException exception) {
            throw new UncheckedIOException("Unable to read " + path, exception);
        }
        final List<List<String>> table = new ArrayList<>();
        List<String> cells = new ArrayList<>();
        final StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        boolean pending = false;
        int line = 1;
        for (int index = 0; index < text.length(); index++) {
            final char c = text.charAt(index);
            if (quoted) {
                if (c == '"' && index + 1 < text.length() && text.charAt(index + 1) == '"') {
                    cell.append('"');
                    index++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cell.append(c);
                    if (c == '\n') {
                        line++;
                    }
                }
            } else if (c == '"' && cell.length() == 0) {
                quoted = true;
                pending = true;
            } else if (c == ',') {
                cells.add(cell.toString());
                cell.setLength(0);
                pending = true;
            } else if (c == '\r' || c == '\n') {
                if (c == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') {
                    index++;
                }
                cells.add(cell.toString());
                table.add(cells);
                cells = new ArrayList<>();
                cell.setLength(0);
                pending = false;
                line++;
            } else {
                cell.append(c);
                pending = true;
            }
        }
        if (quoted) {
            throw new IllegalArgumentException(path + ":" + line + ": unterminated quoted cell");
        }
        if (pending || cell.length() > 0) {
            cells.add(cell.toString());
            table.add(cells);
        }
        return table;
    }

    private static void requireKeyPart(final String value, final String name) {
        requireText(value, name);
        if (value.indexOf('|') >= 0) {
            throw new IllegalArgumentException(name + " must not contain '|' (comparison key separator): " + value);
        }
    }

    private static void requireText(final String value, final String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}

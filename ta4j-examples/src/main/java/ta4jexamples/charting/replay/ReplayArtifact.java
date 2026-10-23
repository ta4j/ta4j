/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.charting.replay;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * Read-only view of one {@code elliott-research-run/1} directory.
 *
 * <p>
 * Every path is resolved against the run directory, so a moved bundle keeps
 * working. The adapter reads {@code run.json}, {@code comparisons.csv}, the
 * retained price-bar sidecar and the real trace; it never recomputes an Elliott
 * count and never fetches data.
 */
final class ReplayArtifact {

    static final String RUN_SCHEMA = "elliott-research-run/1";
    static final String TRACE_SCHEMA = "elliott-research-trace/1";
    static final String BARS_HEADER = "index,beginTime,endTime,open,high,low,close,volume";
    static final String TRACE_MODE_REAL = "real";
    static final String TRACE_MODE_NULL_MEMBER = "selected-null-member";

    private static final List<String> RATE_METRICS = List.of("completeOccupancyRate", "ambiguousRate", "noMatchRate",
            "evidencePassRate", "jointPassRate");
    private static final List<String> TOPOLOGY_METRICS = List.of("completeOccupancyRate", "ambiguousRate",
            "noMatchRate", "confirmationLagBars", "labelStabilityJaccard");
    private static final List<String> COMPARISON_METRICS = List.of("completeOccupancyRate", "ambiguousRate",
            "noMatchRate", "confirmationLagBars", "labelStabilityJaccard", "evidencePassRate", "jointPassRate");
    private static final List<String> CLASSICAL_RULE_IDS = List.of("wave2-origin", "wave3-not-shortest",
            "wave4-nonoverlap", "wave5-divergence");
    private static final List<String> NULL_REFERENCE_COLUMNS = List.of("validNullMembers", "requestedNullMembers",
            "unavailableNullMembers", "nullMedian", "nullLow", "nullHigh", "observedMinusNullMedian",
            "empiricalReferenceRank");

    private static final String REGENERATE = "Regenerate the run with ElliottResearch (see the Elliott research section of ta4j-core/README.md), e.g. `run smoke --out <dir> --overwrite` or the original recipe with --trace real.";

    /**
     * One row of {@code comparisons.csv}, carrying the identity and metric
     * contributions the replay validates against the captured real scope.
     */
    record ComparisonRow(String key, String dataset, String asset, String section, String mode, String grammar,
            List<String> activeRules, String detector, String partition, String metric, int nullBlockLength,
            double observed, Long numerator, Long denominator) {

        ReplayTraceIndex.Family family() {
            return new ReplayTraceIndex.Family(section, mode, grammar, detector, partition);
        }
    }

    /** One dataset entry of {@code run.json}. */
    record Dataset(String id, String asset, String coverageStatus, String coverageMessage, List<String> traces,
            String barsPath, String barsSha256, int barsRows, String sourceSha256, Map<String, String> traceSha256) {
    }

    /** One retained source bar. */
    record PriceBar(int index, Instant begin, Instant end, String open, String high, String low, String close,
            String volume) {
    }

    private final Path directory;
    private final JsonObject run;
    private final Map<String, Dataset> datasets = new HashMap<>();
    private final List<ComparisonRow> comparisons;
    private final Map<String, List<PriceBar>> barsByDataset = new HashMap<>();

    private ReplayArtifact(final Path directory, final JsonObject run, final List<ComparisonRow> comparisons) {
        this.directory = directory;
        this.run = run;
        this.comparisons = comparisons;
    }

    /**
     * Opens and validates a run directory.
     *
     * @param directory the run directory
     * @return the artifact view
     */
    static ReplayArtifact open(final Path directory) {
        Objects.requireNonNull(directory, "directory");
        final Path dir = directory.toAbsolutePath().normalize();
        final Path runFile = dir.resolve("run.json");
        if (!Files.isRegularFile(runFile)) {
            throw new ReplayArtifactException(
                    "no run.json in " + dir + "; pass the run directory written by ElliottResearch. " + REGENERATE);
        }
        final JsonObject run = parseJson(runFile, "run.json");
        final String schema = text(run, "artifactSchemaVersion");
        if (!RUN_SCHEMA.equals(schema)) {
            throw new ReplayArtifactException("unsupported run schema '" + schema + "' in " + runFile
                    + "; this replay reads only '" + RUN_SCHEMA + "'. " + REGENERATE);
        }
        if (!"complete".equals(text(run, "status"))) {
            throw new ReplayArtifactException("run in " + dir + " has status '" + text(run, "status")
                    + "', not 'complete'; replay needs a finished run. " + REGENERATE);
        }
        final Path csv = dir.resolve("comparisons.csv");
        if (!Files.isRegularFile(csv)) {
            throw new ReplayArtifactException(
                    "missing comparisons.csv in " + dir + "; comparison keys cannot be resolved. " + REGENERATE);
        }
        final ReplayArtifact artifact = new ReplayArtifact(dir, run, readComparisons(csv, run));
        artifact.fingerprint();
        final JsonElement datasets = run.get("datasets");
        if (datasets == null || !datasets.isJsonArray()) {
            throw new ReplayArtifactException("run.json in " + dir + " has no datasets array. " + REGENERATE);
        }
        final JsonArray array = datasets.getAsJsonArray();
        for (final JsonElement element : array) {
            if (!element.isJsonObject()) {
                throw new ReplayArtifactException(
                        "run.json in " + dir + " lists a dataset that is not an object. " + REGENERATE);
            }
            final Dataset dataset = artifact.parseDataset(element.getAsJsonObject());
            artifact.datasets.put(dataset.id(), dataset);
        }
        return artifact;
    }

    private Dataset parseDataset(final JsonObject object) {
        final List<String> traces = new ArrayList<>();
        final JsonElement recordedTraces = object.get("traces");
        if (recordedTraces != null && !recordedTraces.isJsonNull()) {
            if (!recordedTraces.isJsonArray()) {
                throw new ReplayArtifactException("artifact field traces must be an array of strings. " + REGENERATE);
            }
            for (final JsonElement trace : recordedTraces.getAsJsonArray()) {
                if (!trace.isJsonPrimitive() || !trace.getAsJsonPrimitive().isString()) {
                    throw new ReplayArtifactException("artifact field traces must contain only strings. " + REGENERATE);
                }
                traces.add(trace.getAsString());
            }
        }
        final JsonObject coverage = objectOrNull(object, "coverage");
        final JsonObject bars = objectOrNull(object, "priceBars");
        final JsonObject source = objectOrNull(object, "source");
        final Map<String, String> traceSha256 = new HashMap<>();
        final JsonObject recordedDigests = objectOrNull(object, "traceSha256");
        if (recordedDigests != null) {
            for (final String relative : recordedDigests.keySet()) {
                traceSha256.put(relative, text(recordedDigests, relative));
            }
        }
        return new Dataset(text(object, "id"), text(object, "asset"), text(coverage, "status"),
                text(coverage, "message"), List.copyOf(traces), bars == null ? null : text(bars, "path"),
                bars == null ? null : text(bars, "sha256"),
                bars == null || !bars.has("rows") ? -1 : integer(bars, "rows"),
                source == null || !source.has("sha256") || source.get("sha256").isJsonNull() ? null
                        : text(source, "sha256"),
                Map.copyOf(traceSha256));
    }

    Path directory() {
        return directory;
    }

    String fingerprint() {
        final String fingerprint = text(objectOrNull(run, "identity"), "fingerprint");
        if (fingerprint.isEmpty()) {
            throw new ReplayArtifactException(
                    "run.json in " + directory + " has no identity.fingerprint. " + REGENERATE);
        }
        return fingerprint;
    }

    String revision() {
        return text(run, "revision");
    }

    List<ComparisonRow> comparisons() {
        return comparisons;
    }

    /**
     * Resolves a comparison key exactly.
     *
     * @param key the {@code comparisons.csv} key
     * @return the row
     */
    ComparisonRow comparison(final String key) {
        return comparisons.stream()
                .filter(row -> row.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new ReplayArtifactException(unknownKeyMessage(key)));
    }

    private String unknownKeyMessage(final String key) {
        final List<String> close = comparisons.stream()
                .map(ComparisonRow::key)
                .sorted(Comparator.comparingInt((String candidate) -> -commonPrefix(candidate, key))
                        .thenComparing(Comparator.naturalOrder()))
                .limit(5)
                .toList();
        return "unknown comparison key '" + key + "' in " + directory.resolve("comparisons.csv") + "; closest keys:\n  "
                + String.join("\n  ", close);
    }

    private static int commonPrefix(final String left, final String right) {
        int length = 0;
        while (length < left.length() && length < right.length() && left.charAt(length) == right.charAt(length)) {
            length++;
        }
        return length;
    }

    /**
     * Suggests a comparison row of the all-rules ablation for the same detector and
     * partition, so a topology-only observation can point at recorded rule
     * evidence.
     *
     * @param row the row being replayed
     * @return a key whose trace carries rule evidence, if the run has one
     */
    Optional<ComparisonRow> ruleEvidenceRow(final ComparisonRow row) {
        return comparisons.stream()
                .filter(candidate -> candidate.dataset().equals(row.dataset())
                        && candidate.detector().equals(row.detector()) && candidate.partition().equals(row.partition())
                        && "h2".equals(candidate.section()) && "all-rules".equals(candidate.mode()))
                .findFirst();
    }

    Dataset dataset(final String id) {
        final Dataset dataset = datasets.get(id);
        if (dataset == null) {
            throw new ReplayArtifactException("dataset '" + id + "' is not listed in " + directory.resolve("run.json")
                    + "; listed: " + datasets.keySet().stream().sorted().toList());
        }
        return dataset;
    }

    /**
     * Selects and validates the trace that carries the requested mode.
     *
     * @param dataset the dataset
     * @param mode    {@link #TRACE_MODE_REAL} or {@link #TRACE_MODE_NULL_MEMBER}
     * @return the trace path relative to the run directory
     */
    String selectTrace(final Dataset dataset, final String mode) {
        if (dataset.traces().isEmpty()) {
            throw new ReplayArtifactException("run in " + directory + " retained no trace for dataset '" + dataset.id()
                    + "'; replay needs the recorded as-of stream. Rerun with --trace " + mode + ". " + REGENERATE);
        }
        for (final String relative : dataset.traces()) {
            final Path file = resolveInside(relative);
            if (!Files.isRegularFile(file)) {
                throw new ReplayArtifactException(
                        "trace " + relative + " listed in run.json is missing from " + directory + ". " + REGENERATE);
            }
            final JsonObject header = ReplayTraceIndex.readHeader(file, relative);
            if (!TRACE_SCHEMA.equals(text(header, "schema"))) {
                throw new ReplayArtifactException("unsupported trace schema '" + text(header, "schema") + "' in "
                        + relative + "; this replay reads only '" + TRACE_SCHEMA + "'. " + REGENERATE);
            }
            if (!mode.equals(text(header, "traceMode"))) {
                continue;
            }
            final String mismatch = headerMismatch(header, dataset, mode);
            if (mismatch != null) {
                throw new ReplayArtifactException("trace " + relative + " does not belong to this run (header "
                        + mismatch + " differs from run.json). " + REGENERATE);
            }
            return relative;
        }
        throw new ReplayArtifactException(
                "run in " + directory + " has no '" + mode + "' trace for dataset '" + dataset.id() + "' (listed: "
                        + dataset.traces() + "). Rerun with --trace " + mode + ". " + REGENERATE);
    }

    /**
     * Mirrors the research inspector's expected trace header: dataset, revision,
     * configuration fingerprint, source digest and null coordinates must all match
     * the run, so a trace copied from another run with an equal recipe is refused.
     *
     * @return the first mismatching header field, or null
     */
    private String headerMismatch(final JsonObject header, final Dataset dataset, final String mode) {
        if (!dataset.id().equals(text(header, "dataset"))) {
            return "dataset";
        }
        if (!revision().equals(text(header, "revision"))) {
            return "revision";
        }
        if (!fingerprint().equals(text(header, "fingerprint"))) {
            return "fingerprint";
        }
        final JsonElement recordedSource = header.get("sourceSha256");
        final String source = recordedSource == null || recordedSource.isJsonNull() ? null
                : text(header, "sourceSha256");
        if (!Objects.equals(dataset.sourceSha256(), source)) {
            return "sourceSha256";
        }
        final int block = integer(header, "nullBlockLength");
        final int member = integer(header, "nullMemberIndex");
        final boolean real = TRACE_MODE_REAL.equals(mode);
        if (block != (real ? -1 : runTraceCoordinate("block")) || member != (real ? -1 : runTraceCoordinate("member"))
                || !real && (block < 1 || member < 0)) {
            return "nullBlockLength/nullMemberIndex";
        }
        return null;
    }

    /**
     * @return the block or member run.json selected for its null trace, or -1 if
     *         none
     */
    private int runTraceCoordinate(final String name) {
        final JsonObject trace = objectOrNull(run, "trace");
        final JsonElement value = trace == null ? null : trace.get(name);
        return value == null || value.isJsonNull() ? -1 : integer(trace, name);
    }

    private static int integer(final JsonObject object, final String name) {
        final JsonElement value = object.get(name);
        final String message = name + " " + value + " must be an exact 32-bit integer. " + REGENERATE;
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new ReplayArtifactException(message);
        }
        try {
            return value.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw new ReplayArtifactException(message, e);
        }
    }

    Path resolveInside(final String relative) {
        final Path resolved = directory.resolve(relative).normalize();
        if (Path.of(relative).isAbsolute() || !resolved.startsWith(directory)) {
            throw new ReplayArtifactException("run.json references '" + relative
                    + "' outside the run directory; artifact links must stay relative to " + directory);
        }
        if (Files.exists(resolved)) {
            try {
                final Path real = resolved.toRealPath();
                if (!real.startsWith(directory.toRealPath())) {
                    throw new ReplayArtifactException("run.json references '" + relative + "', which links to " + real
                            + " outside the run directory; artifact files must live inside " + directory);
                }
            } catch (IOException e) {
                throw new ReplayArtifactException(
                        "cannot resolve '" + relative + "' in " + directory + ": " + e.getMessage(), e);
            }
        }
        return resolved;
    }

    /**
     * Loads the retained source bars of a dataset once and verifies them against
     * the recorded checksum.
     *
     * @param dataset the dataset
     * @return all retained bars in source order
     */
    List<PriceBar> bars(final Dataset dataset) {
        final List<PriceBar> cached = barsByDataset.get(dataset.id());
        if (cached != null) {
            return cached;
        }
        if (dataset.barsPath() == null) {
            throw new ReplayArtifactException(
                    "run.json in " + directory + " has no priceBars entry for dataset '" + dataset.id()
                            + "': the run predates the price-bar sidecar and replay never substitutes live data. "
                            + REGENERATE);
        }
        final Path file = resolveInside(dataset.barsPath());
        if (!Files.isRegularFile(file)) {
            throw new ReplayArtifactException("price-bar sidecar " + dataset.barsPath() + " is missing from "
                    + directory + "; replay never substitutes live data. " + REGENERATE);
        }
        final byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            throw new ReplayArtifactException("cannot read " + file + ": " + e.getMessage(), e);
        }
        final String sha = sha256(bytes);
        if (!sha.equals(dataset.barsSha256())) {
            throw new ReplayArtifactException("price-bar sidecar " + dataset.barsPath() + " sha256 " + sha
                    + " differs from run.json " + dataset.barsSha256() + "; the bundle was modified. " + REGENERATE);
        }
        final List<PriceBar> bars = parseBars(new String(bytes, StandardCharsets.UTF_8), dataset);
        barsByDataset.put(dataset.id(), bars);
        return bars;
    }

    private static List<PriceBar> parseBars(final String text, final Dataset dataset) {
        final List<List<String>> rows = parseCsv(text);
        if (rows.isEmpty() || !BARS_HEADER.equals(String.join(",", rows.getFirst()))) {
            throw new ReplayArtifactException("price-bar sidecar " + dataset.barsPath() + " must start with '"
                    + BARS_HEADER + "'. " + REGENERATE);
        }
        final List<PriceBar> bars = new ArrayList<>(rows.size() - 1);
        try {
            for (int i = 1; i < rows.size(); i++) {
                final List<String> row = rows.get(i);
                final PriceBar bar = new PriceBar(Integer.parseInt(row.get(0)), Instant.parse(row.get(1)),
                        Instant.parse(row.get(2)), row.get(3), row.get(4), row.get(5), row.get(6), row.get(7));
                if (bar.index() != i - 1) {
                    throw new ReplayArtifactException("price-bar sidecar " + dataset.barsPath() + " row " + i
                            + " has index " + bar.index() + ", expected " + (i - 1) + ". " + REGENERATE);
                }
                bars.add(bar);
            }
        } catch (NumberFormatException | DateTimeParseException | IndexOutOfBoundsException e) {
            throw new ReplayArtifactException(
                    "price-bar sidecar " + dataset.barsPath() + " is malformed: " + e.getMessage(), e);
        }
        if (dataset.barsRows() >= 0 && dataset.barsRows() != bars.size()) {
            throw new ReplayArtifactException("price-bar sidecar " + dataset.barsPath() + " has " + bars.size()
                    + " rows but run.json records " + dataset.barsRows() + ". " + REGENERATE);
        }
        return List.copyOf(bars);
    }

    /**
     * Builds a bar series that contains exactly the bars {@code from..to}
     * (inclusive); callers choose {@code to} as the replay cursor so the series can
     * never contain a later bar.
     *
     * @param bars the dataset bars
     * @param name the series name
     * @param from first source index
     * @param to   last source index
     * @return a series whose index {@code i} is source index {@code from + i}
     */
    static BarSeries series(final List<PriceBar> bars, final String name, final int from, final int to) {
        final BarSeries series = new BaseBarSeriesBuilder().withName(name)
                .withNumFactory(DecimalNumFactory.getInstance())
                .build();
        for (int i = from; i <= to; i++) {
            final PriceBar bar = bars.get(i);
            series.barBuilder()
                    .timePeriod(Duration.between(bar.begin(), bar.end()))
                    .endTime(bar.end())
                    .openPrice(bar.open())
                    .highPrice(bar.high())
                    .lowPrice(bar.low())
                    .closePrice(bar.close())
                    .volume(bar.volume())
                    .add();
        }
        return series;
    }

    private static List<ComparisonRow> readComparisons(final Path csv, final JsonObject run) {
        final JsonElement digest = run.get("comparisonsSha256");
        if (digest == null || !digest.isJsonPrimitive() || !digest.getAsJsonPrimitive().isString()) {
            throw new ReplayArtifactException("comparisons.csv has no recorded SHA-256 in run.json. " + REGENERATE);
        }
        final List<List<String>> rows;
        try {
            final byte[] bytes = Files.readAllBytes(csv);
            if (!sha256(bytes).equals(digest.getAsString())) {
                throw new ReplayArtifactException(
                        "comparisons.csv SHA-256 differs from run.json; the artifact bundle was modified or mixed. "
                                + REGENERATE);
            }
            rows = parseCsv(new String(bytes, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ReplayArtifactException("cannot read " + csv + ": " + e.getMessage(), e);
        }
        if (rows.isEmpty()) {
            throw new ReplayArtifactException(csv + " is empty. " + REGENERATE);
        }
        final Map<String, Integer> column = new HashMap<>();
        for (int i = 0; i < rows.getFirst().size(); i++) {
            final String name = rows.getFirst().get(i);
            if (column.put(name, i) != null) {
                throw new ReplayArtifactException(csv + " header has duplicate column '" + name + "'. " + REGENERATE);
            }
        }
        for (final String required : List.of("key", "dataset", "asset", "section", "mode", "grammar", "activeRules",
                "detector", "policy", "partition", "metric", "nullBlockLength", "observed", "numerator", "denominator",
                "validNullMembers", "requestedNullMembers", "unavailableNullMembers", "nullMedian", "nullLow",
                "nullHigh", "observedMinusNullMedian", "empiricalReferenceRank", "availability")) {
            if (!column.containsKey(required)) {
                throw new ReplayArtifactException(csv + " lacks column '" + required + "'. " + REGENERATE);
            }
        }
        final List<ComparisonRow> result = new ArrayList<>(rows.size() - 1);
        final Map<String, ComparisonFamily> expected = expectedComparisons(run);
        final Set<String> keys = new HashSet<>();
        for (int i = 1; i < rows.size(); i++) {
            final List<String> row = rows.get(i);
            if (row.size() != rows.getFirst().size()) {
                throw new ReplayArtifactException(csv + " row " + (i + 1) + " has " + row.size() + " cells; expected "
                        + rows.getFirst().size() + ". " + REGENERATE);
            }
            final String rules = row.get(column.get("activeRules"));
            final String observed = row.get(column.get("observed"));
            final String numerator = row.get(column.get("numerator"));
            final String denominator = row.get(column.get("denominator"));
            try {
                final ComparisonRow comparison = new ComparisonRow(row.get(column.get("key")),
                        row.get(column.get("dataset")), row.get(column.get("asset")), row.get(column.get("section")),
                        row.get(column.get("mode")), row.get(column.get("grammar")),
                        rules.isEmpty() ? List.of() : List.of(rules.split(";")), row.get(column.get("detector")),
                        row.get(column.get("partition")), row.get(column.get("metric")),
                        nullBlockLength(row.get(column.get("nullBlockLength"))),
                        observed.isEmpty() ? Double.NaN : Double.parseDouble(observed),
                        numerator.isEmpty() ? null : Long.valueOf(numerator),
                        denominator.isEmpty() ? null : Long.valueOf(denominator));
                final String key = String.join("|", comparison.dataset(), comparison.section(), comparison.mode(),
                        comparison.detector(), comparison.partition(), comparison.metric(),
                        comparison.nullBlockLength() <= 0 ? "b-" : "b" + comparison.nullBlockLength());
                if (!key.equals(comparison.key())) {
                    throw new ReplayArtifactException(csv + " row " + (i + 1) + " key column '" + comparison.key()
                            + "' does not match row fields. " + REGENERATE);
                }
                if (!keys.add(key)) {
                    throw new ReplayArtifactException(
                            csv + " row " + (i + 1) + " has duplicate comparison key '" + key + "'. " + REGENERATE);
                }
                validateMetricSupport(csv, i + 1, comparison);
                final int requestedNullMembers = validateNullComparison(csv, i + 1, row, column, comparison);
                final ComparisonFamily family = expected.get(key);
                if (family != null) {
                    final String mismatch = !family.asset().equals(comparison.asset()) ? "asset"
                            : !family.grammar().equals(comparison.grammar()) ? "grammar"
                                    : !family.activeRules().equals(comparison.activeRules()) ? "activeRules"
                                            : !family.policy().equals(row.get(column.get("policy"))) ? "policy"
                                                    : family.requestedNullMembers() != requestedNullMembers
                                                            ? "requestedNullMembers"
                                                            : null;
                    if (mismatch != null) {
                        throw new ReplayArtifactException(csv + " row " + (i + 1) + " family metadata " + mismatch
                                + " differs from run.json's recorded configuration. " + REGENERATE);
                    }
                }
                result.add(comparison);
            } catch (NumberFormatException e) {
                throw new ReplayArtifactException(
                        csv + " row " + (i + 1) + " has invalid numeric comparison fields. " + REGENERATE, e);
            }
        }
        final Set<String> missing = new TreeSet<>(expected.keySet());
        missing.removeAll(keys);
        final Set<String> unexpected = new TreeSet<>(keys);
        unexpected.removeAll(expected.keySet());
        if (!missing.isEmpty() || !unexpected.isEmpty()) {
            throw new ReplayArtifactException(csv
                    + " row-key inventory differs from run.json's recorded configuration: " + "missing "
                    + missing.size() + " key(s) " + missing.stream().limit(5).toList() + "; unexpected "
                    + unexpected.size() + " key(s) " + unexpected.stream().limit(5).toList() + ". " + REGENERATE);
        }
        return List.copyOf(result);
    }

    private static void validateMetricSupport(final Path csv, final int line, final ComparisonRow comparison) {
        final String metric = comparison.metric();
        final String context = csv + " row " + line + " metric " + metric + ": ";
        if (RATE_METRICS.contains(metric)) {
            final Long numerator = comparison.numerator();
            final Long denominator = comparison.denominator();
            if (numerator == null || denominator == null || numerator < 0 || denominator < 0
                    || numerator > denominator) {
                throw new ReplayArtifactException(context
                        + "rate metrics require non-negative numerator and denominator with numerator <= denominator. "
                        + REGENERATE);
            }
            final double expected = denominator == 0 ? Double.NaN : numerator / (double) denominator;
            if (Double.compare(expected, comparison.observed()) != 0) {
                throw new ReplayArtifactException(context + "observed must equal numerator / denominator"
                        + " (unavailable exactly when denominator is zero). " + REGENERATE);
            }
        } else if (TOPOLOGY_METRICS.contains(metric)) {
            if (comparison.numerator() != null || comparison.denominator() != null) {
                throw new ReplayArtifactException(
                        context + "non-rate metrics cannot carry a numerator or denominator. " + REGENERATE);
            }
        } else {
            throw new ReplayArtifactException(context + "unknown comparison metric. " + REGENERATE);
        }
    }

    private record ComparisonFamily(String asset, String grammar, List<String> activeRules, String policy,
            int requestedNullMembers) {
    }

    private static Map<String, ComparisonFamily> expectedComparisons(final JsonObject run) {
        final String recipe = requiredText(requiredObject(run, "recipe"), "name");
        if (!List.of("smoke", "explore", "frozen-cf525").contains(recipe)) {
            throw new ReplayArtifactException("run.json has unknown recorded recipe '" + recipe + "'. " + REGENERATE);
        }
        final JsonObject configuration = requiredObject(run, "configuration");
        final List<String> partitions = new ArrayList<>();
        for (final JsonElement entry : requiredArray(configuration, "partitions")) {
            if (!entry.isJsonObject()) {
                throw new ReplayArtifactException(
                        "run.json.configuration.partitions entries must be objects. " + REGENERATE);
            }
            partitions.add(requiredText(entry.getAsJsonObject(), "name"));
        }
        if (partitions.isEmpty()) {
            throw new ReplayArtifactException("run.json.configuration.partitions must not be empty. " + REGENERATE);
        }
        final String primary = requiredText(requiredObject(configuration, "detector"), "name");
        final List<String> robustness = new ArrayList<>();
        for (final JsonElement entry : requiredArray(configuration, "robustnessDetectors")) {
            if (!entry.isJsonObject()) {
                throw new ReplayArtifactException(
                        "run.json.configuration.robustnessDetectors entries must be objects. " + REGENERATE);
            }
            robustness.add(requiredText(entry.getAsJsonObject(), "name"));
        }
        List<String> activeRules = configuredStrings(configuration, "activeRules", CLASSICAL_RULE_IDS);
        if (activeRules.isEmpty()) {
            throw new ReplayArtifactException("run.json.configuration.activeRules must not be empty. " + REGENERATE);
        }
        final boolean frozen = "frozen-cf525".equals(recipe);
        if (frozen) {
            if (!activeRules.containsAll(CLASSICAL_RULE_IDS)) {
                throw new ReplayArtifactException("run.json frozen recipe requires all classical rules. " + REGENERATE);
            }
            activeRules = CLASSICAL_RULE_IDS;
        }
        final List<String> competing = configuredStrings(configuration, "competingModes",
                List.of("3+3", "5+5", "change-point-baseline"));
        final JsonObject nulls = requiredObject(configuration, "null");
        final List<Integer> blocks = new ArrayList<>();
        for (final JsonElement entry : requiredArray(nulls, "blockLengths")) {
            final JsonObject value = new JsonObject();
            value.add("block", entry);
            final int block = integer(value, "block");
            if (block <= 0 || blocks.contains(block)) {
                throw new ReplayArtifactException(
                        "run.json.configuration.null.blockLengths must contain distinct positive values. "
                                + REGENERATE);
            }
            blocks.add(block);
        }
        final int ensembleSize = integer(nulls, "ensembleSize");
        if (blocks.isEmpty() || ensembleSize <= 0) {
            throw new ReplayArtifactException(
                    "run.json.configuration.null requires block lengths and a positive ensembleSize. " + REGENERATE);
        }
        final Map<String, ComparisonFamily> expected = new LinkedHashMap<>();
        final Set<String> datasetIds = new HashSet<>();
        for (final JsonElement entry : requiredArray(run, "datasets")) {
            if (!entry.isJsonObject()) {
                throw new ReplayArtifactException("run.json.datasets entries must be objects. " + REGENERATE);
            }
            final JsonObject dataset = entry.getAsJsonObject();
            final String id = requiredText(dataset, "id");
            if (!datasetIds.add(id)) {
                throw new ReplayArtifactException("run.json has duplicate dataset '" + id + "'. " + REGENERATE);
            }
            if (!"complete".equals(requiredText(dataset, "status"))) {
                continue;
            }
            final String asset = requiredText(dataset, "asset");
            addExpectedComparisons(expected, id, "h1", "topology-only", primary, partitions, blocks,
                    new ComparisonFamily(asset, "MOTIVE_5", List.of(), "kernel-topology", ensembleSize));
            addExpectedComparisons(expected, id, "h2", "topology-only", primary, partitions, blocks,
                    new ComparisonFamily(asset, "CYCLE_5_3", List.of(), "kernel-topology", ensembleSize));
            for (final String rule : activeRules) {
                addExpectedComparisons(expected, id, "h2", "+" + rule, primary, partitions, blocks,
                        new ComparisonFamily(asset, "CYCLE_5_3", List.of(rule), "kernel-topology+evidence",
                                ensembleSize));
            }
            addExpectedComparisons(expected, id, "h2", frozen ? "classical-all" : "all-rules", primary, partitions,
                    blocks,
                    new ComparisonFamily(asset, "CYCLE_5_3", activeRules, "kernel-topology+evidence", ensembleSize));
            for (final String mode : competing) {
                addExpectedComparisons(expected, id, "competing", "competing-" + mode, primary, partitions, List.of(0),
                        new ComparisonFamily(asset, mode, List.of(), "unmatched-recognizer", 0));
            }
            for (final String detector : robustness) {
                addExpectedComparisons(expected, id, "robustness", "topology-only", detector, partitions, List.of(0),
                        new ComparisonFamily(asset, "MOTIVE_5", List.of(), "robustness-detector", 0));
            }
        }
        return expected;
    }

    private static void addExpectedComparisons(final Map<String, ComparisonFamily> expected, final String dataset,
            final String section, final String mode, final String detector, final List<String> partitions,
            final List<Integer> blocks, final ComparisonFamily family) {
        for (final String partition : partitions) {
            for (final String metric : COMPARISON_METRICS) {
                if (family.activeRules().isEmpty()
                        && ("evidencePassRate".equals(metric) || "jointPassRate".equals(metric))) {
                    continue;
                }
                for (final int block : blocks) {
                    final String key = String.join("|", dataset, section, mode, detector, partition, metric,
                            block == 0 ? "b-" : "b" + block);
                    if (expected.putIfAbsent(key, family) != null) {
                        throw new ReplayArtifactException(
                                "run.json configuration produces duplicate key '" + key + "'. " + REGENERATE);
                    }
                }
            }
        }
    }

    private static JsonObject requiredObject(final JsonObject owner, final String name) {
        final JsonObject object = objectOrNull(owner, name);
        if (object == null) {
            throw new ReplayArtifactException("run.json requires object '" + name + "'. " + REGENERATE);
        }
        return object;
    }

    private static JsonArray requiredArray(final JsonObject owner, final String name) {
        final JsonElement value = owner.get(name);
        if (value == null || !value.isJsonArray()) {
            throw new ReplayArtifactException("run.json requires array '" + name + "'. " + REGENERATE);
        }
        return value.getAsJsonArray();
    }

    private static String requiredText(final JsonObject owner, final String name) {
        final String value = text(owner, name);
        if (value.isEmpty()) {
            throw new ReplayArtifactException("run.json requires non-empty string '" + name + "'. " + REGENERATE);
        }
        return value;
    }

    private static List<String> configuredStrings(final JsonObject owner, final String name,
            final List<String> allowed) {
        final List<String> result = new ArrayList<>();
        for (final JsonElement entry : requiredArray(owner, name)) {
            if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()
                    || !allowed.contains(entry.getAsString()) || result.contains(entry.getAsString())) {
                throw new ReplayArtifactException(
                        "run.json.configuration." + name + " requires distinct supported strings. " + REGENERATE);
            }
            result.add(entry.getAsString());
        }
        return List.copyOf(result);
    }

    /**
     * Block length is empty for rows that have no bootstrap null; those carry 0.
     */
    private static int nullBlockLength(final String value) {
        return value.isEmpty() ? 0 : Integer.parseInt(value);
    }

    private static int validateNullComparison(final Path csv, final int line, final List<String> row,
            final Map<String, Integer> column, final ComparisonRow comparison) {
        final int block = comparison.nullBlockLength();
        if (block == 0) {
            for (final String field : NULL_REFERENCE_COLUMNS) {
                if (!row.get(column.get(field)).isEmpty()) {
                    throw nullReferenceError(csv, line, "rows without a bootstrap null must leave " + field + " empty");
                }
            }
        }
        final int valid = block == 0 ? 0 : Integer.parseInt(row.get(column.get("validNullMembers")));
        final int requested = block == 0 ? 0 : Integer.parseInt(row.get(column.get("requestedNullMembers")));
        final int unavailable = block == 0 ? 0 : Integer.parseInt(row.get(column.get("unavailableNullMembers")));
        final double median = optionalNumber(row.get(column.get("nullMedian")));
        final double low = optionalNumber(row.get(column.get("nullLow")));
        final double high = optionalNumber(row.get(column.get("nullHigh")));
        final double difference = optionalNumber(row.get(column.get("observedMinusNullMedian")));
        final double rank = optionalNumber(row.get(column.get("empiricalReferenceRank")));
        if (requested < 0 || valid < 0 || unavailable < 0 || (long) valid + unavailable != requested) {
            throw nullReferenceError(csv, line,
                    "member counts must be non-negative and valid + unavailable must equal requested");
        }
        if (valid == 0) {
            if (!Double.isNaN(median) || !Double.isNaN(low) || !Double.isNaN(high)) {
                throw nullReferenceError(csv, line, "quantiles must be unavailable when no member is valid");
            }
        } else {
            if (!Double.isFinite(low) || !Double.isFinite(median) || !Double.isFinite(high) || low > median
                    || median > high) {
                throw nullReferenceError(csv, line,
                        "valid members require finite ordered nullLow <= nullMedian <= nullHigh");
            }
            if ((nearestRankPosition(valid, 0.025d) == nearestRankPosition(valid, 0.5d)
                    && Double.compare(low, median) != 0)
                    || (nearestRankPosition(valid, 0.5d) == nearestRankPosition(valid, 0.975d)
                            && Double.compare(median, high) != 0)) {
                throw nullReferenceError(csv, line, "quantiles at the same nearest-rank position must agree");
            }
        }
        final double observed = comparison.observed();
        if (Double.isInfinite(observed)) {
            throw nullReferenceError(csv, line, "observed must be finite or unavailable");
        }
        if (block < 0 || (block == 0 && !row.get(column.get("nullBlockLength")).isEmpty())
                || (block == 0 ? requested != 0 : requested == 0)) {
            throw nullReferenceError(csv, line, "null block length and requested member count are inconsistent");
        }
        final boolean comparable = block > 0 && valid > 0 && Double.isFinite(observed);
        final String expected = comparable ? "available"
                : block == 0 && Double.isFinite(observed) ? "not-comparable" : "unavailable";
        if (!expected.equals(row.get(column.get("availability")))) {
            throw nullReferenceError(csv, line,
                    "availability must be " + expected + " for the observed and null values");
        }
        if (!comparable) {
            if (!Double.isNaN(difference) || !Double.isNaN(rank)) {
                throw nullReferenceError(csv, line, "unavailable comparisons cannot carry a difference or rank");
            }
            return requested;
        }
        if (!Double.isFinite(difference) || Double.compare(difference, observed - median) != 0) {
            throw nullReferenceError(csv, line, "observedMinusNullMedian must equal observed - nullMedian");
        }
        final double denominator = valid + 1.0d;
        if (!Double.isFinite(rank) || rank <= 0.0d || rank > 1.0d
                || Double.compare(rank, Math.rint(rank * denominator) / denominator) != 0) {
            throw nullReferenceError(csv, line,
                    "empiricalReferenceRank must be k / (validNullMembers + 1), for 1 <= k <= validNullMembers + 1");
        }
        final long atLeast = Math.round(rank * denominator) - 1L;
        if (!matchesQuantileRank(observed, low, nearestRankPosition(valid, 0.025d), valid, atLeast)
                || !matchesQuantileRank(observed, median, nearestRankPosition(valid, 0.5d), valid, atLeast)
                || !matchesQuantileRank(observed, high, nearestRankPosition(valid, 0.975d), valid, atLeast)) {
            throw nullReferenceError(csv, line, "empiricalReferenceRank contradicts the null nearest-rank quantiles");
        }
        return requested;
    }

    private static double optionalNumber(final String value) {
        return value.isEmpty() ? Double.NaN : Double.parseDouble(value);
    }

    private static int nearestRankPosition(final int count, final double quantile) {
        final int rank = (int) Math.ceil(quantile * count - 1.0e-9d);
        return Math.min(count, Math.max(1, rank));
    }

    private static boolean matchesQuantileRank(final double observed, final double quantile, final int position,
            final int valid, final long atLeast) {
        return observed <= quantile ? atLeast >= (long) valid - position + 1 : atLeast <= (long) valid - position;
    }

    private static ReplayArtifactException nullReferenceError(final Path csv, final int line, final String detail) {
        return new ReplayArtifactException(
                csv + " row " + line + " has inconsistent null reference: " + detail + ". " + REGENERATE);
    }

    /** Parses RFC 4180 CSV (quoted fields, doubled quotes, embedded newlines). */
    static List<List<String>> parseCsv(final String text) {
        final List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        final StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean pending = false;
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
                pending = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
                pending = true;
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                if (pending || field.length() > 0 || !row.isEmpty()) {
                    row.add(field.toString());
                    rows.add(row);
                }
                row = new ArrayList<>();
                field.setLength(0);
                pending = false;
            } else {
                field.append(c);
                pending = true;
            }
        }
        if (pending || field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }

    private static JsonObject parseJson(final Path file, final String display) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            final JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                throw new ReplayArtifactException(
                        display + " in " + file.getParent() + " is not a JSON object. " + REGENERATE);
            }
            return parsed.getAsJsonObject();
        } catch (JsonParseException | IOException e) {
            throw new ReplayArtifactException(
                    display + " in " + file.getParent() + " is unreadable: " + e.getMessage() + ". " + REGENERATE, e);
        }
    }

    void verifyTraceDigest(final Dataset dataset, final String relative) {
        final String expected = dataset.traceSha256().get(relative);
        if (expected == null || !expected.matches("[0-9a-f]{64}")) {
            throw new ReplayArtifactException(
                    "trace " + relative + " has no valid recorded SHA-256 in run.json. " + REGENERATE);
        }
        try {
            if (!sha256(resolveInside(relative)).equals(expected)) {
                throw new ReplayArtifactException("trace " + relative
                        + " SHA-256 differs from run.json; the artifact bundle was modified or mixed. " + REGENERATE);
            }
        } catch (IOException e) {
            throw new ReplayArtifactException(
                    "cannot read trace " + relative + ": " + e.getMessage() + ". " + REGENERATE, e);
        }
    }

    private static String sha256(final Path file) throws IOException {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream input = new DigestInputStream(Files.newInputStream(file), digest)) {
                input.transferTo(OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is mandatory on every Java platform", e);
        }
    }

    static String sha256(final byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is mandatory on every Java platform", e);
        }
    }

    private static JsonObject objectOrNull(final JsonObject owner, final String name) {
        final JsonElement value = owner.get(name);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        if (!value.isJsonObject()) {
            throw new ReplayArtifactException("artifact field " + name + " must be an object. " + REGENERATE);
        }
        return value.getAsJsonObject();
    }

    private static String text(final JsonObject object, final String name) {
        final JsonElement value = object == null ? null : object.get(name);
        if (value == null || value.isJsonNull()) {
            return "";
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new ReplayArtifactException("artifact field " + name + " must be a string. " + REGENERATE);
        }
        return value.getAsString();
    }
}

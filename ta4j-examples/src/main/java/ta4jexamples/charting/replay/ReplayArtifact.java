/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.charting.replay;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
 * retained price-bar sidecar and the real trace; it never recomputes an
 * Elliott count and never fetches data.
 */
final class ReplayArtifact {

    static final String RUN_SCHEMA = "elliott-research-run/1";
    static final String TRACE_SCHEMA = "elliott-research-trace/1";
    static final String BARS_HEADER = "index,beginTime,endTime,open,high,low,close,volume";
    static final String TRACE_MODE_REAL = "real";
    static final String TRACE_MODE_NULL_MEMBER = "selected-null-member";

    private static final String REGENERATE = "Regenerate the run with ElliottResearch (see the Elliott research section of ta4j-core/README.md), e.g. `run smoke --out <dir> --overwrite` or the original recipe with --trace real.";

    /**
     * One row of {@code comparisons.csv}, restricted to the identity columns the
     * replay needs.
     */
    record ComparisonRow(String key, String dataset, String asset, String section, String mode, String grammar,
            List<String> activeRules, String detector, String partition, String metric, int nullBlockLength,
            String observed) {

        ReplayTraceIndex.Family family() {
            return new ReplayTraceIndex.Family(section, mode, grammar, detector, partition);
        }
    }

    /** One dataset entry of {@code run.json}. */
    record Dataset(String id, String asset, String coverageStatus, String coverageMessage, List<String> traces,
            String barsPath, String barsSha256, int barsRows, String sourceSha256) {
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
            throw new ReplayArtifactException("no run.json in " + dir
                    + "; pass the run directory written by ElliottResearch. " + REGENERATE);
        }
        final JsonObject run = parseJson(runFile, "run.json");
        final String schema = text(run, "artifactSchemaVersion");
        if (!RUN_SCHEMA.equals(schema)) {
            throw new ReplayArtifactException("unsupported run schema '" + schema + "' in " + runFile + "; this replay reads only '"
                    + RUN_SCHEMA + "'. " + REGENERATE);
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
        final ReplayArtifact artifact = new ReplayArtifact(dir, run, readComparisons(csv));
        final JsonArray array = run.getAsJsonArray("datasets");
        if (array == null) {
            throw new ReplayArtifactException("run.json in " + dir + " has no datasets array. " + REGENERATE);
        }
        for (final JsonElement element : array) {
            final Dataset dataset = artifact.parseDataset(element.getAsJsonObject());
            artifact.datasets.put(dataset.id(), dataset);
        }
        return artifact;
    }

    private Dataset parseDataset(final JsonObject object) {
        final List<String> traces = new ArrayList<>();
        if (object.has("traces") && object.get("traces").isJsonArray()) {
            object.getAsJsonArray("traces").forEach(trace -> traces.add(trace.getAsString()));
        }
        final JsonObject coverage = object.has("coverage") ? object.getAsJsonObject("coverage") : new JsonObject();
        final JsonObject bars = object.has("priceBars") && object.get("priceBars").isJsonObject()
                ? object.getAsJsonObject("priceBars")
                : null;
        final JsonObject source = object.has("source") && object.get("source").isJsonObject()
                ? object.getAsJsonObject("source")
                : new JsonObject();
        return new Dataset(text(object, "id"), text(object, "asset"), text(coverage, "status"),
                text(coverage, "message"), List.copyOf(traces), bars == null ? null : text(bars, "path"),
                bars == null ? null : text(bars, "sha256"),
                bars == null || !bars.has("rows") ? -1 : bars.get("rows").getAsInt(),
                source.has("sha256") && !source.get("sha256").isJsonNull() ? text(source, "sha256") : null);
    }

    Path directory() {
        return directory;
    }

    String fingerprint() {
        return run.getAsJsonObject("identity").get("fingerprint").getAsString();
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
        return "unknown comparison key '" + key + "' in " + directory.resolve("comparisons.csv")
                + "; closest keys:\n  " + String.join("\n  ", close);
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
                        && candidate.detector().equals(row.detector())
                        && candidate.partition().equals(row.partition()) && "h2".equals(candidate.section())
                        && "all-rules".equals(candidate.mode()))
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
                throw new ReplayArtifactException("trace " + relative + " listed in run.json is missing from "
                        + directory + ". " + REGENERATE);
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
                throw new ReplayArtifactException("trace " + relative
                        + " does not belong to this run (header " + mismatch + " differs from run.json). " + REGENERATE);
            }
            return relative;
        }
        throw new ReplayArtifactException("run in " + directory + " has no '" + mode + "' trace for dataset '"
                + dataset.id() + "' (listed: " + dataset.traces() + "). Rerun with --trace " + mode + ". "
                + REGENERATE);
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
                : recordedSource.getAsString();
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

    /** @return the block or member run.json selected for its null trace, or -1 if none */
    private int runTraceCoordinate(final String name) {
        final JsonElement trace = run.get("trace");
        final JsonElement value = trace != null && trace.isJsonObject() ? trace.getAsJsonObject().get(name) : null;
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? value.getAsInt() : -1;
    }

    private static int integer(final JsonObject object, final String name) {
        final JsonElement value = object.get(name);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? value.getAsInt()
                : Integer.MIN_VALUE;
    }

    Path resolveInside(final String relative) {
        final Path resolved = directory.resolve(relative).normalize();
        if (Path.of(relative).isAbsolute() || !resolved.startsWith(directory)) {
            throw new ReplayArtifactException("run.json references '" + relative
                    + "' outside the run directory; artifact links must stay relative to " + directory);
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
            throw new ReplayArtifactException("run.json in " + directory + " has no priceBars entry for dataset '"
                    + dataset.id() + "': the run predates the price-bar sidecar and replay never substitutes live data. "
                    + REGENERATE);
        }
        final Path file = resolveInside(dataset.barsPath());
        if (!Files.isRegularFile(file)) {
            throw new ReplayArtifactException("price-bar sidecar " + dataset.barsPath() + " is missing from " + directory
                    + "; replay never substitutes live data. " + REGENERATE);
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

    private static List<ComparisonRow> readComparisons(final Path csv) {
        final List<List<String>> rows;
        try (Reader reader = Files.newBufferedReader(csv, StandardCharsets.UTF_8)) {
            final StringBuilder text = new StringBuilder();
            final char[] buffer = new char[1 << 14];
            int read;
            while ((read = reader.read(buffer)) > 0) {
                text.append(buffer, 0, read);
            }
            rows = parseCsv(text.toString());
        } catch (IOException e) {
            throw new ReplayArtifactException("cannot read " + csv + ": " + e.getMessage(), e);
        }
        if (rows.isEmpty()) {
            throw new ReplayArtifactException(csv + " is empty. " + REGENERATE);
        }
        final Map<String, Integer> column = new HashMap<>();
        for (int i = 0; i < rows.getFirst().size(); i++) {
            column.put(rows.getFirst().get(i), i);
        }
        for (final String required : List.of("key", "dataset", "asset", "section", "mode", "grammar", "activeRules",
                "detector", "partition", "metric", "nullBlockLength", "observed")) {
            if (!column.containsKey(required)) {
                throw new ReplayArtifactException(csv + " lacks column '" + required + "'. " + REGENERATE);
            }
        }
        final List<ComparisonRow> result = new ArrayList<>(rows.size() - 1);
        for (int i = 1; i < rows.size(); i++) {
            final List<String> row = rows.get(i);
            final String rules = row.get(column.get("activeRules"));
            result.add(new ComparisonRow(row.get(column.get("key")), row.get(column.get("dataset")),
                    row.get(column.get("asset")), row.get(column.get("section")), row.get(column.get("mode")),
                    row.get(column.get("grammar")),
                    rules.isEmpty() ? List.of() : List.of(rules.split(";")), row.get(column.get("detector")),
                    row.get(column.get("partition")), row.get(column.get("metric")),
                    nullBlockLength(row.get(column.get("nullBlockLength"))), row.get(column.get("observed"))));
        }
        return List.copyOf(result);
    }

    /** Block length is empty for rows that have no bootstrap null; those carry 0. */
    private static int nullBlockLength(final String value) {
        return value.isEmpty() ? 0 : Integer.parseInt(value);
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
                throw new ReplayArtifactException(display + " in " + file.getParent() + " is not a JSON object. "
                        + REGENERATE);
            }
            return parsed.getAsJsonObject();
        } catch (JsonParseException | IOException e) {
            throw new ReplayArtifactException(display + " in " + file.getParent() + " is unreadable: " + e.getMessage()
                    + ". " + REGENERATE, e);
        }
    }

    static String sha256(final byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is mandatory on every Java platform", e);
        }
    }

    private static String text(final JsonObject object, final String name) {
        final JsonElement value = object.get(name);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }
}

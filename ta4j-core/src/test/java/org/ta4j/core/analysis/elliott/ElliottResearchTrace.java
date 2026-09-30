/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/**
 * Streaming JSONL witness of the observations a study run recorded.
 *
 * <p>
 * The trace is a {@link StudyObserver}: it serialises the exact analyses, rule
 * evidence, and alternative outcomes the runner hands it and never recomputes
 * recognition. Each observation is written as one line as soon as it arrives,
 * so memory stays bounded by the number of distinct candidate versions rather
 * than by members times bars. A file is complete only when its footer line
 * exists; a crashed run leaves a readable but truncated file.
 * </p>
 *
 * <p>
 * Layout: a header line, one object per observation, then the footer
 * {@code {"complete":true,"records":N}}.
 * </p>
 *
 * <p>
 * A candidate's identity ({@code candidateKey}) hashes grammar, direction and
 * the full pivot placement (indices, types, price text), so a placement that
 * shares only its start and end with another is a different candidate. Its
 * {@code version} is {@code <candidateKey>@v<n>}: versions are numbered in
 * first-emission order within one file, and a new version is minted only when
 * the candidate's rule-evidence content differs from every content already
 * emitted for it. Earlier lines are never rewritten. Instances are not
 * thread-safe.
 * </p>
 *
 * @since 0.25.1
 */
final class ElliottResearchTrace implements StudyObserver, Closeable {

    static final String SCHEMA = "elliott-research-trace/1";
    static final String MODE_REAL = "real";
    static final String MODE_SELECTED_NULL_MEMBER = "selected-null-member";

    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();

    private final Path file;
    private final String datasetId;
    private final int nullBlockLength;
    private final int nullMemberIndex;
    private final BufferedWriter writer;
    private final Map<String, Map<String, Integer>> versionsByCandidate = new HashMap<>();
    private long records;
    private boolean closed;

    private ElliottResearchTrace(final Path file, final String datasetId, final int nullBlockLength,
            final int nullMemberIndex, final BufferedWriter writer) {
        this.file = file;
        this.datasetId = datasetId;
        this.nullBlockLength = nullBlockLength;
        this.nullMemberIndex = nullMemberIndex;
        this.writer = writer;
    }

    /**
     * Creates the file (and parent directories) and writes the header.
     *
     * @param file            trace destination
     * @param datasetId       dataset identifier stored on every record
     * @param traceMode       {@code real} or {@code selected-null-member}
     * @param nullBlockLength null block length, or {@code -1} for real traces
     * @param nullMemberIndex null member index, or {@code -1} for real traces
     * @return open trace; close it to write the footer
     * @throws IOException on filesystem failure
     */
    static ElliottResearchTrace open(final Path file, final String datasetId, final String traceMode,
            final int nullBlockLength, final int nullMemberIndex) throws IOException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(datasetId, "datasetId");
        Objects.requireNonNull(traceMode, "traceMode");
        if (MODE_REAL.equals(traceMode)) {
            if (nullBlockLength != -1 || nullMemberIndex != -1) {
                throw new IllegalArgumentException("real traces carry no null block length or member index");
            }
        } else if (MODE_SELECTED_NULL_MEMBER.equals(traceMode)) {
            if (nullBlockLength < 1 || nullMemberIndex < 0) {
                throw new IllegalArgumentException("selected-null-member traces need a block length and member index");
            }
        } else {
            throw new IllegalArgumentException("unsupported trace mode: " + traceMode);
        }
        final Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        final BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
        final ElliottResearchTrace trace = new ElliottResearchTrace(file, datasetId, nullBlockLength, nullMemberIndex,
                writer);
        try {
            final JsonObject header = new JsonObject();
            header.addProperty("schema", SCHEMA);
            header.addProperty("dataset", datasetId);
            header.addProperty("traceMode", traceMode);
            header.addProperty("nullBlockLength", nullBlockLength);
            header.addProperty("nullMemberIndex", nullMemberIndex);
            trace.writeLine(header);
        } catch (final IOException | RuntimeException e) {
            writer.close();
            throw e;
        }
        return trace;
    }

    /** @return observation records written so far, excluding header and footer */
    long records() {
        return records;
    }

    @Override
    public void topology(final Scope scope, final String partition, final int recordedIndex, final Instant asOfEnd,
            final List<ConfirmedPivot> visiblePivots, final TopologyAnalysis analysis,
            final List<List<RuleEvidence>> candidateEvidence) {
        Objects.requireNonNull(analysis, "analysis");
        Objects.requireNonNull(candidateEvidence, "candidateEvidence");
        if (candidateEvidence.size() != analysis.candidates().size()) {
            throw new IllegalArgumentException("candidate evidence must align with analysis candidates: "
                    + candidateEvidence.size() + " vs " + analysis.candidates().size());
        }
        final JsonObject record = base(scope, partition, recordedIndex, asOfEnd, visiblePivots, "topology",
                analysis.status().name());
        record.add("direction", analysis.direction() == null ? null : new JsonPrimitive(analysis.direction().name()));
        record.add("pivots", pivotsJson(visiblePivots));
        final JsonArray candidates = new JsonArray();
        for (int i = 0; i < analysis.candidates().size(); i++) {
            candidates.add(candidateJson(analysis.candidates().get(i), candidateEvidence.get(i)));
        }
        record.add("candidates", candidates);
        emit(record);
    }

    @Override
    public void alternative(final Scope scope, final String partition, final int recordedIndex, final Instant asOfEnd,
            final List<ConfirmedPivot> visiblePivots, final String outcome, final Set<String> labels) {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(labels, "labels");
        final JsonObject record = base(scope, partition, recordedIndex, asOfEnd, visiblePivots, "alternative", outcome);
        record.add("direction", null);
        record.add("pivots", pivotsJson(visiblePivots));
        record.add("candidates", new JsonArray());
        final JsonArray labelArray = new JsonArray();
        new TreeSet<>(labels).forEach(labelArray::add);
        record.add("labels", labelArray);
        emit(record);
    }

    /** Writes the footer and releases the file; further observations fail. */
    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        try {
            final JsonObject footer = new JsonObject();
            footer.addProperty("complete", true);
            footer.addProperty("records", records);
            writeLine(footer);
        } finally {
            writer.close();
        }
    }

    private JsonObject base(final Scope scope, final String partition, final int recordedIndex, final Instant asOfEnd,
            final List<ConfirmedPivot> visiblePivots, final String kind, final String status) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(partition, "partition");
        Objects.requireNonNull(asOfEnd, "asOfEnd");
        Objects.requireNonNull(visiblePivots, "visiblePivots");
        if (scope.nullBlockLength() != nullBlockLength || scope.nullMemberIndex() != nullMemberIndex) {
            throw new IllegalArgumentException("scope null coordinates " + scope.nullBlockLength() + "/"
                    + scope.nullMemberIndex() + " do not match trace " + nullBlockLength + "/" + nullMemberIndex);
        }
        final JsonObject record = new JsonObject();
        record.addProperty("dataset", datasetId);
        record.addProperty("section", scope.section());
        record.addProperty("mode", scope.mode());
        record.addProperty("grammar", scope.grammar());
        final JsonArray activeRules = new JsonArray();
        scope.activeRules().forEach(activeRules::add);
        record.add("activeRules", activeRules);
        record.addProperty("detector", scope.detector());
        record.addProperty("nullBlockLength", scope.nullBlockLength());
        record.addProperty("nullMemberIndex", scope.nullMemberIndex());
        record.addProperty("partition", partition);
        record.addProperty("asOfIndex", recordedIndex);
        record.addProperty("asOfTime", asOfEnd.toString());
        record.addProperty("kind", kind);
        record.addProperty("status", status);
        return record;
    }

    private void emit(final JsonObject record) {
        if (closed) {
            throw new IllegalStateException("trace already closed: " + file);
        }
        try {
            writeLine(record);
        } catch (final IOException e) {
            throw new UncheckedIOException("failed writing trace " + file, e);
        }
        records++;
    }

    private void writeLine(final JsonObject line) throws IOException {
        JSON.toJson(line, writer);
        writer.write('\n');
    }

    private static JsonArray pivotsJson(final List<ConfirmedPivot> pivots) {
        final JsonArray array = new JsonArray();
        for (final ConfirmedPivot pivot : pivots) {
            final JsonObject json = placementJson(pivot);
            json.addProperty("confirmationIndex", pivot.confirmationIndex());
            array.add(json);
        }
        return array;
    }

    private static JsonObject placementJson(final ConfirmedPivot pivot) {
        final JsonObject json = new JsonObject();
        json.addProperty("index", pivot.pivotIndex());
        json.addProperty("price", pivot.price().toString());
        json.addProperty("type", pivot.type().name());
        return json;
    }

    private JsonObject candidateJson(final TopologyCandidate candidate, final List<RuleEvidence> evidence) {
        final String key = candidateKey(candidate);
        final JsonArray placement = new JsonArray();
        candidate.pivots().forEach(pivot -> placement.add(placementJson(pivot)));
        final JsonArray rules = new JsonArray();
        for (final RuleEvidence rule : evidence) {
            final JsonObject json = new JsonObject();
            json.addProperty("id", rule.ruleId());
            json.addProperty("state", rule.state().name());
            json.add("score", rule.score().isPresent() ? new JsonPrimitive(rule.score().get()) : null);
            final JsonArray observations = new JsonArray();
            rule.observations().forEach(observations::add);
            json.add("observations", observations);
            json.addProperty("explanation", rule.explanation());
            rules.add(json);
        }
        final Map<String, Integer> versions = versionsByCandidate.computeIfAbsent(key, ignored -> new HashMap<>());
        final int version = versions.computeIfAbsent(rules.toString(), ignored -> versions.size() + 1);
        final JsonObject json = new JsonObject();
        json.addProperty("candidateKey", key);
        json.addProperty("version", key + "@v" + version);
        json.addProperty("direction", candidate.direction().name());
        json.add("placement", placement);
        json.add("rules", rules);
        return json;
    }

    /**
     * Deterministic identity of a candidate's grammar, direction and full pivot
     * placement.
     */
    static String candidateKey(final TopologyCandidate candidate) {
        final StringBuilder identity = new StringBuilder();
        identity.append(candidate.grammar().name()).append('|').append(candidate.direction().name());
        for (final ConfirmedPivot pivot : candidate.pivots()) {
            identity.append('|')
                    .append(pivot.pivotIndex())
                    .append(':')
                    .append(pivot.type().name())
                    .append(':')
                    .append(pivot.price());
        }
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(identity.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * Parsed trace file.
     *
     * @param header   header object
     * @param records  observation records in file order
     * @param complete whether the footer was present and consistent
     * @since 0.25.1
     */
    record TraceFile(JsonObject header, List<JsonObject> records, boolean complete) {
        TraceFile {
            Objects.requireNonNull(header, "header");
            records = List.copyOf(records);
        }
    }

    /**
     * Reads a trace file. A file without a footer, or whose final line is a partial
     * write, is returned with {@code complete == false}.
     *
     * @param path trace file
     * @return parsed file
     * @throws IOException              when the file cannot be read
     * @throws IllegalArgumentException naming file and line when the schema is
     *                                  unsupported or a line is corrupt
     */
    static TraceFile read(final Path path) throws IOException {
        final String[] lines = Files.readString(path, StandardCharsets.UTF_8).split("\n", -1);
        // A newline-terminated file ends with one empty element.
        final int lastLine = lines[lines.length - 1].isEmpty() ? lines.length - 1 : lines.length;
        if (lastLine == 0) {
            throw corrupt(path, 1, "missing header");
        }
        final JsonObject header = parseObject(path, 1, lines[0]);
        final JsonElement schema = header.get("schema");
        if (schema == null || !schema.isJsonPrimitive() || !SCHEMA.equals(schema.getAsString())) {
            throw corrupt(path, 1, "unsupported schema " + schema);
        }
        final List<JsonObject> records = new ArrayList<>();
        boolean complete = false;
        for (int i = 1; i < lastLine; i++) {
            final int lineNumber = i + 1;
            final boolean unterminatedTail = i == lines.length - 1;
            final JsonObject object;
            try {
                object = parseObject(path, lineNumber, lines[i]);
            } catch (final IllegalArgumentException e) {
                if (unterminatedTail) {
                    break;
                }
                throw e;
            }
            if (complete) {
                throw corrupt(path, lineNumber, "content after footer");
            }
            if (object.has("complete") && !object.has("kind")) {
                final JsonElement count = object.get("records");
                if (count == null || !count.isJsonPrimitive() || count.getAsLong() != records.size()) {
                    throw corrupt(path, lineNumber, "footer record count " + count + " but read " + records.size());
                }
                complete = object.get("complete").getAsBoolean();
            } else if (object.has("kind")) {
                records.add(object);
            } else {
                throw corrupt(path, lineNumber, "line is neither a record nor a footer");
            }
        }
        return new TraceFile(header, records, complete);
    }

    private static JsonObject parseObject(final Path path, final int lineNumber, final String text) {
        try {
            final JsonElement element = JsonParser.parseString(text);
            if (!element.isJsonObject()) {
                throw corrupt(path, lineNumber, "not a JSON object");
            }
            return element.getAsJsonObject();
        } catch (final JsonParseException | UnsupportedOperationException | IllegalStateException e) {
            throw corrupt(path, lineNumber, "invalid JSON: " + e.getMessage());
        }
    }

    private static IllegalArgumentException corrupt(final Path path, final int lineNumber, final String reason) {
        return new IllegalArgumentException(path + ": line " + lineNumber + ": " + reason);
    }
}

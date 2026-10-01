/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
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
import java.util.function.Predicate;

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
 * recognition. Each observation is written as one line as soon as it arrives
 * and the writer keeps no per-candidate state, so memory stays bounded by one
 * observation rather than by members times bars or distinct candidates. A file
 * is complete only when its footer line exists; a crashed run leaves a readable
 * but truncated file.
 * </p>
 *
 * <p>
 * Layout: a header line naming the dataset, the capture coordinates and the
 * originating run's configuration fingerprint and source digest, one object per
 * observation, then the footer {@code {"complete":true,"records":N}}.
 * </p>
 *
 * <p>
 * A candidate's identity ({@code candidateKey}) hashes grammar, direction and
 * the full pivot placement (indices, types, price text), so a placement that
 * shares only its start and end with another is a different candidate. Its
 * {@code version} is {@code <candidateKey>@<evidenceKey>}, where the evidence
 * key hashes the candidate's serialised rule evidence: identical evidence
 * always yields the same version, in any file and in any emission order, and
 * changed evidence yields a new one. Earlier lines are never rewritten.
 * Instances are not thread-safe.
 * </p>
 *
 * @since 0.25.1
 */
final class ElliottResearchTrace implements StudyObserver, Closeable {

    static final String SCHEMA = "elliott-research-trace/1";
    static final String MODE_REAL = "real";
    static final String MODE_SELECTED_NULL_MEMBER = "selected-null-member";

    private static final List<String> RECORD_TEXT_FIELDS = List.of("dataset", "section", "mode", "grammar", "detector",
            "partition", "asOfTime", "status");
    private static final List<String> RECORD_INTEGER_FIELDS = List.of("nullBlockLength", "nullMemberIndex",
            "asOfIndex");

    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();

    private final Path file;
    private final String datasetId;
    private final int nullBlockLength;
    private final int nullMemberIndex;
    private final BufferedWriter writer;
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
     * @param revision        code revision of the originating run
     * @param fingerprint     configuration fingerprint of the originating run
     * @param sourceSha256    SHA-256 of the dataset's source candles, or
     *                        {@code null} for generated data
     * @param traceMode       {@code real} or {@code selected-null-member}
     * @param nullBlockLength null block length, or {@code -1} for real traces
     * @param nullMemberIndex null member index, or {@code -1} for real traces
     * @return open trace; close it to write the footer
     * @throws IOException on filesystem failure
     */
    static ElliottResearchTrace open(final Path file, final String datasetId, final String revision,
            final String fingerprint, final String sourceSha256, final String traceMode, final int nullBlockLength,
            final int nullMemberIndex) throws IOException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(datasetId, "datasetId");
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(fingerprint, "fingerprint");
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
            header.addProperty("revision", revision);
            header.addProperty("fingerprint", fingerprint);
            header.addProperty("sourceSha256", sourceSha256);
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
        final JsonObject json = new JsonObject();
        json.addProperty("candidateKey", key);
        json.addProperty("version", key + "@" + digest(rules.toString()));
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
        return digest(identity.toString());
    }

    /** First 128 bits of the text's SHA-256, as lowercase hex. */
    private static String digest(final String text) {
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * Parsed trace file.
     *
     * @param header      header object
     * @param records     retained observation records in file order
     * @param recordCount number of observation records in the file, retained or not
     * @param complete    whether the footer was present and consistent
     * @since 0.25.1
     */
    record TraceFile(JsonObject header, List<JsonObject> records, long recordCount, boolean complete) {
        TraceFile {
            Objects.requireNonNull(header, "header");
            records = List.copyOf(records);
        }
    }

    /**
     * Reads a trace file, retaining every record.
     *
     * @param path trace file
     * @return parsed file
     * @throws IOException              when the file cannot be read
     * @throws IllegalArgumentException naming file and line when the schema is
     *                                  unsupported or a line is corrupt
     * @see #read(Path, Predicate)
     */
    static TraceFile read(final Path path) throws IOException {
        return read(path, record -> true);
    }

    /**
     * Streams a trace file line by line, validating every line but retaining only
     * the records {@code retain} accepts, so memory is bounded by the retained
     * records rather than the file size. A file without a footer, or whose final
     * unterminated line is a partial write before any footer, is returned with
     * {@code complete == false}; any content after a footer is corrupt.
     *
     * @param path   trace file
     * @param retain records to keep in {@link TraceFile#records()}
     * @return parsed file
     * @throws IOException              when the file cannot be read
     * @throws IllegalArgumentException naming file and line when the schema is
     *                                  unsupported or a line is corrupt
     */
    static TraceFile read(final Path path, final Predicate<JsonObject> retain) throws IOException {
        Objects.requireNonNull(retain, "retain");
        final boolean newlineTerminated = endsWithNewline(path);
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            final String headerLine = reader.readLine();
            if (headerLine == null) {
                throw corrupt(path, 1, "missing header");
            }
            final JsonObject header = parseObject(path, 1, headerLine);
            final JsonElement schema = header.get("schema");
            if (schema == null || !schema.isJsonPrimitive() || !SCHEMA.equals(schema.getAsString())) {
                throw corrupt(path, 1, "unsupported schema " + schema);
            }
            final List<JsonObject> records = new ArrayList<>();
            long recordCount = 0;
            boolean complete = false;
            long lineNumber = 1;
            String line = reader.readLine();
            while (line != null) {
                lineNumber++;
                final String next = reader.readLine();
                final boolean unterminatedTail = next == null && !newlineTerminated;
                final JsonObject object;
                try {
                    object = parseObject(path, lineNumber, line);
                } catch (final IllegalArgumentException e) {
                    if (unterminatedTail && !complete) {
                        break;
                    }
                    throw e;
                }
                if (complete) {
                    throw corrupt(path, lineNumber, "content after footer");
                }
                if (object.has("complete") && !object.has("kind")) {
                    final JsonElement count = object.get("records");
                    if (count == null || !count.isJsonPrimitive() || !count.getAsJsonPrimitive().isNumber()
                            || count.getAsLong() != recordCount) {
                        throw corrupt(path, lineNumber, "footer record count " + count + " but read " + recordCount);
                    }
                    final JsonElement flag = object.get("complete");
                    if (!flag.isJsonPrimitive() || !flag.getAsJsonPrimitive().isBoolean()) {
                        throw corrupt(path, lineNumber, "footer complete flag " + flag + " is not a boolean");
                    }
                    complete = flag.getAsBoolean();
                } else if (object.has("kind")) {
                    validateRecord(path, lineNumber, object);
                    recordCount++;
                    if (retain.test(object)) {
                        records.add(object);
                    }
                } else {
                    throw corrupt(path, lineNumber, "line is neither a record nor a footer");
                }
                line = next;
            }
            return new TraceFile(header, records, recordCount, complete);
        }
    }

    private static boolean endsWithNewline(final Path path) throws IOException {
        try (SeekableByteChannel channel = Files.newByteChannel(path)) {
            final long size = channel.size();
            if (size == 0) {
                return false;
            }
            channel.position(size - 1);
            final ByteBuffer last = ByteBuffer.allocate(1);
            return channel.read(last) == 1 && last.get(0) == '\n';
        }
    }

    private static JsonObject parseObject(final Path path, final long lineNumber, final String text) {
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

    /**
     * Rejects an observation line missing a field {@link #base} or the kind's
     * writer always emits, so a hand-edited or foreign line cannot count toward the
     * footer or feed inspection as if it were a recorded observation.
     */
    private static void validateRecord(final Path path, final long lineNumber, final JsonObject record) {
        final String kind = text(record, "kind");
        if (!"topology".equals(kind) && !"alternative".equals(kind)) {
            throw corrupt(path, lineNumber, "record kind " + record.get("kind") + " is not topology or alternative");
        }
        for (final String field : RECORD_TEXT_FIELDS) {
            if (text(record, field) == null) {
                throw corrupt(path, lineNumber, kind + " record field " + field + " is missing or not a string");
            }
        }
        for (final String field : RECORD_INTEGER_FIELDS) {
            final JsonElement value = record.get(field);
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                    || value.getAsBigDecimal().stripTrailingZeros().scale() > 0) {
                throw corrupt(path, lineNumber, kind + " record field " + field + " is missing or not an integer");
            }
        }
        final List<String> arrays = "topology".equals(kind) ? List.of("activeRules", "pivots", "candidates")
                : List.of("activeRules", "pivots", "candidates", "labels");
        for (final String field : arrays) {
            final JsonElement value = record.get(field);
            if (value == null || !value.isJsonArray()) {
                throw corrupt(path, lineNumber, kind + " record field " + field + " is missing or not an array");
            }
        }
    }

    private static String text(final JsonObject record, final String field) {
        final JsonElement value = record.get(field);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString()
                : null;
    }

    private static IllegalArgumentException corrupt(final Path path, final long lineNumber, final String reason) {
        return new IllegalArgumentException(path + ": line " + lineNumber + ": " + reason);
    }
}

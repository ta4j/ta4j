/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Consumer;

import org.ta4j.core.BarSeries;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * Research-run artifact for explicit parent/child scale relations.
 *
 * <p>
 * A run whose recipe declares a {@code hierarchy} writes one sidecar file per
 * dataset, {@code relations/<dataset>.jsonl}: a header, one {@code frame} line
 * per observation that changed the relation set or its extraction bookkeeping,
 * and a footer. Frames carry immutable lifecycle events; a later frame never
 * rewrites an earlier one, so the active relation set at any as-of index is
 * recovered by replaying frames up to that index. Parent and child versions are
 * the {@link ElliottResearchTrace#candidateVersion topology-only candidate
 * versions}, so an edge's candidate keys join any observation-trace record of
 * the same run.
 *
 * <p>
 * The file is a research artifact, not a released API; the run's observation
 * trace, comparison rows and reports are unchanged by it.
 */
final class ElliottResearchRelations {

    static final String SCHEMA = "elliott-research-relations/1";
    static final String RELATIONS_DIR = "relations";

    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();

    private ElliottResearchRelations() {
    }

    static String fileName(final String datasetId) {
        return RELATIONS_DIR + "/" + datasetId + ".jsonl";
    }

    /**
     * One declared scale of a recipe hierarchy.
     *
     * @param detector  name of a configured detector (primary or robustness)
     * @param degree    optional label, recorded as metadata only
     * @param dataset   optional dataset the scale claims to read, must equal the
     *                  hierarchy's dataset
     * @param timeframe optional bar period the scale claims to read, must equal the
     *                  series' bar period
     */
    record HierarchyScale(String detector, String degree, String dataset, Duration timeframe) {
        HierarchyScale {
            Objects.requireNonNull(detector, "detector");
        }
    }

    /**
     * An explicit, ordered (coarse to fine) scale chain.
     *
     * @param scales   two or three declared scales
     * @param interior interior-anchor policy
     * @param edgeCap  retained edges per observation
     */
    record Hierarchy(List<HierarchyScale> scales, ScaleRelation.Interior interior, int edgeCap) {
        Hierarchy {
            scales = List.copyOf(scales);
            Objects.requireNonNull(interior, "interior");
            if (scales.size() < 2 || scales.size() > ScaleRelation.MAX_SCALES) {
                throw new IllegalArgumentException("recipe.hierarchy.scales needs 2 to " + ScaleRelation.MAX_SCALES
                        + " entries but has " + scales.size());
            }
            final List<String> seen = new ArrayList<>();
            for (final HierarchyScale scale : scales) {
                if (seen.contains(scale.detector())) {
                    throw new IllegalArgumentException("recipe.hierarchy.scales names detector '" + scale.detector()
                            + "' twice; a scale cannot be related to itself");
                }
                seen.add(scale.detector());
            }
            if (edgeCap < 1) {
                throw new IllegalArgumentException("recipe.hierarchy.edgeCap must be positive, was " + edgeCap);
            }
        }

        /**
         * Checks the declared timeframes against the series the study will read, before
         * any detector runs.
         */
        void validateSeries(final String datasetId, final BarSeries series) {
            for (int i = 0; i < scales.size(); i++) {
                final Duration declared = scales.get(i).timeframe();
                if (declared == null || series.isEmpty()) {
                    continue;
                }
                final Duration actual = series.getBar(series.getBeginIndex()).getTimePeriod();
                if (!declared.equals(actual)) {
                    throw new IllegalArgumentException("recipe.hierarchy.scales[" + i + "].timeframe " + declared
                            + " does not match the bar period " + actual + " of dataset '" + datasetId
                            + "'; scales are not resampled or aligned automatically");
                }
            }
        }

        JsonObject toJson() {
            final JsonObject json = new JsonObject();
            final JsonArray array = new JsonArray();
            for (int i = 0; i < scales.size(); i++) {
                final HierarchyScale scale = scales.get(i);
                final JsonObject entry = new JsonObject();
                entry.addProperty("name", scale.detector());
                entry.addProperty("rank", i);
                entry.addProperty("degree", scale.degree());
                entry.addProperty("timeframe", scale.timeframe() == null ? null : scale.timeframe().toString());
                array.add(entry);
            }
            json.add("scales", array);
            json.addProperty("interiorAnchors", interior.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'));
            json.addProperty("edgeCap", edgeCap);
            return json;
        }
    }

    /** Exact totals of one written relation file. */
    record Totals(long frames, long events, long truncatedFrames, long incompleteFrames, int peakRetainedEdges) {
    }

    // ---------------------------------------------------------------- write

    /**
     * Replays the declared scales over {@code [start, end]} and writes the relation
     * file.
     *
     * @return exact totals, also recorded in the footer
     * @throws IOException on filesystem failure
     */
    static Totals write(final Path file, final String datasetId, final String revision, final String fingerprint,
            final String sourceSha256, final Hierarchy hierarchy, final List<ScaleRelationStudy.ScaleInput> inputs,
            final List<RelationshipRule> childRules, final StudyRunner.Partitions partitions, final BarSeries series,
            final int start, final int end, final DetectorReplays replays) throws IOException {
        final ScaleRelation.Policy policy = ScaleRelation.Policy.defaults()
                .withInterior(hierarchy.interior())
                .withEdgeCap(hierarchy.edgeCap());
        final ScaleRelationStudy study = new ScaleRelationStudy(inputs, policy, childRules, identity(), partitions);
        final Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        final long[] counts = new long[4];
        final int[] peak = new int[1];
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            final JsonObject header = new JsonObject();
            header.addProperty("schema", SCHEMA);
            header.addProperty("dataset", datasetId);
            header.addProperty("revision", revision);
            header.addProperty("fingerprint", fingerprint);
            header.addProperty("sourceSha256", sourceSha256);
            final JsonObject declared = hierarchy.toJson();
            declared.entrySet().forEach(entry -> header.add(entry.getKey(), entry.getValue()));
            header.addProperty("parentGrammar", policy.parentGrammar().name());
            final JsonArray rules = new JsonArray();
            childRules.forEach(rule -> rules.add(rule.id()));
            header.add("childRules", rules);
            writeLine(writer, header);
            try {
                study.run(series, start, end, replays, frame -> {
                    try {
                        writeLine(writer, frameJson(datasetId, frame));
                    } catch (final IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    counts[0]++;
                    counts[1] += frame.events().size();
                    counts[2] += frame.coverage().truncated() ? 1 : 0;
                    counts[3] += frame.coverage().incomplete() ? 1 : 0;
                    peak[0] = Math.max(peak[0], frame.coverage().edgesRetained());
                });
            } catch (final UncheckedIOException e) {
                throw e.getCause();
            }
            final JsonObject footer = new JsonObject();
            footer.addProperty("complete", true);
            footer.addProperty("frames", counts[0]);
            footer.addProperty("events", counts[1]);
            footer.addProperty("truncatedFrames", counts[2]);
            footer.addProperty("incompleteFrames", counts[3]);
            footer.addProperty("peakRetainedEdges", peak[0]);
            writeLine(writer, footer);
        }
        return new Totals(counts[0], counts[1], counts[2], counts[3], peak[0]);
    }

    static ScaleRelationExtractor.Identity identity() {
        return new ScaleRelationExtractor.Identity() {
            @Override
            public String key(final TopologyCandidate candidate) {
                return ElliottResearchTrace.candidateKey(candidate);
            }

            @Override
            public String version(final TopologyCandidate candidate, final List<RuleEvidence> evidence) {
                return ElliottResearchTrace.candidateVersion(candidate, evidence);
            }
        };
    }

    static void writeLine(final BufferedWriter writer, final JsonObject line) throws IOException {
        JSON.toJson(line, writer);
        writer.write('\n');
    }

    static JsonObject frameJson(final String datasetId, final ScaleRelationStudy.Frame frame) {
        final JsonObject json = new JsonObject();
        json.addProperty("kind", "frame");
        json.addProperty("dataset", datasetId);
        json.addProperty("partition", frame.partition());
        json.addProperty("asOfIndex", frame.asOfIndex());
        json.addProperty("asOfTime", frame.asOfTime().toString());
        json.add("coverage", coverageJson(frame.coverage()));
        final JsonArray events = new JsonArray();
        for (final ScaleRelation.Event event : frame.events()) {
            final JsonObject entry = new JsonObject();
            entry.addProperty("lifecycle", event.lifecycle().name().toLowerCase(java.util.Locale.ROOT));
            entry.addProperty("reason", event.reason().name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'));
            entry.add("edge", edgeJson(event.edge()));
            events.add(entry);
        }
        json.add("events", events);
        return json;
    }

    static JsonObject edgeJson(final ScaleRelation.Edge edge) {
        final JsonObject json = new JsonObject();
        json.addProperty("key", edge.key());
        json.addProperty("version", edge.version());
        json.addProperty("parentScale", edge.parentScale());
        json.addProperty("childScale", edge.childScale());
        json.addProperty("parentCandidateKey", edge.parentCandidateKey());
        json.addProperty("parentVersion", edge.parentVersion());
        json.addProperty("parentGrammar", edge.parentGrammar().name());
        json.addProperty("parentDirection", edge.parentDirection().name());
        json.addProperty("parentLeg", edge.parentLeg());
        json.add("parentStart", pivotJson(edge.parentStart()));
        json.add("parentEnd", pivotJson(edge.parentEnd()));
        json.addProperty("state", edge.state().label());
        json.addProperty("childGrammar", edge.childGrammar() == null ? null : edge.childGrammar().name());
        json.addProperty("childCandidateKey", edge.childCandidateKey());
        json.addProperty("childVersion", edge.childVersion());
        final JsonArray pivots = new JsonArray();
        edge.childPivots().forEach(pivot -> pivots.add(pivotJson(pivot)));
        json.add("childPivots", pivots);
        json.addProperty("childPivotCount", edge.childPivotCount());
        json.add("predicates", predicatesJson(edge.predicates()));
        json.addProperty("availableAt", edge.availableAt());
        return json;
    }

    static JsonObject pivotJson(final ConfirmedPivot pivot) {
        final JsonObject json = new JsonObject();
        json.addProperty("index", pivot.pivotIndex());
        json.addProperty("price", pivot.price().toString());
        json.addProperty("type", pivot.type().name());
        json.addProperty("confirmationIndex", pivot.confirmationIndex());
        return json;
    }

    static JsonObject coverageJson(final ScaleRelation.Coverage coverage) {
        final JsonObject json = new JsonObject();
        json.addProperty("parentCandidates", coverage.parentCandidates());
        json.addProperty("legsChecked", coverage.legsChecked());
        json.addProperty("edgesGenerated", coverage.edgesGenerated());
        json.addProperty("edgesRetained", coverage.edgesRetained());
        json.addProperty("edgesOmitted", coverage.edgesOmitted());
        json.addProperty("edgeCap", coverage.edgeCap());
        json.addProperty("decompositionLegsTruncated", coverage.decompositionLegsTruncated());
        json.addProperty("truncated", coverage.truncated());
        json.addProperty("incomplete", coverage.incomplete());
        return json;
    }

    static JsonArray predicatesJson(final List<ScaleRelation.Predicate> predicates) {
        final JsonArray array = new JsonArray();
        for (final ScaleRelation.Predicate predicate : predicates) {
            final JsonObject entry = new JsonObject();
            entry.addProperty("id", predicate.id());
            entry.addProperty("state", predicate.state().name());
            entry.addProperty("detail", predicate.detail());
            array.add(entry);
        }
        return array;
    }

    // ----------------------------------------------------------------- read

    /** Header, footer and counts of one relation file; frames go to a sink. */
    record Meta(JsonObject header, JsonObject footer, long frames, boolean complete) {
    }

    /**
     * Streams a relation file, validating every line and handing each frame to
     * {@code sink}; memory is bounded by what the sink retains.
     *
     * @throws IllegalArgumentException naming file and line when the schema is
     *                                  unsupported or a line is corrupt
     */
    static Meta read(final Path path, final Consumer<JsonObject> sink) throws IOException {
        return read(path, SCHEMA, ElliottResearchRelations::requireFrame, sink);
    }

    /** Checks one frame line's shape, throwing {@link IllegalArgumentException}. */
    @FunctionalInterface
    interface FrameCheck {
        void check(Path path, long line, JsonObject frame);
    }

    /**
     * Streams a header/frame/footer artifact of {@code schema}; shared by the
     * artifacts that follow the relation file's line protocol.
     */
    static Meta read(final Path path, final String schema, final FrameCheck frameCheck, final Consumer<JsonObject> sink)
            throws IOException {
        final boolean newlineTerminated = endsWithNewline(path);
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            final String headerLine = reader.readLine();
            if (headerLine == null) {
                throw corrupt(path, 1, "missing header");
            }
            final JsonObject header = parse(path, 1, headerLine);
            final JsonElement declared = header.get("schema");
            if (declared == null || !declared.isJsonPrimitive() || !schema.equals(declared.getAsString())) {
                throw corrupt(path, 1, "unsupported schema " + declared);
            }
            JsonObject footer = null;
            long frames = 0;
            long lineNumber = 1;
            String line = reader.readLine();
            while (line != null) {
                lineNumber++;
                final String next = reader.readLine();
                final JsonObject object;
                try {
                    object = parse(path, lineNumber, line);
                } catch (final IllegalArgumentException e) {
                    if (next == null && !newlineTerminated && footer == null) {
                        break;
                    }
                    throw e;
                }
                if (footer != null) {
                    throw corrupt(path, lineNumber, "content after footer");
                }
                if ("frame".equals(text(object, "kind"))) {
                    frameCheck.check(path, lineNumber, object);
                    frames++;
                    sink.accept(object);
                } else if (object.has("complete") && !object.has("kind")) {
                    final JsonElement count = object.get("frames");
                    if (count == null || !count.isJsonPrimitive() || count.getAsLong() != frames
                            || !object.get("complete").isJsonPrimitive()
                            || !object.get("complete").getAsJsonPrimitive().isBoolean()
                            || !object.get("complete").getAsBoolean()) {
                        throw corrupt(path, lineNumber, "footer disagrees with the " + frames + " frames read");
                    }
                    footer = object;
                } else {
                    throw corrupt(path, lineNumber, "line is neither a frame nor a footer");
                }
                line = next;
            }
            return new Meta(header, footer, frames, footer != null);
        }
    }

    private static void requireFrame(final Path path, final long line, final JsonObject frame) {
        for (final String field : List.of("asOfIndex", "partition", "coverage", "events")) {
            if (!frame.has(field) || frame.get(field).isJsonNull()) {
                throw corrupt(path, line, "frame lacks " + field);
            }
        }
        try {
            frame.get("asOfIndex").getAsInt();
            frame.getAsJsonObject("coverage");
            for (final JsonElement event : frame.getAsJsonArray("events")) {
                final JsonObject object = event.getAsJsonObject();
                object.getAsJsonObject("edge").get("key").getAsString();
                object.get("lifecycle").getAsString();
            }
        } catch (final RuntimeException e) {
            throw corrupt(path, line, "malformed frame: " + e.getMessage());
        }
    }

    static String text(final JsonObject object, final String key) {
        final JsonElement element = object.get(key);
        return element == null || !element.isJsonPrimitive() ? null : element.getAsString();
    }

    static JsonObject parse(final Path path, final long line, final String text) {
        try {
            final JsonElement element = JsonParser.parseString(text);
            if (!element.isJsonObject()) {
                throw corrupt(path, line, "not a JSON object");
            }
            return element.getAsJsonObject();
        } catch (final JsonParseException | UnsupportedOperationException | IllegalStateException e) {
            throw corrupt(path, line, "invalid JSON: " + e.getMessage());
        }
    }

    static IllegalArgumentException corrupt(final Path path, final long line, final String message) {
        return new IllegalArgumentException(path + ": line " + line + ": " + message);
    }

    /** Reads only the final byte, so the check costs O(1) memory and I/O. */
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

    // --------------------------------------------------------------- replay

    /** Active relation edges at one as-of index and the evidence behind them. */
    record Replay(Integer cursor, String cursorTime, JsonObject coverage, Map<String, JsonObject> active,
            Map<String, Integer> observedByState, Map<String, Integer> endedByReason, List<JsonObject> history,
            long truncatedFrames, long incompleteFrames) {
    }

    /**
     * Replays frames up to and including {@code asOf}.
     *
     * @param asOf    last as-of index to include, or {@code null} for the whole
     *                file
     * @param edgeKey edge whose events are collected into {@link Replay#history()},
     *                or {@code null}
     */
    static Replay replay(final Path path, final Integer asOf, final String edgeKey) throws IOException {
        final Map<String, JsonObject> active = new TreeMap<>();
        final Map<String, Integer> observed = new TreeMap<>();
        final Map<String, Integer> ended = new TreeMap<>();
        final List<JsonObject> history = new ArrayList<>();
        final Object[] cursor = new Object[3];
        final long[] flags = new long[2];
        read(path, frame -> {
            final int index = frame.get("asOfIndex").getAsInt();
            if (asOf != null && index > asOf) {
                return;
            }
            cursor[0] = index;
            cursor[1] = text(frame, "asOfTime");
            cursor[2] = frame.getAsJsonObject("coverage");
            final JsonObject coverage = frame.getAsJsonObject("coverage");
            flags[0] += coverage.get("truncated").getAsBoolean() ? 1 : 0;
            flags[1] += coverage.get("incomplete").getAsBoolean() ? 1 : 0;
            for (final JsonElement element : frame.getAsJsonArray("events")) {
                final JsonObject event = element.getAsJsonObject();
                final JsonObject edge = event.getAsJsonObject("edge");
                final String key = edge.get("key").getAsString();
                if ("ended".equals(event.get("lifecycle").getAsString())) {
                    active.remove(key);
                    ended.merge(event.get("reason").getAsString(), 1, Integer::sum);
                } else {
                    active.put(key, edge);
                    if ("observed".equals(event.get("reason").getAsString())) {
                        observed.merge(edge.get("state").getAsString(), 1, Integer::sum);
                    }
                }
                if (key.equals(edgeKey)) {
                    final JsonObject entry = event.deepCopy();
                    entry.addProperty("asOfIndex", index);
                    history.add(entry);
                }
            }
        });
        return new Replay((Integer) cursor[0], (String) cursor[1], (JsonObject) cursor[2], active, observed, ended,
                history, flags[0], flags[1]);
    }

    // --------------------------------------------------------------- render

    /**
     * Prints the relation set at an as-of index.
     *
     * @param out     destination
     * @param path    relation file
     * @param asOf    as-of index, or {@code null} for the last recorded frame
     * @param limit   most edges printed
     * @param edgeKey edge whose full event history is also printed, or {@code null}
     * @return {@code false} when the file is incomplete
     */
    static boolean print(final PrintStream out, final Path path, final Integer asOf, final int limit,
            final String edgeKey) throws IOException {
        final Meta meta = read(path, frame -> {
        });
        final Replay replay = replay(path, asOf, edgeKey);
        out.println("Relations: " + path);
        out.println("Dataset: " + text(meta.header(), "dataset") + "  scales: " + scaleNames(meta.header())
                + "  interior: " + text(meta.header(), "interiorAnchors") + "  edge cap: "
                + meta.header().get("edgeCap").getAsInt());
        if (!meta.complete()) {
            out.println("Note: the file has no footer; the run was interrupted and the relations are incomplete.");
        }
        if (replay.cursor() == null) {
            out.println("No relation frame at or before index " + asOf + "; no relation was observable yet.");
            return meta.complete();
        }
        out.println("As of index " + replay.cursor() + " (" + replay.cursorTime() + "): " + replay.active().size()
                + " active edge(s)");
        final JsonObject coverage = replay.coverage();
        out.println("Coverage: parentCandidates=" + coverage.get("parentCandidates").getAsInt() + " legsChecked="
                + coverage.get("legsChecked").getAsInt() + " generated=" + coverage.get("edgesGenerated").getAsInt()
                + " retained=" + coverage.get("edgesRetained").getAsInt() + " omitted="
                + coverage.get("edgesOmitted").getAsInt() + " cap=" + coverage.get("edgeCap").getAsInt()
                + " decompositionLegsTruncated=" + coverage.get("decompositionLegsTruncated").getAsInt());
        if (coverage.get("incomplete").getAsBoolean()) {
            out.println("Note: a bound was hit at this observation; absent edges are not proof of absent structure.");
        }
        final Map<String, Integer> byState = new TreeMap<>();
        replay.active().values().forEach(edge -> byState.merge(edge.get("state").getAsString(), 1, Integer::sum));
        out.println("Active by state: " + (byState.isEmpty() ? "(none)" : byState));
        final List<JsonObject> edges = replay.active()
                .values()
                .stream()
                .sorted(Comparator.comparing((JsonObject edge) -> edge.get("parentScale").getAsString())
                        .thenComparing(edge -> edge.get("parentStart").getAsJsonObject().get("index").getAsInt())
                        .thenComparing(edge -> edge.get("parentLeg").getAsInt())
                        .thenComparing(edge -> edge.get("key").getAsString()))
                .toList();
        int printed = 0;
        for (final JsonObject edge : edges) {
            if (printed++ >= limit) {
                out.println("... " + (edges.size() - limit) + " more (raise --limit)");
                break;
            }
            out.println(describe(edge));
            evidence(edge, "      ").forEach(out::println);
        }
        if (edgeKey != null) {
            out.println("History of edge " + edgeKey + ":");
            if (replay.history().isEmpty()) {
                out.println("  (no event for this edge up to the cursor)");
            }
            for (final JsonObject event : replay.history()) {
                final JsonObject edge = event.getAsJsonObject("edge");
                out.println("  @" + event.get("asOfIndex").getAsInt() + " " + event.get("lifecycle").getAsString() + " "
                        + event.get("reason").getAsString() + " state=" + edge.get("state").getAsString() + " version="
                        + edge.get("version").getAsString() + " availableAt=" + edge.get("availableAt").getAsInt());
                evidence(edge, "      ").forEach(out::println);
            }
        }
        return meta.complete();
    }

    private static String describe(final JsonObject edge) {
        final JsonObject start = edge.getAsJsonObject("parentStart");
        final JsonObject end = edge.getAsJsonObject("parentEnd");
        final StringBuilder line = new StringBuilder("  ").append(edge.get("key").getAsString())
                .append(' ')
                .append(edge.get("state").getAsString())
                .append(" parent ")
                .append(edge.get("parentScale").getAsString())
                .append(" leg ")
                .append(edge.get("parentLeg").getAsInt() + 1)
                .append(" [")
                .append(start.get("index").getAsInt())
                .append("..")
                .append(end.get("index").getAsInt())
                .append("] ")
                .append(edge.get("parentDirection").getAsString())
                .append(" child ")
                .append(edge.get("childScale").getAsString());
        final JsonElement grammar = edge.get("childGrammar");
        if (grammar != null && !grammar.isJsonNull()) {
            line.append(' ').append(grammar.getAsString());
        }
        line.append(" pivots=").append(edge.get("childPivotCount").getAsInt());
        line.append(" availableAt=").append(edge.get("availableAt").getAsInt());
        final List<String> notable = new ArrayList<>();
        for (final JsonElement element : edge.getAsJsonArray("predicates")) {
            final JsonObject predicate = element.getAsJsonObject();
            final String state = predicate.get("state").getAsString();
            if ("FAIL".equals(state) || "PENDING".equals(state) || "UNAVAILABLE".equals(state)) {
                notable.add(predicate.get("id").getAsString() + "=" + state);
            }
        }
        if (!notable.isEmpty()) {
            line.append(" [").append(String.join(", ", notable)).append(']');
        }
        return line.toString();
    }

    /**
     * Evidence lines of one stored edge version: the child pivot sequence with each
     * pivot's confirmation time, then every predicate with its explanation.
     */
    private static List<String> evidence(final JsonObject edge, final String indent) {
        final List<String> lines = new ArrayList<>();
        final JsonArray pivots = edge.getAsJsonArray("childPivots");
        final int count = edge.get("childPivotCount").getAsInt();
        final List<String> rendered = new ArrayList<>();
        for (final JsonElement element : pivots) {
            final JsonObject pivot = element.getAsJsonObject();
            rendered.add(pivot.get("index").getAsInt() + " " + pivot.get("type").getAsString() + " "
                    + pivot.get("price").getAsString() + " (confirmed @" + pivot.get("confirmationIndex").getAsInt()
                    + ")");
        }
        lines.add(indent + "child pivots: " + (rendered.isEmpty() ? "(none)" : String.join(" -> ", rendered)));
        if (count > pivots.size()) {
            lines.add(indent + "note: " + count + " child pivots rely on this edge but only the first " + pivots.size()
                    + " are stored (limit " + ScaleRelation.MAX_STORED_CHILD_PIVOTS
                    + "); the sequence above is truncated");
        }
        for (final JsonElement element : edge.getAsJsonArray("predicates")) {
            final JsonObject predicate = element.getAsJsonObject();
            lines.add(indent + "predicate " + predicate.get("id").getAsString() + " "
                    + predicate.get("state").getAsString() + ": " + predicate.get("detail").getAsString());
        }
        return lines;
    }

    private static String scaleNames(final JsonObject header) {
        final List<String> names = new ArrayList<>();
        for (final JsonElement element : header.getAsJsonArray("scales")) {
            names.add(element.getAsJsonObject().get("name").getAsString());
        }
        return String.join(" > ", names);
    }

    /**
     * Markdown lines summarising one dataset's relation file for
     * {@code summary.md}. The file is cross-checked against the run before any
     * claim is made.
     *
     * @param expectedHeader header fields the file must carry to belong to the run
     */
    static List<String> summaryLines(final Path path, final String datasetId, final JsonObject expectedHeader) {
        final List<String> lines = new ArrayList<>();
        try {
            final Meta meta = read(path, frame -> {
            });
            for (final Map.Entry<String, JsonElement> field : expectedHeader.entrySet()) {
                final JsonElement actual = meta.header().get(field.getKey());
                if (!field.getValue().equals(actual == null ? JsonNull.INSTANCE : actual)) {
                    lines.add("- `" + datasetId + "`: relation file does not belong to this run (header "
                            + field.getKey() + " differs); rerun the recipe");
                    return lines;
                }
            }
            if (!meta.complete()) {
                lines.add("- `" + datasetId + "`: relation file is incomplete (no footer); rerun the recipe");
                return lines;
            }
            final Replay replay = replay(path, null, null);
            final JsonObject footer = meta.footer();
            lines.add("- `" + datasetId + "`: " + scaleNames(meta.header()) + ", " + meta.frames() + " frame(s), "
                    + footer.get("events").getAsLong() + " event(s); first observed by state "
                    + (replay.observedByState().isEmpty() ? "(none)" : replay.observedByState()) + "; ended by reason "
                    + (replay.endedByReason().isEmpty() ? "(none)" : replay.endedByReason())
                    + "; frames with omitted edges " + footer.get("truncatedFrames").getAsLong()
                    + ", with an unexamined bound " + footer.get("incompleteFrames").getAsLong()
                    + "; peak retained edges " + footer.get("peakRetainedEdges").getAsInt() + " of cap "
                    + meta.header().get("edgeCap").getAsInt());
        } catch (final IOException | IllegalArgumentException unreadable) {
            lines.add("- `" + datasetId + "`: relation file unreadable (" + unreadable.getMessage()
                    + "); rerun the recipe");
        }
        return lines;
    }
}

/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ta4j.core.BarSeries;

class ElliottResearchRelationsTest {

    private static final int BARS = 60;

    @TempDir
    Path directory;

    private Path file;
    private ElliottResearchRelations.Hierarchy hierarchy;
    private ElliottResearchRelations.Totals totals;

    @BeforeEach
    void write() throws IOException {
        file = directory.resolve(ElliottResearchRelations.fileName("toy"));
        hierarchy = new ElliottResearchRelations.Hierarchy(
                List.of(new ElliottResearchRelations.HierarchyScale("parent", "intermediate", null, Duration.ofDays(1)),
                        new ElliottResearchRelations.HierarchyScale("child", "minor", null, null)),
                ScaleRelation.Interior.CONTIGUOUS, 20);
        final BarSeries series = ScaleRelationFixtures.series(BARS);
        hierarchy.validateSeries("toy", series);
        totals = ElliottResearchRelations.write(file, "toy", "rev", "fp", "sha", hierarchy, List.of(
                ScaleRelationFixtures.input("parent", ScaleRelationFixtures.PARENT, ScaleRelationFixtures.PARENT_LAG),
                ScaleRelationFixtures.input("child", ScaleRelationFixtures.CHILD, ScaleRelationFixtures.CHILD_LAG)),
                ScaleRelationFixtures.passingRules(), ScaleRelationFixtures.PARTITIONS, series, 0, BARS - 1,
                DetectorReplays.uncached());
    }

    @Test
    void fileLivesUnderTheRelationsDirectory() {
        assertEquals("relations/toy.jsonl", ElliottResearchRelations.fileName("toy"));
    }

    @Test
    void footerTotalsMatchWhatTheReaderCounts() throws IOException {
        assertTrue(totals.frames() > 0 && totals.events() > 0, totals.toString());
        final long[] events = new long[1];
        final ElliottResearchRelations.Meta meta = ElliottResearchRelations.read(file,
                frame -> events[0] += frame.getAsJsonArray("events").size());
        assertTrue(meta.complete());
        assertEquals(totals.frames(), meta.frames());
        assertEquals(totals.events(), events[0]);
        assertEquals(totals.events(), meta.footer().get("events").getAsLong());
        assertEquals("elliott-research-relations/1", meta.header().get("schema").getAsString());
        assertEquals("fp", meta.header().get("fingerprint").getAsString());
        assertEquals("parent",
                meta.header().getAsJsonArray("scales").get(0).getAsJsonObject().get("name").getAsString());
        assertEquals("PT24H",
                meta.header().getAsJsonArray("scales").get(0).getAsJsonObject().get("timeframe").getAsString());
        assertEquals("contiguous", meta.header().get("interiorAnchors").getAsString());
    }

    @Test
    void replayStopsAtTheRequestedAsOfIndexAndHidesLaterEvidence() throws IOException {
        assertNull(ElliottResearchRelations.replay(file, ScaleRelationFixtures.PARENT_COMPLETE - 1, null).cursor(),
                "no relation is observable before the parent completes");
        final ElliottResearchRelations.Replay early = ElliottResearchRelations.replay(file,
                ScaleRelationFixtures.PARENT_COMPLETE, null);
        final ElliottResearchRelations.Replay all = ElliottResearchRelations.replay(file, null, null);
        assertEquals(ScaleRelationFixtures.PARENT_COMPLETE, early.cursor());
        assertTrue(all.cursor() >= early.cursor());
        assertFalse(all.observedByState().isEmpty());
        assertNull(ElliottResearchRelations.replay(file, -1, null).cursor());
    }

    @Test
    void edgeHistoryShowsEveryLifecycleEventOfOneEdge() throws IOException {
        final ElliottResearchRelations.Replay all = ElliottResearchRelations.replay(file, null, null);
        final String key = all.active().keySet().iterator().next();
        final ElliottResearchRelations.Replay traced = ElliottResearchRelations.replay(file, null, key);
        assertFalse(traced.history().isEmpty());
        assertEquals("active", traced.history().get(0).get("lifecycle").getAsString());
        assertEquals("observed", traced.history().get(0).get("reason").getAsString());

        final String printed = printed(null, 1, key);
        assertTrue(printed.contains("History of edge " + key), printed);
        assertTrue(printed.contains("more (raise --limit)"), printed);
    }

    @Test
    void printReportsCursorCoverageAndStates() throws IOException {
        final String printed = printed(null, 100, null);
        assertTrue(printed.contains("Dataset: toy  scales: parent > child"), printed);
        assertTrue(printed.contains("Coverage: parentCandidates="), printed);
        assertTrue(printed.contains("Active by state:"), printed);

        final String before = printed(-1, 10, null);
        assertTrue(before.contains("no relation was observable yet"), before);
    }

    @Test
    void printShowsChildPivotsConfirmationsAndPredicateDetails() throws IOException {
        final String printed = printed(null, 100, null);

        assertTrue(printed.contains("child pivots: 0 LOW 100"), printed);
        assertTrue(printed.contains("(confirmed @"), printed);
        assertTrue(printed.contains("predicate "), printed);
        assertFalse(printed.contains("only the first"), printed);
    }

    @Test
    void printNamesTruncatedChildPivotSequences() throws IOException {
        final List<String> lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
        boolean changed = false;
        for (int index = 0; index < lines.size() && !changed; index++) {
            final JsonObject line = JsonParser.parseString(lines.get(index)).getAsJsonObject();
            if (!line.has("kind") || !"frame".equals(line.get("kind").getAsString())) {
                continue;
            }
            for (final var event : line.getAsJsonArray("events")) {
                final JsonObject edge = event.getAsJsonObject().getAsJsonObject("edge");
                edge.addProperty("childPivotCount", edge.getAsJsonArray("childPivots").size() + 7);
                changed = true;
            }
            lines.set(index, line.toString());
        }
        assertTrue(changed);
        Files.write(file, lines, StandardCharsets.UTF_8);

        final String printed = printed(null, 100, null);

        assertTrue(printed.contains("only the first"), printed);
        assertTrue(printed.contains("truncated"), printed);
    }

    @Test
    void newlineTerminatedFileWithAGarbledLastLineIsCorruptNotTorn() throws IOException {
        final List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        lines.set(lines.size() - 1, "{\"complete\":tru");
        Files.write(file, lines, StandardCharsets.UTF_8);

        assertTrue(Files.readString(file, StandardCharsets.UTF_8).endsWith("\n"));
        assertThrows(IllegalArgumentException.class, () -> ElliottResearchRelations.read(file, frame -> {
        }));
    }

    @Test
    void tornTrailingLineMarksTheFileIncompleteButCompleteFilesStayReadable() throws IOException {
        final List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        final String torn = String.join("\n", lines.subList(0, lines.size() - 1)) + "\n{\"kind\":\"fra";
        Files.writeString(file, torn, StandardCharsets.UTF_8);
        final ElliottResearchRelations.Meta meta = ElliottResearchRelations.read(file, frame -> {
        });
        assertFalse(meta.complete());
        assertEquals(totals.frames(), meta.frames());
        assertTrue(printed(null, 3, null).contains("no footer"));
    }

    @Test
    void corruptionIsRejectedWithFileAndLine() throws IOException {
        final List<String> lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));

        final List<String> middle = new ArrayList<>(lines);
        middle.set(2, "not json");
        Files.write(file, middle, StandardCharsets.UTF_8);
        final IllegalArgumentException invalid = assertThrows(IllegalArgumentException.class,
                () -> ElliottResearchRelations.read(file, frame -> {
                }));
        assertTrue(invalid.getMessage().contains("line 3"), invalid.getMessage());

        final JsonObject header = JsonParser.parseString(lines.get(0)).getAsJsonObject();
        header.addProperty("schema", "elliott-research-relations/0");
        final List<String> oldSchema = new ArrayList<>(lines);
        oldSchema.set(0, header.toString());
        Files.write(file, oldSchema, StandardCharsets.UTF_8);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> ElliottResearchRelations.read(file, frame -> {
        })).getMessage().contains("unsupported schema"));

        final List<String> afterFooter = new ArrayList<>(lines);
        afterFooter.add(lines.get(1));
        Files.write(file, afterFooter, StandardCharsets.UTF_8);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> ElliottResearchRelations.read(file, frame -> {
        })).getMessage().contains("content after footer"));

        final List<String> dropped = new ArrayList<>(lines);
        dropped.remove(1);
        Files.write(file, dropped, StandardCharsets.UTF_8);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> ElliottResearchRelations.read(file, frame -> {
        })).getMessage().contains("footer disagrees"));

        Files.writeString(file, "", StandardCharsets.UTF_8);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> ElliottResearchRelations.read(file, frame -> {
        })).getMessage().contains("missing header"));
    }

    @Test
    public void observationIndicesRejectNarrowingAndNonIntegerValues() throws IOException {
        final List<String> lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
        final JsonObject frame = JsonParser.parseString(lines.get(1)).getAsJsonObject();
        for (final String invalid : List.of("2147483648", "-2147483649", "4294967296", "1e1000", "0.5", "\"3\"", "true",
                "null")) {
            frame.add("asOfIndex", JsonParser.parseString(invalid));
            lines.set(1, frame.toString());
            Files.write(file, lines, StandardCharsets.UTF_8);
            final String message = assertThrows(IllegalArgumentException.class,
                    () -> ElliottResearchRelations.read(file, ignored -> {
                    })).getMessage();
            assertTrue(message.contains(file.toString()) && message.contains("line 2") && message.contains("asOfIndex"),
                    message);
        }
    }

    @Test
    public void signedIntegerBoundaryIndicesRemainExact() throws IOException {
        final List<String> lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
        final JsonObject frame = JsonParser.parseString(lines.get(1)).getAsJsonObject();
        for (final int boundary : new int[] { Integer.MIN_VALUE, Integer.MAX_VALUE }) {
            frame.addProperty("asOfIndex", boundary);
            lines.set(1, frame.toString());
            Files.write(file, lines, StandardCharsets.UTF_8);
            final long[] frames = new long[1];
            assertTrue(ElliottResearchRelations.read(file, observed -> {
                if (frames[0]++ == 0) {
                    assertEquals(boundary, observed.get("asOfIndex").getAsInt());
                }
            }).complete());
            assertEquals(totals.frames(), frames[0]);
        }
    }

    @Test
    public void storedEdgeAndCoverageIntegersCannotWrap() throws IOException {
        final List<String> lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
        int line = 1;
        while (line < lines.size() - 1 && !lines.get(line).contains("\"parentStart\"")) {
            line++;
        }
        assertTrue(line < lines.size() - 1, "fixture must contain an edge");
        final JsonObject frame = JsonParser.parseString(lines.get(line)).getAsJsonObject();
        final JsonObject edge = frame.getAsJsonArray("events").get(0).getAsJsonObject().getAsJsonObject("edge");
        for (final JsonObject owner : List.of(frame.getAsJsonObject("coverage"), edge,
                edge.getAsJsonObject("parentStart"), edge.getAsJsonObject("parentEnd"),
                edge.getAsJsonArray("childPivots").get(0).getAsJsonObject())) {
            for (final Map.Entry<String, JsonElement> entry : owner.entrySet()) {
                final JsonElement original = entry.getValue();
                if (!original.isJsonPrimitive() || !original.getAsJsonPrimitive().isNumber()) {
                    continue;
                }
                owner.addProperty(entry.getKey(), 4294967296L);
                lines.set(line, frame.toString());
                Files.write(file, lines, StandardCharsets.UTF_8);
                final String message = assertThrows(IllegalArgumentException.class,
                        () -> ElliottResearchRelations.read(file, ignored -> {
                        })).getMessage();
                assertTrue(message.contains("line " + (line + 1)) && message.contains(entry.getKey()), message);
                owner.add(entry.getKey(), original);
            }
        }
    }

    @Test
    public void frameAndScaleScopeLabelsMustBeNonblankStrings() throws IOException {
        final List<String> lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
        int line = 1;
        while (line < lines.size() - 1 && !lines.get(line).contains("\"parentStart\"")) {
            line++;
        }
        assertTrue(line < lines.size() - 1, "fixture must contain an edge");
        final JsonObject frame = JsonParser.parseString(lines.get(line)).getAsJsonObject();
        final JsonObject edge = frame.getAsJsonArray("events").get(0).getAsJsonObject().getAsJsonObject("edge");
        for (final JsonObject owner : List.of(frame, edge)) {
            for (final String field : owner == frame ? List.of("dataset", "partition")
                    : List.of("parentScale", "childScale")) {
                final JsonElement original = owner.get(field);
                for (final String invalid : List.of("null", "\"\"", "\" \\t\"", "1", "true", "[]", "{}")) {
                    owner.add(field, JsonParser.parseString(invalid));
                    assertScopeRejected(lines, line, frame, field);
                }
                owner.remove(field);
                assertScopeRejected(lines, line, frame, field);
                owner.add(field, original);
            }
        }
        lines.set(line, frame.toString());
        Files.write(file, lines, StandardCharsets.UTF_8);
        assertTrue(ElliottResearchRelations.read(file, ignored -> {
        }).complete());
    }

    @Test
    public void eventLabelsAndCoverageFlagsMustBeRecognisedValues() throws IOException {
        final List<String> lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
        int line = 1;
        while (line < lines.size() - 1 && !lines.get(line).contains("\"parentStart\"")) {
            line++;
        }
        assertTrue(line < lines.size() - 1, "fixture must contain an edge");
        final JsonObject frame = JsonParser.parseString(lines.get(line)).getAsJsonObject();
        final JsonObject event = frame.getAsJsonArray("events").get(0).getAsJsonObject();
        final JsonObject coverage = frame.getAsJsonObject("coverage");
        for (final JsonObject owner : List.of(event, event.getAsJsonObject("edge"))) {
            for (final String field : owner == event ? List.of("lifecycle", "reason") : List.of("state")) {
                final JsonElement original = owner.get(field);
                for (final String invalid : List.of("null", "\"\"", "\"Active\"", "\"unknown\"", "1", "true")) {
                    owner.add(field, JsonParser.parseString(invalid));
                    assertIdentityRejected(lines, line, frame, field);
                }
                owner.add(field, original);
            }
        }
        for (final String field : List.of("truncated", "incomplete")) {
            final JsonElement original = coverage.get(field);
            for (final String invalid : List.of("null", "\"true\"", "1", "[]")) {
                coverage.add(field, JsonParser.parseString(invalid));
                assertIdentityRejected(lines, line, frame, field);
            }
            coverage.add(field, original);
        }
        lines.set(line, frame.toString());
        Files.write(file, lines, StandardCharsets.UTF_8);
        assertTrue(ElliottResearchRelations.read(file, ignored -> {
        }).complete());
    }

    @Test
    public void edgeAndParentIdentitiesRejectMalformedKeysAndVersions() throws IOException {
        final List<String> lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
        int line = 1;
        while (line < lines.size() - 1 && !lines.get(line).contains("\"parentStart\"")) {
            line++;
        }
        assertTrue(line < lines.size() - 1, "fixture must contain an edge");
        final JsonObject frame = JsonParser.parseString(lines.get(line)).getAsJsonObject();
        final JsonObject edge = frame.getAsJsonArray("events").get(0).getAsJsonObject().getAsJsonObject("edge");
        for (final String[] fields : List.of(new String[] { "key", "version" },
                new String[] { "parentCandidateKey", "parentVersion" })) {
            final JsonElement originalKey = edge.get(fields[0]);
            final JsonElement originalVersion = edge.get(fields[1]);
            for (final String invalid : List.of("null", "42", "true", "\"\"", "\"short\"", "\"" + "A".repeat(32) + "\"",
                    "\"" + "0".repeat(64) + "\"")) {
                edge.add(fields[0], JsonParser.parseString(invalid));
                assertIdentityRejected(lines, line, frame, fields[0]);
            }
            edge.add(fields[0], originalKey);
            final String key = originalKey.getAsString();
            final String otherKey = (key.charAt(0) == '0' ? "1" : "0") + key.substring(1);
            for (final String invalid : List.of("null", "42", "true", "\"\"", "\"" + key + "@short\"",
                    "\"" + otherKey + "@" + "0".repeat(32) + "\"", "\"" + key + "@" + "A".repeat(32) + "\"")) {
                edge.add(fields[1], JsonParser.parseString(invalid));
                assertIdentityRejected(lines, line, frame, fields[1]);
            }
            edge.add(fields[1], originalVersion);
        }
    }

    @Test
    public void optionalChildIdentityIsAbsentOrAConsistentPair() throws IOException {
        final List<String> lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
        int line = 1;
        while (line < lines.size() - 1 && !lines.get(line).contains("\"parentStart\"")) {
            line++;
        }
        assertTrue(line < lines.size() - 1, "fixture must contain an edge");
        final JsonObject frame = JsonParser.parseString(lines.get(line)).getAsJsonObject();
        final JsonObject edge = frame.getAsJsonArray("events").get(0).getAsJsonObject().getAsJsonObject("edge");
        edge.remove("childCandidateKey");
        edge.remove("childVersion");
        lines.set(line, frame.toString());
        Files.write(file, lines, StandardCharsets.UTF_8);
        assertTrue(ElliottResearchRelations.read(file, ignored -> {
        }).complete());

        edge.add("childCandidateKey", edge.get("parentCandidateKey"));
        assertIdentityRejected(lines, line, frame, "childVersion");
        edge.add("childVersion", edge.get("parentVersion"));
        lines.set(line, frame.toString());
        Files.write(file, lines, StandardCharsets.UTF_8);
        assertTrue(ElliottResearchRelations.read(file, ignored -> {
        }).complete());

        final String key = edge.get("childCandidateKey").getAsString();
        final String otherKey = (key.charAt(0) == '0' ? "1" : "0") + key.substring(1);
        edge.addProperty("childVersion", otherKey + "@" + "0".repeat(32));
        assertIdentityRejected(lines, line, frame, "childVersion");
        edge.remove("childCandidateKey");
        assertIdentityRejected(lines, line, frame, "childCandidateKey");
        edge.add("childCandidateKey", JsonParser.parseString("null"));
        edge.add("childVersion", JsonParser.parseString("null"));
        lines.set(line, frame.toString());
        Files.write(file, lines, StandardCharsets.UTF_8);
        assertTrue(ElliottResearchRelations.read(file, ignored -> {
        }).complete());
    }

    @Test
    public void footerCountsMustMatchWithoutFractionalOrLongNarrowing() throws IOException {
        final List<String> lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
        final JsonObject footer = JsonParser.parseString(lines.get(lines.size() - 1)).getAsJsonObject();
        final String wrappingCount = BigInteger.ONE.shiftLeft(Long.SIZE)
                .add(BigInteger.valueOf(totals.frames()))
                .toString();
        for (final String invalid : List.of(totals.frames() + ".5", "\"" + totals.frames() + "\"", "true",
                wrappingCount)) {
            footer.add("frames", JsonParser.parseString(invalid));
            lines.set(lines.size() - 1, footer.toString());
            Files.write(file, lines, StandardCharsets.UTF_8);
            final String message = assertThrows(IllegalArgumentException.class,
                    () -> ElliottResearchRelations.read(file, ignored -> {
                    })).getMessage();
            assertTrue(message.contains(file.toString()) && message.contains("line " + lines.size())
                    && message.contains("frames"), message);
        }
    }

    @Test
    public void footerStatisticsCannotWrapOrCoerce() throws IOException {
        final List<String> lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
        final int line = lines.size() - 1;
        final JsonObject footer = JsonParser.parseString(lines.get(line)).getAsJsonObject();
        for (final String field : List.of("events", "truncatedFrames", "incompleteFrames", "peakRetainedEdges")) {
            final JsonElement original = footer.get(field);
            final boolean intField = "peakRetainedEdges".equals(field);
            for (final String invalid : List.of(intField ? "4294967296" : "18446744073709551616",
                    intField ? "-2147483649" : "-9223372036854775809", "1.5", "\"1\"", "true", "null", "[]", "{}")) {
                footer.add(field, JsonParser.parseString(invalid));
                lines.set(line, footer.toString());
                Files.write(file, lines, StandardCharsets.UTF_8);
                final String message = assertThrows(IllegalArgumentException.class,
                        () -> ElliottResearchRelations.read(file, ignored -> {
                        })).getMessage();
                assertTrue(message.startsWith(file + ": line " + (line + 1) + ": malformed footer: " + field + " "),
                        message);
            }
            footer.add(field, original);
        }
        footer.addProperty("events", Long.MAX_VALUE);
        footer.addProperty("truncatedFrames", Long.MAX_VALUE);
        footer.addProperty("incompleteFrames", Long.MAX_VALUE);
        footer.addProperty("peakRetainedEdges", Integer.MAX_VALUE);
        lines.set(line, footer.toString());
        Files.write(file, lines, StandardCharsets.UTF_8);
        final ElliottResearchRelations.Meta meta = ElliottResearchRelations.read(file, ignored -> {
        });
        assertTrue(meta.complete());
        assertEquals(Long.MAX_VALUE, meta.footer().get("events").getAsLong());
        assertEquals(Integer.MAX_VALUE, meta.footer().get("peakRetainedEdges").getAsInt());
        for (final String field : List.of("events", "truncatedFrames", "incompleteFrames", "peakRetainedEdges")) {
            footer.add(field, JsonParser.parseString("5.0"));
        }
        lines.set(line, footer.toString());
        Files.write(file, lines, StandardCharsets.UTF_8);
        assertTrue(ElliottResearchRelations.read(file, ignored -> {
        }).complete());
    }

    @Test
    public void headerEdgeCapsCannotWrap() throws IOException {
        final List<String> lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
        final JsonObject header = JsonParser.parseString(lines.get(0)).getAsJsonObject();
        header.addProperty("edgeCap", 4294967296L);
        lines.set(0, header.toString());
        Files.write(file, lines, StandardCharsets.UTF_8);
        final String message = assertThrows(IllegalArgumentException.class,
                () -> ElliottResearchRelations.read(file, ignored -> {
                })).getMessage();
        assertTrue(message.contains("line 1") && message.contains("edgeCap"), message);
    }

    @Test
    void summaryLinesNameTheDatasetAndRefuseForeignOrIncompleteFiles() throws IOException {
        final JsonObject expected = new JsonObject();
        expected.addProperty("fingerprint", "fp");
        final List<String> lines = ElliottResearchRelations.summaryLines(file, "toy", expected);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("`toy`: parent > child"), lines.get(0));
        assertTrue(lines.get(0).contains("peak retained edges"), lines.get(0));

        expected.addProperty("fingerprint", "other");
        assertTrue(ElliottResearchRelations.summaryLines(file, "toy", expected)
                .get(0)
                .contains("does not belong to this run"));

        final List<String> all = Files.readAllLines(file, StandardCharsets.UTF_8);
        Files.write(file, all.subList(0, all.size() - 1), StandardCharsets.UTF_8);
        expected.addProperty("fingerprint", "fp");
        assertTrue(ElliottResearchRelations.summaryLines(file, "toy", expected).get(0).contains("incomplete"));

        Files.writeString(file, "garbage\n", StandardCharsets.UTF_8);
        assertTrue(ElliottResearchRelations.summaryLines(file, "toy", expected).get(0).contains("unreadable"));
    }

    @Test
    void hierarchyDeclarationsAreValidatedBeforeAnyDetectorRuns() {
        final ElliottResearchRelations.HierarchyScale a = new ElliottResearchRelations.HierarchyScale("a", null, null,
                null);
        final ElliottResearchRelations.HierarchyScale b = new ElliottResearchRelations.HierarchyScale("b", null, null,
                null);
        final ElliottResearchRelations.HierarchyScale c = new ElliottResearchRelations.HierarchyScale("c", null, null,
                null);
        final ElliottResearchRelations.HierarchyScale d = new ElliottResearchRelations.HierarchyScale("d", null, null,
                null);
        final ScaleRelation.Interior interior = ScaleRelation.Interior.CONTIGUOUS;
        assertThrows(IllegalArgumentException.class,
                () -> new ElliottResearchRelations.Hierarchy(List.of(a), interior, 10));
        assertThrows(IllegalArgumentException.class,
                () -> new ElliottResearchRelations.Hierarchy(List.of(a, b, c, d), interior, 10));
        assertThrows(IllegalArgumentException.class,
                () -> new ElliottResearchRelations.Hierarchy(List.of(a, a), interior, 10));
        assertThrows(IllegalArgumentException.class,
                () -> new ElliottResearchRelations.Hierarchy(List.of(a, b), interior, 0));
        assertEquals(3, new ElliottResearchRelations.Hierarchy(List.of(a, b, c), interior, 10).scales().size());

        final ElliottResearchRelations.Hierarchy hourly = new ElliottResearchRelations.Hierarchy(
                List.of(new ElliottResearchRelations.HierarchyScale("a", null, null, Duration.ofHours(1)), b), interior,
                10);
        final IllegalArgumentException mismatch = assertThrows(IllegalArgumentException.class,
                () -> hourly.validateSeries("toy", ScaleRelationFixtures.series(5)));
        assertTrue(mismatch.getMessage().contains("not resampled or aligned"), mismatch.getMessage());
    }

    private void assertScopeRejected(final List<String> lines, final int line, final JsonObject frame,
            final String field) throws IOException {
        lines.set(line, frame.toString());
        Files.write(file, lines, StandardCharsets.UTF_8);
        final String message = assertThrows(IllegalArgumentException.class,
                () -> ElliottResearchRelations.read(file, ignored -> {
                })).getMessage();
        final String prefix = file + ": line " + (line + 1) + ": ";
        assertTrue(message.startsWith(prefix + "malformed frame: " + field + " ")
                || message.equals(prefix + "frame lacks " + field), message);
    }

    private void assertIdentityRejected(final List<String> lines, final int line, final JsonObject frame,
            final String field) throws IOException {
        lines.set(line, frame.toString());
        Files.write(file, lines, StandardCharsets.UTF_8);
        final String message = assertThrows(IllegalArgumentException.class,
                () -> ElliottResearchRelations.read(file, ignored -> {
                })).getMessage();
        assertTrue(message.startsWith(file + ": line " + (line + 1) + ": malformed frame: " + field + " "), message);
    }

    private String printed(final Integer asOf, final int limit, final String edge) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            ElliottResearchRelations.print(out, file, asOf, limit, edge);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }
}

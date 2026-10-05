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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

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
        totals = ElliottResearchRelations.write(file, "toy", "rev", "fp", "sha", hierarchy,
                List.of(ScaleRelationFixtures.input("parent", ScaleRelationFixtures.PARENT,
                        ScaleRelationFixtures.PARENT_LAG),
                        ScaleRelationFixtures.input("child", ScaleRelationFixtures.CHILD,
                                ScaleRelationFixtures.CHILD_LAG)),
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
        assertEquals("parent", meta.header().getAsJsonArray("scales").get(0).getAsJsonObject().get("name").getAsString());
        assertEquals("PT24H", meta.header().getAsJsonArray("scales").get(0).getAsJsonObject().get("timeframe").getAsString());
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

    private String printed(final Integer asOf, final int limit, final String edge) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            ElliottResearchRelations.print(out, file, asOf, limit, edge);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }
}

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
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ta4j.core.BarSeries;

class ElliottResearchFamiliesTest {

    @TempDir
    Path directory;

    private Path file;
    private ElliottResearchFamilies.Totals totals;

    @BeforeEach
    void write() throws IOException {
        file = directory.resolve(ElliottResearchFamilies.fileName("toy"));
        final ElliottResearchRelations.Hierarchy hierarchy = new ElliottResearchRelations.Hierarchy(
                List.of(new ElliottResearchRelations.HierarchyScale("parent", "intermediate", null, null),
                        new ElliottResearchRelations.HierarchyScale("child", "minor", null, null)),
                ScaleRelation.Interior.CONTIGUOUS, 20);
        final BarSeries series = ScaleRelationFixtures.series(CorrectiveFamilyTest.BARS);
        hierarchy.validateSeries("toy", series);
        final ElliottResearchFamilies.Families families = new ElliottResearchFamilies.Families(8,
                List.of(CorrectiveFamily.Spec.defaults(CorrectiveFamily.Profile.ZIGZAG)));
        totals = ElliottResearchFamilies.write(file, "toy", "rev", "fp", "sha", hierarchy, families,
                List.of(ScaleRelationFixtures.input("parent", CorrectiveFamilyTest.ZIGZAG_PARENT,
                        CorrectiveFamilyTest.PARENT_LAG),
                        ScaleRelationFixtures.input("child", CorrectiveFamilyTest.ZIGZAG_CHILD,
                                CorrectiveFamilyTest.CHILD_LAG)),
                ScaleRelationFixtures.passingRules(), ScaleRelationFixtures.PARTITIONS, series, 0,
                CorrectiveFamilyTest.BARS - 1, DetectorReplays.uncached());
    }

    @Test
    void fileLivesUnderTheFamiliesDirectory() {
        assertEquals("families/toy.jsonl", ElliottResearchFamilies.fileName("toy"));
    }

    @Test
    void headerDeclaresTheProfileIdentityAndFooterTotalsMatchTheReader() throws IOException {
        final long[] frames = new long[1];
        final ElliottResearchRelations.Meta meta = ElliottResearchFamilies.read(file, frame -> frames[0]++);
        assertTrue(meta.complete());
        assertEquals(totals.frames(), frames[0]);
        assertEquals(totals.frames(), meta.frames());
        assertTrue(totals.events() > 0);
        assertEquals("elliott-research-families/1", meta.header().get("schema").getAsString());
        final JsonObject profile = meta.header().getAsJsonArray("profiles").get(0).getAsJsonObject();
        assertEquals(CorrectiveFamily.Profile.ZIGZAG.id(), profile.get("id").getAsString());
        assertEquals("5-3-5", profile.get("subdivision").getAsString());
        assertEquals(String.valueOf(CorrectiveFamily.PROFILE_REVISION),
                meta.header().get("profileRevision").getAsString());
        assertEquals("descriptive", meta.header().get("comparisonStatus").getAsString());
        assertTrue(meta.header().get("comparisonReason").getAsString().contains("no matched null comparison"));
    }

    @Test
    void replayStopsAtTheRequestedAsOfIndexAndHidesLaterVerdicts() throws IOException {
        final ElliottResearchFamilies.Replay full = ElliottResearchFamilies.replay(file, null, null);
        assertFalse(full.active().isEmpty());
        assertTrue(full.cursor() > 5 && full.cursor() <= CorrectiveFamilyTest.BARS - 1, "cursor=" + full.cursor());

        final ElliottResearchFamilies.Replay early = ElliottResearchFamilies.replay(file, 5, null);
        assertTrue(early.active().isEmpty());
        assertTrue(early.cursor() == null || early.cursor() <= 5);

        assertNull(ElliottResearchFamilies.replay(file, -1, null).cursor());
    }

    @Test
    void verdictHistoryAndPrintExposeEveryPredicateAndTheCaveat() throws IOException {
        final ElliottResearchFamilies.Replay full = ElliottResearchFamilies.replay(file, null, null);
        final String key = full.active().keySet().iterator().next();
        final ElliottResearchFamilies.Replay history = ElliottResearchFamilies.replay(file, null, key);
        assertFalse(history.history().isEmpty());

        final String text = printed(null, 5, key);
        assertTrue(text.contains("not a forecast"), text);
        assertTrue(text.contains("does not rescue or replace a base grammar count"), text);
        assertTrue(text.contains("History of verdict " + key), text);
        assertTrue(text.contains("Comparison: descriptive"), text);
        assertTrue(text.contains("envelope "), text);
        assertTrue(text.contains("leg 1 expects"), text);
        assertTrue(text.contains("relation "), text);
        assertTrue(text.contains("Coverage "), text);
    }

    @Test
    void everyStoredChildEdgeCarriesItsExactChildPivotSequence() throws IOException {
        final List<JsonObject> edges = storedEdges();
        assertFalse(edges.isEmpty());
        for (final JsonObject edge : edges) {
            final JsonArray pivots = edge.getAsJsonArray("childPivots");
            assertEquals(edge.get("childPivotCount").getAsInt(), pivots.size());
            assertTrue(pivots.size() >= 4, "a leg subdivision rests on at least four pivots: " + edge);
            int previous = -1;
            for (final JsonElement element : pivots) {
                final JsonObject pivot = element.getAsJsonObject();
                assertTrue(pivot.get("index").getAsInt() > previous);
                previous = pivot.get("index").getAsInt();
                assertNotNull(pivot.get("price"));
                assertTrue(pivot.get("type").getAsString().matches("HIGH|LOW"));
                assertTrue(pivot.get("confirmationIndex").getAsInt() >= previous);
            }
        }
        final List<Integer> firstLeg = new ArrayList<>();
        for (final JsonElement element : edges.get(0).getAsJsonArray("childPivots")) {
            firstLeg.add(element.getAsJsonObject().get("index").getAsInt());
        }
        assertEquals(List.of(0, 2, 3, 6, 7, 10), firstLeg);
    }

    @Test
    void inspectionPrintsTheChildPivotSequenceOfEveryEdgeWithItsConfirmationBar() throws IOException {
        final String key = ElliottResearchFamilies.replay(file, null, null).active().keySet().iterator().next();
        final String text = printed(null, 5, key);
        for (final JsonObject edge : storedEdges()) {
            final List<String> rendered = new ArrayList<>();
            for (final JsonElement element : edge.getAsJsonArray("childPivots")) {
                final JsonObject pivot = element.getAsJsonObject();
                rendered.add(pivot.get("index").getAsInt() + " " + pivot.get("type").getAsString() + " "
                        + pivot.get("price").getAsString() + " (confirmed @"
                        + pivot.get("confirmationIndex").getAsInt() + ")");
            }
            assertTrue(text.contains("child pivots: " + String.join(" -> ", rendered)), text);
        }
    }

    @Test
    void anArtifactWithoutChildPivotsOnAnEdgeIsRejectedAsCorrupt() throws IOException {
        final List<String> lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
        for (int i = 1; i < lines.size() - 1; i++) {
            if (lines.get(i).contains("\"childPivots\"")) {
                final JsonObject frame = JsonParser.parseString(lines.get(i)).getAsJsonObject();
                for (final JsonElement event : frame.getAsJsonArray("events")) {
                    for (final JsonElement leg : event.getAsJsonObject().getAsJsonObject("verdict")
                            .getAsJsonArray("legs")) {
                        for (final JsonElement edge : leg.getAsJsonObject().getAsJsonArray("edges")) {
                            edge.getAsJsonObject().remove("childPivots");
                        }
                    }
                }
                lines.set(i, frame.toString());
                Files.write(file, lines, StandardCharsets.UTF_8);
                assertTrue(rejection().contains("line " + (i + 1)));
                return;
            }
        }
        throw new AssertionError("no stored edge found");
    }

    @Test
    void printBeforeAnyFrameStatesThatNothingWasObservable() throws IOException {
        assertTrue(printed(-1, 5, null).contains("no verdict was observable yet"));
    }

    @Test
    void tornTrailingLineMarksTheFileIncompleteButCompleteFilesStayReadable() throws IOException {
        final List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        Files.writeString(file, String.join("\n", lines.subList(0, lines.size() - 1)) + "\n{\"kind\":\"fra",
                StandardCharsets.UTF_8);
        final ElliottResearchRelations.Meta meta = ElliottResearchFamilies.read(file, frame -> {
        });
        assertFalse(meta.complete());
        assertTrue(printed(null, 3, null).contains("no footer"));
    }

    @Test
    void corruptionIsRejectedWithFileAndLine() throws IOException {
        final List<String> lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));

        final List<String> middle = new ArrayList<>(lines);
        middle.set(2, "not json");
        Files.write(file, middle, StandardCharsets.UTF_8);
        assertTrue(rejection().contains("line 3"));

        final JsonObject header = JsonParser.parseString(lines.get(0)).getAsJsonObject();
        header.addProperty("schema", "elliott-research-families/0");
        final List<String> oldSchema = new ArrayList<>(lines);
        oldSchema.set(0, header.toString());
        Files.write(file, oldSchema, StandardCharsets.UTF_8);
        assertTrue(rejection().contains("unsupported schema"));

        final JsonObject frame = JsonParser.parseString(lines.get(1)).getAsJsonObject();
        frame.remove("events");
        final List<String> malformed = new ArrayList<>(lines);
        malformed.set(1, frame.toString());
        Files.write(file, malformed, StandardCharsets.UTF_8);
        assertTrue(rejection().contains("frame lacks events"));

        final List<String> dropped = new ArrayList<>(lines);
        dropped.remove(1);
        Files.write(file, dropped, StandardCharsets.UTF_8);
        assertTrue(rejection().contains("footer disagrees"));

        Files.writeString(file, "", StandardCharsets.UTF_8);
        assertTrue(rejection().contains("missing header"));
    }

    @Test
    void summaryLinesNameTheDatasetAndRefuseForeignOrIncompleteFiles() throws IOException {
        final JsonObject expected = new JsonObject();
        expected.addProperty("fingerprint", "fp");
        final List<String> lines = ElliottResearchFamilies.summaryLines(file, "toy", expected);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("`toy`"), lines.get(0));
        assertTrue(lines.get(0).contains("ever verified"), lines.get(0));
        assertTrue(lines.get(0).contains("latest status"), lines.get(0));

        expected.addProperty("fingerprint", "other");
        assertTrue(ElliottResearchFamilies.summaryLines(file, "toy", expected)
                .get(0)
                .contains("does not belong to this run"));

        final List<String> all = Files.readAllLines(file, StandardCharsets.UTF_8);
        Files.write(file, all.subList(0, all.size() - 1), StandardCharsets.UTF_8);
        expected.addProperty("fingerprint", "fp");
        assertTrue(ElliottResearchFamilies.summaryLines(file, "toy", expected).get(0).contains("incomplete"));

        Files.writeString(file, "garbage\n", StandardCharsets.UTF_8);
        assertTrue(ElliottResearchFamilies.summaryLines(file, "toy", expected).get(0).contains("unreadable"));
    }

    @Test
    void familiesDeclarationsAreValidatedBeforeAnyDetectorRuns() {
        final CorrectiveFamily.Spec zigzag = CorrectiveFamily.Spec.defaults(CorrectiveFamily.Profile.ZIGZAG);
        assertThrows(IllegalArgumentException.class, () -> new ElliottResearchFamilies.Families(0, List.of(zigzag)));
        assertThrows(IllegalArgumentException.class,
                () -> new ElliottResearchFamilies.Families(8, List.of(zigzag, zigzag)));
        assertNotNull(new ElliottResearchFamilies.Families(8, List.of(zigzag)).toJson().get("profiles"));
    }

    private String rejection() {
        return assertThrows(IllegalArgumentException.class, () -> ElliottResearchFamilies.read(file, frame -> {
        })).getMessage();
    }

    private List<JsonObject> storedEdges() throws IOException {
        final List<JsonObject> edges = new ArrayList<>();
        for (final String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (!line.contains("\"childPivots\"")) {
                continue;
            }
            final JsonObject frame = JsonParser.parseString(line).getAsJsonObject();
            for (final JsonElement event : frame.getAsJsonArray("events")) {
                for (final JsonElement leg : event.getAsJsonObject().getAsJsonObject("verdict")
                        .getAsJsonArray("legs")) {
                    for (final JsonElement edge : leg.getAsJsonObject().getAsJsonArray("edges")) {
                        edges.add(edge.getAsJsonObject());
                    }
                }
            }
        }
        return edges;
    }

    private String printed(final Integer asOf, final int limit, final String key) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            ElliottResearchFamilies.print(out, file, asOf, limit, key);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }
}

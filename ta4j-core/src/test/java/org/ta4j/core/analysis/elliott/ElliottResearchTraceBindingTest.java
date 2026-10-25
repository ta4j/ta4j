/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.analysis.elliott.ElliottResearchFixtures.OCCUPANCY_KEY;
import static org.ta4j.core.analysis.elliott.ElliottResearchFixtures.copyTree;
import static org.ta4j.core.analysis.elliott.ElliottResearchFixtures.launch;
import static org.ta4j.core.analysis.elliott.ElliottResearchFixtures.readJson;
import static org.ta4j.core.analysis.elliott.ElliottResearchFixtures.replayLine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import org.ta4j.core.analysis.elliott.ElliottResearchFixtures.Result;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Behavioural tests for how the Elliott research launcher binds traces to a
 * run: a trace that is damaged, foreign, linked or from another run is never
 * evidence.
 */
class ElliottResearchTraceBindingTest {

    @TempDir
    static Path shared;

    @TempDir
    Path work;

    private static Path smokeRun;
    private static Path nullMemberRun;

    @BeforeAll
    static void runFixtures() throws IOException {
        smokeRun = shared.resolve("smoke");
        final Result result = launch("run", "smoke", "--out", smokeRun.toString());
        assertEquals(0, result.code(), result.err());

        nullMemberRun = shared.resolve("smoke-null-b20-m3");
        final Result member = launch("run", "smoke", "--trace", "selected-null-member", "--block", "20", "--member",
                "3", "--out", nullMemberRun.toString());
        assertEquals(0, member.code(), member.err());
    }

    @Test
    public void selectedNullMemberRunWritesNullTraceUsedByInspect() throws Exception {
        final Path run = work.resolve("null-member");
        final Result result = launch("run", "smoke", "--trace", "selected-null-member", "--block", "20", "--member",
                "3", "--out", run.toString());
        assertEquals(0, result.code(), result.err());
        final Path nullTrace = run.resolve("traces/smoke-null-b20-m3.jsonl");
        assertTrue(Files.isRegularFile(nullTrace));
        assertFalse(Files.exists(run.resolve("traces/smoke-real.jsonl")));
        final ElliottResearchTrace.TraceFile parsed = ElliottResearchTrace.read(nullTrace);
        assertTrue(parsed.complete());
        assertFalse(parsed.records().isEmpty());

        final Result inspect = launch("inspect", run.toString(), OCCUPANCY_KEY);
        assertEquals(0, inspect.code(), inspect.err());
        assertTrue(inspect.out().contains("Selected null member trace: traces/smoke-null-b20-m3.jsonl"), inspect.out());
        assertTrue(inspect.out().contains("member 3"), inspect.out());
        // The viewer reads the real trace by default; a run that retained only the null
        // member must name that mode or the printed command fails.
        assertTrue(replayLine(inspect.out()).contains("--trace selected-null-member"), inspect.out());
        final JsonObject nullReport = readJson(run.resolve("reports/smoke.json")).getAsJsonArray("nulls")
                .asList()
                .stream()
                .map(element -> element.getAsJsonObject())
                .filter(candidate -> "MOTIVE_5".equals(candidate.get("grammar").getAsString())
                        && candidate.get("blockLength").getAsInt() == 20)
                .findFirst()
                .orElseThrow();
        long expected = -1L;
        for (final com.google.gson.JsonElement element : nullReport.getAsJsonArray("members")) {
            final JsonObject member = element.getAsJsonObject();
            if (member.get("memberIndex").getAsInt() == 3
                    && "calibration".equals(member.get("partition").getAsString())) {
                expected = member.getAsJsonArray("partitions")
                        .get(0)
                        .getAsJsonObject()
                        .get("evaluationCount")
                        .getAsLong();
            }
        }
        assertTrue(expected > 0L, "member 3 calibration evaluations");
        assertTrue(inspect.out().contains(", " + expected + " in this row's scope)"), inspect.out());
        assertTrue(inspect.out().contains("real trace not captured"), inspect.out());
        final Result summary = launch("summarize", run.toString());
        assertEquals(0, summary.code(), summary.err());
        assertTrue(Files.readString(run.resolve("summary.md")).contains("--trace real"));

        // A genuine competing row has no captured null stream: request a real
        // recapture, not an unusable viewer command.
        final String competingKey = "smoke|competing|competing-3+3|fractal-w5|calibration|completeOccupancyRate|b-";
        final Result competing = launch("inspect", run.toString(), competingKey);
        assertEquals(2, competing.code(), competing.err());
        assertTrue(competing.err().contains("no trace was captured for key " + competingKey), competing.err());
        assertTrue(competing.err().contains("--trace real"), competing.err());
    }

    @Test
    public void inspectRejectsRecordedNullMemberOutsideTheIntegerRange() throws Exception {
        final Path copy = work.resolve("member-range");
        copyTree(smokeRun, copy);
        final JsonObject original = readJson(copy.resolve("run.json"));
        for (final String number : List.of("-2147483649", "2147483648", "4294967296", "1.0000000000000001")) {
            final JsonObject damaged = original.deepCopy();
            damaged.getAsJsonObject("trace").add("member", JsonParser.parseString(number));
            Files.writeString(copy.resolve("run.json"), damaged.toString());
            final Result result = launch("inspect", copy.toString(), OCCUPANCY_KEY);
            assertEquals(1, result.code(), result.err());
            assertTrue(result.err().contains("run.trace.member must be an integer"), result.err());
        }
    }

    @Test
    void traceFooterWithNonTrueCompleteIsCorrupt() throws Exception {
        final Path copy = work.resolve("bad-footer");
        copyTree(smokeRun, copy);
        final Path trace = copy.resolve("traces/smoke-real.jsonl");
        final List<String> original = Files.readAllLines(trace, StandardCharsets.UTF_8);
        final List<String> lines = new java.util.ArrayList<>(original);
        final JsonObject footer = JsonParser.parseString(lines.get(lines.size() - 1)).getAsJsonObject();
        footer.add("complete", new JsonArray());
        lines.set(lines.size() - 1, footer.toString());
        Files.write(trace, lines, StandardCharsets.UTF_8);

        final Result result = launch("inspect", copy.toString(), OCCUPANCY_KEY);
        assertEquals(2, result.code());
        assertTrue(result.err().contains("footer complete flag [] is not true"), result.err());

        // Regression: a complete:false marker mid-file must not let later records and a
        // valid footer pass as a complete trace.
        final List<String> spliced = new java.util.ArrayList<>(original.subList(0, original.size() - 1));
        final JsonObject falseFooter = new JsonObject();
        falseFooter.addProperty("complete", false);
        falseFooter.addProperty("records", spliced.size() - 1);
        spliced.add(falseFooter.toString());
        spliced.add(original.get(original.size() - 1));
        Files.write(trace, spliced, StandardCharsets.UTF_8);

        final Result falseMarker = launch("inspect", copy.toString(), OCCUPANCY_KEY);
        assertEquals(2, falseMarker.code());
        assertTrue(falseMarker.err().contains("footer complete flag false is not true"), falseMarker.err());
    }

    @Test
    void inspectRejectsTraceCapturedForAnotherDatasetModeOrRun() throws Exception {
        // Regression: a complete trace from another dataset, null member, or run
        // (other revision, configuration or source) at the expected path was trusted
        // on schema, footer and coordinates alone.
        final List<java.util.function.Consumer<JsonObject>> foreign = List
                .of(header -> header.addProperty("dataset", "other"), header -> {
                    header.addProperty("traceMode", "selected-null-member");
                    header.addProperty("nullBlockLength", 20);
                    header.addProperty("nullMemberIndex", 3);
                }, header -> header.addProperty("revision", "other-revision"),
                        header -> header.addProperty("fingerprint", "other-configuration"),
                        header -> header.addProperty("sourceSha256", "other-source"));
        for (int index = 0; index < foreign.size(); index++) {
            final Path copy = work.resolve("foreign-" + index);
            copyTree(smokeRun, copy);
            rewriteTraceHeader(copy.resolve("traces/smoke-real.jsonl"), foreign.get(index));

            final Result result = launch("inspect", copy.toString(), OCCUPANCY_KEY);
            assertEquals(2, result.code(), "mutation " + index);
            final String expected = index < 2 ? "do not match header" : "was captured for";
            assertTrue(result.err().contains(expected), result.err());
            assertTrue(result.err().contains("Recapture"), result.err());
        }
    }

    @Test
    void summaryReportsEvidenceNotCapturedWhenTheTraceIsMissingTruncatedOrForeign() throws Exception {
        // Regression: summarize labelled every row "trace captured" from the run's
        // trace mode alone, even after the trace file was deleted, truncated, or
        // replaced by a null-member trace or one captured at another revision.
        final List<java.util.function.Consumer<Path>> damage = List.of(trace -> {
            try {
                Files.delete(trace);
            } catch (final IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }, trace -> {
            try {
                final List<String> lines = Files.readAllLines(trace, StandardCharsets.UTF_8);
                Files.write(trace, lines.subList(0, lines.size() - 1), StandardCharsets.UTF_8);
            } catch (final IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }, trace -> rewriteTraceHeader(trace, header -> {
            header.addProperty("traceMode", "selected-null-member");
            header.addProperty("nullBlockLength", 20);
            header.addProperty("nullMemberIndex", 3);
        }), trace -> rewriteTraceHeader(trace, header -> header.addProperty("revision", "other-revision")));
        assertTrue(Files.readString(smokeRun.resolve("summary.md")).contains("trace captured"));
        for (int index = 0; index < damage.size(); index++) {
            final Path copy = work.resolve("summary-damage-" + index);
            copyTree(smokeRun, copy);
            damage.get(index).accept(copy.resolve("traces/smoke-real.jsonl"));

            final Result result = launch("summarize", copy.toString());
            assertEquals(0, result.code(), result.err());
            final String summary = Files.readString(copy.resolve("summary.md"));
            assertFalse(summary.contains("trace captured"), "damage " + index);
            assertTrue(summary.contains("Evidence not captured for smoke."), summary);
            assertTrue(summary.contains("run smoke --trace real"), summary);
        }
    }

    @Test
    void inspectRefusesTraceReferencesThatLeaveTheRunDirectory() throws Exception {
        // Regression: a trace reference in run.json was resolved against the run
        // directory without containment, so a received run could make inspect read
        // whatever file the reference pointed at.
        final Path outside = work.resolve("outside-real.jsonl");
        Files.copy(smokeRun.resolve("traces/smoke-real.jsonl"), outside, StandardCopyOption.REPLACE_EXISTING);
        final List<String> references = List.of("../outside-real.jsonl", outside.toString());
        for (int index = 0; index < references.size(); index++) {
            final Path copy = work.resolve("escaping-reference-" + index);
            copyTree(smokeRun, copy);
            final JsonObject run = readJson(copy.resolve("run.json"));
            final JsonArray traces = new JsonArray();
            traces.add(references.get(index));
            run.getAsJsonArray("datasets").get(0).getAsJsonObject().add("traces", traces);
            Files.writeString(copy.resolve("run.json"), run.toString(), StandardCharsets.UTF_8);

            final Result result = launch("inspect", copy.toString(), OCCUPANCY_KEY);
            assertEquals(2, result.code(), references.get(index));
            assertTrue(result.err().contains("lies outside the run directory"), result.err());
        }
    }

    @Test
    public void traceLinkedFromOutsideTheRunDirectoryIsNotEvidence() throws Exception {
        // Regression: a trace that was a link to a file elsewhere was read as the
        // run's own evidence by both summarize and inspect.
        final Path outside = work.resolve("linked-target-real.jsonl");
        Files.copy(smokeRun.resolve("traces/smoke-real.jsonl"), outside, StandardCopyOption.REPLACE_EXISTING);
        final Path copy = work.resolve("linked-trace");
        copyTree(smokeRun, copy);
        final Path trace = copy.resolve("traces/smoke-real.jsonl");
        Files.delete(trace);
        Files.createSymbolicLink(trace, outside);

        final Result summarize = launch("summarize", copy.toString());
        assertEquals(0, summarize.code(), summarize.err());
        assertTrue(Files.readString(copy.resolve("summary.md")).contains("Evidence not captured for smoke."));

        final Result inspect = launch("inspect", copy.toString(), OCCUPANCY_KEY);
        assertEquals(2, inspect.code());
        assertTrue(inspect.err().contains("lies outside the run directory"), inspect.err());
    }

    @Test
    public void summarizeReplacesALinkedSummaryInsteadOfWritingThroughIt() throws Exception {
        // Regression: summarize wrote summary.md through an existing link, so a
        // received run could make it overwrite any file the link pointed at.
        final Path copy = work.resolve("linked-summary");
        copyTree(smokeRun, copy);
        final Path victim = work.resolve("victim.txt");
        Files.writeString(victim, "keep", StandardCharsets.UTF_8);
        final Path summary = copy.resolve("summary.md");
        Files.delete(summary);
        Files.createSymbolicLink(summary, victim);

        final Result result = launch("summarize", copy.toString());
        assertEquals(0, result.code(), result.err());
        assertEquals("keep", Files.readString(victim, StandardCharsets.UTF_8));
        assertFalse(Files.isSymbolicLink(summary));
        assertTrue(Files.readString(summary, StandardCharsets.UTF_8).contains("Elliott research run: smoke"));
    }

    @Test
    public void selectedNullMemberScopesEveryGrammarAndModeToTheRowsDetector() throws Exception {
        for (final boolean allForeign : List.of(false, true)) {
            final Path copy = work.resolve("null-detector-" + allForeign);
            copyTree(nullMemberRun, copy);
            final String relative = "traces/smoke-null-b20-m3.jsonl";
            final Path trace = copy.resolve(relative);
            final List<String> lines = new ArrayList<>(Files.readAllLines(trace, StandardCharsets.UTF_8));
            int changed = 0;
            for (int i = 1; i < lines.size() - 1; i++) {
                final JsonObject record = JsonParser.parseString(lines.get(i)).getAsJsonObject();
                if (allForeign || changed == 0 && "all-rules".equals(record.get("mode").getAsString())
                        && "CYCLE_5_3".equals(record.get("grammar").getAsString())
                        && "calibration".equals(record.get("partition").getAsString())) {
                    record.addProperty("detector", "foreign-detector");
                    lines.set(i, record.toString());
                    changed++;
                }
            }
            assertTrue(changed > 0, "the fixture must contain selected null observations");
            Files.write(trace, lines, StandardCharsets.UTF_8);
            final JsonObject run = readJson(copy.resolve("run.json"));
            run.getAsJsonArray("datasets")
                    .get(0)
                    .getAsJsonObject()
                    .getAsJsonObject("traceSha256")
                    .addProperty(relative, HexFormat.of()
                            .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(trace))));
            Files.writeString(copy.resolve("run.json"), run.toString());
            for (final String sectionMode : List.of("h1|topology-only", "h2|topology-only", "h2|all-rules")) {
                final String key = "smoke|" + sectionMode + "|fractal-w5|calibration|ambiguousRate|b20";
                final Result valid = launch("inspect", nullMemberRun.toString(), key);
                assertEquals(0, valid.code(), valid.err());
                assertTrue(valid.out().contains("366 in this row's scope"), valid.out());
                final Result scoped = launch("inspect", copy.toString(), key);
                assertEquals(0, scoped.code(), scoped.err());
                final int expected = allForeign ? 0 : "h2|all-rules".equals(sectionMode) ? 365 : 366;
                assertTrue(scoped.out().contains(expected + " in this row's scope"), scoped.out());
                if (allForeign) {
                    assertTrue(scoped.out().contains("Null member ambiguousRate: 0/0 = NaN"), scoped.out());
                    assertTrue(scoped.out().contains("Null member status tallies: no records"), scoped.out());
                } else if (!"h2|all-rules".equals(sectionMode)) {
                    assertEquals(valid.out().substring(valid.out().indexOf("Null member status tallies:")),
                            scoped.out().substring(scoped.out().indexOf("Null member status tallies:")));
                }
            }
        }
    }

    @Test
    public void selectedNullMemberShowsItsOwnUnavailableRatioContributions() {
        for (final String metric : List.of("evidencePassRate", "jointPassRate")) {
            final Result result = launch("inspect", nullMemberRun.toString(),
                    "smoke|h2|all-rules|fractal-w5|validation|" + metric + "|b20");
            assertEquals(0, result.code(), result.err());
            assertTrue(result.out().contains("Null member " + metric + ": 0/0 = NaN"), result.out());
            assertFalse(result.out().contains("-> match"), result.out());
        }
    }

    private static void rewriteTraceHeader(final Path trace, final java.util.function.Consumer<JsonObject> edit) {
        try {
            final List<String> lines = new java.util.ArrayList<>(Files.readAllLines(trace, StandardCharsets.UTF_8));
            final JsonObject header = JsonParser.parseString(lines.get(0)).getAsJsonObject();
            edit.accept(header);
            lines.set(0, header.toString());
            Files.write(trace, lines, StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}

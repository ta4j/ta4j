/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntToDoubleFunction;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Behavioural tests for the Elliott research launcher.
 */
class ElliottResearchTest {

    private static final String OCCUPANCY_KEY = "smoke|h1|topology-only|fractal-w5|calibration|completeOccupancyRate|b20";

    /**
     * Ways a published calibration CSV can disagree with the row count its run
     * recorded, each with the reason a reader must name.
     */
    private static final List<Damage> ROW_DAMAGES = List.of(
            new Damage("header-only", text -> text.substring(0, text.indexOf("\r\n") + 2),
                    " holds 0 rows but the run recorded "),
            new Damage("lost-last-row", text -> text.substring(0, text.lastIndexOf("\r\n", text.length() - 3) + 2),
                    " rows but the run recorded "),
            new Damage("cut-in-last-row",
                    text -> text.substring(0, text.lastIndexOf("\r\n", text.length() - 3) + 2 + 10),
                    " cells, expected "));

    @TempDir
    static Path shared;

    @TempDir
    Path work;

    private static Path smokeRun;
    private static Path calibratedRun;

    /** Captured result of one launcher invocation. */
    private record Result(int code, String out, String err) {
    }

    /**
     * One way of damaging a published artifact and the reason readers must give.
     */
    private record Damage(String name, UnaryOperator<String> edit, String reason) {
    }

    @BeforeAll
    static void runSmoke() throws IOException {
        smokeRun = shared.resolve("smoke");
        final Result result = launch("run", "smoke", "--out", smokeRun.toString());
        assertEquals(0, result.code(), result.err());

        final Path candles = shared.resolve("calibrated-candles.json");
        writeCandles(candles, LocalDate.of(2020, 1, 1), 366, date -> true);
        final Path recipe = shared.resolve("calibrated-recipe.json");
        Files.writeString(recipe, calibrationRecipe("cal", "{\"horizon\":5,\"minGroups\":1}"));
        calibratedRun = shared.resolve("calibrated");
        final Result calibrated = launch("run", "explore", "--source", candles.toString(), "--recipe",
                recipe.toString(), "--out", calibratedRun.toString());
        assertEquals(0, calibrated.code(), calibrated.err());
    }

    private static Result launch(final String... args) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final ByteArrayOutputStream err = new ByteArrayOutputStream();
        final int code;
        try (PrintStream outStream = new PrintStream(out, true, StandardCharsets.UTF_8);
                PrintStream errStream = new PrintStream(err, true, StandardCharsets.UTF_8)) {
            code = ElliottResearch.execute(args, outStream, errStream);
        }
        return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private static JsonObject readJson(final Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static void copyTree(final Path from, final Path to) throws IOException {
        try (Stream<Path> paths = Files.walk(from)) {
            for (final Path source : paths.toList()) {
                final Path target = to.resolve(from.relativize(source).toString());
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static long rowDenominator(final Path run, final String key) throws IOException {
        for (final ElliottResearchReport.Row row : ElliottResearchReport.readCsv(run.resolve("comparisons.csv"))) {
            if (row.key().equals(key)) {
                return row.denominator();
            }
        }
        throw new AssertionError("row not found: " + key);
    }

    @Test
    void smokeRunWritesEveryArtifactAndInspectMatchesRowDenominator() throws Exception {
        for (final String artifact : List.of("run.json", "comparisons.csv", "coverage.csv", "summary.md",
                "reports/smoke.json", "traces/smoke-real.jsonl")) {
            assertTrue(Files.isRegularFile(smokeRun.resolve(artifact)), artifact);
        }
        final JsonObject run = readJson(smokeRun.resolve("run.json"));
        assertEquals("elliott-research-run/1", run.get("artifactSchemaVersion").getAsString());
        assertEquals("complete", run.get("status").getAsString());
        final JsonObject dataset = run.getAsJsonArray("datasets").get(0).getAsJsonObject();
        assertEquals("traces/smoke-real.jsonl", dataset.getAsJsonArray("traces").get(0).getAsString());
        assertEquals("reports/smoke.json", dataset.get("report").getAsString());
        assertFalse(run.toString().contains(smokeRun.toString()), "artifact paths must stay relative");
        assertFalse(ElliottResearchReport.readCsv(smokeRun.resolve("comparisons.csv")).isEmpty());
        // Regression: a run from uncommitted sources recorded only HEAD, so it
        // looked reproducible from a revision that never produced it.
        final String worktree = run.get("worktree").getAsString();
        assertTrue(List.of("clean", "dirty", "unknown").contains(worktree), worktree);
        assertEquals(0, launch("summarize", smokeRun.toString()).code());
        final String summary = Files.readString(smokeRun.resolve("summary.md"));
        assertTrue(summary.contains("- worktree: " + worktree), summary);
        assertEquals(!"clean".equals(worktree), summary.contains("results may not reproduce from it"), summary);

        final long denominator = rowDenominator(smokeRun, OCCUPANCY_KEY);
        final Result inspect = launch("inspect", smokeRun.toString(), OCCUPANCY_KEY);
        assertEquals(0, inspect.code(), inspect.err());
        assertTrue(inspect.out().contains(denominator + " in this row's scope"), inspect.out());
        assertTrue(inspect.out().contains("-> match"), inspect.out());

        for (final String artifact : List.of("events.jsonl", "outcomes.csv", "outcomes-summary.csv")) {
            assertTrue(Files.isRegularFile(smokeRun.resolve(artifact)), artifact);
        }
        final List<String> eventLines = Files.readAllLines(smokeRun.resolve("events.jsonl"));
        assertTrue(eventLines.get(0).contains("ta4j-elliott-research-events/1"), eventLines.get(0));
        assertTrue(eventLines.get(eventLines.size() - 1).contains("\"complete\":true"), "events footer");
        assertEquals("events.jsonl", run.getAsJsonObject("outcomes").get("events").getAsString());
        assertTrue(summary.contains("Forward outcomes"), summary);
        assertTrue(Files.readString(smokeRun.resolve("outcomes.csv")).contains(",correction-completed,"),
                "the default structural mode must match the H2 mode the research runner evaluates");
    }

    @Test
    void inspectFocusesOnAsOfIndexAndRejectsUnknownOnes() {
        final Result focus = launch("inspect", smokeRun.toString(), OCCUPANCY_KEY, "--as-of", "100", "--limit", "3");
        assertEquals(0, focus.code(), focus.err());
        assertTrue(focus.out().contains("As-of 100:"), focus.out());
        assertTrue(focus.out().contains("Supportive as-of indices"), focus.out());

        final Result outside = launch("inspect", smokeRun.toString(), OCCUPANCY_KEY, "--as-of", "99999");
        assertEquals(1, outside.code());
        assertTrue(outside.err().contains("no record at as-of index 99999"), outside.err());
    }

    @Test
    void inspectUnknownKeyListsClosestKeysWithExitOne() {
        final Result result = launch("inspect", smokeRun.toString(),
                "smoke|h1|topology-only|fractal-w5|calibration|noSuchMetric|b20");
        assertEquals(1, result.code());
        assertTrue(result.err().contains("closest keys"), result.err());
        assertTrue(result.err().contains("smoke|h1|topology-only|fractal-w5|calibration|"), result.err());
    }

    @Test
    void copiedRunDirectoryStaysSummarizableAndInspectable() throws Exception {
        final Path copy = work.resolve("moved");
        copyTree(smokeRun, copy);

        final Result summarize = launch("summarize", copy.toString());
        assertEquals(0, summarize.code(), summarize.err());
        assertTrue(Files.readString(copy.resolve("summary.md")).contains("Elliott research run: smoke"));
        assertEquals(0, launch("inspect", copy.toString(), OCCUPANCY_KEY).code());
    }

    @Test
    void summarizeDiagnosesAMissingOutcomesSummaryThatTheRunDeclares() throws Exception {
        final Path copy = work.resolve("no-outcomes-summary");
        copyTree(smokeRun, copy);
        Files.delete(copy.resolve("outcomes-summary.csv"));
        Files.delete(copy.resolve("summary.md"));

        final Result result = launch("summarize", copy.toString());

        assertEquals(2, result.code());
        assertTrue(result.err().contains("outcomes-summary.csv is declared by run.json but missing"), result.err());
        assertFalse(Files.exists(copy.resolve("summary.md")), "an incomplete run must not get a summary");
    }

    @Test
    void runJsonDeclaresTheRecordCountsOfTheOutcomeArtifactsItVouchesFor() throws Exception {
        final JsonObject records = readJson(smokeRun.resolve("run.json")).getAsJsonObject("outcomes")
                .getAsJsonObject("records");

        assertTrue(records.get("events").getAsLong() > 0, "the smoke run must exercise the counts");
        assertEquals(Files.readAllLines(smokeRun.resolve("events.jsonl")).size() - 2L,
                records.get("events").getAsLong());
        assertEquals(csvRows("outcomes.csv"), records.get("outcomes").getAsLong());
        assertEquals(csvRows("outcomes-summary.csv"), records.get("summary").getAsLong());
    }

    @Test
    void summarizeRejectsAnEventsFileThatHoldsOnlyItsHeader() throws Exception {
        final Result result = summarizeAfterEditing("events-header-only", "events.jsonl",
                text -> text.substring(0, text.indexOf('\n') + 1));

        assertIncomplete(result, "events.jsonl has no completion footer");
    }

    @Test
    void summarizeRejectsAnEventsFileWithoutItsFooter() throws Exception {
        final Result result = summarizeAfterEditing("events-no-footer", "events.jsonl",
                text -> text.substring(0, text.lastIndexOf('\n', text.length() - 2) + 1));

        assertIncomplete(result, "events.jsonl has no completion footer");
    }

    @Test
    void summarizeRejectsAnEventsFileThatLostARowBeforeItsFooter() throws Exception {
        final Result result = summarizeAfterEditing("events-lost-row", "events.jsonl", text -> {
            final int first = text.indexOf('\n') + 1;
            return text.substring(0, first) + text.substring(text.indexOf('\n', first) + 1);
        });

        assertIncomplete(result, "its footer counts");
    }

    @Test
    void summarizeRejectsAnOutcomesFileCutInsideItsLastRow() throws Exception {
        final Result result = summarizeAfterEditing("outcomes-cut", "outcomes.csv",
                text -> text.substring(0, text.lastIndexOf("\r\n", text.length() - 3) + 2 + 10));

        assertIncomplete(result, "cells, expected 22");
    }

    @Test
    void summarizeRejectsAnOutcomesFileThatLostItsLastRow() throws Exception {
        final Result result = summarizeAfterEditing("outcomes-prefix", "outcomes.csv",
                text -> text.substring(0, text.lastIndexOf("\r\n", text.length() - 3) + 2));

        assertIncomplete(result, "outcomes.csv holds ");
        assertTrue(result.err().contains(" rows but the run recorded "), result.err());
    }

    @Test
    void summarizeRejectsAHeaderOnlyOutcomesSummary() throws Exception {
        final Result result = summarizeAfterEditing("summary-header-only", "outcomes-summary.csv",
                text -> text.substring(0, text.indexOf("\r\n") + 2));

        assertIncomplete(result, "outcomes-summary.csv holds 0 rows but the run recorded ");
    }

    @Test
    void summarizeRejectsARunThatNeverDeclaredItsOutcomeRecordCounts() throws Exception {
        // A run interrupted while publishing keeps the checkpoint run.json: status
        // "running" and no record counts, beside artifacts that may look complete.
        final Path copy = work.resolve("no-records");
        copyTree(smokeRun, copy);
        Files.delete(copy.resolve("summary.md"));
        final JsonObject run = readJson(copy.resolve("run.json"));
        run.addProperty("status", "running");
        run.getAsJsonObject("outcomes").remove("records");
        Files.writeString(copy.resolve("run.json"), run.toString());

        final Result result = launch("summarize", copy.toString());

        assertEquals(2, result.code(), result.err());
        assertTrue(result.err().contains("without their record counts"), result.err());
        assertFalse(Files.exists(copy.resolve("summary.md")), "an incomplete run must not get a summary");
    }

    @Test
    void summarizeRefusesAnOutcomeArtifactThatLinksOutsideTheRunDirectory() throws Exception {
        final Path copy = work.resolve("linked-outcomes");
        copyTree(smokeRun, copy);
        Files.delete(copy.resolve("summary.md"));
        final Path outside = work.resolve("outside-events.jsonl");
        Files.move(copy.resolve("events.jsonl"), outside);
        symlinkOrSkip(copy.resolve("events.jsonl"), outside);

        final Result result = launch("summarize", copy.toString());

        assertEquals(2, result.code(), result.err());
        assertTrue(result.err().contains("refusing to read it"), result.err());
        assertFalse(Files.exists(copy.resolve("summary.md")), "an incomplete run must not get a summary");
    }

    @Test
    void summarizeStaysCleanForARunWrittenBeforeOutcomesExisted() throws Exception {
        final Path copy = work.resolve("legacy-run");
        copyTree(smokeRun, copy);
        final JsonObject run = readJson(copy.resolve("run.json"));
        run.remove("outcomes");
        Files.writeString(copy.resolve("run.json"), run.toString());
        for (final String artifact : List.of("events.jsonl", "outcomes.csv", "outcomes-summary.csv")) {
            Files.delete(copy.resolve(artifact));
        }

        final Result result = launch("summarize", copy.toString());

        assertEquals(0, result.code(), result.err());
        assertFalse(Files.readString(copy.resolve("summary.md")).contains("Forward outcomes"));
    }

    @Test
    void missingTraceExitsTwoWithRecaptureCommand() throws Exception {
        final Path copy = work.resolve("no trace");
        copyTree(smokeRun, copy);
        Files.delete(copy.resolve("traces/smoke-real.jsonl"));

        final Result result = launch("inspect", copy.toString(), OCCUPANCY_KEY);
        assertEquals(2, result.code());
        assertTrue(result.err().contains("exec:java"), result.err());
        assertTrue(result.err().contains("run smoke --trace real"), result.err());
        assertTrue(result.err().contains("--out \"" + copy + "-recapture\""), result.err());
        final String fingerprint = readJson(copy.resolve("run.json")).getAsJsonObject("identity")
                .get("fingerprint")
                .getAsString();
        assertTrue(result.err().contains("--expect-fingerprint " + fingerprint), result.err());
        assertTrue(result.err()
                .contains("--expect-revision " + readJson(copy.resolve("run.json")).get("revision").getAsString()),
                result.err());
        final Path stale = work.resolve("stale-smoke");
        final Result mismatch = launch("run", "smoke", "--expect-fingerprint", "0" + fingerprint, "--out",
                stale.toString());
        assertEquals(1, mismatch.code());
        assertTrue(mismatch.err().contains("the recipe changed since the run"), mismatch.err());
        assertFalse(Files.exists(stale));
    }

    @Test
    public void recaptureRejectsAnotherRevisionBeforeReadingInputsOrCreatingArtifacts() {
        final Path out = work.resolve("wrong-revision");
        final Result result = launch("run", "explore", "--expect-revision", "not-the-current-revision", "--source",
                work.resolve("missing-candles.json").toString(), "--recipe",
                work.resolve("missing-recipe.json").toString(), "--out", out.toString());
        assertEquals(1, result.code());
        assertTrue(result.err().contains("check out the recorded revision in a separate worktree"), result.err());
        assertFalse(Files.exists(out));
    }

    @Test
    public void revisionBoundRecaptureRequiresKnownRevisionAndCleanProvenance() {
        ElliottResearch.requireRecordedRevision("recorded", "recorded", "clean");
        for (final String worktree : List.of("dirty", "unknown")) {
            final IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> ElliottResearch.requireRecordedRevision("recorded", "recorded", worktree));
            assertTrue(error.getMessage().contains("clean worktree"), error.getMessage());
        }
        for (final List<String> revisions : List.of(List.of("unknown", "unknown"), List.of("recorded", "unknown"),
                List.of("unknown", "recorded"))) {
            final IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> ElliottResearch.requireRecordedRevision(revisions.get(0), revisions.get(1), "clean"));
            assertTrue(error.getMessage().contains("cannot be verified"), error.getMessage());
        }
    }

    @Test
    public void comparisonsMustMatchRecordedDigestBeforeInspectOrSummaryTrustsRows() throws Exception {
        for (final String corruption : List.of("changed-csv", "missing-digest")) {
            final Path copy = work.resolve(corruption);
            Files.createDirectories(copy);
            Files.copy(smokeRun.resolve("run.json"), copy.resolve("run.json"));
            Files.copy(smokeRun.resolve("comparisons.csv"), copy.resolve("comparisons.csv"));
            Files.writeString(copy.resolve("summary.md"), "keep the existing summary");
            if ("changed-csv".equals(corruption)) {
                Files.writeString(copy.resolve("comparisons.csv"),
                        Files.readString(copy.resolve("comparisons.csv")) + "\n");
            } else {
                final JsonObject run = readJson(copy.resolve("run.json"));
                run.remove("comparisonsSha256");
                Files.writeString(copy.resolve("run.json"), run.toString());
            }
            final Result summary = launch("summarize", copy.toString());
            assertEquals(2, summary.code(), summary.err());
            assertTrue(summary.err().contains("SHA-256"), summary.err());
            assertEquals("keep the existing summary", Files.readString(copy.resolve("summary.md")));
            final Result inspect = launch("inspect", copy.toString(), OCCUPANCY_KEY);
            assertEquals(2, inspect.code(), inspect.err());
            assertTrue(inspect.err().contains("SHA-256"), inspect.err());
        }
    }

    @Test
    public void overwritePreservesExploreInputsUnderManagedPathsIncludingSymlinkAliases() throws Exception {
        final Path source = work.resolve("overwrite-source.json");
        writeCandles(source, LocalDate.of(2020, 1, 1), 366, date -> true);
        final Path recipe = work.resolve("overwrite-recipe.json");
        Files.writeString(recipe, toyRecipe("overwrite"));
        final String recordedRun = Files.readString(smokeRun.resolve("run.json"));
        int caseIndex = 0;
        for (final String artifact : List.of("traces/input.json", "reports/input.json", "summary.md",
                ElliottResearchOutcomes.EVENTS_FILE, ElliottResearchOutcomes.OUTCOMES_FILE,
                ElliottResearchOutcomes.SUMMARY_FILE, ElliottResearchCalibration.TABLES_FILE,
                ElliottResearchCalibration.PREDICTIONS_FILE, ElliottResearchCalibration.SUMMARY_FILE,
                ElliottResearchCalibration.RELIABILITY_FILE)) {
            for (final boolean recipeInput : List.of(false, true)) {
                for (final boolean symlink : List.of(false, true)) {
                    final Path out = work.resolve("overwrite-managed-" + caseIndex++);
                    Files.createDirectories(out.resolve("reports"));
                    Files.writeString(out.resolve("run.json"), recordedRun);
                    Files.writeString(out.resolve("reports/keep.txt"), "keep");
                    final Path input = out.resolve(artifact);
                    Files.createDirectories(input.getParent());
                    final String original = Files.readString(recipeInput ? recipe : source);
                    Files.writeString(input, original);
                    final Path selected = symlink
                            ? Files.createSymbolicLink(work.resolve("input-alias-" + caseIndex), input)
                            : input;
                    final Result result = launch("run", "explore", "--source",
                            (recipeInput ? source : selected).toString(), "--recipe",
                            (recipeInput ? selected : recipe).toString(), "--out", out.toString(), "--overwrite");
                    assertEquals(1, result.code(), result.err());
                    assertTrue(result.err().contains("--overwrite would remove explore input"), result.err());
                    assertEquals(recordedRun, Files.readString(out.resolve("run.json")));
                    assertEquals("keep", Files.readString(out.resolve("reports/keep.txt")));
                    assertEquals(original, Files.readString(input));
                    assertFalse(Files.exists(out.resolve(".run.lock")));
                }
            }
        }
    }

    @Test
    public void overwriteAllowsUnmanagedExploreInputsInTheRunDirectory() throws Exception {
        final Path out = work.resolve("overwrite-unmanaged");
        Files.createDirectories(out);
        Files.copy(smokeRun.resolve("run.json"), out.resolve("run.json"));
        final Path source = out.resolve("candles.json");
        writeCandles(source, LocalDate.of(2020, 1, 1), 366, date -> true);
        final Path recipe = out.resolve("recipe.json");
        Files.writeString(recipe, toyRecipe("preserved"));
        final String candles = Files.readString(source);
        final String definition = Files.readString(recipe);
        final Result result = launch("run", "explore", "--source", source.toString(), "--recipe", recipe.toString(),
                "--out", out.toString(), "--overwrite");
        assertEquals(0, result.code(), result.err());
        assertEquals(candles, Files.readString(source));
        assertEquals(definition, Files.readString(recipe));
    }

    @Test
    void traceWithoutFooterIsReportedAsTruncated() throws Exception {
        final Path copy = work.resolve("truncated");
        copyTree(smokeRun, copy);
        final Path trace = copy.resolve("traces/smoke-real.jsonl");
        final List<String> lines = Files.readAllLines(trace, StandardCharsets.UTF_8);
        Files.write(trace, lines.subList(0, lines.size() - 1), StandardCharsets.UTF_8);

        final Result result = launch("inspect", copy.toString(), OCCUPANCY_KEY);
        assertEquals(2, result.code());
        assertTrue(result.err().contains("truncated"), result.err());
        assertTrue(result.err().contains("Recapture"), result.err());
    }

    @Test
    void corruptTraceExitsTwo() throws Exception {
        final Path copy = work.resolve("corrupt");
        copyTree(smokeRun, copy);
        Files.writeString(copy.resolve("traces/smoke-real.jsonl"), "not json at all\n");

        final Result result = launch("inspect", copy.toString(), OCCUPANCY_KEY);
        assertEquals(2, result.code());
        assertTrue(result.err().contains("corrupt"), result.err());
    }

    @Test
    void failedDatasetIsExplainedWithExitTwo() throws Exception {
        final Path copy = work.resolve("failed");
        copyTree(smokeRun, copy);
        final JsonObject run = readJson(copy.resolve("run.json"));
        final JsonObject dataset = run.getAsJsonArray("datasets").get(0).getAsJsonObject();
        dataset.addProperty("status", "failed");
        dataset.addProperty("message", "IllegalStateException: boom");
        Files.writeString(copy.resolve("run.json"), run.toString());

        final Result result = launch("inspect", copy.toString(), OCCUPANCY_KEY);
        assertEquals(2, result.code());
        assertTrue(result.err().contains("IllegalStateException: boom"), result.err());
    }

    @Test
    void nonEmptyOutputWithoutOverwriteFailsAndLeavesRunUntouched() throws Exception {
        final Path copy = work.resolve("collision");
        copyTree(smokeRun, copy);
        final byte[] before = Files.readAllBytes(copy.resolve("run.json"));

        final Result result = launch("run", "smoke", "--out", copy.toString());
        assertEquals(1, result.code());
        assertTrue(result.err().contains("not empty"), result.err());
        assertTrue(java.util.Arrays.equals(before, Files.readAllBytes(copy.resolve("run.json"))));
    }

    @Test
    void overwriteRefusesDirectoryThatIsNotAPreviousRun() throws Exception {
        // Regression: a refused reservation must not leave its lock file behind.
        final Path other = work.resolve("foreign");
        Files.createDirectories(other);
        Files.writeString(other.resolve("precious.txt"), "keep");

        final Result result = launch("run", "smoke", "--out", other.toString(), "--overwrite");
        assertEquals(1, result.code());
        assertTrue(result.err().contains("not a previous research run"), result.err());
        assertEquals("keep", Files.readString(other.resolve("precious.txt")));
        assertFalse(Files.exists(other.resolve(".run.lock")), "refused --overwrite left a lock file");

        final Result plain = launch("run", "smoke", "--out", other.toString());
        assertEquals(1, plain.code());
        assertTrue(plain.err().contains("not empty"), plain.err());
        assertFalse(Files.exists(other.resolve(".run.lock")), "refused non-empty run left a lock file");
    }

    @Test
    void overwriteRefusesRunJsonThatIsNotAResearchRun() throws Exception {
        final Path other = work.resolve("foreign-run-json");
        Files.createDirectories(other.resolve("reports"));
        Files.createDirectories(other.resolve("traces"));
        Files.writeString(other.resolve("run.json"), "{\"artifactSchemaVersion\":\"someone-else/3\"}");
        Files.writeString(other.resolve("reports/keep.json"), "keep");
        Files.writeString(other.resolve("traces/keep.jsonl"), "keep");

        final Result result = launch("run", "smoke", "--out", other.toString(), "--overwrite");
        assertEquals(1, result.code());
        assertTrue(result.err().contains("not a previous research run"), result.err());
        assertEquals("keep", Files.readString(other.resolve("reports/keep.json")));
        assertEquals("keep", Files.readString(other.resolve("traces/keep.jsonl")));

        Files.writeString(other.resolve("run.json"), "not json");
        assertEquals(1, launch("run", "smoke", "--out", other.toString(), "--overwrite").code());
        assertEquals("keep", Files.readString(other.resolve("traces/keep.jsonl")));
    }

    @Test
    void invalidNullSelectionFailsBeforeCreatingArtifacts() {
        final Path badBlock = work.resolve("bad-block");
        final Result block = launch("run", "smoke", "--trace", "selected-null-member", "--block", "7", "--member", "0",
                "--out", badBlock.toString());
        assertEquals(1, block.code());
        assertTrue(block.err().contains("--block 7"), block.err());
        assertFalse(Files.exists(badBlock));

        final Path badMember = work.resolve("bad-member");
        final Result member = launch("run", "smoke", "--trace", "selected-null-member", "--block", "20", "--member",
                "8", "--out", badMember.toString());
        assertEquals(1, member.code());
        assertTrue(member.err().contains("--member 8"), member.err());
        assertFalse(Files.exists(badMember));

        final Result missing = launch("run", "smoke", "--trace", "selected-null-member", "--out",
                work.resolve("missing").toString());
        assertEquals(1, missing.code());
        assertTrue(missing.err().contains("requires both --block and --member"), missing.err());
        assertFalse(Files.exists(work.resolve("missing")));
    }

    @Test
    void unknownCommandsAndOptionsAreUsageErrors() {
        assertEquals(1, launch().code());
        assertEquals(1, launch("frobnicate").code());
        assertEquals(1, launch("run").code());
        assertEquals(1, launch("run", "nonsense").code());
        final Result option = launch("run", "smoke", "--frobnicate", "--out", work.resolve("x").toString());
        assertEquals(1, option.code());
        assertTrue(option.err().contains("unknown option --frobnicate"), option.err());
        assertFalse(Files.exists(work.resolve("x")));
        assertEquals(1, launch("inspect", smokeRun.toString(), OCCUPANCY_KEY, "--bogus").code());
        final Result rank = launch("inspect", smokeRun.toString(), OCCUPANCY_KEY, "--rank", "luck");
        assertEquals(1, rank.code(), "an uncalibrated run must still reject an unknown rank");
        assertTrue(rank.err().contains("--rank must be"), rank.err());
        assertEquals(1, launch("run", "smoke", "--block", "20", "--out", work.resolve("y").toString()).code());
        assertEquals(0, launch("help").code());
    }

    @Test
    void selectedNullMemberRunWritesNullTraceUsedByInspect() throws Exception {
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
    }

    @Test
    void exploreRunUsesRecipeAndRecordsNarrowCoverageAsPartial() throws Exception {
        final Path candles = work.resolve("candles.json");
        writeCandles(candles, LocalDate.of(2020, 1, 1), 300, date -> true);
        final Path recipe = work.resolve("recipe.json");
        Files.writeString(recipe, """
                {"datasetId":"toy","asset":"TOY",
                 "partitions":[{"name":"calibration","start":"2020-01-01","end":"2020-06-30"},
                               {"name":"validation","start":"2020-07-01","end":"2020-09-30"},
                               {"name":"holdout","start":"2020-10-01","end":"2020-12-31"}],
                 "forbiddenCalibrationStart":"2024-01-01",
                 "detector":{"name":"fractal-w3","factory":"fractal","params":[3]},
                 "activeRules":["wave2-origin","wave4-nonoverlap"],
                 "momentum":{"type":"RSI","barCount":14},
                 "competingModes":["3+3"],
                 "null":{"blockLengths":[10],"ensembleSize":4,"seed":7}}
                """);
        final Path out = work.resolve("explore");
        final Result result = launch("run", "explore", "--source", candles.toString(), "--recipe", recipe.toString(),
                "--out", out.toString());
        assertEquals(0, result.code(), result.err());
        final JsonObject run = readJson(out.resolve("run.json"));
        assertEquals("complete", run.get("status").getAsString());
        assertEquals("TOY", run.getAsJsonArray("datasets").get(0).getAsJsonObject().get("asset").getAsString());
        assertEquals(2, run.getAsJsonObject("configuration").getAsJsonArray("activeRules").size());
        final String coverage = Files.readString(out.resolve("coverage.csv"));
        assertTrue(coverage.contains("partial"), coverage);
        assertTrue(coverage.contains("partition holdout ends at"), coverage);
        assertFalse(Files.exists(out.resolve("traces/toy-real.jsonl")), "explore defaults to trace off");
        assertNotNull(run.getAsJsonObject("identity").get("fingerprint"));
        assertTrue(run.getAsJsonObject("identity").get("fingerprint").getAsString().startsWith("explore-"));
    }

    @Test
    void exploreRunMarksCoveragePartialWhenAMiddlePartitionHasNoBars() throws Exception {
        // Regression: coverage compared only the first and last bar with the
        // requested window, so a tape missing the whole validation partition
        // was reported complete while that partition's metrics were empty.
        final Path candles = work.resolve("gap-candles.json");
        writeCandles(candles, LocalDate.of(2020, 1, 1), 366,
                date -> date.isBefore(LocalDate.of(2020, 7, 1)) || date.isAfter(LocalDate.of(2020, 9, 30)));
        final Path recipe = work.resolve("gap-recipe.json");
        Files.writeString(recipe, """
                {"datasetId":"gap","asset":"GAP",
                 "partitions":[{"name":"calibration","start":"2020-01-01","end":"2020-06-30"},
                               {"name":"validation","start":"2020-07-01","end":"2020-09-30"},
                               {"name":"holdout","start":"2020-10-01","end":"2020-12-31"}],
                 "forbiddenCalibrationStart":"2024-01-01",
                 "detector":{"name":"fractal-w3","factory":"fractal","params":[3]},
                 "activeRules":["wave2-origin"],
                 "momentum":{"type":"RSI","barCount":14},
                 "competingModes":["3+3"],
                 "null":{"blockLengths":[10],"ensembleSize":2,"seed":7}}
                """);
        final Path out = work.resolve("explore-gap");
        final Result result = launch("run", "explore", "--source", candles.toString(), "--recipe", recipe.toString(),
                "--out", out.toString());
        assertEquals(0, result.code(), result.err());
        final JsonObject coverage = readJson(out.resolve("run.json")).getAsJsonArray("datasets")
                .get(0)
                .getAsJsonObject()
                .getAsJsonObject("coverage");
        assertEquals("partial", coverage.get("status").getAsString(), coverage.toString());
        assertTrue(coverage.get("message")
                .getAsString()
                .contains("partition validation (2020-07-01..2020-09-30) has no bars"), coverage.toString());
    }

    private static void writeCandles(final Path file, final LocalDate first, final int days,
            final java.util.function.Predicate<LocalDate> keep) throws IOException {
        writeCandles(file, first, days, keep,
                index -> 100.0d + 15.0d * StrictMath.sin(0.2d * index) + 6.0d * StrictMath.sin(0.7d * index));
    }

    private static void writeCandles(final Path file, final LocalDate first, final int days,
            final java.util.function.Predicate<LocalDate> keep, final IntToDoubleFunction closeAt) throws IOException {
        final JsonArray array = new JsonArray();
        for (int index = 0; index < days; index++) {
            final LocalDate date = first.plusDays(index);
            if (!keep.test(date)) {
                continue;
            }
            final double close = closeAt.applyAsDouble(index);
            final JsonObject candle = new JsonObject();
            candle.addProperty("start", date.atStartOfDay(ZoneOffset.UTC).toEpochSecond());
            candle.addProperty("open", Double.toString(close - 0.5d));
            candle.addProperty("high", Double.toString(close + 2.0d));
            candle.addProperty("low", Double.toString(close - 2.0d));
            candle.addProperty("close", Double.toString(close));
            candle.addProperty("volume", "1");
            array.add(candle);
        }
        final JsonObject candleFile = new JsonObject();
        candleFile.add("candles", array);
        Files.writeString(file, candleFile.toString());
    }

    @Test
    void exploreRecipeRejectsUnknownFieldsBeforeCreatingArtifacts() throws Exception {
        final Path candles = work.resolve("c.json");
        Files.writeString(candles, "{\"candles\":[]}");
        final Path recipe = work.resolve("bad-recipe.json");
        Files.writeString(recipe, "{\"datasetId\":\"toy\",\"unexpected\":1}");
        final Path out = work.resolve("explore-bad");
        final Result result = launch("run", "explore", "--source", candles.toString(), "--recipe", recipe.toString(),
                "--out", out.toString());
        assertEquals(1, result.code());
        assertTrue(result.err().contains("unknown field recipe.unexpected"), result.err());
        assertFalse(Files.exists(out));
    }

    @Test
    void exploreRecipeRejectsStructuralModeTheRunDoesNotEvaluate() throws Exception {
        final Path candles = work.resolve("mode-candles.json");
        writeCandles(candles, LocalDate.of(2020, 1, 1), 366, date -> true);
        final Path recipe = work.resolve("mode-recipe.json");
        Files.writeString(recipe, toyRecipe("mode").replace("\"null\":", """
                "activeRules":["wave2-origin"],
                "outcomes":{"structuralMode":"+wave3-not-shortest"},
                "null\":"""));
        final Path out = work.resolve("explore-mode");
        final Result result = launch("run", "explore", "--source", candles.toString(), "--recipe", recipe.toString(),
                "--out", out.toString());
        assertEquals(1, result.code(), result.err());
        assertTrue(
                result.err().contains("outcomes.structuralMode '+wave3-not-shortest' is not a mode this run evaluates"),
                result.err());
        assertTrue(result.err().contains("+wave2-origin"), result.err());
        assertFalse(Files.exists(out));
    }

    @Test
    void exploreRecipeRejectsKeySeparatorInDetectorNameBeforeRunning() throws Exception {
        final Path candles = work.resolve("sep-candles.json");
        writeCandles(candles, LocalDate.of(2020, 1, 1), 300, date -> true);
        final Path recipe = work.resolve("sep-recipe.json");
        Files.writeString(recipe, """
                {"datasetId":"toy","asset":"TOY",
                 "partitions":[{"name":"calibration","start":"2020-01-01","end":"2020-06-30"},
                               {"name":"validation","start":"2020-07-01","end":"2020-09-30"},
                               {"name":"holdout","start":"2020-10-01","end":"2020-12-31"}],
                 "forbiddenCalibrationStart":"2024-01-01",
                 "detector":{"name":"fractal-w3","factory":"fractal","params":[3]},
                 "robustnessDetectors":[{"name":"fractal|w5","factory":"fractal","params":[5]}],
                 "momentum":{"type":"RSI","barCount":14},
                 "null":{"blockLengths":[10],"ensembleSize":2,"seed":7}}
                """);
        final Path out = work.resolve("explore-sep");
        final Result result = launch("run", "explore", "--source", candles.toString(), "--recipe", recipe.toString(),
                "--out", out.toString());
        assertEquals(1, result.code());
        assertTrue(result.err().contains("recipe.robustnessDetectors[0].name must not contain '|'"), result.err());
        assertFalse(Files.exists(out));
    }

    @Test
    public void exploreRecipeRejectsFormulaLeadingAssetBeforeRunning() throws Exception {
        final Path candles = work.resolve("formula-candles.json");
        writeCandles(candles, LocalDate.of(2020, 1, 1), 366, date -> true);
        final Path recipe = work.resolve("formula-recipe.json");
        int index = 0;
        for (final String prefix : List.of("=", "+", "-", "@", "\t", "\r")) {
            final JsonObject definition = JsonParser.parseString(toyRecipe("formula")).getAsJsonObject();
            definition.addProperty("asset", prefix + "1+1");
            Files.writeString(recipe, definition.toString());
            final Path out = work.resolve("formula-asset-" + index++);
            final Result result = launch("run", "explore", "--source", candles.toString(), "--recipe",
                    recipe.toString(), "--out", out.toString());
            assertEquals(1, result.code(), result.err());
            assertTrue(result.err().contains("recipe.asset must not start with a spreadsheet formula character"),
                    result.err());
            assertFalse(Files.exists(out));
        }
    }

    @Test
    void runRefusesDirectoryHeldByAnotherRun() throws Exception {
        final Path held = work.resolve("held");
        Files.createDirectories(held);
        try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(held.resolve(".run.lock"),
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE);
                java.nio.channels.FileLock lock = channel.lock()) {
            final Result result = launch("run", "smoke", "--out", held.toString());
            assertEquals(1, result.code());
            assertTrue(result.err().contains("another run is writing to"), result.err());
            assertFalse(Files.exists(held.resolve("run.json")));
        }
        assertEquals(0, launch("run", "smoke", "--out", held.toString()).code(), "released lock frees the directory");
    }

    @Test
    public void summarizeRefusesAnActiveRunAndLeavesItsSummaryUntouched() throws Exception {
        final Path copy = work.resolve("held-summary");
        copyTree(smokeRun, copy);
        final Path summary = copy.resolve("summary.md");
        Files.writeString(summary, "writer-owned summary");
        try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(copy.resolve(".run.lock"),
                java.nio.file.StandardOpenOption.WRITE); java.nio.channels.FileLock lock = channel.lock()) {
            final Result result = launch("summarize", copy.toString());
            assertEquals(1, result.code(), result.err());
            assertTrue(result.err().contains("another run is writing to"), result.err());
            assertEquals("writer-owned summary", Files.readString(summary));
        }
        final Result released = launch("summarize", copy.toString());
        assertEquals(0, released.code(), released.err());
        assertTrue(Files.readString(summary).contains("Elliott research run: smoke"));
    }

    @Test
    public void summarizeRejectsNonRunPathsWithoutCreatingLockFiles() throws Exception {
        final Path missing = work.resolve("missing-summary-run");
        final Path other = Files.createDirectories(work.resolve("not-a-summary-run"));
        Files.writeString(other.resolve("precious.txt"), "keep");
        for (final Path dir : List.of(missing, other)) {
            final Result result = launch("summarize", dir.toString());
            assertEquals(1, result.code(), result.err());
            assertTrue(result.err().contains("not a run directory"), result.err());
            assertFalse(Files.exists(dir.resolve(".run.lock")), "refused summary left a lock file");
        }
        assertFalse(Files.exists(missing));
        assertEquals("keep", Files.readString(other.resolve("precious.txt")));
    }

    @Test
    public void summarizeRejectsInvalidRunMetadataWithoutCreatingLockFiles() throws Exception {
        final Path dir = Files.createDirectories(work.resolve("invalid-summary-run"));
        for (final String metadata : List.of("{", "{}", "{\"artifactSchemaVersion\":\"wrong\"}")) {
            Files.writeString(dir.resolve("run.json"), metadata);
            final Result result = launch("summarize", dir.toString());
            assertEquals(1, result.code(), result.err());
            assertTrue(result.err().contains("run.json"), result.err());
            assertFalse(Files.exists(dir.resolve(".run.lock")), "invalid metadata gained a lock file");
        }
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
    void traceLinkedFromOutsideTheRunDirectoryIsNotEvidence() throws Exception {
        // Regression: a trace that was a link to a file elsewhere was read as the
        // run's own evidence by both summarize and inspect.
        final Path outside = work.resolve("linked-target-real.jsonl");
        Files.copy(smokeRun.resolve("traces/smoke-real.jsonl"), outside, StandardCopyOption.REPLACE_EXISTING);
        final Path copy = work.resolve("linked-trace");
        copyTree(smokeRun, copy);
        final Path trace = copy.resolve("traces/smoke-real.jsonl");
        Files.delete(trace);
        symlinkOrSkip(trace, outside);

        final Result summarize = launch("summarize", copy.toString());
        assertEquals(0, summarize.code(), summarize.err());
        assertTrue(Files.readString(copy.resolve("summary.md")).contains("Evidence not captured for smoke."));

        final Result inspect = launch("inspect", copy.toString(), OCCUPANCY_KEY);
        assertEquals(2, inspect.code());
        assertTrue(inspect.err().contains("lies outside the run directory"), inspect.err());
    }

    @Test
    void summarizeReplacesALinkedSummaryInsteadOfWritingThroughIt() throws Exception {
        // Regression: summarize wrote summary.md through an existing link, so a
        // received run could make it overwrite any file the link pointed at.
        final Path copy = work.resolve("linked-summary");
        copyTree(smokeRun, copy);
        final Path victim = work.resolve("victim.txt");
        Files.writeString(victim, "keep", StandardCharsets.UTF_8);
        final Path summary = copy.resolve("summary.md");
        Files.delete(summary);
        symlinkOrSkip(summary, victim);

        final Result result = launch("summarize", copy.toString());
        assertEquals(0, result.code(), result.err());
        assertEquals("keep", Files.readString(victim, StandardCharsets.UTF_8));
        assertFalse(Files.isSymbolicLink(summary));
        assertTrue(Files.readString(summary, StandardCharsets.UTF_8).contains("Elliott research run: smoke"));
    }

    @Test
    void exploreRecipeRejectsWindowsReservedDatasetIdBeforeRunning() throws Exception {
        final Path candles = work.resolve("reserved-candles.json");
        writeCandles(candles, LocalDate.of(2020, 1, 1), 300, date -> true);
        for (final String id : List.of("CON", "nul", "Com1", "lpt9.a", "aux.")) {
            final Path recipe = work.resolve("reserved-recipe.json");
            Files.writeString(recipe, """
                    {"datasetId":"%s","asset":"TOY",
                     "partitions":[{"name":"calibration","start":"2020-01-01","end":"2020-06-30"},
                                   {"name":"validation","start":"2020-07-01","end":"2020-09-30"},
                                   {"name":"holdout","start":"2020-10-01","end":"2020-12-31"}],
                     "forbiddenCalibrationStart":"2024-01-01",
                     "detector":{"name":"fractal-w3","factory":"fractal","params":[3]},
                     "momentum":{"type":"RSI","barCount":14},
                     "null":{"blockLengths":[10],"ensembleSize":2,"seed":7}}
                    """.formatted(id));
            final Path out = work.resolve("explore-reserved");
            final Result result = launch("run", "explore", "--source", candles.toString(), "--recipe",
                    recipe.toString(), "--out", out.toString());
            assertEquals(1, result.code(), id);
            assertTrue(result.err().contains("reserved Windows device name"), result.err());
            assertFalse(Files.exists(out), id);
        }
    }

    private static String toyRecipe(final String datasetId) {
        return """
                {"datasetId":"%s","asset":"TOY",
                 "partitions":[{"name":"calibration","start":"2020-01-01","end":"2020-06-30"},
                               {"name":"validation","start":"2020-07-01","end":"2020-09-30"},
                               {"name":"holdout","start":"2020-10-01","end":"2020-12-31"}],
                 "forbiddenCalibrationStart":"2024-01-01",
                 "detector":{"name":"fractal-w3","factory":"fractal","params":[3]},
                 "momentum":{"type":"RSI","barCount":14},
                 "null":{"blockLengths":[10],"ensembleSize":2,"seed":7}}
                """.formatted(datasetId);
    }

    private static String calibrationRecipe(final String datasetId, final String calibration) {
        return toyRecipe(datasetId).replace("\"null\":", "\"calibration\":" + calibration + ",\n \"null\":");
    }

    private Path runCalibrated(final String name, final String calibration) throws IOException {
        return runRecipe(name, calibrationRecipe("cal", calibration));
    }

    private Path runRecipe(final String name, final String recipeJson) throws IOException {
        final Path candles = work.resolve(name + "-candles.json");
        writeCandles(candles, LocalDate.of(2020, 1, 1), 366, date -> true);
        final Path recipe = work.resolve(name + "-recipe.json");
        Files.writeString(recipe, recipeJson);
        final Path out = work.resolve(name);
        final Result result = launch("run", "explore", "--source", candles.toString(), "--recipe", recipe.toString(),
                "--out", out.toString());
        assertEquals(0, result.code(), result.err());
        return out;
    }

    private static String keyOfSection(final Path run, final String partition, final String section)
            throws IOException {
        for (final ElliottResearchReport.Row row : ElliottResearchReport.readCsv(run.resolve("comparisons.csv"))) {
            if (row.partition().equals(partition) && row.section().equals(section)) {
                return row.key();
            }
        }
        throw new AssertionError("no " + section + " comparison row in partition " + partition);
    }

    private static String keyOf(final Path run, final String partition, final String section, final String grammar)
            throws IOException {
        for (final ElliottResearchReport.Row row : ElliottResearchReport.readCsv(run.resolve("comparisons.csv"))) {
            if (row.partition().equals(partition) && row.section().equals(section) && row.grammar().equals(grammar)) {
                return row.key();
            }
        }
        throw new AssertionError("no " + section + " " + grammar + " comparison row in partition " + partition);
    }

    @Test
    void traceFreeInspectSucceedsOnlyForRowsCoveredByTheCalibrationTables() throws Exception {
        final String recipe = calibrationRecipe("cal", "{\"horizon\":5,\"minGroups\":1}").replace("\"momentum\"",
                "\"robustnessDetectors\":[{\"name\":\"fractal-w5\",\"factory\":\"fractal\",\"params\":[5]}],\n"
                        + " \"momentum\"");
        final Path run = runRecipe("scope", recipe);
        final ElliottResearchCalibration.Identity identity = ElliottResearchCalibration
                .readTables(run.resolve(ElliottResearchCalibration.TABLES_FILE))
                .get(0)
                .identity();
        assertEquals("fractal-w3", identity.detector());
        assertEquals("fractal(3)", identity.detectorConfig());
        assertEquals("RSI(14)", identity.momentum());
        final List<String> summaryLines = Files.readAllLines(run.resolve(ElliottResearchCalibration.SUMMARY_FILE));
        assertTrue(List.of(summaryLines.get(0).split(",", -1)).contains("purged"), summaryLines.get(0));

        final Result primary = launch("inspect", run.toString(), keyOf(run, "holdout", "h1", "MOTIVE_5"), "--limit",
                "2");
        assertEquals(0, primary.code(), primary.err());
        assertTrue(primary.out().contains("estimated event probability:"), primary.out());
        assertFalse(primary.out().contains("not applicable to this scope"), primary.out());

        String robustness = null;
        for (final ElliottResearchReport.Row row : ElliottResearchReport.readCsv(run.resolve("comparisons.csv"))) {
            if (row.partition().equals("holdout") && row.section().equals("robustness")
                    && row.detector().equals("fractal-w5")) {
                robustness = row.key();
                break;
            }
        }
        assertTrue(robustness != null, "the recipe must produce a robustness-detector row");
        final Result other = launch("inspect", run.toString(), robustness, "--limit", "2");
        assertEquals(2, other.code(), other.out());
        assertTrue(other.err().contains("--trace real"), other.err());
        assertFalse(other.out().contains("estimated event probability:"), other.out());

        final Result competing = launch("inspect", run.toString(), keyOfSection(run, "holdout", "competing"), "--limit",
                "2");
        assertEquals(2, competing.code(), competing.out());
        assertTrue(competing.err().contains("--trace real"), competing.err());
        assertFalse(competing.out().contains("estimated event probability:"), competing.out());

        final Result h2 = launch("inspect", run.toString(), keyOfSection(run, "holdout", "h2"));
        assertEquals(2, h2.code(), h2.out());
        assertTrue(h2.err().contains("--trace real"), h2.err());
    }

    private static String keyOfPartition(final Path run, final String partition) throws IOException {
        for (final ElliottResearchReport.Row row : ElliottResearchReport.readCsv(run.resolve("comparisons.csv"))) {
            if (row.partition().equals(partition)) {
                return row.key();
            }
        }
        throw new AssertionError("no comparison row in partition " + partition);
    }

    /**
     * The fingerprint hashes the whole recipe, so it legitimately differs once
     * calibration is configured.
     */
    private static String withoutFingerprint(final Path file) throws IOException {
        return Files.readString(file).replaceAll("explore-[0-9a-f]{64}", "explore-FINGERPRINT");
    }

    @Test
    void calibrationLeavesEveryBaseArtifactByteIdenticalAndAddsDiagnosticsOnlyWhenConfigured() throws Exception {
        final Path candles = work.resolve("both-candles.json");
        writeCandles(candles, LocalDate.of(2020, 1, 1), 366, date -> true);
        final Path plainRecipe = work.resolve("plain-recipe.json");
        Files.writeString(plainRecipe, toyRecipe("cal"));
        final Path plain = work.resolve("plain");
        assertEquals(0, launch("run", "explore", "--source", candles.toString(), "--recipe", plainRecipe.toString(),
                "--out", plain.toString()).code());
        final Path calibrated = runCalibrated("calibrated", "{\"horizon\":5,\"minGroups\":1}");

        for (final String artifact : List.of("comparisons.csv", "coverage.csv", "events.jsonl", "outcomes.csv",
                "outcomes-summary.csv", "reports/cal.json")) {
            assertEquals(withoutFingerprint(plain.resolve(artifact)), withoutFingerprint(calibrated.resolve(artifact)),
                    artifact);
        }
        final List<String> files = List.of(ElliottResearchCalibration.TABLES_FILE,
                ElliottResearchCalibration.PREDICTIONS_FILE, ElliottResearchCalibration.SUMMARY_FILE,
                ElliottResearchCalibration.RELIABILITY_FILE);
        for (final String file : files) {
            assertFalse(Files.exists(plain.resolve(file)), file);
            assertTrue(Files.isRegularFile(calibrated.resolve(file)), file);
        }
        assertFalse(readJson(plain.resolve("run.json")).has("calibration"));
        assertFalse(Files.readString(plain.resolve("summary.md")).contains("## Outcome calibration"));
        final JsonObject block = readJson(calibrated.resolve("run.json")).getAsJsonObject("calibration");
        assertEquals(ElliottResearchCalibration.TABLES_FILE, block.get("tables").getAsString());
        assertEquals(5,
                readJson(calibrated.resolve("run.json")).getAsJsonObject("configuration")
                        .getAsJsonObject("calibration")
                        .get("horizon")
                        .getAsInt());
        final String summary = Files.readString(calibrated.resolve("summary.md"));
        assertTrue(summary.contains("## Outcome calibration"), summary);
        assertTrue(summary.contains("no predictive efficacy"), summary);
    }

    @Test
    void calibrationTablesAreFittedBeforeTheDecisionsTheyScore() throws Exception {
        final Path run = runCalibrated("causal", "{\"horizon\":5,\"minGroups\":1}");
        final List<ElliottResearchCalibration.Calibrator> tables = ElliottResearchCalibration
                .readTables(run.resolve(ElliottResearchCalibration.TABLES_FILE));
        assertEquals(List.of("validation", "evaluation"), tables.stream().map(table -> table.stage()).toList());
        assertEquals(List.of("calibration"), tables.get(0).fitPartitions());
        assertEquals(List.of("calibration", "validation"), tables.get(1).fitPartitions());
        assertTrue(tables.get(0).cutoffIndex() < tables.get(1).cutoffIndex());
        final List<String> lines = Files.readAllLines(run.resolve(ElliottResearchCalibration.PREDICTIONS_FILE));
        final List<String> header = List.of(lines.get(0).split(",", -1));
        assertTrue(lines.size() > 1, "the recipe must enroll scored alternatives");
        assertTrue(
                lines.subList(1, lines.size())
                        .stream()
                        .noneMatch(line -> line.split(",", -1)[header.indexOf("status")]
                                .equals(ElliottResearchCalibration.STATUS_NO_FEATURE)),
                "rules evaluated on the enrolled pivots must give every scored alternative a feature");
        for (final String line : lines.subList(1, lines.size())) {
            final String[] cells = line.split(",", -1);
            final String stage = cells[header.indexOf("stage")];
            final ElliottResearchCalibration.Calibrator table = tables.get(stage.equals("validation") ? 0 : 1);
            assertTrue(Integer.parseInt(cells[header.indexOf("enrollIndex")]) > table.cutoffIndex(), line);
            assertEquals(String.valueOf(table.cutoffIndex()), cells[header.indexOf("fitCutoff")], line);
            assertEquals(stage.equals("validation") ? "validation" : "holdout", cells[header.indexOf("partition")]);
        }
    }

    @Test
    void insufficientSupportAbstainsInsteadOfFabricatingAProbability() throws Exception {
        final Path run = runCalibrated("sparse", "{\"horizon\":5,\"minGroups\":100000}");
        final List<String> lines = Files.readAllLines(run.resolve(ElliottResearchCalibration.PREDICTIONS_FILE));
        final List<String> header = List.of(lines.get(0).split(",", -1));
        assertTrue(lines.size() > 1, "the recipe must enroll scored alternatives");
        for (final String line : lines.subList(1, lines.size())) {
            final String[] cells = line.split(",", -1);
            assertTrue(List.of("unsupported", "no-feature").contains(cells[header.indexOf("status")]), line);
            assertEquals("", cells[header.indexOf("probability")], line);
            assertFalse(cells[header.indexOf("reason")].isEmpty(), line);
        }
        final List<List<String>> summary = ElliottResearchReport
                .parseCsv(run.resolve(ElliottResearchCalibration.SUMMARY_FILE));
        final int estimatorStatus = summary.getFirst().indexOf("estimatorStatus");
        assertTrue(summary.size() > 1, "the recipe must report calibration metrics");
        for (final List<String> row : summary.subList(1, summary.size())) {
            assertEquals("unavailable", row.get(estimatorStatus), row.toString());
        }
    }

    @Test
    void inspectShowsDecisionTimeProbabilitiesAndHidesOutcomesUnlessRetrospective() throws Exception {
        final Path run = runCalibrated("inspect", "{\"horizon\":5,\"minGroups\":1}");
        final String key = keyOfPartition(run, "holdout");
        final Result hidden = launch("inspect", run.toString(), key, "--limit", "2");
        assertEquals(0, hidden.code(), hidden.err());
        assertTrue(hidden.out().contains("Calibration: target " + ElliottResearchCalibration.TARGET), hidden.out());
        assertTrue(hidden.out().contains("Ranking basis: enrollment order"), hidden.out());
        assertTrue(hidden.out().contains("estimated event probability:"), hidden.out());
        assertTrue(hidden.out().contains("fit support:"), hidden.out());
        assertFalse(hidden.out().contains("retrospective outcome:"), hidden.out());
        assertTrue(hidden.out().contains("hidden; rerun with --retrospective"), hidden.out());

        final Result revealed = launch("inspect", run.toString(), key, "--limit", "2", "--retrospective", "--rank",
                "probability");
        assertEquals(0, revealed.code(), revealed.err());
        assertTrue(revealed.out().contains("retrospective outcome:"), revealed.out());
        assertTrue(revealed.out().contains("Ranking basis: estimated event probability"), revealed.out());
        assertTrue(revealed.out().contains("Retrospective evaluation support"), revealed.out());

        final Result badRank = launch("inspect", run.toString(), key, "--rank", "luck");
        assertEquals(1, badRank.code());
        assertTrue(badRank.err().contains("--rank must be"), badRank.err());

        final List<String> lines = Files.readAllLines(run.resolve(ElliottResearchCalibration.PREDICTIONS_FILE));
        final List<String> header = List.of(lines.get(0).split(",", -1));
        final String[] holdout = lines.subList(1, lines.size())
                .stream()
                .map(line -> line.split(",", -1))
                .filter(cells -> cells[header.indexOf("partition")].equals("holdout"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the recipe must score holdout alternatives"));
        final String candidateKey = holdout[header.indexOf("candidateKey")];
        final String version = holdout[header.indexOf("version")];
        final String listed = "- " + candidateKey + " (version " + version;
        for (final String selector : List.of(candidateKey.substring(0, candidateKey.length() - 1), version)) {
            final Result selected = launch("inspect", run.toString(), key, "--candidate", selector);
            assertEquals(0, selected.code(), selected.err());
            assertTrue(selected.out().contains(listed), selector + " must select like trace inspection does");
        }
    }

    @Test
    void calibratedRunWithAMissingDeclaredTraceStillExitsTwo() throws Exception {
        final Path candles = work.resolve("declared-candles.json");
        writeCandles(candles, LocalDate.of(2020, 1, 1), 366, date -> true);
        final Path recipe = work.resolve("declared-recipe.json");
        Files.writeString(recipe, calibrationRecipe("cal", "{\"horizon\":5,\"minGroups\":1}"));
        final Path run = work.resolve("declared");
        final Result result = launch("run", "explore", "--source", candles.toString(), "--recipe", recipe.toString(),
                "--trace", "real", "--out", run.toString());
        assertEquals(0, result.code(), result.err());
        final String key = keyOfPartition(run, "holdout");
        assertEquals(0, launch("inspect", run.toString(), key).code());
        Files.delete(run.resolve("traces/cal-real.jsonl"));

        final Result missing = launch("inspect", run.toString(), key);
        assertEquals(2, missing.code());
        assertTrue(missing.err().contains("no trace was captured"), missing.err());
    }

    @Test
    void summarizeRegeneratesTheCalibrationSectionAndDiagnosesMissingFiles() throws Exception {
        final Path run = runCalibrated("resummarize", "{\"horizon\":5,\"minGroups\":1}");
        final String before = Files.readString(run.resolve("summary.md"));
        assertEquals(0, launch("summarize", run.toString()).code());
        assertEquals(before, Files.readString(run.resolve("summary.md")));
        Files.delete(run.resolve(ElliottResearchCalibration.SUMMARY_FILE));
        final Result missing = launch("summarize", run.toString());
        assertEquals(2, missing.code());
        assertTrue(missing.err().contains(ElliottResearchCalibration.SUMMARY_FILE), missing.err());
    }

    @Test
    void calibrationArtifactLinkedFromOutsideTheRunDirectoryIsNotRead() throws Exception {
        final Path run = runCalibrated("linked-calibration", "{\"horizon\":5,\"minGroups\":1}");
        final Path outside = work.resolve("outside-calibration-summary.csv");
        Files.move(run.resolve(ElliottResearchCalibration.SUMMARY_FILE), outside);
        symlinkOrSkip(run.resolve(ElliottResearchCalibration.SUMMARY_FILE), outside);
        Files.delete(run.resolve("summary.md"));
        final Result linked = launch("summarize", run.toString());
        assertEquals(2, linked.code());
        assertTrue(linked.err().contains("calibration artifact"), linked.err());
        assertFalse(Files.exists(run.resolve("summary.md")), "a refused artifact must not produce a summary");
    }

    @Test
    void runJsonDeclaresTheRecordCountsOfTheCalibrationArtifactsItVouchesFor() throws Exception {
        final JsonObject records = readJson(calibratedRun.resolve("run.json")).getAsJsonObject("calibration")
                .getAsJsonObject("records");

        assertEquals(
                ElliottResearchCalibration.readTables(calibratedRun.resolve(ElliottResearchCalibration.TABLES_FILE))
                        .size(),
                records.get("tables").getAsLong());
        assertEquals(csvRows(calibratedRun, ElliottResearchCalibration.PREDICTIONS_FILE),
                records.get("predictions").getAsLong());
        assertEquals(csvRows(calibratedRun, ElliottResearchCalibration.SUMMARY_FILE),
                records.get("summary").getAsLong());
        assertEquals(csvRows(calibratedRun, ElliottResearchCalibration.RELIABILITY_FILE),
                records.get("reliability").getAsLong());
        for (final String count : List.of("tables", "predictions", "summary", "reliability")) {
            assertTrue(records.get(count).getAsLong() > 0, "the calibrated run must exercise the " + count + " count");
        }
    }

    @Test
    void summarizeRejectsAHeaderOnlyCalibrationSummaryInsteadOfReportingNoEvidence() throws Exception {
        final Result result = summarizeAfterEditing(calibratedRun, "calibration-summary-header-only",
                ElliottResearchCalibration.SUMMARY_FILE, text -> text.substring(0, text.indexOf("\r\n") + 2));

        assertIncomplete(result, "calibration-summary.csv holds 0 rows but the run recorded ");
    }

    @Test
    void summarizeAndInspectRefuseCalibrationRowsThatDisagreeWithTheRecordedCounts() throws Exception {
        for (final String artifact : List.of(ElliottResearchCalibration.PREDICTIONS_FILE,
                ElliottResearchCalibration.SUMMARY_FILE, ElliottResearchCalibration.RELIABILITY_FILE)) {
            for (final Damage damage : ROW_DAMAGES) {
                assertRefusedBySummarizeAndInspect(artifact, damage);
            }
        }
    }

    @Test
    public void summarizeAndInspectRefuseBlankCalibrationCsvIdentities() throws IOException {
        final Map<String, List<String>> fields = Map.of(ElliottResearchCalibration.PREDICTIONS_FILE,
                List.of("dataset", "stage", "partition", "candidateKey", "version", "direction", "target"),
                ElliottResearchCalibration.SUMMARY_FILE,
                List.of("dataset", "stage", "partition", "view", "target", "fitPartitions"),
                ElliottResearchCalibration.RELIABILITY_FILE, List.of("dataset", "stage", "view"));
        for (final String artifact : List.of(ElliottResearchCalibration.PREDICTIONS_FILE,
                ElliottResearchCalibration.SUMMARY_FILE, ElliottResearchCalibration.RELIABILITY_FILE)) {
            final List<List<String>> table = ElliottResearchReport.parseCsv(calibratedRun.resolve(artifact));
            assertTrue(table.size() > 1, "the calibrated run must exercise " + artifact);
            final List<String> header = table.get(0);
            for (final String field : fields.get(artifact)) {
                for (final String blank : List.of("", "  ")) {
                    final List<List<String>> rows = new ArrayList<>(table.subList(1, table.size()));
                    final List<String> cells = new ArrayList<>(rows.get(0));
                    cells.set(header.indexOf(field), blank);
                    rows.set(0, cells);
                    final Path damagedCsv = work.resolve("blank-calibration-identity.csv");
                    ElliottResearchReport.writeTable(damagedCsv, header, rows);
                    final String damaged = Files.readString(damagedCsv, StandardCharsets.UTF_8);
                    assertRefusedBySummarizeAndInspect(artifact, new Damage("blank-" + field + "-" + blank.length(),
                            text -> damaged, " row 1 has blank " + field + " identity"));
                }
            }
        }
    }

    @Test
    public void summarizeAndInspectRefuseNarrowedCalibrationIntegers() throws IOException {
        for (final String field : List.of("identity.horizon", "policy.minGroups", "fit.firstIndex", "fit.cutoffIndex",
                "base.groups", "base.successes", "base.failures", "masks.bins.groups", "masks.bins.successes",
                "masks.bins.failures")) {
            assertRefusedBySummarizeAndInspect(ElliottResearchCalibration.TABLES_FILE,
                    new Damage("narrowed-" + field.replace('.', '-'), text -> {
                        final JsonObject file = JsonParser.parseString(text).getAsJsonObject();
                        for (final JsonElement element : file.getAsJsonArray("tables")) {
                            final JsonObject table = element.getAsJsonObject();
                            final JsonObject owner = field.startsWith("masks.")
                                    ? table.getAsJsonArray("masks").get(0).getAsJsonObject().getAsJsonArray("bins").get(0)
                                            .getAsJsonObject()
                                    : table.getAsJsonObject(field.substring(0, field.indexOf('.')));
                            final String name = field.substring(field.lastIndexOf('.') + 1);
                            owner.addProperty(name, owner.get(name).getAsLong() + 4294967296L);
                        }
                        return file.toString();
                    }, field + " must be an integer"));
        }
    }

    @Test
    void summarizeAndInspectRefuseACalibrationTablesFileThatIsNotWhole() throws Exception {
        final String tables = ElliottResearchCalibration.TABLES_FILE;
        final String notATableFile = " is not a calibration table file";

        assertRefusedBySummarizeAndInspect(tables, new Damage("empty", text -> "", notATableFile));
        assertRefusedBySummarizeAndInspect(tables,
                new Damage("cut", text -> text.substring(0, text.length() / 2), notATableFile));
        assertRefusedBySummarizeAndInspect(tables, new Damage("lost-table", text -> {
            final JsonObject file = JsonParser.parseString(text).getAsJsonObject();
            final JsonArray held = file.getAsJsonArray("tables");
            held.remove(held.size() - 1);
            return file.toString();
        }, " holds 1 tables but the run recorded 2"));
    }

    @Test
    void summarizeAndInspectRefuseARunThatNeverDeclaredItsCalibrationRecordCounts() throws Exception {
        // A run interrupted while publishing keeps the checkpoint run.json: status
        // "running" and no record counts, beside artifacts that may look complete.
        final Path copy = editedCopy(calibratedRun, "calibration-no-records", "run.json", text -> {
            final JsonObject run = JsonParser.parseString(text).getAsJsonObject();
            run.addProperty("status", "running");
            run.getAsJsonObject("calibration").remove("records");
            return run.toString();
        });

        final Result inspected = launch("inspect", copy.toString(), keyOfPartition(copy, "holdout"));
        assertEquals(2, inspected.code(), inspected.err());
        assertTrue(inspected.err().contains("declares the calibration artifacts without their record counts"),
                inspected.err());
        assertEquals("", inspected.out());
        final Result summarized = summarizeExpectingNoSummary(copy);
        assertEquals(2, summarized.code(), summarized.err());
        assertTrue(summarized.err().contains("declares the calibration artifacts without their record counts"),
                summarized.err());
    }

    @Test
    void summarizeRejectsCalibrationRecordCountsThatLackAnArtifact() throws Exception {
        final Path copy = editedCopy(calibratedRun, "calibration-partial-records", "run.json", text -> {
            final JsonObject run = JsonParser.parseString(text).getAsJsonObject();
            run.getAsJsonObject("calibration").getAsJsonObject("records").remove("reliability");
            return run.toString();
        });

        assertIncomplete(summarizeExpectingNoSummary(copy), "the run declares no reliability record count");
    }

    @Test
    void aCalibrationRunThatEnrolledNothingIsEmptyNotIncomplete() throws Exception {
        // Flat prices enroll no alternative: the run records zero predictions beside a
        // header-only file, which is an honest empty result readers must keep
        // accepting.
        final Path candles = work.resolve("flat-candles.json");
        writeCandles(candles, LocalDate.of(2020, 1, 1), 366, date -> true, index -> 100.0d);
        final Path recipe = work.resolve("flat-recipe.json");
        Files.writeString(recipe, calibrationRecipe("cal", "{\"horizon\":5,\"minGroups\":1}"));
        final Path run = work.resolve("flat");
        final Result ran = launch("run", "explore", "--source", candles.toString(), "--recipe", recipe.toString(),
                "--out", run.toString());
        assertEquals(0, ran.code(), ran.err());
        final JsonObject records = readJson(run.resolve("run.json")).getAsJsonObject("calibration")
                .getAsJsonObject("records");
        assertEquals(0L, records.get("predictions").getAsLong());
        assertEquals(0L, csvRows(run, ElliottResearchCalibration.PREDICTIONS_FILE));
        assertTrue(records.get("summary").getAsLong() > 0, records.toString());

        Files.delete(run.resolve("summary.md"));
        final Result summarized = launch("summarize", run.toString());
        assertEquals(0, summarized.code(), summarized.err());
        assertTrue(Files.isRegularFile(run.resolve("summary.md")), "the run is complete, so it is summarized");
        final Result inspected = launch("inspect", run.toString(), keyOfPartition(run, "holdout"));
        assertEquals(0, inspected.code(), inspected.err());
        assertTrue(inspected.out().contains("No calibration predictions for dataset cal partition holdout"),
                inspected.out());
    }

    @Test
    void aRunThatRecordedNoCalibrationRowsStillReportsNoCalibrationEvidence() throws Exception {
        final Path copy = work.resolve("calibration-recorded-empty");
        copyTree(calibratedRun, copy);
        for (final String artifact : List.of(ElliottResearchCalibration.PREDICTIONS_FILE,
                ElliottResearchCalibration.SUMMARY_FILE, ElliottResearchCalibration.RELIABILITY_FILE)) {
            final String text = Files.readString(copy.resolve(artifact), StandardCharsets.UTF_8);
            Files.writeString(copy.resolve(artifact), text.substring(0, text.indexOf("\r\n") + 2),
                    StandardCharsets.UTF_8);
        }
        Files.writeString(copy.resolve(ElliottResearchCalibration.TABLES_FILE),
                "{\"schema\":\"" + ElliottResearchCalibration.SCHEMA + "\",\"tables\":[]}\n", StandardCharsets.UTF_8);
        final JsonObject run = readJson(copy.resolve("run.json"));
        final JsonObject records = run.getAsJsonObject("calibration").getAsJsonObject("records");
        for (final String count : List.of("tables", "predictions", "summary", "reliability")) {
            records.addProperty(count, 0);
        }
        Files.writeString(copy.resolve("run.json"), run.toString(), StandardCharsets.UTF_8);
        Files.delete(copy.resolve("summary.md"));

        final Result result = launch("summarize", copy.toString());

        assertEquals(0, result.code(), result.err());
        assertTrue(Files.readString(copy.resolve("summary.md")).contains("No dataset produced calibration evidence."));
    }

    @Test
    void calibrationRecipeErrorsFailBeforeCreatingArtifacts() throws Exception {
        final Path candles = work.resolve("bad-cal-candles.json");
        writeCandles(candles, LocalDate.of(2020, 1, 1), 366, date -> true);
        final Map<String, String> cases = Map.of("{}", "calibration.horizon is required", "{\"horizon\":7}",
                "must be one of outcomes.horizons", "{\"horizon\":5,\"fit\":\"nope\"}", "unknown partition 'nope'",
                "{\"horizon\":5,\"extra\":1}", "unknown field 'extra'");
        for (final Map.Entry<String, String> entry : cases.entrySet()) {
            final Path recipe = work.resolve("bad-cal-recipe.json");
            Files.writeString(recipe, calibrationRecipe("cal", entry.getKey()));
            final Path out = work.resolve("bad-cal-out");
            final Result result = launch("run", "explore", "--source", candles.toString(), "--recipe",
                    recipe.toString(), "--out", out.toString());
            assertEquals(1, result.code(), entry.getKey());
            assertTrue(result.err().contains(entry.getValue()), result.err());
            assertFalse(Files.exists(out), entry.getKey());
        }
    }

    private static JsonObject exploreCoverage(final Path out) throws IOException {
        return readJson(out.resolve("run.json")).getAsJsonArray("datasets")
                .get(0)
                .getAsJsonObject()
                .getAsJsonObject("coverage");
    }

    @Test
    void exploreCoverageIgnoresIntervalsBetweenNoncontiguousPartitions() throws Exception {
        final Path recipe = work.resolve("noncontiguous-recipe.json");
        Files.writeString(recipe, noncontiguousRecipe());
        final Path candles = work.resolve("noncontiguous-candles.json");
        writeCandles(candles, LocalDate.of(2020, 1, 1), 152, date -> date.getMonthValue() % 2 == 1);
        final Path out = work.resolve("explore-noncontiguous");
        final Result result = launch("run", "explore", "--source", candles.toString(), "--recipe", recipe.toString(),
                "--out", out.toString());
        assertEquals(0, result.code(), result.err());
        final JsonObject coverage = exploreCoverage(out);
        assertEquals("complete", coverage.get("status").getAsString(), coverage.toString());
        assertEquals("", coverage.get("message").getAsString());
    }

    @Test
    void exploreCoverageStillFlagsInternalGapsInNoncontiguousPartitions() throws Exception {
        final Path recipe = work.resolve("noncontiguous-gappy-recipe.json");
        Files.writeString(recipe, noncontiguousRecipe());
        final Path candles = work.resolve("noncontiguous-gappy-candles.json");
        writeCandles(candles, LocalDate.of(2020, 1, 1), 152, date -> date.getMonthValue() % 2 == 1
                && (date.isBefore(LocalDate.of(2020, 3, 10)) || date.isAfter(LocalDate.of(2020, 3, 18))));
        final Path out = work.resolve("explore-noncontiguous-gappy");
        final Result result = launch("run", "explore", "--source", candles.toString(), "--recipe", recipe.toString(),
                "--out", out.toString());
        assertEquals(0, result.code(), result.err());
        final JsonObject coverage = exploreCoverage(out);
        assertEquals("partial", coverage.get("status").getAsString(), coverage.toString());
        assertEquals("1 internal gap(s) longer than 7 bar periods, widest 2020-03-09T00:00:00Z..2020-03-19T00:00:00Z",
                coverage.get("message").getAsString());
    }

    @Test
    public void exploreCoverageChecksBothEdgesOfEveryPopulatedPartition() throws Exception {
        final Path recipe = work.resolve("partition-edges-recipe.json");
        Files.writeString(recipe, noncontiguousRecipe());
        final int[] firstDays = { 16, 9, 1, 8 };
        final int[] lastDays = { 16, 31, 23, 24 };
        for (int index = 0; index < firstDays.length; index++) {
            final LocalDate first = LocalDate.of(2020, 3, firstDays[index]);
            final LocalDate last = LocalDate.of(2020, 3, lastDays[index]);
            final Path candles = work.resolve("partition-edges-" + index + ".json");
            writeCandles(candles, LocalDate.of(2020, 1, 1), 152, date -> date.getMonthValue() == 1
                    || date.getMonthValue() == 5 || !date.isBefore(first) && !date.isAfter(last));
            if (index == 3) {
                final JsonObject shifted = readJson(candles);
                for (final com.google.gson.JsonElement candle : shifted.getAsJsonArray("candles")) {
                    final JsonObject item = candle.getAsJsonObject();
                    item.addProperty("start", item.get("start").getAsLong() + 43_200);
                }
                Files.writeString(candles, shifted.toString());
            }
            final Path out = work.resolve("partition-edges-" + index);
            final Result result = launch("run", "explore", "--source", candles.toString(), "--recipe",
                    recipe.toString(), "--out", out.toString());
            assertEquals(0, result.code(), result.err());
            final JsonObject coverage = exploreCoverage(out);
            assertEquals(index == 3 ? "complete" : "partial", coverage.get("status").getAsString(),
                    coverage.toString());
            final String message = coverage.get("message").getAsString();
            assertEquals(index == 0 || index == 1, message.contains("partition validation starts at"), message);
            assertEquals(index == 0 || index == 2, message.contains("partition validation ends at"), message);
            assertFalse(message.contains("internal gap"), message);
        }
    }

    @Test
    public void exploreCoverageAppliesTheSameToleranceAtBothOuterBoundaries() throws Exception {
        final Path recipe = work.resolve("outer-boundaries-recipe.json");
        Files.writeString(recipe, toyRecipe("outer-boundaries"));
        final LocalDate start = LocalDate.of(2020, 1, 1);
        final LocalDate end = LocalDate.of(2020, 12, 31);
        for (final int missingDays : List.of(1, 7, 8)) {
            final LocalDate first = start.plusDays(missingDays);
            final LocalDate last = end.minusDays(missingDays);
            final Path candles = work.resolve("outer-boundaries-" + missingDays + ".json");
            writeCandles(candles, start, 366, date -> !date.isBefore(first) && !date.isAfter(last));
            final Path out = work.resolve("outer-boundaries-" + missingDays);
            final Result result = launch("run", "explore", "--source", candles.toString(), "--recipe",
                    recipe.toString(), "--out", out.toString());
            assertEquals(0, result.code(), result.err());
            final JsonObject coverage = exploreCoverage(out);
            final boolean partial = missingDays > 7;
            assertEquals(partial ? "partial" : "complete", coverage.get("status").getAsString(), coverage.toString());
            final String message = coverage.get("message").getAsString();
            assertEquals(partial, message.contains("partition calibration starts at"), message);
            assertEquals(partial, message.contains("partition holdout ends at"), message);
            if (!partial) {
                assertEquals("", message);
            }
        }
    }

    private static String noncontiguousRecipe() {
        return toyRecipe("noncontiguous").replace("2020-06-30", "2020-01-31")
                .replace("2020-07-01", "2020-03-01")
                .replace("2020-09-30", "2020-03-31")
                .replace("2020-10-01", "2020-05-01")
                .replace("2020-12-31", "2020-05-31");
    }

    @Test
    void exploreCoverageFlagsMissingBarsInsideAPopulatedPartition() throws Exception {
        // Regression: coverage only asked whether each partition held any bar, so
        // weeks missing inside a partition were reported complete.
        final Path recipe = work.resolve("internal-gap-recipe.json");
        Files.writeString(recipe, toyRecipe("gappy"));
        final Path tolerated = work.resolve("week-candles.json");
        writeCandles(tolerated, LocalDate.of(2020, 1, 1), 366,
                date -> date.isBefore(LocalDate.of(2020, 5, 2)) || date.isAfter(LocalDate.of(2020, 5, 7)));
        final Path toleratedOut = work.resolve("explore-week");
        assertEquals(0, launch("run", "explore", "--source", tolerated.toString(), "--recipe", recipe.toString(),
                "--out", toleratedOut.toString()).code());
        assertEquals("complete", exploreCoverage(toleratedOut).get("status").getAsString(),
                "a gap of exactly seven bar periods stays within tolerance");

        final Path gappy = work.resolve("gappy-candles.json");
        writeCandles(gappy, LocalDate.of(2020, 1, 1), 366,
                date -> date.isBefore(LocalDate.of(2020, 3, 1)) || date.isAfter(LocalDate.of(2020, 3, 20)));
        final Path gappyOut = work.resolve("explore-gappy");
        assertEquals(0, launch("run", "explore", "--source", gappy.toString(), "--recipe", recipe.toString(), "--out",
                gappyOut.toString()).code());
        final JsonObject coverage = exploreCoverage(gappyOut);
        assertEquals("partial", coverage.get("status").getAsString(), coverage.toString());
        assertEquals("1 internal gap(s) longer than 7 bar periods, widest 2020-02-29T00:00:00Z..2020-03-21T00:00:00Z",
                coverage.get("message").getAsString());
    }

    @Test
    void exploreRecipeRejectsDatasetIdTooLongForArtifactNames() throws Exception {
        final Path candles = work.resolve("long-candles.json");
        writeCandles(candles, LocalDate.of(2020, 1, 1), 366, date -> true);
        final Path recipe = work.resolve("long-recipe.json");
        Files.writeString(recipe, toyRecipe("a".repeat(221)));
        final Path out = work.resolve("explore-long");
        final Result rejected = launch("run", "explore", "--source", candles.toString(), "--recipe", recipe.toString(),
                "--out", out.toString());
        assertEquals(1, rejected.code());
        assertTrue(rejected.err().contains("recipe.datasetId is 221 characters; at most 220"), rejected.err());
        assertFalse(Files.exists(out));

        Files.writeString(recipe, toyRecipe("a".repeat(220)));
        final Result accepted = launch("run", "explore", "--source", candles.toString(), "--recipe", recipe.toString(),
                "--trace", "selected-null-member", "--block", "10", "--member", "1", "--out", out.toString());
        assertEquals(0, accepted.code(), accepted.err());
        assertTrue(Files.isRegularFile(out.resolve("traces/" + "a".repeat(220) + "-null-b10-m1.jsonl")));
    }

    @Test
    void exploreRecaptureRefusesInputsEditedSinceTheRun() throws Exception {
        // Regression: the recapture command re-read the recorded paths without
        // checking them, pairing a trace of edited inputs with the old rows.
        final Path candles = work.resolve("recapture-candles.json");
        writeCandles(candles, LocalDate.of(2020, 1, 1), 366, date -> true);
        final Path recipe = work.resolve("recapture-recipe.json");
        Files.writeString(recipe, toyRecipe("recap"));
        final Path out = work.resolve("explore-recap");
        assertEquals(0, launch("run", "explore", "--source", candles.toString(), "--recipe", recipe.toString(), "--out",
                out.toString()).code());
        final JsonObject run = readJson(out.resolve("run.json"));
        final String sourceSha256 = run.getAsJsonArray("datasets")
                .get(0)
                .getAsJsonObject()
                .getAsJsonObject("source")
                .get("sha256")
                .getAsString();
        final String fingerprint = run.getAsJsonObject("identity").get("fingerprint").getAsString();
        final String key = ElliottResearchReport.readCsv(out.resolve("comparisons.csv")).get(0).key();
        final Result inspect = launch("inspect", out.toString(), key);
        assertEquals(2, inspect.code());
        assertTrue(inspect.err().contains("--expect-source-sha256 " + sourceSha256), inspect.err());
        assertTrue(inspect.err().contains("--expect-fingerprint " + fingerprint), inspect.err());

        writeCandles(candles, LocalDate.of(2020, 1, 2), 366, date -> true);
        final Path editedSource = work.resolve("recap-edited-source");
        final Result sourceChanged = launch("run", "explore", "--source", candles.toString(), "--recipe",
                recipe.toString(), "--expect-source-sha256", sourceSha256, "--expect-fingerprint", fingerprint, "--out",
                editedSource.toString());
        assertEquals(1, sourceChanged.code());
        assertTrue(sourceChanged.err().contains("the file changed since the run"), sourceChanged.err());
        assertFalse(Files.exists(editedSource));

        writeCandles(candles, LocalDate.of(2020, 1, 1), 366, date -> true);
        Files.writeString(recipe, toyRecipe("recap").replace("\"seed\":7", "\"seed\":8"));
        final Path editedRecipe = work.resolve("recap-edited-recipe");
        final Result recipeChanged = launch("run", "explore", "--source", candles.toString(), "--recipe",
                recipe.toString(), "--expect-source-sha256", sourceSha256, "--expect-fingerprint", fingerprint, "--out",
                editedRecipe.toString());
        assertEquals(1, recipeChanged.code());
        assertTrue(recipeChanged.err().contains("the recipe changed since the run"), recipeChanged.err());
        assertFalse(Files.exists(editedRecipe));
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

    private static void symlinkOrSkip(final Path link, final Path target) {
        try {
            Files.createSymbolicLink(link, target);
        } catch (final UnsupportedOperationException | IOException unsupported) {
            assumeTrue(false, "symbolic links are unavailable: " + unsupported);
        }
    }

    private Result summarizeAfterEditing(final String name, final String artifact, final UnaryOperator<String> edit)
            throws IOException {
        return summarizeAfterEditing(smokeRun, name, artifact, edit);
    }

    private Result summarizeAfterEditing(final Path source, final String name, final String artifact,
            final UnaryOperator<String> edit) throws IOException {
        return summarizeExpectingNoSummary(editedCopy(source, name, artifact, edit));
    }

    /**
     * A copy of {@code source} in which {@code artifact} is rewritten by
     * {@code edit}.
     */
    private Path editedCopy(final Path source, final String name, final String artifact,
            final UnaryOperator<String> edit) throws IOException {
        final Path copy = work.resolve(name);
        copyTree(source, copy);
        Files.writeString(copy.resolve(artifact), edit.apply(Files.readString(copy.resolve(artifact))),
                StandardCharsets.UTF_8);
        return copy;
    }

    /** Summarizes a run that must be refused, after dropping its stale summary. */
    private static Result summarizeExpectingNoSummary(final Path copy) throws IOException {
        Files.delete(copy.resolve("summary.md"));
        final Result result = launch("summarize", copy.toString());
        assertFalse(Files.exists(copy.resolve("summary.md")), "an incomplete run must not get a summary");
        return result;
    }

    /**
     * Both readers refuse the calibrated run once {@code artifact} is damaged, and
     * inspect prints nothing before refusing.
     */
    private void assertRefusedBySummarizeAndInspect(final String artifact, final Damage damage) throws IOException {
        final Path copy = editedCopy(calibratedRun, artifact + "-" + damage.name(), artifact, damage.edit());

        final Result inspected = launch("inspect", copy.toString(), keyOfPartition(copy, "holdout"));
        assertIncomplete(inspected, damage.reason());
        assertEquals("", inspected.out(), "inspect must not print partial evidence: " + copy.getFileName());
        assertIncomplete(summarizeExpectingNoSummary(copy), damage.reason());
    }

    private static void assertIncomplete(final Result result, final String reason) {
        assertEquals(2, result.code(), result.err());
        assertTrue(result.err().contains(reason), result.err());
        assertTrue(result.err().contains("the run is incomplete, rerun the recipe"), result.err());
    }

    private static long csvRows(final Path run, final String name) {
        return ElliottResearchReport.parseCsv(run.resolve(name)).size() - 1L;
    }

    private static long csvRows(final String name) {
        return csvRows(smokeRun, name);
    }
}

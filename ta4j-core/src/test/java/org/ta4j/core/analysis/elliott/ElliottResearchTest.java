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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.IntToDoubleFunction;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import com.google.gson.Gson;
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
    private static Path nullMemberRun;

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

        nullMemberRun = shared.resolve("smoke-null-b20-m3");
        final Result member = launch("run", "smoke", "--trace", "selected-null-member", "--block", "20", "--member",
                "3", "--out", nullMemberRun.toString());
        assertEquals(0, member.code(), member.err());
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
    public void comparisonInventoryRejectsMissingRowsMetricsModesAndPartitionsBeforeSelection() throws Exception {
        final List<ElliottResearchReport.Row> rows = ElliottResearchReport.readCsv(smokeRun.resolve("comparisons.csv"));
        for (final String missing : List.of("one-row", "metric", "mode", "partition", "dataset")) {
            final Path copy = work.resolve("missing-inventory-" + missing);
            Files.createDirectories(copy);
            Files.copy(smokeRun.resolve("run.json"), copy.resolve("run.json"));
            final List<ElliottResearchReport.Row> retained = new ArrayList<>(rows);
            retained.removeIf(row -> switch (missing) {
            case "one-row" -> row.key().equals(rows.getLast().key());
            case "metric" -> "labelStabilityJaccard".equals(row.metric());
            case "mode" -> "h2".equals(row.section()) && "+wave2-origin".equals(row.mode());
            case "partition" -> "holdout".equals(row.partition());
            default -> "smoke".equals(row.dataset());
            });
            assertTrue(retained.size() < rows.size(), missing);
            writeBoundComparisons(copy, readJson(copy.resolve("run.json")), retained);
            assertInventoryRejected(copy, OCCUPANCY_KEY, "missing ", rows.size() - retained.size());
            final Result foreignSelection = launch("inspect", copy.toString(),
                    OCCUPANCY_KEY.replace("smoke|", "other|"));
            assertEquals(2, foreignSelection.code(), foreignSelection.err());
            assertTrue(foreignSelection.err().contains("row-key inventory"), foreignSelection.err());
            assertFalse(foreignSelection.err().contains("unknown dataset"), foreignSelection.err());
        }
    }

    @Test
    public void comparisonInventoryRejectsInventedCoordinatesEvenWhenAnotherRowIsSelected() throws Exception {
        final List<ElliottResearchReport.Row> rows = ElliottResearchReport.readCsv(smokeRun.resolve("comparisons.csv"));
        for (final String coordinate : List.of("dataset", "section", "mode", "detector", "partition", "block")) {
            final ElliottResearchReport.Row original = "block".equals(coordinate) ? rows.getFirst() : rows.getLast();
            for (final boolean append : List.of(false, true)) {
                final Path copy = work.resolve("foreign-inventory-" + coordinate + "-" + append);
                Files.createDirectories(copy);
                Files.copy(smokeRun.resolve("run.json"), copy.resolve("run.json"));
                final List<ElliottResearchReport.Row> mutated = new ArrayList<>(rows);
                if (!append) {
                    mutated.remove(original);
                }
                mutated.add(new ElliottResearchReport.Row("dataset".equals(coordinate) ? "foreign" : original.dataset(),
                        original.asset(), "section".equals(coordinate) ? "foreign" : original.section(),
                        "mode".equals(coordinate) ? "foreign" : original.mode(), original.grammar(),
                        original.activeRules(), "detector".equals(coordinate) ? "foreign" : original.detector(),
                        original.policy(), "partition".equals(coordinate) ? "foreign" : original.partition(),
                        original.metric(), "block".equals(coordinate) ? Integer.MAX_VALUE : original.nullBlockLength(),
                        original.observed(), original.numerator(), original.denominator(), original.validNullMembers(),
                        original.requestedNullMembers(), original.unavailableNullMembers(), original.nullMedian(),
                        original.nullLow(), original.nullHigh(), original.observedMinusNullMedian(),
                        original.empiricalReferenceRank(), original.availability()));
                writeBoundComparisons(copy, readJson(copy.resolve("run.json")), mutated);
                assertInventoryRejected(copy, OCCUPANCY_KEY, "unexpected ", 1);
                final String diagnostic = launch("inspect", copy.toString(), OCCUPANCY_KEY).err();
                assertTrue(diagnostic.contains(mutated.getLast().key()), diagnostic);
                assertTrue(diagnostic.contains("missing " + (append ? 0 : 1) + " key(s)"), diagnostic);
            }
        }
    }

    @Test
    public void comparisonFamiliesMustMatchRecordedMetadataBeforeSelectingAnotherRow() throws Exception {
        final List<ElliottResearchReport.Row> rows = ElliottResearchReport.readCsv(smokeRun.resolve("comparisons.csv"));
        for (final String field : List.of("asset", "grammar", "activeRules", "policy", "requestedNullMembers")) {
            final Path copy = work.resolve("foreign-family-" + field);
            Files.createDirectories(copy);
            Files.copy(smokeRun.resolve("run.json"), copy.resolve("run.json"));
            final List<ElliottResearchReport.Row> mutated = new ArrayList<>(rows.size());
            String changedKey = null;
            for (final ElliottResearchReport.Row row : rows) {
                final boolean evidenceFamily = "activeRules".equals(field) || "requestedNullMembers".equals(field);
                final boolean replace = evidenceFamily ? "h2".equals(row.section()) && "all-rules".equals(row.mode())
                        : "robustness".equals(row.section());
                if (!replace) {
                    mutated.add(row);
                    continue;
                }
                changedKey = row.key();
                mutated.add(new ElliottResearchReport.Row(row.dataset(),
                        "asset".equals(field) ? "foreign-asset" : row.asset(), row.section(), row.mode(),
                        "grammar".equals(field) ? "CYCLE_5_3" : row.grammar(),
                        "activeRules".equals(field) ? row.activeRules().reversed() : row.activeRules(), row.detector(),
                        "policy".equals(field) ? "kernel-topology" : row.policy(), row.partition(), row.metric(),
                        row.nullBlockLength(), row.observed(), row.numerator(), row.denominator(),
                        row.validNullMembers(),
                        row.requestedNullMembers() + ("requestedNullMembers".equals(field) ? 1 : 0),
                        row.unavailableNullMembers() + ("requestedNullMembers".equals(field) ? 1 : 0), row.nullMedian(),
                        row.nullLow(), row.nullHigh(), row.observedMinusNullMedian(), row.empiricalReferenceRank(),
                        row.availability()));
            }
            assertNotNull(changedKey, field);
            assertFalse(OCCUPANCY_KEY.equals(changedKey), field);
            writeBoundComparisons(copy, readJson(copy.resolve("run.json")), mutated);
            Files.writeString(copy.resolve("summary.md"), "keep the existing summary");
            for (final Result rejected : List.of(launch("summarize", copy.toString()),
                    launch("inspect", copy.toString(), OCCUPANCY_KEY))) {
                assertEquals(2, rejected.code(), rejected.out() + rejected.err());
                assertTrue(rejected.err().contains("comparisons.csv family metadata " + field), rejected.err());
                assertTrue(rejected.err().contains("run.json's recorded configuration"), rejected.err());
                assertTrue(rejected.err().contains("rerun the recipe to regenerate"), rejected.err());
            }
            assertEquals("keep the existing summary", Files.readString(copy.resolve("summary.md")));
        }
    }

    @Test
    public void frozenInventoryRequiresCompletedDatasetsButNotFailedOrPendingDatasetsOrTraces() throws Exception {
        final Path runDir = frozenInventoryRun();
        final JsonObject run = readJson(runDir.resolve("run.json"));
        final List<ElliottResearchReport.Row> rows = ElliottResearchReport.readCsv(runDir.resolve("comparisons.csv"));
        final String key = rows.getFirst().key();
        final Result initial = launch("summarize", runDir.toString());
        assertEquals(0, initial.code(), initial.err());
        final Result noTrace = launch("inspect", runDir.toString(), key);
        assertEquals(2, noTrace.code(), noTrace.err());
        assertTrue(noTrace.err().contains("Recapture with:"), noTrace.err());
        assertFalse(noTrace.err().contains("row-key inventory"), noTrace.err());
        assertTrue(rows.stream().anyMatch(row -> "classical-all".equals(row.mode())));
        assertTrue(rows.stream().noneMatch(row -> "all-rules".equals(row.mode())));
        final JsonObject omitted = run.getAsJsonArray("datasets").get(1).getAsJsonObject();
        final String omittedId = omitted.get("id").getAsString();
        final List<ElliottResearchReport.Row> retained = rows.stream()
                .filter(row -> !omittedId.equals(row.dataset()))
                .toList();
        assertTrue(retained.size() < rows.size());
        writeBoundComparisons(runDir, run, retained);
        assertInventoryRejected(runDir, key, "missing ", rows.size() - retained.size());
        final String omittedKey = rows.stream()
                .filter(row -> omittedId.equals(row.dataset()))
                .findFirst()
                .orElseThrow()
                .key();
        for (final String status : List.of("failed", "pending")) {
            run.addProperty("status", "failed".equals(status) ? "partial" : "running");
            omitted.addProperty("status", status);
            omitted.addProperty("message", "intentional " + status + " dataset");
            omitted.getAsJsonObject("coverage").addProperty("status", "failed");
            omitted.add("report", null);
            writeBoundComparisons(runDir, run, retained);
            final Result summary = launch("summarize", runDir.toString());
            assertEquals(0, summary.code(), summary.err());
            final Result failedSelection = launch("inspect", runDir.toString(), omittedKey);
            assertEquals(2, failedSelection.code(), failedSelection.err());
            assertTrue(failedSelection.err().contains("intentional " + status + " dataset"), failedSelection.err());
            assertFalse(failedSelection.err().contains("row-key inventory"), failedSelection.err());
            final Result completeSelection = launch("inspect", runDir.toString(), key);
            assertEquals(2, completeSelection.code(), completeSelection.err());
            assertTrue(completeSelection.err().contains("Recapture with:"), completeSelection.err());
        }
        run.addProperty("status", "failed");
        for (final JsonElement element : run.getAsJsonArray("datasets")) {
            element.getAsJsonObject().addProperty("status", "failed");
            element.getAsJsonObject().getAsJsonObject("coverage").addProperty("status", "failed");
        }
        writeBoundComparisons(runDir, run, List.of());
        final Result allFailed = launch("summarize", runDir.toString());
        assertEquals(0, allFailed.code(), allFailed.err());
        assertTrue(ElliottResearchReport.readCsv(runDir.resolve("comparisons.csv")).isEmpty());
    }

    @Test
    public void exploreInventoryPreservesOneRuleEmptyAlternativesAndConfiguredDetectorAndBlockBoundaries()
            throws Exception {
        final Path candles = work.resolve("inventory-candles.json");
        writeCandles(candles, LocalDate.of(2020, 1, 1), 3, date -> true);
        final JsonObject definition = JsonParser.parseString(toyRecipe("inventory")).getAsJsonObject();
        definition.add("activeRules", JsonParser.parseString("[\"wave4-nonoverlap\"]"));
        definition.add("competingModes", new JsonArray());
        definition.getAsJsonObject("null").add("blockLengths", JsonParser.parseString("[1,10]"));
        definition.getAsJsonObject("null").addProperty("ensembleSize", 1);
        final Path recipe = work.resolve("inventory-recipe.json");
        for (final boolean withRobustness : List.of(false, true)) {
            definition.add("robustnessDetectors",
                    JsonParser.parseString(withRobustness
                            ? "[{\"name\":\"custom-primary\",\"factory\":\"fractal\",\"params\":[3]},"
                                    + "{\"name\":\"custom-slope\",\"factory\":\"slopeChange\",\"params\":[2]},"
                                    + "{\"name\":\"custom-prominence\",\"factory\":\"prominence\",\"params\":[]}]"
                            : "[]"));
            definition.getAsJsonObject("detector").addProperty("name", "custom-primary");
            Files.writeString(recipe, definition.toString());
            final Path out = work.resolve("inventory-explore-" + withRobustness);
            final Result produced = launch("run", "explore", "--source", candles.toString(), "--recipe",
                    recipe.toString(), "--out", out.toString());
            assertEquals(0, produced.code(), produced.err());
            final List<ElliottResearchReport.Row> rows = ElliottResearchReport.readCsv(out.resolve("comparisons.csv"));
            assertEquals(withRobustness ? 189 : 144, rows.size());
            assertTrue(rows.stream().noneMatch(row -> "competing".equals(row.section())));
            for (final String mode : List.of("+wave4-nonoverlap", "all-rules")) {
                assertTrue(rows.stream()
                        .anyMatch(row -> mode.equals(row.mode()) && "evidencePassRate".equals(row.metric())));
                assertTrue(
                        rows.stream().anyMatch(row -> mode.equals(row.mode()) && "jointPassRate".equals(row.metric())));
            }
            assertTrue(rows.stream()
                    .filter(row -> "robustness".equals(row.section()))
                    .allMatch(row -> row.nullBlockLength() == -1));
            final Result summary = launch("summarize", out.toString());
            assertEquals(0, summary.code(), summary.err());
            final Result inspect = launch("inspect", out.toString(), rows.getFirst().key());
            assertEquals(2, inspect.code(), inspect.err());
            assertTrue(inspect.err().contains("Recapture with:"), inspect.err());
            assertFalse(inspect.err().contains("row-key inventory"), inspect.err());
        }
    }

    private static void writeBoundComparisons(final Path dir, final JsonObject run,
            final List<ElliottResearchReport.Row> rows) throws Exception {
        final Path csv = dir.resolve("comparisons.csv");
        ElliottResearchReport.writeCsv(csv, rows);
        run.addProperty("comparisonsSha256",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(csv))));
        Files.writeString(dir.resolve("run.json"), run.toString());
    }

    private static void assertInventoryRejected(final Path dir, final String key, final String countLabel,
            final int count) throws Exception {
        Files.writeString(dir.resolve("summary.md"), "keep the existing summary");
        for (final Result rejected : List.of(launch("summarize", dir.toString()),
                launch("inspect", dir.toString(), key))) {
            assertEquals(2, rejected.code(), rejected.out() + rejected.err());
            assertTrue(rejected.err().contains("comparisons.csv row-key inventory"), rejected.err());
            assertTrue(rejected.err().contains("run.json's recorded configuration"), rejected.err());
            assertTrue(rejected.err().contains(countLabel + count + " key(s)"), rejected.err());
            assertTrue(rejected.err().contains("rerun the recipe to regenerate"), rejected.err());
        }
        assertEquals("keep the existing summary", Files.readString(dir.resolve("summary.md")));
    }

    private Path frozenInventoryRun() throws Exception {
        final ElliottStudyProtocol protocol = ElliottStudyProtocol.load();
        final ElliottStudyProtocol.Partitions bounds = protocol.partitions();
        final StudyRunner.Partitions partitions = new StudyRunner.Partitions(
                List.of(new StudyRunner.Partition("calibration", bounds.calibrationStart(), bounds.calibrationEnd()),
                        new StudyRunner.Partition("validation", bounds.validationStart(), bounds.validationEnd()),
                        new StudyRunner.Partition("holdout", bounds.holdoutStart(), bounds.holdoutEnd())),
                bounds.forbiddenCalibrationStart());
        final List<DetectorRobustnessMatrix.DetectorSpec> detectors = DetectorRobustnessMatrix.defaults();
        final DetectorRobustnessMatrix.DetectorSpec primary = detectors.stream()
                .filter(detector -> protocol.primaryDetector().equals(detector.name()))
                .findFirst()
                .orElseThrow();
        final StudyRunner runner = StudyRunner.frozenPreregistered(primary.factory(),
                series -> new RSIIndicator(new ClosePriceIndicator(series), protocol.momentumIndicator().barCount()),
                new StudyRunner.Configuration(partitions, protocol.fingerprintSha256(), protocol.nullEnsemble().seed(),
                        protocol.nullEnsemble().blockLengths(), protocol.nullEnsemble().ensembleSize(), detectors,
                        protocol.primaryDetector(), protocol.competingGrammars()),
                protocol.ablationSet());
        // One out-of-window bar exercises the frozen producer's full unavailable
        // inventory without evaluating bundled market histories.
        final BarSeries series = new BaseBarSeriesBuilder().withName("inventory-boundary").build();
        series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(bounds.calibrationStart().minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant())
                .openPrice(1)
                .highPrice(1)
                .lowPrice(1)
                .closePrice(1)
                .volume(1)
                .add();
        final Path dir = Files.createDirectories(work.resolve("frozen-inventory"));
        Files.createDirectories(dir.resolve("reports"));
        final JsonObject run = readJson(smokeRun.resolve("run.json"));
        run.getAsJsonObject("recipe").addProperty("name", "frozen-cf525");
        run.getAsJsonObject("trace").addProperty("mode", "off");
        run.getAsJsonObject("identity").addProperty("fingerprint", protocol.fingerprintSha256());
        final JsonObject configuration = run.getAsJsonObject("configuration");
        final ElliottResearchOutcomes.Settings outcomeSettings = ElliottResearch.frozenOutcomeSettings();
        configuration.add("outcomes", outcomeSettings.toJson());
        final Gson gson = new Gson();
        final JsonArray partitionJson = new JsonArray();
        for (final StudyRunner.Partition partition : partitions.entries()) {
            final JsonObject entry = new JsonObject();
            entry.addProperty("name", partition.name());
            entry.addProperty("start", partition.start().toString());
            entry.addProperty("end", partition.end().toString());
            partitionJson.add(entry);
        }
        configuration.add("partitions", partitionJson);
        configuration.addProperty("forbiddenCalibrationStart", bounds.forbiddenCalibrationStart().toString());
        configuration.add("robustnessDetectors", gson.toJsonTree(protocol.detectorConfigurations()));
        configuration.add("detector",
                gson.toJsonTree(protocol.detectorConfigurations()
                        .stream()
                        .filter(detector -> protocol.primaryDetector().equals(detector.name()))
                        .findFirst()
                        .orElseThrow()));
        configuration.add("momentum", gson.toJsonTree(protocol.momentumIndicator()));
        configuration.add("competingModes", gson.toJsonTree(protocol.competingGrammars()));
        final JsonObject nulls = gson.toJsonTree(protocol.nullEnsemble()).getAsJsonObject();
        nulls.remove("type");
        configuration.add("null", nulls);
        final JsonObject template = run.getAsJsonArray("datasets").get(0).getAsJsonObject();
        final JsonArray datasets = new JsonArray();
        final List<ElliottResearchReport.Row> rows = new ArrayList<>();
        for (final ElliottStudyProtocol.DatasetSpec spec : protocol.datasets()) {
            final StudyReport report = runner.evaluate(spec.asset(), series, 0, 0);
            rows.addAll(ElliottResearchReport.comparisons(spec.id(), report));
            Files.writeString(dir.resolve("reports").resolve(spec.id() + ".json"), report.toJson());
            final JsonObject dataset = template.deepCopy();
            dataset.addProperty("id", spec.id());
            dataset.addProperty("asset", spec.asset());
            dataset.getAsJsonObject("source").addProperty("generator", "inventory-boundary");
            dataset.getAsJsonObject("requested").addProperty("from", bounds.calibrationStart().toString());
            dataset.getAsJsonObject("requested").addProperty("to", bounds.holdoutEnd().toString());
            dataset.getAsJsonObject("effective").add("from", null);
            dataset.getAsJsonObject("effective").add("to", null);
            dataset.addProperty("bars", 0);
            dataset.getAsJsonObject("coverage").addProperty("status", "partial");
            dataset.getAsJsonObject("coverage").addProperty("message", "no bars inside the requested window");
            dataset.addProperty("report", "reports/" + spec.id() + ".json");
            dataset.add("traces", new JsonArray());
            dataset.add("traceSha256", new JsonObject());
            datasets.add(dataset);
        }
        run.add("datasets", datasets);
        final List<ElliottResearchOutcomes.Result> outcomes = List.of();
        ElliottResearchOutcomes.writeEvents(dir.resolve(ElliottResearchOutcomes.EVENTS_FILE),
                protocol.fingerprintSha256(), outcomeSettings, outcomes);
        ElliottResearchOutcomes.writeOutcomes(dir.resolve(ElliottResearchOutcomes.OUTCOMES_FILE), outcomes);
        ElliottResearchOutcomes.writeSummary(dir.resolve(ElliottResearchOutcomes.SUMMARY_FILE), outcomes);
        run.getAsJsonObject("outcomes").add("records", ElliottResearchOutcomes.recordCounts(outcomes));
        writeBoundComparisons(dir, run, rows);
        return dir;
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
                "relations/input.json", "families/input.json", ElliottResearchOutcomes.EVENTS_FILE,
                ElliottResearchOutcomes.OUTCOMES_FILE, ElliottResearchOutcomes.SUMMARY_FILE,
                ElliottResearchCalibration.TABLES_FILE, ElliottResearchCalibration.PREDICTIONS_FILE,
                ElliottResearchCalibration.SUMMARY_FILE, ElliottResearchCalibration.RELIABILITY_FILE)) {
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
    public void metadataFailuresPreserveTheLastValidSnapshotAndCleanTemporaryFiles() throws Exception {
        final Path candles = work.resolve("metadata-candles.json");
        writeCandles(candles, LocalDate.of(2020, 1, 1), 3, date -> true);
        final Path recipe = work.resolve("metadata-recipe.json");
        Files.writeString(recipe, toyRecipe("metadata"));
        for (final String failurePoint : List.of("write", "replace", "unsupported-atomic")) {
            final Path out = work.resolve("metadata-" + failurePoint);
            final Result initial = launch("run", "explore", "--source", candles.toString(), "--recipe",
                    recipe.toString(), "--out", out.toString());
            assertEquals(0, initial.code(), initial.err());
            final Path metadata = out.resolve("run.json");
            final String previous = Files.readString(metadata, StandardCharsets.UTF_8);
            final JsonObject replacement = readJson(metadata);
            replacement.addProperty("status", "running");
            final ElliottResearch.MetadataFiles files = new ElliottResearch.MetadataFiles() {
                @Override
                public void write(final Path temporary, final String contents) throws IOException {
                    Files.writeString(temporary, "write".equals(failurePoint) ? "{" : contents, StandardCharsets.UTF_8);
                    if ("write".equals(failurePoint)) {
                        throw new IOException("injected partial metadata write");
                    }
                }

                @Override
                public void replace(final Path temporary, final Path target) throws IOException {
                    assertEquals(ElliottResearch.SCHEMA,
                            readJson(temporary).get("artifactSchemaVersion").getAsString());
                    if ("unsupported-atomic".equals(failurePoint)) {
                        throw new AtomicMoveNotSupportedException(temporary.toString(), target.toString(),
                                "injected unsupported atomic replacement");
                    }
                    throw new IOException("injected metadata replacement failure");
                }
            };
            final IOException failure = assertThrows(IOException.class,
                    () -> ElliottResearch.writeRunJson(out, replacement, files));
            assertTrue(failure.getMessage().contains("injected"), failure.getMessage());
            assertEquals(previous, Files.readString(metadata, StandardCharsets.UTF_8));
            assertEquals("complete", readJson(metadata).get("status").getAsString());
            assertNoMetadataTemporaryFiles(out);

            final Result recovered = launch("run", "explore", "--source", candles.toString(), "--recipe",
                    recipe.toString(), "--out", out.toString(), "--overwrite");
            assertEquals(0, recovered.code(), recovered.err());
            final JsonObject complete = readJson(metadata);
            assertEquals("complete", complete.get("status").getAsString());
            assertEquals(
                    HexFormat.of()
                            .formatHex(MessageDigest.getInstance("SHA-256")
                                    .digest(Files.readAllBytes(out.resolve("comparisons.csv")))),
                    complete.get("comparisonsSha256").getAsString());
            assertNoMetadataTemporaryFiles(out);
        }
    }

    private static void assertNoMetadataTemporaryFiles(final Path dir) throws IOException {
        try (Stream<Path> children = Files.list(dir)) {
            assertEquals(List.of(),
                    children.filter(child -> child.getFileName().toString().startsWith("run.json-")).toList());
        }
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
        writeBoundComparisons(copy, run, List.of());

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
    public void explorePreservesRecipeRuleOrderAcrossExecutionAndEveryArtifact() throws Exception {
        final Path candles = work.resolve("ordered-rule-candles.json");
        writeCandles(candles, LocalDate.of(2020, 1, 1), 366, date -> true);
        int index = 0;
        for (final List<String> order : List.of(
                List.of("wave5-divergence", "wave4-nonoverlap", "wave3-not-shortest", "wave2-origin"),
                List.of("wave5-divergence", "wave2-origin", "wave4-nonoverlap"))) {
            final JsonObject definition = JsonParser.parseString(toyRecipe("ordered")).getAsJsonObject();
            final JsonArray activeRules = new JsonArray();
            order.forEach(activeRules::add);
            definition.add("activeRules", activeRules);
            final Path recipe = work.resolve("ordered-rule-recipe-" + index + ".json");
            Files.writeString(recipe, definition.toString());
            final Path out = work.resolve("ordered-rules-" + index++);
            final Result result = launch("run", "explore", "--source", candles.toString(), "--recipe",
                    recipe.toString(), "--trace", "real", "--out", out.toString());
            assertEquals(0, result.code(), result.err());
            assertEquals(activeRules,
                    readJson(out.resolve("run.json")).getAsJsonObject("configuration").getAsJsonArray("activeRules"));
            final JsonArray modes = readJson(out.resolve("reports/ordered.json")).getAsJsonObject("H2")
                    .getAsJsonArray("modes");
            for (int i = 0; i < order.size(); i++) {
                assertEquals("+" + order.get(i), modes.get(i + 1).getAsJsonObject().get("mode").getAsString());
            }
            assertEquals(activeRules, modes.get(modes.size() - 1).getAsJsonObject().getAsJsonArray("activeRules"));
            final List<ElliottResearchReport.Row> rows = ElliottResearchReport.readCsv(out.resolve("comparisons.csv"))
                    .stream()
                    .filter(row -> "h2".equals(row.section()) && "all-rules".equals(row.mode()))
                    .toList();
            assertFalse(rows.isEmpty());
            rows.forEach(row -> assertEquals(order, row.activeRules()));
            final List<JsonObject> records = ElliottResearchTrace.read(out.resolve("traces/ordered-real.jsonl"))
                    .records()
                    .stream()
                    .filter(record -> "h2".equals(record.get("section").getAsString())
                            && "all-rules".equals(record.get("mode").getAsString()))
                    .toList();
            assertFalse(records.isEmpty());
            int candidateCount = 0;
            for (final JsonObject record : records) {
                assertEquals(activeRules, record.getAsJsonArray("activeRules"));
                for (final com.google.gson.JsonElement candidate : record.getAsJsonArray("candidates")) {
                    final JsonArray rules = candidate.getAsJsonObject().getAsJsonArray("rules");
                    assertEquals(order.size(), rules.size());
                    for (int i = 0; i < order.size(); i++) {
                        assertEquals(order.get(i), rules.get(i).getAsJsonObject().get("id").getAsString());
                    }
                    candidateCount++;
                }
            }
            assertTrue(candidateCount > 0);
        }
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
    public void exploreRecipeRequiresSemanticPartitionOrderBeforeCreatingArtifacts() throws Exception {
        final Path candles = work.resolve("partition-order-candles.json");
        writeCandles(candles, LocalDate.of(2020, 1, 1), 3, date -> true);
        final Path recipe = work.resolve("partition-order-recipe.json");
        final List<String> expectedOrder = List.of("calibration", "validation", "holdout");
        int index = 0;
        for (final List<String> order : List.of(expectedOrder, List.of("validation", "calibration", "holdout"),
                List.of("calibration", "holdout", "validation"), List.of("holdout", "validation", "calibration"),
                List.of("validation", "holdout", "calibration"), List.of("holdout", "calibration", "validation"))) {
            final JsonObject definition = JsonParser.parseString(toyRecipe("partition-order")).getAsJsonObject();
            final JsonArray partitions = definition.getAsJsonArray("partitions");
            for (int partition = 0; partition < order.size(); partition++) {
                partitions.get(partition).getAsJsonObject().addProperty("name", order.get(partition));
            }
            Files.writeString(recipe, definition.toString());
            final Path out = work.resolve("partition-order-" + index++);
            final Result result = launch("run", "explore", "--source", candles.toString(), "--recipe",
                    recipe.toString(), "--out", out.toString());
            if (expectedOrder.equals(order)) {
                assertEquals(0, result.code(), result.err());
                assertEquals(partitions, readJson(out.resolve("run.json")).getAsJsonObject("configuration")
                        .getAsJsonArray("partitions"));
            } else {
                assertEquals(1, result.code(), order + ": " + result.err());
                assertTrue(result.err().contains("recipe.partitions invalid"), result.err());
                assertTrue(result.err().contains("calibration -> validation -> holdout"), result.err());
                assertFalse(Files.exists(out), order.toString());
            }
        }
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
    public void exploreRecipeRejectsFormulaLeadingCsvLabelsBeforeRunning() throws Exception {
        final Path candles = work.resolve("formula-candles.json");
        writeCandles(candles, LocalDate.of(2020, 1, 1), 366, date -> true);
        final Path recipe = work.resolve("formula-recipe.json");
        int index = 0;
        for (final String field : List.of("asset", "partitions[1].name", "detector.name",
                "robustnessDetectors[0].name")) {
            for (final String prefix : List.of("=", "+", "-", "@", "\t", "\r")) {
                final JsonObject definition = recipeWithLabel(field, prefix + "1+1");
                Files.writeString(recipe, definition.toString());
                final Path out = work.resolve("formula-label-" + index++);
                final Result result = launch("run", "explore", "--source", candles.toString(), "--recipe",
                        recipe.toString(), "--out", out.toString());
                assertEquals(1, result.code(), result.err());
                assertTrue(
                        result.err()
                                .contains("recipe." + field + " must not start with a spreadsheet formula character"),
                        result.err());
                assertFalse(Files.exists(out));
            }
        }
    }

    @Test
    public void exploreRecipeRejectsStructuralControlsInEveryCsvLabelBeforeRunning() throws Exception {
        final Path candles = work.resolve("control-candles.json");
        writeCandles(candles, LocalDate.of(2020, 1, 1), 366, date -> true);
        final Path recipe = work.resolve("control-recipe.json");
        int index = 0;
        for (final String field : List.of("asset", "partitions[1].name", "detector.name",
                "robustnessDetectors[0].name")) {
            for (final String control : List.of("\n", "\t", "\r", "\0", "\u001b", "\u0085", "\u2028", "\u2029")) {
                for (final boolean leading : List.of(false, true)) {
                    final String value = (leading ? "" : "label") + control + "value";
                    Files.writeString(recipe, recipeWithLabel(field, value).toString());
                    final Path out = work.resolve("control-label-" + index++);
                    final Result result = launch("run", "explore", "--source", candles.toString(), "--recipe",
                            recipe.toString(), "--out", out.toString());
                    assertEquals(1, result.code(), result.err());
                    final String diagnostic = leading && ("\t".equals(control) || "\r".equals(control))
                            ? " must not start with a spreadsheet formula character"
                            : " must not contain control characters or line separators";
                    assertTrue(result.err().contains("recipe." + field + diagnostic), result.err());
                    assertFalse(Files.exists(out));
                }
            }
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
    public void runAndSummarizeRejectSymbolicLinksAtTheLockPath() throws Exception {
        for (final String command : List.of("run", "overwrite", "summarize")) {
            for (final boolean existing : List.of(false, true)) {
                final String label = command + "-" + existing;
                final Path dir = work.resolve("symlink-lock-" + label);
                if ("run".equals(command)) {
                    Files.createDirectories(dir);
                } else {
                    copyTree(smokeRun, dir);
                }
                final Path lock = dir.resolve(".run.lock");
                Files.deleteIfExists(lock);
                final Path target = work.resolve("outside-lock-" + label);
                if (existing) {
                    Files.writeString(target, "outside file must stay untouched");
                }
                Files.createSymbolicLink(lock, target);
                final String metadata = "run".equals(command) ? null : Files.readString(dir.resolve("run.json"));
                final String summary = "run".equals(command) ? null : Files.readString(dir.resolve("summary.md"));
                final Result result = switch (command) {
                case "summarize" -> launch("summarize", dir.toString());
                case "overwrite" -> launch("run", "smoke", "--out", dir.toString(), "--overwrite");
                default -> launch("run", "smoke", "--out", dir.toString());
                };
                assertEquals(1, result.code(), label + ": " + result.err());
                assertTrue(Files.isSymbolicLink(lock), label);
                assertEquals(target, Files.readSymbolicLink(lock), label);
                if (existing) {
                    assertEquals("outside file must stay untouched", Files.readString(target), label);
                } else {
                    assertFalse(Files.exists(target), label + " created the outside lock target");
                }
                if (metadata == null) {
                    assertFalse(Files.exists(dir.resolve("run.json")), label);
                    assertFalse(Files.exists(dir.resolve("summary.md")), label);
                } else {
                    assertEquals(metadata, Files.readString(dir.resolve("run.json")), label);
                    assertEquals(summary, Files.readString(dir.resolve("summary.md")), label);
                }
            }
        }
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
                                    ? table.getAsJsonArray("masks")
                                            .get(0)
                                            .getAsJsonObject()
                                            .getAsJsonArray("bins")
                                            .get(0)
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

    private static JsonObject recipeWithLabel(final String field, final String value) {
        final JsonObject definition = JsonParser.parseString(toyRecipe("labels")).getAsJsonObject();
        switch (field) {
        case "asset" -> definition.addProperty("asset", value);
        case "partitions[1].name" -> {
            final JsonArray original = definition.getAsJsonArray("partitions");
            original.get(0).getAsJsonObject().addProperty("end", "2020-03-31");
            final JsonObject extra = new JsonObject();
            extra.addProperty("name", value);
            extra.addProperty("start", "2020-04-01");
            extra.addProperty("end", "2020-06-30");
            final JsonArray partitions = new JsonArray();
            partitions.add(original.get(0));
            partitions.add(extra);
            partitions.add(original.get(1));
            partitions.add(original.get(2));
            definition.add("partitions", partitions);
        }
        case "detector.name" -> definition.getAsJsonObject("detector").addProperty("name", value);
        case "robustnessDetectors[0].name" -> {
            final JsonObject detector = definition.getAsJsonObject("detector").deepCopy();
            detector.addProperty("name", value);
            final JsonArray detectors = new JsonArray();
            detectors.add(detector);
            definition.add("robustnessDetectors", detectors);
        }
        default -> throw new AssertionError(field);
        }
        return definition;
    }

    private static JsonObject exploreCoverage(final Path out) throws IOException {
        return readJson(out.resolve("run.json")).getAsJsonArray("datasets")
                .get(0)
                .getAsJsonObject()
                .getAsJsonObject("coverage");
    }

    @Test
    public void exploreCoverageIgnoresIntervalsBetweenNoncontiguousPartitions() throws Exception {
        final Path recipe = work.resolve("noncontiguous-recipe.json");
        Files.writeString(recipe, noncontiguousRecipe());
        final Path candles = work.resolve("noncontiguous-candles.json");
        writeCandles(candles, LocalDate.of(2020, 1, 1), 152, date -> true);
        final Path out = work.resolve("explore-noncontiguous");
        final Result result = launch("run", "explore", "--source", candles.toString(), "--recipe", recipe.toString(),
                "--out", out.toString());
        assertEquals(0, result.code(), result.err());
        final JsonObject coverage = exploreCoverage(out);
        assertEquals("complete", coverage.get("status").getAsString(), coverage.toString());
        assertEquals("", coverage.get("message").getAsString());
        final JsonObject dataset = readJson(out.resolve("run.json")).getAsJsonArray("datasets")
                .get(0)
                .getAsJsonObject();
        assertEquals(93, dataset.get("bars").getAsInt(), dataset.toString());
        assertEquals("2020-01-01", dataset.getAsJsonObject("effective").get("from").getAsString());
        assertEquals("2020-05-31", dataset.getAsJsonObject("effective").get("to").getAsString());
        assertTrue(Files.readString(out.resolve("coverage.csv"))
                .contains("noncontiguous,TOY,2020-01-01,2020-05-31,2020-01-01,2020-05-31,93,complete,"));
    }

    @Test
    public void exploreCoverageCountsOnlyRequestedBarsAndTheirEffectiveBounds() throws Exception {
        final Path recipe = work.resolve("coverage-union-recipe.json");
        Files.writeString(recipe, noncontiguousRecipe());
        for (final boolean includeRequested : List.of(false, true)) {
            final Path candles = work.resolve("coverage-union-" + includeRequested + "-candles.json");
            writeCandles(candles, LocalDate.of(2020, 1, 1), 152,
                    date -> date.getMonthValue() % 2 == 0 || includeRequested
                            && !date.isBefore(LocalDate.of(2020, 3, 10)) && !date.isAfter(LocalDate.of(2020, 3, 12)));
            final Path out = work.resolve("coverage-union-" + includeRequested);
            final Result result = launch("run", "explore", "--source", candles.toString(), "--recipe",
                    recipe.toString(), "--out", out.toString());
            assertEquals(0, result.code(), result.err());
            final JsonObject dataset = readJson(out.resolve("run.json")).getAsJsonArray("datasets")
                    .get(0)
                    .getAsJsonObject();
            assertEquals(includeRequested ? 3 : 0, dataset.get("bars").getAsInt(), dataset.toString());
            final JsonObject effective = dataset.getAsJsonObject("effective");
            final String bounds;
            if (includeRequested) {
                assertEquals("2020-03-10", effective.get("from").getAsString());
                assertEquals("2020-03-12", effective.get("to").getAsString());
                bounds = "2020-03-10,2020-03-12,3";
            } else {
                assertTrue(effective.get("from").isJsonNull(), effective.toString());
                assertTrue(effective.get("to").isJsonNull(), effective.toString());
                assertEquals("no bars inside the requested window",
                        dataset.getAsJsonObject("coverage").get("message").getAsString());
                bounds = ",,0";
            }
            assertEquals("partial", dataset.getAsJsonObject("coverage").get("status").getAsString());
            assertTrue(Files.readString(out.resolve("coverage.csv"))
                    .contains("noncontiguous,TOY,2020-01-01,2020-05-31," + bounds + ",partial,"));
        }
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

    private static String hierarchyRecipe(final String hierarchy) {
        return """
                {"datasetId":"toy","asset":"TOY",
                 "partitions":[{"name":"calibration","start":"2020-01-01","end":"2020-06-30"},
                               {"name":"validation","start":"2020-07-01","end":"2020-09-30"},
                               {"name":"holdout","start":"2020-10-01","end":"2020-12-31"}],
                 "forbiddenCalibrationStart":"2024-01-01",
                 "detector":{"name":"fractal-w3","factory":"fractal","params":[3]},
                 "robustnessDetectors":[{"name":"fractal-w5","factory":"fractal","params":[5]}],
                 "momentum":{"type":"RSI","barCount":14},
                 "null":{"blockLengths":[10],"ensembleSize":2,"seed":7}%s}
                """.formatted(hierarchy == null ? "" : ",\"hierarchy\":" + hierarchy);
    }

    private Path hierarchyRun(final String name, final String hierarchy) throws IOException {
        final Path candles = work.resolve(name + "-candles.json");
        writeCandles(candles, LocalDate.of(2020, 1, 1), 366, date -> true);
        final Path recipe = work.resolve(name + "-recipe.json");
        Files.writeString(recipe, hierarchyRecipe(hierarchy));
        final Path out = work.resolve(name);
        final Result result = launch("run", "explore", "--source", candles.toString(), "--recipe", recipe.toString(),
                "--out", out.toString());
        assertEquals(0, result.code(), result.err());
        return out;
    }

    @Test
    void hierarchyRecipeWritesRelationArtifactAndOperatorCanReplayIt() throws Exception {
        final Path out = hierarchyRun("hier",
                "{\"scales\":[{\"detector\":\"fractal-w5\",\"degree\":\"intermediate\",\"timeframe\":\"PT24H\"},"
                        + "{\"detector\":\"fractal-w3\",\"degree\":\"minor\"}],\"edgeCap\":50}");
        assertTrue(Files.isRegularFile(out.resolve("relations/toy.jsonl")));
        final JsonObject relations = readJson(out.resolve("run.json")).getAsJsonArray("datasets")
                .get(0)
                .getAsJsonObject()
                .getAsJsonObject("relations");
        assertEquals("relations/toy.jsonl", relations.get("file").getAsString());
        assertTrue(relations.get("frames").getAsLong() > 0, relations.toString());
        assertTrue(relations.get("peakRetainedEdges").getAsInt() <= 50, relations.toString());
        final String summary = Files.readString(out.resolve("summary.md"));
        assertTrue(summary.contains("## Scale relations"), summary);
        assertTrue(summary.contains("fractal-w5 > fractal-w3"), summary);

        final Result print = launch("relations", out.toString(), "toy", "--limit", "5");
        assertEquals(0, print.code(), print.err());
        assertTrue(print.out().contains("As of index"), print.out());
        assertTrue(print.out().contains("Active by state:"), print.out());

        final Result early = launch("relations", out.toString(), "toy", "--as-of", "1");
        assertEquals(0, early.code(), early.err());
        assertTrue(early.out().contains("no relation was observable yet"), early.out());

        final Result missing = launch("relations", out.toString(), "nope");
        assertEquals(1, missing.code());
    }

    @Test
    void runWithoutHierarchyWritesNoRelationArtifacts() throws Exception {
        final Path out = hierarchyRun("no-hier", null);
        assertFalse(Files.exists(out.resolve("relations")));
        assertFalse(
                readJson(out.resolve("run.json")).getAsJsonArray("datasets").get(0).getAsJsonObject().has("relations"));
        assertFalse(Files.readString(out.resolve("summary.md")).contains("Scale relations"));
        final Result none = launch("relations", out.toString(), "toy");
        assertEquals(2, none.code());
        assertTrue(none.err().contains("recorded no scale relations"), none.err());
    }

    @Test
    void hierarchyRecipeIsRejectedBeforeCreatingArtifacts() throws Exception {
        final Path candles = work.resolve("hier-bad-candles.json");
        writeCandles(candles, LocalDate.of(2020, 1, 1), 366, date -> true);
        final Map<String, String> cases = new java.util.LinkedHashMap<>();
        cases.put("{\"scales\":[{\"detector\":\"fractal-w5\"},{\"detector\":\"ghost\"}]}",
                "recipe.hierarchy.scales[1].detector 'ghost'");
        cases.put("{\"scales\":[{\"detector\":\"fractal-w5\"}]}", "needs 2 to");
        cases.put("{\"scales\":[{\"detector\":\"fractal-w5\"},{\"detector\":\"fractal-w5\"}]}",
                "a scale cannot be related");
        cases.put("{\"scales\":[{\"detector\":\"fractal-w5\"},{\"detector\":\"fractal-w3\",\"dataset\":\"other\"}]}",
                "cross-source hierarchies are not supported");
        cases.put("{\"scales\":[{\"detector\":\"fractal-w5\"},{\"detector\":\"fractal-w3\",\"timeframe\":\"PT1H\"}]}",
                "does not match the bar period");
        cases.put("{\"scales\":[{\"detector\":\"fractal-w5\"},{\"detector\":\"fractal-w3\"}],\"edgeCap\":0}",
                "edgeCap must be positive");
        cases.put(
                "{\"scales\":[{\"detector\":\"fractal-w5\"},{\"detector\":\"fractal-w3\"}],\"interiorAnchors\":\"x\"}",
                "interiorAnchors must be contiguous or allow-skipped");
        cases.put("{\"scales\":[{\"detector\":\"fractal-w5\"},{\"detector\":\"fractal-w3\"}],\"extra\":1}",
                "unknown field recipe.hierarchy.extra");
        int index = 0;
        for (final Map.Entry<String, String> entry : cases.entrySet()) {
            final Path recipe = work.resolve("hier-bad-recipe.json");
            Files.writeString(recipe, hierarchyRecipe(entry.getKey()));
            final Path out = work.resolve("hier-bad-" + index++);
            final Result result = launch("run", "explore", "--source", candles.toString(), "--recipe",
                    recipe.toString(), "--out", out.toString());
            assertEquals(1, result.code(), entry.getKey());
            assertTrue(result.err().contains(entry.getValue()), entry.getValue() + " in " + result.err());
            assertFalse(Files.exists(out), entry.getKey());
        }
    }

    private static final String TWO_SCALES = "{\"scales\":[{\"detector\":\"fractal-w5\"},{\"detector\":\"fractal-w3\"}]}";

    private Result runWithFamilies(final String name, final String hierarchy, final String families) throws Exception {
        return runWithFamilies(name, hierarchy, families, "off");
    }

    private Result runWithFamilies(final String name, final String hierarchy, final String families, final String trace)
            throws Exception {
        final Path candles = work.resolve(name + "-candles.json");
        writeCandles(candles, LocalDate.of(2020, 1, 1), 366, date -> true);
        final Path recipe = work.resolve(name + "-recipe.json");
        Files.writeString(recipe, hierarchyRecipe(hierarchy).replace("\"null\":{",
                (families == null ? "" : "\"families\":" + families + ",") + "\"null\":{"));
        return launch("run", "explore", "--source", candles.toString(), "--recipe", recipe.toString(), "--trace", trace,
                "--out", work.resolve(name).toString());
    }

    /** Candidate rule outcomes {@code state -> count} across a run's real trace. */
    private static Map<String, Integer> ruleStates(final Path trace) throws IOException {
        final Map<String, Integer> counts = new java.util.TreeMap<>();
        int candidates = 0;
        for (final JsonObject record : ElliottResearchTrace.read(trace).records()) {
            for (final com.google.gson.JsonElement candidate : record.getAsJsonArray("candidates")) {
                candidates++;
                for (final com.google.gson.JsonElement rule : candidate.getAsJsonObject().getAsJsonArray("rules")) {
                    counts.merge(rule.getAsJsonObject().get("state").getAsString(), 1, Integer::sum);
                }
            }
        }
        counts.put("candidates", candidates);
        return counts;
    }

    @Test
    void failedBaseRuleCandidatesStayVisibleAndCountedWhenFamiliesAreEnabled() throws Exception {
        assertEquals(0, runWithFamilies("base-plain", TWO_SCALES, null, "real").code());
        assertEquals(0,
                runWithFamilies("base-fam", TWO_SCALES,
                        "{\"profiles\":[{\"id\":\"zigzag\"},{\"id\":\"regular-flat\"},{\"id\":\"expanded-flat\"}]}",
                        "real").code());
        final Path plainTrace = work.resolve("base-plain/traces/toy-real.jsonl");
        final Path familyTrace = work.resolve("base-fam/traces/toy-real.jsonl");

        final Map<String, Integer> plain = ruleStates(plainTrace);
        assertTrue(plain.getOrDefault("FAIL", 0) > 0,
                "the launcher's configured rules must fail some toy candidate: " + plain);
        assertEquals(plain, ruleStates(familyTrace));
        assertEquals(fingerprintless(Files.readString(plainTrace, StandardCharsets.UTF_8)),
                fingerprintless(Files.readString(familyTrace, StandardCharsets.UTF_8)));
        assertEquals(Files.readString(work.resolve("base-plain/comparisons.csv"), StandardCharsets.UTF_8),
                Files.readString(work.resolve("base-fam/comparisons.csv"), StandardCharsets.UTF_8));

        final String key = ElliottResearchReport.readCsv(work.resolve("base-fam/comparisons.csv"))
                .stream()
                .filter(row -> !row.activeRules().isEmpty())
                .findFirst()
                .orElseThrow(() -> new AssertionError("no row evaluated the launcher's rules"))
                .key();
        final Result inspectPlain = launch("inspect", work.resolve("base-plain").toString(), key);
        final Result inspectFamilies = launch("inspect", work.resolve("base-fam").toString(), key);
        assertEquals(0, inspectPlain.code(), inspectPlain.err());
        assertEquals(0, inspectFamilies.code(), inspectFamilies.err());
        for (final String prefix : List.of("Status tallies:", "Rule disagreements across modes:", "Support check:")) {
            assertEquals(lineStartingWith(inspectPlain.out(), prefix), lineStartingWith(inspectFamilies.out(), prefix),
                    prefix);
        }
        assertTrue(families("base-fam").get("frames").getAsLong() > 0);
    }

    private static String lineStartingWith(final String text, final String prefix) {
        return text.lines()
                .filter(line -> line.startsWith(prefix))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no '" + prefix + "' line in " + text));
    }

    private JsonObject families(final String name) throws IOException {
        return readJson(work.resolve(name).resolve("run.json")).getAsJsonArray("datasets")
                .get(0)
                .getAsJsonObject()
                .getAsJsonObject("families");
    }

    @Test
    void familyRecipeWritesFamilyArtifactAndOperatorCanReplayIt() throws Exception {
        final Result run = runWithFamilies("fam", TWO_SCALES,
                "{\"profiles\":[{\"id\":\"zigzag\"},{\"id\":\"regular-flat\",\"minRetracement\":\"0.8\"},"
                        + "{\"id\":\"expanded-flat\"},{\"id\":\"contracting-triangle\"}],\"maxCompositions\":4}");
        assertEquals(0, run.code(), run.err());
        final Path out = work.resolve("fam");
        assertTrue(Files.isRegularFile(out.resolve("families/toy.jsonl")));
        assertTrue(Files.isRegularFile(out.resolve("relations/toy.jsonl")));

        final JsonObject recipeJson = readJson(out.resolve("run.json")).getAsJsonObject("configuration");
        final JsonObject declared = recipeJson.getAsJsonObject("families");
        assertEquals(4, declared.getAsJsonArray("profiles").size());
        assertEquals(4, declared.get("maxCompositions").getAsInt());
        assertEquals("0.8",
                declared.getAsJsonArray("profiles").get(1).getAsJsonObject().get("minRetracement").getAsString());
        final JsonObject families = readJson(out.resolve("run.json")).getAsJsonArray("datasets")
                .get(0)
                .getAsJsonObject()
                .getAsJsonObject("families");
        assertEquals("families/toy.jsonl", families.get("file").getAsString());
        assertTrue(families.get("frames").getAsLong() > 0, families.toString());
        final String summary = Files.readString(out.resolve("summary.md"));
        assertTrue(summary.contains("## Corrective families (experimental)"), summary);
        assertTrue(summary.contains("regular-flat/1{minRetracement=0.8"), summary);

        final Result print = launch("families", out.toString(), "toy", "--limit", "5");
        assertEquals(0, print.code(), print.err());
        assertTrue(print.out().contains("Families: "), print.out());
        assertTrue(print.out().contains("not a forecast"), print.out());
        assertTrue(print.out().contains("As of index"), print.out());

        final Result early = launch("families", out.toString(), "toy", "--as-of", "1");
        assertEquals(0, early.code(), early.err());
        assertTrue(early.out().contains("no verdict was observable yet"), early.out());
        assertEquals(1, launch("families", out.toString(), "nope").code());
    }

    @Test
    void enablingFamiliesLeavesEveryOtherArtifactByteIdentical() throws Exception {
        assertEquals(0, runWithFamilies("plain", TWO_SCALES, null).code());
        assertEquals(0, runWithFamilies("with", TWO_SCALES, "{\"profiles\":[{\"id\":\"zigzag\"}]}").code());
        final Path plain = work.resolve("plain");
        final Path with = work.resolve("with");

        assertFalse(Files.exists(plain.resolve("families")));
        assertFalse(readJson(plain.resolve("run.json")).getAsJsonArray("datasets")
                .get(0)
                .getAsJsonObject()
                .has("families"));
        assertFalse(Files.readString(plain.resolve("summary.md")).contains("Corrective families"));
        try (Stream<Path> paths = Files.walk(plain)) {
            for (final Path file : paths.filter(Files::isRegularFile).toList()) {
                final Path relative = plain.relativize(file);
                final String name = relative.toString().replace('\\', '/');
                if (name.equals("run.json") || name.equals("summary.md")) {
                    continue;
                }
                assertEquals(fingerprintless(Files.readString(file, StandardCharsets.UTF_8)),
                        fingerprintless(Files.readString(with.resolve(relative.toString()), StandardCharsets.UTF_8)),
                        name);
            }
        }
        final Result none = launch("families", plain.toString(), "toy");
        assertEquals(2, none.code());
        assertTrue(none.err().contains("the recipe declares no families"), none.err());
    }

    /**
     * The recipe fingerprint legitimately covers the families block; everything
     * else must match byte for byte.
     */
    private static String fingerprintless(final String text) {
        return text.replaceAll("explore-[0-9a-f]+", "explore-FINGERPRINT");
    }

    @Test
    void familyRecipeIsRejectedBeforeCreatingArtifacts() throws Exception {
        final Map<String, String[]> cases = new java.util.LinkedHashMap<>();
        cases.put("families without hierarchy", new String[] { null, "{\"profiles\":[{\"id\":\"zigzag\"}]}",
                "recipe.families requires recipe.hierarchy" });
        cases.put("unknown profile",
                new String[] { TWO_SCALES, "{\"profiles\":[{\"id\":\"diagonal\"}]}", "recipe.families.profiles[0]" });
        cases.put("bands on a bandless profile", new String[] { TWO_SCALES,
                "{\"profiles\":[{\"id\":\"zigzag\",\"minRetracement\":0.5}]}", "has no tolerance bands" });
        cases.put("duplicate profile", new String[] { TWO_SCALES,
                "{\"profiles\":[{\"id\":\"zigzag\"},{\"id\":\"zigzag\"}]}", "recipe.families.profiles" });
        cases.put("non-positive composition bound", new String[] { TWO_SCALES,
                "{\"profiles\":[{\"id\":\"zigzag\"}],\"maxCompositions\":0}", "maxCompositions must be positive" });
        cases.put("unknown field", new String[] { TWO_SCALES, "{\"profiles\":[{\"id\":\"zigzag\"}],\"extra\":1}",
                "unknown field recipe.families.extra" });
        cases.put("missing profiles", new String[] { TWO_SCALES, "{}", "recipe.families" });
        int index = 0;
        for (final Map.Entry<String, String[]> entry : cases.entrySet()) {
            final String name = "fam-bad-" + index++;
            final Result result = runWithFamilies(name, entry.getValue()[0], entry.getValue()[1]);
            assertEquals(1, result.code(), entry.getKey());
            assertTrue(result.err().contains(entry.getValue()[2]), entry.getKey() + ": " + result.err());
            assertFalse(Files.exists(work.resolve(name)), entry.getKey());
        }
    }

    @Test
    void familiesRefuseSkippedInteriorPivotsButHierarchyAloneStillAllowsThem() throws Exception {
        final String skipping = "{\"scales\":[{\"detector\":\"fractal-w5\"},{\"detector\":\"fractal-w3\"}],"
                + "\"interiorAnchors\":\"allow-skipped\"}";
        final Result rejected = runWithFamilies("fam-skip", skipping, "{\"profiles\":[{\"id\":\"zigzag\"}]}");
        assertEquals(1, rejected.code());
        assertTrue(rejected.err().contains("recipe.families requires recipe.hierarchy.interiorAnchors"),
                rejected.err());
        assertTrue(rejected.err().contains("contiguous"), rejected.err());
        assertFalse(Files.exists(work.resolve("fam-skip")));

        final Result relationsOnly = runWithFamilies("hier-skip", skipping, null);
        assertEquals(0, relationsOnly.code(), relationsOnly.err());
        assertTrue(Files.isRegularFile(work.resolve("hier-skip/relations/toy.jsonl")));
        assertFalse(Files.exists(work.resolve("hier-skip/families")));
    }

    @Test
    void incompleteOrForeignFamilyFileIsReportedNotTrusted() throws Exception {
        assertEquals(0, runWithFamilies("fam-damage", TWO_SCALES, "{\"profiles\":[{\"id\":\"zigzag\"}]}").code());
        final Path out = work.resolve("fam-damage");
        final Path file = out.resolve("families/toy.jsonl");
        final List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);

        Files.write(file, lines.subList(0, lines.size() - 1), StandardCharsets.UTF_8);
        final Result truncated = launch("families", out.toString(), "toy");
        assertEquals(2, truncated.code());
        assertTrue(truncated.out().contains("no footer"), truncated.out());
        assertEquals(0, launch("summarize", out.toString()).code());
        assertTrue(Files.readString(out.resolve("summary.md")).contains("family file is incomplete"));

        final List<String> foreign = new java.util.ArrayList<>(lines);
        final JsonObject header = JsonParser.parseString(foreign.get(0)).getAsJsonObject();
        header.addProperty("fingerprint", "someone-else");
        foreign.set(0, header.toString());
        Files.write(file, foreign, StandardCharsets.UTF_8);
        assertEquals(2, launch("families", out.toString(), "toy").code());
        assertEquals(0, launch("summarize", out.toString()).code());
        assertTrue(Files.readString(out.resolve("summary.md")).contains("does not belong to this run"));

        Files.writeString(file, "not json\n");
        assertEquals(2, launch("families", out.toString(), "toy").code());
    }

    private Path familyRunWithVisibleVerdicts(final String name) throws Exception {
        final Result run = runWithFamilies(name, TWO_SCALES,
                "{\"profiles\":[{\"id\":\"zigzag\"},{\"id\":\"regular-flat\",\"minRetracement\":\"0.8\"}]}");
        assertEquals(0, run.code(), run.err());
        final Path out = work.resolve(name);
        assertTrue(Files.readString(out.resolve("summary.md")).contains("regular-flat/1{minRetracement=0.8"));
        return out;
    }

    @Test
    void familyReferencesThatLeaveTheRunDirectoryAreRefusedNotRead() throws Exception {
        // Regression: the family file name recorded in run.json was resolved against
        // the run directory without containment, so a received run could make
        // `families` and `summarize` read whatever file the name pointed at.
        final Path out = familyRunWithVisibleVerdicts("fam-escape");
        final Path outside = work.resolve("outside-families.jsonl");
        Files.copy(out.resolve("families/toy.jsonl"), outside, StandardCopyOption.REPLACE_EXISTING);
        final List<String> references = List.of("../outside-families.jsonl", outside.toString());
        for (int index = 0; index < references.size(); index++) {
            final Path copy = work.resolve("escaping-families-" + index);
            copyTree(out, copy);
            final JsonObject run = readJson(copy.resolve("run.json"));
            run.getAsJsonArray("datasets")
                    .get(0)
                    .getAsJsonObject()
                    .getAsJsonObject("families")
                    .addProperty("file", references.get(index));
            Files.writeString(copy.resolve("run.json"), run.toString(), StandardCharsets.UTF_8);

            final Result print = launch("families", copy.toString(), "toy");
            assertEquals(2, print.code(), references.get(index));
            assertTrue(print.err().contains("lies outside the run directory"), print.err());
            assertEquals(0, launch("summarize", copy.toString()).code());
            final String summary = Files.readString(copy.resolve("summary.md"));
            assertTrue(summary.contains("family file unreadable"), summary);
            assertTrue(summary.contains("lies outside the run directory"), summary);
            assertFalse(summary.contains("regular-flat/1{minRetracement=0.8"), summary);
        }
    }

    @Test
    void familyFileLinkedFromOutsideTheRunDirectoryIsNotRead() throws Exception {
        final Path out = familyRunWithVisibleVerdicts("fam-linked");
        final Path file = out.resolve("families/toy.jsonl");
        final Path outside = work.resolve("linked-families.jsonl");
        Files.move(file, outside);
        Files.createSymbolicLink(file, outside);

        final Result print = launch("families", out.toString(), "toy");
        assertEquals(2, print.code());
        assertTrue(print.err().contains("lies outside the run directory"), print.err());
        assertEquals(0, launch("summarize", out.toString()).code());
        final String summary = Files.readString(out.resolve("summary.md"));
        assertTrue(summary.contains("lies outside the run directory"), summary);
        assertFalse(summary.contains("regular-flat/1{minRetracement=0.8"), summary);
    }

    private Result runRecipeWithRobustnessDetector(final String name, final String detector) throws Exception {
        final Path candles = work.resolve(name + "-candles.json");
        writeCandles(candles, LocalDate.of(2020, 1, 1), 366, date -> true);
        final Path recipe = work.resolve(name + "-recipe.json");
        Files.writeString(recipe, hierarchyRecipe(null)
                .replace("{\"name\":\"fractal-w5\",\"factory\":\"fractal\",\"params\":[5]}", detector));
        return launch("run", "explore", "--source", candles.toString(), "--recipe", recipe.toString(), "--out",
                work.resolve(name).toString());
    }

    @Test
    void sameDetectorNameWithAnotherDefinitionIsRejectedBeforeCreatingArtifacts() throws Exception {
        final Result result = runRecipeWithRobustnessDetector("clash",
                "{\"name\":\"fractal-w3\",\"factory\":\"fractal\",\"params\":[5]}");

        assertEquals(1, result.code());
        assertTrue(result.err().contains("already used by recipe.detector with a different definition"), result.err());
        assertFalse(Files.exists(work.resolve("clash")));
    }

    @Test
    void sameDetectorNameWithTheIdenticalDefinitionIsAccepted() throws Exception {
        final Result result = runRecipeWithRobustnessDetector("twin",
                "{\"name\":\"fractal-w3\",\"factory\":\"fractal\",\"params\":[3]}");

        assertEquals(0, result.code(), result.err());
    }

    @Test
    void incompleteOrForeignRelationFileIsReportedNotTrusted() throws Exception {
        final Path out = hierarchyRun("hier-damage",
                "{\"scales\":[{\"detector\":\"fractal-w5\"},{\"detector\":\"fractal-w3\"}]}");
        final Path file = out.resolve("relations/toy.jsonl");
        final List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);

        Files.write(file, lines.subList(0, lines.size() - 1), StandardCharsets.UTF_8);
        final Result truncated = launch("relations", out.toString(), "toy");
        assertEquals(2, truncated.code());
        assertTrue(truncated.out().contains("no footer"), truncated.out());
        assertEquals(0, launch("summarize", out.toString()).code());
        assertTrue(Files.readString(out.resolve("summary.md")).contains("relation file is incomplete"));

        final List<String> foreign = new java.util.ArrayList<>(lines);
        final JsonObject header = JsonParser.parseString(foreign.get(0)).getAsJsonObject();
        header.addProperty("fingerprint", "someone-else");
        foreign.set(0, header.toString());
        Files.write(file, foreign, StandardCharsets.UTF_8);
        final Result other = launch("relations", out.toString(), "toy");
        assertEquals(2, other.code());
        assertEquals(0, launch("summarize", out.toString()).code());
        assertTrue(Files.readString(out.resolve("summary.md")).contains("does not belong to this run"));

        Files.writeString(file, "not json\n");
        assertEquals(2, launch("relations", out.toString(), "toy").code());
    }

    @Test
    void relationReferencesThatLeaveTheRunDirectoryAreRefusedNotRead() throws Exception {
        // Regression: the relation file name recorded in run.json was resolved against
        // the run directory without containment, so a received run could make
        // `relations` and `summarize` read whatever file the name pointed at.
        final Path out = hierarchyRun("hier-escape",
                "{\"scales\":[{\"detector\":\"fractal-w5\"},{\"detector\":\"fractal-w3\"}]}");
        final Path outside = work.resolve("outside-relations.jsonl");
        Files.copy(out.resolve("relations/toy.jsonl"), outside, StandardCopyOption.REPLACE_EXISTING);
        final List<String> references = List.of("../outside-relations.jsonl", outside.toString());
        for (int index = 0; index < references.size(); index++) {
            final Path copy = work.resolve("escaping-relations-" + index);
            copyTree(out, copy);
            final JsonObject run = readJson(copy.resolve("run.json"));
            run.getAsJsonArray("datasets")
                    .get(0)
                    .getAsJsonObject()
                    .getAsJsonObject("relations")
                    .addProperty("file", references.get(index));
            Files.writeString(copy.resolve("run.json"), run.toString(), StandardCharsets.UTF_8);

            final Result print = launch("relations", copy.toString(), "toy");
            assertEquals(2, print.code(), references.get(index));
            assertTrue(print.err().contains("lies outside the run directory"), print.err());
            assertEquals(0, launch("summarize", copy.toString()).code());
            final String summary = Files.readString(copy.resolve("summary.md"));
            assertTrue(summary.contains("lies outside the run directory"), summary);
            assertFalse(summary.contains("fractal-w5 > fractal-w3"), summary);
        }
    }

    @Test
    void relationFileLinkedFromOutsideTheRunDirectoryIsNotRead() throws Exception {
        final Path out = hierarchyRun("hier-linked",
                "{\"scales\":[{\"detector\":\"fractal-w5\"},{\"detector\":\"fractal-w3\"}]}");
        final Path file = out.resolve("relations/toy.jsonl");
        final Path outside = work.resolve("linked-relations.jsonl");
        Files.move(file, outside);
        Files.createSymbolicLink(file, outside);

        final Result print = launch("relations", out.toString(), "toy");
        assertEquals(2, print.code());
        assertTrue(print.err().contains("lies outside the run directory"), print.err());
        assertEquals(0, launch("summarize", out.toString()).code());
        final String summary = Files.readString(out.resolve("summary.md"));
        assertTrue(summary.contains("lies outside the run directory"), summary);
        assertFalse(summary.contains("fractal-w5 > fractal-w3"), summary);
    }

    @Test
    public void inspectBindsRealActiveRulesToEveryComparedMode() throws Exception {
        assertModeRuleBinding(smokeRun, "smoke-real.jsonl", false);
    }

    @Test
    public void inspectBindsSelectedNullActiveRulesToTheComparisonMode() throws Exception {
        assertModeRuleBinding(nullMemberRun, "smoke-null-b20-m3.jsonl", true);
    }

    @Test
    public void completeTraceBytesAreBoundToMetadataBeforeAnyScopeIsEvidence() throws Exception {
        for (final boolean nullMember : List.of(false, true)) {
            for (final String damage : List.of("selected-observation", "other-observation", "labels",
                    "missing-digest")) {
                final Path copy = work.resolve("trace-digest-" + nullMember + "-" + damage);
                copyTree(nullMember ? nullMemberRun : smokeRun, copy);
                final String relative = nullMember ? "traces/smoke-null-b20-m3.jsonl" : "traces/smoke-real.jsonl";
                final Path trace = copy.resolve(relative);
                if ("missing-digest".equals(damage)) {
                    final JsonObject run = readJson(copy.resolve("run.json"));
                    run.getAsJsonArray("datasets").get(0).getAsJsonObject().remove("traceSha256");
                    Files.writeString(copy.resolve("run.json"), run.toString());
                } else {
                    final List<String> lines = new ArrayList<>(Files.readAllLines(trace, StandardCharsets.UTF_8));
                    final String grammar = "other-observation".equals(damage) ? "MOTIVE_5" : "CYCLE_5_3";
                    for (int i = 1; i < lines.size() - 1; i++) {
                        final JsonObject observation = JsonParser.parseString(lines.get(i)).getAsJsonObject();
                        if (!grammar.equals(observation.get("grammar").getAsString())
                                || !nullMember && !"topology-only".equals(observation.get("mode").getAsString())) {
                            continue;
                        }
                        if ("labels".equals(damage)) {
                            final JsonArray labels = observation.getAsJsonArray("labels");
                            if (labels.size() < 2) {
                                continue;
                            }
                            final JsonElement first = labels.get(0);
                            labels.set(0, labels.get(1));
                            labels.set(1, first);
                            lines.set(i, observation.toString());
                        } else {
                            lines.remove(i);
                            final JsonObject footer = JsonParser.parseString(lines.getLast()).getAsJsonObject();
                            footer.addProperty("records", footer.get("records").getAsLong() - 1);
                            lines.set(lines.size() - 1, footer.toString());
                        }
                        break;
                    }
                    Files.write(trace, lines, StandardCharsets.UTF_8);
                }
                final Result rejected = launch("inspect", copy.toString(),
                        "smoke|h2|topology-only|fractal-w5|calibration|completeOccupancyRate|b20");
                assertEquals(2, rejected.code(), rejected.out() + rejected.err());
                assertTrue(rejected.err().contains("SHA-256") && rejected.err().contains("Recapture"), rejected.err());
                final Result summary = launch("summarize", copy.toString());
                assertEquals(0, summary.code(), summary.err());
                assertFalse(Files.readString(copy.resolve("summary.md")).contains("trace captured"));
            }
        }
    }

    @Test
    public void inspectReconstructsLabelStabilityFromAdjacentRecordedLabelSets() {
        for (final String sectionMode : List.of("h1|topology-only", "h2|topology-only", "h2|all-rules")) {
            final Result result = launch("inspect", smokeRun.toString(),
                    "smoke|" + sectionMode + "|fractal-w5|calibration|labelStabilityJaccard|b20");
            assertEquals(0, result.code(), result.err());
            assertTrue(result.out().contains("adjacent label-set Jaccard mean="), result.out());
            assertTrue(result.out().contains("from 365 transitions") && result.out().contains("-> match"),
                    result.out());
            assertFalse(result.out().contains("not attributable"), result.out());
        }
    }

    @Test
    public void inspectReconstructsConfirmationLagFromRecordedCandidatePivots() throws Exception {
        for (final String sectionMode : List.of("h1|topology-only", "h2|topology-only", "h2|all-rules")) {
            final Result result = launch("inspect", smokeRun.toString(),
                    "smoke|" + sectionMode + "|fractal-w5|calibration|confirmationLagBars|b20");
            assertEquals(0, result.code(), result.err());
            assertTrue(result.out().contains("mean candidate-pivot confirmation lag=5 bars"), result.out());
            assertTrue(result.out().contains("complete candidate observations vs row 5 -> match"), result.out());
            assertTrue(result.out().contains("Supporting as-of indices ("), result.out());
            assertFalse(result.out().contains("not attributable"), result.out());
        }
        final Result member = launch("inspect", nullMemberRun.toString(),
                "smoke|h2|topology-only|fractal-w5|calibration|confirmationLagBars|b20");
        assertEquals(0, member.code(), member.err());
        assertTrue(
                member.out().contains("Null member confirmationLagBars: mean candidate-pivot confirmation lag=5 bars"),
                member.out());
        assertTrue(member.out().contains("Null member supporting as-of indices ("), member.out());

        final Path copy = work.resolve("confirmation-lag-changed-pivot");
        copyTree(smokeRun, copy);
        final String relative = "traces/smoke-real.jsonl";
        final Path trace = copy.resolve(relative);
        final List<String> lines = new ArrayList<>(Files.readAllLines(trace, StandardCharsets.UTF_8));
        boolean changed = false;
        for (int i = 1; i < lines.size() - 1; i++) {
            final JsonObject record = JsonParser.parseString(lines.get(i)).getAsJsonObject();
            if (!"h2".equals(record.get("section").getAsString())
                    || !"topology-only".equals(record.get("mode").getAsString())
                    || !"calibration".equals(record.get("partition").getAsString())
                    || !"COMPLETE".equals(record.get("status").getAsString())) {
                continue;
            }
            final int index = record.getAsJsonArray("candidates")
                    .get(0)
                    .getAsJsonObject()
                    .getAsJsonArray("placement")
                    .get(0)
                    .getAsJsonObject()
                    .get("index")
                    .getAsInt();
            for (final JsonElement element : record.getAsJsonArray("pivots")) {
                final JsonObject pivot = element.getAsJsonObject();
                if (pivot.get("index").getAsInt() == index) {
                    pivot.addProperty("confirmationIndex", pivot.get("confirmationIndex").getAsInt() + 1);
                    changed = true;
                    break;
                }
            }
            lines.set(i, record.toString());
            break;
        }
        assertTrue(changed, "the fixture must change a completed candidate's confirmation lag");
        Files.write(trace, lines, StandardCharsets.UTF_8);
        final JsonObject run = readJson(copy.resolve("run.json"));
        run.getAsJsonArray("datasets")
                .get(0)
                .getAsJsonObject()
                .getAsJsonObject("traceSha256")
                .addProperty(relative, HexFormat.of()
                        .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(trace))));
        Files.writeString(copy.resolve("run.json"), run.toString());
        final Result mismatch = launch("inspect", copy.toString(),
                "smoke|h2|topology-only|fractal-w5|calibration|confirmationLagBars|b20");
        assertEquals(2, mismatch.code(), mismatch.out() + mismatch.err());
        assertTrue(mismatch.err().contains("candidate-pivot confirmation lags do not match")
                && mismatch.err().contains("Recapture with:"), mismatch.err());
    }

    @Test
    public void inspectChecksEveryRatioValueAndCountIncludingUnavailableRows() throws Exception {
        final List<ElliottResearchReport.Row> rows = ElliottResearchReport.readCsv(smokeRun.resolve("comparisons.csv"));
        for (final String scopeMetric : List.of("calibration|completeOccupancyRate", "calibration|ambiguousRate",
                "calibration|noMatchRate", "calibration|evidencePassRate", "calibration|jointPassRate",
                "validation|evidencePassRate", "validation|jointPassRate")) {
            final String key = "smoke|h2|all-rules|fractal-w5|" + scopeMetric + "|b20";
            final Result valid = launch("inspect", smokeRun.toString(), key);
            assertEquals(0, valid.code(), valid.err());
            assertTrue(valid.out().contains(scopeMetric.startsWith("validation|") ? "-> unavailable" : "-> match"),
                    valid.out());
            final Path copy = work.resolve("ratio-" + scopeMetric.replace('|', '-'));
            copyTree(smokeRun, copy);
            for (final String changed : List.of("observed-and-support", "proportional-support",
                    "denominator-and-observed")) {
                final List<ElliottResearchReport.Row> mutated = new ArrayList<>(rows.size());
                for (final ElliottResearchReport.Row row : rows) {
                    if (!key.equals(row.key())) {
                        mutated.add(row);
                        continue;
                    }
                    final long numerator;
                    final long denominator;
                    if (row.denominator() == 0) {
                        numerator = "observed-and-support".equals(changed) ? 1 : 0;
                        denominator = "proportional-support".equals(changed) ? 2 : 1;
                    } else if ("observed-and-support".equals(changed)) {
                        numerator = row.numerator().equals(row.denominator()) ? row.numerator() - 1
                                : row.numerator() + 1;
                        denominator = row.denominator();
                    } else if ("proportional-support".equals(changed)) {
                        numerator = row.numerator() * 2;
                        denominator = row.denominator() * 2;
                    } else {
                        numerator = row.numerator();
                        denominator = row.denominator() + 1;
                    }
                    final double observed = numerator / (double) denominator;
                    final boolean replacedNullReference = row.validNullMembers() > 0;
                    mutated.add(new ElliottResearchReport.Row(row.dataset(), row.asset(), row.section(), row.mode(),
                            row.grammar(), row.activeRules(), row.detector(), row.policy(), row.partition(),
                            row.metric(), row.nullBlockLength(), observed, numerator, denominator,
                            row.validNullMembers(), row.requestedNullMembers(), row.unavailableNullMembers(),
                            replacedNullReference ? observed : row.nullMedian(),
                            replacedNullReference ? observed : row.nullLow(),
                            replacedNullReference ? observed : row.nullHigh(),
                            replacedNullReference ? 0.0d : row.observedMinusNullMedian(),
                            replacedNullReference ? 1.0d : row.empiricalReferenceRank(),
                            replacedNullReference ? ElliottResearchReport.AVAILABLE : row.availability()));
                }
                final Path csv = copy.resolve("comparisons.csv");
                ElliottResearchReport.writeCsv(csv, mutated);
                final JsonObject run = readJson(copy.resolve("run.json"));
                run.addProperty("comparisonsSha256",
                        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(csv))));
                Files.writeString(copy.resolve("run.json"), run.toString());
                final Result rejected = launch("inspect", copy.toString(), key);
                assertEquals(2, rejected.code(), changed + ": " + rejected.out() + rejected.err());
                assertTrue(rejected.err().contains("contributions do not match")
                        && rejected.err().contains("Recapture with:"), rejected.err());
            }
        }
    }

    @Test
    public void inspectRefusesEveryRatioWhenRecordedCompleteObservationIsMissing() throws Exception {
        final Path copy = work.resolve("ratios-missing-complete-observation");
        copyTree(smokeRun, copy);
        final String relative = "traces/smoke-real.jsonl";
        final Path trace = copy.resolve(relative);
        final List<String> lines = new ArrayList<>(Files.readAllLines(trace, StandardCharsets.UTF_8));
        boolean removed = false;
        for (int i = 1; i < lines.size() - 1; i++) {
            final JsonObject record = JsonParser.parseString(lines.get(i)).getAsJsonObject();
            if ("h2".equals(record.get("section").getAsString()) && "all-rules".equals(record.get("mode").getAsString())
                    && "calibration".equals(record.get("partition").getAsString())
                    && "COMPLETE".equals(record.get("status").getAsString())) {
                lines.remove(i);
                removed = true;
                break;
            }
        }
        assertTrue(removed, "the fixture must omit a completed observation");
        final JsonObject footer = JsonParser.parseString(lines.getLast()).getAsJsonObject();
        footer.addProperty("records", footer.get("records").getAsLong() - 1);
        lines.set(lines.size() - 1, footer.toString());
        Files.write(trace, lines, StandardCharsets.UTF_8);
        final JsonObject run = readJson(copy.resolve("run.json"));
        run.getAsJsonArray("datasets")
                .get(0)
                .getAsJsonObject()
                .getAsJsonObject("traceSha256")
                .addProperty(relative, HexFormat.of()
                        .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(trace))));
        Files.writeString(copy.resolve("run.json"), run.toString());
        for (final String metric : List.of("completeOccupancyRate", "ambiguousRate", "noMatchRate", "evidencePassRate",
                "jointPassRate")) {
            final Result rejected = launch("inspect", copy.toString(),
                    "smoke|h2|all-rules|fractal-w5|calibration|" + metric + "|b20");
            assertEquals(2, rejected.code(), rejected.out() + rejected.err());
            assertTrue(rejected.err().contains("trace " + metric + " contributions do not match")
                    && rejected.err().contains("Recapture with:"), rejected.err());
        }
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

    @Test
    public void inspectIdentifiesUnavailableConfirmationLagWithoutClaimingANumericMatch() {
        for (final String key : List.of("smoke|competing|competing-3+3|fractal-w5|calibration|confirmationLagBars|b-",
                "smoke|h2|all-rules|fractal-w5|validation|confirmationLagBars|b20")) {
            final Result result = launch("inspect", smokeRun.toString(), key);
            assertEquals(0, result.code(), result.err());
            assertTrue(result.out().contains("unavailable (no complete topology candidates)"), result.out());
            assertFalse(result.out().contains("-> match"), result.out());
        }
    }

    private void assertModeRuleBinding(final Path source, final String traceName, final boolean nullMember)
            throws Exception {
        for (final boolean reorder : List.of(false, true)) {
            final Path copy = work.resolve("mode-bound-" + nullMember + "-" + reorder);
            copyTree(source, copy);
            final Path trace = copy.resolve("traces").resolve(traceName);
            final List<String> lines = new ArrayList<>(Files.readAllLines(trace, StandardCharsets.UTF_8));
            int recordLine = -1;
            JsonObject record = null;
            for (int i = 1; i < lines.size() - 1; i++) {
                final JsonObject observation = JsonParser.parseString(lines.get(i)).getAsJsonObject();
                if ((nullMember || "h2".equals(observation.get("section").getAsString()))
                        && observation.getAsJsonArray("activeRules").size() > 1
                        && !observation.getAsJsonArray("candidates").isEmpty()) {
                    recordLine = i;
                    record = observation;
                    break;
                }
            }
            assertTrue(recordLine > 0, "the captured trace must contain all-rules evidence");
            final JsonArray original = record.getAsJsonArray("activeRules");
            final JsonArray activeRules = new JsonArray();
            for (int i = 0; i < original.size(); i++) {
                activeRules.add(reorder ? original.get(original.size() - 1 - i).getAsString()
                        : "foreign-" + original.get(i).getAsString());
            }
            record.add("activeRules", activeRules);
            for (final JsonElement element : record.getAsJsonArray("candidates")) {
                final JsonObject candidate = element.getAsJsonObject();
                final JsonArray originalRules = candidate.getAsJsonArray("rules");
                final JsonArray rules = new JsonArray();
                for (int i = 0; i < originalRules.size(); i++) {
                    final JsonObject rule = originalRules.get(reorder ? originalRules.size() - 1 - i : i)
                            .getAsJsonObject();
                    rule.addProperty("id", activeRules.get(i).getAsString());
                    rules.add(rule);
                }
                candidate.add("rules", rules);
                final byte[] digest = MessageDigest.getInstance("SHA-256")
                        .digest(rules.toString().getBytes(StandardCharsets.UTF_8));
                candidate.addProperty("version",
                        candidate.get("candidateKey").getAsString() + "@" + HexFormat.of().formatHex(digest, 0, 16));
            }
            lines.set(recordLine, record.toString());
            Files.write(trace, lines, StandardCharsets.UTF_8);
            final JsonObject observation = record;
            final List<ElliottResearchReport.Row> family = ElliottResearchReport
                    .readCsv(copy.resolve("comparisons.csv"))
                    .stream()
                    .filter(row -> "h2".equals(row.section())
                            && row.grammar().equals(observation.get("grammar").getAsString())
                            && row.partition().equals(observation.get("partition").getAsString()))
                    .toList();
            final ElliottResearchReport.Row allRules = family.stream()
                    .filter(row -> row.mode().equals(observation.get("mode").getAsString()))
                    .findFirst()
                    .orElseThrow();
            final List<String> keys = new ArrayList<>(List.of(allRules.key()));
            if (!nullMember) {
                keys.add(family.stream().filter(row -> row.activeRules().size() == 1).findFirst().orElseThrow().key());
            }
            for (final String key : keys) {
                final Result rejected = launch("inspect", copy.toString(), key);
                assertEquals(2, rejected.code(), rejected.out() + rejected.err());
                assertTrue(rejected.err().contains("activeRules"), rejected.err());
                assertTrue(rejected.err().contains("comparisons.csv mode"), rejected.err());
                assertTrue(rejected.err().contains("line " + (recordLine + 1)), rejected.err());
                assertTrue(rejected.err().contains("Recapture with:"), rejected.err());
            }
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

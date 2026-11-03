/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.analysis.elliott.ElliottResearchFixtures.OCCUPANCY_KEY;
import static org.ta4j.core.analysis.elliott.ElliottResearchFixtures.assertIncomplete;
import static org.ta4j.core.analysis.elliott.ElliottResearchFixtures.copyTree;
import static org.ta4j.core.analysis.elliott.ElliottResearchFixtures.csvRows;
import static org.ta4j.core.analysis.elliott.ElliottResearchFixtures.launch;
import static org.ta4j.core.analysis.elliott.ElliottResearchFixtures.readJson;
import static org.ta4j.core.analysis.elliott.ElliottResearchFixtures.replayLine;
import static org.ta4j.core.analysis.elliott.ElliottResearchFixtures.summarizeAfterEditing;
import static org.ta4j.core.analysis.elliott.ElliottResearchFixtures.symlinkOrSkip;
import static org.ta4j.core.analysis.elliott.ElliottResearchFixtures.toyRecipe;
import static org.ta4j.core.analysis.elliott.ElliottResearchFixtures.writeCandles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.analysis.elliott.ElliottResearchFixtures.Result;
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
 * Behavioural tests for the Elliott research launcher: the smoke run, its
 * artifacts, the summary, the inventory binding and the guards on published
 * files.
 */
class ElliottResearchTest {

    @TempDir
    static Path shared;

    @TempDir
    Path work;

    private static Path smokeRun;

    @BeforeAll
    static void runFixtures() throws IOException {
        smokeRun = shared.resolve("smoke");
        final Result result = launch("run", "smoke", "--out", smokeRun.toString());
        assertEquals(0, result.code(), result.err());
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
        // Replay: the sidecar bars are hashed and complete, and inspect names the
        // viewer command with this key.
        final JsonObject priceBars = dataset.getAsJsonObject("priceBars");
        assertEquals("bars/smoke.csv", priceBars.get("path").getAsString());
        final byte[] barBytes = Files.readAllBytes(smokeRun.resolve("bars/smoke.csv"));
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(barBytes)),
                priceBars.get("sha256").getAsString());
        assertEquals(dataset.get("bars").getAsInt() + 1, Files.readAllLines(smokeRun.resolve("bars/smoke.csv")).size());
        assertTrue(inspect.out().contains("Replay: mvn -q -pl ta4j-examples exec:java"), inspect.out());
        assertTrue(inspect.out().contains("ElliottReplayInspector"), inspect.out());
        assertFalse(replayLine(inspect.out()).contains("--trace"), inspect.out());
        assertTrue(inspect.out().contains(OCCUPANCY_KEY), inspect.out());

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
        assertEquals(csvRows(smokeRun, "outcomes.csv"), records.get("outcomes").getAsLong());
        assertEquals(csvRows(smokeRun, "outcomes-summary.csv"), records.get("summary").getAsLong());
    }

    @Test
    void summarizeRejectsAnEventsFileThatHoldsOnlyItsHeader() throws Exception {
        final Result result = summarizeAfterEditing(work, smokeRun, "events-header-only", "events.jsonl",
                text -> text.substring(0, text.indexOf('\n') + 1));

        assertIncomplete(result, "events.jsonl has no completion footer");
    }

    @Test
    void summarizeRejectsAnEventsFileWithoutItsFooter() throws Exception {
        final Result result = summarizeAfterEditing(work, smokeRun, "events-no-footer", "events.jsonl",
                text -> text.substring(0, text.lastIndexOf('\n', text.length() - 2) + 1));

        assertIncomplete(result, "events.jsonl has no completion footer");
    }

    @Test
    void summarizeRejectsAnEventsFileThatLostARowBeforeItsFooter() throws Exception {
        final Result result = summarizeAfterEditing(work, smokeRun, "events-lost-row", "events.jsonl", text -> {
            final int first = text.indexOf('\n') + 1;
            return text.substring(0, first) + text.substring(text.indexOf('\n', first) + 1);
        });

        assertIncomplete(result, "its footer counts");
    }

    @Test
    void summarizeRejectsAnOutcomesFileCutInsideItsLastRow() throws Exception {
        final Result result = summarizeAfterEditing(work, smokeRun, "outcomes-cut", "outcomes.csv",
                text -> text.substring(0, text.lastIndexOf("\r\n", text.length() - 3) + 2 + 10));

        assertIncomplete(result, "cells, expected 22");
    }

    @Test
    void summarizeRejectsAnOutcomesFileThatLostItsLastRow() throws Exception {
        final Result result = summarizeAfterEditing(work, smokeRun, "outcomes-prefix", "outcomes.csv",
                text -> text.substring(0, text.lastIndexOf("\r\n", text.length() - 3) + 2));

        assertIncomplete(result, "outcomes.csv holds ");
        assertTrue(result.err().contains(" rows but the run recorded "), result.err());
    }

    @Test
    void summarizeRejectsAHeaderOnlyOutcomesSummary() throws Exception {
        final Result result = summarizeAfterEditing(work, smokeRun, "summary-header-only", "outcomes-summary.csv",
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
                ElliottResearchCalibration.SUMMARY_FILE, ElliottResearchCalibration.RELIABILITY_FILE,
                "bars/input.json")) {
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
}

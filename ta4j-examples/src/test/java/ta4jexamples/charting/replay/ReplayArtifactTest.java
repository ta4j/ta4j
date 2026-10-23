/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.charting.replay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

class ReplayArtifactTest {

    @TempDir
    Path temp;

    private String failureOf(final Path run, final String key) {
        final ReplayArtifactException e = assertThrows(ReplayArtifactException.class,
                () -> ReplaySession.open(ReplayArtifact.open(run), key, ReplayArtifact.TRACE_MODE_REAL, 120, 8));
        return e.getMessage();
    }

    private static void copy(final Path from, final Path to) throws IOException {
        try (Stream<Path> files = Files.walk(from)) {
            for (final Path file : (Iterable<Path>) files::iterator) {
                final Path target = to.resolve(from.relativize(file).toString());
                if (Files.isDirectory(file)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(file, target);
                }
            }
        }
    }

    @Test
    void relocatedBundleReplaysIdentically() throws IOException {
        final Path original = ReplayFixture.write(temp.resolve("original"));
        final Path moved = temp.resolve("elsewhere").resolve("moved");
        Files.createDirectories(moved);
        copy(original, moved);

        final String before = ReplaySession
                .open(ReplayArtifact.open(original), ReplayFixture.RULES_KEY, ReplayArtifact.TRACE_MODE_REAL, 120, 8)
                .seek(36)
                .digest();
        try (Stream<Path> files = Files.walk(original)) {
            files.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
        final String after = ReplaySession
                .open(ReplayArtifact.open(moved), ReplayFixture.RULES_KEY, ReplayArtifact.TRACE_MODE_REAL, 120, 8)
                .seek(36)
                .digest();

        assertEquals(before, after);
    }

    @Test
    void barsAreReadFromTheSidecarAndVerified() {
        final ReplayArtifact artifact = ReplayArtifact.open(ReplayFixture.write(temp.resolve("run")));

        final List<ReplayArtifact.PriceBar> bars = artifact.bars(artifact.dataset(ReplayFixture.DATASET));

        assertEquals(ReplayFixture.BARS, bars.size());
        assertEquals(Integer.toString(ReplayFixture.close(7)), bars.get(7).close());
        assertEquals(ReplayFixture.START.plusSeconds(86_400L * 8), bars.get(7).end());
    }

    @Test
    void sidecarEditedAfterTheRunIsRejected() throws IOException {
        final Path run = ReplayFixture.write(temp.resolve("run"));
        Files.writeString(run.resolve("bars/d1.csv"), Files.readString(run.resolve("bars/d1.csv")) + "\n",
                StandardCharsets.UTF_8);

        assertTrue(failureOf(run, ReplayFixture.RULES_KEY).contains("bundle was modified"));
    }

    @Test
    void missingSidecarFileIsRequiredNotSubstituted() {
        final Path run = ReplayFixture.write(temp.resolve("run"), options -> {
            options.omitBarsFile = true;
            return options;
        });

        final String message = failureOf(run, ReplayFixture.RULES_KEY);

        assertTrue(message.contains("bars/d1.csv"));
        assertTrue(message.contains("never substitutes live data"));
    }

    @Test
    void runWithoutPriceBarsExplainsHowToRegenerate() {
        final Path run = ReplayFixture.write(temp.resolve("run"), options -> {
            options.omitPriceBars = true;
            return options;
        });

        final String message = failureOf(run, ReplayFixture.RULES_KEY);

        assertTrue(message.contains("predates the price-bar sidecar"));
        assertTrue(message.contains("Regenerate"));
    }

    @Test
    void runWithoutTraceNamesTheMissingOption() {
        final Path run = ReplayFixture.write(temp.resolve("run"), options -> {
            options.omitTrace = true;
            return options;
        });

        assertTrue(failureOf(run, ReplayFixture.RULES_KEY).contains("retained no trace"));
    }

    @Test
    void unsupportedSchemasAreRejectedByName() {
        final Path runSchema = ReplayFixture.write(temp.resolve("run-schema"), options -> {
            options.runSchema = "elliott-research-run/9";
            return options;
        });
        final Path traceSchema = ReplayFixture.write(temp.resolve("trace-schema"), options -> {
            options.traceSchema = "elliott-research-trace/9";
            return options;
        });

        assertTrue(assertThrows(ReplayArtifactException.class, () -> ReplayArtifact.open(runSchema)).getMessage()
                .contains("unsupported run schema 'elliott-research-run/9'"));
        assertTrue(failureOf(traceSchema, ReplayFixture.RULES_KEY)
                .contains("unsupported trace schema 'elliott-research-trace/9'"));
    }

    @Test
    void incompleteRunIsRejected() {
        final Path run = ReplayFixture.write(temp.resolve("run"), options -> {
            options.status = "running";
            return options;
        });

        assertTrue(assertThrows(ReplayArtifactException.class, () -> ReplayArtifact.open(run)).getMessage()
                .contains("not 'complete'"));
    }

    @Test
    void truncatedTraceIsRejected() {
        final Path run = ReplayFixture.write(temp.resolve("run"), options -> {
            options.truncateTrace = true;
            return options;
        });

        assertTrue(failureOf(run, ReplayFixture.RULES_KEY).contains("truncated"));
    }

    @Test
    void footerRecordCountMustMatch() throws IOException {
        final Path run = ReplayFixture.write(temp.resolve("run"));
        final Path trace = run.resolve("traces/d1-real.jsonl");
        Files.writeString(trace, Files.readString(trace).replace("\"records\":300", "\"records\":299"),
                StandardCharsets.UTF_8);
        ReplayFixture.refreshTraceDigest(run, "traces/d1-real.jsonl");

        assertTrue(failureOf(run, ReplayFixture.RULES_KEY).contains("footer declares 299"));
    }

    @Test
    void malformedFooterIsRejectedAsArtifactError() throws IOException {
        final String footer = "{\"complete\":true,\"records\":300}";
        for (final String replacement : List.of("{\"complete\":[],\"records\":300}", "{\"complete\":true}",
                "{\"complete\":true,\"records\":\"300\"}", "{\"complete\":true,\"records\":300.5}",
                "{\"complete\":true,\"records\":300e2147483648}")) {
            final Path run = ReplayFixture.write(temp.resolve("run-" + Math.abs(replacement.hashCode())));
            final Path trace = run.resolve("traces/d1-real.jsonl");
            final String original = Files.readString(trace);
            assertTrue(original.contains(footer), original);
            Files.writeString(trace, original.replace(footer, replacement), StandardCharsets.UTF_8);
            ReplayFixture.refreshTraceDigest(run, "traces/d1-real.jsonl");

            final String message = failureOf(run, ReplayFixture.RULES_KEY);
            assertTrue(message.contains("truncated") || message.contains("footer declares"),
                    replacement + ": " + message);
        }
    }

    @Test
    void traceOfAnotherRunIsRejected() throws IOException {
        final Path run = ReplayFixture.write(temp.resolve("run"));
        final Path trace = run.resolve("traces/d1-real.jsonl");
        Files.writeString(trace, Files.readString(trace).replace(ReplayFixture.FINGERPRINT, "other"),
                StandardCharsets.UTF_8);
        ReplayFixture.refreshTraceDigest(run, "traces/d1-real.jsonl");

        assertTrue(failureOf(run, ReplayFixture.RULES_KEY).contains("does not belong to this run"));
    }

    @Test
    void linksLeavingTheBundleAreRejected() throws IOException {
        final Path run = ReplayFixture.write(temp.resolve("run"));
        final JsonObject json = JsonParser.parseString(Files.readString(run.resolve("run.json"))).getAsJsonObject();
        json.getAsJsonArray("datasets")
                .get(0)
                .getAsJsonObject()
                .getAsJsonObject("priceBars")
                .addProperty("path", "../outside.csv");
        Files.writeString(run.resolve("run.json"), json.toString(), StandardCharsets.UTF_8);

        assertTrue(failureOf(run, ReplayFixture.RULES_KEY).contains("outside the run directory"));
    }

    @Test
    void symbolicLinksLeavingTheBundleAreRejected() throws IOException {
        final Path run = ReplayFixture.write(temp.resolve("run"));
        final Path outside = temp.resolve("outside.jsonl");
        final Path trace = run.resolve("traces/d1-real.jsonl");
        Files.move(trace, outside);
        Files.createSymbolicLink(trace, outside);

        final String failure = failureOf(run, ReplayFixture.RULES_KEY);
        assertTrue(failure.contains("outside the run directory"), failure);
        assertTrue(failure.contains("links to"), failure);
    }

    @Test
    void unknownKeySuggestsRecordedKeys() {
        final Path run = ReplayFixture.write(temp.resolve("run"));

        final String message = failureOf(run, "d1|h2|all-rules|fractal-w5|calibration|nope|b20");

        assertTrue(message.contains(ReplayFixture.RULES_KEY));
    }

    @Test
    void missingRunFileNamesTheDirectory() {
        final Path empty = temp.resolve("empty");

        assertTrue(assertThrows(ReplayArtifactException.class, () -> ReplayArtifact.open(empty)).getMessage()
                .contains("no run.json in"));
    }

    @Test
    void nonPositiveWindowAndCapAreRejected() {
        final ReplayArtifact artifact = ReplayArtifact.open(ReplayFixture.write(temp.resolve("run")));

        assertThrows(ReplayArtifactException.class,
                () -> ReplaySession.open(artifact, ReplayFixture.RULES_KEY, ReplayArtifact.TRACE_MODE_REAL, 0, 8));
        assertThrows(ReplayArtifactException.class,
                () -> ReplaySession.open(artifact, ReplayFixture.RULES_KEY, ReplayArtifact.TRACE_MODE_REAL, 8, 0));
    }

    @Test
    void requestedTraceModeMustBeRetained() {
        final ReplayArtifact artifact = ReplayArtifact.open(ReplayFixture.write(temp.resolve("run")));

        final ReplayArtifactException e = assertThrows(ReplayArtifactException.class, () -> ReplaySession.open(artifact,
                ReplayFixture.RULES_KEY, ReplayArtifact.TRACE_MODE_NULL_MEMBER, 120, 8));

        assertTrue(e.getMessage().contains("has no 'selected-null-member' trace"));
    }

    @Test
    void csvParserHandlesQuotesAndEmbeddedNewlines() {
        final List<List<String>> rows = ReplayArtifact.parseCsv("a,b\r\n\"x,1\",\"he said \"\"hi\"\"\nthere\"\n,\n");

        assertEquals(List.of(List.of("a", "b"), List.of("x,1", "he said \"hi\"\nthere"), List.of("", "")), rows);
    }

    @Test
    public void nullBlockLengthMayBeEmptyForConfiguredCompetingRows() {
        final Path run = ReplayFixture.write(temp.resolve("run"));

        assertEquals(0, ReplayArtifact.open(run).comparison(ReplayFixture.COMPETING_KEY).nullBlockLength());
    }

    @Test
    public void comparisonRowsRequireTheRecordedContentDigest() throws IOException {
        for (final String corruption : List.of("foreign-comparisons", "missing-digest")) {
            final Path run = ReplayFixture.write(temp.resolve(corruption));
            if ("foreign-comparisons".equals(corruption)) {
                final Path csv = run.resolve("comparisons.csv");
                Files.writeString(csv, Files.readString(csv).replace(",0.16,", ",0.6,"), StandardCharsets.UTF_8);
            } else {
                final Path metadata = run.resolve("run.json");
                final JsonObject json = JsonParser.parseString(Files.readString(metadata)).getAsJsonObject();
                json.remove("comparisonsSha256");
                Files.writeString(metadata, json.toString(), StandardCharsets.UTF_8);
            }
            final String message = assertThrows(ReplayArtifactException.class, () -> ReplayArtifact.open(run))
                    .getMessage();
            assertTrue(message.contains("comparisons.csv"), message);
            assertTrue(message.contains("SHA-256"), message);
        }
    }

    @Test
    public void comparisonRowsRejectAdjacentAndNonadjacentDuplicateKeys() throws IOException {
        for (final boolean adjacent : List.of(true, false)) {
            final Path run = ReplayFixture.write(temp.resolve("duplicate-" + adjacent));
            final Path csv = run.resolve("comparisons.csv");
            final List<String> rows = new ArrayList<>(Files.readAllLines(csv, StandardCharsets.UTF_8));
            final int duplicateIndex = adjacent ? 2 : rows.size();
            rows.add(duplicateIndex, rows.get(1));
            Files.write(csv, rows, StandardCharsets.UTF_8);
            ReplayFixture.refreshComparisonsDigest(run);

            final String message = assertThrows(ReplayArtifactException.class, () -> ReplayArtifact.open(run))
                    .getMessage();
            assertTrue(message.startsWith(csv + " row " + (duplicateIndex + 1) + " has duplicate comparison key '"
                    + ReplayFixture.RULES_KEY + "'."), message);
            assertTrue(message.contains("Regenerate"), message);
        }
    }

    @Test
    public void comparisonRowsRejectConflictingObservedValuesInEitherOrder() throws IOException {
        for (final boolean conflictFirst : List.of(false, true)) {
            final Path run = ReplayFixture.write(temp.resolve("observed-" + conflictFirst));
            final Path csv = run.resolve("comparisons.csv");
            final List<String> rows = new ArrayList<>(Files.readAllLines(csv, StandardCharsets.UTF_8));
            final List<String> columns = List.of(rows.getFirst().split(",", -1));
            final String[] cells = rows.get(1).split(",", -1);
            cells[columns.indexOf("observed")] = "0.75";
            cells[columns.indexOf("numerator")] = "3";
            cells[columns.indexOf("denominator")] = "4";
            for (final String field : List.of("nullMedian", "nullLow", "nullHigh")) {
                cells[columns.indexOf(field)] = "0.75";
            }
            cells[columns.indexOf("observedMinusNullMedian")] = "0";
            cells[columns.indexOf("empiricalReferenceRank")] = "1";
            final String conflict = String.join(",", cells);
            assertTrue(!conflict.equals(rows.get(1)), conflict);
            final int duplicateLine = conflictFirst ? 3 : rows.size() + 1;
            rows.add(conflictFirst ? 1 : rows.size(), conflict);
            Files.write(csv, rows, StandardCharsets.UTF_8);
            ReplayFixture.refreshComparisonsDigest(run);

            final String message = assertThrows(ReplayArtifactException.class, () -> ReplayArtifact.open(run))
                    .getMessage();
            assertTrue(message.startsWith(
                    csv + " row " + duplicateLine + " has duplicate comparison key '" + ReplayFixture.RULES_KEY + "'."),
                    message);
        }
    }

    @Test
    public void comparisonRowsRejectConflictingRuleMetadataInEitherOrder() throws IOException {
        for (final boolean conflictFirst : List.of(false, true)) {
            final Path run = ReplayFixture.write(temp.resolve("rules-" + conflictFirst));
            final Path csv = run.resolve("comparisons.csv");
            final List<String> rows = new ArrayList<>(Files.readAllLines(csv, StandardCharsets.UTF_8));
            final String conflict = rows.get(1).replace("wave2-origin;wave3-not-shortest", "wave2-origin");
            assertTrue(!conflict.equals(rows.get(1)), conflict);
            final int duplicateLine = conflictFirst ? 3 : rows.size() + 1;
            rows.add(conflictFirst ? 1 : rows.size(), conflict);
            Files.write(csv, rows, StandardCharsets.UTF_8);
            ReplayFixture.refreshComparisonsDigest(run);

            final String message = assertThrows(ReplayArtifactException.class, () -> ReplayArtifact.open(run))
                    .getMessage();
            if (conflictFirst) {
                assertTrue(message.startsWith(csv + " row 2 family metadata activeRules"), message);
            } else {
                assertTrue(message.startsWith(csv + " row " + duplicateLine + " has duplicate comparison key '"
                        + ReplayFixture.RULES_KEY + "'."), message);
            }
        }
    }

    @Test
    public void comparisonRowsRejectKeyFieldMismatchBeforeDuplicateDetection() throws IOException {
        for (final boolean append : List.of(false, true)) {
            final Path run = ReplayFixture.write(temp.resolve("key-mismatch-" + append));
            final Path csv = run.resolve("comparisons.csv");
            final List<String> rows = new ArrayList<>(Files.readAllLines(csv, StandardCharsets.UTF_8));
            final String mismatchedKey = ReplayFixture.RULES_KEY.replace("|b20", "|b21");
            final String mismatchedRow = rows.get(1).replace(ReplayFixture.RULES_KEY, mismatchedKey);
            final int mismatchLine = append ? rows.size() + 1 : 2;
            if (append) {
                rows.add(mismatchedRow);
            } else {
                rows.set(1, mismatchedRow);
            }
            Files.write(csv, rows, StandardCharsets.UTF_8);
            ReplayFixture.refreshComparisonsDigest(run);

            final String message = assertThrows(ReplayArtifactException.class, () -> ReplayArtifact.open(run))
                    .getMessage();
            assertTrue(message.startsWith(
                    csv + " row " + mismatchLine + " key column '" + mismatchedKey + "' does not match row fields."),
                    message);
            assertTrue(message.contains("Regenerate"), message);
        }
    }

    @Test
    public void comparisonRowsWithDistinctCanonicalKeysRemainReadable() {
        final ReplayArtifact artifact = ReplayArtifact.open(ReplayFixture.write(temp.resolve("distinct-keys")));

        assertEquals(36, artifact.comparisons().size());
        assertEquals(ReplayFixture.RULES_KEY, artifact.comparison(ReplayFixture.RULES_KEY).key());
        assertEquals(ReplayFixture.TOPOLOGY_KEY, artifact.comparison(ReplayFixture.TOPOLOGY_KEY).key());
        assertEquals(ReplayFixture.COMPETING_KEY, artifact.comparison(ReplayFixture.COMPETING_KEY).key());
    }

    @Test
    void recordsThatReachPastTheRetainedBarsAreRejected() throws IOException {
        final Path run = ReplayFixture.write(temp.resolve("run"));
        final Path trace = run.resolve("traces/d1-real.jsonl");
        final String text = Files.readString(trace);
        Files.writeString(trace, text.replace("\"asOfIndex\":59", "\"asOfIndex\":590"), StandardCharsets.UTF_8);
        ReplayFixture.refreshTraceDigest(run, "traces/d1-real.jsonl");

        final ReplaySession session = ReplaySession.open(ReplayArtifact.open(run), ReplayFixture.RULES_KEY,
                ReplayArtifact.TRACE_MODE_REAL, 120, 8);

        assertTrue(assertThrows(ReplayArtifactException.class, () -> session.seek(590)).getMessage()
                .contains("retained price bars end at 59"));
    }

    @Test
    void traceOfTheSameRecipeOnAnotherSourceIsRejected() {
        final Path run = ReplayFixture.write(temp.resolve("run"), options -> {
            options.datasetSource = "aaaa";
            options.traceSource = "bbbb";
            return options;
        });

        final String failure = failureOf(run, ReplayFixture.RULES_KEY);

        assertTrue(failure.contains("does not belong to this run"), failure);
        assertTrue(failure.contains("sourceSha256"), failure);
        final Path matching = ReplayFixture.write(temp.resolve("matching"), options -> {
            options.datasetSource = "aaaa";
            options.traceSource = "aaaa";
            return options;
        });
        assertEquals(33,
                ReplaySession
                        .open(ReplayArtifact.open(matching), ReplayFixture.RULES_KEY, ReplayArtifact.TRACE_MODE_REAL,
                                120, 8)
                        .cursor());
    }

    @Test
    void traceOfAnotherRevisionOrBlockIsRejected() throws IOException {
        final Path revision = ReplayFixture.write(temp.resolve("revision"));
        final Path trace = revision.resolve("traces/d1-real.jsonl");
        Files.writeString(trace, Files.readString(trace).replace("fixture-revision", "other"), StandardCharsets.UTF_8);
        ReplayFixture.refreshTraceDigest(revision, "traces/d1-real.jsonl");
        assertTrue(failureOf(revision, ReplayFixture.RULES_KEY).contains("revision"));

        final Path block = ReplayFixture.write(temp.resolve("block"));
        final Path blockTrace = block.resolve("traces/d1-real.jsonl");
        Files.writeString(blockTrace,
                Files.readString(blockTrace).replaceFirst("\"nullBlockLength\":-1", "\"nullBlockLength\":20"),
                StandardCharsets.UTF_8);
        ReplayFixture.refreshTraceDigest(block, "traces/d1-real.jsonl");
        assertTrue(failureOf(block, ReplayFixture.RULES_KEY).contains("nullBlockLength"));
    }

    @Test
    void nullTraceOfAnotherMemberOfTheSameRunRecipeIsRejected() throws IOException {
        // Regression: only the row's block length was compared, so a null trace of the
        // same source, revision and configuration but another member passed run.json's
        // selected block/member check.
        final Path run = ReplayFixture.write(temp.resolve("run"), options -> {
            options.nullTrace = true;
            return options;
        });
        assertEquals(ReplayFixture.FIRST_AS_OF,
                ReplaySession
                        .open(ReplayArtifact.open(run), ReplayFixture.RULES_KEY, ReplayArtifact.TRACE_MODE_NULL_MEMBER,
                                120, 8)
                        .firstAsOf());
        final Path trace = run.resolve("traces/d1-null.jsonl");
        Files.writeString(trace, Files.readString(trace).replaceFirst("\"nullMemberIndex\":0", "\"nullMemberIndex\":1"),
                StandardCharsets.UTF_8);
        ReplayFixture.refreshTraceDigest(run, "traces/d1-null.jsonl");

        final ReplayArtifactException e = assertThrows(ReplayArtifactException.class,
                () -> ReplaySession.open(ReplayArtifact.open(run), ReplayFixture.RULES_KEY,
                        ReplayArtifact.TRACE_MODE_NULL_MEMBER, 120, 8));

        assertTrue(e.getMessage().contains("does not belong to this run"), e.getMessage());
        assertTrue(e.getMessage().contains("nullBlockLength/nullMemberIndex"), e.getMessage());
    }

    @Test
    void missingOrNonArrayRequiredTraceFieldsAreRejectedWithTheField() throws IOException {
        final Path missing = ReplayFixture.write(temp.resolve("missing"));
        final Path missingTrace = missing.resolve("traces/d1-real.jsonl");
        Files.writeString(missingTrace, Files.readString(missingTrace).replace("\"candidates\"", "\"candidatez\""),
                StandardCharsets.UTF_8);
        ReplayFixture.refreshTraceDigest(missing, "traces/d1-real.jsonl");
        final String missingFailure = failureOf(missing, ReplayFixture.RULES_KEY);
        assertTrue(missingFailure.contains("candidates is missing or not an array"), missingFailure);
        assertTrue(missingFailure.contains("line 2"), missingFailure);

        final Path nonArray = ReplayFixture.write(temp.resolve("nonarray"));
        final Path nonArrayTrace = nonArray.resolve("traces/d1-real.jsonl");
        Files.writeString(nonArrayTrace,
                Files.readString(nonArrayTrace).replace("\"candidates\":[]", "\"candidates\":{}"),
                StandardCharsets.UTF_8);
        ReplayFixture.refreshTraceDigest(nonArray, "traces/d1-real.jsonl");
        assertTrue(failureOf(nonArray, ReplayFixture.RULES_KEY).contains("candidates is missing or not an array"));

        final Path rules = ReplayFixture.write(temp.resolve("rules"));
        final Path rulesTrace = rules.resolve("traces/d1-real.jsonl");
        Files.writeString(rulesTrace, Files.readString(rulesTrace).replace("\"rules\":[", "\"rulez\":["),
                StandardCharsets.UTF_8);
        ReplayFixture.refreshTraceDigest(rules, "traces/d1-real.jsonl");
        assertTrue(failureOf(rules, ReplayFixture.RULES_KEY).contains("rules is missing or not an array"));
    }

    @Test
    public void blankRecordIdentityFieldsAreRejectedBeforeFamilyFiltering() throws IOException {
        int fixture = 0;
        for (final String kind : List.of("topology", "alternative")) {
            for (final String field : List.of("dataset", "section", "mode", "grammar", "detector", "partition",
                    "asOfTime", "status")) {
                for (final String blank : List.of("", " ", "\t\r\n")) {
                    final Path run = ReplayFixture.write(temp.resolve("blank-identity-" + fixture++));
                    final Path trace = run.resolve("traces/d1-real.jsonl");
                    final List<String> lines = Files.readAllLines(trace);
                    int line = 1;
                    JsonObject record = JsonParser.parseString(lines.get(line)).getAsJsonObject();
                    while (!kind.equals(record.get("kind").getAsString())) {
                        record = JsonParser.parseString(lines.get(++line)).getAsJsonObject();
                    }
                    record.addProperty(field, blank);
                    lines.set(line, record.toString());
                    Files.write(trace, lines, StandardCharsets.UTF_8);
                    ReplayFixture.refreshTraceDigest(run, "traces/d1-real.jsonl");

                    final String message = failureOf(run, ReplayFixture.RULES_KEY);
                    assertTrue(message.contains(field + " is blank") && message.contains("line " + (line + 1))
                            && message.contains("traces/d1-real.jsonl"), kind + "." + field + ": " + message);
                }
            }
        }
    }

    @Test
    public void unknownTraceStatusesAndRuleStatesAreRejectedAtTheirLine() throws IOException {
        int fixture = 0;
        for (final String scope : List.of("topology", "alternative", "rule")) {
            for (final String value : List.of("bogus", "", "pass", "PASS ")) {
                final Path run = ReplayFixture.write(temp.resolve("state-" + fixture++));
                final Path trace = run.resolve("traces/d1-real.jsonl");
                final List<String> lines = Files.readAllLines(trace);
                int changedLine = -1;
                for (int line = 1; line < lines.size() - 1; line++) {
                    final JsonObject record = JsonParser.parseString(lines.get(line)).getAsJsonObject();
                    final String field = "rule".equals(scope) ? "state" : "status";
                    final JsonObject owner;
                    if ("rule".equals(scope)) {
                        final var candidates = record.getAsJsonArray("candidates");
                        if (candidates.isEmpty()
                                || candidates.get(0).getAsJsonObject().getAsJsonArray("rules").isEmpty()) {
                            continue;
                        }
                        owner = candidates.get(0).getAsJsonObject().getAsJsonArray("rules").get(0).getAsJsonObject();
                    } else if (scope.equals(record.get("kind").getAsString())) {
                        owner = record;
                    } else {
                        continue;
                    }
                    owner.addProperty(field, value);
                    lines.set(line, record.toString());
                    changedLine = line;
                    break;
                }
                assertTrue(changedLine > 0, scope);
                Files.write(trace, lines, StandardCharsets.UTF_8);
                ReplayFixture.refreshTraceDigest(run, "traces/d1-real.jsonl");

                final String message = failureOf(run, ReplayFixture.RULES_KEY);
                assertTrue(message.contains("rule".equals(scope) ? ".state" : "status")
                        && message.contains("line " + (changedLine + 1)), scope + "=" + value + ": " + message);
            }
        }
    }

    @Test
    public void candidateIdentityMustMatchTheRecordedPlacementAndRuleEvidence() throws IOException {
        for (final String field : List.of("candidateKey", "version", "grammar", "direction", "placement.index",
                "placement.type", "placement.price", "placement.order", "rules.id", "rules.state", "rules.score",
                "rules.explanation", "rules.observations", "rules.order")) {
            final Path run = ReplayFixture.write(temp.resolve("candidate-identity-" + field));
            final Path trace = run.resolve("traces/d1-real.jsonl");
            final List<String> lines = Files.readAllLines(trace);
            int line = 1;
            JsonObject record = JsonParser.parseString(lines.get(line)).getAsJsonObject();
            while (record.getAsJsonArray("candidates").isEmpty()) {
                record = JsonParser.parseString(lines.get(++line)).getAsJsonObject();
            }
            final JsonObject candidate = record.getAsJsonArray("candidates").get(0).getAsJsonObject();
            final var placement = candidate.getAsJsonArray("placement");
            final JsonObject point = placement.get(0).getAsJsonObject();
            final var rules = candidate.getAsJsonArray("rules");
            final JsonObject rule = rules.get(0).getAsJsonObject();
            final JsonObject secondRule = rules.get(1).getAsJsonObject();
            switch (field) {
            case "candidateKey" -> candidate.addProperty("candidateKey", "0".repeat(32));
            case "version" -> candidate.addProperty("version", "0".repeat(65));
            case "grammar" -> record.addProperty("grammar", "CORRECTIVE_3");
            case "direction" -> candidate.addProperty("direction", "BEARISH");
            case "placement.index" -> point.addProperty("index", 6);
            case "placement.type" ->
                point.addProperty("type", "HIGH".equals(point.get("type").getAsString()) ? "LOW" : "HIGH");
            case "placement.price" -> point.addProperty("price", point.get("price").getAsString() + ".0");
            case "placement.order" -> {
                final var first = placement.get(0);
                placement.set(0, placement.get(1));
                placement.set(1, first);
            }
            case "rules.id" -> rule.addProperty("id", "wave4-overlap");
            case "rules.state" -> rule.addProperty("state", "FAIL");
            case "rules.score" -> rule.addProperty("score", 0.5);
            case "rules.explanation" -> rule.addProperty("explanation", "forged explanation");
            case "rules.observations" -> rule.getAsJsonArray("observations").add("forged observation");
            case "rules.order" -> {
                rules.set(0, secondRule);
                rules.set(1, rule);
            }
            default -> throw new AssertionError(field);
            }
            lines.set(line, record.toString());
            Files.write(trace, lines, StandardCharsets.UTF_8);
            ReplayFixture.refreshTraceDigest(run, "traces/d1-real.jsonl");

            final String expected = "version".equals(field) || field.startsWith("rules.") ? ".version"
                    : ".candidateKey";
            final String message = failureOf(run, ReplayFixture.RULES_KEY);
            assertTrue(message.contains("candidates[0]" + expected) && message.contains("line " + (line + 1))
                    && message.contains("traces/d1-real.jsonl"), field + ": " + message);
        }
    }

    @Test
    public void selfConsistentTraceRecordsMustSatisfyDomainInvariantsBeforeFamilyFiltering() throws IOException {
        for (final String mutation : List.of("complete-empty", "complete-multiple", "ambiguous-empty",
                "ambiguous-single", "candidate-bound", "rules-omitted", "rules-duplicated", "rules-renamed",
                "rules-reordered", "rules-extra", "forming-null-direction", "record-unknown-direction",
                "candidate-unknown-direction", "unknown-grammar", "placement-count", "placement-index-order",
                "placement-type-order", "placement-negative-index", "placement-invalid-type",
                "placement-nonfinite-price", "pivot-negative-index", "pivot-confirmation-before-index",
                "pivot-invalid-type", "pivot-nonfinite-price", "score-on-fail", "score-below-zero", "score-above-one",
                "score-extreme", "score-tiny-negative")) {
            final Path run = ReplayFixture.write(temp.resolve("domain-" + mutation));
            final Path trace = run.resolve("traces/d1-real.jsonl");
            final List<String> lines = Files.readAllLines(trace);
            final int line = firstRulesCandidateLine(lines);
            final JsonObject record = JsonParser.parseString(lines.get(line)).getAsJsonObject();
            final JsonArray candidates = record.getAsJsonArray("candidates");
            final JsonObject candidate = candidates.get(0).getAsJsonObject();
            final JsonArray rules = candidate.getAsJsonArray("rules");
            final JsonObject rule = rules.get(0).getAsJsonObject();
            final JsonArray placement = candidate.getAsJsonArray("placement");
            final JsonObject point = placement.get(0).getAsJsonObject();
            final JsonObject pivot = record.getAsJsonArray("pivots").get(0).getAsJsonObject();
            final String field;
            switch (mutation) {
            case "complete-empty", "ambiguous-empty" -> {
                record.addProperty("status", mutation.startsWith("ambiguous") ? "AMBIGUOUS" : "COMPLETE");
                record.add("candidates", new JsonArray());
                field = "candidates";
            }
            case "complete-multiple", "candidate-bound" -> {
                record.addProperty("status", "candidate-bound".equals(mutation) ? "AMBIGUOUS" : "COMPLETE");
                while (candidates.size() < ("candidate-bound".equals(mutation) ? 65 : 2)) {
                    candidates.add(candidate.deepCopy());
                }
                field = "candidates";
            }
            case "ambiguous-single" -> {
                record.addProperty("status", "AMBIGUOUS");
                field = "candidates";
            }
            case "rules-omitted", "rules-extra", "rules-duplicated", "rules-renamed", "rules-reordered" -> {
                switch (mutation) {
                case "rules-omitted" -> rules.remove(1);
                case "rules-extra" -> rules.add(rule.deepCopy());
                case "rules-duplicated" -> rules.set(1, rule.deepCopy());
                case "rules-renamed" -> rule.addProperty("id", "foreign-rule");
                case "rules-reordered" -> {
                    rules.set(0, rules.get(1));
                    rules.set(1, rule);
                }
                default -> throw new AssertionError(mutation);
                }
                field = "rules";
            }
            case "forming-null-direction", "record-unknown-direction" -> {
                if ("forming-null-direction".equals(mutation)) {
                    record.addProperty("status", "FORMING");
                    record.add("direction", JsonNull.INSTANCE);
                } else {
                    record.addProperty("direction", "FLAT");
                }
                field = "direction";
            }
            case "candidate-unknown-direction" -> {
                candidate.addProperty("direction", "FLAT");
                field = "direction";
            }
            case "unknown-grammar" -> {
                record.addProperty("grammar", "FOREIGN");
                field = "grammar";
            }
            case "placement-count", "placement-index-order", "placement-type-order", "placement-negative-index",
                    "placement-invalid-type", "placement-nonfinite-price" -> {
                switch (mutation) {
                case "placement-count" -> placement.remove(placement.size() - 1);
                case "placement-index-order" ->
                    point.addProperty("index", placement.get(1).getAsJsonObject().get("index").getAsInt());
                case "placement-type-order" ->
                    point.addProperty("type", placement.get(1).getAsJsonObject().get("type").getAsString());
                case "placement-negative-index" -> point.addProperty("index", -1);
                case "placement-invalid-type" -> point.addProperty("type", "CENTER");
                case "placement-nonfinite-price" -> point.addProperty("price", "NaN");
                default -> throw new AssertionError(mutation);
                }
                field = "placement";
            }
            case "pivot-negative-index", "pivot-confirmation-before-index", "pivot-invalid-type",
                    "pivot-nonfinite-price" -> {
                switch (mutation) {
                case "pivot-negative-index" -> pivot.addProperty("index", -1);
                case "pivot-confirmation-before-index" -> pivot.addProperty("confirmationIndex", -1);
                case "pivot-invalid-type" -> pivot.addProperty("type", "CENTER");
                case "pivot-nonfinite-price" -> pivot.addProperty("price", "Infinity");
                default -> throw new AssertionError(mutation);
                }
                field = "pivots[0]";
            }
            case "score-on-fail", "score-below-zero", "score-above-one", "score-extreme", "score-tiny-negative" -> {
                switch (mutation) {
                case "score-on-fail" -> rule.addProperty("state", "FAIL");
                case "score-below-zero" -> rule.addProperty("score", -0.01);
                case "score-above-one" -> rule.addProperty("score", 1.01);
                case "score-extreme" -> rule.add("score", JsonParser.parseString("1e1000"));
                case "score-tiny-negative" -> rule.add("score", JsonParser.parseString("-1e-1000"));
                default -> throw new AssertionError(mutation);
                }
                field = "rules[0].score";
            }
            default -> throw new AssertionError(mutation);
            }
            ReplayFixture.refreshIdentity(record);
            lines.set(line, record.toString());
            Files.write(trace, lines, StandardCharsets.UTF_8);
            ReplayFixture.refreshTraceDigest(run, "traces/d1-real.jsonl");
            for (final String key : List.of(ReplayFixture.RULES_KEY, ReplayFixture.TOPOLOGY_KEY)) {
                final String message = failureOf(run, key);
                assertTrue(
                        message.contains(field) && message.contains("line " + (line + 1))
                                && message.contains("traces/d1-real.jsonl") && message.contains("regenerate"),
                        mutation + ": " + message);
            }
        }
    }

    @Test
    public void supportedTopologyGrammarsDirectionsAndCandidateBoundsRemainReadable() throws IOException {
        for (final String grammar : List.of("MOTIVE_5", "CORRECTIVE_3", "CYCLE_5_3")) {
            for (final String direction : List.of("BULLISH", "BEARISH")) {
                for (final int count : List.of(1, 64)) {
                    final Path run = ReplayFixture.write(temp.resolve("boundary-" + grammar + "-" + direction + count));
                    final Path trace = run.resolve("traces/d1-real.jsonl");
                    final List<String> lines = Files.readAllLines(trace);
                    final int line = firstRulesCandidateLine(lines);
                    final JsonObject record = JsonParser.parseString(lines.get(line)).getAsJsonObject();
                    orientGrammarShape(record, grammar, direction);
                    record.add("direction", count == 1 ? new JsonPrimitive(direction) : JsonNull.INSTANCE);
                    record.addProperty("status", count == 1 ? "COMPLETE" : "AMBIGUOUS");
                    final JsonObject candidate = record.getAsJsonArray("candidates").get(0).getAsJsonObject();
                    final JsonArray rules = candidate.getAsJsonArray("rules");
                    rules.get(0).getAsJsonObject().addProperty("score", 0);
                    rules.get(1).getAsJsonObject().addProperty("state", "PASS");
                    rules.get(1).getAsJsonObject().addProperty("score", 1);
                    retainDistinctCandidates(record, count);
                    ReplayFixture.refreshIdentity(record);
                    final Path boundary = singleRecordTrace("boundary-" + grammar + "-" + direction + count,
                            lines.getFirst(), record);
                    for (final boolean keep : List.of(false, true)) {
                        assertNotNull(
                                ReplayTraceIndex.scan(boundary, "boundary", family -> keep, Map.of(), "ambiguousRate"));
                    }
                }
            }
        }
    }

    @Test
    public void everyTopologyStateEnforcesItsCandidateAndDirectionContractBeforeFiltering() throws IOException {
        final String[] directions = { null, "BULLISH", "BEARISH" };
        for (final String status : List.of("COMPLETE", "AMBIGUOUS", "FORMING", "NO_MATCH", "INVALIDATED",
                "INSUFFICIENT_HISTORY")) {
            for (final int count : List.of(0, 1, 2, 64, 65)) {
                for (final String direction : directions) {
                    final boolean validCount = switch (status) {
                    case "COMPLETE" -> count == 1;
                    case "AMBIGUOUS" -> count >= 2 && count <= 64;
                    default -> count == 0;
                    };
                    final boolean validDirection = switch (status) {
                    case "COMPLETE" -> "BULLISH".equals(direction);
                    case "FORMING" -> direction != null;
                    default -> direction == null;
                    };
                    final Path run = ReplayFixture
                            .write(temp.resolve("state-" + status + "-" + count + "-" + direction));
                    final Path trace = run.resolve("traces/d1-real.jsonl");
                    final List<String> lines = Files.readAllLines(trace);
                    final int line = firstRulesCandidateLine(lines);
                    final JsonObject record = JsonParser.parseString(lines.get(line)).getAsJsonObject();
                    record.addProperty("status", status);
                    record.addProperty("direction", direction);
                    if ("FORMING".equals(status)) {
                        record.addProperty("formingStartBarIndex", 0);
                        record.addProperty("formingEndBarIndex", 2);
                    }
                    if ("FORMING".equals(status) && "BEARISH".equals(direction)) {
                        orientGrammarShape(record, "MOTIVE_5", direction);
                    }
                    retainDistinctCandidates(record, Math.max(1, count));
                    if (count == 0) {
                        record.add("candidates", new JsonArray());
                    }
                    final JsonArray pivots = record.getAsJsonArray("pivots");
                    final int visibleCount = "INSUFFICIENT_HISTORY".equals(status) ? 1
                            : "FORMING".equals(status) ? 3 : pivots.size();
                    while (pivots.size() > visibleCount) {
                        pivots.remove(pivots.size() - 1);
                    }
                    ReplayFixture.refreshIdentity(record);
                    lines.set(line, record.toString());
                    Files.write(trace, lines, StandardCharsets.UTF_8);
                    ReplayFixture.refreshTraceDigest(run, "traces/d1-real.jsonl");
                    if (validCount && validDirection) {
                        final Path valid = singleRecordTrace("state-" + status + "-" + count + "-" + direction,
                                lines.getFirst(), record);
                        assertNotNull(ReplayTraceIndex.scan(valid, "state", family -> true, Map.of(), "ambiguousRate"));
                        assertNotNull(
                                ReplayTraceIndex.scan(valid, "state", family -> false, Map.of(), "ambiguousRate"));
                    } else {
                        final String field = "FORMING".equals(status) && direction == null || validCount ? "direction"
                                : "candidates";
                        for (final String key : List.of(ReplayFixture.RULES_KEY, ReplayFixture.TOPOLOGY_KEY)) {
                            final String message = failureOf(run, key);
                            assertTrue(
                                    message.contains(field) && message.contains("line " + (line + 1))
                                            && message.contains("regenerate"),
                                    status + "/" + count + "/" + direction + ": " + message);
                        }
                    }
                }
            }
        }
    }

    @Test
    public void placementsMustMatchTheVisiblePivotViewBeforeFamilyFiltering() throws IOException {
        for (final String mutation : List.of("missing-visible-pivot", "different-visible-index",
                "different-visible-price", "different-visible-type", "duplicate-visible-index",
                "unsorted-visible-indices", "price-beyond-default-precision")) {
            final Path run = ReplayFixture.write(temp.resolve("visible-placement-" + mutation));
            final Path trace = run.resolve("traces/d1-real.jsonl");
            final List<String> lines = Files.readAllLines(trace);
            final int line = firstRulesCandidateLine(lines);
            final JsonObject record = JsonParser.parseString(lines.get(line)).getAsJsonObject();
            final JsonArray pivots = record.getAsJsonArray("pivots");
            final JsonArray placement = record.getAsJsonArray("candidates")
                    .get(0)
                    .getAsJsonObject()
                    .getAsJsonArray("placement");
            final JsonObject placed = placement.get(0).getAsJsonObject();
            final JsonObject visible = pivots.get(0).getAsJsonObject();
            final String field;
            switch (mutation) {
            case "missing-visible-pivot" -> {
                pivots.remove(0);
                field = "placement[0]";
            }
            case "different-visible-index" -> {
                final JsonObject last = pivots.get(pivots.size() - 1).getAsJsonObject();
                last.addProperty("index", last.get("index").getAsInt() + 1);
                field = "placement[" + (placement.size() - 1) + "]";
            }
            case "different-visible-price" -> {
                visible.addProperty("price", visible.get("price").getAsString() + "1");
                field = "placement[0]";
            }
            case "different-visible-type" -> {
                visible.addProperty("type", "HIGH");
                field = "pivots[1].type";
            }
            case "duplicate-visible-index" -> {
                pivots.get(1).getAsJsonObject().addProperty("index", 0);
                field = "pivots[1].index";
            }
            case "unsorted-visible-indices" -> {
                pivots.set(0, pivots.get(1));
                pivots.set(1, visible);
                field = "pivots[1].index";
            }
            case "price-beyond-default-precision" -> {
                visible.addProperty("price", "10.0000000000000000000000000000000000000000");
                placed.addProperty("price", "10.0000000000000000000000000000000000000001");
                field = "placement[0]";
            }
            default -> throw new AssertionError(mutation);
            }
            ReplayFixture.refreshIdentity(record);
            lines.set(line, record.toString());
            Files.write(trace, lines, StandardCharsets.UTF_8);
            ReplayFixture.refreshTraceDigest(run, "traces/d1-real.jsonl");
            for (final String key : List.of(ReplayFixture.RULES_KEY, ReplayFixture.TOPOLOGY_KEY)) {
                final String message = failureOf(run, key);
                assertTrue(message.contains(field) && message.contains("line " + (line + 1))
                        && message.contains("regenerate"), mutation + ": " + message);
            }
        }
        final Path run = ReplayFixture.write(temp.resolve("equivalent-visible-price"));
        final Path trace = run.resolve("traces/d1-real.jsonl");
        final List<String> lines = Files.readAllLines(trace);
        final int line = firstRulesCandidateLine(lines);
        final JsonObject record = JsonParser.parseString(lines.get(line)).getAsJsonObject();
        final JsonObject visible = record.getAsJsonArray("pivots").get(0).getAsJsonObject();
        visible.addProperty("price", visible.get("price").getAsString() + ".0");
        lines.set(line, record.toString());
        Files.write(trace, lines, StandardCharsets.UTF_8);
        assertNotNull(ReplayTraceIndex.scan(trace, "traces/d1-real.jsonl", family -> true, Map.of(), "ambiguousRate"));
        assertNotNull(ReplayTraceIndex.scan(trace, "traces/d1-real.jsonl", family -> false, Map.of(), "ambiguousRate"));
    }

    @Test
    public void comparedModesBindRuleIdsInRealAndNullTracesBeforeFamilyFiltering() throws IOException {
        for (final boolean nullMember : List.of(false, true)) {
            for (final boolean reorder : List.of(false, true)) {
                final Path run = ReplayFixture.write(temp.resolve("mode-rules-" + nullMember + "-" + reorder),
                        options -> {
                            options.nullTrace = nullMember;
                            return options;
                        });
                final String traceFile = "traces/d1-" + (nullMember ? "null" : "real") + ".jsonl";
                final Path trace = run.resolve(traceFile);
                final List<String> lines = Files.readAllLines(trace);
                final int line = firstRulesCandidateLine(lines);
                final JsonObject record = JsonParser.parseString(lines.get(line)).getAsJsonObject();
                final JsonArray active = record.getAsJsonArray("activeRules");
                if (reorder) {
                    final JsonElement first = active.get(0);
                    active.set(0, active.get(1));
                    active.set(1, first);
                } else {
                    active.set(0, new JsonPrimitive("foreign-rule"));
                }
                for (final JsonElement element : record.getAsJsonArray("candidates")) {
                    final JsonArray rules = element.getAsJsonObject().getAsJsonArray("rules");
                    if (reorder) {
                        final JsonElement first = rules.get(0);
                        rules.set(0, rules.get(1));
                        rules.set(1, first);
                    } else {
                        rules.get(0).getAsJsonObject().addProperty("id", "foreign-rule");
                    }
                }
                ReplayFixture.refreshIdentity(record);
                lines.set(line, record.toString());
                Files.write(trace, lines, StandardCharsets.UTF_8);
                ReplayFixture.refreshTraceDigest(run, traceFile);
                final String traceMode = nullMember ? ReplayArtifact.TRACE_MODE_NULL_MEMBER
                        : ReplayArtifact.TRACE_MODE_REAL;
                for (final String key : List.of(ReplayFixture.RULES_KEY, ReplayFixture.TOPOLOGY_KEY)) {
                    final ReplayArtifactException e = assertThrows(ReplayArtifactException.class,
                            () -> ReplaySession.open(ReplayArtifact.open(run), key, traceMode, 120, 8));
                    assertTrue(e.getMessage().contains("activeRules") && e.getMessage().contains("comparisons.csv")
                            && e.getMessage().contains(traceFile) && e.getMessage().contains("line " + (line + 1))
                            && e.getMessage().contains("regenerate"), e.getMessage());
                }
            }
        }
    }

    @Test
    public void recordKindsAreBoundToGrammarScopesBeforeFamilyFiltering() throws IOException {
        for (final boolean nullMember : List.of(false, true)) {
            final Path run = ReplayFixture.write(temp.resolve("kind-grammar-" + nullMember), options -> {
                options.nullTrace = nullMember;
                return options;
            });
            final String traceFile = "traces/d1-" + (nullMember ? "null" : "real") + ".jsonl";
            final Path trace = run.resolve(traceFile);
            final List<String> lines = Files.readAllLines(trace);
            final JsonObject original = JsonParser.parseString(lines.get(2)).getAsJsonObject();
            final String traceMode = nullMember ? ReplayArtifact.TRACE_MODE_NULL_MEMBER
                    : ReplayArtifact.TRACE_MODE_REAL;
            for (final String grammar : List.of("MOTIVE_5", "CORRECTIVE_3", "CYCLE_5_3")) {
                for (final String status : List.of("insufficient-history", "no-match", "forming", "complete",
                        "ambiguous")) {
                    final JsonObject alternative = original.deepCopy();
                    alternative.addProperty("kind", "alternative");
                    alternative.addProperty("grammar", grammar);
                    alternative.addProperty("status", status);
                    alternative.add("direction", null);
                    alternative.add("candidates", new JsonArray());
                    alternative.add("labels", new JsonArray());
                    lines.set(2, alternative.toString());
                    Files.write(trace, lines, StandardCharsets.UTF_8);
                    ReplayFixture.refreshTraceDigest(run, traceFile);
                    for (final String key : List.of(ReplayFixture.RULES_KEY, ReplayFixture.TOPOLOGY_KEY)) {
                        final ReplayArtifactException e = assertThrows(ReplayArtifactException.class,
                                () -> ReplaySession.open(ReplayArtifact.open(run), key, traceMode, 120, 8));
                        assertTrue(e.getMessage().contains("grammar") && e.getMessage().contains("line 3")
                                && e.getMessage().contains("regenerate"), e.getMessage());
                    }
                    for (final boolean keep : List.of(false, true)) {
                        final ReplayArtifactException e = assertThrows(ReplayArtifactException.class,
                                () -> ReplayTraceIndex.scan(trace, traceFile, family -> keep, Map.of(),
                                        "ambiguousRate"));
                        assertTrue(e.getMessage().contains("grammar") && e.getMessage().contains("line 3"),
                                e.getMessage());
                    }
                }
            }
        }
        for (final String grammar : List.of("3+3", "5+5", "change-point-baseline")) {
            final Path run = ReplayFixture.write(temp.resolve("structural-kind-" + grammar));
            final Path trace = run.resolve("traces/d1-real.jsonl");
            final List<String> lines = Files.readAllLines(trace);
            final JsonObject alternative = JsonParser.parseString(lines.get(3)).getAsJsonObject();
            alternative.addProperty("grammar", grammar);
            alternative.addProperty("mode", "competing-" + grammar);
            if ("change-point-baseline".equals(grammar)) {
                final JsonArray labels = new JsonArray();
                labels.add("stable");
                alternative.add("labels", labels);
                alternative.add("pivots", new JsonArray());
            }
            lines.set(3, alternative.toString());
            Files.write(trace, lines, StandardCharsets.UTF_8);
            assertNotNull(
                    ReplayTraceIndex.scan(trace, "traces/d1-real.jsonl", family -> true, Map.of(), "ambiguousRate"));
            assertNotNull(
                    ReplayTraceIndex.scan(trace, "traces/d1-real.jsonl", family -> false, Map.of(), "ambiguousRate"));
            alternative.addProperty("kind", "topology");
            alternative.addProperty("status", "INSUFFICIENT_HISTORY");
            lines.set(3, alternative.toString());
            Files.write(trace, lines, StandardCharsets.UTF_8);
            for (final boolean keep : List.of(false, true)) {
                final ReplayArtifactException e = assertThrows(ReplayArtifactException.class, () -> ReplayTraceIndex
                        .scan(trace, "traces/d1-real.jsonl", family -> keep, Map.of(), "ambiguousRate"));
                assertTrue(e.getMessage().contains("grammar") && e.getMessage().contains("line 4")
                        && e.getMessage().contains("regenerate"), e.getMessage());
            }
        }
    }

    @Test
    public void grammarShapesAreValidatedBeforeFamilyFiltering() throws IOException {
        for (final String grammar : List.of("MOTIVE_5", "CORRECTIVE_3", "CYCLE_5_3")) {
            final int required = "MOTIVE_5".equals(grammar) ? 6 : "CORRECTIVE_3".equals(grammar) ? 4 : 9;
            final List<String> mutations = new ArrayList<>(List.of("wrong-direction", "wrong-origin-type"));
            for (int p = 1; p < required; p++) {
                mutations.add("flat-leg-" + p);
                mutations.add("reversed-leg-" + p);
            }
            for (final String direction : List.of("BULLISH", "BEARISH")) {
                for (final String mutation : mutations) {
                    final Path run = ReplayFixture
                            .write(temp.resolve("shape-" + grammar + "-" + direction + "-" + mutation));
                    final Path trace = run.resolve("traces/d1-real.jsonl");
                    final List<String> lines = Files.readAllLines(trace);
                    final int line = firstRulesCandidateLine(lines);
                    final JsonObject record = JsonParser.parseString(lines.get(line)).getAsJsonObject();
                    orientGrammarShape(record, grammar, direction);
                    final JsonObject candidate = record.getAsJsonArray("candidates").get(0).getAsJsonObject();
                    final JsonArray placement = candidate.getAsJsonArray("placement");
                    if ("wrong-direction".equals(mutation)) {
                        final String opposite = "BULLISH".equals(direction) ? "BEARISH" : "BULLISH";
                        record.addProperty("direction", opposite);
                        candidate.addProperty("direction", opposite);
                    } else if ("wrong-origin-type".equals(mutation)) {
                        for (final JsonElement element : placement) {
                            final JsonObject pivot = element.getAsJsonObject();
                            pivot.addProperty("type", "LOW".equals(pivot.get("type").getAsString()) ? "HIGH" : "LOW");
                        }
                    } else {
                        final int p = Integer.parseInt(mutation.substring(mutation.lastIndexOf('-') + 1));
                        final JsonObject previous = placement.get(p - 1).getAsJsonObject();
                        final JsonObject pivot = placement.get(p).getAsJsonObject();
                        if (mutation.startsWith("flat")) {
                            pivot.add("price", previous.get("price").deepCopy());
                        } else {
                            final boolean rising = pivot.get("price")
                                    .getAsBigDecimal()
                                    .compareTo(previous.get("price").getAsBigDecimal()) > 0;
                            pivot.addProperty("price", rising ? "-2e1000" : "2e1000");
                        }
                    }
                    synchronizePlacement(record);
                    ReplayFixture.refreshIdentity(record);
                    lines.set(line, record.toString());
                    Files.write(trace, lines, StandardCharsets.UTF_8);
                    for (final boolean keep : List.of(false, true)) {
                        final ReplayArtifactException e = assertThrows(ReplayArtifactException.class,
                                () -> ReplayTraceIndex.scan(trace, "traces/d1-real.jsonl", family -> keep, Map.of(),
                                        "ambiguousRate"));
                        assertTrue(e.getMessage().contains("placement") && e.getMessage().contains("line " + (line + 1))
                                && e.getMessage().contains("regenerate"), mutation + ": " + e.getMessage());
                    }
                }
            }
        }
    }

    @Test
    public void everyFamilyMustHaveUniqueStrictlyIncreasingObservations() throws IOException {
        for (final String mode : List.of("all-rules", "topology-only")) {
            for (final boolean duplicate : List.of(false, true)) {
                final Path run = ReplayFixture.write(temp.resolve("observation-order-" + mode + "-" + duplicate));
                final Path trace = run.resolve("traces/d1-real.jsonl");
                final List<String> lines = Files.readAllLines(trace);
                final int line = firstRulesCandidateLine(lines) + ("all-rules".equals(mode) ? 0 : 1);
                final int failedLine;
                if (duplicate) {
                    lines.add(line + 1, lines.get(line));
                    final JsonObject footer = JsonParser.parseString(lines.getLast()).getAsJsonObject();
                    footer.addProperty("records", footer.get("records").getAsInt() + 1);
                    lines.set(lines.size() - 1, footer.toString());
                    failedLine = line + 2;
                } else {
                    final ReplayTraceIndex.Family family = ReplayTraceIndex.Family
                            .of(JsonParser.parseString(lines.get(line)).getAsJsonObject());
                    int previousLine = line - 1;
                    while (previousLine > 0 && !family.equals(ReplayTraceIndex.Family
                            .of(JsonParser.parseString(lines.get(previousLine)).getAsJsonObject()))) {
                        previousLine--;
                    }
                    assertTrue(previousLine > 0, "fixture has no earlier observation in " + family);
                    final String previous = lines.get(previousLine);
                    lines.set(previousLine, lines.get(line));
                    lines.set(line, previous);
                    failedLine = line + 1;
                }
                Files.write(trace, lines, StandardCharsets.UTF_8);
                ReplayFixture.refreshTraceDigest(run, "traces/d1-real.jsonl");
                for (final String key : List.of(ReplayFixture.RULES_KEY, ReplayFixture.TOPOLOGY_KEY)) {
                    final String message = failureOf(run, key);
                    assertTrue(
                            message.contains("asOfIndex") && message.contains("line " + failedLine)
                                    && message.contains("strictly increase") && message.contains("regenerate"),
                            message);
                }
                final ReplayArtifactException e = assertThrows(ReplayArtifactException.class, () -> ReplayTraceIndex
                        .scan(trace, "traces/d1-real.jsonl", family -> false, Map.of(), "ambiguousRate"));
                assertTrue(e.getMessage().contains("asOfIndex") && e.getMessage().contains("line " + failedLine),
                        e.getMessage());
            }
        }
        for (final String field : List.of("section", "mode", "grammar", "detector", "partition")) {
            final Path run = ReplayFixture.write(temp.resolve("distinct-scope-" + field));
            final Path trace = run.resolve("traces/d1-real.jsonl");
            final List<String> lines = Files.readAllLines(trace);
            final JsonObject distinct = JsonParser.parseString(lines.get(1)).getAsJsonObject();
            distinct.addProperty(field, "grammar".equals(field) ? "CORRECTIVE_3" : "another-" + field);
            lines.add(2, distinct.toString());
            final JsonObject footer = JsonParser.parseString(lines.getLast()).getAsJsonObject();
            footer.addProperty("records", footer.get("records").getAsInt() + 1);
            lines.set(lines.size() - 1, footer.toString());
            Files.write(trace, lines, StandardCharsets.UTF_8);
            assertNotNull(
                    ReplayTraceIndex.scan(trace, "traces/d1-real.jsonl", family -> true, Map.of(), "ambiguousRate"));
            assertNotNull(
                    ReplayTraceIndex.scan(trace, "traces/d1-real.jsonl", family -> false, Map.of(), "ambiguousRate"));
        }
    }

    @Test
    public void formingSuffixGrammarBoundsAndNewestEndpointsAreCheckedBeforeFamilyFiltering() throws IOException {
        final Path run = ReplayFixture.write(temp.resolve("forming-contract-header"));
        final List<String> lines = Files.readAllLines(run.resolve("traces/d1-real.jsonl"));
        final JsonObject original = JsonParser.parseString(lines.get(firstRulesCandidateLine(lines))).getAsJsonObject();
        for (final String grammar : List.of("MOTIVE_5", "CORRECTIVE_3", "CYCLE_5_3")) {
            final int required = "MOTIVE_5".equals(grammar) ? 6 : "CORRECTIVE_3".equals(grammar) ? 4 : 9;
            for (final String direction : List.of("BULLISH", "BEARISH")) {
                for (int length = 3; length < required; length++) {
                    final JsonObject record = original.deepCopy();
                    orientGrammarShape(record, grammar, direction);
                    retainDistinctCandidates(record, 1);
                    final JsonArray completePivots = record.getAsJsonArray("pivots").deepCopy();
                    final JsonArray pivots = record.getAsJsonArray("pivots");
                    while (pivots.size() > length) {
                        pivots.remove(pivots.size() - 1);
                    }
                    pivots.get(0)
                            .getAsJsonObject()
                            .addProperty("confirmationIndex", record.get("asOfIndex").getAsInt());
                    record.addProperty("status", "FORMING");
                    record.addProperty("direction", direction);
                    record.add("candidates", new JsonArray());
                    record.addProperty("formingStartBarIndex", 0);
                    record.addProperty("formingEndBarIndex", length - 1);
                    ReplayFixture.refreshIdentity(record);
                    final String name = "forming-" + grammar + "-" + direction + "-" + length;
                    final Path trace = singleRecordTrace(name, lines.getFirst(), record);
                    assertSingleRecordReadable(trace, name, record);
                    for (final String mutation : List.of("not-newest", "two-pivot-suffix", "wrong-direction",
                            "flat-leg", "complete-length")) {
                        final JsonObject damaged = record.deepCopy();
                        switch (mutation) {
                        case "not-newest" -> damaged.addProperty("formingEndBarIndex", length - 2);
                        case "two-pivot-suffix" -> damaged.addProperty("formingStartBarIndex", length - 2);
                        case "wrong-direction" ->
                            damaged.addProperty("direction", "BULLISH".equals(direction) ? "BEARISH" : "BULLISH");
                        case "flat-leg" -> damaged.getAsJsonArray("pivots")
                                .get(length - 1)
                                .getAsJsonObject()
                                .add("price", pivots.get(length - 2).getAsJsonObject().get("price").deepCopy());
                        case "complete-length" -> {
                            damaged.add("pivots", completePivots.deepCopy());
                            damaged.addProperty("formingEndBarIndex", required - 1);
                        }
                        default -> throw new AssertionError(mutation);
                        }
                        ReplayFixture.refreshIdentity(damaged);
                        singleRecordTrace(name, lines.getFirst(), damaged);
                        for (final boolean keep : List.of(false, true)) {
                            final ReplayArtifactException rejected = assertThrows(ReplayArtifactException.class,
                                    () -> ReplayTraceIndex.scan(trace, name, family -> keep, Map.of(),
                                            "ambiguousRate"));
                            assertTrue(
                                    rejected.getMessage().contains("forming")
                                            && rejected.getMessage().contains("line 2")
                                            && rejected.getMessage().contains("regenerate"),
                                    mutation + ": " + rejected.getMessage());
                        }
                    }
                }
            }
        }
    }

    @Test
    public void shorterDisjointFormingSuffixesAndFutureConfirmationWarningsRemainReadable() throws IOException {
        final Path run = ReplayFixture.write(temp.resolve("disjoint-contract-header"));
        final List<String> lines = Files.readAllLines(run.resolve("traces/d1-real.jsonl"));
        final JsonObject original = JsonParser.parseString(lines.get(firstRulesCandidateLine(lines))).getAsJsonObject();
        final ReplaySession source = ReplaySession.open(ReplayArtifact.open(run), ReplayFixture.RULES_KEY,
                ReplayArtifact.TRACE_MODE_REAL, 120, 8);
        final List<String> activeRules = original.getAsJsonArray("activeRules")
                .asList()
                .stream()
                .map(JsonElement::getAsString)
                .toList();
        for (final String grammar : List.of("MOTIVE_5", "CORRECTIVE_3", "CYCLE_5_3")) {
            final int required = "MOTIVE_5".equals(grammar) ? 6 : "CORRECTIVE_3".equals(grammar) ? 4 : 9;
            for (final String direction : List.of("BULLISH", "BEARISH")) {
                final JsonObject record = original.deepCopy();
                orientGrammarShape(record, grammar, direction);
                retainDistinctCandidates(record, 3);
                final JsonArray pivots = record.getAsJsonArray("pivots");
                pivots.remove(pivots.size() - 1);
                record.add("candidates", new JsonArray());
                record.addProperty("status", "FORMING");
                final boolean low = "LOW".equals(pivots.get(required).getAsJsonObject().get("type").getAsString());
                record.addProperty("direction", low != "CORRECTIVE_3".equals(grammar) ? "BULLISH" : "BEARISH");
                record.addProperty("formingStartBarIndex", required);
                record.addProperty("formingEndBarIndex", required + 2);
                ReplayFixture.refreshIdentity(record);
                final String name = "disjoint-" + grammar + "-" + direction;
                final Path trace = singleRecordTrace(name, lines.getFirst(), record);
                assertSingleRecordReadable(trace, name, record);
                final JsonObject leaked = record.deepCopy();
                final JsonObject futurePivot = pivots.get(pivots.size() - 1).getAsJsonObject().deepCopy();
                futurePivot.addProperty("index", record.get("asOfIndex").getAsInt() + 1);
                futurePivot.addProperty("confirmationIndex", record.get("asOfIndex").getAsInt() + 1);
                leaked.getAsJsonArray("pivots").add(futurePivot);
                final Path leakedTrace = singleRecordTrace(name + "-future", lines.getFirst(), leaked);
                assertSingleRecordReadable(leakedTrace, name + "-future", leaked);
                for (final String profile : List.of("interior", "suffix", "all")) {
                    final JsonObject futureSuffix = leaked.deepCopy();
                    int suppressedCount = 1;
                    for (int p = 0; p < pivots.size(); p++) {
                        if ("all".equals(profile) || "suffix".equals(profile) && p >= required
                                || "interior".equals(profile) && p == required + 1) {
                            futureSuffix.getAsJsonArray("pivots")
                                    .get(p)
                                    .getAsJsonObject()
                                    .addProperty("confirmationIndex", record.get("asOfIndex").getAsInt() + 1);
                            suppressedCount++;
                        }
                    }
                    final String futureName = name + "-future-" + profile;
                    final Path suffixTrace = singleRecordTrace(futureName, lines.getFirst(), futureSuffix);
                    assertSingleRecordReadable(suffixTrace, futureName, futureSuffix);
                    final ReplayFrame frame = ReplayFrame.project(ReplayFixture.RULES_KEY,
                            ReplayTraceIndex.Family.of(futureSuffix), activeRules, futureSuffix, source.bars(), true,
                            false, "", 120, 8, "", 0);
                    assertEquals("FORMING", frame.status());
                    assertEquals(suppressedCount, frame.suppressedFuture());
                    assertEquals(futureSuffix.getAsJsonArray("pivots").size() - suppressedCount, frame.pivots().size());
                    assertTrue(ReplayEvidenceText.render(frame).contains("causality guard removed " + suppressedCount));
                }
            }
        }
        final JsonObject future = original.deepCopy();
        retainDistinctCandidates(future, 1);
        insertInterveningPivots(future, true);
        future.getAsJsonArray("pivots").get(1).getAsJsonObject().addProperty("type", "LOW");
        future.getAsJsonArray("pivots").get(2).getAsJsonObject().addProperty("type", "LOW");
        ReplayFixture.refreshIdentity(future);
        final Path futureTrace = singleRecordTrace("future-intervening-pivots", lines.getFirst(), future);
        assertSingleRecordReadable(futureTrace, "future-intervening-pivots", future);
        final ReplayFrame suppressed = ReplayFrame.project(ReplayFixture.RULES_KEY, ReplayTraceIndex.Family.of(future),
                activeRules, future, source.bars(), true, false, "", 120, 8, "", 0);
        assertEquals(2, suppressed.suppressedFuture());
        assertEquals(original.getAsJsonArray("pivots").size(), suppressed.pivots().size());
        assertTrue(ReplayEvidenceText.render(suppressed).contains("causality guard removed 2"));
        final JsonObject placedFuture = original.deepCopy();
        retainDistinctCandidates(placedFuture, 1);
        placedFuture.getAsJsonArray("pivots")
                .get(2)
                .getAsJsonObject()
                .addProperty("confirmationIndex", placedFuture.get("asOfIndex").getAsInt() + 1);
        ReplayFixture.refreshIdentity(placedFuture);
        final Path placedTrace = singleRecordTrace("future-candidate-pivot", lines.getFirst(), placedFuture);
        assertSingleRecordReadable(placedTrace, "future-candidate-pivot", placedFuture);
        final ReplayFrame placedSuppressed = ReplayFrame.project(ReplayFixture.RULES_KEY,
                ReplayTraceIndex.Family.of(placedFuture), activeRules, placedFuture, source.bars(), true, false, "",
                120, 8, "", 0);
        assertEquals(2, placedSuppressed.suppressedFuture());
        assertEquals(original.getAsJsonArray("pivots").size() - 1, placedSuppressed.pivots().size());
        assertEquals(
                original.getAsJsonArray("candidates").get(0).getAsJsonObject().getAsJsonArray("placement").size() - 1,
                placedSuppressed.candidates().getFirst().placement().size());
        assertTrue(ReplayEvidenceText.render(placedSuppressed).contains("causality guard removed 2"));
    }

    @Test
    public void futureConfirmedHistoryRemainsInspectableForEveryTopologyState() throws IOException {
        final Path run = ReplayFixture.write(temp.resolve("future-history-contract"));
        final List<String> lines = Files.readAllLines(run.resolve("traces/d1-real.jsonl"));
        final JsonObject original = JsonParser.parseString(lines.get(firstRulesCandidateLine(lines))).getAsJsonObject();
        final ReplaySession source = ReplaySession.open(ReplayArtifact.open(run), ReplayFixture.RULES_KEY,
                ReplayArtifact.TRACE_MODE_REAL, 120, 8);
        final List<String> activeRules = original.getAsJsonArray("activeRules")
                .asList()
                .stream()
                .map(JsonElement::getAsString)
                .toList();
        for (final String status : List.of("COMPLETE", "AMBIGUOUS", "INSUFFICIENT_HISTORY", "NO_MATCH",
                "INVALIDATED")) {
            for (final int confirmed : List.of(0, 1)) {
                final JsonObject record = original.deepCopy();
                retainDistinctCandidates(record, "AMBIGUOUS".equals(status) ? 2 : 1);
                record.addProperty("status", status);
                if (!"COMPLETE".equals(status)) {
                    record.add("direction", JsonNull.INSTANCE);
                    if (!"AMBIGUOUS".equals(status)) {
                        record.add("candidates", new JsonArray());
                    }
                }
                final JsonArray pivots = record.getAsJsonArray("pivots");
                for (int p = confirmed; p < pivots.size(); p++) {
                    pivots.get(p)
                            .getAsJsonObject()
                            .addProperty("confirmationIndex", record.get("asOfIndex").getAsInt() + 1);
                }
                ReplayFixture.refreshIdentity(record);
                final String name = "future-history-" + status + "-" + confirmed;
                final Path trace = singleRecordTrace(name, lines.getFirst(), record);
                assertSingleRecordReadable(trace, name, record);
                int suppressedCount = pivots.size() - confirmed;
                for (final JsonElement candidate : record.getAsJsonArray("candidates")) {
                    for (final JsonElement point : candidate.getAsJsonObject().getAsJsonArray("placement")) {
                        if (confirmed == 0 || point.getAsJsonObject()
                                .get("index")
                                .getAsInt() != pivots.get(0).getAsJsonObject().get("index").getAsInt()) {
                            suppressedCount++;
                        }
                    }
                }
                final ReplayFrame frame = ReplayFrame.project(ReplayFixture.RULES_KEY,
                        ReplayTraceIndex.Family.of(record), activeRules, record, source.bars(), true, false, "", 120, 8,
                        "", 0);
                assertEquals(status, frame.status());
                assertEquals(confirmed, frame.pivots().size());
                assertEquals(suppressedCount, frame.suppressedFuture());
                assertTrue(ReplayEvidenceText.render(frame).contains("causality guard removed " + suppressedCount));
            }
        }
    }

    @Test
    public void capturedPivotStatesAreValidatedEvenForAnUnselectedReplayFamily() throws IOException {
        for (final String mutation : List.of("nonalternating", "baseline-pivots", "alternative-direction",
                "insufficient-with-visible-history", "no-match-with-one-pivot", "invalidated-with-one-pivot",
                "duplicate-candidate", "gapped-candidate", "forming-not-newest", "forming-two-pivots",
                "forming-wrong-direction", "forming-flat-leg", "forming-complete-length")) {
            final Path run = ReplayFixture.write(temp.resolve("captured-state-" + mutation));
            final String relative = "traces/d1-real.jsonl";
            final Path trace = run.resolve(relative);
            final List<String> lines = Files.readAllLines(trace);
            final int line = firstRulesCandidateLine(lines) + 1;
            JsonObject record = JsonParser.parseString(lines.get(line)).getAsJsonObject();
            retainDistinctCandidates(record, 1);
            String field;
            if (mutation.startsWith("forming-")) {
                final JsonArray pivots = record.getAsJsonArray("pivots");
                if (!"forming-complete-length".equals(mutation)) {
                    pivots.remove(pivots.size() - 1);
                }
                record.addProperty("status", "FORMING");
                record.addProperty("direction", "BULLISH");
                record.add("candidates", new JsonArray());
                record.addProperty("formingStartBarIndex", "forming-two-pivots".equals(mutation) ? 3 : 0);
                record.addProperty("formingEndBarIndex", "forming-not-newest".equals(mutation) ? 3 : pivots.size() - 1);
                if ("forming-wrong-direction".equals(mutation)) {
                    record.addProperty("direction", "BEARISH");
                } else if ("forming-flat-leg".equals(mutation)) {
                    pivots.get(4)
                            .getAsJsonObject()
                            .add("price", pivots.get(3).getAsJsonObject().get("price").deepCopy());
                }
                field = "forming";
            } else {
                switch (mutation) {
                case "nonalternating" -> {
                    record.getAsJsonArray("pivots").get(1).getAsJsonObject().addProperty("type", "LOW");
                    field = "pivots[1].type";
                }
                case "baseline-pivots", "alternative-direction" -> {
                    record = ReplayFixture.competingRecord(record.get("asOfIndex").getAsInt());
                    if ("baseline-pivots".equals(mutation)) {
                        record.addProperty("grammar", "change-point-baseline");
                        record.addProperty("mode", "competing-change-point-baseline");
                        record.addProperty("status", "complete");
                        final JsonArray labels = new JsonArray();
                        labels.add("change@" + record.get("asOfIndex").getAsInt());
                        record.add("labels", labels);
                        field = "pivots";
                    } else {
                        record.addProperty("direction", "BULLISH");
                        field = "direction";
                    }
                }
                case "insufficient-with-visible-history", "no-match-with-one-pivot", "invalidated-with-one-pivot" -> {
                    record.addProperty("status",
                            "insufficient-with-visible-history".equals(mutation) ? "INSUFFICIENT_HISTORY"
                                    : "no-match-with-one-pivot".equals(mutation) ? "NO_MATCH" : "INVALIDATED");
                    record.add("direction", JsonNull.INSTANCE);
                    record.add("candidates", new JsonArray());
                    if (!"insufficient-with-visible-history".equals(mutation)) {
                        final JsonArray pivots = record.getAsJsonArray("pivots");
                        while (pivots.size() > 1) {
                            pivots.remove(pivots.size() - 1);
                        }
                    }
                    field = "status";
                }
                case "duplicate-candidate" -> {
                    record.addProperty("status", "AMBIGUOUS");
                    record.add("direction", JsonNull.INSTANCE);
                    record.getAsJsonArray("candidates").add(record.getAsJsonArray("candidates").get(0).deepCopy());
                    field = "candidateKey";
                }
                case "gapped-candidate" -> {
                    insertInterveningPivots(record, false);
                    field = "placement[1]";
                }
                default -> throw new AssertionError(mutation);
                }
            }
            ReplayFixture.refreshIdentity(record);
            lines.set(line, record.toString());
            Files.write(trace, lines, StandardCharsets.UTF_8);
            ReplayFixture.refreshTraceDigest(run, relative);
            for (final String key : List.of(ReplayFixture.RULES_KEY, ReplayFixture.TOPOLOGY_KEY)) {
                final String message = failureOf(run, key);
                assertTrue(message.contains(field) && message.contains("line " + (line + 1))
                        && message.contains("regenerate"), mutation + ": " + message);
            }
            for (final boolean keep : List.of(false, true)) {
                final ReplayArtifactException rejected = assertThrows(ReplayArtifactException.class,
                        () -> ReplayTraceIndex.scan(trace, relative, family -> keep, Map.of(), "ambiguousRate"));
                assertTrue(rejected.getMessage().contains(field), mutation + ": " + rejected.getMessage());
            }
        }
    }

    private static void assertSingleRecordReadable(final Path trace, final String display, final JsonObject record) {
        for (final boolean keep : List.of(false, true)) {
            final ReplayTraceIndex index = ReplayTraceIndex.scan(trace, display, family -> keep, Map.of(),
                    "ambiguousRate");
            assertEquals(1, index.recordCount());
            final List<ReplayTraceIndex.Entry> entries = index.entries(ReplayTraceIndex.Family.of(record));
            assertEquals(keep ? 1 : 0, entries.size());
            if (keep) {
                assertEquals(record, index.read(entries.getFirst()));
            }
        }
    }

    private static void insertInterveningPivots(final JsonObject record, final boolean future) {
        final JsonArray pivots = record.getAsJsonArray("pivots");
        final JsonArray expanded = new JsonArray();
        for (int p = 0; p < pivots.size(); p++) {
            if (p == 1) {
                for (int inserted = 1; inserted <= 2; inserted++) {
                    final JsonObject extra = pivots.get(inserted % 2).getAsJsonObject().deepCopy();
                    extra.addProperty("index", inserted);
                    extra.addProperty("confirmationIndex", future ? record.get("asOfIndex").getAsInt() + 1 : inserted);
                    expanded.add(extra);
                }
            }
            final JsonObject pivot = pivots.get(p).getAsJsonObject().deepCopy();
            pivot.addProperty("index", p == 0 ? 0 : p + 2);
            pivot.addProperty("confirmationIndex", p == 0 ? 0 : p + 2);
            expanded.add(pivot);
        }
        record.add("pivots", expanded);
        final JsonObject candidate = record.getAsJsonArray("candidates").get(0).getAsJsonObject();
        final JsonArray placement = candidate.getAsJsonArray("placement");
        for (int p = 1; p < placement.size(); p++) {
            placement.get(p).getAsJsonObject().addProperty("index", p + 2);
        }
        candidate.addProperty("endBarIndex", pivots.size() + 1);
        record.addProperty("asOfIndex", Math.max(record.get("asOfIndex").getAsInt(), pivots.size() + 1));
    }

    private static void orientGrammarShape(final JsonObject record, final String grammar, final String direction) {
        record.addProperty("grammar", grammar);
        record.addProperty("direction", direction);
        final JsonObject candidate = record.getAsJsonArray("candidates").get(0).getAsJsonObject();
        candidate.addProperty("direction", direction);
        final JsonArray placement = candidate.getAsJsonArray("placement");
        final int required = "MOTIVE_5".equals(grammar) ? 6 : "CORRECTIVE_3".equals(grammar) ? 4 : 9;
        while (placement.size() > required) {
            placement.remove(placement.size() - 1);
        }
        while (placement.size() < required) {
            final JsonObject next = placement.get(placement.size() - 1).getAsJsonObject().deepCopy();
            next.addProperty("index", next.get("index").getAsInt() + 1);
            placement.add(next);
            final JsonObject visible = next.deepCopy();
            visible.addProperty("confirmationIndex", visible.get("index").getAsInt());
            record.getAsJsonArray("pivots").add(visible);
        }
        final JsonArray visible = record.getAsJsonArray("pivots");
        while (visible.size() > placement.size()) {
            visible.remove(visible.size() - 1);
        }
        final boolean startsLow = "BULLISH".equals(direction) != "CORRECTIVE_3".equals(grammar);
        for (int p = 0; p < placement.size(); p++) {
            final boolean low = (p % 2 == 0) == startsLow;
            final JsonObject pivot = placement.get(p).getAsJsonObject();
            pivot.addProperty("type", low ? "LOW" : "HIGH");
            pivot.addProperty("price", low ? "-1e1000" : "1e1000");
        }
        synchronizePlacement(record);
    }

    private static void retainDistinctCandidates(final JsonObject record, final int count) {
        final JsonObject template = record.getAsJsonArray("candidates").get(0).getAsJsonObject().deepCopy();
        final JsonArray original = template.getAsJsonArray("placement");
        final int required = original.size();
        final JsonArray pivots = new JsonArray();
        for (int p = 0; p < required + 2 * (count - 1); p++) {
            final JsonObject pivot = original.get(p % 2).getAsJsonObject().deepCopy();
            pivot.addProperty("index", p);
            pivot.addProperty("confirmationIndex", p);
            pivots.add(pivot);
        }
        final JsonArray candidates = new JsonArray();
        for (int i = 0; i < count; i++) {
            final int start = 2 * i;
            final JsonArray placement = new JsonArray();
            for (int p = start; p < start + required; p++) {
                final JsonObject pivot = pivots.get(p).getAsJsonObject().deepCopy();
                pivot.remove("confirmationIndex");
                placement.add(pivot);
            }
            final JsonObject candidate = template.deepCopy();
            candidate.add("placement", placement);
            candidate.addProperty("startBarIndex", start);
            candidate.addProperty("endBarIndex", start + required - 1);
            candidates.add(candidate);
        }
        record.add("pivots", pivots);
        record.add("candidates", candidates);
        record.addProperty("asOfIndex", Math.max(record.get("asOfIndex").getAsInt(), pivots.size() - 1));
    }

    private static void synchronizePlacement(final JsonObject record) {
        for (final JsonElement element : record.getAsJsonArray("candidates")
                .get(0)
                .getAsJsonObject()
                .getAsJsonArray("placement")) {
            final JsonObject placed = element.getAsJsonObject();
            for (final JsonElement visible : record.getAsJsonArray("pivots")) {
                final JsonObject pivot = visible.getAsJsonObject();
                if (pivot.get("index").equals(placed.get("index"))) {
                    pivot.add("type", placed.get("type").deepCopy());
                    pivot.add("price", placed.get("price").deepCopy());
                }
            }
        }
    }

    private static int firstRulesCandidateLine(final List<String> lines) {
        for (int line = 1; line < lines.size() - 1; line++) {
            final JsonObject record = JsonParser.parseString(lines.get(line)).getAsJsonObject();
            if ("all-rules".equals(record.get("mode").getAsString())
                    && record.getAsJsonArray("candidates").size() == 1) {
                return line;
            }
        }
        throw new AssertionError("fixture has no single-candidate all-rules record");
    }

    @Test
    public void allRecordedRuleStatesRemainReplayable() throws IOException {
        for (final String state : List.of("PASS", "FAIL", "PENDING", "UNAVAILABLE", "NOT_APPLICABLE")) {
            final Path run = ReplayFixture.write(temp.resolve("valid-state-" + state));
            final Path trace = run.resolve("traces/d1-real.jsonl");
            final List<String> lines = Files.readAllLines(trace);
            int asOf = -1;
            for (int line = 1; line < lines.size() - 1; line++) {
                final JsonObject record = JsonParser.parseString(lines.get(line)).getAsJsonObject();
                final var candidates = record.getAsJsonArray("candidates");
                if (!candidates.isEmpty() && !candidates.get(0).getAsJsonObject().getAsJsonArray("rules").isEmpty()) {
                    final JsonObject candidate = candidates.get(0).getAsJsonObject();
                    final JsonObject rule = candidate.getAsJsonArray("rules").get(0).getAsJsonObject();
                    rule.addProperty("state", state);
                    if (!"PASS".equals(state)) {
                        rule.add("score", JsonNull.INSTANCE);
                    }
                    ReplayFixture.refreshVersion(candidate);
                    lines.set(line, record.toString());
                    asOf = record.get("asOfIndex").getAsInt();
                    break;
                }
            }
            assertTrue(asOf >= 0, state);
            Files.write(trace, lines, StandardCharsets.UTF_8);
            ReplayFixture.refreshTraceDigest(run, "traces/d1-real.jsonl");

            final ReplaySession session = ReplaySession.open(ReplayArtifact.open(run), ReplayFixture.RULES_KEY,
                    ReplayArtifact.TRACE_MODE_REAL, 120, 8);
            assertTrue(ReplayEvidenceText.render(session.seek(asOf)).contains(state), state);
        }
    }

    @Test
    public void traceIntegersCannotWrapBeforeReplayFramesReadThem() throws IOException {
        final List<String> fields = List.of("asOfIndex", "nullBlockLength", "nullMemberIndex", "pivot.index",
                "pivot.confirmationIndex", "candidate.placement.index");
        int fixture = 0;
        for (final String field : fields) {
            for (final String value : List.of("2147483648", "-2147483649", "4294967296", "1e1000", "10.5",
                    "1e2147483648")) {
                final Path run = ReplayFixture.write(temp.resolve("record-integer-" + fixture++));
                final Path trace = run.resolve("traces/d1-real.jsonl");
                final List<String> lines = Files.readAllLines(trace);
                int line = 1;
                JsonObject record = JsonParser.parseString(lines.get(line)).getAsJsonObject();
                while (record.getAsJsonArray("candidates").isEmpty()) {
                    record = JsonParser.parseString(lines.get(++line)).getAsJsonObject();
                }
                final JsonObject owner;
                final String name;
                if (field.startsWith("pivot.")) {
                    owner = record.getAsJsonArray("pivots").get(0).getAsJsonObject();
                    name = field.substring("pivot.".length());
                } else if (field.equals("candidate.placement.index")) {
                    owner = record.getAsJsonArray("candidates")
                            .get(0)
                            .getAsJsonObject()
                            .getAsJsonArray("placement")
                            .get(0)
                            .getAsJsonObject();
                    name = "index";
                } else {
                    owner = record;
                    name = field;
                }
                owner.add(name, JsonParser.parseString(value));
                lines.set(line, record.toString());
                Files.write(trace, lines, StandardCharsets.UTF_8);
                ReplayFixture.refreshTraceDigest(run, "traces/d1-real.jsonl");

                final String message = failureOf(run, ReplayFixture.RULES_KEY);
                assertTrue(
                        message.contains(name) && message.contains("32-bit") && message.contains("line " + (line + 1)),
                        field + "=" + value + ": " + message);
            }
        }
    }

    @Test
    public void headerAndSelectedMemberCoordinatesCannotWrapBeforeIdentityChecks() throws IOException {
        int fixture = 0;
        for (final String field : List.of("nullBlockLength", "nullMemberIndex")) {
            for (final String value : List.of("2147483648", "-2147483649", "4294967295", "1e1000", "1.5",
                    "1e2147483648")) {
                final Path run = ReplayFixture.write(temp.resolve("header-integer-" + fixture++));
                final Path trace = run.resolve("traces/d1-real.jsonl");
                final List<String> lines = Files.readAllLines(trace);
                final JsonObject header = JsonParser.parseString(lines.getFirst()).getAsJsonObject();
                header.add(field, JsonParser.parseString(value));
                lines.set(0, header.toString());
                Files.write(trace, lines, StandardCharsets.UTF_8);
                ReplayFixture.refreshTraceDigest(run, "traces/d1-real.jsonl");

                final String message = failureOf(run, ReplayFixture.RULES_KEY);
                assertTrue(message.contains(field) && message.contains("32-bit"), value + ": " + message);

                final Path selected = ReplayFixture.write(temp.resolve("selected-integer-" + fixture++), options -> {
                    options.nullTrace = true;
                    return options;
                });
                final Path metadata = selected.resolve("run.json");
                final JsonObject runJson = JsonParser.parseString(Files.readString(metadata)).getAsJsonObject();
                final String selectedField = "nullBlockLength".equals(field) ? "block" : "member";
                runJson.getAsJsonObject("trace").add(selectedField, JsonParser.parseString(value));
                Files.writeString(metadata, runJson.toString(), StandardCharsets.UTF_8);
                final ReplayArtifactException e = assertThrows(ReplayArtifactException.class,
                        () -> ReplaySession.open(ReplayArtifact.open(selected), ReplayFixture.RULES_KEY,
                                ReplayArtifact.TRACE_MODE_NULL_MEMBER, 120, 8));
                assertTrue(e.getMessage().contains(selectedField) && e.getMessage().contains("32-bit"),
                        value + ": " + e.getMessage());
            }
        }
    }

    @Test
    public void retainedPriceBarRowCountIsValidatedBeforeNarrowing() throws IOException {
        int fixture = 0;
        for (final String value : List.of("4294967446", "2147483648", "-2147483649", "150.5", "1e1000",
                "1e2147483648")) {
            final Path run = ReplayFixture.write(temp.resolve("rows-integer-" + fixture++));
            final Path metadata = run.resolve("run.json");
            final JsonObject runJson = JsonParser.parseString(Files.readString(metadata)).getAsJsonObject();
            runJson.getAsJsonArray("datasets")
                    .get(0)
                    .getAsJsonObject()
                    .getAsJsonObject("priceBars")
                    .add("rows", JsonParser.parseString(value));
            Files.writeString(metadata, runJson.toString(), StandardCharsets.UTF_8);

            final ReplayArtifactException e = assertThrows(ReplayArtifactException.class,
                    () -> ReplayArtifact.open(run));
            assertTrue(e.getMessage().contains("rows") && e.getMessage().contains("32-bit"),
                    value + ": " + e.getMessage());
        }
    }

    @Test
    public void malformedRunMetadataHasArtifactDiagnosticsInsteadOfUncheckedFailures() throws IOException {
        int fixture = 0;
        for (final String field : List.of("artifactSchemaVersion", "status", "revision", "datasets", "dataset-entry",
                "traces", "trace-entry", "coverage", "priceBars", "source", "id", "asset", "identity", "fingerprint",
                "missing-identity", "missing-fingerprint", "empty-fingerprint", "number-fingerprint", "rows")) {
            final Path run = ReplayFixture.write(temp.resolve("malformed-" + fixture++));
            final Path metadata = run.resolve("run.json");
            final JsonObject json = JsonParser.parseString(Files.readString(metadata)).getAsJsonObject();
            final JsonObject dataset = json.getAsJsonArray("datasets").get(0).getAsJsonObject();
            final String expectedField;
            switch (field) {
            case "artifactSchemaVersion", "status", "revision", "datasets" -> {
                json.add(field, new JsonObject());
                expectedField = field;
            }
            case "dataset-entry" -> {
                json.getAsJsonArray("datasets").set(0, JsonParser.parseString("7"));
                expectedField = "dataset";
            }
            case "trace-entry" -> {
                dataset.getAsJsonArray("traces").set(0, new JsonObject());
                expectedField = "traces";
            }
            case "coverage", "priceBars", "source" -> {
                dataset.add(field, JsonParser.parseString("[]"));
                expectedField = field;
            }
            case "identity" -> {
                json.add(field, JsonParser.parseString("[]"));
                expectedField = field;
            }
            case "fingerprint" -> {
                json.getAsJsonObject("identity").add(field, new JsonObject());
                expectedField = field;
            }
            case "missing-identity" -> {
                json.remove("identity");
                expectedField = "identity.fingerprint";
            }
            case "missing-fingerprint" -> {
                json.getAsJsonObject("identity").remove("fingerprint");
                expectedField = "identity.fingerprint";
            }
            case "empty-fingerprint" -> {
                json.getAsJsonObject("identity").addProperty("fingerprint", "");
                expectedField = "identity.fingerprint";
            }
            case "number-fingerprint" -> {
                json.getAsJsonObject("identity").addProperty("fingerprint", 7);
                expectedField = "fingerprint";
            }
            case "rows" -> {
                dataset.getAsJsonObject("priceBars").addProperty("rows", "not-a-number");
                expectedField = field;
            }
            default -> {
                dataset.add(field, new JsonObject());
                expectedField = field;
            }
            }
            Files.writeString(metadata, json.toString(), StandardCharsets.UTF_8);

            final String message = failureOf(run, ReplayFixture.RULES_KEY);
            assertTrue(message.contains(expectedField), field + ": " + message);
            assertTrue(message.contains("Regenerate"), field + ": " + message);
        }
    }

    @Test
    public void completeTraceBytesMustMatchMetadataEvenOutsideTheSelectedFamily() throws IOException {
        for (final boolean nullMember : List.of(false, true)) {
            for (final String damage : List.of("selected-observation", "other-observation", "labels",
                    "missing-digest")) {
                final Path run = ReplayFixture.write(temp.resolve("trace-digest-" + nullMember + "-" + damage),
                        options -> {
                            options.nullTrace = nullMember;
                            return options;
                        });
                final String relative = nullMember ? "traces/d1-null.jsonl" : "traces/d1-real.jsonl";
                if ("missing-digest".equals(damage)) {
                    final Path metadata = run.resolve("run.json");
                    final JsonObject json = JsonParser.parseString(Files.readString(metadata)).getAsJsonObject();
                    json.getAsJsonArray("datasets").get(0).getAsJsonObject().remove("traceSha256");
                    Files.writeString(metadata, json.toString());
                } else {
                    final Path trace = run.resolve(relative);
                    final List<String> lines = new ArrayList<>(Files.readAllLines(trace, StandardCharsets.UTF_8));
                    final String mode = "other-observation".equals(damage) ? nullMember ? "MOTIVE_5" : "topology-only"
                            : "all-rules";
                    for (int i = 1; i < lines.size() - 1; i++) {
                        final JsonObject record = JsonParser.parseString(lines.get(i)).getAsJsonObject();
                        if (!mode.equals(record.get("mode").getAsString())) {
                            continue;
                        }
                        if ("labels".equals(damage)) {
                            final JsonArray labels = record.getAsJsonArray("labels");
                            if (labels.size() < 2) {
                                continue;
                            }
                            final JsonElement first = labels.get(0);
                            labels.set(0, labels.get(1));
                            labels.set(1, first);
                            lines.set(i, record.toString());
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
                final String traceMode = nullMember ? ReplayArtifact.TRACE_MODE_NULL_MEMBER
                        : ReplayArtifact.TRACE_MODE_REAL;
                final ReplayArtifactException rejected = assertThrows(ReplayArtifactException.class,
                        () -> ReplaySession.open(ReplayArtifact.open(run), ReplayFixture.RULES_KEY, traceMode, 120, 8));
                assertTrue(rejected.getMessage().contains("SHA-256") && rejected.getMessage().contains("Regenerate"),
                        damage + ": " + rejected.getMessage());
            }
        }
    }

    @Test
    public void malformedTraceIdentityContainersAndSourceDigestsHaveArtifactDiagnostics() throws IOException {
        final Path selected = ReplayFixture.write(temp.resolve("invalid-config"), options -> {
            options.nullTrace = true;
            return options;
        });
        final Path metadata = selected.resolve("run.json");
        final JsonObject json = JsonParser.parseString(Files.readString(metadata)).getAsJsonObject();
        json.add("trace", JsonParser.parseString("[]"));
        Files.writeString(metadata, json.toString(), StandardCharsets.UTF_8);
        final ReplayArtifactException invalidConfig = assertThrows(ReplayArtifactException.class,
                () -> ReplaySession.open(ReplayArtifact.open(selected), ReplayFixture.RULES_KEY,
                        ReplayArtifact.TRACE_MODE_NULL_MEMBER, 120, 8));
        assertTrue(invalidConfig.getMessage().contains("trace"), invalidConfig.getMessage());

        final Path real = ReplayFixture.write(temp.resolve("bad-header-source"));
        final Path trace = real.resolve("traces/d1-real.jsonl");
        final List<String> lines = Files.readAllLines(trace);
        final JsonObject header = JsonParser.parseString(lines.getFirst()).getAsJsonObject();
        header.add("sourceSha256", new JsonObject());
        lines.set(0, header.toString());
        Files.write(trace, lines, StandardCharsets.UTF_8);
        ReplayFixture.refreshTraceDigest(real, "traces/d1-real.jsonl");

        final String message = failureOf(real, ReplayFixture.RULES_KEY);
        assertTrue(message.contains("sourceSha256") && message.contains("string"), message);
    }

    @Test
    public void topologyLabelsAndFormingCoordinatesAreOutcomeBoundBeforeFamilyFiltering() throws IOException {
        for (final boolean nullMember : List.of(false, true)) {
            for (final String damage : List.of("empty-labels", "foreign-label", "duplicate-label", "wrong-span",
                    "missing-start", "missing-end", "fractional-start", "overflow-end", "missing-explanation",
                    "null-explanation", "numeric-explanation", "nonforming-bounds", "negative-start", "reversed-bounds",
                    "future-end", "invisible-start", "unconfirmed-end")) {
                final Path run = ReplayFixture.write(temp.resolve("outcome-labels-" + nullMember + "-" + damage),
                        options -> {
                            options.nullTrace = nullMember;
                            return options;
                        });
                final String relative = "traces/d1-" + (nullMember ? "null" : "real") + ".jsonl";
                final Path trace = run.resolve(relative);
                final List<String> lines = Files.readAllLines(trace);
                final int line = firstRulesCandidateLine(lines);
                final JsonObject record = JsonParser.parseString(lines.get(line)).getAsJsonObject();
                if (List.of("negative-start", "reversed-bounds", "future-end", "invisible-start", "unconfirmed-end")
                        .contains(damage)) {
                    record.addProperty("status", "FORMING");
                    record.addProperty("direction", "BULLISH");
                    record.add("candidates", new JsonArray());
                    record.addProperty("formingStartBarIndex", 0);
                    record.addProperty("formingEndBarIndex", 20);
                    ReplayFixture.refreshIdentity(record);
                }
                final String field = switch (damage) {
                case "empty-labels" -> {
                    record.add("labels", new JsonArray());
                    yield "labels";
                }
                case "foreign-label", "wrong-span" -> {
                    record.getAsJsonArray("labels")
                            .set(0, new JsonPrimitive("wrong-span".equals(damage) ? "BULLISH:0-21" : "foreign"));
                    yield "labels";
                }
                case "duplicate-label" -> {
                    record.getAsJsonArray("labels").add(record.getAsJsonArray("labels").get(0));
                    yield "labels";
                }
                case "missing-start", "missing-end" -> {
                    record.remove("missing-start".equals(damage) ? "formingStartBarIndex" : "formingEndBarIndex");
                    yield "forming";
                }
                case "fractional-start" -> {
                    record.addProperty("formingStartBarIndex", 0.5);
                    yield "forming";
                }
                case "overflow-end" -> {
                    record.addProperty("formingEndBarIndex", 2_147_483_648L);
                    yield "forming";
                }
                case "missing-explanation" -> {
                    record.remove("explanation");
                    yield "explanation";
                }
                case "null-explanation" -> {
                    record.add("explanation", JsonNull.INSTANCE);
                    yield "explanation";
                }
                case "numeric-explanation" -> {
                    record.addProperty("explanation", 1);
                    yield "explanation";
                }
                case "nonforming-bounds", "invisible-start" -> {
                    record.addProperty("formingStartBarIndex", "invisible-start".equals(damage) ? 3 : 0);
                    yield "forming";
                }
                case "negative-start" -> {
                    record.addProperty("formingStartBarIndex", -1);
                    yield "forming";
                }
                case "reversed-bounds" -> {
                    record.addProperty("formingStartBarIndex", 20);
                    record.addProperty("formingEndBarIndex", 0);
                    yield "forming";
                }
                case "future-end" -> {
                    record.addProperty("formingEndBarIndex", record.get("asOfIndex").getAsInt() + 1);
                    yield "forming";
                }
                case "unconfirmed-end" -> {
                    record.getAsJsonArray("pivots")
                            .get(5)
                            .getAsJsonObject()
                            .addProperty("confirmationIndex", record.get("asOfIndex").getAsInt() + 1);
                    yield "forming";
                }
                default -> throw new AssertionError(damage);
                };
                lines.set(line, record.toString());
                Files.write(trace, lines, StandardCharsets.UTF_8);
                ReplayFixture.refreshTraceDigest(run, relative);
                final String traceMode = nullMember ? ReplayArtifact.TRACE_MODE_NULL_MEMBER
                        : ReplayArtifact.TRACE_MODE_REAL;
                for (final String key : List.of(ReplayFixture.RULES_KEY, ReplayFixture.TOPOLOGY_KEY)) {
                    final ReplayArtifactException rejected = assertThrows(ReplayArtifactException.class,
                            () -> ReplaySession.open(ReplayArtifact.open(run), key, traceMode, 120, 8));
                    assertTrue(
                            rejected.getMessage().contains(field)
                                    && rejected.getMessage().contains("line " + (line + 1))
                                    && rejected.getMessage().contains("regenerate"),
                            damage + ": " + rejected.getMessage());
                }
                for (final boolean keep : List.of(false, true)) {
                    final ReplayArtifactException rejected = assertThrows(ReplayArtifactException.class,
                            () -> ReplayTraceIndex.scan(trace, relative, family -> keep, Map.of(),
                                    "labelStabilityJaccard"));
                    assertTrue(rejected.getMessage().contains(field), damage + ": " + rejected.getMessage());
                }
            }
        }
    }

    @Test
    public void bijectiveRelabelingCannotManufactureApparentlyUnchangedStabilitySupport() throws IOException {
        final Path run = ReplayFixture.write(temp.resolve("bijective-label-remap"));
        final String relative = "traces/d1-real.jsonl";
        final Path trace = run.resolve(relative);
        final List<String> lines = Files.readAllLines(trace);
        int changed = 0;
        for (int i = 1; i < lines.size() - 1; i++) {
            final JsonObject record = JsonParser.parseString(lines.get(i)).getAsJsonObject();
            if (!"topology".equals(record.get("kind").getAsString())) {
                continue;
            }
            final JsonArray labels = record.getAsJsonArray("labels");
            for (int label = 0; label < labels.size(); label++) {
                labels.set(label, new JsonPrimitive("forged:" + labels.get(label).getAsString()));
                changed++;
            }
            lines.set(i, record.toString());
        }
        assertTrue(changed > 0, "prefixing every label is a bijection, preserving every Jaccard intersection/union");
        Files.write(trace, lines, StandardCharsets.UTF_8);
        ReplayFixture.refreshTraceDigest(run, relative);
        for (final boolean keep : List.of(false, true)) {
            final ReplayArtifactException rejected = assertThrows(ReplayArtifactException.class,
                    () -> ReplayTraceIndex.scan(trace, relative, family -> keep, Map.of(), "labelStabilityJaccard"));
            assertTrue(rejected.getMessage().contains("labels do not match"), rejected.getMessage());
        }
    }

    @Test
    public void structuralAlternativeOutcomesAndLabelsMatchCapturedWindowsInBothDirections() throws IOException {
        final Path run = ReplayFixture.write(temp.resolve("alternative-label-header"));
        final String header = Files.readAllLines(run.resolve("traces/d1-real.jsonl")).getFirst();
        for (final String grammar : List.of("3+3", "5+5")) {
            for (final boolean bullish : List.of(false, true)) {
                for (final String shape : List.of("insufficient", "no-match", "forming", "flat-partial", "complete",
                        "ambiguous-partial", "ambiguous-complete")) {
                    final int[] prices = switch (shape) {
                    case "insufficient" -> new int[] { 0 };
                    case "no-match" -> new int[] { 0, 4 };
                    case "forming" -> "3+3".equals(grammar) ? new int[] { 0, 4, 2, 6 } : new int[] { 0, 4, 2, 6, 3, 8 };
                    case "flat-partial" ->
                        "3+3".equals(grammar) ? new int[] { 0, 4, 2, 6, 6 } : new int[] { 0, 4, 2, 6, 3, 8, 8 };
                    case "complete" -> "3+3".equals(grammar) ? new int[] { 0, 4, 2, 6, 1, 5, -1 }
                            : new int[] { 0, 4, 2, 6, 3, 8, 1, 7, 0, 6, -1 };
                    case "ambiguous-partial" ->
                        "3+3".equals(grammar) ? new int[] { 0, 4, 2, 6, 1 } : new int[] { 0, 4, 2, 6, 3, 8, 1 };
                    case "ambiguous-complete" -> "3+3".equals(grammar) ? new int[] { 0, 4, 2, 6, 1, 5, -1, 7 }
                            : new int[] { 0, 4, 2, 6, 3, 8, 1, 7, 0, 6, -1, 9 };
                    default -> throw new AssertionError(shape);
                    };
                    final JsonObject record = ReplayFixture.competingRecord(59);
                    record.addProperty("grammar", grammar);
                    record.addProperty("mode", "competing-" + grammar);
                    final JsonArray pivots = new JsonArray();
                    for (int i = 0; i < prices.length; i++) {
                        final JsonObject pivot = new JsonObject();
                        pivot.addProperty("index", i);
                        pivot.addProperty("confirmationIndex", i);
                        pivot.addProperty("type", (i % 2 == 0) == bullish ? "LOW" : "HIGH");
                        pivot.addProperty("price", Integer.toString(bullish ? prices[i] : -prices[i]));
                        pivots.add(pivot);
                    }
                    record.add("pivots", pivots);
                    final String direction = bullish ? "BULLISH" : "BEARISH";
                    final String opposite = bullish ? "BEARISH" : "BULLISH";
                    final JsonArray labels = new JsonArray();
                    switch (shape) {
                    case "insufficient" -> {
                        record.addProperty("status", "insufficient-history");
                        labels.add("insufficient-history");
                    }
                    case "no-match" -> {
                        record.addProperty("status", "no-match");
                        labels.add("no-match");
                    }
                    case "forming", "flat-partial" -> {
                        record.addProperty("status", "forming");
                        labels.add(direction + ":0-" + (prices.length - 1));
                    }
                    case "complete" -> {
                        record.addProperty("status", "complete");
                        labels.add("0-" + (prices.length - 1));
                    }
                    case "ambiguous-partial" -> {
                        record.addProperty("status", "ambiguous");
                        labels.add(direction + ":0-" + (prices.length - 1));
                        labels.add(opposite + ":1-" + (prices.length - 1));
                    }
                    case "ambiguous-complete" -> {
                        record.addProperty("status", "ambiguous");
                        labels.add("0-" + (prices.length - 2));
                        labels.add("1-" + (prices.length - 1));
                    }
                    default -> throw new AssertionError(shape);
                    }
                    record.add("labels", labels);
                    final String name = grammar + "-" + bullish + "-" + shape;
                    final Path trace = singleRecordTrace(name, header, record);
                    for (final boolean keep : List.of(false, true)) {
                        assertNotNull(
                                ReplayTraceIndex.scan(trace, name, family -> keep, Map.of(), "labelStabilityJaccard"));
                    }
                    record.addProperty("status",
                            "no-match".equals(record.get("status").getAsString()) ? "complete" : "no-match");
                    singleRecordTrace(name, header, record);
                    for (final boolean keep : List.of(false, true)) {
                        final ReplayArtifactException rejected = assertThrows(ReplayArtifactException.class,
                                () -> ReplayTraceIndex.scan(trace, name, family -> keep, Map.of(),
                                        "labelStabilityJaccard"));
                        assertTrue(rejected.getMessage().contains("status"), name + ": " + rejected.getMessage());
                    }
                }
            }
        }
    }

    @Test
    public void changePointLabelsBindOnlyItsSupportedRecordedOutcomes() throws IOException {
        final Path run = ReplayFixture.write(temp.resolve("baseline-label-header"));
        final String header = Files.readAllLines(run.resolve("traces/d1-real.jsonl")).getFirst();
        for (final String status : List.of("insufficient-history", "no-match", "complete")) {
            final JsonObject record = ReplayFixture.competingRecord(59);
            record.addProperty("grammar", "change-point-baseline");
            record.addProperty("mode", "competing-change-point-baseline");
            record.addProperty("status", status);
            record.add("pivots", new JsonArray());
            final JsonArray labels = new JsonArray();
            labels.add(switch (status) {
            case "insufficient-history" -> "insufficient-history";
            case "no-match" -> "stable";
            default -> "change@59";
            });
            record.add("labels", labels);
            final Path trace = singleRecordTrace("baseline-" + status, header, record);
            for (final boolean keep : List.of(false, true)) {
                assertNotNull(
                        ReplayTraceIndex.scan(trace, "baseline", family -> keep, Map.of(), "labelStabilityJaccard"));
            }
            labels.add(labels.get(0));
            singleRecordTrace("baseline-" + status, header, record);
            for (final boolean keep : List.of(false, true)) {
                final ReplayArtifactException rejected = assertThrows(ReplayArtifactException.class,
                        () -> ReplayTraceIndex.scan(trace, "baseline", family -> keep, Map.of(),
                                "labelStabilityJaccard"));
                assertTrue(rejected.getMessage().contains("duplicates a label"), rejected.getMessage());
            }
        }
        for (final String status : List.of("forming", "ambiguous")) {
            final JsonObject record = ReplayFixture.competingRecord(59);
            record.addProperty("grammar", "change-point-baseline");
            record.addProperty("status", status);
            record.add("pivots", new JsonArray());
            final Path trace = singleRecordTrace("invalid-baseline-" + status, header, record);
            final ReplayArtifactException rejected = assertThrows(ReplayArtifactException.class,
                    () -> ReplayTraceIndex.scan(trace, "baseline", family -> false, Map.of(), "labelStabilityJaccard"));
            assertTrue(rejected.getMessage().contains("status"), rejected.getMessage());
        }
    }

    private Path singleRecordTrace(final String name, final String header, final JsonObject record) throws IOException {
        final Path trace = temp.resolve(name + ".jsonl");
        Files.write(trace, List.of(header, record.toString(), "{\"complete\":true,\"records\":1}"),
                StandardCharsets.UTF_8);
        return trace;
    }
}

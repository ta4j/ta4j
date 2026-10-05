/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.charting.replay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
        Files.writeString(trace, Files.readString(trace).replace("\"records\":150", "\"records\":149"),
                StandardCharsets.UTF_8);

        assertTrue(failureOf(run, ReplayFixture.RULES_KEY).contains("footer declares 149"));
    }

    @Test
    void malformedFooterIsRejectedAsArtifactError() throws IOException {
        final String footer = "{\"complete\":true,\"records\":150}";
        for (final String replacement : List.of("{\"complete\":[],\"records\":150}", "{\"complete\":true}",
                "{\"complete\":true,\"records\":\"150\"}")) {
            final Path run = ReplayFixture.write(temp.resolve("run-" + Math.abs(replacement.hashCode())));
            final Path trace = run.resolve("traces/d1-real.jsonl");
            final String original = Files.readString(trace);
            assertTrue(original.contains(footer), original);
            Files.writeString(trace, original.replace(footer, replacement), StandardCharsets.UTF_8);

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
    void nullBlockLengthMayBeEmpty() throws IOException {
        final Path run = ReplayFixture.write(temp.resolve("run"));
        final Path csv = run.resolve("comparisons.csv");
        Files.writeString(csv, Files.readString(csv).replace(",20,0.5", ",,0.5"), StandardCharsets.UTF_8);

        assertEquals(0, ReplayArtifact.open(run).comparison(ReplayFixture.RULES_KEY).nullBlockLength());
    }

    @Test
    void recordsThatReachPastTheRetainedBarsAreRejected() throws IOException {
        final Path run = ReplayFixture.write(temp.resolve("run"));
        final Path trace = run.resolve("traces/d1-real.jsonl");
        final String text = Files.readString(trace);
        Files.writeString(trace, text.replace("\"asOfIndex\":59", "\"asOfIndex\":590"), StandardCharsets.UTF_8);

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
        assertTrue(failureOf(revision, ReplayFixture.RULES_KEY).contains("revision"));

        final Path block = ReplayFixture.write(temp.resolve("block"));
        final Path blockTrace = block.resolve("traces/d1-real.jsonl");
        Files.writeString(blockTrace,
                Files.readString(blockTrace).replaceFirst("\"nullBlockLength\":-1", "\"nullBlockLength\":20"),
                StandardCharsets.UTF_8);
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
        final String missingFailure = failureOf(missing, ReplayFixture.RULES_KEY);
        assertTrue(missingFailure.contains("candidates is missing or not an array"), missingFailure);
        assertTrue(missingFailure.contains("line 2"), missingFailure);

        final Path nonArray = ReplayFixture.write(temp.resolve("nonarray"));
        final Path nonArrayTrace = nonArray.resolve("traces/d1-real.jsonl");
        Files.writeString(nonArrayTrace,
                Files.readString(nonArrayTrace).replace("\"candidates\":[]", "\"candidates\":{}"),
                StandardCharsets.UTF_8);
        assertTrue(failureOf(nonArray, ReplayFixture.RULES_KEY).contains("candidates is missing or not an array"));

        final Path rules = ReplayFixture.write(temp.resolve("rules"));
        final Path rulesTrace = rules.resolve("traces/d1-real.jsonl");
        Files.writeString(rulesTrace, Files.readString(rulesTrace).replace("\"rules\":[", "\"rulez\":["),
                StandardCharsets.UTF_8);
        assertTrue(failureOf(rules, ReplayFixture.RULES_KEY).contains("rules is missing or not an array"));
    }
}

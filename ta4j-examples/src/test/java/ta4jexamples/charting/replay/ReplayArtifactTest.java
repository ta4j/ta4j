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
                "{\"complete\":true,\"records\":\"150\"}", "{\"complete\":true,\"records\":150.5}",
                "{\"complete\":true,\"records\":150e2147483648}")) {
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
        ReplayFixture.refreshComparisonsDigest(run);

        assertEquals(0, ReplayArtifact.open(run).comparison(ReplayFixture.RULES_KEY).nullBlockLength());
    }

    @Test
    public void comparisonRowsRequireTheRecordedContentDigest() throws IOException {
        for (final String corruption : List.of("foreign-comparisons", "missing-digest")) {
            final Path run = ReplayFixture.write(temp.resolve(corruption));
            if ("foreign-comparisons".equals(corruption)) {
                final Path csv = run.resolve("comparisons.csv");
                Files.writeString(csv, Files.readString(csv).replace(",0.5", ",0.6"), StandardCharsets.UTF_8);
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

                final String message = failureOf(run, ReplayFixture.RULES_KEY);
                assertTrue(message.contains("rule".equals(scope) ? ".state" : "status")
                        && message.contains("line " + (changedLine + 1)), scope + "=" + value + ": " + message);
            }
        }
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
                    candidates.get(0)
                            .getAsJsonObject()
                            .getAsJsonArray("rules")
                            .get(0)
                            .getAsJsonObject()
                            .addProperty("state", state);
                    lines.set(line, record.toString());
                    asOf = record.get("asOfIndex").getAsInt();
                    break;
                }
            }
            assertTrue(asOf >= 0, state);
            Files.write(trace, lines, StandardCharsets.UTF_8);

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
        for (final String field : List.of("artifactSchemaVersion", "status", "revision", "datasets", "dataset-entry",
                "traces", "trace-entry", "coverage", "priceBars", "source", "id", "asset", "identity", "fingerprint",
                "missing-identity", "missing-fingerprint", "empty-fingerprint", "number-fingerprint", "rows")) {
            final Path run = ReplayFixture.write(temp.resolve("malformed-" + field));
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
    public void malformedTraceIdentityContainersAndSourceDigestsHaveArtifactDiagnostics() throws IOException {
        final Path selected = ReplayFixture.write(temp.resolve("bad-trace-config"), options -> {
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

        final String message = failureOf(real, ReplayFixture.RULES_KEY);
        assertTrue(message.contains("sourceSha256") && message.contains("string"), message);
    }
}

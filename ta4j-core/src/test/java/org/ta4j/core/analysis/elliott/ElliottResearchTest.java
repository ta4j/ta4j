/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
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

    @TempDir
    static Path shared;

    @TempDir
    Path work;

    private static Path smokeRun;

    /** Captured result of one launcher invocation. */
    private record Result(int code, String out, String err) {
    }

    @BeforeAll
    static void runSmoke() {
        smokeRun = shared.resolve("smoke");
        final Result result = launch("run", "smoke", "--out", smokeRun.toString());
        assertEquals(0, result.code(), result.err());
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
        final Path stale = work.resolve("stale-smoke");
        final Result mismatch = launch("run", "smoke", "--expect-fingerprint", "0" + fingerprint, "--out",
                stale.toString());
        assertEquals(1, mismatch.code());
        assertTrue(mismatch.err().contains("the recipe changed since the run"), mismatch.err());
        assertFalse(Files.exists(stale));
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
        final Path other = work.resolve("foreign");
        Files.createDirectories(other);
        Files.writeString(other.resolve("precious.txt"), "keep");

        final Result result = launch("run", "smoke", "--out", other.toString(), "--overwrite");
        assertEquals(1, result.code());
        assertTrue(result.err().contains("not a previous research run"), result.err());
        assertEquals("keep", Files.readString(other.resolve("precious.txt")));
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
        assertTrue(coverage.contains("narrower than requested"), coverage);
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
        final JsonArray array = new JsonArray();
        for (int index = 0; index < days; index++) {
            final LocalDate date = first.plusDays(index);
            if (!keep.test(date)) {
                continue;
            }
            final double close = 100.0d + 15.0d * StrictMath.sin(0.2d * index) + 6.0d * StrictMath.sin(0.7d * index);
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
    void traceFooterWithNonBooleanCompleteIsCorrupt() throws Exception {
        final Path copy = work.resolve("bad-footer");
        copyTree(smokeRun, copy);
        final Path trace = copy.resolve("traces/smoke-real.jsonl");
        final List<String> lines = new java.util.ArrayList<>(Files.readAllLines(trace, StandardCharsets.UTF_8));
        final JsonObject footer = JsonParser.parseString(lines.get(lines.size() - 1)).getAsJsonObject();
        footer.add("complete", new JsonArray());
        lines.set(lines.size() - 1, footer.toString());
        Files.write(trace, lines, StandardCharsets.UTF_8);

        final Result result = launch("inspect", copy.toString(), OCCUPANCY_KEY);
        assertEquals(2, result.code());
        assertTrue(result.err().contains("footer complete flag [] is not a boolean"), result.err());
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
            assertTrue(result.err().contains("was captured for"), result.err());
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

    private static JsonObject exploreCoverage(final Path out) throws IOException {
        return readJson(out.resolve("run.json")).getAsJsonArray("datasets")
                .get(0)
                .getAsJsonObject()
                .getAsJsonObject("coverage");
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
}

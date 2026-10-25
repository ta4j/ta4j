/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.analysis.elliott.ElliottResearchFixtures.assertIncomplete;
import static org.ta4j.core.analysis.elliott.ElliottResearchFixtures.copyTree;
import static org.ta4j.core.analysis.elliott.ElliottResearchFixtures.csvRows;
import static org.ta4j.core.analysis.elliott.ElliottResearchFixtures.editedCopy;
import static org.ta4j.core.analysis.elliott.ElliottResearchFixtures.launch;
import static org.ta4j.core.analysis.elliott.ElliottResearchFixtures.readJson;
import static org.ta4j.core.analysis.elliott.ElliottResearchFixtures.summarizeAfterEditing;
import static org.ta4j.core.analysis.elliott.ElliottResearchFixtures.summarizeExpectingNoSummary;
import static org.ta4j.core.analysis.elliott.ElliottResearchFixtures.symlinkOrSkip;
import static org.ta4j.core.analysis.elliott.ElliottResearchFixtures.toyRecipe;
import static org.ta4j.core.analysis.elliott.ElliottResearchFixtures.writeCandles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import org.ta4j.core.analysis.elliott.ElliottResearchFixtures.Result;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Behavioural tests for the Elliott research {@code explore} recipes:
 * partitions, calibration, hierarchy and families, and the readers that refuse
 * damaged runs.
 */
class ElliottResearchExploreTest {

    @TempDir
    static Path shared;

    @TempDir
    Path work;

    private static Path calibratedRun;

    @BeforeAll
    static void runFixtures() throws IOException {
        final Path candles = shared.resolve("calibrated-candles.json");
        writeCandles(candles, LocalDate.of(2020, 1, 1), 366, date -> true);
        final Path recipe = shared.resolve("calibrated-recipe.json");
        Files.writeString(recipe, calibrationRecipe("cal", "{\"horizon\":5,\"minGroups\":1}"));
        calibratedRun = shared.resolve("calibrated");
        final Result calibrated = launch("run", "explore", "--source", candles.toString(), "--recipe",
                recipe.toString(), "--out", calibratedRun.toString());
        assertEquals(0, calibrated.code(), calibrated.err());
    }

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

    /**
     * One way of damaging a published artifact and the reason readers must give.
     */
    private record Damage(String name, UnaryOperator<String> edit, String reason) {
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
        final Result result = summarizeAfterEditing(work, calibratedRun, "calibration-summary-header-only",
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
        final Path copy = editedCopy(work, calibratedRun, "calibration-no-records", "run.json", text -> {
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
        final Path copy = editedCopy(work, calibratedRun, "calibration-partial-records", "run.json", text -> {
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

    /**
     * Both readers refuse the calibrated run once {@code artifact} is damaged, and
     * inspect prints nothing before refusing.
     */
    private void assertRefusedBySummarizeAndInspect(final String artifact, final Damage damage) throws IOException {
        final Path copy = editedCopy(work, calibratedRun, artifact + "-" + damage.name(), artifact, damage.edit());

        final Result inspected = launch("inspect", copy.toString(), keyOfPartition(copy, "holdout"));
        assertIncomplete(inspected, damage.reason());
        assertEquals("", inspected.out(), "inspect must not print partial evidence: " + copy.getFileName());
        assertIncomplete(summarizeExpectingNoSummary(copy), damage.reason());
    }
}

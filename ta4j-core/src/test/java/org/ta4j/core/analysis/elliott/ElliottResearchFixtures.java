/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.util.function.IntToDoubleFunction;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Shared launcher fixtures for the Elliott research tests: running the launcher
 * in process, building recipes and candles, and rewriting published artifacts.
 */
final class ElliottResearchFixtures {

    private ElliottResearchFixtures() {
    }

    /** Captured result of one launcher invocation. */
    record Result(int code, String out, String err) {
    }

    static final String OCCUPANCY_KEY = "smoke|h1|topology-only|fractal-w5|calibration|completeOccupancyRate|b20";

    static Result launch(final String... args) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final ByteArrayOutputStream err = new ByteArrayOutputStream();
        final int code;
        try (PrintStream outStream = new PrintStream(out, true, StandardCharsets.UTF_8);
                PrintStream errStream = new PrintStream(err, true, StandardCharsets.UTF_8)) {
            code = ElliottResearch.execute(args, outStream, errStream);
        }
        return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    static JsonObject readJson(final Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    static void copyTree(final Path from, final Path to) throws IOException {
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

    static String replayLine(final String inspectOutput) {
        return inspectOutput.lines().filter(line -> line.startsWith("Replay: ")).findFirst().orElseThrow();
    }

    static void writeCandles(final Path file, final LocalDate first, final int days,
            final java.util.function.Predicate<LocalDate> keep) throws IOException {
        writeCandles(file, first, days, keep,
                index -> 100.0d + 15.0d * StrictMath.sin(0.2d * index) + 6.0d * StrictMath.sin(0.7d * index));
    }

    static void writeCandles(final Path file, final LocalDate first, final int days,
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

    static String toyRecipe(final String datasetId) {
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

    static void symlinkOrSkip(final Path link, final Path target) {
        try {
            Files.createSymbolicLink(link, target);
        } catch (final UnsupportedOperationException | IOException unsupported) {
            assumeTrue(false, "symbolic links are unavailable: " + unsupported);
        }
    }

    /**
     * A copy of {@code source} in which {@code artifact} is rewritten by
     * {@code edit}.
     */
    static Path editedCopy(final Path work, final Path source, final String name, final String artifact,
            final UnaryOperator<String> edit) throws IOException {
        final Path copy = work.resolve(name);
        copyTree(source, copy);
        Files.writeString(copy.resolve(artifact), edit.apply(Files.readString(copy.resolve(artifact))),
                StandardCharsets.UTF_8);
        return copy;
    }

    static Result summarizeAfterEditing(final Path work, final Path source, final String name, final String artifact,
            final UnaryOperator<String> edit) throws IOException {
        return summarizeExpectingNoSummary(editedCopy(work, source, name, artifact, edit));
    }

    /** Summarizes a run that must be refused, after dropping its stale summary. */
    static Result summarizeExpectingNoSummary(final Path copy) throws IOException {
        Files.delete(copy.resolve("summary.md"));
        final Result result = launch("summarize", copy.toString());
        assertFalse(Files.exists(copy.resolve("summary.md")), "an incomplete run must not get a summary");
        return result;
    }

    static void assertIncomplete(final Result result, final String reason) {
        assertEquals(2, result.code(), result.err());
        assertTrue(result.err().contains(reason), result.err());
        assertTrue(result.err().contains("the run is incomplete, rerun the recipe"), result.err());
    }

    static long csvRows(final Path run, final String name) {
        return ElliottResearchReport.parseCsv(run.resolve(name)).size() - 1L;
    }
}

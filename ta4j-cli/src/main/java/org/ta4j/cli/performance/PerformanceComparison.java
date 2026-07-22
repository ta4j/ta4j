/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.performance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Compares two {@link PerformanceExperimentRunner} artifact directories.
 *
 * <p>
 * The comparison validates that experiment metadata and checksums match before
 * reporting timing deltas. A checksum mismatch fails the comparison because it
 * means the baseline and candidate did not exercise equivalent behavior.
 *
 * @since 0.23.1
 */
public final class PerformanceComparison {

    static final String COMPARISON_FILE = "comparison.json";
    static final String SUMMARY_FILE = "summary.md";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.00");
    private static final double UNBOUNDED_DELTA_PCT = Double.MAX_VALUE;

    private PerformanceComparison() {
    }

    /**
     * Compares two experiment artifact directories.
     *
     * @param baseDir          baseline artifact directory
     * @param candidateDir     candidate artifact directory
     * @param outputDir        comparison artifact directory
     * @param maxRegressionPct maximum allowed median regression percentage
     * @return comparison JSON
     * @throws IOException when artifacts cannot be read or written
     * @since 0.23.1
     */
    public static JsonObject compare(Path baseDir, Path candidateDir, Path outputDir, double maxRegressionPct)
            throws IOException {
        JsonObject base = readPerformanceJson(baseDir);
        JsonObject candidate = readPerformanceJson(candidateDir);
        Files.createDirectories(outputDir);

        boolean metadataMatch = base.get("experimentId")
                .getAsString()
                .equals(candidate.get("experimentId").getAsString())
                && base.get("repetitions").getAsInt() == candidate.get("repetitions").getAsInt()
                && base.get("warmups").getAsInt() == candidate.get("warmups").getAsInt()
                && base.get("barCounts").equals(candidate.get("barCounts"))
                && base.get("scenarioIds").equals(candidate.get("scenarioIds"));
        if (!metadataMatch) {
            throw new IllegalStateException("Cannot compare performance artifacts with different experiment inputs");
        }

        Map<String, JsonObject> baseResults = resultMap(base);
        Map<String, JsonObject> candidateResults = resultMap(candidate);
        if (!baseResults.keySet().equals(candidateResults.keySet())) {
            throw new IllegalStateException("Cannot compare performance artifacts with different result cells");
        }

        JsonArray cells = new JsonArray();
        boolean checksumMatch = true;
        boolean regressionWithinThreshold = true;
        Map<String, Long> previousBaseMedianByScenario = new LinkedHashMap<>();
        Map<String, Long> previousCandidateMedianByScenario = new LinkedHashMap<>();
        Map<String, Integer> previousBarCountByScenario = new LinkedHashMap<>();
        for (Map.Entry<String, JsonObject> entry : baseResults.entrySet()) {
            JsonObject baseResult = entry.getValue();
            JsonObject candidateResult = candidateResults.get(entry.getKey());
            String scenarioId = baseResult.get("scenarioId").getAsString();
            int barCount = baseResult.get("barCount").getAsInt();
            long baseChecksum = baseResult.get("checksum").getAsLong();
            long candidateChecksum = candidateResult.get("checksum").getAsLong();
            boolean cellChecksumMatch = baseChecksum == candidateChecksum
                    && baseResult.get("checksumStable").getAsBoolean()
                    && candidateResult.get("checksumStable").getAsBoolean();
            checksumMatch &= cellChecksumMatch;

            JsonObject baseStats = baseResult.getAsJsonObject("stats");
            JsonObject candidateStats = candidateResult.getAsJsonObject("stats");
            long baseMedian = baseStats.get("medianNanos").getAsLong();
            long candidateMedian = candidateStats.get("medianNanos").getAsLong();
            double medianDeltaPct = percentDelta(baseMedian, candidateMedian);
            if (medianDeltaPct > maxRegressionPct) {
                regressionWithinThreshold = false;
            }

            JsonObject cell = new JsonObject();
            cell.addProperty("scenarioId", scenarioId);
            cell.addProperty("barCount", barCount);
            cell.addProperty("checksumMatch", cellChecksumMatch);
            cell.addProperty("baseChecksum", baseChecksum);
            cell.addProperty("candidateChecksum", candidateChecksum);
            cell.addProperty("baseMedianNanos", baseMedian);
            cell.addProperty("candidateMedianNanos", candidateMedian);
            cell.addProperty("medianDeltaPct", medianDeltaPct);
            cell.addProperty("baseOperationsPerSecond", baseStats.get("operationsPerSecond").getAsDouble());
            cell.addProperty("candidateOperationsPerSecond", candidateStats.get("operationsPerSecond").getAsDouble());
            cell.add("scaling",
                    scalingShape(previousBarCountByScenario.get(scenarioId),
                            previousBaseMedianByScenario.get(scenarioId),
                            previousCandidateMedianByScenario.get(scenarioId), barCount, baseMedian, candidateMedian));
            cells.add(cell);
            previousBarCountByScenario.put(scenarioId, barCount);
            previousBaseMedianByScenario.put(scenarioId, baseMedian);
            previousCandidateMedianByScenario.put(scenarioId, candidateMedian);
        }

        JsonObject comparison = new JsonObject();
        comparison.addProperty("schemaVersion", 1);
        comparison.addProperty("experimentId", base.get("experimentId").getAsString());
        comparison.addProperty("baseRef", base.get("gitRef").getAsString());
        comparison.addProperty("candidateRef", candidate.get("gitRef").getAsString());
        comparison.addProperty("metadataMatch", metadataMatch);
        comparison.addProperty("checksumMatch", checksumMatch);
        comparison.addProperty("maxRegressionPct", maxRegressionPct);
        comparison.addProperty("regressionWithinThreshold", regressionWithinThreshold);
        comparison.add("cells", cells);

        Files.writeString(outputDir.resolve(COMPARISON_FILE), GSON.toJson(comparison) + System.lineSeparator(),
                StandardCharsets.UTF_8);
        Files.writeString(outputDir.resolve(SUMMARY_FILE), summary(comparison), StandardCharsets.UTF_8);
        if (!checksumMatch) {
            throw new IllegalStateException("Cannot compare performance artifacts with mismatched checksums");
        }
        if (!regressionWithinThreshold) {
            throw new IllegalStateException("Performance regression exceeded threshold");
        }
        return comparison;
    }

    private static JsonObject readPerformanceJson(Path dir) throws IOException {
        return JsonParser
                .parseString(Files.readString(dir.resolve(PerformanceExperimentRunner.PERFORMANCE_FILE),
                        StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    private static Map<String, JsonObject> resultMap(JsonObject root) {
        Map<String, JsonObject> byCell = new LinkedHashMap<>();
        JsonArray results = root.getAsJsonArray("results");
        for (int i = 0; i < results.size(); i++) {
            JsonObject result = results.get(i).getAsJsonObject();
            String key = result.get("scenarioId").getAsString() + ":" + result.get("barCount").getAsInt();
            JsonObject previous = byCell.putIfAbsent(key, result);
            if (previous != null) {
                throw new IllegalStateException("Duplicate result cell: " + key);
            }
        }
        return byCell;
    }

    private static double percentDelta(long base, long candidate) {
        if (base == 0L) {
            return candidate == 0L ? 0d : UNBOUNDED_DELTA_PCT;
        }
        return (candidate - base) * 100d / base;
    }

    private static JsonObject scalingShape(Integer previousBarCount, Long previousBaseMedian,
            Long previousCandidateMedian, int barCount, long baseMedian, long candidateMedian) {
        JsonObject scaling = new JsonObject();
        if (previousBarCount == null || previousBaseMedian == null || previousCandidateMedian == null) {
            scaling.add("barCountScale", JsonNull.INSTANCE);
            scaling.add("baseMedianScale", JsonNull.INSTANCE);
            scaling.add("candidateMedianScale", JsonNull.INSTANCE);
            return scaling;
        }
        scaling.addProperty("barCountScale", ratio(previousBarCount, barCount));
        scaling.addProperty("baseMedianScale", ratio(previousBaseMedian, baseMedian));
        scaling.addProperty("candidateMedianScale", ratio(previousCandidateMedian, candidateMedian));
        return scaling;
    }

    private static double ratio(long previous, long current) {
        if (previous == 0L) {
            return 0d;
        }
        return current / (double) previous;
    }

    private static String summary(JsonObject comparison) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Performance Comparison").append(System.lineSeparator()).append(System.lineSeparator());
        builder.append("- Experiment: `")
                .append(comparison.get("experimentId").getAsString())
                .append("`")
                .append(System.lineSeparator());
        builder.append("- Base: `")
                .append(comparison.get("baseRef").getAsString())
                .append("`")
                .append(System.lineSeparator());
        builder.append("- Candidate: `")
                .append(comparison.get("candidateRef").getAsString())
                .append("`")
                .append(System.lineSeparator());
        builder.append("- Checksum match: `")
                .append(comparison.get("checksumMatch").getAsBoolean())
                .append("`")
                .append(System.lineSeparator());
        builder.append("- Regression threshold: `")
                .append(comparison.get("maxRegressionPct").getAsDouble())
                .append("%`")
                .append(System.lineSeparator());
        builder.append("- Regression within threshold: `")
                .append(comparison.get("regressionWithinThreshold").getAsBoolean())
                .append("`")
                .append(System.lineSeparator())
                .append(System.lineSeparator());
        builder.append("| Scenario | Bars | Base median ms | Candidate median ms | Delta % | Candidate scale |")
                .append(System.lineSeparator());
        builder.append("| --- | ---: | ---: | ---: | ---: | ---: |").append(System.lineSeparator());

        JsonArray cells = comparison.getAsJsonArray("cells");
        for (int i = 0; i < cells.size(); i++) {
            JsonObject cell = cells.get(i).getAsJsonObject();
            builder.append("| `")
                    .append(cell.get("scenarioId").getAsString())
                    .append("` | ")
                    .append(cell.get("barCount").getAsInt())
                    .append(" | ")
                    .append(formatMillis(cell.get("baseMedianNanos").getAsLong()))
                    .append(" | ")
                    .append(formatMillis(cell.get("candidateMedianNanos").getAsLong()))
                    .append(" | ")
                    .append(formatPercent(cell.get("medianDeltaPct").getAsDouble()))
                    .append(" | ")
                    .append(formatScale(cell.getAsJsonObject("scaling").get("candidateMedianScale")))
                    .append(" |")
                    .append(System.lineSeparator());
        }
        return builder.toString();
    }

    private static String formatMillis(long nanos) {
        return DECIMAL_FORMAT.format(nanos / 1_000_000d);
    }

    private static String formatPercent(double percent) {
        if (percent == UNBOUNDED_DELTA_PCT) {
            return "Infinity";
        }
        return DECIMAL_FORMAT.format(percent);
    }

    private static String formatScale(JsonElement scale) {
        if (scale == null || scale.isJsonNull()) {
            return "-";
        }
        return DECIMAL_FORMAT.format(scale.getAsDouble());
    }

    /**
     * Artifacts from one performance comparison run.
     *
     * @param outputDir      directory containing written artifacts
     * @param comparisonJson in-memory representation of {@code comparison.json}
     * @since 0.23.1
     */
    public record ComparisonArtifacts(Path outputDir, JsonObject comparisonJson) {
    }
}

/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.performance;

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
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Compares two {@link PerformanceExperimentRunner} artifact directories.
 *
 * <p>
 * The comparison validates that experiment metadata and checksums match before
 * reporting timing deltas. A checksum mismatch fails the comparison because it
 * means the baseline and candidate did not exercise equivalent behavior.
 *
 * @since 0.22.7
 */
public final class PerformanceComparison {

    static final String COMPARISON_FILE = "comparison.json";
    static final String SUMMARY_FILE = "summary.md";

    private static final Logger LOG = LogManager.getLogger(PerformanceComparison.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.00");

    private PerformanceComparison() {
    }

    /**
     * CLI entrypoint.
     *
     * @param args command-line arguments
     * @throws Exception when comparison validation fails or artifacts cannot be
     *                   written
     */
    public static void main(String[] args) throws Exception {
        ComparisonCli cli = ComparisonCli.parse(args);
        if (cli.help()) {
            LOG.info(ComparisonCli.usage());
            return;
        }
        compare(cli.baseDir(), cli.candidateDir(), cli.outputDir(), cli.maxRegressionPct());
    }

    static JsonObject compare(Path baseDir, Path candidateDir, Path outputDir, double maxRegressionPct)
            throws IOException {
        JsonObject base = readPerformanceJson(baseDir);
        JsonObject candidate = readPerformanceJson(candidateDir);
        Files.createDirectories(outputDir);

        boolean metadataMatch = base.get("experimentId").getAsString().equals(candidate.get("experimentId").getAsString())
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
        for (Map.Entry<String, JsonObject> entry : baseResults.entrySet()) {
            JsonObject baseResult = entry.getValue();
            JsonObject candidateResult = candidateResults.get(entry.getKey());
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
            cell.addProperty("scenarioId", baseResult.get("scenarioId").getAsString());
            cell.addProperty("barCount", baseResult.get("barCount").getAsInt());
            cell.addProperty("checksumMatch", cellChecksumMatch);
            cell.addProperty("baseChecksum", baseChecksum);
            cell.addProperty("candidateChecksum", candidateChecksum);
            cell.addProperty("baseMedianNanos", baseMedian);
            cell.addProperty("candidateMedianNanos", candidateMedian);
            cell.addProperty("medianDeltaPct", medianDeltaPct);
            cell.addProperty("baseOperationsPerSecond", baseStats.get("operationsPerSecond").getAsDouble());
            cell.addProperty("candidateOperationsPerSecond", candidateStats.get("operationsPerSecond").getAsDouble());
            cells.add(cell);
        }

        if (!checksumMatch) {
            throw new IllegalStateException("Cannot compare performance artifacts with mismatched checksums");
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
        LOG.info("Performance comparison artifacts written to {}", outputDir);
        return comparison;
    }

    private static JsonObject readPerformanceJson(Path dir) throws IOException {
        return JsonParser.parseString(Files.readString(dir.resolve(PerformanceExperimentRunner.PERFORMANCE_FILE),
                StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static Map<String, JsonObject> resultMap(JsonObject root) {
        Map<String, JsonObject> byCell = new LinkedHashMap<>();
        JsonArray results = root.getAsJsonArray("results");
        for (int i = 0; i < results.size(); i++) {
            JsonObject result = results.get(i).getAsJsonObject();
            String key = result.get("scenarioId").getAsString() + ":" + result.get("barCount").getAsInt();
            byCell.put(key, result);
        }
        return byCell;
    }

    private static double percentDelta(long base, long candidate) {
        if (base == 0L) {
            return 0d;
        }
        return (candidate - base) * 100d / base;
    }

    private static String summary(JsonObject comparison) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Performance Comparison").append(System.lineSeparator()).append(System.lineSeparator());
        builder.append("- Experiment: `").append(comparison.get("experimentId").getAsString()).append("`")
                .append(System.lineSeparator());
        builder.append("- Base: `").append(comparison.get("baseRef").getAsString()).append("`")
                .append(System.lineSeparator());
        builder.append("- Candidate: `").append(comparison.get("candidateRef").getAsString()).append("`")
                .append(System.lineSeparator());
        builder.append("- Checksum match: `").append(comparison.get("checksumMatch").getAsBoolean()).append("`")
                .append(System.lineSeparator());
        builder.append("- Regression threshold: `").append(comparison.get("maxRegressionPct").getAsDouble())
                .append("%`").append(System.lineSeparator()).append(System.lineSeparator());
        builder.append("| Scenario | Bars | Base median ms | Candidate median ms | Delta % |")
                .append(System.lineSeparator());
        builder.append("| --- | ---: | ---: | ---: | ---: |").append(System.lineSeparator());

        JsonArray cells = comparison.getAsJsonArray("cells");
        for (int i = 0; i < cells.size(); i++) {
            JsonObject cell = cells.get(i).getAsJsonObject();
            builder.append("| `").append(cell.get("scenarioId").getAsString()).append("` | ")
                    .append(cell.get("barCount").getAsInt()).append(" | ")
                    .append(formatMillis(cell.get("baseMedianNanos").getAsLong())).append(" | ")
                    .append(formatMillis(cell.get("candidateMedianNanos").getAsLong())).append(" | ")
                    .append(DECIMAL_FORMAT.format(cell.get("medianDeltaPct").getAsDouble())).append(" |")
                    .append(System.lineSeparator());
        }
        return builder.toString();
    }

    private static String formatMillis(long nanos) {
        return DECIMAL_FORMAT.format(nanos / 1_000_000d);
    }

    private record ComparisonCli(Path baseDir, Path candidateDir, Path outputDir, double maxRegressionPct,
            boolean help) {

        static ComparisonCli parse(String[] args) {
            Path baseDir = null;
            Path candidateDir = null;
            Path outputDir = null;
            double maxRegressionPct = 5d;
            boolean help = false;
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                case "-h", "--help" -> help = true;
                case "--baseDir", "--base-dir" -> baseDir = Path.of(requireValue(args, ++i, arg));
                case "--candidateDir", "--candidate-dir" -> candidateDir = Path.of(requireValue(args, ++i, arg));
                case "--outputDir", "--output-dir" -> outputDir = Path.of(requireValue(args, ++i, arg));
                case "--maxRegressionPct", "--max-regression-pct" ->
                    maxRegressionPct = Double.parseDouble(requireValue(args, ++i, arg));
                default -> throw new IllegalArgumentException("Unknown argument: " + arg);
                }
            }
            if (help) {
                return new ComparisonCli(Path.of("."), Path.of("."), Path.of("."), maxRegressionPct, true);
            }
            if (baseDir == null || candidateDir == null || outputDir == null) {
                throw new IllegalArgumentException("--baseDir, --candidateDir, and --outputDir are required");
            }
            if (maxRegressionPct < 0d) {
                throw new IllegalArgumentException("--maxRegressionPct must be non-negative");
            }
            return new ComparisonCli(baseDir, candidateDir, outputDir, maxRegressionPct, false);
        }

        private static String usage() {
            return """
                    PerformanceComparison

                    Options:
                      --baseDir <dir>           Baseline artifact directory
                      --candidateDir <dir>      Candidate artifact directory
                      --outputDir <dir>         Comparison artifact directory
                      --maxRegressionPct <pct>  Median runtime regression threshold (default 5)
                    """;
        }

        private static String requireValue(String[] args, int index, String argument) {
            if (index >= args.length) {
                throw new IllegalArgumentException(argument + " requires a value");
            }
            return args[index];
        }
    }
}

/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.performance;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Generic performance experiment runner for examples-layer optimization work.
 *
 * <p>
 * The runner executes named scenarios for a registered experiment, writes
 * machine-readable JSON, and emits a short Markdown summary that can be compared
 * across git refs by {@link PerformanceComparison}.
 *
 * @since 0.22.7
 */
public final class PerformanceExperimentRunner {

    static final String PERFORMANCE_FILE = "performance.json";
    static final String SUMMARY_FILE = "summary.md";

    private static final Logger LOG = LogManager.getLogger(PerformanceExperimentRunner.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final NumberFormat INTEGER_FORMAT = NumberFormat.getIntegerInstance(Locale.US);
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.00");
    private static final int DEFAULT_REPETITIONS = 5;
    private static final int DEFAULT_WARMUPS = 1;

    private PerformanceExperimentRunner() {
    }

    /**
     * CLI entrypoint.
     *
     * @param args command-line arguments
     * @throws Exception when the experiment cannot be run or artifacts cannot be
     *                   written
     */
    public static void main(String[] args) throws Exception {
        RunArtifacts artifacts = run(args);
        LOG.info("Performance experiment artifacts written to {}", artifacts.outputDir());
    }

    static RunArtifacts run(String[] args) throws IOException {
        RunnerCli cli = RunnerCli.parse(args);
        if (cli.help()) {
            LOG.info(RunnerCli.usage());
            return new RunArtifacts(Path.of("."), new JsonObject());
        }

        PerformanceExperiment experiment = experiment(cli.experimentId());
        List<PerformanceScenario> scenarios = selectScenarios(experiment, cli.scenarioIds());
        Path outputDir = cli.outputDir().orElseGet(() -> defaultOutputDir(experiment.id())).toAbsolutePath().normalize();
        Files.createDirectories(outputDir);

        Instant startedAt = Instant.now();
        JsonArray results = new JsonArray();
        for (PerformanceScenario scenario : scenarios) {
            for (Integer barCount : cli.barCounts()) {
                ScenarioAggregation aggregation = runScenario(scenario, barCount, cli.warmups(), cli.repetitions(),
                        cli.profile());
                results.add(aggregation.toJson());
                LOG.info("{} bars={} median={} ms ops/s={}", scenario.id(), INTEGER_FORMAT.format(barCount),
                        formatMillis(aggregation.stats().medianNanos()),
                        DECIMAL_FORMAT.format(aggregation.stats().operationsPerSecond()));
            }
        }
        Instant completedAt = Instant.now();

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("experimentId", experiment.id());
        root.addProperty("description", experiment.description());
        root.addProperty("gitRef", gitRef());
        root.addProperty("startedAt", startedAt.toString());
        root.addProperty("completedAt", completedAt.toString());
        root.addProperty("repetitions", cli.repetitions());
        root.addProperty("warmups", cli.warmups());
        root.addProperty("profile", cli.profile());
        root.add("barCounts", intArray(cli.barCounts()));
        root.add("scenarioIds", stringArray(scenarios.stream().map(PerformanceScenario::id).toList()));
        root.add("host", HostTelemetry.capture().toJson());
        if (cli.profile()) {
            root.add("profileHints", profileHints(scenarios, outputDir));
        }
        root.add("results", results);

        Files.writeString(outputDir.resolve(PERFORMANCE_FILE), GSON.toJson(root) + System.lineSeparator(),
                StandardCharsets.UTF_8);
        Files.writeString(outputDir.resolve(SUMMARY_FILE), summary(root), StandardCharsets.UTF_8);
        return new RunArtifacts(outputDir, root);
    }

    private static ScenarioAggregation runScenario(PerformanceScenario scenario, int barCount, int warmups,
            int repetitions, boolean profile) {
        for (int i = 0; i < warmups; i++) {
            scenario.measure(new PerformanceScenario.Context(barCount, 0, profile));
        }

        List<PerformanceScenario.Measurement> measurements = new ArrayList<>(repetitions);
        for (int repetition = 1; repetition <= repetitions; repetition++) {
            measurements.add(scenario.measure(new PerformanceScenario.Context(barCount, repetition, profile)));
        }
        return ScenarioAggregation.from(scenario, barCount, measurements);
    }

    private static PerformanceExperiment experiment(String id) {
        if (KalmanFilterPerformanceExperiment.ID.equals(id)) {
            return new KalmanFilterPerformanceExperiment();
        }
        throw new IllegalArgumentException("Unknown experiment: " + id);
    }

    private static List<PerformanceScenario> selectScenarios(PerformanceExperiment experiment, List<String> ids) {
        Map<String, PerformanceScenario> byId = experiment.scenarios()
                .stream()
                .collect(Collectors.toMap(PerformanceScenario::id, scenario -> scenario, (left, right) -> left,
                        LinkedHashMap::new));
        List<String> selectedIds = ids.isEmpty() ? experiment.defaultScenarioIds() : ids;
        List<PerformanceScenario> selected = new ArrayList<>();
        for (String id : selectedIds) {
            PerformanceScenario scenario = byId.get(id);
            if (scenario == null) {
                throw new IllegalArgumentException("Unknown scenario for " + experiment.id() + ": " + id);
            }
            selected.add(scenario);
        }
        return selected;
    }

    private static Path defaultOutputDir(String experimentId) {
        String timestamp = Instant.now().toString().replace(':', '-');
        return Path.of(".agents", "benchmarks", "performance", experimentId, timestamp);
    }

    private static JsonArray profileHints(List<PerformanceScenario> scenarios, Path outputDir) {
        JsonArray hints = new JsonArray();
        for (PerformanceScenario scenario : scenarios) {
            JsonObject hint = new JsonObject();
            hint.addProperty("scenarioId", scenario.id());
            hint.addProperty("jfrFile", outputDir.resolve(scenario.id() + ".jfr").toString());
            String customHint = scenario.profileHint();
            if (!customHint.isBlank()) {
                hint.addProperty("hint", customHint);
            }
            hints.add(hint);
        }
        return hints;
    }

    private static JsonArray intArray(List<Integer> values) {
        JsonArray array = new JsonArray();
        for (Integer value : values) {
            array.add(value);
        }
        return array;
    }

    private static JsonArray stringArray(List<String> values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }

    private static String summary(JsonObject root) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Performance Experiment").append(System.lineSeparator()).append(System.lineSeparator());
        builder.append("- Experiment: `").append(root.get("experimentId").getAsString()).append("`")
                .append(System.lineSeparator());
        builder.append("- Git ref: `").append(root.get("gitRef").getAsString()).append("`")
                .append(System.lineSeparator());
        builder.append("- Repetitions: `").append(root.get("repetitions").getAsInt()).append("`")
                .append(System.lineSeparator());
        builder.append("- Warmups: `").append(root.get("warmups").getAsInt()).append("`")
                .append(System.lineSeparator());
        builder.append(System.lineSeparator());
        builder.append("| Scenario | Bars | Median ms | p90 ms | Ops/s | Checksum |")
                .append(System.lineSeparator());
        builder.append("| --- | ---: | ---: | ---: | ---: | ---: |").append(System.lineSeparator());

        JsonArray results = root.getAsJsonArray("results");
        for (int i = 0; i < results.size(); i++) {
            JsonObject result = results.get(i).getAsJsonObject();
            JsonObject stats = result.getAsJsonObject("stats");
            builder.append("| `").append(result.get("scenarioId").getAsString()).append("` | ")
                    .append(INTEGER_FORMAT.format(result.get("barCount").getAsInt())).append(" | ")
                    .append(formatMillis(stats.get("medianNanos").getAsLong())).append(" | ")
                    .append(formatMillis(stats.get("p90Nanos").getAsLong())).append(" | ")
                    .append(DECIMAL_FORMAT.format(stats.get("operationsPerSecond").getAsDouble())).append(" | ")
                    .append(result.get("checksum").getAsLong()).append(" |").append(System.lineSeparator());
        }
        return builder.toString();
    }

    private static String formatMillis(long nanos) {
        return DECIMAL_FORMAT.format(nanos / 1_000_000d);
    }

    private static String gitRef() {
        return commandOutput("git", "rev-parse", "--short", "HEAD").orElse("unknown");
    }

    private static Optional<String> commandOutput(String... command) {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        try {
            Process process = processBuilder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            int exitCode = process.waitFor();
            if (exitCode == 0 && !output.isBlank()) {
                return Optional.of(output);
            }
        } catch (IOException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
        return Optional.empty();
    }

    record RunArtifacts(Path outputDir, JsonObject performanceJson) {
    }

    record RunnerCli(String experimentId, List<Integer> barCounts, List<String> scenarioIds, int repetitions,
            int warmups, Optional<Path> outputDir, boolean profile, boolean help) {

        static RunnerCli parse(String[] args) {
            String experimentId = KalmanFilterPerformanceExperiment.ID;
            List<Integer> barCounts = List.of(1_000, 5_000, 10_000, 50_000);
            List<String> scenarioIds = List.of();
            int repetitions = DEFAULT_REPETITIONS;
            int warmups = DEFAULT_WARMUPS;
            Optional<Path> outputDir = Optional.empty();
            boolean profile = false;
            boolean help = false;

            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                case "-h", "--help" -> help = true;
                case "--experiment" -> experimentId = requireValue(args, ++i, arg);
                case "--barCounts", "--bar-counts" -> barCounts = parsePositiveInts(requireValue(args, ++i, arg),
                        "--barCounts");
                case "--scenarios" -> scenarioIds = parseCsv(requireValue(args, ++i, arg));
                case "--repetitions" -> repetitions = parsePositiveInt(requireValue(args, ++i, arg), "--repetitions");
                case "--warmups" -> warmups = parseNonNegativeInt(requireValue(args, ++i, arg), "--warmups");
                case "--outputDir", "--output-dir" -> outputDir = Optional.of(Path.of(requireValue(args, ++i, arg)));
                case "--profile" -> profile = true;
                default -> throw new IllegalArgumentException("Unknown argument: " + arg);
                }
            }

            return new RunnerCli(experimentId, barCounts, scenarioIds, repetitions, warmups, outputDir, profile, help);
        }

        private static String usage() {
            return """
                    PerformanceExperimentRunner

                    Options:
                      --experiment <id>       Experiment id, for example kalman-filter
                      --barCounts <csv>       Positive bar counts, for example 1000,5000,10000
                      --scenarios <csv>       Scenario ids, or omitted for experiment defaults
                      --repetitions <n>       Measured repetitions per cell (default 5)
                      --warmups <n>           Warmup repetitions per cell (default 1)
                      --outputDir <dir>       Artifact directory
                      --profile               Emit profiler hints in performance.json
                    """;
        }

        private static String requireValue(String[] args, int index, String argument) {
            if (index >= args.length) {
                throw new IllegalArgumentException(argument + " requires a value");
            }
            return args[index];
        }

        private static List<Integer> parsePositiveInts(String value, String argument) {
            List<String> tokens = parseCsv(value);
            if (tokens.isEmpty()) {
                throw new IllegalArgumentException(argument + " must not be empty");
            }
            List<Integer> values = new ArrayList<>(tokens.size());
            for (String token : tokens) {
                values.add(parsePositiveInt(token, argument));
            }
            Set<Integer> deduplicated = new LinkedHashSet<>(values);
            return List.copyOf(deduplicated);
        }

        private static int parsePositiveInt(String value, String argument) {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException(argument + " values must be positive");
            }
            return parsed;
        }

        private static int parseNonNegativeInt(String value, String argument) {
            int parsed = Integer.parseInt(value);
            if (parsed < 0) {
                throw new IllegalArgumentException(argument + " values must be non-negative");
            }
            return parsed;
        }

        private static List<String> parseCsv(String value) {
            return Arrays.stream(value.split(","))
                    .map(String::trim)
                    .filter(token -> !token.isEmpty())
                    .toList();
        }
    }

    private record ScenarioAggregation(String scenarioId, String description, String hypothesis, int barCount,
            List<PerformanceScenario.Measurement> measurements, ScenarioStats stats, long checksum,
            boolean checksumStable) {

        static ScenarioAggregation from(PerformanceScenario scenario, int barCount,
                List<PerformanceScenario.Measurement> measurements) {
            Objects.requireNonNull(scenario, "scenario");
            if (measurements.isEmpty()) {
                throw new IllegalArgumentException("measurements must not be empty");
            }
            long checksum = measurements.getFirst().checksum();
            boolean checksumStable = measurements.stream().allMatch(measurement -> measurement.checksum() == checksum);
            return new ScenarioAggregation(scenario.id(), scenario.description(), scenario.hypothesis(), barCount,
                    List.copyOf(measurements), ScenarioStats.from(measurements), checksum, checksumStable);
        }

        JsonObject toJson() {
            JsonObject object = new JsonObject();
            object.addProperty("scenarioId", scenarioId);
            object.addProperty("description", description);
            object.addProperty("hypothesis", hypothesis);
            object.addProperty("barCount", barCount);
            object.addProperty("checksum", checksum);
            object.addProperty("checksumStable", checksumStable);
            object.add("stats", stats.toJson());
            JsonArray measurementArray = new JsonArray();
            for (PerformanceScenario.Measurement measurement : measurements) {
                JsonObject measurementJson = new JsonObject();
                measurementJson.addProperty("operations", measurement.operations());
                measurementJson.addProperty("durationNanos", measurement.durationNanos());
                measurementJson.addProperty("checksum", measurement.checksum());
                JsonObject countersJson = new JsonObject();
                measurement.counters().entrySet()
                        .stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> countersJson.addProperty(entry.getKey(), entry.getValue()));
                measurementJson.add("counters", countersJson);
                measurementArray.add(measurementJson);
            }
            object.add("measurements", measurementArray);
            return object;
        }
    }

    private record ScenarioStats(long minNanos, long maxNanos, long averageNanos, long medianNanos, long p90Nanos,
            long totalOperations, long totalDurationNanos, double operationsPerSecond) {

        static ScenarioStats from(List<PerformanceScenario.Measurement> measurements) {
            List<Long> durations = measurements.stream()
                    .map(PerformanceScenario.Measurement::durationNanos)
                    .sorted()
                    .toList();
            long totalDuration = measurements.stream().mapToLong(PerformanceScenario.Measurement::durationNanos).sum();
            long totalOperations = measurements.stream().mapToLong(PerformanceScenario.Measurement::operations).sum();
            long average = totalDuration / measurements.size();
            long median = durations.get(durations.size() / 2);
            int p90Index = Math.min(durations.size() - 1, (int) Math.ceil(durations.size() * 0.9d) - 1);
            double operationsPerSecond = totalDuration == 0 ? 0d : totalOperations / (totalDuration / 1_000_000_000d);
            return new ScenarioStats(durations.getFirst(), durations.getLast(), average, median, durations.get(p90Index),
                    totalOperations, totalDuration, operationsPerSecond);
        }

        JsonObject toJson() {
            JsonObject object = new JsonObject();
            object.addProperty("minNanos", minNanos);
            object.addProperty("maxNanos", maxNanos);
            object.addProperty("averageNanos", averageNanos);
            object.addProperty("medianNanos", medianNanos);
            object.addProperty("p90Nanos", p90Nanos);
            object.addProperty("totalOperations", totalOperations);
            object.addProperty("totalDurationNanos", totalDurationNanos);
            object.addProperty("operationsPerSecond", operationsPerSecond);
            return object;
        }
    }

    private record HostTelemetry(String hostId, String osName, String osArch, String osVersion, String javaVersion,
            String jvmName, int availableProcessors) {

        static HostTelemetry capture() {
            return new HostTelemetry(hashedHostId(), System.getProperty("os.name", "unknown"),
                    System.getProperty("os.arch", "unknown"), System.getProperty("os.version", "unknown"),
                    System.getProperty("java.version", "unknown"),
                    ManagementFactory.getRuntimeMXBean().getVmName(), Runtime.getRuntime().availableProcessors());
        }

        JsonObject toJson() {
            JsonObject object = new JsonObject();
            object.addProperty("hostId", hostId);
            object.addProperty("osName", osName);
            object.addProperty("osArch", osArch);
            object.addProperty("osVersion", osVersion);
            object.addProperty("javaVersion", javaVersion);
            object.addProperty("jvmName", jvmName);
            object.addProperty("availableProcessors", availableProcessors);
            return object;
        }

        private static String hashedHostId() {
            try {
                String hostname = InetAddress.getLocalHost().getHostName();
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hashed = digest.digest(hostname.getBytes(StandardCharsets.UTF_8));
                StringBuilder builder = new StringBuilder("sha256:");
                for (byte value : hashed) {
                    builder.append(String.format("%02x", value));
                }
                return builder.toString();
            } catch (IOException | NoSuchAlgorithmException e) {
                return "unknown";
            }
        }
    }
}

interface PerformanceExperiment {

    String id();

    String description();

    List<PerformanceScenario> scenarios();

    default List<String> defaultScenarioIds() {
        return scenarios().stream()
                .map(PerformanceScenario::id)
                .toList();
    }
}

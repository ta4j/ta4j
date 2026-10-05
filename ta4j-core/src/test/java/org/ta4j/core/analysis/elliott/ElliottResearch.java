/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.Indicator;
import org.ta4j.core.analysis.elliott.ElliottResearchReport.CoverageRow;
import org.ta4j.core.analysis.elliott.ElliottResearchReport.Row;
import org.ta4j.core.analysis.elliott.swing.SwingDetector;
import org.ta4j.core.analysis.elliott.swing.SwingDetectors;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/**
 * Reproducible command-line launcher for Elliott-wave research over the
 * existing study kernel.
 *
 * <p>
 * The launcher adds no recognition logic. It configures a {@link StudyRunner}
 * from a named recipe, records one relocatable run directory ({@code run.json},
 * {@code reports/}, {@code comparisons.csv}, {@code coverage.csv},
 * {@code summary.md}, {@code traces/}), and later summarises or inspects that
 * directory without recomputing anything. Commands:
 * </p>
 *
 * <pre>
 * help
 * run &lt;smoke|frozen-cf525|explore&gt; [--out DIR] [--overwrite]
 *     [--trace off|real|selected-null-member] [--block L --member M]
 *     [--source candles.json --recipe recipe.json]
 * summarize &lt;runDir&gt;
 * inspect &lt;runDir&gt; &lt;key&gt; [--as-of IDX] [--candidate KEY] [--limit N]
 * </pre>
 *
 * <p>
 * Exit codes: {@code 0} success, {@code 1} usage error or run failure (also a
 * partial or failed run, an unknown key or as-of index), {@code 2} inspection
 * diagnostic (missing, truncated or corrupt trace, failed dataset).
 * </p>
 *
 * @since 0.25.1
 */
final class ElliottResearch {

    static final String SCHEMA = "elliott-research-run/1";

    private static final String RECIPE_SMOKE = "smoke";
    private static final String RECIPE_FROZEN = "frozen-cf525";
    private static final String RECIPE_EXPLORE = "explore";
    private static final List<String> RECIPES = List.of(RECIPE_SMOKE, RECIPE_FROZEN, RECIPE_EXPLORE);
    private static final String TRACE_OFF = "off";
    private static final List<String> TRACE_MODES = List.of(TRACE_OFF, ElliottResearchTrace.MODE_REAL,
            ElliottResearchTrace.MODE_SELECTED_NULL_MEMBER);
    private static final List<String> RULE_IDS = List.of("wave2-origin", "wave3-not-shortest", "wave4-nonoverlap",
            "wave5-divergence");
    private static final List<String> COMPETING_MODES = List.of("3+3", "5+5", "change-point-baseline");
    private static final List<String> DETECTOR_FACTORIES = List.of("fractal", "slopeChange", "prominence");
    private static final Pattern DATASET_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");
    // Windows device names are reserved as a file basename regardless of extension.
    private static final Pattern WINDOWS_RESERVED = Pattern.compile("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);
    private static final String RUN_FILE = "run.json";
    private static final String COMPARISONS_FILE = "comparisons.csv";
    private static final String COVERAGE_FILE = "coverage.csv";
    private static final String SUMMARY_FILE = "summary.md";
    private static final String REPORTS_DIR = "reports";
    private static final String TRACES_DIR = "traces";
    /**
     * Per-dataset price bars a replay viewer needs to draw the traced as-of
     * indices.
     */
    private static final String BARS_DIR = "bars";
    private static final String BARS_HEADER = "index,beginTime,endTime,open,high,low,close,volume";
    /**
     * Consecutive in-window bars further apart than this many bar periods mark
     * coverage partial.
     */
    private static final int INTERNAL_GAP_BAR_PERIODS = 7;
    // Longest artifact basename a dataset id feeds is its selected-null-member
    // trace; keep it within the
    // 255-byte basename limit common to supported file systems (ids are ASCII, so
    // chars equal bytes).
    private static final int MAX_DATASET_ID_LENGTH = 255
            - nullTraceName("", Integer.MAX_VALUE, Integer.MAX_VALUE).length() + TRACES_DIR.length() + 1;
    private static final String LOCK_FILE = ".run.lock";
    private static final int DEFAULT_LIMIT = 10;
    private static final int SMOKE_BARS = 912;
    private static final String SMOKE_DATASET = "smoke";
    private static final String SMOKE_ASSET = "SMOKE-SYN";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
            .disableHtmlEscaping()
            .serializeNulls()
            .create();

    private ElliottResearch() {
    }

    /**
     * Runs one launcher command and exits with a non-zero status on failure.
     *
     * @param args command line
     */
    public static void main(final String[] args) {
        final int exitCode = execute(args, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    /**
     * Executes one launcher command.
     *
     * @param args command line
     * @param out  destination of normal output
     * @param err  destination of error and diagnostic output
     * @return process exit code: 0 success, 1 usage or run failure, 2 diagnostic
     */
    static int execute(final String[] args, final PrintStream out, final PrintStream err) {
        try {
            if (args.length == 0) {
                err.println(usage());
                return 1;
            }
            switch (args[0]) {
            case "help", "--help", "-h" -> {
                out.println(usage());
                return 0;
            }
            case "run" -> {
                return runCommand(List.of(args).subList(1, args.length), out, err);
            }
            case "summarize" -> {
                return summarizeCommand(List.of(args).subList(1, args.length), out);
            }
            case "inspect" -> {
                return inspectCommand(List.of(args).subList(1, args.length), out);
            }
            default -> throw new IllegalArgumentException("unknown command: " + args[0]);
            }
        } catch (final Diagnostic diagnostic) {
            err.println("diagnostic: " + diagnostic.getMessage());
            return 2;
        } catch (final IllegalArgumentException usage) {
            err.println("error: " + usage.getMessage());
            err.println("run 'help' for usage");
            return 1;
        } catch (final IOException | RuntimeException failure) {
            err.println("error: " + failure);
            return 1;
        }
    }

    private static String usage() {
        return String.join("\n", "Elliott research launcher (test scope)", "",
                "  help                                     print this text",
                "  run <smoke|frozen-cf525|explore> [options]",
                "      --out DIR            run directory (default target/elliott-research/<recipe>-<UTC stamp>)",
                "      --overwrite          reuse a non-empty DIR that already holds a run.json",
                "      --trace MODE         off | real | selected-null-member (default real for smoke, else off)",
                "      --block L --member M null block length and member index (selected-null-member only)",
                "      --source FILE --recipe FILE   candle JSON and recipe JSON (explore only, both required)",
                "      --expect-fingerprint FP       fail unless the run's configuration fingerprint is FP",
                "      --expect-source-sha256 SHA    fail unless the explore source candles hash to SHA",
                "  summarize <runDir>       regenerate summary.md from the recorded artifacts",
                "  inspect <runDir> <key> [--as-of IDX] [--candidate KEY] [--limit N]",
                "                           list the recorded observations behind one comparison key", "",
                "Run status: complete = every dataset evaluated (coverage.csv marks datasets whose data cover",
                "less than the requested window as partial), partial = some dataset failed, failed = all failed.",
                "Exit codes: 0 ok; 1 usage error or run failure; 2 missing, truncated or corrupt trace, or failed",
                "dataset while inspecting.");
    }

    /** Actionable inspection failure that maps to exit code 2. */
    private static final class Diagnostic extends RuntimeException {

        private static final long serialVersionUID = 1L;

        Diagnostic(final String message) {
            super(message);
        }
    }

    // ------------------------------------------------------------------ run

    private record RunOptions(String recipe, Path out, boolean overwrite, String trace, Integer block, Integer member,
            Path source, Path recipeFile, String expectFingerprint, String expectSourceSha256) {
    }

    private static RunOptions parseRunOptions(final List<String> args) {
        if (args.isEmpty() || args.get(0).startsWith("--")) {
            throw new IllegalArgumentException("run needs a recipe: one of " + RECIPES);
        }
        final String recipe = args.get(0);
        if (!RECIPES.contains(recipe)) {
            throw new IllegalArgumentException("unknown recipe '" + recipe + "'; expected one of " + RECIPES);
        }
        final Map<String, String> values = new HashMap<>();
        boolean overwrite = false;
        for (int index = 1; index < args.size(); index++) {
            final String option = args.get(index);
            if ("--overwrite".equals(option)) {
                if (overwrite) {
                    throw new IllegalArgumentException("option --overwrite given twice");
                }
                overwrite = true;
            } else if (Set
                    .of("--out", "--trace", "--block", "--member", "--source", "--recipe", "--expect-fingerprint",
                            "--expect-source-sha256")
                    .contains(option)) {
                if (index + 1 >= args.size() || args.get(index + 1).startsWith("--")) {
                    throw new IllegalArgumentException("option " + option + " requires a value");
                }
                if (values.put(option, args.get(++index)) != null) {
                    throw new IllegalArgumentException("option " + option + " given twice");
                }
            } else if (option.startsWith("--")) {
                throw new IllegalArgumentException("unknown option " + option);
            } else {
                throw new IllegalArgumentException("unexpected argument '" + option + "'");
            }
        }
        final String trace = values.getOrDefault("--trace",
                RECIPE_SMOKE.equals(recipe) ? ElliottResearchTrace.MODE_REAL : TRACE_OFF);
        if (!TRACE_MODES.contains(trace)) {
            throw new IllegalArgumentException("--trace must be one of " + TRACE_MODES + ", was '" + trace + "'");
        }
        final Integer block = integerOption(values, "--block");
        final Integer member = integerOption(values, "--member");
        if (ElliottResearchTrace.MODE_SELECTED_NULL_MEMBER.equals(trace)) {
            if (block == null || member == null) {
                throw new IllegalArgumentException("--trace selected-null-member requires both --block and --member");
            }
        } else if (block != null || member != null) {
            throw new IllegalArgumentException("--block and --member are only valid with --trace selected-null-member");
        }
        final boolean explore = RECIPE_EXPLORE.equals(recipe);
        if (explore && (!values.containsKey("--source") || !values.containsKey("--recipe"))) {
            throw new IllegalArgumentException("recipe explore requires both --source and --recipe");
        }
        if (!explore && (values.containsKey("--source") || values.containsKey("--recipe")
                || values.containsKey("--expect-source-sha256"))) {
            throw new IllegalArgumentException(
                    "--source, --recipe and --expect-source-sha256 are only valid with the explore recipe");
        }
        return new RunOptions(recipe, values.containsKey("--out") ? Path.of(values.get("--out")) : null, overwrite,
                trace, block, member, values.containsKey("--source") ? Path.of(values.get("--source")) : null,
                values.containsKey("--recipe") ? Path.of(values.get("--recipe")) : null,
                values.get("--expect-fingerprint"), values.get("--expect-source-sha256"));
    }

    private static Integer integerOption(final Map<String, String> values, final String option) {
        final String text = values.get(option);
        if (text == null) {
            return null;
        }
        try {
            final int value = Integer.parseInt(text);
            if (value < 0) {
                throw new IllegalArgumentException("option " + option + " must not be negative, was " + text);
            }
            return value;
        } catch (final NumberFormatException e) {
            throw new IllegalArgumentException("option " + option + " needs an integer, was '" + text + "'");
        }
    }

    /** One detector configuration resolvable to a fresh detector per use. */
    private record DetectorRecipe(String name, String factory, List<Integer> params) {

        DetectorRecipe {
            if (!DETECTOR_FACTORIES.contains(factory)) {
                throw new IllegalArgumentException(
                        "detector '" + name + "': unknown factory '" + factory + "'; expected " + DETECTOR_FACTORIES);
            }
            params = List.copyOf(params);
            final int expected = "prominence".equals(factory) ? 0 : 1;
            if (params.size() != expected) {
                throw new IllegalArgumentException("detector '" + name + "': factory " + factory + " needs " + expected
                        + " integer parameter(s), got " + params);
            }
        }

        Supplier<SwingDetector> supplier() {
            return switch (factory) {
            case "fractal" -> () -> SwingDetectors.fractal(params.get(0));
            case "slopeChange" -> () -> SwingDetectors.slopeChange(params.get(0));
            default -> SwingDetectors::prominence;
            };
        }

        JsonObject toJson() {
            final JsonObject json = new JsonObject();
            json.addProperty("name", name);
            json.addProperty("factory", factory);
            json.add("params", intArray(params));
            return json;
        }
    }

    /** Effective analytical configuration of one run. */
    private record Setup(String fingerprint, StudyRunner.Partitions partitions, DetectorRecipe primary,
            List<DetectorRecipe> robustness, List<String> activeRules, int momentumBarCount,
            List<String> competingModes, List<Integer> blockLengths, int ensembleSize, long seed) {

        StudyRunner runner() {
            final Function<BarSeries, Indicator<Num>> momentum = series -> new RSIIndicator(
                    new ClosePriceIndicator(series), momentumBarCount);
            final List<RelationshipRule> rules = ClassicalRelationshipRules.classicalRelationships(momentum)
                    .stream()
                    .filter(rule -> activeRules.contains(rule.id()))
                    .toList();
            final List<DetectorRobustnessMatrix.DetectorSpec> specs = robustness.stream()
                    .map(detector -> new DetectorRobustnessMatrix.DetectorSpec(detector.name(), detector.supplier()))
                    .toList();
            return new StudyRunner(primary.supplier(), List.of(TopologyGrammar.MOTIVE_5, TopologyGrammar.CYCLE_5_3),
                    rules, new StudyRunner.Configuration(partitions, fingerprint, seed, blockLengths, ensembleSize,
                            specs, primary.name(), competingModes));
        }

        LocalDate requestedFrom() {
            return partitions.entries()
                    .stream()
                    .map(StudyRunner.Partition::start)
                    .min(Comparator.naturalOrder())
                    .orElseThrow();
        }

        LocalDate requestedTo() {
            return partitions.entries()
                    .stream()
                    .map(StudyRunner.Partition::end)
                    .max(Comparator.naturalOrder())
                    .orElseThrow();
        }

        void validateSelection(final Integer block, final Integer member) {
            if (block != null && !blockLengths.contains(block)) {
                throw new IllegalArgumentException(
                        "--block " + block + " is not a configured null block length; expected one of " + blockLengths);
            }
            if (member != null && member >= ensembleSize) {
                throw new IllegalArgumentException("--member " + member
                        + " is outside the configured ensemble; expected 0.." + (ensembleSize - 1));
            }
        }

        JsonObject toJson() {
            final JsonObject json = new JsonObject();
            final JsonArray entries = new JsonArray();
            for (final StudyRunner.Partition partition : partitions.entries()) {
                final JsonObject entry = new JsonObject();
                entry.addProperty("name", partition.name());
                entry.addProperty("start", partition.start().toString());
                entry.addProperty("end", partition.end().toString());
                entries.add(entry);
            }
            json.add("partitions", entries);
            json.addProperty("forbiddenCalibrationStart", partitions.forbiddenCalibrationStart().toString());
            json.add("detector", primary.toJson());
            final JsonArray detectors = new JsonArray();
            robustness.forEach(detector -> detectors.add(detector.toJson()));
            json.add("robustnessDetectors", detectors);
            json.add("activeRules", stringArray(activeRules));
            final JsonObject momentum = new JsonObject();
            momentum.addProperty("type", "RSI");
            momentum.addProperty("barCount", momentumBarCount);
            json.add("momentum", momentum);
            json.add("competingModes", competingModes == null ? JsonNull.INSTANCE : stringArray(competingModes));
            final JsonObject nulls = new JsonObject();
            nulls.add("blockLengths", intArray(blockLengths));
            nulls.addProperty("ensembleSize", ensembleSize);
            nulls.addProperty("seed", seed);
            json.add("null", nulls);
            return json;
        }
    }

    /** Where the run's identity and recapture inputs come from. */
    private record Recipe(String name, String source, String recipeFile, JsonObject definition) {
    }

    private static Setup smokeSetup() {
        final StudyRunner.Partitions partitions = new StudyRunner.Partitions(
                List.of(new StudyRunner.Partition("calibration", LocalDate.of(2020, 1, 1), LocalDate.of(2020, 12, 31)),
                        new StudyRunner.Partition("validation", LocalDate.of(2021, 1, 1), LocalDate.of(2021, 9, 30)),
                        new StudyRunner.Partition("holdout", LocalDate.of(2021, 10, 1), LocalDate.of(2022, 6, 30))),
                LocalDate.of(2024, 1, 1));
        final DetectorRecipe w3 = new DetectorRecipe("fractal-w3", "fractal", List.of(3));
        final DetectorRecipe w5 = new DetectorRecipe("fractal-w5", "fractal", List.of(5));
        final String description = "elliott-research-smoke/1: " + SMOKE_BARS
                + " synthetic daily bars from 2020-01-01 (StrictMath sine mix); primary fractal-w5; robustness fractal-w3,fractal-w5; "
                + "RSI14; competing 3+3,5+5,change-point-baseline; null block 20 ensemble 8 seed 5252026";
        return new Setup(sha256(description.getBytes(StandardCharsets.UTF_8)), partitions, w5, List.of(w3, w5),
                RULE_IDS, 14, COMPETING_MODES, List.of(20), 8, 5_252_026L);
    }

    private static BarSeries smokeSeries() {
        final BarSeries series = new BaseBarSeriesBuilder().withName(SMOKE_ASSET).build();
        final Instant start = LocalDate.of(2020, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant();
        double previousClose = Double.NaN;
        for (int index = 0; index < SMOKE_BARS; index++) {
            final double close = 100.0d + 0.02d * index + 22.0d * StrictMath.sin(0.11d * index)
                    + 11.0d * StrictMath.sin(0.37d * index + 1.3d) + 4.0d * StrictMath.sin(0.91d * index + 0.4d);
            final double open = Double.isNaN(previousClose) ? close : previousClose;
            final double spread = 1.5d + StrictMath.abs(StrictMath.sin(0.53d * index));
            series.addBar(series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(start.plus(Duration.ofDays(index + 1L)))
                    .openPrice(open)
                    .highPrice(Math.max(open, close) + spread)
                    .lowPrice(Math.min(open, close) - spread)
                    .closePrice(close)
                    .volume(1)
                    .build());
            previousClose = close;
        }
        return series;
    }

    private static Setup frozenSetup(final ElliottStudyProtocol protocol) {
        final ElliottStudyProtocol.Partitions partitions = protocol.partitions();
        final StudyRunner.Partitions runnerPartitions = new StudyRunner.Partitions(List.of(
                new StudyRunner.Partition("calibration", partitions.calibrationStart(), partitions.calibrationEnd()),
                new StudyRunner.Partition("validation", partitions.validationStart(), partitions.validationEnd()),
                new StudyRunner.Partition("holdout", partitions.holdoutStart(), partitions.holdoutEnd())),
                partitions.forbiddenCalibrationStart());
        final List<DetectorRecipe> detectors = new ArrayList<>();
        DetectorRecipe primary = null;
        for (final ElliottStudyProtocol.DetectorConfiguration configuration : protocol.detectorConfigurations()) {
            final DetectorRecipe detector = new DetectorRecipe(configuration.name(), configuration.factory(),
                    configuration.params());
            detectors.add(detector);
            if (detector.name().equals(protocol.primaryDetector())) {
                primary = detector;
            }
        }
        if (primary == null) {
            throw new IllegalStateException(
                    "protocol primary detector is not configured: " + protocol.primaryDetector());
        }
        return new Setup(protocol.fingerprintSha256(), runnerPartitions, primary, detectors, RULE_IDS,
                protocol.momentumIndicator().barCount(), protocol.competingGrammars(),
                protocol.nullEnsemble().blockLengths(), protocol.nullEnsemble().ensembleSize(),
                protocol.nullEnsemble().seed());
    }

    private static final Set<String> RECIPE_FIELDS = Set.of("datasetId", "asset", "partitions",
            "forbiddenCalibrationStart", "detector", "robustnessDetectors", "activeRules", "momentum", "competingModes",
            "null");

    /** Parsed explore recipe plus the setup it describes. */
    private record ExploreRecipe(String datasetId, String asset, Setup setup, JsonObject definition) {
    }

    private static ExploreRecipe parseExplore(final byte[] recipeBytes) {
        final JsonObject root;
        try {
            final JsonElement parsed = JsonParser.parseString(new String(recipeBytes, StandardCharsets.UTF_8));
            root = objectOf(parsed, "recipe");
        } catch (final JsonParseException e) {
            throw new IllegalArgumentException("recipe is not valid JSON: " + e.getMessage());
        }
        rejectUnknown(root, "recipe", RECIPE_FIELDS);
        final String datasetId = text(required(root, "datasetId", "recipe"), "recipe.datasetId");
        if (!DATASET_ID.matcher(datasetId).matches()) {
            throw new IllegalArgumentException(
                    "recipe.datasetId must match " + DATASET_ID.pattern() + ", was '" + datasetId + "'");
        }
        if (WINDOWS_RESERVED.matcher(datasetId).matches()) {
            throw new IllegalArgumentException("recipe.datasetId '" + datasetId
                    + "' is a reserved Windows device name and cannot name its report file");
        }
        if (datasetId.length() > MAX_DATASET_ID_LENGTH) {
            throw new IllegalArgumentException("recipe.datasetId is " + datasetId.length() + " characters; at most "
                    + MAX_DATASET_ID_LENGTH + " keeps every artifact file name within 255 bytes");
        }
        final String asset = text(required(root, "asset", "recipe"), "recipe.asset");
        final List<StudyRunner.Partition> partitions = new ArrayList<>();
        final JsonArray partitionArray = arrayOf(required(root, "partitions", "recipe"), "recipe.partitions");
        for (int index = 0; index < partitionArray.size(); index++) {
            final String where = "recipe.partitions[" + index + "]";
            final JsonObject entry = objectOf(partitionArray.get(index), where);
            rejectUnknown(entry, where, Set.of("name", "start", "end"));
            final String name = text(required(entry, "name", where), where + ".name");
            ElliottResearchReport.requireKeyPart(name, where + ".name");
            partitions.add(new StudyRunner.Partition(name, date(required(entry, "start", where), where + ".start"),
                    date(required(entry, "end", where), where + ".end")));
        }
        final StudyRunner.Partitions runnerPartitions;
        try {
            runnerPartitions = new StudyRunner.Partitions(partitions,
                    date(required(root, "forbiddenCalibrationStart", "recipe"), "recipe.forbiddenCalibrationStart"));
            runnerPartitions.validation();
            runnerPartitions.holdout();
            runnerPartitions.assertCalibrationConfiguration();
        } catch (final IllegalStateException | IllegalArgumentException e) {
            throw new IllegalArgumentException("recipe.partitions invalid: " + e.getMessage());
        }
        final DetectorRecipe primary = detector(required(root, "detector", "recipe"), "recipe.detector");
        final List<DetectorRecipe> robustness = new ArrayList<>();
        if (root.has("robustnessDetectors")) {
            final JsonArray array = arrayOf(root.get("robustnessDetectors"), "recipe.robustnessDetectors");
            for (int index = 0; index < array.size(); index++) {
                robustness.add(detector(array.get(index), "recipe.robustnessDetectors[" + index + "]"));
            }
        }
        final List<String> activeRules = root.has("activeRules")
                ? strings(root.get("activeRules"), "recipe.activeRules", RULE_IDS)
                : RULE_IDS;
        if (activeRules.isEmpty()) {
            throw new IllegalArgumentException("recipe.activeRules must not be empty");
        }
        final JsonObject momentum = objectOf(required(root, "momentum", "recipe"), "recipe.momentum");
        rejectUnknown(momentum, "recipe.momentum", Set.of("type", "barCount"));
        if (!"RSI".equals(text(required(momentum, "type", "recipe.momentum"), "recipe.momentum.type"))) {
            throw new IllegalArgumentException("recipe.momentum.type must be RSI");
        }
        final int barCount = integer(required(momentum, "barCount", "recipe.momentum"), "recipe.momentum.barCount");
        if (barCount < 2) {
            throw new IllegalArgumentException("recipe.momentum.barCount must be at least 2, was " + barCount);
        }
        final List<String> competing = root.has("competingModes")
                ? strings(root.get("competingModes"), "recipe.competingModes", COMPETING_MODES)
                : COMPETING_MODES;
        final JsonObject nulls = objectOf(required(root, "null", "recipe"), "recipe.null");
        rejectUnknown(nulls, "recipe.null", Set.of("blockLengths", "ensembleSize", "seed"));
        final List<Integer> blockLengths = new ArrayList<>();
        final JsonArray blocks = arrayOf(required(nulls, "blockLengths", "recipe.null"), "recipe.null.blockLengths");
        for (int index = 0; index < blocks.size(); index++) {
            blockLengths.add(integer(blocks.get(index), "recipe.null.blockLengths[" + index + "]"));
        }
        final int ensembleSize = integer(required(nulls, "ensembleSize", "recipe.null"), "recipe.null.ensembleSize");
        final long seed = longValue(required(nulls, "seed", "recipe.null"), "recipe.null.seed");
        final Setup setup = new Setup("explore-" + sha256(recipeBytes), runnerPartitions, primary, robustness,
                activeRules, barCount, competing, blockLengths, ensembleSize, seed);
        try {
            setup.runner();
            primary.supplier().get();
            for (final DetectorRecipe detector : robustness) {
                detector.supplier().get();
            }
        } catch (final IllegalArgumentException | IllegalStateException e) {
            throw new IllegalArgumentException("recipe rejected: " + e.getMessage());
        }
        return new ExploreRecipe(datasetId, asset, setup, root);
    }

    private static DetectorRecipe detector(final JsonElement element, final String where) {
        final JsonObject object = objectOf(element, where);
        rejectUnknown(object, where, Set.of("name", "factory", "params"));
        final List<Integer> params = new ArrayList<>();
        if (object.has("params")) {
            final JsonArray array = arrayOf(object.get("params"), where + ".params");
            for (int index = 0; index < array.size(); index++) {
                params.add(integer(array.get(index), where + ".params[" + index + "]"));
            }
        }
        final String name = text(required(object, "name", where), where + ".name");
        ElliottResearchReport.requireKeyPart(name, where + ".name");
        return new DetectorRecipe(name, text(required(object, "factory", where), where + ".factory"), params);
    }

    private static List<String> strings(final JsonElement element, final String where, final List<String> allowed) {
        final JsonArray array = arrayOf(element, where);
        final List<String> values = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            final String value = text(array.get(index), where + "[" + index + "]");
            if (!allowed.contains(value)) {
                throw new IllegalArgumentException(where + "[" + index + "] '" + value + "' is not one of " + allowed);
            }
            if (values.contains(value)) {
                throw new IllegalArgumentException(where + " lists '" + value + "' twice");
            }
            values.add(value);
        }
        return values;
    }

    private static JsonElement required(final JsonObject object, final String key, final String where) {
        final JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            throw new IllegalArgumentException("missing required field " + where + "." + key);
        }
        return element;
    }

    private static void rejectUnknown(final JsonObject object, final String where, final Set<String> allowed) {
        for (final String key : object.keySet()) {
            if (!allowed.contains(key)) {
                throw new IllegalArgumentException(
                        "unknown field " + where + "." + key + " (allowed: " + new java.util.TreeSet<>(allowed) + ")");
            }
        }
    }

    private static JsonObject objectOf(final JsonElement element, final String where) {
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException(where + " must be a JSON object");
        }
        return element.getAsJsonObject();
    }

    private static JsonArray arrayOf(final JsonElement element, final String where) {
        if (!element.isJsonArray()) {
            throw new IllegalArgumentException(where + " must be a JSON array");
        }
        return element.getAsJsonArray();
    }

    private static String text(final JsonElement element, final String where) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString() || element.getAsString().isBlank()) {
            throw new IllegalArgumentException(where + " must be a non-blank string");
        }
        return element.getAsString();
    }

    private static int integer(final JsonElement element, final String where) {
        try {
            return numeric(element, where).intValueExact();
        } catch (final ArithmeticException e) {
            throw new IllegalArgumentException(where + " must be an integer");
        }
    }

    private static long longValue(final JsonElement element, final String where) {
        try {
            return numeric(element, where).longValueExact();
        } catch (final ArithmeticException e) {
            throw new IllegalArgumentException(where + " must be an integer");
        }
    }

    private static BigDecimal numeric(final JsonElement element, final String where) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(where + " must be a number");
        }
        return element.getAsBigDecimal();
    }

    private static LocalDate date(final JsonElement element, final String where) {
        final String text = text(element, where);
        try {
            return LocalDate.parse(text);
        } catch (final DateTimeParseException e) {
            throw new IllegalArgumentException(where + " must be an ISO date (yyyy-MM-dd), was '" + text + "'");
        }
    }

    private static int runCommand(final List<String> args, final PrintStream out, final PrintStream err)
            throws IOException {
        final RunOptions options = parseRunOptions(args);
        final Setup setup;
        final Recipe recipe;
        final RunPlan plan;
        switch (options.recipe()) {
        case RECIPE_SMOKE -> {
            setup = smokeSetup();
            recipe = new Recipe(RECIPE_SMOKE, null, null, null);
            plan = run -> {
                final JsonObject source = new JsonObject();
                source.addProperty("kind", "synthetic");
                source.addProperty("description", "deterministic StrictMath sine mix, " + SMOKE_BARS + " daily bars");
                run.register(SMOKE_DATASET, SMOKE_ASSET, source);
                run.start();
                run.evaluateSeries(SMOKE_DATASET, SMOKE_ASSET, setup.runner(), smokeSeries());
            };
        }
        case RECIPE_FROZEN -> {
            final ElliottStudyProtocol protocol = ElliottStudyProtocol.load();
            setup = frozenSetup(protocol);
            recipe = new Recipe(RECIPE_FROZEN, null, null, null);
            plan = run -> {
                for (final ElliottStudyProtocol.DatasetSpec dataset : protocol.datasets()) {
                    final JsonObject source = new JsonObject();
                    source.addProperty("kind", "bundled-resource");
                    source.addProperty("resource", dataset.resource());
                    source.addProperty("sha256", dataset.sha256());
                    run.register(dataset.id(), dataset.asset(), source);
                }
                run.start();
                run.evaluateFrozen(protocol);
            };
        }
        default -> {
            final Path recipePath = options.recipeFile().toAbsolutePath().normalize();
            final Path sourcePath = options.source().toAbsolutePath().normalize();
            final ExploreRecipe explore = parseExplore(readBytes(recipePath, "recipe"));
            final byte[] sourceBytes = readBytes(sourcePath, "source candles");
            final String sourceSha256 = sha256(sourceBytes);
            // A recapture names the original paths; refuse edited inputs instead of
            // pairing a new trace with the old run's statistics.
            if (options.expectSourceSha256() != null && !options.expectSourceSha256().equals(sourceSha256)) {
                throw new IllegalArgumentException("source candles " + sourcePath + " hash to " + sourceSha256
                        + ", not the expected " + options.expectSourceSha256() + "; the file changed since the run");
            }
            final BarSeries exploreSeries;
            try {
                exploreSeries = OssifiedElliottWaveSeriesLoader.parseCandles(sourceBytes, explore.asset());
            } catch (final RuntimeException e) {
                throw new IllegalArgumentException("source candles " + sourcePath + " could not be parsed: " + e);
            }
            if (exploreSeries.isEmpty()) {
                throw new IllegalArgumentException("source candles " + sourcePath + " contain no bars");
            }
            setup = explore.setup();
            recipe = new Recipe(RECIPE_EXPLORE, sourcePath.toString(), recipePath.toString(), explore.definition());
            plan = run -> {
                final JsonObject source = new JsonObject();
                source.addProperty("kind", "file");
                source.addProperty("path", recipe.source());
                source.addProperty("sha256", sourceSha256);
                run.register(explore.datasetId(), explore.asset(), source);
                run.start();
                run.evaluateSeries(explore.datasetId(), explore.asset(), setup.runner(), exploreSeries);
            };
        }
        }
        if (options.expectFingerprint() != null && !options.expectFingerprint().equals(setup.fingerprint())) {
            throw new IllegalArgumentException("configuration fingerprint is " + setup.fingerprint()
                    + ", not the expected " + options.expectFingerprint() + "; the recipe changed since the run");
        }
        setup.validateSelection(options.block(), options.member());

        final Path dir = (options.out() != null ? options.out()
                : Path.of("target", "elliott-research", options.recipe() + "-" + STAMP.format(Instant.now())))
                .toAbsolutePath()
                .normalize();
        // Probe provenance before claiming the directory: an output path inside the
        // checkout would otherwise mark a clean worktree dirty with our own lock file.
        final String revision = gitRevision();
        final String worktree = gitWorktreeState();
        try (FileChannel lock = reserveRunDirectory(dir, options.overwrite())) {
            final Run run = new Run(dir, options, setup, recipe, revision, worktree);
            plan.execute(run);
            run.finish();
            out.println("Run directory: " + dir);
            out.println("Summary: " + dir.resolve(SUMMARY_FILE));
            out.println("Status: " + run.status + " (" + run.rows.size() + " comparison rows)");
            for (final DatasetEntry entry : run.entries.values()) {
                if ("failed".equals(entry.status)) {
                    err.println("dataset " + entry.id + " failed: " + entry.message);
                }
            }
            return "complete".equals(run.status) ? 0 : 1;
        }
    }

    /** Registers a recipe's datasets on a prepared run and evaluates them. */
    @FunctionalInterface
    private interface RunPlan {

        void execute(Run run) throws IOException;
    }

    private static byte[] readBytes(final Path path, final String what) {
        try {
            return Files.readAllBytes(path);
        } catch (final IOException e) {
            throw new IllegalArgumentException("cannot read " + what + " file " + path + ": " + e.getMessage());
        }
    }

    /**
     * Claims {@code dir} for one run: an exclusive lock on its lock file, held
     * until the returned channel closes (or the process exits), stops a second
     * launcher from writing into the same directory; the emptiness check that
     * follows ignores the lock file itself. The refusal checks run read-only before
     * the lock file exists, so a refused directory gains no lock file, and repeat
     * under the lock before anything is replaced. The lock file is never deleted:
     * unlinking it could strand another process's lock on an orphaned inode and
     * admit a second writer.
     */
    private static FileChannel reserveRunDirectory(final Path dir, final boolean overwrite) throws IOException {
        if (Files.exists(dir) && !Files.isDirectory(dir)) {
            throw new IllegalArgumentException("output path exists and is not a directory: " + dir);
        }
        checkRunDirectory(dir, overwrite);
        Files.createDirectories(dir);
        final FileChannel channel = FileChannel.open(dir.resolve(LOCK_FILE), StandardOpenOption.CREATE,
                StandardOpenOption.WRITE);
        try {
            final FileLock lock;
            try {
                lock = channel.tryLock();
            } catch (final OverlappingFileLockException e) {
                throw new IllegalArgumentException("another run is writing to " + dir + "; choose another --out");
            }
            if (lock == null) {
                throw new IllegalArgumentException("another run is writing to " + dir + "; choose another --out");
            }
            prepareRunDirectory(dir, overwrite);
            return channel;
        } catch (final IOException | RuntimeException e) {
            channel.close();
            throw e;
        }
    }

    /**
     * Read-only refusal checks for {@code dir}.
     *
     * @return {@code true} when {@code dir} holds a previous run that
     *         {@code --overwrite} may replace; {@code false} when it is absent or
     *         empty apart from the lock file
     */
    private static boolean checkRunDirectory(final Path dir, final boolean overwrite) throws IOException {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        final boolean empty;
        try (Stream<Path> children = Files.list(dir)) {
            empty = children.allMatch(child -> LOCK_FILE.equals(child.getFileName().toString()));
        }
        if (empty) {
            return false;
        }
        if (!overwrite) {
            throw new IllegalArgumentException("output directory is not empty: " + dir
                    + " (choose another --out, or pass --overwrite to replace a previous run)");
        }
        final JsonObject previous;
        try {
            previous = loadRun(dir);
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "refusing to overwrite " + dir + ": it is not a previous research run (" + e.getMessage() + ")");
        }
        if (previous.get("recipe") == null || !previous.get("recipe").isJsonObject()) {
            throw new IllegalArgumentException(
                    "refusing to overwrite " + dir + ": " + RUN_FILE + " holds no research recipe");
        }
        return true;
    }

    private static void prepareRunDirectory(final Path dir, final boolean overwrite) throws IOException {
        if (checkRunDirectory(dir, overwrite)) {
            for (final String file : List.of(RUN_FILE, COMPARISONS_FILE, COVERAGE_FILE, SUMMARY_FILE)) {
                Files.deleteIfExists(dir.resolve(file));
            }
            deleteTree(dir.resolve(REPORTS_DIR));
            deleteTree(dir.resolve(TRACES_DIR));
            deleteTree(dir.resolve(BARS_DIR));
        }
        Files.createDirectories(dir.resolve(REPORTS_DIR));
        Files.createDirectories(dir.resolve(TRACES_DIR));
        Files.createDirectories(dir.resolve(BARS_DIR));
    }

    private static void deleteTree(final Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (final Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    /** Mutable per-dataset run state serialised into run.json. */
    private static final class DatasetEntry {
        private final String id;
        private final String asset;
        private final JsonObject source;
        private String status = "pending";
        private String message = "";
        private String coverageStatus = "failed";
        private String coverageMessage = "not evaluated";
        private LocalDate effectiveFrom;
        private LocalDate effectiveTo;
        private int bars;
        private String report;
        private final List<String> traces = new ArrayList<>();
        private String barsFile;
        private String barsSha256;
        private int barsRows;

        DatasetEntry(final String id, final String asset, final JsonObject source) {
            this.id = id;
            this.asset = asset;
            this.source = source;
        }
    }

    /** One launcher run and the artifacts it writes. */
    private static final class Run {
        private final Path dir;
        private final RunOptions options;
        private final Setup setup;
        private final Recipe recipe;
        private final String revision;
        private final String worktree;
        private final Map<String, DatasetEntry> entries = new LinkedHashMap<>();
        private final List<Row> rows = new ArrayList<>();
        private final Map<String, ElliottResearchTrace> openTraces = new HashMap<>();
        private String numFactory = "unknown";
        private String status = "running";

        Run(final Path dir, final RunOptions options, final Setup setup, final Recipe recipe, final String revision,
                final String worktree) {
            this.dir = dir;
            this.options = options;
            this.setup = setup;
            this.recipe = recipe;
            this.revision = revision;
            this.worktree = worktree;
        }

        void register(final String id, final String asset, final JsonObject source) {
            entries.put(id, new DatasetEntry(id, asset, source));
        }

        void start() throws IOException {
            writeRunJson();
        }

        StudyObserver observer(final String id) throws IOException {
            if (!ElliottResearchTrace.MODE_REAL.equals(options.trace())) {
                return null;
            }
            final ElliottResearchTrace trace = ElliottResearchTrace.open(dir.resolve(realTraceName(id)), id, revision,
                    setup.fingerprint(), sourceSha256(id), ElliottResearchTrace.MODE_REAL, -1, -1);
            openTraces.put(id, trace);
            return trace;
        }

        /**
         * Digest of the dataset's source candles, or {@code null} for generated data.
         */
        private String sourceSha256(final String id) {
            final JsonElement sha256 = entries.get(id).source.get("sha256");
            return sha256 == null ? null : sha256.getAsString();
        }

        void evaluateSeries(final String id, final String asset, final StudyRunner runner, final BarSeries series)
                throws IOException {
            final StudyReport report;
            final Path reportFile = dir.resolve(REPORTS_DIR).resolve(id + ".json");
            try {
                final StudyObserver observer = observer(id);
                report = runner.evaluate(asset, series, series.getBeginIndex(), series.getEndIndex(), observer);
                Files.writeString(reportFile, report.toJson(), StandardCharsets.UTF_8);
            } catch (final IOException | RuntimeException failure) {
                fail(id, failure);
                return;
            }
            completed(id, runner, series, report, reportFile);
        }

        void evaluateFrozen(final ElliottStudyProtocol protocol) throws IOException {
            final FrozenProtocolStudy.DatasetListener listener = new FrozenProtocolStudy.DatasetListener() {
                @Override
                public StudyObserver observer(final String datasetId) throws IOException {
                    return Run.this.observer(datasetId);
                }

                @Override
                public void completed(final String datasetId, final StudyRunner runner, final BarSeries series,
                        final StudyReport report, final Path reportFile) {
                    Run.this.completed(datasetId, runner, series, report, reportFile);
                }

                @Override
                public void failed(final String datasetId, final RuntimeException failure) {
                    fail(datasetId, failure);
                }
            };
            try {
                FrozenProtocolStudy.run(protocol, dir.resolve(REPORTS_DIR), listener);
            } catch (final IOException | RuntimeException failure) {
                for (final DatasetEntry entry : entries.values()) {
                    if ("pending".equals(entry.status)) {
                        fail(entry.id, failure);
                    }
                }
            }
        }

        private void completed(final String id, final StudyRunner runner, final BarSeries series,
                final StudyReport report, final Path reportFile) {
            final DatasetEntry entry = entries.get(id);
            try {
                numFactory = series.numFactory().getClass().getName();
                writeBars(entry, series);
                final ElliottResearchTrace real = openTraces.remove(id);
                if (real != null) {
                    real.close();
                    entry.traces.add(relative(dir.resolve(realTraceName(id))));
                }
                if (ElliottResearchTrace.MODE_SELECTED_NULL_MEMBER.equals(options.trace())) {
                    final Path file = dir.resolve(nullTraceName(id, options.block(), options.member()));
                    try (ElliottResearchTrace trace = ElliottResearchTrace.open(file, id, revision, setup.fingerprint(),
                            sourceSha256(id), ElliottResearchTrace.MODE_SELECTED_NULL_MEMBER, options.block(),
                            options.member())) {
                        runner.replayNullMember(series, series.getBeginIndex(), series.getEndIndex(), options.block(),
                                options.member(), trace);
                    }
                    entry.traces.add(relative(file));
                }
                rows.addAll(ElliottResearchReport.comparisons(id, report));
                entry.report = relative(reportFile);
                applyCoverage(entry, series);
                entry.status = "complete";
                writeRunJson();
            } catch (final IOException | RuntimeException failure) {
                fail(id, failure);
            }
        }

        private void applyCoverage(final DatasetEntry entry, final BarSeries series) {
            final LocalDate from = setup.requestedFrom();
            final LocalDate to = setup.requestedTo();
            LocalDate first = null;
            LocalDate last = null;
            int count = 0;
            final List<StudyRunner.Partition> partitions = setup.partitions().entries();
            final boolean[] populated = new boolean[partitions.size()];
            final Instant[] previous = new Instant[partitions.size()];
            int internalGaps = 0;
            Instant widestFrom = null;
            Instant widestTo = null;
            for (int index = series.getBeginIndex(); index <= series.getEndIndex(); index++) {
                final Bar bar = series.getBar(index);
                final LocalDate date = bar.getBeginTime().atZone(ZoneOffset.UTC).toLocalDate();
                if (date.isBefore(from) || date.isAfter(to)) {
                    continue;
                }
                first = first == null || date.isBefore(first) ? date : first;
                last = last == null || date.isAfter(last) ? date : last;
                count++;
                final Duration tolerance = bar.getTimePeriod().multipliedBy(INTERNAL_GAP_BAR_PERIODS);
                for (int partition = 0; partition < partitions.size(); partition++) {
                    final StudyRunner.Partition window = partitions.get(partition);
                    if (!window.contains(date)) {
                        continue;
                    }
                    populated[partition] = true;
                    // Only missing bars within this partition affect its as-of windows;
                    // intervals between configured partitions are intentionally unrequested.
                    final Instant prior = previous[partition];
                    if (prior != null && Duration.between(prior, bar.getBeginTime()).compareTo(tolerance) > 0) {
                        internalGaps++;
                        if (widestFrom == null || Duration.between(prior, bar.getBeginTime())
                                .compareTo(Duration.between(widestFrom, widestTo)) > 0) {
                            widestFrom = prior;
                            widestTo = bar.getBeginTime();
                        }
                    }
                    previous[partition] = bar.getBeginTime();
                }
            }
            entry.effectiveFrom = first;
            entry.effectiveTo = last;
            entry.bars = count;
            // Extremes alone would call a tape complete while an entire middle
            // partition is missing and its metrics are empty.
            final List<String> gaps = new ArrayList<>();
            if (first == null) {
                gaps.add("no bars inside the requested window");
            } else if (first.isAfter(from) || last.isBefore(to)) {
                gaps.add("data cover " + first + ".." + last + ", narrower than requested " + from + ".." + to);
            }
            for (int partition = 0; first != null && partition < partitions.size(); partition++) {
                if (!populated[partition]) {
                    final StudyRunner.Partition missing = partitions.get(partition);
                    gaps.add("partition " + missing.name() + " (" + missing.start() + ".." + missing.end()
                            + ") has no bars");
                }
            }
            if (internalGaps > 0) {
                gaps.add(internalGaps + " internal gap(s) longer than " + INTERNAL_GAP_BAR_PERIODS
                        + " bar periods, widest " + widestFrom + ".." + widestTo);
            }
            entry.coverageStatus = gaps.isEmpty() ? "complete" : "partial";
            entry.coverageMessage = String.join("; ", gaps);
        }

        private void fail(final String id, final Exception failure) {
            final DatasetEntry entry = entries.get(id);
            final ElliottResearchTrace open = openTraces.remove(id);
            if (open != null) {
                try {
                    open.close();
                } catch (final IOException ignored) {
                    // the partial trace is deleted below
                }
            }
            try {
                Files.deleteIfExists(dir.resolve(realTraceName(id)));
                if (options.block() != null) {
                    Files.deleteIfExists(dir.resolve(nullTraceName(id, options.block(), options.member())));
                }
                Files.deleteIfExists(dir.resolve(REPORTS_DIR).resolve(id + ".json"));
                Files.deleteIfExists(dir.resolve(barsName(id)));
            } catch (final IOException ignored) {
                // best effort: run.json records no trace or report for a failed dataset
            }
            rows.removeIf(row -> row.dataset().equals(id));
            entry.traces.clear();
            entry.report = null;
            entry.bars = 0;
            entry.barsFile = null;
            entry.barsSha256 = null;
            entry.barsRows = 0;
            entry.effectiveFrom = null;
            entry.effectiveTo = null;
            entry.status = "failed";
            entry.message = failure.getClass().getSimpleName() + ": " + failure.getMessage();
            entry.coverageStatus = "failed";
            entry.coverageMessage = entry.message;
            try {
                writeRunJson();
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        void finish() throws IOException {
            final long complete = entries.values().stream().filter(entry -> "complete".equals(entry.status)).count();
            status = complete == entries.size() ? "complete" : complete == 0 ? "failed" : "partial";
            ElliottResearchReport.writeCsv(dir.resolve(COMPARISONS_FILE), rows);
            final JsonObject json = toJson();
            ElliottResearchReport.writeCoverage(dir.resolve(COVERAGE_FILE), coverage(json));
            Files.writeString(dir.resolve(RUN_FILE), GSON.toJson(json) + "\n", StandardCharsets.UTF_8);
            Files.writeString(dir.resolve(SUMMARY_FILE), renderSummary(dir, json, rows), StandardCharsets.UTF_8);
        }

        private void writeRunJson() throws IOException {
            Files.writeString(dir.resolve(RUN_FILE), GSON.toJson(toJson()) + "\n", StandardCharsets.UTF_8);
        }

        private String relative(final Path path) {
            return dir.relativize(path).toString().replace('\\', '/');
        }

        JsonObject toJson() {
            final JsonObject json = new JsonObject();
            json.addProperty("artifactSchemaVersion", SCHEMA);
            json.addProperty("revision", revision);
            json.addProperty("worktree", worktree);
            final JsonObject recipeJson = new JsonObject();
            recipeJson.addProperty("name", recipe.name());
            recipeJson.add("source", recipe.source() == null ? JsonNull.INSTANCE : new JsonPrimitive(recipe.source()));
            recipeJson.add("recipeFile",
                    recipe.recipeFile() == null ? JsonNull.INSTANCE : new JsonPrimitive(recipe.recipeFile()));
            recipeJson.add("definition", recipe.definition() == null ? JsonNull.INSTANCE : recipe.definition());
            json.add("recipe", recipeJson);
            json.addProperty("status", status);
            final JsonObject trace = new JsonObject();
            trace.addProperty("mode", options.trace());
            trace.add("block", options.block() == null ? JsonNull.INSTANCE : new JsonPrimitive(options.block()));
            trace.add("member", options.member() == null ? JsonNull.INSTANCE : new JsonPrimitive(options.member()));
            json.add("trace", trace);
            final JsonObject identity = new JsonObject();
            identity.addProperty("fingerprint", setup.fingerprint());
            json.add("identity", identity);
            final JsonObject numeric = new JsonObject();
            numeric.addProperty("numFactory", numFactory);
            json.add("numericContext", numeric);
            json.add("configuration", setup.toJson());
            final JsonArray datasets = new JsonArray();
            for (final DatasetEntry entry : entries.values()) {
                final JsonObject dataset = new JsonObject();
                dataset.addProperty("id", entry.id);
                dataset.addProperty("asset", entry.asset);
                dataset.add("source", entry.source);
                dataset.addProperty("status", entry.status);
                dataset.addProperty("message", entry.message);
                final JsonObject requested = new JsonObject();
                requested.addProperty("from", setup.requestedFrom().toString());
                requested.addProperty("to", setup.requestedTo().toString());
                dataset.add("requested", requested);
                final JsonObject effective = new JsonObject();
                effective.add("from", entry.effectiveFrom == null ? JsonNull.INSTANCE
                        : new JsonPrimitive(entry.effectiveFrom.toString()));
                effective.add("to", entry.effectiveTo == null ? JsonNull.INSTANCE
                        : new JsonPrimitive(entry.effectiveTo.toString()));
                dataset.add("effective", effective);
                dataset.addProperty("bars", entry.bars);
                if (entry.barsFile == null) {
                    dataset.add("priceBars", JsonNull.INSTANCE);
                } else {
                    final JsonObject priceBars = new JsonObject();
                    priceBars.addProperty("path", entry.barsFile);
                    priceBars.addProperty("sha256", entry.barsSha256);
                    priceBars.addProperty("rows", entry.barsRows);
                    dataset.add("priceBars", priceBars);
                }
                final JsonObject coverage = new JsonObject();
                coverage.addProperty("status", entry.coverageStatus);
                coverage.addProperty("message", entry.coverageMessage);
                dataset.add("coverage", coverage);
                dataset.add("report", entry.report == null ? JsonNull.INSTANCE : new JsonPrimitive(entry.report));
                dataset.add("traces", stringArray(entry.traces));
                datasets.add(dataset);
            }
            json.add("datasets", datasets);
            return json;
        }

        /**
         * Persists every bar of the evaluated series under its source index, the
         * coordinate trace records use, so a viewer can draw them without the source
         * candles.
         */
        private void writeBars(final DatasetEntry entry, final BarSeries series) throws IOException {
            final StringBuilder csv = new StringBuilder(BARS_HEADER).append('\n');
            int rows = 0;
            for (int index = series.getBeginIndex(); index <= series.getEndIndex(); index++) {
                final Bar bar = series.getBar(index);
                csv.append(index)
                        .append(',')
                        .append(bar.getBeginTime())
                        .append(',')
                        .append(bar.getEndTime())
                        .append(',')
                        .append(bar.getOpenPrice())
                        .append(',')
                        .append(bar.getHighPrice())
                        .append(',')
                        .append(bar.getLowPrice())
                        .append(',')
                        .append(bar.getClosePrice())
                        .append(',')
                        .append(bar.getVolume())
                        .append('\n');
                rows++;
            }
            final byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
            Files.write(dir.resolve(barsName(entry.id)), bytes);
            entry.barsFile = barsName(entry.id);
            entry.barsSha256 = sha256(bytes);
            entry.barsRows = rows;
        }
    }

    private static String realTraceName(final String datasetId) {
        return TRACES_DIR + "/" + datasetId + "-real.jsonl";
    }

    private static String barsName(final String datasetId) {
        return BARS_DIR + "/" + datasetId + ".csv";
    }

    private static String nullTraceName(final String datasetId, final int block, final int member) {
        return TRACES_DIR + "/" + datasetId + "-null-b" + block + "-m" + member + ".jsonl";
    }

    private static String gitRevision() {
        final String output = git("rev-parse", "HEAD");
        return output != null && output.trim().matches("[0-9a-f]{40}") ? output.trim() : "unknown";
    }

    /**
     * Whether tracked or untracked sources differ from the recorded revision: a
     * dirty run cannot be reproduced by checking that revision out.
     */
    private static String gitWorktreeState() {
        final String output = git("status", "--porcelain", "--untracked-files=normal");
        if (output == null) {
            return "unknown";
        }
        return output.isBlank() ? "clean" : "dirty";
    }

    /**
     * Runs git with a ten-second limit. Output goes to a temporary file rather than
     * a pipe so a stalled git cannot block the caller before the limit applies.
     *
     * @return the command's standard output, or {@code null} when git failed or
     *         timed out
     */
    private static String git(final String... args) {
        final List<String> command = new ArrayList<>(args.length + 1);
        command.add("git");
        command.addAll(List.of(args));
        Path output = null;
        try {
            output = Files.createTempFile("elliott-research-git", ".out");
            final Process process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD)
                    .redirectOutput(output.toFile())
                    .start();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            return process.exitValue() == 0 ? Files.readString(output, StandardCharsets.UTF_8) : null;
        } catch (final IOException e) {
            return null;
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            if (output != null) {
                try {
                    Files.deleteIfExists(output);
                } catch (final IOException ignored) {
                    // best effort: a leftover temp file does not affect the probe
                }
            }
        }
    }

    private static String sha256(final byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static JsonArray stringArray(final List<String> values) {
        final JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    private static JsonArray intArray(final List<Integer> values) {
        final JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    // ------------------------------------------------- summarize / shared

    private static JsonObject loadRun(final Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("not a run directory: " + dir);
        }
        final Path file = dir.resolve(RUN_FILE);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("not a run directory: " + file + " is missing");
        }
        try {
            final JsonObject run = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            final String schema = run.get("artifactSchemaVersion").getAsString();
            if (!SCHEMA.equals(schema)) {
                throw new IllegalArgumentException(file + " has schema " + schema + ", expected " + SCHEMA);
            }
            return run;
        } catch (final JsonParseException | IllegalStateException | NullPointerException | ClassCastException e) {
            throw new IllegalArgumentException(file + " is not a valid run file: " + e.getMessage());
        }
    }

    private static List<CoverageRow> coverage(final JsonObject run) {
        final List<CoverageRow> coverage = new ArrayList<>();
        for (final JsonElement element : run.getAsJsonArray("datasets")) {
            final JsonObject dataset = element.getAsJsonObject();
            final JsonObject requested = dataset.getAsJsonObject("requested");
            final JsonObject effective = dataset.getAsJsonObject("effective");
            final JsonObject state = dataset.getAsJsonObject("coverage");
            coverage.add(new CoverageRow(dataset.get("id").getAsString(), dataset.get("asset").getAsString(),
                    optional(requested, "from"), optional(requested, "to"), optional(effective, "from"),
                    optional(effective, "to"), dataset.get("bars").getAsInt(), state.get("status").getAsString(),
                    state.get("message").getAsString()));
        }
        return coverage;
    }

    private static String optional(final JsonObject object, final String key) {
        final JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? "" : element.getAsString();
    }

    private static String renderSummary(final Path dir, final JsonObject run, final List<Row> rows) {
        final JsonObject trace = run.getAsJsonObject("trace");
        final String mode = trace.get("mode").getAsString();
        final StringBuilder markdown = new StringBuilder(ElliottResearchReport.summaryMarkdown(
                "Elliott research run: " + run.getAsJsonObject("recipe").get("name").getAsString(), coverage(run), rows,
                verifiedRealTraces(dir, run), recaptureCommand(dir, run, ElliottResearchTrace.MODE_REAL, null, null)));
        markdown.append("\n## Run\n\n");
        markdown.append("- status: ").append(run.get("status").getAsString()).append('\n');
        markdown.append("- revision: ").append(run.get("revision").getAsString()).append('\n');
        final String worktree = run.get("worktree").getAsString();
        markdown.append("- worktree: ").append(worktree);
        if (!"clean".equals(worktree)) {
            markdown.append(" (sources may differ from the revision; results may not reproduce from it)");
        }
        markdown.append('\n');
        markdown.append("- fingerprint: ")
                .append(run.getAsJsonObject("identity").get("fingerprint").getAsString())
                .append('\n');
        markdown.append("- trace mode: ").append(mode).append('\n');
        final List<String> traces = new ArrayList<>();
        for (final JsonElement element : run.getAsJsonArray("datasets")) {
            element.getAsJsonObject().getAsJsonArray("traces").forEach(trace1 -> traces.add(trace1.getAsString()));
        }
        if (!traces.isEmpty()) {
            markdown.append("\n## Traces\n\n");
            traces.forEach(name -> markdown.append("- `").append(name).append("`\n"));
        }
        markdown.append("\nInspect any key with `inspect ").append(displayPath(dir)).append(" <key>`.\n");
        return markdown.toString();
    }

    /**
     * Datasets whose real trace is listed in the run, present, complete and matches
     * {@link #expectedTraceHeader} for a real capture; only their rows may claim
     * captured evidence. A deleted, truncated, corrupt, null-member or foreign
     * trace is treated as not captured.
     */
    private static Set<String> verifiedRealTraces(final Path dir, final JsonObject run) {
        final Set<String> traced = new HashSet<>();
        for (final JsonElement element : run.getAsJsonArray("datasets")) {
            final JsonObject dataset = element.getAsJsonObject();
            final String id = dataset.get("id").getAsString();
            final String name = realTraceName(id);
            final Path file = dir.resolve(name);
            if (!dataset.getAsJsonArray("traces").contains(new JsonPrimitive(name)) || !Files.isRegularFile(file)) {
                continue;
            }
            final JsonObject expected = expectedTraceHeader(run, dataset, ElliottResearchTrace.MODE_REAL, -1, -1);
            try {
                final ElliottResearchTrace.TraceFile parsed = ElliottResearchTrace.read(file, record -> false);
                if (parsed.complete() && headerMismatch(parsed.header(), expected) == null) {
                    traced.add(id);
                }
            } catch (final IOException | IllegalArgumentException unreadable) {
                // An unreadable or corrupt trace is not evidence; the summary offers a
                // recapture.
            }
        }
        return traced;
    }

    /**
     * Header fields a trace must carry to belong to this run, this dataset and this
     * capture: code revision, configuration fingerprint and source digest bind it
     * to the run, so a trace copied in from another run with equal coordinates is
     * refused.
     */
    private static JsonObject expectedTraceHeader(final JsonObject run, final JsonObject dataset,
            final String traceMode, final int blockLength, final int memberIndex) {
        final JsonObject expected = new JsonObject();
        expected.add("dataset", dataset.get("id"));
        expected.add("revision", run.get("revision"));
        expected.add("fingerprint", run.getAsJsonObject("identity").get("fingerprint"));
        final JsonElement sourceSha256 = dataset.getAsJsonObject("source").get("sha256");
        expected.add("sourceSha256", sourceSha256 == null ? JsonNull.INSTANCE : sourceSha256);
        expected.addProperty("traceMode", traceMode);
        expected.addProperty("nullBlockLength", blockLength);
        expected.addProperty("nullMemberIndex", memberIndex);
        return expected;
    }

    /** @return the first expected header field the trace does not carry, or null */
    private static String headerMismatch(final JsonObject header, final JsonObject expected) {
        for (final Map.Entry<String, JsonElement> field : expected.entrySet()) {
            if (!field.getValue().equals(header.get(field.getKey()))) {
                return field.getKey();
            }
        }
        return null;
    }

    /**
     * Renders a path for a copy-pasteable command: relative to the working
     * directory when the path lies below it (so a run directory reads the same
     * wherever the repository is checked out), otherwise absolute.
     */
    private static String displayPath(final Path path) {
        final Path absolute = path.toAbsolutePath().normalize();
        final Path cwd = Path.of("").toAbsolutePath().normalize();
        return absolute.startsWith(cwd) && !absolute.equals(cwd) ? cwd.relativize(absolute).toString()
                : absolute.toString();
    }

    /**
     * Quotes one value for the exec plugin's argument parser, which honours single
     * and double quotes without escapes and joins adjacent quoted segments into one
     * argument, so any path survives intact.
     */
    private static String execQuoted(final String value) {
        if (value.indexOf('"') < 0) {
            return "\"" + value + "\"";
        }
        if (value.indexOf('\'') < 0) {
            return "'" + value + "'";
        }
        return "\"" + value.replace("\"", "\"'\"'\"") + "\"";
    }

    /** Single-quotes one POSIX shell word so no character in it is interpreted. */
    private static String shellQuoted(final String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    /**
     * Builds the exact command that captures a missing trace kind for the same
     * recipe into a sibling directory. Paths are quoted for the exec argument
     * parser and the whole argument string for a POSIX shell; the run directory is
     * taken from where the caller currently reads it, never from a stored absolute
     * location.
     */
    private static String recaptureCommand(final Path dir, final JsonObject run, final String mode, final Integer block,
            final Integer member) {
        final JsonObject recipe = run.getAsJsonObject("recipe");
        final StringBuilder args = new StringBuilder("run ").append(recipe.get("name").getAsString())
                .append(" --trace ")
                .append(mode);
        if (ElliottResearchTrace.MODE_SELECTED_NULL_MEMBER.equals(mode)) {
            args.append(" --block ").append(block).append(" --member ").append(member);
        }
        if (!recipe.get("source").isJsonNull()) {
            args.append(" --source ").append(execQuoted(recipe.get("source").getAsString()));
            args.append(" --recipe ").append(execQuoted(recipe.get("recipeFile").getAsString()));
            final String sourceSha256 = run.getAsJsonArray("datasets")
                    .get(0)
                    .getAsJsonObject()
                    .getAsJsonObject("source")
                    .get("sha256")
                    .getAsString();
            args.append(" --expect-source-sha256 ").append(sourceSha256);
        }
        // The recorded identity makes the recapture fail on edited inputs instead of
        // pairing a trace from different data or settings with this run's rows.
        args.append(" --expect-fingerprint ").append(run.getAsJsonObject("identity").get("fingerprint").getAsString());
        args.append(" --out ").append(execQuoted(displayPath(dir) + "-recapture"));
        return "mvn -q -pl ta4j-core test-compile exec:java " + shellQuoted("-Dexec.args=" + args);
    }

    private static int summarizeCommand(final List<String> args, final PrintStream out) throws IOException {
        if (args.size() != 1 || args.get(0).startsWith("--")) {
            throw new IllegalArgumentException("summarize takes exactly one run directory");
        }
        final Path dir = Path.of(args.get(0)).toAbsolutePath().normalize();
        final JsonObject run = loadRun(dir);
        final Path comparisons = dir.resolve(COMPARISONS_FILE);
        if (!Files.isRegularFile(comparisons)) {
            throw new Diagnostic(COMPARISONS_FILE + " is missing in " + dir + " (run status "
                    + run.get("status").getAsString() + "); the run did not finish, rerun the recipe");
        }
        final List<Row> rows = ElliottResearchReport.readCsv(comparisons);
        Files.writeString(dir.resolve(SUMMARY_FILE), renderSummary(dir, run, rows), StandardCharsets.UTF_8);
        out.println("Summary: " + dir.resolve(SUMMARY_FILE));
        return 0;
    }

    // -------------------------------------------------------------- inspect

    private static int inspectCommand(final List<String> args, final PrintStream out) throws IOException {
        Path dir = null;
        String keyText = null;
        String candidate = null;
        Integer asOf = null;
        int limit = DEFAULT_LIMIT;
        for (int index = 0; index < args.size(); index++) {
            final String argument = args.get(index);
            if (Set.of("--as-of", "--candidate", "--limit").contains(argument)) {
                if (index + 1 >= args.size()) {
                    throw new IllegalArgumentException("option " + argument + " requires a value");
                }
                final String value = args.get(++index);
                if ("--candidate".equals(argument)) {
                    candidate = value;
                } else {
                    final int number;
                    try {
                        number = Integer.parseInt(value);
                    } catch (final NumberFormatException e) {
                        throw new IllegalArgumentException(
                                "option " + argument + " needs an integer, was '" + value + "'");
                    }
                    if ("--as-of".equals(argument)) {
                        asOf = number;
                    } else if (number < 1) {
                        throw new IllegalArgumentException("--limit must be positive, was " + number);
                    } else {
                        limit = number;
                    }
                }
            } else if (argument.startsWith("--")) {
                throw new IllegalArgumentException("unknown option " + argument);
            } else if (dir == null) {
                dir = Path.of(argument).toAbsolutePath().normalize();
            } else if (keyText == null) {
                keyText = argument;
            } else {
                throw new IllegalArgumentException("unexpected argument '" + argument + "'");
            }
        }
        if (dir == null || keyText == null) {
            throw new IllegalArgumentException("inspect needs a run directory and a comparison key");
        }
        final ElliottResearchReport.Key key = ElliottResearchReport.parseKey(keyText);
        final JsonObject run = loadRun(dir);
        final JsonObject dataset = findDataset(run, key.dataset());
        if (!"complete".equals(dataset.get("status").getAsString())) {
            throw new Diagnostic("dataset '" + key.dataset() + "' is " + dataset.get("status").getAsString() + ": "
                    + dataset.get("message").getAsString() + " (rerun the recipe to recompute it)");
        }
        final Path comparisons = dir.resolve(COMPARISONS_FILE);
        if (!Files.isRegularFile(comparisons)) {
            throw new Diagnostic(COMPARISONS_FILE + " is missing in " + dir + "; rerun the recipe");
        }
        final List<Row> rows = ElliottResearchReport.readCsv(comparisons);
        Row row = null;
        for (final Row candidateRow : rows) {
            if (candidateRow.key().equals(key.toString())) {
                row = candidateRow;
                break;
            }
        }
        if (row == null) {
            throw new IllegalArgumentException(unknownKeyMessage(key, rows));
        }
        new Inspection(dir, run, dataset, row, limit).print(out, asOf, candidate);
        return 0;
    }

    private static JsonObject findDataset(final JsonObject run, final String id) {
        final List<String> ids = new ArrayList<>();
        for (final JsonElement element : run.getAsJsonArray("datasets")) {
            final JsonObject dataset = element.getAsJsonObject();
            if (id.equals(dataset.get("id").getAsString())) {
                return dataset;
            }
            ids.add(dataset.get("id").getAsString());
        }
        throw new IllegalArgumentException("unknown dataset '" + id + "'; the run holds " + ids);
    }

    private static String unknownKeyMessage(final ElliottResearchReport.Key key, final List<Row> rows) {
        final List<String> wanted = List.of(key.dataset(), key.section(), key.mode(), key.detector(), key.partition(),
                key.metric(), String.valueOf(key.nullBlockLength()));
        final TreeMap<String, Integer> scores = new TreeMap<>();
        for (final Row row : rows) {
            final ElliottResearchReport.Key candidate = ElliottResearchReport.parseKey(row.key());
            final List<String> parts = List.of(candidate.dataset(), candidate.section(), candidate.mode(),
                    candidate.detector(), candidate.partition(), candidate.metric(),
                    String.valueOf(candidate.nullBlockLength()));
            int shared = 0;
            for (int index = 0; index < parts.size(); index++) {
                shared += parts.get(index).equals(wanted.get(index)) ? 1 : 0;
            }
            scores.put(row.key(), shared);
        }
        final List<String> close = scores.entrySet()
                .stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue()
                        .reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(5)
                .map(entry -> "  " + entry.getKey())
                .toList();
        return "unknown comparison key " + key + "; closest keys in comparisons.csv:\n" + String.join("\n", close);
    }

    /** Read-only view of the recorded observations behind one comparison row. */
    private static final class Inspection {
        private final Path dir;
        private final JsonObject run;
        private final JsonObject dataset;
        private final Row row;
        private final int limit;

        Inspection(final Path dir, final JsonObject run, final JsonObject dataset, final Row row, final int limit) {
            this.dir = dir;
            this.run = run;
            this.dataset = dataset;
            this.row = row;
            this.limit = limit;
        }

        /**
         * One loaded trace: {@code file.records()} holds only the records the loader
         * retained, and {@code scope} the subset in the row's scope.
         */
        private record View(String label, String relative, ElliottResearchTrace.TraceFile file,
                List<JsonObject> scope) {
        }

        void print(final PrintStream out, final Integer asOf, final String candidate) throws IOException {
            // Real: retain the whole family so cross-mode rule disagreements stay visible.
            final View real = load("real", findTrace(realSuffixMatcher()),
                    expectedTraceHeader(run, dataset, ElliottResearchTrace.MODE_REAL, -1, -1), this::sameFamily,
                    this::inRealScope, ElliottResearch.recapture(dir, run, ElliottResearchTrace.MODE_REAL, null, null));
            // A run that selected no member captured no null trace, so -1 rejects any found
            // one.
            final JsonElement member = run.getAsJsonObject("trace").get("member");
            final View nullView = row.nullBlockLength() > 0 ? load("null", findTrace(nullMatcher()),
                    expectedTraceHeader(run, dataset, ElliottResearchTrace.MODE_SELECTED_NULL_MEMBER,
                            row.nullBlockLength(), member == null || member.isJsonNull() ? -1 : member.getAsInt()),
                    this::inNullScope, this::inNullScope, recaptureNull()) : null;
            if (real == null && nullView == null) {
                throw new Diagnostic(
                        "no trace was captured for key " + row.key() + " in " + dir + ". Recapture with:\n  "
                                + ElliottResearch.recapture(dir, run, ElliottResearchTrace.MODE_REAL, null, null)
                                + (row.nullBlockLength() > 0 ? "\n  " + recaptureNull() : ""));
            }
            out.println("Key: " + row.key());
            out.println("Dataset: " + row.dataset() + " (" + row.asset() + ")  section: " + row.section() + "  mode: "
                    + row.mode() + "  grammar: " + row.grammar() + "  detector: " + row.detector() + "  partition: "
                    + row.partition());
            out.println(replayLine(real == null));
            out.println(
                    "Active rules: " + (row.activeRules().isEmpty() ? "(none)" : String.join(", ", row.activeRules())));
            out.println("Metric: " + row.metric() + "  observed=" + number(row.observed()) + "  numerator="
                    + (row.numerator() == null ? "-" : row.numerator()) + "  denominator="
                    + (row.denominator() == null ? "-" : row.denominator()));
            out.println("Null reference (block " + (row.nullBlockLength() > 0 ? row.nullBlockLength() : "-")
                    + "): median=" + number(row.nullMedian()) + "  band=" + number(row.nullLow()) + ".."
                    + number(row.nullHigh()) + "  observed-median=" + number(row.observedMinusNullMedian()) + "  rank="
                    + number(row.empiricalReferenceRank()) + "  valid/requested=" + row.validNullMembers() + "/"
                    + row.requestedNullMembers() + "  availability=" + row.availability());
            printFlags(out);
            if (real == null) {
                out.println("Note: real trace not captured; the observed side cannot be verified. Recapture with:\n  "
                        + ElliottResearch.recapture(dir, run, ElliottResearchTrace.MODE_REAL, null, null));
            } else {
                printReal(out, real);
            }
            if (nullView != null) {
                printNull(out, nullView);
            } else if (row.nullBlockLength() > 0) {
                out.println("Note: no selected null-member trace for block " + row.nullBlockLength()
                        + ". Capture with:\n  " + recaptureNull());
            }
            final View focus = real != null ? real : nullView;
            if (asOf != null) {
                printAsOf(out, focus, asOf);
            }
            if (candidate != null) {
                printCandidate(out, focus, candidate);
            }
        }

        /**
         * The command that replays this row's trace in the examples-module viewer, or
         * the reason it cannot: runs written before price bars were persisted lack
         * them, and a selected null member records only h1 and h2 rows. The viewer
         * reads a real trace by default, so a run that retained only a selected null
         * member names that mode.
         */
        private String replayLine(final boolean nullMemberOnly) {
            if (dataset.get("priceBars") == null || dataset.get("priceBars").isJsonNull()) {
                return "Replay: unavailable, this run persisted no price bars. Regenerate the run to enable replay.";
            }
            if (nullMemberOnly && !"h1".equals(row.section()) && !"h2".equals(row.section())) {
                return "Replay: unavailable, this run retained only a selected null member, which records only h1"
                        + " and h2 rows. Recapture with --trace real to replay this row.";
            }
            final String args = execQuoted(displayPath(dir)) + " --key " + execQuoted(row.key())
                    + (nullMemberOnly ? " --trace " + ElliottResearchTrace.MODE_SELECTED_NULL_MEMBER : "");
            return "Replay: mvn -q -pl ta4j-examples exec:java"
                    + " -Dexec.mainClass=ta4jexamples.charting.replay.ElliottReplayInspector "
                    + shellQuoted("-Dexec.args=" + args)
                    + "  (run `mvn -pl ta4j-examples -am install -DskipTests` once first)";
        }

        private void printFlags(final PrintStream out) {
            final List<String> flags = new ArrayList<>();
            if (Double.isFinite(row.observed()) && Double.isFinite(row.nullLow()) && Double.isFinite(row.nullHigh())
                    && (row.observed() < row.nullLow() || row.observed() > row.nullHigh())) {
                flags.add("observed outside the null reference band");
            }
            if ("partial".equals(datasetCoverage())) {
                flags.add("dataset coverage is partial ("
                        + dataset.getAsJsonObject("coverage").get("message").getAsString() + ")");
            }
            if (!flags.isEmpty()) {
                out.println("Flags (navigation aids only): " + String.join("; ", flags));
            }
        }

        private String datasetCoverage() {
            return dataset.getAsJsonObject("coverage").get("status").getAsString();
        }

        private void printReal(final PrintStream out, final View real) {
            out.println("Real trace: " + real.relative() + " (complete, " + real.file().recordCount() + " records, "
                    + real.scope().size() + " in this row's scope)");
            out.println("Status tallies: " + tally(real.scope()));
            final Attribution attribution = attribute(real.scope());
            if (attribution == null) {
                out.println("Support check: " + row.metric()
                        + " is not attributable to single as-of records; tallies only.");
            } else {
                final boolean evidence = "evidencePassRate".equals(row.metric())
                        || "jointPassRate".equals(row.metric());
                final String expected = (evidence ? "recomputed " : "records ") + attribution.numerator + "/"
                        + attribution.denominator;
                final boolean matches = row.denominator() != null && row.denominator() == attribution.denominator
                        && row.numerator() != null && row.numerator() == attribution.numerator;
                out.println("Support check: " + expected + " vs row " + row.numerator() + "/" + row.denominator()
                        + " -> " + (matches ? "match" : "DIFFERS"));
                out.println("Supportive as-of indices (" + attribution.supportive.size() + "): "
                        + limited(attribution.supportive));
                out.println("Counterexample as-of indices (" + attribution.counter.size() + "): "
                        + limited(attribution.counter));
            }
            printDisagreements(out, real.file());
        }

        private void printNull(final PrintStream out, final View nullView) {
            out.println("Selected null member trace: " + nullView.relative() + " (block "
                    + nullView.file().header().get("nullBlockLength").getAsInt() + ", member "
                    + nullView.file().header().get("nullMemberIndex").getAsInt() + ", " + nullView.file().recordCount()
                    + " records, " + nullView.scope().size() + " in this row's scope)");
            out.println("Null member status tallies: " + tally(nullView.scope()));
            final Attribution attribution = attribute(nullView.scope());
            if (attribution != null && attribution.denominator > 0) {
                out.println("Null member " + row.metric() + ": " + attribution.numerator + "/" + attribution.denominator
                        + " = " + number((double) attribution.numerator / attribution.denominator));
            }
        }

        private void printDisagreements(final PrintStream out, final ElliottResearchTrace.TraceFile file) {
            final Map<RuleAt, Map<String, List<String>>> states = new TreeMap<>(RuleAt.ORDER);
            for (final JsonObject record : file.records()) {
                if (!sameFamily(record)) {
                    continue;
                }
                final JsonArray candidates = record.getAsJsonArray("candidates");
                if (candidates == null) {
                    continue;
                }
                for (final JsonElement candidateElement : candidates) {
                    final JsonObject candidate = candidateElement.getAsJsonObject();
                    for (final JsonElement ruleElement : candidate.getAsJsonArray("rules")) {
                        final JsonObject rule = ruleElement.getAsJsonObject();
                        final RuleAt at = new RuleAt(record.get("asOfIndex").getAsInt(),
                                candidate.get("candidateKey").getAsString(), rule.get("id").getAsString());
                        states.computeIfAbsent(at, ignored -> new TreeMap<>())
                                .computeIfAbsent(rule.get("state").getAsString(), ignored -> new ArrayList<>())
                                .add(record.get("mode").getAsString());
                    }
                }
            }
            final List<String> disagreements = states.entrySet()
                    .stream()
                    .filter(entry -> entry.getValue().size() > 1)
                    .map(entry -> "  as-of " + entry.getKey().asOfIndex() + " candidate "
                            + entry.getKey().candidateKey() + " rule " + entry.getKey().ruleId() + ": "
                            + entry.getValue())
                    .toList();
            if (disagreements.isEmpty()) {
                out.println("Rule disagreements across modes: none");
            } else {
                out.println("Rule disagreements across modes (" + disagreements.size() + ", showing "
                        + Math.min(limit, disagreements.size()) + "):");
                disagreements.stream().limit(limit).forEach(out::println);
            }
        }

        /** One rule evaluation site, ordered by as-of index, candidate and rule. */
        private record RuleAt(int asOfIndex, String candidateKey, String ruleId) {

            private static final Comparator<RuleAt> ORDER = Comparator.comparingInt(RuleAt::asOfIndex)
                    .thenComparing(RuleAt::candidateKey)
                    .thenComparing(RuleAt::ruleId);
        }

        private void printAsOf(final PrintStream out, final View view, final int asOf) {
            final List<JsonObject> hits = view.scope()
                    .stream()
                    .filter(record -> record.get("asOfIndex").getAsInt() == asOf)
                    .toList();
            if (hits.isEmpty()) {
                final IntSummary range = indexRange(view.scope());
                throw new IllegalArgumentException("no record at as-of index " + asOf + " in this row's scope"
                        + (range == null ? "" : "; recorded indices span " + range.min + ".." + range.max));
            }
            out.println("As-of " + asOf + ":");
            for (final JsonObject record : hits) {
                out.println("  " + record.get("asOfTime").getAsString() + " " + record.get("kind").getAsString()
                        + " status=" + status(record) + " direction=" + textOf(record, "direction") + " pivots="
                        + (record.has("pivots") ? record.getAsJsonArray("pivots").size() : 0) + " labels="
                        + (record.has("labels") ? record.get("labels") : "[]"));
                final JsonArray candidates = record.getAsJsonArray("candidates");
                if (candidates == null) {
                    continue;
                }
                for (final JsonElement candidateElement : candidates) {
                    final JsonObject candidate = candidateElement.getAsJsonObject();
                    out.println("    candidate " + candidate.get("version").getAsString() + " direction="
                            + candidate.get("direction").getAsString());
                    for (final JsonElement ruleElement : candidate.getAsJsonArray("rules")) {
                        final JsonObject rule = ruleElement.getAsJsonObject();
                        out.println("      " + rule.get("id").getAsString() + " " + rule.get("state").getAsString()
                                + " score=" + (rule.get("score").isJsonNull() ? "-" : rule.get("score").getAsString())
                                + " - " + rule.get("explanation").getAsString());
                    }
                }
            }
        }

        private void printCandidate(final PrintStream out, final View view, final String candidateKey) {
            final List<String> history = new ArrayList<>();
            for (final JsonObject record : view.scope()) {
                final JsonArray candidates = record.getAsJsonArray("candidates");
                if (candidates == null) {
                    continue;
                }
                for (final JsonElement candidateElement : candidates) {
                    final JsonObject candidate = candidateElement.getAsJsonObject();
                    if (!candidate.get("candidateKey").getAsString().startsWith(candidateKey)
                            && !candidate.get("version").getAsString().equals(candidateKey)) {
                        continue;
                    }
                    final StringBuilder states = new StringBuilder();
                    for (final JsonElement ruleElement : candidate.getAsJsonArray("rules")) {
                        final JsonObject rule = ruleElement.getAsJsonObject();
                        states.append(states.length() == 0 ? "" : ", ")
                                .append(rule.get("id").getAsString())
                                .append('=')
                                .append(rule.get("state").getAsString());
                    }
                    history.add("  as-of " + record.get("asOfIndex").getAsInt() + " status=" + status(record) + " "
                            + candidate.get("version").getAsString() + " [" + states + "]");
                }
            }
            if (history.isEmpty()) {
                throw new IllegalArgumentException(
                        "candidate '" + candidateKey + "' does not appear in this row's scope");
            }
            out.println("Candidate " + candidateKey + " history (" + history.size() + " observations, showing "
                    + Math.min(limit, history.size()) + "):");
            history.stream().limit(limit).forEach(out::println);
        }

        // ---- scope selection

        private boolean inRealScope(final JsonObject record) {
            return sameFamily(record) && row.mode().equals(textOf(record, "mode"));
        }

        /** Records of the row's section, grammar, detector and partition, any mode. */
        private boolean sameFamily(final JsonObject record) {
            return row.section().equals(textOf(record, "section")) && row.grammar().equals(textOf(record, "grammar"))
                    && row.detector().equals(textOf(record, "detector"))
                    && row.partition().equals(textOf(record, "partition"));
        }

        /**
         * Null topology records carry the grammar name as their mode, except for H2
         * ablation modes which keep their own name. H1 and competing rows have real
         * modes ("topology-only", "competing-*") that never appear on null records.
         */
        private boolean inNullScope(final JsonObject record) {
            final String nullMode = "h2".equals(row.section()) ? row.mode() : row.grammar();
            return "null".equals(textOf(record, "section")) && row.grammar().equals(textOf(record, "grammar"))
                    && row.partition().equals(textOf(record, "partition")) && nullMode.equals(textOf(record, "mode"));
        }

        private java.util.function.Predicate<String> realSuffixMatcher() {
            return name -> name.endsWith("-real.jsonl");
        }

        private java.util.function.Predicate<String> nullMatcher() {
            final Pattern pattern = Pattern.compile(".*-null-b" + row.nullBlockLength() + "-m\\d+\\.jsonl");
            return name -> pattern.matcher(name).matches();
        }

        private String findTrace(final java.util.function.Predicate<String> matcher) {
            for (final JsonElement element : dataset.getAsJsonArray("traces")) {
                if (matcher.test(element.getAsString())) {
                    return element.getAsString();
                }
            }
            return null;
        }

        private String recaptureNull() {
            return ElliottResearch.recapture(dir, run, ElliottResearchTrace.MODE_SELECTED_NULL_MEMBER,
                    row.nullBlockLength(), memberOrZero());
        }

        private int memberOrZero() {
            final JsonElement member = run.getAsJsonObject("trace").get("member");
            return member == null || member.isJsonNull() ? 0 : member.getAsInt();
        }

        private View load(final String label, final String relative, final JsonObject expected,
                final java.util.function.Predicate<JsonObject> retain,
                final java.util.function.Predicate<JsonObject> scope, final String recapture) throws IOException {
            if (relative == null) {
                return null;
            }
            final Path file = dir.resolve(relative);
            if (!Files.isRegularFile(file)) {
                return null;
            }
            final ElliottResearchTrace.TraceFile trace;
            try {
                trace = ElliottResearchTrace.read(file, retain);
            } catch (final IllegalArgumentException corrupt) {
                throw new Diagnostic("corrupt " + label + " trace " + relative + ": " + corrupt.getMessage()
                        + ". Recapture with:\n  " + recapture);
            }
            if (!trace.complete()) {
                throw new Diagnostic("truncated " + label + " trace " + relative + " (" + trace.recordCount()
                        + " records, no footer): the capturing run did not finish. Recapture with:\n  " + recapture);
            }
            final String mismatch = headerMismatch(trace.header(), expected);
            if (mismatch != null) {
                throw new Diagnostic(label + " trace " + relative + " was captured for " + mismatch + " "
                        + trace.header().get(mismatch) + ", not this run's " + expected.get(mismatch)
                        + ". Recapture with:\n  " + recapture);
            }
            return new View(label, relative, trace, trace.records().stream().filter(scope).toList());
        }

        // ---- metric attribution

        private static final class Attribution {
            private long numerator;
            private long denominator;
            private final List<Integer> supportive = new ArrayList<>();
            private final List<Integer> counter = new ArrayList<>();
        }

        private Attribution attribute(final List<JsonObject> records) {
            final String metric = row.metric();
            if (!Set.of("completeOccupancyRate", "ambiguousRate", "noMatchRate", "evidencePassRate", "jointPassRate")
                    .contains(metric)) {
                return null;
            }
            final Attribution attribution = new Attribution();
            for (final JsonObject record : records) {
                final int asOf = record.get("asOfIndex").getAsInt();
                final String status = status(record);
                long numerator = 0;
                long denominator = 0;
                switch (metric) {
                case "completeOccupancyRate" -> {
                    denominator = 1;
                    numerator = "complete".equals(status) ? 1 : 0;
                }
                case "ambiguousRate" -> {
                    denominator = 1;
                    numerator = "ambiguous".equals(status) ? 1 : 0;
                }
                case "noMatchRate" -> {
                    denominator = 1;
                    numerator = "no-match".equals(status) ? 1 : 0;
                }
                default -> {
                    if ("complete".equals(status) && record.getAsJsonArray("candidates") != null) {
                        for (final JsonElement candidateElement : record.getAsJsonArray("candidates")) {
                            final JsonArray rules = candidateElement.getAsJsonObject().getAsJsonArray("rules");
                            long passes = 0;
                            for (final JsonElement rule : rules) {
                                passes += "PASS".equals(rule.getAsJsonObject().get("state").getAsString()) ? 1 : 0;
                            }
                            if ("evidencePassRate".equals(metric)) {
                                numerator += passes;
                                denominator += rules.size();
                            } else if (rules.size() > 0) {
                                numerator += passes == rules.size() ? 1 : 0;
                                denominator += 1;
                            }
                        }
                    }
                }
                }
                attribution.numerator += numerator;
                attribution.denominator += denominator;
                if (denominator > 0) {
                    (numerator == denominator ? attribution.supportive : attribution.counter).add(asOf);
                }
            }
            return attribution;
        }

        private String limited(final List<Integer> values) {
            if (values.isEmpty()) {
                return "none";
            }
            final String head = values.stream()
                    .limit(limit)
                    .map(String::valueOf)
                    .collect(java.util.stream.Collectors.joining(", "));
            return values.size() > limit ? head + " (+" + (values.size() - limit) + " more)" : head;
        }
    }

    private record IntSummary(int min, int max) {
    }

    private static IntSummary indexRange(final List<JsonObject> records) {
        if (records.isEmpty()) {
            return null;
        }
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (final JsonObject record : records) {
            final int index = record.get("asOfIndex").getAsInt();
            min = Math.min(min, index);
            max = Math.max(max, index);
        }
        return new IntSummary(min, max);
    }

    private static String recapture(final Path dir, final JsonObject run, final String mode, final Integer block,
            final Integer member) {
        return recaptureCommand(dir, run, mode, block, member);
    }

    private static String tally(final List<JsonObject> records) {
        final TreeMap<String, Integer> counts = new TreeMap<>();
        for (final JsonObject record : records) {
            counts.merge(status(record), 1, Integer::sum);
        }
        return counts.isEmpty() ? "no records" : counts.toString();
    }

    private static String status(final JsonObject record) {
        return record.get("status").getAsString().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String textOf(final JsonObject record, final String key) {
        final JsonElement element = record.get(key);
        return element == null || element.isJsonNull() ? "" : element.getAsString();
    }

    private static String number(final double value) {
        return Double.isFinite(value) ? new BigDecimal(value, new MathContext(6)).stripTrailingZeros().toPlainString()
                : "NaN";
    }
}

/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import java.io.IOException;
import java.io.OutputStream;
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
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
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
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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
 *     [--rank enrollment|probability] [--retrospective]
 * </pre>
 *
 * <p>
 * Exit codes: {@code 0} success, {@code 1} usage error or run failure (also a
 * partial or failed run, an unknown key or as-of index), {@code 2} inspection
 * diagnostic (missing, truncated or corrupt trace, failed dataset) or
 * incomplete run (an outcome or calibration artifact that is missing, does not
 * hold the record count {@code run.json} declares for it, or has no declared
 * count). With no captured trace, {@code inspect} may instead print applicable
 * calibration estimates with a "real trace not captured" note and exit
 * {@code 0}. Rows outside the fitted calibration scope still require a trace.
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
    private static final List<String> MANAGED_FILES = List.of(RUN_FILE, COMPARISONS_FILE, COVERAGE_FILE, SUMMARY_FILE,
            ElliottResearchOutcomes.EVENTS_FILE, ElliottResearchOutcomes.OUTCOMES_FILE,
            ElliottResearchOutcomes.SUMMARY_FILE, ElliottResearchCalibration.TABLES_FILE,
            ElliottResearchCalibration.PREDICTIONS_FILE, ElliottResearchCalibration.SUMMARY_FILE,
            ElliottResearchCalibration.RELIABILITY_FILE);
    private static final List<String> MANAGED_DIRECTORIES = List.of(REPORTS_DIR, TRACES_DIR);
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
                "      --expect-revision REV        fail unless the current code revision is REV",
                "  summarize <runDir>       regenerate summary.md from the recorded artifacts",
                "  inspect <runDir> <key> [--as-of IDX] [--candidate KEY] [--limit N]",
                "          [--rank enrollment|probability] [--retrospective]",
                "                           list the recorded observations behind one comparison key; a calibrated",
                "                           run also lists decision-time probabilities (outcomes only with",
                "                           --retrospective, probability ranking only with --rank)", "",
                "Run status: complete = every dataset evaluated (coverage.csv marks datasets whose data cover",
                "less than the requested window as partial), partial = some dataset failed, failed = all failed.",
                "Every run also writes events.jsonl (enrolled MOTIVE_5 event lifecycles), outcomes.csv (per-horizon",
                "structural and price labels) and outcomes-summary.csv (per-partition tallies and comparators);",
                "explore recipes may set an optional \"outcomes\" object (horizons, structuralMode, invalidation).",
                "A recipe may also set \"calibration\" (horizon, fit, validation, evaluation, minGroups) to add",
                "calibration-tables.json, calibration-predictions.csv, calibration-summary.csv and",
                "calibration-reliability.csv: outcome-calibrated event probabilities, fitted chronologically.",
                "Null-member events are included for the member chosen with --trace selected-null-member.", "",
                "Exit codes: 0 ok; 1 usage error or run failure; 2 missing, truncated or corrupt trace, failed",
                "dataset while inspecting, or an incomplete run (an outcome or calibration artifact that is missing,",
                "does not hold the record count run.json declares for it, or has none: rerun the recipe). A run that",
                "declares calibration but captured no trace still exits 0 for the primary detector's H1 MOTIVE_5",
                "scope: inspect prints its calibration estimates with a \"real trace not captured\" note.",
                "Robustness-detector, H2 and competing-grammar rows still require a captured trace.");
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
            Path source, Path recipeFile, String expectFingerprint, String expectSourceSha256, String expectRevision) {
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
                            "--expect-source-sha256", "--expect-revision")
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
                values.get("--expect-fingerprint"), values.get("--expect-source-sha256"),
                values.get("--expect-revision"));
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

        /** @return the factory and parameters, e.g. {@code fractal(3)} */
        String configuration() {
            return factory + params.stream().map(String::valueOf).collect(Collectors.joining(",", "(", ")"));
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
            List<String> competingModes, List<Integer> blockLengths, int ensembleSize, long seed,
            List<String> structuralModes, ElliottResearchOutcomes.Settings outcomes,
            ElliottResearchCalibration.Settings calibration) {

        Setup {
            if (!structuralModes.contains(outcomes.structuralMode())) {
                throw new IllegalArgumentException("outcomes.structuralMode '" + outcomes.structuralMode()
                        + "' is not a mode this run evaluates; expected one of " + structuralModes);
            }
        }

        StudyRunner runner() {
            final List<DetectorRobustnessMatrix.DetectorSpec> specs = robustness.stream()
                    .map(detector -> new DetectorRobustnessMatrix.DetectorSpec(detector.name(), detector.supplier()))
                    .toList();
            return new StudyRunner(primary.supplier(), List.of(TopologyGrammar.MOTIVE_5, TopologyGrammar.CYCLE_5_3),
                    rules(activeRules, momentumBarCount), new StudyRunner.Configuration(partitions, fingerprint, seed,
                            blockLengths, ensembleSize, specs, primary.name(), competingModes));
        }

        private static List<String> structuralModes(final List<String> activeRules, final int momentumBarCount) {
            return RuleAblation.modes(rules(activeRules, momentumBarCount))
                    .stream()
                    .map(RuleAblation.Mode::name)
                    .toList();
        }

        private static List<RelationshipRule> rules(final List<String> activeRules, final int momentumBarCount) {
            final Function<BarSeries, Indicator<Num>> momentum = series -> new RSIIndicator(
                    new ClosePriceIndicator(series), momentumBarCount);
            return configuredRules(activeRules, ClassicalRelationshipRules.classicalRelationships(momentum));
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
            json.add("outcomes", outcomes.toJson());
            if (calibration != null) {
                json.add("calibration", calibration.toJson());
            }
            return json;
        }
    }

    private static List<RelationshipRule> configuredRules(final List<String> activeRules,
            final List<RelationshipRule> registered) {
        final List<RelationshipRule> rules = new ArrayList<>(activeRules.size());
        for (final String id : activeRules) {
            for (final RelationshipRule rule : registered) {
                if (id.equals(rule.id())) {
                    rules.add(rule);
                    break;
                }
            }
        }
        if (rules.size() != activeRules.size()) {
            throw new IllegalArgumentException("activeRules contains an unregistered relationship rule");
        }
        return rules;
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
                + "RSI14; competing 3+3,5+5,change-point-baseline; null block 20 ensemble 8 seed 5252026; "
                + "outcomes horizons 5,20,60 all-rules origin-pivot";
        return new Setup(sha256(description.getBytes(StandardCharsets.UTF_8)), partitions, w5, List.of(w3, w5),
                RULE_IDS, 14, COMPETING_MODES, List.of(20), 8, 5_252_026L, Setup.structuralModes(RULE_IDS, 14),
                ElliottResearchOutcomes.Settings.defaults(), null);
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
                protocol.nullEnsemble().seed(), RuleAblation.frozenModeNames(), frozenOutcomeSettings(), null);
    }

    static ElliottResearchOutcomes.Settings frozenOutcomeSettings() {
        return new ElliottResearchOutcomes.Settings(ElliottResearchOutcomes.DEFAULT_HORIZONS, "classical-all",
                ElliottResearchOutcomes.Invalidation.ORIGIN_PIVOT);
    }

    private static final Set<String> RECIPE_FIELDS = Set.of("datasetId", "asset", "partitions",
            "forbiddenCalibrationStart", "detector", "robustnessDetectors", "activeRules", "momentum", "competingModes",
            "null", "outcomes", "calibration");

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
        final String asset = csvLabel(required(root, "asset", "recipe"), "recipe.asset");
        final List<StudyRunner.Partition> partitions = new ArrayList<>();
        final JsonArray partitionArray = arrayOf(required(root, "partitions", "recipe"), "recipe.partitions");
        for (int index = 0; index < partitionArray.size(); index++) {
            final String where = "recipe.partitions[" + index + "]";
            final JsonObject entry = objectOf(partitionArray.get(index), where);
            rejectUnknown(entry, where, Set.of("name", "start", "end"));
            final String name = csvLabel(required(entry, "name", where), where + ".name");
            ElliottResearchReport.requireKeyPart(name, where + ".name");
            partitions.add(new StudyRunner.Partition(name, date(required(entry, "start", where), where + ".start"),
                    date(required(entry, "end", where), where + ".end")));
        }
        final StudyRunner.Partitions runnerPartitions;
        try {
            runnerPartitions = new StudyRunner.Partitions(partitions,
                    date(required(root, "forbiddenCalibrationStart", "recipe"), "recipe.forbiddenCalibrationStart"));
            final StudyRunner.Partition calibration = runnerPartitions.calibration();
            final StudyRunner.Partition validation = runnerPartitions.validation();
            final StudyRunner.Partition holdout = runnerPartitions.holdout();
            if (!calibration.end().isBefore(validation.start()) || !validation.end().isBefore(holdout.start())) {
                throw new IllegalArgumentException(
                        "calibration -> validation -> holdout must be in chronological order");
            }
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
        final ElliottResearchOutcomes.Settings outcomes = root.has("outcomes")
                ? ElliottResearchOutcomes.Settings.parse(objectOf(root.get("outcomes"), "recipe.outcomes"))
                : ElliottResearchOutcomes.Settings.defaults();
        final ElliottResearchCalibration.Settings calibration;
        try {
            calibration = root.has("calibration")
                    ? ElliottResearchCalibration.Settings.parse(objectOf(root.get("calibration"), "recipe.calibration"),
                            outcomes, partitions)
                    : null;
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException("recipe " + e.getMessage());
        }
        rejectUnknown(nulls, "recipe.null", Set.of("blockLengths", "ensembleSize", "seed"));
        final List<Integer> blockLengths = new ArrayList<>();
        final JsonArray blocks = arrayOf(required(nulls, "blockLengths", "recipe.null"), "recipe.null.blockLengths");
        for (int index = 0; index < blocks.size(); index++) {
            blockLengths.add(integer(blocks.get(index), "recipe.null.blockLengths[" + index + "]"));
        }
        final int ensembleSize = integer(required(nulls, "ensembleSize", "recipe.null"), "recipe.null.ensembleSize");
        final long seed = longValue(required(nulls, "seed", "recipe.null"), "recipe.null.seed");
        final Setup setup = new Setup("explore-" + sha256(recipeBytes), runnerPartitions, primary, robustness,
                activeRules, barCount, competing, blockLengths, ensembleSize, seed,
                Setup.structuralModes(activeRules, barCount), outcomes, calibration);
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
        final String name = csvLabel(required(object, "name", where), where + ".name");
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

    private static String csvLabel(final JsonElement element, final String where) {
        final String value = text(element, where);
        if ("=+-@\t\r".indexOf(value.charAt(0)) >= 0) {
            throw new IllegalArgumentException(where + " must not start with a spreadsheet formula character");
        }
        for (int i = 0; i < value.length(); i++) {
            final char character = value.charAt(i);
            if (Character.isISOControl(character) || character == '\u2028' || character == '\u2029') {
                throw new IllegalArgumentException(where + " must not contain control characters or line separators");
            }
        }
        return value;
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
        final String revision = gitRevision();
        // Sample before the run can dirty the checkout with its own output directory.
        final String worktree = gitWorktreeState();
        if (options.expectRevision() != null) {
            requireRecordedRevision(options.expectRevision(), revision, worktree);
        }
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
        if (options.overwrite() && RECIPE_EXPLORE.equals(options.recipe())) {
            final Path realDir = Files.exists(dir) ? dir.toRealPath() : dir;
            requirePreservedInput(dir, realDir, options.source());
            requirePreservedInput(dir, realDir, options.recipeFile());
        }
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
        final FileChannel channel = lockRunDirectory(dir);
        try {
            prepareRunDirectory(dir, overwrite);
            return channel;
        } catch (final IOException | RuntimeException e) {
            channel.close();
            throw e;
        }
    }

    /** Holds the shared writer lock until the returned channel closes. */
    private static FileChannel lockRunDirectory(final Path dir) throws IOException {
        final FileChannel channel = FileChannel.open(dir.resolve(LOCK_FILE), StandardOpenOption.CREATE,
                StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
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

    private static void requirePreservedInput(final Path dir, final Path realDir, final Path input) throws IOException {
        if (isManagedArtifact(dir, input.toAbsolutePath().normalize())
                || isManagedArtifact(realDir, input.toRealPath())) {
            throw new IllegalArgumentException("--overwrite would remove explore input " + input
                    + "; move --source and --recipe outside the run's managed artifact paths before rerunning");
        }
    }

    private static boolean isManagedArtifact(final Path dir, final Path input) {
        for (final String file : MANAGED_FILES) {
            if (input.equals(dir.resolve(file))) {
                return true;
            }
        }
        for (final String directory : MANAGED_DIRECTORIES) {
            if (input.startsWith(dir.resolve(directory))) {
                return true;
            }
        }
        return false;
    }

    private static void prepareRunDirectory(final Path dir, final boolean overwrite) throws IOException {
        if (checkRunDirectory(dir, overwrite)) {
            for (final String file : MANAGED_FILES) {
                Files.deleteIfExists(dir.resolve(file));
            }
            for (final String directory : MANAGED_DIRECTORIES) {
                deleteTree(dir.resolve(directory));
            }
        }
        Files.createDirectories(dir.resolve(REPORTS_DIR));
        Files.createDirectories(dir.resolve(TRACES_DIR));
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
        private final Map<String, String> traceSha256 = new LinkedHashMap<>();

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
        private final Map<String, ElliottResearchEvents> recorders = new HashMap<>();
        private final Map<String, ElliottResearchOutcomes.Result> outcomes = new LinkedHashMap<>();
        private final Map<String, ElliottResearchCalibration.Computation> calibrations = new LinkedHashMap<>();
        private String numFactory = "unknown";
        private String status = "running";
        private JsonObject outcomeRecords;
        private JsonObject calibrationRecords;

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
            writeRunJson(toJson());
        }

        StudyObserver observer(final String id) throws IOException {
            final ElliottResearchEvents recorder = new ElliottResearchEvents(setup.outcomes().structuralMode(),
                    setup.outcomes().maxHorizon());
            recorders.put(id, recorder);
            if (!ElliottResearchTrace.MODE_REAL.equals(options.trace())) {
                return recorder;
            }
            final ElliottResearchTrace trace = ElliottResearchTrace.open(dir.resolve(realTraceName(id)), id, revision,
                    setup.fingerprint(), sourceSha256(id), ElliottResearchTrace.MODE_REAL, -1, -1);
            openTraces.put(id, trace);
            return ElliottResearchEvents.tee(trace, recorder);
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
                final ElliottResearchTrace real = openTraces.remove(id);
                if (real != null) {
                    real.close();
                    entry.traces.add(relative(dir.resolve(realTraceName(id))));
                    entry.traceSha256.put(relative(dir.resolve(realTraceName(id))),
                            sha256(dir.resolve(realTraceName(id))));
                }
                final ElliottResearchEvents recorder = recorders.get(id);
                recorder.bindRealTape(series);
                if (ElliottResearchTrace.MODE_SELECTED_NULL_MEMBER.equals(options.trace())) {
                    final Path file = dir.resolve(nullTraceName(id, options.block(), options.member()));
                    try (ElliottResearchTrace trace = ElliottResearchTrace.open(file, id, revision, setup.fingerprint(),
                            sourceSha256(id), ElliottResearchTrace.MODE_SELECTED_NULL_MEMBER, options.block(),
                            options.member())) {
                        runner.replayNullMember(series, series.getBeginIndex(), series.getEndIndex(), options.block(),
                                options.member(), ElliottResearchEvents.tee(trace, recorder));
                    }
                    entry.traces.add(relative(file));
                    entry.traceSha256.put(relative(file), sha256(file));
                }
                outcomes.put(id, ElliottResearchOutcomes.evaluate(id, recorder, setup.outcomes()));
                if (setup.calibration() != null) {
                    final Map<String, Integer> lastObserved = new HashMap<>();
                    for (final ElliottResearchEvents.Stream stream : recorder.streams()) {
                        if (stream.key().real()) {
                            lastObserved.put(stream.key().partition(), stream.lastObserved());
                        }
                    }
                    final List<RelationshipRule> rules = Setup.rules(setup.activeRules(), setup.momentumBarCount());
                    calibrations.put(id, ElliottResearchCalibration.compute(outcomes.get(id), lastObserved,
                            setup.calibration(),
                            ElliottResearchCalibration.Identity.of(id, setup.primary().name(),
                                    setup.primary().configuration(), setup.activeRules(),
                                    "RSI(" + setup.momentumBarCount() + ")", setup.outcomes(), setup.calibration()),
                            new ElliottResearchCalibration.Provenance(setup.fingerprint(), revision),
                            event -> ElliottResearchCalibration.enrollmentEvidence(event, rules, series)));
                }
                recorders.remove(id);
                rows.addAll(ElliottResearchReport.comparisons(id, report));
                entry.report = relative(reportFile);
                applyCoverage(entry, series);
                entry.status = "complete";
                writeRunJson(toJson());
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
            final Bar[] firstObserved = new Bar[partitions.size()];
            final Bar[] previous = new Bar[partitions.size()];
            int internalGaps = 0;
            Instant widestFrom = null;
            Instant widestTo = null;
            for (int index = series.getBeginIndex(); index <= series.getEndIndex(); index++) {
                final Bar bar = series.getBar(index);
                final LocalDate date = bar.getBeginTime().atZone(ZoneOffset.UTC).toLocalDate();
                if (date.isBefore(from) || date.isAfter(to)) {
                    continue;
                }
                for (int partition = 0; partition < partitions.size(); partition++) {
                    final StudyRunner.Partition window = partitions.get(partition);
                    if (!window.contains(date)) {
                        continue;
                    }
                    first = first == null || date.isBefore(first) ? date : first;
                    last = last == null || date.isAfter(last) ? date : last;
                    count++;
                    final Duration tolerance = bar.getTimePeriod().multipliedBy(INTERNAL_GAP_BAR_PERIODS);
                    if (firstObserved[partition] == null) {
                        firstObserved[partition] = bar;
                    }
                    // Only missing bars within this partition affect its as-of windows;
                    // intervals between configured partitions are intentionally unrequested.
                    final Bar prior = previous[partition];
                    if (prior != null
                            && Duration.between(prior.getBeginTime(), bar.getBeginTime()).compareTo(tolerance) > 0) {
                        internalGaps++;
                        if (widestFrom == null || Duration.between(prior.getBeginTime(), bar.getBeginTime())
                                .compareTo(Duration.between(widestFrom, widestTo)) > 0) {
                            widestFrom = prior.getBeginTime();
                            widestTo = bar.getBeginTime();
                        }
                    }
                    previous[partition] = bar;
                    break;
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
            }
            for (int partition = 0; first != null && partition < partitions.size(); partition++) {
                final StudyRunner.Partition window = partitions.get(partition);
                final Bar firstBar = firstObserved[partition];
                final Bar lastBar = previous[partition];
                if (firstBar == null) {
                    gaps.add("partition " + window.name() + " (" + window.start() + ".." + window.end()
                            + ") has no bars");
                    continue;
                }
                final Instant start = window.start().atStartOfDay(ZoneOffset.UTC).toInstant();
                final Instant end = window.end().atStartOfDay(ZoneOffset.UTC).toInstant();
                final Instant firstDay = firstBar.getBeginTime()
                        .atZone(ZoneOffset.UTC)
                        .toLocalDate()
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant();
                final Instant lastDay = lastBar.getBeginTime()
                        .atZone(ZoneOffset.UTC)
                        .toLocalDate()
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant();
                if (Duration.between(start, firstDay)
                        .compareTo(firstBar.getTimePeriod().multipliedBy(INTERNAL_GAP_BAR_PERIODS)) > 0) {
                    gaps.add("partition " + window.name() + " starts at " + firstBar.getBeginTime() + ", more than "
                            + INTERNAL_GAP_BAR_PERIODS + " bar periods after " + start);
                }
                if (Duration.between(lastDay, end)
                        .compareTo(lastBar.getTimePeriod().multipliedBy(INTERNAL_GAP_BAR_PERIODS)) > 0) {
                    gaps.add("partition " + window.name() + " ends at " + lastBar.getBeginTime() + ", more than "
                            + INTERNAL_GAP_BAR_PERIODS + " bar periods before " + end);
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
            } catch (final IOException ignored) {
                // best effort: run.json records no trace or report for a failed dataset
            }
            rows.removeIf(row -> row.dataset().equals(id));
            recorders.remove(id);
            outcomes.remove(id);
            calibrations.remove(id);
            entry.traces.clear();
            entry.traceSha256.clear();
            entry.report = null;
            entry.bars = 0;
            entry.effectiveFrom = null;
            entry.effectiveTo = null;
            entry.status = "failed";
            entry.message = failure.getClass().getSimpleName() + ": " + failure.getMessage();
            entry.coverageStatus = "failed";
            entry.coverageMessage = entry.message;
            try {
                writeRunJson(toJson());
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        void finish() throws IOException {
            final long complete = entries.values().stream().filter(entry -> "complete".equals(entry.status)).count();
            status = complete == entries.size() ? "complete" : complete == 0 ? "failed" : "partial";
            final List<ElliottResearchOutcomes.Result> results = List.copyOf(outcomes.values());
            ElliottResearchReport.writeCsv(dir.resolve(COMPARISONS_FILE), rows);
            ElliottResearchOutcomes.writeEvents(dir.resolve(ElliottResearchOutcomes.EVENTS_FILE), setup.fingerprint(),
                    setup.outcomes(), results);
            ElliottResearchOutcomes.writeOutcomes(dir.resolve(ElliottResearchOutcomes.OUTCOMES_FILE), results);
            ElliottResearchOutcomes.writeSummary(dir.resolve(ElliottResearchOutcomes.SUMMARY_FILE), results);
            if (setup.calibration() != null) {
                final List<ElliottResearchCalibration.Computation> computations = List.copyOf(calibrations.values());
                ElliottResearchCalibration.writeTables(dir.resolve(ElliottResearchCalibration.TABLES_FILE),
                        computations);
                ElliottResearchCalibration.writePredictions(dir.resolve(ElliottResearchCalibration.PREDICTIONS_FILE),
                        computations);
                ElliottResearchCalibration.writeSummary(dir.resolve(ElliottResearchCalibration.SUMMARY_FILE),
                        computations);
                ElliottResearchCalibration.writeReliability(dir.resolve(ElliottResearchCalibration.RELIABILITY_FILE),
                        computations);
                calibrationRecords = ElliottResearchCalibration.recordCounts(computations);
            }
            outcomeRecords = ElliottResearchOutcomes.recordCounts(results);
            final JsonObject json = toJson();
            json.addProperty("comparisonsSha256", sha256(Files.readAllBytes(dir.resolve(COMPARISONS_FILE))));
            ElliottResearchReport.writeCoverage(dir.resolve(COVERAGE_FILE), coverage(json));
            // run.json declares the final status and the outcome and calibration record
            // counts, so it is written after every artifact it vouches for: a run
            // interrupted while publishing keeps the "running" status and no counts of its
            // last checkpoint, which readers reject as incomplete.
            writeRunJson(json);
            writeSummary(dir, renderSummary(dir, json, rows));
        }

        /**
         * Publishes only a complete metadata snapshot. If atomic replacement is
         * unsupported or any write fails, the previous run file remains recoverable.
         */
        private void writeRunJson(final JsonObject json) throws IOException {
            ElliottResearch.writeRunJson(dir, json, MetadataFiles.DEFAULT);
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
                final JsonObject coverage = new JsonObject();
                coverage.addProperty("status", entry.coverageStatus);
                coverage.addProperty("message", entry.coverageMessage);
                dataset.add("coverage", coverage);
                dataset.add("report", entry.report == null ? JsonNull.INSTANCE : new JsonPrimitive(entry.report));
                dataset.add("traces", stringArray(entry.traces));
                final JsonObject traceSha256 = new JsonObject();
                entry.traceSha256.forEach(traceSha256::addProperty);
                dataset.add("traceSha256", traceSha256);
                datasets.add(dataset);
            }
            json.add("datasets", datasets);
            final JsonObject outcomeFiles = new JsonObject();
            outcomeFiles.addProperty("schema", ElliottResearchOutcomes.SCHEMA);
            outcomeFiles.addProperty("events", ElliottResearchOutcomes.EVENTS_FILE);
            outcomeFiles.addProperty("outcomes", ElliottResearchOutcomes.OUTCOMES_FILE);
            outcomeFiles.addProperty("summary", ElliottResearchOutcomes.SUMMARY_FILE);
            if (outcomeRecords != null) {
                outcomeFiles.add("records", outcomeRecords);
            }
            json.add("outcomes", outcomeFiles);
            if (setup.calibration() != null) {
                final JsonObject calibrationFiles = new JsonObject();
                calibrationFiles.addProperty("schema", ElliottResearchCalibration.SCHEMA);
                calibrationFiles.addProperty("tables", ElliottResearchCalibration.TABLES_FILE);
                calibrationFiles.addProperty("predictions", ElliottResearchCalibration.PREDICTIONS_FILE);
                calibrationFiles.addProperty("summary", ElliottResearchCalibration.SUMMARY_FILE);
                calibrationFiles.addProperty("reliability", ElliottResearchCalibration.RELIABILITY_FILE);
                if (calibrationRecords != null) {
                    calibrationFiles.add("records", calibrationRecords);
                }
                json.add("calibration", calibrationFiles);
            }
            return json;
        }
    }

    private static String realTraceName(final String datasetId) {
        return TRACES_DIR + "/" + datasetId + "-real.jsonl";
    }

    private static String nullTraceName(final String datasetId, final int block, final int member) {
        return TRACES_DIR + "/" + datasetId + "-null-b" + block + "-m" + member + ".jsonl";
    }

    static void requireRecordedRevision(final String expected, final String revision, final String worktree) {
        if ("unknown".equals(expected) || "unknown".equals(revision)) {
            throw new IllegalArgumentException("recorded revision cannot be verified"
                    + "; check out the recorded revision in a separate worktree before recapturing");
        }
        if (!expected.equals(revision)) {
            throw new IllegalArgumentException("current revision is " + revision + ", not the recorded revision "
                    + expected + "; check out the recorded revision in a separate worktree before recapturing");
        }
        if (!"clean".equals(worktree)) {
            throw new IllegalArgumentException(
                    "revision-bound recapture requires a clean worktree, but this checkout is " + worktree
                            + "; check out the recorded revision in a separate worktree before recapturing");
        }
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

    private static String sha256(final Path path) throws IOException {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream input = new DigestInputStream(Files.newInputStream(path), digest)) {
                input.transferTo(OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static void verifyTraceDigest(final Path file, final JsonObject dataset, final String relative)
            throws IOException {
        final JsonElement digests = dataset.get("traceSha256");
        final JsonElement digest = digests != null && digests.isJsonObject() ? digests.getAsJsonObject().get(relative)
                : null;
        if (digest == null || !digest.isJsonPrimitive() || !digest.getAsJsonPrimitive().isString()
                || !digest.getAsString().matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("trace " + relative + " has no valid recorded SHA-256 in run.json");
        }
        if (!sha256(file).equals(digest.getAsString())) {
            throw new IllegalArgumentException(
                    "trace " + relative + " SHA-256 differs from run.json; the artifact bundle was modified or mixed");
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
        final Path file = resolveInside(dir, RUN_FILE);
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

    /**
     * Resolves an artifact name recorded in a run directory. A run directory can
     * come from someone else, so absolute names, traversal and links that leave it
     * are refused instead of read.
     */
    private static Path resolveInside(final Path dir, final String relative) throws IOException {
        final Path base = dir.toAbsolutePath().normalize();
        final Path resolved = base.resolve(relative).normalize();
        final boolean inside = !Path.of(relative).isAbsolute() && resolved.startsWith(base)
                && (!Files.exists(resolved) || resolved.toRealPath().startsWith(base.toRealPath()));
        if (!inside) {
            throw new IllegalArgumentException("'" + relative + "' lies outside the run directory " + dir);
        }
        return resolved;
    }

    /**
     * Replaces {@code summary.md} without writing through what it was: an existing
     * symlink or hard link would otherwise redirect the regenerated Markdown to its
     * target.
     */
    private static void writeSummary(final Path dir, final String markdown) throws IOException {
        final Path file = dir.resolve(SUMMARY_FILE);
        Files.deleteIfExists(file);
        Files.writeString(file, markdown, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
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

    private static String renderSummary(final Path dir, final JsonObject run, final List<Row> rows) throws IOException {
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
        if (run.has("outcomes")) {
            // A run that declares outcome artifacts is incomplete without them; only a
            // run written before outcomes existed legitimately has none.
            final Path outcomesSummary = verifiedOutcomes(dir, run.get("outcomes"));
            markdown.append('\n').append(ElliottResearchOutcomes.summaryMarkdown(outcomesSummary));
            markdown.append("\nEvent lifecycles are in `")
                    .append(ElliottResearchOutcomes.EVENTS_FILE)
                    .append("`, per-horizon labels in `")
                    .append(ElliottResearchOutcomes.OUTCOMES_FILE)
                    .append("`.\n");
        }
        if (run.has("calibration")) {
            // Likewise for calibration: a header-only summary of a run that scored
            // datasets must not read as "no dataset produced calibration evidence".
            final CalibrationFiles calibration = verifiedCalibration(dir, run.get("calibration"));
            markdown.append('\n')
                    .append(ElliottResearchCalibration.summaryMarkdown(calibration.summary(),
                            calibration.reliability()));
        }
        markdown.append("\nInspect any key with `inspect ").append(displayPath(dir)).append(" <key>`.\n");
        return markdown.toString();
    }

    /**
     * Resolves the outcome artifacts a run declares and checks them against the
     * record counts {@code run.json} carries. Those counts are written after the
     * artifacts, so a run interrupted while publishing has none, and artifacts
     * truncated afterwards no longer match: both are incomplete, never summarized.
     *
     * @return the verified {@code outcomes-summary.csv}
     */
    private static Path verifiedOutcomes(final Path dir, final JsonElement declared) throws IOException {
        final Path events = runArtifact(dir, ElliottResearchOutcomes.EVENTS_FILE, "outcome");
        final Path outcomes = runArtifact(dir, ElliottResearchOutcomes.OUTCOMES_FILE, "outcome");
        final Path summary = runArtifact(dir, ElliottResearchOutcomes.SUMMARY_FILE, "outcome");
        final JsonObject records = declaredRecords(dir, declared, "outcome");
        try {
            ElliottResearchOutcomes.verifyArtifacts(events, outcomes, summary, records);
        } catch (final IllegalArgumentException incomplete) {
            throw new Diagnostic(incomplete.getMessage() + "; the run is incomplete, rerun the recipe");
        }
        return summary;
    }

    /** The verified calibration artifacts of one run directory. */
    private record CalibrationFiles(Path tables, Path predictions, Path summary, Path reliability) {
    }

    /**
     * Resolves the calibration artifacts a run declares and checks them against the
     * record counts {@code run.json} carries, as {@link #verifiedOutcomes} does for
     * the outcome artifacts. A header-only summary, reliability, or prediction file
     * of a run that scored datasets, a table file that lost a table, and artifacts
     * truncated after publishing no longer match: all are incomplete, never
     * summarized or inspected.
     *
     * @return the verified calibration artifacts
     */
    private static CalibrationFiles verifiedCalibration(final Path dir, final JsonElement declared) throws IOException {
        final Path tables = runArtifact(dir, ElliottResearchCalibration.TABLES_FILE, "calibration");
        final Path predictions = runArtifact(dir, ElliottResearchCalibration.PREDICTIONS_FILE, "calibration");
        final Path summary = runArtifact(dir, ElliottResearchCalibration.SUMMARY_FILE, "calibration");
        final Path reliability = runArtifact(dir, ElliottResearchCalibration.RELIABILITY_FILE, "calibration");
        final JsonObject records = declaredRecords(dir, declared, "calibration");
        try {
            ElliottResearchCalibration.verifyArtifacts(tables, predictions, summary, reliability, records);
        } catch (final IllegalArgumentException incomplete) {
            throw new Diagnostic(incomplete.getMessage() + "; the run is incomplete, rerun the recipe");
        }
        return new CalibrationFiles(tables, predictions, summary, reliability);
    }

    private static JsonObject declaredRecords(final Path dir, final JsonElement declared, final String kind) {
        final JsonElement records = declared.isJsonObject() ? declared.getAsJsonObject().get("records") : null;
        if (records == null || !records.isJsonObject()) {
            throw new Diagnostic(RUN_FILE + " declares the " + kind + " artifacts without their record counts in " + dir
                    + "; the run did not finish publishing, rerun the recipe");
        }
        return records.getAsJsonObject();
    }

    private static Path runArtifact(final Path dir, final String name, final String kind) throws IOException {
        final Path file;
        try {
            file = resolveInside(dir, name);
        } catch (final IllegalArgumentException outside) {
            throw new Diagnostic(
                    kind + " artifact " + outside.getMessage() + "; refusing to read it, rerun the recipe");
        }
        if (!Files.isRegularFile(file)) {
            throw new Diagnostic(name + " is declared by " + RUN_FILE + " but missing in " + dir
                    + "; the run is incomplete, rerun the recipe");
        }
        return file;
    }

    /**
     * Datasets whose real trace is listed in the run, present, complete and matches
     * {@link #expectedTraceHeader} for a real capture; only their rows may claim
     * captured evidence. A deleted, truncated, corrupt, null-member, foreign or
     * out-of-directory trace is treated as not captured.
     */
    private static Set<String> verifiedRealTraces(final Path dir, final JsonObject run) {
        final Set<String> traced = new HashSet<>();
        for (final JsonElement element : run.getAsJsonArray("datasets")) {
            final JsonObject dataset = element.getAsJsonObject();
            final String id = dataset.get("id").getAsString();
            final String name = realTraceName(id);
            if (!dataset.getAsJsonArray("traces").contains(new JsonPrimitive(name))) {
                continue;
            }
            final JsonObject expected = expectedTraceHeader(run, dataset, ElliottResearchTrace.MODE_REAL, -1, -1);
            try {
                final Path file = resolveInside(dir, name);
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                final ElliottResearchTrace.TraceFile parsed = ElliottResearchTrace.read(file, record -> false);
                if (parsed.complete() && headerMismatch(parsed.header(), expected) == null) {
                    verifyTraceDigest(file, dataset, name);
                    traced.add(id);
                }
            } catch (final IOException | IllegalArgumentException unreadable) {
                // An unreadable, corrupt or out-of-directory trace is not evidence; the
                // summary offers a recapture.
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
        // Bind the recapture to the original code, inputs and settings.
        args.append(" --expect-fingerprint ").append(run.getAsJsonObject("identity").get("fingerprint").getAsString());
        args.append(" --expect-revision ").append(run.get("revision").getAsString());
        args.append(" --out ").append(execQuoted(displayPath(dir) + "-recapture"));
        return "mvn -q -pl ta4j-core test-compile exec:java " + shellQuoted("-Dexec.args=" + args);
    }

    private static int summarizeCommand(final List<String> args, final PrintStream out) throws IOException {
        if (args.size() != 1 || args.get(0).startsWith("--")) {
            throw new IllegalArgumentException("summarize takes exactly one run directory");
        }
        final Path dir = Path.of(args.get(0)).toAbsolutePath().normalize();
        loadRun(dir);
        try (FileChannel lock = lockRunDirectory(dir)) {
            final JsonObject run = loadRun(dir);
            final Path comparisons = resolveInside(dir, COMPARISONS_FILE);
            if (!Files.isRegularFile(comparisons)) {
                throw new Diagnostic(COMPARISONS_FILE + " is missing in " + dir + " (run status "
                        + run.get("status").getAsString() + "); the run did not finish, rerun the recipe");
            }
            final List<Row> rows = readVerifiedComparisons(comparisons, run);
            writeSummary(dir, renderSummary(dir, run, rows));
            out.println("Summary: " + dir.resolve(SUMMARY_FILE));
            return 0;
        }
    }

    private static List<Row> readVerifiedComparisons(final Path path, final JsonObject run) throws IOException {
        final JsonElement digest = run.get("comparisonsSha256");
        if (digest == null || !digest.isJsonPrimitive() || !digest.getAsJsonPrimitive().isString()) {
            throw new Diagnostic(COMPARISONS_FILE
                    + " has no recorded SHA-256 in run.json; rerun the recipe to produce a bound artifact bundle");
        }
        final byte[] bytes = Files.readAllBytes(path);
        if (!sha256(bytes).equals(digest.getAsString())) {
            throw new Diagnostic(COMPARISONS_FILE
                    + " SHA-256 differs from run.json; the artifact bundle was modified or mixed, rerun the recipe");
        }
        final List<Row> rows = ElliottResearchReport.readCsv(path, bytes);
        final Map<String, ComparisonFamily> expected;
        try {
            expected = expectedComparisonKeys(run);
        } catch (final IllegalArgumentException | IllegalStateException | NullPointerException e) {
            throw new Diagnostic(COMPARISONS_FILE + " row-key inventory cannot be derived from run.json: "
                    + e.getMessage() + "; rerun the recipe to regenerate the artifact bundle");
        }
        final Set<String> missing = new TreeSet<>(expected.keySet());
        final Set<String> unexpected = new TreeSet<>();
        for (final Row row : rows) {
            if (!missing.remove(row.key())) {
                unexpected.add(row.key());
            }
        }
        if (!missing.isEmpty() || !unexpected.isEmpty()) {
            throw new Diagnostic(
                    COMPARISONS_FILE + " row-key inventory differs from run.json's recorded configuration: "
                            + "missing " + missing.size() + " key(s) " + missing.stream().limit(5).toList()
                            + "; unexpected " + unexpected.size() + " key(s) " + unexpected.stream().limit(5).toList()
                            + "; rerun the recipe to regenerate the complete artifact bundle");
        }
        for (final Row row : rows) {
            final ComparisonFamily family = expected.get(row.key());
            final String mismatch = !family.asset().equals(row.asset()) ? "asset"
                    : !family.grammar().equals(row.grammar()) ? "grammar"
                            : !family.activeRules().equals(row.activeRules()) ? "activeRules"
                                    : !family.policy().equals(row.policy()) ? "policy"
                                            : family.requestedNullMembers() != row.requestedNullMembers()
                                                    ? "requestedNullMembers"
                                                    : null;
            if (mismatch != null) {
                throw new Diagnostic(COMPARISONS_FILE + " family metadata " + mismatch
                        + " differs from run.json's recorded configuration for comparison " + row.key()
                        + "; rerun the recipe to regenerate the artifact bundle");
            }
        }
        return rows;
    }

    /**
     * Derives the producer's complete row shape without reading reports or traces.
     */
    private static Map<String, ComparisonFamily> expectedComparisonKeys(final JsonObject run) {
        final JsonObject recipe = objectOf(required(run, "recipe", RUN_FILE), "run.json.recipe");
        final String recipeName = text(required(recipe, "name", "run.json.recipe"), "run.json.recipe.name");
        if (!RECIPES.contains(recipeName)) {
            throw new IllegalArgumentException("unknown recorded recipe '" + recipeName + "'");
        }
        final String where = "run.json.configuration";
        final JsonObject configuration = objectOf(required(run, "configuration", RUN_FILE), where);
        final List<String> partitions = new ArrayList<>();
        final JsonArray partitionEntries = arrayOf(required(configuration, "partitions", where), where + ".partitions");
        for (int index = 0; index < partitionEntries.size(); index++) {
            final String partitionWhere = where + ".partitions[" + index + "]";
            final JsonObject partition = objectOf(partitionEntries.get(index), partitionWhere);
            partitions.add(text(required(partition, "name", partitionWhere), partitionWhere + ".name"));
        }
        if (partitions.isEmpty()) {
            throw new IllegalArgumentException(where + ".partitions must not be empty");
        }
        final String primary = detector(required(configuration, "detector", where), where + ".detector").name();
        final List<String> robustness = new ArrayList<>();
        final JsonArray detectors = arrayOf(required(configuration, "robustnessDetectors", where),
                where + ".robustnessDetectors");
        for (int index = 0; index < detectors.size(); index++) {
            robustness.add(detector(detectors.get(index), where + ".robustnessDetectors[" + index + "]").name());
        }
        final List<String> activeRules = strings(required(configuration, "activeRules", where), where + ".activeRules",
                RULE_IDS);
        if (activeRules.isEmpty()) {
            throw new IllegalArgumentException(where + ".activeRules must not be empty");
        }
        final JsonObject momentum = objectOf(required(configuration, "momentum", where), where + ".momentum");
        final int momentumBarCount = integer(required(momentum, "barCount", where + ".momentum"),
                where + ".momentum.barCount");
        final Function<BarSeries, Indicator<Num>> momentumFactory = series -> new RSIIndicator(
                new ClosePriceIndicator(series), momentumBarCount);
        final List<RelationshipRule> rules = configuredRules(activeRules,
                ClassicalRelationshipRules.classicalRelationships(momentumFactory));
        final List<RuleAblation.Mode> modes = RECIPE_FROZEN.equals(recipeName) ? RuleAblation.frozenModes(rules)
                : RuleAblation.modes(rules);
        final List<String> competing = strings(required(configuration, "competingModes", where),
                where + ".competingModes", COMPETING_MODES);
        final JsonObject nulls = objectOf(required(configuration, "null", where), where + ".null");
        final List<Integer> blockLengths = new ArrayList<>();
        final JsonArray blocks = arrayOf(required(nulls, "blockLengths", where + ".null"),
                where + ".null.blockLengths");
        for (int index = 0; index < blocks.size(); index++) {
            final int block = integer(blocks.get(index), where + ".null.blockLengths[" + index + "]");
            if (block <= 0 || blockLengths.contains(block)) {
                throw new IllegalArgumentException(where + ".null.blockLengths must contain distinct positive values");
            }
            blockLengths.add(block);
        }
        if (blockLengths.isEmpty()) {
            throw new IllegalArgumentException(where + ".null.blockLengths must not be empty");
        }
        final int ensembleSize = integer(required(nulls, "ensembleSize", where + ".null"),
                where + ".null.ensembleSize");
        if (ensembleSize <= 0) {
            throw new IllegalArgumentException(where + ".null.ensembleSize must be positive");
        }
        final Map<String, ComparisonFamily> expected = new LinkedHashMap<>();
        final Set<String> datasetIds = new HashSet<>();
        final JsonArray datasets = arrayOf(required(run, "datasets", RUN_FILE), "run.json.datasets");
        for (int index = 0; index < datasets.size(); index++) {
            final String datasetWhere = "run.json.datasets[" + index + "]";
            final JsonObject dataset = objectOf(datasets.get(index), datasetWhere);
            final String id = text(required(dataset, "id", datasetWhere), datasetWhere + ".id");
            if (!datasetIds.add(id)) {
                throw new IllegalArgumentException("duplicate recorded dataset '" + id + "'");
            }
            if (!"complete".equals(text(required(dataset, "status", datasetWhere), datasetWhere + ".status"))) {
                continue;
            }
            final String asset = text(required(dataset, "asset", datasetWhere), datasetWhere + ".asset");
            addExpectedComparisonKeys(expected, id, "h1", "topology-only", primary, partitions, blockLengths,
                    new ComparisonFamily(asset, "MOTIVE_5", List.of(), "kernel-topology", ensembleSize));
            for (final RuleAblation.Mode mode : modes) {
                addExpectedComparisonKeys(expected, id, "h2", mode.name(), primary, partitions, blockLengths,
                        new ComparisonFamily(asset, "CYCLE_5_3", mode.ruleIds(),
                                mode.rules().isEmpty() ? "kernel-topology" : "kernel-topology+evidence", ensembleSize));
            }
            for (final String mode : competing) {
                addExpectedComparisonKeys(expected, id, "competing", "competing-" + mode, primary, partitions,
                        List.of(-1), new ComparisonFamily(asset, mode, List.of(), "unmatched-recognizer", 0));
            }
            for (final String detector : robustness) {
                addExpectedComparisonKeys(expected, id, "robustness", "topology-only", detector, partitions,
                        List.of(-1), new ComparisonFamily(asset, "MOTIVE_5", List.of(), "robustness-detector", 0));
            }
        }
        return expected;
    }

    /** Metadata shared by the metrics of one recorded study mode. */
    private record ComparisonFamily(String asset, String grammar, List<String> activeRules, String policy,
            int requestedNullMembers) {
    }

    private static void addExpectedComparisonKeys(final Map<String, ComparisonFamily> expected, final String dataset,
            final String section, final String mode, final String detector, final List<String> partitions,
            final List<Integer> blocks, final ComparisonFamily family) {
        for (final String partition : partitions) {
            for (final ElliottResearchReport.Metric metric : ElliottResearchReport.Metric.values()) {
                if (family.activeRules().isEmpty() && (metric == ElliottResearchReport.Metric.EVIDENCE_PASS
                        || metric == ElliottResearchReport.Metric.JOINT_PASS)) {
                    continue;
                }
                for (final int block : blocks) {
                    final String key = new ElliottResearchReport.Key(dataset, section, mode, detector, partition,
                            metric.id(), block).toString();
                    if (expected.putIfAbsent(key, family) != null) {
                        throw new IllegalArgumentException("recorded configuration produces duplicate key " + key);
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------- inspect

    private static int inspectCommand(final List<String> args, final PrintStream out) throws IOException {
        Path dir = null;
        String keyText = null;
        String candidate = null;
        Integer asOf = null;
        int limit = DEFAULT_LIMIT;
        String rank = ElliottResearchCalibration.RANK_ENROLLMENT;
        boolean retrospective = false;
        for (int index = 0; index < args.size(); index++) {
            final String argument = args.get(index);
            if (Set.of("--as-of", "--candidate", "--limit", "--rank").contains(argument)) {
                if (index + 1 >= args.size()) {
                    throw new IllegalArgumentException("option " + argument + " requires a value");
                }
                final String value = args.get(++index);
                if ("--candidate".equals(argument)) {
                    candidate = value;
                } else if ("--rank".equals(argument)) {
                    if (!ElliottResearchCalibration.RANK_ENROLLMENT.equals(value)
                            && !ElliottResearchCalibration.RANK_PROBABILITY.equals(value)) {
                        throw new IllegalArgumentException(
                                "--rank must be " + ElliottResearchCalibration.RANK_ENROLLMENT + " or "
                                        + ElliottResearchCalibration.RANK_PROBABILITY + ", was '" + value + "'");
                    }
                    rank = value;
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
            } else if ("--retrospective".equals(argument)) {
                retrospective = true;
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
        final Path comparisons = resolveInside(dir, COMPARISONS_FILE);
        final List<Row> rows = Files.isRegularFile(comparisons) ? readVerifiedComparisons(comparisons, run) : null;
        final JsonObject dataset = findDataset(run, key.dataset());
        if (!"complete".equals(dataset.get("status").getAsString())) {
            throw new Diagnostic("dataset '" + key.dataset() + "' is " + dataset.get("status").getAsString() + ": "
                    + dataset.get("message").getAsString() + " (rerun the recipe to recompute it)");
        }
        if (rows == null) {
            throw new Diagnostic(COMPARISONS_FILE + " is missing in " + dir + "; rerun the recipe");
        }
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
        new Inspection(dir, run, dataset, row, rows, limit, rank, retrospective).print(out, asOf, candidate);
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
        private final String rank;
        private final boolean retrospective;
        private final Map<String, List<String>> activeRulesByMode = new HashMap<>();

        Inspection(final Path dir, final JsonObject run, final JsonObject dataset, final Row row,
                final List<Row> allRows, final int limit, final String rank, final boolean retrospective) {
            this.dir = dir;
            this.run = run;
            this.dataset = dataset;
            this.row = row;
            this.limit = limit;
            this.rank = rank;
            this.retrospective = retrospective;
            for (final Row comparison : allRows) {
                if (row.dataset().equals(comparison.dataset()) && row.section().equals(comparison.section())
                        && row.grammar().equals(comparison.grammar()) && row.detector().equals(comparison.detector())
                        && row.partition().equals(comparison.partition())) {
                    activeRulesByMode.put(comparison.mode(), comparison.activeRules());
                }
            }
        }

        /**
         * One loaded trace: {@code file.records()} holds only the records the loader
         * retained, and {@code scope} the subset in the row's scope.
         */
        private record View(String label, String relative, ElliottResearchTrace.TraceFile file,
                List<JsonObject> scope) {
        }

        private void printCalibration(final PrintStream out, final CalibrationFiles files, final Integer asOf,
                final String candidate) throws IOException {
            final String mismatch = ElliottResearchCalibration.scopeMismatch(files.tables(), row.dataset(),
                    row.section(), row.grammar(), row.detector());
            if (mismatch != null) {
                out.println();
                out.println("Calibration: not applicable to this scope - the fitted tables describe only the primary"
                        + " detector's real " + ElliottResearchCalibration.GRAMMAR + " stream; this row differs in "
                        + mismatch);
                return;
            }
            ElliottResearchCalibration.inspect(out, files.predictions(), files.reliability(), row.dataset(),
                    row.partition(), candidate, asOf, limit, rank, retrospective);
        }

        void print(final PrintStream out, final Integer asOf, final String candidate) throws IOException {
            // Calibration artifacts are verified before anything is printed, so a
            // truncated run never shows partial evidence.
            final CalibrationFiles calibration = run.has("calibration")
                    ? verifiedCalibration(dir, run.get("calibration"))
                    : null;
            // Real: retain the whole family so cross-mode rule disagreements stay visible.
            final String realTrace = findTrace(realSuffixMatcher());
            final View real = load("real", realTrace,
                    expectedTraceHeader(run, dataset, ElliottResearchTrace.MODE_REAL, -1, -1), this::retainRealFamily,
                    this::inRealScope, ElliottResearch.recapture(dir, run, ElliottResearchTrace.MODE_REAL, null, null));
            // A run that selected no member captured no null trace, so -1 rejects any found
            // one.
            final JsonElement member = run.getAsJsonObject("trace").get("member");
            final String nullTrace = row.nullBlockLength() > 0 ? findTrace(nullMatcher()) : null;
            final View nullView = nullTrace != null
                    ? load("null", nullTrace,
                            expectedTraceHeader(run, dataset, ElliottResearchTrace.MODE_SELECTED_NULL_MEMBER,
                                    row.nullBlockLength(),
                                    member == null || member.isJsonNull() ? -1 : integer(member, "run.trace.member")),
                            this::retainNullScope, this::inNullScope, recaptureNull())
                    : null;
            // Only applicable calibration estimates can replace an uncaptured trace;
            // a declared trace that is missing is still an incomplete capture.
            final boolean traceDeclared = realTrace != null || nullTrace != null;
            final boolean calibrationApplies = calibration != null
                    && ElliottResearchCalibration.scopeMismatch(calibration.tables(), row.dataset(), row.section(),
                            row.grammar(), row.detector()) == null;
            if (real == null && nullView == null && (traceDeclared || !calibrationApplies)) {
                throw new Diagnostic(
                        "no trace was captured for key " + row.key() + " in " + dir + ". Recapture with:\n  "
                                + ElliottResearch.recapture(dir, run, ElliottResearchTrace.MODE_REAL, null, null)
                                + (row.nullBlockLength() > 0 ? "\n  " + recaptureNull() : ""));
            }
            out.println("Key: " + row.key());
            out.println("Dataset: " + row.dataset() + " (" + row.asset() + ")  section: " + row.section() + "  mode: "
                    + row.mode() + "  grammar: " + row.grammar() + "  detector: " + row.detector() + "  partition: "
                    + row.partition());
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
            if (focus != null && asOf != null) {
                printAsOf(out, focus, asOf);
            }
            if (focus != null && candidate != null) {
                printCandidate(out, focus, candidate);
            }
            if (calibration != null) {
                printCalibration(out, calibration, asOf, candidate);
            }
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
            if ("labelStabilityJaccard".equals(row.metric())) {
                final double mean = stability(real.scope());
                if (Double.compare(mean, row.observed()) != 0) {
                    throw new Diagnostic("trace label-stability transitions do not match the selected comparison row."
                            + " Recapture with:\n  "
                            + ElliottResearch.recapture(dir, run, ElliottResearchTrace.MODE_REAL, null, null));
                }
                out.println("Support check: adjacent label-set Jaccard mean=" + number(mean) + " from "
                        + Math.max(0, real.scope().size() - 1) + " transitions vs row " + number(row.observed())
                        + " -> " + (Double.isNaN(mean) ? "unavailable (no adjacent label-set transitions)" : "match"));
            } else if ("confirmationLagBars".equals(row.metric())) {
                final ConfirmationLag lag = confirmationLag(real.scope());
                if (Double.compare(lag.mean(), row.observed()) != 0) {
                    throw new Diagnostic("trace candidate-pivot confirmation lags do not match the selected comparison "
                            + "row. Recapture with:\n  "
                            + ElliottResearch.recapture(dir, run, ElliottResearchTrace.MODE_REAL, null, null));
                }
                out.println("Support check: mean candidate-pivot confirmation lag=" + number(lag.mean()) + " bars from "
                        + lag.count() + " complete candidate observations vs row " + number(row.observed()) + " -> "
                        + (Double.isNaN(lag.mean()) ? "unavailable (no complete topology candidates)" : "match"));
                out.println("Supporting as-of indices (" + lag.indices().size() + "): " + limited(lag.indices()));
            } else if (attribution == null) {
                out.println("Support check: " + row.metric()
                        + " is not attributable to single as-of records; tallies only.");
            } else {
                final boolean evidence = "evidencePassRate".equals(row.metric())
                        || "jointPassRate".equals(row.metric());
                final String expected = (evidence ? "recomputed " : "records ") + attribution.numerator + "/"
                        + attribution.denominator;
                final double observed = attribution.denominator == 0 ? Double.NaN
                        : attribution.numerator / (double) attribution.denominator;
                final boolean matches = row.denominator() != null && row.denominator() == attribution.denominator
                        && row.numerator() != null && row.numerator() == attribution.numerator
                        && Double.compare(observed, row.observed()) == 0;
                if (!matches) {
                    throw new Diagnostic(
                            "trace " + row.metric() + " contributions do not match the selected comparison row: "
                                    + expected + " = " + number(observed) + " vs row " + row.numerator() + "/"
                                    + row.denominator() + " = " + number(row.observed()) + ". Recapture with:\n  "
                                    + ElliottResearch.recapture(dir, run, ElliottResearchTrace.MODE_REAL, null, null));
                }
                out.println("Support check: " + expected + " vs row " + row.numerator() + "/" + row.denominator()
                        + " -> " + (attribution.denominator == 0 ? "unavailable (no contributions)" : "match"));
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
            if ("labelStabilityJaccard".equals(row.metric())) {
                out.println("Null member labelStabilityJaccard: adjacent label-set Jaccard mean="
                        + number(stability(nullView.scope())) + " from " + Math.max(0, nullView.scope().size() - 1)
                        + " transitions");
            } else if ("confirmationLagBars".equals(row.metric())) {
                final ConfirmationLag lag = confirmationLag(nullView.scope());
                out.println("Null member confirmationLagBars: mean candidate-pivot confirmation lag="
                        + number(lag.mean()) + " bars from " + lag.count() + " complete candidate observations");
                out.println("Null member supporting as-of indices (" + lag.indices().size() + "): "
                        + limited(lag.indices()));
            }
            final Attribution attribution = attribute(nullView.scope());
            if (attribution != null) {
                final double observed = attribution.denominator == 0 ? Double.NaN
                        : attribution.numerator / (double) attribution.denominator;
                out.println("Null member " + row.metric() + ": " + attribution.numerator + "/" + attribution.denominator
                        + " = " + number(observed));
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
                    if (!ElliottResearchTrace.selects(candidateKey, candidate.get("candidateKey").getAsString(),
                            candidate.get("version").getAsString())) {
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

        private boolean retainRealFamily(final JsonObject record) {
            if (!sameFamily(record)) {
                return false;
            }
            requireActiveRules(record, activeRulesByMode.get(textOf(record, "mode")));
            return true;
        }

        private boolean retainNullScope(final JsonObject record) {
            if (!inNullScope(record)) {
                return false;
            }
            requireActiveRules(record, row.activeRules());
            return true;
        }

        private static void requireActiveRules(final JsonObject record, final List<String> expected) {
            final JsonArray activeRules = record.getAsJsonArray("activeRules");
            boolean matches = expected != null && activeRules.size() == expected.size();
            for (int i = 0; matches && i < activeRules.size(); i++) {
                matches = expected.get(i).equals(activeRules.get(i).getAsString());
            }
            if (!matches) {
                throw new IllegalArgumentException("record activeRules " + activeRules
                        + " do not match comparisons.csv mode " + textOf(record, "mode") + " expected " + expected);
            }
        }

        /**
         * Null topology records carry the grammar name as their mode, except for H2
         * ablation modes which keep their own name. H1 and competing rows have real
         * modes ("topology-only", "competing-*") that never appear on null records.
         */
        private boolean inNullScope(final JsonObject record) {
            final String nullMode = "h2".equals(row.section()) ? row.mode() : row.grammar();
            return "null".equals(textOf(record, "section")) && row.grammar().equals(textOf(record, "grammar"))
                    && row.detector().equals(textOf(record, "detector"))
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
            return member == null || member.isJsonNull() ? 0 : integer(member, "run.trace.member");
        }

        private View load(final String label, final String relative, final JsonObject expected,
                final java.util.function.Predicate<JsonObject> retain,
                final java.util.function.Predicate<JsonObject> scope, final String recapture) throws IOException {
            if (relative == null) {
                return null;
            }
            final Path file;
            try {
                file = resolveInside(dir, relative);
            } catch (final IllegalArgumentException outside) {
                throw new Diagnostic(label + " trace " + outside.getMessage()
                        + "; refusing to read it. Recapture with:\n  " + recapture);
            }
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
            try {
                verifyTraceDigest(file, dataset, relative);
            } catch (final IllegalArgumentException corrupt) {
                throw new Diagnostic(corrupt.getMessage() + ". Recapture with:\n  " + recapture);
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

        private record ConfirmationLag(double mean, long count, List<Integer> indices) {
        }

        private static ConfirmationLag confirmationLag(final List<JsonObject> records) {
            double sum = 0.0d;
            long count = 0;
            final List<Integer> indices = new ArrayList<>();
            for (final JsonObject record : records) {
                if (!"topology".equals(record.get("kind").getAsString()) || !"complete".equals(status(record))) {
                    continue;
                }
                final JsonArray visible = record.getAsJsonArray("pivots");
                for (final JsonElement candidate : record.getAsJsonArray("candidates")) {
                    final JsonArray placement = candidate.getAsJsonObject().getAsJsonArray("placement");
                    double lag = 0.0d;
                    int visibleIndex = 0;
                    for (final JsonElement placed : placement) {
                        final int pivotIndex = placed.getAsJsonObject().get("index").getAsInt();
                        while (visible.get(visibleIndex).getAsJsonObject().get("index").getAsInt() < pivotIndex) {
                            visibleIndex++;
                        }
                        final int confirmedAt = visible.get(visibleIndex)
                                .getAsJsonObject()
                                .get("confirmationIndex")
                                .getAsInt();
                        lag += Math.max(0, confirmedAt - pivotIndex);
                    }
                    sum += lag / placement.size();
                    count++;
                }
                indices.add(record.get("asOfIndex").getAsInt());
            }
            return new ConfirmationLag(count == 0 ? Double.NaN : sum / count, count, indices);
        }

        private static double stability(final List<JsonObject> records) {
            Set<String> previous = null;
            double sum = 0.0d;
            long transitions = 0;
            for (final JsonObject record : records) {
                final Set<String> current = new HashSet<>();
                record.getAsJsonArray("labels").forEach(label -> current.add(label.getAsString()));
                if (previous != null) {
                    int intersection = 0;
                    for (final String label : current) {
                        if (previous.contains(label)) {
                            intersection++;
                        }
                    }
                    final int union = previous.size() + current.size() - intersection;
                    sum += union == 0 ? 1.0d : intersection / (double) union;
                    transitions++;
                }
                previous = current;
            }
            return transitions == 0 ? Double.NaN : sum / transitions;
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

    /**
     * Real filesystem operations, replaceable only at the metadata I/O boundary.
     */
    interface MetadataFiles {
        MetadataFiles DEFAULT = new MetadataFiles() {
        };

        default void write(final Path temporary, final String contents) throws IOException {
            Files.writeString(temporary, contents, StandardCharsets.UTF_8);
        }

        default void replace(final Path temporary, final Path target) throws IOException {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static void writeRunJson(final Path dir, final JsonObject json, final MetadataFiles files) throws IOException {
        final Path temporary = Files.createTempFile(dir, RUN_FILE + "-", ".tmp");
        try {
            files.write(temporary, GSON.toJson(json) + "\n");
            files.replace(temporary, dir.resolve(RUN_FILE));
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}

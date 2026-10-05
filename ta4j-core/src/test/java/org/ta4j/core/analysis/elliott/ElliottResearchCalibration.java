/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import org.ta4j.core.BarSeries;
import org.ta4j.core.analysis.elliott.ElliottResearchEvents.Event;
import org.ta4j.core.analysis.elliott.ElliottResearchOutcomes.Evaluated;
import org.ta4j.core.analysis.elliott.ElliottResearchOutcomes.Label;

/**
 * Explicit outcome calibration of enrolled Elliott motive alternatives.
 *
 * <p>
 * The first target is the {@link ElliottResearchOutcomes} structural label
 * {@code correction-completed}: the enrolled motive's declared correction
 * completes before invalidation or withdrawal and within {@code h} bars. The
 * only feature is the heuristic evidence score {@code PASS / (PASS + FAIL)}
 * across the declared rules evaluated on the candidate's frozen enrollment
 * pivots; {@code PENDING}, {@code UNAVAILABLE}, and {@code NOT_APPLICABLE}
 * rules are coverage states, not failures, and a candidate with no evaluable
 * rule has no feature and no probability. The estimator is a five-bin, smoothed
 * weighted frequency {@code p = (S + 1) / (W + 2)} fitted per available-rule
 * mask, where each eligible alternative of one dataset, partition, and decision
 * index weighs {@code 1 / m}. A bin abstains, with the exact reason and its raw
 * support, unless it holds enough distinct decision groups and at least one
 * success and one failure.
 * </p>
 *
 * <p>
 * Fitting is chronological: a fit row is eligible only when its whole label
 * window ended by the fit cutoff, even if an absorbing result came earlier, so
 * an estimate only ever uses a table that existed at its decision time. The
 * heuristic score is never relabelled as a probability: both stay separate
 * fields everywhere. Estimates are marginal per alternative and are never
 * normalised across simultaneous alternatives. Nothing here claims predictive
 * efficacy; poor calibration and worse-than-baseline loss are reported results.
 * </p>
 *
 * @since 0.26.1
 */
final class ElliottResearchCalibration {

    /** Schema of one serialized calibration table. */
    static final String SCHEMA = "ta4j-elliott-calibration-table/1";
    static final String TABLES_FILE = "calibration-tables.json";
    static final String PREDICTIONS_FILE = "calibration-predictions.csv";
    static final String SUMMARY_FILE = "calibration-summary.csv";
    static final String RELIABILITY_FILE = "calibration-reliability.csv";
    /**
     * Declared event: correction completes before invalidation, within the horizon.
     */
    static final String TARGET = "correction-completes-before-invalidation-within-horizon";
    static final String FEATURE = "pass/(pass+fail)";
    static final String GRAMMAR = "MOTIVE_5";
    static final String VIEW_GROUPED = "group-weighted";
    static final String VIEW_COHORT = "non-overlapping-cohort";
    static final String STAGE_VALIDATION = "validation";
    static final String STAGE_EVALUATION = "evaluation";
    static final int BINS = 5;
    static final int DEFAULT_MIN_GROUPS = 30;
    static final String DEFAULT_FIT = "calibration";
    static final String DEFAULT_VALIDATION = "validation";
    static final String DEFAULT_EVALUATION = "holdout";
    static final String STATUS_PREDICTED = "predicted";
    static final String STATUS_NO_FEATURE = "no-feature";
    static final String STATUS_UNSUPPORTED = "unsupported";
    static final String RANK_ENROLLMENT = "enrollment";
    static final String RANK_PROBABILITY = "probability";

    private static final double[] EDGES = { 0.0, 0.2, 0.4, 0.6, 0.8, 1.0 };
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping()
            .serializeNulls()
            .setPrettyPrinting()
            .create();
    private static final List<String> PREDICTION_HEADER = List.of("dataset", "stage", "partition", "candidateKey",
            "version", "direction", "enrollIndex", "enrollTime", "target", "horizon", "mask", "pass", "fail", "pending",
            "unavailable", "notApplicable", "heuristicScore", "scoreBin", "status", "probability", "baseProbability",
            "reason", "fitCutoff", "fitFirstIndex", "fitGroups", "fitWeight", "fitSuccessWeight", "fitSuccesses",
            "fitFailures", "outcome", "availableIndex", "windowEnd", "resolutionIndex", "cohort", "weight");
    private static final List<String> SUMMARY_HEADER = List.of("dataset", "stage", "partition", "view", "target",
            "horizon", "fitPartitions", "fitCutoff", "estimatorStatus", "validationStatus", "candidates", "purged",
            "alreadyResolved", "censored", "incompleteWindow", "eligible", "groups", "weight", "success", "failure",
            "noFeature", "unsupported", "predicted", "coverage", "modelBrier", "baseBrier", "brierVsBase",
            "modelLogLoss", "baseLogLoss", "logLossVsBase");
    private static final List<String> RELIABILITY_HEADER = List.of("dataset", "stage", "view", "bin", "lower", "upper",
            "rows", "groups", "weight", "successes", "failures", "meanForecast", "observedRate", "meanBase");

    private ElliottResearchCalibration() {
    }

    // ------------------------------------------------------------- settings

    /**
     * Declared calibration recipe.
     *
     * @param horizon    the one outcome horizon in bars the target is declared at
     * @param fit        chronologically first partition, the base fit window
     * @param validation partition whose predictions use a table fitted on
     *                   {@code fit} only
     * @param evaluation partition whose predictions use a table fitted on
     *                   {@code fit} and {@code validation}
     * @param minGroups  minimum distinct decision groups a bin needs
     * @since 0.26.1
     */
    record Settings(int horizon, String fit, String validation, String evaluation, int minGroups) {

        private static final List<String> FIELDS = List.of("horizon", "fit", "validation", "evaluation", "minGroups");

        Settings {
            if (horizon < 1) {
                throw new IllegalArgumentException("calibration.horizon must be positive, was " + horizon);
            }
            Objects.requireNonNull(fit, "fit");
            Objects.requireNonNull(validation, "validation");
            Objects.requireNonNull(evaluation, "evaluation");
            if (minGroups < 1) {
                throw new IllegalArgumentException("calibration.minGroups must be positive, was " + minGroups);
            }
            if (new HashSet<>(List.of(fit, validation, evaluation)).size() != 3) {
                throw new IllegalArgumentException("calibration.fit, validation, and evaluation must name three"
                        + " distinct partitions, were " + List.of(fit, validation, evaluation));
            }
        }

        /**
         * Parses the optional recipe object. The horizon is required because the target
         * is only defined at one declared horizon; there is no default pooling.
         *
         * @param json       the {@code calibration} object
         * @param outcomes   the recipe's outcome settings the horizon must belong to
         * @param partitions the recipe's partitions
         * @return validated settings
         * @since 0.26.1
         */
        static Settings parse(final JsonObject json, final ElliottResearchOutcomes.Settings outcomes,
                final List<StudyRunner.Partition> partitions) {
            for (final String key : json.keySet()) {
                if (!FIELDS.contains(key)) {
                    throw new IllegalArgumentException("calibration has unknown field '" + key + "'");
                }
            }
            if (!json.has("horizon")) {
                throw new IllegalArgumentException(
                        "calibration.horizon is required: the event is declared at one outcome horizon of "
                                + outcomes.horizons());
            }
            final int horizon = integer(json, "horizon");
            if (!outcomes.horizons().contains(horizon)) {
                throw new IllegalArgumentException(
                        "calibration.horizon " + horizon + " must be one of outcomes.horizons " + outcomes.horizons());
            }
            final Settings settings = new Settings(horizon, text(json, "fit", DEFAULT_FIT),
                    text(json, "validation", DEFAULT_VALIDATION), text(json, "evaluation", DEFAULT_EVALUATION),
                    json.has("minGroups") ? integer(json, "minGroups") : DEFAULT_MIN_GROUPS);
            final Map<String, StudyRunner.Partition> byName = new LinkedHashMap<>();
            partitions.forEach(partition -> byName.put(partition.name(), partition));
            final List<StudyRunner.Partition> ordered = new ArrayList<>();
            for (final String name : List.of(settings.fit(), settings.validation(), settings.evaluation())) {
                final StudyRunner.Partition partition = byName.get(name);
                if (partition == null) {
                    throw new IllegalArgumentException(
                            "calibration names unknown partition '" + name + "'; the recipe has " + byName.keySet());
                }
                ordered.add(partition);
            }
            for (int at = 1; at < ordered.size(); at++) {
                if (!ordered.get(at - 1).end().isBefore(ordered.get(at).start())) {
                    throw new IllegalArgumentException("calibration partitions must be chronological and disjoint"
                            + " (fit, validation, evaluation); '" + ordered.get(at - 1).name() + "' does not end"
                            + " before '" + ordered.get(at).name() + "' starts");
                }
            }
            return settings;
        }

        /** @return the recipe object as recorded in {@code run.json} */
        JsonObject toJson() {
            final JsonObject json = new JsonObject();
            json.addProperty("target", TARGET);
            json.addProperty("horizon", horizon);
            json.addProperty("fit", fit);
            json.addProperty("validation", validation);
            json.addProperty("evaluation", evaluation);
            json.addProperty("minGroups", minGroups);
            return json;
        }

        private static int integer(final JsonObject json, final String field) {
            final JsonElement element = json.get(field);
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()
                    || element.getAsDouble() != Math.rint(element.getAsDouble())
                    || Math.abs(element.getAsDouble()) > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("calibration." + field + " must be an integer");
            }
            return element.getAsInt();
        }

        private static String text(final JsonObject json, final String field, final String fallback) {
            if (!json.has(field)) {
                return fallback;
            }
            final JsonElement element = json.get(field);
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()
                    || element.getAsString().isBlank()) {
                throw new IllegalArgumentException("calibration." + field + " must be a non-blank string");
            }
            return element.getAsString();
        }
    }

    // ------------------------------------------------------------- identity

    /**
     * Everything a table is only valid for: dataset scope, grammar, the detector
     * and its configuration, rule set and momentum lookback behind the feature,
     * structural policy, target, feature, and horizon. A detector is identified by
     * its factory and parameters as well as its display name, so two recipes that
     * share a name but detect swings at different scales never share a table.
     *
     * @param dataset        dataset id
     * @param detector       primary detector display name
     * @param detectorConfig primary detector factory and parameters, e.g.
     *                       {@code fractal(3)}
     * @param grammar        enrolled grammar
     * @param structuralMode structural mode that defines completion
     * @param invalidation   invalidation policy id
     * @param rules          active rule ids
     * @param momentum       momentum indicator behind the rule evidence, e.g.
     *                       {@code RSI(14)}
     * @param target         declared event id
     * @param feature        declared feature id
     * @param horizon        horizon in bars
     * @since 0.26.1
     */
    record Identity(String dataset, String detector, String detectorConfig, String grammar, String structuralMode,
            String invalidation, List<String> rules, String momentum, String target, String feature, int horizon) {

        /** Comparison section whose real stream the calibration events come from. */
        static final String ENROLLMENT_SECTION = "h1";

        Identity {
            rules = List.copyOf(rules);
        }

        /**
         * @param dataset        dataset id
         * @param detector       primary detector display name
         * @param detectorConfig primary detector factory and parameters
         * @param rules          active rule ids
         * @param momentum       momentum indicator behind the rule evidence
         * @param outcomes       recipe outcome settings
         * @param settings       calibration settings
         * @return the declared identity
         */
        static Identity of(final String dataset, final String detector, final String detectorConfig,
                final List<String> rules, final String momentum, final ElliottResearchOutcomes.Settings outcomes,
                final Settings settings) {
            return new Identity(dataset, detector, detectorConfig, GRAMMAR, outcomes.structuralMode(),
                    outcomes.invalidation().id(), rules, momentum, TARGET, FEATURE, settings.horizon());
        }

        JsonObject toJson() {
            final JsonObject json = new JsonObject();
            json.addProperty("dataset", dataset);
            json.addProperty("detector", detector);
            json.addProperty("detectorConfig", detectorConfig);
            json.addProperty("grammar", grammar);
            json.addProperty("structuralMode", structuralMode);
            json.addProperty("invalidation", invalidation);
            final JsonArray array = new JsonArray();
            rules.forEach(array::add);
            json.add("rules", array);
            json.addProperty("momentum", momentum);
            json.addProperty("target", target);
            json.addProperty("feature", feature);
            json.addProperty("horizon", horizon);
            return json;
        }

        static Identity fromJson(final JsonObject json) {
            final List<String> rules = new ArrayList<>();
            for (final JsonElement element : json.getAsJsonArray("rules")) {
                rules.add(element.getAsString());
            }
            return new Identity(json.get("dataset").getAsString(), json.get("detector").getAsString(),
                    json.get("detectorConfig").getAsString(), json.get("grammar").getAsString(),
                    json.get("structuralMode").getAsString(), json.get("invalidation").getAsString(), rules,
                    json.get("momentum").getAsString(), json.get("target").getAsString(),
                    json.get("feature").getAsString(), json.get("horizon").getAsInt());
        }

        /** @return the names of the fields that differ from {@code other} */
        List<String> differences(final Identity other) {
            final List<String> fields = new ArrayList<>();
            check(fields, "dataset", dataset, other.dataset);
            check(fields, "detector", detector, other.detector);
            check(fields, "detectorConfig", detectorConfig, other.detectorConfig);
            check(fields, "grammar", grammar, other.grammar);
            check(fields, "structuralMode", structuralMode, other.structuralMode);
            check(fields, "invalidation", invalidation, other.invalidation);
            check(fields, "rules", rules, other.rules);
            check(fields, "momentum", momentum, other.momentum);
            check(fields, "target", target, other.target);
            check(fields, "feature", feature, other.feature);
            check(fields, "horizon", horizon, other.horizon);
            return fields;
        }

        /**
         * Checks whether this table's estimates describe an inspected comparison scope.
         * Calibration events are the primary detector's real {@code h1} stream of the
         * declared grammar, so a robustness detector, a competing grammar, or the
         * {@code h2} hypothesis is another scope this table does not cover.
         *
         * @param section  comparison section of the inspected row
         * @param grammar  grammar of the inspected row
         * @param detector detector of the inspected row
         * @return the reason the table does not apply, or {@code null} when it does
         */
        String scopeMismatch(final String section, final String grammar, final String detector) {
            final List<String> fields = new ArrayList<>();
            check(fields, "section", ENROLLMENT_SECTION, section);
            check(fields, "grammar", this.grammar, grammar);
            check(fields, "detector", this.detector, detector);
            return fields.isEmpty() ? null : String.join("; ", fields);
        }

        private static void check(final List<String> fields, final String name, final Object left, final Object right) {
            if (!left.equals(right)) {
                fields.add(name + " (" + left + " vs " + right + ")");
            }
        }
    }

    /**
     * Executed recipe and code identity of one fitted table.
     *
     * @param fingerprint recipe fingerprint
     * @param revision    code revision the run executed
     * @since 0.26.1
     */
    record Provenance(String fingerprint, String revision) {
    }

    // ----------------------------------------------------------------- rows

    /** Outcome class of one enrolled alternative at the declared horizon. */
    enum Outcome {
        SUCCESS("success"), FAILURE("failure"), CENSORED("censored"), ALREADY_RESOLVED("already-resolved"),
        INCOMPLETE_WINDOW("incomplete-window");

        private final String id;

        Outcome(final String id) {
            this.id = id;
        }

        String id() {
            return id;
        }

        static Outcome parse(final String id) {
            for (final Outcome outcome : values()) {
                if (outcome.id.equals(id)) {
                    return outcome;
                }
            }
            throw new IllegalArgumentException("unknown calibration outcome '" + id + "'");
        }
    }

    /**
     * One enrolled alternative: the feature frozen at the decision and the outcome
     * record it later received.
     *
     * @param dataset         dataset id
     * @param partition       real partition the alternative enrolled in
     * @param candidateKey    canonical candidate key
     * @param version         candidate version at enrollment
     * @param direction       motive direction
     * @param enrollIndex     decision index in source coordinates
     * @param enrollTime      decision time
     * @param pass            rules passing at enrollment
     * @param fail            rules failing at enrollment
     * @param pending         rules pending at enrollment
     * @param unavailable     rules unavailable at enrollment
     * @param notApplicable   rules not applicable at enrollment
     * @param mask            evaluable (pass or fail) rule ids joined by {@code +}
     * @param outcome         outcome class
     * @param availableIndex  first index the structural label is known
     * @param windowEnd       last index of the label window, decision plus horizon
     * @param resolutionIndex index of the absorbing result, or {@code -1}
     * @param cohort          whether the row is in the non-overlapping sensitivity
     *                        cohort
     * @since 0.26.1
     */
    record Row(String dataset, String partition, String candidateKey, String version, String direction, int enrollIndex,
            Instant enrollTime, int pass, int fail, int pending, int unavailable, int notApplicable, String mask,
            Outcome outcome, int availableIndex, int windowEnd, int resolutionIndex, boolean cohort) {

        /** @return whether the row has a fully observed success or failure label */
        boolean eligible() {
            return outcome == Outcome.SUCCESS || outcome == Outcome.FAILURE;
        }

        boolean hasFeature() {
            return pass + fail > 0;
        }

        /**
         * @return {@code PASS / (PASS + FAIL)}, or {@code NaN} without evaluable rule
         */
        double feature() {
            return hasFeature() ? (double) pass / (pass + fail) : Double.NaN;
        }

        /** @return the score bin, or {@code -1} without a feature */
        int bin() {
            return hasFeature() ? Math.min(BINS - 1, BINS * pass / (pass + fail)) : -1;
        }

        String identity() {
            return dataset + "|" + partition + "|" + enrollIndex + "|" + candidateKey + "|" + version;
        }

        String group() {
            return dataset + "|" + partition + "|" + enrollIndex;
        }
    }

    /** A row with its decision-group weight. */
    record Weighted(Row row, double weight) {
    }

    /**
     * Evaluates the declared relationship rules on an enrolled motive exactly as
     * its enrollment observation could: the frozen placement's pivots, read through
     * the real series. Every rule reads only bars up to its pivots, never the label
     * window, so the evidence is causal at the decision index.
     *
     * @param event  enrolled real-stream event
     * @param rules  declared relationship rules
     * @param series real source series
     * @return one evidence entry per rule, in rule order
     * @since 0.26.1
     */
    static List<RuleEvidence> enrollmentEvidence(final Event event, final List<RelationshipRule> rules,
            final BarSeries series) {
        final TopologyCandidate candidate = new TopologyCandidate(TopologyGrammar.MOTIVE_5, event.direction,
                event.pivots);
        final List<RuleEvidence> evidence = new ArrayList<>(rules.size());
        for (final RelationshipRule rule : rules) {
            evidence.add(rule.evaluate(candidate, series));
        }
        return List.copyOf(evidence);
    }

    /**
     * Builds the rows of one dataset's real streams from its outcome labels. The
     * non-overlapping cohort comes from the same {@link ElliottResearchOutcomes}
     * policy that reports it.
     *
     * @param result       outcome labels
     * @param lastObserved last observed source index per real partition
     * @param horizon      declared horizon
     * @param evidence     rule evidence of an event, evaluated at its enrollment
     * @return one row per real enrolled alternative, in label order
     * @since 0.26.1
     */
    static List<Row> rows(final ElliottResearchOutcomes.Result result, final Map<String, Integer> lastObserved,
            final int horizon, final Function<Event, List<RuleEvidence>> evidence) {
        final Map<String, List<Label>> byPartition = new LinkedHashMap<>();
        final List<Label> labels = new ArrayList<>();
        for (final Evaluated evaluated : result.events()) {
            if (!evaluated.event().stream.real()) {
                continue;
            }
            final Label label = evaluated.labels()
                    .stream()
                    .filter(candidate -> candidate.horizon() == horizon)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "no label at horizon " + horizon + " for " + evaluated.event().candidateKey));
            labels.add(label);
            byPartition.computeIfAbsent(evaluated.event().stream.partition(), key -> new ArrayList<>()).add(label);
        }
        final Set<Label> cohort = new HashSet<>();
        byPartition.values().forEach(group -> cohort.addAll(ElliottResearchOutcomes.cohortLabels(group, horizon)));
        final List<Row> rows = new ArrayList<>(labels.size());
        for (final Label label : labels) {
            rows.add(row(result.dataset(), label, lastObserved, horizon, cohort.contains(label), evidence));
        }
        return rows;
    }

    private static Row row(final String dataset, final Label label, final Map<String, Integer> lastObserved,
            final int horizon, final boolean cohort, final Function<Event, List<RuleEvidence>> evidence) {
        final Event event = label.event();
        final int windowEnd = event.enrollIndex + horizon;
        final boolean observed = windowEnd <= lastObserved.getOrDefault(event.stream.partition(), -1);
        final Outcome outcome = switch (label.structural()) {
        case ALREADY_RESOLVED -> Outcome.ALREADY_RESOLVED;
        case CENSORED -> Outcome.CENSORED;
        case CORRECTION_COMPLETED -> observed ? Outcome.SUCCESS : Outcome.INCOMPLETE_WINDOW;
        case INVALIDATED_OR_WITHDRAWN -> observed ? Outcome.FAILURE : Outcome.INCOMPLETE_WINDOW;
        case HORIZON_EXPIRED -> Outcome.FAILURE;
        };
        int pass = 0;
        int fail = 0;
        int pending = 0;
        int unavailable = 0;
        int notApplicable = 0;
        final List<String> evaluable = new ArrayList<>();
        for (final RuleEvidence rule : evidence.apply(event)) {
            switch (rule.state()) {
            case PASS -> pass++;
            case FAIL -> fail++;
            case PENDING -> pending++;
            case UNAVAILABLE -> unavailable++;
            case NOT_APPLICABLE -> notApplicable++;
            }
            if (rule.state() == EvidenceState.PASS || rule.state() == EvidenceState.FAIL) {
                evaluable.add(rule.ruleId());
            }
        }
        return new Row(dataset, event.stream.partition(), event.candidateKey, event.version, event.direction.name(),
                event.enrollIndex, event.enrollTime, pass, fail, pending, unavailable, notApplicable,
                String.join("+", evaluable), outcome, label.structuralAvailableIndex(), windowEnd,
                label.resolutionIndex(), cohort);
    }

    /**
     * Deduplicates by the canonical enrollment and candidate-version key, then
     * gives every eligible alternative of one dataset, partition, and decision
     * index the weight {@code 1 / m}, {@code m} counting all eligible alternatives
     * of the group. Rows without a success or failure label weigh zero.
     *
     * @param rows candidate rows
     * @return unique rows with weights, in first-seen order
     * @throws IllegalStateException when one key carries conflicting rows
     * @since 0.26.1
     */
    static List<Weighted> weigh(final Collection<Row> rows) {
        final Map<String, Row> unique = new LinkedHashMap<>();
        for (final Row row : rows) {
            final Row previous = unique.putIfAbsent(row.identity(), row);
            if (previous != null && !previous.equals(row)) {
                throw new IllegalStateException("conflicting rows share the candidate-version key " + row.identity());
            }
        }
        final Map<String, Integer> sizes = new HashMap<>();
        for (final Row row : unique.values()) {
            if (row.eligible()) {
                sizes.merge(row.group(), 1, Integer::sum);
            }
        }
        final List<Weighted> weighted = new ArrayList<>(unique.size());
        for (final Row row : unique.values()) {
            weighted.add(new Weighted(row, row.eligible() ? 1.0d / sizes.get(row.group()) : 0.0d));
        }
        return weighted;
    }

    // ----------------------------------------------------------- calibrator

    /**
     * Weighted support of one bin.
     *
     * @param weight        {@code W}, the weight sum
     * @param successWeight {@code S}, the success weight sum
     * @param groups        distinct decision groups
     * @param successes     distinct successful rows
     * @param failures      distinct failed rows
     * @since 0.26.1
     */
    record BinFit(double weight, double successWeight, int groups, int successes, int failures) {

        static final BinFit EMPTY = new BinFit(0.0d, 0.0d, 0, 0, 0);

        /** @return the smoothed estimate {@code (S + 1) / (W + 2)} */
        double probability() {
            return (successWeight + 1.0d) / (weight + 2.0d);
        }

        /** @return the abstention reason, or {@code null} when the bin is supported */
        String reason(final int minGroups) {
            if (groups < minGroups) {
                return "insufficient decision groups: " + groups + " < " + minGroups;
            }
            if (successes == 0) {
                return "no observed success";
            }
            if (failures == 0) {
                return "no observed failure";
            }
            return null;
        }
    }

    /**
     * One declared estimate.
     *
     * @param status          predicted, no-feature, or unsupported
     * @param probability     estimate, {@code NaN} unless predicted
     * @param baseProbability same-fit base rate, {@code NaN} unless predicted
     * @param bin             score bin, or {@code -1} without a feature
     * @param support         bin support with its raw counts
     * @param reason          abstention reason, or an empty string
     * @since 0.26.1
     */
    record Estimate(String status, double probability, double baseProbability, int bin, BinFit support, String reason) {

        boolean predicted() {
            return STATUS_PREDICTED.equals(status);
        }
    }

    /**
     * A fitted calibration table: the identity it is valid for, the fit interval,
     * and weighted counts per available-rule mask and score bin.
     *
     * @param stage         stage the table serves
     * @param identity      scope the table is valid for
     * @param provenance    recipe and code identity
     * @param minGroups     support floor per bin
     * @param fitPartitions partitions the fit read
     * @param firstIndex    first fitted decision index, or {@code -1}
     * @param cutoffIndex   fit cutoff: every fitted label window ended by it
     * @param base          training base rate over all fitted rows
     * @param masks         bins per available-rule mask
     * @since 0.26.1
     */
    record Calibrator(String stage, Identity identity, Provenance provenance, int minGroups, List<String> fitPartitions,
            int firstIndex, int cutoffIndex, BinFit base, Map<String, List<BinFit>> masks) {

        Calibrator {
            fitPartitions = List.copyOf(fitPartitions);
            final Map<String, List<BinFit>> copy = new TreeMap<>();
            masks.forEach((mask, bins) -> copy.put(mask, List.copyOf(bins)));
            masks = java.util.Collections.unmodifiableMap(copy);
        }

        private static final class Acc {
            private double weight;
            private double successWeight;
            private final Set<String> groups = new HashSet<>();
            private int successes;
            private int failures;

            void add(final Weighted weighted) {
                weight += weighted.weight();
                groups.add(weighted.row().group());
                if (weighted.row().outcome() == Outcome.SUCCESS) {
                    successWeight += weighted.weight();
                    successes++;
                } else {
                    failures++;
                }
            }

            BinFit fit() {
                return new BinFit(weight, successWeight, groups.size(), successes, failures);
            }
        }

        /**
         * Fits one table. A row enters the fit only when it is eligible, in a fit
         * partition, its label is known by the cutoff, and its whole label window ended
         * by the cutoff, so no later outcome can shape the table.
         *
         * @param stage         stage the table serves
         * @param identity      declared scope
         * @param provenance    recipe and code identity
         * @param minGroups     support floor per bin
         * @param rows          all candidate rows of the dataset
         * @param fitPartitions partitions to fit on
         * @param cutoff        last index the fit may have observed
         * @return the fitted table
         * @since 0.26.1
         */
        static Calibrator fit(final String stage, final Identity identity, final Provenance provenance,
                final int minGroups, final List<Row> rows, final List<String> fitPartitions, final int cutoff) {
            final List<Row> fitRows = rows.stream()
                    .filter(row -> fitPartitions.contains(row.partition()) && row.eligible()
                            && row.availableIndex() <= cutoff && row.windowEnd() <= cutoff)
                    .toList();
            final Acc base = new Acc();
            final Map<String, List<Acc>> masks = new TreeMap<>();
            int first = -1;
            for (final Weighted weighted : weigh(fitRows)) {
                final Row row = weighted.row();
                first = first < 0 ? row.enrollIndex() : Math.min(first, row.enrollIndex());
                base.add(weighted);
                if (row.hasFeature()) {
                    masks.computeIfAbsent(row.mask(), mask -> {
                        final List<Acc> bins = new ArrayList<>();
                        for (int bin = 0; bin < BINS; bin++) {
                            bins.add(new Acc());
                        }
                        return bins;
                    }).get(row.bin()).add(weighted);
                }
            }
            final Map<String, List<BinFit>> fitted = new TreeMap<>();
            masks.forEach((mask, bins) -> fitted.put(mask, bins.stream().map(Acc::fit).toList()));
            return new Calibrator(stage, identity, provenance, minGroups, fitPartitions, first, cutoff, base.fit(),
                    fitted);
        }

        /**
         * @return the training base rate, or {@code NaN} when the base is unsupported
         */
        double baseProbability() {
            return base.reason(minGroups) == null ? base.probability() : Double.NaN;
        }

        /**
         * @return {@code fitted} when at least one bin is supported, otherwise
         *         {@code unavailable}
         */
        String estimatorStatus() {
            for (final List<BinFit> bins : masks.values()) {
                for (final BinFit bin : bins) {
                    if (bin.reason(minGroups) == null) {
                        return "fitted";
                    }
                }
            }
            return "unavailable";
        }

        /**
         * Estimates one alternative.
         *
         * @param requested identity the caller applies the table to
         * @param row       alternative enrolled after the fit cutoff
         * @return the estimate, or an abstention with its reason and raw support
         * @throws IllegalArgumentException when the identity differs from the table's
         * @throws IllegalStateException    when the row was enrolled at or before the
         *                                  fit cutoff, so the table did not exist at
         *                                  its decision time
         * @since 0.26.1
         */
        Estimate estimate(final Identity requested, final Row row) {
            final List<String> differences = identity.differences(requested);
            if (!differences.isEmpty()) {
                throw new IllegalArgumentException(
                        "calibration table identity mismatch, not applied: " + String.join("; ", differences));
            }
            if (row.enrollIndex() <= cutoffIndex) {
                throw new IllegalStateException("alternative enrolled at index " + row.enrollIndex()
                        + " is not after the fit cutoff " + cutoffIndex + ": the table did not exist at its decision");
            }
            if (!row.hasFeature()) {
                return new Estimate(STATUS_NO_FEATURE, Double.NaN, Double.NaN, -1, BinFit.EMPTY,
                        "no evaluable rule at enrollment");
            }
            final List<BinFit> bins = masks.get(row.mask());
            final BinFit support = bins == null ? BinFit.EMPTY : bins.get(row.bin());
            final String reason = support.reason(minGroups);
            if (reason != null) {
                return new Estimate(STATUS_UNSUPPORTED, Double.NaN, Double.NaN, row.bin(), support, reason);
            }
            return new Estimate(STATUS_PREDICTED, support.probability(), baseProbability(), row.bin(), support, "");
        }

        /** @return the serialized table */
        JsonObject toJson() {
            final JsonObject json = new JsonObject();
            json.addProperty("schema", SCHEMA);
            json.addProperty("stage", stage);
            json.add("identity", identity.toJson());
            final JsonObject policy = new JsonObject();
            final JsonArray edges = new JsonArray();
            for (final double edge : EDGES) {
                edges.add(edge);
            }
            policy.add("binEdges", edges);
            policy.addProperty("binRule", "lower edge inclusive; the last bin includes 1");
            policy.addProperty("smoothing", "(S + 1) / (W + 2)");
            policy.addProperty("weighting",
                    "1 / m per dataset, partition, and decision-index group of m eligible rows");
            policy.addProperty("deduplication", "dataset|partition|enrollIndex|candidateKey|version");
            policy.addProperty("minGroups", minGroups);
            policy.addProperty("requiresSuccessAndFailure", true);
            policy.addProperty("clipping", "none");
            json.add("policy", policy);
            final JsonObject fit = new JsonObject();
            final JsonArray partitions = new JsonArray();
            fitPartitions.forEach(partitions::add);
            fit.add("partitions", partitions);
            fit.addProperty("firstIndex", firstIndex);
            fit.addProperty("cutoffIndex", cutoffIndex);
            json.add("fit", fit);
            final JsonObject provenanceJson = new JsonObject();
            provenanceJson.addProperty("fingerprint", provenance.fingerprint());
            provenanceJson.addProperty("revision", provenance.revision());
            json.add("provenance", provenanceJson);
            json.add("base", binJson(-1, base));
            final JsonArray maskArray = new JsonArray();
            masks.forEach((mask, bins) -> {
                final JsonObject entry = new JsonObject();
                entry.addProperty("mask", mask);
                final JsonArray binArray = new JsonArray();
                for (int bin = 0; bin < bins.size(); bin++) {
                    binArray.add(binJson(bin, bins.get(bin)));
                }
                entry.add("bins", binArray);
                maskArray.add(entry);
            });
            json.add("masks", maskArray);
            return json;
        }

        private JsonObject binJson(final int bin, final BinFit fit) {
            final JsonObject json = new JsonObject();
            if (bin >= 0) {
                json.addProperty("bin", bin);
                json.addProperty("lower", EDGES[bin]);
                json.addProperty("upper", EDGES[bin + 1]);
            }
            json.addProperty("weight", fit.weight());
            json.addProperty("successWeight", fit.successWeight());
            json.addProperty("groups", fit.groups());
            json.addProperty("successes", fit.successes());
            json.addProperty("failures", fit.failures());
            final String reason = fit.reason(minGroups);
            json.addProperty("supported", reason == null);
            json.add("probability", reason == null ? new JsonPrimitive(fit.probability()) : JsonNull.INSTANCE);
            json.add("reason", reason == null ? JsonNull.INSTANCE : new JsonPrimitive(reason));
            return json;
        }

        /**
         * Reloads a serialized table; estimates are recomputed from the stored counts.
         *
         * @param json serialized table
         * @return the table
         * @throws IllegalArgumentException when the schema or bin policy differs
         * @since 0.26.1
         */
        static Calibrator fromJson(final JsonObject json) {
            if (!SCHEMA.equals(json.has("schema") ? json.get("schema").getAsString() : null)) {
                throw new IllegalArgumentException("calibration table schema must be " + SCHEMA);
            }
            final JsonObject policy = json.getAsJsonObject("policy");
            final JsonArray edges = policy.getAsJsonArray("binEdges");
            if (edges.size() != EDGES.length) {
                throw new IllegalArgumentException("calibration table bin edges differ from " + List.of(EDGES));
            }
            for (int at = 0; at < EDGES.length; at++) {
                if (edges.get(at).getAsDouble() != EDGES[at]) {
                    throw new IllegalArgumentException("calibration table bin edges differ from the declared bins");
                }
            }
            final JsonObject fit = json.getAsJsonObject("fit");
            final List<String> partitions = new ArrayList<>();
            fit.getAsJsonArray("partitions").forEach(element -> partitions.add(element.getAsString()));
            final JsonObject provenance = json.getAsJsonObject("provenance");
            final Map<String, List<BinFit>> masks = new TreeMap<>();
            for (final JsonElement element : json.getAsJsonArray("masks")) {
                final JsonObject entry = element.getAsJsonObject();
                final List<BinFit> bins = new ArrayList<>();
                for (final JsonElement bin : entry.getAsJsonArray("bins")) {
                    bins.add(binFit(bin.getAsJsonObject()));
                }
                if (bins.size() != BINS) {
                    throw new IllegalArgumentException("calibration table mask '" + entry.get("mask").getAsString()
                            + "' must hold " + BINS + " bins");
                }
                masks.put(entry.get("mask").getAsString(), bins);
            }
            return new Calibrator(json.get("stage").getAsString(), Identity.fromJson(json.getAsJsonObject("identity")),
                    new Provenance(provenance.get("fingerprint").getAsString(),
                            provenance.get("revision").getAsString()),
                    policy.get("minGroups").getAsInt(), partitions, fit.get("firstIndex").getAsInt(),
                    fit.get("cutoffIndex").getAsInt(), binFit(json.getAsJsonObject("base")), masks);
        }

        private static BinFit binFit(final JsonObject json) {
            return new BinFit(json.get("weight").getAsDouble(), json.get("successWeight").getAsDouble(),
                    json.get("groups").getAsInt(), json.get("successes").getAsInt(), json.get("failures").getAsInt());
        }
    }

    // ------------------------------------------------------- computation

    /**
     * One estimate with its row, weight, and the table that produced it.
     *
     * @param stage      stage name
     * @param row        alternative
     * @param estimate   estimate
     * @param weight     group weight, zero for an ineligible row
     * @param calibrator table used
     * @since 0.26.1
     */
    record Prediction(String stage, Row row, Estimate estimate, double weight, Calibrator calibrator) {
    }

    /** One summary line: coverage, support, and loss of one stage and view. */
    record Metrics(String dataset, String stage, String partition, String view, Settings settings,
            Calibrator calibrator, String validationStatus, int candidates, int purged, int alreadyResolved,
            int censored, int incompleteWindow, int eligible, int groups, double weight, int success, int failure,
            int noFeature, int unsupported, int predicted, double coverage, double modelBrier, double baseBrier,
            double modelLogLoss, double baseLogLoss) {

        /**
         * @return {@code better}, {@code worse}, {@code equal}, or empty without a
         *         value
         */
        static String versus(final double model, final double base) {
            if (!Double.isFinite(model) || !Double.isFinite(base)) {
                return "";
            }
            final int order = Double.compare(model, base);
            return order < 0 ? "better" : order > 0 ? "worse" : "equal";
        }

        List<String> cells() {
            return List.of(dataset, stage, partition, view, TARGET, Integer.toString(settings.horizon()),
                    String.join("+", calibrator.fitPartitions()), Integer.toString(calibrator.cutoffIndex()),
                    calibrator.estimatorStatus(), validationStatus, Integer.toString(candidates),
                    Integer.toString(purged), Integer.toString(alreadyResolved), Integer.toString(censored),
                    Integer.toString(incompleteWindow), Integer.toString(eligible), Integer.toString(groups),
                    ElliottResearchReport.plain(weight), Integer.toString(success), Integer.toString(failure),
                    Integer.toString(noFeature), Integer.toString(unsupported), Integer.toString(predicted),
                    ElliottResearchReport.plain(coverage), ElliottResearchReport.plain(modelBrier),
                    ElliottResearchReport.plain(baseBrier), versus(modelBrier, baseBrier),
                    ElliottResearchReport.plain(modelLogLoss), ElliottResearchReport.plain(baseLogLoss),
                    versus(modelLogLoss, baseLogLoss));
        }
    }

    /** One forecast bin of held-out reliability. */
    record Reliability(String dataset, String stage, String view, int bin, int rows, int groups, double weight,
            int successes, int failures, double meanForecast, double observedRate, double meanBase) {

        List<String> cells() {
            return List.of(dataset, stage, view, Integer.toString(bin), ElliottResearchReport.plain(EDGES[bin]),
                    ElliottResearchReport.plain(EDGES[bin + 1]), Integer.toString(rows), Integer.toString(groups),
                    ElliottResearchReport.plain(weight), Integer.toString(successes), Integer.toString(failures),
                    ElliottResearchReport.plain(meanForecast), ElliottResearchReport.plain(observedRate),
                    ElliottResearchReport.plain(meanBase));
        }
    }

    /**
     * All calibration output of one dataset.
     *
     * @param dataset     dataset id
     * @param settings    declared recipe
     * @param tables      fitted tables, validation then evaluation
     * @param predictions every estimate of the validation and evaluation partitions
     * @param metrics     summary lines per stage and view
     * @param reliability held-out reliability per stage, view, and forecast bin
     * @since 0.26.1
     */
    record Computation(String dataset, Settings settings, List<Calibrator> tables, List<Prediction> predictions,
            List<Metrics> metrics, List<Reliability> reliability) {

        Computation {
            tables = List.copyOf(tables);
            predictions = List.copyOf(predictions);
            metrics = List.copyOf(metrics);
            reliability = List.copyOf(reliability);
        }
    }

    /**
     * Fits the chronological tables and evaluates their estimates. The validation
     * stage applies a table fitted on the fit partition only; the evaluation stage
     * applies one fitted on the fit and validation partitions. Nothing is tuned or
     * selected: the bins, support floor, and partitions are the declared recipe.
     *
     * @param result       outcome labels of the dataset
     * @param lastObserved last observed source index per real partition
     * @param settings     declared calibration recipe
     * @param identity     declared table identity
     * @param provenance   recipe and code identity
     * @param evidence     rule evidence of an event, evaluated at its enrollment
     * @return tables, predictions, and diagnostics
     * @since 0.26.1
     */
    static Computation compute(final ElliottResearchOutcomes.Result result, final Map<String, Integer> lastObserved,
            final Settings settings, final Identity identity, final Provenance provenance,
            final Function<Event, List<RuleEvidence>> evidence) {
        final List<Row> rows = rows(result, lastObserved, settings.horizon(), evidence);
        final List<Calibrator> tables = new ArrayList<>();
        final List<Prediction> predictions = new ArrayList<>();
        final List<Metrics> metrics = new ArrayList<>();
        final List<Reliability> reliability = new ArrayList<>();
        final List<String> validationFit = List.of(settings.fit());
        final List<String> evaluationFit = List.of(settings.fit(), settings.validation());
        final String[] stages = { STAGE_VALIDATION, STAGE_EVALUATION };
        final String[] partitions = { settings.validation(), settings.evaluation() };
        final List<List<String>> fits = List.of(validationFit, evaluationFit);
        for (int at = 0; at < stages.length; at++) {
            final List<String> fit = fits.get(at);
            final int cutoff = fit.stream()
                    .mapToInt(partition -> lastObserved.getOrDefault(partition, -1))
                    .max()
                    .orElse(-1);
            final Calibrator table = Calibrator.fit(stages[at], identity, provenance, settings.minGroups(), rows, fit,
                    cutoff);
            tables.add(table);
            final String partition = partitions[at];
            final LineageSplit split = splitLineages(rows, fit, partition);
            final List<Row> purged = split.purged();
            final List<Prediction> stage = new ArrayList<>();
            for (final Weighted weighted : weigh(split.heldOut())) {
                stage.add(new Prediction(stages[at], weighted.row(), table.estimate(identity, weighted.row()),
                        weighted.weight(), table));
            }
            predictions.addAll(stage);
            for (final String view : List.of(VIEW_GROUPED, VIEW_COHORT)) {
                metrics.add(metrics(result.dataset(), stages[at], partition, view, settings, table, stage, purged));
                reliability.addAll(reliability(result.dataset(), stages[at], view, stage));
            }
        }
        return new Computation(result.dataset(), settings, tables, predictions, metrics, reliability);
    }

    /**
     * Rows of a scored partition that survive the lineage purge, and the rows
     * purged.
     *
     * @param heldOut rows whose candidate lineage never appears in a fit partition
     * @param purged  rows whose lineage appears in a fit partition
     * @since 0.26.1
     */
    record LineageSplit(List<Row> heldOut, List<Row> purged) {

        LineageSplit {
            heldOut = List.copyOf(heldOut);
            purged = List.copyOf(purged);
        }
    }

    /**
     * Splits the rows of {@code partition} by candidate lineage. CF-587 enrolls
     * each partition as its own stream, so one candidate (the same grammar,
     * direction, and placement, whatever its version) visible across a partition
     * boundary can contribute a fit-window outcome to the fitted table and a later
     * outcome to the scored stage. A lineage present in any fit partition is
     * therefore purged from the scored partition rather than scored against a table
     * it helped fit.
     *
     * @param rows      all candidate rows of the dataset
     * @param fit       partitions the table is fitted on
     * @param partition partition the table is scored on
     * @return the surviving and the purged rows of {@code partition}
     * @since 0.26.1
     */
    static LineageSplit splitLineages(final List<Row> rows, final List<String> fit, final String partition) {
        final Set<String> fitLineages = new HashSet<>();
        for (final Row row : rows) {
            if (fit.contains(row.partition())) {
                fitLineages.add(row.candidateKey());
            }
        }
        final List<Row> heldOut = new ArrayList<>();
        final List<Row> purged = new ArrayList<>();
        for (final Row row : rows) {
            if (row.partition().equals(partition)) {
                (fitLineages.contains(row.candidateKey()) ? purged : heldOut).add(row);
            }
        }
        return new LineageSplit(heldOut, purged);
    }

    private static double weightIn(final String view, final Prediction prediction) {
        return VIEW_GROUPED.equals(view) ? prediction.weight() : prediction.row().eligible() ? 1.0d : 0.0d;
    }

    private static List<Prediction> inView(final String view, final List<Prediction> stage) {
        return VIEW_GROUPED.equals(view) ? stage
                : stage.stream().filter(prediction -> prediction.row().cohort()).toList();
    }

    private static Metrics metrics(final String dataset, final String stage, final String partition, final String view,
            final Settings settings, final Calibrator table, final List<Prediction> all, final List<Row> purged) {
        final List<Prediction> predictions = inView(view, all);
        int alreadyResolved = 0;
        int censored = 0;
        int incomplete = 0;
        int eligible = 0;
        int success = 0;
        int failure = 0;
        int noFeature = 0;
        int unsupported = 0;
        int predicted = 0;
        double weight = 0.0d;
        double scored = 0.0d;
        double modelBrier = 0.0d;
        double baseBrier = 0.0d;
        double modelLoss = 0.0d;
        double baseLoss = 0.0d;
        final Set<String> groups = new HashSet<>();
        for (final Prediction prediction : predictions) {
            final Row row = prediction.row();
            switch (row.outcome()) {
            case ALREADY_RESOLVED -> alreadyResolved++;
            case CENSORED -> censored++;
            case INCOMPLETE_WINDOW -> incomplete++;
            case SUCCESS -> success++;
            case FAILURE -> failure++;
            }
            if (!row.eligible()) {
                continue;
            }
            eligible++;
            final double w = weightIn(view, prediction);
            weight += w;
            groups.add(row.group());
            switch (prediction.estimate().status()) {
            case STATUS_NO_FEATURE -> noFeature++;
            case STATUS_UNSUPPORTED -> unsupported++;
            default -> {
                predicted++;
                final double y = row.outcome() == Outcome.SUCCESS ? 1.0d : 0.0d;
                final double p = prediction.estimate().probability();
                final double base = prediction.estimate().baseProbability();
                scored += w;
                modelBrier += w * (p - y) * (p - y);
                baseBrier += w * (base - y) * (base - y);
                modelLoss += w * logLoss(p, y);
                baseLoss += w * logLoss(base, y);
            }
            }
        }
        final String validation = predicted > 0 ? "evaluated with diagnostics" : "not evaluated";
        final double coverage = eligible == 0 ? Double.NaN : (double) predicted / eligible;
        final int purgedRows = (int) purged.stream().filter(row -> VIEW_GROUPED.equals(view) || row.cohort()).count();
        return new Metrics(dataset, stage, partition, view, settings, table, validation, predictions.size(), purgedRows,
                alreadyResolved, censored, incomplete, eligible, groups.size(), weight, success, failure, noFeature,
                unsupported, predicted, coverage, scored > 0 ? modelBrier / scored : Double.NaN,
                scored > 0 ? baseBrier / scored : Double.NaN, scored > 0 ? modelLoss / scored : Double.NaN,
                scored > 0 ? baseLoss / scored : Double.NaN);
    }

    private static double logLoss(final double probability, final double outcome) {
        return -(outcome * Math.log(probability) + (1.0d - outcome) * Math.log(1.0d - probability));
    }

    private static List<Reliability> reliability(final String dataset, final String stage, final String view,
            final List<Prediction> all) {
        final List<Reliability> bins = new ArrayList<>();
        for (int bin = 0; bin < BINS; bin++) {
            int rows = 0;
            int successes = 0;
            int failures = 0;
            double weight = 0.0d;
            double forecast = 0.0d;
            double observed = 0.0d;
            double base = 0.0d;
            final Set<String> groups = new HashSet<>();
            for (final Prediction prediction : inView(view, all)) {
                final Row row = prediction.row();
                if (!row.eligible() || !prediction.estimate().predicted()
                        || forecastBin(prediction.estimate().probability()) != bin) {
                    continue;
                }
                final double w = weightIn(view, prediction);
                final double y = row.outcome() == Outcome.SUCCESS ? 1.0d : 0.0d;
                rows++;
                groups.add(row.group());
                successes += (int) y;
                failures += 1 - (int) y;
                weight += w;
                forecast += w * prediction.estimate().probability();
                observed += w * y;
                base += w * prediction.estimate().baseProbability();
            }
            bins.add(new Reliability(dataset, stage, view, bin, rows, groups.size(), weight, successes, failures,
                    weight > 0 ? forecast / weight : Double.NaN, weight > 0 ? observed / weight : Double.NaN,
                    weight > 0 ? base / weight : Double.NaN));
        }
        return bins;
    }

    /** @return the forecast bin of a probability, the last bin including 1 */
    static int forecastBin(final double probability) {
        for (int bin = BINS - 1; bin > 0; bin--) {
            if (probability >= EDGES[bin]) {
                return bin;
            }
        }
        return 0;
    }

    // --------------------------------------------------------------- files

    /**
     * Writes {@code calibration-tables.json}: every fitted table with its identity,
     * policy, fit interval, provenance, and weighted counts.
     *
     * @param path         target file
     * @param computations per-dataset output
     * @throws IOException when the file cannot be written
     * @since 0.26.1
     */
    static void writeTables(final Path path, final List<Computation> computations) throws IOException {
        final JsonObject json = new JsonObject();
        json.addProperty("schema", SCHEMA);
        final JsonArray tables = new JsonArray();
        computations.forEach(computation -> computation.tables().forEach(table -> tables.add(table.toJson())));
        json.add("tables", tables);
        Files.writeString(path, JSON.toJson(json) + "\n", StandardCharsets.UTF_8);
    }

    /**
     * Reloads the tables of a written {@code calibration-tables.json}.
     *
     * @param path file to read
     * @return the tables in file order
     * @throws IOException when the file cannot be read
     * @since 0.26.1
     */
    static List<Calibrator> readTables(final Path path) throws IOException {
        final JsonObject json;
        try {
            json = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (final JsonParseException | IllegalStateException exception) {
            throw new IllegalArgumentException(path + " is not a calibration table file: " + exception.getMessage());
        }
        final List<Calibrator> tables = new ArrayList<>();
        for (final JsonElement element : json.getAsJsonArray("tables")) {
            tables.add(Calibrator.fromJson(element.getAsJsonObject()));
        }
        return tables;
    }

    /**
     * Checks an inspected comparison scope against the identity of the dataset's
     * calibration tables.
     *
     * @param tables   {@code calibration-tables.json}
     * @param dataset  dataset id of the inspected row
     * @param section  comparison section of the inspected row
     * @param grammar  grammar of the inspected row
     * @param detector detector of the inspected row
     * @return the reason the calibration does not describe that scope, or
     *         {@code null} when it does
     * @throws IOException when the tables cannot be read
     * @since 0.26.1
     */
    static String scopeMismatch(final Path tables, final String dataset, final String section, final String grammar,
            final String detector) throws IOException {
        for (final Calibrator table : readTables(tables)) {
            if (table.identity().dataset().equals(dataset)) {
                return table.identity().scopeMismatch(section, grammar, detector);
            }
        }
        return "no calibration table for dataset " + dataset;
    }

    /**
     * Writes {@code calibration-predictions.csv}: one line per estimate with its
     * trace links.
     */
    static void writePredictions(final Path path, final List<Computation> computations) {
        final List<List<String>> table = new ArrayList<>();
        for (final Computation computation : computations) {
            for (final Prediction prediction : computation.predictions()) {
                final Row row = prediction.row();
                final Estimate estimate = prediction.estimate();
                final Calibrator calibrator = prediction.calibrator();
                final BinFit fit = estimate.support();
                table.add(List.of(row.dataset(), prediction.stage(), row.partition(), row.candidateKey(), row.version(),
                        row.direction(), Integer.toString(row.enrollIndex()), row.enrollTime().toString(), TARGET,
                        Integer.toString(computation.settings().horizon()), row.mask(), Integer.toString(row.pass()),
                        Integer.toString(row.fail()), Integer.toString(row.pending()),
                        Integer.toString(row.unavailable()), Integer.toString(row.notApplicable()),
                        ElliottResearchReport.plain(row.feature()), row.bin() < 0 ? "" : Integer.toString(row.bin()),
                        estimate.status(), ElliottResearchReport.plain(estimate.probability()),
                        ElliottResearchReport.plain(estimate.baseProbability()), estimate.reason(),
                        Integer.toString(calibrator.cutoffIndex()), Integer.toString(calibrator.firstIndex()),
                        Integer.toString(fit.groups()), ElliottResearchReport.plain(fit.weight()),
                        ElliottResearchReport.plain(fit.successWeight()), Integer.toString(fit.successes()),
                        Integer.toString(fit.failures()), row.outcome().id(), Integer.toString(row.availableIndex()),
                        Integer.toString(row.windowEnd()), Integer.toString(row.resolutionIndex()),
                        Boolean.toString(row.cohort()), ElliottResearchReport.plain(prediction.weight())));
            }
        }
        ElliottResearchReport.writeTable(path, PREDICTION_HEADER, table);
    }

    /**
     * Writes {@code calibration-summary.csv}: coverage, support, and loss per stage
     * and view.
     */
    static void writeSummary(final Path path, final List<Computation> computations) {
        final List<List<String>> table = new ArrayList<>();
        computations.forEach(computation -> computation.metrics().forEach(metrics -> table.add(metrics.cells())));
        ElliottResearchReport.writeTable(path, SUMMARY_HEADER, table);
    }

    /**
     * Writes {@code calibration-reliability.csv}: held-out reliability per forecast
     * bin.
     */
    static void writeReliability(final Path path, final List<Computation> computations) {
        final List<List<String>> table = new ArrayList<>();
        computations.forEach(computation -> computation.reliability().forEach(bin -> table.add(bin.cells())));
        ElliottResearchReport.writeTable(path, RELIABILITY_HEADER, table);
    }

    // ------------------------------------------------------------- summary

    /**
     * Compact Markdown section over written {@code calibration-summary.csv} and
     * {@code calibration-reliability.csv}, so {@code summarize} regenerates it from
     * the recorded artifacts.
     *
     * @param summaryCsv     {@code calibration-summary.csv}
     * @param reliabilityCsv {@code calibration-reliability.csv}
     * @return the Markdown section
     * @since 0.26.1
     */
    static String summaryMarkdown(final Path summaryCsv, final Path reliabilityCsv) {
        final List<List<String>> summary = checked(summaryCsv, SUMMARY_HEADER);
        final List<List<String>> reliability = checked(reliabilityCsv, RELIABILITY_HEADER);
        final StringBuilder text = new StringBuilder("## Outcome calibration\n\n");
        text.append("Declared event `")
                .append(TARGET)
                .append("`; heuristic evidence score `")
                .append(FEATURE)
                .append("` is kept separate from the estimated event probability. Estimates are smoothed weighted")
                .append(" frequencies `(S + 1) / (W + 2)` per available-rule mask and score bin, marginal per")
                .append(" alternative (never normalised across alternatives). A bin abstains below its support")
                .append(" floor or without both outcomes. Loss compares the model with the training base rate on the")
                .append(" same predicted rows and weights; poor calibration or worse-than-base loss is a reported")
                .append(" result, not a failure, and no predictive efficacy is claimed. Per-row estimates are in `")
                .append(PREDICTIONS_FILE)
                .append("`, tables in `")
                .append(TABLES_FILE)
                .append("`.\n\n");
        if (summary.size() <= 1) {
            return text.append("No dataset produced calibration evidence.\n").toString();
        }
        text.append("| dataset | stage | partition | view | estimator | validation | purged | eligible | groups"
                + " | predicted | coverage | model Brier | base Brier | Brier vs base | model log loss"
                + " | base log loss | log loss vs base |\n");
        text.append("|---|---|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---|---:|---:|---|\n");
        for (final List<String> cells : summary.subList(1, summary.size())) {
            text.append("| ");
            for (final String column : List.of("dataset", "stage", "partition", "view", "estimatorStatus",
                    "validationStatus", "purged", "eligible", "groups", "predicted", "coverage", "modelBrier",
                    "baseBrier", "brierVsBase", "modelLogLoss", "baseLogLoss", "logLossVsBase")) {
                final String cell = cells.get(SUMMARY_HEADER.indexOf(column));
                text.append(cell.isEmpty() ? "n/a" : rounded(cell)).append(" | ");
            }
            text.setLength(text.length() - 1);
            text.append('\n');
        }
        text.append("\nHeld-out reliability by forecast bin (group-weighted view; empty bins are shown as n/a):\n\n");
        text.append("| dataset | stage | bin | rows | groups | weight | successes | failures | mean forecast"
                + " | observed rate | mean base |\n");
        text.append("|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (final List<String> cells : reliability.subList(1, reliability.size())) {
            if (!VIEW_GROUPED.equals(cells.get(RELIABILITY_HEADER.indexOf("view")))) {
                continue;
            }
            text.append("| ");
            for (final String column : List.of("dataset", "stage", "bin", "rows", "groups", "weight", "successes",
                    "failures", "meanForecast", "observedRate", "meanBase")) {
                final String cell = cells.get(RELIABILITY_HEADER.indexOf(column));
                text.append(cell.isEmpty() ? "n/a" : "bin".equals(column) ? binLabel(cell) : rounded(cell))
                        .append(" | ");
            }
            text.setLength(text.length() - 1);
            text.append('\n');
        }
        return text.toString();
    }

    private static String binLabel(final String bin) {
        final int at = Integer.parseInt(bin);
        return "[" + ElliottResearchReport.plain(EDGES[at]) + "," + ElliottResearchReport.plain(EDGES[at + 1])
                + (at == BINS - 1 ? "]" : ")");
    }

    private static List<List<String>> checked(final Path csv, final List<String> header) {
        final List<List<String>> table = ElliottResearchReport.parseCsv(csv);
        if (table.isEmpty() || !table.get(0).equals(header)) {
            throw new IllegalArgumentException(csv + ":1: unexpected header, expected " + header);
        }
        return table;
    }

    private static String rounded(final String cell) {
        if (!cell.contains(".")) {
            return cell;
        }
        try {
            final double value = Double.parseDouble(cell);
            return Double.isFinite(value) ? String.format(Locale.ROOT, "%.4g", value) : "n/a";
        } catch (final NumberFormatException exception) {
            return cell;
        }
    }

    // ------------------------------------------------------------- inspect

    /**
     * Prints the calibration view of one dataset and partition: per alternative the
     * heuristic evidence score, the estimated event probability, target and
     * horizon, fit support, and availability, each linked to its calibration table,
     * feature record, and event. The default view is decision-time only and lists
     * alternatives in enrollment order, the same order the topology and legacy
     * ranking use. Probability ranking is opt-in and names its basis; the eventual
     * outcome and evaluation support are retrospective and appear only on request.
     *
     * @param out            output
     * @param predictionsCsv {@code calibration-predictions.csv}
     * @param reliabilityCsv {@code calibration-reliability.csv}
     * @param dataset        dataset id
     * @param partition      partition of the inspected comparison row
     * @param candidate      candidate key prefix or exact version to focus on, or
     *                       {@code null}
     * @param asOf           last decision index to show, or {@code null}
     * @param limit          maximum alternatives shown
     * @param rank           {@code enrollment} or {@code probability}
     * @param retrospective  whether to reveal outcomes and evaluation support
     * @since 0.26.1
     */
    static void inspect(final PrintStream out, final Path predictionsCsv, final Path reliabilityCsv,
            final String dataset, final String partition, final String candidate, final Integer asOf, final int limit,
            final String rank, final boolean retrospective) {
        if (!RANK_ENROLLMENT.equals(rank) && !RANK_PROBABILITY.equals(rank)) {
            throw new IllegalArgumentException(
                    "--rank must be " + RANK_ENROLLMENT + " or " + RANK_PROBABILITY + ", was '" + rank + "'");
        }
        final List<List<String>> table = checked(predictionsCsv, PREDICTION_HEADER);
        final List<Map<String, String>> matching = new ArrayList<>();
        for (final List<String> cells : table.subList(1, table.size())) {
            final Map<String, String> record = new LinkedHashMap<>();
            for (int at = 0; at < PREDICTION_HEADER.size(); at++) {
                record.put(PREDICTION_HEADER.get(at), cells.get(at));
            }
            if (record.get("dataset").equals(dataset) && record.get("partition").equals(partition)
                    && (candidate == null || ElliottResearchTrace.selects(candidate, record.get("candidateKey"),
                            record.get("version")))
                    && (asOf == null || Integer.parseInt(record.get("enrollIndex")) <= asOf)) {
                matching.add(record);
            }
        }
        out.println();
        out.println("Calibration: target " + TARGET + "; heuristic score " + FEATURE
                + " is not a probability; estimates are marginal per alternative");
        if (matching.isEmpty()) {
            out.println("No calibration predictions for dataset " + dataset + " partition " + partition
                    + (candidate == null ? "" : " candidate " + candidate)
                    + " (predictions exist for the validation and evaluation partitions of the recipe).");
            return;
        }
        if (RANK_PROBABILITY.equals(rank)) {
            matching.sort(Comparator.comparingDouble((Map<String, String> record) -> probabilityOf(record)).reversed());
        }
        out.println("Ranking basis: "
                + (RANK_PROBABILITY.equals(rank) ? "estimated event probability, descending (opt-in; abstentions last)"
                        : "enrollment order (default; unchanged heuristic and topology ordering)"));
        out.println("Showing " + Math.min(limit, matching.size()) + " of " + matching.size() + " alternatives"
                + (asOf == null ? "" : " enrolled at or before index " + asOf) + "; stage "
                + matching.get(0).get("stage") + ", horizon " + matching.get(0).get("horizon"));
        for (final Map<String, String> record : matching.subList(0, Math.min(limit, matching.size()))) {
            out.println("- " + record.get("candidateKey") + " (version " + record.get("version") + ", "
                    + record.get("direction") + ") decision index " + record.get("enrollIndex") + " at "
                    + record.get("enrollTime"));
            out.println("    heuristic evidence score: "
                    + (record.get("heuristicScore").isEmpty() ? "unavailable (no evaluable rule)"
                            : rounded(record.get("heuristicScore")) + " (pass " + record.get("pass") + ", fail "
                                    + record.get("fail") + "; pending " + record.get("pending") + ", unavailable "
                                    + record.get("unavailable") + ", not applicable " + record.get("notApplicable")
                                    + "; mask " + (record.get("mask").isEmpty() ? "-" : record.get("mask")) + ")"));
            out.println("    estimated event probability: "
                    + (record.get("probability").isEmpty() ? record.get("status") + " - " + record.get("reason")
                            : rounded(record.get("probability")) + " (base rate "
                                    + rounded(record.get("baseProbability")) + "; score bin " + record.get("scoreBin")
                                    + ")"));
            out.println("    target/horizon: " + record.get("target") + " / " + record.get("horizon") + " bars");
            out.println("    fit support: " + record.get("fitGroups") + " decision groups, weight "
                    + rounded(record.get("fitWeight")) + ", " + record.get("fitSuccesses") + " successes, "
                    + record.get("fitFailures") + " failures; fit cutoff index " + record.get("fitCutoff")
                    + ", fit from index " + record.get("fitFirstIndex"));
            out.println("    availability: available at decision index " + record.get("enrollIndex") + " from the "
                    + record.get("stage") + "-stage table fitted by index " + record.get("fitCutoff"));
            out.println("    links: " + TABLES_FILE + " (stage " + record.get("stage") + ", mask "
                    + (record.get("mask").isEmpty() ? "-" : record.get("mask")) + ", bin " + record.get("scoreBin")
                    + "); " + ElliottResearchOutcomes.EVENTS_FILE + " (candidate " + record.get("candidateKey") + "); "
                    + ElliottResearchOutcomes.OUTCOMES_FILE + " (horizon " + record.get("horizon") + ")");
            if (retrospective) {
                out.println("    retrospective outcome: " + record.get("outcome") + " (label known at index "
                        + availableText(record.get("availableIndex")) + ", window ends " + record.get("windowEnd")
                        + ", resolution index " + resolutionText(record.get("resolutionIndex")) + ")");
            }
        }
        if (retrospective) {
            printEvaluationSupport(out, reliabilityCsv, dataset, matching.get(0).get("stage"));
        } else {
            out.println("Outcomes and held-out evaluation support are hidden; rerun with --retrospective to reveal"
                    + " them.");
        }
    }

    private static double probabilityOf(final Map<String, String> record) {
        return record.get("probability").isEmpty() ? Double.NEGATIVE_INFINITY
                : Double.parseDouble(record.get("probability"));
    }

    private static String availableText(final String index) {
        return Integer.parseInt(index) == Integer.MAX_VALUE ? "never (censored)" : index;
    }

    private static String resolutionText(final String index) {
        return Integer.parseInt(index) < 0 ? "none" : index;
    }

    private static void printEvaluationSupport(final PrintStream out, final Path reliabilityCsv, final String dataset,
            final String stage) {
        out.println("Retrospective evaluation support (" + stage + " stage, " + VIEW_GROUPED
                + " view, held-out outcomes by forecast bin):");
        final List<List<String>> table = checked(reliabilityCsv, RELIABILITY_HEADER);
        for (final List<String> cells : table.subList(1, table.size())) {
            if (cells.get(0).equals(dataset) && cells.get(1).equals(stage) && cells.get(2).equals(VIEW_GROUPED)) {
                out.println("  forecast bin " + binLabel(cells.get(3)) + ": " + cells.get(6) + " rows, " + cells.get(7)
                        + " groups, observed rate " + (cells.get(12).isEmpty() ? "n/a" : rounded(cells.get(12)))
                        + " vs mean forecast " + (cells.get(11).isEmpty() ? "n/a" : rounded(cells.get(11))));
            }
        }
    }
}

/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Immutable result of one locked empirical Elliott study evaluation.
 *
 * <p>
 * The report keeps H1 (topology) and H2 (relationship evidence) in separate
 * fields. Partition metrics are represented as a list, rather than a combined
 * aggregate, so a caller cannot accidentally interpret a cross-partition
 * statistic as a held-out result.
 * </p>
 */
final class StudyReport {

    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();

    private final String assetId;
    private final String protocolFingerprint;
    private final long seed;
    private final List<PartitionSpec> partitions;
    private final LocalDate forbiddenCalibrationStart;
    private final HypothesisReport h1;
    private final HypothesisReport h2;
    private final List<ModeReport> competingGrammars;
    private final List<ModeReport> ablations;
    private final RobustnessReport robustness;
    private final List<NullReport> nulls;

    StudyReport(final String assetId, final String protocolFingerprint, final long seed,
            final List<PartitionSpec> partitions, final LocalDate forbiddenCalibrationStart, final HypothesisReport h1,
            final HypothesisReport h2, final List<ModeReport> competingGrammars, final List<ModeReport> ablations,
            final RobustnessReport robustness, final List<NullReport> nulls) {
        this.assetId = requireText(assetId, "assetId");
        this.protocolFingerprint = requireText(protocolFingerprint, "protocolFingerprint");
        this.seed = seed;
        this.partitions = immutable(partitions, "partitions");
        this.forbiddenCalibrationStart = Objects.requireNonNull(forbiddenCalibrationStart, "forbiddenCalibrationStart");
        this.h1 = Objects.requireNonNull(h1, "h1");
        this.h2 = Objects.requireNonNull(h2, "h2");
        this.competingGrammars = immutable(competingGrammars, "competingGrammars");
        this.ablations = immutable(ablations, "ablations");
        this.robustness = Objects.requireNonNull(robustness, "robustness");
        this.nulls = immutable(nulls, "nulls");
    }

    String assetId() {
        return assetId;
    }

    String protocolFingerprint() {
        return protocolFingerprint;
    }

    long seed() {
        return seed;
    }

    List<PartitionSpec> partitions() {
        return partitions;
    }

    LocalDate forbiddenCalibrationStart() {
        return forbiddenCalibrationStart;
    }

    HypothesisReport h1() {
        return h1;
    }

    HypothesisReport h2() {
        return h2;
    }

    List<ModeReport> competingGrammars() {
        return competingGrammars;
    }

    List<ModeReport> ablations() {
        return ablations;
    }

    RobustnessReport robustness() {
        return robustness;
    }

    List<NullReport> nulls() {
        return nulls;
    }

    /**
     * Serializes this report with a fixed insertion order and no locale-sensitive
     * formatting.
     *
     * @return deterministic JSON representation
     */
    String toJson() {
        final JsonObject root = new JsonObject();
        root.addProperty("assetId", assetId);
        root.addProperty("protocolFingerprint", protocolFingerprint);
        root.addProperty("seed", seed);
        final JsonArray partitionsJson = new JsonArray();
        for (final PartitionSpec partition : partitions) {
            final JsonObject partitionJson = new JsonObject();
            partitionJson.addProperty("name", partition.name());
            partitionJson.addProperty("start", partition.start().toString());
            partitionJson.addProperty("end", partition.end().toString());
            partitionsJson.add(partitionJson);
        }
        root.add("partitions", partitionsJson);
        root.addProperty("forbiddenCalibrationStart", forbiddenCalibrationStart.toString());
        root.add("H1", hypothesisJson(h1));
        root.add("H2", hypothesisJson(h2));
        root.add("competingGrammars", modesJson(competingGrammars));
        root.add("ablations", modesJson(ablations));
        root.add("robustness", robustnessJson(robustness));
        root.add("nulls", nullsJson(nulls));
        return JSON.toJson(root);
    }

    /**
     * Writes this report to a caller-selected path.
     *
     * @param path destination path
     */
    void write(final Path path) {
        Objects.requireNonNull(path, "path");
        try {
            final Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, toJson(), StandardCharsets.UTF_8);
        } catch (final IOException exception) {
            throw new IllegalStateException("Unable to write study report to " + path, exception);
        }
    }

    @Override
    public String toString() {
        return toJson();
    }

    private static JsonObject hypothesisJson(final HypothesisReport hypothesis) {
        final JsonObject json = new JsonObject();
        json.addProperty("id", hypothesis.id());
        json.addProperty("grammar", hypothesis.grammar());
        json.add("modes", modesJson(hypothesis.modes()));
        return json;
    }

    private static JsonArray modesJson(final List<ModeReport> modes) {
        final JsonArray json = new JsonArray();
        for (final ModeReport mode : modes) {
            final JsonObject modeJson = new JsonObject();
            modeJson.addProperty("mode", mode.mode());
            modeJson.addProperty("grammar", mode.grammar());
            final JsonArray rules = new JsonArray();
            for (final String rule : mode.activeRuleIds()) {
                rules.add(new JsonPrimitive(rule));
            }
            modeJson.add("activeRules", rules);
            final JsonArray partitions = new JsonArray();
            for (final PartitionMetrics metrics : mode.partitions()) {
                partitions.add(partitionJson(metrics));
            }
            modeJson.add("partitions", partitions);
            json.add(modeJson);
        }
        return json;
    }

    private static JsonObject partitionJson(final PartitionMetrics metrics) {
        final JsonObject json = new JsonObject();
        json.addProperty("partition", metrics.partition());
        json.addProperty("fromIndex", metrics.fromIndex());
        json.addProperty("toIndex", metrics.toIndex());
        json.addProperty("evaluationCount", metrics.evaluationCount());
        json.addProperty("completeCount", metrics.completeCount());
        json.addProperty("formingCount", metrics.formingCount());
        json.addProperty("ambiguousCount", metrics.ambiguousCount());
        json.addProperty("noMatchCount", metrics.noMatchCount());
        json.addProperty("invalidatedCount", metrics.invalidatedCount());
        json.addProperty("insufficientHistoryCount", metrics.insufficientHistoryCount());
        json.addProperty("matchRate", metrics.matchRate());
        json.addProperty("ambiguousRate", metrics.ambiguousRate());
        json.addProperty("noMatchRate", metrics.noMatchRate());
        json.addProperty("confirmationLagBars", metrics.confirmationLagBars());
        json.addProperty("labelStabilityJaccard", metrics.labelStabilityJaccard());
        json.addProperty("evidenceEvaluationCount", metrics.evidenceEvaluationCount());
        json.addProperty("evidencePassCount", metrics.evidencePassCount());
        json.addProperty("evidenceFailCount", metrics.evidenceFailCount());
        json.addProperty("evidencePendingCount", metrics.evidencePendingCount());
        json.addProperty("evidenceUnavailableCount", metrics.evidenceUnavailableCount());
        json.addProperty("evidenceNotApplicableCount", metrics.evidenceNotApplicableCount());
        json.addProperty("evidencePassRate", metrics.evidencePassRate());
        final JsonArray rules = new JsonArray();
        for (final RuleMetrics rule : metrics.rules()) {
            final JsonObject ruleJson = new JsonObject();
            ruleJson.addProperty("ruleId", rule.ruleId());
            ruleJson.addProperty("evaluationCount", rule.evaluationCount());
            ruleJson.addProperty("passCount", rule.passCount());
            ruleJson.addProperty("failCount", rule.failCount());
            ruleJson.addProperty("pendingCount", rule.pendingCount());
            ruleJson.addProperty("unavailableCount", rule.unavailableCount());
            ruleJson.addProperty("notApplicableCount", rule.notApplicableCount());
            ruleJson.addProperty("passRate", rule.passRate());
            ruleJson.addProperty("scoredCount", rule.scoredCount());
            ruleJson.addProperty("scoreMean", rule.scoreMean());
            ruleJson.addProperty("scoreMin", rule.scoreMin());
            ruleJson.addProperty("scoreMax", rule.scoreMax());
            rules.add(ruleJson);
        }
        json.add("rules", rules);
        return json;
    }

    private static JsonObject robustnessJson(final RobustnessReport robustness) {
        final JsonObject json = new JsonObject();
        final JsonArray detectors = new JsonArray();
        for (final DetectorResult detector : robustness.detectors()) {
            final JsonObject detectorJson = new JsonObject();
            detectorJson.addProperty("name", detector.name());
            detectorJson.addProperty("mode", detector.mode().mode());
            detectorJson.addProperty("grammar", detector.mode().grammar());
            final JsonArray partitions = new JsonArray();
            for (final PartitionMetrics metrics : detector.mode().partitions()) {
                partitions.add(partitionJson(metrics));
            }
            detectorJson.add("partitions", partitions);
            detectors.add(detectorJson);
        }
        json.add("detectors", detectors);
        return json;
    }

    private static JsonArray nullsJson(final List<NullReport> nulls) {
        final JsonArray json = new JsonArray();
        for (final NullReport nullReport : nulls) {
            final JsonObject nullJson = new JsonObject();
            nullJson.addProperty("grammar", nullReport.grammar());
            nullJson.addProperty("blockLength", nullReport.blockLength());
            nullJson.addProperty("ensembleSize", nullReport.ensembleSize());
            nullJson.addProperty("seed", nullReport.seed());
            final JsonArray partitions = new JsonArray();
            for (final PartitionMetrics metrics : nullReport.partitions()) {
                partitions.add(partitionJson(metrics));
            }
            nullJson.add("partitions", partitions);
            json.add(nullJson);
        }
        return json;
    }

    private static String requireText(final String value, final String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static <T> List<T> immutable(final List<T> values, final String name) {
        if (values == null) {
            throw new NullPointerException(name);
        }
        return List.copyOf(values);
    }

    /** Immutable report for one hypothesis. */
    /** Immutable locked partition echo. */
    record PartitionSpec(String name, java.time.LocalDate start, java.time.LocalDate end) {
        PartitionSpec {
            name = requireText(name, "partition.name");
            Objects.requireNonNull(start, "partition.start");
            Objects.requireNonNull(end, "partition.end");
            if (start.isAfter(end)) {
                throw new IllegalArgumentException("partition start must not exceed end");
            }
        }
    }

    record HypothesisReport(String id, String grammar, List<ModeReport> modes) {
        HypothesisReport {
            id = requireText(id, "hypothesis.id");
            grammar = requireText(grammar, "hypothesis.grammar");
            modes = immutable(modes, "hypothesis.modes");
        }

        ModeReport topologyOnly() {
            return modes.stream().filter(mode -> "topology-only".equals(mode.mode())).findFirst().orElse(null);
        }
    }

    /** Immutable report for one study mode. */
    record ModeReport(String mode, String grammar, List<String> activeRuleIds, List<PartitionMetrics> partitions) {
        ModeReport {
            mode = requireText(mode, "mode");
            grammar = requireText(grammar, "grammar");
            activeRuleIds = activeRuleIds == null ? List.of() : List.copyOf(activeRuleIds);
            partitions = immutable(partitions, "partitions");
        }
    }

    /** Immutable metrics for exactly one protocol partition. */
    record PartitionMetrics(String partition, int fromIndex, int toIndex, long evaluationCount, long completeCount,
            long formingCount, long ambiguousCount, long noMatchCount, long invalidatedCount,
            long insufficientHistoryCount, double matchRate, double ambiguousRate, double noMatchRate,
            double confirmationLagBars, double labelStabilityJaccard, long evidenceEvaluationCount,
            long evidencePassCount, long evidenceFailCount, long evidencePendingCount, long evidenceUnavailableCount,
            long evidenceNotApplicableCount, double evidencePassRate, List<RuleMetrics> rules) {
        PartitionMetrics {
            partition = requireText(partition, "partition");
            if (fromIndex > toIndex && evaluationCount > 0) {
                throw new IllegalArgumentException("fromIndex must not exceed toIndex");
            }
            if (evaluationCount < 0 || completeCount < 0 || evidenceEvaluationCount < 0) {
                throw new IllegalArgumentException("metric counts must be non-negative");
            }
            rules = immutable(rules, "rules");
        }
    }

    /**
     * Immutable counts for one relationship rule in one partition.
     *
     * @param scoredCount number of PASS evaluations carrying a soft score
     * @param scoreMean   mean of the carried scores; {@code 0} when unscored
     * @param scoreMin    minimum carried score; {@code 0} when unscored
     * @param scoreMax    maximum carried score; {@code 0} when unscored
     */
    record RuleMetrics(String ruleId, long evaluationCount, long passCount, long failCount, long pendingCount,
            long unavailableCount, long notApplicableCount, double passRate, long scoredCount, double scoreMean,
            double scoreMin, double scoreMax) {
        RuleMetrics {
            ruleId = requireText(ruleId, "ruleId");
            if (evaluationCount < 0 || passCount < 0 || failCount < 0 || pendingCount < 0 || unavailableCount < 0
                    || notApplicableCount < 0 || scoredCount < 0) {
                throw new IllegalArgumentException("rule metric counts must be non-negative");
            }
        }
    }

    /** Detector-robustness results for one detector configuration. */
    record DetectorResult(String name, ModeReport mode) {
        DetectorResult {
            name = requireText(name, "detector.name");
            mode = Objects.requireNonNull(mode, "mode");
        }
    }

    /** Immutable detector matrix report. */
    record RobustnessReport(List<DetectorResult> detectors) {
        RobustnessReport {
            detectors = immutable(detectors, "detectors");
        }
    }

    /** Immutable null-ensemble report for one stationary block length. */
    record NullReport(String grammar, int blockLength, int ensembleSize, long seed, List<PartitionMetrics> partitions) {
        NullReport {
            if (grammar == null || grammar.isBlank()) {
                throw new IllegalArgumentException("null report grammar must not be blank");
            }
            if (blockLength <= 0 || ensembleSize <= 0) {
                throw new IllegalArgumentException("null parameters must be positive");
            }
            partitions = immutable(partitions, "partitions");
        }
    }
}

/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Immutable, content-addressed loader for the CF-525 Elliott hypothesis study
 * protocol.
 *
 * <p>
 * The protocol JSON and all referenced dataset resources are loaded from the
 * classpath. Dataset hashes are checked before this object is returned, so a
 * protocol cannot be used with silently changed input data.
 * </p>
 */
final class ElliottStudyProtocol {

    /** Classpath location of the frozen protocol document. */
    static final String RESOURCE = "/org/ta4j/core/analysis/elliott/cf525-study-protocol-v1.json";

    private static final Gson GSON = new Gson();
    private static final long SEED_MULTIPLIER = 1_000_003L;

    private final int schemaVersion;
    private final String protocolId;
    private final String version;
    private final LocalDate frozenAt;
    private final String fingerprintSha256;
    private final List<Hypothesis> hypotheses;
    private final List<DatasetSpec> datasets;
    private final Partitions partitions;
    private final List<DetectorConfiguration> detectorConfigurations;
    private final NullSpec nullEnsemble;
    private final MomentumSpec momentumIndicator;
    private final String primaryDetector;
    private final List<String> competingGrammars;
    private final List<String> metrics;
    private final List<String> ablationSet;

    private ElliottStudyProtocol(int schemaVersion, String protocolId, String version, LocalDate frozenAt,
            String fingerprintSha256, List<Hypothesis> hypotheses, List<DatasetSpec> datasets, Partitions partitions,
            List<DetectorConfiguration> detectorConfigurations, NullSpec nullEnsemble, List<String> competingGrammars,
            List<String> metrics, List<String> ablationSet, MomentumSpec momentumIndicator, String primaryDetector) {
        if (schemaVersion != 1) {
            throw new IllegalArgumentException("Unsupported protocol schemaVersion: " + schemaVersion);
        }
        this.schemaVersion = schemaVersion;
        this.protocolId = requireText(protocolId, "protocolId");
        this.version = requireText(version, "protocolVersion");
        this.frozenAt = Objects.requireNonNull(frozenAt, "frozenAt");
        this.fingerprintSha256 = requireText(fingerprintSha256, "fingerprintSha256");
        requireNonEmpty(hypotheses, "hypotheses");
        this.hypotheses = List.copyOf(hypotheses);
        requireNonEmpty(datasets, "datasets");
        this.datasets = List.copyOf(datasets);
        this.partitions = Objects.requireNonNull(partitions, "partitions");
        requireNonEmpty(detectorConfigurations, "detectorConfigurations");
        this.detectorConfigurations = List.copyOf(detectorConfigurations);
        this.nullEnsemble = Objects.requireNonNull(nullEnsemble, "nullEnsemble");
        requireNonEmpty(competingGrammars, "competingGrammars");
        this.competingGrammars = List.copyOf(competingGrammars);
        requireNonEmpty(metrics, "metrics");
        this.metrics = List.copyOf(metrics);
        requireNonEmpty(ablationSet, "ablationSet");
        this.ablationSet = List.copyOf(ablationSet);
        this.momentumIndicator = Objects.requireNonNull(momentumIndicator, "momentumIndicator");
        this.primaryDetector = requireText(primaryDetector, "primaryDetector");
    }

    /**
     * Loads and verifies the frozen protocol and every dataset referenced by it.
     *
     * @return the verified protocol
     * @throws IllegalStateException if the protocol, a referenced resource, or a
     *                               checksum is invalid
     */
    public static ElliottStudyProtocol load() {
        byte[] protocolBytes;
        try (InputStream input = openResource(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing study protocol resource: " + RESOURCE);
            }
            protocolBytes = input.readAllBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read study protocol resource: " + RESOURCE, exception);
        }

        String fingerprint = sha256(protocolBytes);
        RawProtocol raw;
        try {
            raw = GSON.fromJson(new String(protocolBytes, StandardCharsets.UTF_8), RawProtocol.class);
        } catch (JsonParseException exception) {
            throw new IllegalStateException("Failed to parse study protocol resource: " + RESOURCE, exception);
        }
        if (raw == null) {
            throw new IllegalStateException("Study protocol resource is empty: " + RESOURCE);
        }

        try {
            ElliottStudyProtocol protocol = fromRaw(raw, fingerprint);
            verifyDatasets(protocol.datasets);
            return protocol;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid study protocol resource: " + RESOURCE, exception);
        }
    }

    /** @return the JSON schema version */
    public int schemaVersion() {
        return schemaVersion;
    }

    /** @return stable protocol identifier */
    public String protocolId() {
        return protocolId;
    }

    /** @return protocol semantic version */
    public String version() {
        return version;
    }

    /** @return date on which the protocol was frozen */
    public LocalDate frozenAt() {
        return frozenAt;
    }

    /**
     * Returns the SHA-256 fingerprint of the exact protocol resource bytes.
     *
     * @return lowercase hexadecimal SHA-256 digest
     */
    public String fingerprintSha256() {
        return fingerprintSha256;
    }

    /** @return H1 followed by H2 */
    public List<Hypothesis> hypotheses() {
        return hypotheses;
    }

    /** @return the H1 hypothesis */
    public Hypothesis h1() {
        return hypotheses.get(0);
    }

    /** @return the H2 hypothesis */
    public Hypothesis h2() {
        return hypotheses.get(1);
    }

    /** @return verified dataset specifications */
    public List<DatasetSpec> datasets() {
        return datasets;
    }

    /** @return calibration, validation, and holdout bounds */
    public Partitions partitions() {
        return partitions;
    }

    /**
     * Returns whether calibration-dependent work is permitted on a date. The
     * explicit forbidden start is authoritative even when a date falls in a later
     * partition.
     *
     * @param date date to check
     * @return {@code true} only before the forbidden calibration start
     */
    public boolean isCalibrationAllowed(LocalDate date) {
        return partitions.isCalibrationAllowed(date);
    }

    /** @return detector configurations in protocol order */
    public List<DetectorConfiguration> detectorConfigurations() {
        return detectorConfigurations;
    }

    /** @return stationary-block-bootstrap null ensemble specification */
    public NullSpec nullEnsemble() {
        return nullEnsemble;
    }

    /** @return competing grammar identifiers in protocol order */
    public List<String> competingGrammars() {
        return competingGrammars;
    }

    /** @return metric identifiers in protocol order */
    public List<String> metrics() {
        return metrics;
    }

    /** @return ablation labels in protocol order */
    public List<String> ablationSet() {
        return ablationSet;
    }

    /** @return frozen wave-5 divergence momentum indicator specification */
    public MomentumSpec momentumIndicator() {
        return momentumIndicator;
    }

    /** @return stable name of the primary detector configuration */
    public String primaryDetector() {
        return primaryDetector;
    }

    private static ElliottStudyProtocol fromRaw(RawProtocol raw, String fingerprint) {
        if (raw.schemaVersion == null) {
            throw new IllegalArgumentException("schemaVersion is required");
        }
        if (raw.hypotheses == null) {
            throw new IllegalArgumentException("hypotheses is required");
        }
        Hypothesis h1 = toHypothesis(raw.hypotheses.h1, "H1");
        Hypothesis h2 = toHypothesis(raw.hypotheses.h2, "H2");

        if (raw.datasets == null) {
            throw new IllegalArgumentException("datasets is required");
        }
        List<DatasetSpec> datasets = raw.datasets.stream().map(ElliottStudyProtocol::toDataset).toList();
        if (raw.partitions == null) {
            throw new IllegalArgumentException("partitions is required");
        }
        Partitions partitions = toPartitions(raw.partitions);
        if (raw.detectorConfigurations == null) {
            throw new IllegalArgumentException("detectorConfigurations is required");
        }
        List<DetectorConfiguration> detectorConfigurations = raw.detectorConfigurations.stream()
                .map(ElliottStudyProtocol::toDetectorConfiguration)
                .toList();
        NullSpec nullEnsemble = toNullSpec(raw.nullEnsemble);
        MomentumSpec momentumIndicator = toMomentumSpec(raw.momentumIndicator);

        return new ElliottStudyProtocol(raw.schemaVersion, raw.protocolId, raw.protocolVersion,
                parseDate(raw.frozenAt, "frozenAt"), fingerprint, List.of(h1, h2), datasets, partitions,
                detectorConfigurations, nullEnsemble, raw.competingGrammars, raw.metrics, raw.ablationSet,
                momentumIndicator, raw.primaryDetector);
    }

    private static MomentumSpec toMomentumSpec(RawMomentumSpec raw) {
        if (raw == null) {
            throw new IllegalArgumentException("momentumIndicator is required");
        }
        if (raw.barCount == null) {
            throw new IllegalArgumentException("momentumIndicator.barCount is required");
        }
        return new MomentumSpec(raw.type, raw.barCount);
    }

    private static Hypothesis toHypothesis(RawHypothesis raw, String expectedId) {
        if (raw == null) {
            throw new IllegalArgumentException("Missing hypothesis " + expectedId);
        }
        Hypothesis hypothesis = new Hypothesis(raw.id, raw.statement, raw.grammar);
        if (!expectedId.equals(hypothesis.id())) {
            throw new IllegalArgumentException(
                    "Hypothesis key " + expectedId + " does not match id " + hypothesis.id());
        }
        return hypothesis;
    }

    private static DatasetSpec toDataset(RawDataset raw) {
        if (raw == null) {
            throw new IllegalArgumentException("datasets must not contain null entries");
        }
        return new DatasetSpec(raw.id, raw.resource, raw.role, raw.asset, raw.barSize, raw.sha256);
    }

    private static Partitions toPartitions(RawPartitions raw) {
        if (raw.calibration == null || raw.validation == null || raw.holdout == null) {
            throw new IllegalArgumentException("all partition ranges are required");
        }
        return new Partitions(parseDate(raw.calibration.start, "calibration.start"),
                parseDate(raw.calibration.end, "calibration.end"), parseDate(raw.validation.start, "validation.start"),
                parseDate(raw.validation.end, "validation.end"), parseDate(raw.holdout.start, "holdout.start"),
                parseDate(raw.holdout.end, "holdout.end"),
                parseDate(raw.forbiddenCalibrationStart, "forbiddenCalibrationStart"));
    }

    private static DetectorConfiguration toDetectorConfiguration(RawDetectorConfiguration raw) {
        if (raw == null) {
            throw new IllegalArgumentException("detectorConfigurations must not contain null entries");
        }
        return new DetectorConfiguration(raw.name, raw.factory, raw.params);
    }

    private static NullSpec toNullSpec(RawNullSpec raw) {
        if (raw == null) {
            throw new IllegalArgumentException("nullEnsemble is required");
        }
        if (raw.ensembleSize == null || raw.seed == null) {
            throw new IllegalArgumentException("nullEnsemble ensembleSize and seed are required");
        }
        return new NullSpec(raw.type, raw.expectedBlockLengths, raw.ensembleSize, raw.seed);
    }

    private static void verifyDatasets(List<DatasetSpec> datasets) {
        for (DatasetSpec dataset : datasets) {
            byte[] data;
            try (InputStream input = openResource(dataset.resource())) {
                if (input == null) {
                    throw new IllegalStateException("Missing dataset resource: " + dataset.resource());
                }
                data = input.readAllBytes();
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to read dataset resource: " + dataset.resource(), exception);
            }
            String actualSha256 = sha256(data);
            if (!dataset.sha256().equals(actualSha256)) {
                throw new IllegalStateException("Dataset checksum mismatch for " + dataset.resource() + ": expected "
                        + dataset.sha256() + " but found " + actualSha256);
            }
        }
    }

    private static InputStream openResource(String resource) {
        String normalized = resource != null && resource.startsWith("/") ? resource : "/" + resource;
        return ElliottStudyProtocol.class.getResourceAsStream(normalized);
    }

    private static String sha256(byte[] bytes) {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        byte[] hash = digest.digest(bytes);
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte value : hash) {
            hex.append(String.format("%02x", value & 0xff));
        }
        return hex.toString();
    }

    private static LocalDate parseDate(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return LocalDate.parse(value);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private static <T> void requireNonEmpty(List<T> values, String fieldName) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be empty");
        }
    }

    /** Immutable hypothesis entry. */
    record Hypothesis(String id, String statement, String grammar) {
        Hypothesis {
            id = requireText(id, "hypothesis.id");
            statement = requireText(statement, "hypothesis.statement");
            grammar = requireText(grammar, "hypothesis.grammar");
        }
    }

    /** Immutable dataset entry whose bytes are verified by {@link #load()}. */
    record DatasetSpec(String id, String resource, String role, String asset, String barSize, String sha256) {
        DatasetSpec {
            id = requireText(id, "dataset.id");
            resource = requireText(resource, "dataset.resource");
            role = requireText(role, "dataset.role");
            asset = requireText(asset, "dataset.asset");
            barSize = requireText(barSize, "dataset.barSize");
            sha256 = requireText(sha256, "dataset.sha256");
        }
    }

    /** Immutable inclusive calendar partition bounds. */
    record Partitions(LocalDate calibrationStart, LocalDate calibrationEnd, LocalDate validationStart,
            LocalDate validationEnd, LocalDate holdoutStart, LocalDate holdoutEnd,
            LocalDate forbiddenCalibrationStart) {
        Partitions {
            Objects.requireNonNull(calibrationStart, "calibrationStart");
            Objects.requireNonNull(calibrationEnd, "calibrationEnd");
            Objects.requireNonNull(validationStart, "validationStart");
            Objects.requireNonNull(validationEnd, "validationEnd");
            Objects.requireNonNull(holdoutStart, "holdoutStart");
            Objects.requireNonNull(holdoutEnd, "holdoutEnd");
            Objects.requireNonNull(forbiddenCalibrationStart, "forbiddenCalibrationStart");
            if (calibrationStart.isAfter(calibrationEnd)) {
                throw new IllegalArgumentException("calibrationStart must not be after calibrationEnd");
            }
            if (validationStart.isAfter(validationEnd)) {
                throw new IllegalArgumentException("validationStart must not be after validationEnd");
            }
            if (holdoutStart.isAfter(holdoutEnd)) {
                throw new IllegalArgumentException("holdoutStart must not be after holdoutEnd");
            }
        }

        /**
         * Returns whether calibration is permitted for a date under the protocol's
         * embargo.
         *
         * @param date date to check
         * @return false on or after {@code forbiddenCalibrationStart}
         */
        public boolean isCalibrationAllowed(LocalDate date) {
            Objects.requireNonNull(date, "date");
            return date.isBefore(forbiddenCalibrationStart);
        }
    }

    /** Immutable detector factory configuration. */
    record DetectorConfiguration(String name, String factory, List<Integer> params) {
        DetectorConfiguration {
            name = requireText(name, "detectorConfiguration.name");
            factory = requireText(factory, "detectorConfiguration.factory");
            params = params == null ? List.of() : List.copyOf(params);
        }
    }

    /**
     * Frozen wave-5 divergence momentum indicator. The preregistered definition
     * participates in the protocol fingerprint, so two executions cannot silently
     * use different momentum readings while claiming the same protocol.
     *
     * @since 0.24.2
     */
    record MomentumSpec(String type, int barCount) {
        MomentumSpec {
            type = requireText(type, "momentumIndicator.type");
            if (!"RSI".equals(type)) {
                throw new IllegalArgumentException(
                        "momentumIndicator.type must be RSI for schemaVersion 1, found " + type);
            }
            if (barCount < 2 || barCount > 1000) {
                throw new IllegalArgumentException("momentumIndicator.barCount must be within [2, 1000]");
            }
        }
    }

    /** Immutable stationary-block-bootstrap specification. */
    record NullSpec(String type, List<Integer> blockLengths, int ensembleSize, long seed) {
        NullSpec {
            type = requireText(type, "nullEnsemble.type");
            if (blockLengths == null || blockLengths.isEmpty()) {
                throw new IllegalArgumentException("nullEnsemble.blockLengths must not be empty");
            }
            blockLengths = List.copyOf(blockLengths);
            if (blockLengths.stream().anyMatch(length -> length == null || length <= 0)) {
                throw new IllegalArgumentException("nullEnsemble.blockLengths must contain positive values");
            }
            if (ensembleSize <= 0) {
                throw new IllegalArgumentException("nullEnsemble.ensembleSize must be positive");
            }
        }

        /**
         * Derives a deterministic, index-specific seed for one null ensemble member.
         *
         * @param ensembleIndex zero-based ensemble member index
         * @return deterministic derived seed
         */
        public long seedFor(int ensembleIndex) {
            if (ensembleIndex < 0) {
                throw new IllegalArgumentException("ensembleIndex must be non-negative");
            }
            return seed * SEED_MULTIPLIER + ensembleIndex;
        }
    }

    private record RawProtocol(Integer schemaVersion, String protocolId, String protocolVersion, String frozenAt,
            RawHypotheses hypotheses, List<RawDataset> datasets, RawPartitions partitions,
            List<RawDetectorConfiguration> detectorConfigurations, RawNullSpec nullEnsemble,
            List<String> competingGrammars, List<String> metrics, List<String> ablationSet,
            RawMomentumSpec momentumIndicator, String primaryDetector) {
    }

    private record RawHypotheses(@SerializedName("H1") RawHypothesis h1, @SerializedName("H2") RawHypothesis h2) {
    }

    private record RawHypothesis(String id, String statement, String grammar) {
    }

    private record RawDataset(String id, String resource, String role, String asset, String barSize, String sha256) {
    }

    private record RawPartitions(RawPartitionRange calibration, RawPartitionRange validation, RawPartitionRange holdout,
            String forbiddenCalibrationStart) {
    }

    private record RawPartitionRange(String start, String end) {
    }

    private record RawDetectorConfiguration(String name, String factory, List<Integer> params) {
    }

    private record RawNullSpec(String type, List<Integer> expectedBlockLengths, Integer ensembleSize, Long seed) {
    }

    private record RawMomentumSpec(String type, Integer barCount) {
    }
}

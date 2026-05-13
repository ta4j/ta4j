/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.indicators;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.serialization.IndicatorSerialization;
import org.ta4j.core.num.Num;

/**
 * Deterministic manifest definition for a curated indicator subset.
 *
 * @param manifestId    stable manifest identifier
 * @param schemaVersion schema version
 * @param datasetId     source dataset identifier
 * @param indicators    indicator definitions in deterministic order
 * @param metadata      optional metadata map
 * @since 0.22.7
 */
public record IndicatorFamilyManifest(String manifestId, String schemaVersion, String datasetId,
        List<IndicatorManifestItem> indicators, Map<String, String> metadata) {

    public static final String DEFAULT_SCHEMA_VERSION = "v1";
    private static final String PAIR_KEY_SEPARATOR = "/";

    /**
     * Creates a validated manifest.
     */
    public IndicatorFamilyManifest {
        Objects.requireNonNull(manifestId, "manifestId");
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        Objects.requireNonNull(datasetId, "datasetId");
        if (manifestId.isBlank()) {
            throw new IllegalArgumentException("manifestId must not be blank");
        }
        if (schemaVersion.isBlank()) {
            throw new IllegalArgumentException("schemaVersion must not be blank");
        }
        if (datasetId.isBlank()) {
            throw new IllegalArgumentException("datasetId must not be blank");
        }
        if (indicators == null || indicators.isEmpty()) {
            throw new IllegalArgumentException("indicators must not be empty");
        }

        HashSet<String> indicatorIds = new HashSet<>(indicators.size() * 2);
        for (int index = 0; index < indicators.size(); index++) {
            IndicatorManifestItem item = indicators.get(index);
            if (item == null) {
                throw new IllegalArgumentException("indicators[" + index + "] must not be null");
            }
            if (!indicatorIds.add(item.indicatorId())) {
                throw new IllegalArgumentException("indicator ids must be unique");
            }
        }
        indicators = List.copyOf(indicators);
        metadata = canonicalizeMetadata(metadata);
    }

    /**
     * Creates a manifest for tests and local experiments.
     *
     * @param manifestId stable manifest identifier
     * @param datasetId  dataset identifier
     * @param indicators manifest items
     * @return manifest using the default schema version
     * @since 0.22.7
     */
    public static IndicatorFamilyManifest of(String manifestId, String datasetId,
            List<IndicatorManifestItem> indicators) {
        return new IndicatorFamilyManifest(manifestId, DEFAULT_SCHEMA_VERSION, datasetId, indicators, Map.of());
    }

    /**
     * Resolves serialized manifest entries into live indicators.
     *
     * @param series input series used to reconstruct indicators
     * @return list of resolved indicator instances in manifest order
     * @since 0.22.7
     */
    public List<ResolvedManifestItem> resolveIndicators(BarSeries series) {
        Objects.requireNonNull(series, "series");
        List<ResolvedManifestItem> resolved = new ArrayList<>(indicators.size());
        for (IndicatorManifestItem item : indicators) {
            @SuppressWarnings("unchecked")
            Indicator<Num> indicator = (Indicator<Num>) IndicatorSerialization.fromJson(series,
                    item.indicatorDescriptor());
            Objects.requireNonNull(indicator, "resolved indicator");
            resolved.add(new ResolvedManifestItem(item.indicatorId(), item.indicatorDescriptor(), indicator,
                    item.metadata()));
        }
        return resolved;
    }

    /**
     * Returns a deterministic configuration hash for reproducibility.
     *
     * @return manifest configuration hash
     * @since 0.22.7
     */
    public String configHash() {
        return sha256Hex(canonicalDefinition());
    }

    private String canonicalDefinition() {
        StringBuilder builder = new StringBuilder();
        appendCanonical(builder, manifestId);
        appendCanonical(builder, schemaVersion);
        appendCanonical(builder, datasetId);
        appendCanonical(builder, String.valueOf(indicators.size()));
        for (IndicatorManifestItem item : indicators) {
            appendCanonical(builder, item.indicatorId());
            appendCanonical(builder, item.indicatorDescriptor());
            appendCanonical(builder, normalizedMetadata(item.metadata()));
        }
        for (String key : metadata.keySet().stream().sorted().toList()) {
            appendCanonical(builder, key);
            appendCanonical(builder, metadata.get(key));
        }
        return builder.toString();
    }

    private static String normalizedMetadata(Map<String, String> metadata) {
        if (metadata.isEmpty()) {
            return "0";
        }

        List<String> keys = new ArrayList<>(metadata.keySet());
        keys.sort(Comparator.naturalOrder());

        StringBuilder builder = new StringBuilder();
        appendCanonical(builder, String.valueOf(keys.size()));
        for (String key : keys) {
            appendCanonical(builder, key);
            appendCanonical(builder, metadata.get(key));
        }
        return builder.toString();
    }

    private static Map<String, String> canonicalizeMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }

        Map<String, String> sortedMetadata = new TreeMap<>();
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                throw new IllegalArgumentException("metadata keys must not be blank");
            }
            if (entry.getValue() == null) {
                throw new IllegalArgumentException("metadata values must not be null");
            }
            sortedMetadata.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(sortedMetadata));
    }

    private static void validateIndicatorId(String indicatorId) {
        if (indicatorId.isBlank()) {
            throw new IllegalArgumentException("indicatorId must not be blank");
        }
        if (indicatorId.contains(PAIR_KEY_SEPARATOR)) {
            throw new IllegalArgumentException("indicatorId must not contain '" + PAIR_KEY_SEPARATOR + "'");
        }
    }

    private static void appendCanonical(StringBuilder builder, String value) {
        builder.append(value.length()).append(':').append(value).append('|');
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", exception);
        }
    }

    /**
     * A single indicator entry in a manifest.
     *
     * @param indicatorId         stable indicator identifier
     * @param indicatorDescriptor serialized indicator descriptor
     * @param metadata            optional metadata
     * @since 0.22.7
     */
    public record IndicatorManifestItem(String indicatorId, String indicatorDescriptor, Map<String, String> metadata) {

        public IndicatorManifestItem {
            Objects.requireNonNull(indicatorId, "indicatorId");
            Objects.requireNonNull(indicatorDescriptor, "indicatorDescriptor");
            validateIndicatorId(indicatorId);
            if (indicatorDescriptor.isBlank()) {
                throw new IllegalArgumentException("indicatorDescriptor must not be blank");
            }
            metadata = canonicalizeMetadata(metadata);
        }
    }

    /**
     * A resolved manifest item with an attached indicator.
     *
     * @param indicatorId         stable indicator identifier
     * @param indicatorDescriptor serialized indicator descriptor
     * @param indicator           reconstructed indicator
     * @param metadata            optional metadata
     * @since 0.22.7
     */
    public record ResolvedManifestItem(String indicatorId, String indicatorDescriptor, Indicator<Num> indicator,
            Map<String, String> metadata) {
        public ResolvedManifestItem {
            Objects.requireNonNull(indicatorId, "indicatorId");
            Objects.requireNonNull(indicatorDescriptor, "indicatorDescriptor");
            Objects.requireNonNull(indicator, "indicator");
            metadata = canonicalizeMetadata(metadata);
        }
    }
}

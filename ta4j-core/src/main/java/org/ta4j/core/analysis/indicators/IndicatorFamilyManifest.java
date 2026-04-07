/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.indicators;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

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
        for (IndicatorManifestItem item : indicators) {
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
        return Integer.toHexString(canonicalDefinition().hashCode());
    }

    private String canonicalDefinition() {
        StringJoiner joiner = new StringJoiner("|");
        joiner.add(manifestId).add(schemaVersion).add(datasetId).add(String.valueOf(indicators.size()));
        for (IndicatorManifestItem item : indicators) {
            joiner.add(item.indicatorId());
            joiner.add(item.indicatorDescriptor());
            joiner.add(normalizedMetadata(item.metadata()));
        }
        for (String key : metadata.keySet().stream().sorted().toList()) {
            joiner.add(key).add(metadata.get(key));
        }
        return joiner.toString();
    }

    private String normalizedMetadata(Map<String, String> metadata) {
        if (metadata.isEmpty()) {
            return "{}";
        }

        List<String> keys = new ArrayList<>(metadata.keySet());
        keys.sort(Comparator.naturalOrder());

        StringJoiner joiner = new StringJoiner(",", "{", "}");
        for (String key : keys) {
            joiner.add(key + ":" + metadata.get(key));
        }
        return joiner.toString();
    }

    private static Map<String, String> canonicalizeMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }

        Map<String, String> canonicalMetadata = new HashMap<>();
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                throw new IllegalArgumentException("metadata keys must not be blank");
            }
            if (entry.getValue() == null) {
                throw new IllegalArgumentException("metadata values must not be null");
            }
            canonicalMetadata.put(entry.getKey(), entry.getValue());
        }
        return Map.copyOf(canonicalMetadata);
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
            if (indicatorId.isBlank()) {
                throw new IllegalArgumentException("indicatorId must not be blank");
            }
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

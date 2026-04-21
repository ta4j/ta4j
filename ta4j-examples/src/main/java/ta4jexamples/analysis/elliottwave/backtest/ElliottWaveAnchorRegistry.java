/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.elliottwave.backtest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.elliott.ElliottPhase;

import com.google.gson.Gson;

/**
 * Loads and resolves the BTC anchor registry used by the CF-17 anchor-aware
 * calibration study.
 *
 * <p>
 * The registry stores broad calendar windows plus provenance. Resolution is
 * deterministic: each anchor window is collapsed to the highest high or lowest
 * low inside the local ossified BTC dataset, then the resolved anchors are
 * partitioned chronologically into validation and holdout segments.
 *
 * <p>
 * These committed windows are the canonical BTC daily validation set for the
 * CF-17 macro study.
 * {@link ElliottWaveAnchorCalibrationHarness#defaultBitcoinAnchors(BarSeries)}
 * preserves that contract by translating the distance from the resolved
 * extremum to each window edge into {@code toleranceBefore} and
 * {@code toleranceAfter}, so acceptable match windows stay pinned to the
 * registry instead of drifting via runtime heuristics.
 *
 * @since 0.22.7
 */
final class ElliottWaveAnchorRegistry {

    static final String DEFAULT_RESOURCE = "/ta4jexamples/analysis/elliottwave/backtest/BTC-anchor-registry-v2.json";

    private static final Gson GSON = new Gson();

    private final String registryId;
    private final String datasetResource;
    private final String provenance;
    private final List<AnchorSpec> anchors;

    private ElliottWaveAnchorRegistry(String registryId, String datasetResource, String provenance,
            List<AnchorSpec> anchors) {
        this.registryId = requireText(registryId, "registryId");
        this.datasetResource = requireText(datasetResource, "datasetResource");
        this.provenance = requireText(provenance, "provenance");
        if (anchors == null || anchors.isEmpty()) {
            throw new IllegalArgumentException("anchors must not be empty");
        }
        this.anchors = anchors.stream().sorted(Comparator.comparing(AnchorSpec::windowStart)).toList();
    }

    /**
     * Loads the registry from the given classpath resource.
     *
     * @param resource classpath resource path
     * @return parsed registry
     * @since 0.22.7
     */
    static ElliottWaveAnchorRegistry load(String resource) {
        String normalized = resource != null && resource.startsWith("/") ? resource : "/" + resource;
        try (InputStream stream = ElliottWaveAnchorRegistry.class.getResourceAsStream(normalized)) {
            if (stream == null) {
                throw new IllegalStateException("Missing anchor registry resource: " + normalized);
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                RegistryDocument document = GSON.fromJson(reader, RegistryDocument.class);
                Objects.requireNonNull(document, "document");
                List<AnchorSpec> anchorSpecs = new ArrayList<>();
                for (RegistryAnchor anchor : document.anchors()) {
                    anchorSpecs.add(anchor.toSpec());
                }
                return new ElliottWaveAnchorRegistry(document.registryId(), document.datasetResource(),
                        document.provenance(), anchorSpecs);
            }
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load anchor registry: " + normalized, ex);
        }
    }

    /**
     * Resolves every stored anchor window against the supplied series and assigns
     * the trailing anchors to holdout.
     *
     * <p>
     * Resolution never expands or contracts the committed calendar windows. The
     * JSON window bounds remain authoritative; the selected bar is simply the local
     * extremum inside each stored range.
     *
     * @param series       BTC series used for resolution
     * @param holdoutCount trailing resolved anchors reserved for holdout
     * @return resolved anchors in chronological order
     * @since 0.22.7
     */
    List<ResolvedAnchor> resolve(BarSeries series, int holdoutCount) {
        Objects.requireNonNull(series, "series");
        List<ResolvedAnchor> resolved = new ArrayList<>(anchors.size());
        for (AnchorSpec anchor : anchors) {
            resolved.add(resolve(series, anchor));
        }
        resolved.sort(Comparator.comparing(ResolvedAnchor::resolvedTime));

        int normalizedHoldout = Math.max(0, Math.min(holdoutCount, resolved.size()));
        int validationCutoff = Math.max(0, resolved.size() - normalizedHoldout);

        List<ResolvedAnchor> partitioned = new ArrayList<>(resolved.size());
        for (int i = 0; i < resolved.size(); i++) {
            AnchorPartition partition = i < validationCutoff ? AnchorPartition.VALIDATION : AnchorPartition.HOLDOUT;
            partitioned.add(resolved.get(i).withPartition(partition));
        }
        return List.copyOf(partitioned);
    }

    /**
     * @return registry identifier
     * @since 0.22.7
     */
    String registryId() {
        return registryId;
    }

    /**
     * @return backing BTC dataset resource name
     * @since 0.22.7
     */
    String datasetResource() {
        return datasetResource;
    }

    /**
     * @return provenance summary
     * @since 0.22.7
     */
    String provenance() {
        return provenance;
    }

    /**
     * @return stored anchor specs
     * @since 0.22.7
     */
    List<AnchorSpec> anchors() {
        return anchors;
    }

    private static ResolvedAnchor resolve(BarSeries series, AnchorSpec anchor) {
        long midpointEpochMillis = anchor.windowStart().toEpochMilli()
                + ((anchor.windowEnd().toEpochMilli() - anchor.windowStart().toEpochMilli()) / 2L);

        int bestIndex = -1;
        double bestPrice = anchor.kind() == AnchorKind.TOP ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        long bestDistance = Long.MAX_VALUE;

        for (int index = series.getBeginIndex(); index <= series.getEndIndex(); index++) {
            Bar bar = series.getBar(index);
            Instant endTime = bar.getEndTime();
            if (endTime.isBefore(anchor.windowStart()) || endTime.isAfter(anchor.windowEnd())) {
                continue;
            }

            double candidatePrice = priceFor(bar, anchor.kind());
            long candidateDistance = Math.abs(endTime.toEpochMilli() - midpointEpochMillis);
            if (bestIndex < 0 || better(anchor.kind(), candidatePrice, bestPrice)
                    || (same(candidatePrice, bestPrice) && (candidateDistance < bestDistance
                            || (candidateDistance == bestDistance && index < bestIndex)))) {
                bestIndex = index;
                bestPrice = candidatePrice;
                bestDistance = candidateDistance;
            }
        }

        if (bestIndex < 0) {
            throw new IllegalStateException("No bars found inside anchor window " + anchor.id());
        }

        return new ResolvedAnchor(anchor, bestIndex, series.getBar(bestIndex).getEndTime(), bestPrice,
                AnchorPartition.VALIDATION);
    }

    private static boolean better(AnchorKind kind, double candidatePrice, double bestPrice) {
        if (kind == AnchorKind.TOP) {
            return candidatePrice > bestPrice;
        }
        return candidatePrice < bestPrice;
    }

    private static boolean same(double left, double right) {
        return Double.compare(left, right) == 0;
    }

    private static double priceFor(Bar bar, AnchorKind kind) {
        Objects.requireNonNull(bar, "bar");
        double value = kind == AnchorKind.TOP ? bar.getHighPrice().doubleValue() : bar.getLowPrice().doubleValue();
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            value = bar.getClosePrice().doubleValue();
        }
        return value;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    /**
     * Anchor direction used when resolving local extrema.
     *
     * @since 0.22.7
     */
    enum AnchorKind {
        TOP, BOTTOM
    }

    /**
     * Chronological anchor partition used for validation-versus-holdout reporting.
     *
     * @since 0.22.7
     */
    enum AnchorPartition {
        VALIDATION, HOLDOUT
    }

    /**
     * Immutable anchor specification loaded from the registry file.
     *
     * @param id             stable anchor identifier
     * @param label          human-readable label
     * @param kind           anchor direction
     * @param windowStart    inclusive window start
     * @param windowEnd      inclusive window end
     * @param expectedPhases acceptable Elliott phases for a hit
     * @param source         provenance string
     * @param notes          optional note
     * @since 0.22.7
     */
    record AnchorSpec(String id, String label, AnchorKind kind, Instant windowStart, Instant windowEnd,
            Set<ElliottPhase> expectedPhases, String source, String notes) {

        /**
         * Creates a validated anchor specification.
         */
        AnchorSpec {
            id = requireText(id, "id");
            label = requireText(label, "label");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(windowStart, "windowStart");
            Objects.requireNonNull(windowEnd, "windowEnd");
            if (windowEnd.isBefore(windowStart)) {
                throw new IllegalArgumentException("windowEnd must not be before windowStart");
            }
            expectedPhases = expectedPhases == null || expectedPhases.isEmpty() ? EnumSet.noneOf(ElliottPhase.class)
                    : EnumSet.copyOf(expectedPhases);
            if (expectedPhases.isEmpty()) {
                throw new IllegalArgumentException("expectedPhases must not be empty");
            }
            source = requireText(source, "source");
            notes = notes == null ? "" : notes.trim();
        }
    }

    /**
     * Concrete anchor resolved against the BTC series.
     *
     * @param spec          original anchor specification
     * @param decisionIndex resolved series index
     * @param resolvedTime  resolved bar end time
     * @param resolvedPrice resolved bar high/low used for matching
     * @param partition     validation or holdout
     * @since 0.22.7
     */
    record ResolvedAnchor(AnchorSpec spec, int decisionIndex, Instant resolvedTime, double resolvedPrice,
            AnchorPartition partition) {

        /**
         * Creates a validated resolved anchor.
         */
        ResolvedAnchor {
            Objects.requireNonNull(spec, "spec");
            if (decisionIndex < 0) {
                throw new IllegalArgumentException("decisionIndex must be >= 0");
            }
            Objects.requireNonNull(resolvedTime, "resolvedTime");
            Objects.requireNonNull(partition, "partition");
            if (Double.isNaN(resolvedPrice) || Double.isInfinite(resolvedPrice)) {
                throw new IllegalArgumentException("resolvedPrice must be finite");
            }
        }

        /**
         * Creates a copy with a different partition.
         *
         * @param newPartition new partition
         * @return copied resolved anchor
         * @since 0.22.7
         */
        ResolvedAnchor withPartition(AnchorPartition newPartition) {
            return new ResolvedAnchor(spec, decisionIndex, resolvedTime, resolvedPrice, newPartition);
        }
    }

    /**
     * Raw registry document loaded through Gson.
     *
     * @param registryId      registry identifier
     * @param datasetResource dataset resource name
     * @param provenance      provenance summary
     * @param anchors         raw anchors
     */
    private record RegistryDocument(String registryId, String datasetResource, String provenance,
            List<RegistryAnchor> anchors) {
    }

    /**
     * Raw anchor entry loaded through Gson.
     *
     * @param id             stable anchor identifier
     * @param label          human-readable label
     * @param kind           anchor direction
     * @param windowStart    inclusive window start in ISO-8601 form
     * @param windowEnd      inclusive window end in ISO-8601 form
     * @param expectedPhases accepted Elliott phases as enum names
     * @param source         provenance string
     * @param notes          optional note
     */
    private record RegistryAnchor(String id, String label, AnchorKind kind, String windowStart, String windowEnd,
            List<String> expectedPhases, String source, String notes) {

        private AnchorSpec toSpec() {
            Set<ElliottPhase> phases = EnumSet.noneOf(ElliottPhase.class);
            for (String phase : Objects.requireNonNull(expectedPhases, "expectedPhases")) {
                phases.add(ElliottPhase.valueOf(requireText(phase, "phase")));
            }
            return new AnchorSpec(id, label, kind, Instant.parse(requireText(windowStart, "windowStart")),
                    Instant.parse(requireText(windowEnd, "windowEnd")), phases, source, notes);
        }
    }
}

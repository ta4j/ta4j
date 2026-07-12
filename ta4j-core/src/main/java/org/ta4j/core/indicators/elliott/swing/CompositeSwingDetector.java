/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.swing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.num.Num;

/**
 * Combines multiple swing detectors with exact or tolerant pivot agreement.
 *
 * <p>
 * Use the policy constructor for exact AND/OR compatibility. Use the
 * tolerance/quorum constructor when nearby same-type pivots should count as
 * consensus even when their indexes do not match exactly.
 *
 * @since 0.22.2
 */
public final class CompositeSwingDetector implements SwingDetector {

    /**
     * Composite policies for pivot agreement.
     *
     * @since 0.22.2
     */
    public enum Policy {
        AND, OR
    }

    private final List<SwingDetector> detectors;
    private final int indexTolerance;
    private final int requiredVotes;

    /**
     * Creates a composite detector.
     *
     * @param policy    agreement policy
     * @param detectors detectors to combine
     * @since 0.22.2
     */
    public CompositeSwingDetector(final Policy policy, final List<SwingDetector> detectors) {
        this(detectors, 0,
                Objects.requireNonNull(policy, "policy") == Policy.AND && detectors != null ? detectors.size() : 1);
    }

    /**
     * Creates a consensus detector that clusters nearby same-type pivots.
     *
     * @param detectors      detectors to combine
     * @param indexTolerance maximum bar distance within a pivot cluster
     * @param requiredVotes  distinct detectors required to retain a cluster
     * @since 0.22.9
     */
    public CompositeSwingDetector(final List<SwingDetector> detectors, final int indexTolerance,
            final int requiredVotes) {
        if (detectors == null || detectors.isEmpty()) {
            throw new IllegalArgumentException("detectors cannot be null or empty");
        }
        this.detectors = List.copyOf(detectors);
        if (indexTolerance < 0) {
            throw new IllegalArgumentException("indexTolerance must be non-negative");
        }
        if (requiredVotes < 1 || requiredVotes > detectors.size()) {
            throw new IllegalArgumentException("requiredVotes must be between 1 and the detector count");
        }
        this.indexTolerance = indexTolerance;
        this.requiredVotes = requiredVotes;
    }

    @Override
    public SwingDetectorResult detect(final BarSeries series, final int index, final ElliottDegree degree) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(degree, "degree");
        if (series.isEmpty()) {
            return new SwingDetectorResult(List.of(), List.of());
        }
        final List<List<SwingPivot>> pivotSets = new ArrayList<>(detectors.size());
        for (final SwingDetector detector : detectors) {
            final SwingDetectorResult result = detector.detect(series, index, degree);
            pivotSets.add(result.pivots());
        }
        final List<SwingPivot> merged = mergePivots(pivotSets);
        return SwingDetectorResult.fromPivots(merged, degree);
    }

    private List<SwingPivot> mergePivots(final List<List<SwingPivot>> pivotSets) {
        final List<PivotAccumulator> accumulators = new ArrayList<>();
        final List<TaggedPivot> candidates = new ArrayList<>();
        for (int detectorIndex = 0; detectorIndex < pivotSets.size(); detectorIndex++) {
            final List<SwingPivot> pivots = pivotSets.get(detectorIndex);
            if (pivots == null || pivots.isEmpty()) {
                continue;
            }
            for (final SwingPivot pivot : pivots) {
                if (pivot == null || Num.isNaNOrNull(pivot.price())) {
                    continue;
                }
                candidates.add(new TaggedPivot(detectorIndex, pivot));
            }
        }
        candidates.sort(Comparator.comparingInt((TaggedPivot candidate) -> candidate.pivot().index())
                .thenComparing(candidate -> candidate.pivot().type())
                .thenComparingInt(TaggedPivot::detectorIndex));

        for (final TaggedPivot candidate : candidates) {
            final SwingPivot pivot = candidate.pivot();
            PivotAccumulator accumulator = null;
            for (final PivotAccumulator cluster : accumulators) {
                if (cluster.type == pivot.type() && Math.abs(cluster.anchorIndex - pivot.index()) <= indexTolerance) {
                    accumulator = cluster;
                    break;
                }
            }
            if (accumulator == null) {
                accumulator = new PivotAccumulator(pivot);
                accumulators.add(accumulator);
            }
            accumulator.update(candidate.detectorIndex(), pivot);
        }

        if (accumulators.isEmpty()) {
            return List.of();
        }

        final List<SwingPivot> merged = new ArrayList<>();
        for (final PivotAccumulator accumulator : accumulators) {
            if (accumulator.voters.size() < requiredVotes || Num.isNaNOrNull(accumulator.representative.price())) {
                continue;
            }
            merged.add(accumulator.representative);
        }

        return SwingDetectorSupport.normalizePivots(merged);
    }

    /**
     * @return maximum bar distance used to cluster pivots
     * @since 0.22.9
     */
    public int getIndexTolerance() {
        return indexTolerance;
    }

    /**
     * @return detector quorum required for a cluster
     * @since 0.22.9
     */
    public int getRequiredVotes() {
        return requiredVotes;
    }

    private record TaggedPivot(int detectorIndex, SwingPivot pivot) {
    }

    private static final class PivotAccumulator {
        private final SwingPivotType type;
        private final int anchorIndex;
        private final Set<Integer> voters = new HashSet<>();
        private SwingPivot representative;

        private PivotAccumulator(final SwingPivot pivot) {
            this.type = pivot.type();
            this.anchorIndex = pivot.index();
            this.representative = pivot;
        }

        private void update(final int detectorIndex, final SwingPivot candidate) {
            voters.add(detectorIndex);
            if (candidate == null || Num.isNaNOrNull(candidate.price())) {
                return;
            }
            final Num price = representative.price();
            if (Num.isNaNOrNull(price)) {
                representative = candidate;
                return;
            }
            if (type == SwingPivotType.HIGH) {
                if (candidate.price().isGreaterThan(price)) {
                    representative = candidate;
                }
            } else {
                if (candidate.price().isLessThan(price)) {
                    representative = candidate;
                }
            }
        }
    }
}

/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.supportresistance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static org.ta4j.core.num.NaN.NaN;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Base class for price cluster support and resistance indicators.
 *
 * <p>
 * Concrete implementations provide the tie-breaking behaviour for equally
 * popular clusters.
 *
 * @since 0.22.2
 */
public abstract class AbstractPriceClusterIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> priceIndicator;
    @SuppressWarnings("unused")
    private final Indicator<Num> weightIndicatorSource;
    private final int lookbackCount;
    private final Num tolerance;
    private final transient Indicator<Num> weightIndicator;
    private final transient Map<Integer, Integer> clusterIndexCache = new ConcurrentHashMap<>();

    /**
     * Constructor.
     *
     * @param priceIndicator the price source to cluster
     * @param lookbackCount  the number of bars to evaluate (non-positive for the
     *                       full history)
     * @param tolerance      the absolute tolerance for bucket membership
     * @since 0.22.2
     */
    protected AbstractPriceClusterIndicator(Indicator<Num> priceIndicator, int lookbackCount, Num tolerance) {
        this(priceIndicator, null, lookbackCount, tolerance);
    }

    /**
     * Constructor supporting a custom weight indicator.
     *
     * @param priceIndicator  the price source to cluster
     * @param weightIndicator optional weight indicator (defaults to unit weights
     *                        when {@code null})
     * @param lookbackCount   the number of bars to evaluate (non-positive for the
     *                        full history)
     * @param tolerance       the absolute tolerance for bucket membership
     * @since 0.22.2
     */
    protected AbstractPriceClusterIndicator(Indicator<Num> priceIndicator, Indicator<Num> weightIndicator,
            int lookbackCount, Num tolerance) {
        this(priceIndicator, weightIndicator, weightIndicator, lookbackCount, tolerance);
    }

    /**
     * Constructor supporting separate source and derived weight indicators.
     *
     * @param priceIndicator        the price source to cluster
     * @param weightIndicator       indicator used for weighting calculations
     * @param weightIndicatorSource logical weight indicator to expose for
     *                              serialization
     * @param lookbackCount         the number of bars to evaluate (non-positive for
     *                              the full history)
     * @param tolerance             the absolute tolerance for bucket membership
     * @since 0.22.2
     */
    protected AbstractPriceClusterIndicator(Indicator<Num> priceIndicator, Indicator<Num> weightIndicator,
            Indicator<Num> weightIndicatorSource, int lookbackCount, Num tolerance) {
        super(priceIndicator);
        this.priceIndicator = Objects.requireNonNull(priceIndicator, "priceIndicator must not be null");
        this.lookbackCount = lookbackCount;
        this.tolerance = Objects.requireNonNull(tolerance, "tolerance must not be null");
        BarSeries series = Objects.requireNonNull(priceIndicator.getBarSeries(),
                "indicator must reference a bar series");
        if (isInvalid(tolerance) || tolerance.isLessThan(series.numFactory().zero())) {
            throw new IllegalArgumentException("tolerance must be greater than or equal to zero");
        }
        Indicator<Num> resolvedWeight = weightIndicator;
        if (resolvedWeight == null) {
            resolvedWeight = new ConstantIndicator<>(series, series.numFactory().one());
        }
        Indicator<Num> resolvedSource = weightIndicatorSource != null ? weightIndicatorSource : resolvedWeight;
        if (resolvedWeight.getBarSeries() != series) {
            throw new IllegalArgumentException("weightIndicator must share the same bar series as priceIndicator");
        }
        if (resolvedSource.getBarSeries() != series) {
            throw new IllegalArgumentException(
                    "weightIndicatorSource must share the same bar series as priceIndicator");
        }
        this.weightIndicator = resolvedWeight;
        this.weightIndicatorSource = resolvedSource;
    }

    /**
     * Constructor.
     *
     * @param series        the bar series backing the indicator
     * @param lookbackCount the number of bars to evaluate (non-positive for the
     *                      full history)
     * @param tolerance     the absolute tolerance for bucket membership
     * @since 0.22.2
     */
    protected AbstractPriceClusterIndicator(BarSeries series, int lookbackCount, Num tolerance) {
        this(new ClosePriceIndicator(series), lookbackCount, tolerance);
    }

    @Override
    protected Num calculate(int index) {
        BarSeries series = getBarSeries();
        if (series == null || index < series.getBeginIndex()) {
            clusterIndexCache.put(index, -1);
            return NaN;
        }
        if (index < series.getBeginIndex() + getCountOfUnstableBars()) {
            clusterIndexCache.put(index, -1);
            return NaN;
        }

        int startIndex = computeStartIndex(index, series);
        List<PriceCluster> clusters = buildClusters(startIndex, index);
        PriceCluster bestCluster = selectBestCluster(clusters);

        if (bestCluster == null) {
            clusterIndexCache.put(index, -1);
            return NaN;
        }

        clusterIndexCache.put(index, bestCluster.getLastIndex());
        return bestCluster.getRepresentativePrice();
    }

    /**
     * Returns the unstable warmup covering source indicator warmup plus the
     * configured look-back window.
     *
     * @return number of unstable bars
     * @since 0.22.2
     */
    @Override
    public int getCountOfUnstableBars() {
        int componentUnstableBars = Math.max(priceIndicator.getCountOfUnstableBars(),
                weightIndicator.getCountOfUnstableBars());
        return componentUnstableBars + Math.max(0, lookbackCount - 1);
    }

    /**
     * Returns the index of the most recent price that belongs to the dominant
     * cluster at the requested bar.
     *
     * @param index the bar index
     * @return the most recent cluster member index or {@code -1} when no cluster is
     *         available
     * @since 0.22.2
     */
    public int getClusterIndex(int index) {
        getValue(index);
        return clusterIndexCache.getOrDefault(index, -1);
    }

    private int computeStartIndex(int index, BarSeries series) {
        if (lookbackCount <= 0) {
            return series.getBeginIndex();
        }
        int desiredStart = index - lookbackCount + 1;
        return Math.max(series.getBeginIndex(), desiredStart);
    }

    private List<PriceCluster> buildClusters(int startIndex, int endIndex) {
        List<PriceCluster> clusters = new ArrayList<>();
        for (int i = startIndex; i <= endIndex; i++) {
            Num value = priceIndicator.getValue(i);
            if (isInvalid(value)) {
                continue;
            }
            Num weight = weightIndicator.getValue(i);
            if (isInvalid(weight) || !weight.isPositive()) {
                continue;
            }
            boolean assigned = false;
            for (PriceCluster cluster : clusters) {
                if (cluster.tryInclude(value, weight, i, tolerance)) {
                    assigned = true;
                    break;
                }
            }
            if (!assigned) {
                clusters.add(new PriceCluster(value, weight, i));
            }
        }
        return clusters;
    }

    private PriceCluster selectBestCluster(List<PriceCluster> clusters) {
        PriceCluster best = null;
        for (PriceCluster cluster : clusters) {
            if (best == null || cluster.getTotalWeight().isGreaterThan(best.getTotalWeight())) {
                best = cluster;
                continue;
            }
            if (cluster.getTotalWeight().isEqual(best.getTotalWeight())) {
                if (cluster.getCount() > best.getCount()) {
                    best = cluster;
                    continue;
                }
                if (cluster.getCount() == best.getCount()) {
                    Num clusterPrice = cluster.getRepresentativePrice();
                    Num bestPrice = best.getRepresentativePrice();
                    if (preferLowerPriceOnTie()) {
                        if (clusterPrice.isLessThan(bestPrice)) {
                            best = cluster;
                            continue;
                        }
                    } else {
                        if (clusterPrice.isGreaterThan(bestPrice)) {
                            best = cluster;
                            continue;
                        }
                    }
                    if (clusterPrice.isEqual(bestPrice) && cluster.getLastIndex() > best.getLastIndex()) {
                        best = cluster;
                    }
                }
            }
        }
        return best;
    }

    /**
     * Indicates whether tied clusters should favour the lower price.
     *
     * @return {@code true} to prefer lower prices, {@code false} to prefer higher
     *         prices
     * @since 0.22.2
     */
    protected abstract boolean preferLowerPriceOnTie();

    private static boolean isInvalid(Num value) {
        if (Num.isNaNOrNull(value)) {
            return true;
        }
        return Double.isNaN(value.doubleValue());
    }

    private static final class PriceCluster {
        private Num weightedSum;
        private Num totalWeight;
        private int count;
        private Num representativePrice;
        private int lastIndex;

        private PriceCluster(Num value, Num weight, int index) {
            this.weightedSum = value.multipliedBy(weight);
            this.totalWeight = weight;
            this.count = 1;
            this.representativePrice = value;
            this.lastIndex = index;
        }

        private boolean tryInclude(Num value, Num weight, int index, Num tolerance) {
            if (value.minus(representativePrice).abs().isLessThanOrEqual(tolerance)) {
                weightedSum = weightedSum.plus(value.multipliedBy(weight));
                totalWeight = totalWeight.plus(weight);
                count++;
                representativePrice = weightedSum.dividedBy(totalWeight);
                lastIndex = index;
                return true;
            }
            return false;
        }

        private Num getTotalWeight() {
            return totalWeight;
        }

        private int getCount() {
            return count;
        }

        private Num getRepresentativePrice() {
            return representativePrice;
        }

        private int getLastIndex() {
            return lastIndex;
        }
    }
}

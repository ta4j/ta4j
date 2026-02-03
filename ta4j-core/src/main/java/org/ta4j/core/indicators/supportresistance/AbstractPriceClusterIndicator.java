/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators.supportresistance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
 * @since 0.19
 */
public abstract class AbstractPriceClusterIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> priceIndicator;
    private final Indicator<Num> weightIndicator;
    private final int lookbackLength;
    private final Num tolerance;
    private final transient Map<Integer, Integer> clusterIndexCache = new HashMap<>();

    /**
     * Constructor.
     *
     * @param priceIndicator the price source to cluster
     * @param lookbackLength the number of bars to evaluate (non-positive for the
     *                       full history)
     * @param tolerance      the absolute tolerance for bucket membership
     * @since 0.19
     */
    protected AbstractPriceClusterIndicator(Indicator<Num> priceIndicator, int lookbackLength, Num tolerance) {
        this(priceIndicator, null, lookbackLength, tolerance);
    }

    /**
     * Constructor supporting a custom weight indicator.
     *
     * @param priceIndicator  the price source to cluster
     * @param weightIndicator optional weight indicator (defaults to unit weights
     *                        when {@code null})
     * @param lookbackLength  the number of bars to evaluate (non-positive for the
     *                        full history)
     * @param tolerance       the absolute tolerance for bucket membership
     * @since 0.19
     */
    protected AbstractPriceClusterIndicator(Indicator<Num> priceIndicator, Indicator<Num> weightIndicator,
            int lookbackLength, Num tolerance) {
        super(priceIndicator);
        this.priceIndicator = Objects.requireNonNull(priceIndicator, "priceIndicator must not be null");
        this.lookbackLength = lookbackLength;
        this.tolerance = Objects.requireNonNull(tolerance, "tolerance must not be null");
        BarSeries series = Objects.requireNonNull(priceIndicator.getBarSeries(), "indicator must reference a bar series");
        if (tolerance.isLessThan(series.numFactory().zero())) {
            throw new IllegalArgumentException("tolerance must be greater than or equal to zero");
        }
        if (weightIndicator != null && weightIndicator.getBarSeries() != series) {
            throw new IllegalArgumentException("weightIndicator must share the same bar series as priceIndicator");
        }
        this.weightIndicator = weightIndicator != null ? weightIndicator
                : new ConstantIndicator<>(series, series.numFactory().one());
    }

    /**
     * Constructor.
     *
     * @param series        the bar series backing the indicator
     * @param lookbackCount the number of bars to evaluate (non-positive for the
     *                      full history)
     * @param tolerance     the absolute tolerance for bucket membership
     * @since 0.19
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
     * Returns the index of the most recent price that belongs to the dominant
     * cluster at the requested bar.
     *
     * @param index the bar index
     * @return the most recent cluster member index or {@code -1} when no cluster is
     *         available
     * @since 0.19
     */
    public int getClusterIndex(int index) {
        getValue(index);
        return clusterIndexCache.getOrDefault(index, -1);
    }

    private int computeStartIndex(int index, BarSeries series) {
        if (lookbackLength <= 0) {
            return series.getBeginIndex();
        }
        int desiredStart = index - lookbackLength + 1;
        return Math.max(series.getBeginIndex(), desiredStart);
    }

    private List<PriceCluster> buildClusters(int startIndex, int endIndex) {
        List<PriceCluster> clusters = new ArrayList<>();
        for (int i = startIndex; i <= endIndex; i++) {
            Num value = priceIndicator.getValue(i);
            if (Num.isNaNOrNull(value)) {
                continue;
            }
            Num weight = weightIndicator.getValue(i);
            if (Num.isNaNOrNull(weight) || !weight.isPositive()) {
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
     * @since 0.19
     */
    protected abstract boolean preferLowerPriceOnTie();

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

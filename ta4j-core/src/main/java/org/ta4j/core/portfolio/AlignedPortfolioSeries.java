/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Chronologically aligned, asset-labeled collection of bar series.
 *
 * <p>
 * Alignment is deterministic and strict: the aligned timeline is the
 * intersection of bar end times present in every source series. Missing bars
 * are therefore excluded up front instead of being forward-filled implicitly.
 * Applications that need exchange-calendar joins or synthetic carry-forward
 * bars can prepare those inputs before creating this contract.
 * </p>
 *
 * @since 0.22.9
 */
public final class AlignedPortfolioSeries {

    private final List<PortfolioSeries> series;
    private final List<PortfolioAsset> assets;
    private final Map<PortfolioAsset, PortfolioSeries> seriesByAsset;
    private final Map<PortfolioAsset, BarSeries> sourceSeriesByAsset;
    private final List<Instant> endTimes;
    private final Map<PortfolioAsset, List<Integer>> sourceIndexesByAsset;
    private final NumFactory numFactory;

    private AlignedPortfolioSeries(List<PortfolioSeries> sourceSeries) {
        if (sourceSeries.size() < 2) {
            throw new IllegalArgumentException("portfolio series must contain at least two assets");
        }

        Map<PortfolioAsset, PortfolioSeries> orderedSeriesByAsset = new LinkedHashMap<>();
        for (PortfolioSeries portfolioSeries : sourceSeries) {
            Objects.requireNonNull(portfolioSeries, "series must not contain null entries");
            PortfolioSeries previous = orderedSeriesByAsset.putIfAbsent(portfolioSeries.asset(), portfolioSeries);
            if (previous != null) {
                throw new IllegalArgumentException("duplicate portfolio asset: " + portfolioSeries.asset());
            }
        }

        this.series = List.copyOf(orderedSeriesByAsset.values());
        this.assets = List.copyOf(orderedSeriesByAsset.keySet());
        this.seriesByAsset = Map.copyOf(orderedSeriesByAsset);
        this.sourceSeriesByAsset = sourceSeriesByAsset(this.series);
        this.numFactory = this.sourceSeriesByAsset.get(this.assets.getFirst()).numFactory();

        Alignment alignment = align(this.series, this.sourceSeriesByAsset);
        this.endTimes = alignment.endTimes();
        this.sourceIndexesByAsset = alignment.sourceIndexesByAsset();
    }

    /**
     * Aligns source series by common bar end time.
     *
     * @param series asset/series pairs in deterministic output order
     * @return aligned portfolio series
     * @since 0.22.9
     */
    public static AlignedPortfolioSeries of(List<PortfolioSeries> series) {
        Objects.requireNonNull(series, "series");
        return new AlignedPortfolioSeries(series);
    }

    /**
     * @return assets in deterministic portfolio order
     * @since 0.22.9
     */
    public List<PortfolioAsset> assets() {
        return assets;
    }

    /**
     * @return source series in deterministic portfolio order
     * @since 0.22.9
     */
    public List<PortfolioSeries> series() {
        return series;
    }

    /**
     * @return shared numeric factory used for portfolio-level accounting
     * @since 0.22.9
     */
    public NumFactory numFactory() {
        return numFactory;
    }

    /**
     * @return aligned bar count after strict end-time intersection
     * @since 0.22.9
     */
    public int getBarCount() {
        return endTimes.size();
    }

    /**
     * @return aligned end times in chronological order
     * @since 0.22.9
     */
    public List<Instant> endTimes() {
        return List.copyOf(endTimes);
    }

    /**
     * Returns the source series for an asset.
     *
     * @param asset asset id
     * @return source series
     * @since 0.22.9
     */
    public BarSeries getSeries(PortfolioAsset asset) {
        return PortfolioSeries.snapshotSeries(sourceSeriesByAsset.get(requireAsset(asset).asset()));
    }

    /**
     * Returns the source index for an aligned portfolio bar.
     *
     * @param asset asset id
     * @param index aligned portfolio index
     * @return source series index
     * @since 0.22.9
     */
    public int getSourceIndex(PortfolioAsset asset, int index) {
        requireIndex(index);
        return sourceIndexesByAsset.get(requireAsset(asset).asset()).get(index);
    }

    /**
     * Returns a source bar by aligned portfolio index.
     *
     * @param asset asset id
     * @param index aligned portfolio index
     * @return source bar
     * @since 0.22.9
     */
    public Bar getBar(PortfolioAsset asset, int index) {
        PortfolioSeries portfolioSeries = requireAsset(asset);
        return sourceSeriesByAsset.get(portfolioSeries.asset()).getBar(getSourceIndex(portfolioSeries.asset(), index));
    }

    /**
     * Returns the close price converted to the portfolio numeric factory.
     *
     * @param asset asset id
     * @param index aligned portfolio index
     * @return close price
     * @since 0.22.9
     */
    public Num getClosePrice(PortfolioAsset asset, int index) {
        return toPortfolioNum(getBar(asset, index).getClosePrice());
    }

    /**
     * Converts a numeric value into the portfolio numeric factory when needed.
     *
     * @param value source value
     * @return value compatible with {@link #numFactory()}
     * @since 0.22.9
     */
    public Num toPortfolioNum(Num value) {
        Objects.requireNonNull(value, "value");
        if (numFactory.produces(value)) {
            return value;
        }
        return numFactory.numOf(value.doubleValue());
    }

    private PortfolioSeries requireAsset(PortfolioAsset asset) {
        Objects.requireNonNull(asset, "asset");
        PortfolioSeries portfolioSeries = seriesByAsset.get(asset);
        if (portfolioSeries == null) {
            throw new IllegalArgumentException("asset is not in this portfolio series: " + asset);
        }
        return portfolioSeries;
    }

    private void requireIndex(int index) {
        if (index < 0 || index >= endTimes.size()) {
            throw new IndexOutOfBoundsException("index must be between 0 and " + (endTimes.size() - 1));
        }
    }

    private static Alignment align(List<PortfolioSeries> sourceSeries,
            Map<PortfolioAsset, BarSeries> sourceSeriesByAsset) {
        List<Map<Instant, Integer>> indexesByEndTime = new ArrayList<>(sourceSeries.size());
        TreeSet<Instant> commonEndTimes = null;

        for (PortfolioSeries portfolioSeries : sourceSeries) {
            Map<Instant, Integer> currentIndexes = indexesByEndTime(portfolioSeries.asset(),
                    sourceSeriesByAsset.get(portfolioSeries.asset()));
            indexesByEndTime.add(currentIndexes);
            if (commonEndTimes == null) {
                commonEndTimes = new TreeSet<>(currentIndexes.keySet());
            } else {
                commonEndTimes.retainAll(currentIndexes.keySet());
            }
        }

        if (commonEndTimes == null || commonEndTimes.isEmpty()) {
            throw new IllegalArgumentException("portfolio series do not share any common bar end times");
        }

        List<Instant> alignedEndTimes = List.copyOf(commonEndTimes);
        Map<PortfolioAsset, List<Integer>> sourceIndexesByAsset = new HashMap<>();
        for (int seriesIndex = 0; seriesIndex < sourceSeries.size(); seriesIndex++) {
            PortfolioAsset asset = sourceSeries.get(seriesIndex).asset();
            Map<Instant, Integer> currentIndexes = indexesByEndTime.get(seriesIndex);
            List<Integer> sourceIndexes = new ArrayList<>(alignedEndTimes.size());
            for (Instant endTime : alignedEndTimes) {
                sourceIndexes.add(currentIndexes.get(endTime));
            }
            sourceIndexesByAsset.put(asset, List.copyOf(sourceIndexes));
        }

        return new Alignment(alignedEndTimes, Map.copyOf(sourceIndexesByAsset));
    }

    private static Map<PortfolioAsset, BarSeries> sourceSeriesByAsset(List<PortfolioSeries> sourceSeries) {
        Map<PortfolioAsset, BarSeries> sourceSeriesByAsset = new LinkedHashMap<>();
        for (PortfolioSeries portfolioSeries : sourceSeries) {
            sourceSeriesByAsset.put(portfolioSeries.asset(), portfolioSeries.series());
        }
        return Map.copyOf(sourceSeriesByAsset);
    }

    private static Map<Instant, Integer> indexesByEndTime(PortfolioAsset asset, BarSeries source) {
        Map<Instant, Integer> indexesByEndTime = new HashMap<>();
        for (int index = source.getBeginIndex(); index <= source.getEndIndex(); index++) {
            Instant endTime = source.getBar(index).getEndTime();
            Integer previous = indexesByEndTime.putIfAbsent(endTime, index);
            if (previous != null) {
                throw new IllegalArgumentException("duplicate bar end time for asset " + asset + ": " + endTime);
            }
        }
        return indexesByEndTime;
    }

    private record Alignment(List<Instant> endTimes, Map<PortfolioAsset, List<Integer>> sourceIndexesByAsset) {
    }
}

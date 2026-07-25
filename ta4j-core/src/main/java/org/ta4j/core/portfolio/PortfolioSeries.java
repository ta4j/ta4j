/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Chronologically aligned collection of named {@link BarSeries} instances.
 *
 * <p>
 * Alignment is deterministic and strict: the portfolio timeline is the
 * intersection of bar end times present in every source series. Missing bars
 * are excluded instead of being forward-filled. Source series are snapshotted
 * once during construction so later source mutations cannot invalidate the
 * retained source-index mapping.
 * </p>
 *
 * <p>
 * At least two source series are required. The simple constructors use each
 * {@link BarSeries#getName()} as its asset name; use the map constructor when
 * explicit aliases are preferable.
 * </p>
 *
 * @since 0.23.1
 */
public final class PortfolioSeries {

    private final List<String> assets;
    private final Map<String, BarSeries> sourceSeriesByAsset;
    private final List<Instant> endTimes;
    private final Map<String, List<Integer>> sourceIndexesByAsset;
    private final NumFactory numFactory;

    /**
     * Creates a portfolio from bar series, using each series name as its asset
     * name.
     *
     * @param series source bar series in deterministic portfolio order
     * @since 0.23.1
     */
    public PortfolioSeries(BarSeries... series) {
        this(Arrays.asList(Objects.requireNonNull(series, "series")));
    }

    /**
     * Creates a portfolio from bar series, using each series name as its asset
     * name.
     *
     * @param series source bar series in deterministic portfolio order
     * @since 0.23.1
     */
    public PortfolioSeries(List<BarSeries> series) {
        this(seriesByName(series));
    }

    /**
     * Creates a portfolio from explicit asset-name and bar-series associations.
     *
     * <p>
     * Encounter order is retained for matrices, snapshots, reports, and normal
     * iteration.
     * </p>
     *
     * @param seriesByAsset source series keyed by non-blank asset name
     * @since 0.23.1
     */
    public PortfolioSeries(Map<String, BarSeries> seriesByAsset) {
        Objects.requireNonNull(seriesByAsset, "seriesByAsset");
        if (seriesByAsset.size() < 2) {
            throw new IllegalArgumentException("portfolio series must contain at least two assets");
        }

        Map<String, BarSeries> snapshots = new LinkedHashMap<>();
        for (Map.Entry<String, BarSeries> entry : seriesByAsset.entrySet()) {
            String asset = requireAssetName(entry.getKey());
            BarSeries source = Objects.requireNonNull(entry.getValue(), "seriesByAsset must not contain null series");
            if (source.isEmpty()) {
                throw new IllegalArgumentException("series must not be empty for asset " + asset);
            }
            BarSeries previous = snapshots.putIfAbsent(asset, snapshotSeries(source));
            if (previous != null) {
                throw new IllegalArgumentException("duplicate portfolio asset: " + asset);
            }
        }

        this.assets = List.copyOf(snapshots.keySet());
        this.sourceSeriesByAsset = Collections.unmodifiableMap(snapshots);
        this.numFactory = snapshots.get(assets.getFirst()).numFactory();

        Alignment alignment = align(assets, snapshots);
        this.endTimes = alignment.endTimes();
        this.sourceIndexesByAsset = alignment.sourceIndexesByAsset();
    }

    /**
     * @return asset names in deterministic portfolio order
     * @since 0.23.1
     */
    public List<String> getAssets() {
        return assets;
    }

    /**
     * @return shared numeric factory used for portfolio-level calculations
     * @since 0.23.1
     */
    public NumFactory numFactory() {
        return numFactory;
    }

    /**
     * @return first aligned portfolio index
     * @since 0.23.1
     */
    public int getBeginIndex() {
        return 0;
    }

    /**
     * @return final aligned portfolio index
     * @since 0.23.1
     */
    public int getEndIndex() {
        return endTimes.size() - 1;
    }

    /**
     * @return aligned bar count after strict end-time intersection
     * @since 0.23.1
     */
    public int getBarCount() {
        return endTimes.size();
    }

    /**
     * @return aligned end times in chronological order
     * @since 0.23.1
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "endTimes is copied once and is immutable")
    public List<Instant> getEndTimes() {
        return endTimes;
    }

    /**
     * Returns a defensive snapshot of an asset's source series.
     *
     * @param asset asset name
     * @return source series snapshot
     * @since 0.23.1
     */
    public BarSeries getBarSeries(String asset) {
        return snapshotSeries(sourceSeriesByAsset.get(requireAsset(asset)));
    }

    /**
     * Returns the original source index for an aligned portfolio bar.
     *
     * @param asset asset name
     * @param index aligned portfolio index
     * @return source series index
     * @since 0.23.1
     */
    public int getSourceIndex(String asset, int index) {
        requireIndex(index);
        return sourceIndexesByAsset.get(requireAsset(asset)).get(index);
    }

    /**
     * Returns a source bar by aligned portfolio index.
     *
     * @param asset asset name
     * @param index aligned portfolio index
     * @return source bar
     * @since 0.23.1
     */
    public Bar getBar(String asset, int index) {
        String requiredAsset = requireAsset(asset);
        return sourceSeriesByAsset.get(requiredAsset).getBar(getSourceIndex(requiredAsset, index));
    }

    /**
     * Returns the close price converted to the portfolio numeric factory.
     *
     * @param asset asset name
     * @param index aligned portfolio index
     * @return close price
     * @since 0.23.1
     */
    public Num getClosePrice(String asset, int index) {
        return toPortfolioNum(getBar(asset, index).getClosePrice());
    }

    Num toPortfolioNum(Num value) {
        Objects.requireNonNull(value, "value");
        if (!Num.isFinite(value)) {
            return NaN.NaN;
        }
        return numFactory.numOf(value.bigDecimalValue());
    }

    private String requireAsset(String asset) {
        String requiredAsset = requireAssetName(asset);
        if (!sourceSeriesByAsset.containsKey(requiredAsset)) {
            throw new IllegalArgumentException("asset is not in this portfolio series: " + requiredAsset);
        }
        return requiredAsset;
    }

    private void requireIndex(int index) {
        if (index < getBeginIndex() || index > getEndIndex()) {
            throw new IndexOutOfBoundsException("index must be between " + getBeginIndex() + " and " + getEndIndex());
        }
    }

    private static Map<String, BarSeries> seriesByName(List<BarSeries> series) {
        Objects.requireNonNull(series, "series");
        Map<String, BarSeries> seriesByAsset = new LinkedHashMap<>();
        for (BarSeries barSeries : series) {
            BarSeries source = Objects.requireNonNull(barSeries, "series must not contain null entries");
            String asset = requireAssetName(source.getName());
            BarSeries previous = seriesByAsset.putIfAbsent(asset, source);
            if (previous != null) {
                throw new IllegalArgumentException("duplicate portfolio asset: " + asset);
            }
        }
        return seriesByAsset;
    }

    private static String requireAssetName(String asset) {
        Objects.requireNonNull(asset, "asset");
        if (asset.isBlank()) {
            throw new IllegalArgumentException("asset must not be blank");
        }
        return asset;
    }

    private static Alignment align(List<String> assets, Map<String, BarSeries> sourceSeriesByAsset) {
        List<Map<Instant, Integer>> indexesByEndTime = new ArrayList<>(assets.size());
        TreeSet<Instant> commonEndTimes = null;

        for (String asset : assets) {
            Map<Instant, Integer> currentIndexes = indexesByEndTime(asset, sourceSeriesByAsset.get(asset));
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
        Map<String, List<Integer>> sourceIndexes = new LinkedHashMap<>();
        for (int seriesIndex = 0; seriesIndex < assets.size(); seriesIndex++) {
            String asset = assets.get(seriesIndex);
            Map<Instant, Integer> currentIndexes = indexesByEndTime.get(seriesIndex);
            List<Integer> alignedIndexes = new ArrayList<>(alignedEndTimes.size());
            for (Instant endTime : alignedEndTimes) {
                alignedIndexes.add(currentIndexes.get(endTime));
            }
            sourceIndexes.put(asset, List.copyOf(alignedIndexes));
        }

        return new Alignment(alignedEndTimes, Collections.unmodifiableMap(sourceIndexes));
    }

    private static Map<Instant, Integer> indexesByEndTime(String asset, BarSeries source) {
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

    private static BarSeries snapshotSeries(BarSeries source) {
        return new BaseBarSeriesBuilder().withName(source.getName())
                .withNumFactory(source.numFactory())
                .withBeginIndex(source.getBeginIndex())
                .withBars(source.getBarData())
                .withMaxBarCount(source.getMaximumBarCount())
                .build();
    }

    private record Alignment(List<Instant> endTimes, Map<String, List<Integer>> sourceIndexesByAsset) {
    }
}

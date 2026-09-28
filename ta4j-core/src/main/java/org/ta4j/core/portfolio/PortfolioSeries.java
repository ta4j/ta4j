/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Immutable, chronologically aligned collection of named {@link BarSeries}
 * instances: the input of a portfolio backtest.
 *
 * <p>
 * Alignment is deterministic and strict: the portfolio timeline is the
 * intersection of bar end times present in every source series. Missing bars
 * are excluded instead of being forward-filled, so callers that need
 * exchange-calendar joins or carry-forward bars should prepare those inputs
 * upstream. All prices must be quoted in one common currency and include any
 * split/dividend adjustments the experiment requires.
 * </p>
 *
 * <p>
 * The portfolio owns a detached copy of every source bar, taken once during
 * construction. Later mutations of the source series or of their bars (for
 * example {@link BarSeries#addPrice(Num)}) do not change the portfolio, and the
 * bars and series returned by {@link #getBar(String, int)} and
 * {@link #getBarSeries(String)} are fresh copies that callers may modify
 * freely. Repeated backtests over the same instance are therefore reproducible.
 * </p>
 *
 * <p>
 * A single series is a valid portfolio (for example a 60% equity / 40% cash
 * benchmark). The simple constructors use each {@link BarSeries#getName()} as
 * its asset name; use {@link #PortfolioSeries(Map)} for explicit aliases.
 * Portfolio-level accounting uses the {@link NumFactory} of the first series.
 * </p>
 *
 * @since 0.25.1
 */
public final class PortfolioSeries {

    private final List<String> assets;
    private final Map<String, Integer> assetPositions;
    private final BarSeries[] ownedSeries;
    private final List<Instant> endTimes;
    private final int[][] sourceIndexes;
    private final Num[][] closePrices;
    private final NumFactory numFactory;

    /**
     * Creates a portfolio from bar series, using each series name as its asset
     * name.
     *
     * @param series non-empty source bar series in portfolio order
     * @since 0.25.1
     */
    public PortfolioSeries(BarSeries... series) {
        this(Arrays.asList(Objects.requireNonNull(series, "series")));
    }

    /**
     * Creates a portfolio from bar series, using each series name as its asset
     * name.
     *
     * @param series non-empty source bar series in portfolio order
     * @since 0.25.1
     */
    public PortfolioSeries(List<BarSeries> series) {
        this(seriesByName(series));
    }

    /**
     * Creates a portfolio from explicit asset-name and bar-series associations.
     *
     * <p>
     * Encounter order is retained for snapshots, weights, and normal iteration, so
     * prefer an ordered map such as {@link LinkedHashMap}.
     * </p>
     *
     * @param seriesByAsset non-empty source series keyed by non-blank asset name
     * @since 0.25.1
     */
    public PortfolioSeries(Map<String, BarSeries> seriesByAsset) {
        Objects.requireNonNull(seriesByAsset, "seriesByAsset");
        if (seriesByAsset.isEmpty()) {
            throw new IllegalArgumentException("portfolio series must contain at least one asset");
        }

        List<String> assetNames = new ArrayList<>(seriesByAsset.size());
        Map<String, Integer> positions = new HashMap<>();
        BarSeries[] owned = new BarSeries[seriesByAsset.size()];
        for (Map.Entry<String, BarSeries> entry : seriesByAsset.entrySet()) {
            String asset = requireAssetName(entry.getKey());
            BarSeries source = Objects.requireNonNull(entry.getValue(), "series must not be null for asset " + asset);
            if (source.isEmpty()) {
                throw new IllegalArgumentException("series must not be empty for asset " + asset);
            }
            if (positions.putIfAbsent(asset, assetNames.size()) != null) {
                throw new IllegalArgumentException("duplicate portfolio asset: " + asset);
            }
            owned[assetNames.size()] = detachedCopy(source);
            assetNames.add(asset);
        }

        this.assets = List.copyOf(assetNames);
        this.assetPositions = Map.copyOf(positions);
        this.ownedSeries = owned;
        this.numFactory = owned[0].numFactory();

        List<Map<Instant, Integer>> indexesByEndTime = new ArrayList<>(owned.length);
        for (int position = 0; position < owned.length; position++) {
            indexesByEndTime.add(indexesByEndTime(assets.get(position), owned[position]));
        }
        TreeSet<Instant> commonEndTimes = new TreeSet<>(indexesByEndTime.getFirst().keySet());
        for (Map<Instant, Integer> currentIndexes : indexesByEndTime) {
            commonEndTimes.retainAll(currentIndexes.keySet());
        }
        if (commonEndTimes.isEmpty()) {
            throw new IllegalArgumentException("portfolio series do not share any common bar end times");
        }

        this.endTimes = List.copyOf(commonEndTimes);
        this.sourceIndexes = new int[owned.length][endTimes.size()];
        this.closePrices = new Num[owned.length][endTimes.size()];
        for (int position = 0; position < owned.length; position++) {
            Map<Instant, Integer> currentIndexes = indexesByEndTime.get(position);
            for (int index = 0; index < endTimes.size(); index++) {
                int sourceIndex = currentIndexes.get(endTimes.get(index));
                sourceIndexes[position][index] = sourceIndex;
                closePrices[position][index] = toPortfolioNum(owned[position].getBar(sourceIndex).getClosePrice());
            }
        }
    }

    /**
     * @return asset names in portfolio order
     * @since 0.25.1
     */
    public List<String> getAssets() {
        return assets;
    }

    /**
     * @return numeric factory used for portfolio-level accounting
     * @since 0.25.1
     */
    public NumFactory numFactory() {
        return numFactory;
    }

    /**
     * @return first aligned portfolio index (always {@code 0})
     * @since 0.25.1
     */
    public int getBeginIndex() {
        return 0;
    }

    /**
     * @return last aligned portfolio index
     * @since 0.25.1
     */
    public int getEndIndex() {
        return endTimes.size() - 1;
    }

    /**
     * @return aligned bar count after strict end-time intersection
     * @since 0.25.1
     */
    public int getBarCount() {
        return endTimes.size();
    }

    /**
     * @return aligned bar end times in chronological order
     * @since 0.25.1
     */
    public List<Instant> getEndTimes() {
        return endTimes;
    }

    /**
     * Returns a fresh, detached copy of an asset's full retained source history,
     * including bars that are not part of the aligned timeline. Source indexes are
     * preserved, so {@link #getSourceIndex(String, int)} addresses this copy and
     * the caller's original series alike.
     *
     * @param asset asset name
     * @return detached source series copy
     * @since 0.25.1
     */
    public BarSeries getBarSeries(String asset) {
        return detachedCopy(ownedSeries[position(asset)]);
    }

    /**
     * Returns the source-series index of an aligned portfolio bar. Use it to look
     * up indicators computed on the original per-asset series.
     *
     * @param asset asset name
     * @param index aligned portfolio index
     * @return source series index
     * @since 0.25.1
     */
    public int getSourceIndex(String asset, int index) {
        int position = position(asset);
        requireIndex(index);
        return sourceIndexes[position][index];
    }

    /**
     * Returns a fresh, detached copy of an asset's bar at an aligned index.
     *
     * @param asset asset name
     * @param index aligned portfolio index
     * @return detached source bar copy
     * @since 0.25.1
     */
    public Bar getBar(String asset, int index) {
        int position = position(asset);
        requireIndex(index);
        return detachedCopy(ownedSeries[position].getBar(sourceIndexes[position][index]));
    }

    /**
     * Returns an asset's close price at an aligned index, converted to
     * {@link #numFactory()}.
     *
     * @param asset asset name
     * @param index aligned portfolio index
     * @return close price
     * @since 0.25.1
     */
    public Num getClosePrice(String asset, int index) {
        int position = position(asset);
        requireIndex(index);
        return closePrices[position][index];
    }

    /**
     * @return compact description with assets, aligned bar count, and date range
     */
    @Override
    public String toString() {
        return "PortfolioSeries{assets=" + assets + ", bars=" + endTimes.size() + ", from=" + endTimes.getFirst()
                + ", to=" + endTimes.getLast() + '}';
    }

    Num closePrice(int assetPosition, int index) {
        return closePrices[assetPosition][index];
    }

    Duration timePeriod(int index) {
        return ownedSeries[0].getBar(sourceIndexes[0][index]).getTimePeriod();
    }

    Num toPortfolioNum(Num value) {
        Objects.requireNonNull(value, "value");
        if (!Num.isFinite(value)) {
            return NaN.NaN;
        }
        return numFactory.numOf(value.bigDecimalValue());
    }

    private int position(String asset) {
        Objects.requireNonNull(asset, "asset");
        Integer position = assetPositions.get(asset);
        if (position == null) {
            throw new IllegalArgumentException("asset is not in this portfolio series: " + asset);
        }
        return position;
    }

    private void requireIndex(int index) {
        if (index < 0 || index >= endTimes.size()) {
            throw new IndexOutOfBoundsException("index must be between 0 and " + getEndIndex() + " but was " + index);
        }
    }

    private static Map<String, BarSeries> seriesByName(List<BarSeries> series) {
        Objects.requireNonNull(series, "series");
        Map<String, BarSeries> seriesByAsset = new LinkedHashMap<>();
        for (BarSeries barSeries : series) {
            BarSeries source = Objects.requireNonNull(barSeries, "series must not contain null entries");
            String asset = requireAssetName(source.getName());
            if (seriesByAsset.putIfAbsent(asset, source) != null) {
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

    private static Map<Instant, Integer> indexesByEndTime(String asset, BarSeries source) {
        List<Bar> bars = source.getBarData();
        int beginIndex = source.getBeginIndex();
        Map<Instant, Integer> indexesByEndTime = new HashMap<>();
        // Offset iteration stays overflow-safe when the retained end index is
        // Integer.MAX_VALUE.
        for (int offset = 0; offset < bars.size(); offset++) {
            Instant endTime = bars.get(offset).getEndTime();
            if (indexesByEndTime.putIfAbsent(endTime, beginIndex + offset) != null) {
                throw new IllegalArgumentException("duplicate bar end time for asset " + asset + ": " + endTime);
            }
        }
        return indexesByEndTime;
    }

    private static BarSeries detachedCopy(BarSeries source) {
        List<Bar> sourceBars = source.getBarData();
        List<Bar> bars = new ArrayList<>(sourceBars.size());
        for (Bar bar : sourceBars) {
            bars.add(detachedCopy(bar));
        }
        return new BaseBarSeriesBuilder().withName(source.getName())
                .withNumFactory(source.numFactory())
                .withBeginIndex(source.getBeginIndex())
                .withBars(bars)
                .withMaxBarCount(source.getMaximumBarCount())
                .build();
    }

    private static Bar detachedCopy(Bar bar) {
        return new BaseBar(bar.getTimePeriod(), bar.getBeginTime(), bar.getEndTime(), bar.getOpenPrice(),
                bar.getHighPrice(), bar.getLowPrice(), bar.getClosePrice(), bar.getVolume(), bar.getAmount(),
                bar.getTrades());
    }
}

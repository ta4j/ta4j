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
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Immutable, chronologically aligned snapshot of named {@link BarSeries}
 * instances: the input of a portfolio backtest.
 *
 * <p>
 * The constructors align on the intersection of end times; {@link #union(Map)}
 * instead includes every end time. Neither mode synthesizes missing bars.
 * All prices must be quoted in one common currency and include any
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
 * Legacy intersection constructors permit other factories and convert close
 * prices to the portfolio factory. Union snapshots require matching factories.
 * </p>
 *
 * @since 0.25.1
 */
public final class PortfolioSeries implements PortfolioSeriesView {

    private final List<String> assets;
    private final Map<String, Integer> assetPositions;
    private final BarSeries[] ownedSeries;
    private final List<Instant> endTimes;
    private final int[][] sourceIndexes;
    private final Num[][] closePrices;
    private final NumFactory numFactory;
    private final boolean strictIntersection;

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
        this(seriesByAsset, true);
    }

    /**
     * Creates a detached portfolio snapshot whose timeline is the sorted union
     * of retained source bar end times. Sources may be empty; if all are empty
     * the timeline has end index {@code -1}. An asset has an exact bar only
     * where its end time matches the timeline, while its source index is an
     * as-of index even across gaps.
     *
     * <p>
     * Factories must match: decimal factories must have equal math contexts,
     * while other factories must be equal according to {@link Object#equals}.
     * Encounter order of the supplied map is preserved.
     * </p>
     *
     * @param seriesByAsset non-empty map of non-blank asset names to source series
     * @return an independent union snapshot
     * @throws IllegalArgumentException if the input is invalid, contains duplicate
     *                                  end times within a source, or factories do
     *                                  not match
     * @since 0.27.1
     */
    public static PortfolioSeries union(Map<String, BarSeries> seriesByAsset) {
        return new PortfolioSeries(seriesByAsset, false);
    }

    private PortfolioSeries(Map<String, BarSeries> seriesByAsset, boolean strictIntersection) {
        Objects.requireNonNull(seriesByAsset, "seriesByAsset");
        if (seriesByAsset.isEmpty()) {
            throw new IllegalArgumentException("portfolio series must contain at least one asset");
        }

        List<String> assetNames = new ArrayList<>(seriesByAsset.size());
        Map<String, Integer> positions = new HashMap<>();
        BarSeries[] owned = new BarSeries[seriesByAsset.size()];
        NumFactory firstFactory = null;
        for (Map.Entry<String, BarSeries> entry : seriesByAsset.entrySet()) {
            String asset = requireAssetName(entry.getKey());
            BarSeries source = Objects.requireNonNull(entry.getValue(), "series must not be null for asset " + asset);
            if (firstFactory == null) {
                firstFactory = Objects.requireNonNull(source.numFactory(), "source numFactory");
            } else if (!strictIntersection) {
                requireMatchingFactory(firstFactory, source.numFactory(), asset);
            }
            if (positions.putIfAbsent(asset, assetNames.size()) != null) {
                throw new IllegalArgumentException("duplicate portfolio asset: " + asset);
            }
            BarSeries copy = detachedCopy(source);
            if (strictIntersection && copy.isEmpty()) {
                throw new IllegalArgumentException("series must not be empty for asset " + asset);
            }
            owned[assetNames.size()] = copy;
            assetNames.add(asset);
        }

        this.assets = List.copyOf(assetNames);
        this.assetPositions = Map.copyOf(positions);
        this.ownedSeries = owned;
        this.numFactory = owned[0].numFactory();
        this.strictIntersection = strictIntersection;

        List<Map<Instant, Integer>> indexesByEndTime = new ArrayList<>(owned.length);
        for (int position = 0; position < owned.length; position++) {
            indexesByEndTime.add(indexesByEndTime(assets.get(position), owned[position]));
        }
        TreeSet<Instant> alignedEndTimes = new TreeSet<>(indexesByEndTime.getFirst().keySet());
        for (Map<Instant, Integer> currentIndexes : indexesByEndTime) {
            if (strictIntersection) {
                alignedEndTimes.retainAll(currentIndexes.keySet());
            } else {
                alignedEndTimes.addAll(currentIndexes.keySet());
            }
        }
        if (strictIntersection && alignedEndTimes.isEmpty()) {
            throw new IllegalArgumentException("portfolio series do not share any common bar end times");
        }

        this.endTimes = List.copyOf(alignedEndTimes);
        this.sourceIndexes = new int[owned.length][endTimes.size()];
        this.closePrices = new Num[owned.length][endTimes.size()];
        for (int position = 0; position < owned.length; position++) {
            Map<Instant, Integer> currentIndexes = indexesByEndTime.get(position);
            int latestSourceIndex = -1;
            Num latestClosePrice = null;
            for (int index = 0; index < endTimes.size(); index++) {
                Integer exactSourceIndex = currentIndexes.get(endTimes.get(index));
                if (exactSourceIndex != null) {
                    latestSourceIndex = exactSourceIndex;
                    latestClosePrice = toPortfolioNum(owned[position].getBar(latestSourceIndex).getClosePrice());
                }
                sourceIndexes[position][index] = latestSourceIndex;
                closePrices[position][index] = latestClosePrice;
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.27.1
     */
    @Override
    public List<String> assets() {
        return assets;
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
    @Override
    public NumFactory numFactory() {
        return numFactory;
    }

    /**
     * @return first aligned portfolio index (always {@code 0})
     * @since 0.25.1
     */
    @Override
    public int getBeginIndex() {
        return 0;
    }

    /**
     * @return last aligned portfolio index
     * @since 0.25.1
     */
    @Override
    public int getEndIndex() {
        return endTimes.size() - 1;
    }

    /**
     * @return aligned bar count
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
     * {@inheritDoc}
     *
     * @since 0.27.1
     */
    @Override
    public Instant getEndTime(int index) {
        requireIndex(index);
        return endTimes.get(index);
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.27.1
     */
    @Override
    public boolean hasBar(String asset, int index) {
        requireIndex(index);
        Integer position = assetPositions.get(Objects.requireNonNull(asset, "asset"));
        if (position == null) {
            return false;
        }
        int sourceIndex = sourceIndexes[position][index];
        return sourceIndex >= 0 && ownedSeries[position].getBar(sourceIndex).getEndTime().equals(endTimes.get(index));
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
    @Override
    public BarSeries getBarSeries(String asset) {
        return detachedCopy(ownedSeries[position(asset)]);
    }

    /**
     * Returns the retained source-series index as of an aligned portfolio time.
     * At a gap the index identifies the latest earlier bar, although
     * {@link #hasBar(String, int)} is false. For a union snapshot the result is
     * {@code -1} before the asset's first bar or for an unknown asset. Legacy
     * intersection snapshots continue to reject unknown assets.
     *
     * @param asset asset name
     * @param index aligned portfolio index
     * @return as-of source series index, or {@code -1} when unavailable
     * @since 0.25.1
     */
    @Override
    public int getSourceIndex(String asset, int index) {
        Integer position = assetPositions.get(Objects.requireNonNull(asset, "asset"));
        if (position == null && strictIntersection) {
            throw new IllegalArgumentException("asset is not in this portfolio series: " + asset);
        }
        requireIndex(index);
        if (position == null) {
            return -1;
        }
        return sourceIndexes[position][index];
    }

    /**
     * Returns a fresh, detached copy of an asset's as-of bar at an aligned index.
     * A gap resolves to the latest earlier bar; before the first bar this method
     * throws rather than creating a synthetic bar.
     *
     * @param asset asset name
     * @param index aligned portfolio index
     * @return detached source bar copy
     * @throws IllegalStateException if no as-of bar is available
     * @since 0.25.1
     */
    public Bar getBar(String asset, int index) {
        int position = position(asset);
        requireIndex(index);
        int sourceIndex = sourceIndexes[position][index];
        if (sourceIndex < 0) {
            throw new IllegalStateException("no bar is available for asset " + asset + " at index " + index);
        }
        return detachedCopy(ownedSeries[position].getBar(sourceIndex));
    }

    /**
     * Returns an asset's as-of close price at an aligned index, converted to
     * {@link #numFactory()}. At a gap the latest earlier close is returned.
     *
     * @param asset asset name
     * @param index aligned portfolio index
     * @return close price
     * @throws IllegalStateException if no as-of bar is available
     * @since 0.25.1
     */
    public Num getClosePrice(String asset, int index) {
        int position = position(asset);
        requireIndex(index);
        Num price = closePrices[position][index];
        if (price == null) {
            throw new IllegalStateException("no bar is available for asset " + asset + " at index " + index);
        }
        return price;
    }

    /**
     * @return compact description with assets, aligned bar count, and date range
     */
    @Override
    public String toString() {
        if (endTimes.isEmpty()) {
            return "PortfolioSeries{assets=" + assets + ", bars=0}";
        }
        return "PortfolioSeries{assets=" + assets + ", bars=" + endTimes.size() + ", from=" + endTimes.getFirst()
                + ", to=" + endTimes.getLast() + '}';
    }

    Num closePrice(int assetPosition, int index) {
        return closePrices[assetPosition][index];
    }

    Duration timePeriod(int index) {
        for (int position = 0; position < ownedSeries.length; position++) {
            int sourceIndex = sourceIndexes[position][index];
            if (sourceIndex >= 0) {
                Bar bar = ownedSeries[position].getBar(sourceIndex);
                if (bar.getEndTime().equals(endTimes.get(index))) {
                    return bar.getTimePeriod();
                }
            }
        }
        throw new IllegalStateException("no bar at portfolio index " + index);
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

    static void requireMatchingFactory(NumFactory expected, NumFactory actual, String asset) {
        Objects.requireNonNull(actual, "source numFactory");
        boolean matches = expected.equals(actual);
        if (!matches && expected instanceof DecimalNumFactory && actual instanceof DecimalNumFactory) {
            DecimalNum expectedOne = (DecimalNum) expected.one();
            DecimalNum actualOne = (DecimalNum) actual.one();
            matches = expectedOne.getMathContext().equals(actualOne.getMathContext());
        }
        if (!matches) {
            throw new IllegalArgumentException("numeric factory does not match portfolio for asset " + asset);
        }
    }

    private static BarSeries detachedCopy(BarSeries source) {
        return source.withReadLock(() -> {
            int beginIndex = source.getBeginIndex();
            int endIndex = source.getEndIndex();
            List<Bar> bars = new ArrayList<>();
            if (!source.isEmpty()) {
                for (long index = beginIndex; index <= endIndex; index++) {
                    bars.add(detachedCopy(source.getBar((int) index)));
                }
            }
            BaseBarSeriesBuilder builder = new BaseBarSeriesBuilder().withName(source.getName())
                    .withNumFactory(source.numFactory())
                    .withBars(bars)
                    .withMaxBarCount(source.getMaximumBarCount());
            if (!bars.isEmpty()) {
                builder.withBeginIndex(beginIndex);
            }
            return builder.build();
        });
    }

    private static Bar detachedCopy(Bar bar) {
        return new BaseBar(bar.getTimePeriod(), bar.getBeginTime(), bar.getEndTime(), bar.getOpenPrice(),
                bar.getHighPrice(), bar.getLowPrice(), bar.getClosePrice(), bar.getVolume(), bar.getAmount(),
                bar.getTrades());
    }
}

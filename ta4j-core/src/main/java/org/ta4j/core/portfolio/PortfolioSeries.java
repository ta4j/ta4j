/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;

/**
 * Associates one {@link PortfolioAsset} with its source {@link BarSeries}.
 *
 * <p>
 * A portfolio universe is built from these pairs and then aligned by bar end
 * time before execution. Empty series are rejected so alignment failures are
 * explicit at construction time.
 * </p>
 *
 * @param asset  asset id
 * @param series source bar series
 * @since 0.22.9
 */
public record PortfolioSeries(PortfolioAsset asset, BarSeries series) {

    /**
     * Creates an asset/series pair.
     *
     * @param asset  asset id
     * @param series non-empty source bar series
     * @since 0.22.9
     */
    public PortfolioSeries {
        Objects.requireNonNull(asset, "asset");
        Objects.requireNonNull(series, "series");
        if (series.isEmpty()) {
            throw new IllegalArgumentException("series must not be empty");
        }
        series = snapshotSeries(series);
    }

    /**
     * Factory method for readable portfolio setup code.
     *
     * @param assetId asset id
     * @param series  non-empty source bar series
     * @return asset/series pair
     * @since 0.22.9
     */
    public static PortfolioSeries of(String assetId, BarSeries series) {
        return new PortfolioSeries(PortfolioAsset.of(assetId), series);
    }

    /**
     * @return snapshot copy of the source bar series
     * @since 0.22.9
     */
    @Override
    public BarSeries series() {
        return snapshotSeries(series);
    }

    private static BarSeries snapshotSeries(BarSeries barSeries) {
        BarSeries source = Objects.requireNonNull(barSeries, "barSeries");
        return new BaseBarSeriesBuilder().withName(source.getName())
                .withNumFactory(source.numFactory())
                .withBars(source.getBarData())
                .withMaxBarCount(source.getMaximumBarCount())
                .build();
    }
}

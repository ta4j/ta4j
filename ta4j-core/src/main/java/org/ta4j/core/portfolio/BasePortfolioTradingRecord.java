/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;

/**
 * Portfolio record backed by independently supplied single-asset records.
 *
 * <p>
 * The factory can restore existing records, including open positions. Portfolio
 * position queries load every currently listed asset before counting, and keep
 * records for removed assets so their open positions are not lost. This class
 * is not thread-safe; callers must coordinate concurrent access.
 * </p>
 *
 * @since 0.27.1
 */
public final class BasePortfolioTradingRecord implements PortfolioTradingRecord {

    private final PortfolioSeriesView series;
    private final Function<String, TradingRecord> factory;
    private final Map<String, TradingRecord> records = new LinkedHashMap<>();

    /**
     * Creates a portfolio with empty buy-first records.
     *
     * @param series aligned portfolio view
     * @since 0.27.1
     */
    public BasePortfolioTradingRecord(PortfolioSeriesView series) {
        this(series, asset -> new BaseTradingRecord());
    }

    /**
     * Creates a portfolio with records supplied per asset.
     *
     * @param series aligned portfolio view
     * @param factory creates or restores a non-null trading record for an asset
     * @since 0.27.1
     */
    public BasePortfolioTradingRecord(PortfolioSeriesView series, Function<String, TradingRecord> factory) {
        this.series = Objects.requireNonNull(series, "series");
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    /** @since 0.27.1 */
    @Override
    public PortfolioSeriesView series() {
        return series;
    }

    /** @since 0.27.1 */
    @Override
    public TradingRecord record(String asset) {
        Objects.requireNonNull(asset, "asset");
        return records.computeIfAbsent(asset,
                key -> Objects.requireNonNull(factory.apply(key), "record factory returned null for " + key));
    }

    /** @since 0.27.1 */
    @Override
    public int openPositionCount() {
        loadListedAssets();
        int count = 0;
        for (TradingRecord record : records.values()) {
            if (!record.isClosed()) {
                count++;
            }
        }
        return count;
    }

    /** @since 0.27.1 */
    @Override
    public Map<String, Position> openPositions() {
        loadListedAssets();
        Map<String, Position> open = new LinkedHashMap<>();
        records.forEach((asset, record) -> {
            Position position = record.getCurrentPosition();
            if (position.isOpened()) {
                open.put(asset, position);
            }
        });
        return Collections.unmodifiableMap(open);
    }

    private void loadListedAssets() {
        for (String asset : series.assets()) {
            record(asset);
        }
    }
}

/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import java.util.Map;

import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

/**
 * Trading history for a multi-series strategy, with one trading record per
 * asset. Trade indexes are indexes of that asset's source series, not aligned
 * portfolio indexes.
 *
 * @since 0.27.1
 */
public interface PortfolioTradingRecord {

    /**
     * @return the aligned series used for portfolio decisions
     * @since 0.27.1
     */
    PortfolioSeriesView series();

    /**
     * Returns the asset's record, creating it through the record factory if
     * necessary. Changes made directly to this record are reflected in portfolio
     * position queries.
     *
     * @param asset asset identifier
     * @return the asset's trading record
     * @since 0.27.1
     */
    TradingRecord record(String asset);

    /**
     * @return number of assets with an open position
     * @since 0.27.1
     */
    int openPositionCount();

    /**
     * @return a snapshot mapping each asset with an open position to its position
     * @since 0.27.1
     */
    Map<String, Position> openPositions();

    /**
     * Opens a position in one asset's record.
     *
     * @param asset  asset identifier
     * @param index  source-series index
     * @param price  entry price
     * @param amount entry amount
     * @return whether the entry was placed
     * @since 0.27.1
     */
    default boolean enter(String asset, int index, Num price, Num amount) {
        return record(asset).enter(index, price, amount);
    }

    /**
     * Closes a position in one asset's record.
     *
     * @param asset  asset identifier
     * @param index  source-series index
     * @param price  exit price
     * @param amount exit amount
     * @return whether the exit was placed
     * @since 0.27.1
     */
    default boolean exit(String asset, int index, Num price, Num amount) {
        return record(asset).exit(index, price, amount);
    }
}

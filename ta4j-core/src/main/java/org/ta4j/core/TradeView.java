/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.io.Serializable;
import java.time.Instant;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.num.Num;

/**
 * Read-only view of a trade shared by modeled trades and live executions.
 *
 * <ul>
 * <li>the index (in the {@link BarSeries bar series}) on which the trade is
 * executed
 * <li>a {@link Trade.TradeType type} (BUY or SELL)
 * <li>a price per asset (optional)
 * <li>a trade amount (optional)
 * </ul>
 *
 * <p>
 * Metadata fields (timestamp, instrument, ids) are optional and may return
 * {@code null}. They loosely mirror the attributes in XChange's trade DTO so
 * adapters can preserve exchange-provided identifiers when available.
 * </p>
 *
 * <p>
 * Use {@link ModeledTrade} as the default modeled implementation and
 * {@link LiveTrade} for live fills.
 * </p>
 *
 * @since 0.22.2
 */
public interface TradeView extends Serializable {

    /**
     * @return the trade type (BUY or SELL)
     */
    Trade.TradeType getType();

    /**
     * @return the index the trade is executed
     */
    int getIndex();

    /**
     * @return the trade price per asset
     */
    Num getPricePerAsset();

    /**
     * @param barSeries the bar series
     * @return the trade price per asset, or, if {@code NaN}, the close price from
     *         the supplied {@link BarSeries}
     */
    default Num getPricePerAsset(BarSeries barSeries) {
        Num price = getPricePerAsset();
        if (price.isNaN()) {
            return barSeries.getBar(getIndex()).getClosePrice();
        }
        return price;
    }

    /**
     * @return the net price per asset for the trade (i.e.
     *         {@link #getPricePerAsset()} with trading costs)
     */
    Num getNetPrice();

    /**
     * @return the trade amount
     */
    Num getAmount();

    /**
     * @return the modeled costs of the trade as calculated by the configured
     *         {@link CostModel}
     */
    Num getCost();

    /**
     * @return the cost model for trade execution
     */
    CostModel getCostModel();

    /**
     * @return execution timestamp if available, otherwise {@code null}
     * @since 0.22.2
     */
    default Instant getTime() {
        return null;
    }

    /**
     * @return exchange-provided trade id if available, otherwise {@code null}
     * @since 0.22.2
     */
    default String getId() {
        return null;
    }

    /**
     * @return instrument identifier (symbol/pair) if available, otherwise
     *         {@code null}
     * @since 0.22.2
     */
    default String getInstrument() {
        return null;
    }

    /**
     * @return originating order id if available, otherwise {@code null}
     * @since 0.22.2
     */
    default String getOrderId() {
        return null;
    }

    /**
     * @return correlation id if available, otherwise {@code null}
     * @since 0.22.2
     */
    default String getCorrelationId() {
        return null;
    }

    /**
     * @return true if this is a BUY trade, false otherwise
     */
    default boolean isBuy() {
        return getType() == Trade.TradeType.BUY;
    }

    /**
     * @return true if this is a SELL trade, false otherwise
     */
    default boolean isSell() {
        return getType() == Trade.TradeType.SELL;
    }

    /**
     * @return the value of a trade (without transaction cost)
     */
    default Num getValue() {
        return getPricePerAsset().multipliedBy(getAmount());
    }
}

/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.io.Serial;
import java.time.Instant;
import java.util.List;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.num.Num;

/**
 * Legacy trade implementation retained for compatibility.
 *
 * <p>
 * Use {@link BaseTrade} for new code. This class now delegates to
 * {@link BaseTrade} and will be removed in a future release.
 * </p>
 *
 * @since 0.22.2
 * @deprecated since 0.22.4; use {@link BaseTrade}
 */
@Deprecated(since = "0.22.4", forRemoval = true)
public class SimulatedTrade extends BaseTrade {

    @Serial
    private static final long serialVersionUID = -905474949010114150L;

    /**
     * @param index  the index the trade is executed
     * @param series the bar series
     * @param type   the trade type
     */
    protected SimulatedTrade(int index, BarSeries series, Trade.TradeType type) {
        super(index, series, type);
    }

    /**
     * @param index  the index the trade is executed
     * @param series the bar series
     * @param type   the trade type
     * @param amount the trade amount
     */
    protected SimulatedTrade(int index, BarSeries series, Trade.TradeType type, Num amount) {
        super(index, series, type, amount);
    }

    /**
     * @param index                the index the trade is executed
     * @param series               the bar series
     * @param type                 the trade type
     * @param amount               the trade amount
     * @param transactionCostModel the cost model for trade execution
     */
    protected SimulatedTrade(int index, BarSeries series, Trade.TradeType type, Num amount,
            CostModel transactionCostModel) {
        super(index, series, type, amount, transactionCostModel);
    }

    /**
     * @param index         the index the trade is executed
     * @param type          the trade type
     * @param pricePerAsset the trade price per asset
     */
    protected SimulatedTrade(int index, Trade.TradeType type, Num pricePerAsset) {
        super(index, type, pricePerAsset);
    }

    /**
     * @param index         the index the trade is executed
     * @param type          the trade type
     * @param pricePerAsset the trade price per asset
     * @param amount        the trade amount
     */
    protected SimulatedTrade(int index, Trade.TradeType type, Num pricePerAsset, Num amount) {
        super(index, type, pricePerAsset, amount);
    }

    /**
     * @param index                the index the trade is executed
     * @param type                 the trade type
     * @param pricePerAsset        the trade price per asset
     * @param amount               the trade amount
     * @param transactionCostModel the cost model for trade execution
     */
    protected SimulatedTrade(int index, Trade.TradeType type, Num pricePerAsset, Num amount,
            CostModel transactionCostModel) {
        super(index, type, pricePerAsset, amount, transactionCostModel);
    }

    /**
     * @param type                 trade type
     * @param fills                execution fills (must not be empty)
     * @param transactionCostModel the cost model for trade execution
     * @since 0.22.4
     */
    protected SimulatedTrade(Trade.TradeType type, List<TradeFill> fills, CostModel transactionCostModel) {
        super(type, fills, transactionCostModel);
    }

    /**
     * @param index         trade index
     * @param time          execution timestamp
     * @param pricePerAsset execution price per asset
     * @param amount        execution amount
     * @param fee           recorded execution fee (nullable, defaults to zero)
     * @param side          execution side
     * @param orderId       optional order id
     * @param correlationId optional correlation id
     * @since 0.22.4
     */
    public SimulatedTrade(int index, Instant time, Num pricePerAsset, Num amount, Num fee, ExecutionSide side,
            String orderId, String correlationId) {
        super(index, time, pricePerAsset, amount, fee, side, orderId, correlationId);
    }

    @Override
    public SimulatedTrade withIndex(int index) {
        BaseTrade reindexed = super.withIndex(index);
        return new SimulatedTrade(reindexed.getType(), reindexed.getFills(), reindexed.getCostModel());
    }

    /**
     * @param index  the index the trade is executed
     * @param series the bar series
     * @return a BUY trade
     * @since 0.22.2
     */
    public static SimulatedTrade buyAt(int index, BarSeries series) {
        return new SimulatedTrade(index, series, Trade.TradeType.BUY);
    }

    /**
     * @param index                the index the trade is executed
     * @param price                the trade price per asset
     * @param amount               the trade amount
     * @param transactionCostModel the cost model for trade execution
     * @return a BUY trade
     * @since 0.22.2
     */
    public static SimulatedTrade buyAt(int index, Num price, Num amount, CostModel transactionCostModel) {
        return new SimulatedTrade(index, Trade.TradeType.BUY, price, amount, transactionCostModel);
    }

    /**
     * @param index  the index the trade is executed
     * @param price  the trade price per asset
     * @param amount the trade amount
     * @return a BUY trade
     * @since 0.22.2
     */
    public static SimulatedTrade buyAt(int index, Num price, Num amount) {
        return new SimulatedTrade(index, Trade.TradeType.BUY, price, amount);
    }

    /**
     * @param index  the index the trade is executed
     * @param series the bar series
     * @param amount the trade amount
     * @return a BUY trade
     * @since 0.22.2
     */
    public static SimulatedTrade buyAt(int index, BarSeries series, Num amount) {
        return new SimulatedTrade(index, series, Trade.TradeType.BUY, amount);
    }

    /**
     * @param index                the index the trade is executed
     * @param series               the bar series
     * @param amount               the trade amount
     * @param transactionCostModel the cost model for trade execution
     * @return a BUY trade
     * @since 0.22.2
     */
    public static SimulatedTrade buyAt(int index, BarSeries series, Num amount, CostModel transactionCostModel) {
        return new SimulatedTrade(index, series, Trade.TradeType.BUY, amount, transactionCostModel);
    }

    /**
     * @param index  the index the trade is executed
     * @param series the bar series
     * @return a SELL trade
     * @since 0.22.2
     */
    public static SimulatedTrade sellAt(int index, BarSeries series) {
        return new SimulatedTrade(index, series, Trade.TradeType.SELL);
    }

    /**
     * @param index  the index the trade is executed
     * @param price  the trade price per asset
     * @param amount the trade amount
     * @return a SELL trade
     * @since 0.22.2
     */
    public static SimulatedTrade sellAt(int index, Num price, Num amount) {
        return new SimulatedTrade(index, Trade.TradeType.SELL, price, amount);
    }

    /**
     * @param index                the index the trade is executed
     * @param price                the trade price per asset
     * @param amount               the trade amount
     * @param transactionCostModel the cost model for trade execution
     * @return a SELL trade
     * @since 0.22.2
     */
    public static SimulatedTrade sellAt(int index, Num price, Num amount, CostModel transactionCostModel) {
        return new SimulatedTrade(index, Trade.TradeType.SELL, price, amount, transactionCostModel);
    }

    /**
     * @param index  the index the trade is executed
     * @param series the bar series
     * @param amount the trade amount
     * @return a SELL trade
     * @since 0.22.2
     */
    public static SimulatedTrade sellAt(int index, BarSeries series, Num amount) {
        return new SimulatedTrade(index, series, Trade.TradeType.SELL, amount);
    }

    /**
     * @param index                the index the trade is executed
     * @param series               the bar series
     * @param amount               the trade amount
     * @param transactionCostModel the cost model for trade execution
     * @return a SELL trade
     * @since 0.22.2
     */
    public static SimulatedTrade sellAt(int index, BarSeries series, Num amount, CostModel transactionCostModel) {
        return new SimulatedTrade(index, series, Trade.TradeType.SELL, amount, transactionCostModel);
    }
}

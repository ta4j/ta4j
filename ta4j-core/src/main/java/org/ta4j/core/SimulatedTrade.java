/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.io.Serial;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.num.Num;
import org.ta4j.core.utils.DeprecationNotifier;

/**
 * Deprecated simulated-trade compatibility facade.
 *
 * <p>
 * Use {@link BaseTrade} or the static factory methods on {@link Trade} for new
 * code. This type remains available in the 0.22.x line so existing backtest
 * integrations can migrate without a hard patch-line break.
 * </p>
 *
 * @since 0.22.2
 */
@Deprecated(since = "0.22.4")
public class SimulatedTrade extends BaseTrade {

    @Serial
    private static final long serialVersionUID = -905474949010114150L;

    /**
     * Constructor.
     *
     * @param index  the index the trade is executed
     * @param series the bar series
     * @param type   the trade type
     * @since 0.22.2
     */
    protected SimulatedTrade(int index, BarSeries series, Trade.TradeType type) {
        this(index, series, type, series.numFactory().one());
    }

    /**
     * Constructor.
     *
     * @param index  the index the trade is executed
     * @param series the bar series
     * @param type   the trade type
     * @param amount the trade amount
     * @since 0.22.2
     */
    protected SimulatedTrade(int index, BarSeries series, Trade.TradeType type, Num amount) {
        this(index, series, type, amount, new ZeroCostModel());
    }

    /**
     * Constructor.
     *
     * @param index                the index the trade is executed
     * @param series               the bar series
     * @param type                 the trade type
     * @param amount               the trade amount
     * @param transactionCostModel the cost model for trade execution cost
     * @since 0.22.2
     */
    protected SimulatedTrade(int index, BarSeries series, Trade.TradeType type, Num amount,
            CostModel transactionCostModel) {
        super(index, series, type, amount, transactionCostModel);
        warnDeprecated();
    }

    /**
     * Constructor.
     *
     * @param index         the index the trade is executed
     * @param type          the trade type
     * @param pricePerAsset the trade price per asset
     * @since 0.22.2
     */
    protected SimulatedTrade(int index, Trade.TradeType type, Num pricePerAsset) {
        this(index, type, pricePerAsset, pricePerAsset.getNumFactory().one());
    }

    /**
     * Constructor.
     *
     * @param index         the index the trade is executed
     * @param type          the trade type
     * @param pricePerAsset the trade price per asset
     * @param amount        the trade amount
     * @since 0.22.2
     */
    protected SimulatedTrade(int index, Trade.TradeType type, Num pricePerAsset, Num amount) {
        this(index, type, pricePerAsset, amount, new ZeroCostModel());
    }

    /**
     * Constructor.
     *
     * @param index                the index the trade is executed
     * @param type                 the trade type
     * @param pricePerAsset        the trade price per asset
     * @param amount               the trade amount
     * @param transactionCostModel the cost model for trade execution
     * @since 0.22.2
     */
    protected SimulatedTrade(int index, Trade.TradeType type, Num pricePerAsset, Num amount,
            CostModel transactionCostModel) {
        super(index, type, pricePerAsset, amount, transactionCostModel);
        warnDeprecated();
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

    private static void warnDeprecated() {
        DeprecationNotifier.warnOnce(SimulatedTrade.class, "org.ta4j.core.BaseTrade");
    }
}

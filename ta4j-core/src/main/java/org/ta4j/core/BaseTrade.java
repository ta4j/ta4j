/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.Serial;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.RecordedTradeCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.num.Num;

/**
 * Unified {@link Trade} implementation for backtest and live flows.
 *
 * <ul>
 * <li>the index (in the {@link BarSeries bar series}) on which the trade is
 * executed
 * <li>a {@link Trade.TradeType type} (BUY or SELL)
 * <li>a pricePerAsset (optional)
 * <li>a trade amount (optional)
 * </ul>
 *
 * A {@link Position position} is a pair of complementary trades.
 *
 * <p>
 * Trades are backed by one or more {@link TradeFill} entries. Scalar
 * constructors create a single fill; aggregated constructors preserve full fill
 * progression.
 * </p>
 *
 * @since 0.22.4
 */
public class BaseTrade implements Trade {

    @Serial
    private static final long serialVersionUID = -905474949010114150L;

    private static final Gson GSON = new Gson();
    private static final CostModel DEFAULT_COST_MODEL = new ZeroCostModel();

    /** The type of the trade. */
    private final Trade.TradeType type;

    /** The index the trade was executed. */
    private final int index;

    /** The trade price per asset. */
    private Num pricePerAsset;

    /**
     * The net price per asset for the trade (i.e. {@link #pricePerAsset} with
     * {@link #cost}).
     */
    private Num netPrice;

    /** The trade amount. */
    private final Num amount;

    /** Execution fills for this trade (single fill for scalar trades). */
    private final List<TradeFill> fills;

    /** Execution timestamp. */
    private final Instant time;

    /** Execution side. */
    private final ExecutionSide side;

    /** Optional order id. */
    private final String orderId;

    /** Optional correlation id. */
    private final String correlationId;

    /**
     * The simulated execution cost for this trade, derived from the configured
     * {@link CostModel}.
     */
    private Num cost;

    /** The cost model for trade execution. */
    private transient CostModel costModel;

    /**
     * Constructor.
     *
     * @param index  the index the trade is executed
     * @param series the bar series
     * @param type   the trade type
     */
    protected BaseTrade(int index, BarSeries series, Trade.TradeType type) {
        this(index, series, type, series.numFactory().one());
    }

    /**
     * Constructor.
     *
     * @param index  the index the trade is executed
     * @param series the bar series
     * @param type   the trade type
     * @param amount the trade amount
     */
    protected BaseTrade(int index, BarSeries series, Trade.TradeType type, Num amount) {
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
     */
    protected BaseTrade(int index, BarSeries series, Trade.TradeType type, Num amount, CostModel transactionCostModel) {
        Num executionPrice = series.getBar(index).getClosePrice();
        Instant executionTime = series.getBar(index).getEndTime();
        this.type = type;
        this.index = index;
        this.amount = amount;
        this.time = executionTime;
        this.side = executionSide(type);
        this.orderId = null;
        this.correlationId = null;
        this.fills = List.of(new TradeFill(index, executionTime, executionPrice, amount, side));
        setPricesAndCost(executionPrice, amount, transactionCostModel, this.fills);
    }

    /**
     * Constructor.
     *
     * @param index         the index the trade is executed
     * @param type          the trade type
     * @param pricePerAsset the trade price per asset
     */
    protected BaseTrade(int index, Trade.TradeType type, Num pricePerAsset) {
        this(index, type, pricePerAsset, pricePerAsset.getNumFactory().one());
    }

    /**
     * Constructor.
     *
     * @param index         the index the trade is executed
     * @param type          the trade type
     * @param pricePerAsset the trade price per asset
     * @param amount        the trade amount
     */
    protected BaseTrade(int index, Trade.TradeType type, Num pricePerAsset, Num amount) {
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
     */
    protected BaseTrade(int index, Trade.TradeType type, Num pricePerAsset, Num amount,
            CostModel transactionCostModel) {
        this.type = type;
        this.index = index;
        this.amount = amount;
        this.time = null;
        this.side = executionSide(type);
        this.orderId = null;
        this.correlationId = null;
        this.fills = List.of(new TradeFill(index, null, pricePerAsset, amount, side));
        setPricesAndCost(pricePerAsset, amount, transactionCostModel, this.fills);
    }

    /**
     * Constructor for multi-fill trades.
     *
     * @param type                 trade type
     * @param fills                execution fills (must not be empty)
     * @param transactionCostModel the cost model for trade execution
     * @since 0.22.4
     */
    protected BaseTrade(Trade.TradeType type, List<TradeFill> fills, CostModel transactionCostModel) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(transactionCostModel, "transactionCostModel");
        FillSummary fillSummary = summarizeFills(type, fills);
        FillMetadata metadata = summarizeMetadata(type, fillSummary.firstFill());
        this.type = type;
        this.index = fillSummary.firstFill().index();
        this.amount = fillSummary.totalAmount();
        this.time = metadata.time();
        this.side = metadata.side();
        this.orderId = metadata.orderId();
        this.correlationId = metadata.correlationId();
        this.fills = fillSummary.fills();
        setPricesAndCost(fillSummary.weightedAveragePrice(), fillSummary.totalAmount(), transactionCostModel,
                this.fills);
    }

    /**
     * Constructor for live execution trades.
     *
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
    public BaseTrade(int index, Instant time, Num pricePerAsset, Num amount, Num fee, ExecutionSide side,
            String orderId, String correlationId) {
        if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0");
        }
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(pricePerAsset, "pricePerAsset");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(side, "side");
        Num normalizedFee = fee == null ? pricePerAsset.getNumFactory().zero() : fee;
        this.type = side.toTradeType();
        this.index = index;
        this.amount = amount;
        this.time = time;
        this.side = side;
        this.orderId = orderId;
        this.correlationId = correlationId;
        this.fills = List
                .of(new TradeFill(index, time, pricePerAsset, amount, normalizedFee, side, orderId, correlationId));
        setPricesAndCost(pricePerAsset, amount, RecordedTradeCostModel.INSTANCE, this.fills);
    }

    @Override
    public Trade.TradeType getType() {
        return type;
    }

    @Override
    public Num getCost() {
        return cost;
    }

    @Override
    public int getIndex() {
        return index;
    }

    @Override
    public Num getPricePerAsset() {
        return pricePerAsset;
    }

    @Override
    public Num getPricePerAsset(BarSeries barSeries) {
        if (pricePerAsset.isNaN()) {
            return barSeries.getBar(index).getClosePrice();
        }
        return pricePerAsset;
    }

    @Override
    public Num getNetPrice() {
        return netPrice;
    }

    @Override
    public Num getAmount() {
        return amount;
    }

    @Override
    public Instant getTime() {
        return time;
    }

    @Override
    public String getOrderId() {
        return orderId;
    }

    @Override
    public String getCorrelationId() {
        return correlationId;
    }

    @Override
    public List<TradeFill> getFills() {
        return exportedFills();
    }

    /**
     * @return execution timestamp
     * @since 0.22.4
     */
    public Instant time() {
        return time;
    }

    /**
     * @return execution price per asset
     * @since 0.22.4
     */
    public Num price() {
        return pricePerAsset;
    }

    /**
     * @return execution amount
     * @since 0.22.4
     */
    public Num amount() {
        return amount;
    }

    /**
     * @return recorded fee/cost
     * @since 0.22.4
     */
    public Num fee() {
        return cost;
    }

    /**
     * @return execution side
     * @since 0.22.4
     */
    public ExecutionSide side() {
        return side;
    }

    /**
     * @return optional order id
     * @since 0.22.4
     */
    public String orderId() {
        return orderId;
    }

    /**
     * @return optional correlation id
     * @since 0.22.4
     */
    public String correlationId() {
        return correlationId;
    }

    /**
     * @return the configured cost model, or a zero-cost model after deserialization
     *         when the transient model is unset
     *
     * @since 0.22.4
     */
    @Override
    public CostModel getCostModel() {
        return costModel == null ? DEFAULT_COST_MODEL : costModel;
    }

    /**
     * Sets the raw and net prices of the trade.
     *
     * @param pricePerAsset        the raw price of the asset
     * @param amount               the amount of assets ordered
     * @param transactionCostModel the cost model for trade execution
     */
    private void setPricesAndCost(Num pricePerAsset, Num amount, CostModel transactionCostModel,
            List<TradeFill> fills) {
        Objects.requireNonNull(transactionCostModel, "transactionCostModel");
        this.costModel = transactionCostModel;
        this.pricePerAsset = pricePerAsset;
        this.cost = resolveCost(transactionCostModel, this.pricePerAsset, amount, fills);

        if (amount.isZero()) {
            this.netPrice = this.pricePerAsset;
            return;
        }
        Num costPerAsset = cost.dividedBy(amount);
        // add transaction costs to the pricePerAsset at the trade
        if (type.equals(Trade.TradeType.BUY)) {
            this.netPrice = this.pricePerAsset.plus(costPerAsset);
        } else {
            this.netPrice = this.pricePerAsset.minus(costPerAsset);
        }
    }

    private static Num resolveCost(CostModel transactionCostModel, Num pricePerAsset, Num amount,
            List<TradeFill> fills) {
        if (transactionCostModel instanceof RecordedTradeCostModel) {
            return sumFillFees(pricePerAsset.getNumFactory().zero(), fills);
        }
        return transactionCostModel.calculate(pricePerAsset, amount);
    }

    private static Num sumFillFees(Num zero, List<TradeFill> fills) {
        Num totalFee = zero;
        for (TradeFill fill : fills) {
            totalFee = totalFee.plus(fill.fee());
        }
        return totalFee;
    }

    private static FillSummary summarizeFills(Trade.TradeType tradeType, List<TradeFill> fills) {
        Objects.requireNonNull(fills, "fills");
        if (fills.isEmpty()) {
            throw new IllegalArgumentException("fills must not be empty");
        }
        Num totalAmount = fills.getFirst().amount().getNumFactory().zero();
        Num weightedPrice = fills.getFirst().price().getNumFactory().zero();
        TradeFill earliestFill = fills.getFirst();
        ExecutionSide expectedSide = executionSide(tradeType);
        for (TradeFill fill : fills) {
            if (fill.side() != null && fill.side() != expectedSide) {
                throw new IllegalArgumentException("fill side must match trade type at index " + fill.index());
            }
            if (fill.price().isNaN()) {
                throw new IllegalArgumentException("fill price must be set");
            }
            if (fill.amount().isNaN() || fill.amount().isZero() || fill.amount().isNegative()) {
                throw new IllegalArgumentException("fill amount must be positive");
            }
            if (fill.index() < earliestFill.index()) {
                earliestFill = fill;
            }
            totalAmount = totalAmount.plus(fill.amount());
            weightedPrice = weightedPrice.plus(fill.price().multipliedBy(fill.amount()));
        }
        return new FillSummary(List.copyOf(fills), earliestFill, totalAmount, weightedPrice.dividedBy(totalAmount));
    }

    private static FillMetadata summarizeMetadata(Trade.TradeType tradeType, TradeFill firstFill) {
        Instant firstTime = firstFill.time();
        String firstOrderId = firstFill.orderId();
        String firstCorrelationId = firstFill.correlationId();
        ExecutionSide resolvedSide = firstFill.side() == null ? executionSide(tradeType) : firstFill.side();
        return new FillMetadata(firstTime, resolvedSide, firstOrderId, firstCorrelationId);
    }

    /**
     * Exports fills with trade-level modeled costs apportioned back onto the fills
     * when no explicit per-fill fees were recorded.
     */
    private List<TradeFill> exportedFills() {
        if (fills.isEmpty() || cost == null || cost.isNaN()) {
            return fills;
        }

        CostModel effectiveCostModel = getCostModel();
        if (effectiveCostModel instanceof RecordedTradeCostModel) {
            return fills;
        }

        Num zero = fills.getFirst().price().getNumFactory().zero();
        Num recordedFeeTotal = sumFillFees(zero, fills);
        Num residualFee = cost.minus(recordedFeeTotal);
        if (!residualFee.isPositive()) {
            return fills;
        }

        Num totalWeight = totalFillWeight(zero);
        if (totalWeight.isZero()) {
            return fills;
        }

        Num remainingFee = residualFee;
        List<TradeFill> adjustedFills = new ArrayList<>(fills.size());
        for (int i = 0; i < fills.size(); i++) {
            TradeFill fill = fills.get(i);
            Num feeShare = i == fills.size() - 1 ? remainingFee
                    : residualFee.multipliedBy(fillWeight(fill)).dividedBy(totalWeight);
            remainingFee = remainingFee.minus(feeShare);
            adjustedFills.add(copyWithFee(fill, fill.fee().plus(feeShare)));
        }
        return List.copyOf(adjustedFills);
    }

    private Num totalFillWeight(Num zero) {
        Num totalWeight = zero;
        for (TradeFill fill : fills) {
            totalWeight = totalWeight.plus(fillWeight(fill));
        }
        return totalWeight;
    }

    private Num fillWeight(TradeFill fill) {
        return fill.price().multipliedBy(fill.amount());
    }

    private TradeFill copyWithFee(TradeFill fill, Num fee) {
        return new TradeFill(fill.index(), fill.time(), fill.price(), fill.amount(), fee, fill.side(), fill.orderId(),
                fill.correlationId());
    }

    private static ExecutionSide executionSide(Trade.TradeType tradeType) {
        if (tradeType == Trade.TradeType.BUY) {
            return ExecutionSide.BUY;
        }
        return ExecutionSide.SELL;
    }

    @Override
    public boolean isBuy() {
        return type == Trade.TradeType.BUY;
    }

    @Override
    public boolean isSell() {
        return type == Trade.TradeType.SELL;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, index, time, pricePerAsset, amount, cost, side, orderId, correlationId);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof BaseTrade other)) {
            return false;
        }
        return Objects.equals(type, other.type) && Objects.equals(index, other.index)
                && Objects.equals(time, other.time) && Objects.equals(pricePerAsset, other.pricePerAsset)
                && Objects.equals(amount, other.amount) && Objects.equals(cost, other.cost)
                && Objects.equals(side, other.side) && Objects.equals(orderId, other.orderId)
                && Objects.equals(correlationId, other.correlationId);
    }

    @Override
    public String toString() {
        JsonObject json = new JsonObject();
        json.addProperty("type", type == null ? null : type.name());
        json.addProperty("index", index);
        json.addProperty("time", time == null ? null : time.toString());
        json.addProperty("pricePerAsset", pricePerAsset == null ? null : pricePerAsset.toString());
        json.addProperty("netPrice", netPrice == null ? null : netPrice.toString());
        json.addProperty("amount", amount == null ? null : amount.toString());
        json.addProperty("cost", cost == null ? null : cost.toString());
        json.addProperty("side", side == null ? null : side.name());
        json.addProperty("orderId", orderId);
        json.addProperty("correlationId", correlationId);
        return GSON.toJson(json);
    }

    /**
     * Returns a copy of this trade with a new index.
     *
     * @param index trade index
     * @return trade with the provided index
     * @since 0.22.4
     */
    public BaseTrade withIndex(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0");
        }
        int delta = index - this.index;
        List<TradeFill> indexedFills = fills.stream()
                .map(fill -> new TradeFill(fill.index() + delta, fill.time(), fill.price(), fill.amount(), fill.fee(),
                        fill.side(), fill.orderId(), fill.correlationId()))
                .toList();
        return new BaseTrade(type, indexedFills, resolveCopyCostModel(indexedFills));
    }

    private CostModel resolveCopyCostModel(List<TradeFill> indexedFills) {
        if (costModel != null) {
            return costModel;
        }
        Num fillFeeTotal = sumFillFees(cost.getNumFactory().zero(), indexedFills);
        if (cost.equals(fillFeeTotal)) {
            return RecordedTradeCostModel.INSTANCE;
        }
        return new PreservedTradeCostModel(cost);
    }

    private record FillSummary(List<TradeFill> fills, TradeFill firstFill, Num totalAmount, Num weightedAveragePrice) {
    }

    private record FillMetadata(Instant time, ExecutionSide side, String orderId, String correlationId) {
    }

    private static final class PreservedTradeCostModel implements CostModel {

        private final Num preservedCost;

        private PreservedTradeCostModel(Num preservedCost) {
            this.preservedCost = Objects.requireNonNull(preservedCost, "preservedCost");
        }

        @Override
        public Num calculate(Position position, int finalIndex) {
            return preservedCost;
        }

        @Override
        public Num calculate(Position position) {
            return preservedCost;
        }

        @Override
        public Num calculate(Num price, Num amount) {
            return preservedCost;
        }

        @Override
        public boolean equals(CostModel otherModel) {
            if (!(otherModel instanceof PreservedTradeCostModel other)) {
                return false;
            }
            return preservedCost.equals(other.preservedCost);
        }
    }

}

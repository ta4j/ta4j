/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.RecordedTradeCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import static org.ta4j.core.num.NaN.NaN;

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
@SuppressFBWarnings(value = "CT_CONSTRUCTOR_THROW", justification = "Every constructor validates trade data and futures-fill consistency before an instance is published, so invalid trades are rejected fail-fast instead of escaping partially initialized")
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

    /** The contract shared by every fill, or null for a spot trade. */
    private final FuturesContract futuresContract;

    /** Resolved fee components in the settlement currency; empty for spot. */
    private final List<TradeFee> feeComponents;

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
        this(seriesConfig(index, series, type));
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
        this(seriesConfig(index, series, type, amount));
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
        this(seriesConfig(index, series, type, amount, transactionCostModel));
    }

    /**
     * Constructor.
     *
     * @param index         the index the trade is executed
     * @param type          the trade type
     * @param pricePerAsset the trade price per asset
     */
    protected BaseTrade(int index, Trade.TradeType type, Num pricePerAsset) {
        this(priceConfig(index, type, pricePerAsset));
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
        this(priceConfig(index, type, pricePerAsset, amount));
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
        this(priceConfig(index, type, pricePerAsset, amount, transactionCostModel));
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
        this(fillConfig(type, fills, transactionCostModel));
    }

    private BaseTrade(TradeConfig config) {
        this.type = config.type();
        this.index = config.index();
        this.pricePerAsset = config.pricePerAsset();
        this.netPrice = config.netPrice();
        this.amount = config.amount();
        this.fills = config.fills();
        this.futuresContract = singleContract(config.fills());
        this.feeComponents = aggregateFeeComponents(futuresContract, config.fills());
        this.time = config.time();
        this.side = config.side();
        this.orderId = config.orderId();
        this.correlationId = config.correlationId();
        this.cost = config.cost();
        this.costModel = config.costModel();
    }

    @Serial
    private void readObject(ObjectInputStream inputStream) throws IOException, ClassNotFoundException {
        inputStream.defaultReadObject();
        if (costModel == null && futuresContract != null) {
            costModel = RecordedTradeCostModel.INSTANCE;
        }
    }

    private static TradeConfig seriesConfig(int index, BarSeries series, Trade.TradeType type) {
        return seriesConfig(index, series, type, series.numFactory().one());
    }

    private static TradeConfig seriesConfig(int index, BarSeries series, Trade.TradeType type, Num amount) {
        return seriesConfig(index, series, type, amount, new ZeroCostModel());
    }

    private static TradeConfig seriesConfig(int index, BarSeries series, Trade.TradeType type, Num amount,
            CostModel transactionCostModel) {
        Num executionPrice = series.getBar(index).getClosePrice();
        Instant executionTime = series.getBar(index).getEndTime();
        ExecutionSide side = executionSide(type);
        List<TradeFill> fills = List.of(new TradeFill(index, executionTime, executionPrice, amount, side));
        return config(type, index, executionTime, executionPrice, amount, side, null, null, fills,
                transactionCostModel);
    }

    private static TradeConfig priceConfig(int index, Trade.TradeType type, Num pricePerAsset) {
        return priceConfig(index, type, pricePerAsset, pricePerAsset.getNumFactory().one());
    }

    private static TradeConfig priceConfig(int index, Trade.TradeType type, Num pricePerAsset, Num amount) {
        return priceConfig(index, type, pricePerAsset, amount, new ZeroCostModel());
    }

    private static TradeConfig priceConfig(int index, Trade.TradeType type, Num pricePerAsset, Num amount,
            CostModel transactionCostModel) {
        ExecutionSide side = executionSide(type);
        List<TradeFill> fills = List.of(new TradeFill(index, null, pricePerAsset, amount, side));
        return config(type, index, null, pricePerAsset, amount, side, null, null, fills, transactionCostModel);
    }

    private static TradeConfig fillConfig(Trade.TradeType type, List<TradeFill> fills, CostModel transactionCostModel) {
        Objects.requireNonNull(type, "type");
        CostModel validatedCostModel = Objects.requireNonNull(transactionCostModel, "transactionCostModel");
        FillSummary fillSummary = summarizeFills(type, fills);
        FillMetadata metadata = summarizeMetadata(type, fillSummary.firstFill());
        return config(type, fillSummary.firstFill().index(), metadata.time(), fillSummary.weightedAveragePrice(),
                fillSummary.totalAmount(), metadata.side(), metadata.orderId(), metadata.correlationId(),
                fillSummary.fills(), validatedCostModel);
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
        this(liveConfig(index, time, pricePerAsset, amount, fee, side, orderId, correlationId));
    }

    private static TradeConfig liveConfig(int index, Instant time, Num pricePerAsset, Num amount, Num fee,
            ExecutionSide side, String orderId, String correlationId) {
        if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0");
        }
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(pricePerAsset, "pricePerAsset");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(side, "side");
        Num normalizedFee = fee == null ? pricePerAsset.getNumFactory().zero() : fee;
        List<TradeFill> fills = List
                .of(new TradeFill(index, time, pricePerAsset, amount, normalizedFee, side, orderId, correlationId));
        return config(side.toTradeType(), index, time, pricePerAsset, amount, side, orderId, correlationId, fills,
                RecordedTradeCostModel.INSTANCE);
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
     * @return the traded contract, or {@code null} for a spot trade
     * @since 0.25.1
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "FuturesContract is immutable, so returning the shared contract value cannot expose mutable trade state")
    @Override
    public FuturesContract getFuturesContract() {
        return futuresContract;
    }

    /**
     * @return resolved fee components of every fill, in the settlement currency;
     *         empty for a spot trade
     * @since 0.25.1
     */
    @Override
    public List<TradeFee> getFees() {
        return feeComponents == null ? List.of() : feeComponents;
    }

    /**
     * @return the contract symbol, or {@code null} for a spot trade
     * @since 0.25.1
     */
    @Override
    public String getInstrument() {
        return futuresContract == null ? null : futuresContract.symbol();
    }

    private static TradeConfig config(Trade.TradeType type, int index, Instant time, Num pricePerAsset, Num amount,
            ExecutionSide side, String orderId, String correlationId, List<TradeFill> fills,
            CostModel transactionCostModel) {
        CostModel validatedCostModel = Objects.requireNonNull(transactionCostModel, "transactionCostModel");
        List<TradeFill> normalizedFills = resolveFuturesFees(fills, validatedCostModel);
        PriceCost priceCost = priceCost(type, pricePerAsset, amount, normalizedFills, validatedCostModel);
        return new TradeConfig(type, index, pricePerAsset, priceCost.netPrice(), amount, normalizedFills, time, side,
                orderId, correlationId, priceCost.cost(), validatedCostModel);
    }

    /**
     * Resolves fee components for futures fills that carry no recorded fees.
     *
     * <p>
     * Explicitly recorded fees always win over a modeled schedule, so a fill
     * captured from an exchange is never re-priced.
     * </p>
     */
    private static List<TradeFill> resolveFuturesFees(List<TradeFill> fills, CostModel transactionCostModel) {
        FuturesContract contract = singleContract(fills);
        if (contract == null) {
            return List.copyOf(fills);
        }
        List<TradeFill> resolvedFills = new ArrayList<>(fills.size());
        for (TradeFill fill : fills) {
            resolvedFills.add(fill.hasRecordedFees() ? fill
                    : fill.toBuilder().fees(transactionCostModel.calculateFees(fill)).build());
        }
        return List.copyOf(resolvedFills);
    }

    /**
     * Resolves the single contract shared by {@code fills}.
     *
     * @param fills the execution fills of one trade
     * @return the shared contract, or {@code null} when every fill is a spot fill
     * @throws IllegalArgumentException when fills mix spot and futures fills, or
     *                                  mix contract specifications
     */
    private static FuturesContract singleContract(List<TradeFill> fills) {
        FuturesContract contract = null;
        boolean hasSpotFill = false;
        for (TradeFill fill : fills) {
            FuturesContract fillContract = fill.futuresContract();
            if (fillContract == null) {
                hasSpotFill = true;
            } else if (contract == null) {
                contract = fillContract;
            } else if (!contract.equals(fillContract)) {
                throw new IllegalArgumentException(
                        "fills must share one contract, found " + contract + " and " + fillContract);
            }
        }
        if (contract != null && hasSpotFill) {
            throw new IllegalArgumentException("fills must not mix spot and futures fills");
        }
        return contract;
    }

    private static List<TradeFee> aggregateFeeComponents(FuturesContract contract, List<TradeFill> fills) {
        if (contract == null) {
            return List.of();
        }
        List<TradeFee> components = new ArrayList<>();
        for (TradeFill fill : fills) {
            components.addAll(fill.fees());
        }
        return List.copyOf(components);
    }

    private static PriceCost priceCost(Trade.TradeType type, Num pricePerAsset, Num amount, List<TradeFill> fills,
            CostModel transactionCostModel) {
        FuturesContract contract = singleContract(fills);
        Num cost = resolveCost(transactionCostModel, pricePerAsset, amount, fills, contract);

        if (contract != null) {
            return new PriceCost(cost, futuresNetPrice(contract, type, pricePerAsset, amount, cost));
        }

        final Num netPrice;
        if (amount.isZero()) {
            netPrice = pricePerAsset;
        } else {
            Num costPerAsset = cost.dividedBy(amount);
            // add transaction costs to the pricePerAsset at the trade
            if (type.equals(Trade.TradeType.BUY)) {
                netPrice = pricePerAsset.plus(costPerAsset);
            } else {
                netPrice = pricePerAsset.minus(costPerAsset);
            }
        }
        return new PriceCost(cost, netPrice);
    }

    /**
     * Derives the effective entry/exit price that a futures fee is charged against.
     *
     * <p>
     * Settlement is expressed in the settlement currency, so the fee per contract
     * is {@code cost / (amount * contractSize)} in that currency. A linear contract
     * is quoted in it directly; an inverse contract is quoted as the base currency
     * price, so the charge is applied to the reciprocal quote and inverted back.
     * The result is the quote price whose settlement notional equals
     * {@code trade value + cost}. Costs too large for the contract to settle return
     * {@link org.ta4j.core.num.NaN#NaN}.
     * </p>
     */
    private static Num futuresNetPrice(FuturesContract contract, Trade.TradeType type, Num pricePerAsset, Num amount,
            Num cost) {
        if (amount.isZero()) {
            return pricePerAsset;
        }
        Num feePerContract = cost
                .dividedBy(amount.multipliedBy(amount.getNumFactory().numOf(contract.contractSize().getDelegate())));
        NumFactory numFactory = pricePerAsset.getNumFactory();
        if (contract.settlementType() == FuturesContract.SettlementType.LINEAR) {
            Num adjusted = type == Trade.TradeType.BUY ? pricePerAsset.plus(feePerContract)
                    : pricePerAsset.minus(feePerContract);
            return isUsableQuote(adjusted) ? adjusted : NaN;
        }
        Num inverseQuote = numFactory.one().dividedBy(pricePerAsset);
        Num adjustedInverse = type == Trade.TradeType.BUY ? inverseQuote.minus(feePerContract)
                : inverseQuote.plus(feePerContract);
        if (!isUsableQuote(adjustedInverse)) {
            return NaN;
        }
        Num adjusted = numFactory.one().dividedBy(adjustedInverse);
        return isUsableQuote(adjusted) ? adjusted : NaN;
    }

    private static boolean isUsableQuote(Num quote) {
        return Num.isFinite(quote) && quote.isPositive();
    }

    private static Num resolveCost(CostModel transactionCostModel, Num pricePerAsset, Num amount, List<TradeFill> fills,
            FuturesContract contract) {
        if (contract != null || transactionCostModel instanceof RecordedTradeCostModel) {
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
        Num quoteWeightedPrice = fills.getFirst().price().getNumFactory().zero();
        Num quotePriceSum = fills.getFirst().price().getNumFactory().zero();
        FuturesContract contract = singleContract(fills);
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
            quoteWeightedPrice = quoteWeightedPrice.plus(fill.price().multipliedBy(fill.amount()));
            quotePriceSum = quotePriceSum.plus(fill.amount().dividedBy(fill.price()));
        }
        Num aggregatedPrice = contract != null && contract.settlementType() == FuturesContract.SettlementType.INVERSE
                ? totalAmount.dividedBy(quotePriceSum)
                : quoteWeightedPrice.dividedBy(totalAmount);
        return new FillSummary(List.copyOf(fills), earliestFill, totalAmount, aggregatedPrice);
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

        if (futuresContract != null) {
            // Native fee components are already the authoritative cost record; they
            // must never be redistributed through the scalar spot path.
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
        return fill.toBuilder().fee(fee).build();
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
        return Objects.hash(type, index, time, pricePerAsset, amount, cost, side, orderId, correlationId,
                futuresContract, feeComponents);
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
                && Objects.equals(correlationId, other.correlationId)
                && Objects.equals(futuresContract, other.futuresContract)
                && Objects.equals(feeComponents, other.feeComponents);
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
        if (futuresContract != null) {
            json.addProperty("instrument", futuresContract.symbol());
            json.addProperty("futuresContract", futuresContract.toString());
            json.addProperty("feeComponents", GSON.toJson(feeComponents));
        }
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
                .map(fill -> fill.toBuilder().index(fill.index() + delta).build())
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

    private record PriceCost(Num cost, Num netPrice) {
    }

    private record TradeConfig(Trade.TradeType type, int index, Num pricePerAsset, Num netPrice, Num amount,
            List<TradeFill> fills, Instant time, ExecutionSide side, String orderId, String correlationId, Num cost,
            CostModel costModel) {
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

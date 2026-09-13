/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.ta4j.core.num.NaN.NaN;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * A {@code Position} models either a closed entry/exit pair or an open position
 * snapshot with only an entry trade.
 *
 * <p>
 * The exit trade has the complement type of the entry trade, i.e.:
 * <ul>
 * <li>entry == BUY --> exit == SELL
 * <li>entry == SELL --> exit == BUY
 * </ul>
 *
 * <p>
 * Open-position inspection APIs on {@link TradingRecord} also use this type, so
 * callers can query per-lot and net exposure through one consistent contract.
 * </p>
 */
@SuppressFBWarnings(value = "CT_CONSTRUCTOR_THROW", justification = "Every constructor validates trade data and futures contract consistency before an instance is published, so invalid positions are rejected fail-fast instead of escaping partially initialized")
public class Position implements Serializable {

    @Serial
    private static final long serialVersionUID = -5484709075767220358L;

    /** The entry trade */
    private Trade entry;

    /** The exit trade */
    private Trade exit;

    /** The type of the entry trade */
    private final TradeType startingType;

    /** The cost model for transactions of the asset */
    private final transient CostModel transactionCostModel;

    /** The cost model for holding the asset */
    private final transient CostModel holdingCostModel;

    /** The futures contract of the position, or null for spot positions */
    private FuturesContract futuresContract;

    /** Cash flows allocated to the position, empty for spot positions */
    private final List<FuturesCashFlow> cashFlows;

    /** Constructor with {@link #startingType} = BUY. */
    public Position() {
        this(validateDefaultStartingPosition());
    }

    /**
     * Constructor.
     *
     * @param startingType the starting {@link TradeType trade type} of the position
     *                     (i.e. type of the entry trade)
     */
    public Position(TradeType startingType) {
        this(validateDefaultStartingPosition(startingType));
    }

    /**
     * Constructor.
     *
     * @param startingType         the starting {@link TradeType trade type} of the
     *                             position (i.e. type of the entry trade)
     * @param transactionCostModel the cost model for transactions of the asset
     * @param holdingCostModel     the cost model for holding asset (e.g. borrowing)
     */
    public Position(TradeType startingType, CostModel transactionCostModel, CostModel holdingCostModel) {
        this(validateStartingPosition(startingType, transactionCostModel, holdingCostModel));
    }

    /**
     * Constructor.
     *
     * @param entry the entry {@link Trade trade}
     * @param exit  the exit {@link Trade trade}
     */
    public Position(Trade entry, Trade exit) {
        this(validateClosedPositionWithDefaults(entry, exit), List.of());
    }

    /**
     * Constructor.
     *
     * @param entry                the entry {@link Trade trade}
     * @param exit                 the exit {@link Trade trade}
     * @param transactionCostModel the cost model for transactions of the asset
     * @param holdingCostModel     the cost model for holding asset (e.g. borrowing)
     */
    public Position(Trade entry, Trade exit, CostModel transactionCostModel, CostModel holdingCostModel) {
        this(validateClosedPosition(entry, exit, transactionCostModel, holdingCostModel), List.of());
    }

    /**
     * Constructor for an open position.
     *
     * @param entry                the entry {@link Trade trade}
     * @param transactionCostModel the cost model for transactions of the asset
     * @param holdingCostModel     the cost model for holding asset (e.g. borrowing)
     * @since 0.22.2
     */
    public Position(Trade entry, CostModel transactionCostModel, CostModel holdingCostModel) {
        this(validateOpenPosition(entry, transactionCostModel, holdingCostModel), List.of());
    }

    /**
     * @return the entry {@link Trade trade} of the position
     */
    public Trade getEntry() {
        return entry;
    }

    /**
     * @return the exit {@link Trade trade} of the position
     */
    public Trade getExit() {
        return exit;
    }

    /**
     * Returns the futures contract of this position.
     *
     * <p>
     * The contract is taken from the entry trade and the exit trade must reference
     * the same contract, so a position never spans contracts or mixes spot and
     * futures exposure.
     * </p>
     *
     * @return the traded contract, or {@code null} for a spot position
     * @since 0.25.1
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "FuturesContract is a final value type whose instances are shared by reference; copying the immutable contract per accessor would allocate on every read")
    public FuturesContract getFuturesContract() {
        return futuresContract;
    }

    /**
     * Returns the cash flows allocated to this position.
     *
     * <p>
     * Funding and variation margin events are attributed to the position they
     * belong to; the list is empty for spot positions and for futures positions
     * without recorded events.
     * </p>
     *
     * @return an immutable list of allocated cash flows, never {@code null}
     * @since 0.25.1
     */
    public List<FuturesCashFlow> getCashFlows() {
        return cashFlows == null ? List.of() : cashFlows;
    }

    /**
     * Returns the entry-side direction of this position.
     *
     * @return the entry side, or {@code null} when the position has no entry yet
     * @since 0.22.4
     */
    public ExecutionSide side() {
        if (entry == null) {
            return null;
        }
        return entry.isBuy() ? ExecutionSide.BUY : ExecutionSide.SELL;
    }

    /**
     * Returns the entry amount of this position.
     *
     * <p>
     * For aggregated open positions this is the net open amount.
     * </p>
     *
     * @return the entry amount, or {@code null} when the position has no entry yet
     * @since 0.22.4
     */
    public Num amount() {
        return entry == null ? null : entry.getAmount();
    }

    /**
     * Returns the average entry price of this position.
     *
     * <p>
     * For standard positions this is the entry trade price. For aggregated open
     * positions this is the weighted average entry price of the net exposure.
     * </p>
     *
     * @return the average entry price, or {@code null} when the position has no
     *         entry yet
     * @since 0.22.4
     */
    public Num averageEntryPrice() {
        return entry == null ? null : entry.getPricePerAsset();
    }

    /**
     * Returns the total entry cost of this position.
     *
     * @return the total entry cost, or {@code null} when the position has no entry
     *         yet
     * @since 0.22.4
     */
    public Num totalEntryCost() {
        return entry == null ? null : entry.getValue();
    }

    /**
     * Returns the entry fees currently carried by this position.
     *
     * <p>
     * For aggregated open positions this reflects the summed remaining entry fees.
     * </p>
     *
     * @return the entry fees, or {@code null} when the position has no entry yet
     * @since 0.22.4
     */
    public Num totalFees() {
        return entry == null ? null : entry.getCost();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Position p) {
            return (entry == null ? p.getEntry() == null : entry.equals(p.getEntry()))
                    && (exit == null ? p.getExit() == null : exit.equals(p.getExit()))
                    && getCashFlows().equals(p.getCashFlows());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(entry, exit, getCashFlows());
    }

    /**
     * Operates the position at the index-th position.
     *
     * @param index the bar index
     * @return the trade
     * @see #operate(int, Num, Num)
     */
    public Trade operate(int index) {
        return operate(index, NaN, NaN);
    }

    /**
     * Operates the position at the index-th position.
     *
     * @param index  the bar index
     * @param price  the price
     * @param amount the amount
     * @return the trade
     * @throws IllegalStateException if {@link #isOpened()} and index {@literal <}
     *                               entry.index
     */
    public Trade operate(int index, Num price, Num amount) {
        CostModel effectiveTransactionCostModel = getTransactionCostModel();
        Trade trade = null;
        if (isNew()) {
            trade = operate(new BaseTrade(index, startingType, price, amount, effectiveTransactionCostModel));
        } else if (isOpened()) {
            if (index < entry.getIndex()) {
                throw new IllegalStateException("The index i is less than the entryTrade index");
            }
            trade = operate(
                    new BaseTrade(index, startingType.complementType(), price, amount, effectiveTransactionCostModel));
        }
        return trade;
    }

    /**
     * Operates the position with a pre-built trade.
     *
     * @param trade the trade to apply
     * @return the trade
     * @since 0.22.4
     */
    public Trade operate(Trade trade) {
        Objects.requireNonNull(trade, "trade");
        CostModel effectiveTransactionCostModel = getTransactionCostModel();
        if (!trade.getCostModel().equals(effectiveTransactionCostModel)) {
            throw new IllegalArgumentException("Trades and the position must incorporate the same trading cost model");
        }
        if (isNew()) {
            if (trade.getType() != startingType) {
                throw new IllegalArgumentException("The first trade type must match the starting type");
            }
            futuresContract = trade.getFuturesContract();
            entry = trade;
            return trade;
        }
        if (isOpened()) {
            validateContract(trade.getFuturesContract());
            if (trade.getType() != startingType.complementType()) {
                throw new IllegalArgumentException("The exit trade type must complement the entry trade type");
            }
            if (trade.getIndex() < entry.getIndex()) {
                throw new IllegalStateException("The index i is less than the entryTrade index");
            }
            exit = trade;
            return trade;
        }
        return null;
    }

    /**
     * @return true if the position is closed, false otherwise
     */
    public boolean isClosed() {
        return (entry != null) && (exit != null);
    }

    /**
     * @return true if the position is opened, false otherwise
     */
    public boolean isOpened() {
        return (entry != null) && (exit == null);
    }

    /**
     * @return true if the position is new, false otherwise
     */
    public boolean isNew() {
        return (entry == null) && (exit == null);
    }

    /**
     * @return true if position is closed and {@link #getProfit()} > 0
     */
    public boolean hasProfit() {
        return getProfit().isPositive();
    }

    /**
     * @return true if position is closed and {@link #getProfit()} {@literal <} 0
     */
    public boolean hasLoss() {
        return getProfit().isNegative();
    }

    /**
     * Calculates the net profit of the position if it is closed. The net profit
     * includes any trading costs.
     *
     * @return the profit or loss of the position
     */
    public Num getProfit() {
        if (isOpened()) {
            return zero();
        } else if (futuresContract != null) {
            return FuturesPositionAccounting.profit(this, exit.getPricePerAsset(), Integer.MAX_VALUE);
        } else {
            return getGrossProfit(exit.getPricePerAsset()).minus(getPositionCost());
        }
    }

    /**
     * Calculates the net profit of the position. If it is open, calculates the
     * profit until the final bar. The net profit includes any trading costs.
     *
     * @param finalIndex the index of the final bar to be considered (if position is
     *                   open)
     * @param finalPrice the price of the final bar to be considered (if position is
     *                   open)
     * @return the profit or loss of the position
     */
    public Num getProfit(int finalIndex, Num finalPrice) {
        if (futuresContract != null) {
            return FuturesPositionAccounting.profit(this, finalPrice, finalIndex);
        }
        Num grossProfit = isOpened() || exit.getIndex() > finalIndex ? openGrossProfit(finalPrice)
                : getGrossProfit(finalPrice);
        Num tradingCost = getPositionCost(finalIndex);
        return grossProfit.minus(tradingCost);
    }

    /**
     * Calculates the realized profit of the position as of {@code finalIndex}.
     *
     * <p>
     * Executed fills realize their payoff net of fees, funding, and holding cost.
     * While exposure is still open, only executed fees, funding, holding cost and
     * paid variation margin are realized; the mark-to-entry part of the exposure
     * stays unrealized. Realized plus {@link #getUnrealizedProfit(Num, int)} equals
     * {@link #getProfit(int, Num)}.
     * </p>
     *
     * <p>
     * Spot positions realize their entry cost while exposure is still open and
     * their full net profit once the exit has executed at or before
     * {@code finalIndex}.
     * </p>
     *
     * @param finalIndex the index of the final bar to be considered
     * @return the realized profit in the settlement currency
     * @since 0.25.1
     */
    public Num getRealizedProfit(int finalIndex) {
        if (futuresContract != null) {
            return FuturesPositionAccounting.realizedProfit(this, finalIndex);
        }
        if (isOpened() || exit.getIndex() > finalIndex) {
            Num realizedSpotCost = getRealizedSpotCost(finalIndex);
            return realizedSpotCost.isZero() ? zero() : realizedSpotCost.negate();
        }
        return getProfit(finalIndex, exit.getPricePerAsset());
    }

    private Num getRealizedSpotCost(int finalIndex) {
        if (entry == null || entry.getIndex() > finalIndex) {
            return zero();
        }
        return entry.getCost().plus(getHoldingCost(finalIndex));
    }

    /**
     * Calculates the unrealized mark-to-entry profit of the open exposure.
     *
     * <p>
     * The value is the raw mark-to-entry profit of the exposure that has not been
     * closed yet, minus variation margin already settled for it. It is zero once
     * the exit has executed at or before {@code finalIndex}.
     * </p>
     *
     * <p>
     * Spot positions value their own open exposure, so the result is the part of
     * {@link #getProfit(int, Num)} that {@link #getRealizedProfit(int)} has not
     * realized yet.
     * </p>
     *
     * @param markPrice  the mark price of the open exposure, positive and finite
     *                   for futures positions
     * @param finalIndex the index of the final bar to be considered
     * @return the unrealized profit in the settlement currency
     * @since 0.25.1
     */
    public Num getUnrealizedProfit(Num markPrice, int finalIndex) {
        if (futuresContract != null) {
            return FuturesPositionAccounting.unrealizedProfit(this, markPrice, finalIndex);
        }
        if (isOpened() || exit.getIndex() > finalIndex) {
            return getProfit(finalIndex, markPrice).minus(getRealizedProfit(finalIndex));
        }
        return zero();
    }

    /**
     * Calculates the return of the position on an explicitly supplied margin
     * amount.
     *
     * <p>
     * The margin is a caller-supplied economic assumption ({@code initialCapital *
     * initialMarginRate} in a futures record), never a collateral requirement
     * enforced here.
     * </p>
     *
     * @param initialMargin positive and finite margin in the settlement currency
     * @param finalPrice    the price of the final bar to be considered
     * @param finalIndex    the index of the final bar to be considered
     * @return the return including the base, i.e.
     *         {@code 1 + profit / initialMargin}
     * @throws IllegalArgumentException when the margin is not positive and finite
     * @since 0.25.1
     */
    public Num getReturnOnMargin(Num initialMargin, Num finalPrice, int finalIndex) {
        FuturesValidation.requirePositiveFinite(initialMargin, "initialMargin");
        Num profit = getProfit(finalIndex, finalPrice);
        Num margin = profit.getNumFactory().numOf(initialMargin.getDelegate());
        return profit.getNumFactory().one().plus(profit.dividedBy(margin));
    }

    /**
     * Calculates the gross profit of the position if it is closed. The gross profit
     * excludes any trading costs.
     *
     * @return the gross profit of the position
     */
    public Num getGrossProfit() {
        if (isOpened()) {
            return zero();
        } else {
            return getGrossProfit(exit.getPricePerAsset());
        }
    }

    /**
     * Calculates the gross profit of the position. The gross profit excludes any
     * trading costs.
     *
     * @param finalPrice the price of the final bar to be considered (if position is
     *                   open)
     * @return the profit or loss of the position
     */
    public Num getGrossProfit(Num finalPrice) {
        if (futuresContract != null) {
            return FuturesPositionAccounting.payoff(this, finalPrice, Integer.MAX_VALUE);
        }
        if (isOpened()) {
            return openGrossProfit(finalPrice);
        }
        Num grossProfit = exit.getValue().minus(entry.getValue());

        // Profits of long position are losses of short
        if (entry.isSell()) {
            grossProfit = grossProfit.negate();
        }
        return grossProfit;
    }

    /**
     * Calculates the gross profit of the outstanding exposure valued at the
     * supplied price against its entry value. Profits of a long position are losses
     * of a short.
     *
     * @param finalPrice price used to value the outstanding exposure
     * @return the gross profit of the outstanding exposure
     */
    private Num openGrossProfit(Num finalPrice) {
        Num grossProfit = entry.getAmount().multipliedBy(finalPrice).minus(entry.getValue());
        return entry.isSell() ? grossProfit.negate() : grossProfit;
    }

    /**
     * Calculates the gross return of the position if it is closed. The gross return
     * excludes any trading costs (and includes the base).
     *
     * @return the gross return of the position in percent
     * @see #getGrossReturn(Num)
     */
    public Num getGrossReturn() {
        if (isOpened()) {
            return zero();
        } else {
            return getGrossReturn(exit.getPricePerAsset());
        }
    }

    /**
     * Calculates the gross return of the position, if it exited at the provided
     * price. The gross return excludes any trading costs (and includes the base).
     *
     * @param finalPrice the price of the final bar to be considered (if position is
     *                   open)
     * @return the gross return of the position in percent
     * @see #getGrossReturn(Num, Num)
     */
    public Num getGrossReturn(Num finalPrice) {
        if (futuresContract != null) {
            return FuturesPositionAccounting.grossReturn(this, finalPrice);
        }
        return getGrossReturn(getEntry().getPricePerAsset(), finalPrice);
    }

    /**
     * Calculates the gross return of the position. If either the entry or exit
     * price is {@code NaN}, the close price from given {@code barSeries} is used.
     * The gross return excludes any trading costs (and includes the base).
     *
     * @param barSeries
     * @return the gross return in percent with entry and exit prices from the
     *         barSeries
     * @see #getGrossReturn(Num, Num)
     */
    public Num getGrossReturn(BarSeries barSeries) {
        Num entryPrice = getEntry().getPricePerAsset(barSeries);
        Num exitPrice = getExit().getPricePerAsset(barSeries);
        return getGrossReturn(entryPrice, exitPrice);
    }

    /**
     * Calculates the gross return between entry and exit price in percent. Includes
     * the base.
     *
     * <p>
     * For example:
     * <ul>
     * <li>For buy position with a profit of 4%, it returns 1.04 (includes the base)
     * <li>For sell position with a loss of 4%, it returns 0.96 (includes the base)
     * </ul>
     *
     * @param entryPrice the entry price
     * @param exitPrice  the exit price
     * @return the gross return in percent between entryPrice and exitPrice
     *         (includes the base)
     */
    public Num getGrossReturn(Num entryPrice, Num exitPrice) {
        if (futuresContract != null) {
            Num quantity = FuturesPositionAccounting.matchedQuantity(this);
            Num entryNotional = futuresContract.settlementNotional(quantity, entryPrice);
            Num payoff = futuresContract.profit(getStartingType(), quantity, entryPrice, exitPrice);
            return entryPrice.getNumFactory().one().plus(payoff.dividedBy(entryNotional));
        }
        if (getEntry().isBuy()) {
            return exitPrice.dividedBy(entryPrice);
        } else {
            Num one = entryPrice.getNumFactory().one();
            return ((exitPrice.dividedBy(entryPrice).minus(one)).negate()).plus(one);
        }
    }

    /**
     * Calculates the total cost of the position.
     *
     * @param finalIndex the index of the final bar to be considered (if position is
     *                   open)
     * @return the cost of the position
     */
    public Num getPositionCost(int finalIndex) {
        Num transactionCost = transactionCostModel.calculate(this, finalIndex);
        Num borrowingCost = getHoldingCost(finalIndex);
        return transactionCost.plus(borrowingCost);
    }

    /**
     * Calculates the total cost of the closed position.
     *
     * @return the cost of the position
     */
    public Num getPositionCost() {
        Num transactionCost = transactionCostModel.calculate(this);
        Num borrowingCost = getHoldingCost();
        return transactionCost.plus(borrowingCost);
    }

    /**
     * Calculates the holding cost of the closed position. Entry fills of a native
     * futures position accrue over their own exposure interval, so a position whose
     * fills span several executions is charged to its completion index.
     *
     * @return the cost of the position
     */
    public Num getHoldingCost() {
        if (futuresContract != null && exit != null
                && (Trade.executionFillsOf(entry).size() > 1 || Trade.executionFillsOf(exit).size() > 1)) {
            return getHoldingCost(exitCompletionIndex());
        }
        return holdingCostModel.calculate(this);
    }

    /**
     * Calculates the holding cost of the position. For native futures positions,
     * every executed fill accrues over its own exposure interval: an entry fill is
     * charged until the closing fill that consumes it, and any remainder until
     * {@code finalIndex}.
     *
     * @param finalIndex the index of the final bar to be considered (if position is
     *                   open)
     * @return the cost of the position
     */
    public Num getHoldingCost(int finalIndex) {
        CostModel model = getHoldingCostModel();
        if (futuresContract == null) {
            return model.calculate(this, finalIndex);
        }
        NumFactory numFactory = entry.getPricePerAsset().getNumFactory();
        List<TradeFill> executedEntryFills = FuturesPositionAccounting.executedFills(entry, finalIndex);
        if (executedEntryFills.isEmpty()) {
            return numFactory.zero();
        }
        if (Trade.executionFillsOf(entry).size() == 1 && (exit == null || Trade.executionFillsOf(exit).size() == 1)) {
            return model.calculate(this, finalIndex);
        }
        Deque<TradeFill> closingFills = new ArrayDeque<>(
                exit == null ? List.of() : FuturesPositionAccounting.executedFills(exit, finalIndex));
        Deque<Num> closingAmounts = new ArrayDeque<>();
        for (TradeFill closingFill : closingFills) {
            closingAmounts.addLast(closingFill.amount());
        }
        Num holdingCost = numFactory.zero();
        for (TradeFill entryFill : executedEntryFills) {
            Num openAmount = entryFill.amount();
            while (openAmount.isPositive() && !closingAmounts.isEmpty()) {
                TradeFill closingFill = closingFills.removeFirst();
                Num closingAmount = closingAmounts.removeFirst();
                Num closeAmount = openAmount.isLessThan(closingAmount) ? openAmount : closingAmount;
                holdingCost = holdingCost
                        .plus(model.calculate(slicePosition(entryFill, closingFill, closeAmount), finalIndex));
                openAmount = openAmount.minus(closeAmount);
                Num retainedAmount = closingAmount.minus(closeAmount);
                if (retainedAmount.isPositive()) {
                    closingFills.addFirst(closingFill);
                    closingAmounts.addFirst(retainedAmount);
                }
            }
            if (openAmount.isPositive()) {
                holdingCost = holdingCost.plus(model.calculate(slicePosition(entryFill, null, openAmount), finalIndex));
            }
        }
        return holdingCost;
    }

    /**
     * Builds the sub-position of a single entry fill matched against a single
     * closing fill, so a pluggable cost model can charge that exposure slice.
     *
     * @param entryFill   entry fill holding the slice
     * @param closingFill closing fill consuming the slice, or {@code null} when the
     *                    slice is still open
     * @param amount      amount of the slice
     * @return sub-position covering the slice
     */
    private Position slicePosition(TradeFill entryFill, TradeFill closingFill, Num amount) {
        Trade sliceEntry = Trade.fromFills(entry.getType(), List.of(entryFill.toBuilder().amount(amount).build()),
                getTransactionCostModel());
        if (closingFill == null) {
            return new Position(sliceEntry, getTransactionCostModel(), getHoldingCostModel());
        }
        Trade sliceExit = Trade.fromFills(exit.getType(), List.of(closingFill.toBuilder().amount(amount).build()),
                getTransactionCostModel());
        return new Position(sliceEntry, sliceExit, getTransactionCostModel(), getHoldingCostModel());
    }

    /**
     * @return the index of the latest executed closing fill, falling back to the
     *         recorded index of the exit
     */
    private int exitCompletionIndex() {
        int completionIndex = exit.getIndex();
        for (TradeFill exitFill : Trade.executionFillsOf(exit)) {
            if (exitFill.index() >= 0) {
                completionIndex = Math.max(completionIndex, exitFill.index());
            }
        }
        return completionIndex;
    }

    /**
     * @return the transaction cost model, or a zero-cost model after
     *         deserialization when the model is unset
     *
     * @since 0.22.2
     */
    public CostModel getTransactionCostModel() {
        return transactionCostModel == null ? new ZeroCostModel() : transactionCostModel;
    }

    /**
     * @return the holding cost model, or a zero-cost model after deserialization
     *         when the model is unset
     *
     * @since 0.22.2
     */
    public CostModel getHoldingCostModel() {
        return holdingCostModel == null ? new ZeroCostModel() : holdingCostModel;
    }

    /**
     * @return the {@link #startingType}
     */
    public TradeType getStartingType() {
        return startingType;
    }

    private Position(ValidatedStartingPosition config) {
        this.startingType = config.startingType();
        this.transactionCostModel = config.transactionCostModel();
        this.holdingCostModel = config.holdingCostModel();
        this.futuresContract = null;
        this.cashFlows = List.of();
    }

    private Position(ValidatedClosedPosition config, List<FuturesCashFlow> cashFlows) {
        this.startingType = config.entry().getType();
        this.entry = config.entry();
        this.exit = config.exit();
        this.transactionCostModel = config.transactionCostModel();
        this.holdingCostModel = config.holdingCostModel();
        this.futuresContract = config.contract();
        this.cashFlows = validateCashFlows(config.contract(), cashFlows);
    }

    private Position(ValidatedOpenPosition config, List<FuturesCashFlow> cashFlows) {
        this.startingType = config.entry().getType();
        this.entry = config.entry();
        this.exit = null;
        this.transactionCostModel = config.transactionCostModel();
        this.holdingCostModel = config.holdingCostModel();
        this.futuresContract = config.contract();
        this.cashFlows = validateCashFlows(config.contract(), cashFlows);
    }

    /**
     * Constructor for a closed position with explicitly allocated cash flows.
     *
     * @param entry                the entry {@link Trade trade}
     * @param exit                 the exit {@link Trade trade}
     * @param transactionCostModel the cost model for transactions of the asset
     * @param holdingCostModel     the cost model for holding asset
     * @param cashFlows            cash flows allocated to this position
     * @since 0.25.1
     */
    Position(Trade entry, Trade exit, CostModel transactionCostModel, CostModel holdingCostModel,
            List<FuturesCashFlow> cashFlows) {
        this(validateClosedPosition(entry, exit, transactionCostModel, holdingCostModel), cashFlows);
    }

    /**
     * Constructor for an open position with explicitly allocated cash flows.
     *
     * @param entry                the entry {@link Trade trade}
     * @param transactionCostModel the cost model for transactions of the asset
     * @param holdingCostModel     the cost model for holding asset
     * @param cashFlows            cash flows allocated to this position
     * @since 0.25.1
     */
    Position(Trade entry, CostModel transactionCostModel, CostModel holdingCostModel, List<FuturesCashFlow> cashFlows) {
        this(validateOpenPosition(entry, transactionCostModel, holdingCostModel), cashFlows);
    }

    private static ValidatedStartingPosition validateStartingPosition(TradeType startingType,
            CostModel transactionCostModel, CostModel holdingCostModel) {
        if (startingType == null) {
            throw new IllegalArgumentException("Starting type must not be null");
        }
        return new ValidatedStartingPosition(startingType, transactionCostModel, holdingCostModel);
    }

    private static ValidatedStartingPosition validateDefaultStartingPosition() {
        return validateStartingPosition(TradeType.BUY, new ZeroCostModel(), new ZeroCostModel());
    }

    private static ValidatedStartingPosition validateDefaultStartingPosition(TradeType startingType) {
        return validateStartingPosition(startingType, new ZeroCostModel(), new ZeroCostModel());
    }

    private static CostModel defaultTransactionCostModel(Trade entry) {
        return Objects.requireNonNull(entry, "entry").getCostModel();
    }

    private static ValidatedClosedPosition validateClosedPositionWithDefaults(Trade entry, Trade exit) {
        return validateClosedPosition(entry, exit, defaultTransactionCostModel(entry), new ZeroCostModel());
    }

    private static ValidatedClosedPosition validateClosedPosition(Trade entry, Trade exit,
            CostModel transactionCostModel, CostModel holdingCostModel) {
        Trade validatedEntry = Objects.requireNonNull(entry, "entry");
        Trade validatedExit = Objects.requireNonNull(exit, "exit");

        if (validatedEntry.getType().equals(validatedExit.getType())) {
            throw new IllegalArgumentException("Both trades must have different types");
        }
        if (!(validatedEntry.getCostModel().equals(transactionCostModel))
                || !(validatedExit.getCostModel().equals(transactionCostModel))) {
            throw new IllegalArgumentException("Trades and the position must incorporate the same trading cost model");
        }

        return new ValidatedClosedPosition(validatedEntry, validatedExit, transactionCostModel, holdingCostModel,
                resolveContract(validatedEntry, validatedExit));
    }

    private static ValidatedOpenPosition validateOpenPosition(Trade entry, CostModel transactionCostModel,
            CostModel holdingCostModel) {
        Trade validatedEntry = Objects.requireNonNull(entry, "entry");
        if (!(validatedEntry.getCostModel().equals(transactionCostModel))) {
            throw new IllegalArgumentException("Trades and the position must incorporate the same trading cost model");
        }
        return new ValidatedOpenPosition(validatedEntry, transactionCostModel, holdingCostModel,
                resolveContract(validatedEntry, null));
    }

    private void validateContract(FuturesContract tradeContract) {
        if (!Objects.equals(futuresContract, tradeContract)) {
            throw new IllegalArgumentException("Trade futures contract must match the position contract");
        }
    }

    private static FuturesContract resolveContract(Trade entry, Trade exit) {
        FuturesContract contract = entry.getFuturesContract();
        if (exit == null) {
            return contract;
        }
        FuturesContract exitContract = exit.getFuturesContract();
        if (contract == null && exitContract == null) {
            return null;
        }
        if (contract == null || !contract.equals(exitContract)) {
            throw new IllegalArgumentException("Both trades must reference the same futures contract");
        }
        return contract;
    }

    private static List<FuturesCashFlow> validateCashFlows(FuturesContract contract, List<FuturesCashFlow> cashFlows) {
        if (cashFlows == null || cashFlows.isEmpty()) {
            return List.of();
        }
        if (contract == null) {
            throw new IllegalArgumentException("Cash flows are only defined for futures positions");
        }
        for (FuturesCashFlow cashFlow : cashFlows) {
            FuturesContract cashFlowContract = Objects.requireNonNull(cashFlow, "cashFlow").contract();
            if (cashFlowContract != null && !contract.equals(cashFlowContract)) {
                throw new IllegalArgumentException("Cash flow contract must match the position contract");
            }
        }
        return List.copyOf(cashFlows);
    }

    private record ValidatedStartingPosition(TradeType startingType, CostModel transactionCostModel,
            CostModel holdingCostModel) {
    }

    private record ValidatedClosedPosition(Trade entry, Trade exit, CostModel transactionCostModel,
            CostModel holdingCostModel, FuturesContract contract) {
    }

    private record ValidatedOpenPosition(Trade entry, CostModel transactionCostModel, CostModel holdingCostModel,
            FuturesContract contract) {
    }

    /**
     * @return the Num of 0
     */
    private Num zero() {
        return entry.getNetPrice().getNumFactory().zero();
    }

    @Override
    public String toString() {
        return "Entry: " + entry + " exit: " + exit;
    }
}

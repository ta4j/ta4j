/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.util.ArrayDeque;
import java.util.List;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Derives the settlement economics of a matched futures {@link Position} from
 * its executed entry and exit fills and its allocated cash flows.
 *
 * <p>
 * The helper never matches raw trades: it consumes the already-matched entry
 * and exit carried by the position. Exposure is split by execution index, so
 * portions that have not executed yet are marked to a supplied price instead of
 * being realized early.
 * </p>
 *
 * <p>
 * Sign conventions: contract payoff, fees and funding are all expressed in the
 * contract settlement currency. Fees are charge-positive, funding and variation
 * margin cash flows are credit-positive. Variation margin never changes the
 * total profit of a position: it moves an equal amount from unrealized to
 * realized economics.
 * </p>
 *
 * @since 0.25.1
 */
final class FuturesPositionAccounting {

    private FuturesPositionAccounting() {
    }

    /**
     * Returns the futures contract of a futures position.
     *
     * @param position position to inspect
     * @return the contract carried by the position
     * @throws IllegalStateException when the position is not a futures position or
     *                               has no entry
     * @since 0.25.1
     */
    static FuturesContract requireContract(Position position) {
        FuturesContract contract = position.getFuturesContract();
        if (contract == null) {
            throw new IllegalStateException("position is not a futures position");
        }
        return contract;
    }

    /**
     * Returns the settlement-currency payoff of the position at a price.
     *
     * @param position   futures position
     * @param finalPrice price used for the unexecuted portion, ignored when the
     *                   exit already executed
     * @param finalIndex index up to which executions are recognized
     * @return signed payoff in the settlement currency
     * @since 0.25.1
     */
    static Num payoff(Position position, Num finalPrice, int finalIndex) {
        FuturesContract contract = requireContract(position);
        Trade entry = position.getEntry();
        NumFactory numFactory = entry.getPricePerAsset().getNumFactory();
        List<TradeFill> entryFills = executedFills(entry, finalIndex);
        ArrayDeque<FillSlice> exits = new ArrayDeque<>();
        Trade exit = position.getExit();
        if (exit != null) {
            for (TradeFill fill : executedFills(exit, finalIndex)) {
                exits.addLast(fillSlice(fill, numFactory));
            }
        }
        Num total = numFactory.zero();
        for (TradeFill entryFill : entryFills) {
            FillSlice normalizedEntry = fillSlice(entryFill, numFactory);
            Num remainingEntry = normalizedEntry.amount();
            while (remainingEntry.isPositive() && !exits.isEmpty()) {
                FillSlice exitFill = exits.removeFirst();
                Num matched = remainingEntry.isLessThan(exitFill.amount()) ? remainingEntry : exitFill.amount();
                total = total
                        .plus(contract.profit(entry.getType(), matched, normalizedEntry.price(), exitFill.price()));
                remainingEntry = remainingEntry.minus(matched);
                Num remainingExit = exitFill.amount().minus(matched);
                if (remainingExit.isPositive()) {
                    exits.addFirst(new FillSlice(exitFill.price(), remainingExit));
                }
            }
            if (remainingEntry.isPositive()) {
                total = total
                        .plus(contract.profit(entry.getType(), remainingEntry, normalizedEntry.price(), finalPrice));
            }
        }
        return total;
    }

    /**
     * Returns the settlement-currency payoff of the position when the executed
     * portion is realized and the unexecuted portion is marked to
     * {@code finalPrice}.
     *
     * @param position   futures position
     * @param finalPrice price used for the unexecuted portion
     * @return signed payoff in the settlement currency
     * @since 0.25.1
     */
    static Num payoff(Position position, Num finalPrice) {
        return payoff(position, finalPrice, Integer.MAX_VALUE);
    }

    /**
     * Returns the credit-positive funding cash flows allocated to the position.
     *
     * @param position futures position
     * @return funding credits minus funding debits in the settlement currency
     * @since 0.25.1
     */
    static Num funding(Position position) {
        return funding(position, Integer.MAX_VALUE);
    }

    /**
     * Returns the credit-positive funding cash flows accounted no later than
     * {@code finalIndex}.
     *
     * @param position   futures position
     * @param finalIndex index up to which cash flows are recognized
     * @return funding credits minus funding debits in the settlement currency
     * @since 0.25.1
     */
    static Num funding(Position position, int finalIndex) {
        return sum(position, FuturesCashFlow.Type.FUNDING, finalIndex);
    }

    /**
     * Returns the credit-positive variation margin allocated to the position.
     *
     * @param position futures position
     * @return variation margin credits minus debits in the settlement currency
     * @since 0.25.1
     */
    static Num variationMargin(Position position) {
        return variationMargin(position, Integer.MAX_VALUE);
    }

    /**
     * Returns the credit-positive variation margin accounted no later than
     * {@code finalIndex}.
     *
     * @param position   futures position
     * @param finalIndex index up to which cash flows are recognized
     * @return variation margin credits minus debits in the settlement currency
     * @since 0.25.1
     */
    static Num variationMargin(Position position, int finalIndex) {
        return sum(position, FuturesCashFlow.Type.VARIATION_MARGIN, finalIndex);
    }

    /**
     * Returns the settlement-currency fees executed up to {@code finalIndex}.
     *
     * @param position   futures position
     * @param finalIndex index up to which executions are recognized
     * @return charge-positive fee total
     * @since 0.25.1
     */
    static Num executedFees(Position position, int finalIndex) {
        Trade entry = position.getEntry();
        NumFactory numFactory = entry.getPricePerAsset().getNumFactory();
        Num total = sumFillFees(entry, finalIndex, numFactory);
        Trade exit = position.getExit();
        if (exit != null) {
            total = total.plus(sumFillFees(exit, finalIndex, numFactory));
        }
        return total;
    }

    /**
     * Returns the settlement-currency fees of the position.
     *
     * @param position futures position
     * @return charge-positive fee total
     * @since 0.25.1
     */
    static Num executedFees(Position position) {
        return executedFees(position, Integer.MAX_VALUE);
    }

    /**
     * Returns the realized profit of the position as of {@code finalIndex}.
     *
     * <p>
     * A closed slice realizes its payoff net of fees, funding and holding cost. An
     * open slice realizes executed fees, funding, holding cost and paid variation
     * margin; the mark-to-entry part of its exposure stays unrealized.
     * </p>
     *
     * @param position   futures position
     * @param finalIndex index up to which executions are recognized
     * @return realized profit in the settlement currency
     * @since 0.25.1
     */
    static Num realizedProfit(Position position, int finalIndex) {
        Num fees = executedFees(position, finalIndex);
        Num funding = funding(position, finalIndex);
        Num holdingCost = holdingCost(position, finalIndex);
        Num realizedPayoff = executedPayoff(position, finalIndex);
        if (isFullyExecutedExit(position, finalIndex)) {
            return realizedPayoff.minus(fees).plus(funding).minus(holdingCost);
        }
        // Variation margin paid on the still-open exposure is realized cash that
        // the unrealized mark-to-entry value must give back.
        return realizedPayoff.minus(fees).plus(funding).minus(holdingCost).plus(variationMargin(position, finalIndex));
    }

    private static Num holdingCost(Position position, int finalIndex) {
        return position.getHoldingCost(finalIndex);
    }

    /**
     * Returns the unrealized mark-to-entry profit of the still-open exposure.
     *
     * @param position   futures position
     * @param markPrice  positive and finite mark price
     * @param finalIndex index up to which executions are recognized
     * @return unrealized profit in the settlement currency, zero when the exit has
     *         already executed
     * @since 0.25.1
     */
    static Num unrealizedProfit(Position position, Num markPrice, int finalIndex) {
        if (isFullyExecutedExit(position, finalIndex)) {
            return position.getEntry().getPricePerAsset().getNumFactory().zero();
        }
        return payoff(position, markPrice, finalIndex).minus(executedPayoff(position, finalIndex))
                .minus(variationMargin(position, finalIndex));
    }

    /**
     * Returns the unlevered gross return of the position.
     *
     * <p>
     * The denominator is the settlement notional of the matched quantity at the
     * original entry price, never the margin posted for it.
     * </p>
     *
     * @param position   futures position
     * @param finalPrice price used for the unexecuted portion
     * @return gross return including the base, i.e.
     *         {@code 1 + payoff / entryNotional}
     * @since 0.25.1
     */
    static Num grossReturn(Position position, Num finalPrice) {
        Trade entry = position.getEntry();
        Num quantity = matchedQuantity(position);
        Num entryNotional = requireContract(position).settlementNotional(quantity, entry.getPricePerAsset());
        Num payoff = payoff(position, finalPrice);
        return entry.getPricePerAsset().getNumFactory().one().plus(payoff.dividedBy(entryNotional));
    }

    /**
     * Returns the net profit of the position: payoff plus funding minus fees.
     *
     * @param position   futures position
     * @param finalPrice price used for the unexecuted portion
     * @param finalIndex index up to which executions are recognized
     * @return net profit in the settlement currency
     * @since 0.25.1
     */
    static Num profit(Position position, Num finalPrice, int finalIndex) {
        return payoff(position, finalPrice, finalIndex).plus(funding(position, finalIndex))
                .minus(executedFees(position, finalIndex))
                .minus(position.getHoldingCost(finalIndex));
    }

    /**
     * Returns the quantity used to normalize the position's executed futures
     * exposure.
     *
     * @param position futures position
     * @return matched contract count
     * @since 0.25.1
     */
    static Num matchedQuantity(Position position) {
        Trade entry = position.getEntry();
        Trade exit = position.getExit();
        if (exit != null) {
            return exit.getAmount();
        }
        List<TradeFill> fills = Trade.executionFillsOf(entry);
        if (fills.isEmpty()) {
            return entry.getAmount();
        }
        NumFactory numFactory = entry.getPricePerAsset().getNumFactory();
        Num executedAmount = numFactory.zero();
        for (TradeFill fill : fills) {
            if (fill.index() >= 0) {
                executedAmount = executedAmount.plus(numFactory.numOf(fill.amount().getDelegate()));
            }
        }
        return executedAmount;
    }

    private static Num executedPayoff(Position position, int finalIndex) {
        FuturesContract contract = requireContract(position);
        Trade entry = position.getEntry();
        NumFactory numFactory = entry.getPricePerAsset().getNumFactory();
        ArrayDeque<FillSlice> exits = new ArrayDeque<>();
        Trade exit = position.getExit();
        if (exit != null) {
            for (TradeFill fill : executedFills(exit, finalIndex)) {
                exits.addLast(fillSlice(fill, numFactory));
            }
        }
        Num total = numFactory.zero();
        for (TradeFill entryFill : executedFills(entry, finalIndex)) {
            FillSlice normalizedEntry = fillSlice(entryFill, numFactory);
            Num remainingEntry = normalizedEntry.amount();
            while (remainingEntry.isPositive() && !exits.isEmpty()) {
                FillSlice exitFill = exits.removeFirst();
                Num matched = remainingEntry.isLessThan(exitFill.amount()) ? remainingEntry : exitFill.amount();
                total = total
                        .plus(contract.profit(entry.getType(), matched, normalizedEntry.price(), exitFill.price()));
                remainingEntry = remainingEntry.minus(matched);
                Num remainingExit = exitFill.amount().minus(matched);
                if (remainingExit.isPositive()) {
                    exits.addFirst(new FillSlice(exitFill.price(), remainingExit));
                }
            }
        }
        return total;
    }

    private static boolean isFullyExecutedExit(Position position, int finalIndex) {
        Trade exit = position.getExit();
        if (exit == null) {
            return false;
        }
        NumFactory numFactory = exit.getPricePerAsset().getNumFactory();
        Num executed = numFactory.zero();
        for (TradeFill fill : executedFills(exit, finalIndex)) {
            executed = executed.plus(numFactory.numOf(fill.amount().getDelegate()));
        }
        return executed.isGreaterThanOrEqual(numFactory.numOf(exit.getAmount().getDelegate()));
    }

    private static Trade executedExit(Position position, int finalIndex) {
        Trade exit = position.getExit();
        return exit != null && !executedFills(exit, finalIndex).isEmpty() ? exit : null;
    }

    static List<TradeFill> executedFills(Trade trade, int finalIndex) {
        return Trade.executionFillsOf(trade)
                .stream()
                .filter(fill -> fill.index() >= 0 && fill.index() <= finalIndex)
                .toList();
    }

    private static Num sumFillFees(Trade trade, int finalIndex, NumFactory numFactory) {
        Num total = numFactory.zero();
        for (TradeFill fill : executedFills(trade, finalIndex)) {
            total = total.plus(numFactory.numOf(fill.fee().getDelegate()));
        }
        return total;
    }

    private static FillSlice fillSlice(TradeFill fill, NumFactory numFactory) {
        return new FillSlice(numFactory.numOf(fill.price().getDelegate()),
                numFactory.numOf(fill.amount().getDelegate()));
    }

    private record FillSlice(Num price, Num amount) {
    }

    private static Num sum(Position position, FuturesCashFlow.Type type, int finalIndex) {
        List<FuturesCashFlow> cashFlows = position.getCashFlows();
        Num total = position.getEntry().getPricePerAsset().getNumFactory().zero();
        for (FuturesCashFlow cashFlow : cashFlows) {
            if (cashFlow.type() != type || cashFlow.index() > finalIndex) {
                continue;
            }
            Num amount = cashFlow.settlementAmount() == null ? cashFlow.amount() : cashFlow.settlementAmount();
            total = total.plus(total.getNumFactory().numOf(amount.getDelegate()));
        }
        return total;
    }
}

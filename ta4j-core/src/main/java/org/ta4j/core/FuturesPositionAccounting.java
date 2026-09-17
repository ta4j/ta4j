/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
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
        ArrayDeque<FillSlice> exits = new ArrayDeque<>();
        Trade exit = position.getExit();
        if (exit != null) {
            for (TradeFill fill : executedFills(exit, finalIndex)) {
                exits.addLast(fillSlice(fill, numFactory));
            }
        }
        return matchedPayoff(entry, contract, numFactory, exits, finalIndex, finalPrice);
    }

    /**
     * Matches executed entry fills against executed exit fills and returns the
     * settlement payoff of the matched quantity.
     *
     * <p>
     * The open exposure maintains its chronological average entry basis. The basis
     * is reset when an exit reduces exposure to zero, so a later entry cannot
     * change the payoff of an earlier flat exposure interval.
     * </p>
     *
     * @param entry           entry trade carrying the basis
     * @param contract        futures contract
     * @param numFactory      numeric factory of the entry price
     * @param exits           queued exit slices, consumed in order
     * @param finalIndex      index up to which executions are recognized
     * @param unexecutedPrice price applied to entry quantity without an exit, or
     *                        {@code null} to leave that quantity unrealized
     * @return signed payoff in the settlement currency
     */
    private static Num matchedPayoff(Trade entry, FuturesContract contract, NumFactory numFactory,
            ArrayDeque<FillSlice> exits, int finalIndex, Num unexecutedPrice) {
        List<TradeFill> entryFills = executedFills(entry, finalIndex);
        List<ExposureEvent> events = new ArrayList<>(entryFills.size() + exits.size());
        boolean useTradeBasis = entryFills.size() == 1 && entryFills.size() == Trade.executionFillsOf(entry).size();
        for (TradeFill entryFill : entryFills) {
            FillSlice entrySlice = fillSlice(entryFill, numFactory);
            if (useTradeBasis) {
                entrySlice = new FillSlice(numFactory.numOf(entry.getPricePerAsset().getDelegate()),
                        entrySlice.amount(), entrySlice.index(), entrySlice.time());
            }
            events.add(new ExposureEvent(entrySlice, true));
        }
        while (!exits.isEmpty()) {
            events.add(new ExposureEvent(exits.removeFirst(), false));
        }
        events.sort(Comparator.comparingInt((ExposureEvent event) -> event.slice().index())
                .thenComparing(event -> event.slice().time(), Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(ExposureEvent::entry, Comparator.reverseOrder()));

        Num activeAmount = numFactory.zero();
        Num basis = null;
        Num total = numFactory.zero();
        for (ExposureEvent event : events) {
            FillSlice fill = event.slice();
            if (event.entry()) {
                basis = combinedBasis(contract, basis, activeAmount, fill.price(), fill.amount());
                activeAmount = activeAmount.plus(fill.amount());
                continue;
            }
            if (basis == null || fill.amount().isGreaterThan(activeAmount)) {
                throw new IllegalArgumentException("futures exit amount exceeds executed entry amount");
            }
            total = total.plus(contract.profit(entry.getType(), fill.amount(), basis, fill.price()));
            activeAmount = activeAmount.minus(fill.amount());
            if (activeAmount.isZero()) {
                basis = null;
            }
        }
        if (unexecutedPrice != null && activeAmount.isPositive()) {
            total = total.plus(contract.profit(entry.getType(), activeAmount, basis, unexecutedPrice));
        }
        return total;
    }

    private static Num combinedBasis(FuturesContract contract, Num basis, Num activeAmount, Num entryPrice,
            Num entryAmount) {
        if (basis == null || activeAmount.isZero()) {
            return entryPrice;
        }
        Num totalAmount = activeAmount.plus(entryAmount);
        if (contract.settlementType() == FuturesContract.SettlementType.INVERSE) {
            Num quoteAmount = activeAmount.dividedBy(basis).plus(entryAmount.dividedBy(entryPrice));
            return totalAmount.dividedBy(quoteAmount);
        }
        return weightedAverage(basis, activeAmount, entryPrice, entryAmount);
    }

    private static Num weightedAverage(Num firstPrice, Num firstWeight, Num secondPrice, Num secondWeight) {
        Num totalWeight = firstWeight.plus(secondWeight);
        Num weightedPrice = firstPrice.multipliedBy(firstWeight).plus(secondPrice.multipliedBy(secondWeight));
        if (Num.isFinite(totalWeight) && !totalWeight.isZero() && Num.isFinite(weightedPrice)) {
            return weightedPrice.dividedBy(totalWeight);
        }
        Num maximumWeight = firstWeight.isGreaterThan(secondWeight) ? firstWeight : secondWeight;
        if (maximumWeight.isZero()) {
            return maximumWeight.getNumFactory().zero();
        }
        Num scaledFirstWeight = firstWeight.dividedBy(maximumWeight);
        Num scaledSecondWeight = secondWeight.dividedBy(maximumWeight);
        Num scaledTotalWeight = scaledFirstWeight.plus(scaledSecondWeight);
        Num firstShare = scaledFirstWeight.dividedBy(scaledTotalWeight);
        Num secondShare = scaledSecondWeight.dividedBy(scaledTotalWeight);
        return firstPrice.multipliedBy(firstShare).plus(secondPrice.multipliedBy(secondShare));
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
        SettlementAmountSupport.CompensatedSum total = new SettlementAmountSupport.CompensatedSum(numFactory,
                "fee amount", "fee total");
        sumFillFees(total, entry, finalIndex);
        Trade exit = position.getExit();
        if (exit != null) {
            sumFillFees(total, exit, finalIndex);
        }
        return total.total();
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
     * executed entry basis, never the margin posted for it.
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
        if (quantity.isZero()) {
            return entry.getPricePerAsset().getNumFactory().one();
        }
        FuturesContract contract = requireContract(position);
        NumFactory numFactory = entry.getPricePerAsset().getNumFactory();
        Num executedEntryBasis = entryBasis(entry, contract, numFactory, executedFills(entry, Integer.MAX_VALUE));
        Num entryNotional = contract.rawSettlementNotional(quantity, executedEntryBasis);
        if (!Num.isFinite(entryNotional) || entryNotional.isZero()) {
            return org.ta4j.core.num.NaN.NaN;
        }
        Num positionPayoff = payoff(position, finalPrice);
        return numFactory.one().plus(positionPayoff.dividedBy(entryNotional));
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
     * @return executed entry contract count
     * @since 0.25.1
     */
    static Num matchedQuantity(Position position) {
        Trade entry = position.getEntry();
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
        return matchedPayoff(entry, contract, numFactory, exits, finalIndex, null);
    }

    private static boolean isFullyExecutedExit(Position position, int finalIndex) {
        Trade entry = position.getEntry();
        Trade exit = position.getExit();
        if (exit == null) {
            return false;
        }
        NumFactory numFactory = entry.getPricePerAsset().getNumFactory();
        Num executedEntry = executedAmount(entry, finalIndex, numFactory);
        Num executedExit = executedAmount(exit, finalIndex, numFactory);
        return executedEntry.isPositive() && executedExit.isGreaterThanOrEqual(executedEntry);
    }

    private static Num executedAmount(Trade trade, int finalIndex, NumFactory numFactory) {
        Num total = numFactory.zero();
        for (TradeFill fill : executedFills(trade, finalIndex)) {
            total = total.plus(numFactory.numOf(fill.amount().getDelegate()));
        }
        return total;
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

    /**
     * Allocates cash flows to entry fills by timestamp eligibility. A fill owns a
     * share of every cash flow that happens strictly after it, pro rata by fill
     * amount; the last eligible fill absorbs the rounding residue so the shares
     * always add up to the recorded event.
     *
     * @param cashFlows       recorded cash flows, {@code null} treated as empty
     * @param fills           entry fills the cash flows are allocated to, may be
     *                        {@code null} or empty
     * @param quantityFactory factory the fill quantities are expressed in
     * @return one immutable slice per fill, aligned with {@code fills}; the source
     *         flows as a single slice when there is no fill to allocate to
     * @throws IllegalStateException    when an entry fill has no timestamp
     * @throws IllegalArgumentException when a cash flow has no eligible fill
     * @since 0.25.1
     */
    static List<List<FuturesCashFlow>> allocateCashFlowsByFill(List<FuturesCashFlow> cashFlows, List<TradeFill> fills,
            NumFactory quantityFactory) {
        List<FuturesCashFlow> source = cashFlows == null ? List.of() : List.copyOf(cashFlows);
        if (fills == null || fills.isEmpty()) {
            return List.of(source);
        }
        List<List<FuturesCashFlow>> slices = new ArrayList<>(fills.size());
        for (int i = 0; i < fills.size(); i++) {
            slices.add(new ArrayList<>());
        }
        for (FuturesCashFlow cashFlow : source) {
            List<Integer> eligibleIndices = new ArrayList<>();
            Num eligibleAmount = quantityFactory.zero();
            for (int i = 0; i < fills.size(); i++) {
                TradeFill fill = fills.get(i);
                Instant fillTime = fill.time();
                if (fillTime == null) {
                    throw new IllegalStateException("Futures cash flows require entry timestamps");
                }
                if (fill.index() >= 0 && fillTime.isBefore(cashFlow.time())) {
                    eligibleIndices.add(i);
                    eligibleAmount = eligibleAmount.plus(quantityFactory.numOf(fill.amount().getDelegate()));
                }
            }
            if (eligibleIndices.isEmpty() || eligibleAmount.isZero()) {
                throw new IllegalArgumentException("Cash flow has no eligible entry fill at " + cashFlow.time());
            }
            NumFactory amountFactory = cashFlow.amount().getNumFactory();
            NumFactory settlementFactory = cashFlow.settlementAmount().getNumFactory();
            Num eventAmount = amountFactory.numOf(cashFlow.amount().getDelegate());
            Num eventSettlement = settlementFactory.numOf(cashFlow.settlementAmount().getDelegate());
            Num allocatedAmount = amountFactory.zero();
            Num allocatedSettlement = settlementFactory.zero();
            for (int eligibleIndex = 0; eligibleIndex < eligibleIndices.size(); eligibleIndex++) {
                int fillIndex = eligibleIndices.get(eligibleIndex);
                Num fillAmount = quantityFactory.numOf(fills.get(fillIndex).amount().getDelegate());
                boolean last = eligibleIndex == eligibleIndices.size() - 1;
                Num portionAmount = last ? eventAmount.minus(allocatedAmount)
                        : proportional(eventAmount, amountFactory.numOf(fillAmount.getDelegate()),
                                amountFactory.numOf(eligibleAmount.getDelegate()));
                Num portionSettlement = last ? eventSettlement.minus(allocatedSettlement)
                        : proportional(eventSettlement, settlementFactory.numOf(fillAmount.getDelegate()),
                                settlementFactory.numOf(eligibleAmount.getDelegate()));
                allocatedAmount = allocatedAmount.plus(portionAmount);
                allocatedSettlement = allocatedSettlement.plus(portionSettlement);
                slices.get(fillIndex)
                        .add(cashFlow.toBuilder().amount(portionAmount).settlementAmount(portionSettlement).build());
            }
        }
        List<List<FuturesCashFlow>> immutableSlices = new ArrayList<>(slices.size());
        for (List<FuturesCashFlow> slice : slices) {
            immutableSlices.add(List.copyOf(slice));
        }
        return List.copyOf(immutableSlices);
    }

    /**
     * Scales a value to the portion of a total it represents.
     *
     * @param value   value to scale
     * @param portion portion of the total
     * @param total   total the portion is measured against
     * @return {@code value * portion / total}
     * @since 0.25.1
     */
    static Num proportional(Num value, Num portion, Num total) {
        Num product = value.multipliedBy(portion);
        if (Num.isFinite(product)) {
            Num result = product.dividedBy(total);
            if (Num.isFinite(result) && (!result.isZero() || value.isZero() || portion.isZero())) {
                return result;
            }
        }
        Num result = value.dividedBy(total).multipliedBy(portion);
        if (Num.isFinite(result) && (!result.isZero() || value.isZero() || portion.isZero())) {
            return result;
        }
        return portion.dividedBy(total).multipliedBy(value);
    }

    private static void sumFillFees(SettlementAmountSupport.CompensatedSum total, Trade trade, int finalIndex) {
        for (TradeFill fill : executedFills(trade, finalIndex)) {
            total.add(fill.fee());
        }
    }

    private static Num entryBasis(Trade entry, FuturesContract contract, NumFactory numFactory,
            List<TradeFill> entryFills) {
        List<TradeFill> allEntryFills = Trade.executionFillsOf(entry);
        if (entryFills.isEmpty() || entryFills.size() == allEntryFills.size()) {
            return entry.getPricePerAsset();
        }
        Num totalAmount = numFactory.zero();
        Num quoteWeightedPrice = numFactory.zero();
        Num quotePriceSum = numFactory.zero();
        for (TradeFill fill : entryFills) {
            FillSlice slice = fillSlice(fill, numFactory);
            totalAmount = totalAmount.plus(slice.amount());
            quoteWeightedPrice = quoteWeightedPrice.plus(slice.price().multipliedBy(slice.amount()));
            if (contract.settlementType() == FuturesContract.SettlementType.INVERSE) {
                quotePriceSum = quotePriceSum.plus(slice.amount().dividedBy(slice.price()));
            }
        }
        return contract.settlementType() == FuturesContract.SettlementType.INVERSE
                ? totalAmount.dividedBy(quotePriceSum)
                : quoteWeightedPrice.dividedBy(totalAmount);
    }

    private static FillSlice fillSlice(TradeFill fill, NumFactory numFactory) {
        return new FillSlice(numFactory.numOf(fill.price().getDelegate()),
                numFactory.numOf(fill.amount().getDelegate()), fill.index(), fill.time());
    }

    private record ExposureEvent(FillSlice slice, boolean entry) {
    }

    private record FillSlice(Num price, Num amount, int index, Instant time) {
    }

    private static Num sum(Position position, FuturesCashFlow.Type type, int finalIndex) {
        List<FuturesCashFlow> cashFlows = position.getCashFlows();
        Num total = position.getEntry().getPricePerAsset().getNumFactory().zero();
        for (FuturesCashFlow cashFlow : cashFlows) {
            if (cashFlow.type() != type || cashFlow.index() > finalIndex) {
                continue;
            }
            Num amount = cashFlow.settlementAmount() == null ? cashFlow.amount() : cashFlow.settlementAmount();
            Num normalizedAmount = total.getNumFactory().numOf(amount.getDelegate());
            FuturesValidation.requireFinite(normalizedAmount, "cash flow settlement amount");
            if (normalizedAmount.isZero() && !amount.isZero()) {
                throw new IllegalArgumentException(
                        "cash flow settlement amount cannot be represented in position number factory");
            }
            Num nextTotal = total.plus(normalizedAmount);
            FuturesValidation.requireFinite(nextTotal, "cash flow settlement total");
            total = nextTotal;
        }
        return total;
    }
}

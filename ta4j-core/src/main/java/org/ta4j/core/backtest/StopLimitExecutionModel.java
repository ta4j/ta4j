/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.ta4j.core.AggregatedTrade;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradeFill;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

/**
 * Stop-limit execution model with partial fill progression.
 *
 * <p>
 * Strategy signals place stop-limit orders. Pending orders are evaluated on
 * each bar through {@link #onBar(int, TradingRecord, BarSeries)} and can be
 * filled progressively based on available bar volume participation. Partially
 * filled entry orders are committed on expiry; partially filled exit orders are
 * rejected to keep position accounting consistent with single-entry/single-exit
 * trading records.
 * </p>
 *
 * @since 0.22.2
 */
public class StopLimitExecutionModel implements TradeExecutionModel {

    /**
     * Base price source used when creating stop/limit levels from a signal.
     *
     * @since 0.22.2
     */
    public enum ReferencePrice {
        /** Use the current bar close. */
        CURRENT_CLOSE,
        /** Use the next bar open. */
        NEXT_OPEN
    }

    private final Num stopTriggerRatio;
    private final Num limitOffsetRatio;
    private final Num maxBarParticipationRate;
    private final int maxBarsToFill;
    private final ReferencePrice referencePrice;

    private final Map<TradingRecord, PendingOrder> pendingOrders = new IdentityHashMap<>();
    private final Map<TradingRecord, List<RejectedOrder>> rejectedOrders = new IdentityHashMap<>();

    /**
     * Creates a stop-limit execution model using next-bar open as the reference
     * price.
     *
     * @param stopTriggerRatio    stop trigger ratio
     * @param limitOffsetRatio    limit offset ratio
     * @param maxBarParticipation max per-bar fill participation in (0,1]
     * @param maxBarsToFill       order time-to-live in bars (>= 1)
     * @since 0.22.2
     */
    public StopLimitExecutionModel(Num stopTriggerRatio, Num limitOffsetRatio, Num maxBarParticipation,
            int maxBarsToFill) {
        this(stopTriggerRatio, limitOffsetRatio, maxBarParticipation, maxBarsToFill, ReferencePrice.NEXT_OPEN);
    }

    /**
     * Creates a stop-limit execution model.
     *
     * @param stopTriggerRatio    stop trigger ratio
     * @param limitOffsetRatio    limit offset ratio (must be >= stop ratio)
     * @param maxBarParticipation max per-bar fill participation in (0,1]
     * @param maxBarsToFill       order time-to-live in bars (>= 1)
     * @param referencePrice      base signal price source
     * @since 0.22.2
     */
    public StopLimitExecutionModel(Num stopTriggerRatio, Num limitOffsetRatio, Num maxBarParticipation,
            int maxBarsToFill, ReferencePrice referencePrice) {
        validateRatio(stopTriggerRatio, "stopTriggerRatio");
        validateRatio(limitOffsetRatio, "limitOffsetRatio");
        validateRatio(maxBarParticipation, "maxBarParticipation");
        Objects.requireNonNull(referencePrice, "referencePrice");
        Num one = stopTriggerRatio.getNumFactory().one();
        if (maxBarParticipation.isZero()) {
            throw new IllegalArgumentException("maxBarParticipation must be > 0");
        }
        if (maxBarParticipation.isGreaterThan(one)) {
            throw new IllegalArgumentException("maxBarParticipation must be <= 1");
        }
        if (limitOffsetRatio.isLessThan(stopTriggerRatio)) {
            throw new IllegalArgumentException("limitOffsetRatio must be >= stopTriggerRatio");
        }
        if (maxBarsToFill < 1) {
            throw new IllegalArgumentException("maxBarsToFill must be >= 1");
        }
        this.stopTriggerRatio = stopTriggerRatio;
        this.limitOffsetRatio = limitOffsetRatio;
        this.maxBarParticipationRate = maxBarParticipation;
        this.maxBarsToFill = maxBarsToFill;
        this.referencePrice = referencePrice;
    }

    /**
     * Returns rejected orders for a trading record.
     *
     * @param tradingRecord trading record
     * @return rejected orders
     * @since 0.22.2
     */
    public List<RejectedOrder> getRejectedOrders(TradingRecord tradingRecord) {
        List<RejectedOrder> rejected = rejectedOrders.get(tradingRecord);
        return rejected == null ? List.of() : List.copyOf(rejected);
    }

    /**
     * Returns the current pending order snapshot for the trading record.
     *
     * @param tradingRecord trading record
     * @return pending order snapshot
     * @since 0.22.2
     */
    public Optional<PendingOrderSnapshot> getPendingOrder(TradingRecord tradingRecord) {
        PendingOrder order = pendingOrders.get(tradingRecord);
        if (order == null) {
            return Optional.empty();
        }
        return Optional.of(order.snapshot());
    }

    @Override
    public void execute(int index, TradingRecord tradingRecord, BarSeries barSeries, Num amount) {
        Objects.requireNonNull(tradingRecord, "tradingRecord");
        Objects.requireNonNull(barSeries, "barSeries");
        if (amount == null || amount.isNaN() || amount.isZero() || amount.isNegative()) {
            Num requestedAmount = amountOrZero(amount, barSeries);
            addRejectedOrder(tradingRecord, new RejectedOrder(index, index, nextTradeType(tradingRecord),
                    requestedAmount, requestedAmount.getNumFactory().zero(), "Invalid requested amount"));
            return;
        }
        Num requestedAmount = resolveRequestedAmount(tradingRecord, amount);
        if (pendingOrders.containsKey(tradingRecord)) {
            PendingOrder pendingOrder = pendingOrders.get(tradingRecord);
            addRejectedOrder(tradingRecord,
                    new RejectedOrder(index, index, pendingOrder.tradeType, pendingOrder.requestedAmount,
                            pendingOrder.filledAmount, "Signal ignored while another stop-limit order is pending"));
            return;
        }
        ReferenceTarget referenceTarget = resolveReferenceTarget(index, barSeries);
        if (referenceTarget == null) {
            addRejectedOrder(tradingRecord,
                    new RejectedOrder(index, index, nextTradeType(tradingRecord), requestedAmount,
                            requestedAmount.getNumFactory().zero(),
                            "Unable to resolve reference bar for stop-limit order"));
            return;
        }

        TradeType tradeType = nextTradeType(tradingRecord);
        Num stopPrice = toStopPrice(referenceTarget.price, tradeType);
        Num limitPrice = toLimitPrice(referenceTarget.price, tradeType);
        pendingOrders.put(tradingRecord, new PendingOrder(index, referenceTarget.index, tradeType, requestedAmount,
                stopPrice, limitPrice, referenceTarget.index + maxBarsToFill - 1));
    }

    @Override
    public void onBar(int index, TradingRecord tradingRecord, BarSeries barSeries) {
        PendingOrder order = pendingOrders.get(tradingRecord);
        if (order == null || index < order.activationIndex) {
            return;
        }

        Bar bar = barSeries.getBar(index);
        if (!order.triggered) {
            order.triggered = triggerReached(order.tradeType, bar, order.stopPrice);
        }

        if (order.triggered && limitReachable(order.tradeType, bar, order.limitPrice)) {
            Num fillAmount = fillAmount(order.remainingAmount(), bar.getVolume());
            if (fillAmount.isPositive()) {
                order.recordFill(index, order.limitPrice, fillAmount);
            }
        }

        if (order.isCompletelyFilled()) {
            tradingRecord.operate(order.toTrade(tradingRecord));
            pendingOrders.remove(tradingRecord);
            return;
        }

        if (index >= order.expiryIndex) {
            if (order.hasAnyFill() && order.tradeType == tradingRecord.getStartingType()) {
                tradingRecord.operate(order.toTrade(tradingRecord));
            }
            addRejectedOrder(tradingRecord, order.toExpiryRejection(index));
            pendingOrders.remove(tradingRecord);
        }
    }

    private static void validateRatio(Num ratio, String name) {
        Objects.requireNonNull(ratio, name);
        if (ratio.isNaN() || ratio.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive or zero");
        }
    }

    private ReferenceTarget resolveReferenceTarget(int signalIndex, BarSeries barSeries) {
        if (referencePrice == ReferencePrice.CURRENT_CLOSE) {
            return new ReferenceTarget(signalIndex, barSeries.getBar(signalIndex).getClosePrice());
        }
        int referenceIndex = signalIndex + 1;
        if (referenceIndex > barSeries.getEndIndex()) {
            return null;
        }
        return new ReferenceTarget(referenceIndex, barSeries.getBar(referenceIndex).getOpenPrice());
    }

    private Num toStopPrice(Num reference, TradeType tradeType) {
        Num one = reference.getNumFactory().one();
        if (tradeType == TradeType.BUY) {
            return reference.multipliedBy(one.plus(stopTriggerRatio));
        }
        return reference.multipliedBy(one.minus(stopTriggerRatio));
    }

    private Num toLimitPrice(Num reference, TradeType tradeType) {
        Num one = reference.getNumFactory().one();
        if (tradeType == TradeType.BUY) {
            return reference.multipliedBy(one.plus(limitOffsetRatio));
        }
        return reference.multipliedBy(one.minus(limitOffsetRatio));
    }

    private Num fillAmount(Num remainingAmount, Num barVolume) {
        Num availableAmount = remainingAmount;
        if (!Num.isNaNOrNull(barVolume) && barVolume.isPositive()) {
            availableAmount = barVolume.multipliedBy(maxBarParticipationRate);
        }
        if (availableAmount.isNaN() || availableAmount.isNegativeOrZero()) {
            return remainingAmount.getNumFactory().zero();
        }
        if (availableAmount.isGreaterThan(remainingAmount)) {
            return remainingAmount;
        }
        return availableAmount;
    }

    private static boolean triggerReached(TradeType tradeType, Bar bar, Num stopPrice) {
        if (tradeType == TradeType.BUY) {
            return bar.getHighPrice().isGreaterThanOrEqual(stopPrice);
        }
        return bar.getLowPrice().isLessThanOrEqual(stopPrice);
    }

    private static boolean limitReachable(TradeType tradeType, Bar bar, Num limitPrice) {
        if (tradeType == TradeType.BUY) {
            return bar.getLowPrice().isLessThanOrEqual(limitPrice);
        }
        return bar.getHighPrice().isGreaterThanOrEqual(limitPrice);
    }

    private static TradeType nextTradeType(TradingRecord tradingRecord) {
        if (tradingRecord.isClosed()) {
            return tradingRecord.getStartingType();
        }
        return tradingRecord.getCurrentPosition().getEntry().getType().complementType();
    }

    private static Num amountOrZero(Num amount, BarSeries barSeries) {
        if (amount == null || amount.isNaN()) {
            return barSeries.numFactory().zero();
        }
        return amount;
    }

    private static Num resolveRequestedAmount(TradingRecord tradingRecord, Num defaultAmount) {
        if (tradingRecord.isClosed()) {
            return defaultAmount;
        }
        return tradingRecord.getCurrentPosition().getEntry().getAmount();
    }

    private void addRejectedOrder(TradingRecord tradingRecord, RejectedOrder rejection) {
        rejectedOrders.computeIfAbsent(tradingRecord, ignored -> new ArrayList<>()).add(rejection);
    }

    /**
     * Rejected stop-limit order metadata.
     *
     * @param signalIndex     strategy signal index
     * @param rejectionIndex  bar index where rejection happened
     * @param tradeType       trade side
     * @param requestedAmount requested amount
     * @param filledAmount    amount filled before rejection
     * @param reason          rejection reason
     * @since 0.22.2
     */
    public record RejectedOrder(int signalIndex, int rejectionIndex, TradeType tradeType, Num requestedAmount,
            Num filledAmount, String reason) {
    }

    /**
     * Snapshot of a pending stop-limit order.
     *
     * @param signalIndex     strategy signal index
     * @param activationIndex first index where order can execute
     * @param tradeType       order side
     * @param requestedAmount requested amount
     * @param filledAmount    filled amount
     * @param stopPrice       stop trigger price
     * @param limitPrice      limit price
     * @param expiryIndex     last fillable bar index
     * @param triggered       true if stop trigger was reached
     * @param fills           current fills
     * @since 0.22.2
     */
    public record PendingOrderSnapshot(int signalIndex, int activationIndex, TradeType tradeType, Num requestedAmount,
            Num filledAmount, Num stopPrice, Num limitPrice, int expiryIndex, boolean triggered,
            List<TradeFill> fills) {
    }

    private record ReferenceTarget(int index, Num price) {
    }

    private static final class PendingOrder {
        private final int signalIndex;
        private final int activationIndex;
        private final TradeType tradeType;
        private final Num requestedAmount;
        private final Num stopPrice;
        private final Num limitPrice;
        private final int expiryIndex;
        private boolean triggered;
        private Num filledAmount;
        private final List<TradeFill> fills;

        private PendingOrder(int signalIndex, int activationIndex, TradeType tradeType, Num requestedAmount,
                Num stopPrice, Num limitPrice, int expiryIndex) {
            this.signalIndex = signalIndex;
            this.activationIndex = activationIndex;
            this.tradeType = tradeType;
            this.requestedAmount = requestedAmount;
            this.stopPrice = stopPrice;
            this.limitPrice = limitPrice;
            this.expiryIndex = expiryIndex;
            this.triggered = false;
            this.filledAmount = requestedAmount.getNumFactory().zero();
            this.fills = new ArrayList<>();
        }

        private Num remainingAmount() {
            return requestedAmount.minus(filledAmount);
        }

        private void recordFill(int index, Num price, Num amount) {
            fills.add(new TradeFill(index, price, amount));
            filledAmount = filledAmount.plus(amount);
        }

        private boolean isCompletelyFilled() {
            return !requestedAmount.minus(filledAmount).isPositive();
        }

        private boolean hasAnyFill() {
            return filledAmount.isPositive();
        }

        private AggregatedTrade toTrade(TradingRecord tradingRecord) {
            return new AggregatedTrade(tradeType, List.copyOf(fills), tradingRecord.getTransactionCostModel());
        }

        private RejectedOrder toExpiryRejection(int rejectionIndex) {
            return new RejectedOrder(signalIndex, rejectionIndex, tradeType, requestedAmount, filledAmount,
                    "Stop-limit order expired before filling requested amount");
        }

        private PendingOrderSnapshot snapshot() {
            return new PendingOrderSnapshot(signalIndex, activationIndex, tradeType, requestedAmount, filledAmount,
                    stopPrice, limitPrice, expiryIndex, triggered, List.copyOf(fills));
        }
    }
}

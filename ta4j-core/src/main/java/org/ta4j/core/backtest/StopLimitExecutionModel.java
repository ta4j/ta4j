/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.ExecutionSide;
import org.ta4j.core.FuturesContract;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradeFill;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.backtest.TradeExecutionModel.ExecutionTarget;
import org.ta4j.core.num.Num;

/**
 * Stop-limit execution model with partial fill progression.
 *
 * <p>
 * Strategy signals place stop-limit orders. Pending orders are evaluated on
 * each bar through {@link #onBar(int, TradingRecord, BarSeries)} and can be
 * filled progressively based on available bar volume participation. Partially
 * filled orders are committed on expiry when the target trading record supports
 * partial-exit accounting. Generated fills include execution side and bar end
 * timestamps so backtest fills match live-fill metadata shape.
 * </p>
 *
 * <p>
 * This model is safe for concurrent use with distinct trading records: all
 * access to the internal pending/rejected order maps is synchronized, so
 * parallel strategy batches (e.g. {@link BacktestExecutor}) can share one model
 * instance without losing or corrupting orders. Weak-key semantics are
 * preserved: orders are removed on fill or expiry and stale keys are reclaimed
 * once their trading records become unreachable. A single trading record must
 * still be driven from one thread at a time.
 * </p>
 *
 * @since 0.22.4
 */
public class StopLimitExecutionModel implements TradeExecutionModel {

    private final Num stopTriggerRatio;
    private final Num limitOffsetRatio;
    private final Num maxBarParticipationRate;
    private final int maxBarsToFill;
    private final PriceSource priceSource;

    /**
     * Guards {@link #pendingOrders} and {@link #rejectedOrders}:
     * {@link WeakHashMap} is not thread-safe, so every structural map access is
     * synchronized on this lock.
     */
    private final Object stateLock = new Object();

    private final Map<TradingRecord, PendingOrder> pendingOrders = new WeakHashMap<>();
    private final Map<TradingRecord, List<RejectedOrder>> rejectedOrders = new WeakHashMap<>();

    /**
     * Creates a stop-limit execution model using next-bar open as the reference
     * price.
     *
     * @param stopTriggerRatio    stop trigger ratio in [0,1)
     * @param limitOffsetRatio    limit offset ratio in [0,1)
     * @param maxBarParticipation max per-bar fill participation in (0,1]
     * @param maxBarsToFill       order time-to-live in bars (>= 1)
     * @since 0.22.4
     */
    public StopLimitExecutionModel(Num stopTriggerRatio, Num limitOffsetRatio, Num maxBarParticipation,
            int maxBarsToFill) {
        this(validatedConfig(stopTriggerRatio, limitOffsetRatio, maxBarParticipation, maxBarsToFill,
                PriceSource.NEXT_OPEN));
    }

    /**
     * Creates a stop-limit execution model.
     *
     * @param stopTriggerRatio    stop trigger ratio in [0,1)
     * @param limitOffsetRatio    limit offset ratio in [0,1) (must be >= stop
     *                            ratio)
     * @param maxBarParticipation max per-bar fill participation in (0,1]
     * @param maxBarsToFill       order time-to-live in bars (>= 1)
     * @param priceSource         base signal price source
     * @since 0.22.4
     */
    public StopLimitExecutionModel(Num stopTriggerRatio, Num limitOffsetRatio, Num maxBarParticipation,
            int maxBarsToFill, PriceSource priceSource) {
        this(validatedConfig(stopTriggerRatio, limitOffsetRatio, maxBarParticipation, maxBarsToFill, priceSource));
    }

    private StopLimitExecutionModel(Config config) {
        this.stopTriggerRatio = config.stopTriggerRatio();
        this.limitOffsetRatio = config.limitOffsetRatio();
        this.maxBarParticipationRate = config.maxBarParticipation();
        this.maxBarsToFill = config.maxBarsToFill();
        this.priceSource = config.priceSource();
    }

    private static Config validatedConfig(Num stopTriggerRatio, Num limitOffsetRatio, Num maxBarParticipation,
            int maxBarsToFill, PriceSource priceSource) {
        Num validatedStopTriggerRatio = validateRatio(stopTriggerRatio, "stopTriggerRatio");
        Num validatedLimitOffsetRatio = validateRatio(limitOffsetRatio, "limitOffsetRatio");
        Num validatedMaxBarParticipation = validateRatio(maxBarParticipation, "maxBarParticipation");
        PriceSource validatedPriceSource = Objects.requireNonNull(priceSource, "priceSource");
        Num one = validatedStopTriggerRatio.getNumFactory().one();
        if (validatedStopTriggerRatio.isGreaterThanOrEqual(one)) {
            throw new IllegalArgumentException("stopTriggerRatio must be < 1");
        }
        if (validatedLimitOffsetRatio.isGreaterThanOrEqual(one)) {
            throw new IllegalArgumentException("limitOffsetRatio must be < 1");
        }
        if (validatedMaxBarParticipation.isZero()) {
            throw new IllegalArgumentException("maxBarParticipation must be > 0");
        }
        if (validatedMaxBarParticipation.isGreaterThan(one)) {
            throw new IllegalArgumentException("maxBarParticipation must be <= 1");
        }
        if (validatedLimitOffsetRatio.isLessThan(validatedStopTriggerRatio)) {
            throw new IllegalArgumentException("limitOffsetRatio must be >= stopTriggerRatio");
        }
        if (maxBarsToFill < 1) {
            throw new IllegalArgumentException("maxBarsToFill must be >= 1");
        }
        return new Config(validatedStopTriggerRatio, validatedLimitOffsetRatio, validatedMaxBarParticipation,
                maxBarsToFill, validatedPriceSource);
    }

    /**
     * Returns rejected orders for a trading record.
     *
     * @param tradingRecord trading record
     * @return rejected orders
     * @since 0.22.4
     */
    public List<RejectedOrder> getRejectedOrders(TradingRecord tradingRecord) {
        synchronized (stateLock) {
            List<RejectedOrder> rejected = rejectedOrders.get(tradingRecord);
            return rejected == null ? List.of() : List.copyOf(rejected);
        }
    }

    /**
     * Returns the current pending order snapshot for the trading record.
     *
     * @param tradingRecord trading record
     * @return pending order snapshot
     * @since 0.22.4
     */
    public Optional<PendingOrderSnapshot> getPendingOrder(TradingRecord tradingRecord) {
        synchronized (stateLock) {
            PendingOrder order = pendingOrders.get(tradingRecord);
            if (order == null) {
                return Optional.empty();
            }
            return Optional.of(order.snapshot());
        }
    }

    @Override
    public ExecutionTarget estimateEntryTarget(int signalIndex, BarSeries barSeries, TradeType tradeType) {
        ExecutionTarget referenceTarget = ExecutionModelSupport.resolveExecutionTarget(signalIndex, barSeries,
                priceSource);
        if (referenceTarget == null) {
            return null;
        }
        return activationTarget(referenceTarget, barSeries, tradeType);
    }

    @Override
    public void execute(int index, TradingRecord tradingRecord, BarSeries barSeries, Num amount) {
        Objects.requireNonNull(tradingRecord, "tradingRecord");
        Objects.requireNonNull(barSeries, "barSeries");
        expireIfStale(index, tradingRecord);
        if (amount == null || amount.isNaN() || amount.isZero() || amount.isNegative()) {
            Num requestedAmount = amountOrZero(amount, barSeries);
            addRejectedOrder(tradingRecord,
                    new RejectedOrder(index, index, ExecutionModelSupport.nextTradeType(tradingRecord), requestedAmount,
                            requestedAmount.getNumFactory().zero(), "Invalid requested amount"));
            return;
        }
        Num requestedAmount = resolveRequestedAmount(tradingRecord, amount);
        PendingOrder pendingOrder = pendingOrderOf(tradingRecord);
        if (pendingOrder != null && !cancelUnfilledFuturesRemainder(index, tradingRecord, pendingOrder)) {
            addRejectedOrder(tradingRecord,
                    new RejectedOrder(index, index, pendingOrder.tradeType, requestedAmount,
                            requestedAmount.getNumFactory().zero(),
                            "Signal ignored while another stop-limit order is pending"));
            return;
        }
        ExecutionTarget referenceTarget = ExecutionModelSupport.resolveExecutionTarget(index, barSeries, priceSource);
        if (referenceTarget == null) {
            addRejectedOrder(tradingRecord,
                    new RejectedOrder(index, index, ExecutionModelSupport.nextTradeType(tradingRecord), requestedAmount,
                            requestedAmount.getNumFactory().zero(),
                            "Unable to resolve reference bar for stop-limit order"));
            return;
        }

        TradeType tradeType = ExecutionModelSupport.nextTradeType(tradingRecord);
        Num stopPrice = toStopPrice(referenceTarget.price(), tradeType);
        Num limitPrice = toLimitPrice(referenceTarget.price(), tradeType);
        ExecutionTarget activation = activationTarget(referenceTarget, barSeries, tradeType);
        if (activation == null) {
            addRejectedOrder(tradingRecord, new RejectedOrder(index, index, tradeType, requestedAmount,
                    requestedAmount.getNumFactory().zero(), "Unable to resolve activation bar for stop-limit order"));
            return;
        }
        if (!isCompleteClose(tradingRecord, requestedAmount)) {
            FuturesOrderQuantitySupport.requireTradable(tradingRecord.getFuturesContract(), requestedAmount,
                    activation.price());
        }
        putPendingOrder(tradingRecord, new PendingOrder(index, activation.index(), tradeType, requestedAmount,
                stopPrice, limitPrice, expiryIndex(activation.index(), maxBarsToFill)));
    }

    private boolean isCompleteClose(TradingRecord tradingRecord, Num requestedAmount) {
        if (tradingRecord.getFuturesContract() == null || tradingRecord.getCurrentPosition() == null
                || tradingRecord.getCurrentPosition().getEntry() == null
                || tradingRecord.getCurrentPosition().isClosed()) {
            return false;
        }
        Num openAmount = tradingRecord.getCurrentPosition().getEntry().getAmount();
        return requestedAmount.isEqual(openAmount);
    }

    @Override
    public void onBar(int index, TradingRecord tradingRecord, BarSeries barSeries) {
        PendingOrder order = pendingOrderOf(tradingRecord);
        if (order == null || index < order.activationIndex) {
            return;
        }

        Bar bar = barSeries.getBar(index);
        if (!order.triggered) {
            order.triggered = triggerReached(order.tradeType, bar, order.stopPrice);
        }

        FuturesContract futuresContract = tradingRecord.getFuturesContract();
        if (order.triggered && limitReachable(order.tradeType, bar, order.limitPrice)) {
            Num fillAmount = fillAmount(order.remainingAmount(), bar.getVolume(), futuresContract);
            if (fillAmount.isPositive()) {
                // Commit the fill to the record before booking it on the pending
                // order, so a rejected fill leaves the pending order unbooked.
                TradeFill fill = order.toFill(index, bar, order.limitPrice, fillAmount, futuresContract);
                if (futuresContract != null) {
                    tradingRecord.operate(fill);
                }
                order.recordFill(fill, fillAmount, futuresContract);
            }
        }

        if (order.isCompletelyFilled()) {
            if (futuresContract == null) {
                tradingRecord.operate(order.toTrade(tradingRecord));
            }
            removePendingOrder(tradingRecord);
            return;
        }

        if (index >= order.expiryIndex) {
            expireOrder(index, tradingRecord, order);
        }
    }

    @Override
    public void onRunEnd(int lastProcessedIndex, TradingRecord tradingRecord) {
        PendingOrder order = pendingOrderOf(tradingRecord);
        if (order == null) {
            return;
        }
        expireOrder(lastProcessedIndex, tradingRecord, order);
    }

    private void expireIfStale(int index, TradingRecord tradingRecord) {
        PendingOrder order = pendingOrderOf(tradingRecord);
        if (order == null || index <= order.expiryIndex) {
            return;
        }
        expireOrder(index, tradingRecord, order);
    }

    private void expireOrder(int index, TradingRecord tradingRecord, PendingOrder order) {
        if (shouldCommitPartial(order, tradingRecord)) {
            tradingRecord.operate(order.toTrade(tradingRecord));
        }
        addRejectedOrder(tradingRecord, order.toExpiryRejection(index));
        removePendingOrder(tradingRecord);
    }

    private static boolean shouldCommitPartial(PendingOrder order, TradingRecord tradingRecord) {
        if (!order.hasUnbookedFills()) {
            return false;
        }
        if (order.tradeType == tradingRecord.getStartingType()) {
            return true;
        }
        return !tradingRecord.getOpenPositions().isEmpty();
    }

    /**
     * Cancels the unfilled remainder of a pending futures entry when an opposing
     * signal arrives.
     *
     * <p>
     * Fills of a futures order are committed as they execute, so a pending entry
     * remainder would otherwise reopen exposure after the exit that the opposing
     * signal requests. Spot records keep their existing single-pending-order
     * rejection behavior.
     * </p>
     *
     * @param index         bar index of the opposing signal
     * @param tradingRecord record owning the pending order
     * @param order         pending order
     * @return {@code true} when the remainder was cancelled and the new signal may
     *         be processed
     */
    private boolean cancelUnfilledFuturesRemainder(int index, TradingRecord tradingRecord, PendingOrder order) {
        if (tradingRecord.getFuturesContract() == null) {
            return false;
        }
        if (order.tradeType == ExecutionModelSupport.nextTradeType(tradingRecord)) {
            return false;
        }
        if (order.remainingAmount().isPositive()) {
            addRejectedOrder(tradingRecord, new RejectedOrder(index, index, order.tradeType, order.remainingAmount(),
                    order.filledAmount, "Unfilled futures remainder cancelled by an opposing signal"));
        }
        removePendingOrder(tradingRecord);
        return true;
    }

    private static Num validateRatio(Num ratio, String name) {
        Num validatedRatio = Objects.requireNonNull(ratio, name);
        if (validatedRatio.isNaN() || validatedRatio.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive or zero");
        }
        return validatedRatio;
    }

    private int resolveActivationIndex(int referenceIndex) {
        if (priceSource == PriceSource.CURRENT_CLOSE) {
            return referenceIndex + 1;
        }
        return referenceIndex;
    }

    /**
     * Resolves the bar that activates a stop-limit order referenced at
     * {@code referenceTarget}, or {@code null} when no activation bar exists.
     *
     * <p>
     * {@link PriceSource#CURRENT_CLOSE} activates on the bar after the reference:
     * when the reference is the last representable index, that bar cannot exist and
     * a naive {@code referenceIndex + 1} would wrap to a negative index, defeating
     * the subsequent end-index check.
     * </p>
     *
     * @param referenceTarget resolved reference execution target
     * @param barSeries       series being executed
     * @param tradeType       trade direction of the pending order
     * @return activation target, or {@code null} when no activation bar exists
     */
    private ExecutionTarget activationTarget(ExecutionTarget referenceTarget, BarSeries barSeries,
            TradeType tradeType) {
        if (priceSource == PriceSource.CURRENT_CLOSE && referenceTarget.index() == Integer.MAX_VALUE) {
            return null;
        }
        int activationIndex = resolveActivationIndex(referenceTarget.index());
        if (activationIndex > barSeries.getEndIndex()) {
            return null;
        }
        return new ExecutionTarget(activationIndex, toLimitPrice(referenceTarget.price(), tradeType),
                barSeries.getBar(activationIndex).getEndTime());
    }

    /**
     * Computes the last bar on which a stop-limit order may be filled, using wider
     * arithmetic and clamping to {@link Integer#MAX_VALUE}. An activation near the
     * maximum index must not wrap the expiry index negative, which would expire the
     * order on its very first activation bar instead of keeping its configured
     * time-to-live. The expiry is deliberately not clamped to the series end:
     * bar-series managers may process raw bars past a constrained end to close open
     * positions.
     *
     * @param activationIndex activation bar index
     * @param maxBarsToFill   fillable bar count including the activation bar
     * @return clamped expiry index, never below the activation index
     */
    private static int expiryIndex(int activationIndex, int maxBarsToFill) {
        return (int) Math.min((long) activationIndex + maxBarsToFill - 1L, Integer.MAX_VALUE);
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

    private Num fillAmount(Num remainingAmount, Num barVolume, FuturesContract futuresContract) {
        Num availableAmount = remainingAmount;
        if (!Num.isNaNOrNull(barVolume)) {
            if (!barVolume.isPositive()) {
                return remainingAmount.getNumFactory().zero();
            }
            availableAmount = barVolume.multipliedBy(maxBarParticipationRate);
        }
        if (futuresContract != null) {
            // A simulated partial fill must honor the contract quantity
            // increment; an exchange-observed fill keeps its executed
            // quantity.
            availableAmount = FuturesOrderQuantitySupport.roundDown(futuresContract, availableAmount);
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
        Position currentPosition = tradingRecord.getCurrentPosition();
        if (currentPosition.isOpened() && currentPosition.getEntry() != null
                && currentPosition.getEntry().getAmount() != null && !currentPosition.getEntry().getAmount().isNaN()) {
            return currentPosition.getEntry().getAmount();
        }
        return defaultAmount;
    }

    private void addRejectedOrder(TradingRecord tradingRecord, RejectedOrder rejection) {
        synchronized (stateLock) {
            rejectedOrders.computeIfAbsent(tradingRecord, ignored -> new ArrayList<>()).add(rejection);
        }
    }

    private PendingOrder pendingOrderOf(TradingRecord tradingRecord) {
        synchronized (stateLock) {
            return pendingOrders.get(tradingRecord);
        }
    }

    private void putPendingOrder(TradingRecord tradingRecord, PendingOrder order) {
        synchronized (stateLock) {
            pendingOrders.put(tradingRecord, order);
        }
    }

    private void removePendingOrder(TradingRecord tradingRecord) {
        synchronized (stateLock) {
            pendingOrders.remove(tradingRecord);
        }
    }

    private record Config(Num stopTriggerRatio, Num limitOffsetRatio, Num maxBarParticipation, int maxBarsToFill,
            PriceSource priceSource) {
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
     * @since 0.22.4
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
     * @since 0.22.4
     */
    public record PendingOrderSnapshot(int signalIndex, int activationIndex, TradeType tradeType, Num requestedAmount,
            Num filledAmount, Num stopPrice, Num limitPrice, int expiryIndex, boolean triggered,
            List<TradeFill> fills) {
        public PendingOrderSnapshot {
            fills = List.copyOf(Objects.requireNonNull(fills, "fills must not be null"));
        }

        public List<TradeFill> fills() {
            return List.copyOf(fills);
        }
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
        private Num bookedAmount;
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
            this.bookedAmount = requestedAmount.getNumFactory().zero();
            this.fills = new ArrayList<>();
        }

        private Num remainingAmount() {
            return requestedAmount.minus(filledAmount);
        }

        private void recordFill(TradeFill fill, Num amount, FuturesContract futuresContract) {
            fills.add(fill);
            filledAmount = filledAmount.plus(amount);
            if (futuresContract != null) {
                bookedAmount = filledAmount;
            }
        }

        private TradeFill toFill(int index, Bar bar, Num price, Num amount, FuturesContract futuresContract) {
            if (futuresContract == null) {
                return new TradeFill(index, bar.getEndTime(), price, amount, sideOf(tradeType));
            }
            if (bar.getEndTime() == null) {
                throw new IllegalStateException("native futures execution requires bar timestamps but bar " + index
                        + " has none; use a timestamped bar series or a spot trading record");
            }
            return TradeFill.builder()
                    .index(index)
                    .time(bar.getEndTime())
                    .price(price)
                    .amount(amount)
                    .side(sideOf(tradeType))
                    .futuresContract(futuresContract)
                    .build();
        }

        private boolean isCompletelyFilled() {
            return !requestedAmount.minus(filledAmount).isPositive();
        }

        private boolean hasUnbookedFills() {
            return filledAmount.minus(bookedAmount).isPositive();
        }

        private Trade toTrade(TradingRecord tradingRecord) {
            return Trade.fromFills(tradeType, fills, tradingRecord.getTransactionCostModel());
        }

        private RejectedOrder toExpiryRejection(int rejectionIndex) {
            return new RejectedOrder(signalIndex, rejectionIndex, tradeType, requestedAmount, filledAmount,
                    "Stop-limit order expired before filling requested amount");
        }

        private PendingOrderSnapshot snapshot() {
            return new PendingOrderSnapshot(signalIndex, activationIndex, tradeType, requestedAmount, filledAmount,
                    stopPrice, limitPrice, expiryIndex, triggered, List.copyOf(fills));
        }

        private static ExecutionSide sideOf(TradeType tradeType) {
            if (tradeType == TradeType.BUY) {
                return ExecutionSide.BUY;
            }
            return ExecutionSide.SELL;
        }
    }
}

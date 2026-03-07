/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.io.Serial;
import java.time.Instant;
import java.util.Objects;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.RecordedTradeCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.num.Num;
import org.ta4j.core.utils.DeprecationNotifier;

/**
 * Deprecated live-trading compatibility facade.
 *
 * <p>
 * Use {@link BaseTradingRecord} for new code. This type remains available in
 * the 0.22.x line so existing adapters can migrate without a hard patch-line
 * break.
 * </p>
 *
 * @since 0.22.2
 */
@Deprecated(since = "0.22.4")
public class LiveTradingRecord extends BaseTradingRecord implements PositionLedger {

    @Serial
    private static final long serialVersionUID = 7960596064337713648L;

    /**
     * Creates a live trading record with BUY entries and FIFO matching.
     *
     * @since 0.22.2
     */
    public LiveTradingRecord() {
        this(TradeType.BUY);
    }

    /**
     * Creates a live trading record.
     *
     * @param startingType entry trade type
     * @since 0.22.2
     */
    public LiveTradingRecord(TradeType startingType) {
        this(startingType, ExecutionMatchPolicy.FIFO, RecordedTradeCostModel.INSTANCE, new ZeroCostModel(), null, null);
    }

    /**
     * Creates a live trading record.
     *
     * @param startingType         entry trade type
     * @param matchPolicy          matching policy
     * @param transactionCostModel ignored in favor of recorded fees
     * @param holdingCostModel     holding cost model
     * @param startIndex           optional start index
     * @param endIndex             optional end index
     * @since 0.22.2
     */
    public LiveTradingRecord(TradeType startingType, ExecutionMatchPolicy matchPolicy, CostModel transactionCostModel,
            CostModel holdingCostModel, Integer startIndex, Integer endIndex) {
        super(startingType, matchPolicy, RecordedTradeCostModel.INSTANCE,
                holdingCostModel == null ? new ZeroCostModel() : holdingCostModel, startIndex, endIndex);
        warnDeprecated();
    }

    /**
     * Records a live trade using an auto-incremented trade index.
     *
     * @param trade live trade
     * @since 0.22.2
     */
    public void recordFill(LiveTrade trade) {
        super.recordFill(trade);
    }

    /**
     * Records a live trade using the provided trade index.
     *
     * @param index trade index
     * @param trade live trade
     * @since 0.22.2
     */
    public void recordFill(int index, LiveTrade trade) {
        super.recordFill(index, trade);
    }

    /**
     * Records a live fill using the deprecated {@link ExecutionFill} contract.
     *
     * @param fill execution fill
     * @since 0.22.2
     */
    public void recordExecutionFill(ExecutionFill fill) {
        Objects.requireNonNull(fill, "fill");
        int index = fill.index();
        LiveTrade trade;
        if (fill instanceof LiveTrade liveTrade) {
            trade = liveTrade;
        } else {
            ExecutionSide side = resolveExecutionSide(fill.side());
            Instant time = fill.time() == null ? Instant.EPOCH : fill.time();
            Num fee = fill.fee();
            trade = new LiveTrade(index >= 0 ? index : 0, time, fill.price(), fill.amount(), fee, side, fill.orderId(),
                    fill.correlationId());
        }
        if (index >= 0) {
            recordFill(index, trade);
        } else {
            recordFill(trade);
        }
    }

    /**
     * Rehydrates transient cost models after deserialization.
     *
     * <p>
     * Live trading records always use {@link RecordedTradeCostModel} for
     * transaction costs.
     * </p>
     *
     * @param holdingCostModel holding cost model, null defaults to
     *                         {@link ZeroCostModel}
     * @since 0.22.2
     */
    @Override
    public void rehydrate(CostModel holdingCostModel) {
        rehydrate(RecordedTradeCostModel.INSTANCE, holdingCostModel);
    }

    /**
     * Rehydrates transient cost models after deserialization.
     *
     * <p>
     * Live trading records always use {@link RecordedTradeCostModel} for
     * transaction costs. The supplied transaction cost model is ignored.
     * </p>
     *
     * @param transactionCostModel ignored in favor of recorded fees
     * @param holdingCostModel     holding cost model, null defaults to
     *                             {@link ZeroCostModel}
     * @since 0.22.2
     */
    @Override
    public void rehydrate(CostModel transactionCostModel, CostModel holdingCostModel) {
        super.rehydrate(RecordedTradeCostModel.INSTANCE, holdingCostModel);
    }

    private ExecutionSide resolveExecutionSide(ExecutionSide side) {
        if (side != null) {
            return side;
        }
        OpenPosition netOpenPosition = getNetOpenPosition();
        if (netOpenPosition == null || netOpenPosition.amount() == null || netOpenPosition.amount().isZero()) {
            return getStartingType() == TradeType.BUY ? ExecutionSide.BUY : ExecutionSide.SELL;
        }
        return netOpenPosition.side() == ExecutionSide.BUY ? ExecutionSide.SELL : ExecutionSide.BUY;
    }

    private static void warnDeprecated() {
        DeprecationNotifier.warnOnce(LiveTradingRecord.class, "org.ta4j.core.BaseTradingRecord");
    }
}

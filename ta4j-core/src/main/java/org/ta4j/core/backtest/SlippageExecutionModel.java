/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import java.util.Objects;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.backtest.ExecutionModelSupport.ExecutionTarget;
import org.ta4j.core.backtest.ExecutionModelSupport.PriceSource;
import org.ta4j.core.num.Num;

/**
 * Execution model that applies configurable slippage to each trade.
 *
 * <p>
 * Buy orders are filled at a worse price ({@code +slippage}), while sell orders
 * are filled at a worse price ({@code -slippage}).
 * </p>
 *
 * @since 0.22.4
 */
public class SlippageExecutionModel implements TradeExecutionModel {

    /**
     * Base price source used before applying slippage.
     *
     * @since 0.22.4
     */
    public enum ExecutionPrice {
        /** Use the current bar close price. */
        CURRENT_CLOSE,
        /** Use the next bar open price. */
        NEXT_OPEN
    }

    private final Num slippageRatio;
    private final ExecutionPrice executionPrice;

    /**
     * Creates a slippage execution model based on next-bar open prices.
     *
     * @param slippageRatio slippage ratio (for example 0.001 for 10 bps)
     * @since 0.22.4
     */
    public SlippageExecutionModel(Num slippageRatio) {
        this(slippageRatio, ExecutionPrice.NEXT_OPEN);
    }

    /**
     * Creates a slippage execution model.
     *
     * @param slippageRatio  slippage ratio in [0,1)
     * @param executionPrice base price source
     * @since 0.22.4
     */
    public SlippageExecutionModel(Num slippageRatio, ExecutionPrice executionPrice) {
        Objects.requireNonNull(slippageRatio, "slippageRatio");
        Objects.requireNonNull(executionPrice, "executionPrice");
        if (slippageRatio.isNaN() || slippageRatio.isNegative()) {
            throw new IllegalArgumentException("slippageRatio must be positive or zero");
        }
        Num one = slippageRatio.getNumFactory().one();
        if (slippageRatio.isGreaterThanOrEqual(one)) {
            throw new IllegalArgumentException("slippageRatio must be less than 1");
        }
        this.slippageRatio = slippageRatio;
        this.executionPrice = executionPrice;
    }

    /**
     * @return configured slippage ratio
     * @since 0.22.4
     */
    public Num getSlippageRatio() {
        return slippageRatio;
    }

    /**
     * @return configured base execution price source
     * @since 0.22.4
     */
    public ExecutionPrice getExecutionPrice() {
        return executionPrice;
    }

    @Override
    public void execute(int index, TradingRecord tradingRecord, BarSeries barSeries, Num amount) {
        ExecutionTarget executionTarget = ExecutionModelSupport.resolveExecutionTarget(index, barSeries,
                toPriceSource(executionPrice));
        if (executionTarget == null) {
            return;
        }
        TradeType tradeType = ExecutionModelSupport.nextTradeType(tradingRecord);
        Num slippedPrice = applySlippage(executionTarget.price(), tradeType, slippageRatio);
        tradingRecord.operate(executionTarget.index(), slippedPrice, amount);
    }

    private static PriceSource toPriceSource(ExecutionPrice executionPrice) {
        if (executionPrice == ExecutionPrice.CURRENT_CLOSE) {
            return PriceSource.CURRENT_CLOSE;
        }
        return PriceSource.NEXT_OPEN;
    }

    private static Num applySlippage(Num price, TradeType tradeType, Num slippageRatio) {
        Num one = price.getNumFactory().one();
        if (tradeType == TradeType.BUY) {
            return price.multipliedBy(one.plus(slippageRatio));
        }
        return price.multipliedBy(one.minus(slippageRatio));
    }
}

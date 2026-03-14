/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import java.util.Objects;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.backtest.ExecutionModelSupport.ExecutionTarget;
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

    private final Num slippageRatio;
    private final PriceSource priceSource;

    /**
     * Creates a slippage execution model based on next-bar open prices.
     *
     * @param slippageRatio slippage ratio (for example 0.001 for 10 bps)
     * @since 0.22.4
     */
    public SlippageExecutionModel(Num slippageRatio) {
        this(slippageRatio, PriceSource.NEXT_OPEN);
    }

    /**
     * Creates a slippage execution model.
     *
     * @param slippageRatio slippage ratio in [0,1)
     * @param priceSource   base price source
     * @since 0.22.4
     */
    public SlippageExecutionModel(Num slippageRatio, PriceSource priceSource) {
        Objects.requireNonNull(slippageRatio, "slippageRatio");
        Objects.requireNonNull(priceSource, "priceSource");
        if (slippageRatio.isNaN() || slippageRatio.isNegative()) {
            throw new IllegalArgumentException("slippageRatio must be positive or zero");
        }
        Num one = slippageRatio.getNumFactory().one();
        if (slippageRatio.isGreaterThanOrEqual(one)) {
            throw new IllegalArgumentException("slippageRatio must be less than 1");
        }
        this.slippageRatio = slippageRatio;
        this.priceSource = priceSource;
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
    public PriceSource getPriceSource() {
        return priceSource;
    }

    @Override
    public void execute(int index, TradingRecord tradingRecord, BarSeries barSeries, Num amount) {
        ExecutionTarget executionTarget = ExecutionModelSupport.resolveExecutionTarget(index, barSeries, priceSource);
        if (executionTarget == null) {
            return;
        }
        TradeType tradeType = ExecutionModelSupport.nextTradeType(tradingRecord);
        Num slippedPrice = applySlippage(executionTarget.price(), tradeType, slippageRatio);
        tradingRecord.operate(executionTarget.index(), slippedPrice, amount);
    }

    private static Num applySlippage(Num price, TradeType tradeType, Num slippageRatio) {
        Num one = price.getNumFactory().one();
        if (tradeType == TradeType.BUY) {
            return price.multipliedBy(one.plus(slippageRatio));
        }
        return price.multipliedBy(one.minus(slippageRatio));
    }
}

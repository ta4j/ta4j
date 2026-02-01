/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import com.google.gson.Gson;
import java.io.Serial;
import java.time.Instant;
import java.util.Objects;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.num.Num;

/**
 * Live trade for live trading record updates.
 *
 * @param index         trade index
 * @param time          execution timestamp (UTC)
 * @param price         execution price per asset
 * @param amount        execution amount
 * @param fee           execution fee (nullable, defaults to zero)
 * @param side          execution side (BUY/SELL)
 * @param orderId       optional order identifier
 * @param correlationId optional correlation identifier
 * @since 0.22.2
 */
public record LiveTrade(int index, Instant time, Num price, Num amount, Num fee, ExecutionSide side, String orderId,
        String correlationId) implements Trade {

    @Serial
    private static final long serialVersionUID = 3196554864123769210L;

    private static final Gson GSON = new Gson();

    private static final CostModel NO_COST_MODEL = new ZeroCostModel();

    public LiveTrade {
        if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0");
        }
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(side, "side");
        if (fee == null) {
            fee = price.getNumFactory().zero();
        }
    }

    /**
     * @return true when the fill has a non-zero fee
     * @since 0.22.2
     */
    public boolean hasFee() {
        return !fee.isZero();
    }

    @Override
    public TradeType getType() {
        return side.toTradeType();
    }

    @Override
    public int getIndex() {
        return index;
    }

    @Override
    public Num getPricePerAsset() {
        return price;
    }

    @Override
    public Num getNetPrice() {
        if (amount.isZero()) {
            return price;
        }
        Num costPerAsset = fee.dividedBy(amount);
        if (side == ExecutionSide.BUY) {
            return price.plus(costPerAsset);
        }
        return price.minus(costPerAsset);
    }

    @Override
    public Num getAmount() {
        return amount;
    }

    @Override
    public Num getCost() {
        return fee;
    }

    @Override
    public CostModel getCostModel() {
        return NO_COST_MODEL;
    }

    /**
     * Returns a copy of this trade with a new index.
     *
     * @param index the trade index
     * @return a trade with the provided index
     * @since 0.22.2
     */
    public LiveTrade withIndex(int index) {
        return new LiveTrade(index, time, price, amount, fee, side, orderId, correlationId);
    }

    @Override
    public String toString() {
        return GSON.toJson(this);
    }
}

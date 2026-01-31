/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import com.google.gson.Gson;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import org.ta4j.core.num.Num;

/**
 * Execution fill for live trading record updates.
 *
 * @param time          execution timestamp (UTC)
 * @param price         execution price per asset
 * @param amount        execution amount
 * @param fee           execution fee (nullable, defaults to zero)
 * @param side          execution side (BUY/SELL)
 * @param orderId       optional order identifier
 * @param correlationId optional correlation identifier
 * @since 0.22.2
 */
public record ExecutionFill(Instant time, Num price, Num amount, Num fee, ExecutionSide side, String orderId,
        String correlationId) implements Serializable {

    @Serial
    private static final long serialVersionUID = 3196554864123769210L;

    private static final Gson GSON = new Gson();

    public ExecutionFill {
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
    public String toString() {
        return GSON.toJson(this);
    }
}

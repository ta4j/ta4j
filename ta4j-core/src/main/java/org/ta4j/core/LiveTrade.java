/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.Serial;
import java.time.Instant;
import org.ta4j.core.num.Num;

/**
 * Legacy live trade model retained for compatibility.
 *
 * <p>
 * Use {@link BaseTrade} for new code. This class now delegates to
 * {@link BaseTrade} and will be removed in a future release.
 * </p>
 *
 * @since 0.22.2
 * @deprecated since 0.22.4; use {@link BaseTrade}
 */
@Deprecated(since = "0.22.4", forRemoval = true)
public class LiveTrade extends BaseTrade {

    @Serial
    private static final long serialVersionUID = 3196554864123769210L;

    private static final Gson GSON = new Gson();

    /**
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
    public LiveTrade(int index, Instant time, Num price, Num amount, Num fee, ExecutionSide side, String orderId,
            String correlationId) {
        super(index, time, price, amount, fee, side, orderId, correlationId);
    }

    /**
     * @return trade index
     * @since 0.22.2
     */
    public int index() {
        return getIndex();
    }

    /**
     * @return true when the fill has a non-zero fee
     * @since 0.22.2
     */
    public boolean hasFee() {
        return !fee().isZero();
    }

    @Override
    public LiveTrade withIndex(int index) {
        return new LiveTrade(index, time(), price(), amount(), fee(), side(), orderId(), correlationId());
    }

    @Override
    public String toString() {
        JsonObject json = new JsonObject();
        json.addProperty("index", index());
        json.addProperty("time", time() == null ? null : time().toString());
        json.addProperty("price", price() == null ? null : price().toString());
        json.addProperty("amount", amount() == null ? null : amount().toString());
        json.addProperty("fee", fee() == null ? null : fee().toString());
        json.addProperty("side", side() == null ? null : side().name());
        json.addProperty("orderId", orderId());
        json.addProperty("correlationId", correlationId());
        return GSON.toJson(json);
    }
}

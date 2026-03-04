/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import org.ta4j.core.num.Num;

/**
 * Immutable execution fill used to represent partial trade executions.
 *
 * <p>
 * Metadata fields ({@code time}, {@code side}, IDs) are optional and may be
 * {@code null}. {@code fee} defaults to zero when omitted.
 * </p>
 *
 * @param index         bar index where the fill happened
 * @param time          optional execution timestamp (UTC)
 * @param price         execution price per asset
 * @param amount        executed amount
 * @param fee           optional execution fee (defaults to zero when null)
 * @param side          optional execution side
 * @param orderId       optional order id
 * @param correlationId optional correlation id
 *
 * @since 0.22.4
 */
public record TradeFill(int index, Instant time, Num price, Num amount, Num fee, ExecutionSide side, String orderId,
        String correlationId) implements ExecutionFill, Serializable {

    @Serial
    private static final long serialVersionUID = -258216480640174496L;

    /**
     * Creates a trade fill with scalar fields only.
     *
     * @param index  bar index where the fill happened
     * @param price  execution price per asset
     * @param amount executed amount
     * @since 0.22.4
     */
    public TradeFill(int index, Num price, Num amount) {
        this(index, null, price, amount, null, null, null, null);
    }

    /**
     * Creates a trade fill with execution side/time metadata.
     *
     * @param index  bar index where the fill happened
     * @param time   execution timestamp (UTC), nullable
     * @param price  execution price per asset
     * @param amount executed amount
     * @param side   execution side, nullable
     * @since 0.22.4
     */
    public TradeFill(int index, Instant time, Num price, Num amount, ExecutionSide side) {
        this(index, time, price, amount, null, side, null, null);
    }

    /**
     * Creates a trade fill.
     *
     * @throws NullPointerException if price or amount is null
     * @since 0.22.4
     */
    public TradeFill {
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(amount, "amount");
        if (fee == null) {
            fee = price.getNumFactory().zero();
        }
    }
}

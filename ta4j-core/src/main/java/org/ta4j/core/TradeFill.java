/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.util.Objects;
import org.ta4j.core.num.Num;

/**
 * Immutable execution fill used to represent partial trade executions.
 *
 * @param index  bar index where the fill happened
 * @param price  execution price per asset
 * @param amount executed amount
 *
 * @since 0.22.2
 */
public record TradeFill(int index, Num price, Num amount) {

    /**
     * Creates a trade fill.
     *
     * @throws NullPointerException if price or amount is null
     */
    public TradeFill {
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(amount, "amount");
    }
}

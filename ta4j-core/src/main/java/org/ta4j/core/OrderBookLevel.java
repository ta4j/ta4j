/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.io.Serializable;

import org.ta4j.core.num.Num;

/**
 * Immutable aggregated displayed quantity at a finite, possibly signed price.
 * Zero amount represents a normalized builder deletion; published snapshots
 * require positive amounts. Value equality follows the component Num values.
 *
 * @param price  finite price in quoted price units
 * @param amount finite nonnegative quantity in the book's declared unit
 * @since 0.27.1
 */
public record OrderBookLevel(Num price, Num amount) implements Serializable {
    /**
     * Validates finite price and nonnegative finite quantity.
     * 
     * @since 0.27.1
     */
    public OrderBookLevel {
        if (!Num.isFinite(price) || !Num.isFinite(amount) || amount.isNegative()) {
            throw new IllegalArgumentException("price must be finite and amount finite and nonnegative");
        }
    }
}

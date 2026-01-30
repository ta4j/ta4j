/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core;

import com.google.gson.Gson;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import org.ta4j.core.num.Num;

/**
 * Represents an open position lot in a live trading record.
 *
 * @since 0.22.2
 */
public final class PositionLot implements Serializable {

    @Serial
    private static final long serialVersionUID = 3650729320159324081L;

    private static final Gson GSON = new Gson();

    private final int entryIndex;
    private final Instant entryTime;
    private final Num entryPrice;
    private Num amount;
    private Num fee;
    private final String orderId;
    private final String correlationId;

    PositionLot(int entryIndex, Instant entryTime, Num entryPrice, Num amount, Num fee, String orderId,
            String correlationId) {
        Objects.requireNonNull(entryTime, "entryTime");
        Objects.requireNonNull(entryPrice, "entryPrice");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(fee, "fee");
        this.entryIndex = entryIndex;
        this.entryTime = entryTime;
        this.entryPrice = entryPrice;
        this.amount = amount;
        this.fee = fee;
        this.orderId = orderId;
        this.correlationId = correlationId;
    }

    /**
     * @return entry index
     * @since 0.22.2
     */
    public int entryIndex() {
        return entryIndex;
    }

    /**
     * @return entry time
     * @since 0.22.2
     */
    public Instant entryTime() {
        return entryTime;
    }

    /**
     * @return entry price
     * @since 0.22.2
     */
    public Num entryPrice() {
        return entryPrice;
    }

    /**
     * @return remaining amount
     * @since 0.22.2
     */
    public Num amount() {
        return amount;
    }

    /**
     * @return entry fee (remaining)
     * @since 0.22.2
     */
    public Num fee() {
        return fee;
    }

    /**
     * @return optional order id
     * @since 0.22.2
     */
    public String orderId() {
        return orderId;
    }

    /**
     * @return optional correlation id
     * @since 0.22.2
     */
    public String correlationId() {
        return correlationId;
    }

    PositionLot reduce(Num reduceAmount, Num reduceFee) {
        amount = amount.minus(reduceAmount);
        fee = fee.minus(reduceFee);
        return this;
    }

    PositionLot merge(PositionLot other) {
        Num totalAmount = amount.plus(other.amount);
        Num totalCost = entryPrice.multipliedBy(amount).plus(other.entryPrice.multipliedBy(other.amount));
        Num mergedPrice = totalCost.dividedBy(totalAmount);
        Num mergedFee = fee.plus(other.fee);
        int mergedIndex = Math.min(entryIndex, other.entryIndex);
        Instant mergedTime = entryTime.isBefore(other.entryTime) ? entryTime : other.entryTime;
        return new PositionLot(mergedIndex, mergedTime, mergedPrice, totalAmount, mergedFee, null, null);
    }

    @Override
    public String toString() {
        return GSON.toJson(this);
    }
}

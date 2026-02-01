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
 * Represents an open position lot in a live trading record.
 *
 * @since 0.22.2
 */
public final class PositionLot implements Serializable {

    @Serial
    private static final long serialVersionUID = 3650729320159324081L;

    private static final Gson GSON = new Gson();

    private final int entryIndex;
    private final long entrySequence;
    private final Instant entryTime;
    private final Num entryPrice;
    private Num amount;
    private Num fee;
    private final String orderId;
    private final String correlationId;

    PositionLot(int entryIndex, Instant entryTime, Num entryPrice, Num amount, Num fee, String orderId,
            String correlationId, long entrySequence) {
        Objects.requireNonNull(entryTime, "entryTime");
        Objects.requireNonNull(entryPrice, "entryPrice");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(fee, "fee");
        this.entryIndex = entryIndex;
        this.entrySequence = entrySequence;
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

    long entrySequence() {
        return entrySequence;
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
        long mergedSequence = Math.min(entrySequence, other.entrySequence);
        return new PositionLot(mergedIndex, mergedTime, mergedPrice, totalAmount, mergedFee, null, null,
                mergedSequence);
    }

    PositionLot snapshot() {
        return new PositionLot(entryIndex, entryTime, entryPrice, amount, fee, orderId, correlationId, entrySequence);
    }

    @Override
    public String toString() {
        return GSON.toJson(this);
    }
}

/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.ta4j.core.num.Num;

/**
 * Immutable funding boundary observation for a futures contract.
 *
 * <p>
 * A funding event names one rate at one reference price and one instant. It is
 * not a recurring schedule and not an already paid cash amount: the settling
 * cash flow is derived from the exposure eligible at {@link #time()} and
 * persisted as a {@link FuturesCashFlow}.
 * </p>
 *
 * @since 0.25.1
 */
public final class FuturesFunding implements Serializable {

    @Serial
    private static final long serialVersionUID = 3445097121827460362L;

    private final FuturesContract contract;
    private final String eventId;
    private final int index;
    private final Instant time;
    private final Num rate;
    private final Num referencePrice;
    private final String source;

    private FuturesFunding(Builder builder) {
        this.contract = FuturesValidation.requireNonNull(builder.contract, "contract");
        this.eventId = FuturesValidation.requireNonBlank(builder.eventId, "eventId");
        if (builder.index < 0) {
            throw new IllegalArgumentException("index must be nonnegative");
        }
        this.index = builder.index;
        this.time = FuturesValidation.requireNonNull(builder.time, "time");
        this.rate = FuturesValidation.requireFinite(builder.rate, "rate");
        this.referencePrice = FuturesValidation.requirePositiveFinite(builder.referencePrice, "referencePrice");
        this.source = FuturesValidation.requireNonBlankOrNull(builder.source, "source");
    }

    /**
     * @return new funding-event builder
     * @since 0.25.1
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * @return a builder pre-populated with this event
     * @since 0.25.1
     */
    public Builder toBuilder() {
        return new Builder(this);
    }

    /**
     * @return the contract the event applies to
     * @since 0.25.1
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "FuturesContract is a final value type whose instances are shared by reference; copying the immutable contract per accessor would allocate on every read")
    public FuturesContract contract() {
        return contract;
    }

    /**
     * @return the stable event identifier used for idempotent recording
     * @since 0.25.1
     */
    public String eventId() {
        return eventId;
    }

    /**
     * @return the logical bar index of the funding boundary
     * @since 0.25.1
     */
    public int index() {
        return index;
    }

    /**
     * @return the funding boundary instant
     * @since 0.25.1
     */
    public Instant time() {
        return time;
    }

    /**
     * @return the signed funding rate; a positive rate charges longs
     * @since 0.25.1
     */
    public Num rate() {
        return rate;
    }

    /**
     * @return the positive reference price the rate applies to
     * @since 0.25.1
     */
    public Num referencePrice() {
        return referencePrice;
    }

    /**
     * @return the data source, or {@code null}
     * @since 0.25.1
     */
    public String source() {
        return source;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FuturesFunding funding)) {
            return false;
        }
        return contract.equals(funding.contract) && eventId.equals(funding.eventId) && index == funding.index
                && time.equals(funding.time) && rate.isEqual(funding.rate)
                && referencePrice.isEqual(funding.referencePrice) && Objects.equals(source, funding.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contract, eventId, index, time, FuturesValidation.numHash(rate),
                FuturesValidation.numHash(referencePrice), source);
    }

    @Override
    public String toString() {
        return "FuturesFunding[eventId=" + eventId + ", contract=" + contract + ", index=" + index + ", time=" + time
                + ", rate=" + rate + ", referencePrice=" + referencePrice + "]";
    }

    /**
     * Builder for {@link FuturesFunding} values.
     *
     * @since 0.25.1
     */
    public static final class Builder {

        private FuturesContract contract;
        private String eventId;
        private int index = -1;
        private Instant time;
        private Num rate;
        private Num referencePrice;
        private String source;

        private Builder() {
        }

        private Builder(FuturesFunding source) {
            this.contract = source.contract;
            this.eventId = source.eventId;
            this.index = source.index;
            this.time = source.time;
            this.rate = source.rate;
            this.referencePrice = source.referencePrice;
            this.source = source.source;
        }

        @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "The builder stores the immutable FuturesContract by reference; the contract is never mutated after the value is built")
        public Builder contract(FuturesContract contract) {
            this.contract = contract;
            return this;
        }

        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder index(int index) {
            this.index = index;
            return this;
        }

        public Builder time(Instant time) {
            this.time = time;
            return this;
        }

        public Builder rate(Num rate) {
            this.rate = rate;
            return this;
        }

        public Builder referencePrice(Num referencePrice) {
            this.referencePrice = referencePrice;
            return this;
        }

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public FuturesFunding build() {
            return new FuturesFunding(this);
        }
    }
}

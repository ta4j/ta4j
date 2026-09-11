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
 * Immutable settled cash movement of a futures position.
 *
 * <p>
 * {@code amount} is credit-positive: a positive amount credits the position
 * account and a negative amount debits it. This is the opposite sign convention
 * from the charge-positive {@link TradeFee}; the two are never summed without
 * an explicit sign decision.
 * </p>
 *
 * <p>
 * The observed amount is authoritative and {@link #rate()} /
 * {@link #referencePrice()} are audit inputs only. {@code settlementAmount}
 * holds the amount resolved into the contract settlement currency, so
 * cross-currency values are never summed implicitly.
 * </p>
 *
 * @since 0.25.1
 */
public final class FuturesCashFlow implements Serializable {

    @Serial
    private static final long serialVersionUID = 6710321884955617308L;

    /**
     * Kind of settled cash movement.
     *
     * @since 0.25.1
     */
    public enum Type {

        /** Periodic funding credit or debit. */
        FUNDING,

        /** Variation-margin settlement of unrealized profit and loss. */
        VARIATION_MARGIN
    }

    private final FuturesContract contract;
    private final Type type;
    private final String eventId;
    private final int index;
    private final Instant time;
    private final Num amount;
    private final String currency;
    private final Num settlementAmount;
    private final Num rate;
    private final Num referencePrice;
    private final String source;

    private FuturesCashFlow(Builder builder) {
        this.contract = FuturesValidation.requireNonNull(builder.contract, "contract");
        this.type = Objects.requireNonNull(builder.type, "type");
        this.eventId = FuturesValidation.requireNonBlank(builder.eventId, "eventId");
        if (builder.index < 0) {
            throw new IllegalArgumentException("index must be nonnegative");
        }
        this.index = builder.index;
        this.time = FuturesValidation.requireNonNull(builder.time, "time");
        this.amount = FuturesValidation.requireFinite(builder.amount, "amount");
        this.currency = FuturesValidation.requireNonBlank(builder.currency, "currency");
        this.settlementAmount = SettlementAmountSupport.resolve(amount, currency, builder.settlementAmount,
                contract.settlementCurrency());
        this.rate = FuturesValidation.requireFiniteOrNull(builder.rate, "rate");
        this.referencePrice = FuturesValidation.requirePositiveFiniteOrNull(builder.referencePrice, "referencePrice");
        this.source = FuturesValidation.requireNonBlankOrNull(builder.source, "source");
    }

    /**
     * @return new cash-flow builder
     * @since 0.25.1
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * @return a builder pre-populated with this cash flow
     * @since 0.25.1
     */
    public Builder toBuilder() {
        return new Builder(this);
    }

    /**
     * @return the contract the cash flow belongs to
     * @since 0.25.1
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "FuturesContract is a final value type whose instances are shared by reference; copying the immutable contract per accessor would allocate on every read")
    public FuturesContract contract() {
        return contract;
    }

    /**
     * @return the cash-flow kind
     * @since 0.25.1
     */
    public Type type() {
        return type;
    }

    /**
     * @return the stable event identifier used for idempotent recording
     * @since 0.25.1
     */
    public String eventId() {
        return eventId;
    }

    /**
     * @return the logical bar index the cash flow is accounted at
     * @since 0.25.1
     */
    public int index() {
        return index;
    }

    /**
     * @return the effective instant of the cash flow
     * @since 0.25.1
     */
    public Instant time() {
        return time;
    }

    /**
     * @return the credit-positive amount in {@link #currency()}
     * @since 0.25.1
     */
    public Num amount() {
        return amount;
    }

    /**
     * @return the currency of {@link #amount()}
     * @since 0.25.1
     */
    public String currency() {
        return currency;
    }

    /**
     * @return the credit-positive amount resolved into the contract settlement
     *         currency
     * @since 0.25.1
     */
    public Num settlementAmount() {
        return settlementAmount;
    }

    /**
     * @return the funding rate used to derive this cash flow, or {@code null}
     * @since 0.25.1
     */
    public Num rate() {
        return rate;
    }

    /**
     * @return the reference price used to derive this cash flow, or {@code null}
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
        if (!(other instanceof FuturesCashFlow cashFlow)) {
            return false;
        }
        return contract.equals(cashFlow.contract) && type == cashFlow.type && eventId.equals(cashFlow.eventId)
                && index == cashFlow.index && time.equals(cashFlow.time) && amount.isEqual(cashFlow.amount)
                && currency.equals(cashFlow.currency) && settlementAmount.isEqual(cashFlow.settlementAmount)
                && FuturesValidation.numEqualsNullable(rate, cashFlow.rate)
                && FuturesValidation.numEqualsNullable(referencePrice, cashFlow.referencePrice)
                && Objects.equals(source, cashFlow.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contract, type, eventId, index, time, amount, currency, settlementAmount, rate,
                referencePrice, source);
    }

    @Override
    public String toString() {
        return "FuturesCashFlow[type=" + type + ", eventId=" + eventId + ", contract=" + contract + ", index=" + index
                + ", time=" + time + ", amount=" + amount + " " + currency + ", settlementAmount=" + settlementAmount
                + "]";
    }

    /**
     * Builder for {@link FuturesCashFlow} values.
     *
     * @since 0.25.1
     */
    public static final class Builder {

        private FuturesContract contract;
        private Type type;
        private String eventId;
        private int index = -1;
        private Instant time;
        private Num amount;
        private String currency;
        private Num settlementAmount;
        private Num rate;
        private Num referencePrice;
        private String source;

        private Builder() {
        }

        private Builder(FuturesCashFlow source) {
            this.contract = source.contract;
            this.type = source.type;
            this.eventId = source.eventId;
            this.index = source.index;
            this.time = source.time;
            this.amount = source.amount;
            this.currency = source.currency;
            this.settlementAmount = source.settlementAmount;
            this.rate = source.rate;
            this.referencePrice = source.referencePrice;
            this.source = source.source;
        }

        @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "The builder stores the immutable FuturesContract by reference; the contract is never mutated after the value is built")
        /**
         * Sets the futures contract for this cash flow.
         *
         * @param contract futures contract for this cash flow
         * @return this builder
         * @since 0.25.1
         */
        public Builder contract(FuturesContract contract) {
            this.contract = contract;
            return this;
        }

        /**
         * Sets the cash-flow type.
         *
         * @param type cash-flow type
         * @return this builder
         * @since 0.25.1
         */
        public Builder type(Type type) {
            this.type = type;
            return this;
        }

        /**
         * Sets the stable event identifier.
         *
         * @param eventId stable event identifier
         * @return this builder
         * @since 0.25.1
         */
        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        /**
         * Sets the logical bar index.
         *
         * @param index logical bar index
         * @return this builder
         * @since 0.25.1
         */
        public Builder index(int index) {
            this.index = index;
            return this;
        }

        /**
         * Sets the effective cash-flow instant.
         *
         * @param time effective cash-flow instant
         * @return this builder
         * @since 0.25.1
         */
        public Builder time(Instant time) {
            this.time = time;
            return this;
        }

        /**
         * Sets the credit-positive amount.
         *
         * @param amount credit-positive amount
         * @return this builder
         * @since 0.25.1
         */
        public Builder amount(Num amount) {
            this.amount = amount;
            return this;
        }

        /**
         * Sets the amount currency.
         *
         * @param currency amount currency
         * @return this builder
         * @since 0.25.1
         */
        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }

        /**
         * Sets the credit-positive settlement amount.
         *
         * @param settlementAmount credit-positive settlement amount
         * @return this builder
         * @since 0.25.1
         */
        public Builder settlementAmount(Num settlementAmount) {
            this.settlementAmount = settlementAmount;
            return this;
        }

        /**
         * Sets the funding rate.
         *
         * @param rate funding rate
         * @return this builder
         * @since 0.25.1
         */
        public Builder rate(Num rate) {
            this.rate = rate;
            return this;
        }

        /**
         * Sets the reference price.
         *
         * @param referencePrice reference price
         * @return this builder
         * @since 0.25.1
         */
        public Builder referencePrice(Num referencePrice) {
            this.referencePrice = referencePrice;
            return this;
        }

        /**
         * Sets the data source identifier.
         *
         * @param source data source identifier
         * @return this builder
         * @since 0.25.1
         */
        public Builder source(String source) {
            this.source = source;
            return this;
        }

        /**
         * Builds the immutable cash flow.
         *
         * @return the configured cash flow
         * @since 0.25.1
         */
        public FuturesCashFlow build() {
            return new FuturesCashFlow(this);
        }
    }
}

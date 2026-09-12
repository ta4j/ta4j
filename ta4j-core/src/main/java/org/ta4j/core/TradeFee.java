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
 * Immutable execution cost component attached to a {@link TradeFill}.
 *
 * <p>
 * {@code amount} is charge-positive: a negative amount is a rebate and is never
 * clamped to zero. The owning fill supplies the settlement currency; when
 * {@code currency} differs from it, {@code settlementAmount} carries the
 * explicit converted value and both values are preserved.
 * </p>
 *
 * <p>
 * Detail components such as exchange, clearing or regulatory charges are
 * modelled with {@link Type}; a modelled cost copies its schedule identity into
 * {@code source}/{@code scheduleAsOf} so provenance survives slicing and
 * serialization.
 * </p>
 *
 * @since 0.25.1
 */
public final class TradeFee implements Serializable {

    @Serial
    private static final long serialVersionUID = 7314885209166345511L;

    /**
     * Kind of execution cost component.
     *
     * @since 0.25.1
     */
    public enum Type {

        /** Broker or venue commission. */
        COMMISSION,

        /** Venue trading fee. */
        EXCHANGE,

        /** Clearing house fee. */
        CLEARING,

        /** Regulatory or settlement fee. */
        REGULATORY,

        /** Liquidation fee. */
        LIQUIDATION,

        /** Any other execution cost. */
        OTHER
    }

    private final Type type;
    private final Num amount;
    private final String currency;
    private final Num settlementAmount;
    private final String source;
    private final Instant scheduleAsOf;

    private TradeFee(Builder builder) {
        this.type = Objects.requireNonNull(builder.type, "type");
        this.amount = FuturesValidation.requireFinite(builder.amount, "amount");
        this.currency = FuturesValidation.requireNonBlank(builder.currency, "currency");
        this.settlementAmount = FuturesValidation.requireFiniteOrNull(builder.settlementAmount, "settlementAmount");
        this.source = FuturesValidation.requireNonBlankOrNull(builder.source, "source");
        this.scheduleAsOf = builder.scheduleAsOf;
        if (settlementAmount != null) {
            requireConsistentConversion(amount, settlementAmount);
        }
    }

    private static void requireConsistentConversion(Num amount, Num settlementAmount) {
        if (amount.isZero()) {
            if (!settlementAmount.isZero()) {
                throw new IllegalArgumentException("settlementAmount must be zero when amount is zero");
            }
            return;
        }
        if (!settlementAmount.isZero() && amount.isPositive() != settlementAmount.isPositive()) {
            throw new IllegalArgumentException("settlementAmount must not oppose the sign of amount");
        }
    }

    /**
     * @return new fee builder
     * @since 0.25.1
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * @return a builder pre-populated with this fee's components
     * @since 0.25.1
     */
    public Builder toBuilder() {
        return new Builder(this);
    }

    /**
     * @return the fee component kind
     * @since 0.25.1
     */
    public Type type() {
        return type;
    }

    /**
     * @return the charge-positive amount in {@link #currency()}
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
     * @return the amount converted into the owning fill's settlement currency, or
     *         {@code null} when the conversion has not been bound yet
     * @since 0.25.1
     */
    public Num settlementAmount() {
        return settlementAmount;
    }

    /**
     * @return provenance of a modelled fee, or {@code null}
     * @since 0.25.1
     */
    public String source() {
        return source;
    }

    /**
     * @return schedule timestamp of a modelled fee, or {@code null}
     * @since 0.25.1
     */
    public Instant scheduleAsOf() {
        return scheduleAsOf;
    }

    /**
     * Binds this fee to the settlement currency of its owning fill.
     *
     * @param settlementCurrency settlement currency of the owning fill
     * @return an equivalent fee carrying a resolved {@link #settlementAmount()}
     * @throws IllegalArgumentException when the conversion is missing, differing or
     *                                  sign-opposed
     * @since 0.25.1
     */
    TradeFee resolveFor(String settlementCurrency) {
        Num resolved = SettlementAmountSupport.resolve(amount, currency, settlementAmount, settlementCurrency);
        if (resolved == settlementAmount) {
            return this;
        }
        return toBuilder().settlementAmount(resolved).build();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TradeFee fee)) {
            return false;
        }
        return type == fee.type && FuturesValidation.numEqualsNullable(amount, fee.amount)
                && currency.equals(fee.currency)
                && FuturesValidation.numEqualsNullable(settlementAmount, fee.settlementAmount)
                && Objects.equals(source, fee.source) && Objects.equals(scheduleAsOf, fee.scheduleAsOf);
    }

    public int hashCode() {
        return Objects.hash(type, FuturesValidation.numHash(amount), currency,
                FuturesValidation.numHash(settlementAmount), source, scheduleAsOf);
    }

    @Override
    public String toString() {
        return "TradeFee[type=" + type + ", amount=" + amount + " " + currency + ", settlementAmount="
                + settlementAmount + ", source=" + source + ", scheduleAsOf=" + scheduleAsOf + "]";
    }

    /**
     * Builder for {@link TradeFee} values.
     *
     * @since 0.25.1
     */
    public static final class Builder {

        private Type type;
        private Num amount;
        private String currency;
        private Num settlementAmount;
        private String source;
        private Instant scheduleAsOf;

        private Builder() {
        }

        private Builder(TradeFee source) {
            this.type = source.type;
            this.amount = source.amount;
            this.currency = source.currency;
            this.settlementAmount = source.settlementAmount;
            this.source = source.source;
            this.scheduleAsOf = source.scheduleAsOf;
        }

        /**
         * Sets the fee type.
         *
         * @param type fee type
         * @return this builder
         * @since 0.25.1
         */
        public Builder type(Type type) {
            this.type = type;
            return this;
        }

        /**
         * Sets the charge-positive amount.
         *
         * @param amount charge-positive amount
         * @return this builder
         * @since 0.25.1
         */
        public Builder amount(Num amount) {
            this.amount = amount;
            return this;
        }

        /**
         * Sets the fee currency.
         *
         * @param currency fee currency
         * @return this builder
         * @since 0.25.1
         */
        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }

        /**
         * Sets the settlement-currency amount.
         *
         * @param settlementAmount settlement-currency amount
         * @return this builder
         * @since 0.25.1
         */
        public Builder settlementAmount(Num settlementAmount) {
            this.settlementAmount = settlementAmount;
            return this;
        }

        /**
         * Sets the fee source identifier.
         *
         * @param source fee source identifier
         * @return this builder
         * @since 0.25.1
         */
        public Builder source(String source) {
            this.source = source;
            return this;
        }

        /**
         * Sets the schedule effective time.
         *
         * @param scheduleAsOf schedule effective time
         * @return this builder
         * @since 0.25.1
         */
        public Builder scheduleAsOf(Instant scheduleAsOf) {
            this.scheduleAsOf = scheduleAsOf;
            return this;
        }

        /**
         * Builds the immutable fee.
         *
         * @return the configured fee
         * @since 0.25.1
         */
        public TradeFee build() {
            return new TradeFee(this);
        }
    }
}

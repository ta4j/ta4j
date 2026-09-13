/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.ta4j.core.num.Num;

/**
 * Immutable venue observation of a futures contract's market and risk state.
 *
 * <p>
 * Every field beyond the contract and the observation timestamp is optional and
 * stays {@code null} when the venue did not report it. A snapshot is therefore
 * never a fabricated zero-rate quote, and it is not a recurring funding
 * schedule: {@link #fundingRate()} describes one observed rate at
 * {@link #fundingTime()}.
 * </p>
 *
 * @since 0.25.1
 */
public final class FuturesMarketSnapshot implements Serializable {

    @Serial
    private static final long serialVersionUID = 2109447361203418855L;

    private final FuturesContract contract;
    private final Instant observedAt;
    private final String source;
    private final Num markPrice;
    private final Num indexPrice;
    private final Num settlementPrice;
    private final Num fundingRate;
    private final Instant fundingTime;
    private final Duration fundingInterval;
    private final Num openInterest;
    private final Num maxLeverage;
    private final Num intradayLongMarginRate;
    private final Num intradayShortMarginRate;
    private final Num overnightLongMarginRate;
    private final Num overnightShortMarginRate;
    private final Num maintenanceMarginRate;
    private final Map<String, String> attributes;

    private FuturesMarketSnapshot(Builder builder) {
        this.contract = FuturesValidation.requireNonNull(builder.contract, "contract");
        this.observedAt = FuturesValidation.requireNonNull(builder.observedAt, "observedAt");
        this.source = FuturesValidation.requireNonBlankOrNull(builder.source, "source");
        this.markPrice = FuturesValidation.requirePositiveFiniteOrNull(builder.markPrice, "markPrice");
        this.indexPrice = FuturesValidation.requirePositiveFiniteOrNull(builder.indexPrice, "indexPrice");
        this.settlementPrice = FuturesValidation.requirePositiveFiniteOrNull(builder.settlementPrice,
                "settlementPrice");
        this.fundingRate = FuturesValidation.requireFiniteOrNull(builder.fundingRate, "fundingRate");
        this.fundingTime = builder.fundingTime;
        this.fundingInterval = FuturesValidation.requirePositiveDurationOrNull(builder.fundingInterval,
                "fundingInterval");
        this.openInterest = FuturesValidation.requireNonNegativeFiniteOrNull(builder.openInterest, "openInterest");
        this.maxLeverage = FuturesValidation.requireNonNegativeFiniteOrNull(builder.maxLeverage, "maxLeverage");
        this.intradayLongMarginRate = FuturesValidation.requireNonNegativeFiniteOrNull(builder.intradayLongMarginRate,
                "intradayLongMarginRate");
        this.intradayShortMarginRate = FuturesValidation.requireNonNegativeFiniteOrNull(builder.intradayShortMarginRate,
                "intradayShortMarginRate");
        this.overnightLongMarginRate = FuturesValidation.requireNonNegativeFiniteOrNull(builder.overnightLongMarginRate,
                "overnightLongMarginRate");
        this.overnightShortMarginRate = FuturesValidation
                .requireNonNegativeFiniteOrNull(builder.overnightShortMarginRate, "overnightShortMarginRate");
        this.maintenanceMarginRate = FuturesValidation.requireNonNegativeFiniteOrNull(builder.maintenanceMarginRate,
                "maintenanceMarginRate");
        this.attributes = Map.copyOf(Objects.requireNonNull(builder.attributes, "attributes"));
    }

    /**
     * @return new snapshot builder
     * @since 0.25.1
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * @return a builder pre-populated with this observation
     * @since 0.25.1
     */
    public Builder toBuilder() {
        return new Builder(this);
    }

    /**
     * @return the observed contract
     * @since 0.25.1
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "FuturesContract is a final value type whose instances are shared by reference; copying the immutable contract per accessor would allocate on every read")
    public FuturesContract contract() {
        return contract;
    }

    /**
     * @return the instant the venue published this observation
     * @since 0.25.1
     */
    public Instant observedAt() {
        return observedAt;
    }

    /**
     * @return the data source, or {@code null}
     * @since 0.25.1
     */
    public String source() {
        return source;
    }

    /**
     * @return the mark price, or {@code null}
     * @since 0.25.1
     */
    public Num markPrice() {
        return markPrice;
    }

    /**
     * @return the underlying index price, or {@code null}
     * @since 0.25.1
     */
    public Num indexPrice() {
        return indexPrice;
    }

    /**
     * @return the settlement price, or {@code null}
     * @since 0.25.1
     */
    public Num settlementPrice() {
        return settlementPrice;
    }

    /**
     * @return the signed funding rate observed for {@link #fundingTime()}, or
     *         {@code null}
     * @since 0.25.1
     */
    public Num fundingRate() {
        return fundingRate;
    }

    /**
     * @return the instant the observed funding rate applies to, or {@code null}
     * @since 0.25.1
     */
    public Instant fundingTime() {
        return fundingTime;
    }

    /**
     * @return the observed funding interval, or {@code null}
     * @since 0.25.1
     */
    public Duration fundingInterval() {
        return fundingInterval;
    }

    /**
     * @return the open interest in contracts, or {@code null}
     * @since 0.25.1
     */
    public Num openInterest() {
        return openInterest;
    }

    /**
     * @return the venue's maximum leverage, or {@code null}
     * @since 0.25.1
     */
    public Num maxLeverage() {
        return maxLeverage;
    }

    /**
     * @return the intraday long margin rate, or {@code null}
     * @since 0.25.1
     */
    public Num intradayLongMarginRate() {
        return intradayLongMarginRate;
    }

    /**
     * @return the intraday short margin rate, or {@code null}
     * @since 0.25.1
     */
    public Num intradayShortMarginRate() {
        return intradayShortMarginRate;
    }

    /**
     * @return the overnight long margin rate, or {@code null}
     * @since 0.25.1
     */
    public Num overnightLongMarginRate() {
        return overnightLongMarginRate;
    }

    /**
     * @return the overnight short margin rate, or {@code null}
     * @since 0.25.1
     */
    public Num overnightShortMarginRate() {
        return overnightShortMarginRate;
    }

    /**
     * @return the maintenance margin rate, or {@code null}
     * @since 0.25.1
     */
    public Num maintenanceMarginRate() {
        return maintenanceMarginRate;
    }

    /**
     * @return immutable non-economic provider labels
     * @since 0.25.1
     */
    public Map<String, String> attributes() {
        return attributes;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FuturesMarketSnapshot snapshot)) {
            return false;
        }
        return contract.equals(snapshot.contract) && observedAt.equals(snapshot.observedAt)
                && Objects.equals(source, snapshot.source)
                && FuturesValidation.numEqualsNullable(markPrice, snapshot.markPrice)
                && FuturesValidation.numEqualsNullable(indexPrice, snapshot.indexPrice)
                && FuturesValidation.numEqualsNullable(settlementPrice, snapshot.settlementPrice)
                && FuturesValidation.numEqualsNullable(fundingRate, snapshot.fundingRate)
                && Objects.equals(fundingTime, snapshot.fundingTime)
                && Objects.equals(fundingInterval, snapshot.fundingInterval)
                && FuturesValidation.numEqualsNullable(openInterest, snapshot.openInterest)
                && FuturesValidation.numEqualsNullable(maxLeverage, snapshot.maxLeverage)
                && FuturesValidation.numEqualsNullable(intradayLongMarginRate, snapshot.intradayLongMarginRate)
                && FuturesValidation.numEqualsNullable(intradayShortMarginRate, snapshot.intradayShortMarginRate)
                && FuturesValidation.numEqualsNullable(overnightLongMarginRate, snapshot.overnightLongMarginRate)
                && FuturesValidation.numEqualsNullable(overnightShortMarginRate, snapshot.overnightShortMarginRate)
                && FuturesValidation.numEqualsNullable(maintenanceMarginRate, snapshot.maintenanceMarginRate)
                && attributes.equals(snapshot.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contract, observedAt, source, FuturesValidation.numHash(markPrice),
                FuturesValidation.numHash(indexPrice), FuturesValidation.numHash(settlementPrice),
                FuturesValidation.numHash(fundingRate), fundingTime, fundingInterval,
                FuturesValidation.numHash(openInterest), FuturesValidation.numHash(maxLeverage),
                FuturesValidation.numHash(intradayLongMarginRate), FuturesValidation.numHash(intradayShortMarginRate),
                FuturesValidation.numHash(overnightLongMarginRate), FuturesValidation.numHash(overnightShortMarginRate),
                FuturesValidation.numHash(maintenanceMarginRate), attributes);
    }

    @Override
    public String toString() {
        return "FuturesMarketSnapshot[contract=" + contract + ", observedAt=" + observedAt + ", markPrice=" + markPrice
                + ", indexPrice=" + indexPrice + ", fundingRate=" + fundingRate + "]";
    }

    /**
     * Builder for {@link FuturesMarketSnapshot} values.
     *
     * @since 0.25.1
     */
    public static final class Builder {

        private FuturesContract contract;
        private Instant observedAt;
        private String source;
        private Num markPrice;
        private Num indexPrice;
        private Num settlementPrice;
        private Num fundingRate;
        private Instant fundingTime;
        private Duration fundingInterval;
        private Num openInterest;
        private Num maxLeverage;
        private Num intradayLongMarginRate;
        private Num intradayShortMarginRate;
        private Num overnightLongMarginRate;
        private Num overnightShortMarginRate;
        private Num maintenanceMarginRate;
        private Map<String, String> attributes = Map.of();

        private Builder() {
        }

        private Builder(FuturesMarketSnapshot source) {
            this.contract = source.contract;
            this.observedAt = source.observedAt;
            this.source = source.source;
            this.markPrice = source.markPrice;
            this.indexPrice = source.indexPrice;
            this.settlementPrice = source.settlementPrice;
            this.fundingRate = source.fundingRate;
            this.fundingTime = source.fundingTime;
            this.fundingInterval = source.fundingInterval;
            this.openInterest = source.openInterest;
            this.maxLeverage = source.maxLeverage;
            this.intradayLongMarginRate = source.intradayLongMarginRate;
            this.intradayShortMarginRate = source.intradayShortMarginRate;
            this.overnightLongMarginRate = source.overnightLongMarginRate;
            this.overnightShortMarginRate = source.overnightShortMarginRate;
            this.maintenanceMarginRate = source.maintenanceMarginRate;
            this.attributes = source.attributes;
        }

        /**
         * Sets the futures contract.
         *
         * @param contract futures contract
         * @return this builder
         * @since 0.25.1
         */
        @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "The builder stores the immutable FuturesContract by reference; the contract is never mutated after the value is built")
        public Builder contract(FuturesContract contract) {
            this.contract = contract;
            return this;
        }

        /**
         * Sets the observation instant.
         *
         * @param observedAt observation instant
         * @return this builder
         * @since 0.25.1
         */
        public Builder observedAt(Instant observedAt) {
            this.observedAt = observedAt;
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
         * Sets the mark price.
         *
         * @param markPrice mark price
         * @return this builder
         * @since 0.25.1
         */
        public Builder markPrice(Num markPrice) {
            this.markPrice = markPrice;
            return this;
        }

        /**
         * Sets the index price.
         *
         * @param indexPrice index price
         * @return this builder
         * @since 0.25.1
         */
        public Builder indexPrice(Num indexPrice) {
            this.indexPrice = indexPrice;
            return this;
        }

        /**
         * Sets the settlement price.
         *
         * @param settlementPrice settlement price
         * @return this builder
         * @since 0.25.1
         */
        public Builder settlementPrice(Num settlementPrice) {
            this.settlementPrice = settlementPrice;
            return this;
        }

        /**
         * Sets the funding rate.
         *
         * @param fundingRate funding rate
         * @return this builder
         * @since 0.25.1
         */
        public Builder fundingRate(Num fundingRate) {
            this.fundingRate = fundingRate;
            return this;
        }

        /**
         * Sets the funding boundary instant.
         *
         * @param fundingTime funding boundary instant
         * @return this builder
         * @since 0.25.1
         */
        public Builder fundingTime(Instant fundingTime) {
            this.fundingTime = fundingTime;
            return this;
        }

        /**
         * Sets the funding interval.
         *
         * @param fundingInterval funding interval
         * @return this builder
         * @since 0.25.1
         */
        public Builder fundingInterval(Duration fundingInterval) {
            this.fundingInterval = fundingInterval;
            return this;
        }

        /**
         * Sets the open interest.
         *
         * @param openInterest open interest
         * @return this builder
         * @since 0.25.1
         */
        public Builder openInterest(Num openInterest) {
            this.openInterest = openInterest;
            return this;
        }

        /**
         * Sets the maximum leverage.
         *
         * @param maxLeverage maximum leverage
         * @return this builder
         * @since 0.25.1
         */
        public Builder maxLeverage(Num maxLeverage) {
            this.maxLeverage = maxLeverage;
            return this;
        }

        /**
         * Sets the intraday long margin rate.
         *
         * @param intradayLongMarginRate intraday long margin rate
         * @return this builder
         * @since 0.25.1
         */
        public Builder intradayLongMarginRate(Num intradayLongMarginRate) {
            this.intradayLongMarginRate = intradayLongMarginRate;
            return this;
        }

        /**
         * Sets the intraday short margin rate.
         *
         * @param intradayShortMarginRate intraday short margin rate
         * @return this builder
         * @since 0.25.1
         */
        public Builder intradayShortMarginRate(Num intradayShortMarginRate) {
            this.intradayShortMarginRate = intradayShortMarginRate;
            return this;
        }

        /**
         * Sets the overnight long margin rate.
         *
         * @param overnightLongMarginRate overnight long margin rate
         * @return this builder
         * @since 0.25.1
         */
        public Builder overnightLongMarginRate(Num overnightLongMarginRate) {
            this.overnightLongMarginRate = overnightLongMarginRate;
            return this;
        }

        /**
         * Sets the overnight short margin rate.
         *
         * @param overnightShortMarginRate overnight short margin rate
         * @return this builder
         * @since 0.25.1
         */
        public Builder overnightShortMarginRate(Num overnightShortMarginRate) {
            this.overnightShortMarginRate = overnightShortMarginRate;
            return this;
        }

        /**
         * Sets the maintenance margin rate.
         *
         * @param maintenanceMarginRate maintenance margin rate
         * @return this builder
         * @since 0.25.1
         */
        public Builder maintenanceMarginRate(Num maintenanceMarginRate) {
            this.maintenanceMarginRate = maintenanceMarginRate;
            return this;
        }

        /**
         * Sets additional snapshot attributes.
         *
         * @param attributes additional snapshot attributes
         * @return this builder
         * @since 0.25.1
         */
        public Builder attributes(Map<String, String> attributes) {
            this.attributes = attributes == null ? Map.of() : attributes;
            return this;
        }

        /**
         * Builds the immutable market snapshot.
         *
         * @return this builder
         * @since 0.25.1
         * @return the configured market snapshot
         * @since 0.25.1
         */
        public FuturesMarketSnapshot build() {
            return new FuturesMarketSnapshot(this);
        }
    }
}

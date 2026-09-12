/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.ta4j.core.num.Num;

/**
 * Immutable venue observation of a futures position as the exchange reports it.
 *
 * <p>
 * These are observed account values, not computed native economics. Recording a
 * snapshot never replaces fill-derived exposure, seeds synthetic trades,
 * reconciles an account or triggers liquidation. Unknown values stay
 * {@code null}.
 * </p>
 *
 * @since 0.25.1
 */
public final class FuturesPositionSnapshot implements Serializable {

    @Serial
    private static final long serialVersionUID = 5801207738194611023L;

    /**
     * Collateral mode of the observed position.
     *
     * @since 0.25.1
     */
    public enum MarginMode {

        /** Collateral is shared across the account. */
        CROSS,

        /** Collateral is isolated to the position. */
        ISOLATED
    }

    private final FuturesContract contract;
    private final Instant observedAt;
    private final Num signedContracts;
    private final String positionId;
    private final MarginMode marginMode;
    private final Num averageEntryPrice;
    private final Num collateral;
    private final Num initialMargin;
    private final Num maintenanceMargin;
    private final Num marginRatio;
    private final Num leverage;
    private final Num liquidationPrice;
    private final Num realizedPnl;
    private final Num unrealizedPnl;
    private final String source;
    private final Map<String, String> attributes;

    private FuturesPositionSnapshot(Builder builder) {
        this.contract = FuturesValidation.requireNonNull(builder.contract, "contract");
        this.observedAt = FuturesValidation.requireNonNull(builder.observedAt, "observedAt");
        this.signedContracts = FuturesValidation.requireFinite(builder.signedContracts, "signedContracts");
        this.positionId = FuturesValidation.requireNonBlankOrNull(builder.positionId, "positionId");
        this.marginMode = builder.marginMode;
        this.averageEntryPrice = FuturesValidation.requirePositiveFiniteOrNull(builder.averageEntryPrice,
                "averageEntryPrice");
        this.collateral = FuturesValidation.requireFiniteOrNull(builder.collateral, "collateral");
        this.initialMargin = FuturesValidation.requireNonNegativeFiniteOrNull(builder.initialMargin, "initialMargin");
        this.maintenanceMargin = FuturesValidation.requireNonNegativeFiniteOrNull(builder.maintenanceMargin,
                "maintenanceMargin");
        this.marginRatio = FuturesValidation.requireNonNegativeFiniteOrNull(builder.marginRatio, "marginRatio");
        this.leverage = FuturesValidation.requireNonNegativeFiniteOrNull(builder.leverage, "leverage");
        this.liquidationPrice = FuturesValidation.requirePositiveFiniteOrNull(builder.liquidationPrice,
                "liquidationPrice");
        this.realizedPnl = FuturesValidation.requireFiniteOrNull(builder.realizedPnl, "realizedPnl");
        this.unrealizedPnl = FuturesValidation.requireFiniteOrNull(builder.unrealizedPnl, "unrealizedPnl");
        this.source = FuturesValidation.requireNonBlankOrNull(builder.source, "source");
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
     * @return signed contract count, positive for a long position
     * @since 0.25.1
     */
    public Num signedContracts() {
        return signedContracts;
    }

    /**
     * @return the venue position identifier, or {@code null}
     * @since 0.25.1
     */
    public String positionId() {
        return positionId;
    }

    /**
     * @return the collateral mode, or {@code null}
     * @since 0.25.1
     */
    public MarginMode marginMode() {
        return marginMode;
    }

    /**
     * @return the venue's average entry price, or {@code null}
     * @since 0.25.1
     */
    public Num averageEntryPrice() {
        return averageEntryPrice;
    }

    /**
     * @return posted collateral in the settlement currency, or {@code null}
     * @since 0.25.1
     */
    public Num collateral() {
        return collateral;
    }

    /**
     * @return posted initial margin in the settlement currency, or {@code null}
     * @since 0.25.1
     */
    public Num initialMargin() {
        return initialMargin;
    }

    /**
     * @return posted maintenance margin in the settlement currency, or {@code null}
     * @since 0.25.1
     */
    public Num maintenanceMargin() {
        return maintenanceMargin;
    }

    /**
     * @return the venue margin ratio, or {@code null}; may exceed one
     * @since 0.25.1
     */
    public Num marginRatio() {
        return marginRatio;
    }

    /**
     * @return the venue-reported leverage, or {@code null}
     * @since 0.25.1
     */
    public Num leverage() {
        return leverage;
    }

    /**
     * @return the venue liquidation price, or {@code null}
     * @since 0.25.1
     */
    public Num liquidationPrice() {
        return liquidationPrice;
    }

    /**
     * @return venue realized profit and loss, or {@code null}
     * @since 0.25.1
     */
    public Num realizedPnl() {
        return realizedPnl;
    }

    /**
     * @return venue unrealized profit and loss, or {@code null}
     * @since 0.25.1
     */
    public Num unrealizedPnl() {
        return unrealizedPnl;
    }

    /**
     * @return the data source, or {@code null}
     * @since 0.25.1
     */
    public String source() {
        return source;
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
        if (!(other instanceof FuturesPositionSnapshot snapshot)) {
            return false;
        }
        return contract.equals(snapshot.contract) && observedAt.equals(snapshot.observedAt)
                && signedContracts.isEqual(snapshot.signedContracts) && Objects.equals(positionId, snapshot.positionId)
                && marginMode == snapshot.marginMode
                && FuturesValidation.numEqualsNullable(averageEntryPrice, snapshot.averageEntryPrice)
                && FuturesValidation.numEqualsNullable(collateral, snapshot.collateral)
                && FuturesValidation.numEqualsNullable(initialMargin, snapshot.initialMargin)
                && FuturesValidation.numEqualsNullable(maintenanceMargin, snapshot.maintenanceMargin)
                && FuturesValidation.numEqualsNullable(marginRatio, snapshot.marginRatio)
                && FuturesValidation.numEqualsNullable(leverage, snapshot.leverage)
                && FuturesValidation.numEqualsNullable(liquidationPrice, snapshot.liquidationPrice)
                && FuturesValidation.numEqualsNullable(realizedPnl, snapshot.realizedPnl)
                && FuturesValidation.numEqualsNullable(unrealizedPnl, snapshot.unrealizedPnl)
                && Objects.equals(source, snapshot.source) && attributes.equals(snapshot.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contract, observedAt, signedContracts, positionId, marginMode, averageEntryPrice,
                collateral, initialMargin, maintenanceMargin, marginRatio, leverage, liquidationPrice, realizedPnl,
                unrealizedPnl, source, attributes);
    }

    @Override
    public String toString() {
        return "FuturesPositionSnapshot[contract=" + contract + ", observedAt=" + observedAt + ", signedContracts="
                + signedContracts + ", marginMode=" + marginMode + "]";
    }

    /**
     * Builder for {@link FuturesPositionSnapshot} values.
     *
     * @since 0.25.1
     */
    public static final class Builder {

        private FuturesContract contract;
        private Instant observedAt;
        private Num signedContracts;
        private String positionId;
        private MarginMode marginMode;
        private Num averageEntryPrice;
        private Num collateral;
        private Num initialMargin;
        private Num maintenanceMargin;
        private Num marginRatio;
        private Num leverage;
        private Num liquidationPrice;
        private Num realizedPnl;
        private Num unrealizedPnl;
        private String source;
        private Map<String, String> attributes = Map.of();

        private Builder() {
        }

        private Builder(FuturesPositionSnapshot source) {
            this.contract = source.contract;
            this.observedAt = source.observedAt;
            this.signedContracts = source.signedContracts;
            this.positionId = source.positionId;
            this.marginMode = source.marginMode;
            this.averageEntryPrice = source.averageEntryPrice;
            this.collateral = source.collateral;
            this.initialMargin = source.initialMargin;
            this.maintenanceMargin = source.maintenanceMargin;
            this.marginRatio = source.marginRatio;
            this.leverage = source.leverage;
            this.liquidationPrice = source.liquidationPrice;
            this.realizedPnl = source.realizedPnl;
            this.unrealizedPnl = source.unrealizedPnl;
            this.source = source.source;
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
         * Sets the signed contract count.
         *
         * @param signedContracts signed contract count
         * @return this builder
         * @since 0.25.1
         */
        public Builder signedContracts(Num signedContracts) {
            this.signedContracts = signedContracts;
            return this;
        }

        /**
         * Sets the position identifier.
         *
         * @param positionId position identifier
         * @return this builder
         * @since 0.25.1
         */
        public Builder positionId(String positionId) {
            this.positionId = positionId;
            return this;
        }

        /**
         * Sets the margin mode.
         *
         * @param marginMode margin mode
         * @return this builder
         * @since 0.25.1
         */
        public Builder marginMode(MarginMode marginMode) {
            this.marginMode = marginMode;
            return this;
        }

        /**
         * Sets the average entry price.
         *
         * @param averageEntryPrice average entry price
         * @return this builder
         * @since 0.25.1
         */
        public Builder averageEntryPrice(Num averageEntryPrice) {
            this.averageEntryPrice = averageEntryPrice;
            return this;
        }

        /**
         * Sets the collateral.
         *
         * @param collateral collateral
         * @return this builder
         * @since 0.25.1
         */
        public Builder collateral(Num collateral) {
            this.collateral = collateral;
            return this;
        }

        /**
         * Sets the initial margin.
         *
         * @param initialMargin initial margin
         * @return this builder
         * @since 0.25.1
         */
        public Builder initialMargin(Num initialMargin) {
            this.initialMargin = initialMargin;
            return this;
        }

        /**
         * Sets the maintenance margin.
         *
         * @param maintenanceMargin maintenance margin
         * @return this builder
         * @since 0.25.1
         */
        public Builder maintenanceMargin(Num maintenanceMargin) {
            this.maintenanceMargin = maintenanceMargin;
            return this;
        }

        /**
         * Sets the margin ratio.
         *
         * @param marginRatio margin ratio
         * @return this builder
         * @since 0.25.1
         */
        public Builder marginRatio(Num marginRatio) {
            this.marginRatio = marginRatio;
            return this;
        }

        /**
         * Sets the leverage.
         *
         * @param leverage leverage
         * @return this builder
         * @since 0.25.1
         */
        public Builder leverage(Num leverage) {
            this.leverage = leverage;
            return this;
        }

        /**
         * Sets the liquidation price.
         *
         * @param liquidationPrice liquidation price
         * @return this builder
         * @since 0.25.1
         */
        public Builder liquidationPrice(Num liquidationPrice) {
            this.liquidationPrice = liquidationPrice;
            return this;
        }

        /**
         * Sets the realized profit and loss.
         *
         * @param realizedPnl realized profit and loss
         * @return this builder
         * @since 0.25.1
         */
        public Builder realizedPnl(Num realizedPnl) {
            this.realizedPnl = realizedPnl;
            return this;
        }

        /**
         * Sets the unrealized profit and loss.
         *
         * @param unrealizedPnl unrealized profit and loss
         * @return this builder
         * @since 0.25.1
         */
        public Builder unrealizedPnl(Num unrealizedPnl) {
            this.unrealizedPnl = unrealizedPnl;
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
         * Builds the immutable position snapshot.
         *
         * @return this builder
         * @since 0.25.1
         * @return the configured position snapshot
         * @since 0.25.1
         */
        public FuturesPositionSnapshot build() {
            return new FuturesPositionSnapshot(this);
        }
    }
}

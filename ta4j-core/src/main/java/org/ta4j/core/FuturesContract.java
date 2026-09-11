/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Immutable economic specification of a listed futures contract.
 *
 * <p>
 * Quantity is always a count of contracts. {@link SettlementType#LINEAR} sizes
 * {@link #contractSize()} in base units per contract and settles in the quote
 * currency; {@link SettlementType#INVERSE} sizes it in quote units per contract
 * and settles in the base currency. Other combinations, including quanto
 * products, are rejected: similar currency names never imply a conversion.
 * </p>
 *
 * <p>
 * A record binds exactly one specification. Changed economic terms require a
 * new contract value rather than reinterpreting historical fills, while mutable
 * market and risk observations belong in {@link FuturesMarketSnapshot} and
 * {@link FuturesPositionSnapshot}.
 * </p>
 *
 * @since 0.25.1
 */
public final class FuturesContract implements Serializable {

    @Serial
    private static final long serialVersionUID = 4188336072517306842L;

    /**
     * Listing family of the contract.
     *
     * @since 0.25.1
     */
    public enum ProductType {

        /** Contract without a settlement date; it may still carry a rolling expiry. */
        PERPETUAL,

        /** Contract that expires and settles at {@link #expiry()}. */
        DATED
    }

    /**
     * Notional and settlement convention of the contract.
     *
     * @since 0.25.1
     */
    public enum SettlementType {

        /**
         * Base-asset quantity per contract, unsettled notional in the quote currency.
         */
        LINEAR,

        /**
         * Quote-currency face value per contract, unsettled notional in the base asset.
         */
        INVERSE
    }

    private final String venue;
    private final String symbol;
    private final ProductType productType;
    private final SettlementType settlementType;
    private final String baseCurrency;
    private final String quoteCurrency;
    private final String settlementCurrency;
    private final Num contractSize;

    private final String productId;
    private final String contractCode;
    private final String contractRoot;
    private final String displayName;
    private final String contractExpiryType;
    private final String contractRootUnit;
    private final Instant expiry;
    private final ZoneId expiryTimeZone;
    private final Instant tradingDisabledAt;
    private final Num priceIncrement;
    private final Num quantityIncrement;
    private final Num minimumQuantity;
    private final Num maximumQuantity;
    private final Num minimumNotional;
    private final Num maximumNotional;
    private final Boolean perpetualStyle;
    private final Boolean trading24x7;
    private final Boolean nonCrypto;
    private final String riskManagedBy;
    private final Map<String, String> attributes;

    private FuturesContract(Builder builder) {
        this.venue = FuturesValidation.requireNonBlank(builder.venue, "venue");
        this.symbol = FuturesValidation.requireNonBlank(builder.symbol, "symbol");
        this.productType = Objects.requireNonNull(builder.productType, "productType");
        this.settlementType = Objects.requireNonNull(builder.settlementType, "settlementType");
        this.baseCurrency = FuturesValidation.requireNonBlank(builder.baseCurrency, "baseCurrency");
        this.quoteCurrency = FuturesValidation.requireNonBlank(builder.quoteCurrency, "quoteCurrency");
        this.settlementCurrency = FuturesValidation.requireNonBlank(builder.settlementCurrency, "settlementCurrency");
        this.contractSize = FuturesValidation.requirePositiveFinite(builder.contractSize, "contractSize");
        this.productId = FuturesValidation.requireNonBlankOrNull(builder.productId, "productId");
        this.contractCode = FuturesValidation.requireNonBlankOrNull(builder.contractCode, "contractCode");
        this.contractRoot = FuturesValidation.requireNonBlankOrNull(builder.contractRoot, "contractRoot");
        this.displayName = FuturesValidation.requireNonBlankOrNull(builder.displayName, "displayName");
        this.contractExpiryType = FuturesValidation.requireNonBlankOrNull(builder.contractExpiryType,
                "contractExpiryType");
        this.contractRootUnit = FuturesValidation.requireNonBlankOrNull(builder.contractRootUnit, "contractRootUnit");
        this.expiry = builder.expiry;
        this.expiryTimeZone = builder.expiryTimeZone;
        this.tradingDisabledAt = builder.tradingDisabledAt;
        this.priceIncrement = FuturesValidation.requirePositiveFiniteOrNull(builder.priceIncrement, "priceIncrement");
        this.quantityIncrement = FuturesValidation.requirePositiveFiniteOrNull(builder.quantityIncrement,
                "quantityIncrement");
        this.minimumQuantity = FuturesValidation.requirePositiveFiniteOrNull(builder.minimumQuantity,
                "minimumQuantity");
        this.maximumQuantity = FuturesValidation.requirePositiveFiniteOrNull(builder.maximumQuantity,
                "maximumQuantity");
        this.minimumNotional = FuturesValidation.requirePositiveFiniteOrNull(builder.minimumNotional,
                "minimumNotional");
        this.maximumNotional = FuturesValidation.requirePositiveFiniteOrNull(builder.maximumNotional,
                "maximumNotional");
        this.perpetualStyle = builder.perpetualStyle;
        this.trading24x7 = builder.trading24x7;
        this.nonCrypto = builder.nonCrypto;
        this.riskManagedBy = FuturesValidation.requireNonBlankOrNull(builder.riskManagedBy, "riskManagedBy");
        this.attributes = Map.copyOf(Objects.requireNonNull(builder.attributes, "attributes"));
        requireSettlementConvention();
        FuturesValidation.requireNotGreaterThan(minimumQuantity, maximumQuantity, "minimumQuantity", "maximumQuantity");
        FuturesValidation.requireNotGreaterThan(minimumNotional, maximumNotional, "minimumNotional", "maximumNotional");
    }

    private void requireSettlementConvention() {
        if (productType == ProductType.DATED && expiry == null) {
            throw new IllegalArgumentException("DATED contracts require an expiry");
        }
        switch (settlementType) {
        case LINEAR -> {
            if (!settlementCurrency.equals(quoteCurrency)) {
                throw new IllegalArgumentException("LINEAR contracts settle in the quote currency, expected "
                        + quoteCurrency + " but got " + settlementCurrency);
            }
        }
        case INVERSE -> {
            if (!settlementCurrency.equals(baseCurrency)) {
                throw new IllegalArgumentException("INVERSE contracts settle in the base currency, expected "
                        + baseCurrency + " but got " + settlementCurrency);
            }
        }
        default -> throw new IllegalArgumentException("Unsupported settlement type: " + settlementType);
        }
    }

    /**
     * @return new contract builder
     * @since 0.25.1
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * @return a builder pre-populated with this contract's specification
     * @since 0.25.1
     */
    public Builder toBuilder() {
        return new Builder(this);
    }

    /**
     * @return the venue that lists the contract
     * @since 0.25.1
     */
    public String venue() {
        return venue;
    }

    /**
     * @return the venue's symbol, preserved verbatim
     * @since 0.25.1
     */
    public String symbol() {
        return symbol;
    }

    /**
     * @return the listing family
     * @since 0.25.1
     */
    public ProductType productType() {
        return productType;
    }

    /**
     * @return the notional and settlement convention
     * @since 0.25.1
     */
    public SettlementType settlementType() {
        return settlementType;
    }

    /**
     * @return the base asset currency
     * @since 0.25.1
     */
    public String baseCurrency() {
        return baseCurrency;
    }

    /**
     * @return the quote currency
     * @since 0.25.1
     */
    public String quoteCurrency() {
        return quoteCurrency;
    }

    /**
     * @return the currency that realized profit, fees and funding settle in
     * @since 0.25.1
     */
    public String settlementCurrency() {
        return settlementCurrency;
    }

    /**
     * @return base units per contract for a LINEAR contract, quote units per
     *         contract for an INVERSE contract
     * @since 0.25.1
     */
    public Num contractSize() {
        return contractSize;
    }

    /**
     * @return the venue product identifier, or {@code null}
     * @since 0.25.1
     */
    public String productId() {
        return productId;
    }

    /**
     * @return the venue contract code, or {@code null}
     * @since 0.25.1
     */
    public String contractCode() {
        return contractCode;
    }

    /**
     * @return the contract root shared by a rolling family, or {@code null}
     * @since 0.25.1
     */
    public String contractRoot() {
        return contractRoot;
    }

    /**
     * @return the human-readable product name, or {@code null}
     * @since 0.25.1
     */
    public String displayName() {
        return displayName;
    }

    /**
     * @return the venue's expiry classification label, or {@code null}
     * @since 0.25.1
     */
    public String contractExpiryType() {
        return contractExpiryType;
    }

    /**
     * @return the unit the contract root is quoted in, or {@code null}
     * @since 0.25.1
     */
    public String contractRootUnit() {
        return contractRootUnit;
    }

    /**
     * @return the exchange expiry, or {@code null}; a perpetual may retain a
     *         far-dated rolling expiry
     * @since 0.25.1
     */
    public Instant expiry() {
        return expiry;
    }

    /**
     * @return the zone the expiry is expressed in, or {@code null}
     * @since 0.25.1
     */
    public ZoneId expiryTimeZone() {
        return expiryTimeZone;
    }

    /**
     * @return the instant trading is disabled, or {@code null}
     * @since 0.25.1
     */
    public Instant tradingDisabledAt() {
        return tradingDisabledAt;
    }

    /**
     * @return the minimum price increment, or {@code null}
     * @since 0.25.1
     */
    public Num priceIncrement() {
        return priceIncrement;
    }

    /**
     * @return the minimum quantity increment in contracts, or {@code null}
     * @since 0.25.1
     */
    public Num quantityIncrement() {
        return quantityIncrement;
    }

    /**
     * @return the minimum order quantity in contracts, or {@code null}
     * @since 0.25.1
     */
    public Num minimumQuantity() {
        return minimumQuantity;
    }

    /**
     * @return the maximum order quantity in contracts, or {@code null}
     * @since 0.25.1
     */
    public Num maximumQuantity() {
        return maximumQuantity;
    }

    /**
     * @return the minimum order size evaluated with {@link #quoteNotional}, or
     *         {@code null}
     * @since 0.25.1
     */
    public Num minimumNotional() {
        return minimumNotional;
    }

    /**
     * @return the maximum order size evaluated with {@link #quoteNotional}, or
     *         {@code null}
     * @since 0.25.1
     */
    public Num maximumNotional() {
        return maximumNotional;
    }

    /**
     * @return whether the venue flags the product as perpetual-style, or
     *         {@code null}
     * @since 0.25.1
     */
    public Boolean perpetualStyle() {
        return perpetualStyle;
    }

    /**
     * @return whether the venue flags continuous trading, or {@code null}
     * @since 0.25.1
     */
    public Boolean trading24x7() {
        return trading24x7;
    }

    /**
     * @return whether the venue flags a non-crypto underlying, or {@code null}
     * @since 0.25.1
     */
    public Boolean nonCrypto() {
        return nonCrypto;
    }

    /**
     * @return the venue's risk engine label, or {@code null}
     * @since 0.25.1
     */
    public String riskManagedBy() {
        return riskManagedBy;
    }

    /**
     * @return immutable non-economic provider identifiers and labels
     * @since 0.25.1
     */
    public Map<String, String> attributes() {
        return attributes;
    }

    /**
     * Returns the base-asset quantity of {@code contracts} at {@code price}.
     *
     * @param contracts contract count, nonnegative and finite
     * @param price     positive and finite reference price
     * @return base units, {@code contracts * contractSize} for LINEAR and
     *         {@code contracts * contractSize / price} for INVERSE
     * @since 0.25.1
     */
    public Num baseQuantity(Num contracts, Num price) {
        Num sized = contractsPerSize(contracts, price);
        return settlementType == SettlementType.LINEAR ? sized : sized.dividedBy(price);
    }

    /**
     * Returns the notional of {@code contracts} at {@code price} in the quote
     * currency.
     *
     * @param contracts contract count, nonnegative and finite
     * @param price     positive and finite reference price
     * @return quote-currency notional
     * @since 0.25.1
     */
    public Num quoteNotional(Num contracts, Num price) {
        Num sized = contractsPerSize(contracts, price);
        return settlementType == SettlementType.LINEAR ? sized.multipliedBy(price) : sized;
    }

    /**
     * Returns the notional of {@code contracts} at {@code price} in the settlement
     * currency.
     *
     * @param contracts contract count, nonnegative and finite
     * @param price     positive and finite reference price
     * @return settlement-currency notional
     * @since 0.25.1
     */
    public Num settlementNotional(Num contracts, Num price) {
        Num sized = contractsPerSize(contracts, price);
        return settlementType == SettlementType.LINEAR ? sized.multipliedBy(price) : sized.dividedBy(price);
    }

    /**
     * Returns the settlement-currency profit of a matched entry and exit.
     *
     * @param entryType  direction of the entry
     * @param contracts  matched contract count, nonnegative and finite
     * @param entryPrice positive and finite entry price
     * @param exitPrice  positive and finite exit price
     * @return profit in the settlement currency, negative for a loss
     * @since 0.25.1
     */
    public Num profit(Trade.TradeType entryType, Num contracts, Num entryPrice, Num exitPrice) {
        Objects.requireNonNull(entryType, "entryType");
        FuturesValidation.requirePositiveFinite(exitPrice, "exitPrice");
        NumFactory numFactory = entryPrice.getNumFactory();
        Num exit = numFactory.numOf(exitPrice.getDelegate());
        Num sized = contractsPerSize(contracts, entryPrice);
        Num payoff = settlementType == SettlementType.LINEAR ? exit.minus(entryPrice)
                : numFactory.one().dividedBy(entryPrice).minus(numFactory.one().dividedBy(exit));
        Num profit = sized.multipliedBy(payoff);
        return entryType == Trade.TradeType.BUY ? profit : profit.negate();
    }

    /**
     * Returns the credit-positive funding cash flow of a signed position.
     *
     * <p>
     * A positive rate charges longs and credits shorts; the amount is settled in
     * {@link #settlementCurrency()}.
     * </p>
     *
     * @param signedContracts signed contract count, positive for long
     * @param referencePrice  positive and finite funding reference price
     * @param fundingRate     signed and finite funding rate
     * @return funding credit when positive, funding debit when negative
     * @since 0.25.1
     */
    public Num fundingCashFlow(Num signedContracts, Num referencePrice, Num fundingRate) {
        FuturesValidation.requireFinite(signedContracts, "signedContracts");
        FuturesValidation.requireFinite(fundingRate, "fundingRate");
        Num notional = settlementNotional(signedContracts.abs(), referencePrice);
        Num magnitude = notional.multipliedBy(notional.getNumFactory().numOf(fundingRate.getDelegate()));
        return signedContracts.isNegative() ? magnitude : magnitude.negate();
    }

    /**
     * Returns the margin required for {@code contracts} at an explicit rate.
     *
     * <p>
     * Callers choose the rate: initial or maintenance, intraday or overnight. This
     * is a calculation, not collateral enforcement.
     * </p>
     *
     * @param contracts      contract count, nonnegative and finite
     * @param referencePrice positive and finite reference price
     * @param marginRate     nonnegative and finite margin rate
     * @return required margin in the settlement currency
     * @since 0.25.1
     */
    public Num marginRequirement(Num contracts, Num referencePrice, Num marginRate) {
        FuturesValidation.requireNonNegativeFinite(marginRate, "marginRate");
        Num notional = settlementNotional(contracts, referencePrice);
        Num normalizedMarginRate = notional.getNumFactory().numOf(marginRate.getDelegate());
        return notional.multipliedBy(normalizedMarginRate);
    }

    /**
     * Returns the settlement-notional leverage of {@code contracts} against
     * explicitly supplied collateral.
     *
     * @param contracts      contract count, nonnegative and finite
     * @param referencePrice positive and finite reference price
     * @param collateral     positive and finite collateral in the settlement
     *                       currency
     * @return settlement notional divided by collateral
     * @since 0.25.1
     */
    public Num effectiveLeverage(Num contracts, Num referencePrice, Num collateral) {
        FuturesValidation.requirePositiveFinite(collateral, "collateral");
        Num normalizedCollateral = referencePrice.getNumFactory().numOf(collateral.getDelegate());
        return settlementNotional(contracts, referencePrice).dividedBy(normalizedCollateral);
    }

    private Num contractsPerSize(Num contracts, Num price) {
        FuturesValidation.requireNonNegativeFinite(contracts, "contracts");
        FuturesValidation.requirePositiveFinite(price, "price");
        NumFactory numFactory = price.getNumFactory();
        return numFactory.numOf(contracts.getDelegate()).multipliedBy(numFactory.numOf(contractSize.getDelegate()));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FuturesContract contract)) {
            return false;
        }
        return venue.equals(contract.venue) && symbol.equals(contract.symbol) && productType == contract.productType
                && settlementType == contract.settlementType && baseCurrency.equals(contract.baseCurrency)
                && quoteCurrency.equals(contract.quoteCurrency)
                && settlementCurrency.equals(contract.settlementCurrency) && contractSize.isEqual(contract.contractSize)
                && Objects.equals(productId, contract.productId) && Objects.equals(contractCode, contract.contractCode)
                && Objects.equals(contractRoot, contract.contractRoot)
                && Objects.equals(displayName, contract.displayName)
                && Objects.equals(contractExpiryType, contract.contractExpiryType)
                && Objects.equals(contractRootUnit, contract.contractRootUnit)
                && Objects.equals(expiry, contract.expiry) && Objects.equals(expiryTimeZone, contract.expiryTimeZone)
                && Objects.equals(tradingDisabledAt, contract.tradingDisabledAt)
                && FuturesValidation.numEqualsNullable(priceIncrement, contract.priceIncrement)
                && FuturesValidation.numEqualsNullable(quantityIncrement, contract.quantityIncrement)
                && FuturesValidation.numEqualsNullable(minimumQuantity, contract.minimumQuantity)
                && FuturesValidation.numEqualsNullable(maximumQuantity, contract.maximumQuantity)
                && FuturesValidation.numEqualsNullable(minimumNotional, contract.minimumNotional)
                && FuturesValidation.numEqualsNullable(maximumNotional, contract.maximumNotional)
                && Objects.equals(perpetualStyle, contract.perpetualStyle)
                && Objects.equals(trading24x7, contract.trading24x7) && Objects.equals(nonCrypto, contract.nonCrypto)
                && Objects.equals(riskManagedBy, contract.riskManagedBy) && attributes.equals(contract.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(venue, symbol, productType, settlementType, baseCurrency, quoteCurrency, settlementCurrency,
                contractSize, productId, contractCode, contractRoot, displayName, contractExpiryType, contractRootUnit,
                expiry, expiryTimeZone, tradingDisabledAt, priceIncrement, quantityIncrement, minimumQuantity,
                maximumQuantity, minimumNotional, maximumNotional, perpetualStyle, trading24x7, nonCrypto,
                riskManagedBy, attributes);
    }

    @Override
    public String toString() {
        return "FuturesContract[venue=" + venue + ", symbol=" + symbol + ", productType=" + productType
                + ", settlementType=" + settlementType + ", baseCurrency=" + baseCurrency + ", quoteCurrency="
                + quoteCurrency + ", settlementCurrency=" + settlementCurrency + ", contractSize=" + contractSize + "]";
    }

    /**
     * Builder for {@link FuturesContract} values.
     *
     * @since 0.25.1
     */
    public static final class Builder {

        private String venue;
        private String symbol;
        private ProductType productType;
        private SettlementType settlementType;
        private String baseCurrency;
        private String quoteCurrency;
        private String settlementCurrency;
        private Num contractSize;
        private String productId;
        private String contractCode;
        private String contractRoot;
        private String displayName;
        private String contractExpiryType;
        private String contractRootUnit;
        private Instant expiry;
        private ZoneId expiryTimeZone;
        private Instant tradingDisabledAt;
        private Num priceIncrement;
        private Num quantityIncrement;
        private Num minimumQuantity;
        private Num maximumQuantity;
        private Num minimumNotional;
        private Num maximumNotional;
        private Boolean perpetualStyle;
        private Boolean trading24x7;
        private Boolean nonCrypto;
        private String riskManagedBy;
        private Map<String, String> attributes = Map.of();

        private Builder() {
        }

        private Builder(FuturesContract source) {
            this.venue = source.venue;
            this.symbol = source.symbol;
            this.productType = source.productType;
            this.settlementType = source.settlementType;
            this.baseCurrency = source.baseCurrency;
            this.quoteCurrency = source.quoteCurrency;
            this.settlementCurrency = source.settlementCurrency;
            this.contractSize = source.contractSize;
            this.productId = source.productId;
            this.contractCode = source.contractCode;
            this.contractRoot = source.contractRoot;
            this.displayName = source.displayName;
            this.contractExpiryType = source.contractExpiryType;
            this.contractRootUnit = source.contractRootUnit;
            this.expiry = source.expiry;
            this.expiryTimeZone = source.expiryTimeZone;
            this.tradingDisabledAt = source.tradingDisabledAt;
            this.priceIncrement = source.priceIncrement;
            this.quantityIncrement = source.quantityIncrement;
            this.minimumQuantity = source.minimumQuantity;
            this.maximumQuantity = source.maximumQuantity;
            this.minimumNotional = source.minimumNotional;
            this.maximumNotional = source.maximumNotional;
            this.perpetualStyle = source.perpetualStyle;
            this.trading24x7 = source.trading24x7;
            this.nonCrypto = source.nonCrypto;
            this.riskManagedBy = source.riskManagedBy;
            this.attributes = source.attributes;
        }

        /**
         * Sets {@code venue}.
         *
         * @param venue builder value
         * @return this builder
         * @since 0.25.1
         */
        public Builder venue(String venue) {
            this.venue = venue;
            return this;
        }

        /**
         * Sets {@code symbol}.
         *
         * @param symbol builder value
         * @return this builder
         * @since 0.25.1
         */
        public Builder symbol(String symbol) {
            this.symbol = symbol;
            return this;
        }

        /**
         * Sets {@code productType}.
         *
         * @param productType builder value
         * @return this builder
         * @since 0.25.1
         */
        public Builder productType(ProductType productType) {
            this.productType = productType;
            return this;
        }

        /**
         * Sets {@code settlementType}.
         *
         * @param settlementType builder value
         * @return this builder
         * @since 0.25.1
         */
        public Builder settlementType(SettlementType settlementType) {
            this.settlementType = settlementType;
            return this;
        }

        /**
         * Sets {@code baseCurrency}.
         *
         * @param baseCurrency builder value
         * @return this builder
         * @since 0.25.1
         */
        public Builder baseCurrency(String baseCurrency) {
            this.baseCurrency = baseCurrency;
            return this;
        }

        /**
         * Sets {@code quoteCurrency}.
         *
         * @param quoteCurrency builder value
         * @return this builder
         * @since 0.25.1
         */
        public Builder quoteCurrency(String quoteCurrency) {
            this.quoteCurrency = quoteCurrency;
            return this;
        }

        /**
         * Sets {@code settlementCurrency}.
         *
         * @param settlementCurrency builder value
         * @return this builder
         * @since 0.25.1
         */
        public Builder settlementCurrency(String settlementCurrency) {
            this.settlementCurrency = settlementCurrency;
            return this;
        }

        /**
         * Sets {@code contractSize}.
         *
         * @param contractSize builder value
         * @return this builder
         * @since 0.25.1
         */
        public Builder contractSize(Num contractSize) {
            this.contractSize = contractSize;
            return this;
        }

        /**
         * Sets {@code productId}.
         *
         * @param productId builder value
         * @return this builder
         * @since 0.25.1
         */
        public Builder productId(String productId) {
            this.productId = productId;
            return this;
        }

        /**
         * Sets {@code contractCode}.
         *
         * @param contractCode builder value
         * @return this builder
         * @since 0.25.1
         */
        public Builder contractCode(String contractCode) {
            this.contractCode = contractCode;
            return this;
        }

        /**
         * Sets {@code contractRoot}.
         *
         * @param contractRoot builder value
         * @return this builder
         * @since 0.25.1
         */
        public Builder contractRoot(String contractRoot) {
            this.contractRoot = contractRoot;
            return this;
        }

        /**
         * Sets {@code displayName}.
         *
         * @param displayName builder value
         * @return this builder
         * @since 0.25.1
         */
        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        /**
         * Sets {@code contractExpiryType}.
         *
         * @param contractExpiryType builder value
         * @return this builder
         * @since 0.25.1
         */
        public Builder contractExpiryType(String contractExpiryType) {
            this.contractExpiryType = contractExpiryType;
            return this;
        }

        /**
         * Sets {@code contractRootUnit}.
         *
         * @param contractRootUnit builder value
         * @return this builder
         * @since 0.25.1
         */
        public Builder contractRootUnit(String contractRootUnit) {
            this.contractRootUnit = contractRootUnit;
            return this;
        }

        /**
         * Sets {@code expiry}.
         *
         * @param expiry builder value
         * @return this builder
         * @since 0.25.1
         */
        public Builder expiry(Instant expiry) {
            this.expiry = expiry;
            return this;
        }

        /**
         * Sets {@code expiryTimeZone}.
         *
         * @param expiryTimeZone builder value
         * @return this builder
         * @since 0.25.1
         */
        public Builder expiryTimeZone(ZoneId expiryTimeZone) {
            this.expiryTimeZone = expiryTimeZone;
            return this;
        }

        /**
         * Sets {@code tradingDisabledAt}.
         *
         * @param tradingDisabledAt builder value
         * @return this builder
         * @since 0.25.1
         */
        public Builder tradingDisabledAt(Instant tradingDisabledAt) {
            this.tradingDisabledAt = tradingDisabledAt;
            return this;
        }

        /**
         * Sets {@code priceIncrement}.
         *
         * @param priceIncrement builder value
         * @return this builder
         * @since 0.25.1
         */
        public Builder priceIncrement(Num priceIncrement) {
            this.priceIncrement = priceIncrement;
            return this;
        }

        /**
         * Sets {@code quantityIncrement}.
         *
         * @param quantityIncrement builder value
         * @return this builder
         * @since 0.25.1
         */
        public Builder quantityIncrement(Num quantityIncrement) {
            this.quantityIncrement = quantityIncrement;
            return this;
        }

        /**
         * Sets {@code minimumQuantity}.
         *
         * @param minimumQuantity builder value
         * @return this builder
         * @since 0.25.1
         */
        public Builder minimumQuantity(Num minimumQuantity) {
            this.minimumQuantity = minimumQuantity;
            return this;
        }

        /**
         * Sets {@code maximumQuantity}.
         *
         * @param maximumQuantity builder value
         * @return this builder
         * @since 0.25.1
         */
        public Builder maximumQuantity(Num maximumQuantity) {
            this.maximumQuantity = maximumQuantity;
            return this;
        }

        /**
         * Sets {@code minimumNotional}.
         *
         * @param minimumNotional builder value
         * @return this builder
         * @since 0.25.1
         */
        public Builder minimumNotional(Num minimumNotional) {
            this.minimumNotional = minimumNotional;
            return this;
        }

        /**
         * Sets {@code maximumNotional}.
         *
         * @param maximumNotional builder value
         * @return this builder
         * @since 0.25.1
         */
        public Builder maximumNotional(Num maximumNotional) {
            this.maximumNotional = maximumNotional;
            return this;
        }

        /**
         * Sets {@code perpetualStyle}.
         *
         * @param perpetualStyle builder value
         * @return this builder
         * @since 0.25.1
         */
        public Builder perpetualStyle(Boolean perpetualStyle) {
            this.perpetualStyle = perpetualStyle;
            return this;
        }

        /**
         * Sets {@code trading24x7}.
         *
         * @param trading24x7 builder value
         * @return this builder
         * @since 0.25.1
         */
        public Builder trading24x7(Boolean trading24x7) {
            this.trading24x7 = trading24x7;
            return this;
        }

        /**
         * Sets {@code nonCrypto}.
         *
         * @param nonCrypto builder value
         * @return this builder
         * @since 0.25.1
         */
        public Builder nonCrypto(Boolean nonCrypto) {
            this.nonCrypto = nonCrypto;
            return this;
        }

        /**
         * Sets {@code riskManagedBy}.
         *
         * @param riskManagedBy builder value
         * @return this builder
         * @since 0.25.1
         */
        public Builder riskManagedBy(String riskManagedBy) {
            this.riskManagedBy = riskManagedBy;
            return this;
        }

        /**
         * Sets {@code attributes}.
         *
         * @param attributes builder value
         * @return this builder
         * @since 0.25.1
         */
        public Builder attributes(Map<String, String> attributes) {
            this.attributes = attributes == null ? Map.of() : attributes;
            return this;
        }

        /**
         * Builds a futures contract from the configured values.
         *
         * @return immutable futures contract
         * @since 0.25.1
         */
        public FuturesContract build() {
            return new FuturesContract(this);
        }
    }
}

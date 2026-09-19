/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.cost;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.ta4j.core.FuturesContract;
import org.ta4j.core.Position;
import org.ta4j.core.RealtimeBar;
import org.ta4j.core.Trade;
import org.ta4j.core.TradeFee;
import org.ta4j.core.TradeFill;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Models perpetual futures fees from a maker/taker rate schedule.
 *
 * <p>
 * The modeled commission is the selected rate applied to the contract
 * settlement notional of each execution fill, so a linear contract is charged
 * on {@code contracts * contractSize * price} and an inverse contract on
 * {@code contracts * contractSize / price}. Charges are expressed in the
 * contract settlement currency; a venue fee recorded in another currency is
 * converted by {@link TradeFee} instead of by this model.
 * </p>
 *
 * <p>
 * Rates are never clamped: a negative rate models a rebate, and the optional
 * per-contract minimum is the only floor. The minimum applies to the commission
 * alone and never to the additional per-contract charges.
 * </p>
 *
 * @since 0.25.1
 */
public final class FuturesTransactionCostModel implements CostModel {

    private final Num makerRate;
    private final Num takerRate;
    private final Num minimumPerContract;
    private final Map<TradeFee.Type, Num> perContractCharges;
    private final RealtimeBar.Liquidity defaultLiquidity;
    private final String source;
    private final Instant asOf;

    private FuturesTransactionCostModel(Builder builder) {
        this.makerRate = builder.makerRate;
        this.takerRate = builder.takerRate;
        this.minimumPerContract = builder.minimumPerContract;
        this.perContractCharges = Map.copyOf(builder.perContractCharges);
        this.defaultLiquidity = builder.defaultLiquidity;
        this.source = builder.source;
        this.asOf = builder.asOf;
    }

    /**
     * @return a new model builder
     * @since 0.25.1
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * @param position     the position
     * @param currentIndex the index up to which executed fills are charged
     * @return the modeled fee of the position portion executed by
     *         {@code currentIndex}
     * @since 0.25.1
     */
    @Override
    public Num calculate(Position position, int currentIndex) {
        Trade entry = position == null ? null : position.getEntry();
        if (entry == null) {
            return DoubleNumFactory.getInstance().zero();
        }
        NumFactory numFactory = entry.getPricePerAsset().getNumFactory();
        Num total = numFactory.zero();
        total = total.plus(numFactory.numOf(sumModeledFees(entry, currentIndex).getDelegate()));
        Trade exit = position.getExit();
        if (exit != null) {
            total = total.plus(numFactory.numOf(sumModeledFees(exit, currentIndex).getDelegate()));
        }
        return total;
    }

    /**
     * @param position the position
     * @return the modeled fee of every executed fill of the position
     * @since 0.25.1
     */
    @Override
    public Num calculate(Position position) {
        return calculate(position, Integer.MAX_VALUE);
    }

    /**
     * @throws UnsupportedOperationException always: a rate schedule needs the
     *                                       contract settlement terms that this
     *                                       overload does not receive
     * @since 0.25.1
     */
    @Override
    public Num calculate(Num price, Num amount) {
        throw new UnsupportedOperationException(
                "FuturesTransactionCostModel needs the traded contract; use calculate(TradeFill)");
    }

    /**
     * @param fill the futures execution fill
     * @return the modeled charge of {@code fill} in the settlement currency
     * @since 0.25.1
     */
    @Override
    public Num calculate(TradeFill fill) {
        Num total = fill.price().getNumFactory().zero();
        for (TradeFee fee : calculateFees(fill)) {
            total = total.plus(normalizeFor(fee.amount(), total.getNumFactory(), "fee"));
        }
        return total;
    }

    /**
     * Items the modeled charge of a futures execution fill, using the maker rate
     * for a maker fill and the taker rate otherwise.
     *
     * @param fill the futures execution fill
     * @return the commission component followed by one component per configured
     *         per-contract charge
     * @throws IllegalArgumentException when {@code fill} is not a futures fill
     * @since 0.25.1
     */
    @Override
    public List<TradeFee> calculateFees(TradeFill fill) {
        Objects.requireNonNull(fill, "fill");
        FuturesContract contract = fill.futuresContract();
        if (contract == null) {
            throw new IllegalArgumentException("fee components are only defined for futures fills");
        }
        NumFactory numFactory = fill.price().getNumFactory();
        Num amount = normalizeFor(fill.amount(), numFactory, "amount");
        Num settlementNotional = contract.settlementNotional(fill.amount(), fill.price());
        Num feeRate = normalizeFor(selectedRate(fill), numFactory, "fee rate");
        Num commission = requireRepresentableProduct(settlementNotional, feeRate,
                settlementNotional.multipliedBy(feeRate), "commission");
        if (minimumPerContract != null) {
            Num minimumRate = normalizeFor(minimumPerContract, numFactory, "minimumPerContract");
            Num floor = requireRepresentableProduct(amount, minimumRate, amount.multipliedBy(minimumRate),
                    "minimum commission");
            if (floor.isGreaterThan(commission)) {
                commission = floor;
            }
        }

        List<TradeFee> fees = new ArrayList<>(perContractCharges.size() + 1);
        fees.add(component(TradeFee.Type.COMMISSION, commission, contract.settlementCurrency()));
        for (Map.Entry<TradeFee.Type, Num> charge : perContractCharges.entrySet()) {
            Num chargeRate = normalizeFor(charge.getValue(), numFactory, "perContractCharge");
            Num chargeAmount = requireRepresentableProduct(amount, chargeRate, amount.multipliedBy(chargeRate),
                    "per-contract charge");
            fees.add(component(charge.getKey(), chargeAmount, contract.settlementCurrency()));
        }
        return List.copyOf(fees);
    }

    @Override
    public boolean equals(CostModel otherModel) {
        if (!(otherModel instanceof FuturesTransactionCostModel other)) {
            return false;
        }
        return numEqualsNullable(makerRate, other.makerRate) && numEqualsNullable(takerRate, other.takerRate)
                && numEqualsNullable(minimumPerContract, other.minimumPerContract)
                && numMapEquals(perContractCharges, other.perContractCharges)
                && defaultLiquidity == other.defaultLiquidity && Objects.equals(source, other.source)
                && Objects.equals(asOf, other.asOf);
    }

    private static boolean numEqualsNullable(Num left, Num right) {
        if (left == null || right == null) {
            return left == right;
        }
        if (left.isNaN() || right.isNaN()) {
            return left.isNaN() && right.isNaN();
        }
        return left.bigDecimalValue().compareTo(right.bigDecimalValue()) == 0;
    }

    private static boolean numMapEquals(Map<TradeFee.Type, Num> left, Map<TradeFee.Type, Num> right) {
        if (!left.keySet().equals(right.keySet())) {
            return false;
        }
        for (Map.Entry<TradeFee.Type, Num> charge : left.entrySet()) {
            if (!numEqualsNullable(charge.getValue(), right.get(charge.getKey()))) {
                return false;
            }
        }
        return true;
    }

    private Num selectedRate(TradeFill fill) {
        RealtimeBar.Liquidity liquidity = fill.liquidity() == null ? defaultLiquidity : fill.liquidity();
        return liquidity == RealtimeBar.Liquidity.MAKER ? makerRate : takerRate;
    }

    private static Num normalizeFor(Num value, NumFactory numFactory, String name) {
        Objects.requireNonNull(value, name);
        Num normalized = numFactory.numOf(value.getDelegate());
        if (!Num.isFinite(normalized)) {
            throw new IllegalArgumentException(name + " must be finite and representable in the fill number factory");
        }
        if (!value.isZero() && normalized.isZero()) {
            throw new IllegalArgumentException(name + " must be representable in the fill number factory");
        }
        return normalized;
    }

    private static Num requireRepresentableProduct(Num left, Num right, Num product, String name) {
        if (!Num.isFinite(product)) {
            throw new IllegalArgumentException(name + " must be finite in the fill number factory");
        }
        if (!left.isZero() && !right.isZero() && product.isZero()) {
            throw new IllegalArgumentException(name + " cannot be represented in the fill number factory");
        }
        return product;
    }

    private TradeFee component(TradeFee.Type type, Num amount, String settlementCurrency) {
        return TradeFee.builder()
                .type(type)
                .amount(amount)
                .currency(settlementCurrency)
                .source(source)
                .scheduleAsOf(asOf)
                .build();
    }

    private Num sumModeledFees(Trade trade, int currentIndex) {
        NumFactory numFactory = trade.getPricePerAsset().getNumFactory();
        Num total = numFactory.zero();
        for (TradeFill fill : Trade.executionFillsOf(trade)) {
            if (fill.index() >= 0 && fill.index() <= currentIndex) {
                Num fee = fill.hasRecordedFees() ? fill.fee() : calculate(fill);
                total = total.plus(normalizeFor(fee, numFactory, "fee"));
            }
        }
        return total;
    }

    /**
     * Builder for {@link FuturesTransactionCostModel} values.
     *
     * @since 0.25.1
     */
    public static final class Builder {

        private Num makerRate;
        private Num takerRate;
        private Num minimumPerContract;
        private final Map<TradeFee.Type, Num> perContractCharges = new EnumMap<>(TradeFee.Type.class);
        private RealtimeBar.Liquidity defaultLiquidity = RealtimeBar.Liquidity.TAKER;
        private String source;
        private Instant asOf;

        private Builder() {
        }

        /**
         * Sets the maker fee rate.
         *
         * @param makerRate maker fee rate
         * @return this builder
         * @since 0.25.1
         */
        public Builder makerRate(Num makerRate) {
            this.makerRate = requireFinite(makerRate, "makerRate");
            return this;
        }

        /**
         * Sets the taker fee rate.
         *
         * @param takerRate taker fee rate
         * @return this builder
         * @since 0.25.1
         */
        public Builder takerRate(Num takerRate) {
            this.takerRate = requireFinite(takerRate, "takerRate");
            return this;
        }

        /**
         * Sets the minimum fee per contract.
         *
         * @param minimumPerContract minimum fee per contract
         * @return this builder
         * @since 0.25.1
         */
        public Builder minimumPerContract(Num minimumPerContract) {
            this.minimumPerContract = minimumPerContract == null ? null
                    : requireNonNegative(minimumPerContract, "minimumPerContract");
            return this;
        }

        /**
         * Sets the per-contract fee amount.
         *
         * @param type   fee type
         * @param amount per-contract fee amount
         * @return this builder
         * @since 0.25.1
         */
        public Builder perContractCharge(TradeFee.Type type, Num amount) {
            Objects.requireNonNull(type, "type");
            perContractCharges.put(type, requireNonNegative(amount, "perContractCharge"));
            return this;
        }

        /**
         * Sets the default liquidity.
         *
         * @param defaultLiquidity default liquidity
         * @return this builder
         * @since 0.25.1
         */
        public Builder defaultLiquidity(RealtimeBar.Liquidity defaultLiquidity) {
            this.defaultLiquidity = Objects.requireNonNull(defaultLiquidity, "defaultLiquidity");
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
         * Sets the effective instant.
         *
         * @param asOf effective instant
         * @return this builder
         * @since 0.25.1
         */
        public Builder asOf(Instant asOf) {
            this.asOf = asOf;
            return this;
        }

        /**
         * Builds the immutable futures transaction cost model.
         *
         * @return this builder
         * @since 0.25.1
         * @return the configured futures transaction cost model
         * @since 0.25.1
         */
        public FuturesTransactionCostModel build() {
            if (makerRate == null || takerRate == null) {
                throw new IllegalArgumentException("makerRate and takerRate are required");
            }
            return new FuturesTransactionCostModel(this);
        }

        private static Num requireFinite(Num value, String name) {
            Objects.requireNonNull(value, name);
            if (!Num.isFinite(value)) {
                throw new IllegalArgumentException(name + " must be finite");
            }
            return value;
        }

        private static Num requireNonNegative(Num value, String name) {
            Objects.requireNonNull(value, name);
            if (!Num.isFinite(value) || value.isNegative()) {
                throw new IllegalArgumentException(name + " must be nonnegative and finite");
            }
            return value;
        }
    }
}

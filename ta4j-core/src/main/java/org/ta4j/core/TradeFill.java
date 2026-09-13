/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.ta4j.core.num.Num;

/**
 * Immutable execution fill used to represent partial trade executions.
 *
 * <p>
 * This is the preferred fill primitive for new integrations that need
 * broker-confirmed execution recording through
 * {@link TradingRecord#operate(TradeFill)}.
 * </p>
 *
 * <p>
 * Metadata fields ({@code time}, {@code side}, IDs) are optional and may be
 * {@code null}. {@code fee} defaults to zero when omitted.
 * </p>
 *
 * <p>
 * A fill either describes a spot execution or a native futures execution. Spot
 * fills carry the legacy scalar {@code fee}; futures fills bind one
 * {@link FuturesContract} and carry resolved {@link TradeFee} components in the
 * contract settlement currency. {@link #fees()} is empty for spot fills, and
 * {@link #hasRecordedFees()} distinguishes an explicit (possibly empty) futures
 * component list from an omitted one that still needs cost modelling.
 * </p>
 *
 * @since 0.22.4
 */
public final class TradeFill implements Serializable {

    @Serial
    private static final long serialVersionUID = -258216480640174496L;

    private final int index;
    private final Instant time;
    private final Num price;
    private final Num amount;
    private final Num fee;
    private final ExecutionSide side;
    private final String orderId;
    private final String correlationId;

    private final String instrument;
    private final FuturesContract futuresContract;
    private final RealtimeBar.Liquidity liquidity;
    private final String executionId;
    private final List<TradeFee> fees;
    private final boolean liquidation;
    private final boolean reduceOnly;
    private final FuturesMarketSnapshot marketSnapshot;
    private final FuturesPositionSnapshot positionSnapshot;

    private TradeFill(Builder builder) {
        if (builder.index < -1) {
            throw new IllegalArgumentException("index must be >= -1");
        }
        this.index = builder.index;
        this.time = builder.time;
        this.price = FuturesValidation.requireNonNull(builder.price, "price");
        this.amount = FuturesValidation.requireNonNull(builder.amount, "amount");
        this.side = builder.side;
        this.orderId = builder.orderId;
        this.correlationId = builder.correlationId;
        this.futuresContract = builder.futuresContract;
        this.liquidity = builder.liquidity;
        this.executionId = builder.executionId;
        this.liquidation = builder.liquidation;
        this.reduceOnly = builder.reduceOnly;
        this.marketSnapshot = builder.marketSnapshot;
        this.positionSnapshot = builder.positionSnapshot;
        if (futuresContract == null) {
            requireSpotOnly(builder);
            this.instrument = builder.instrument;
            this.fees = null;
            this.fee = builder.fee == null ? price.getNumFactory().zero() : builder.fee;
        } else {
            requireFuturesMetadata(builder);
            this.instrument = builder.instrument == null ? futuresContract.symbol() : builder.instrument;
            this.fees = resolveFees(builder);
            this.fee = SettlementAmountSupport.sumSettlementAmounts(this.fees, price.getNumFactory());
        }
    }

    private void requireSpotOnly(Builder builder) {
        if (builder.fees != null) {
            throw new IllegalArgumentException("fee components require a futures contract");
        }
        if (builder.liquidation || builder.reduceOnly) {
            throw new IllegalArgumentException("liquidation and reduceOnly require a futures contract");
        }
        if (builder.marketSnapshot != null || builder.positionSnapshot != null) {
            throw new IllegalArgumentException("futures snapshots require a futures contract");
        }
    }

    private void requireFuturesMetadata(Builder builder) {
        if (index < -1) {
            throw new IllegalArgumentException("index must be >= -1");
        }
        FuturesValidation.requireNonNull(time, "time");
        FuturesValidation.requireNonNull(side, "side");
        FuturesValidation.requirePositiveFinite(price, "price");
        FuturesValidation.requirePositiveFinite(amount, "amount");
        if (builder.instrument != null && !builder.instrument.equals(futuresContract.symbol())) {
            throw new IllegalArgumentException("instrument must match the contract symbol " + futuresContract.symbol()
                    + " but was " + builder.instrument);
        }
        requireSnapshotContract(marketSnapshot, "marketSnapshot");
        requireSnapshotContract(positionSnapshot, "positionSnapshot");
    }

    private void requireSnapshotContract(FuturesMarketSnapshot snapshot, String name) {
        if (snapshot != null && !snapshot.contract().equals(futuresContract)) {
            throw new IllegalArgumentException(name + " must describe the fill contract " + futuresContract.symbol());
        }
    }

    private void requireSnapshotContract(FuturesPositionSnapshot snapshot, String name) {
        if (snapshot != null && !snapshot.contract().equals(futuresContract)) {
            throw new IllegalArgumentException(name + " must describe the fill contract " + futuresContract.symbol());
        }
    }

    private List<TradeFee> resolveFees(Builder builder) {
        if (builder.fees != null) {
            if (builder.fee != null) {
                throw new IllegalArgumentException("supply either a scalar fee or fee components, not both");
            }
            return builder.fees.stream().map(fee -> fee.resolveFor(futuresContract.settlementCurrency())).toList();
        }
        if (builder.fee == null) {
            return null;
        }
        return List.of(TradeFee.builder()
                .type(TradeFee.Type.COMMISSION)
                .amount(builder.fee)
                .currency(futuresContract.settlementCurrency())
                .build()
                .resolveFor(futuresContract.settlementCurrency()));
    }

    /**
     * Creates a trade fill with scalar fields only.
     *
     * @param index  bar index where the fill happened
     * @param price  execution price per asset
     * @param amount executed amount
     * @since 0.22.4
     */
    public TradeFill(int index, Num price, Num amount) {
        this(index, null, price, amount, null, null, null, null);
    }

    /**
     * Creates a trade fill with execution side/time metadata.
     *
     * @param index  bar index where the fill happened
     * @param time   execution timestamp (UTC), nullable
     * @param price  execution price per asset
     * @param amount executed amount
     * @param side   execution side, nullable
     * @since 0.22.4
     */
    public TradeFill(int index, Instant time, Num price, Num amount, ExecutionSide side) {
        this(index, time, price, amount, null, side, null, null);
    }

    /**
     * Creates a spot trade fill.
     *
     * @param index         bar index where the fill happened
     * @param time          execution timestamp (UTC), nullable
     * @param price         execution price per asset
     * @param amount        executed amount
     * @param fee           optional execution fee (defaults to zero when null)
     * @param side          optional execution side
     * @param orderId       optional order id
     * @param correlationId optional correlation id
     * @throws NullPointerException when price or amount is null
     * @since 0.22.4
     */
    public TradeFill(int index, Instant time, Num price, Num amount, Num fee, ExecutionSide side, String orderId,
            String correlationId) {
        this(builder().index(index)
                .time(time)
                .price(price)
                .amount(amount)
                .fee(fee)
                .side(side)
                .orderId(orderId)
                .correlationId(correlationId));
    }

    /**
     * @return new fill builder
     * @since 0.25.1
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * @return a builder pre-populated with this fill
     * @since 0.25.1
     */
    public Builder toBuilder() {
        return new Builder(this);
    }

    /**
     * @return bar index where the fill happened; {@code -1} is reserved for fills
     *         awaiting recorder-assigned indices
     * @since 0.22.4
     */
    public int index() {
        return index;
    }

    /**
     * @return execution timestamp (UTC), or {@code null}
     * @since 0.22.4
     */
    public Instant time() {
        return time;
    }

    /**
     * @return execution price per asset
     * @since 0.22.4
     */
    public Num price() {
        return price;
    }

    /**
     * @return executed amount: base-asset units for spot, contract count for
     *         futures
     * @since 0.22.4
     */
    public Num amount() {
        return amount;
    }

    /**
     * @return the charge-positive execution fee: the recorded scalar for spot
     *         fills, the sum of the resolved settlement-currency components for
     *         futures fills
     * @since 0.22.4
     */
    public Num fee() {
        return fee;
    }

    /**
     * @return execution side, or {@code null}
     * @since 0.22.4
     */
    public ExecutionSide side() {
        return side;
    }

    /**
     * @return order id, or {@code null}
     * @since 0.22.4
     */
    public String orderId() {
        return orderId;
    }

    /**
     * @return correlation id, or {@code null}
     * @since 0.22.4
     */
    public String correlationId() {
        return correlationId;
    }

    /**
     * @return instrument identifier; the contract symbol for futures fills, or
     *         {@code null} for spot fills without a label
     * @since 0.25.1
     */
    public String instrument() {
        return instrument;
    }

    /**
     * @return the traded contract, or {@code null} for a spot fill
     * @since 0.25.1
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "FuturesContract is a final value type whose instances are shared by reference; copying the immutable contract per accessor would allocate on every read")
    public FuturesContract futuresContract() {
        return futuresContract;
    }

    /**
     * @return the venue liquidity role, or {@code null} when unknown
     * @since 0.25.1
     */
    public RealtimeBar.Liquidity liquidity() {
        return liquidity;
    }

    /**
     * @return the venue execution identifier, or {@code null}; never fabricated
     * @since 0.25.1
     */
    public String executionId() {
        return executionId;
    }

    /**
     * @return resolved fee components in the contract settlement currency; empty
     *         for spot fills and for futures fills without recorded components
     * @since 0.25.1
     */
    public List<TradeFee> fees() {
        return fees == null ? List.of() : fees;
    }

    /**
     * @return true when this fill carries an explicit fee component list, even when
     *         it is empty; false when fees are still to be modelled
     * @since 0.25.1
     */
    public boolean hasRecordedFees() {
        return fees != null;
    }

    /**
     * @return true when the venue reports this fill as a liquidation
     * @since 0.25.1
     */
    public boolean liquidation() {
        return liquidation;
    }

    /**
     * @return true when the venue reports this fill as reduce-only
     * @since 0.25.1
     */
    public boolean reduceOnly() {
        return reduceOnly;
    }

    /**
     * @return the market observation attached to this fill, or {@code null}
     * @since 0.25.1
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "The captured market snapshot is an immutable value type shared by reference; copying it per accessor would allocate on every read")
    public FuturesMarketSnapshot marketSnapshot() {
        return marketSnapshot;
    }

    /**
     * @return the position observation attached to this fill, or {@code null}
     * @since 0.25.1
     */
    public FuturesPositionSnapshot positionSnapshot() {
        return positionSnapshot;
    }

    /**
     * Builds the single compatibility fill for a trade that does not expose native
     * fills.
     *
     * <p>
     * Spot trades mirror the trade-level scalar metadata. A native futures trade
     * keeps its contract and resolved fee components instead of degrading into a
     * spot fill; a missing component list is derived from the recorded scalar cost
     * as one commission in the contract settlement currency.
     * </p>
     *
     * @param trade trade to mirror
     * @param side  execution side resolved from the trade direction
     * @return immutable fill mirroring the trade
     */
    static TradeFill forTrade(Trade trade, ExecutionSide side) {
        FuturesContract contract = trade.getFuturesContract();
        if (contract == null) {
            return TradeFill.builder()
                    .index(trade.getIndex())
                    .time(trade.getTime())
                    .price(trade.getPricePerAsset())
                    .amount(trade.getAmount())
                    .fee(trade.getCost())
                    .side(side)
                    .orderId(trade.getOrderId())
                    .correlationId(trade.getCorrelationId())
                    .instrument(trade.getInstrument())
                    .build();
        }
        List<TradeFee> components = trade.getFees();
        if (components.isEmpty()) {
            Num cost = trade.getCost();
            components = List.of(TradeFee.builder()
                    .type(TradeFee.Type.COMMISSION)
                    .amount(cost == null ? trade.getPricePerAsset().getNumFactory().zero() : cost)
                    .currency(contract.settlementCurrency())
                    .build());
        }
        return TradeFill.builder()
                .index(trade.getIndex())
                .time(trade.getTime())
                .price(trade.getPricePerAsset())
                .amount(trade.getAmount())
                .side(side)
                .orderId(trade.getOrderId())
                .correlationId(trade.getCorrelationId())
                .futuresContract(contract)
                .fees(components)
                .build();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TradeFill fill)) {
            return false;
        }
        return index == fill.index && Objects.equals(time, fill.time)
                && FuturesValidation.numEqualsNullable(price, fill.price)
                && FuturesValidation.numEqualsNullable(amount, fill.amount)
                && FuturesValidation.numEqualsNullable(fee, fill.fee) && side == fill.side
                && Objects.equals(orderId, fill.orderId) && Objects.equals(correlationId, fill.correlationId)
                && Objects.equals(instrument, fill.instrument) && Objects.equals(futuresContract, fill.futuresContract)
                && liquidity == fill.liquidity && Objects.equals(executionId, fill.executionId)
                && Objects.equals(fees, fill.fees) && liquidation == fill.liquidation && reduceOnly == fill.reduceOnly
                && Objects.equals(marketSnapshot, fill.marketSnapshot)
                && Objects.equals(positionSnapshot, fill.positionSnapshot);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, time, FuturesValidation.numHash(price), FuturesValidation.numHash(amount),
                FuturesValidation.numHash(fee), side, orderId, correlationId, instrument, futuresContract, liquidity,
                executionId, fees, liquidation, reduceOnly, marketSnapshot, positionSnapshot);
    }

    @Override
    public String toString() {
        String base = "TradeFill[index=" + index + ", time=" + time + ", price=" + price + ", amount=" + amount
                + ", fee=" + fee + ", side=" + side + ", orderId=" + orderId + ", correlationId=" + correlationId;
        if (futuresContract == null) {
            return base + ", instrument=" + instrument + "]";
        }
        return base + ", instrument=" + instrument + ", contract=" + futuresContract + ", liquidity=" + liquidity
                + ", executionId=" + executionId + ", fees=" + fees + ", liquidation=" + liquidation + ", reduceOnly="
                + reduceOnly + ", marketSnapshot=" + marketSnapshot + ", positionSnapshot=" + positionSnapshot + "]";
    }

    /**
     * Builder for {@link TradeFill} values.
     *
     * @since 0.25.1
     */
    public static final class Builder {

        private int index = -1;
        private Instant time;
        private Num price;
        private Num amount;
        private Num fee;
        private ExecutionSide side;
        private String orderId;
        private String correlationId;
        private String instrument;
        private FuturesContract futuresContract;
        private RealtimeBar.Liquidity liquidity;
        private String executionId;
        private List<TradeFee> fees;
        private boolean liquidation;
        private boolean reduceOnly;
        private FuturesMarketSnapshot marketSnapshot;
        private FuturesPositionSnapshot positionSnapshot;

        private Builder() {
        }

        private Builder(TradeFill source) {
            this.index = source.index;
            this.time = source.time;
            this.price = source.price;
            this.amount = source.amount;
            this.fee = source.futuresContract == null ? source.fee : null;
            this.side = source.side;
            this.orderId = source.orderId;
            this.correlationId = source.correlationId;
            this.instrument = source.instrument;
            this.futuresContract = source.futuresContract;
            this.liquidity = source.liquidity;
            this.executionId = source.executionId;
            this.fees = source.fees;
            this.liquidation = source.liquidation;
            this.reduceOnly = source.reduceOnly;
            this.marketSnapshot = source.marketSnapshot;
            this.positionSnapshot = source.positionSnapshot;
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
         * Sets the execution time.
         *
         * @param time execution time
         * @return this builder
         * @since 0.25.1
         */
        public Builder time(Instant time) {
            this.time = time;
            return this;
        }

        /**
         * Sets the execution price.
         *
         * @param price execution price
         * @return this builder
         * @since 0.25.1
         */
        public Builder price(Num price) {
            this.price = price;
            return this;
        }

        /**
         * Sets the executed amount.
         *
         * @param amount executed amount
         * @return this builder
         * @since 0.25.1
         */
        public Builder amount(Num amount) {
            this.amount = amount;
            return this;
        }

        /**
         * Sets the legacy fee amount.
         *
         * @param fee legacy fee amount
         * @return this builder
         * @since 0.25.1
         */
        public Builder fee(Num fee) {
            this.fee = fee;
            return this;
        }

        /**
         * Sets the execution side.
         *
         * @param side execution side
         * @return this builder
         * @since 0.25.1
         */
        public Builder side(ExecutionSide side) {
            this.side = side;
            return this;
        }

        /**
         * Sets the order identifier.
         *
         * @param orderId order identifier
         * @return this builder
         * @since 0.25.1
         */
        public Builder orderId(String orderId) {
            this.orderId = orderId;
            return this;
        }

        /**
         * Sets the correlation identifier.
         *
         * @param correlationId correlation identifier
         * @return this builder
         * @since 0.25.1
         */
        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        /**
         * Sets the instrument identifier.
         *
         * @param instrument instrument identifier
         * @return this builder
         * @since 0.25.1
         */
        public Builder instrument(String instrument) {
            this.instrument = instrument;
            return this;
        }

        /**
         * Sets the futures contract.
         *
         * @param futuresContract futures contract
         * @return this builder
         * @since 0.25.1
         */
        @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "The builder stores the immutable FuturesContract by reference; the contract is never mutated after the value is built")
        public Builder futuresContract(FuturesContract futuresContract) {
            this.futuresContract = futuresContract;
            return this;
        }

        /**
         * Sets the execution liquidity.
         *
         * @param liquidity execution liquidity
         * @return this builder
         * @since 0.25.1
         */
        public Builder liquidity(RealtimeBar.Liquidity liquidity) {
            this.liquidity = liquidity;
            return this;
        }

        /**
         * Sets the execution identifier.
         *
         * @param executionId execution identifier
         * @return this builder
         * @since 0.25.1
         */
        public Builder executionId(String executionId) {
            this.executionId = executionId;
            return this;
        }

        /**
         * Sets the fee components.
         *
         * @param fees fee components
         * @return this builder
         * @since 0.25.1
         */
        public Builder fees(List<TradeFee> fees) {
            this.fees = fees == null ? null : List.copyOf(fees);
            return this;
        }

        /**
         * Sets whether the fill liquidates the position.
         *
         * @param liquidation whether the fill liquidates the position
         * @return this builder
         * @since 0.25.1
         */
        public Builder liquidation(boolean liquidation) {
            this.liquidation = liquidation;
            return this;
        }

        /**
         * Sets whether the fill is reduce-only.
         *
         * @param reduceOnly whether the fill is reduce-only
         * @return this builder
         * @since 0.25.1
         */
        public Builder reduceOnly(boolean reduceOnly) {
            this.reduceOnly = reduceOnly;
            return this;
        }

        /**
         * Sets the market snapshot.
         *
         * @param marketSnapshot market snapshot
         * @return this builder
         * @since 0.25.1
         */
        @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "The builder stores the immutable market snapshot by reference; the snapshot is never mutated after the fill is built")
        public Builder marketSnapshot(FuturesMarketSnapshot marketSnapshot) {
            this.marketSnapshot = marketSnapshot;
            return this;
        }

        /**
         * Sets the position snapshot.
         *
         * @param positionSnapshot position snapshot
         * @return this builder
         * @since 0.25.1
         */
        public Builder positionSnapshot(FuturesPositionSnapshot positionSnapshot) {
            this.positionSnapshot = positionSnapshot;
            return this;
        }

        /**
         * Builds the immutable fill.
         *
         * @return the configured fill
         * @since 0.25.1
         */
        public TradeFill build() {
            return new TradeFill(this);
        }
    }
}

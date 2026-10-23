/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * One immutable, coherent L2 observation for a venue, instrument and quantity
 * unit. Levels describe displayed aggregate quantity, not orders or guaranteed
 * fills. Implementations publish unique numeric prices, best first, using their
 * owning numeric factory. No bar series is required.
 *
 * @since 0.27.1
 */
public interface OrderBook extends Serializable {

    /**
     * Book sides, independent of execution direction.
     *
     * @since 0.27.1
     */
    enum Side {
        BID, ASK
    }

    /**
     * Known feed/reconstruction continuity.
     *
     * @since 0.27.1
     */
    enum State {
        SYNCHRONIZED, UNSYNCHRONIZED
    }

    /**
     * Immutable observation identity and coverage. An incomplete side is a known
     * contiguous best-price prefix. Completeness refers only to displayed depth.
     * Source sequence is opaque; resets alone do not establish a gap. Event and
     * availability clocks may be skewed.
     *
     * @param venue          nonblank venue
     * @param instrument     nonblank instrument
     * @param quantityUnit   nonblank displayed quantity unit (e.g. BTC or
     *                       contracts)
     * @param eventTime      source timestamp, or null when unknown
     * @param availableTime  when the observation or validated continuity became
     *                       available, never rebuild/query time
     * @param sourceSequence opaque source sequence, or null when absent
     * @param state          known synchronization state
     * @param completeBids   whether the entire displayed bid side is known
     * @param completeAsks   whether the entire displayed ask side is known
     * @since 0.27.1
     */
    record Metadata(String venue, String instrument, String quantityUnit, Instant eventTime, Instant availableTime,
            String sourceSequence, State state, boolean completeBids, boolean completeAsks) implements Serializable {
        /**
         * Validates required identity, availability and state.
         * 
         * @since 0.27.1
         */
        public Metadata {
            Objects.requireNonNull(venue, "venue");
            Objects.requireNonNull(instrument, "instrument");
            Objects.requireNonNull(quantityUnit, "quantityUnit");
            if (venue.isBlank() || instrument.isBlank() || quantityUnit.isBlank()) {
                throw new IllegalArgumentException("venue, instrument and quantityUnit must be nonblank");
            }
            Objects.requireNonNull(availableTime, "availableTime");
            Objects.requireNonNull(state, "state");
        }
    }

    /**
     * @return owning numeric factory
     *
     * @since 0.27.1
     */
    NumFactory numFactory();

    /**
     * @return immutable observation metadata
     *
     * @since 0.27.1
     */
    Metadata getMetadata();

    /**
     * @return immutable positive-amount bids in strictly descending price order
     *
     * @since 0.27.1
     */
    List<OrderBookLevel> getBids();

    /**
     * @return immutable positive-amount asks in strictly ascending price order
     *
     * @since 0.27.1
     */
    List<OrderBookLevel> getAsks();

    /**
     * @return best bid, including retained unsynchronized quotes
     *
     * @since 0.27.1
     */
    default Optional<OrderBookLevel> getBestBid() {
        return getBids().isEmpty() ? Optional.empty() : Optional.of(getBids().getFirst());
    }

    /**
     * @return best ask, including retained unsynchronized quotes
     *
     * @since 0.27.1
     */
    default Optional<OrderBookLevel> getBestAsk() {
        return getAsks().isEmpty() ? Optional.empty() : Optional.of(getAsks().getFirst());
    }

    /**
     * Tests coverage independently of synchronization. A complete side can end
     * before N, including at zero levels; an incomplete side must contain N levels.
     *
     * @param side       required side
     * @param levelCount positive requested depth
     * @return whether the requested depth is known
     * @since 0.27.1
     */
    default boolean hasDepth(Side side, int levelCount) {
        Objects.requireNonNull(side, "side");
        if (levelCount <= 0) {
            throw new IllegalArgumentException("levelCount must be positive");
        }
        return side == Side.BID ? getMetadata().completeBids() || getBids().size() >= levelCount
                : getMetadata().completeAsks() || getAsks().size() >= levelCount;
    }

    /**
     * @return ask minus bid, or NaN for unsynchronized, one-sided or crossed
     *         quotes; locked quotes have zero spread
     * @since 0.27.1
     */
    default Num getSpread() {
        return hasNormalQuotes() ? getAsks().getFirst().price().minus(getBids().getFirst().price()) : NaN.NaN;
    }

    /**
     * @return quote midpoint, or NaN under the same conditions as spread
     * @since 0.27.1
     */
    default Num getMidPrice() {
        if (!hasNormalQuotes()) {
            return NaN.NaN;
        }
        Num ask = getAsks().getFirst().price();
        Num bid = getBids().getFirst().price();
        Num sum = ask.plus(bid);
        // Halve first only when the primitive-backed sum overflows.
        return Num.isFinite(sum) ? sum.dividedBy(numFactory().two())
                : ask.dividedBy(numFactory().two()).plus(bid.dividedBy(numFactory().two()));
    }

    /**
     * @param side       required side
     * @param levelCount positive requested depth
     * @return displayed quantity up to N levels, zero for a complete empty side, or
     *         NaN for unsynchronized/insufficient coverage
     * @since 0.27.1
     */
    default Num getDepth(Side side, int levelCount) {
        boolean covered = hasDepth(side, levelCount);
        if (!covered || getMetadata().state() != State.SYNCHRONIZED) {
            return NaN.NaN;
        }
        List<OrderBookLevel> levels = side == Side.BID ? getBids() : getAsks();
        Num depth = numFactory().zero();
        for (int i = 0; i < Math.min(levelCount, levels.size()); i++) {
            depth = depth.plus(levels.get(i).amount());
        }
        return Num.isFinite(depth) ? depth : NaN.NaN;
    }

    /**
     * @param levelCount positive requested depth on both sides
     * @return (bid depth - ask depth) / total depth, or NaN without normal quotes,
     *         sufficient coverage and finite positive total quantity; this is not a
     *         probability
     * @since 0.27.1
     */
    default Num getImbalance(int levelCount) {
        Num bids = getDepth(Side.BID, levelCount);
        Num asks = getDepth(Side.ASK, levelCount);
        if (!hasNormalQuotes() || !Num.isFinite(bids) || !Num.isFinite(asks)) {
            return NaN.NaN;
        }
        Num total = bids.plus(asks);
        return Num.isFinite(total) && total.isPositive() ? bids.minus(asks).dividedBy(total) : NaN.NaN;
    }

    private boolean hasNormalQuotes() {
        return getMetadata().state() == State.SYNCHRONIZED && !getBids().isEmpty() && !getAsks().isEmpty()
                && (getBids().getFirst().price().isZero() && getAsks().getFirst().price().isZero()
                        || getBids().getFirst().price().isLessThanOrEqual(getAsks().getFirst().price()));
    }
}

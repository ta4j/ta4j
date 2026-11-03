/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.time.Instant;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

import org.ta4j.core.OrderBook.Metadata;
import org.ta4j.core.OrderBook.Side;
import org.ta4j.core.OrderBook.State;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Thread-confined normalized book construction. Every successful mutation
 * publishes a coherent immutable snapshot; failed batches leave the prior
 * observation unchanged. Source adapters own continuity validation,
 * stale-message rejection and native sequencing/checksums. This builder does
 * not infer gaps from opaque sequence values.
 *
 * <pre>{@code
 * BaseOrderBookBuilder builder = new BaseOrderBookBuilder(factory, "venue", "BTC-USD", "BTC");
 * builder.replace(metadata, bids, asks);
 * OrderBook snapshot = builder.build();
 * builder.apply(nextMetadata, bidUpdates, askUpdates);
 * }</pre>
 *
 * @since 0.27.1
 */
public final class BaseOrderBookBuilder {
    private final NumFactory numFactory;
    private final String venue;
    private final String instrument;
    private final String quantityUnit;
    private BaseOrderBook snapshot;

    /**
     * Creates an uninitialized builder bound to one numeric domain and market. Call
     * replace before build/apply/invalidate.
     *
     * @param numFactory   owning numeric factory
     * @param venue        nonblank venue
     * @param instrument   nonblank instrument
     * @param quantityUnit nonblank displayed quantity unit
     * @since 0.27.1
     */
    public BaseOrderBookBuilder(NumFactory numFactory, String venue, String instrument, String quantityUnit) {
        this.numFactory = Objects.requireNonNull(numFactory, "numFactory");
        this.venue = Objects.requireNonNull(venue, "venue");
        this.instrument = Objects.requireNonNull(instrument, "instrument");
        this.quantityUnit = Objects.requireNonNull(quantityUnit, "quantityUnit");
        if (venue.isBlank() || instrument.isBlank() || quantityUnit.isBlank()) {
            throw new IllegalArgumentException("venue, instrument and quantityUnit must be nonblank");
        }
    }

    /**
     * Atomically initializes/replaces both sides, clearing obsolete prices.
     * Replacement may carry incomplete prefixes and is the only recovery after
     * invalidation. A replacement is not proof of whole-market depth: metadata must
     * declare actual coverage.
     *
     * @param metadata new observation metadata for the bound market
     * @param bids     fresh bid side
     * @param asks     fresh ask side
     * @return this builder
     * @since 0.27.1
     */
    public BaseOrderBookBuilder replace(Metadata metadata, List<OrderBookLevel> bids, List<OrderBookLevel> asks) {
        validateIdentity(metadata);
        BaseOrderBook replacement = new BaseOrderBook(numFactory, metadata, bids, asks);
        snapshot = replacement;
        return this;
    }

    /**
     * Applies absolute quantities on complete stored sides. Positive quantities
     * replace; zero deletes (including harmless missing-price deletes). Empty
     * update lists leave that side unchanged and may accompany an adapter's
     * validated continuity confirmation. Repeated numeric prices in a side fail.
     *
     * <p>
     * Both old and new state must be synchronized. Coverage flags must remain
     * unchanged: deltas cannot establish a previously unknown tail. Use replace for
     * new prefixes/coverage or resynchronization. No time or sequence ordering is
     * inferred here; adapters admit only causally valid normalized observations.
     *
     * @param metadata   new observation/validated-continuity metadata
     * @param bidUpdates absolute bid quantities
     * @param askUpdates absolute ask quantities
     * @return this builder
     * @throws IllegalStateException    before initialization or after invalidation
     * @throws IllegalArgumentException for invalid metadata, unsupported partial
     *                                  side updates or malformed batches
     * @since 0.27.1
     */
    public BaseOrderBookBuilder apply(Metadata metadata, List<OrderBookLevel> bidUpdates,
            List<OrderBookLevel> askUpdates) {
        BaseOrderBook current = build();
        if (current.getMetadata().state() != State.SYNCHRONIZED) {
            throw new IllegalStateException("replace is required after invalidation");
        }
        validateIdentity(metadata);
        Metadata previous = current.getMetadata();
        if (metadata.state() != State.SYNCHRONIZED || metadata.completeBids() != previous.completeBids()
                || metadata.completeAsks() != previous.completeAsks()) {
            throw new IllegalArgumentException("apply requires synchronized metadata and unchanged depth coverage");
        }
        List<OrderBookLevel> bids = BaseOrderBook.normalize(numFactory, bidUpdates, Side.BID, true);
        List<OrderBookLevel> asks = BaseOrderBook.normalize(numFactory, askUpdates, Side.ASK, true);
        if (!previous.completeBids() && !bids.isEmpty() || !previous.completeAsks() && !asks.isEmpty()) {
            throw new IllegalArgumentException("incremental updates require a complete stored side; use replace");
        }
        BaseOrderBook updated = new BaseOrderBook(numFactory, metadata, applySide(current.getBids(), bids),
                applySide(current.getAsks(), asks));
        snapshot = updated;
        return this;
    }

    /**
     * Marks retained quotes unusable after an adapter-reported continuity failure.
     * Preserves source time/sequence and coverage; availableTime is when the
     * condition became known. Only replace permits synchronized updates afterward.
     *
     * @param availableTime time the failure became known
     * @return this builder
     * @throws IllegalStateException before initialization
     * @since 0.27.1
     */
    public BaseOrderBookBuilder invalidate(Instant availableTime) {
        BaseOrderBook current = build();
        Metadata old = current.getMetadata();
        Metadata invalid = new Metadata(venue, instrument, quantityUnit, old.eventTime(), availableTime,
                old.sourceSequence(), State.UNSYNCHRONIZED, old.completeBids(), old.completeAsks());
        snapshot = new BaseOrderBook(numFactory, invalid, current.getBids(), current.getAsks());
        return this;
    }

    /**
     * @return an immutable observation detached from subsequent builder changes;
     *         unchanged builds reuse it without altering availability or source
     *         time
     * @throws IllegalStateException before initialization
     * @since 0.27.1
     */
    public BaseOrderBook build() {
        if (snapshot == null) {
            throw new IllegalStateException("replace must initialize the builder");
        }
        return snapshot;
    }

    private void validateIdentity(Metadata metadata) {
        Objects.requireNonNull(metadata, "metadata");
        if (!venue.equals(metadata.venue()) || !instrument.equals(metadata.instrument())
                || !quantityUnit.equals(metadata.quantityUnit())) {
            throw new IllegalArgumentException(
                    "metadata must describe the builder's venue, instrument and quantity unit");
        }
    }

    private static List<OrderBookLevel> applySide(List<OrderBookLevel> current, List<OrderBookLevel> updates) {
        NavigableMap<Num, OrderBookLevel> levels = new TreeMap<>(BaseOrderBook::comparePrices);
        for (OrderBookLevel level : current) {
            levels.put(level.price(), level);
        }
        for (OrderBookLevel level : updates) {
            if (level.amount().isZero()) {
                levels.remove(level.price());
            } else {
                levels.put(level.price(), level);
            }
        }
        return List.copyOf(levels.values());
    }
}

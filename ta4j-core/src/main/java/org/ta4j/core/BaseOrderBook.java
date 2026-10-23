/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.io.Serial;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.ta4j.core.num.NumFactory;
import org.ta4j.core.num.Num;

/**
 * Immutable, serializable L2 snapshot with numerically unique, best-first
 * sides. Construction normalizes unordered input without changing
 * locked/crossed quotes. Uses identity equality, like other core backing
 * objects; no equality contract across arbitrary OrderBook implementations is
 * implied.
 *
 * @since 0.27.1
 */
@Immutable
public final class BaseOrderBook implements OrderBook {
    @Serial
    private static final long serialVersionUID = 1L;

    private final NumFactory numFactory;
    private final Metadata metadata;
    private final List<OrderBookLevel> bids;
    private final List<OrderBookLevel> asks;

    /**
     * @param numFactory owning factory; compatibility follows NumFactory.produces
     *                   (numeric implementation, not identical decimal precision);
     *                   values are retained without conversion
     * @param metadata   immutable observation identity, availability and coverage
     * @param bids       unordered bid levels with positive amounts
     * @param asks       unordered ask levels with positive amounts
     * @throws IllegalArgumentException for duplicate numeric prices, zero amounts
     *                                  or incompatible numeric values
     * @since 0.27.1
     */
    public BaseOrderBook(NumFactory numFactory, Metadata metadata, List<OrderBookLevel> bids,
            List<OrderBookLevel> asks) {
        this.numFactory = Objects.requireNonNull(numFactory, "numFactory");
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        this.bids = List.copyOf(normalize(numFactory, bids, Side.BID, false));
        this.asks = List.copyOf(normalize(numFactory, asks, Side.ASK, false));
    }

    /**
     * Materializes an immutable snapshot of an external implementation.
     * 
     * @param book source that obeys the immutable OrderBook contract
     * @return the source if already a BaseOrderBook, otherwise a detached snapshot
     * @since 0.27.1
     */
    public static BaseOrderBook copyOf(OrderBook book) {
        Objects.requireNonNull(book, "book");
        return book instanceof BaseOrderBook base ? base
                : new BaseOrderBook(book.numFactory(), book.getMetadata(), book.getBids(), book.getAsks());
    }

    static List<OrderBookLevel> normalize(NumFactory factory, List<OrderBookLevel> levels, Side side, boolean updates) {
        List<OrderBookLevel> sorted = new ArrayList<>(Objects.requireNonNull(levels, "levels"));
        for (OrderBookLevel level : sorted) {
            Objects.requireNonNull(level, "level");
            if (!factory.produces(level.price()) || !factory.produces(level.amount())) {
                throw new IllegalArgumentException("level values must be compatible with numFactory");
            }
            if (!updates && level.amount().isZero()) {
                throw new IllegalArgumentException("snapshot amounts must be positive");
            }
        }
        Comparator<OrderBookLevel> comparator = (left, right) -> comparePrices(left.price(), right.price());
        sorted.sort(side == Side.BID ? comparator.reversed() : comparator);
        for (int i = 1; i < sorted.size(); i++) {
            if (comparePrices(sorted.get(i - 1).price(), sorted.get(i).price()) == 0) {
                throw new IllegalArgumentException("duplicate numeric price on " + side);
            }
        }
        return sorted;
    }

    static int comparePrices(Num left, Num right) {
        return left.isZero() && right.isZero() ? 0 : left.compareTo(right);
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.27.1
     */
    @Override
    public NumFactory numFactory() {
        return numFactory;
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.27.1
     */
    @Override
    public Metadata getMetadata() {
        return metadata;
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.27.1
     */
    @Override
    public List<OrderBookLevel> getBids() {
        return bids;
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.27.1
     */
    @Override
    public List<OrderBookLevel> getAsks() {
        return asks;
    }

    @Override
    public String toString() {
        return "BaseOrderBook[" + metadata.venue() + "/" + metadata.instrument() + ", " + metadata.availableTime()
                + ", " + metadata.state() + ", bestBid=" + getBestBid() + ", bestAsk=" + getBestAsk() + ", bids="
                + bids.size() + ", asks=" + asks.size() + "]";
    }

    @Serial
    private Object writeReplace() {
        return new SerializationProxy(numFactory, metadata, bids, asks);
    }

    @Serial
    private void readObject(ObjectInputStream input) throws InvalidObjectException {
        throw new InvalidObjectException("serialization proxy required");
    }

    private record SerializationProxy(NumFactory numFactory, Metadata metadata, List<OrderBookLevel> bids,
            List<OrderBookLevel> asks) implements Serializable {
        @Serial
        private Object readResolve() {
            return new BaseOrderBook(numFactory, metadata, bids, asks);
        }
    }
}
